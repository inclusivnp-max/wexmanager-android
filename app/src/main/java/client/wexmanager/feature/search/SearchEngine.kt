package client.wexmanager.feature.search

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.fs.NameFilter
import client.wexmanager.core.util.FileOpener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Calendar

// ------------------------------------------------------------------ Фильтры

enum class TypeFilter(val label: String, val icon: ImageVector) {
    Folders("Папки", Icons.Rounded.Folder),
    Images("Изображения", Icons.Rounded.Image),
    Video("Видео", Icons.Rounded.Movie),
    Audio("Аудио", Icons.Rounded.MusicNote),
    Documents("Документы", Icons.Rounded.Description),
    Archives("Архивы", Icons.Rounded.FolderZip),
    Apk("APK", Icons.Rounded.Android),
    Code("Код/текст", Icons.Rounded.Code);

    fun matches(item: FileItem): Boolean = when (this) {
        Folders -> item.isDirectory
        Images -> item.kind == FileKind.Image || item.kind == FileKind.Vector
        Video -> item.kind == FileKind.Video
        Audio -> item.kind == FileKind.Audio
        Documents -> item.kind == FileKind.Pdf || item.kind == FileKind.Document || item.kind == FileKind.Spreadsheet ||
            item.kind == FileKind.Presentation || item.kind == FileKind.Ebook
        Archives -> item.kind == FileKind.Archive
        Apk -> item.kind == FileKind.Apk
        Code -> !item.isDirectory && item.kind.isTextLike
    }
}

enum class SizeFilter(val label: String) {
    Over1M("> 1 МБ"),
    Over10M("> 10 МБ"),
    Over100M("> 100 МБ"),
    Under100K("< 100 КБ");

    fun test(size: Long): Boolean = when (this) {
        Over1M -> size > MB
        Over10M -> size > 10 * MB
        Over100M -> size > 100 * MB
        Under100K -> size < 100 * 1024L
    }

    private companion object {
        const val MB = 1024L * 1024L
    }
}

enum class DateFilter(val label: String) {
    Today("Сегодня"),
    Week("7 дней"),
    Month("30 дней"),
    Year("Год");

    fun since(now: Long): Long = when (this) {
        Today -> Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        Week -> now - 7 * DAY
        Month -> now - 30 * DAY
        Year -> now - 365 * DAY
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
    }
}

enum class ResultSort(val label: String) {
    Relevance("По релевантности"),
    Name("По имени"),
    Size("По размеру"),
    Date("По дате"),
}

@Immutable
data class SearchParams(
    val root: String,
    val query: String = "",
    val regex: Boolean = false,
    val caseSensitive: Boolean = false,
    val types: Set<TypeFilter> = emptySet(),
    val size: SizeFilter? = null,
    val date: DateFilter? = null,
    val includeHidden: Boolean = false,
    val inContent: Boolean = false,
) {
    val hasFilters: Boolean get() = types.isNotEmpty() || size != null || date != null
    val extraFilterCount: Int
        get() = (if (size != null) 1 else 0) + (if (date != null) 1 else 0) + (if (includeHidden) 1 else 0) + (if (inContent) 1 else 0)

    /** Есть что искать: запрос или хотя бы один фильтр (поиск по содержимому требует запрос). */
    val isRunnable: Boolean get() = if (inContent) query.isNotEmpty() else query.isNotEmpty() || hasFilters
}

// ------------------------------------------------------------------ Результаты

@Immutable
data class ContentMatch(
    val line: Int,
    val snippet: String,
    val ranges: List<IntRange>,
    val count: Int,
)

@Immutable
data class SearchHit(
    val item: FileItem,
    val nameRanges: List<IntRange>,
    val score: Int,
    /** Папка, где лежит элемент, относительно корня поиска. */
    val location: String,
    val content: ContentMatch? = null,
)

// ------------------------------------------------------------------ Сопоставление

/**
 * Сопоставитель запроса: подстрока, шаблон (`*`, `?`) или regex, с учётом/без учёта регистра.
 * Возвращает диапазоны для подсветки или `null`, если совпадения нет.
 */
class QueryMatcher private constructor(
    private val plain: String?,
    private val regex: Regex?,
    private val glob: Boolean,
    private val globLiterals: List<String>,
    private val negated: NameFilter?,
    private val ignoreCase: Boolean,
    val error: String?,
) {
    val isValid: Boolean get() = error == null

    /** Запрос, который ripgrep понимает так же: подстрока или регулярное выражение (не шаблон и не «!»). */
    val ripgrepFriendly: Boolean get() = !glob && negated == null && (plain != null || regex != null)
    val isEmpty: Boolean get() = plain == null && regex == null && negated == null

    /** Совпадение по имени (шаблон должен покрывать имя целиком, как в фильтре браузера). */
    fun matchName(name: String): List<IntRange>? {
        if (negated != null) return if (negated.matches(name)) emptyList() else null
        if (plain != null) {
            val i = name.indexOf(plain, ignoreCase = ignoreCase)
            return if (i < 0) null else listOf(i until i + plain.length)
        }
        val r = regex ?: return emptyList()
        if (glob) {
            if (!r.matches(name)) return null
            // Подсвечиваем литеральные куски шаблона по порядку.
            val ranges = ArrayList<IntRange>(globLiterals.size)
            var pos = 0
            for (lit in globLiterals) {
                val i = name.indexOf(lit, pos, ignoreCase = ignoreCase)
                if (i < 0) break
                ranges += i until i + lit.length
                pos = i + lit.length
            }
            return ranges
        }
        val m = r.find(name) ?: return null
        return if (m.range.isEmpty()) emptyList() else listOf(m.range)
    }

    /** Совпадения внутри строки текста (шаблон — без привязки к началу/концу). */
    fun matchLine(line: String): List<IntRange>? {
        if (plain != null) {
            var i = line.indexOf(plain, ignoreCase = ignoreCase)
            if (i < 0) return null
            val out = ArrayList<IntRange>(2)
            while (i >= 0 && out.size < 8) {
                out += i until i + plain.length
                i = line.indexOf(plain, i + plain.length, ignoreCase = ignoreCase)
            }
            return out
        }
        val r = contentRegex ?: return null
        val out = ArrayList<IntRange>(2)
        for (m in r.findAll(line)) {
            if (!m.range.isEmpty()) out += m.range
            if (out.size >= 8) break
        }
        return out.ifEmpty { null }
    }

    private val contentRegex: Regex? by lazy {
        when {
            negated != null -> null
            glob -> regex?.pattern?.let { p ->
                // В содержимом шаблон ищется как фрагмент строки: «.*» по краям не нужны.
                val opts = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                runCatching { Regex(p.removePrefix(".*").removeSuffix(".*").ifEmpty { p }, opts) }.getOrNull()
            }
            else -> regex
        }
    }

    companion object {
        val Empty = QueryMatcher(null, null, false, emptyList(), null, true, null)

        fun create(query: String, regex: Boolean, caseSensitive: Boolean): QueryMatcher {
            if (query.isEmpty()) return Empty
            val opts = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
            return when {
                regex -> try {
                    QueryMatcher(null, Regex(query, opts), false, emptyList(), null, !caseSensitive, null)
                } catch (e: Exception) {
                    QueryMatcher(null, null, false, emptyList(), null, !caseSensitive, e.message?.lineSequence()?.firstOrNull() ?: "Неверное выражение")
                }
                // «!текст» — отрицание в стиле фильтра браузера.
                query.startsWith('!') && query.length > 1 && !caseSensitive ->
                    QueryMatcher(null, null, false, emptyList(), NameFilter.parse(query), true, null)
                query.contains('*') || query.contains('?') -> {
                    val base = NameFilter.globToRegex(query)
                    val r = if (caseSensitive) Regex(base.pattern) else base
                    val literals = query.split('*', '?').filter { it.isNotEmpty() }
                    QueryMatcher(null, r, true, literals, null, !caseSensitive, null)
                }
                else -> QueryMatcher(query, null, false, emptyList(), null, !caseSensitive, null)
            }
        }
    }
}

// ------------------------------------------------------------------ Обход

object SearchEngine {
    const val MAX_RESULTS = 5000
    private const val MAX_CONTENT_SIZE = 5L * 1024 * 1024
    private const val BATCH_SIZE = 50
    private const val BATCH_INTERVAL_MS = 150L
    private val systemDirs = setOf("/proc", "/sys", "/dev", "/acct", "/d", "/config")

    class Progress(val scanned: Int, val currentDir: String)

    /**
     * Обход в ширину от [SearchParams.root]: сначала мелкие уровни, затем вглубь.
     * Симлинки на папки проходятся один раз — по (dev, inode), поэтому петли не страшны.
     * [onBatch] вызывается на IO-потоке порциями (по 50 или каждые 150 мс).
     * Возвращает число просмотренных элементов и признак достижения лимита.
     */
    suspend fun run(
        params: SearchParams,
        matcher: QueryMatcher,
        onBatch: (hits: List<SearchHit>, progress: Progress) -> Unit,
    ): Pair<Int, Boolean> {
        if (params.inContent && Ripgrep.available && matcher.ripgrepFriendly) {
            runRipgrep(params, matcher, onBatch)?.let { return it }
        }
        return runBuiltIn(params, matcher, onBatch)
    }

    private fun rootTitle(root: String): String = when {
        root == "/" -> "/"
        root == android.os.Environment.getExternalStorageDirectory().path -> "Память"
        else -> root.substringAfterLast('/')
    }

    /**
     * Содержимое — через ripgrep (Rust, как в WEXdesktop): параллельный обход, пропуск двоичных файлов,
     * SIMD-поиск. null — ripgrep не справился (например, выражение, которое понимает только Java):
     * тогда ищет встроенный обход.
     */
    private suspend fun runRipgrep(
        params: SearchParams,
        matcher: QueryMatcher,
        onBatch: (hits: List<SearchHit>, progress: Progress) -> Unit,
    ): Pair<Int, Boolean>? {
        val root = params.root.trimEnd('/').ifEmpty { "/" }
        val rootTitle = rootTitle(root)
        val roots = if (root == "/") File("/").list()?.map { "/$it" }?.filter { it !in systemDirs }.orEmpty() else listOf(root)
        if (roots.isEmpty()) return null
        val since = params.date?.since(System.currentTimeMillis()) ?: 0L
        var found = 0
        var withMatches = 0
        var currentDir = root
        val batch = ArrayList<SearchHit>(BATCH_SIZE)
        var lastFlush = SystemClock.uptimeMillis()
        fun flush(force: Boolean) {
            val now = SystemClock.uptimeMillis()
            if (force || batch.size >= BATCH_SIZE || now - lastFlush >= BATCH_INTERVAL_MS) {
                onBatch(ArrayList(batch), Progress(withMatches, currentDir))
                batch.clear()
                lastFlush = now
            }
        }
        val outcome = try {
            Ripgrep.search(roots, params.query, params.regex, params.caseSensitive, params.includeHidden) { fm ->
                withMatches++
                val name = fm.path.substringAfterLast('/')
                currentDir = fm.path.substringBeforeLast('/', root)
                val item = statEntry(fm.path, name)?.item ?: return@search true
                if (params.types.isNotEmpty() && params.types.none { it.matches(item) }) return@search true
                if (params.size != null && !params.size.test(item.size)) return@search true
                if (params.date != null && item.lastModified < since) return@search true
                val content = if (fm.ranges.isEmpty()) ContentMatch(fm.line, fm.text.trim().take(132), emptyList(), fm.count)
                else snippet(fm.text, fm.line, fm.ranges).copy(count = fm.count)
                batch += hitFor(item, matcher.matchName(name).orEmpty(), content, root, rootTitle)
                found++
                flush(false)
                found < MAX_RESULTS
            }
        } catch (_: IOException) {
            return null
        }
        if (outcome.error != null && found == 0) return null
        flush(true)
        val scanned = if (outcome.searched >= 0) outcome.searched else withMatches
        if (scanned != withMatches) onBatch(emptyList(), Progress(scanned, currentDir))
        return scanned to (found >= MAX_RESULTS)
    }

    private suspend fun runBuiltIn(
        params: SearchParams,
        matcher: QueryMatcher,
        onBatch: (hits: List<SearchHit>, progress: Progress) -> Unit,
    ): Pair<Int, Boolean> = withContext(Dispatchers.IO) {
        val root = params.root.trimEnd('/').ifEmpty { "/" }
        val rootTitle = rootTitle(root)
        val since = params.date?.since(System.currentTimeMillis()) ?: 0L
        val queue = ArrayDeque<String>()
        val visited = HashSet<Long>()
        inodeKey(root)?.let { visited += it }
        queue.addLast(root)

        var scanned = 0
        var found = 0
        val batch = ArrayList<SearchHit>(BATCH_SIZE)
        var lastFlush = SystemClock.uptimeMillis()
        var currentDir = root

        fun flush(force: Boolean) {
            val now = SystemClock.uptimeMillis()
            if (force || batch.size >= BATCH_SIZE || now - lastFlush >= BATCH_INTERVAL_MS) {
                onBatch(ArrayList(batch), Progress(scanned, currentDir))
                batch.clear()
                lastFlush = now
            }
        }

        while (queue.isNotEmpty() && found < MAX_RESULTS) {
            ensureActive()
            val dir = queue.removeFirst()
            currentDir = dir
            val names = File(dir).list() ?: continue
            for (name in names) {
                if (++scanned and 127 == 0) {
                    ensureActive()
                    flush(false)
                }
                if (!params.includeHidden && name.startsWith('.')) continue
                val path = if (dir == "/") "/$name" else "$dir/$name"
                val entry = statEntry(path, name) ?: continue
                val item = entry.item
                if (item.isDirectory && path !in systemDirs) {
                    val key = entry.inode
                    if (key == null || visited.add(key)) queue.addLast(path)
                }
                val hit = evaluate(item, params, matcher, since, root, rootTitle) ?: continue
                batch += hit
                found++
                if (found >= MAX_RESULTS) break
                flush(false)
            }
            flush(false)
        }
        flush(true)
        scanned to (found >= MAX_RESULTS)
    }

    private class Entry(val item: FileItem, val inode: Long?)

    private fun inodeKey(path: String): Long? = runCatching {
        val st = Os.stat(path)
        (st.st_dev shl 40) xor st.st_ino
    }.getOrNull()

    private fun statEntry(path: String, name: String): Entry? = try {
        val st = Os.lstat(path)
        if (OsConstants.S_ISLNK(st.st_mode)) {
            val target = runCatching { Os.stat(path) }.getOrNull()
            val isDir = target != null && OsConstants.S_ISDIR(target.st_mode)
            Entry(
                FileItem(
                    path = path,
                    name = name,
                    isDirectory = isDir,
                    size = target?.st_size ?: 0L,
                    lastModified = st.st_mtime * 1000L,
                    isSymlink = true,
                    mode = st.st_mode,
                    linkTarget = runCatching { Os.readlink(path) }.getOrNull(),
                ),
                if (target != null && isDir) (target.st_dev shl 40) xor target.st_ino else null,
            )
        } else {
            val isDir = OsConstants.S_ISDIR(st.st_mode)
            Entry(
                FileItem(
                    path = path,
                    name = name,
                    isDirectory = isDir,
                    size = st.st_size,
                    lastModified = st.st_mtime * 1000L,
                    mode = st.st_mode,
                ),
                if (isDir) (st.st_dev shl 40) xor st.st_ino else null,
            )
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun evaluate(
        item: FileItem,
        p: SearchParams,
        matcher: QueryMatcher,
        since: Long,
        root: String,
        rootTitle: String,
    ): SearchHit? {
        if (p.types.isNotEmpty() && p.types.none { it.matches(item) }) return null
        if (p.size != null && (item.isDirectory || !p.size.test(item.size))) return null
        if (p.date != null && item.lastModified < since) return null

        var content: ContentMatch? = null
        val nameRanges: List<IntRange>
        if (p.inContent) {
            if (item.isDirectory || item.size > MAX_CONTENT_SIZE) return null
            content = searchContent(item, matcher) ?: return null
            nameRanges = matcher.matchName(item.name).orEmpty()
        } else {
            nameRanges = matcher.matchName(item.name) ?: return null
        }
        return hitFor(item, nameRanges, content, root, rootTitle)
    }

    private fun hitFor(item: FileItem, nameRanges: List<IntRange>, content: ContentMatch?, root: String, rootTitle: String): SearchHit {
        val parent = item.path.substringBeforeLast('/', "")
        val rel = parent.removePrefix(root).trimStart('/')
        val location = if (rel.isEmpty()) rootTitle else "$rootTitle/$rel"
        val depth = rel.count { it == '/' } + if (rel.isEmpty()) 0 else 1
        return SearchHit(item, nameRanges, score(item, nameRanges, depth, content), location, content)
    }

    private fun score(item: FileItem, ranges: List<IntRange>, depth: Int, content: ContentMatch?): Int {
        var s = 0
        val name = item.name
        if (ranges.isNotEmpty()) {
            val first = ranges.first()
            val matched = ranges.sumOf { it.last - it.first + 1 }
            s += when {
                first.first == 0 && matched == name.length -> 1000
                first.first == 0 && matched == item.baseName.length -> 850
                first.first == 0 -> 650
                !name[first.first - 1].isLetterOrDigit() -> 450
                else -> 250
            }
            s -= (name.length - matched).coerceAtMost(120)
        }
        if (content != null) s += content.count.coerceAtMost(50) * 4
        if (item.isDirectory) s += 15
        s -= depth * 12
        return s
    }

    private suspend fun searchContent(item: FileItem, matcher: QueryMatcher): ContentMatch? {
        val file = File(item.path)
        val textual = item.kind.isTextLike || (item.kind == FileKind.Unknown && FileOpener.looksLikeText(file))
        if (!textual) return null
        var first: ContentMatch? = null
        var count = 0
        val ctx = currentCoroutineContext()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 16 * 1024).use { reader ->
                var lineNo = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    lineNo++
                    if (lineNo and 511 == 0) ctx.ensureActive()
                    if (line.indexOf('\u0000') >= 0) return null
                    val ranges = matcher.matchLine(line) ?: continue
                    count++
                    if (first == null) first = snippet(line, lineNo, ranges)
                    if (count >= 999) break
                }
            }
        } catch (_: IOException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        return first?.copy(count = count)
    }

    /** Вырезает окно вокруг первого совпадения и сдвигает диапазоны подсветки. */
    private fun snippet(line: String, lineNo: Int, ranges: List<IntRange>): ContentMatch {
        val first = ranges.first()
        var lead = 0
        while (lead < line.length && line[lead].isWhitespace()) lead++
        val start = maxOf(lead, first.first - 36).coerceAtMost(first.first)
        val end = minOf(line.length, first.last + 1 + 96)
        val prefix = if (start > lead) "…" else ""
        val suffix = if (end < line.length) "…" else ""
        val body = line.substring(start, end).replace('\t', ' ')
        val shift = prefix.length - start
        val shifted = ranges.mapNotNull { r ->
            if (r.first < start || r.last >= end) null else (r.first + shift)..(r.last + shift)
        }
        return ContentMatch(lineNo, prefix + body + suffix, shifted, 1)
    }
}
