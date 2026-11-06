package client.wexmanager.core.archive

import android.util.LruCache
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.ops.ConflictChoice
import client.wexmanager.core.ops.ConflictDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

enum class ArchiveFormat(val extension: String, val label: String, val supportsLevel: Boolean) {
    Zip("zip", "ZIP", true),
    SevenZ("7z", "7z", true),
    Tar("tar", "TAR", false),
    TarGz("tar.gz", "TAR.GZ", true),
    TarXz("tar.xz", "TAR.XZ", true),
    TarBz2("tar.bz2", "TAR.BZ2", true),
}

/** Тип архива для чтения. */
enum class ArchiveType { Zip, SevenZ, Rar, Tar, TarGz, TarXz, TarBz2, Gz, Xz, Bz2 }

class ArchiveIndex(
    val archive: File,
    val type: ArchiveType,
    private val tree: Map<String, List<FileItem>>,
    val totalEntries: Int,
    val totalSize: Long,
) {
    fun list(inner: String): List<FileItem>? = tree[inner]
    fun contains(inner: String): Boolean = tree.containsKey(inner)
}

class UnsupportedArchiveException(name: String) : IOException("Формат архива не поддерживается: $name")

/** Архив защищён паролем: [wrong] — пароль был, но не подошёл. */
class ArchivePasswordException(val wrong: Boolean) :
    IOException(if (wrong) "Неверный пароль архива" else "Архив защищён паролем")

object ArchiveEngine {

    private val cache = LruCache<String, ArchiveIndex>(6)

    fun detect(file: File): ArchiveType? {
        val n = file.name.lowercase()
        return when {
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> ArchiveType.TarGz
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> ArchiveType.TarXz
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> ArchiveType.TarBz2
            n.endsWith(".tar") -> ArchiveType.Tar
            n.endsWith(".7z") -> ArchiveType.SevenZ
            n.endsWith(".rar") || n.endsWith(".cbr") -> ArchiveType.Rar
            n.endsWith(".gz") -> ArchiveType.Gz
            n.endsWith(".xz") -> ArchiveType.Xz
            n.endsWith(".bz2") -> ArchiveType.Bz2
            n.endsWith(".zip") || n.endsWith(".apk") || n.endsWith(".jar") || n.endsWith(".aar") ||
                n.endsWith(".apks") || n.endsWith(".xapk") || n.endsWith(".apkm") || n.endsWith(".obb") ||
                n.endsWith(".war") || n.endsWith(".epub") || n.endsWith(".docx") || n.endsWith(".xlsx") ||
                n.endsWith(".pptx") || n.endsWith(".odt") || n.endsWith(".cbz") || n.endsWith(".aab") -> ArchiveType.Zip
            else -> sniff(file)
        }
    }

    private fun sniff(file: File): ArchiveType? = runCatching {
        FileInputStream(file).use { s ->
            val h = ByteArray(6)
            val n = s.read(h)
            when {
                n >= 4 && h[0] == 0x50.toByte() && h[1] == 0x4B.toByte() && h[2] == 3.toByte() && h[3] == 4.toByte() -> ArchiveType.Zip
                n >= 6 && h[0] == '7'.code.toByte() && h[1] == 'z'.code.toByte() && h[2] == 0xBC.toByte() -> ArchiveType.SevenZ
                n >= 2 && h[0] == 0x1F.toByte() && h[1] == 0x8B.toByte() -> ArchiveType.Gz
                n >= 4 && h[0] == 'R'.code.toByte() && h[1] == 'a'.code.toByte() && h[2] == 'r'.code.toByte() && h[3] == '!'.code.toByte() -> ArchiveType.Rar
                else -> null
            }
        }
    }.getOrNull()

    fun isSupported(file: File): Boolean = detect(file) != null

    suspend fun index(file: File): ArchiveIndex = withContext(Dispatchers.IO) {
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}"
        cache.get(key)?.let { return@withContext it }
        val type = detect(file) ?: throw UnsupportedArchiveException(file.name)
        val builder = TreeBuilder(file.absolutePath)
        when (type) {
            ArchiveType.Zip -> openZip(file).use { zf ->
                for (e in zf.entries) {
                    currentCoroutineContext().ensureActive()
                    builder.add(e.name, e.isDirectory, e.size, e.time, e.unixMode)
                }
            }
            ArchiveType.SevenZ -> openSevenZ(file, null).use { sz ->
                for (e in sz.entries) {
                    currentCoroutineContext().ensureActive()
                    builder.add(e.name, e.isDirectory, e.size, if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L, -1)
                }
            }
            ArchiveType.Tar, ArchiveType.TarGz, ArchiveType.TarXz, ArchiveType.TarBz2 -> openTar(file, type).use { tar ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val e = tar.nextEntry ?: break
                    builder.add(e.name, e.isDirectory, e.size, e.modTime.time, e.mode)
                }
            }
            ArchiveType.Rar -> if (Unrar.handles(file)) {
                for (e in Unrar.list(file, null)) builder.add(e.name, e.isDir, e.size, e.time, -1)
            } else openRar(file, null).use { rar ->
                for (h in rar.fileHeaders) {
                    currentCoroutineContext().ensureActive()
                    builder.add(h.fileName.replace('\\', '/'), h.isDirectory, h.fullUnpackSize, h.mTime?.time ?: 0L, -1)
                }
            }
            ArchiveType.Gz, ArchiveType.Xz, ArchiveType.Bz2 -> {
                builder.add(singleName(file), false, -1, file.lastModified(), -1)
            }
        }
        val index = builder.build(file, type)
        cache.put(key, index)
        index
    }

    fun invalidate(file: File) {
        // snapshot() у заглушки android.jar в JVM-тестах — null.
        @Suppress("SENSELESS_COMPARISON", "UNNECESSARY_SAFE_CALL")
        cache.snapshot()?.keys?.filter { it.startsWith(file.absolutePath + ":") }?.forEach { cache.remove(it) }
    }

    private fun singleName(file: File): String = file.name.substringBeforeLast('.')

    /**
     * ZIP с именами в кодировке системы, где его создали: «Проводник» Windows пишет имена в OEM-кодировке
     * (для русского — CP866) без флага UTF-8. Если такие имена не читаются как UTF-8, архив
     * переоткрывается в кодировке по языку устройства.
     */
    private fun openZip(file: File): ZipFile {
        val zf = ZipFile.builder().setFile(file).get()
        val legacy = try { legacyNameCharset(zf) } catch (e: Throwable) { zf.close(); throw e } ?: return zf
        zf.close()
        return ZipFile.builder().setFile(file).setCharset(legacy).get()
    }

    /** Кодировка имён для архива без флага UTF-8 с «битыми» в UTF-8 именами; null — UTF-8 подходит. */
    private fun legacyNameCharset(zf: ZipFile): java.nio.charset.Charset? {
        // Сырые байты имени: строгий UTF-8 (без замены «?») — отличает CP866/GBK от настоящего UTF-8.
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        val garbled = zf.entries.asSequence().take(20_000).any { e ->
            if (e.generalPurposeBit.usesUTF8ForNames()) return@any false
            val raw = e.rawName ?: return@any false
            raw.any { it < 0 } && runCatching { strict.reset().decode(java.nio.ByteBuffer.wrap(raw)) }.isFailure
        }
        return if (garbled) legacyCharsetFor(java.util.Locale.getDefault()) else null
    }

    /** OEM-кодировка Windows для языка (так zip-архиватор «Проводника» пишет имена). */
    internal fun legacyCharsetFor(locale: java.util.Locale): java.nio.charset.Charset {
        val name = when (locale.language) {
            "ru", "uk", "be", "kk", "ky", "tg", "mn", "bg", "sr", "mk" -> "IBM866"
            "zh" -> "GBK"
            "ja" -> "Shift_JIS"
            "ko" -> "x-windows-949"
            "el" -> "x-IBM737"
            "tr" -> "IBM857"
            "he", "iw" -> "IBM862"
            "ar", "fa" -> "IBM864"
            "pl", "cs", "sk", "hu", "sl", "hr", "ro" -> "IBM852"
            else -> "IBM437"
        }
        return runCatching { java.nio.charset.Charset.forName(name) }.getOrElse { java.nio.charset.Charset.forName("IBM437") }
    }

    /** Та же кодировка имён для zip4j (зашифрованные архивы). */
    private fun zip4jCharset(file: File): java.nio.charset.Charset? =
        runCatching { ZipFile.builder().setFile(file).get().use { legacyNameCharset(it) } }.getOrNull()

    private fun openRar(file: File, password: String?): com.github.junrar.Archive = try {
        val a = if (password != null) com.github.junrar.Archive(file, password) else com.github.junrar.Archive(file)
        if (password == null && runCatching { a.isPasswordProtected }.getOrDefault(false)) {
            a.close()
            throw ArchivePasswordException(wrong = false)
        }
        a
    } catch (e: com.github.junrar.exception.UnsupportedRarVersionException) {
        throw UnsupportedArchiveException("${file.name}: эта версия RAR (RAR5) пока не поддерживается")
    } catch (e: com.github.junrar.exception.WrongPasswordException) {
        // RAR5 с шифрованными заголовками сообщает так и об отсутствующем пароле.
        throw ArchivePasswordException(wrong = password != null)
    } catch (e: com.github.junrar.exception.UnsupportedRarEncryptedException) {
        throw IOException("Зашифрованный RAR не поддерживается")
    } catch (e: com.github.junrar.exception.RarException) {
        throw IOException("Ошибка RAR: ${e.message ?: e.javaClass.simpleName}")
    }

    /** Переводит ошибки 7z, связанные с паролем, в [ArchivePasswordException]. */
    internal inline fun <T> sevenZPasswordGuard(password: String?, block: () -> T): T = try {
        block()
    } catch (e: org.apache.commons.compress.PasswordRequiredException) {
        throw ArchivePasswordException(wrong = false)
    } catch (e: IOException) {
        val msg = e.message.orEmpty()
        val notAboutPassword = "ENOSPC" in msg || "No space" in msg || "EACCES" in msg || "EROFS" in msg ||
            e is org.apache.commons.compress.MemoryLimitException || e is java.io.FileNotFoundException
        if (password != null && e !is ArchivePasswordException && !notAboutPassword) throw ArchivePasswordException(wrong = true) else throw e
    }

    private fun openSevenZ(file: File, password: String?): SevenZFile {
        val b = SevenZFile.builder().setFile(file)
        if (password != null) b.setPassword(password.toCharArray())
        return b.get()
    }

    /** Считает прочитанные (сжатые) байты — по ним идёт прогресс tar.*, где размер распакованного заранее неизвестен. */
    private class CountingStream(inner: InputStream) : java.io.FilterInputStream(inner) {
        @Volatile var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) count++ }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }
        override fun skip(n: Long): Long = super.skip(n).also { count += it }
    }

    private fun openTar(file: File, type: ArchiveType, counter: CountingStream? = null): TarArchiveInputStream {
        val raw: InputStream = BufferedInputStream(counter ?: FileInputStream(file), 1 shl 16)
        return try {
            val decompressed = when (type) {
                ArchiveType.TarGz -> GzipCompressorInputStream(raw, true)
                ArchiveType.TarXz -> XZCompressorInputStream(raw, true)
                ArchiveType.TarBz2 -> BZip2CompressorInputStream(raw, true)
                else -> raw
            }
            TarArchiveInputStream(decompressed)
        } catch (e: Throwable) {
            raw.close() // повреждённый заголовок — не оставляем открытый дескриптор
            throw e
        }
    }

    private fun openSingle(file: File, type: ArchiveType): InputStream {
        val raw = BufferedInputStream(FileInputStream(file), 1 shl 16)
        return try {
            when (type) {
                ArchiveType.Gz -> GzipCompressorInputStream(raw, true)
                ArchiveType.Xz -> XZCompressorInputStream(raw, true)
                ArchiveType.Bz2 -> BZip2CompressorInputStream(raw, true)
                else -> raw
            }
        } catch (e: Throwable) {
            raw.close()
            throw e
        }
    }

    /** Рекурсивное удаление, не заходящее по символическим ссылкам (удаляется сама ссылка). */
    private fun deleteNoFollow(f: File) {
        if (!java.nio.file.Files.isSymbolicLink(f.toPath()) && f.isDirectory) f.listFiles()?.forEach(::deleteNoFollow)
        f.delete()
    }

    private fun normalize(name: String): String =
        name.replace('\\', '/').trimStart('/').removePrefix("./").trimEnd('/')

    private class TreeBuilder(private val archivePath: String) {
        private val dirs = HashMap<String, MutableMap<String, FileItem>>()
        var count = 0
        var size = 0L

        init {
            dirs[""] = LinkedHashMap()
        }

        fun add(rawName: String, isDir: Boolean, entrySize: Long, time: Long, mode: Int) {
            val name = normalize(rawName)
            if (name.isEmpty() || name.split('/').any { it == ".." }) return
            count++
            if (entrySize > 0) size += entrySize
            ensureDir(name.substringBeforeLast('/', ""))
            val parent = name.substringBeforeLast('/', "")
            val simple = name.substringAfterLast('/')
            val item = FileItem(
                path = name,
                name = simple,
                isDirectory = isDir,
                size = if (isDir) 0 else entrySize,
                lastModified = time,
                mode = if (mode > 0) mode else -1,
                archivePath = archivePath,
            )
            val children = dirs.getOrPut(parent) { LinkedHashMap() }
            if (isDir) {
                dirs.getOrPut(name) { LinkedHashMap() }
                val existing = children[simple]
                if (existing == null || existing.lastModified == 0L) children[simple] = item
            } else {
                children[simple] = item
            }
        }

        private fun ensureDir(path: String) {
            if (path.isEmpty() || dirs.containsKey(path)) return
            ensureDir(path.substringBeforeLast('/', ""))
            dirs[path] = LinkedHashMap()
            val parent = path.substringBeforeLast('/', "")
            val simple = path.substringAfterLast('/')
            dirs.getOrPut(parent) { LinkedHashMap() }.putIfAbsent(
                simple,
                FileItem(path = path, name = simple, isDirectory = true, size = 0, lastModified = 0, archivePath = archivePath),
            )
        }

        fun build(file: File, type: ArchiveType): ArchiveIndex {
            val tree = dirs.mapValues { (path, children) ->
                children.values.map { item ->
                    if (item.isDirectory) item.copy(childCount = dirs[item.path]?.size ?: 0) else item
                }
            }
            return ArchiveIndex(file, type, tree, count, size).also { if (dirs.isEmpty()) Unit }
        }
    }

    // ---------------------------------------------------------------- Распаковка

    /**
     * @param entries пути внутри архива (файлы или папки), null — всё.
     * @param basePath общая родительская папка выбранных элементов — отрезается при распаковке.
     * @param onProgress name + дельта байт (или -1 — элемент завершён).
     */
    suspend fun extract(
        archive: File,
        destDir: File,
        entries: List<String>?,
        password: String?,
        basePath: String = "",
        onTotal: (items: Int, bytes: Long) -> Unit = { _, _ -> },
        onConflict: suspend (FileItem, FileItem) -> ConflictDecision,
        onProgress: (name: String, delta: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val type = detect(archive) ?: throw UnsupportedArchiveException(archive.name)
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath
        val destPrefix = canonicalDest.trimEnd('/') + "/"
        val filters = entries?.map { normalize(it) }
        val base = normalize(basePath)

        fun accept(name: String): Boolean =
            filters == null || filters.any { name == it || name.startsWith("$it/") }

        fun targetFor(name: String): File? {
            val rel = if (base.isNotEmpty() && name.startsWith("$base/")) name.removePrefix("$base/") else name
            if (rel.isEmpty() || rel.split('/').any { it == ".." }) return null
            val f = File(destDir, rel)
            val cp = f.canonicalPath
            if (cp != canonicalDest && !cp.startsWith(destPrefix)) return null // zip-slip
            return f
        }

        /** [staged] — уже распакованный и проверенный файл (RAR5): переносится на место вместо копирования [input]. */
        suspend fun write(name: String, input: InputStream, isDir: Boolean, time: Long, reportBytes: Boolean = true, staged: File? = null) {
            currentCoroutineContext().ensureActive()
            val target = targetFor(name) ?: return
            if (isDir) {
                target.mkdirs()
                return
            }
            var out = target
            var replaceDir = false
            if (out.exists()) {
                val srcItem = FileItem(name, name.substringAfterLast('/'), false, -1, time, archivePath = archive.path)
                val d = onConflict(srcItem, LocalFileSystem.stat(out.path))
                when (d.choice) {
                    ConflictChoice.Skip -> { onProgress(name, -1); return }
                    ConflictChoice.KeepBoth -> out = File(out.parentFile, LocalFileSystem.uniqueName(out.parent!!, out.name))
                    else -> replaceDir = out.isDirectory
                }
            }
            out.parentFile?.mkdirs()
            // Распаковываем во временный файл и подменяем только после успеха: при ошибке
            // (CRC, неверный пароль, обрыв) существующий файл остаётся нетронутым.
            // Уникальное имя: две распаковки в одну папку не должны делить временный файл.
            // Короткое уникальное имя: длинное исходное + суффикс превысило бы предел длины имени (255 байт).
            val tmp = staged ?: File(out.parentFile, ".wex-${java.util.UUID.randomUUID().toString().take(12)}.part")
            try {
                if (staged == null) BufferedOutputStream(FileOutputStream(tmp), 1 shl 16).use { os ->
                    copyStream(input, os) { if (reportBytes) onProgress(name, it.toLong()) }
                }
                if (replaceDir) {
                    // Папку сначала откладываем в сторону и удаляем только после успешной подмены.
                    val old = File(out.parentFile, ".wex-${java.util.UUID.randomUUID().toString().take(12)}.old")
                    if (!out.renameTo(old)) throw IOException("Не удалось заменить папку ${out.name}")
                    if (!tmp.renameTo(out)) {
                        old.renameTo(out)
                        throw IOException("Не удалось записать ${out.name}")
                    }
                    deleteNoFollow(old)
                } else if (!tmp.renameTo(out)) {
                    // ФС не разрешила замену — копируем поверх, не удаляя оригинал заранее.
                    FileInputStream(tmp).use { src -> FileOutputStream(out).use { dst -> src.copyTo(dst, 1 shl 16) } }
                    tmp.delete()
                }
            } catch (e: Throwable) {
                tmp.delete()
                throw e
            }
            if (time > 0) out.setLastModified(time)
            onProgress(name, -1)
        }

        when (type) {
            ArchiveType.Zip -> {
                val encrypted = openZip(archive).use { zf -> zf.entries.asSequence().any { it.generalPurposeBit.usesEncryption() } }
                if (!encrypted) openZip(archive).use { zf ->
                    val list = zf.entries.toList().filter { accept(normalize(it.name)) }
                    onTotal(list.size, list.sumOf { maxOf(it.size, 0) })
                    for (e in list) {
                        val name = normalize(e.name)
                        if (e.isDirectory) write(name, InputStream.nullInputStream(), true, e.time)
                        else zf.getInputStream(e).use { write(name, it, false, e.time) }
                    }
                } else {
                    // Зашифрованный ZIP (ZipCrypto / AES) — через zip4j.
                    if (password.isNullOrEmpty()) throw ArchivePasswordException(wrong = false)
                    net.lingala.zip4j.ZipFile(archive, password.toCharArray()).use { zf ->
                        zip4jCharset(archive)?.let { zf.charset = it }
                        val headers = zf.fileHeaders.filter { accept(normalize(it.fileName)) }
                        onTotal(headers.size, headers.sumOf { maxOf(it.uncompressedSize, 0) })
                        for (h in headers) {
                            val name = normalize(h.fileName)
                            val time = runCatching { h.lastModifiedTimeEpoch }.getOrDefault(0L)
                            if (h.isDirectory) { write(name, InputStream.nullInputStream(), true, time); continue }
                            try {
                                zf.getInputStream(h).use { write(name, it, false, time) }
                            } catch (e: net.lingala.zip4j.exception.ZipException) {
                                if (e.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD) throw ArchivePasswordException(wrong = true)
                                throw IOException(e.message ?: "Ошибка ZIP")
                            }
                        }
                    }
                }
            }
            ArchiveType.SevenZ -> sevenZPasswordGuard(password) { openSevenZ(archive, password) }.use { sz ->
                val all = sz.entries.toList()
                val accepted = all.filter { accept(normalize(it.name)) }
                onTotal(accepted.size, accepted.sumOf { maxOf(it.size, 0) })
                while (true) {
                    val e: SevenZArchiveEntry = sz.nextEntry ?: break
                    val name = normalize(e.name)
                    if (!accept(name)) continue
                    val time = if (e.hasLastModifiedDate) e.lastModifiedDate.time else 0L
                    if (e.isDirectory) write(name, InputStream.nullInputStream(), true, time)
                    else write(name, SevenZEntryStream(sz, password), false, time)
                }
            }
            ArchiveType.Tar, ArchiveType.TarGz, ArchiveType.TarXz, ArchiveType.TarBz2 -> {
                val counter = CountingStream(FileInputStream(archive))
                onTotal(0, archive.length())
                var reported = 0L
                fun reportCompressed(name: String) {
                    val now = counter.count
                    if (now > reported) { onProgress(name, now - reported); reported = now }
                }
                openTar(archive, type, counter).use { tar ->
                    while (true) {
                        val e: TarArchiveEntry = tar.nextEntry ?: break
                        val name = normalize(e.name)
                        if (!accept(name)) { reportCompressed(name); continue }
                        if (e.isSymbolicLink) {
                            // Ссылка создаётся, только если её цель остаётся внутри папки распаковки.
                            val link = targetFor(name)
                            val dest = e.linkName
                            if (link != null && !dest.startsWith("/")) {
                                val resolved = File(link.parentFile, dest).canonicalPath
                                if (resolved == canonicalDest || resolved.startsWith(destPrefix)) {
                                    link.parentFile?.mkdirs()
                                    runCatching { android.system.Os.symlink(dest, link.path) }
                                }
                            }
                            onProgress(name, -1)
                        } else {
                            write(name, NonClosingStream(tar), e.isDirectory, e.modTime.time, reportBytes = false)
                        }
                        reportCompressed(name)
                    }
                }
            }
            ArchiveType.Rar -> if (Unrar.handles(archive)) {
                val list = Unrar.list(archive, password).filter { accept(normalize(it.name)) }
                val files = list.filter { !it.isDir && !it.link }
                if (password.isNullOrEmpty() && files.any { it.encrypted }) throw ArchivePasswordException(wrong = false)
                onTotal(files.size, files.sumOf { it.size })
                // UnRAR распаковывает во временную папку рядом (та же ФС — перенос без копирования); на место
                // попадают только файлы из завершившейся без ошибок распаковки: неверный пароль или битый CRC
                // не затрут существующие файлы.
                val staging = File(destDir, ".wex-unrar-${java.util.UUID.randomUUID().toString().take(8)}")
                try {
                    val total = files.sumOf { it.size }.coerceAtLeast(1)
                    var reported = 0L
                    val damaged = Unrar.extract(archive, password, if (entries == null) null else files.map { it.name }, staging) { pct ->
                        val now = total * pct / 100
                        if (now > reported) { onProgress(archive.name, now - reported); reported = now }
                    }
                    for (e in list) if (e.isDir) write(normalize(e.name), InputStream.nullInputStream(), true, e.time)
                    // Ссылки не создаются (как и для RAR4): только файлы и папки.
                    for (e in list) if (e.link) onProgress(normalize(e.name), -1)
                    val stagingPath = staging.canonicalPath + "/"
                    for (e in files) {
                        val staged = File(staging, e.name)
                        if (!staged.canonicalPath.startsWith(stagingPath) || !staged.isFile) { onProgress(normalize(e.name), -1); continue }
                        write(normalize(e.name), InputStream.nullInputStream(), false, e.time, reportBytes = false, staged = staged)
                    }
                    if (damaged.isNotEmpty()) throw IOException("Повреждены и не распакованы: " + damaged.take(5).joinToString(", ") + if (damaged.size > 5) " и ещё ${damaged.size - 5}" else "")
                } finally {
                    deleteNoFollow(staging)
                }
            } else {
                val rar = openRar(archive, password)
                try {
                    coroutineScope {
                        // Отмена сразу закрывает архив: это прерывает и долгий пропуск записей solid-архива,
                        // пока поток распаковки висит в чтении из канала.
                        val closer = launch { try { awaitCancellation() } finally { runCatching { rar.close() } } }
                        try {
                            extractRar(rar, password, ::accept, onTotal, ::write)
                        } catch (e: IOException) {
                            currentCoroutineContext().ensureActive() // отмена — это «Отменено», а не ошибка
                            throw e
                        } finally {
                            closer.cancel()
                        }
                    }
                } finally {
                    runCatching { rar.close() }
                }
            }
            ArchiveType.Gz, ArchiveType.Xz, ArchiveType.Bz2 -> openSingle(archive, type).use { s ->
                onTotal(1, -1)
                write(singleName(archive), s, false, archive.lastModified())
            }
        }
    }

    private fun rarError(f: Throwable, h: com.github.junrar.rarfile.FileHeader, password: String?): IOException = when {
        f is ArchivePasswordException -> f
        f is com.github.junrar.exception.WrongPasswordException -> ArchivePasswordException(wrong = password != null)
        f is com.github.junrar.exception.CrcErrorException && password != null && h.isEncrypted -> ArchivePasswordException(wrong = true)
        else -> IOException("${h.fileName}: ${f.message ?: f.javaClass.simpleName}")
    }

    private suspend fun extractRar(
        rar: com.github.junrar.Archive,
        password: String?,
        accept: (String) -> Boolean,
        onTotal: (items: Int, bytes: Long) -> Unit,
        write: suspend (name: String, input: InputStream, isDir: Boolean, time: Long, reportBytes: Boolean) -> Unit,
    ) {
        val list = rar.fileHeaders.filter { accept(normalize(it.fileName.replace('\\', '/'))) }
        onTotal(list.size, list.sumOf { maxOf(it.fullUnpackSize, 0) })
        for (h in list) {
            currentCoroutineContext().ensureActive()
            val name = normalize(h.fileName.replace('\\', '/'))
            val time = h.mTime?.time ?: 0L
            if (h.isDirectory) {
                write(name, InputStream.nullInputStream(), true, time, true)
                continue
            }
            // junrar пишет в OutputStream — распаковываем через канал в отдельном потоке.
            val pipeIn = java.io.PipedInputStream(1 shl 16)
            val pipeOut = java.io.PipedOutputStream(pipeIn)
            var failure: Throwable? = null // видимость обеспечивает Thread.join
            val worker = Thread {
                try { pipeOut.use { rar.extractFile(h, it) } } catch (t: Throwable) { failure = t }
            }.apply { isDaemon = true; start() }
            // На EOF проверяем, не завершился ли рабочий поток ошибкой: тогда write() удалит
            // недописанный файл, а неверный пароль превратится в запрос пароля.
            var consumed = false // запись действительно прочитана (а не пропущена по конфликту)
            val checked = object : java.io.FilterInputStream(pipeIn) {
                private fun onEof() {
                    consumed = true
                    worker.join(30_000)
                    failure?.let { throw rarError(it, h, password) }
                }
                override fun read(): Int = super.read().also { if (it < 0) onEof() }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it < 0) onEof() }
            }
            try {
                write(name, checked, false, time, true)
                // Файл могли пропустить (конфликт) — дочитываем остаток, иначе рабочий поток
                // навсегда заблокируется на заполненном канале.
                val sink = ByteArray(1 shl 16)
                while (pipeIn.read(sink) >= 0) currentCoroutineContext().ensureActive()
            } finally {
                // Закрытие читающей стороны разблокирует рабочий поток при ошибке/отмене.
                runCatching { pipeIn.close() }
                worker.join(10_000)
            }
            // Ошибка в пропущенной записи (чужой пароль, CRC) не должна обрывать всю распаковку.
            if (consumed) failure?.let { throw rarError(it, h, password) }
        }
    }

    /**
     * Проверка целостности без распаковки: каждая запись читается целиком, контрольные суммы сверяются
     * (ZIP — CRC32 вручную, зашифрованный ZIP/7z/RAR/gzip — средствами библиотек).
     * Возвращает список «запись: причина» для повреждённых записей.
     */
    suspend fun verify(
        archive: File,
        password: String?,
        onTotal: (items: Int, bytes: Long) -> Unit = { _, _ -> },
        onProgress: (name: String, delta: Long) -> Unit,
    ): List<String> = withContext(Dispatchers.IO) {
        val type = detect(archive) ?: throw UnsupportedArchiveException(archive.name)
        val bad = ArrayList<String>()
        val buf = ByteArray(1 shl 16)
        // Для сжатых tar/gz прогресс считаем по прочитанным сжатым байтам (общий размер известен только для них).
        var counter: CountingStream? = null
        var counted = 0L
        suspend fun drain(name: String, input: InputStream, crc: java.util.zip.CRC32? = null) {
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                crc?.update(buf, 0, n)
                val c = counter
                if (c == null) onProgress(name, n.toLong()) else { onProgress(name, c.count - counted); counted = c.count }
            }
        }
        // Пароль подтверждён, если хотя бы одна запись уже прочиталась: дальнейшие ошибки — повреждение, а не пароль.
        var passwordConfirmed = false
        suspend fun check(name: String, block: suspend () -> Unit) {
            try {
                block()
                if (password != null) passwordConfirmed = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: ArchivePasswordException) {
                if (!passwordConfirmed) throw e
                bad += "$name: данные повреждены (контрольная сумма не совпадает)"
            } catch (e: org.apache.commons.compress.archivers.zip.UnsupportedZipFeatureException) {
                bad += "$name: метод сжатия не поддерживается — проверить нельзя"
            } catch (e: Exception) {
                bad += "$name: ${e.message ?: e.javaClass.simpleName}"
            }
            onProgress(name, -1)
        }
        when (type) {
            ArchiveType.Zip -> {
                val encrypted = openZip(archive).use { zf -> zf.entries.asSequence().any { it.generalPurposeBit.usesEncryption() } }
                if (!encrypted) openZip(archive).use { zf ->
                    val list = zf.entries.toList().filter { !it.isDirectory }
                    onTotal(list.size, list.sumOf { maxOf(it.size, 0) })
                    for (e in list) check(e.name) {
                        val crc = java.util.zip.CRC32()
                        zf.getInputStream(e).use { drain(e.name, it, crc) }
                        if (e.crc != -1L && crc.value != e.crc) throw IOException("контрольная сумма не совпадает")
                    }
                } else {
                    if (password.isNullOrEmpty()) throw ArchivePasswordException(wrong = false)
                    net.lingala.zip4j.ZipFile(archive, password.toCharArray()).use { zf ->
                        zip4jCharset(archive)?.let { zf.charset = it }
                        val headers = zf.fileHeaders.filter { !it.isDirectory }
                        onTotal(headers.size, headers.sumOf { maxOf(it.uncompressedSize, 0) })
                        for (h in headers) check(h.fileName) {
                            try {
                                zf.getInputStream(h).use { drain(h.fileName, it) }
                            } catch (e: net.lingala.zip4j.exception.ZipException) {
                                if (e.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD) throw ArchivePasswordException(wrong = true)
                                throw e
                            }
                        }
                    }
                }
            }
            ArchiveType.SevenZ -> sevenZPasswordGuard(password) { openSevenZ(archive, password) }.use { sz ->
                val all = sz.entries.toList().filter { !it.isDirectory }
                onTotal(all.size, all.sumOf { maxOf(it.size, 0) })
                while (true) {
                    val e: SevenZArchiveEntry = sz.nextEntry ?: break
                    if (e.isDirectory) continue
                    check(e.name) { drain(e.name, SevenZEntryStream(sz, password)) }
                }
            }
            ArchiveType.Rar -> if (Unrar.handles(archive)) {
                val list = Unrar.list(archive, password).filter { !it.isDir && !it.link }
                if (password.isNullOrEmpty() && list.any { it.encrypted }) throw ArchivePasswordException(wrong = false)
                onTotal(list.size, list.sumOf { it.size })
                bad += Unrar.test(archive, password)
                for (e in list) { onProgress(e.name, e.size); onProgress(e.name, -1) }
            } else openRar(archive, password).use { rar ->
                val list = rar.fileHeaders.filter { !it.isDirectory }
                onTotal(list.size, list.sumOf { maxOf(it.fullUnpackSize, 0) })
                val job = currentCoroutineContext()[kotlinx.coroutines.Job]
                for (h in list) check(h.fileName) {
                    currentCoroutineContext().ensureActive()
                    try {
                        // junrar сам сверяет CRC при распаковке; пишем в «никуда», считая байты.
                        // Отмена проверяется на каждой порции — иначе большая запись не прерывалась бы.
                        rar.extractFile(h, object : java.io.OutputStream() {
                            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
                            override fun write(b: ByteArray, off: Int, len: Int) {
                                if (job?.isActive == false) throw java.io.InterruptedIOException("отменено")
                                onProgress(h.fileName, len.toLong())
                            }
                        })
                    } catch (e: Exception) {
                        currentCoroutineContext().ensureActive()
                        throw rarError(e, h, password)
                    }
                }
            }
            ArchiveType.Tar, ArchiveType.TarGz, ArchiveType.TarXz, ArchiveType.TarBz2 -> {
                val c = CountingStream(FileInputStream(archive)).also { counter = it }
                val raw = BufferedInputStream(c, 1 shl 16)
                val decompressed: InputStream = try {
                    when (type) {
                        ArchiveType.TarGz -> GzipCompressorInputStream(raw, true)
                        ArchiveType.TarXz -> XZCompressorInputStream(raw, true)
                        ArchiveType.TarBz2 -> BZip2CompressorInputStream(raw, true)
                        else -> raw
                    }
                } catch (e: Throwable) {
                    raw.close()
                    throw e
                }
                TarArchiveInputStream(decompressed).use { tar ->
                onTotal(-1, archive.length())
                while (true) {
                    val e = try {
                        tar.nextEntry ?: break
                    } catch (ex: IOException) {
                        bad += "${archive.name}: ${ex.message ?: "архив повреждён"}"
                        break
                    }
                    if (!e.isDirectory) check(e.name) { drain(e.name, tar) }
                }
                // Дочитываем хвост распаковщика: CRC gzip и индекс xz проверяются только в самом конце потока.
                if (decompressed !== raw) check(archive.name) { drain(archive.name, decompressed) }
                }
            }
            ArchiveType.Gz, ArchiveType.Xz, ArchiveType.Bz2 -> check(singleName(archive)) {
                openSingle(archive, type).use { drain(singleName(archive), it) }
            }
        }
        bad
    }

    /** Извлекает один файл во временную папку (для просмотра без распаковки). */
    suspend fun extractToCache(archive: File, entry: String, cacheRoot: File): File = withContext(Dispatchers.IO) {
        val dir = File(cacheRoot, "archive/${(archive.absolutePath + archive.lastModified()).hashCode().toUInt()}")
        val target = File(dir, normalize(entry))
        if (target.exists() && target.length() > 0) {
            dir.setLastModified(System.currentTimeMillis()) // используется — уборщик кеша не тронет
            return@withContext target
        }
        extract(
            archive, dir, listOf(entry), null,
            onConflict = { _, _ -> ConflictDecision(ConflictChoice.Replace, true) },
            onProgress = { _, _ -> },
        )
        if (!target.exists()) throw IOException("Не удалось извлечь $entry")
        target
    }

    // ---------------------------------------------------------- Изменение zip

    /**
     * Перезаписывает zip: записи из [replace] (путь внутри → файл) заменяются/добавляются,
     * записи из [remove] (файлы или папки) удаляются. Остальные копируются «как есть» (raw, без пережатия).
     */
    suspend fun modifyZip(archive: File, replace: Map<String, File>, remove: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        if (detect(archive) != ArchiveType.Zip) throw UnsupportedArchiveException(archive.name)
        val tmp = File(archive.parentFile, ".${archive.name}.tmp")
        val normalizedReplace = replace.mapKeys { normalize(it.key) }
        val removeSet = remove.map { normalize(it) }
        fun removed(name: String) = removeSet.any { name == it || name.startsWith("$it/") }
        try {
            openZip(archive).use { zf ->
                ZipArchiveOutputStream(tmp).use { zos ->
                    zos.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.AsNeeded)
                    val originalMethod = HashMap<String, Int>()
                    for (e in zf.entries) {
                        currentCoroutineContext().ensureActive()
                        val name = normalize(e.name)
                        originalMethod[name] = e.method
                        if (removed(name) || name in normalizedReplace) continue
                        // addRawArchiveEntry копирует запись без поля alignment, но с extra-полями:
                        // выравнивание задаём полем 0xD935 (как у zipalign), отступ пересчитается.
                        if (e.method == ZipArchiveEntry.STORED) {
                            e.addExtraField(org.apache.commons.compress.archivers.zip.ResourceAlignmentExtraField(storedAlignment(name)))
                        }
                        zf.getRawInputStream(e).use { raw -> zos.addRawArchiveEntry(e, raw) }
                    }
                    for ((name, file) in normalizedReplace) {
                        val entry = ZipArchiveEntry(file, name)
                        // Метод — как у заменяемой записи (несжатый DEX остаётся несжатым), а таблица
                        // ресурсов APK — всегда без сжатия и с выравниванием (требование Android 11+).
                        val stored = name == "resources.arsc" || originalMethod[name] == ZipArchiveEntry.STORED
                        if (stored) {
                            entry.method = ZipArchiveEntry.STORED
                            entry.size = file.length()
                            entry.crc = crc32(file)
                            entry.setAlignment(storedAlignment(name))
                        }
                        zos.putArchiveEntry(entry)
                        FileInputStream(file).use { it.copyTo(zos, 1 shl 16) }
                        zos.closeArchiveEntry()
                    }
                }
            }
            // Замена через резервную копию: при сбое оригинал восстанавливается.
            val backup = File(archive.parentFile, ".${archive.name}.bak")
            backup.delete()
            if (!archive.renameTo(backup)) throw IOException("Не удалось заменить архив")
            if (!tmp.renameTo(archive)) {
                backup.renameTo(archive)
                throw IOException("Не удалось заменить архив")
            }
            backup.delete()
            invalidate(archive)
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    /** Выравнивание несжатых записей как у zipalign: .so — по странице 16 КБ (Android 15+), остальное — по 4 байта. */
    internal fun storedAlignment(name: String): Int = if (name.endsWith(".so")) 16384 else 4

    internal fun crc32(file: File): Long {
        val crc = java.util.zip.CRC32()
        FileInputStream(file).use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                crc.update(buf, 0, n)
            }
        }
        return crc.value
    }

    // ---------------------------------------------------------------- Сжатие

    suspend fun compress(
        sources: List<File>,
        target: File,
        format: ArchiveFormat,
        level: Int,
        password: String?,
        onProgress: (name: String, delta: Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val tmp = File(target.parentFile, ".${target.name}.part")
        val targetDir = target.parentFile?.canonicalPath ?: ""
        sources.filter { it.isDirectory }.forEach { s ->
            val sp = s.canonicalPath
            if (targetDir == sp || targetDir.startsWith("$sp/")) throw IOException("Архив нельзя сохранить внутрь сжимаемой папки")
        }
        val skip = setOf(tmp.absolutePath, target.absolutePath)
        try {
            when (format) {
                ArchiveFormat.Zip -> if (!password.isNullOrEmpty()) {
                    // ZIP с паролем: AES-256 через потоковый ZipOutputStream из zip4j.
                    net.lingala.zip4j.io.outputstream.ZipOutputStream(FileOutputStream(tmp), password.toCharArray()).use { zos ->
                        walk(sources, skip) { file, name ->
                            currentCoroutineContext().ensureActive()
                            val params = net.lingala.zip4j.model.ZipParameters().apply {
                                fileNameInZip = name
                                lastModifiedFileTime = file.lastModified()
                                if (file.isFile) {
                                    isEncryptFiles = true
                                    encryptionMethod = net.lingala.zip4j.model.enums.EncryptionMethod.AES
                                    aesKeyStrength = net.lingala.zip4j.model.enums.AesKeyStrength.KEY_STRENGTH_256
                                    compressionMethod = if (level == 0) net.lingala.zip4j.model.enums.CompressionMethod.STORE else net.lingala.zip4j.model.enums.CompressionMethod.DEFLATE
                                    // Для STORE zip4j требует заранее известный размер записи.
                                    if (level == 0) entrySize = file.length()
                                    compressionLevel = when {
                                        level <= 0 -> net.lingala.zip4j.model.enums.CompressionLevel.NO_COMPRESSION
                                        level <= 3 -> net.lingala.zip4j.model.enums.CompressionLevel.FASTEST
                                        level <= 6 -> net.lingala.zip4j.model.enums.CompressionLevel.NORMAL
                                        else -> net.lingala.zip4j.model.enums.CompressionLevel.ULTRA
                                    }
                                }
                            }
                            zos.putNextEntry(params)
                            if (file.isFile) FileInputStream(file).use { input -> copyStream(input, zos) { onProgress(name, it.toLong()) } }
                            zos.closeEntry()
                            if (file.isFile) onProgress(name, -1)
                        }
                    }
                } else ZipArchiveOutputStream(tmp).use { zos ->
                    zos.setLevel(level.coerceIn(0, 9))
                    zos.setUseZip64(org.apache.commons.compress.archivers.zip.Zip64Mode.AsNeeded)
                    walk(sources, skip) { file, name ->
                        val entry = ZipArchiveEntry(file, name)
                        writeEntry(zos, entry, file, name, onProgress)
                    }
                }
                ArchiveFormat.SevenZ -> SevenZOutputFile(tmp).use { sz ->
                    sz.setContentCompression(if (level == 0) SevenZMethod.COPY else SevenZMethod.LZMA2)
                    walk(sources, skip) { file, name ->
                        currentCoroutineContext().ensureActive()
                        val entry = sz.createArchiveEntry(file, name)
                        sz.putArchiveEntry(entry)
                        if (file.isFile) {
                            FileInputStream(file).use { input ->
                                val buf = ByteArray(1 shl 16)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    sz.write(buf, 0, n)
                                    onProgress(name, n.toLong())
                                }
                            }
                        }
                        sz.closeArchiveEntry()
                        if (file.isFile) onProgress(name, -1)
                    }
                }
                ArchiveFormat.Tar, ArchiveFormat.TarGz, ArchiveFormat.TarXz, ArchiveFormat.TarBz2 -> {
                    val raw: OutputStream = BufferedOutputStream(FileOutputStream(tmp), 1 shl 16)
                    val compressed: OutputStream = when (format) {
                        ArchiveFormat.TarGz -> GzipCompressorOutputStream(raw, GzipParameters().apply { compressionLevel = level.coerceIn(1, 9) })
                        ArchiveFormat.TarXz -> XZCompressorOutputStream(raw, level.coerceIn(0, 9))
                        ArchiveFormat.TarBz2 -> BZip2CompressorOutputStream(raw, level.coerceIn(1, 9))
                        else -> raw
                    }
                    TarArchiveOutputStream(compressed).use { tos ->
                        tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                        tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                        walk(sources, skip) { file, name ->
                            writeEntry(tos, TarArchiveEntry(file, name), file, name, onProgress)
                        }
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw IOException("Не удалось сохранить архив")
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        }
    }

    private suspend fun walk(sources: List<File>, skip: Set<String> = emptySet(), visit: suspend (File, String) -> Unit) {
        for (root in sources) {
            val stack = ArrayDeque<Pair<File, String>>()
            stack.add(root to root.name)
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val (f, name) = stack.removeLast()
                if (f.absolutePath in skip) continue
                // Симлинки на папки не обходим — иначе возможна бесконечная рекурсия.
                val link = runCatching { android.system.OsConstants.S_ISLNK(android.system.Os.lstat(f.path).st_mode) }.getOrDefault(false)
                if (f.isDirectory && !link) {
                    visit(f, "$name/")
                    f.listFiles()?.sortedByDescending { it.name }?.forEach { stack.add(it to "$name/${it.name}") }
                } else {
                    visit(f, name)
                }
            }
        }
    }

    private suspend fun <E : ArchiveEntry> writeEntry(
        out: ArchiveOutputStream<E>,
        entry: E,
        file: File,
        name: String,
        onProgress: (String, Long) -> Unit,
    ) {
        out.putArchiveEntry(entry)
        if (file.isFile) {
            FileInputStream(file).use { input ->
                copyStream(input, out) { onProgress(name, it.toLong()) }
            }
        }
        out.closeArchiveEntry()
        if (file.isFile) onProgress(name, -1)
    }

    private suspend fun copyStream(input: InputStream, output: OutputStream, onChunk: (Int) -> Unit) {
        val buf = ByteArray(1 shl 16)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            onChunk(n)
        }
    }

    private class NonClosingStream(private val inner: ArchiveInputStream<*>) : InputStream() {
        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, len)
        override fun close() = Unit
    }

    /** Чтение текущей записи 7z; ошибки расшифровки (а не записи на диск) трактуются как проблема пароля. */
    private class SevenZEntryStream(private val sz: SevenZFile, private val password: String?) : InputStream() {
        override fun read(): Int = sevenZPasswordGuard(password) { sz.read() }
        override fun read(b: ByteArray, off: Int, len: Int): Int = sevenZPasswordGuard(password) { sz.read(b, off, len) }
        override fun close() = Unit
    }
}
