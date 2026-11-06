package client.wexmanager.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import client.wexmanager.appContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Base64
import java.util.Locale

/** Преобразования текста редактора: применяются к выделению, а без него — ко всему файлу. */
enum class TextTool(val title: String, val icon: ImageVector, val group: String) {
    Upper("ВЕРХНИЙ РЕГИСТР", Icons.Rounded.Title, "Регистр"),
    Lower("нижний регистр", Icons.Rounded.TextFields, "Регистр"),
    Capitalize("Каждое Слово С Заглавной", Icons.Rounded.FormatSize, "Регистр"),
    SortAsc("Сортировать строки А→Я", Icons.AutoMirrored.Rounded.Sort, "Строки"),
    SortDesc("Сортировать строки Я→А", Icons.Rounded.SwapVert, "Строки"),
    Unique("Удалить повторяющиеся строки", Icons.Rounded.FilterList, "Строки"),
    Reverse("Обратный порядок строк", Icons.Rounded.SwapVert, "Строки"),
    RemoveEmpty("Удалить пустые строки", Icons.Rounded.UnfoldLess, "Строки"),
    TrimTrailing("Убрать пробелы в конце", Icons.Rounded.VerticalAlignBottom, "Строки"),
    JsonFormat("Форматировать JSON", Icons.Rounded.DataObject, "JSON"),
    JsonMinify("Сжать JSON в одну строку", Icons.Rounded.Compress, "JSON"),
    XmlFormat("Форматировать XML", Icons.Rounded.Code, "XML"),
    Base64Encode("Base64: закодировать", Icons.Rounded.Key, "Кодирование"),
    Base64Decode("Base64: раскодировать", Icons.Rounded.LockOpen, "Кодирование"),
    UrlEncode("URL: закодировать", Icons.Rounded.Link, "Кодирование"),
    UrlDecode("URL: раскодировать", Icons.Rounded.LinkOff, "Кодирование"),
    UnicodeEscape("Символы → \\uXXXX", Icons.Rounded.Translate, "Кодирование"),
    UnicodeUnescape("\\uXXXX → символы", Icons.Rounded.Translate, "Кодирование"),
    Validate("Проверить синтаксис JSON / XML", Icons.Rounded.FactCheck, "Проверка"),
}

/** Результат проверки: [line]/[column] — с единицы, если известны. */
data class Validation(val ok: Boolean, val message: String, val line: Int = -1, val column: Int = -1)

object TextTools {
    /**
     * Проверка синтаксиса: JSON (kotlinx.serialization) или XML (XmlPullParser) — по первому значимому символу.
     * [xmlParser] — фабрика парсера (на устройстве android.util.Xml), чтобы проверка тестировалась на JVM.
     */
    fun validate(text: String, xmlParser: () -> org.xmlpull.v1.XmlPullParser): Validation {
        val first = text.firstOrNull { !it.isWhitespace() } ?: return Validation(false, "Файл пуст")
        return when (first) {
            '{', '[' -> try {
                Json.parseToJsonElement(text)
                Validation(true, "JSON корректен")
            } catch (e: Exception) {
                val offset = Regex("offset (\\d+)").find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                val (line, col) = if (offset != null) lineColumn(text, offset.coerceAtMost(text.length)) else (-1 to -1)
                val reason = e.message.orEmpty().lineSequence().firstOrNull().orEmpty()
                    .substringAfter(": ", "").ifBlank { "ошибка разбора" }
                Validation(false, if (line > 0) "Ошибка JSON в строке $line: $reason" else "Ошибка JSON: $reason", line, col)
            }
            '<' -> try {
                val p = xmlParser()
                p.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                p.setInput(java.io.StringReader(text))
                while (p.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) Unit
                Validation(true, "XML корректен")
            } catch (e: org.xmlpull.v1.XmlPullParserException) {
                Validation(false, "Ошибка XML в строке ${e.lineNumber}: ${e.message?.substringBefore(" (position")}", e.lineNumber, e.columnNumber)
            }
            else -> Validation(false, "Проверка доступна для JSON и XML")
        }
    }

    /**
     * Отступы по вложенности. Текст элемента без вложенных тегов остаётся в одной строке с тегами;
     * длинный тег с несколькими атрибутами разносится по атрибуту на строку (как в Android Studio).
     * Комментарии, CDATA, `<?…?>` и `<!DOCTYPE>` сохраняются как есть.
     */
    fun formatXml(text: String, indent: String = "    "): String {
        val tokens = ArrayList<String>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '<') {
                val end = text.indexOf('<', i).let { if (it < 0) text.length else it }
                tokens += text.substring(i, end)
                i = end
                continue
            }
            val end = when {
                text.startsWith("<!--", i) -> text.indexOf("-->", i).let { if (it < 0) throw IllegalArgumentException("Незакрытый комментарий") else it + 3 }
                text.startsWith("<![CDATA[", i) -> text.indexOf("]]>", i).let { if (it < 0) throw IllegalArgumentException("Незакрытый CDATA") else it + 3 }
                text.startsWith("<?", i) -> text.indexOf("?>", i).let { if (it < 0) throw IllegalArgumentException("Незакрытая инструкция <?") else it + 2 }
                else -> {
                    // Конец тега — первая «>» вне кавычек.
                    var j = i + 1
                    var quote = 0.toChar()
                    while (j < text.length) {
                        val c = text[j]
                        if (quote != 0.toChar()) { if (c == quote) quote = 0.toChar() } else if (c == '"' || c == '\'') quote = c else if (c == '>') break
                        j++
                    }
                    if (j >= text.length) throw IllegalArgumentException("Незакрытый тег")
                    j + 1
                }
            }
            tokens += text.substring(i, end)
            i = end
        }
        val out = StringBuilder()
        var depth = 0
        fun pad(d: Int) = indent.repeat(d.coerceAtLeast(0))
        fun newline() { if (out.isNotEmpty() && out.last() != '\n') out.append('\n') }
        var k = 0
        while (k < tokens.size) {
            val t = tokens[k]
            when {
                !t.startsWith("<") -> {
                    val s = t.trim()
                    if (s.isNotEmpty()) { newline(); out.append(pad(depth)).append(s) }
                }
                t.startsWith("<!--") || t.startsWith("<?") || t.startsWith("<!") && !t.startsWith("<![CDATA[") -> {
                    newline(); out.append(pad(depth)).append(t.trim())
                }
                t.startsWith("<![CDATA[") -> { newline(); out.append(pad(depth)).append(t) }
                t.startsWith("</") -> { depth--; newline(); out.append(pad(depth)).append(t.trim()) }
                else -> {
                    val selfClosing = t.endsWith("/>")
                    newline(); out.append(pad(depth)).append(tag(t, pad(depth) + indent))
                    if (!selfClosing) {
                        // <a>текст</a> — одной строкой.
                        val next = tokens.getOrNull(k + 1)
                        val after = tokens.getOrNull(k + 2)
                        if (next != null && !next.startsWith("<") && after != null && after.startsWith("</") && !next.contains('\n')) {
                            out.append(next.trim()).append(after.trim())
                            k += 3
                            continue
                        }
                        if (next != null && next.startsWith("</")) { out.append(next.trim()); k += 2; continue }
                        depth++
                    }
                }
            }
            k++
        }
        if (depth != 0) throw IllegalArgumentException("Несбалансированные теги")
        return out.toString()
    }

    /** Пробелы внутри тега вне кавычек — по одному; длинный тег с атрибутами — по атрибуту на строку. */
    private fun tag(raw: String, attrPad: String): String {
        val sb = StringBuilder()
        var quote = 0.toChar()
        var space = false
        for (c in raw) {
            if (quote == 0.toChar() && c.isWhitespace()) { space = true; continue }
            if (space) { sb.append(' '); space = false }
            sb.append(c)
            if (quote != 0.toChar()) { if (c == quote) quote = 0.toChar() } else if (c == '"' || c == '\'') quote = c
        }
        // Правится только конец тега: внутри значений атрибутов «>» и пробелы остаются как были.
        val one = sb.toString().let { if (it.endsWith(" >")) it.dropLast(2) + ">" else it }
        // Атрибуты: «имя="значение"» вне кавычек.
        val attrs = Regex("""\s([^\s=]+="[^"]*"|[^\s=]+='[^']*')""").findAll(one).map { it.groupValues[1] }.toList()
        if (one.length <= 100 || attrs.size < 2) return one
        val name = one.substringBefore(' ')
        val close = if (one.endsWith("/>")) " />" else ">"
        return name + attrs.joinToString("") { "\n" + attrPad + it } + close
    }

    private fun lineColumn(text: String, offset: Int): Pair<Int, Int> {
        var line = 1
        var lineStart = 0
        for (i in 0 until offset) if (text[i] == '\n') { line++; lineStart = i + 1 }
        return line to (offset - lineStart + 1)
    }

    private val compactJson = Json { prettyPrint = false }

    @OptIn(ExperimentalSerializationApi::class)
    private fun prettyJson(indent: String) = Json { prettyPrint = true; prettyPrintIndent = indent }

    /** @throws IllegalArgumentException с понятным сообщением, если вход некорректен. */
    fun apply(tool: TextTool, text: String, indent: String = "  "): String = when (tool) {
        TextTool.Validate -> text // проверка не меняет текст — см. validate()
        TextTool.Upper -> text.uppercase(Locale.ROOT)
        TextTool.Lower -> text.lowercase(Locale.ROOT)
        TextTool.Capitalize -> capitalizeWords(text)
        TextTool.SortAsc -> mapLines(text) { it.sortedWith(client.wexmanager.core.fs.NaturalOrder) }
        TextTool.SortDesc -> mapLines(text) { it.sortedWith(client.wexmanager.core.fs.NaturalOrder.reversed()) }
        TextTool.Unique -> mapLines(text) { it.distinct() }
        TextTool.Reverse -> mapLines(text) { it.reversed() }
        TextTool.RemoveEmpty -> mapLines(text) { lines -> lines.filter { it.isNotBlank() } }
        TextTool.TrimTrailing -> mapLines(text) { lines -> lines.map { it.trimEnd(' ', '\t') } }
        TextTool.JsonFormat -> prettyJson(indent).encodeToString(JsonElement.serializer(), parseJson(text)) + trailingNewline(text)
        TextTool.JsonMinify -> compactJson.encodeToString(JsonElement.serializer(), parseJson(text)) + trailingNewline(text)
        TextTool.XmlFormat -> formatXml(text, indent) + trailingNewline(text)
        TextTool.Base64Encode -> Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        TextTool.Base64Decode -> {
            // Строгий декодер: посторонние символы — ошибка, а не молчаливое «выбрасывание».
            val bytes = try {
                Base64.getDecoder().decode(text.filterNot(Char::isWhitespace))
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Это не Base64")
            }
            try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            } catch (e: java.nio.charset.CharacterCodingException) {
                throw IllegalArgumentException("В Base64 двоичные данные, а не текст UTF-8")
            }
        }
        TextTool.UrlEncode -> URLEncoder.encode(text, "UTF-8")
        TextTool.UrlDecode -> try {
            URLDecoder.decode(text, "UTF-8")
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Некорректная URL-последовательность")
        }
        TextTool.UnicodeEscape -> buildString {
            for (ch in text) if (ch.code < 0x80) append(ch) else append("\\u").append(String.format(Locale.ROOT, "%04x", ch.code))
        }
        TextTool.UnicodeUnescape -> UNICODE_ESCAPE.replace(text) { it.groupValues[1].toInt(16).toChar().toString() }.also { out ->
            // Одиночная половинка суррогатной пары не сохранится ни в одной кодировке.
            var i = 0
            while (i < out.length) {
                val ch = out[i]
                if (ch.isHighSurrogate() && i + 1 < out.length && out[i + 1].isLowSurrogate()) i += 2
                else if (ch.isSurrogate()) throw IllegalArgumentException("Непарный суррогат \\u%04x — нужна пара (например \\ud83d\\ude00)".format(ch.code))
                else i++
            }
        }
    }

    private fun trailingNewline(text: String) = if (text.endsWith("\n")) "\n" else ""

    private val UNICODE_ESCAPE = Regex("""\\u([0-9a-fA-F]{4})""")

    private fun parseJson(text: String): JsonElement = try {
        Json.parseToJsonElement(text)
    } catch (e: Exception) {
        val detail = e.message?.lineSequence()?.firstOrNull()?.removePrefix("Unexpected JSON token at offset ")
        throw IllegalArgumentException("Некорректный JSON" + (detail?.let { " (позиция $it)" } ?: ""))
    }

    /** Сохраняет завершающий перевод строки, если он был. */
    private inline fun mapLines(text: String, transform: (List<String>) -> List<String>): String {
        val trailing = text.endsWith("\n")
        val lines = (if (trailing) text.dropLast(1) else text).split('\n')
        return transform(lines).joinToString("\n") + if (trailing) "\n" else ""
    }

    private fun capitalizeWords(text: String): String {
        val sb = StringBuilder(text.length)
        var start = true
        for (ch in text) {
            sb.append(if (start && ch.isLetter()) ch.titlecaseChar() else if (ch.isLetter()) ch.lowercaseChar() else ch)
            start = !ch.isLetterOrDigit() && ch != '\''
        }
        return sb.toString()
    }
}

@Composable
fun TextToolsSheet(
    hasSelection: Boolean,
    readOnly: Boolean,
    onDismiss: () -> Unit,
    onTool: (TextTool) -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text("Инструменты текста", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
            Text(
                when {
                    readOnly -> "Файл открыт только для чтения"
                    hasSelection -> "Применяется к выделенному фрагменту"
                    else -> "Применяется ко всему файлу · выделите текст, чтобы изменить только его"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 12.dp),
            )
            TextTool.entries.groupBy { it.group }.forEach { (group, tools) ->
                Text(
                    group,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    tools.forEachIndexed { i, tool ->
                        SegmentedListItem(
                            onClick = { onTool(tool) },
                            enabled = !readOnly,
                            shapes = ListItemDefaults.segmentedShapes(i, tools.size),
                            leadingContent = { Icon(tool.icon, null) },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(tool.title) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp).navigationBarsPadding().fillMaxWidth())
        }
    }
}

/** Недавние текстовые файлы: быстрый переход между ними без возврата в браузер. */
@Composable
fun RecentFilesSheet(
    currentPath: String,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val files by androidx.compose.runtime.produceState<List<Pair<client.wexmanager.core.fs.FileItem, Long>>?>(null, currentPath) {
        val data = context.appContainer.userData.data.value
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // Недавние из браузера + файлы, в которых запоминалась позиция курсора (последние — свежее).
            val fromEditor = data.editorPositions.keys.reversed().map { it to 0L }
            val fromRecents = data.recents.filter { !it.isDirectory }.map { it.path to it.openedAt }
            (fromRecents + fromEditor)
                .distinctBy { it.first }
                .asSequence()
                .filter { it.first != currentPath && !it.first.startsWith("/data/") } // без служебных копий из кеша приложения
                .filter { client.wexmanager.core.fs.FileKind.of(it.first.substringAfterLast('/'), false).isTextLike }
                .filter { java.io.File(it.first).isFile }
                .take(30)
                .map { (p, t) -> client.wexmanager.core.fs.LocalFileSystem.stat(p) to t }
                .toList()
        }
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Text("Недавние файлы", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, bottom = 12.dp))
        val list = files
        when {
            list == null -> androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.LoadingIndicator()
            }
            list.isEmpty() -> Text(
                "Здесь появятся текстовые файлы, которые вы открывали",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
            )
            else -> androidx.compose.foundation.lazy.LazyColumn(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            ) {
                items(list.size, key = { list[it].first.path }) { i ->
                    val (item, openedAt) = list[i]
                    SegmentedListItem(
                        onClick = { onOpen(item.path) },
                        shapes = ListItemDefaults.segmentedShapes(i, list.size),
                        leadingContent = { client.wexmanager.ui.components.FileIcon(item, size = 40.dp) },
                        supportingContent = {
                            Text(
                                buildString {
                                    append(item.path.substringBeforeLast('/').replace(android.os.Environment.getExternalStorageDirectory().path, "Память"))
                                    if (openedAt > 0) append(" · ").append(client.wexmanager.core.util.Format.relative(openedAt))
                                },
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis,
                            )
                        },
                        trailingContent = { Text(client.wexmanager.core.util.Format.size(item.size), style = MaterialTheme.typography.labelMedium) },
                    ) { Text(item.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis) }
                }
            }
        }
    }
}
