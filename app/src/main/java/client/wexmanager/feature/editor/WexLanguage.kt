package client.wexmanager.feature.editor

import android.os.Bundle
import client.wexmanager.feature.editor.lang.CompletionEngine
import client.wexmanager.feature.editor.lang.CompletionRequest
import client.wexmanager.feature.editor.lang.GoWorkspaceIndex
import client.wexmanager.feature.editor.lang.Language
import client.wexmanager.feature.editor.lang.LineLexer
import client.wexmanager.feature.editor.lang.SyntaxInfo
import client.wexmanager.feature.editor.lang.Tok
import client.wexmanager.feature.editor.lang.Token
import client.wexmanager.feature.editor.lang.WordIndex
import client.wexmanager.feature.editor.lang.WordIndexer
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager
import io.github.rosemoe.sora.lang.analysis.IncrementalAnalyzeManager.LineTokenizeResult
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.CodeBlock
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.line.LineSideIcon
import io.github.rosemoe.sora.lang.styling.SpanFactory
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.SymbolPairMatch
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Язык редактора на лексерах и подсказках из WEXdesktop: построчный разбор с состоянием конца строки
 * (правка перелексирует только затронутые строки — дальше, пока состояние не совпадёт с прежним),
 * блоки по отступам для направляющих и закреплённых заголовков, подсказки с нечётким поиском.
 */
class WexLanguage(
    val language: Language,
    highlight: Boolean,
    private val tabSize: Int,
    private val useSpaces: Boolean,
    private val autoPopup: Boolean,
    private val baseDir: File?,
) : EmptyLanguage() {
    /** Общее с [WexCodeEditor]: регионы переживают смену языка (размер таба, подсветка). */
    var folds: FoldingState? = null

    /** Большой файл: регионы не строим, даже если подсветку включили вручную. */
    var foldEnabled: Boolean = true

    private val analyzer = WexAnalyzer(if (highlight) language.lexer else null, tabSize, this)

    init {
        CompletionEngine.homeDir = android.os.Environment.getExternalStorageDirectory().path
    }
    private val indentUnit: String get() = if (useSpaces) " ".repeat(tabSize) else "\t"

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    /** Сколько полных проходов разбора завершено (растёт после каждой правки и загрузки текста). */
    val analysisPasses: Int get() = analyzer.passes

    /** Число строк текста, разобранного последним проходом. */
    val analyzedLines: Int get() = analyzer.lastLines
    override fun getInterruptionLevel(): Int = INTERRUPTION_LEVEL_STRONG
    override fun useTab(): Boolean = !useSpaces

    // ---------- Подсказки ----------

    /** Следующий запрос — по Ctrl+Space: показывать и без набранного префикса. */
    private val explicitNext = AtomicBoolean(false)

    fun requestExplicit() = explicitNext.set(true)

    @Volatile private var index: WordIndex? = null
    @Volatile private var indexAt = 0L
    private val indexing = AtomicBoolean(false)
    private val indexer = Executors.newSingleThreadExecutor { r -> Thread(r, "wex-word-index").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }

    /** Слова файла для подсказок: строятся в фоне не чаще раза в полторы секунды, пока — прежний индекс. */
    private fun indexFor(text: String, version: Long): WordIndex? {
        val cur = index
        if (cur != null && (cur.version == version || System.nanoTime() - indexAt < 1_500_000_000L)) return cur
        if (cur == null && text.length < 300_000) {
            return WordIndexer.build(text, language, version).also { index = it; indexAt = System.nanoTime() }
        }
        if (indexing.compareAndSet(false, true)) {
            indexer.execute {
                try {
                    index = WordIndexer.build(text, language, version)
                    indexAt = System.nanoTime()
                } catch (_: Throwable) {
                } finally {
                    indexing.set(false)
                }
            }
        }
        return cur
    }

    override fun requireAutoComplete(content: ContentReference, position: CharPosition, publisher: CompletionPublisher, extraArguments: Bundle) {
        val explicit = explicitNext.getAndSet(false)
        if (!autoPopup && !explicit) return
        try {
            val caret = position.index
            if (!explicit) {
                // Как в десктопе: окно открывает буква слова или символ-повод (точка, «<», «$», «/»…)
                val ch = if (position.column > 0) content.charAt(position.line, position.column - 1) else return
                if (!language.isWordChar(ch) && ch !in triggers()) return
            }
            val text = content.toString()
            val syntax = LineSyntax(text, position.line, caret - position.column)
            // Символы проекта читаются из кэша в памяти: скан идёт в фоне и на ввод не влияет.
            val project = if (language.id == "go") GoWorkspaceIndex.snapshot(baseDir) else null
            val moduleDependencies = if (language.id == "go") GoWorkspaceIndex.moduleInfo(baseDir)?.second.orEmpty() else emptyList()
            val req = CompletionRequest(
                text, caret, language, syntax, indexFor(text, content.documentVersion), explicit, baseDir?.toPath(),
                workspace = project?.allSymbols.orEmpty(),
                workspacePackages = project?.packages.orEmpty(),
                moduleDependencies = moduleDependencies,
            )
            val result = CompletionEngine.complete(req) ?: return
            publisher.checkCancelled()
            val prefixLen = caret - result.replaceStart
            val items = result.items.map { WexCompletionItem(it, prefixLen, indentUnit) }
            publisher.addItems(items)
        } catch (e: java.util.concurrent.CancellationException) {
            return
        }
    }

    private fun triggers(): String = when {
        language.id == "smali" -> ".-/>:["
        language.markup -> "<:@/.?-"
        language.id == "go" -> "./@$\"/"
        language.id == "css" || language.id == "scss" -> "-@"
        language.id == "shell" || language.id == "fish" -> "$-/{"
        else -> "./@$"
    }

    /** Тип токена у курсора: строка перелексируется от состояния конца предыдущей (из анализатора). */
    private inner class LineSyntax(private val text: String, private val caretLine: Int, private val caretLineStart: Int) : SyntaxInfo {
        private var cachedStart = -1
        private var cachedTokens: List<Token> = emptyList()

        override fun tokenAt(offset: Int): Tok? {
            val lexer = analyzer.lexer ?: return null
            if (offset < 0 || offset >= text.length) return null
            val start = if (offset == 0) 0 else text.lastIndexOf('\n', offset - 1) + 1
            if (start != cachedStart) {
                val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
                if (end - start > MAX_LEX_LINE) return null
                var line = caretLine
                if (start != caretLineStart) {
                    // Не строка курсора (бывает редко): номер строки — подсчётом переводов.
                    line = 0
                    var i = text.indexOf('\n')
                    while (i in 0 until start) {
                        line++
                        i = text.indexOf('\n', i + 1)
                    }
                }
                val state = if (line == 0) 0 else runCatching { analyzer.getState(line - 1)?.state }.getOrNull() ?: 0
                val out = ArrayList<Token>()
                runCatching { lexer.tokenize(text.substring(start, end), state, out) }
                cachedStart = start
                cachedTokens = out
            }
            val rel = offset - start
            var found: Tok? = null
            for (t in cachedTokens) if (t.start <= rel && rel < t.end && t.type != Tok.TEXT) found = t.type
            return found
        }
    }

    // ---------- Отступы и Enter ----------

    override fun getIndentAdvance(content: ContentReference, line: Int, column: Int): Int {
        val re = language.indentAfter ?: return 0
        val before = content.getLine(line).substring(0, column.coerceAtMost(content.getColumnCount(line)))
        return if (re.containsMatchIn(before)) tabSize else 0
    }

    private val handlers: Array<NewlineHandler> = arrayOf(BetweenBrackets(), DedentAfter())

    override fun getNewlineHandlers(): Array<NewlineHandler> = handlers

    private fun leadingWhitespace(text: Content, line: Int): String {
        val l = text.getLine(line)
        var i = 0
        while (i < l.length && (l[i] == ' ' || l[i] == '\t')) i++
        return l.subSequence(0, i).toString()
    }

    /** Enter между «{» и «}»: закрывающая скобка уходит на строку ниже, курсор — на строку с отступом. */
    private inner class BetweenBrackets : NewlineHandler {
        override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?): Boolean {
            val line = text.getLine(position.line)
            val c = position.column
            if (c <= 0 || c >= line.length) return false
            return when (line[c - 1]) {
                '{' -> line[c] == '}'
                '[' -> line[c] == ']'
                '(' -> line[c] == ')'
                else -> false
            }
        }

        override fun handleNewline(text: Content, position: CharPosition, style: Styles?, tabSize: Int): NewlineHandleResult {
            val indent = leadingWhitespace(text, position.line)
            return NewlineHandleResult("\n" + indent + indentUnit + "\n" + indent, indent.length + 1)
        }
    }

    /** После return/pass/break (Python и т. п.) следующая строка — на уровень левее. */
    private inner class DedentAfter : NewlineHandler {
        override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?): Boolean {
            val re = language.dedentAfter ?: return false
            val line = text.getLine(position.line)
            if (position.column != line.length) return false
            return re.containsMatchIn(line.toString())
        }

        override fun handleNewline(text: Content, position: CharPosition, style: Styles?, tabSize: Int): NewlineHandleResult {
            val indent = leadingWhitespace(text, position.line)
            val unit = indentUnit
            val less = when {
                indent.endsWith(unit) -> indent.dropLast(unit.length)
                indent.endsWith("\t") -> indent.dropLast(1)
                else -> indent.dropLast(minOf(indent.length, tabSize))
            }
            return NewlineHandleResult("\n" + less, 0)
        }
    }

    // ---------- Пары символов ----------

    private val pairs: SymbolPairMatch = SymbolPairMatch().apply {
        val p = language.autoPairs
        var i = 0
        while (i + 1 < p.length) {
            putPair(p[i], SymbolPairMatch.SymbolPair(p[i].toString(), p[i + 1].toString()))
            i += 2
        }
    }

    override fun getSymbolPairs(): SymbolPairMatch = pairs

    // Анализатор останавливает сам Sora (setEditorLanguage / release) — здесь только свой поток индекса.
    override fun destroy() {
        indexer.shutdownNow()
    }

    companion object {
        /** Строки длиннее не раскрашиваются (минифицированный файл в одну строку). */
        const val MAX_LEX_LINE = 100_000
    }
}

/** Инкрементальный разбор: состояние — Int конца строки, как у лексеров десктопа. */
/**
 * Подсветка публикуется одним пакетом после разбора, а не кусками во время него: каждая порция
 * заставляет Sora перемерять ширину строк в главном потоке, и на большом файле прокрутка и зум
 * сразу после открытия (или после начала блочного комментария, меняющего состояние всех строк ниже) вязли.
 */
private class WexAnalyzer(val lexer: LineLexer?, private val tabSize: Int, private val lang: WexLanguage) : AsyncIncrementalAnalyzeManager<Int, Any>() {
    private val scratch = ArrayList<Token>()

    @Volatile var passes = 0
    @Volatile var lastLines = -1

    init {
        setUpdateStylesDuringAnalysis(false)
    }

    override fun getInitialState(): Int = 0
    override fun stateEquals(state: Int?, another: Int?): Boolean = state == another
    override fun onAddState(state: Int?) = Unit
    override fun onAbandonState(state: Int?) = Unit

    override fun tokenizeLine(line: CharSequence, state: Int, lineIndex: Int): LineTokenizeResult<Int, Any> {
        val spans = ArrayList<Span>(8)
        val lexer = lexer
        if (lexer == null || line.length > WexLanguage.MAX_LEX_LINE || (line.isEmpty() && state == 0)) {
            spans += SpanFactory.obtain(0, SyntaxPalette.normalStyle)
            return LineTokenizeResult(0, null, spans)
        }
        scratch.clear()
        val end = try {
            lexer.tokenize(line.toString(), state, scratch)
        } catch (_: Exception) {
            scratch.clear(); 0
        } catch (_: StackOverflowError) {
            scratch.clear(); 0
        }
        buildSpans(scratch, line.length, spans)
        return LineTokenizeResult(end, null, spans)
    }

    override fun generateSpansForLine(tokens: LineTokenizeResult<Int, Any>): List<Span> =
        tokens.spans ?: listOf(SpanFactory.obtain(0, SyntaxPalette.normalStyle))

    /**
     * Токены отсортированы по началу и могут перекрываться (экранирование внутри строки) — у пересечения
     * побеждает более поздний, как в десктопе. Спаны Sora — плоская последовательность «с колонки N стиль S».
     */
    private fun buildSpans(tokens: List<Token>, len: Int, out: MutableList<Span>) {
        var overlap = false
        var lastEnd = 0
        for (t in tokens) {
            if (t.start < lastEnd) { overlap = true; break }
            lastEnd = maxOf(lastEnd, t.end)
        }
        if (!overlap) {
            var pos = 0
            for (t in tokens) {
                val a = t.start.coerceIn(0, len)
                val b = t.end.coerceIn(a, len)
                if (b <= a || t.type == Tok.TEXT || t.type == Tok.PUNCTUATION) continue
                if (a > pos || out.isEmpty()) add(out, pos, SyntaxPalette.normalStyle)
                add(out, a, SyntaxPalette.style(t.type))
                pos = b
            }
            if (out.isEmpty()) add(out, 0, SyntaxPalette.normalStyle)
            if (pos < len) add(out, pos, SyntaxPalette.normalStyle)
            return
        }
        // Перекрытия: раскраска «поздний поверх раннего» в массив, затем — сжатие в спаны.
        val paint = IntArray(len) { -1 }
        for (t in tokens) {
            val a = t.start.coerceIn(0, len)
            val b = t.end.coerceIn(a, len)
            if (t.type == Tok.TEXT) continue
            val v = if (t.type == Tok.PUNCTUATION) -1 else t.type.ordinal
            for (k in a until b) paint[k] = v
        }
        var prev = -2
        for (k in 0 until len) {
            if (paint[k] != prev) {
                prev = paint[k]
                add(out, k, if (prev < 0) SyntaxPalette.normalStyle else SyntaxPalette.style(Tok.entries[prev]))
            }
        }
        if (out.isEmpty()) add(out, 0, SyntaxPalette.normalStyle)
    }

    private fun add(out: MutableList<Span>, column: Int, style: Long) {
        val last = out.lastOrNull()
        if (last != null) {
            if (last.column == column) { last.style = style; return }
            if (last.style == style) return
        }
        out += SpanFactory.obtain(column, style)
    }

    /**
     * Блоки по отступам: строка открывает блок, если следующая непустая строка глубже. Конец — строка,
     * вернувшаяся на тот же уровень с закрывающей скобкой/словом (её включаем), иначе — последняя глубокая.
     */
    // Sora зовёт computeBlocks после разбора всех строк — это и есть конец прохода.
    // Свёртка считается здесь же, не на UI и не отдельным проходом по каждой букве.
    override fun computeBlocks(text: Content, delegate: CodeBlockAnalyzeDelegate): List<CodeBlock> =
        try {
            val blocks = indentBlocks(text, delegate)
            if (!delegate.isCancelled) publishFolds(text, blocks, delegate)
            blocks
        } finally {
            lastLines = text.lineCount
            passes++
        }

    private fun publishFolds(text: Content, blocks: List<CodeBlock>, delegate: CodeBlockAnalyzeDelegate) {
        val state = lang.folds ?: return
        val regions = if (!lang.foldEnabled || text.lineCount > 80_000) emptyList()
        else FoldComputer.compute(text, lang.language, blocks) { delegate.isCancelled }
        state.publish(regions)
        val styles = runCatching { getManagedStyles() }.getOrNull() ?: return
        styles.lineStyles?.map { it.line }?.distinct()?.forEach { styles.eraseLineStyle(it, LineSideIcon::class.java) }
        val seen = HashSet<Int>()
        for (r in regions) {
            if (r.endLine <= r.startLine || !seen.add(r.startLine)) continue
            styles.addLineStyle(LineSideIcon(r.startLine, FoldMarkerDrawable(state, r.startLine)))
        }
    }

    private fun indentBlocks(text: Content, delegate: CodeBlockAnalyzeDelegate): List<CodeBlock> {
        val n = text.lineCount
        if (n > 300_000) return emptyList()
        val width = IntArray(n)
        val column = IntArray(n)
        for (i in 0 until n) {
            if (i and 1023 == 0 && delegate.isCancelled) return emptyList()
            val l = text.getLine(i)
            var w = 0
            var c = 0
            while (c < l.length && (l[c] == ' ' || l[c] == '\t')) {
                w += if (l[c] == '\t') tabSize - w % tabSize else 1
                c++
            }
            if (c == l.length) { width[i] = -1; continue }
            width[i] = w
            column[i] = c
        }
        val blocks = ArrayList<CodeBlock>()
        val stack = IntArray(256)
        var top = 0
        var lastNonBlank = -1
        fun close(start: Int, endLine: Int, includeEnd: Boolean) {
            if (endLine <= start) return
            blocks += CodeBlock().apply {
                startLine = start
                startColumn = column[start]
                this.endLine = endLine
                endColumn = if (includeEnd) column[endLine] else column[start]
                toBottomOfEndLine = !includeEnd
            }
        }
        for (i in 0 until n) {
            if (i and 1023 == 0 && delegate.isCancelled) return emptyList()
            val w = width[i]
            if (w < 0) continue
            while (top > 0 && width[stack[top - 1]] >= w) {
                val start = stack[--top]
                val closing = width[start] == w && isCloser(text, i, column[i])
                close(start, if (closing) i else lastNonBlank, closing)
            }
            var next = i + 1
            while (next < n && width[next] < 0) next++
            if (next < n && width[next] > w && top < stack.size) stack[top++] = i
            lastNonBlank = i
        }
        while (top > 0) close(stack[--top], lastNonBlank, false)
        return blocks
    }

    private fun isCloser(text: Content, line: Int, col: Int): Boolean {
        val l = text.getLine(line)
        if (col >= l.length) return false
        val c = l[col]
        if (c == '}' || c == ']' || c == ')' || c == '<' && col + 1 < l.length && l[col + 1] == '/') return true
        val word = StringBuilder()
        var k = col
        while (k < l.length && l[k].isLetter() && word.length < 8) word.append(l[k++])
        return word.toString() in CLOSE_WORDS
    }

    companion object {
        private val CLOSE_WORDS = setOf("end", "fi", "done", "esac", "endif", "endfor", "endwhile", "until", "else", "elif", "elsif", "except", "finally", "catch")
    }
}
