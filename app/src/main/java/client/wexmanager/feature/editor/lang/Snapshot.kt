package client.wexmanager.feature.editor.lang

/**
 * Неизменяемый снимок разметки текста для структуры файла (как HighlightSnapshot в WEXdesktop):
 * строки и токены каждой строки — тройки (начало, конец, Tok.ordinal) относительно начала строки.
 */
class HighlightSnapshot(
    val text: String,
    val language: Language,
    private val starts: IntArray,
    val lineCount: Int,
    private val tokens: Array<IntArray?>,
    private val endStates: IntArray? = null,
    val lexed: Boolean = true,
) : SyntaxInfo {
    fun lineStart(line: Int): Int = starts[line]
    fun endState(line: Int): Int = endStates?.getOrNull(line) ?: 0
    fun lineEnd(line: Int): Int = if (line + 1 < lineCount) starts[line + 1] - 1 else text.length

    fun lineOf(offset: Int): Int {
        var lo = 0
        var hi = lineCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun lineTokens(line: Int): IntArray = tokens[line] ?: EMPTY

    override fun tokenAt(offset: Int): Tok? {
        if (offset < 0 || offset >= text.length || lineCount == 0) return null
        val line = lineOf(offset)
        val rel = offset - starts[line]
        val t = tokens[line] ?: return null
        var k = t.size - 3
        while (k >= 0) {
            if (t[k] <= rel && rel < t[k + 1]) return TOKS[t[k + 2]]
            k -= 3
        }
        return null
    }

    companion object {
        val EMPTY = IntArray(0)
        val TOKS: Array<Tok> = Tok.values()
        /** Больше — только строки без токенов (структура по регуляркам всё равно работает). */
        private const val MAX_LEXED_CHARS = 4_000_000

        /** Прогон лексера языка по всему тексту (в фоне; прерывание потока — отмена). */
        fun build(text: String, language: Language): HighlightSnapshot {
            val starts = ArrayList<Int>(1024).apply { add(0) }
            for (i in text.indices) if (text[i] == '\n') starts += i + 1
            val n = starts.size
            val lexed = text.length <= MAX_LEXED_CHARS
            val tokens = arrayOfNulls<IntArray>(n)
            val ends = IntArray(n)
            if (lexed) {
                val out = ArrayList<Token>(32)
                var state = 0
                for (line in 0 until n) {
                    stopIfInterrupted(line)
                    val s = starts[line]
                    val e = if (line + 1 < n) starts[line + 1] - 1 else text.length
                    out.clear()
                    state = language.lexer.tokenize(text.substring(s, e), state, out)
                    ends[line] = state
                    if (out.isNotEmpty()) tokens[line] = IntArray(out.size * 3).also { a ->
                        out.forEachIndexed { k, t -> a[3 * k] = t.start; a[3 * k + 1] = t.end; a[3 * k + 2] = t.type.ordinal }
                    }
                }
            }
            return HighlightSnapshot(text, language, starts.toIntArray(), n, tokens, ends, lexed)
        }
    }
}
