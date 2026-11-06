package client.wexmanager.core.apk

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import client.wexmanager.core.settings.SigningKeyMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECKey
import java.security.interfaces.RSAKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.UUID

/** Понятная пользователю ошибка импорта ключа. */
class SigningKeyException(message: String) : IOException(message)

/**
 * Ключи подписи APK. Импортированный ключ переносится в Android Keystore
 * (без возможности извлечь обратно), поэтому пароли от хранилищ нигде не сохраняются.
 */
object SigningKeys {
    const val BUILTIN_ALIAS = "wex_apk_signer"
    private const val PREFIX = "wex_sign_"

    data class Info(val subject: String, val sha256: String, val notAfter: Long, val algorithm: String)

    fun describe(cert: X509Certificate): Info {
        val key = cert.publicKey
        val bits = when (key) {
            is RSAKey -> " ${key.modulus.bitLength()}"
            is ECKey -> " ${key.params.curve.field.fieldSize}"
            else -> ""
        }
        return Info(
            subject = cert.subjectX500Principal.name,
            sha256 = fingerprint(cert),
            notAfter = cert.notAfter.time,
            algorithm = key.algorithm + bits,
        )
    }

    fun fingerprint(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString(":") { "%02X".format(it) }

    /** Сертификат в PEM — чтобы поделиться им или сверить отпечаток. */
    fun toPem(cert: X509Certificate): String = buildString {
        append("-----BEGIN CERTIFICATE-----\n")
        Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(cert.encoded).let(::append)
        append("\n-----END CERTIFICATE-----\n")
    }

    suspend fun builtinInfo(): Info = withContext(Dispatchers.IO) { describe(ApkSigning.certificate(null)) }

    private fun readUri(context: Context, uri: Uri, limit: Int = 1 shl 20): ByteArray {
        val input = context.contentResolver.openInputStream(uri) ?: throw SigningKeyException("Не удалось открыть файл")
        return input.use { stream ->
            val bytes = stream.readNBytes(limit + 1)
            if (bytes.size > limit) throw SigningKeyException("Файл слишком большой для ключа")
            bytes
        }
    }

    /** Содержимое файла хранилища — читается один раз, а не на каждую проверку пароля. */
    suspend fun readStore(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) { readUri(context, uri) }

    /** Псевдонимы ключей в хранилище (для выбора, если их несколько). */
    suspend fun aliases(bytes: ByteArray, storePassword: String): List<String> = withContext(Dispatchers.IO) {
        val ks = openStore(bytes, storePassword)
        ks.aliases().toList().filter { ks.isKeyEntry(it) }
    }

    private fun openStore(bytes: ByteArray, password: String): KeyStore {
        if (bytes.size >= 4) {
            val magic = ((bytes[0].toInt() and 0xFF) shl 24) or ((bytes[1].toInt() and 0xFF) shl 16) or
                ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
            if (magic == 0xFEEDFEED.toInt() || magic == 0xCECECECE.toInt()) {
                throw SigningKeyException(
                    "Формат JKS/JCEKS Android не читает. Преобразуйте в PKCS12:\n" +
                        "keytool -importkeystore -srckeystore key.jks -destkeystore key.p12 -deststoretype pkcs12",
                )
            }
        }
        val errors = ArrayList<Throwable>()
        for (type in listOf("PKCS12", "BKS")) {
            try {
                return KeyStore.getInstance(type).apply { load(ByteArrayInputStream(bytes), password.toCharArray()) }
            } catch (e: Exception) {
                errors += e
            }
        }
        val chain = errors.flatMap { e -> generateSequence(e) { it.cause }.toList() }
        // Пароль хранилища подошёл, но ключ зашифрован другим паролем — Android такие PKCS12 не читает.
        if (chain.any { it.message?.contains("unwrapping", true) == true }) {
            throw SigningKeyException(
                "У ключа свой пароль, отличный от пароля хранилища, — Android так не умеет. Пересохраните хранилище:\n" +
                    "keytool -importkeystore -srckeystore key.p12 -destkeystore key2.p12 -deststoretype pkcs12 -destkeypass <пароль хранилища>",
            )
        }
        // Смотрим ошибки всех форматов: PKCS12 сообщает о неверном пароле, а BKS — лишь «не тот формат».
        val wrong = chain.any {
            it is UnrecoverableKeyException || it.message?.contains("password", true) == true ||
                it.message?.contains("mac", true) == true || it.message?.contains("integrity", true) == true
        }
        throw SigningKeyException(if (wrong) "Неверный пароль хранилища" else "Не удалось прочитать хранилище ключей (поддерживаются PKCS12 и BKS)")
    }

    /** Импорт из PKCS12/BKS. [alias] = null — первый ключ в хранилище; [keyPassword] = null — как у хранилища. */
    suspend fun importKeyStore(
        bytes: ByteArray,
        name: String,
        storePassword: String,
        alias: String?,
        keyPassword: String?,
    ): SigningKeyMeta = withContext(Dispatchers.IO) {
        val ks = openStore(bytes, storePassword)
        val keyAliases = ks.aliases().toList().filter { ks.isKeyEntry(it) }
        if (keyAliases.isEmpty()) throw SigningKeyException("В хранилище нет закрытых ключей")
        val chosen = alias?.takeIf { it.isNotBlank() }?.let { a ->
            keyAliases.firstOrNull { it.equals(a, ignoreCase = true) }
                ?: throw SigningKeyException("Ключ «$a» не найден. Есть: ${keyAliases.joinToString()}")
        } ?: keyAliases.first()
        val key = try {
            ks.getKey(chosen, (keyPassword?.takeIf { it.isNotEmpty() } ?: storePassword).toCharArray()) as? PrivateKey
        } catch (e: UnrecoverableKeyException) {
            throw SigningKeyException("Неверный пароль ключа «$chosen»")
        } ?: throw SigningKeyException("«$chosen» — не закрытый ключ")
        val chain = ks.getCertificateChain(chosen)?.map { it as X509Certificate }
            ?: throw SigningKeyException("У ключа «$chosen» нет сертификата")
        store(name, key, chain)
    }

    /** Импорт пары «ключ PKCS#8 (.pk8) + сертификат (.x509.pem / .der)», как testkey в AOSP. */
    suspend fun importPk8(context: Context, keyUri: Uri, certUri: Uri, name: String): SigningKeyMeta = withContext(Dispatchers.IO) {
        val certs = try {
            CertificateFactory.getInstance("X.509").generateCertificates(ByteArrayInputStream(readUri(context, certUri)))
                .map { it as X509Certificate }
        } catch (e: Exception) {
            throw SigningKeyException("Файл сертификата не распознан (нужен X.509 в PEM или DER)")
        }
        if (certs.isEmpty()) throw SigningKeyException("В файле нет сертификата")
        val raw = readUri(context, keyUri)
        val der = pemBody(raw) ?: raw
        val algorithm = certs.first().publicKey.algorithm
        val key = try {
            KeyFactory.getInstance(algorithm).generatePrivate(PKCS8EncodedKeySpec(der))
        } catch (e: Exception) {
            throw SigningKeyException("Ключ не распознан: нужен незашифрованный PKCS#8 ($algorithm)")
        }
        store(name, key, certs)
    }

    private fun pemBody(bytes: ByteArray): ByteArray? {
        val text = runCatching { String(bytes, Charsets.US_ASCII) }.getOrNull() ?: return null
        if (!text.contains("-----BEGIN")) return null
        if (text.contains("ENCRYPTED")) throw SigningKeyException("Зашифрованный PEM-ключ не поддерживается — снимите пароль (openssl pkcs8 -nocrypt)")
        val body = text.substringAfter("-----", "").substringAfter("\n").substringBefore("-----END").filterNot { it.isWhitespace() }
        return runCatching { Base64.getDecoder().decode(body) }.getOrNull()
    }

    private fun store(name: String, key: PrivateKey, chain: List<X509Certificate>): SigningKeyMeta {
        val leaf = chain.first()
        // Пробная подпись: пара «ключ ↔ сертификат» должна совпадать, иначе apksig упадёт при каждой подписи.
        val sigAlg = when (key.algorithm.uppercase()) {
            "RSA" -> "SHA256withRSA"
            "EC", "ECDSA" -> "SHA256withECDSA"
            else -> throw SigningKeyException("Алгоритм ключа ${key.algorithm} не поддерживается (нужен RSA или EC)")
        }
        val probe = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val matches = runCatching {
            val signature = java.security.Signature.getInstance(sigAlg).run { initSign(key); update(probe); sign() }
            java.security.Signature.getInstance(sigAlg).run { initVerify(leaf.publicKey); update(probe); verify(signature) }
        }.getOrDefault(false)
        if (!matches) throw SigningKeyException("Ключ не подходит к сертификату")
        val alias = PREFIX + UUID.randomUUID().toString().replace("-", "").take(16)
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            .apply { if (key.algorithm == "RSA") setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1) }
            .build()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .setEntry(alias, KeyStore.PrivateKeyEntry(key, chain.toTypedArray()), protection)
        } catch (e: Exception) {
            throw SigningKeyException("Хранилище Android отклонило ключ (${key.algorithm}): ${e.message ?: e.javaClass.simpleName}")
        }
        val info = describe(leaf)
        return SigningKeyMeta(
            alias = alias,
            name = name.ifBlank { info.subject.substringAfter("CN=").substringBefore(',') },
            subject = info.subject,
            sha256 = info.sha256,
            notAfter = info.notAfter,
            algorithm = info.algorithm,
        )
    }

    fun delete(alias: String) {
        if (!alias.startsWith(PREFIX)) return
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }

    /** Есть ли ключ в Keystore (после восстановления на другом устройстве его не будет). */
    fun exists(alias: String): Boolean =
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias(alias) }.getOrDefault(false)
}
