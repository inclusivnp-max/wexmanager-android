package client.wexmanager.core.network

import android.content.Context
import android.util.Base64
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Отпечатки ключей серверов (TOFU): filesDir/known_hosts.
 * Строка: `<хост> <тип> <base64>`. Для SSH хранится сам ключ, для FTPS — SHA-256 сертификата.
 */
object KnownHosts {
    private data class Entry(val host: String, val type: String, val key: String)

    sealed interface Verdict {
        data object Known : Verdict
        data object Added : Verdict
        data class Changed(val oldKey: String, val oldType: String) : Verdict
    }

    private var entries: MutableList<Entry>? = null

    private fun file(context: Context) = File(context.applicationContext.filesDir, "known_hosts")

    private fun load(context: Context): MutableList<Entry> {
        entries?.let { return it }
        val list = runCatching {
            file(context).takeIf { it.exists() }?.readLines().orEmpty().mapNotNull { line ->
                val p = line.trim().split(' ')
                if (p.size >= 3) Entry(p[0], p[1], p[2]) else null
            }
        }.getOrDefault(emptyList()).toMutableList()
        entries = list
        return list
    }

    private fun save(context: Context, list: List<Entry>) {
        runCatching {
            val f = file(context)
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(list.joinToString("\n") { "${it.host} ${it.type} ${it.key}" })
            if (!tmp.renameTo(f)) {
                f.delete()
                tmp.renameTo(f)
            }
        }
    }

    /**
     * Проверяет ключ; ключ совсем нового хоста — запоминает.
     * Если для хоста уже известен ключ другого типа, это тоже [Verdict.Changed]:
     * иначе подмену можно провести, просто предложив другой алгоритм.
     */
    fun verify(context: Context, host: String, type: String, key: String): Verdict = synchronized(this) {
        val list = load(context)
        val known = list.filter { it.host == host }
        val same = known.firstOrNull { it.type == type }
        when {
            known.isEmpty() -> {
                list += Entry(host, type, key)
                save(context, list)
                Verdict.Added
            }
            same == null -> known.first().let { Verdict.Changed(it.key, it.type) }
            same.key == key -> Verdict.Known
            else -> Verdict.Changed(same.key, same.type)
        }
    }

    /** Пользователь доверился новому ключу: все прежние записи хоста (любых типов) заменяются. */
    fun accept(context: Context, e: HostKeyChangedException) = synchronized(this) {
        val list = load(context)
        list.removeAll { it.host == e.hostId }
        list += Entry(e.hostId, e.keyType, e.newKey)
        save(context, list)
    }

    fun forget(context: Context, hostPrefix: String) = synchronized(this) {
        val list = load(context)
        if (list.removeAll { it.host == hostPrefix }) save(context, list)
    }

    /** Удаляет все записи одного хоста и записывает файл так же, как [save]. */
    fun removeHost(context: Context, host: String) = synchronized(this) {
        val list = load(context)
        if (list.removeAll { it.host == host }) save(context, list)
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    /** Отпечаток в стиле OpenSSH: `SHA256:…`. */
    fun sshFingerprint(base64Key: String): String = runCatching {
        "SHA256:" + Base64.encodeToString(sha256(Base64.decode(base64Key, Base64.NO_WRAP)), Base64.NO_WRAP or Base64.NO_PADDING)
    }.getOrDefault(base64Key.take(24))

    private fun hexFingerprint(base64Digest: String): String = runCatching {
        Base64.decode(base64Digest, Base64.NO_WRAP).joinToString(":") { "%02X".format(it) }
    }.getOrDefault(base64Digest)

    /** Тип ключа из SSH-блоба: первая строка в формате «uint32 длина + байты». */
    private fun sshKeyType(blob: ByteArray): String = runCatching {
        val len = ((blob[0].toInt() and 0xFF) shl 24) or ((blob[1].toInt() and 0xFF) shl 16) or
            ((blob[2].toInt() and 0xFF) shl 8) or (blob[3].toInt() and 0xFF)
        String(blob, 4, len, Charsets.US_ASCII)
    }.getOrDefault("ssh")

    /** Репозиторий для JSch: неизвестные ключи принимает и запоминает, изменённые — отклоняет. */
    internal class SshRepository(context: Context) : HostKeyRepository {
        private val ctx = context.applicationContext

        @Volatile
        var changed: HostKeyChangedException? = null
            private set

        override fun check(host: String, key: ByteArray): Int {
            val type = sshKeyType(key)
            val b64 = Base64.encodeToString(key, Base64.NO_WRAP)
            return when (val v = verify(ctx, host, type, b64)) {
                Verdict.Known, Verdict.Added -> HostKeyRepository.OK
                is Verdict.Changed -> {
                    // Сменился тип ключа — показываем его, иначе отпечатки непонятно сравнивать.
                    val typed = v.oldType != type
                    val old = sshFingerprint(v.oldKey).let { if (typed) "${v.oldType} $it" else it }
                    val new = sshFingerprint(b64).let { if (typed) "$type $it" else it }
                    changed = HostKeyChangedException(host, type, old, new, b64)
                    HostKeyRepository.CHANGED
                }
            }
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
        override fun remove(host: String?, type: String?) = Unit
        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
        override fun getKnownHostsRepositoryID(): String = "wex_known_hosts"
        override fun getHostKey(): Array<HostKey> = getHostKey(null, null)

        /** Отдаём известные ключи — JSch предпочтёт уже знакомый алгоритм. */
        override fun getHostKey(host: String?, type: String?): Array<HostKey> = synchronized(KnownHosts) {
            load(ctx)
                .filter { (host == null || it.host == host) && (type == null || it.type == type) && !it.host.startsWith("ftps:") }
                .mapNotNull { e -> runCatching { HostKey(e.host, HostKey.GUESS, Base64.decode(e.key, Base64.NO_WRAP)) }.getOrNull() }
                .toTypedArray()
        }
    }

    /**
     * Доверие к сертификату FTPS: сначала системные CA (тогда имя хоста проверяет [hostnameVerifier]);
     * самоподписанный — по отпечатку (TOFU), имя хоста для него не проверяется.
     */
    internal class TofuTrustManager(context: Context, host: String, port: Int) : X509TrustManager {
        private val ctx = context.applicationContext
        private val hostId = "ftps:[$host]:$port"
        private val system: X509TrustManager? = runCatching {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        }.getOrNull()

        @Volatile
        var changed: HostKeyChangedException? = null
            private set

        /** Последний сертификат принят по сохранённому отпечатку, а не системными CA. */
        @Volatile
        var pinnedByTofu: Boolean = false
            private set

        /** Имя хоста проверяется только для сертификатов, принятых по цепочке системных CA. */
        val hostnameVerifier: HostnameVerifier = run {
            val default = HttpsURLConnection.getDefaultHostnameVerifier()
            HostnameVerifier { hostname, session -> pinnedByTofu || default.verify(hostname, session) }
        }

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
            pinnedByTofu = false
            val sys = system
            val trustedBySystem = sys != null && runCatching { sys.checkServerTrusted(chain, authType) }.isSuccess
            if (trustedBySystem) return
            val leaf = chain.firstOrNull() ?: throw CertificateException("Пустая цепочка сертификатов")
            val digest = Base64.encodeToString(sha256(leaf.encoded), Base64.NO_WRAP)
            when (val v = verify(ctx, hostId, "x509", digest)) {
                Verdict.Known, Verdict.Added -> pinnedByTofu = true
                is Verdict.Changed -> {
                    val e = HostKeyChangedException(hostId, "x509", hexFingerprint(v.oldKey), hexFingerprint(digest), digest)
                    changed = e
                    throw CertificateException("Сертификат сервера изменился", e)
                }
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = system?.acceptedIssuers ?: emptyArray()
    }
}
