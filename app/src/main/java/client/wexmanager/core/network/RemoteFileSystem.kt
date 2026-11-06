package client.wexmanager.core.network

import android.content.Context
import client.wexmanager.core.fs.FileItem
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMBApiException
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.SftpException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

/** Удалённая файловая система. Все методы — на Dispatchers.IO; закрывать вне главного потока. */
interface RemoteFileSystem : Closeable {
    /** Домашняя (стартовая) папка. */
    val home: String

    /** Соединение живо (по локальным признакам, без обращения к серверу). */
    val isAlive: Boolean

    /** Без «.», «..», небезопасных имён и дублей путей. */
    suspend fun list(path: String): List<FileItem>

    /** Сведения о файле; null — не существует. Неизвестные поля: size = -1, lastModified = 0. */
    suspend fun stat(path: String): FileItem?

    suspend fun download(remote: String, local: File, onProgress: (Long, Long) -> Unit)

    /** Загрузка атомарна: во временный «.<имя>.wexpart» рядом, затем замена целевого файла. */
    suspend fun upload(local: File, remoteDir: String, onProgress: (Long, Long) -> Unit)

    /** Загрузка из потока (content://) в [remotePath]; [size] < 0 — неизвестен. Атомарна, как [upload]. */
    suspend fun uploadStream(input: InputStream, size: Long, remotePath: String, onProgress: (Long, Long) -> Unit)

    /** Папки удаляются рекурсивно. */
    suspend fun delete(path: String, isDir: Boolean)
    suspend fun rename(from: String, to: String)
    suspend fun mkdir(path: String)
}

object RemoteFs {
    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 30_000

    private val connectScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Открывает соединение. Отменяется сразу; если сокет всё же успел подключиться
     * после отмены — соединение закрывается, утечки нет.
     */
    suspend fun open(context: Context, connection: RemoteConnection, password: String): RemoteFileSystem {
        val app = context.applicationContext
        val result = CompletableDeferred<RemoteFileSystem>()
        connectScope.launch {
            try {
                val fs = when (connection.type) {
                    RemoteType.SFTP -> SftpFs.connect(app, connection, password)
                    RemoteType.FTP, RemoteType.FTPS -> FtpFs.connect(app, connection, password)
                    RemoteType.SMB -> SmbFs.connect(connection, password)
                    RemoteType.WEBDAV -> WebDavFs.connect(connection, password)
                }
                if (!result.complete(fs)) fs.closeQuietly()
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        }
        try {
            return result.await()
        } catch (e: CancellationException) {
            if (!result.completeExceptionally(e)) {
                runCatching { result.getCompleted() }.getOrNull()?.let { fs -> connectScope.launch { fs.closeQuietly() } }
            }
            throw e
        }
    }

    /** Открыть с паролем из хранилища. */
    suspend fun open(context: Context, connection: RemoteConnection): RemoteFileSystem =
        open(context, connection, RemoteConnections.get(context).passwordFor(connection))
}

fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: Throwable) {
    }
}

/** Копирование потока с прогрессом и проверкой отмены. */
internal suspend fun copyStream(input: InputStream, output: OutputStream, total: Long, onProgress: (Long, Long) -> Unit): Long {
    val buf = ByteArray(128 * 1024)
    var done = 0L
    var lastReport = 0L
    onProgress(0, total)
    val ctx = currentCoroutineContext()
    while (true) {
        ctx.ensureActive()
        val n = input.read(buf)
        if (n < 0) break
        output.write(buf, 0, n)
        done += n
        val now = System.nanoTime()
        if (now - lastReport > 80_000_000L) {
            lastReport = now
            onProgress(done, total)
        }
    }
    output.flush()
    onProgress(done, if (total > 0) total else done)
    return done
}

internal fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { t -> t.cause?.takeIf { it !== t } }

/** Признак обрыва соединения: имеет смысл переподключиться и повторить. */
internal fun Throwable.isConnectionLost(): Boolean = causes().any { t ->
    when (t) {
        is SocketException, is InterruptedIOException -> true
        is org.apache.commons.net.ftp.FTPConnectionClosedException -> true
        is com.hierynomus.protocol.transport.TransportException -> true
        is SftpException -> t.id == ChannelSftp.SSH_FX_CONNECTION_LOST || t.id == ChannelSftp.SSH_FX_NO_CONNECTION
        is JSchException -> t.message?.contains("session is down", true) == true || t.message?.contains("channel is not opened", true) == true
        else -> false
    }
}

/** Понятный русский текст ошибки. */
object RemoteErrors {
    fun message(t: Throwable): String {
        val chain = t.causes().toList()
        chain.firstNotNullOfOrNull { it as? HostKeyChangedException }?.let {
            return "Ключ сервера изменился! Если вы не меняли его сами, соединение может быть перехвачено."
        }
        chain.firstNotNullOfOrNull { it as? RemoteException }?.let { return it.message ?: "Ошибка" }
        for (c in chain) {
            when (c) {
                is UnknownHostException -> return "Хост не найден. Проверьте адрес и подключение к сети"
                is NoRouteToHostException -> return "Хост недоступен — нет маршрута в сети"
                is ConnectException, is PortUnreachableException ->
                    return "Не удалось подключиться: сервер не отвечает или порт закрыт"
                is SocketTimeoutException, is TimeoutException -> return "Превышено время ожидания ответа сервера"
                is FileNotFoundException -> return "Нет доступа к локальному файлу: ${c.message ?: ""}".trim()
                is SSLException -> return if (c.message?.contains("Hostname", true) == true) {
                    "Сертификат сервера выдан для другого имени хоста — соединение небезопасно"
                } else {
                    "Ошибка защищённого соединения (TLS): ${c.message ?: "рукопожатие не удалось"}"
                }
                is org.apache.commons.net.ftp.FTPConnectionClosedException -> return "Сервер закрыл соединение"
                is SftpException -> return sftp(c)
                is SMBApiException -> return smb(c)
                is JSchException -> jsch(c)?.let { return it }
            }
        }
        for (c in chain) {
            val m = c.message ?: continue
            when {
                m.contains("ENOSPC") || m.contains("No space left", true) -> return "Недостаточно места на устройстве"
                m.contains("EACCES") || m.contains("Permission denied", true) -> return "Нет доступа"
                m.contains("timed out", true) || m.contains("timeout", true) -> return "Превышено время ожидания ответа сервера"
                m.contains("Connection refused", true) -> return "Сервер отклонил подключение (порт закрыт?)"
                m.contains("Connection reset", true) || m.contains("Broken pipe", true) -> return "Соединение разорвано"
            }
        }
        if (t.isConnectionLost()) return "Соединение разорвано"
        val first = chain.first()
        return first.message?.takeIf { it.isNotBlank() } ?: first.javaClass.simpleName
    }

    private fun jsch(e: JSchException): String? {
        val m = e.message.orEmpty()
        return when {
            m.startsWith("Auth fail", true) || m.startsWith("Auth cancel", true) || m.contains("USERAUTH fail", true) ->
                "Неверный логин или пароль"
            m.contains("invalid privatekey", true) || m.contains("invalid private key", true) ->
                "Неподдерживаемый или повреждённый приватный ключ"
            m.contains("timeout", true) || m.contains("timed out", true) -> "Превышено время ожидания ответа сервера"
            m.contains("UnknownHostException") -> "Хост не найден. Проверьте адрес и подключение к сети"
            m.contains("Connection refused", true) -> "Сервер отклонил подключение (порт закрыт?)"
            m.contains("Algorithm negotiation fail", true) -> "Сервер не поддерживает совместимые алгоритмы шифрования"
            m.contains("HostKey has been changed", true) -> "Ключ сервера изменился!"
            m.contains("session is down", true) || m.contains("connection is closed", true) -> "Соединение разорвано"
            m.isNotBlank() -> "Ошибка SSH: $m"
            else -> null
        }
    }

    private fun sftp(e: SftpException): String = when (e.id) {
        ChannelSftp.SSH_FX_NO_SUCH_FILE -> "Файл или папка не найдены"
        ChannelSftp.SSH_FX_PERMISSION_DENIED -> "Нет доступа"
        ChannelSftp.SSH_FX_NO_CONNECTION, ChannelSftp.SSH_FX_CONNECTION_LOST -> "Соединение разорвано"
        ChannelSftp.SSH_FX_OP_UNSUPPORTED -> "Операция не поддерживается сервером"
        ChannelSftp.SSH_FX_FAILURE -> "Сервер не смог выполнить операцию" + (e.message?.takeIf { it.isNotBlank() && it != "Failure" }?.let { ": $it" } ?: "")
        else -> e.message?.takeIf { it.isNotBlank() } ?: "Ошибка SFTP (${e.id})"
    }

    private fun smb(e: SMBApiException): String = when (e.status) {
        NtStatus.STATUS_LOGON_FAILURE, NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED -> "Неверный логин или пароль"
        NtStatus.STATUS_ACCOUNT_DISABLED -> "Учётная запись отключена"
        NtStatus.STATUS_PASSWORD_EXPIRED -> "Срок действия пароля истёк"
        NtStatus.STATUS_ACCESS_DENIED -> "Нет доступа"
        NtStatus.STATUS_BAD_NETWORK_NAME, NtStatus.STATUS_BAD_NETWORK_PATH -> "Общая папка (шара) не найдена"
        NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, NtStatus.STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.STATUS_NO_SUCH_FILE -> "Файл или папка не найдены"
        NtStatus.STATUS_OBJECT_NAME_COLLISION -> "Объект с таким именем уже существует"
        NtStatus.STATUS_DIRECTORY_NOT_EMPTY -> "Папка не пуста"
        NtStatus.STATUS_SHARING_VIOLATION -> "Файл занят другим процессом"
        NtStatus.STATUS_NOT_A_DIRECTORY -> "Это не папка"
        NtStatus.STATUS_OBJECT_NAME_INVALID -> "Недопустимое имя"
        NtStatus.STATUS_USER_SESSION_DELETED, NtStatus.STATUS_NETWORK_NAME_DELETED, NtStatus.STATUS_NETWORK_SESSION_EXPIRED -> "Сеанс завершён сервером"
        else -> "Ошибка SMB: ${e.status?.name ?: e.statusCode.toString(16)}"
    }
}
