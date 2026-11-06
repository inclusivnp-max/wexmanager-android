package client.wexmanager.core.icons

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import client.wexmanager.core.settings.AppJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Иконпак — zip-архив с `pack.json` и картинками (png/webp/svg):
 * ```
 * { "name": "My pack", "author": "me", "icons": { "apk": "apk.png", "folder": "folder.png", "*": "file.png" } }
 * ```
 * Если `icons` не указан, имя файла без расширения считается расширением (`pdf.png` → `.pdf`).
 */
@Immutable
@Serializable
data class IconPackManifest(
    val name: String = "Без названия",
    val author: String = "",
    val version: String = "",
    val description: String = "",
    val icons: Map<String, String> = emptyMap(),
)

@Immutable
data class IconPack(
    val id: String,
    val manifest: IconPackManifest,
    val dir: File,
    val icons: Map<String, File>,
) {
    fun resolve(key: String): File? = icons[key]
}

class IconPackRepository(context: Context, private val scope: CoroutineScope) {
    private val root = File(context.filesDir, "iconpacks").apply { mkdirs() }
    val customIconsDir = File(context.filesDir, "custom_icons").apply { mkdirs() }
    private val resolver = context.contentResolver
    private val cacheDir = context.cacheDir

    private val _packs = MutableStateFlow<List<IconPack>>(emptyList())
    val packs: StateFlow<List<IconPack>> = _packs.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { reload() }
    }

    private fun reload() {
        _packs.value = root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.mapNotNull { load(it) }?.sortedBy { it.manifest.name } ?: emptyList()
    }

    private fun load(dir: File): IconPack? {
        val manifestFile = File(dir, "pack.json")
        val manifest = if (manifestFile.exists()) {
            runCatching { AppJson.decodeFromString(IconPackManifest.serializer(), manifestFile.readText()) }.getOrNull()
                ?: return null
        } else IconPackManifest(name = dir.name)
        val icons = HashMap<String, File>()
        if (manifest.icons.isNotEmpty()) {
            manifest.icons.forEach { (key, file) ->
                val f = File(dir, file)
                if (f.exists()) icons[key.lowercase().removePrefix(".")] = f
            }
        } else {
            dir.walkTopDown().filter { it.isFile && it.extension.lowercase() in IMAGE_EXT }.forEach {
                icons[it.nameWithoutExtension.lowercase()] = it
            }
        }
        if (icons.isEmpty()) return null
        return IconPack(dir.name, manifest, dir, icons)
    }

    suspend fun importFromUri(uri: Uri): IconPack = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw IOException("Не удалось открыть файл")
        input.use { importZip(it) }
    }

    suspend fun importFromFile(file: File): IconPack = withContext(Dispatchers.IO) {
        file.inputStream().use { importZip(it) }
    }

    suspend fun downloadAndImport(url: String, onProgress: (Float) -> Unit = {}): IconPack = withContext(Dispatchers.IO) {
        val tmp = File(cacheDir, "iconpack_${System.currentTimeMillis()}.zip")
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode !in 200..299) throw IOException("Сервер вернул ${conn.responseCode}")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 15)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            tmp.inputStream().use { importZip(it) }
        } finally {
            tmp.delete()
        }
    }

    private fun importZip(input: InputStream): IconPack {
        val id = UUID.randomUUID().toString().take(8)
        val dir = File(root, id).apply { mkdirs() }
        val canonical = dir.canonicalPath
        try {
            ZipInputStream(input.buffered()).use { zis ->
                var total = 0L
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/').trimStart('/')
                    val out = File(dir, name)
                    if (!out.canonicalPath.startsWith(canonical.trimEnd('/') + "/")) continue
                    val ext = out.extension.lowercase()
                    if (ext !in IMAGE_EXT && out.name != "pack.json") continue
                    out.parentFile?.mkdirs()
                    out.outputStream().use { os ->
                        val buf = ByteArray(1 shl 14)
                        while (true) {
                            val n = zis.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_PACK_SIZE) throw IOException("Иконпак слишком большой")
                            os.write(buf, 0, n)
                        }
                    }
                }
            }
            val pack = load(dir) ?: throw IOException("В архиве нет иконок")
            reload()
            return pack
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    /**
     * Устанавливает пак из каталога: уникальные SVG качаются параллельно (не больше 6 одновременно),
     * проверяются (размер, что это действительно SVG), затем пак собирается во временной папке
     * и подменяет прежнюю версию целиком.
     */
    suspend fun installFromCatalog(pack: CatalogPack, onProgress: (Float) -> Unit = {}): IconPack = withContext(Dispatchers.IO) {
        val files = pack.icons.values.distinct()
        val tmp = File(root, ".tmp-${pack.id}").apply { deleteRecursively(); mkdirs() }
        try {
            val done = java.util.concurrent.atomic.AtomicInteger()
            val gate = kotlinx.coroutines.sync.Semaphore(6)
            kotlinx.coroutines.coroutineScope {
                files.map { name ->
                    async {
                        gate.withPermit {
                            // На медленной сети отдельные запросы срываются — до трёх попыток на значок.
                            var attempt = 0
                            var bytes: ByteArray
                            while (true) {
                                try {
                                    bytes = fetch(pack.baseUrl + name + ".svg", limit = 256 * 1024)
                                    break
                                } catch (e: IOException) {
                                    if (++attempt >= 3) throw e
                                    kotlinx.coroutines.delay(700L * attempt)
                                }
                            }
                            val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8)
                            if (!head.contains("<svg")) throw IOException("$name.svg: это не SVG")
                            File(tmp, "$name.svg").writeBytes(bytes)
                            onProgress(done.incrementAndGet().toFloat() / files.size)
                        }
                    }
                }.awaitAll()
            }
            val manifest = IconPackManifest(
                name = pack.name,
                author = pack.author,
                description = "${pack.description} · ${pack.license}",
                icons = pack.icons.mapValues { "${it.value}.svg" },
            )
            File(tmp, "pack.json").writeText(AppJson.encodeToString(IconPackManifest.serializer(), manifest))
            val target = File(root, pack.id)
            // Прежняя версия откладывается и удаляется только после успешной подмены.
            val old = File(root, ".old-${pack.id}").apply { deleteRecursively() }
            if (target.exists() && !target.renameTo(old)) throw IOException("Не удалось заменить иконпак")
            if (!tmp.renameTo(target)) {
                old.renameTo(target)
                reload()
                throw IOException("Не удалось сохранить иконпак")
            }
            old.deleteRecursively()
            val loaded = load(target) ?: throw IOException("Иконпак повреждён")
            reload()
            loaded
        } catch (e: Throwable) {
            tmp.deleteRecursively()
            throw e
        }
    }

    private fun fetch(url: String, limit: Int): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (conn.responseCode !in 200..299) throw IOException("Сервер вернул ${conn.responseCode} для ${url.substringAfterLast('/')}")
            return conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(1 shl 14)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > limit) throw IOException("Значок ${url.substringAfterLast('/')} слишком большой")
                }
                out.toByteArray()
            }
        } finally {
            conn.disconnect()
        }
    }

    fun delete(id: String) {
        scope.launch(Dispatchers.IO) {
            File(root, id).deleteRecursively()
            reload()
        }
    }

    /** Копирует выбранную пользователем картинку в приватную папку и возвращает путь. */
    suspend fun saveCustomIcon(uri: Uri, ext: String): String = withContext(Dispatchers.IO) {
        val target = File(customIconsDir, "${ext}_${System.currentTimeMillis()}.img")
        resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: throw IOException("Не удалось прочитать картинку")
        target.absolutePath
    }

    suspend fun saveCustomIcon(file: File, ext: String): String = withContext(Dispatchers.IO) {
        val target = File(customIconsDir, "${ext}_${System.currentTimeMillis()}.${file.extension}")
        file.copyTo(target, overwrite = true)
        target.absolutePath
    }

    companion object {
        val IMAGE_EXT = setOf("png", "webp", "jpg", "jpeg", "svg", "gif")
        private const val MAX_PACK_SIZE = 64L * 1024 * 1024
    }
}
