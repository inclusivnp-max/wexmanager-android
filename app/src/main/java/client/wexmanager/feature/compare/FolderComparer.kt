package client.wexmanager.feature.compare

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import androidx.compose.runtime.Immutable
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.NaturalOrder
import client.wexmanager.core.ops.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.abs

enum class CmpStatus(val glyph: String, val label: String) {
    OnlyLeft("←", "Только слева"),
    OnlyRight("→", "Только справа"),
    Different("≠", "Отличаются"),
    Same("=", "Одинаковые"),
}

enum class Newer { None, Left, Right }

/** Одна сторона сравнения. Для папки [size]/[files] — суммарно по содержимому (если посчитано). */
@Immutable
data class CmpSide(val path: String, val isDir: Boolean, val size: Long, val mtime: Long, val files: Int = 0) {
    fun toFileItem(): FileItem =
        FileItem(path = path, name = path.substringAfterLast('/'), isDirectory = isDir, size = size, lastModified = mtime)
}

@Immutable
data class CmpEntry(
    /** Путь относительно корней (без ведущего «/»). */
    val rel: String,
    val status: CmpStatus,
    val left: CmpSide?,
    val right: CmpSide?,
    val newer: Newer,
    /** Причина отличия: «размер», «дата», «содержимое», «тип». */
    val reason: String? = null,
) {
    val name: String get() = rel.substringAfterLast('/')
    val parentRel: String get() = rel.substringBeforeLast('/', "")
    val isDir: Boolean get() = (left ?: right)?.isDir == true
    /** Файл с одной стороны и папка с другой. */
    val typeMismatch: Boolean get() = left != null && right != null && left.isDir != right.isDir
}

@Immutable
data class CmpProgress(val items: Int = 0, val compared: Long = 0, val current: String = "")

class CmpResult(val entries: List<CmpEntry>, val unreadable: Int, val durationMs: Long)

/** Сравнение двух деревьев папок по относительным путям (без следования симлинкам). */
object FolderComparer {
    /** Допуск по времени изменения: FAT/exFAT хранит время с точностью 2 с. */
    const val MTIME_TOLERANCE_MS = 2000L
    private const val PROGRESS_MS = 100L

    /**
     * Пустая строка — все файлы. «apk, jpg», «*.so» и «.so» задают один и тот же набор.
     * Сравнение идёт по последнему суффиксу: «tar.gz» совпадёт с `a.tar.gz`.
     */
    fun parseExtensions(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        val out = LinkedHashSet<String>()
        for (part in raw.split(',', ';', ' ', '\n', '\t', '\r')) {
            var token = part.trim().lowercase(Locale.ROOT)
            if (token.isEmpty()) continue
            if (token.startsWith("*")) token = token.substring(1).trim()
            if (token.startsWith(".")) token = token.substring(1).trim()
            if (token.isEmpty() || token.any { it == '/' || it == '\\' || it == '*' || it == '?' }) continue
            out += token
        }
        return out
    }

    /** Файл подходит под фильтр. Пустой набор — подходят все. Точка в начале имени не считается расширением. */
    fun matchesExtension(name: String, extensions: Set<String>): Boolean {
        if (extensions.isEmpty()) return true
        val lower = name.lowercase(Locale.ROOT)
        for (ext in extensions) {
            val suffix = ".$ext"
            if (lower.length > suffix.length && lower.endsWith(suffix)) return true
        }
        return false
    }

    private class Stat(val name: String, val isDir: Boolean, val size: Long, val mtime: Long)

    suspend fun compare(
        leftRoot: String,
        rightRoot: String,
        recursive: Boolean,
        byContent: Boolean,
        extensions: Set<String> = emptySet(),
        onProgress: (CmpProgress) -> Unit,
    ): CmpResult = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val job = currentCoroutineContext()[Job]
        val out = ArrayList<CmpEntry>()
        var unreadable = 0
        var items = 0
        var compared = 0L
        var last = 0L

        fun publish(current: String) {
            val now = SystemClock.uptimeMillis()
            if (now - last >= PROGRESS_MS) {
                last = now
                onProgress(CmpProgress(items, compared, current))
            }
        }

        // Если одна папка лежит внутри другой — не заходим во «вложенный» корень.
        val excluded = setOf(leftRoot, rightRoot)

        fun list(dir: String): Map<String, Stat>? {
            val names = File(dir).list() ?: return null
            val map = HashMap<String, Stat>(names.size * 2)
            for (n in names) {
                if (n == TrashManager.TRASH_DIR) continue
                val p = childPath(dir, n)
                if (p in excluded) continue
                val st: StructStat = try {
                    Os.lstat(p)
                } catch (_: Exception) {
                    continue
                }
                val isDir = OsConstants.S_ISDIR(st.st_mode)
                // Симлинки, FIFO, сокеты и устройства не сравниваем: чтение FIFO блокирует поток навсегда.
                if (!isDir && !OsConstants.S_ISREG(st.st_mode)) continue
                // Чужое расширение отсекается до пары, счётчиков и синхронизации.
                if (!isDir && extensions.isNotEmpty() && !matchesExtension(n, extensions)) continue
                map[n] = Stat(n, isDir, if (isDir) 0L else st.st_size, st.st_mtime * 1000L)
            }
            return map
        }

        // Каталог с фильтром не отдаём одним элементом: копирование унесло бы файлы других типов.
        fun emitMatchingFiles(dirPath: String, dirRel: String, left: Boolean) {
            val dirStack = ArrayDeque<Pair<String, String>>()
            dirStack.addLast(dirPath to dirRel)
            while (dirStack.isNotEmpty()) {
                ensureActive()
                val (dir, rel) = dirStack.removeLast()
                val listed = list(dir)
                if (listed == null) {
                    unreadable++
                    continue
                }
                val nested = ArrayList<Pair<String, String>>()
                for (n in listed.keys.sortedWith(NaturalOrder)) {
                    ensureActive()
                    val st = listed.getValue(n)
                    val childRel = if (rel.isEmpty()) n else "$rel/$n"
                    val p = childPath(dir, n)
                    if (st.isDir) {
                        nested += p to childRel
                    } else {
                        items++
                        publish(childRel)
                        val file = CmpSide(p, false, st.size, st.mtime)
                        out += if (left) {
                            CmpEntry(childRel, CmpStatus.OnlyLeft, file, null, Newer.Left)
                        } else {
                            CmpEntry(childRel, CmpStatus.OnlyRight, null, file, Newer.Right)
                        }
                    }
                }
                for (k in nested.indices.reversed()) dirStack.addLast(nested[k])
            }
        }

        val stack = ArrayDeque<String>()
        stack.addLast("")
        while (stack.isNotEmpty()) {
            ensureActive()
            val rel = stack.removeLast()
            val lDir = if (rel.isEmpty()) leftRoot else childPath(leftRoot, rel)
            val rDir = if (rel.isEmpty()) rightRoot else childPath(rightRoot, rel)
            val l = list(lDir) ?: emptyMap<String, Stat>().also { unreadable++ }
            val r = list(rDir) ?: emptyMap<String, Stat>().also { unreadable++ }
            val names = (l.keys + r.keys).sortedWith(NaturalOrder)
            val subdirs = ArrayList<String>()
            for (n in names) {
                ensureActive()
                val ls = l[n]
                val rs = r[n]
                val childRel = if (rel.isEmpty()) n else "$rel/$n"
                val lp = childPath(lDir, n)
                val rp = childPath(rDir, n)
                if (extensions.isNotEmpty() && (ls?.isDir == true || rs?.isDir == true)) {
                    val typeMismatch = ls != null && rs != null && ls.isDir != rs.isDir
                    if (!typeMismatch) {
                        when {
                            ls?.isDir == true && rs?.isDir == true -> if (recursive) subdirs += childRel
                            recursive && ls?.isDir == true -> emitMatchingFiles(lp, childRel, true)
                            recursive && rs?.isDir == true -> emitMatchingFiles(rp, childRel, false)
                        }
                        continue
                    }
                }
                items++
                publish(childRel)
                when {
                    ls != null && rs == null -> out += CmpEntry(childRel, CmpStatus.OnlyLeft, side(lp, ls, job), null, Newer.Left)
                    ls == null && rs != null -> out += CmpEntry(childRel, CmpStatus.OnlyRight, null, side(rp, rs, job), Newer.Right)
                    ls != null && rs != null -> {
                        val newer = newer(ls.mtime, rs.mtime)
                        when {
                            ls.isDir && rs.isDir -> {
                                if (recursive) subdirs += childRel
                                else out += CmpEntry(childRel, CmpStatus.Same, CmpSide(lp, true, 0, ls.mtime), CmpSide(rp, true, 0, rs.mtime), newer)
                            }
                            ls.isDir != rs.isDir -> out += CmpEntry(
                                childRel, CmpStatus.Different, side(lp, ls, job), side(rp, rs, job), newer, "тип",
                            )
                            else -> {
                                val reason = when {
                                    ls.size != rs.size -> "размер"
                                    byContent -> {
                                        val eq = try {
                                            sameContent(lp, rp, job) { compared += it; publish(childRel) }
                                        } catch (_: IOException) {
                                            false
                                        }
                                        if (eq) null else "содержимое"
                                    }
                                    newer != Newer.None -> "дата"
                                    else -> null
                                }
                                out += CmpEntry(
                                    childRel,
                                    if (reason == null) CmpStatus.Same else CmpStatus.Different,
                                    CmpSide(lp, false, ls.size, ls.mtime),
                                    CmpSide(rp, false, rs.size, rs.mtime),
                                    newer,
                                    reason,
                                )
                            }
                        }
                    }
                }
            }
            // В стек — в обратном порядке, чтобы обход шёл по алфавиту.
            for (k in subdirs.indices.reversed()) stack.addLast(subdirs[k])
        }
        onProgress(CmpProgress(items, compared, ""))
        out.sortWith(entryOrder)
        CmpResult(out, unreadable, SystemClock.elapsedRealtime() - started)
    }

    fun newer(left: Long, right: Long): Newer = when {
        abs(left - right) <= MTIME_TOLERANCE_MS -> Newer.None
        left > right -> Newer.Left
        else -> Newer.Right
    }

    /** Порядок: по родительской папке (посегментно), затем папки, затем файлы. */
    val entryOrder: Comparator<CmpEntry> = Comparator { a, b ->
        val pa = a.parentRel
        val pb = b.parentRel
        if (pa != pb) return@Comparator comparePaths(pa, pb)
        if (a.isDir != b.isDir) return@Comparator if (a.isDir) -1 else 1
        NaturalOrder.compare(a.name, b.name)
    }

    private fun comparePaths(a: String, b: String): Int {
        if (a.isEmpty()) return if (b.isEmpty()) 0 else -1
        if (b.isEmpty()) return 1
        val sa = a.split('/')
        val sb = b.split('/')
        for (i in 0 until minOf(sa.size, sb.size)) {
            val c = NaturalOrder.compare(sa[i], sb[i])
            if (c != 0) return c
        }
        return sa.size - sb.size
    }

    /** Сторона с подсчётом содержимого для папок (обход без симлинков). */
    private fun side(path: String, st: Stat, job: Job?): CmpSide {
        if (!st.isDir) return CmpSide(path, false, st.size, st.mtime)
        var files = 0
        var bytes = 0L
        var tick = 0
        val stack = ArrayDeque<String>()
        stack.addLast(path)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val names = File(dir).list() ?: continue
            for (n in names) {
                if (++tick and 255 == 0) job?.ensureActive()
                val p = childPath(dir, n)
                val s = try {
                    Os.lstat(p)
                } catch (_: Exception) {
                    continue
                }
                if (OsConstants.S_ISDIR(s.st_mode)) stack.addLast(p) else {
                    files++
                    bytes += s.st_size
                }
            }
        }
        return CmpSide(path, true, bytes, st.mtime, files)
    }

    /** Побайтовое сравнение двух файлов одинакового размера (с ранним выходом). */
    fun sameContent(a: String, b: String, job: Job?, onBytes: (Long) -> Unit = {}): Boolean {
        FileInputStream(a).use { ia ->
            FileInputStream(b).use { ib ->
                val ba = ByteArray(1 shl 16)
                val bb = ByteArray(1 shl 16)
                var chunks = 0
                while (true) {
                    val na = readFully(ia, ba)
                    val nb = readFully(ib, bb)
                    if (na != nb) return false
                    if (na <= 0) return true
                    if (!ba.contentRangeEquals(bb, na)) return false
                    onBytes(na.toLong())
                    if (++chunks and 15 == 0) job?.ensureActive()
                }
            }
        }
    }

    private fun readFully(input: FileInputStream, buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val n = input.read(buf, total, buf.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private fun ByteArray.contentRangeEquals(other: ByteArray, length: Int): Boolean {
        for (i in 0 until length) if (this[i] != other[i]) return false
        return true
    }
}
