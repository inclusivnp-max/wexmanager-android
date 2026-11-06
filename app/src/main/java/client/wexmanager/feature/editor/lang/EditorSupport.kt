package client.wexmanager.feature.editor.lang

/** Что лежит в позиции текста — для подсказок (в строке, в комментарии…). */
interface SyntaxInfo {
    /** Тип токена, покрывающего символ [offset] (null — обычный код/пробел). */
    fun tokenAt(offset: Int): Tok?
}

/**
 * Фоновые разборы (индекс слов, подсказки) проверяют прерывание потока раз в 1024 шага:
 * устаревший расчёт — пользователь уже напечатал следующую букву — бросается, а не доделывается.
 */
internal fun stopIfInterrupted(step: Int) {
    if (step and 1023 == 0 && Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
}

/** Текстовые помощники движка подсказок (из десктопного TextOps). */
object TextOps {
    private val VOID_HTML = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr", "!doctype")

    fun lineStartOf(text: String, offset: Int): Int =
        if (offset <= 0) 0 else text.lastIndexOf('\n', (offset - 1).coerceAtMost(text.length - 1)) + 1

    private val anyTagRe = Regex("<(/?)([A-Za-z_][\\w.:-]*)[^<>]*?(/?)>|<!--.*?-->|<!\\[CDATA\\[.*?]]>|<\\?.*?\\?>", RegexOption.DOT_MATCHES_ALL)

    /** Самый внутренний незакрытый тег перед [pos]: имя и позиция "<". */
    fun innermostOpenTag(text: String, pos: Int): Pair<String, Int>? {
        val from = maxOf(0, pos - 400_000)
        val stack = ArrayList<Pair<String, Int>>()
        for (m in anyTagRe.findAll(text.substring(from, pos))) {
            val name = m.groups[2]?.value ?: continue
            val closing = m.groupValues[1] == "/"
            val selfClosing = m.groupValues[3] == "/"
            when {
                selfClosing -> Unit
                closing -> {
                    val idx = stack.indexOfLast { it.first == name }
                    if (idx >= 0) while (stack.size > idx) stack.removeAt(stack.size - 1)
                }
                name.lowercase() in VOID_HTML -> Unit
                else -> stack.add(name to (from + m.range.first))
            }
        }
        return stack.lastOrNull()
    }
}
