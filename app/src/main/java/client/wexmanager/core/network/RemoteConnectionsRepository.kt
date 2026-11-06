package client.wexmanager.core.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import client.wexmanager.core.settings.jsonDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Шифрование паролей AES/GCM ключом из Android Keystore. Формат: base64(iv(12) + шифротекст). */
internal object PasswordCipher {
    private const val ALIAS = "wex_remote"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12

    @Volatile
    private var cached: SecretKey? = null

    private fun key(): SecretKey {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: run {
                val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                val existing = ks.getKey(ALIAS, null) as? SecretKey
                existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                    init(
                        KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .build(),
                    )
                    generateKey()
                }
            }.also { cached = it }
        }
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val data = cipher.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(iv + data, Base64.NO_WRAP)
    }

    /** null — данные повреждены или ключ утерян (например, после переноса на другое устройство). */
    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
        cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE).decodeToString()
    }.getOrNull()
}

/** Список сетевых подключений: filesDir/datastore/connections.json. */
class RemoteConnectionsRepository internal constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = jsonDataStore(appContext, "connections.json", ConnectionsData(), ConnectionsData.serializer(), scope)

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val connections: StateFlow<List<RemoteConnection>> = store.data
        .map { data -> data.connections.sortedBy { it.createdAt } }
        .onEach { _loaded.value = true }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val keysDir: File get() = File(appContext.filesDir, "keys")

    suspend fun get(id: String): RemoteConnection? = store.data.first().connections.firstOrNull { it.id == id }

    suspend fun upsert(connection: RemoteConnection) {
        store.updateData { data ->
            val list = data.connections.toMutableList()
            val i = list.indexOfFirst { it.id == connection.id }
            if (i >= 0) list[i] = connection else list += connection
            data.copy(connections = list)
        }
        // Параметры изменились — открытое соединение больше не годится.
        RemoteSessions.invalidate(connection.id)
    }

    suspend fun duplicate(id: String): RemoteConnection? {
        val src = get(id) ?: return null
        val copy = src.copy(
            id = UUID.randomUUID().toString(),
            name = src.name + " копия",
            createdAt = System.currentTimeMillis(),
        )
        store.updateData { it.copy(connections = it.connections + copy) }
        return copy
    }

    suspend fun delete(id: String) {
        var removed: RemoteConnection? = null
        val after = store.updateData { data ->
            removed = data.connections.firstOrNull { it.id == id }
            data.copy(connections = data.connections.filterNot { it.id == id })
        }
        RemoteSessions.invalidate(id)
        // Ключ удаляем, только если на него больше никто не ссылается.
        val key = removed?.privateKeyPath
        if (key != null && after.connections.none { it.privateKeyPath == key }) deleteKeyFile(key)
        withContext(Dispatchers.IO) { File(appContext.cacheDir, "remote/$id").deleteRecursively() }
    }

    /** Удаляет файл ключа, если он лежит в нашей папке keys. */
    suspend fun deleteKeyFile(path: String) = withContext(Dispatchers.IO) {
        val f = File(path)
        if (f.parentFile?.canonicalPath == keysDir.canonicalPath) f.delete()
    }

    suspend fun isKeyReferenced(path: String): Boolean = store.data.first().connections.any { it.privateKeyPath == path }

    /** Фоновая очистка ключей, на которые не ссылается ни одно подключение. */
    fun deleteUnreferencedKeysAsync(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val list = paths.toList()
        scope.launch { list.forEach { if (!isKeyReferenced(it)) deleteKeyFile(it) } }
    }

    suspend fun encryptPassword(plain: String): String? =
        if (plain.isEmpty()) null else withContext(Dispatchers.IO) { PasswordCipher.encrypt(plain) }

    /** Сохранённый пароль: "" — не сохранён, null — не удалось расшифровать (ключ Keystore утерян). */
    suspend fun decryptPassword(connection: RemoteConnection): String? {
        val encrypted = connection.encryptedPassword ?: return ""
        return withContext(Dispatchers.IO) { PasswordCipher.decrypt(encrypted) }
    }

    /** Пароль для входа; утерянный — понятная ошибка вместо попытки входа с пустым паролем. */
    suspend fun passwordFor(connection: RemoteConnection): String {
        if (connection.usesAnonymous) return ""
        return decryptPassword(connection) ?: throw SavedPasswordLostException()
    }
}

object RemoteConnections {
    @Volatile
    private var instance: RemoteConnectionsRepository? = null

    fun get(context: Context): RemoteConnectionsRepository =
        instance ?: synchronized(this) {
            instance ?: RemoteConnectionsRepository(context.applicationContext).also { instance = it }
        }
}
