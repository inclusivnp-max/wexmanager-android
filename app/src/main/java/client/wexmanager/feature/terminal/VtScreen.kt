package client.wexmanager.feature.terminal

import androidx.compose.runtime.Immutable

internal const val COLOR_DEFAULT = -1
private const val RGB_FLAG = 0x01000000
private const val MAX_SCROLLBACK = 2000
private const val CONT = "\u0001"
private const val UTF8_PENDING = -1
private const val UTF8_RETRY = -2

@Immutable
data class TermSpan(
    val text: String,
    val fg: Int,
    val bg: Int,
    val bold: Boolean,
    val reverse: Boolean,
    val cursor: Boolean,
)

@Immutable
data class TermLine(
    val id: Long,
    val spans: List<TermSpan>,
)

internal class TermSnapshot(
    val cols: Int,
    val rows: Int,
    val scrollback: List<TermLine>,
    val screen: List<TermLine>,
)

/**
 * Экран VT: UTF-8, перевод строки, прокрутка и набор CSI, которого хватает оболочке и
 * простым полноэкранным программам. Неизвестные последовательности съедаются, а не печатаются.
 */
internal class VtScreen {
    var cols: Int = 80
        private set
    var rows: Int = 24
        private set

    @Volatile
    var applicationCursor: Boolean = false
        private set

    @Volatile
    var bracketedPaste: Boolean = false
        private set

    private var primary = Grid(rows, cols)
    private var alternate = Grid(rows, cols)
    private var alt = false
    private val grid: Grid
        get() = if (alt) alternate else primary

    private val scrollback = ArrayDeque<TermLine>()
    private var scrollSnap: List<TermLine> = emptyList()
    private var scrollDirty = false
    private var nextId = 1L

    private var row = 0
    private var col = 0
    private var wrapPending = false
    private var scrollTop = 0
    private var scrollBottom = rows - 1
    private var origin = false
    private var autowrap = true
    private var cursorVisible = true
    private var in1049 = false

    private var penFg = COLOR_DEFAULT
    private var penBg = COLOR_DEFAULT
    private var penBold = false
    private var penRev = false

    private var savedRow = 0
    private var savedCol = 0
    private var savedFg = COLOR_DEFAULT
    private var savedBg = COLOR_DEFAULT
    private var savedBold = false
    private var savedRev = false
    private var savedWrap = false
    private var hasSave = false
    private var save1049Row = 0
    private var save1049Col = 0

    private var state = State.GROUND
    private val params = IntArray(16)
    private var nParams = 0
    private var current = 0
    private var collecting = false
    private var marker = 0
    private var intermediate = 0
    private var oscEsc = false
    private var oscLen = 0
    private val utf8 = Utf8()
    private val replies = ArrayList<ByteArray>(2)

    fun resize(newCols: Int, newRows: Int): Boolean {
        if (newCols < 2 || newRows < 2 || newCols > 500 || newRows > 200) return false
        if (newCols == cols && newRows == rows) return false
        val oldPrimary = primary
        val oldAlternate = alternate
        val oldCursor = row
        val keepFrom = if (!alt && oldCursor >= newRows) oldCursor + 1 - newRows else 0
        if (!alt && keepFrom > 0) {
            for (r in 0 until keepFrom) {
                if (!isBlank(oldPrimary, r)) remember(render(oldPrimary, r, -1))
            }
        }
        primary = Grid(newRows, newCols)
        alternate = Grid(newRows, newCols)
        copyGrid(oldPrimary, keepFrom, primary)
        copyGrid(oldAlternate, 0, alternate)
        cols = newCols
        rows = newRows
        row = (oldCursor - if (alt) 0 else keepFrom).coerceIn(0, newRows - 1)
        col = col.coerceIn(0, newCols - 1)
        scrollTop = 0
        scrollBottom = newRows - 1
        wrapPending = false
        savedRow = savedRow.coerceIn(0, newRows - 1)
        savedCol = savedCol.coerceIn(0, newCols - 1)
        save1049Row = save1049Row.coerceIn(0, newRows - 1)
        save1049Col = save1049Col.coerceIn(0, newCols - 1)
        return true
    }

    fun feed(bytes: ByteArray, length: Int) {
        var index = 0
        while (index < length) {
            val b = bytes[index].toInt() and 0xFF
            when (state) {
                State.GROUND -> when {
                    b == 0x1B -> state = State.ESC
                    b < 0x20 || b == 0x7F -> control(b)
                    else -> {
                        var cp = utf8.feed(b)
                        if (cp == UTF8_RETRY) cp = utf8.feed(b)
                        if (cp >= 0) put(cp)
                    }
                }
                State.ESC -> escape(b)
                State.CSI -> csiByte(b)
                State.STRING -> stringByte(b)
                State.CHARSET -> state = if (b == 0x1B) State.ESC else State.GROUND
            }
            index++
        }
    }

    fun takeReplies(): List<ByteArray> {
        if (replies.isEmpty()) return emptyList()
        val copy = replies.toList()
        replies.clear()
        return copy
    }

    fun snapshot(): TermSnapshot {
        if (scrollDirty) {
            scrollSnap = scrollback.toList()
            scrollDirty = false
        }
        val current = grid
        val lines = ArrayList<TermLine>(rows)
        for (r in 0 until rows) {
            val cursor = if (cursorVisible && r == row) col else -1
            lines.add(TermLine(-(r + 1L), render(current, r, cursor)))
        }
        return TermSnapshot(cols, rows, scrollSnap, lines)
    }

    fun copyText(): String {
        val lines = ArrayList<String>(scrollback.size + rows)
        for (line in scrollback) lines.add(plain(line.spans))
        val current = grid
        for (r in 0 until rows) lines.add(plain(render(current, r, -1)))
        while (lines.isNotEmpty() && lines.last().isEmpty()) lines.removeAt(lines.lastIndex)
        return lines.joinToString("\n")
    }

    /** Стирает текст, курсор оболочки не двигает: эхо ядра считает колонку само. */
    fun clearDisplay() {
        scrollback.clear()
        scrollDirty = true
        clearGrid(primary)
        clearGrid(alternate)
    }

    /** Перед новым процессом: видимые строки уходят в историю, режимы — как у свежего VT. */
    fun archiveAndReset() {
        val current = grid
        for (r in 0 until rows) {
            if (!isBlank(current, r)) remember(render(current, r, -1))
        }
        fullReset(keepScrollback = true)
    }

    private fun remember(spans: List<TermSpan>) {
        scrollback.addLast(TermLine(nextId++, spans))
        while (scrollback.size > MAX_SCROLLBACK) scrollback.removeFirst()
        scrollDirty = true
    }

    private fun put(cp: Int) {
        val width = width(cp)
        if (width <= 0) {
            if (width == 0) appendCombining(cp)
            return
        }
        if (wrapPending) {
            wrapPending = false
            if (autowrap) {
                lineFeed()
                col = 0
            }
        }
        if (col + width > cols) {
            if (autowrap) {
                lineFeed()
                col = 0
            } else {
                col = (cols - width).coerceAtLeast(0)
            }
        }
        writeCell(row, col, cpString(cp), width == 2)
        col += width
        if (col >= cols) {
            col = cols - 1
            wrapPending = autowrap
        }
    }

    private fun appendCombining(cp: Int) {
        var at = if (wrapPending) col else col - 1
        if (at < 0) return
        val current = grid
        if (current.text[row][at] == CONT && at > 0) at--
        val cell = current.text[row][at]
        if (cell.isEmpty() || cell == CONT || cell.length >= 16) return
        current.text[row][at] = cell + cpString(cp)
    }

    private fun control(b: Int) {
        when (b) {
            0x0D -> {
                col = 0
                wrapPending = false
            }
            0x0A, 0x0B, 0x0C -> lineFeed()
            0x08 -> {
                if (wrapPending) wrapPending = false
                else if (col > 0) col--
            }
            0x09 -> {
                wrapPending = false
                val next = (col / 8 + 1) * 8
                col = if (next >= cols) cols - 1 else next
            }
        }
    }

    private fun lineFeed() {
        wrapPending = false
        if (row == scrollBottom) scrollUp(1)
        else if (row < rows - 1) row++
    }

    private fun reverseIndex() {
        wrapPending = false
        if (row == scrollTop) scrollDown(1)
        else if (row > 0) row--
    }

    private fun nel() {
        col = 0
        wrapPending = false
        lineFeed()
    }

    private fun escape(b: Int) {
        when (b) {
            '['.code -> {
                beginCsi()
                state = State.CSI
            }
            ']'.code, 'P'.code, '^'.code, '_'.code, 'X'.code -> {
                state = State.STRING
                oscEsc = false
                oscLen = 0
            }
            '7'.code -> {
                saveCursor()
                state = State.GROUND
            }
            '8'.code -> {
                restoreCursor()
                state = State.GROUND
            }
            'D'.code -> {
                lineFeed()
                state = State.GROUND
            }
            'M'.code -> {
                reverseIndex()
                state = State.GROUND
            }
            'E'.code -> {
                nel()
                state = State.GROUND
            }
            'c'.code -> {
                fullReset(keepScrollback = false)
                state = State.GROUND
            }
            '('.code, ')'.code, '*'.code, '+'.code, '-'.code, '.'.code, '/'.code -> state = State.CHARSET
            else -> state = State.GROUND
        }
    }

    private fun beginCsi() {
        nParams = 0
        current = 0
        collecting = false
        marker = 0
        intermediate = 0
    }

    private fun csiByte(b: Int) {
        when {
            b == 0x1B -> {
                state = State.ESC
                beginCsi()
            }
            b == 0x18 || b == 0x1A -> state = State.GROUND
            b < 0x20 -> control(b)
            b in '0'.code..'9'.code -> {
                collecting = true
                current = (current * 10 + (b - '0'.code)).coerceAtMost(9999)
            }
            b == ';'.code -> addParam(if (collecting) current else -1)
            (b == '?'.code || b == '>'.code || b == '='.code || b == '<'.code) && marker == 0 && !collecting && nParams == 0 ->
                marker = b
            b in 0x20..0x2F -> intermediate = b
            b in 0x40..0x7E -> {
                endParams()
                if (intermediate == 0) dispatch(b)
                state = State.GROUND
            }
            else -> state = State.GROUND
        }
    }

    private fun stringByte(b: Int) {
        if (oscEsc) {
            oscEsc = false
            if (b == '\\'.code) state = State.GROUND
            else if (b == 0x1B) oscEsc = true
            return
        }
        when (b) {
            0x07, 0x18, 0x1A -> state = State.GROUND
            0x1B -> oscEsc = true
            else -> if (oscLen < 2048) oscLen++
        }
    }

    private fun addParam(value: Int) {
        if (nParams < params.size) params[nParams] = value
        nParams++
        current = 0
        collecting = false
    }

    private fun endParams() {
        if (collecting || nParams > 0) addParam(if (collecting) current else -1)
    }

    private fun param(index: Int, default: Int): Int {
        if (index >= nParams || index >= params.size) return default
        val value = params[index]
        return if (value < 0) default else value
    }

    private fun dispatch(final: Int) {
        if (marker == '?'.code) {
            if (final == 'h'.code) decMode(true)
            else if (final == 'l'.code) decMode(false)
            return
        }
        if (marker == '>'.code) {
            if (final == 'c'.code) replies.add("\u001b[>0;0;0c".encodeToByteArray())
            return
        }
        if (marker != 0) return
        when (final) {
            'A'.code -> moveRow(-param(0, 1).coerceAtLeast(1))
            'B'.code -> moveRow(param(0, 1).coerceAtLeast(1))
            'C'.code -> moveCol(param(0, 1).coerceAtLeast(1))
            'D'.code -> moveCol(-param(0, 1).coerceAtLeast(1))
            'H'.code, 'f'.code -> cup(param(0, 1), param(1, 1))
            'G'.code -> {
                col = (param(0, 1).coerceAtLeast(1) - 1).coerceAtMost(cols - 1)
                wrapPending = false
            }
            'd'.code -> cup(param(0, 1), col + 1)
            'J'.code -> eraseDisplay(param(0, 0))
            'K'.code -> eraseLine(param(0, 0))
            'm'.code -> sgr()
            's'.code -> saveCursor()
            'u'.code -> restoreCursor()
            'r'.code -> setScrollRegion(param(0, 1), param(1, rows))
            'L'.code -> insertLines(param(0, 1).coerceAtLeast(1))
            'M'.code -> deleteLines(param(0, 1).coerceAtLeast(1))
            '@'.code -> insertChars(param(0, 1).coerceAtLeast(1))
            'P'.code -> deleteChars(param(0, 1).coerceAtLeast(1))
            'X'.code -> eraseRange(row, col, (col + param(0, 1).coerceAtLeast(1) - 1).coerceAtMost(cols - 1))
            'S'.code -> scrollUp(param(0, 1).coerceAtLeast(1))
            'T'.code -> scrollDown(param(0, 1).coerceAtLeast(1))
            'n'.code -> deviceStatus(param(0, 0))
            'c'.code -> if (param(0, 0) == 0) replies.add("\u001b[?6c".encodeToByteArray())
        }
    }

    private fun decMode(enable: Boolean) {
        val count = if (nParams == 0) 1 else nParams.coerceAtMost(params.size)
        for (index in 0 until count) {
            when (param(index, 0)) {
                1 -> applicationCursor = enable
                6 -> {
                    origin = enable
                    cup(1, 1)
                }
                7 -> autowrap = enable
                25 -> cursorVisible = enable
                47 -> {
                    alt = enable
                    wrapPending = false
                }
                1049 -> if (enable) enter1049() else leave1049()
                2004 -> bracketedPaste = enable
            }
        }
    }

    private fun enter1049() {
        if (!in1049) {
            save1049Row = row
            save1049Col = col
            in1049 = true
        }
        alt = true
        clearGrid(alternate)
        row = 0
        col = 0
        wrapPending = false
    }

    private fun leave1049() {
        alt = false
        if (in1049) {
            row = save1049Row.coerceIn(0, rows - 1)
            col = save1049Col.coerceIn(0, cols - 1)
            in1049 = false
        }
        wrapPending = false
    }

    private fun moveRow(delta: Int) {
        wrapPending = false
        val top = if (origin) scrollTop else 0
        val bottom = if (origin) scrollBottom else rows - 1
        row = (row + delta).coerceIn(top, bottom)
    }

    private fun moveCol(delta: Int) {
        wrapPending = false
        col = (col + delta).coerceIn(0, cols - 1)
    }

    private fun cup(row1: Int, col1: Int) {
        val base = if (origin) scrollTop else 0
        val limit = if (origin) scrollBottom else rows - 1
        row = (base + row1.coerceAtLeast(1) - 1).coerceIn(base, limit)
        col = (col1.coerceAtLeast(1) - 1).coerceIn(0, cols - 1)
        wrapPending = false
    }

    private fun setScrollRegion(top1: Int, bottom1: Int) {
        var top = (top1.coerceAtLeast(1) - 1).coerceIn(0, rows - 1)
        var bottom = (bottom1.coerceAtLeast(1) - 1).coerceIn(0, rows - 1)
        if (top > bottom) {
            top = 0
            bottom = rows - 1
        }
        scrollTop = top
        scrollBottom = bottom
        cup(1, 1)
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseRange(row, col, cols - 1)
                for (r in row + 1 until rows) eraseRange(r, 0, cols - 1)
            }
            1 -> {
                for (r in 0 until row) eraseRange(r, 0, cols - 1)
                eraseRange(row, 0, col)
            }
            2, 3 -> {
                if (mode == 3) {
                    scrollback.clear()
                    scrollDirty = true
                }
                for (r in 0 until rows) eraseRange(r, 0, cols - 1)
            }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> eraseRange(row, col, cols - 1)
            1 -> eraseRange(row, 0, col)
            2 -> eraseRange(row, 0, cols - 1)
        }
    }

    private fun sgr() {
        if (nParams == 0) {
            resetPen()
            return
        }
        var index = 0
        val limit = nParams.coerceAtMost(params.size)
        while (index < limit) {
            val value = param(index, 0)
            when (value) {
                0 -> resetPen()
                1 -> penBold = true
                21, 22 -> penBold = false
                7 -> penRev = true
                27 -> penRev = false
                39 -> penFg = COLOR_DEFAULT
                49 -> penBg = COLOR_DEFAULT
                in 30..37 -> penFg = value - 30
                in 40..47 -> penBg = value - 40
                in 90..97 -> penFg = value - 90 + 8
                in 100..107 -> penBg = value - 100 + 8
                38, 48 -> {
                    val mode = param(index + 1, -1)
                    if (mode == 5 && index + 2 < limit) {
                        val color = color256(param(index + 2, 0))
                        if (value == 38) penFg = color else penBg = color
                        index += 3
                        continue
                    } else if (mode == 2 && index + 4 < limit) {
                        val red = param(index + 2, 0).coerceIn(0, 255)
                        val green = param(index + 3, 0).coerceIn(0, 255)
                        val blue = param(index + 4, 0).coerceIn(0, 255)
                        val color = RGB_FLAG or (red shl 16) or (green shl 8) or blue
                        if (value == 38) penFg = color else penBg = color
                        index += 5
                        continue
                    }
                }
            }
            index++
        }
    }

    private fun deviceStatus(mode: Int) {
        when (mode) {
            5 -> replies.add("\u001b[0n".encodeToByteArray())
            6 -> replies.add("\u001b[${row + 1};${col + 1}R".encodeToByteArray())
        }
    }

    private fun resetPen() {
        penFg = COLOR_DEFAULT
        penBg = COLOR_DEFAULT
        penBold = false
        penRev = false
    }

    private fun saveCursor() {
        savedRow = row
        savedCol = col
        savedFg = penFg
        savedBg = penBg
        savedBold = penBold
        savedRev = penRev
        savedWrap = wrapPending
        hasSave = true
    }

    private fun restoreCursor() {
        if (!hasSave) return
        row = savedRow.coerceIn(0, rows - 1)
        col = savedCol.coerceIn(0, cols - 1)
        penFg = savedFg
        penBg = savedBg
        penBold = savedBold
        penRev = savedRev
        wrapPending = savedWrap
    }

    private fun fullReset(keepScrollback: Boolean) {
        resetPen()
        applicationCursor = false
        bracketedPaste = false
        autowrap = true
        origin = false
        cursorVisible = true
        alt = false
        in1049 = false
        wrapPending = false
        hasSave = false
        row = 0
        col = 0
        scrollTop = 0
        scrollBottom = rows - 1
        clearGrid(primary)
        clearGrid(alternate)
        utf8.reset()
        state = State.GROUND
        if (!keepScrollback) {
            scrollback.clear()
            scrollDirty = true
        }
    }

    private fun scrollUp(times: Int) {
        repeat(times.coerceAtLeast(1)) {
            if (!alt && scrollTop == 0) remember(render(grid, scrollTop, -1))
            val saved = takeRow(grid, scrollTop)
            for (r in scrollTop until scrollBottom) setRow(grid, r, takeRow(grid, r + 1))
            blank(saved)
            setRow(grid, scrollBottom, saved)
        }
    }

    private fun scrollDown(times: Int) {
        repeat(times.coerceAtLeast(1)) {
            val saved = takeRow(grid, scrollBottom)
            for (r in scrollBottom downTo scrollTop + 1) setRow(grid, r, takeRow(grid, r - 1))
            blank(saved)
            setRow(grid, scrollTop, saved)
        }
    }

    private fun insertLines(times: Int) {
        if (row !in scrollTop..scrollBottom) return
        val count = times.coerceAtMost(scrollBottom - row + 1)
        repeat(count) {
            val saved = takeRow(grid, scrollBottom)
            for (r in scrollBottom downTo row + 1) setRow(grid, r, takeRow(grid, r - 1))
            blank(saved)
            setRow(grid, row, saved)
        }
    }

    private fun deleteLines(times: Int) {
        if (row !in scrollTop..scrollBottom) return
        val count = times.coerceAtMost(scrollBottom - row + 1)
        repeat(count) {
            val saved = takeRow(grid, row)
            for (r in row until scrollBottom) setRow(grid, r, takeRow(grid, r + 1))
            blank(saved)
            setRow(grid, scrollBottom, saved)
        }
    }

    private fun insertChars(times: Int) {
        val count = times.coerceAtMost((cols - col).coerceAtLeast(0))
        if (count == 0) return
        val current = grid
        for (c in cols - 1 downTo col + count) copyCell(current, row, c - count, current, row, c)
        for (c in col until col + count) blankCell(current, row, c)
        repair(current, row)
    }

    private fun deleteChars(times: Int) {
        val count = times.coerceAtMost((cols - col).coerceAtLeast(0))
        if (count == 0) return
        val current = grid
        for (c in col until cols - count) copyCell(current, row, c + count, current, row, c)
        for (c in cols - count until cols) blankCell(current, row, c)
        repair(current, row)
    }

    private fun writeCell(r: Int, c: Int, text: String, wide: Boolean) {
        val current = grid
        clearWide(current, r, c)
        if (wide && c + 1 < cols) clearWide(current, r, c + 1)
        current.text[r][c] = text
        paint(current, r, c, penBold)
        if (wide && c + 1 < cols) {
            current.text[r][c + 1] = CONT
            paint(current, r, c + 1, false)
        }
    }

    private fun clearWide(g: Grid, r: Int, c: Int) {
        if (c > 0 && g.text[r][c] == CONT) g.text[r][c - 1] = ""
        if (c + 1 < g.cols && g.text[r][c + 1] == CONT) g.text[r][c + 1] = ""
        g.text[r][c] = ""
    }

    private fun eraseRange(r: Int, from: Int, to: Int) {
        if (r !in 0 until rows || from > to) return
        var start = from.coerceIn(0, cols - 1)
        var end = to.coerceIn(0, cols - 1)
        val current = grid
        if (start > 0 && current.text[r][start] == CONT) start--
        if (end + 1 < cols && current.text[r][end + 1] == CONT) end++
        for (c in start..end) blankCell(current, r, c)
    }

    private fun paint(g: Grid, r: Int, c: Int, bold: Boolean) {
        g.fg[r][c] = penFg
        g.bg[r][c] = penBg
        g.bold[r][c] = bold
        g.rev[r][c] = penRev
    }

    private fun blankCell(g: Grid, r: Int, c: Int) {
        g.text[r][c] = ""
        paint(g, r, c, false)
    }

    private fun clearGrid(g: Grid) {
        for (r in 0 until g.rows) {
            for (c in 0 until g.cols) blankCell(g, r, c)
        }
    }

    private fun blank(held: Held) {
        for (c in held.text.indices) {
            held.text[c] = ""
            held.fg[c] = penFg
            held.bg[c] = penBg
            held.bold[c] = false
            held.rev[c] = penRev
        }
    }

    private fun repair(g: Grid, r: Int) {
        var c = 0
        while (c < g.cols) {
            if (g.text[r][c] == CONT) {
                g.text[r][c] = ""
                c++
                continue
            }
            if (c + 1 < g.cols && g.text[r][c + 1] == CONT) c += 2 else c++
        }
    }

    private fun isBlank(g: Grid, r: Int): Boolean {
        for (c in 0 until g.cols) if (g.text[r][c].isNotEmpty()) return false
        return true
    }

    private fun render(g: Grid, r: Int, cursorCol: Int): List<TermSpan> {
        val spans = ArrayList<TermSpan>(8)
        val text = StringBuilder()
        var fg = 0
        var bg = 0
        var bold = false
        var reverse = false
        var cursor = false
        var open = false
        fun flush() {
            if (text.isEmpty()) return
            spans.add(TermSpan(text.toString(), fg, bg, bold, reverse, cursor))
            text.setLength(0)
        }
        var cursorAt = cursorCol
        if (cursorAt > 0 && cursorAt < g.cols && g.text[r][cursorAt] == CONT) cursorAt--
        var c = 0
        while (c < g.cols) {
            val raw = g.text[r][c]
            if (raw == CONT) {
                c++
                continue
            }
            val chunk = if (raw.isEmpty()) " " else raw
            val atCursor = c == cursorAt
            if (open && g.fg[r][c] == fg && g.bg[r][c] == bg && g.bold[r][c] == bold && g.rev[r][c] == reverse && atCursor == cursor) {
                text.append(chunk)
            } else {
                flush()
                fg = g.fg[r][c]
                bg = g.bg[r][c]
                bold = g.bold[r][c]
                reverse = g.rev[r][c]
                cursor = atCursor
                open = true
                text.append(chunk)
            }
            c++
        }
        flush()
        if (spans.isEmpty()) spans.add(TermSpan(" ", COLOR_DEFAULT, COLOR_DEFAULT, false, false, false))
        return spans
    }

    private fun plain(spans: List<TermSpan>): String {
        val text = StringBuilder()
        for (span in spans) text.append(span.text)
        return text.trimEnd().toString()
    }

    private fun copyGrid(src: Grid, fromRow: Int, dst: Grid) {
        val rowsToCopy = minOf(dst.rows, src.rows - fromRow).coerceAtLeast(0)
        for (r in 0 until rowsToCopy) {
            val srcRow = r + fromRow
            var c = 0
            while (c < dst.cols && c < src.cols) {
                val cell = src.text[srcRow][c]
                if (cell == CONT) {
                    c++
                    continue
                }
                val wide = c + 1 < src.cols && src.text[srcRow][c + 1] == CONT
                if (wide && c + 1 >= dst.cols) break
                dst.text[r][c] = cell
                copyAttr(src, srcRow, c, dst, r, c)
                if (wide) {
                    dst.text[r][c + 1] = CONT
                    copyAttr(src, srcRow, c + 1, dst, r, c + 1)
                    c += 2
                } else {
                    c++
                }
            }
        }
    }

    private fun copyAttr(src: Grid, srcRow: Int, srcCol: Int, dst: Grid, dstRow: Int, dstCol: Int) {
        dst.fg[dstRow][dstCol] = src.fg[srcRow][srcCol]
        dst.bg[dstRow][dstCol] = src.bg[srcRow][srcCol]
        dst.bold[dstRow][dstCol] = src.bold[srcRow][srcCol]
        dst.rev[dstRow][dstCol] = src.rev[srcRow][srcCol]
    }

    private fun copyCell(g: Grid, fromRow: Int, fromCol: Int, dst: Grid, toRow: Int, toCol: Int) {
        dst.text[toRow][toCol] = g.text[fromRow][fromCol]
        copyAttr(g, fromRow, fromCol, dst, toRow, toCol)
    }

    private fun takeRow(g: Grid, r: Int) = Held(g.text[r], g.fg[r], g.bg[r], g.bold[r], g.rev[r])

    private fun setRow(g: Grid, r: Int, held: Held) {
        g.text[r] = held.text
        g.fg[r] = held.fg
        g.bg[r] = held.bg
        g.bold[r] = held.bold
        g.rev[r] = held.rev
    }

    private class Held(
        val text: Array<String>,
        val fg: IntArray,
        val bg: IntArray,
        val bold: BooleanArray,
        val rev: BooleanArray,
    )

    private class Grid(val rows: Int, val cols: Int) {
        val text = Array(rows) { Array(cols) { "" } }
        val fg = Array(rows) { IntArray(cols) { COLOR_DEFAULT } }
        val bg = Array(rows) { IntArray(cols) { COLOR_DEFAULT } }
        val bold = Array(rows) { BooleanArray(cols) }
        val rev = Array(rows) { BooleanArray(cols) }
    }

    private enum class State { GROUND, ESC, CSI, STRING, CHARSET }

    private class Utf8 {
        private var need = 0
        private var cp = 0
        private var lower = 0

        fun reset() {
            need = 0
            cp = 0
            lower = 0
        }

        fun feed(b: Int): Int {
            if (need == 0) {
                if (b < 0x80) return b
                if (b and 0xE0 == 0xC0 && b >= 0xC2) {
                    need = 1
                    cp = b and 0x1F
                    lower = 0x80
                    return UTF8_PENDING
                }
                if (b and 0xF0 == 0xE0) {
                    need = 2
                    cp = b and 0x0F
                    lower = 0x800
                    return UTF8_PENDING
                }
                if (b and 0xF8 == 0xF0 && b <= 0xF4) {
                    need = 3
                    cp = b and 0x07
                    lower = 0x10000
                    return UTF8_PENDING
                }
                return 0xFFFD
            }
            if (b and 0xC0 != 0x80) {
                need = 0
                return UTF8_RETRY
            }
            cp = (cp shl 6) or (b and 0x3F)
            need--
            if (need > 0) return UTF8_PENDING
            val out = cp
            val min = lower
            cp = 0
            lower = 0
            if (out < min || out > 0x10FFFF || out in 0xD800..0xDFFF) return 0xFFFD
            return out
        }
    }

    private fun width(cp: Int): Int {
        if (cp == 0 || cp == 0xFEFF) return 0
        if (cp in 0x300..0x36F || cp in 0x1AB0..0x1AFF || cp in 0x1DC0..0x1DFF) return 0
        if (cp in 0x20D0..0x20FF || cp in 0xFE20..0xFE2F || cp in 0xFE00..0xFE0F) return 0
        if (cp in 0x200B..0x200F || cp in 0x202A..0x202E || cp in 0x2060..0x2069) return 0
        if (cp in 0x1100..0x115F || cp == 0x2329 || cp == 0x232A) return 2
        if (cp in 0x2E80..0xA4CF || cp in 0xAC00..0xD7A3 || cp in 0xF900..0xFAFF) return 2
        if (cp in 0xFE10..0xFE19 || cp in 0xFE30..0xFE6F || cp in 0xFF00..0xFF60 || cp in 0xFFE0..0xFFE6) return 2
        if (cp in 0x1F300..0x1F64F || cp in 0x1F900..0x1F9FF || cp in 0x20000..0x3FFFD) return 2
        return 1
    }

    private fun color256(n: Int): Int {
        val index = n and 255
        if (index < 16) return index
        if (index >= 232) {
            val level = (index - 232) * 10 + 8
            return RGB_FLAG or (level shl 16) or (level shl 8) or level
        }
        val cube = index - 16
        val red = cube / 36
        val green = (cube / 6) % 6
        val blue = cube % 6
        fun channel(value: Int) = if (value == 0) 0 else 55 + value * 40
        return RGB_FLAG or (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
    }

    private fun cpString(cp: Int): String {
        return if (cp <= 0xFFFF) cp.toChar().toString() else String(Character.toChars(cp))
    }
}
