package client.wexmanager.core.network

import android.content.Context
import client.wexmanager.core.fs.FileItem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchChangedHostKeyException
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import com.jcraft.jsch.UIKeyboardInteractive
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/** SFTP через JSch. Один канал на сессию — вызовы сериализуются пулом/очередью. */
class SftpFs private constructor(
    private val session: Session,
    private val channel: ChannelSftp,
    override val home: String,
) : RemoteFileSystem {

    override val isAlive: Boolean get() = session.isConnected && channel.isConnected && !channel.isClosed

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val entries = ArrayList<ChannelSftp.LsEntry>()
        val ctx = currentCoroutineContext()
        channel.ls(path) { entry ->
            if (!ctx.isActive) return@ls ChannelSftp.LsEntrySelector.BREAK
            // «..», «a/b» и т.п. от сервера отбрасываем — иначе выход за пределы папки при скачивании.
            if (RemotePath.isSafeName(entry.filename)) entries += entry
            ChannelSftp.LsEntrySelector.CONTINUE
        }
        ctx.ensureActive()
        // Для ссылок узнаём тип цели уже после листинга: канал не допускает вложенных запросов.
        entries.map { e ->
            val full = RemotePath.join(path, e.filename)
            if (e.attrs.isLink) {
                ctx.ensureActive()
                val target = runCatching { channel.readlink(full) }.getOrNull()
                val attrs = runCatching { channel.stat(full) }.getOrNull() ?: e.attrs
                item(full, e.filename, attrs, isLink = true, target)
            } else {
                item(full, e.filename, e.attrs, isLink = false, null)
            }
        }.distinctBy { it.path }
    }

    override suspend fun stat(path: String): FileItem? = withContext(Dispatchers.IO) {
        try {
            item(path, RemotePath.name(path), channel.stat(path), isLink = false, null)
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) null else throw e
        }
    }

    private fun item(path: String, name: String, a: SftpATTRS, isLink: Boolean, target: String?) = FileItem(
        path = path,
        name = name,
        isDirectory = a.isDir,
        size = if (a.isDir) 0 else a.size,
        lastModified = a.mTime.toLong() * 1000L,
        isSymlink = isLink,
        linkTarget = target,
        mode = -1,
        archivePath = null,
    )

    override suspend fun download(remote: String, local: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val total = runCatching { channel.stat(remote).size }.getOrDefault(-1L)
        channel.get(remote).use { input ->
            local.outputStream().buffered(256 * 1024).use { out -> copyStream(input, out, total, onProgress) }
        }
        Unit
    }

    override suspend fun upload(local: File, remoteDir: String, onProgress: (Long, Long) -> Unit) {
        local.inputStream().use { input -> uploadStream(input, local.length(), RemotePath.join(remoteDir, local.name), onProgress) }
    }

    override suspend fun uploadStream(input: InputStream, size: Long, remotePath: String, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            // Ссылку не затираем обычным файлом — заменяем то, на что она указывает.
            val target = resolveLink(remotePath)
            // Пишем во временный файл: обрыв не оставит на сервере обрезанный оригинал.
            val part = RemotePath.partPath(target)
            var skipCleanup = false
            try {
                channel.put(part, ChannelSftp.OVERWRITE).use { out -> copyStream(input, out, size, onProgress) }
                val existing = lstatOrNull(target)
                // Сохраняем права заменяемого файла (например, бит исполнения у скрипта).
                if (existing != null && existing.isReg) {
                    runCatching { channel.chmod(existing.permissions and 0xFFF, part) }
                }
                try {
                    // JSch сам использует posix-rename@openssh.com (замена поверх), если сервер умеет.
                    channel.rename(part, target)
                } catch (e: SftpException) {
                    if (existing == null || existing.isDir || e.isLost()) throw e
                    // SFTP v3 не заменяет существующий файл: удаляем и переименовываем.
                    channel.rm(target)
                    try {
                        channel.rename(part, target)
                    } catch (e2: SftpException) {
                        // Оригинал уже удалён — новую версию не трогаем.
                        skipCleanup = true
                        throw RemoteException("Не удалось заменить файл, новая версия сохранена как «${RemotePath.name(part)}»", e2)
                    }
                }
                skipCleanup = true
            } finally {
                if (!skipCleanup && isAlive) withContext(NonCancellable) { runCatching { channel.rm(part) } }
            }
            Unit
        }

    private fun SftpException.isLost() = id == ChannelSftp.SSH_FX_CONNECTION_LOST || id == ChannelSftp.SSH_FX_NO_CONNECTION

    private fun lstatOrNull(path: String): SftpATTRS? = try {
        channel.lstat(path)
    } catch (e: SftpException) {
        if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e
        null
    }

    private fun resolveLink(path: String): String {
        val attrs = lstatOrNull(path) ?: return path
        if (!attrs.isLink) return path
        return runCatching { channel.realpath(path) }.getOrNull()?.takeIf { it.startsWith("/") } ?: path
    }

    override suspend fun delete(path: String, isDir: Boolean) = withContext(Dispatchers.IO) {
        if (isDir) deleteTree(path) else channel.rm(path)
    }

    private suspend fun deleteTree(path: String) {
        val children = ArrayList<Pair<String, Boolean>>()
        channel.ls(path) { e ->
            if (e.filename != "." && e.filename != "..") {
                // Ссылки на папки удаляем как файлы — не заходим внутрь цели.
                children += RemotePath.join(path, e.filename) to (e.attrs.isDir && !e.attrs.isLink)
            }
            ChannelSftp.LsEntrySelector.CONTINUE
        }
        for ((p, dir) in children) {
            currentCoroutineContext().ensureActive()
            if (dir) deleteTree(p) else channel.rm(p)
        }
        channel.rmdir(path)
    }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) { channel.rename(from, to) }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        try {
            channel.mkdir(path)
        } catch (e: SftpException) {
            // Уже существует как папка — не ошибка (нужно для рекурсивной загрузки).
            val exists = runCatching { channel.stat(path).isDir }.getOrDefault(false)
            if (!exists) throw e
        }
    }

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }

    companion object {
        internal fun connect(context: Context, conn: RemoteConnection, password: String): SftpFs {
            val jsch = JSch()
            val keyPath = conn.privateKeyPath?.takeIf { it.isNotBlank() }
            if (keyPath != null) {
                if (!File(keyPath).exists()) throw RemoteException("Файл приватного ключа не найден — выберите его заново")
                try {
                    if (password.isNotEmpty()) jsch.addIdentity(keyPath, password.toByteArray()) else jsch.addIdentity(keyPath)
                } catch (e: JSchException) {
                    throw RemoteException("Не удалось прочитать приватный ключ: ${e.message ?: "неверный формат"}", e)
                }
            }
            val repo = KnownHosts.SshRepository(context)
            val session = jsch.getSession(conn.username.trim(), conn.host.trim(), conn.port)
            session.setHostKeyRepository(repo)
            // Неизвестные ключи принимает наш репозиторий (TOFU), изменённые — отклоняются.
            session.setConfig("StrictHostKeyChecking", "yes")
            session.setConfig(
                "PreferredAuthentications",
                if (keyPath != null) "publickey,keyboard-interactive,password" else "keyboard-interactive,password,publickey",
            )
            session.setConfig("MaxAuthTries", "3")
            if (password.isNotEmpty()) session.setPassword(password.toByteArray())
            session.setUserInfo(PasswordUserInfo(password))
            try {
                session.connect(RemoteFs.CONNECT_TIMEOUT_MS)
                // Пинг каждые 15 с; три пропуска — соединение считается мёртвым.
                session.setServerAliveInterval(15_000)
                session.setServerAliveCountMax(3)
                val channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(RemoteFs.CONNECT_TIMEOUT_MS)
                runCatching { channel.setFilenameEncoding(java.nio.charset.Charset.forName(conn.encoding)) }
                val home = runCatching { channel.home }.getOrNull()?.takeIf { it.isNotBlank() } ?: "/"
                return SftpFs(session, channel, RemotePath.normalize(home))
            } catch (e: JSchChangedHostKeyException) {
                session.disconnect()
                throw repo.changed ?: e
            } catch (e: Throwable) {
                session.disconnect()
                repo.changed?.let { throw it }
                throw e
            }
        }
    }

    /** Ответы для keyboard-interactive: пароль отдаём один раз, без бесконечных повторов. */
    private class PasswordUserInfo(private val password: String) : UserInfo, UIKeyboardInteractive {
        private var answered = 0
        override fun getPassphrase(): String? = null
        override fun getPassword(): String? = null
        override fun promptPassword(message: String?): Boolean = false
        override fun promptPassphrase(message: String?): Boolean = false
        override fun promptYesNo(message: String?): Boolean = false
        override fun showMessage(message: String?) = Unit
        override fun promptKeyboardInteractive(
            destination: String?,
            name: String?,
            instruction: String?,
            prompt: Array<out String>?,
            echo: BooleanArray?,
        ): Array<String>? {
            if (prompt == null) return null
            if (prompt.isEmpty()) return emptyArray()
            if (password.isEmpty() || answered++ > 0) return null
            return Array(prompt.size) { password }
        }
    }
}
