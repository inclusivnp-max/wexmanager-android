package client.wexmanager.core.apk

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Подпись APK (схемы v1 + v2 + v3). По умолчанию — собственным ключом приложения:
 * он создаётся один раз в Android Keystore и не покидает устройство, так подписанные
 * WexManager APK можно обновлять поверх друг друга. Свои ключи — см. [SigningKeys].
 */
object ApkSigning {
    private const val ALIAS = "wex_apk_signer"

    @Synchronized
    private fun builtinKey(ks: KeyStore) {
        if (ks.containsAlias(ALIAS)) return
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512, KeyProperties.DIGEST_SHA1)
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
            .setCertificateSubject(X500Principal("CN=WexManager, O=WexManager"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - 86_400_000L))
            .setCertificateNotAfter(Date(now + 30L * 365 * 86_400_000L))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore").apply { initialize(spec) }.generateKeyPair()
    }

    /** Ключ и цепочка сертификатов: [alias] = null — встроенный ключ WexManager. */
    private fun key(alias: String?): Pair<PrivateKey, List<X509Certificate>> {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val a = alias ?: ALIAS.also { builtinKey(ks) }
        val entry = ks.getEntry(a, null) as? KeyStore.PrivateKeyEntry
            ?: throw SigningKeyException("Ключ подписи не найден на устройстве — выберите другой в настройках")
        return entry.privateKey to entry.certificateChain.map { it as X509Certificate }
    }

    fun certificate(alias: String?): X509Certificate = key(alias).second.first()

    /** Подписывает [input] и пишет результат в [output] (выравнивание выполняет apksig). */
    suspend fun sign(input: File, output: File, alias: String? = null) = withContext(Dispatchers.IO) {
        val (privateKey, chain) = key(alias)
        val signer = ApkSigner.SignerConfig.Builder("WEXKEY", privateKey, chain).build()
        // Уникальное временное имя: две подписи одного APK не должны писать в один файл.
        val tmp = File(output.parentFile, ".wexsign-${java.util.UUID.randomUUID().toString().take(12)}.tmp")
        try {
            ApkSigner.Builder(listOf(signer))
                .setInputApk(input)
                .setOutputApk(tmp)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(true)
                .setCreatedBy("WexManager")
                .build()
                .sign()
            kotlinx.coroutines.currentCoroutineContext().ensureActive() // отменили во время подписи — результат не публикуем
            if (!tmp.renameTo(output)) throw java.io.IOException("Не удалось сохранить подписанный APK")
        } catch (e: java.security.InvalidKeyException) {
            tmp.delete()
            throw SigningKeyException(
                if (e.message?.contains("minSdk", true) == true) "Ключ EC подходит только для APK с minSdkVersion 18 и выше — выберите RSA-ключ"
                else "Ключ не подходит для подписи: ${e.message}",
            )
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }
}
