package client.wexmanager.core.network

import android.content.Context
import client.wexmanager.core.fs.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Duration

/** FTP / FTPS через commons-net: пассивный режим, двоичный тип, UTF-8. */
class FtpFs private constructor(
    private val client: FTPClient,
    override val home: String,
    private val mlsd: Boolean,
    private val mlst: Boolean,
) : RemoteFileSystem {

    /** Ставится при сетевой ошибке: команда могла оборвать управляющее соединение. */
    @Volatile
    private var broken = false

    override val isAlive: Boolean get() = !broken && client.isConnected

    private inline fun <T> io(block: () -> T): T = try {
        block()
    } catch (e: IOException) {
        broken = true
        throw e
    }

    private fun fail(action: String): Nothing {
        val reply = client.replyString?.trim()?.takeIf { it.isNotEmpty() }
        val code = client.replyCode
        val text = when (code) {
            550 -> "нет доступа или объект не найден"
            553 -> "недопустимое имя файла"
            552 -> "превышена квота на сервере"
            530 -> "требуется вход"
            450, 451 -> "сервер временно не может выполнить операцию"
            else -> reply ?: "код $code"
        }
        throw RemoteException("$action: $text")
    }

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        io {
            // Переход в папку надёжнее, чем LIST с путём (пробелы, нестандартные серверы).
            if (!client.changeWorkingDirectory(path)) fail("Не удалось открыть папку")
            val files: Array<FTPFile?> = if (mlsd) {
                client.mlistDir().takeUnless { client.replyCode == 500 || client.replyCode == 502 } ?: client.listFiles()
            } else client.listFiles()
            files.mapNotNull { f ->
                if (f == null || f.isSelfOrParent()) return@mapNotNull null
                // Имя с «/», «..» и т.п. от сервера — не доверяем (выход за пределы папки при скачивании).
                val name = f.name?.takeIf(RemotePath::isSafeName) ?: return@mapNotNull null
                item(RemotePath.join(path, name), name, f)
            }.distinctBy { it.path }
        }
    }

    private fun item(path: String, name: String, f: FTPFile) = FileItem(
        path = path,
        name = name,
        isDirectory = f.isDirectory,
        size = if (f.isDirectory) 0 else f.size.coerceAtLeast(0),
        lastModified = f.timestamp?.timeInMillis ?: 0L,
        isSymlink = f.isSymbolicLink,
        linkTarget = f.link,
        mode = -1,
        archivePath = null,
    )

    /** MLSD отдаёт саму папку (type=cdir) и родителя (type=pdir) — парсер помечает их как обычные папки. */
    private fun FTPFile.isSelfOrParent(): Boolean {
        val facts = rawListing?.substringBefore(' ')?.split(';').orEmpty()
        return facts.any { it.equals("type=cdir", true) || it.equals("type=pdir", true) }
    }

    override suspend fun stat(path: String): FileItem? = withContext(Dispatchers.IO) {
        io {
            val name = RemotePath.name(path)
            if (mlst) {
                val f = client.mlistFile(path)
                if (f != null) return@io item(path, name, f)
                if (client.replyCode == 550) return@io null
            }
            // Без MLST: SIZE + MDTM (550 — файла нет; 500/502 — команда не поддерживается).
            val size = client.getSize(path)?.trim()?.toLongOrNull()
            val sizeCode = client.replyCode
            val time = client.mdtmCalendar(path)?.timeInMillis
            val timeCode = client.replyCode
            if (size == null && time == null && (sizeCode == 550 || timeCode == 550)) null
            else FileItem(path, name, isDirectory = false, size = size ?: -1L, lastModified = time ?: 0L)
        }
    }

    /** Ссылки в FTP непрозрачны: проверяем, можно ли в неё «войти». */
    suspend fun isDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        io { client.changeWorkingDirectory(path) }
    }

    override suspend fun download(remote: String, local: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        io {
            val total = runCatching { client.getSize(remote)?.trim()?.toLongOrNull() }.getOrNull() ?: -1L
            val input = client.retrieveFileStream(remote) ?: fail("Не удалось скачать файл")
            var completed = false
            try {
                input.use { stream ->
                    local.outputStream().buffered(256 * 1024).use { out -> copyStream(stream, out, total, onProgress) }
                }
                completed = true
            } finally {
                // Прерванную передачу не дожидаемся: соединение пересоздаст очередь.
                if (!completed) broken = true
            }
            if (!client.completePendingCommand()) fail("Передача не завершена")
        }
    }

    override suspend fun upload(local: File, remoteDir: String, onProgress: (Long, Long) -> Unit) {
        local.inputStream().use { input -> uploadStream(input, local.length(), RemotePath.join(remoteDir, local.name), onProgress) }
    }

    override suspend fun uploadStream(input: InputStream, size: Long, remotePath: String, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            // Пишем во временный файл: обрыв не оставит на сервере обрезанный оригинал.
            val part = RemotePath.partPath(remotePath)
            var skipCleanup = false
            try {
                io {
                    val out = client.storeFileStream(part) ?: fail("Не удалось загрузить файл")
                    var completed = false
                    try {
                        out.use { stream -> copyStream(input, stream, size, onProgress) }
                        completed = true
                    } finally {
                        if (!completed) broken = true
                    }
                    if (!client.completePendingCommand()) fail("Передача не завершена")
                    // Многие серверы переименовывают поверх существующего; иначе — DELE и повтор.
                    if (!client.rename(part, remotePath)) {
                        if (!client.deleteFile(remotePath)) fail("Не удалось заменить файл")
                        if (!client.rename(part, remotePath)) {
                            // Оригинал уже удалён — новую версию не трогаем.
                            skipCleanup = true
                            fail("Не удалось заменить файл, новая версия сохранена как «${RemotePath.name(part)}»")
                        }
                    }
                }
                skipCleanup = true
            } finally {
                // На оборванном соединении удалить уже нельзя — останется «.wexpart».
                if (!skipCleanup && !broken) withContext(NonCancellable) { runCatching { client.deleteFile(part) } }
            }
            Unit
        }

    override suspend fun delete(path: String, isDir: Boolean) = withContext(Dispatchers.IO) {
        if (isDir) deleteTree(path) else io { if (!client.deleteFile(path)) fail("Не удалось удалить") }
    }

    private suspend fun deleteTree(path: String) {
        val children = list(path)
        for (c in children) {
            currentCoroutineContext().ensureActive()
            if (c.isDirectory && !c.isSymlink) deleteTree(c.path) else io { if (!client.deleteFile(c.path)) fail("Не удалось удалить «${c.name}»") }
        }
        io {
            // Нельзя удалить текущую рабочую папку на некоторых серверах.
            RemotePath.parent(path)?.let { client.changeWorkingDirectory(it) }
            if (!client.removeDirectory(path)) fail("Не удалось удалить папку")
        }
    }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        io { if (!client.rename(from, to)) fail("Не удалось переименовать") }
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        io {
            if (!client.makeDirectory(path)) {
                // Уже существует — не ошибка.
                if (!client.changeWorkingDirectory(path)) fail("Не удалось создать папку")
            }
        }
    }

    override fun close() {
        if (client.isConnected) {
            if (!broken) runCatching { client.logout() }
            runCatching { client.disconnect() }
        }
    }

    companion object {
        internal fun connect(context: Context, conn: RemoteConnection, password: String): FtpFs {
            val secure = conn.type == RemoteType.FTPS
            var trust: KnownHosts.TofuTrustManager? = null
            val client: FTPClient = if (secure) {
                // Порт 990 — неявный TLS, иначе явный (AUTH TLS).
                FTPSClient(conn.port == 990).apply {
                    val tm = KnownHosts.TofuTrustManager(context, conn.host.trim(), conn.port)
                    trust = tm
                    setTrustManager(tm)
                    // Имя хоста проверяется для сертификатов от CA; принятые по отпечатку (TOFU) — нет.
                    hostnameVerifier = tm.hostnameVerifier
                }
            } else {
                FTPClient()
            }
            val utf8 = conn.encoding.equals("UTF-8", ignoreCase = true)
            client.setControlEncoding(conn.encoding)
            client.setAutodetectUTF8(utf8)
            client.setConnectTimeout(RemoteFs.CONNECT_TIMEOUT_MS)
            client.setDefaultTimeout(RemoteFs.READ_TIMEOUT_MS)
            client.setDataTimeout(Duration.ofMillis(RemoteFs.READ_TIMEOUT_MS.toLong()))
            client.setListHiddenFiles(true)
            try {
                client.connect(conn.host.trim(), conn.port)
                client.setSoTimeout(RemoteFs.READ_TIMEOUT_MS)
                if (!FTPReply.isPositiveCompletion(client.replyCode)) {
                    throw RemoteException("Сервер отклонил подключение: ${client.replyString?.trim().orEmpty()}")
                }
                if (client is FTPSClient) {
                    client.execPBSZ(0)
                    client.execPROT("P")
                }
                val user = if (conn.anonymous) "anonymous" else conn.username.trim()
                val pass = if (conn.anonymous) "wexmanager@example.com" else password
                if (!client.login(user, pass)) {
                    val code = client.replyCode
                    throw if (code == 530 || code == 331 || code == 332) RemoteAuthException()
                    else RemoteException("Вход не выполнен: ${client.replyString?.trim().orEmpty()}")
                }
                if (conn.passiveMode) client.enterLocalPassiveMode() else client.enterLocalActiveMode()
                if (!client.setFileType(FTP.BINARY_FILE_TYPE)) throw RemoteException("Сервер не поддерживает двоичный режим")
                client.setBufferSize(256 * 1024)
                if (utf8) runCatching { client.sendCommand("OPTS UTF8 ON") }
                val mlst = runCatching { client.hasFeature("MLST") }.getOrDefault(false)
                // По RFC 3659 MLST в FEAT означает и поддержку MLSD (многие серверы отдельно его не объявляют).
                // MLSD даёт время в UTC; LIST — без часового пояса, из-за чего даты «съезжают».
                val mlsd = mlst || runCatching { client.hasFeature("MLSD") }.getOrDefault(false)
                val home = runCatching { client.printWorkingDirectory() }.getOrNull()?.takeIf { it.startsWith("/") } ?: "/"
                return FtpFs(client, RemotePath.normalize(home), mlsd, mlst)
            } catch (e: Throwable) {
                runCatching { if (client.isConnected) client.disconnect() }
                trust?.changed?.let { throw it }
                throw e
            }
        }
    }
}
