package client.wexmanager.core.network

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class RemoteType(val title: String, val defaultPort: Int, val description: String) {
    SFTP("SFTP", 22, "SSH File Transfer — серверы Linux, NAS, роутеры"),
    FTP("FTP", 21, "Классический FTP без шифрования"),
    FTPS("FTPS", 990, "FTP поверх TLS (порт 990 — неявный, иначе AUTH TLS)"),
    SMB("SMB", 445, "Общие папки Windows, Samba, NAS (SMB 2/3)"),
    WEBDAV("WebDAV", 443, "Nextcloud, ownCloud, Яндекс.Диск, NAS — по HTTPS"),
}

@Immutable
@Serializable
data class RemoteConnection(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: RemoteType = RemoteType.SFTP,
    val host: String = "",
    val port: Int = type.defaultPort,
    val username: String = "",
    /** base64(iv + шифротекст) — AES/GCM ключом из Android Keystore. */
    val encryptedPassword: String? = null,
    /** Копия приватного ключа в filesDir/keys (только SFTP). */
    val privateKeyPath: String? = null,
    val initialPath: String = "",
    /** Имя общего ресурса (только SMB). */
    val share: String? = null,
    val anonymous: Boolean = false,
    val passiveMode: Boolean = true,
    val encoding: String = "UTF-8",
    /** HTTPS (только WebDAV). */
    val useTls: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val title: String get() = name.ifBlank { host.ifBlank { type.title } }

    /** `user@host:port` (без пользователя для анонимного входа). */
    val address: String
        get() = buildString {
            if (!anonymous && username.isNotBlank()) append(username).append('@')
            append(host)
            append(':').append(port)
            if (type == RemoteType.SMB && !share.isNullOrBlank()) append('/').append(share.trim('/', '\\'))
            if (type == RemoteType.WEBDAV) { insert(0, if (useTls) "https://" else "http://"); append(initialPath.ifBlank { "/" }) }
        }

    val usesAnonymous: Boolean
        get() = when (type) {
            RemoteType.FTP, RemoteType.FTPS -> anonymous
            RemoteType.SMB -> anonymous || username.isBlank()
            RemoteType.WEBDAV -> username.isBlank()
            RemoteType.SFTP -> false
        }
}

@Serializable
data class ConnectionsData(val connections: List<RemoteConnection> = emptyList())

/** Ошибка с уже понятным пользователю текстом. */
open class RemoteException(message: String, cause: Throwable? = null) : Exception(message, cause)

class RemoteAuthException(message: String = "Неверный логин или пароль", cause: Throwable? = null) : RemoteException(message, cause)

/** Ключ Keystore утерян (например, после восстановления из резервной копии) — пароль нужно ввести заново. */
class SavedPasswordLostException : RemoteException("Сохранённый пароль не удалось расшифровать — введите его заново")

/** Ключ (или сертификат) сервера отличается от сохранённого — возможна атака «человек посередине». */
class HostKeyChangedException(
    val hostId: String,
    val keyType: String,
    val oldFingerprint: String,
    val newFingerprint: String,
    internal val newKey: String,
) : RemoteException("Ключ сервера изменился")

/** Пути на сервере — всегда в стиле POSIX: «/», «/a/b». */
object RemotePath {
    fun normalize(path: String): String {
        val parts = ArrayList<String>()
        path.replace('\\', '/').split('/').forEach { p ->
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += p
            }
        }
        return "/" + parts.joinToString("/")
    }

    fun join(dir: String, name: String): String = if (dir.endsWith('/')) dir + name else "$dir/$name"

    fun parent(path: String): String? {
        if (path == "/" || path.isEmpty()) return null
        return path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }
    }

    fun name(path: String): String = path.trimEnd('/').substringAfterLast('/').ifEmpty { "/" }

    fun segments(path: String): List<String> = path.split('/').filter { it.isNotEmpty() }

    /** Имя из листинга сервера, которое безопасно склеивать в пути (без «.», «..», разделителей и NUL). */
    fun isSafeName(name: String?): Boolean =
        !name.isNullOrEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\\' || it == '\u0000' }

    /** Временный файл для атомарной загрузки рядом с [path]: «.<имя>.wexpart». */
    fun partPath(path: String): String {
        val name = name(path)
        val part = ".$name.wexpart"
        // Слишком длинное имя (лимит ФС — 255 байт) заменяем хешем.
        val safe = if (part.encodeToByteArray().size <= 255) part else ".wex-${name.hashCode().toUInt().toString(16)}.wexpart"
        return join(parent(path) ?: "/", safe)
    }
}
