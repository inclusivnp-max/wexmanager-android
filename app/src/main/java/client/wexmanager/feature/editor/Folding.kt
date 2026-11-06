package client.wexmanager.feature.editor

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.SparseArray
import client.wexmanager.feature.editor.lang.Language
import io.github.rosemoe.sora.lang.folding.FoldingRegion
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.layout.Layout
import io.github.rosemoe.sora.widget.layout.Row
import io.github.rosemoe.sora.widget.layout.RowIterator
import java.util.ArrayDeque

/**
 * Регионы сворачивания на [FoldingRegion] Sora 0.24. У виджета нет своего fold-layout:
 * маркеры — [LineSideIcon] (поле слева от текста, тап — [io.github.rosemoe.sora.event.SideIconClickEvent]),
 * скрытие строк — обёртка над текущим layout.
 */
class FoldingState {
    private val lock = Any()
    private var regions: List<FoldingRegion> = emptyList()

    @Volatile var markerColor: Int = 0xFF888888.toInt()

    /** С анализатора: прыжок в UI, чтобы перерисовать маркеры. */
    var onChanged: (() -> Unit)? = null

    fun snapshot(): List<FoldingRegion> = synchronized(lock) { regions.toList() }

    fun anyCollapsed(): Boolean = synchronized(lock) { regions.any { it.isCollapsed } }

    fun isHidden(line: Int): Boolean = synchronized(lock) {
        regions.any { it.isCollapsed && line > it.startLine && line <= it.endLine }
    }

    fun markerCollapsed(line: Int): Boolean = synchronized(lock) {
        regions.any { it.startLine == line && it.isCollapsed }
    }

    /** Видимый заголовок: внешний свёрнутый регион, внутри которого лежит строка. */
    fun headerFor(line: Int): Int = synchronized(lock) {
        var best = -1
        for (r in regions) {
            if (r.isCollapsed && line > r.startLine && line <= r.endLine && (best < 0 || r.startLine < best)) best = r.startLine
        }
        if (best >= 0) best else line.coerceAtLeast(0)
    }

    /** Совпадение по началу и концу сохраняет свёрнутость между проходами разбора. */
    fun publish(fresh: List<FoldingRegion>) {
        synchronized(lock) {
            val old = regions
            for (n in fresh) {
                val prev = old.find { it.startLine == n.startLine && it.endLine == n.endLine }
                if (prev?.isCollapsed == true) n.isCollapsed = true
            }
            regions = fresh
        }
        onChanged?.invoke()
    }

    fun clear() {
        synchronized(lock) { regions = emptyList() }
    }

    /** Как сдвиг line style в Sora: строка строго ниже места правки. */
    fun onInsert(atLine: Int, delta: Int) {
        if (delta == 0) return
        synchronized(lock) {
            regions = regions.mapNotNull { r ->
                val s = if (r.startLine > atLine) r.startLine + delta else r.startLine
                val e = if (r.endLine > atLine) r.endLine + delta else r.endLine
                copyRegion(r, s, e)
            }
        }
    }

    fun onDelete(startLine: Int, endLine: Int) {
        val delta = startLine - endLine
        synchronized(lock) {
            regions = regions.mapNotNull { r ->
                if (r.startLine > startLine && r.startLine <= endLine) return@mapNotNull null
                val s = if (r.startLine > endLine) r.startLine + delta else r.startLine
                val e = when {
                    r.endLine > endLine -> r.endLine + delta
                    r.endLine > startLine -> startLine
                    else -> r.endLine
                }
                copyRegion(r, s, e)
            }
        }
    }

    fun toggle(line: Int): Boolean {
        synchronized(lock) {
            val group = regions.filter { it.startLine == line }
            val target = group.firstOrNull { it.isCollapsed } ?: group.maxByOrNull { it.endLine - it.startLine } ?: return false
            target.isCollapsed = !target.isCollapsed
            return true
        }
    }

    private fun copyRegion(r: FoldingRegion, start: Int, end: Int): FoldingRegion? {
        if (end <= start) return null
        return FoldingRegion(start, r.startColumn, end, r.endColumn).apply { isCollapsed = r.isCollapsed }
    }
}

/** Плюс/минус в поле номера строки. Цвет и свёрнутость читаются при отрисовке, не при разборе. */
class FoldMarkerDrawable(private val state: FoldingState, var line: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val collapsed = state.markerCollapsed(line)
        paint.color = state.markerColor
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (b.width() * 0.08f).coerceAtLeast(1f)
        val inset = b.width() * 0.18f
        canvas.drawRect(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset, paint)
        paint.style = Paint.Style.FILL
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val arm = b.width() * 0.22f
        val t = (b.width() * 0.08f).coerceAtLeast(1f)
        canvas.drawRect(cx - arm, cy - t / 2f, cx + arm, cy + t / 2f, paint)
        if (!collapsed) canvas.drawRect(cx - t / 2f, cy - arm, cx + t / 2f, cy + arm, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal object FoldComputer {
    private const val MAX_REGIONS = 5_000
    private val SMALI_OPEN = Regex("""^\s*\.(method|annotation|subannotation|packed-switch|sparse-switch|array-data)\b""")
    private val SMALI_END = Regex("""^\s*\.end\s+(method|annotation|subannotation|packed-switch|sparse-switch|array-data)\b""")
    private val TAG = Regex("""</?([A-Za-z_][\w:.-]*)([^>]*)>""")
    private val VOID_HTML = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr")

    fun compute(text: Content, language: Language, indent: List<CodeBlock>, cancelled: () -> Boolean): List<FoldingRegion> {
        val n = text.lineCount
        if (n <= 1 || cancelled()) return emptyList()
        val raw = when {
            language.id == "smali" || language.id == "jvm-bytecode" -> smali(text, cancelled)
            language.markup -> tags(text, language.id == "html", cancelled)
            braceLanguage(language) -> brace(text, language, cancelled) + fromIndent(indent)
            else -> fromIndent(indent)
        }
        if (raw.size <= MAX_REGIONS) return raw
        return raw.take(MAX_REGIONS)
    }

    private fun braceLanguage(language: Language): Boolean {
        if (language.id == "text") return false
        val pattern = language.indentAfter?.pattern
        if (pattern != null && '{' in pattern) return true
        return !language.significantIndent && "{}" in language.autoPairs
    }

    private fun fromIndent(blocks: List<CodeBlock>): List<FoldingRegion> {
        val out = ArrayList<FoldingRegion>(blocks.size)
        for (b in blocks) {
            if (b.endLine <= b.startLine) continue
            out += FoldingRegion(b.startLine, b.startColumn.coerceAtLeast(0), b.endLine, b.endColumn.coerceAtLeast(0))
        }
        return out
    }

    private fun smali(text: Content, cancelled: () -> Boolean): List<FoldingRegion> {
        val out = ArrayList<FoldingRegion>()
        val stack = ArrayDeque<Pair<Int, String>>()
        val n = text.lineCount
        for (i in 0 until n) {
            if (i and 1023 == 0 && cancelled()) return emptyList()
            val line = text.getLine(i).toString()
            val end = SMALI_END.find(line)
            if (end != null) {
                val kind = end.groupValues[1]
                while (stack.isNotEmpty() && stack.last().second != kind) stack.removeLast()
                if (stack.isNotEmpty()) {
                    val start = stack.removeLast().first
                    if (i > start) out += FoldingRegion(start, 0, i, 0)
                }
                continue
            }
            val open = SMALI_OPEN.find(line) ?: continue
            stack.addLast(i to open.groupValues[1])
        }
        return out
    }

    private fun tags(text: Content, html: Boolean, cancelled: () -> Boolean): List<FoldingRegion> {
        val out = ArrayList<FoldingRegion>()
        val stack = ArrayDeque<Pair<Int, String>>()
        var inComment = false
        val n = text.lineCount
        for (i in 0 until n) {
            if (i and 1023 == 0 && cancelled()) return emptyList()
            var line = text.getLine(i).toString()
            if (inComment) {
                val end = line.indexOf("-->")
                if (end < 0) continue
                inComment = false
                line = line.substring(end + 3)
            }
            val comment = line.indexOf("<!--")
            if (comment >= 0) {
                val end = line.indexOf("-->", comment + 4)
                if (end < 0) {
                    inComment = true
                    line = line.substring(0, comment)
                } else {
                    line = line.removeRange(comment, end + 3)
                }
            }
            for (m in TAG.findAll(line)) {
                val raw = m.value
                val name = m.groupValues[1]
                if (raw.startsWith("</")) {
                    val want = if (html) name.lowercase() else name
                    while (stack.isNotEmpty() && stack.last().second != want) stack.removeLast()
                    if (stack.isNotEmpty()) {
                        val start = stack.removeLast().first
                        if (i > start) out += FoldingRegion(start, 0, i, 0)
                    }
                    continue
                }
                if (m.groupValues[2].trimEnd().endsWith("/")) continue
                val key = if (html) name.lowercase() else name
                if (html && key in VOID_HTML) continue
                stack.addLast(i to key)
            }
        }
        return out
    }

    private fun brace(text: Content, language: Language, cancelled: () -> Boolean): List<FoldingRegion> {
        val out = ArrayList<FoldingRegion>()
        val stack = ArrayDeque<IntArray>() // line, column
        val lineComment = language.lineComment
        val block = language.blockComment
        var inBlock = false
        val n = text.lineCount
        for (i in 0 until n) {
            if (i and 1023 == 0 && cancelled()) return emptyList()
            val line = text.getLine(i)
            var c = 0
            var string = 0.toChar()
            while (c < line.length) {
                val ch = line[c]
                if (inBlock) {
                    if (block != null && c + block.second.length <= line.length && line.startsWith(block.second, c)) {
                        inBlock = false
                        c += block.second.length
                        continue
                    }
                    c++
                    continue
                }
                if (string != 0.toChar()) {
                    if (ch == '\\' && c + 1 < line.length) { c += 2; continue }
                    if (ch == string) string = 0.toChar()
                    c++
                    continue
                }
                if (lineComment != null && c + lineComment.length <= line.length && line.startsWith(lineComment, c)) break
                if (block != null && c + block.first.length <= line.length && line.startsWith(block.first, c)) {
                    inBlock = true
                    c += block.first.length
                    continue
                }
                if (ch == '"' || ch == '\'' || ch == '`') {
                    string = ch
                    c++
                    continue
                }
                if (ch == '{') stack.addLast(intArrayOf(i, c))
                else if (ch == '}' && stack.isNotEmpty()) {
                    val open = stack.removeLast()
                    if (i > open[0]) out += FoldingRegion(open[0], open[1], i, c)
                }
                c++
            }
        }
        return out
    }
}

/**
 * Прячет строки свёрнутых регионов. [CodeEditor.layout] пакетный, поэтому обёртку ставит [WexCodeEditor].
 * Внутренний layout по-прежнему считает ширины; здесь только карта «видимая строка → строка текста».
 */
internal class FoldLayout(
    private val editor: CodeEditor,
    val inner: Layout,
    private val state: FoldingState,
) : Layout {
    private var rowToLine = IntArray(0)
    private var rowToInner = IntArray(0)
    private var lineToRow = IntArray(0)
    private var rows = 1

    fun rebuild() {
        val lineCount = editor.text.lineCount.coerceAtLeast(1)
        val hidden = BooleanArray(lineCount)
        for (r in state.snapshot()) {
            if (!r.isCollapsed) continue
            val from = (r.startLine + 1).coerceAtLeast(0)
            val to = r.endLine.coerceAtMost(lineCount - 1)
            for (line in from..to) if (line in hidden.indices) hidden[line] = true
        }
        val toLine = ArrayList<Int>(lineCount)
        val toInner = ArrayList<Int>(lineCount)
        val toRow = IntArray(lineCount) { -1 }
        var innerIndex = 0
        for (line in 0 until lineCount) {
            val count = inner.getRowCountForLine(line).coerceAtLeast(1)
            if (!hidden[line]) {
                toRow[line] = toLine.size
                for (k in 0 until count) {
                    toLine += line
                    toInner += innerIndex + k
                }
            }
            innerIndex += count
        }
        if (toLine.isEmpty()) {
            toLine += 0
            toInner += 0
        }
        for (line in 0 until lineCount) if (toRow[line] < 0) {
            val header = state.headerFor(line).coerceIn(0, lineCount - 1)
            toRow[line] = if (header != line && toRow[header] >= 0) toRow[header] else 0
        }
        rowToLine = toLine.toIntArray()
        rowToInner = toInner.toIntArray()
        lineToRow = toRow
        rows = rowToLine.size
    }

    private fun visualOf(line: Int): Int {
        if (lineToRow.isEmpty()) return 0
        return lineToRow[line.coerceIn(0, lineToRow.lastIndex)]
    }

    override fun destroyLayout() {
        inner.destroyLayout()
    }

    override fun getLineNumberForRow(row: Int): Int {
        if (rowToLine.isEmpty()) return 0
        return rowToLine[row.coerceIn(0, rowToLine.lastIndex)]
    }

    override fun obtainRowIterator(initialRow: Int, preloadedLines: SparseArray<io.github.rosemoe.sora.text.ContentLine>?): RowIterator {
        return object : RowIterator {
            private val result = Row()
            private var row = initialRow
            private val start = initialRow
            override fun next(): Row {
                val src = getRowAt(row)
                result.lineIndex = src.lineIndex
                result.isLeadingRow = src.isLeadingRow
                result.isTrailingRow = src.isTrailingRow
                result.startColumn = src.startColumn
                result.endColumn = src.endColumn
                result.inlayHints = src.inlayHints
                result.renderTranslateX = src.renderTranslateX
                row++
                return result
            }
            override fun hasNext(): Boolean = row in 0 until rows
            override fun reset() { row = start }
        }
    }

    private fun innerRow(visual: Int): Int {
        if (rowToInner.isEmpty()) return 0
        return rowToInner[visual.coerceIn(0, rowToInner.lastIndex)]
    }

    override fun getRowAt(rowIndex: Int): Row {
        return try {
            inner.getRowAt(innerRow(rowIndex))
        } catch (_: RuntimeException) {
            // WordwrapLayout.getRowAt падает, пока таблица рядов ещё пустая.
            Row().apply {
                lineIndex = getLineNumberForRow(rowIndex)
                isLeadingRow = true
                isTrailingRow = true
                startColumn = 0
                endColumn = runCatching { editor.text.getColumnCount(lineIndex) }.getOrDefault(0)
                inlayHints = emptyList()
            }
        }
    }

    override fun getLayoutWidth(): Int = inner.layoutWidth

    override fun getLayoutHeight(): Int = rows * editor.rowHeight

    override fun getRowCount(): Int = rows

    override fun getVisualPositionForLayoutOffset(offsetX: Float, offsetY: Float): Layout.VisualLocation {
        val visual = (offsetY / editor.rowHeight).toInt().coerceIn(0, rows - 1)
        val y = innerRow(visual) * editor.rowHeight + (offsetY % editor.rowHeight)
        return inner.getVisualPositionForLayoutOffset(offsetX, y)
    }

    override fun getCharLayoutOffset(line: Int, column: Int, array: FloatArray?): FloatArray {
        val dest = if (array == null || array.size < 2) FloatArray(2) else array
        if (state.isHidden(line)) {
            dest[0] = editor.getRowBottom(visualOf(line)).toFloat()
            dest[1] = 0f
            return dest
        }
        val at = inner.getCharLayoutOffset(line, column, FloatArray(2))
        val origin = inner.getCharLayoutOffset(line, 0, FloatArray(2))
        val sub = ((at[0] - origin[0]) / editor.rowHeight).toInt().coerceAtLeast(0)
        dest[0] = editor.getRowBottom(visualOf(line) + sub).toFloat()
        dest[1] = at[1]
        return dest
    }

    override fun getRowCountForLine(line: Int): Int {
        if (state.isHidden(line)) return 0
        return inner.getRowCountForLine(line).coerceAtLeast(1)
    }

    override fun getUpPosition(line: Int, column: Int): Long = skipHidden(inner.getUpPosition(line, column), up = true)

    override fun getDownPosition(line: Int, column: Int): Long = skipHidden(inner.getDownPosition(line, column), up = false)

    private fun skipHidden(packed: Long, up: Boolean): Long {
        var line = IntPair.getFirst(packed)
        var column = IntPair.getSecond(packed)
        var guard = 0
        while (state.isHidden(line) && guard++ < 10_000) {
            val next = if (up) inner.getUpPosition(line, column) else inner.getDownPosition(line, column)
            val n = IntPair.getFirst(next)
            if (n == line) {
                line = state.headerFor(line)
                break
            }
            line = n
            column = IntPair.getSecond(next)
        }
        return IntPair.pack(line, column)
    }

    override fun getRowIndexForPosition(index: Int): Int {
        val line = editor.text.indexer.getCharPosition(index).line
        return visualOf(line)
    }

    override fun invalidateLines(range: io.github.rosemoe.sora.lang.analysis.StyleUpdateRange) {
        inner.invalidateLines(range)
    }

    override fun beforeReplace(content: Content) {
        inner.beforeReplace(content)
    }

    override fun afterInsert(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, insertedContent: CharSequence) {
        inner.afterInsert(content, startLine, startColumn, endLine, endColumn, insertedContent)
    }

    override fun afterDelete(content: Content, startLine: Int, startColumn: Int, endLine: Int, endColumn: Int, deletedContent: CharSequence) {
        inner.afterDelete(content, startLine, startColumn, endLine, endColumn, deletedContent)
    }
}
