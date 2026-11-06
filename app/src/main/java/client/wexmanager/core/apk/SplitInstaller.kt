package client.wexmanager.core.apk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.LocaleList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import kotlin.coroutines.coroutineContext

/** Группа части пакета — по ней выбираются части под устройство. */
enum class SplitGroup(val title: String) {
    Base("Основной APK"),
    Feature("Модули"),
    Abi("Архитектура процессора"),
    Density("Плотность экрана"),
    Language("Языки"),
    Other("Прочее"),
}

/** APK внутри пакета: запись в архиве, понятное имя, квалификатор и подходит ли устройству. */
data class SplitApk(
    val entry: String,
    val name: String,
    val size: Long,
    val group: SplitGroup,
    val qualifier: String,
    val recommended: Boolean,
)

/** Пакет .apks/.xapk/.apkm: APK-части и OBB-файлы (xapk). */
data class SplitBundle(val packageName: String?, val splits: List<SplitApk>, val obb: List<String>) {
    val base: SplitApk? get() = splits.firstOrNull { it.group == SplitGroup.Base }
}

/** Итог установки: [ok] или текст ошибки PackageInstaller. */
data class InstallOutcome(val sessionId: Int, val ok: Boolean, val message: String, val packageName: String?)

/**
 * Установка набора APK (split APK) одной сессией PackageInstaller — как «Установить» для .apks/.xapk/.apkm
 * в MT Manager. Подтверждение показывает система; результат приходит в [InstallResultReceiver].
 */
object SplitInstaller {
    val BUNDLE_EXTENSIONS = setOf("apks", "xapk", "apkm")

    private val ABIS = setOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86", "x86_64", "mips", "mips64", "riscv64")
    private val DENSITIES = linkedMapOf(
        "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240, "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640,
    )
    private val LANGUAGE = Regex("^[a-z]{2,3}([_-][A-Za-z0-9]{2,8})*$")

    private val _results = MutableStateFlow<InstallOutcome?>(null)
    val results: StateFlow<InstallOutcome?> = _results.asStateFlow()

    internal fun report(outcome: InstallOutcome) { _results.value = outcome }
    fun consume() { _results.value = null }

    fun isBundle(name: String) = name.substringAfterLast('.', "").lowercase() in BUNDLE_EXTENSIONS

    /** Состав пакета без распаковки; выбор по умолчанию — под ABI, плотность и языки устройства. */
    fun read(file: File, abis: List<String>, densityDpi: Int, languages: List<String>): SplitBundle = ZipFile(file).use { z ->
        val apks = z.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.toList()
        if (apks.isEmpty()) throw IOException("В пакете нет APK" + if (file.extension.equals("apkm", true)) " (зашифрованный .apkm не поддерживается)" else "")
        val obb = z.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".obb", true) }.map { it.name }.toList()
        val pkg = z.getEntry("manifest.json")?.let { e ->
            runCatching { org.json.JSONObject(z.getInputStream(e).bufferedReader().readText()).optString("package_name").ifEmpty { null } }.getOrNull()
        } ?: z.getEntry("info.json")?.let { e ->
            runCatching { org.json.JSONObject(z.getInputStream(e).bufferedReader().readText()).optString("pname").ifEmpty { null } }.getOrNull()
        }
        val names = apks.map { it.name.substringAfterLast('/').removeSuffix(".apk").removeSuffix(".APK") }
        val baseIndex = names.indexOfFirst { it == "base" || it == "base-master" || (pkg != null && it == pkg) }
            .takeIf { it >= 0 }
            ?: apks.indices.filter { qualifierOf(names[it]) == null }.maxByOrNull { apks[it].size }
            ?: apks.indices.maxBy { apks[it].size }
        val raw = apks.mapIndexed { i, e ->
            val name = names[i]
            if (i == baseIndex) return@mapIndexed SplitApk(e.name, name, e.size, SplitGroup.Base, "", true)
            val q = qualifierOf(name)
            val group = when {
                q == null -> SplitGroup.Feature
                q in ABIS -> SplitGroup.Abi
                q in DENSITIES || q == "nodpi" || q == "anydpi" -> SplitGroup.Density
                LANGUAGE.matches(q) -> SplitGroup.Language
                else -> SplitGroup.Other
            }
            SplitApk(e.name, name, e.size, group, q ?: name.removePrefix("split_").removeSuffix("-master"), false)
        }
        val deviceAbis = abis.map { it.replace('-', '_') }
        val abiPick = deviceAbis.firstOrNull { a -> raw.any { it.group == SplitGroup.Abi && it.qualifier == a } }
        val densities = raw.filter { it.group == SplitGroup.Density && it.qualifier in DENSITIES }
        val densityPick = densities.minByOrNull { s ->
            val d = DENSITIES.getValue(s.qualifier)
            // Предпочитаем ближайшую не меньше экранной: картинки уменьшаются без потери чёткости.
            if (d >= densityDpi) d - densityDpi else (densityDpi - d) * 4
        }?.qualifier
        val langs = languages.map { it.lowercase() }.toSet()
        val splits = raw.map { s ->
            val pick = when (s.group) {
                SplitGroup.Base, SplitGroup.Feature, SplitGroup.Other -> true
                SplitGroup.Abi -> s.qualifier == abiPick
                SplitGroup.Density -> s.qualifier == densityPick || s.qualifier == "nodpi" || s.qualifier == "anydpi"
                SplitGroup.Language -> s.qualifier.lowercase().substringBefore('_').substringBefore('-') in langs
            }
            s.copy(recommended = pick)
        }.sortedWith(compareBy<SplitApk>({ it.group.ordinal }, { it.qualifier }))
        SplitBundle(pkg, splits, obb)
    }

    /** «config.xxhdpi», «split_config.ru», «base-arm64_v8a» → квалификатор; null — модуль или основной APK. */
    internal fun qualifierOf(name: String): String? = when {
        name.startsWith("split_config.") -> name.removePrefix("split_config.")
        name.startsWith("config.") -> name.removePrefix("config.")
        name.contains(".config.") -> name.substringAfter(".config.")
        name.startsWith("base-") && name != "base-master" -> name.removePrefix("base-")
        '-' in name && !name.endsWith("-master") -> name.substringAfterLast('-')
        else -> null
    }

    fun deviceLanguages(): List<String> {
        val list = LocaleList.getDefault()
        return (0 until list.size()).map { list[it].language } + "en"
    }

    fun deviceAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    /** Пишет выбранные части в сессию и отправляет её на установку; возвращает id сессии. */
    suspend fun install(
        context: Context,
        file: File,
        entries: List<String>,
        bundle: SplitBundle,
        onProgress: (Float) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        bundle.packageName?.takeIf { ApkCloner.isValidPackage(it) }?.let { params.setAppPackageName(it) }
        params.setInstallReason(PackageManager.INSTALL_REASON_USER)
        ZipFile(file).use { z ->
            val selected = entries.map { name -> z.getEntry(name) ?: throw IOException("Нет записи $name") }
            val total = selected.sumOf { it.size.coerceAtLeast(0) }.coerceAtLeast(1)
            params.setSize(total)
            val id = installer.createSession(params)
            var committed = false
            try {
                installer.openSession(id).use { session ->
                    var done = 0L
                    val buf = ByteArray(256 * 1024)
                    selected.forEachIndexed { i, e ->
                        val name = "%02d_".format(i) + e.name.substringAfterLast('/')
                        z.getInputStream(e).use { input ->
                            session.openWrite(name, 0, e.size).use { out ->
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    onProgress(done.toFloat() / total)
                                }
                                session.fsync(out)
                            }
                        }
                    }
                    val intent = Intent(context, InstallResultReceiver::class.java)
                        .putExtra(InstallResultReceiver.EXTRA_PACKAGE, bundle.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(context, id, intent, flags)
                    session.commit(pending.intentSender)
                    committed = true
                    id
                }
            } finally {
                if (!committed) runCatching { installer.abandonSession(id) }
            }
        }
    }

    /**
     * OBB из xapk — в Android/obb/<пакет> (доступно установщикам с разрешением на установку). Вызывать только
     * после успешной установки: при отмене чужие данные игры не трогаются. Каждый файл — через временный
     * и переименование, чтобы обрыв не оставил обрезанный OBB. Возвращает число скопированных.
     */
    suspend fun copyObb(file: File, bundle: SplitBundle): Int = withContext(Dispatchers.IO) {
        // Имя пакета из manifest.json — недоверенное: «../..» увело бы запись за пределы Android/obb.
        val pkg = bundle.packageName?.takeIf { ApkCloner.isValidPackage(it) } ?: return@withContext 0
        if (bundle.obb.isEmpty()) return@withContext 0
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStorageDirectory(), "Android/obb/$pkg")
        var copied = 0
        ZipFile(file).use { z ->
            for (name in bundle.obb) {
                val base = name.substringAfterLast('/')
                if (base.isEmpty() || base == "." || base == ".." || !base.endsWith(".obb", true)) continue
                runCatching {
                    dir.mkdirs()
                    val target = File(dir, base)
                    val tmp = File(dir, ".$base.wexpart")
                    try {
                        z.getInputStream(z.getEntry(name)).use { input -> tmp.outputStream().use { input.copyTo(it, 1 shl 16) } }
                        if (!tmp.renameTo(target)) throw IOException("rename")
                    } finally {
                        tmp.delete()
                    }
                    copied++
                }
            }
        }
        copied
    }
}

/** Ответ PackageInstaller: запрос подтверждения у пользователя или итог установки. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm == null) {
                    SplitInstaller.report(InstallOutcome(id, false, "Система не запросила подтверждение", pkg))
                    return
                }
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { SplitInstaller.report(InstallOutcome(id, false, "Не удалось показать подтверждение: ${it.message}", pkg)) }
            }
            PackageInstaller.STATUS_SUCCESS -> SplitInstaller.report(InstallOutcome(id, true, "Установлено", pkg))
            else -> {
                val raw = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val text = when (status) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Установка отменена"
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Установка заблокирована"
                    PackageInstaller.STATUS_FAILURE_CONFLICT -> "Конфликт с установленной версией (другая подпись?)"
                    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Пакет несовместим с устройством"
                    PackageInstaller.STATUS_FAILURE_INVALID -> "Пакет повреждён или части не подходят друг к другу"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Недостаточно места"
                    else -> "Ошибка установки"
                }
                SplitInstaller.report(InstallOutcome(id, false, if (raw.isNullOrBlank()) text else "$text: $raw", pkg))
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE = "client.wexmanager.extra.PACKAGE"
    }
}
