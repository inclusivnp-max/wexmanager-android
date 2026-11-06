package client.wexmanager.core.apk

import client.wexmanager.core.archive.ArchiveEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Клон APK со сменой имени пакета (как «Совместная установка» в MT Manager): копия ставится рядом
 * с оригиналом. Меняется только манифест — относительные имена компонентов раскрываются по старому
 * пакету (классы в DEX остаются на месте), authorities провайдеров и собственные разрешения
 * переименовываются, чтобы не конфликтовать с оригиналом. Результат подписывается отдельно.
 */
object ApkCloner {
    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun isValidPackage(name: String) = PACKAGE.matches(name)

    /** Атрибуты с именами классов (относительное имя — от пакета приложения). */
    private val CLASS_ATTRS = listOf(
        "android:name", "android:targetActivity", "android:backupAgent", "android:manageSpaceActivity",
        "android:parentActivityName", "android:appComponentFactory", "android:zygotePreloadName",
    )
    private val COMPONENT_TAGS = setOf("application", "activity", "activity-alias", "service", "receiver", "provider", "instrumentation")

    suspend fun clone(apk: File, newPackage: String, out: File, work: File) = withContext(Dispatchers.IO) {
        if (!isValidPackage(newPackage)) throw IOException("Недопустимое имя пакета: $newPackage")
        work.mkdirs()
        try {
            val original = File(work, "AndroidManifest.orig.xml")
            java.util.zip.ZipFile(apk).use { z ->
                val e = z.getEntry("AndroidManifest.xml") ?: throw IOException("В APK нет манифеста")
                z.getInputStream(e).use { input -> original.outputStream().use { input.copyTo(it) } }
            }
            val text = patchManifest(AxmlDecoder.decode(original.readBytes()).toXml(), newPackage)
            val manifest = File(work, "AndroidManifest.xml").apply {
                writeBytes(try { AxmlFiles.encode(text, original) } catch (e: AxmlEncodeException) { throw IOException(e.message) })
            }
            val tmp = File(out.parentFile, ".${out.name}.wexpart")
            apk.copyTo(tmp, overwrite = true)
            try {
                // Старая подпись к изменённому манифесту не подходит — убираем её, новую поставит подпись.
                val sigs = java.util.zip.ZipFile(tmp).use { z ->
                    z.entries().asSequence().map { it.name }.filter { n ->
                        val u = n.uppercase()
                        u.startsWith("META-INF/") && (u.endsWith(".SF") || u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC") || u == "META-INF/MANIFEST.MF")
                    }.toSet()
                }
                ArchiveEngine.modifyZip(tmp, mapOf("AndroidManifest.xml" to manifest), sigs)
                if (!tmp.renameTo(out)) throw IOException("Не удалось записать ${out.name}")
            } catch (e: Throwable) {
                tmp.delete()
                throw e
            }
        } finally {
            work.deleteRecursively()
        }
    }

    /** Текст манифеста с новым пакетом [newPackage]. */
    internal fun patchManifest(xml: String, newPackage: String): String {
        val old = Regex("<manifest\\b[^>]*?\\spackage=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)
            ?: throw IOException("В манифесте нет атрибута package")
        if (old == newPackage) throw IOException("Новое имя пакета совпадает со старым")
        fun absolute(v: String) = when {
            v.startsWith(".") -> old + v
            !v.contains('.') && v.isNotEmpty() -> "$old.$v"
            else -> v
        }
        var s = Regex("<([\\w-]+)\\b[^>]*>").replace(xml) { m ->
            val tag = m.groupValues[1]
            var t = m.value
            if (tag in COMPONENT_TAGS) for (a in CLASS_ATTRS) {
                t = t.replace(Regex("(\\s${Regex.escape(a)}=\")([^\"]*)(\")")) { am ->
                    // У <application> android:name — класс Application; у остальных — класс компонента.
                    am.groupValues[1] + absolute(am.groupValues[2]) + am.groupValues[3]
                }
            }
            if (tag == "provider") {
                t = t.replace(Regex("(\\sandroid:authorities=\")([^\"]*)(\")")) { am ->
                    val list = am.groupValues[2].split(';').joinToString(";") { a ->
                        if (a.contains(old)) a.replace(old, newPackage) else "$newPackage.$a"
                    }
                    am.groupValues[1] + list + am.groupValues[3]
                }
            }
            if (tag == "manifest") t = t.replace(Regex("\\ssharedUserId=\"[^\"]*\"|\\sandroid:sharedUserId=\"[^\"]*\"|\\sandroid:sharedUserLabel=\"[^\"]*\""), "")
            t
        }
        // Собственные разрешения: оригинал уже владеет ими — у клона свои имена.
        val declared = Regex("<permission\\b[^>]*\\sandroid:name=\"([^\"]+)\"").findAll(s).map { it.groupValues[1] }.toSet()
        for (p in declared) {
            val renamed = if (p.contains(old)) p.replace(old, newPackage) else "$newPackage.$p"
            s = s.replace("\"$p\"", "\"$renamed\"")
        }
        return s.replaceFirst(Regex("(<manifest\\b[^>]*?\\spackage=\")([^\"]+)(\")"), "$1$newPackage$3")
    }
}
