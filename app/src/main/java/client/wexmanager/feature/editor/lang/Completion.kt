package client.wexmanager.feature.editor.lang

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

class CompletionItem(
    val label: String,
    val kind: ItemKind,
    val detail: String = "",
    /** Что вставить; `$0` — позиция курсора, `${1:x}` — поля шаблона. */
    val insertText: String = label,
    val isSnippet: Boolean = false,
    val boost: Int = 0,
    /** После вставки снова открыть подсказки (каталог в пути, "@string/"). */
    val retrigger: Boolean = false
) {
    var score: Int = 0
    var matched: IntArray? = null
}

class CompletionResult(
    val items: List<CompletionItem>,
    /** Начало заменяемого префикса. */
    val replaceStart: Int,
    /** Позиция курсора на момент запроса. */
    val caret: Int,
    /** Конец слова под курсором (Tab заменяет до него). */
    val wordEnd: Int,
    val prefix: String
)

class CompletionRequest(
    val text: String,
    val caret: Int,
    val language: Language,
    val snapshot: SyntaxInfo?,
    val index: WordIndex?,
    val explicit: Boolean,
    val baseDir: Path?,
    /** Символы Go-проекта (экспортированные объявления соседних пакетов) для подсказок. */
    val workspace: List<VocabWord> = emptyList(),
    /** Локальные пакеты проекта: package name -> объявления. */
    val workspacePackages: Map<String, List<VocabWord>> = emptyMap(),
    /** Зависимости из уже кэшированного go.mod. */
    val moduleDependencies: List<String> = emptyList(),
)

/** Нечёткое сопоставление: префикс > начала слов (CamelHumps) > подстрока > подпоследовательность. */
object FuzzyMatcher {
    const val NO_MATCH = Int.MIN_VALUE

    private fun isWordStart(s: String, i: Int): Boolean {
        if (i == 0) return true
        val p = s[i - 1]
        val c = s[i]
        if (!p.isLetterOrDigit()) return c.isLetterOrDigit() || !p.isLetterOrDigit() && c != p
        if (c.isUpperCase() && p.isLowerCase()) return true
        if (c.isDigit() && !p.isDigit()) return true
        return c.isLetter() && p.isDigit()
    }

    fun score(pattern: String, cand: String, positions: IntArray? = null): Int {
        if (pattern.isEmpty()) return 0
        if (pattern.length > cand.length) return NO_MATCH
        if (cand.startsWith(pattern)) {
            positions?.let { for (k in pattern.indices) it[k] = k }
            return 100_000 - cand.length
        }
        if (cand.regionMatches(0, pattern, 0, pattern.length, ignoreCase = true)) {
            positions?.let { for (k in pattern.indices) it[k] = k }
            return 90_000 - cand.length
        }
        val humps = IntArray(pattern.length)
        val humpScore = matchHumps(pattern, cand, humps)
        val sub = cand.indexOf(pattern, ignoreCase = true)
        val subScore = if (sub >= 0) 50_000 - sub * 20 - cand.length + (if (isWordStart(cand, sub)) 15_000 else 0) else NO_MATCH
        val seq = IntArray(pattern.length)
        val seqScore = if (humpScore == NO_MATCH && subScore == NO_MATCH) matchSubsequence(pattern, cand, seq) else NO_MATCH
        val best = maxOf(humpScore, subScore, seqScore)
        if (best == NO_MATCH) return NO_MATCH
        if (positions != null) {
            when (best) {
                humpScore -> humps.copyInto(positions)
                subScore -> for (k in pattern.indices) positions[k] = sub + k
                else -> seq.copyInto(positions)
            }
        }
        return best
    }

    // Каждый символ шаблона — продолжение текущего куска или начало следующего слова
    private fun matchHumps(p: String, c: String, pos: IntArray): Int {
        var j = 0
        var prev = -2
        var starts = 0
        var firstAtStart = false
        for (i in p.indices) {
            val ch = p[i]
            var found = -1
            if (prev >= 0 && prev + 1 < c.length && c[prev + 1].equals(ch, ignoreCase = true) &&
                !(ch.isUpperCase() && !isWordStart(c, prev + 1))
            ) {
                found = prev + 1
            } else {
                var k = j
                while (k < c.length) {
                    if (c[k].equals(ch, ignoreCase = true) && isWordStart(c, k)) {
                        found = k; break
                    }
                    k++
                }
                if (found >= 0) {
                    starts++
                    if (i == 0) firstAtStart = true
                }
            }
            if (found < 0) return NO_MATCH
            pos[i] = found
            prev = found
            j = found + 1
        }
        val lead = if (firstAtStart) pos[0] else 0
        return 70_000 - lead * 30 - starts * 50 - c.length + (if (pos[0] == 0) 5_000 else 0)
    }

    private fun matchSubsequence(p: String, c: String, pos: IntArray): Int {
        var j = 0
        var gaps = 0
        var last = -1
        for (i in p.indices) {
            var k = j
            while (k < c.length && !c[k].equals(p[i], ignoreCase = true)) k++
            if (k >= c.length) return NO_MATCH
            if (last >= 0 && k != last + 1) gaps++
            pos[i] = k
            last = k
            j = k + 1
        }
        return 20_000 - gaps * 400 - pos[0] * 10 - c.length
    }
}

/** Слова документа для подсказок (строится в фоне). */
class WordIndex(val version: Long, val words: Map<String, Int>, val functions: Set<String>, val extra: List<VocabWord>)

object WordIndexer {
    private const val MAX_SCAN = 3_000_000
    private const val MAX_WORDS = 60_000

    private val smaliType = Regex("\\[*L[\\w/$\\-]+;")
    private val smaliMethodRef = Regex("->([\\w$<>\\-]+\\([^)\\s]*\\)[\\w/$;\\[]+)")
    private val smaliFieldRef = Regex("->([\\w$\\-]+:[\\w/$;\\[]+)")
    private val smaliMethodDecl = Regex("^\\s*\\.method\\s+(?:[\\w-]+\\s+)*([\\w$<>\\-]+\\([^)]*\\)\\S+)", RegexOption.MULTILINE)
    private val smaliFieldDecl = Regex("^\\s*\\.field\\s+(?:[\\w-]+\\s+)*([\\w$\\-]+:\\S+)", RegexOption.MULTILINE)
    private val smaliLabel = Regex("(?<=^|\\s):([\\w]+)", RegexOption.MULTILINE)
    private val xmlTag = Regex("<([A-Za-z_][\\w.:-]*)")
    private val xmlAttr = Regex("\\s([A-Za-z_][\\w.:-]*)\\s*=")
    private val xmlRef = Regex("[@?](\\+?)([\\w.]+:)?([\\w]+)/([\\w.]+)")
    private val xmlName = Regex("<(string|color|dimen|bool|integer|style|array|string-array|plurals|attr|declare-styleable|item)\\b[^>]*\\bname=\"([^\"]+)\"")
    private val cssClass = Regex("(?<![\\w-])([.#][A-Za-z_][\\w-]*)")
    private val cssVar = Regex("(--[\\w-]+)")
    private val shellAssign = Regex("(?:^|[\\s;(])(?:local\\s+|export\\s+|readonly\\s+|declare\\s+(?:-\\w+\\s+)*)?([A-Za-z_]\\w*)=", RegexOption.MULTILINE)
    private val shellUse = Regex("\\$\\{?([A-Za-z_]\\w*)")
    private val shellFor = Regex("\\b(?:for|read(?:\\s+-\\w+)*)\\s+([A-Za-z_]\\w*)")
    private val shellFunc = Regex("^\\s*(?:function\\s+)?([\\w:.-]+)\\s*\\(\\s*\\)", RegexOption.MULTILINE)

    fun build(text: String, lang: Language, version: Long): WordIndex {
        val src = if (text.length > MAX_SCAN) text.substring(0, MAX_SCAN) else text
        val words = HashMap<String, Int>()
        val functions = HashSet<String>()
        val n = src.length
        var i = 0
        var steps = 0
        while (i < n && words.size < MAX_WORDS) {
            stopIfInterrupted(++steps)
            val c = src[i]
            if (c.isLetter() || c == '_' || (c == '$' && lang.isWordChar('$'))) {
                var j = i + 1
                while (j < n && lang.isWordChar(src[j])) j++
                val len = j - i
                if (len in 3..80) {
                    val w = src.substring(i, j).trimEnd('.', ':', '-')
                    if (w.length >= 3) {
                        words.merge(w, 1, Int::plus)
                        var k = j
                        while (k < n && src[k] == ' ') k++
                        if (k < n && src[k] == '(') functions.add(w)
                    }
                }
                i = j
            } else i++
        }
        val extra = LinkedHashMap<String, VocabWord>()
        fun put(text: String, kind: ItemKind, detail: String = "в файле") {
            if (extra.size < 20_000 && text.isNotBlank()) extra.putIfAbsent(text, VocabWord(text, kind, detail))
        }
        when {
            lang.id == "smali" -> {
                smaliMethodDecl.findAll(src).forEach { put(it.groupValues[1], ItemKind.FUNCTION, "метод класса") }
                smaliFieldDecl.findAll(src).forEach { put(it.groupValues[1], ItemKind.PROPERTY, "поле класса") }
                smaliMethodRef.findAll(src).forEach { put(it.groupValues[1], ItemKind.FUNCTION) }
                smaliFieldRef.findAll(src).forEach { put(it.groupValues[1], ItemKind.PROPERTY) }
                smaliType.findAll(src).forEach { put(it.value, ItemKind.TYPE) }
                smaliLabel.findAll(src).forEach { put(it.groupValues[1], ItemKind.LABEL) }
            }
            lang.markup -> {
                xmlTag.findAll(src).forEach { put(it.groupValues[1], ItemKind.TAG) }
                xmlAttr.findAll(src).forEach { put(it.groupValues[1], ItemKind.ATTRIBUTE) }
                xmlRef.findAll(src).forEach { m ->
                    val ns = m.groupValues[2]
                    val type = m.groupValues[3]
                    val ref = "@$ns$type/${m.groupValues[4]}"
                    put(ref, ItemKind.VALUE)
                    if (m.groupValues[1] == "+" && type == "id") put("@id/${m.groupValues[4]}", ItemKind.VALUE)
                }
                xmlName.findAll(src).forEach { m ->
                    val type = when (m.groupValues[1]) {
                        "string-array" -> "array"
                        "item" -> null
                        "declare-styleable" -> "styleable"
                        else -> m.groupValues[1]
                    }
                    if (type != null) put("@$type/${m.groupValues[2]}", ItemKind.VALUE, "ресурс")
                }
            }
            lang.id == "css" || lang.id == "scss" -> {
                cssClass.findAll(src).forEach { put(it.groupValues[1], ItemKind.TYPE) }
                cssVar.findAll(src).forEach { put(it.groupValues[1], ItemKind.VARIABLE) }
            }
            lang.id in SHELL_LIKE -> {
                shellAssign.findAll(src).forEach { put("$" + it.groupValues[1], ItemKind.VARIABLE) }
                shellUse.findAll(src).forEach { put("$" + it.groupValues[1], ItemKind.VARIABLE) }
                shellFor.findAll(src).forEach { put("$" + it.groupValues[1], ItemKind.VARIABLE) }
                shellFunc.findAll(src).forEach { put(it.groupValues[1], ItemKind.FUNCTION, "функция") }
            }
            lang.id == "go" -> {
                // Функции, типы, переменные, параметры и импорты — приоритетнее слов из текста.
                goExtras(src).forEach { extra.putIfAbsent(it.text, it) }
            }
        }
        return WordIndex(version, words, functions, extra.values.toList())
    }

    val SHELL_LIKE = setOf("shell", "fish", "makefile", "dockerfile", "dotenv", "powershell")

    private val goFunc = Regex("\\bfunc\\s+(?:\\(\\s*\\w+\\s+[\\w.*\\[\\]]+\\s*\\)\\s*)?([\\w]+)\\s*\\(", RegexOption.MULTILINE)
    private val goType = Regex("\\btype\\s+([\\w]+)\\b", RegexOption.MULTILINE)
    private val goVar = Regex("\\b(?:var|const)\\s+(?:\\(\\s*)?([\\w]+)", RegexOption.MULTILINE)
    private val goShort = Regex("(?:^|[\\s;{])\\s*([\\w]+)\\s*:=", RegexOption.MULTILINE)
    private val goParam = Regex("func\\s*(?:\\([^)]*\\)\\s*)?\\w+\\s*\\(([^)]*)\\)")
    private val goImport = Regex("import\\s+(?:([\\w.]+)\\s+)?\"([^\"]+)\"")
    private val goStruct = Regex("\\btype\\s+([\\w]+)\\s+struct\\s*\\{([^}]*)\\}")
    private val goField = Regex("^\\s*([\\w]+)\\s+([\\w.\\[\\]*]+)")
    private val goMethod = Regex("\\bfunc\\s+\\(\\s*\\w+\\s+\\*?([\\w]+)\\s*\\)\\s*([\\w]+)\\s*\\(")

    /** Go-символы файла: без мусора других языков, только своё — ограниченный индекс документа. */
    fun goExtras(src: String): List<VocabWord> {
        val out = LinkedHashMap<String, VocabWord>()
        fun put(text: String, kind: ItemKind, detail: String) {
            if (out.size < 20_000 && text.length in 2..80 && text != "_") out.putIfAbsent(text, VocabWord(text, kind, detail))
        }
        fun putKey(key: String, word: VocabWord) { if (out.size < 20_000) out.putIfAbsent(key, word) }
        /** Методы и поля важнее общего объявления: owner нужен для подсказок после точки. */
        fun putOwned(text: String, kind: ItemKind, detail: String) {
            if (text.length in 2..80 && text != "_" && out.size < 20_000) out[text] = VocabWord(text, kind, detail)
        }
        goFunc.findAll(src).forEach { put(it.groupValues[1], ItemKind.FUNCTION, "функция") }
        goType.findAll(src).forEach { put(it.groupValues[1], ItemKind.TYPE, "тип") }
        goVar.findAll(src).forEach { put(it.groupValues[1], ItemKind.VARIABLE, "переменная") }
        // Присваивания-объявления: u := User{} — тип переменной помогает goMembers показывать члены u.
        Regex("\\b([\\w]+)\\s*:=\\s*\\*?([\\w]+)\\s*[{\\(]").findAll(src).forEach { m ->
            val (name, type) = m.groupValues[1] to m.groupValues[2]
            if (name != "_" && name != type && name.firstOrNull()?.isLowerCase() == true) {
                putKey("$name.type", VocabWord(name, ItemKind.VARIABLE, "переменная · $type"))
            }
        }
        goShort.findAll(src).forEach { m ->
            val name = m.groupValues[1].trim()
            if (name != "_") put(name, ItemKind.VARIABLE, "переменная")
        }
        for (m in goParam.findAll(src)) {
            for (p in m.groupValues[1].split(',')) {
                val name = p.trim().split(Regex("\\s+")).firstOrNull().orEmpty()
                if (name.matches(Regex("[a-zA-Z_][\\w]*"))) put(name, ItemKind.VARIABLE, "параметр")
            }
        }
        goImport.findAll(src).forEach { m ->
            val raw = m.groupValues[1]
            val path = m.groupValues[2]
            val name = raw.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/')
            if (name != "_" && name != ".") put(name, ItemKind.MODULE, "импорт · $path")
        }
        for (m in goStruct.findAll(src)) {
            val owner = m.groupValues[1]
            for (line in m.groupValues[2].lineSequence()) {
                val field = goField.find(line) ?: continue
                val name = field.groupValues[1]
                if (name.firstOrNull()?.isUpperCase() == true) putOwned(name, ItemKind.PROPERTY, "поле · $owner")
            }
        }
        goMethod.findAll(src).forEach { m ->
            putOwned(m.groupValues[2], ItemKind.FUNCTION, "метод · ${m.groupValues[1]}")
        }
        return out.values.toList()
    }
}

/** Команды из $PATH — сканируются один раз в фоне. */
object PathCommands {
    private val ref = AtomicReference<List<String>?>(null)
    @Volatile
    private var started = false

    fun get(): List<String> {
        ref.get()?.let { return it }
        if (!started) {
            started = true
            Thread({
                val set = java.util.TreeSet<String>()
                val path = System.getenv("PATH") ?: ""
                for (dir in path.split(File.pathSeparatorChar)) {
                    val files = runCatching { File(dir).listFiles() }.getOrNull() ?: continue
                    for (f in files) {
                        if (set.size > 20_000) break
                        if (f.canExecute() && !f.isDirectory) set.add(f.name)
                    }
                }
                ref.set(set.toList())
            }, "wex-path-scan").apply { isDaemon = true }.start()
        }
        return emptyList()
    }
}

/** Разбор шаблона: поля ${N:текст}, $N, $0, $$; отступы тела пересчитываются под файл. */
class SnippetExpansion(val text: String, val stops: List<List<IntRange>>, val finalOffset: Int)

object SnippetExpander {
    fun expand(body: String, lineIndent: String, indentUnit: String): SnippetExpansion {
        // Отступы тела написаны по 4 пробела — переводим в единицу отступа файла
        val normalized = body.split('\n').mapIndexed { idx, line ->
            var spaces = 0
            while (spaces < line.length && line[spaces] == ' ') spaces++
            val levels = spaces / 4
            val rest = line.substring(levels * 4)
            val prefix = if (idx == 0) "" else lineIndent
            prefix + indentUnit.repeat(levels) + rest.replace("\t", indentUnit)
        }.joinToString("\n")

        val defaults = HashMap<Int, String>()
        val placeholder = Regex("\\$\\{(\\d+)(?::((?:[^\\{\\}]|\\{[^\\{\\}]*\\})*))?\\}|\\$(\\d+)|\\$\\$")
        for (m in placeholder.findAll(normalized)) {
            val idx = (m.groups[1] ?: m.groups[3])?.value?.toIntOrNull() ?: continue
            val def = m.groups[2]?.value
            if (def != null && idx !in defaults) defaults[idx] = def.replace("$$", "$")
        }
        val out = StringBuilder()
        val groups = java.util.TreeMap<Int, MutableList<IntRange>>()
        var final = -1
        var last = 0
        for (m in placeholder.findAll(normalized)) {
            out.append(normalized, last, m.range.first)
            last = m.range.last + 1
            if (m.value == "$$") {
                out.append('$')
                continue
            }
            val idx = (m.groups[1] ?: m.groups[3])!!.value.toInt()
            val text = defaults[idx] ?: ""
            val start = out.length
            out.append(text)
            if (idx == 0) {
                if (final < 0) final = start
            } else {
                groups.getOrPut(idx) { ArrayList() }.add(start until start + text.length)
            }
        }
        out.append(normalized, last, normalized.length)
        return SnippetExpansion(out.toString(), groups.values.map { it.toList() }, if (final >= 0) final else out.length)
    }
}

object CompletionEngine {
    private const val MAX_ITEMS = 300

    /** Что значит «~/» в путях: на телефоне — общее хранилище. */
    @Volatile
    var homeDir: String = System.getProperty("user.home") ?: "/"

    fun complete(req: CompletionRequest): CompletionResult? {
        if (req.caret < 0 || req.caret > req.text.length) return null
        val lang = req.language
        pathCompletion(req)?.let { return it }
        return when {
            lang.id == "smali" -> smali(req)
            lang.markup -> markup(req)
            lang.id == "css" || lang.id == "scss" -> css(req) ?: generic(req)
            lang.id == "shell" || lang.id == "fish" -> shell(req) ?: generic(req)
            lang.id == "go" -> go(req) ?: generic(req)
            else -> generic(req)
        }
    }

    // ---------- Go ----------

    /** Члены пакетов собираются из кэша документа, не сканируют текст на каждое нажатие. */
    private fun goMembers(lang: Language, dot: Int, req: CompletionRequest): List<CompletionItem> {
        val text = req.text
        var q = dot
        while (q > 0 && (lang.isWordChar(text[q - 1]) || text[q - 1] == '.')) q--
        val qualifier = text.substring(q, dot).trimStart('.')
        if (qualifier.isEmpty()) return emptyList()
        val head = "$qualifier."
        val out = ArrayList<CompletionItem>()
        lang.vocabulary.words
            .asSequence()
            .filter { it.text.length > head.length && it.text.startsWith(head) }
            .forEach { out.add(CompletionItem(it.text.substring(head.length), it.kind, it.detail, boost = 60)) }
        val local = req.index?.extra.orEmpty()
        val imported = local.firstOrNull { it.kind == ItemKind.MODULE && it.text == qualifier }
        val packageName = imported?.detail?.substringAfterLast('/') ?: qualifier
        req.workspacePackages[packageName].orEmpty().forEach {
            out.add(CompletionItem(it.text, it.kind, it.detail, boost = 62))
        }
        // Members, methods and fields are extracted once by WordIndexer for this document version.
        // Квалификатор может быть типом (User.Greet) или переменной этого типа (u.Greet).
        fun ownerOf(detail: String): String? = when {
            detail.startsWith("поле · ") || detail.startsWith("метод · ") -> detail.substringAfterLast("·").trim()
            detail.startsWith("переменная · ") -> detail.substringAfterLast("·").trim()
            else -> null
        }
        val owners = buildSet {
            add(qualifier)
            add(packageName)
            local.forEach { symbol ->
                if (symbol.text == qualifier) {
                    val owner = ownerOf(symbol.detail)
                    if (owner != null && symbol.kind == ItemKind.VARIABLE) add(owner)
                }
            }
            if (qualifier.isNotEmpty()) {
                val candidate = qualifier.replaceFirstChar { it.uppercase() }
                if (local.any { it.kind == ItemKind.TYPE && it.text == candidate }) add(candidate)
            }
        }
        local.forEach { symbol ->
            val owner = ownerOf(symbol.detail)
            if (owner != null && owner in owners) {
                val kind = when {
                    symbol.detail.startsWith("поле") -> ItemKind.PROPERTY
                    symbol.kind == ItemKind.FUNCTION -> ItemKind.FUNCTION
                    else -> symbol.kind
                }
                out.add(CompletionItem(symbol.text, kind, symbol.detail, boost = 58))
            }
        }
        if (imported != null && out.isEmpty()) {
            out.add(CompletionItem(qualifier, ItemKind.MODULE, imported.detail, boost = 10))
        }
        return out
    }

    private fun go(req: CompletionRequest): CompletionResult? {
        val text = req.text
        val caret = req.caret
        val lang = req.language
        val start = prefixStart(text, caret, lang::isWordChar)
        val prefix = text.substring(start, caret)
        val tok = req.snapshot?.tokenAt(caret - 1)
        val inComment = tok == Tok.COMMENT || tok == Tok.DOC_COMMENT || tok == Tok.DOC_TAG
        val inString = tok == Tok.STRING || tok == Tok.ESCAPE
        val before = text.substring(0, caret)
        val lineStart = text.lastIndexOf('\n', caret - 1).let { if (it < 0) 0 else it + 1 }
        val line = text.substring(lineStart, caret)
        val importPrefix = line.trimStart().let { it == "import" || it.startsWith("import ") || it.startsWith("import\t") }
        val importString = importPrefix && (inString || line.count { it == '"' } % 2 == 1)
        // В import-кавычках сам префикс и перенос-на-подпакеты показываются даже без набранного текста.
        if (!req.explicit && !importString && (prefix.isEmpty() || inComment || inString)) return null
        val items = ArrayList<CompletionItem>()
        val afterDot = start > 0 && text[start - 1] == '.'
        if (!inComment && (!inString || importString)) {
            when {
                afterDot && !importString -> items += goMembers(lang, start - 1, req)
                importString -> {
                    // Поддерживаем импорт "fmt", import "fmt", import ("fmt"), в том числе точку в пути.
                    val packagePrefix = if (prefix.contains('/')) prefix.substringBeforeLast('/') else ""
                    val query = prefix.substringAfterLast('/')
                    // Stdlib и частые пакеты из словаря языка — всегда под рукой.
                    lang.vocabulary.words.asSequence()
                        .filter { it.kind == ItemKind.MODULE && it.text.startsWith(query) }
                        .forEach { items.add(CompletionItem(it.text, ItemKind.MODULE, it.detail.ifEmpty { "пакет" }, boost = 75)) }
                    req.workspacePackages.keys.forEach { pkg ->
                        if (pkg.startsWith(query)) items.add(CompletionItem(pkg, ItemKind.MODULE, "пакет проекта", boost = 80))
                    }
                    req.index?.extra.orEmpty().filter { it.kind == ItemKind.MODULE }.forEach { symbol ->
                        if (symbol.text.startsWith(query)) items.add(CompletionItem(symbol.text, ItemKind.MODULE, symbol.detail, boost = 70))
                    }
                    req.moduleDependencies.forEach { dep ->
                        if (dep.startsWith(prefix)) items.add(CompletionItem(dep, ItemKind.MODULE, "go.mod · библиотека", boost = 60))
                    }
                    if (packagePrefix.isNotEmpty()) {
                        req.moduleDependencies.filter { it.startsWith(packagePrefix) }.forEach { dep ->
                            val rest = dep.removePrefix(packagePrefix).removePrefix("/").substringBefore('/')
                            if (rest.isNotEmpty()) items.add(CompletionItem(rest, ItemKind.MODULE, "путь импорта", rest, retrigger = true, boost = 65))
                        }
                    }
                }
                else -> {
                    items += vocabItems(lang)
                    items += snippetItems(lang, prefix)
                    // Символы проекта (экспортированные объявления всех пакетов) — до слов файла.
                    req.workspace.forEach { w ->
                        items.add(CompletionItem(w.text, w.kind, w.detail.ifEmpty { w.kind.title }, boost = 45))
                    }
                    // Символы файла: функции, типы, переменные, импорты — выше общего словаря.
                    req.index?.extra?.forEach { w ->
                        val boost = when (w.kind) {
                            ItemKind.FUNCTION -> 70
                            ItemKind.TYPE -> 65
                            ItemKind.VARIABLE, ItemKind.CONSTANT -> 55
                            ItemKind.MODULE -> 50
                            else -> 40
                        }
                        items.add(CompletionItem(w.text, w.kind, w.detail.ifEmpty { w.kind.title }, boost = boost))
                    }
                    // Имена собственных функций и типов без словарного покрытия
                    for (m in Regex("\\bfunc\\s+\\w+\\s*\\(|\\btype\\s+\\w+\\s").findAll(before)) {
                        val name = m.value.substring(m.value.indexOfFirst { it.isLetter() })
                        items.add(CompletionItem(name, ItemKind.FUNCTION, "в файле", boost = 75))
                    }
                }
            }
        }
        items += docItems(req, prefix, caret)
        if (items.isEmpty()) return null
        val ranked = rank(prefix, items)
        if (ranked.isEmpty()) return null
        return CompletionResult(ranked, start, caret, wordEnd(text, caret, lang::isWordChar), prefix)
    }

    // ---------- общий случай ----------

    private fun prefixStart(text: String, caret: Int, isChar: (Char) -> Boolean): Int {
        var s = caret
        while (s > 0 && isChar(text[s - 1])) s--
        return s
    }

    private fun wordEnd(text: String, caret: Int, isChar: (Char) -> Boolean): Int {
        var e = caret
        while (e < text.length && isChar(text[e])) e++
        return e
    }

    private fun rank(prefix: String, items: Collection<CompletionItem>, caseInsensitiveLang: Boolean = false): List<CompletionItem> {
        val out = ArrayList<CompletionItem>()
        val seen = HashSet<String>()
        var steps = 0
        for (it in items) {
            // До десятков тысяч слов на каждую букву: устаревший расчёт (буква уже следующая) бросаем
            stopIfInterrupted(++steps)
            if (!seen.add(it.label + "\u0000" + it.kind)) continue
            if (prefix.isNotEmpty() && it.label == prefix && !it.isSnippet) continue
            val pos = IntArray(prefix.length)
            val s = FuzzyMatcher.score(prefix, it.label, pos)
            if (s == FuzzyMatcher.NO_MATCH) continue
            it.score = s + it.boost
            it.matched = pos
            out.add(it)
        }
        out.sortWith(compareByDescending<CompletionItem> { it.score }.thenBy { it.label.length }.thenBy { it.label })
        if (out.size > MAX_ITEMS) return out.subList(0, MAX_ITEMS)
        return out
    }

    private fun vocabItems(lang: Language, kinds: Set<ItemKind>? = null, boost: Int = 0): List<CompletionItem> =
        lang.vocabulary.words.filter { kinds == null || it.kind in kinds }.map {
            CompletionItem(it.text, it.kind, it.detail.ifEmpty { it.kind.title }, boost = boost + kindBoost(it.kind))
        }

    private fun qualifiedMembers(lang: Language, text: String, dot: Int): List<CompletionItem> {
        var q = dot
        while (q > 0 && (lang.isWordChar(text[q - 1]) || text[q - 1] == '.')) q--
        val qualifier = text.substring(q, dot).trimStart('.')
        if (qualifier.isEmpty()) return emptyList()
        val head = "$qualifier."
        return lang.vocabulary.words
            .filter { it.text.length > head.length && it.text.startsWith(head) }
            .map { CompletionItem(it.text.substring(head.length), it.kind, it.detail.ifEmpty { it.kind.title }, boost = kindBoost(it.kind)) }
    }

    private fun kindBoost(k: ItemKind) = when (k) {
        ItemKind.KEYWORD -> 40
        ItemKind.SNIPPET -> 20
        ItemKind.WORD -> 0
        else -> 30
    }

    private fun snippetItems(lang: Language, prefix: String): List<CompletionItem> =
        lang.vocabulary.snippets.map { snippetItem(it, prefix) }

    private fun snippetItem(s: Snippet, prefix: String) = CompletionItem(
        s.trigger, ItemKind.SNIPPET, s.description, s.body, isSnippet = true,
        boost = if (s.trigger == prefix) 200_000 else 10
    )

    private fun docItems(req: CompletionRequest, prefix: String, exclude: Int): List<CompletionItem> {
        val idx = req.index ?: return emptyList()
        val out = ArrayList<CompletionItem>()
        var steps = 0
        for ((w, count) in idx.words) {
            stopIfInterrupted(++steps)
            if (w == prefix) continue
            val kind = when {
                w in idx.functions -> ItemKind.FUNCTION
                else -> ItemKind.WORD
            }
            out.add(CompletionItem(w, kind, "в файле", boost = minOf(count, 30) - 5))
        }
        return out
    }

    private fun generic(req: CompletionRequest): CompletionResult? {
        val text = req.text
        val caret = req.caret
        val lang = req.language
        val start = prefixStart(text, caret, lang::isWordChar)
        val prefix = text.substring(start, caret)
        val tok = req.snapshot?.tokenAt(caret - 1)
        val inComment = tok == Tok.COMMENT || tok == Tok.DOC_COMMENT || tok == Tok.DOC_TAG
        val inString = tok == Tok.STRING || tok == Tok.ESCAPE
        if (!req.explicit) {
            if (prefix.isEmpty() || inComment || inString) return null
            if (prefix.first().isDigit()) return null
        }
        val items = ArrayList<CompletionItem>()
        val afterDot = start > 0 && text[start - 1] == '.'
        if (!inComment && !inString) {
            if (afterDot) {
                // «ta.» — члены пространства имён из словаря (ta.sma → sma), иначе — все функции
                items += qualifiedMembers(lang, text, start - 1).ifEmpty { vocabItems(lang, setOf(ItemKind.FUNCTION, ItemKind.PROPERTY)) }
            } else {
                items += vocabItems(lang)
                items += snippetItems(lang, prefix)
            }
        }
        items += docItems(req, prefix, caret)
        req.index?.extra?.forEach { items.add(CompletionItem(it.text, it.kind, it.detail, boost = 25)) }
        if (items.isEmpty()) return null
        var ranked = rank(prefix, items)
        if (lang.caseInsensitive && prefix.isNotEmpty() && prefix.any { it.isLetter() } && prefix.filter { it.isLetter() }.all { it.isUpperCase() }) {
            ranked = ranked.map { item ->
                if (item.kind == ItemKind.KEYWORD || item.kind == ItemKind.TYPE || item.kind == ItemKind.FUNCTION && lang.id == "sql") {
                    CompletionItem(item.label.uppercase(), item.kind, item.detail, item.insertText.uppercase(), boost = item.boost).also {
                        it.score = item.score; it.matched = item.matched
                    }
                } else item
            }
        }
        if (ranked.isEmpty()) return null
        return CompletionResult(ranked, start, caret, wordEnd(text, caret, lang::isWordChar), prefix)
    }

    // ---------- пути ----------

    private val pathChars: (Char) -> Boolean = { c -> !c.isWhitespace() && c !in "\"'`;|&<>(),=[]{}*" }

    private fun pathCompletion(req: CompletionRequest): CompletionResult? {
        val text = req.text
        val caret = req.caret
        val start = prefixStart(text, caret, pathChars)
        val token = text.substring(start, caret)
        if (!token.contains('/')) return null
        val tok = req.snapshot?.tokenAt(caret - 1)
        val shellLike = req.language.id in WordIndexer.SHELL_LIKE || req.language.id == "ini" || req.language.id == "kdl"
        val inString = tok == Tok.STRING || tok == Tok.ESCAPE
        val looksLikePath = token.startsWith("/") || token.startsWith("~/") || token.startsWith("./") || token.startsWith("../")
        if (!inString && !(shellLike && looksLikePath)) return null
        if (!looksLikePath && !inString) return null
        // Ресурсы Android и smali-типы — не пути
        if (token.startsWith("@") || token.startsWith("?") || (req.language.id == "smali")) return null
        if (Regex("^[a-z]+://").containsMatchIn(token)) return null
        val dirPart = token.substring(0, token.lastIndexOf('/') + 1)
        val namePart = token.substring(dirPart.length)
        val home = homeDir
        val dir: Path = try {
            when {
                dirPart.startsWith("~/") -> Path.of(home, dirPart.substring(2))
                dirPart.startsWith("/") -> Path.of(dirPart)
                req.baseDir != null -> req.baseDir.resolve(dirPart)
                else -> return null
            }
        } catch (_: Exception) {
            return null
        }
        if (!Files.isDirectory(dir)) return null
        val entries = try {
            Files.newDirectoryStream(dir).use { ds -> ds.take(3000).toList() }
        } catch (_: Exception) {
            return null
        }
        val items = entries.mapNotNull { p ->
            val name = p.fileName?.toString() ?: return@mapNotNull null
            if (name.startsWith(".") && !namePart.startsWith(".")) return@mapNotNull null
            val isDir = Files.isDirectory(p)
            CompletionItem(
                if (isDir) "$name/" else name,
                ItemKind.FILE,
                if (isDir) "папка" else "файл",
                boost = if (isDir) 50 else 0,
                retrigger = isDir
            )
        }
        val ranked = rank(namePart, items)
        if (ranked.isEmpty()) return null
        val replaceStart = start + dirPart.length
        return CompletionResult(ranked, replaceStart, caret, wordEnd(text, caret, pathChars), namePart)
    }

    // ---------- smali ----------

    private val smaliDirectiveLine = Regex("^\\s*(\\.[\\w-]*)$")
    private val smaliFirstWord = Regex("^\\s*([a-z][\\w/-]*)$")
    private val smaliMember = Regex("->([\\w$<>-]*)$")
    private val smaliTypeTail = Regex("(?:^|[\\s,{(:)])(\\[*L[\\w/$-]*;?)$")
    private val smaliLabelTail = Regex("(?:^|[\\s,{]):([\\w]*)$")
    private val smaliRegisterTail = Regex("(?:^|[\\s,{])([vp]\\d*)$")
    private val smaliPrimitiveTail = Regex("(?:[\\w$>]|^\\s*\\.\\w+\\s.*)(:|\\))(\\[*)([A-Z]?)$")
    private val SMALI_PRIMITIVES = listOf("Z" to "boolean", "B" to "byte", "S" to "short", "C" to "char", "I" to "int", "J" to "long", "F" to "float", "D" to "double")

    private fun smali(req: CompletionRequest): CompletionResult? {
        val text = req.text
        val caret = req.caret
        val ls = TextOps.lineStartOf(text, caret)
        val before = text.substring(ls, caret)
        val tok = req.snapshot?.tokenAt(caret - 1)
        if (tok == Tok.COMMENT || tok == Tok.STRING) return if (req.explicit) generic(req) else null
        val extra = req.index?.extra ?: emptyList()
        val lang = req.language
        val wordChars: (Char) -> Boolean = { c -> c.isLetterOrDigit() || c in "_$-/<>" }

        smaliDirectiveLine.find(before)?.let { m ->
            val prefix = m.groupValues[1]
            val items = vocabItems(lang, setOf(ItemKind.DIRECTIVE)).map {
                val hint = LanguageData.smaliDirectives.firstOrNull { d -> d.first == it.label }?.second.orEmpty()
                CompletionItem(it.label, ItemKind.DIRECTIVE, hint, if (hint.isEmpty() || it.label.startsWith(".end")) it.label else it.label + " ")
            }
            return result(prefix, items, caret - prefix.length, caret, text, wordChars)
        }
        smaliFirstWord.find(before)?.let { m ->
            val prefix = m.groupValues[1]
            if (!req.explicit && prefix.isEmpty()) return null
            val items = ArrayList<CompletionItem>()
            vocabItems(lang, setOf(ItemKind.OPCODE)).forEach {
                val popularity = POPULAR_OPCODES.indexOf(it.label).let { i -> if (i < 0) 0 else 60 - i }
                items.add(CompletionItem(it.label, ItemKind.OPCODE, it.detail, it.label + " ", boost = 30 + popularity))
            }
            items += snippetItems(lang, prefix)
            return result(prefix, items, caret - prefix.length, caret, text, wordChars)
        }
        smaliMember.find(before)?.let { m ->
            val prefix = m.groupValues[1]
            val items = ArrayList<CompletionItem>()
            extra.filter { it.kind == ItemKind.FUNCTION || it.kind == ItemKind.PROPERTY }
                .forEach { items.add(CompletionItem(it.text, it.kind, it.detail, boost = 40)) }
            vocabItems(lang, setOf(ItemKind.FUNCTION)).forEach { items.add(it) }
            return result(prefix, items, caret - prefix.length, caret, text) { c -> c.isLetterOrDigit() || c in "_$<>-()/;[:" }
        }
        smaliTypeTail.find(before)?.let { m ->
            val prefix = m.groupValues[1]
            if (!req.explicit && prefix.length < 2) return@let
            val arrays = prefix.takeWhile { it == '[' }
            val items = ArrayList<CompletionItem>()
            extra.filter { it.kind == ItemKind.TYPE }.forEach { items.add(CompletionItem(it.text, ItemKind.TYPE, it.detail, boost = 40)) }
            vocabItems(lang, setOf(ItemKind.TYPE)).forEach { if (it.label.startsWith("L")) items.add(it) }
            val mapped = if (arrays.isEmpty()) items else items.map {
                CompletionItem(arrays + it.label.trimStart('['), it.kind, it.detail, boost = it.boost)
            }
            return result(prefix, mapped, caret - prefix.length, caret, text) { c -> c.isLetterOrDigit() || c in "_$/;[-" }
        }
        // Тип после «имя:» (поле, .local, ->поле:) и после «)» (возвращаемый): примитивы и классы файла.
        smaliPrimitiveTail.find(before)?.let { m ->
            val arrays = m.groupValues[2]
            val prefix = m.groupValues[3]
            val items = ArrayList<CompletionItem>()
            val prims = if (m.groupValues[1] == ")") SMALI_PRIMITIVES + ("V" to "void") else SMALI_PRIMITIVES
            prims.forEach { (t, name) -> items.add(CompletionItem(arrays + t, ItemKind.TYPE, name, boost = 60)) }
            extra.filter { it.kind == ItemKind.TYPE }.forEach { items.add(CompletionItem(arrays + it.text.trimStart('['), ItemKind.TYPE, it.detail, boost = 40)) }
            for (common in listOf("Ljava/lang/String;", "Ljava/lang/Object;", "Landroid/content/Context;")) items.add(CompletionItem(arrays + common, ItemKind.TYPE, "класс", boost = 20))
            return result(arrays + prefix, items, caret - prefix.length - arrays.length, caret, text) { c -> c.isLetterOrDigit() || c in "_$/;[-" }
        }
        smaliLabelTail.find(before)?.let { m ->
            val prefix = m.groupValues[1]
            val items = extra.filter { it.kind == ItemKind.LABEL }.map { CompletionItem(it.text, ItemKind.LABEL, "метка") }
            if (items.isNotEmpty()) return result(prefix, items, caret - prefix.length, caret, text) { c -> c.isLetterOrDigit() || c == '_' }
        }
        smaliRegisterTail.find(before)?.let { m ->
            val prefix = m.groupValues[1]
            val items = registersAt(text, caret)
            if (items.isNotEmpty()) return result(prefix, items, caret - prefix.length, caret, text) { c -> c.isLetterOrDigit() }
        }
        return generic(req)
    }

    // Частые инструкции выше в списке при равном совпадении
    private val POPULAR_OPCODES = listOf(
        "invoke-virtual", "invoke-static", "invoke-direct", "const-string", "move-result-object", "return-void",
        "const/4", "iget-object", "iput-object", "sget-object", "new-instance", "if-eqz", "if-nez", "goto",
        "check-cast", "return-object", "move-result", "invoke-interface", "return", "iget-boolean", "iput-boolean",
        "sput-object", "const/16", "const", "aget-object", "array-length", "invoke-super", "move-object", "throw"
    )

    /** Регистры текущего метода: v0..v(locals-1) и p0..pN по сигнатуре. */
    private fun registersAt(text: String, caret: Int): List<CompletionItem> {
        val head = text.lastIndexOf(".method", caret)
        if (head < 0) return emptyList()
        val end = text.indexOf(".end method", head).let { if (it < 0) text.length else it }
        if (caret > end) return emptyList()
        val body = text.substring(head, minOf(end, head + 200_000))
        val sigLine = body.substringBefore('\n')
        val isStatic = Regex("\\bstatic\\b").containsMatchIn(sigLine)
        val params = Regex("\\(([^)]*)\\)").find(sigLine)?.groupValues?.get(1).orEmpty()
        var pCount = if (isStatic) 0 else 1
        var i = 0
        while (i < params.length) {
            when (params[i]) {
                'J', 'D' -> { pCount += 2; i++ }
                'L' -> { pCount++; i = params.indexOf(';', i).let { if (it < 0) params.length else it + 1 } }
                '[' -> {
                    while (i < params.length && params[i] == '[') i++
                    if (i < params.length && params[i] == 'L') i = params.indexOf(';', i).let { if (it < 0) params.length else it + 1 } else i++
                    pCount++
                }
                else -> { pCount++; i++ }
            }
        }
        val locals = Regex("\\.locals\\s+(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
        val registers = Regex("\\.registers\\s+(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()
        val vCount = (locals ?: registers?.minus(pCount) ?: 0).coerceIn(0, 64)
        val out = ArrayList<CompletionItem>()
        for (v in 0 until vCount) out.add(CompletionItem("v$v", ItemKind.VARIABLE, "локальный", boost = 100 - v))
        for (p in 0 until pCount.coerceAtMost(64)) {
            out.add(CompletionItem("p$p", ItemKind.VARIABLE, if (p == 0 && !isStatic) "this" else "параметр", boost = 50 - p))
        }
        return out
    }

    private fun result(
        prefix: String,
        items: List<CompletionItem>,
        start: Int,
        caret: Int,
        text: String,
        wordChars: (Char) -> Boolean
    ): CompletionResult? {
        val ranked = rank(prefix, items)
        if (ranked.isEmpty()) return null
        return CompletionResult(ranked, start, caret, wordEnd(text, caret, wordChars), prefix)
    }

    // ---------- XML / HTML ----------

    private val nameChars: (Char) -> Boolean = { c -> c.isLetterOrDigit() || c in "_.:-" }

    private fun markup(req: CompletionRequest): CompletionResult? {
        val text = req.text
        val caret = req.caret
        val lang = req.language
        val isHtml = lang.id == "html"
        val lt = text.lastIndexOf('<', caret - 1)
        val gt = text.lastIndexOf('>', caret - 1)
        val extra = req.index?.extra ?: emptyList()
        val tok = req.snapshot?.tokenAt(caret - 1)
        if (tok == Tok.COMMENT) return null
        if (lt < 0 || gt > lt) {
            // Текст между тегами: шаблоны
            val start = prefixStart(text, caret, lang::isWordChar)
            val prefix = text.substring(start, caret)
            if (!req.explicit && prefix.length < 2) return null
            val items = snippetItems(lang, prefix)
            return result(prefix, items, start, caret, text, lang::isWordChar)
        }
        val seg = text.substring(lt, caret)
        if (seg.startsWith("<!--") || seg.startsWith("<?") || seg.startsWith("<!")) return null
        // Имя тега
        Regex("^<(/?)([\\w.:-]*)$").find(seg)?.let { m ->
            val closing = m.groupValues[1] == "/"
            val prefix = m.groupValues[2]
            val items = ArrayList<CompletionItem>()
            if (closing) {
                TextOps.innermostOpenTag(text, lt)?.let { items.add(CompletionItem(it.first, ItemKind.TAG, "закрыть", it.first + ">", boost = 500_000)) }
            }
            val tags = if (isHtml) LanguageData.htmlTags else emptySet()
            tags.forEach { items.add(CompletionItem(it, ItemKind.TAG, "html")) }
            if (!isHtml) {
                val rootContext = contextKind(text, lt)
                lang.vocabulary.words.filter { it.kind == ItemKind.TAG }.forEach {
                    val b = if (it.detail == rootContext) 60 else 0
                    items.add(CompletionItem(it.text, ItemKind.TAG, it.detail, boost = b))
                }
            }
            extra.filter { it.kind == ItemKind.TAG }.forEach { items.add(CompletionItem(it.text, ItemKind.TAG, it.detail, boost = 40)) }
            return result(prefix, items, caret - prefix.length, caret, text, nameChars)
        }
        val tagName = Regex("^</?([\\w.:-]+)").find(seg)?.groupValues?.get(1) ?: return null
        // Внутри значения атрибута?
        var quote: Char? = null
        var valueStart = -1
        var i = 0
        while (i < seg.length) {
            val c = seg[i]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
                valueStart = i + 1
            }
            i++
        }
        if (quote != null) {
            val nameMatch = Regex("([\\w.:-]+)\\s*=\\s*[\"']$").find(seg.substring(0, valueStart))
            val attr = nameMatch?.groupValues?.get(1) ?: return null
            val local = attr.substringAfter(':')
            val valueSoFar = seg.substring(valueStart)
            // Флаги через | — подсказываем последний
            val pipe = valueSoFar.lastIndexOf('|')
            val prefix = if (pipe >= 0) valueSoFar.substring(pipe + 1) else valueSoFar
            val items = ArrayList<CompletionItem>()
            LanguageData.attributeValues(local, tagName).forEach { items.add(CompletionItem(it, ItemKind.VALUE, local, boost = 60)) }
            if (prefix.startsWith("@") || prefix.startsWith("?") || req.explicit) {
                LanguageData.resourcePrefixes.forEach {
                    items.add(CompletionItem(it, ItemKind.VALUE, "ресурс", retrigger = it.endsWith("/")))
                }
                extra.filter { it.kind == ItemKind.VALUE }.forEach { items.add(CompletionItem(it.text, ItemKind.VALUE, it.detail, boost = 50)) }
                if (prefix.startsWith("?")) LanguageData.themeAttrs.forEach { items.add(CompletionItem("?attr/$it", ItemKind.VALUE, "атрибут темы")) }
            }
            if (local.startsWith("is") || local in BOOLEAN_ATTRS) {
                items.add(CompletionItem("true", ItemKind.CONSTANT, boost = 70))
                items.add(CompletionItem("false", ItemKind.CONSTANT, boost = 70))
            }
            if (!req.explicit && prefix.isEmpty() && items.none { it.boost >= 60 }) return null
            val start = caret - prefix.length
            return result(prefix, items, start, caret, text) { c -> c != '"' && c != '\'' && c != '|' && !c.isWhitespace() }
        }
        // Имя атрибута
        val start = prefixStart(text, caret, nameChars)
        if (start <= lt) return null
        val prefix = text.substring(start, caret)
        if (!req.explicit && prefix.isEmpty()) return null
        val present = Regex("([\\w.:-]+)\\s*=").findAll(seg).map { it.groupValues[1] }.toSet()
        val items = ArrayList<CompletionItem>()
        val attrs = if (isHtml) lang.vocabulary.words.filter { it.kind == ItemKind.ATTRIBUTE }
        else lang.vocabulary.words.filter { it.kind == ItemKind.ATTRIBUTE }
        for (a in attrs) {
            if (a.text in present) continue
            val insert = if (a.text.contains('=')) a.text else a.text + "=\"$0\""
            items.add(CompletionItem(a.text, ItemKind.ATTRIBUTE, a.detail, insert, isSnippet = true, retrigger = true))
        }
        extra.filter { it.kind == ItemKind.ATTRIBUTE && it.text !in present }.forEach {
            items.add(CompletionItem(it.text, ItemKind.ATTRIBUTE, it.detail, it.text + "=\"$0\"", isSnippet = true, boost = 40, retrigger = true))
        }
        return result(prefix, items, start, caret, text, nameChars)
    }

    private val BOOLEAN_ATTRS = setOf(
        "exported", "enabled", "clickable", "focusable", "singleLine", "allowBackup", "debuggable", "largeHeap",
        "hardwareAccelerated", "supportsRtl", "usesCleartextTraffic", "requestLegacyExternalStorage", "extractNativeLibs",
        "grantUriPermissions", "required", "directBootAware", "autoVerify", "excludeFromRecents", "noHistory",
        "translatable", "textAllCaps", "adjustViewBounds", "fitsSystemWindows", "checked", "clipToPadding",
        "fillViewport", "resizeableActivity", "stopWithTask", "isolatedProcess", "textIsSelectable", "hasFragileUserData",
        "enableOnBackInvokedCallback", "defaultNavHost", "nullable", "formatted", "checkable"
    )

    // Какой набор тегов уместен: манифест, разметка или ресурсы
    private fun contextKind(text: String, pos: Int): String {
        val head = text.substring(0, minOf(pos, 4000))
        return when {
            head.contains("<manifest") -> "manifest"
            head.contains("<resources") -> "res"
            head.contains("<selector") || head.contains("<shape") || head.contains("<vector") || head.contains("<menu") -> "res"
            else -> "layout"
        }
    }

    // ---------- CSS ----------

    private fun css(req: CompletionRequest): CompletionResult? {
        val text = req.text
        val caret = req.caret
        val lang = req.language
        val tok = req.snapshot?.tokenAt(caret - 1)
        if (tok == Tok.COMMENT || tok == Tok.STRING) return null
        val chars: (Char) -> Boolean = { c -> c.isLetterOrDigit() || c in "_-@" }
        val start = prefixStart(text, caret, chars)
        val prefix = text.substring(start, caret)
        if (!req.explicit && prefix.isEmpty()) return null
        // Глубина скобок и позиция относительно ':' в текущем объявлении
        var depth = 0
        val scanFrom = maxOf(0, caret - 100_000)
        for (k in scanFrom until start) {
            when (text[k]) {
                '{' -> depth++
                '}' -> depth = maxOf(0, depth - 1)
            }
        }
        val declStart = maxOf(text.lastIndexOf('{', start - 1), text.lastIndexOf(';', start - 1), text.lastIndexOf('}', start - 1))
        val decl = text.substring(declStart + 1, start)
        val items = ArrayList<CompletionItem>()
        when {
            depth > 0 && decl.contains(':') -> {
                vocabItems(lang, setOf(ItemKind.VALUE, ItemKind.FUNCTION)).forEach { items.add(it) }
                req.index?.extra?.filter { it.kind == ItemKind.VARIABLE }?.forEach {
                    items.add(CompletionItem("var(${it.text})", ItemKind.VARIABLE, "переменная", boost = 40))
                }
            }
            depth > 0 && !prefix.startsWith("@") -> {
                vocabItems(lang, setOf(ItemKind.PROPERTY)).forEach {
                    items.add(CompletionItem(it.label, ItemKind.PROPERTY, "свойство", it.label + ": $0;", isSnippet = true))
                }
                items += snippetItems(lang, prefix)
            }
            else -> {
                vocabItems(lang, setOf(ItemKind.DIRECTIVE)).forEach { items.add(it) }
                LanguageData.htmlTags.forEach { items.add(CompletionItem(it, ItemKind.TAG)) }
                req.index?.extra?.filter { it.kind == ItemKind.TYPE }?.forEach { items.add(CompletionItem(it.text, ItemKind.TYPE, it.detail, boost = 40)) }
            }
        }
        return result(prefix, items, start, caret, text, chars)
    }

    // ---------- shell ----------

    private val commandPosition = Regex("(?:^|[|;&(`]|\\$\\(|&&|\\|\\||\\bthen|\\bdo|\\belse|\\bsudo|\\bexec|\\btime|\\bwatch|\\bxargs|\\bnohup|\\benv)\\s*([\\w.+:-]*)$")

    private fun shell(req: CompletionRequest): CompletionResult? {
        val text = req.text
        val caret = req.caret
        val ls = TextOps.lineStartOf(text, caret)
        val before = text.substring(ls, caret)
        val tok = req.snapshot?.tokenAt(caret - 1)
        if (tok == Tok.COMMENT) return null
        val extra = req.index?.extra ?: emptyList()
        // Переменные
        Regex("\\$\\{?([A-Za-z_]\\w*)?$").find(before)?.let { m ->
            val name = m.groupValues[1]
            val braced = m.value.startsWith("\${")
            val prefix = name
            val items = ArrayList<CompletionItem>()
            extra.filter { it.kind == ItemKind.VARIABLE }.forEach {
                val n = it.text.removePrefix("$")
                items.add(CompletionItem(n, ItemKind.VARIABLE, "в файле", if (braced) "$n}" else n, boost = 50))
            }
            System.getenv().keys.forEach { items.add(CompletionItem(it, ItemKind.VARIABLE, "окружение", if (braced) "$it}" else it)) }
            for (s in listOf("HOME", "PWD", "USER", "PATH", "SHELL", "RANDOM", "LINENO", "BASH_SOURCE", "FUNCNAME", "IFS", "OLDPWD", "XDG_CONFIG_HOME", "XDG_DATA_HOME", "XDG_CACHE_HOME", "XDG_RUNTIME_DIR")) {
                items.add(CompletionItem(s, ItemKind.VARIABLE, "переменная", if (braced) "$s}" else s))
            }
            if (!req.explicit && prefix.isEmpty() && items.isEmpty()) return null
            return result(prefix, items, caret - prefix.length, caret, text) { c -> c.isLetterOrDigit() || c == '_' }
        }
        if (tok == Tok.STRING && !req.explicit) return null
        commandPosition.find(before)?.let { m ->
            val prefix = m.groupValues[1]
            if (!req.explicit && prefix.length < 2) return null
            val items = ArrayList<CompletionItem>()
            vocabItems(req.language).forEach { items.add(it) }
            extra.filter { it.kind == ItemKind.FUNCTION }.forEach { items.add(CompletionItem(it.text, ItemKind.FUNCTION, it.detail, boost = 50)) }
            PathCommands.get().forEach { items.add(CompletionItem(it, ItemKind.COMMAND, "команда", boost = -10)) }
            items += snippetItems(req.language, prefix)
            return result(prefix, items, caret - prefix.length, caret, text) { c -> c.isLetterOrDigit() || c in "_.+:-" }
        }
        return null
    }
}
