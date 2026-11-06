package client.wexmanager.feature.editor.golang

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

internal enum class GoSyntaxPhase { DISABLED, PENDING, CHECKING, READY, ERROR }
internal data class GoProblem(val start: Int, val end: Int, val line: Int, val column: Int, val message: String)
internal data class GoSyntaxResult(val id: Long, val problems: List<GoProblem>)
internal data class GoSyntaxState(
    val enabled: Boolean = true,
    val path: String = "",
    val request: Long = 0,
    val phase: GoSyntaxPhase = GoSyntaxPhase.PENDING,
    val problems: List<GoProblem> = emptyList(),
    val error: String? = null,
)

/** A result from a previous buffer or edit must never repaint the active document. */
internal class GoSyntaxRevision {
    private var id = 0L
    private var path = ""
    fun advance(path: String): Long { this.path = path; return ++id }
    fun accepts(id: Long, path: String) = this.id == id && this.path == path
}

internal fun syntaxRequest(id: Long, path: String, text: String, op: String = ""): String = buildJsonObject {
    put("id", id); put("path", path); put("source", text)
    if (op.isNotEmpty()) put("op", op)
}.toString()

/** Символ файла: функция, метод, тип, поле, переменная, импорт. */
internal data class GoSymbol(
    val name: String, val kind: String, val detail: String,
    val line: Int, val column: Int, val start: Int, val end: Int, val receiver: String,
)

internal fun parseSyntaxSymbols(json: String, expectedId: Long, sourceLength: Int): List<GoSymbol> {
    val root = Json.parseToJsonElement(json).jsonObject
    val id = root["id"]?.jsonPrimitive?.longOrNull
    require(id == expectedId) { "Устаревший ответ проверки синтаксиса" }
    root["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { error(it) }
    val items = root["symbols"] as? JsonArray ?: error("Неверный ответ проверки синтаксиса")
    return items.take(2000).mapNotNull { item ->
        val obj = item.jsonObject
        fun str(name: String) = obj[name]?.jsonPrimitive?.contentOrNull.orEmpty()
        fun num(name: String) = obj[name]?.jsonPrimitive?.intOrNull ?: -1
        val start = num("start"); val end = num("end"); val line = num("line"); val column = num("column")
        if (start < 0 || end < start || end > sourceLength || line < 1 || column < 1) return@mapNotNull null
        val name = str("name").take(120)
        if (name.isEmpty() || name == "_") return@mapNotNull null
        GoSymbol(name, str("kind").ifEmpty { "var" }, str("detail").take(160),
            line, column, start, end, str("receiver").take(48))
    }.distinctBy { it.start to it.name }
}

internal fun parseSyntaxFormat(json: String, expectedId: Long): String {
    val root = Json.parseToJsonElement(json).jsonObject
    val id = root["id"]?.jsonPrimitive?.longOrNull
    require(id == expectedId) { "Устаревший ответ форматирования" }
    root["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { error(it) }
    return root["formatted"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        ?: error("Пустой ответ форматирования")
}

internal fun parseSyntaxResult(json: String, expectedId: Long, sourceLength: Int): GoSyntaxResult {
    val root = Json.parseToJsonElement(json).jsonObject
    val id = root["id"]?.jsonPrimitive?.longOrNull
    require(id == expectedId) { "Устаревший ответ проверки синтаксиса" }
    root["error"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { error(it) }
    val items = root["problems"] as? JsonArray ?: error("Неверный ответ проверки синтаксиса")
    val problems = items.take(100).map { item ->
        val obj = item.jsonObject
        fun number(name: String) = obj[name]?.jsonPrimitive?.intOrNull ?: error("Неверная позиция ошибки")
        val start = number("start"); val end = number("end"); val line = number("line"); val column = number("column")
        require(start >= 0 && end >= start && end <= sourceLength && line >= 1 && column >= 1) { "Ошибка вне текущего документа" }
        GoProblem(start, end, line, column, obj["message"]?.jsonPrimitive?.contentOrNull ?: error("Нет описания ошибки"))
    }.distinct()
    return GoSyntaxResult(expectedId, problems)
}

internal fun nextGoProblem(problems: List<GoProblem>, cursor: Int, backwards: Boolean): GoProblem? {
    val sorted = problems.sortedBy { it.start }
    return if (backwards) sorted.lastOrNull { it.start < cursor } ?: sorted.lastOrNull()
    else sorted.firstOrNull { it.start > cursor } ?: sorted.firstOrNull()
}
