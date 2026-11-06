package client.wexmanager.feature.compare

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.core.util.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class DiffMode(val label: String) { Side("Рядом"), Unified("Единый") }

/** Элемент списка diff. */
@Immutable
sealed interface DiffItem {
    val key: String
}

/** Строка режима «рядом». */
@Immutable
class SideItem(val row: DiffRow, index: Int) : DiffItem {
    override val key: String = "s$index"
}

/** Строка единого режима: [kind] — Equal, Removed или Added. */
@Immutable
class UniItem(
    val kind: RowKind,
    val oldNo: Int,
    val newNo: Int,
    val text: String,
    val marks: IntArray?,
    override val key: String,
) : DiffItem

/** Свёрнутый блок совпадающих строк [start, end) (индексы строк выравнивания). */
@Immutable
class FoldItem(val start: Int, val end: Int) : DiffItem {
    override val key: String = "f$start"
    val count: Int get() = end - start
}

@Immutable
data class DiffFileInfo(val name: String, val path: String, val size: Long, val lines: Int)

@Immutable
data class DiffUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** Похоже на двоичные файлы — нужно подтверждение. */
    val binary: Boolean = false,
    val left: DiffFileInfo? = null,
    val right: DiffFileInfo? = null,
    val stats: DiffStats = DiffStats(0, 0, 0),
    val identical: Boolean = false,
    val notes: List<String> = emptyList(),
    val mode: DiffMode = DiffMode.Side,
    val onlyDiffs: Boolean = false,
    val items: List<DiffItem> = emptyList(),
    /** Индексы [items], с которых начинаются блоки отличий. */
    val hunks: IntArray = IntArray(0),
    val maxLeftChars: Int = 0,
    val maxRightChars: Int = 0,
    val maxLineNo: Int = 0,
    val durationMs: Long = 0,
)

class DiffViewModel(app: Application, private val leftPath: String, private val rightPath: String) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DiffUiState())
    val state: StateFlow<DiffUiState> = _state.asStateFlow()

    private var rows: List<DiffRow> = emptyList()
    private val expanded = HashSet<Int>()
    private var loadJob: Job? = null
    private var buildJob: Job? = null

    init {
        load(force = false)
    }

    fun load(force: Boolean) {
        loadJob?.cancel()
        buildJob?.cancel()
        _state.update { DiffUiState(mode = it.mode, onlyDiffs = it.onlyDiffs) }
        loadJob = viewModelScope.launch {
            val started = System.currentTimeMillis()
            try {
                val (l, r) = coroutineScope {
                    val a = async(Dispatchers.IO) { readTextFile(leftPath) }
                    val b = async(Dispatchers.IO) { readTextFile(rightPath) }
                    a.await() to b.await()
                }
                val leftInfo = DiffFileInfo(l.name, leftPath, l.size, l.lines.size)
                val rightInfo = DiffFileInfo(r.name, rightPath, r.size, r.lines.size)
                if ((l.binary || r.binary) && !force) {
                    _state.update { it.copy(loading = false, binary = true, left = leftInfo, right = rightInfo) }
                    return@launch
                }
                val computed = withContext(Dispatchers.Default) {
                    val job = currentCoroutineContext()[Job]
                    val (del, ins) = DiffEngine.diff(l.lines, r.lines, job)
                    DiffEngine.align(l.lines, r.lines, del, ins, job)
                }
                rows = computed
                expanded.clear()
                val stats = DiffEngine.stats(computed)
                val notes = buildList {
                    if (l.crlf != r.crlf) add("Окончания строк: ${if (l.crlf) "CRLF" else "LF"} → ${if (r.crlf) "CRLF" else "LF"}")
                    if (l.bom != r.bom) add(if (l.bom) "BOM есть только слева" else "BOM есть только справа")
                    if (l.finalNewline != r.finalNewline && l.lines.isNotEmpty() && r.lines.isNotEmpty()) {
                        add(if (l.finalNewline) "Нет перевода строки в конце справа" else "Нет перевода строки в конце слева")
                    }
                    if (l.malformed || r.malformed) add("Некорректные символы UTF-8 заменены на «�»")
                }
                val s = _state.value
                val built = withContext(Dispatchers.Default) { build(computed, s.mode, s.onlyDiffs, emptySet()) }
                _state.update {
                    it.copy(
                        loading = false,
                        error = null,
                        binary = false,
                        left = leftInfo,
                        right = rightInfo,
                        stats = stats,
                        identical = stats.total == 0,
                        notes = notes,
                        items = built.items,
                        hunks = built.hunks,
                        maxLeftChars = built.maxLeft,
                        maxRightChars = built.maxRight,
                        maxLineNo = maxOf(l.lines.size, r.lines.size),
                        durationMs = System.currentTimeMillis() - started,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun setMode(mode: DiffMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode) }
        rebuild()
    }

    fun toggleOnlyDiffs() {
        _state.update { it.copy(onlyDiffs = !it.onlyDiffs) }
        expanded.clear()
        rebuild()
    }

    fun expand(fold: FoldItem) {
        expanded += fold.start
        rebuild()
    }

    private fun rebuild() {
        val s = _state.value
        if (s.loading || rows.isEmpty()) return
        buildJob?.cancel()
        val snapshot = rows
        val exp = HashSet(expanded)
        buildJob = viewModelScope.launch {
            val built = withContext(Dispatchers.Default) { build(snapshot, s.mode, s.onlyDiffs, exp) }
            _state.update {
                it.copy(items = built.items, hunks = built.hunks, maxLeftChars = built.maxLeft, maxRightChars = built.maxRight)
            }
        }
    }

    private class Built(val items: List<DiffItem>, val hunks: IntArray, val maxLeft: Int, val maxRight: Int)

    /** Строит список отображения с учётом режима и свёрнутых совпадающих блоков. */
    private fun build(rows: List<DiffRow>, mode: DiffMode, onlyDiffs: Boolean, expanded: Set<Int>): Built {
        // Последовательность: индекс строки (>= 0) или свёрнутый блок.
        val seq = ArrayList<Any>(rows.size)
        if (!onlyDiffs) {
            for (i in rows.indices) seq += i
        } else {
            var i = 0
            while (i < rows.size) {
                if (rows[i].kind != RowKind.Equal) { seq += i; i++; continue }
                val s = i
                while (i < rows.size && rows[i].kind == RowKind.Equal) i++
                val e = i
                val head = if (s == 0) 0 else CONTEXT
                val tail = if (e == rows.size) 0 else CONTEXT
                if (e - s <= head + tail + 1) {
                    for (k in s until e) seq += k
                } else {
                    for (k in s until s + head) seq += k
                    val fs = s + head
                    val fe = e - tail
                    if (fs in expanded) for (k in fs until fe) seq += k else seq += FoldItem(fs, fe)
                    for (k in fe until e) seq += k
                }
            }
        }
        val items = ArrayList<DiffItem>(seq.size + 16)
        val hunks = ArrayList<Int>()
        var maxLeft = 0
        var maxRight = 0
        var inBlock = false
        when (mode) {
            DiffMode.Side -> for (x in seq) {
                if (x is FoldItem) { items += x; inBlock = false; continue }
                val idx = x as Int
                val row = rows[idx]
                if (row.kind == RowKind.Equal) inBlock = false else if (!inBlock) { hunks += items.size; inBlock = true }
                items += SideItem(row, idx)
                maxLeft = maxOf(maxLeft, row.left?.length ?: 0)
                maxRight = maxOf(maxRight, row.right?.length ?: 0)
            }
            DiffMode.Unified -> {
                // В блоке изменений сначала все удалённые строки, затем все добавленные.
                val pendingAdds = ArrayList<UniItem>()
                fun flush() {
                    items += pendingAdds
                    pendingAdds.clear()
                }
                for (x in seq) {
                    if (x is FoldItem) { flush(); items += x; inBlock = false; continue }
                    val idx = x as Int
                    val row = rows[idx]
                    if (row.kind == RowKind.Equal) {
                        flush()
                        inBlock = false
                        val t = row.left.orEmpty()
                        items += UniItem(RowKind.Equal, row.leftNo, row.rightNo, t, null, "u$idx")
                        maxLeft = maxOf(maxLeft, t.length)
                        continue
                    }
                    if (!inBlock) { hunks += items.size; inBlock = true }
                    if (row.left != null) {
                        items += UniItem(RowKind.Removed, row.leftNo, 0, row.left, row.leftMarks, "u${idx}a")
                        maxLeft = maxOf(maxLeft, row.left.length)
                    }
                    if (row.right != null) {
                        pendingAdds += UniItem(RowKind.Added, 0, row.rightNo, row.right, row.rightMarks, "u${idx}b")
                        maxLeft = maxOf(maxLeft, row.right.length)
                    }
                }
                flush()
            }
        }
        return Built(items, hunks.toIntArray(), maxLeft, maxRight)
    }

    override fun onCleared() {
        loadJob?.cancel()
        buildJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val MAX_BYTES = 5L * 1024 * 1024
        /** Строк контекста вокруг отличий в режиме «только отличия». */
        const val CONTEXT = 3
    }
}

/** Текстовый файл, разбитый на строки (UTF-8 с заменой некорректных последовательностей). */
internal class TextFile(
    val name: String,
    val size: Long,
    val lines: List<String>,
    val crlf: Boolean,
    val bom: Boolean,
    val finalNewline: Boolean,
    val binary: Boolean,
    val malformed: Boolean,
)

private const val MAX_DIFF_LINES = 300_000

internal fun readTextFile(path: String, maxBytes: Long = DiffViewModel.MAX_BYTES): TextFile {
    val f = File(path)
    if (!f.exists()) throw IOException("Файл не найден: ${f.name}")
    if (f.isDirectory) throw IOException("«${f.name}» — это папка")
    val size = f.length()
    if (size > maxBytes) throw IOException("Файл «${f.name}» больше ${Format.size(maxBytes)} (${Format.size(size)})")
    val bytes = try {
        f.readBytes()
    } catch (e: IOException) {
        throw IOException("Не удалось прочитать «${f.name}»: ${e.message}")
    }
    var binary = false
    for (i in 0 until minOf(bytes.size, 8192)) if (bytes[i] == 0.toByte()) { binary = true; break }
    val bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
    val start = if (bom) 3 else 0
    // Конструктор String заменяет некорректные последовательности на U+FFFD.
    val text = String(bytes, start, bytes.size - start, Charsets.UTF_8)
    val lines = ArrayList<String>()
    var crlf = false
    var malformed = false
    var s = 0
    for (i in text.indices) {
        val ch = text[i]
        if (ch == '�') malformed = true
        if (ch == '\n') {
            if (lines.size >= MAX_DIFF_LINES) throw IOException("Слишком много строк в «${f.name}» (больше ${MAX_DIFF_LINES / 1000} тыс.) — сравнение такого файла займёт слишком много памяти")
            var e = i
            if (e > s && text[e - 1] == '\r') { e--; crlf = true }
            lines += text.substring(s, e)
            s = i + 1
        }
    }
    val finalNewline = s == text.length && text.isNotEmpty()
    if (s < text.length) lines += text.substring(s).removeSuffix("\r")
    return TextFile(f.name, size, lines, crlf, bom, finalNewline, binary, malformed)
}
