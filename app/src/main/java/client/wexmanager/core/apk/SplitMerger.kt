package client.wexmanager.core.apk

import client.wexmanager.core.archive.ArchiveEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ResourceAlignmentExtraField
import org.apache.commons.compress.archivers.zip.Zip64Mode
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException

/**
 * Объединение split-APK в один APK (как «Объединить» в MT Manager / APKEditor merge): таблицы
 * ресурсов частей вливаются в таблицу основного APK ([ArscMerge]), файлы частей (lib/, res/, assets/)
 * копируются как есть с выравниванием, из манифеста убираются признаки обязательных частей.
 * Результат без подписи — подписывается отдельно.
 */
object SplitMerger {
    /** Атрибуты, из-за которых установщик требует части отдельно. */
    private val SPLIT_ATTRS = listOf("android:isSplitRequired", "android:requiredSplitTypes", "android:splitTypes")

    /** meta-data о частях и источнике (Play проверяет их при запуске и просит «доустановить» части). */
    private val SPLIT_META = setOf(
        "com.android.vending.splits.required", "com.android.vending.splits",
        "com.android.vending.derived.apk.id", "com.android.stamp.source", "com.android.stamp.type",
    )

    data class Result(val parts: Int, val files: Int)

    /**
     * [bundle] — .apks/.xapk/.apkm; [entries] — выбранные APK внутри (основной обязателен);
     * результат пишется в [out] атомарно. [work] — папка для временных файлов.
     */
    suspend fun merge(bundle: File, base: String, entries: List<String>, out: File, work: File, onProgress: (Float) -> Unit): Result = withContext(Dispatchers.IO) {
        work.mkdirs()
        val parts = listOf(base) + entries.filter { it != base }
        val files = parts.mapIndexed { i, name ->
            currentCoroutineContext().ensureActive()
            File(work, "part$i.apk").also { f ->
                java.util.zip.ZipFile(bundle).use { z ->
                    val e = z.getEntry(name) ?: throw IOException("В пакете нет $name")
                    z.getInputStream(e).use { input -> f.outputStream().use { input.copyTo(it, 1 shl 16) } }
                }
            }
        }
        onProgress(0.2f)
        val tmp = File(out.parentFile, ".${out.name}.wexpart")
        try {
            val zips = files.map { ZipFile.builder().setFile(it).get() }
            try {
                val baseZip = zips.first()
                // Ресурсы: таблица основного APK + таблицы частей.
                val arscEntry = baseZip.getEntry("resources.arsc") ?: throw IOException("В основном APK нет resources.arsc")
                val table = ArscTable.parse(baseZip.getInputStream(arscEntry).use { it.readBytes() })
                for (z in zips.drop(1)) {
                    val e = z.getEntry("resources.arsc") ?: continue
                    ArscMerge.merge(table, ArscTable.parse(z.getInputStream(e).use { it.readBytes() }))
                }
                val arsc = File(work, "resources.arsc").apply { writeBytes(table.encode()) }
                onProgress(0.4f)
                // Манифест: без признаков обязательных частей.
                val manifestEntry = baseZip.getEntry("AndroidManifest.xml") ?: throw IOException("В основном APK нет манифеста")
                val original = File(work, "AndroidManifest.orig.xml").apply { writeBytes(baseZip.getInputStream(manifestEntry).use { it.readBytes() }) }
                val manifestText = patchManifest(AxmlDecoder.decode(original.readBytes()).toXml())
                val manifest = File(work, "AndroidManifest.xml").apply {
                    writeBytes(try { AxmlFiles.encode(manifestText, original) } catch (e: AxmlEncodeException) { throw IOException(e.message) })
                }
                onProgress(0.5f)
                var copied = 0
                ZipArchiveOutputStream(tmp).use { zos ->
                    zos.setUseZip64(Zip64Mode.AsNeeded)
                    val written = HashSet<String>()
                    fun putFile(name: String, file: File, stored: Boolean) {
                        val entry = ZipArchiveEntry(file, name)
                        if (stored) {
                            entry.method = ZipArchiveEntry.STORED
                            entry.size = file.length()
                            entry.crc = ArchiveEngine.crc32(file)
                            entry.setAlignment(ArchiveEngine.storedAlignment(name))
                        }
                        zos.putArchiveEntry(entry)
                        file.inputStream().use { it.copyTo(zos, 1 shl 16) }
                        zos.closeArchiveEntry()
                        written += name
                    }
                    putFile("AndroidManifest.xml", manifest, stored = false)
                    putFile("resources.arsc", arsc, stored = true)
                    zips.forEachIndexed { i, z ->
                        for (e in z.entries) {
                            currentCoroutineContext().ensureActive()
                            val name = e.name
                            if (e.isDirectory || name in written || skip(name)) continue
                            // Записи копируются как есть: несжатые .so и DEX остаются несжатыми (extractNativeLibs=false).
                            if (e.method == ZipArchiveEntry.STORED) e.addExtraField(ResourceAlignmentExtraField(ArchiveEngine.storedAlignment(name)))
                            z.getRawInputStream(e).use { raw -> zos.addRawArchiveEntry(e, raw) }
                            written += name
                            if (i > 0) copied++
                        }
                        onProgress(0.5f + 0.5f * (i + 1) / zips.size)
                    }
                }
                if (!tmp.renameTo(out)) throw IOException("Не удалось записать ${out.name}")
                Result(parts.size, copied)
            } finally {
                zips.forEach { runCatching { it.close() } }
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        } finally {
            work.deleteRecursively()
        }
    }

    /** Подписи частей и их манифесты/таблицы не переносятся. */
    private fun skip(name: String): Boolean {
        if (name == "AndroidManifest.xml" || name == "resources.arsc") return true
        if (!name.startsWith("META-INF/")) return false
        val n = name.substringAfterLast('/').uppercase()
        return n == "MANIFEST.MF" || n.endsWith(".SF") || n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC") ||
            n == "STAMP-CERT-SHA256"
    }

    /** Убирает из текста манифеста признаки обязательных частей (атрибуты и meta-data). */
    internal fun patchManifest(xml: String): String {
        var s = xml
        for (a in SPLIT_ATTRS) s = s.replace(Regex("\\s+" + Regex.escape(a) + "=\"[^\"]*\""), "")
        val names = SPLIT_META.joinToString("|") { Regex.escape(it) }
        s = s.replace(Regex("[ \\t]*<meta-data\\b[^>]*android:name=\"(?:$names)\"[^>]*?(?:/>|>[\\s\\S]*?</meta-data>)[ \\t]*\\r?\\n?"), "")
        return s
    }
}
