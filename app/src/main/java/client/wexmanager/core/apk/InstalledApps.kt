package client.wexmanager.core.apk

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.Collator
import java.util.Locale
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Immutable
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val sourceDir: String,
    val splitSourceDirs: List<String>,
    val apkSize: Long,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    val enabled: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val targetSdk: Int,
    val minSdk: Int,
) {
    val hasSplits: Boolean get() = splitSourceDirs.isNotEmpty()
    val allApks: List<String> get() = listOf(sourceDir) + splitSourceDirs
    val versionLabel: String get() = versionName?.takeIf { it.isNotBlank() } ?: versionCode.toString()

    /** Имя файла для извлечения: «Название_версия.apk(s)» без недопустимых символов. */
    val exportFileName: String
        get() {
            val base = "${label}_$versionLabel".replace(unsafeFileChars, "_").trim().take(120)
            return base + if (hasSplits) ".apks" else ".apk"
        }
}

private val unsafeFileChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+")

object InstalledApps {

    suspend fun loadAll(pm: PackageManager): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packages = installedPackages(pm)
        // loadLabel читает ресурсы каждого APK — распараллеливаем на несколько потоков.
        val chunk = (packages.size / 6).coerceAtLeast(16)
        coroutineScope {
            packages.chunked(chunk).map { part ->
                async {
                    part.mapNotNull { pi ->
                        ensureActive()
                        runCatching { fromPackageInfo(pm, pi) }.getOrNull()
                    }
                }
            }.awaitAll().flatten()
                // packageName — ключ строк списка; на части прошивок PackageManager отдаёт
                // несколько версий одного пакета (статические библиотеки) — оставляем самую новую.
                .sortedByDescending { it.versionCode }
                .distinctBy { it.packageName }
        }
    }

    /**
     * Порядок по уже загруженной [InstalledApp.label]. Русская коллация без учёта регистра,
     * «ё» рядом с «е». Одинаковые подписи — по packageName. Collator создаётся на вызов: он не потокобезопасен.
     */
    fun sortedByLabel(apps: List<InstalledApp>): List<InstalledApp> {
        val collator = Collator.getInstance(Locale.forLanguageTag("ru")).apply {
            strength = Collator.PRIMARY
        }
        return apps.sortedWith { a, b ->
            val byLabel = collator.compare(a.label, b.label)
            if (byLabel != 0) byLabel else a.packageName.compareTo(b.packageName)
        }
    }

    fun load(pm: PackageManager, packageName: String): InstalledApp? = runCatching {
        fromPackageInfo(pm, ApkAnalyzer.packageInfo(pm, packageName, 0))
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun installedPackages(pm: PackageManager): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }

    private fun fromPackageInfo(pm: PackageManager, pi: PackageInfo): InstalledApp? {
        val ai = pi.applicationInfo ?: return null
        val source = ai.sourceDir ?: return null
        val splits = ai.splitSourceDirs?.toList().orEmpty()
        val size = (listOf(source) + splits).sumOf { File(it).length() }
        val label = runCatching { ai.loadLabel(pm).toString() }.getOrNull()?.takeIf { it.isNotBlank() } ?: pi.packageName
        return InstalledApp(
            packageName = pi.packageName,
            label = label,
            versionName = pi.versionName,
            versionCode = pi.longVersionCode,
            sourceDir = source,
            splitSourceDirs = splits,
            apkSize = size,
            isSystem = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            isUpdatedSystem = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            enabled = ai.enabled,
            firstInstallTime = pi.firstInstallTime,
            lastUpdateTime = pi.lastUpdateTime,
            targetSdk = ai.targetSdkVersion,
            minSdk = ai.minSdkVersion,
        )
    }

    /**
     * Извлекает приложение в [dir]: base.apk копируется как есть, при наличии split APK
     * собирается .apks (zip со всеми частями). Запись идёт во временный файл, при отмене он удаляется.
     */
    suspend fun export(app: InstalledApp, dir: File, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Не удалось создать папку ${dir.absolutePath}")
        val target = File(dir, app.exportFileName)
        val temp = File(dir, ".${target.name}.part")
        val sources = app.allApks.map(::File)
        val total = sources.sumOf { it.length() }.coerceAtLeast(1)
        var done = 0L
        var lastReported = -1
        val report = { n: Int ->
            done += n
            val pct = (done * 1000 / total).toInt()
            if (pct != lastReported) {
                lastReported = pct
                onProgress(pct / 1000f)
            }
        }
        try {
            if (!app.hasSplits) {
                FileInputStream(sources[0]).use { input ->
                    FileOutputStream(temp).use { out -> copy(input, out, report) }
                }
            } else {
                ZipOutputStream(FileOutputStream(temp).buffered(256 * 1024)).use { zip ->
                    // APK уже сжаты — пишем без сжатия, чтобы не тратить время.
                    zip.setLevel(Deflater.NO_COMPRESSION)
                    val used = HashSet<String>()
                    sources.forEachIndexed { i, src ->
                        var name = if (i == 0) "base.apk" else src.name
                        if (!used.add(name)) name = "split_$i.apk".also { used += it }
                        zip.putNextEntry(ZipEntry(name).apply { time = src.lastModified() })
                        FileInputStream(src).use { input -> copy(input, zip, report) }
                        zip.closeEntry()
                    }
                }
            }
            ensureActive()
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) throw IllegalStateException("Не удалось сохранить ${target.name}")
            onProgress(1f)
            target
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    private suspend fun copy(input: FileInputStream, out: java.io.OutputStream, report: (Int) -> Unit) {
        val buf = ByteArray(256 * 1024)
        val ctx = currentCoroutineContext()
        while (true) {
            ctx.ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            report(n)
        }
    }
}
