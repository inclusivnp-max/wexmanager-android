package client.wexmanager.feature.share

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import client.wexmanager.core.util.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.net.BindException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

internal class ShareBindException(message: String) : IOException(message)

/**
 * Один принимающий цикл на [Dispatchers.IO]. Отдаёт только обычные файлы и списки папок
 * внутри [root]. `..`, выход за корень и символические ссылки наружу отклоняются.
 */
internal class ShareHttpServer(
    private val root: File,
    private val port: Int,
) {
    private val server = ServerSocket()
    private val closed = AtomicBoolean(false)
    private var job: Job? = null

    @Volatile
    private var current: Socket? = null

    private val requestCount = MutableStateFlow(0)

    /** Успешные ответы GET и HEAD (HTTP 200). Ошибки доступа не входят. */
    val requests: StateFlow<Int> = requestCount

    var url: String = ""
        private set

    fun start(scope: CoroutineScope) {
        if (port !in 1..65535) throw ShareBindException("Порт должен быть от 1 до 65535")
        val canon = try {
            root.canonicalFile
        } catch (_: IOException) {
            throw ShareBindException("Не удалось открыть папку")
        }
        if (!canon.isDirectory) throw ShareBindException("Папка не найдена")
        if (!canon.canRead()) throw ShareBindException("Нет доступа к папке")
        val address = firstNonLoopbackIpv4() ?: throw ShareBindException("Нет сетевого адреса IPv4. Подключитесь к Wi‑Fi.")
        val host = address.hostAddress?.takeIf { isDottedIpv4(it) }
            ?: throw ShareBindException("Нет сетевого адреса IPv4. Подключитесь к Wi‑Fi.")
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(address, port), 32)
            server.soTimeout = ACCEPT_TIMEOUT_MS
        } catch (_: BindException) {
            shutdownSockets()
            throw ShareBindException("Не удалось занять порт. Возможно, он уже используется.")
        } catch (_: SecurityException) {
            shutdownSockets()
            throw ShareBindException("Система запретила открыть порт.")
        } catch (_: IOException) {
            shutdownSockets()
            throw ShareBindException("Не удалось запустить сервер.")
        }
        url = "http://$host:${server.localPort}/"
        job = scope.launch(Dispatchers.IO) {
            try {
                acceptLoop(canon)
            } finally {
                closed.set(true)
                shutdownSockets()
            }
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) {
            shutdownSockets()
            return
        }
        shutdownSockets()
        job?.cancel()
    }

    private fun shutdownSockets() {
        try {
            server.close()
        } catch (_: IOException) {
        }
        current?.let { socket ->
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    private suspend fun acceptLoop(root: File) {
        while (!closed.get() && coroutineContext.isActive) {
            val client = try {
                server.accept()
            } catch (_: SocketTimeoutException) {
                continue
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                break
            }
            current = client
            try {
                if (!closed.get() && coroutineContext.isActive) {
                    serveClient(client, root) { requestCount.update { count -> count + 1 } }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Клиент оборвал соединение — слушаем дальше.
            } finally {
                if (current === client) current = null
                try {
                    client.close()
                } catch (_: IOException) {
                }
            }
        }
    }
}

private const val ACCEPT_TIMEOUT_MS = 500
private const val CLIENT_READ_TIMEOUT_MS = 20_000
private const val MAX_TARGET = 4096
private const val MAX_LINE = 8192
private const val MAX_HEADERS = 16 * 1024
private const val MAX_HEADER_LINES = 100
private const val COPY_BUFFER = 64 * 1024

private val CONTENT_TYPES = mapOf(
    "html" to "text/html; charset=utf-8",
    "htm" to "text/html; charset=utf-8",
    "css" to "text/css; charset=utf-8",
    "js" to "text/javascript; charset=utf-8",
    "mjs" to "text/javascript; charset=utf-8",
    "json" to "application/json",
    "txt" to "text/plain; charset=utf-8",
    "log" to "text/plain; charset=utf-8",
    "md" to "text/plain; charset=utf-8",
    "csv" to "text/csv; charset=utf-8",
    "xml" to "application/xml",
    "svg" to "image/svg+xml",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "ico" to "image/x-icon",
    "bmp" to "image/bmp",
    "pdf" to "application/pdf",
    "zip" to "application/zip",
    "7z" to "application/x-7z-compressed",
    "rar" to "application/vnd.rar",
    "gz" to "application/gzip",
    "tar" to "application/x-tar",
    "apk" to "application/vnd.android.package-archive",
    "mp3" to "audio/mpeg",
    "ogg" to "audio/ogg",
    "wav" to "audio/wav",
    "m4a" to "audio/mp4",
    "mp4" to "video/mp4",
    "webm" to "video/webm",
    "mkv" to "video/x-matroska",
    "woff" to "font/woff",
    "woff2" to "font/woff2",
    "ttf" to "font/ttf",
    "otf" to "font/otf",
)

private sealed interface ParsedTarget {
    data class Ok(val segments: List<String>) : ParsedTarget
    data object Bad : ParsedTarget
    data object Denied : ParsedTarget
}

private sealed interface RequestLine {
    data class Ok(val method: String, val target: String) : RequestLine
    data object Bad : RequestLine
    data object Eof : RequestLine
}

/**
 * Первый не-loopback IPv4 на поднятом интерфейсе. Link-local (169.254) берём только
 * если обычного адреса нет — иначе ссылка ведёт в никуда при живом Wi‑Fi.
 */
private fun firstNonLoopbackIpv4(): Inet4Address? {
    val interfaces = try {
        NetworkInterface.getNetworkInterfaces()?.let { Collections.list(it) }.orEmpty()
    } catch (_: SocketException) {
        return null
    }
    var linkLocal: Inet4Address? = null
    for (nic in interfaces) {
        val up = try {
            nic.isUp && !nic.isLoopback
        } catch (_: SocketException) {
            false
        }
        if (!up) continue
        for (addr in Collections.list(nic.inetAddresses)) {
            if (addr !is Inet4Address || addr.isLoopbackAddress || addr.isAnyLocalAddress || addr.isMulticastAddress) continue
            if (addr.isLinkLocalAddress) {
                if (linkLocal == null) linkLocal = addr
                continue
            }
            return addr
        }
    }
    return linkLocal
}

private fun isDottedIpv4(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all { it.isDigit() } && part.toInt() in 0..255
    }
}

private suspend fun serveClient(socket: Socket, root: File, onSuccess: () -> Unit) {
    socket.soTimeout = CLIENT_READ_TIMEOUT_MS
    socket.tcpNoDelay = true
    when (val request = readRequest(socket.getInputStream())) {
        RequestLine.Eof -> Unit
        RequestLine.Bad -> sendStatus(socket, 400, "Bad Request", "Неверный запрос.\n", head = false)
        is RequestLine.Ok -> when (request.method) {
            "GET" -> servePath(socket, root, request.target, head = false, onSuccess)
            "HEAD" -> servePath(socket, root, request.target, head = true, onSuccess)
            else -> sendStatus(
                socket,
                405,
                "Method Not Allowed",
                "Метод не поддерживается. Доступны только GET и HEAD.\n",
                head = false,
                allow = true,
            )
        }
    }
}

private suspend fun servePath(socket: Socket, root: File, target: String, head: Boolean, onSuccess: () -> Unit) {
    val parsed = parseTarget(target)
    val segments = when (parsed) {
        ParsedTarget.Bad -> {
            sendStatus(socket, 400, "Bad Request", "Неверный запрос.\n", head)
            return
        }
        ParsedTarget.Denied -> {
            sendStatus(socket, 403, "Forbidden", "Доступ запрещён.\n", head)
            return
        }
        is ParsedTarget.Ok -> parsed.segments
    }
    val lexical = joinUnder(root, segments) ?: run {
        sendStatus(socket, 403, "Forbidden", "Доступ запрещён.\n", head)
        return
    }
    val canon = try {
        lexical.canonicalFile
    } catch (_: IOException) {
        sendStatus(socket, 404, "Not Found", "Не найдено.\n", head)
        return
    }
    // canonicalFile раскрывает ссылки. Цель должна остаться внутри выбранного корня.
    if (!isWithin(root.path, canon.path)) {
        sendStatus(socket, 403, "Forbidden", "Доступ запрещён.\n", head)
        return
    }
    val flags = OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC
    val fd = try {
        Os.open(canon.path, flags, 0)
    } catch (error: ErrnoException) {
        when (error.errno) {
            OsConstants.ENOENT, OsConstants.ENOTDIR -> sendStatus(socket, 404, "Not Found", "Не найдено.\n", head)
            else -> sendStatus(socket, 403, "Forbidden", "Доступ запрещён.\n", head)
        }
        return
    }
    try {
        val stat = try {
            Os.fstat(fd)
        } catch (_: ErrnoException) {
            sendStatus(socket, 403, "Forbidden", "Доступ запрещён.\n", head)
            return
        }
        when {
            OsConstants.S_ISDIR(stat.st_mode) -> {
                val body = directoryHtml(segments, canon)
                if (body == null) sendStatus(socket, 403, "Forbidden", "Нет доступа к папке.\n", head)
                else {
                    sendBytes(socket, "text/html; charset=utf-8", body, head)
                    onSuccess()
                }
            }
            // Не каталог и не обычный файл: FIFO, устройство, сокет. Не читаем — FIFO без O_NONBLOCK завис бы.
            OsConstants.S_ISREG(stat.st_mode) -> {
                if (sendFile(socket, fd, stat.st_size, contentType(canon.name), head)) onSuccess()
            }
            else -> sendStatus(socket, 403, "Forbidden", "Доступ запрещён.\n", head)
        }
    } finally {
        try {
            Os.close(fd)
        } catch (_: ErrnoException) {
        }
    }
}

/**
 * `..` режется до любого обращения к файловой системе. На Linux `File(parent, "/abs")`
 * выбрасывает parent, поэтому сегменты собираются по одному и не бывают абсолютными.
 * Декодирование однократное: `%252e%252e` не превращается в `..`.
 */
private fun parseTarget(rawTarget: String): ParsedTarget {
    if (rawTarget.length > MAX_TARGET) return ParsedTarget.Bad
    val hash = rawTarget.indexOf('#')
    val noHash = if (hash >= 0) rawTarget.substring(0, hash) else rawTarget
    val query = noHash.indexOf('?')
    val bare = if (query >= 0) noHash.substring(0, query) else noHash
    if (bare.isEmpty() || bare[0] != '/' || bare.any { it == '\\' || it.code < 0x20 || it.code == 0x7F }) {
        return ParsedTarget.Bad
    }
    val decoded = percentDecode(bare) ?: return ParsedTarget.Bad
    if (decoded.any { it == '\\' || it.code < 0x20 || it.code == 0x7F }) return ParsedTarget.Bad
    val segments = ArrayList<String>()
    for (segment in decoded.split('/')) {
        if (segment.isEmpty() || segment == ".") continue
        if (segment == ".." || segment.contains('/') || segment.contains('\\')) return ParsedTarget.Denied
        if (segment.length > 255) return ParsedTarget.Bad
        segments += segment
    }
    return ParsedTarget.Ok(segments)
}

private fun joinUnder(root: File, segments: List<String>): File? {
    var file = root
    for (segment in segments) {
        if (segment.isEmpty() || segment == "." || segment == ".." || segment.indexOf('/') >= 0 || segment.indexOf('\\') >= 0 || segment.indexOf('\u0000') >= 0) {
            return null
        }
        file = File(file, segment)
    }
    return file
}

private fun isWithin(rootPath: String, childPath: String): Boolean {
    if (childPath == rootPath) return true
    val prefix = if (rootPath.endsWith(File.separator)) rootPath else rootPath + File.separator
    return childPath.startsWith(prefix)
}

private fun percentDecode(raw: String): String? {
    val bytes = ByteArrayOutputStream(raw.length)
    var index = 0
    while (index < raw.length) {
        val c = raw[index]
        if (c == '%') {
            if (index + 2 >= raw.length) return null
            val hi = hex(raw[index + 1])
            val lo = hex(raw[index + 2])
            if (hi < 0 || lo < 0) return null
            bytes.write((hi shl 4) or lo)
            index += 3
        } else {
            if (c.code > 0xFF) return null
            bytes.write(c.code)
            index++
        }
    }
    return try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray()))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

private fun hex(c: Char): Int = when (c) {
    in '0'..'9' -> c.code - '0'.code
    in 'a'..'f' -> c.code - 'a'.code + 10
    in 'A'..'F' -> c.code - 'A'.code + 10
    else -> -1
}

private fun directoryHtml(segments: List<String>, dir: File): ByteArray? {
    val children = try {
        dir.listFiles()
    } catch (_: SecurityException) {
        null
    } ?: return null
    val heading = if (segments.isEmpty()) "Корневая папка" else "/" + segments.joinToString("/")
    val sorted = children.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Format.RU) }))
    val sb = StringBuilder(512 + sorted.size * 48)
    sb.append("<!DOCTYPE html>\n<html lang=\"ru\">\n<head>\n<meta charset=\"utf-8\">\n")
    sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
    sb.append("<title>Каталог</title>\n</head>\n<body>\n<h1>")
    sb.append(htmlEscape(heading))
    sb.append("</h1>\n<ul>\n")
    if (segments.isNotEmpty()) {
        sb.append("<li><a href=\"")
        sb.append(htmlEscape(urlPath(segments.dropLast(1), directory = true)))
        sb.append("\">Вверх</a></li>\n")
    }
    var visible = 0
    for (child in sorted) {
        val name = child.name
        if (!safeListName(name)) continue
        val directory = try {
            child.isDirectory
        } catch (_: SecurityException) {
            false
        }
        sb.append("<li><a href=\"")
        sb.append(htmlEscape(urlPath(segments + name, directory)))
        sb.append("\">")
        sb.append(htmlEscape(if (directory) "$name/" else name))
        sb.append("</a>")
        if (!directory) {
            sb.append(" — ")
            sb.append(htmlEscape(Format.size(child.length())))
        }
        sb.append("</li>\n")
        visible++
    }
    sb.append("</ul>\n")
    if (visible == 0) sb.append("<p>Папка пуста</p>\n")
    sb.append("</body>\n</html>\n")
    return sb.toString().toByteArray(Charsets.UTF_8)
}

private fun safeListName(name: String): Boolean {
    if (name.isEmpty() || name == "." || name == "..") return false
    if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.indexOf('\u0000') >= 0) return false
    return name.none { it.code < 0x20 || it.code == 0x7F }
}

private fun urlPath(segments: List<String>, directory: Boolean): String {
    if (segments.isEmpty()) return "/"
    val sb = StringBuilder()
    for (segment in segments) {
        sb.append('/')
        sb.append(encodeSegment(segment))
    }
    if (directory) sb.append('/')
    return sb.toString()
}

private val HEX = "0123456789ABCDEF".toCharArray()

private fun encodeSegment(name: String): String {
    val bytes = name.toByteArray(Charsets.UTF_8)
    val sb = StringBuilder(bytes.size)
    for (byte in bytes) {
        val value = byte.toInt() and 0xFF
        val plain = value in 'a'.code..'z'.code ||
            value in 'A'.code..'Z'.code ||
            value in '0'.code..'9'.code ||
            value == '-'.code || value == '_'.code || value == '.'.code || value == '~'.code
        if (plain) sb.append(value.toChar()) else {
            sb.append('%')
            sb.append(HEX[value ushr 4])
            sb.append(HEX[value and 0x0F])
        }
    }
    return sb.toString()
}

private fun htmlEscape(text: String): String = buildString(text.length) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(c)
        }
    }
}

private fun contentType(name: String): String {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return "application/octet-stream"
    val ext = name.substring(dot + 1).lowercase(Locale.US)
    if (ext.length > 12 || ext.any { it !in 'a'..'z' && it !in '0'..'9' }) return "application/octet-stream"
    return CONTENT_TYPES[ext] ?: "application/octet-stream"
}

private suspend fun sendFile(socket: Socket, fd: FileDescriptor, size: Long, type: String, head: Boolean): Boolean {
    if (size < 0L) {
        sendStatus(socket, 403, "Forbidden", "Доступ запрещён.\n", head)
        return false
    }
    val out = BufferedOutputStream(socket.getOutputStream(), COPY_BUFFER)
    out.write(headerBytes(200, "OK", type, size, allow = false))
    if (!head && size > 0L) {
        val buf = ByteArray(COPY_BUFFER)
        var left = size
        while (left > 0L) {
            coroutineContext.ensureActive()
            val want = minOf(buf.size.toLong(), left).toInt()
            val read = Os.read(fd, buf, 0, want)
            if (read <= 0) break
            out.write(buf, 0, read)
            left -= read
        }
    }
    out.flush()
    return true
}

private fun sendBytes(socket: Socket, type: String, body: ByteArray, head: Boolean) {
    val out = socket.getOutputStream()
    out.write(headerBytes(200, "OK", type, body.size.toLong(), allow = false))
    if (!head) out.write(body)
    out.flush()
}

private fun sendStatus(socket: Socket, code: Int, reason: String, body: String, head: Boolean, allow: Boolean = false) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    val out = socket.getOutputStream()
    out.write(headerBytes(code, reason, "text/plain; charset=utf-8", bytes.size.toLong(), allow))
    if (!head) out.write(bytes)
    out.flush()
}

private fun headerBytes(code: Int, reason: String, type: String, length: Long, allow: Boolean): ByteArray {
    val text = buildString {
        append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
        append("Content-Type: ").append(type).append("\r\n")
        append("Content-Length: ").append(length).append("\r\n")
        append("Connection: close\r\n")
        if (allow) append("Allow: GET, HEAD\r\n")
        append("\r\n")
    }
    return text.toByteArray(Charsets.US_ASCII)
}

private fun readRequest(input: InputStream): RequestLine {
    var used = 0
    fun line(): String? {
        val out = ByteArrayOutputStream()
        while (true) {
            if (used >= MAX_HEADERS) return null
            val b = input.read()
            if (b < 0) return if (out.size() == 0 && used == 0) "" else null
            used++
            if (b == '\n'.code) break
            if (b == '\r'.code) continue
            if (out.size() >= MAX_LINE) return null
            out.write(b)
        }
        return out.toString(Charsets.ISO_8859_1)
    }
    val request = line() ?: return RequestLine.Bad
    if (request.isEmpty() && used == 0) return RequestLine.Eof
    if (request.isEmpty()) return RequestLine.Bad
    var lines = 0
    var ended = false
    while (lines < MAX_HEADER_LINES) {
        val header = line() ?: return RequestLine.Bad
        if (header.isEmpty()) {
            ended = true
            break
        }
        lines++
    }
    if (!ended) return RequestLine.Bad
    val parts = request.split(' ')
    if (parts.size != 3) return RequestLine.Bad
    val method = parts[0]
    val target = parts[1]
    val version = parts[2]
    if (method.isEmpty() || method.any { it !in 'A'..'Z' && it !in 'a'..'z' }) return RequestLine.Bad
    if (version != "HTTP/1.1" && version != "HTTP/1.0") return RequestLine.Bad
    if (target.isEmpty() || target.length > MAX_TARGET) return RequestLine.Bad
    return RequestLine.Ok(method, target)
}
