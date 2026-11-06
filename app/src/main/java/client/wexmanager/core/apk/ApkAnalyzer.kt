package client.wexmanager.core.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.Signature
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.DSAPublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.util.zip.ZipFile

enum class PermissionLevel { Dangerous, Signature, Normal, Unknown }

data class ApkPermission(
    val name: String,
    val label: String?,
    val description: String?,
    val level: PermissionLevel,
)

data class ApkComponent(
    val name: String,
    val exported: Boolean,
    /** Разрешение / authority провайдера и т.п. */
    val extra: String? = null,
    val launcher: Boolean = false,
)

data class ApkCertificate(
    val subject: String,
    val issuer: String,
    val serial: String,
    val notBefore: Long,
    val notAfter: Long,
    val signatureAlgorithm: String,
    val publicKey: String,
    val md5: String,
    val sha1: String,
    val sha256: String,
)

/**
 * Библиотеки одной ABI. [notAligned16k] — сегменты LOAD выровнены меньше чем на 16 КБ; [zipNotAligned16k] —
 * несжатая запись лежит в APK не на границе 16 КБ. И то и другое ломает загрузку на устройствах
 * со страницами 16 КБ (Android 15+); проверяются только 64-битные ABI.
 */
data class AbiLibraries(
    val abi: String,
    val libraries: List<String>,
    val totalSize: Long,
    val notAligned16k: List<String> = emptyList(),
    val zipNotAligned16k: List<String> = emptyList(),
) {
    val checked16k: Boolean get() = abi == "arm64-v8a" || abi == "x86_64"
    val ready16k: Boolean get() = notAligned16k.isEmpty() && zipNotAligned16k.isEmpty()
}

data class DexEntry(val name: String, val size: Long, val compressedSize: Long)

data class ApkFileStats(
    val entries: Int,
    val uncompressedSize: Long,
    val resourcesArscSize: Long,
    val resCount: Int,
    val assetsCount: Int,
    val assetsSize: Long,
    val metaInfCount: Int,
)

data class InstalledVersion(
    val versionName: String?,
    val versionCode: Long,
    val sourceDir: String?,
    val launchable: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
)

/** Иконка приложения: адаптивные отрисованы без маски (форму задаёт UI). */
class AppIconBitmap(val bitmap: Bitmap, val adaptive: Boolean)

data class ApkDetails(
    val path: String,
    val fileSize: Long,
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val compileSdk: Int,
    val icon: AppIconBitmap?,
    val installed: InstalledVersion?,
    val permissions: List<ApkPermission>,
    val activities: List<ApkComponent>,
    val services: List<ApkComponent>,
    val receivers: List<ApkComponent>,
    val providers: List<ApkComponent>,
    val certificates: List<ApkCertificate>,
    val signatureSchemes: List<String>,
    val abis: List<AbiLibraries>,
    val dexFiles: List<DexEntry>,
    val stats: ApkFileStats,
    val debuggable: Boolean,
    val splitName: String?,
    /** false — PackageManager не смог разобрать APK, данные взяты только из манифеста. */
    val parsedBySystem: Boolean,
) {
    val dangerousCount: Int get() = permissions.count { it.level == PermissionLevel.Dangerous }

    /** >0 — APK новее установленного, <0 — старше, 0 — та же версия. */
    val versionComparison: Int? get() = installed?.let { versionCode.compareTo(it.versionCode) }
}

object ApkAnalyzer {

    private const val BASE_FLAGS = PackageManager.GET_PERMISSIONS or
        PackageManager.GET_ACTIVITIES or
        PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or
        PackageManager.GET_PROVIDERS

    suspend fun analyze(context: Context, path: String, iconSizePx: Int): ApkDetails = withContext(Dispatchers.IO) {
        val file = File(path)
        require(file.isFile) { "Файл не найден" }
        require(file.canRead()) { "Нет доступа к файлу" }
        val pm = context.packageManager

        val manifestBytes = runCatching { readEntry(file, "AndroidManifest.xml") }.getOrNull()
        val manifest = manifestBytes?.let { runCatching { AxmlDecoder.decode(it).root }.getOrNull() }
        ensureActive()

        var pi = archiveInfo(pm, path, BASE_FLAGS or PackageManager.GET_SIGNING_CERTIFICATES)
            ?: archiveInfo(pm, path, BASE_FLAGS)
            ?: archiveInfo(pm, path, 0)
        if (pi == null && manifest == null) {
            throw IllegalStateException("Файл повреждён или не является APK")
        }
        ensureActive()

        val app = pi?.applicationInfo?.apply {
            sourceDir = path
            publicSourceDir = path
        }
        val packageName = pi?.packageName ?: manifest?.attr("package").orEmpty()
        val label = app?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }
            ?.takeIf { it.isNotBlank() } ?: packageName.ifEmpty { file.nameWithoutExtension }
        val icon = app?.let { a -> runCatching { a.loadIcon(pm) }.getOrNull()?.let { renderIcon(it, iconSizePx) } }
        ensureActive()

        val usesSdk = manifest?.elements("uses-sdk")?.firstOrNull()
        fun sdkAttr(e: XmlElement?, id: Int, name: String): Int? =
            (e?.attrs?.firstOrNull { it.resourceId == id } ?: e?.attrs?.firstOrNull { it.name == name })
                ?.value?.toIntOrNull()

        val minSdk = app?.minSdkVersion?.takeIf { it > 0 } ?: sdkAttr(usesSdk, AxmlDecoder.ATTR_MIN_SDK, "minSdkVersion") ?: 1
        val targetSdk = app?.targetSdkVersion?.takeIf { it > 0 } ?: sdkAttr(usesSdk, AxmlDecoder.ATTR_TARGET_SDK, "targetSdkVersion") ?: minSdk
        val compileSdk = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) app?.compileSdkVersion?.takeIf { it > 0 } else null)
            ?: sdkAttr(manifest, AxmlDecoder.ATTR_COMPILE_SDK, "compileSdkVersion")
            ?: manifest?.attr("platformBuildVersionCode")?.toIntOrNull()
            ?: 0

        val versionCode = pi?.longVersionCode ?: manifest?.let { m ->
            (m.attrs.firstOrNull { it.name == "versionCode" }?.value?.toLongOrNull() ?: 0L)
        } ?: 0L
        val versionName = pi?.versionName ?: manifest?.attr("versionName")

        val launchers = manifest?.let { launcherNames(it, packageName) } ?: emptySet()

        val activities = pi?.activities?.map { component(it, launchers, it.permission) }
            ?: manifestComponents(manifest, "activity", packageName, launchers)
        val services = pi?.services?.map { component(it, launchers, it.permission) }
            ?: manifestComponents(manifest, "service", packageName, launchers)
        val receivers = pi?.receivers?.map { component(it, launchers, it.permission) }
            ?: manifestComponents(manifest, "receiver", packageName, launchers)
        val providers = pi?.providers?.map { component(it, launchers, it.authority) }
            ?: manifestComponents(manifest, "provider", packageName, launchers)

        val requested = pi?.requestedPermissions?.toList()
            ?: manifest?.elements("uses-permission")?.mapNotNull { it.attr("name") }
            ?: emptyList()
        val declared = pi?.permissions?.associateBy { it.name } ?: emptyMap()
        val permissions = requested.distinct().map { permission(pm, it, declared[it]) }
            .sortedWith(compareBy<ApkPermission> { it.level.ordinal }.thenBy { it.name })
        ensureActive()

        val certificates = certificates(pm, path, pi, file)
        ensureActive()
        val content = scanZip(file)
        val schemes = buildList {
            if (content.hasV1) add("v1")
            signingBlockSchemes(file).forEach { add(it) }
            if (File("$path.idsig").exists()) add("v4")
        }

        val installed = packageName.takeIf { it.isNotEmpty() }?.let { installedVersion(pm, it) }

        ApkDetails(
            path = path,
            fileSize = file.length(),
            label = label,
            packageName = packageName,
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            compileSdk = compileSdk,
            icon = icon,
            installed = installed,
            permissions = permissions,
            activities = activities.sortedWith(compareByDescending<ApkComponent> { it.launcher }.thenBy { it.name }),
            services = services.sortedBy { it.name },
            receivers = receivers.sortedBy { it.name },
            providers = providers.sortedBy { it.name },
            certificates = certificates,
            signatureSchemes = schemes,
            abis = content.abis,
            dexFiles = content.dex,
            stats = content.stats,
            debuggable = app?.let { it.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
                ?: (manifest?.elements("application")?.firstOrNull()?.attr("debuggable") == "true"),
            splitName = manifest?.attr("split"),
            parsedBySystem = pi != null,
        )
    }

    fun installedVersion(pm: PackageManager, packageName: String): InstalledVersion? = runCatching {
        val info = packageInfo(pm, packageName, 0)
        InstalledVersion(
            versionName = info.versionName,
            versionCode = info.longVersionCode,
            sourceDir = info.applicationInfo?.sourceDir,
            launchable = pm.getLaunchIntentForPackage(packageName) != null,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
        )
    }.getOrNull()

    /** Декодирует AndroidManifest.xml в текст и сохраняет в cacheDir/manifest. Возвращает путь. */
    suspend fun exportManifest(context: Context, path: String): String = withContext(Dispatchers.IO) {
        val file = File(path)
        val bytes = readEntry(file, "AndroidManifest.xml")
        val xml = AxmlDecoder.decode(bytes).toXml()
        val dir = File(context.cacheDir, "manifest").apply { mkdirs() }
        val base = file.nameWithoutExtension.replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_").take(80).ifEmpty { "apk" }
        val out = File(dir, "${base}_AndroidManifest.xml")
        out.writeText(xml)
        out.absolutePath
    }

    // ------------------------------------------------------------------ PackageManager

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String, flags: Int): PackageInfo? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageArchiveInfo(path, flags)
        }
    }.getOrNull()

    @Suppress("DEPRECATION")
    fun packageInfo(pm: PackageManager, packageName: String, flags: Int): PackageInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            pm.getPackageInfo(packageName, flags)
        }

    private fun component(info: ComponentInfo, launchers: Set<String>, extra: String?): ApkComponent {
        val name = info.name ?: ""
        return ApkComponent(name = name, exported = info.exported, extra = extra, launcher = name in launchers)
    }

    private fun resolveName(name: String, pkg: String): String = when {
        name.startsWith(".") -> pkg + name
        !name.contains('.') && pkg.isNotEmpty() -> "$pkg.$name"
        else -> name
    }

    private fun manifestComponents(manifest: XmlElement?, tag: String, pkg: String, launchers: Set<String>): List<ApkComponent> {
        val application = manifest?.elements("application")?.firstOrNull() ?: return emptyList()
        return application.elements(tag).mapNotNull { e ->
            val name = e.attr("name")?.let { resolveName(it, pkg) } ?: return@mapNotNull null
            val exported = e.attr("exported")?.let { it == "true" } ?: e.elements("intent-filter").isNotEmpty()
            val extra = if (tag == "provider") e.attr("authorities") else e.attr("permission")
            ApkComponent(name, exported, extra, name in launchers)
        }
    }

    /** Активности (и цели alias'ов) с фильтром MAIN + LAUNCHER. */
    private fun launcherNames(manifest: XmlElement, pkg: String): Set<String> {
        val application = manifest.elements("application").firstOrNull() ?: return emptySet()
        val result = HashSet<String>()
        (application.elements("activity") + application.elements("activity-alias")).forEach { e ->
            val isLauncher = e.elements("intent-filter").any { f ->
                f.elements("action").any { it.attr("name") == "android.intent.action.MAIN" } &&
                    f.elements("category").any { it.attr("name") == "android.intent.category.LAUNCHER" }
            }
            if (isLauncher) {
                e.attr("name")?.let { result += resolveName(it, pkg) }
                e.attr("targetActivity")?.let { result += resolveName(it, pkg) }
            }
        }
        return result
    }

    private fun permission(pm: PackageManager, name: String, declared: PermissionInfo?): ApkPermission {
        val info = declared ?: runCatching { pm.getPermissionInfo(name, 0) }.getOrNull()
        val level = when {
            info == null -> PermissionLevel.Unknown
            else -> when (info.protection) {
                PermissionInfo.PROTECTION_DANGEROUS -> PermissionLevel.Dangerous
                PermissionInfo.PROTECTION_NORMAL -> PermissionLevel.Normal
                else -> PermissionLevel.Signature
            }
        }
        val label = info?.let { runCatching { it.loadLabel(pm).toString() }.getOrNull() }?.takeIf { it.isNotBlank() && it != name }
        val description = info?.let { runCatching { it.loadDescription(pm)?.toString() }.getOrNull() }?.takeIf { it.isNotBlank() }
        return ApkPermission(name, label, description, level)
    }

    // ------------------------------------------------------------------ Подписи

    @Suppress("DEPRECATION")
    private fun certificates(pm: PackageManager, path: String, pi: PackageInfo?, file: File): List<ApkCertificate> {
        val signing = pi?.signingInfo
        var sigs: Array<Signature>? = signing?.let { if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory }
        if (sigs.isNullOrEmpty()) sigs = pi?.signatures
        if (sigs.isNullOrEmpty()) sigs = archiveInfo(pm, path, PackageManager.GET_SIGNATURES)?.signatures
        val factory = CertificateFactory.getInstance("X.509")
        val certs: List<X509Certificate> = if (!sigs.isNullOrEmpty()) {
            sigs.mapNotNull { s ->
                runCatching { factory.generateCertificate(ByteArrayInputStream(s.toByteArray())) as X509Certificate }.getOrNull()
            }
        } else {
            // Запасной путь для v1-подписи: PKCS#7-блоки в META-INF.
            runCatching {
                ZipFile(file).use { zip ->
                    zip.entries().asSequence()
                        .filter { e ->
                            val n = e.name.uppercase()
                            n.startsWith("META-INF/") && (n.endsWith(".RSA") || n.endsWith(".DSA") || n.endsWith(".EC"))
                        }
                        .flatMap { e -> zip.getInputStream(e).use { factory.generateCertificates(it).toList() } }
                        .filterIsInstance<X509Certificate>()
                        .distinctBy { it.encoded.contentHashCode() }
                        .toList()
                }
            }.getOrDefault(emptyList())
        }
        return certs.map { describe(it) }
    }

    private fun describe(cert: X509Certificate): ApkCertificate {
        val encoded = cert.encoded
        val key = cert.publicKey
        val keyInfo = buildString {
            append(key.algorithm)
            val bits = when (key) {
                is RSAPublicKey -> key.modulus.bitLength()
                is ECPublicKey -> key.params.curve.field.fieldSize
                is DSAPublicKey -> key.params?.p?.bitLength() ?: 0
                else -> 0
            }
            if (bits > 0) append(", ").append(bits).append(" бит")
        }
        return ApkCertificate(
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            serial = cert.serialNumber.toString(16).uppercase(),
            notBefore = cert.notBefore.time,
            notAfter = cert.notAfter.time,
            signatureAlgorithm = cert.sigAlgName,
            publicKey = keyInfo,
            md5 = digest("MD5", encoded),
            sha1 = digest("SHA-1", encoded),
            sha256 = digest("SHA-256", encoded),
        )
    }

    private fun digest(alg: String, data: ByteArray): String =
        MessageDigest.getInstance(alg).digest(data).joinToString(":") { "%02X".format(it) }

    /** Схемы v2/v3/v3.1 по идентификаторам блоков в APK Signing Block. */
    private fun signingBlockSchemes(file: File): List<String> = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val len = raf.length()
            if (len < 22) return emptyList()
            // Поиск End of Central Directory с конца файла (комментарий до 64 КБ).
            val tailSize = minOf(len, 22L + 0xFFFF).toInt()
            val tail = ByteArray(tailSize)
            raf.seek(len - tailSize)
            raf.readFully(tail)
            var eocd = -1
            for (i in tailSize - 22 downTo 0) {
                if (tail[i] == 0x50.toByte() && tail[i + 1] == 0x4b.toByte() && tail[i + 2] == 0x05.toByte() && tail[i + 3] == 0x06.toByte()) {
                    eocd = i; break
                }
            }
            if (eocd < 0) return emptyList()
            val cdOffset = le32(tail, eocd + 16)
            if (cdOffset < 32 || cdOffset >= len) return emptyList()
            val footer = ByteArray(24)
            raf.seek(cdOffset - 24)
            raf.readFully(footer)
            if (String(footer, 8, 16, Charsets.US_ASCII) != "APK Sig Block 42") return emptyList()
            val blockSize = le64(footer, 0)
            val blockStart = cdOffset - blockSize - 8
            if (blockSize < 24 || blockSize > Int.MAX_VALUE || blockStart < 0) return emptyList()
            val ids = ArrayList<Long>()
            var p = blockStart + 8
            val end = cdOffset - 24
            val head = ByteArray(12)
            while (p + 12 <= end && ids.size < 64) {
                raf.seek(p)
                raf.readFully(head)
                val pairLen = le64(head, 0)
                if (pairLen < 4 || p + 8 + pairLen > end) break
                ids += le32(head, 8)
                p += 8 + pairLen
            }
            buildList {
                if (0x7109871aL in ids) add("v2")
                if (0xf05368c0L in ids) add("v3")
                if (0x1b93ad61L in ids) add("v3.1")
            }
        }
    }.getOrDefault(emptyList())

    private fun le32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or
            ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)

    private fun le64(b: ByteArray, o: Int): Long = le32(b, o) or (le32(b, o + 4) shl 32)

    // ------------------------------------------------------------------ Содержимое ZIP

    private class ZipScan(
        val abis: List<AbiLibraries>,
        val dex: List<DexEntry>,
        val stats: ApkFileStats,
        val hasV1: Boolean,
    )

    private val dexName = Regex("classes\\d*\\.dex")

    private fun scanZip(file: File): ZipScan = runCatching {
        ZipFile(file).use { zip ->
            val libs = LinkedHashMap<String, MutableList<Pair<String, Long>>>()
            val dex = ArrayList<DexEntry>()
            var count = 0
            var total = 0L
            var arsc = 0L
            var res = 0
            var assets = 0
            var assetsSize = 0L
            var metaInf = 0
            var hasSf = false
            var hasBlock = false
            for (e in zip.entries()) {
                count++
                val size = e.size.coerceAtLeast(0)
                total += size
                val n = e.name
                when {
                    n.startsWith("lib/") && n.endsWith(".so") -> {
                        val parts = n.split('/')
                        if (parts.size >= 3) libs.getOrPut(parts[1]) { ArrayList() } += parts.last() to size
                    }
                    dexName.matches(n) -> dex += DexEntry(n, size, e.compressedSize.coerceAtLeast(0))
                    n == "resources.arsc" -> arsc = size
                    n.startsWith("res/") -> res++
                    n.startsWith("assets/") && !e.isDirectory -> { assets++; assetsSize += size }
                    n.startsWith("META-INF/") -> {
                        metaInf++
                        val u = n.uppercase()
                        if (u.endsWith(".SF")) hasSf = true
                        if (u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC")) hasBlock = true
                    }
                }
            }
            val pages = if (libs.keys.any { it == "arm64-v8a" || it == "x86_64" }) check16k(file, zip) else emptyMap()
            ZipScan(
                abis = libs.map { (abi, list) ->
                    val p = pages[abi]
                    AbiLibraries(abi, list.map { it.first }.sorted(), list.sumOf { it.second }, p?.first.orEmpty().sorted(), p?.second.orEmpty().sorted())
                }.sortedBy { abiOrder(it.abi) },
                dex = dex.sortedWith(compareBy({ it.name.length }, { it.name })),
                stats = ApkFileStats(count, total, arsc, res, assets, assetsSize, metaInf),
                hasV1 = hasSf && hasBlock,
            )
        }
    }.getOrElse { ZipScan(emptyList(), emptyList(), ApkFileStats(0, 0, 0, 0, 0, 0, 0), false) }

    /** ABI → (библиотеки с LOAD < 16 КБ, несжатые библиотеки не на границе 16 КБ в ZIP). */
    private fun check16k(file: File, zip: ZipFile): Map<String, Pair<List<String>, List<String>>> {
        val elf = HashMap<String, MutableList<String>>()
        val offsets = HashMap<String, MutableList<String>>()
        for (e in zip.entries()) {
            val n = e.name
            if (!n.startsWith("lib/") || !n.endsWith(".so")) continue
            val parts = n.split('/')
            if (parts.size < 3 || (parts[1] != "arm64-v8a" && parts[1] != "x86_64")) continue
            val head = runCatching { zip.getInputStream(e).use { it.readNBytes(64 * 1024) } }.getOrNull() ?: continue
            // Битый .so не должен обнулять сведения обо всём APK.
            val align = runCatching { client.wexmanager.core.elf.Elf.loadAlignment(head) }.getOrNull()
            if (align != null && align < 16384) elf.getOrPut(parts[1]) { ArrayList() } += parts.last()
        }
        // Смещение данных несжатой записи — только через commons-compress.
        runCatching {
            org.apache.commons.compress.archivers.zip.ZipFile.builder().setFile(file).get().use { z ->
                for (e in z.entries) {
                    val n = e.name
                    if (!n.startsWith("lib/") || !n.endsWith(".so") || e.method != org.apache.commons.compress.archivers.zip.ZipArchiveEntry.STORED) continue
                    val parts = n.split('/')
                    if (parts.size < 3 || (parts[1] != "arm64-v8a" && parts[1] != "x86_64")) continue
                    if (e.dataOffset % 16384 != 0L) offsets.getOrPut(parts[1]) { ArrayList() } += parts.last()
                }
            }
        }
        return (elf.keys + offsets.keys).associateWith { (elf[it].orEmpty()) to (offsets[it].orEmpty()) }
    }

    private fun abiOrder(abi: String) = when (abi) {
        "arm64-v8a" -> 0
        "armeabi-v7a" -> 1
        "armeabi" -> 2
        "x86_64" -> 3
        "x86" -> 4
        else -> 5
    }

    /** Манифест реальных APK — десятки-сотни КБ; лимит защищает от OOM на «zip-бомбах». */
    private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024

    private fun readEntry(file: File, name: String): ByteArray = ZipFile(file).use { zip ->
        val entry = zip.getEntry(name) ?: throw IllegalStateException("$name не найден в архиве")
        if (entry.size > MAX_ENTRY_BYTES) throw IllegalStateException("$name слишком большой")
        zip.getInputStream(entry).use { input ->
            val out = ByteArrayOutputStream(entry.size.coerceIn(0L, MAX_ENTRY_BYTES.toLong()).toInt())
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_ENTRY_BYTES) throw IllegalStateException("$name слишком большой")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    }

    // ------------------------------------------------------------------ Иконки

    /** Адаптивные иконки рисуются без системной маски (слои на всю площадь), обычные — как есть. */
    fun renderIcon(drawable: Drawable, size: Int): AppIconBitmap {
        if (drawable is AdaptiveIconDrawable) {
            val bmp = createBitmap(size, size)
            val canvas = Canvas(bmp)
            val inset = size / 4
            listOfNotNull(drawable.background, drawable.foreground).forEach { layer ->
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(canvas)
            }
            return AppIconBitmap(bmp, adaptive = true)
        }
        return AppIconBitmap(drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888), adaptive = false)
    }

    // ------------------------------------------------------------------ Версии Android

    fun androidVersion(api: Int): String = when (api) {
        1 -> "1.0"
        2 -> "1.1"
        3 -> "1.5 Cupcake"
        4 -> "1.6 Donut"
        5 -> "2.0 Eclair"
        6 -> "2.0.1 Eclair"
        7 -> "2.1 Eclair"
        8 -> "2.2 Froyo"
        9 -> "2.3 Gingerbread"
        10 -> "2.3.3 Gingerbread"
        11 -> "3.0 Honeycomb"
        12 -> "3.1 Honeycomb"
        13 -> "3.2 Honeycomb"
        14 -> "4.0 Ice Cream Sandwich"
        15 -> "4.0.3 Ice Cream Sandwich"
        16 -> "4.1 Jelly Bean"
        17 -> "4.2 Jelly Bean"
        18 -> "4.3 Jelly Bean"
        19 -> "4.4 KitKat"
        20 -> "4.4W KitKat"
        21 -> "5.0 Lollipop"
        22 -> "5.1 Lollipop"
        23 -> "6.0 Marshmallow"
        24 -> "7.0 Nougat"
        25 -> "7.1 Nougat"
        26 -> "8.0 Oreo"
        27 -> "8.1 Oreo"
        28 -> "9 Pie"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
        else -> if (api > 37) "API $api" else "—"
    }
}
