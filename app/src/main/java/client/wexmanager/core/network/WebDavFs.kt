package client.wexmanager.core.network

import android.util.Xml
import client.wexmanager.core.fs.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * WebDAV (Nextcloud, ownCloud, Яндекс.Диск, NAS) на OkHttp: PROPFIND для листинга, GET/PUT для передачи,
 * MOVE/MKCOL/DELETE. Пути — абсолютные пути на сервере («/remote.php/dav/files/user/Документы»).
 * Загрузка атомарна: PUT во «.<имя>.wexpart», затем MOVE поверх цели.
 */
class WebDavFs private constructor(
    private val client: OkHttpClient,
    private val origin: HttpUrl,
    override val home: String,
) : RemoteFileSystem {

    @Volatile private var closed = false
    override val isAlive: Boolean get() = !closed

    /** URL пути; у папок — с «/» на конце (без него многие серверы отвечают переадресацией). */
    private fun url(path: String, dir: Boolean = false): HttpUrl {
        val b = origin.newBuilder().encodedPath("/")
        RemotePath.normalize(path).split('/').filter { it.isNotEmpty() }.forEach { b.addPathSegment(it) }
        if (dir && RemotePath.normalize(path) != "/") b.addPathSegment("")
        return b.build()
    }

    /** Запрос с отменой: отмена корутины сразу обрывает HTTP-вызов (а не ждёт таймаута чтения). */
    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                // Отменили, пока шёл ответ, — тело закрываем сами.
                cont.resume(response) { _, r, _ -> r.close() }
            }
        })
    }

    private fun Response.fail(action: String): Nothing {
        val text = when (code) {
            401 -> throw RemoteAuthException()
            403 -> "нет доступа"
            404 -> "не найдено"
            405 -> "операция не разрешена (адрес не WebDAV?)"
            409 -> "родительская папка не существует"
            412 -> "объект уже существует"
            413 -> "файл слишком большой для сервера"
            423 -> "объект заблокирован"
            507 -> "недостаточно места на сервере"
            in 500..599 -> "ошибка сервера ($code)"
            else -> "код $code ${message}".trim()
        }
        close()
        throw RemoteException("$action: $text")
    }

    private suspend fun propfind(path: String, depth: Int, dir: Boolean): Response {
        val body = PROPFIND_BODY.toRequestBody(XML)
        return execute(Request.Builder().url(url(path, dir)).method("PROPFIND", body).header("Depth", depth.toString()).build())
    }

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        propfind(path, 1, true).use { r ->
            if (r.code != 207) r.fail("Не удалось открыть папку")
            val self = RemotePath.normalize(path)
            parse(r.body.byteStream()).filter { it.path != self }
                .mapNotNull { e ->
                    val name = RemotePath.name(e.path).takeIf(RemotePath::isSafeName) ?: return@mapNotNull null
                    // Ответ про чужую папку (переадресация, странный сервер) — не показываем.
                    if (RemotePath.parent(e.path) != self) return@mapNotNull null
                    FileItem(e.path, name, e.dir, if (e.dir) 0 else e.size.coerceAtLeast(0), e.modified)
                }
                .distinctBy { it.path }
        }
    }

    override suspend fun stat(path: String): FileItem? = withContext(Dispatchers.IO) {
        propfind(path, 0, false).use { r ->
            if (r.code == 404) return@use null
            if (r.code != 207) r.fail("Не удалось получить сведения")
            val e = parse(r.body.byteStream()).firstOrNull() ?: return@use null
            val p = RemotePath.normalize(path)
            FileItem(p, RemotePath.name(p), e.dir, if (e.dir) 0 else e.size, e.modified)
        }
    }

    override suspend fun download(remote: String, local: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url(remote)).get().build()).use { r ->
            if (!r.isSuccessful) r.fail("Не удалось скачать файл")
            val total = r.body.contentLength()
            r.body.byteStream().use { input ->
                local.outputStream().buffered(256 * 1024).use { out -> copyStream(input, out, total, onProgress) }
            }
            Unit
        }
    }

    override suspend fun upload(local: File, remoteDir: String, onProgress: (Long, Long) -> Unit) {
        local.inputStream().use { input -> uploadStream(input, local.length(), RemotePath.join(remoteDir, local.name), onProgress) }
    }

    override suspend fun uploadStream(input: InputStream, size: Long, remotePath: String, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            val job = currentCoroutineContext()[Job]
            val body = object : RequestBody() {
                override fun contentType() = OCTET
                override fun contentLength() = size
                // Тело отправляется один раз: повтор после переадресации/авторизации прочитал бы пустой поток.
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) {
                    val buf = ByteArray(256 * 1024)
                    var done = 0L
                    while (true) {
                        if (job?.isActive == false) throw java.io.InterruptedIOException("отменено")
                        val n = input.read(buf)
                        if (n < 0) break
                        sink.write(buf, 0, n)
                        done += n
                        onProgress(done, size)
                    }
                }
            }
            val part = RemotePath.partPath(remotePath)
            var moved = false
            try {
                execute(Request.Builder().url(url(part)).put(body).build()).use { r ->
                    if (!r.isSuccessful) r.fail("Не удалось загрузить файл")
                }
                execute(move(part, remotePath, overwrite = true)).use { r ->
                    if (!r.isSuccessful) r.fail("Не удалось заменить файл")
                }
                moved = true
            } finally {
                if (!moved) runCatching { execute(Request.Builder().url(url(part)).delete().build()).close() }
            }
        }

    private fun move(from: String, to: String, overwrite: Boolean, dir: Boolean = false): Request =
        Request.Builder().url(url(from, dir)).method("MOVE", null)
            .header("Destination", url(to, dir).toString())
            .header("Overwrite", if (overwrite) "T" else "F")
            .build()

    override suspend fun delete(path: String, isDir: Boolean) = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url(path, isDir)).delete().build()).use { r ->
            if (!r.isSuccessful && r.code != 404) r.fail("Не удалось удалить")
        }
    }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        val dir = stat(from)?.isDirectory == true
        execute(move(from, to, overwrite = false, dir = dir)).use { r ->
            if (!r.isSuccessful) r.fail("Не удалось переименовать")
        }
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        execute(Request.Builder().url(url(path, true)).method("MKCOL", null).build()).use { r ->
            if (!r.isSuccessful) r.fail("Не удалось создать папку")
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        client.connectionPool.evictAll()
    }

    private class Entry(val path: String, val dir: Boolean, val size: Long, val modified: Long)

    /** Разбор multistatus (RFC 4918): href, resourcetype/collection, getcontentlength, getlastmodified. */
    private fun parse(input: InputStream): List<Entry> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(input, null)
        val out = ArrayList<Entry>()
        var href: String? = null
        var dir = false
        var size = -1L
        var modified = 0L
        var okStatus = true
        var text = StringBuilder()
        while (true) {
            when (p.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    if (p.namespace == DAV) when (p.name) {
                        "response" -> { href = null; dir = false; size = -1; modified = 0 }
                        "propstat" -> okStatus = true
                        "collection" -> if (okStatus) dir = true
                    }
                }
                XmlPullParser.TEXT -> text.append(p.text)
                XmlPullParser.END_TAG -> if (p.namespace == DAV) when (p.name) {
                    "href" -> href = text.toString().trim()
                    "status" -> okStatus = text.contains(" 200")
                    "getcontentlength" -> text.toString().trim().toLongOrNull()?.let { size = it }
                    "getlastmodified" -> modified = httpDate(text.toString().trim())
                    "response" -> href?.let { h -> pathOf(h)?.let { out += Entry(it, dir, size, modified) } }
                }
            }
        }
        return out
    }

    /** href бывает полным URL или путём; в любом случае — путь, раскодированный из процентов. */
    private fun pathOf(href: String): String? = runCatching {
        val raw = if (href.startsWith("http://") || href.startsWith("https://")) java.net.URI(href).rawPath else href.substringBefore('?')
        RemotePath.normalize(URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8"))
    }.getOrNull()

    private fun httpDate(s: String): Long =
        runCatching { RFC1123.get()!!.parse(s)?.time }.getOrNull() ?: 0L

    companion object {
        private const val DAV = "DAV:"
        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val OCTET = "application/octet-stream".toMediaType()
        private const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
        private val RFC1123 = ThreadLocal.withInitial {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }
        }

        /** Общий клиент: пул соединений и потоки — на всё приложение. */
        private val shared by lazy {
            OkHttpClient.Builder()
                .connectTimeout(RemoteFs.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .readTimeout(RemoteFs.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .writeTimeout(RemoteFs.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .build()
        }

        suspend fun connect(conn: RemoteConnection, password: String): WebDavFs = withContext(Dispatchers.IO) {
            val origin = HttpUrl.Builder()
                .scheme(if (conn.useTls) "https" else "http")
                .host(conn.host.trim())
                .port(conn.port)
                .build()
            val auth = if (conn.anonymous || conn.username.isBlank()) null else Credentials.basic(conn.username, password, Charsets.UTF_8)
            val client = shared.newBuilder()
                // Basic сразу, без лишнего круга 401: соединение и так защищено TLS (или пользователь сам выбрал HTTP).
                .addInterceptor { chain ->
                    val req = chain.request()
                    chain.proceed(if (auth != null) req.newBuilder().header("Authorization", auth).build() else req)
                }
                .build()
            val home = RemotePath.normalize(conn.initialPath.ifBlank { "/" })
            val fs = WebDavFs(client, origin, home)
            // Проверка адреса и входа: PROPFIND по стартовой папке.
            fs.propfind(home, 0, true).use { r ->
                when {
                    r.code == 207 -> Unit
                    r.code == 401 -> throw RemoteAuthException()
                    r.code in 300..399 -> throw RemoteException("Сервер переадресует на ${r.header("Location") ?: "другой адрес"} — укажите его в настройках подключения")
                    r.code == 404 -> throw RemoteException("Папка $home не найдена на сервере")
                    r.code == 405 || r.code == 501 -> throw RemoteException("По этому адресу нет WebDAV (код ${r.code}) — проверьте путь, например /remote.php/dav/files/<имя>/")
                    else -> throw RemoteException("Сервер ответил кодом ${r.code}")
                }
            }
            fs
        }
    }
}
