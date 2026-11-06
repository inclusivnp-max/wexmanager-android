package client.wexmanager.feature.editor.golang

import java.io.File

enum class GoRunScope { FILE, PACKAGE }
enum class GoAction(val label: String) {
    RUN("Запуск"), TEST("Тесты"), BUILD("Сборка"), VET("Проверка vet"),
    FMT("Форматировать"), TIDY("Синхронизация импортов"),
    GET("Добавление зависимости"), INIT("Создание модуля"), CONSOLE("Консоль"),
}

data class GoProject(val file: File, val moduleRoot: File?) {
    val directory: File get() = file.parentFile!!
    val root: File get() = moduleRoot ?: directory
    val defaultScope: GoRunScope get() = if (moduleRoot == null) GoRunScope.FILE else GoRunScope.PACKAGE

    companion object {
        fun detect(path: String): GoProject {
            val file = File(path).canonicalFile
            require(file.extension.equals("go", ignoreCase = true)) { "Выберите файл .go" }
            var dir = file.parentFile
            var module: File? = null
            while (dir != null) {
                if (File(dir, "go.mod").isFile) { module = dir; break }
                dir = dir.parentFile
            }
            return GoProject(file, module)
        }
    }
}

data class GoRunConfig(
    val scope: GoRunScope = GoRunScope.FILE,
    val arguments: String = "",
    val workingDirectory: String = "",
    val syncImports: Boolean = true,
    val buildTags: String = "",
    val testFlags: String = "",
    val envVars: String = "",
    val buildOutput: String = "",
)

/** Одна переменная окружения KEY=VALUE; значение понимает кавычки и escape как аргументы. */
internal fun parseGoEnv(text: String): List<Pair<String, String>> {
    if (text.isBlank()) return emptyList()
    val result = ArrayList<Pair<String, String>>()
    for (raw in text.split('\n')) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith('#')) continue
        val eq = line.indexOf('=')
        require(eq > 0) { "Переменная окружения — в виде KEY=VALUE: $line" }
        val key = line.substring(0, eq).trim()
        require(key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Некорректное имя переменной: $key" }
        var value = line.substring(eq + 1).trim()
        if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\'')))) {
            value = value.substring(1, value.length - 1)
        }
        require('\u0000' !in key && '\u0000' !in value) { "Нулевой байт в окружении" }
        result += key to value
    }
    require(result.size <= 64) { "Слишком много переменных окружения (максимум 64)" }
    val names = result.map { it.first }
    require(names.size == names.toSet().size) { "Повторяющаяся переменная окружения" }
    return result
}

/** Теги сборки: пробелы/запятые как разделители, только безопасные символы. */
internal fun parseGoTags(text: String): List<String> {
    val tags = text.split(Regex("[\\s,]+")).map { it.trim() }.filter { it.isNotEmpty() }
    tags.forEach { require(it.matches(Regex("[A-Za-z0-9_.+-]+"))) { "Некорректный тег сборки: $it" } }
    require(tags.size <= 32) { "Слишком много тегов сборки" }
    return tags.distinct()
}

/** Кликабельная ошибка компилятора: file.go:12:4: message. */
data class GoError(val file: String, val line: Int, val column: Int, val message: String)

private val GO_ERROR_RE = Regex("^(.*?\\.go):(\\d+)(?::(\\d+))?:\\s*(.+)$")

internal fun parseGoErrors(output: String): List<GoError> {
    val result = ArrayList<GoError>()
    for (raw in output.split('\n')) {
        val line = raw.trim().removePrefix("./").removePrefix("/")
        val match = GO_ERROR_RE.find(line) ?: continue
        val row = match.groupValues[2].toIntOrNull() ?: continue
        val col = match.groupValues[3].toIntOrNull() ?: 1
        val message = match.groupValues[4].trim()
        if (message.isEmpty() || message.startsWith('#')) continue
        if (result.size >= 200) break
        result += GoError(match.groupValues[1], row.coerceAtLeast(1), col.coerceAtLeast(1), message.take(500))
    }
    // De-duplicate identical diagnostics (e.g. repeated package headers), keep order.
    return result.distinct()
}

/** Относительный путь ошибки -> абсолютный внутри корня проекта. */
internal fun GoError.resolveIn(root: File, currentFile: File): File? {
    val candidates = listOf(
        File(root, file),
        File(currentFile.parentFile ?: root, file),
        File(file),
    )
    return candidates.firstOrNull { it.isFile }
        ?: candidates.firstOrNull { runCatching { it.canonicalFile }.isSuccess }
}

/** Arguments are parsed, never evaluated by a shell. */
internal fun parseGoArguments(text: String): List<String> {
    val result = ArrayList<String>()
    val word = StringBuilder()
    var quote: Char? = null
    var escaped = false
    var started = false
    for (ch in text) {
        when {
            escaped -> { word.append(ch); escaped = false; started = true }
            ch == '\\' && quote != '\'' -> { escaped = true; started = true }
            quote != null -> if (ch == quote) quote = null else word.append(ch)
            ch == '\'' || ch == '"' -> { quote = ch; started = true }
            ch.isWhitespace() -> if (started) { result.add(word.toString()); word.clear(); started = false }
            else -> { word.append(ch); started = true }
        }
    }
    require(!escaped) { "После \\ ожидается символ" }
    require(quote == null) { "Закройте кавычки в аргументах" }
    if (started) result.add(word.toString())
    require(result.none { '\u0000' in it }) { "Нулевой байт в аргументах" }
    return result
}

internal fun projectBinaryName(project: GoProject): String {
    val base = project.root.name.ifBlank { "app" }.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifEmpty { "app" }
    return base.take(64)
}

internal fun shellQuote(text: String): String {
    require('\u0000' !in text) { "Нулевой байт в команде" }
    return "'" + text.replace("'", "'\"'\"'") + "'"
}

/** Кавычки только там, где они нужны: читаемый предпросмотр команд. */
internal fun shellQuoteIfNeeded(text: String): String =
    if (text.isNotEmpty() && text.all { it.isLetterOrDigit() || it in "-_./=:@+,%" }) text else shellQuote(text)

data class GoRunPlan(val project: GoProject, val action: GoAction, val commands: String, val preview: String) {
    companion object {
        fun create(project: GoProject, config: GoRunConfig, action: GoAction, value: String = ""): GoRunPlan {
            val cwd = if (config.workingDirectory.isBlank()) project.directory else File(config.workingDirectory)
            require(cwd.isAbsolute && cwd.isDirectory) { "Рабочая папка должна существовать: $cwd" }
            val target = if (config.scope == GoRunScope.FILE) project.file.absolutePath else {
                val relative = project.directory.relativeTo(project.root).invariantSeparatorsPath
                if (relative.isEmpty()) "." else "./$relative"
            }
            val lines = mutableListOf("set -e", "cd ${shellQuote(project.root.absolutePath)}")
            if (action != GoAction.CONSOLE) lines += "command -v go >/dev/null || { printf '%s\\n' 'Установите Go в Termux: pkg install golang'; exit 127; }"
            val env = if (action == GoAction.CONSOLE || action == GoAction.FMT) emptyList() else parseGoEnv(config.envVars)
            for ((key, value) in env) lines += "export ${key}=${shellQuote(value)}"
            val tags = if (action == GoAction.CONSOLE || action == GoAction.FMT || action == GoAction.TIDY) emptyList() else parseGoTags(config.buildTags)
            val tagsFlag = if (tags.isEmpty()) "" else " -tags " + shellQuote(tags.joinToString(" "))
            if ((action == GoAction.RUN || action == GoAction.TEST || action == GoAction.BUILD || action == GoAction.VET) && config.syncImports && project.moduleRoot != null) {
                lines += "printf '%s\\n' '$ go mod tidy'"
                lines += "go mod tidy"
            }
            var preview: String
            when (action) {
                GoAction.RUN -> {
                    require(!project.file.name.endsWith("_test.go")) { "Для _test.go выберите «Тесты»" }
                    val args = parseGoArguments(config.arguments).joinToString(" ", transform = ::shellQuoteIfNeeded)
                    lines += "printf '%s\\n' '$ go build'"
                    lines += "go build$tagsFlag -o \"\$WEX_GO_BIN\" ${shellQuote(target)}"
                    lines += "cd ${shellQuote(cwd.absolutePath)}"
                    lines += "printf '%s\\n' '── Программа ──'"
                    lines += "exec \"\$WEX_GO_BIN\" $args"
                    preview = "go run$tagsFlag ${shellQuoteIfNeeded(target)} $args".trimEnd()
                }
                GoAction.BUILD -> {
                    require(!project.file.name.endsWith("_test.go")) { "Для _test.go выберите «Тесты»" }
                    val name = config.buildOutput.trim().ifEmpty { projectBinaryName(project) }
                    require(!name.contains('/') && !name.contains('\\') && name.isNotBlank()) { "Имя бинарника — без пути" }
                    lines += "printf '%s\\n' '$ go build'"
                    lines += "go build$tagsFlag -o ${shellQuote(File(project.root, name).absolutePath)} ${shellQuote(target)}"
                    preview = "go build$tagsFlag -o $name ${shellQuote(target)}".trimEnd()
                }
                GoAction.VET -> {
                    val scope = if (config.scope == GoRunScope.FILE && project.moduleRoot != null) "./..." else target
                    lines += "go vet$tagsFlag ${shellQuote(scope)}"
                    preview = "go vet$tagsFlag ${shellQuoteIfNeeded(scope)}".trimEnd()
                }
                GoAction.FMT -> {
                    // gofmt правит файл на месте; мост копирует результат обратно, редактор перечитает с диска.
                    lines += "gofmt -w ${shellQuote(project.file.absolutePath)}"
                    preview = "gofmt -w ${project.file.name}"
                }
                GoAction.TEST -> {
                    // В скрипт — с надёжным экранированием; в предпросмотр — читаемо.
                    val scriptFlags = parseGoArguments(config.testFlags).joinToString(" ", transform = ::shellQuote)
                    val readFlags = parseGoArguments(config.testFlags).joinToString(" ", transform = ::shellQuoteIfNeeded)
                    lines += ("go test$tagsFlag $scriptFlags ./...").replace(Regex("\\s+"), " ").trimEnd()
                    preview = ("go test$tagsFlag $readFlags ./...").replace(Regex("\\s+"), " ").trimEnd()
                }
                GoAction.TIDY -> {
                    require(project.moduleRoot != null) { "Сначала создайте go.mod через «Модуль»" }
                    lines += "go mod tidy"; preview = "go mod tidy"
                }
                GoAction.GET, GoAction.INIT -> {
                    val arg = value.trim()
                    require(arg.isNotEmpty() && !arg.startsWith('-') && arg.none { it.isWhitespace() || it == '\u0000' }) {
                        "Введите ${if (action == GoAction.GET) "путь зависимости, например github.com/pkg/errors@v0.9.1" else "имя модуля, например example.com/app"}"
                    }
                    if (action == GoAction.GET) require(project.moduleRoot != null) { "Сначала создайте go.mod через «Модуль»" }
                    if (action == GoAction.INIT) require(project.moduleRoot == null) { "Модуль уже существует" }
                    preview = (if (action == GoAction.GET) "go get " else "go mod init ") + shellQuote(arg)
                    lines += preview
                }
                GoAction.CONSOLE -> {
                    lines += "cd ${shellQuote(cwd.absolutePath)}"
                    lines += "printf '%s\\n' 'Консоль Go: вводите команды; exit — выход. Для TUI откройте Termux.'"
                    lines += "exec bash --noprofile --norc -v"
                    preview = "bash · ${cwd.absolutePath}"
                }
            }
            return GoRunPlan(project, action, lines.joinToString("\n"), preview)
        }
    }
}

internal fun rememberGoCommand(history: List<String>, command: String): List<String> =
    if (command.isBlank()) history else (listOf(command) + history.filter { it != command }).take(40)
