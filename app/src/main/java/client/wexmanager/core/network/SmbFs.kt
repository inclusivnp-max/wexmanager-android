package client.wexmanager.core.network

import client.wexmanager.core.fs.FileItem
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/** SMB 2/3 через smbj. Пути внутри шары: «/» — корень шары. */
class SmbFs private constructor(
    private val client: SMBClient,
    private val connection: Connection,
    private val session: Session,
    private val share: DiskShare,
) : RemoteFileSystem {

    override val home: String = "/"

    override val isAlive: Boolean get() = runCatching { connection.isConnected && share.isConnected }.getOrDefault(false)

    private fun smb(path: String): String = path.trim('/').replace('/', '\\')

    override suspend fun list(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        share.list(smb(path)).mapNotNull { info ->
            val name = info.fileName
            // «.», «..» и имена с разделителями от сервера отбрасываем — иначе выход за пределы папки.
            if (!RemotePath.isSafeName(name)) return@mapNotNull null
            val attrs = info.fileAttributes
            val dir = EnumWithValue.EnumUtils.isSet(attrs, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
            val hidden = EnumWithValue.EnumUtils.isSet(attrs, FileAttributes.FILE_ATTRIBUTE_HIDDEN)
            FileItem(
                path = RemotePath.join(path, name),
                name = name,
                isDirectory = dir,
                size = if (dir) 0 else info.endOfFile,
                lastModified = runCatching { info.lastWriteTime.toEpochMillis() }.getOrDefault(0L),
                isHidden = hidden || name.startsWith('.'),
                isSymlink = EnumWithValue.EnumUtils.isSet(attrs, FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT),
                mode = -1,
                archivePath = null,
            )
        }.distinctBy { it.path }
    }

    override suspend fun stat(path: String): FileItem? = withContext(Dispatchers.IO) {
        val p = smb(path)
        val info = try {
            share.getFileInformation(p)
        } catch (e: SMBApiException) {
            if (e.status in NotFound) return@withContext null
            throw e
        }
        val dir = info.standardInformation.isDirectory
        FileItem(
            path = path,
            name = RemotePath.name(path),
            isDirectory = dir,
            size = if (dir) 0 else info.standardInformation.endOfFile,
            lastModified = runCatching { info.basicInformation.lastWriteTime.toEpochMillis() }.getOrDefault(0L),
            mode = -1,
            archivePath = null,
        )
    }

    override suspend fun download(remote: String, local: File, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        share.openFile(
            smb(remote),
            EnumSet.of(AccessMask.GENERIC_READ),
            EnumSet.noneOf(FileAttributes::class.java),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            EnumSet.noneOf(SMB2CreateOptions::class.java),
        ).use { f ->
            val total = runCatching { f.length }.getOrDefault(-1L)
            f.inputStream.use { input ->
                local.outputStream().buffered(256 * 1024).use { out -> copyStream(input, out, total, onProgress) }
            }
        }
        Unit
    }

    override suspend fun upload(local: File, remoteDir: String, onProgress: (Long, Long) -> Unit) {
        local.inputStream().use { input -> uploadStream(input, local.length(), RemotePath.join(remoteDir, local.name), onProgress) }
    }

    override suspend fun uploadStream(input: InputStream, size: Long, remotePath: String, onProgress: (Long, Long) -> Unit) =
        withContext(Dispatchers.IO) {
            // Пишем во временный файл: обрыв не оставит на сервере обрезанный оригинал.
            val part = RemotePath.partPath(remotePath)
            var done = false
            try {
                share.openFile(
                    smb(part),
                    EnumSet.of(AccessMask.GENERIC_WRITE),
                    EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    EnumSet.noneOf(SMB2CreateOptions::class.java),
                ).use { f ->
                    f.outputStream.use { out -> copyStream(input, out, size, onProgress) }
                }
                // Замена поверх существующего — одной операцией (replaceIfExists).
                openForRename(part).use { it.rename(smb(remotePath), true) }
                done = true
            } finally {
                if (!done && isAlive) withContext(NonCancellable) { runCatching { share.rm(smb(part)) } }
            }
            Unit
        }

    private fun openForRename(path: String) = share.open(
        smb(path),
        EnumSet.of(AccessMask.DELETE, AccessMask.FILE_READ_ATTRIBUTES),
        EnumSet.noneOf(FileAttributes::class.java),
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OPEN,
        EnumSet.noneOf(SMB2CreateOptions::class.java),
    )

    override suspend fun delete(path: String, isDir: Boolean) = withContext(Dispatchers.IO) {
        if (isDir) deleteTree(path) else share.rm(smb(path))
    }

    /** Свой обход вместо rmdir(recursive) — чтобы удаление можно было отменить. */
    private suspend fun deleteTree(path: String) {
        for (c in list(path)) {
            currentCoroutineContext().ensureActive()
            if (c.isDirectory && !c.isSymlink) deleteTree(c.path) else share.rm(smb(c.path))
        }
        share.rmdir(smb(path), false)
    }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        openForRename(from).use { it.rename(smb(to), false) }
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        try {
            share.mkdir(smb(path))
        } catch (e: SMBApiException) {
            if (e.status != NtStatus.STATUS_OBJECT_NAME_COLLISION) throw e
        }
    }

    override fun close() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }

    companion object {
        private val NotFound = setOf(
            NtStatus.STATUS_OBJECT_NAME_NOT_FOUND,
            NtStatus.STATUS_OBJECT_PATH_NOT_FOUND,
            NtStatus.STATUS_NO_SUCH_FILE,
        )

        private fun config() = SmbConfig.builder()
            .withSecurityProvider(BCSecurityProvider())
            .withSocketFactory(TimeoutSocketFactory(RemoteFs.CONNECT_TIMEOUT_MS))
            .withTimeout(RemoteFs.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .withMultiProtocolNegotiate(true)
            .withDfsEnabled(false)
            .build()

        /** Общие папки сервера (для выбора в редакторе подключения); шара в [conn] не нужна. */
        internal fun listShares(conn: RemoteConnection, password: String): List<SmbShareInfo> {
            val client = SMBClient(config())
            try {
                val connection = client.connect(conn.host.trim(), conn.port)
                val session = authenticate(connection, conn, password)
                return SmbShares.list(session, conn.host.trim()).sortedWith(compareBy({ !it.isDisk }, { it.name.lowercase() }))
            } finally {
                runCatching { client.close() }
            }
        }

        internal fun connect(conn: RemoteConnection, password: String): SmbFs {
            val shareName = conn.share?.trim()?.trim('/', '\\')?.takeIf { it.isNotEmpty() }
                ?: throw RemoteException("Не указана общая папка (шара)")
            val config = SmbConfig.builder()
                .withSecurityProvider(BCSecurityProvider())
                .withSocketFactory(TimeoutSocketFactory(RemoteFs.CONNECT_TIMEOUT_MS))
                .withTimeout(RemoteFs.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                .withMultiProtocolNegotiate(true)
                .withDfsEnabled(false)
                .build()
            val client = SMBClient(config)
            try {
                val connection = client.connect(conn.host.trim(), conn.port)
                val session = authenticate(connection, conn, password)
                val share = session.connectShare(shareName) as? DiskShare
                    ?: throw RemoteException("«$shareName» — не дисковый ресурс")
                return SmbFs(client, connection, session, share)
            } catch (e: Throwable) {
                runCatching { client.close() }
                throw e
            }
        }

        private fun authenticate(connection: Connection, conn: RemoteConnection, password: String): Session {
            if (conn.usesAnonymous) {
                // Анонимно, затем гостем — Samba и Windows ведут себя по-разному.
                return runCatching { connection.authenticate(AuthenticationContext.anonymous()) }
                    .getOrElse { connection.authenticate(AuthenticationContext.guest()) }
            }
            val raw = conn.username.trim()
            // Поддерживаем «ДОМЕН\пользователь» и «пользователь@домен».
            val (user, domain) = when {
                '\\' in raw -> raw.substringAfter('\\') to raw.substringBefore('\\')
                '@' in raw -> raw.substringBefore('@') to raw.substringAfter('@')
                else -> raw to ""
            }
            return connection.authenticate(AuthenticationContext(user, password.toCharArray(), domain))
        }
    }

    /** smbj не умеет таймаут подключения — даём ему фабрику сокетов с таймаутом. */
    private class TimeoutSocketFactory(private val timeoutMs: Int) : SocketFactory() {
        override fun createSocket(): Socket = Socket()

        override fun createSocket(host: String, port: Int): Socket =
            Socket().apply { connect(InetSocketAddress(host, port), timeoutMs) }

        override fun createSocket(host: String, port: Int, localHost: InetAddress?, localPort: Int): Socket =
            Socket().apply {
                bind(InetSocketAddress(localHost, localPort))
                connect(InetSocketAddress(host, port), timeoutMs)
            }

        override fun createSocket(host: InetAddress, port: Int): Socket =
            Socket().apply { connect(InetSocketAddress(host, port), timeoutMs) }

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress?, localPort: Int): Socket =
            Socket().apply {
                bind(InetSocketAddress(localAddress, localPort))
                connect(InetSocketAddress(address, port), timeoutMs)
            }
    }
}
