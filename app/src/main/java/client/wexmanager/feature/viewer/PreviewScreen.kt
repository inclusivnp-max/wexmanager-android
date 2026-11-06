package client.wexmanager.feature.viewer

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import client.wexmanager.core.db.DbCell
import client.wexmanager.core.db.DbResult
import client.wexmanager.feature.apk.ShapeMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import java.io.File

/** Что показывает предпросмотр. */
enum class PreviewKind { Markdown, Html, Table }

fun previewKindFor(name: String): PreviewKind? = when (name.substringAfterLast('.', "").lowercase()) {
    "md", "markdown", "mdown", "mkd", "mkdn" -> PreviewKind.Markdown
    "html", "htm", "xhtml" -> PreviewKind.Html
    "csv", "tsv", "tab" -> PreviewKind.Table
    else -> null
}

/** Markdown → HTML (GFM: таблицы, зачёркивание, задачи, автоссылки); «сырой» HTML в тексте экранируется. */
object MarkdownHtml {
    private val extensions = listOf(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(), AutolinkExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).escapeHtml(true).build()

    fun render(markdown: String): String = renderer.render(parser.parse(markdown))
}

/** CSV/TSV (RFC 4180: кавычки, удвоенные кавычки, переводы строк внутри поля). */
object Csv {
    fun parse(text: String, separator: Char, maxRows: Int = 20_000): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length && rows.size < maxRows) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                c == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                !quoted && c == separator -> { row += field.toString(); field.clear() }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += field.toString(); field.clear()
                    rows += row; row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PreviewScreen(path: String, base: String? = null, onBack: () -> Unit) {
    val file = remember(path) { File(path) }
    val kind = remember(path) { previewKindFor(file.name) ?: PreviewKind.Markdown }
    val scheme = MaterialTheme.colorScheme
    var content by remember { mutableStateOf<Result<Any>?>(null) }
    var js by rememberSaveable { mutableStateOf(false) }
    var textSp by rememberSaveable { mutableIntStateOf(16) }
    var cell by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        content = runCatching {
            withContext(Dispatchers.IO) {
                if (file.length() > 16L * 1024 * 1024) throw java.io.IOException("Файл больше 16 МБ")
                val text = file.readText()
                when (kind) {
                    PreviewKind.Markdown -> MarkdownHtml.render(text)
                    PreviewKind.Html -> text
                    PreviewKind.Table -> {
                        val sep = if (file.extension.lowercase() in setOf("tsv", "tab")) '\t' else if (text.lineSequence().firstOrNull()?.count { it == ';' } ?: 0 > (text.lineSequence().firstOrNull()?.count { it == ',' } ?: 0)) ';' else ','
                        val rows = Csv.parse(text, sep)
                        val width = rows.maxOfOrNull { it.size } ?: 0
                        val header = rows.firstOrNull().orEmpty().let { h -> List(width) { i -> h.getOrNull(i)?.ifBlank { null } ?: "#${i + 1}" } }
                        DbResult(header, rows.drop(1).map { r -> List(width) { i -> DbCell(r.getOrNull(i).orEmpty()) } })
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text(
                            when (kind) { PreviewKind.Markdown -> "Markdown"; PreviewKind.Html -> "HTML"; PreviewKind.Table -> "Таблица" },
                            style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { textSp = (textSp - 1).coerceAtLeast(12) },
                        enabled = textSp > 12,
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(Icons.Rounded.TextDecrease, "Уменьшить текст") }
                    Text("$textSp sp", style = MaterialTheme.typography.labelLarge)
                    IconButton(
                        onClick = { textSp = (textSp + 1).coerceAtMost(28) },
                        enabled = textSp < 28,
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(Icons.Rounded.TextIncrease, "Увеличить текст") }
                    if (kind == PreviewKind.Html) FilterChip(selected = js, onClick = { js = !js }, label = { Text("JavaScript") }, leadingIcon = { Icon(Icons.Rounded.Code, null, Modifier.size(18.dp)) }, modifier = Modifier.padding(end = 8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
        containerColor = scheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val r = content
            when {
                r == null -> LoadingIndicator(Modifier.size(64.dp).align(Alignment.Center))
                r.isFailure -> ShapeMessage(Icons.Rounded.Error, "Не удалось открыть", r.exceptionOrNull()?.message, error = true)
                kind == PreviewKind.Table -> {
                    val typo = MaterialTheme.typography
                    val sized = typo.copy(
                        bodyLarge = typo.bodyLarge.copy(fontSize = textSp.sp, lineHeight = (textSp * 1.35f).sp),
                        bodySmall = typo.bodySmall.copy(fontSize = textSp.sp, lineHeight = (textSp * 1.35f).sp),
                        labelMedium = typo.labelMedium.copy(fontSize = (textSp * 0.85f).sp),
                        labelSmall = typo.labelSmall.copy(fontSize = (textSp * 0.75f).sp),
                    )
                    MaterialTheme(colorScheme = scheme, typography = sized) {
                        Grid(r.getOrThrow() as DbResult, loadingMore = false, onEnd = {}) { row, col -> cell = (r.getOrThrow() as DbResult).rows[row][col].text }
                    }
                }
                else -> {
                    val body = r.getOrThrow() as String
                    val html = if (kind == PreviewKind.Markdown) page(body, scheme.surface, scheme.onSurface, scheme.primary, scheme.surfaceContainerHigh, scheme.outlineVariant, textSp) else body
                    val baseUrl = "file://" + (base ?: file.parent ?: "/") + "/"
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = object : WebViewClient() {
                                    // Внешние ссылки — в браузер; файлы и якоря — здесь.
                                    override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                                        val url = request.url
                                        if (url.scheme != "http" && url.scheme != "https" && url.scheme != "mailto") return false
                                        runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                        return true
                                    }
                                }
                                settings.allowFileAccess = true // относительные картинки рядом с файлом
                                settings.allowContentAccess = false
                                @Suppress("DEPRECATION")
                                settings.allowFileAccessFromFileURLs = false
                                @Suppress("DEPRECATION")
                                settings.allowUniversalAccessFromFileURLs = false
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                setBackgroundColor(if (kind == PreviewKind.Markdown) scheme.surface.toArgb() else android.graphics.Color.WHITE)
                            }
                        },
                        update = { v ->
                            val zoom = if (kind == PreviewKind.Html) textSp * 100 / 16 else 100
                            if (v.settings.textZoom != zoom) v.settings.textZoom = zoom
                            if (v.settings.javaScriptEnabled != js) v.settings.javaScriptEnabled = js
                            val key = html.hashCode() * 31 + js.hashCode()
                            if (v.tag != key) {
                                v.tag = key
                                v.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                            }
                        },
                        onRelease = { it.destroy() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    cell?.let { t ->
        AlertDialog(
            onDismissRequest = { cell = null },
            text = { SelectionContainer { Text(t, fontFamily = FontFamily.Monospace, fontSize = textSp.sp, lineHeight = (textSp * 1.4f).sp, modifier = Modifier.verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { cell = null }) { Text("Закрыть") } },
        )
    }
}

private fun hex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)

/** Страница для Markdown в цветах темы приложения. */
private fun page(body: String, bg: Color, fg: Color, accent: Color, code: Color, line: Color, textSp: Int): String = """
<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body{background:${hex(bg)};color:${hex(fg)};font:${textSp.coerceIn(12, 28)}px/1.6 sans-serif;margin:0;padding:16px 18px 48px;word-wrap:break-word}
a{color:${hex(accent)}} h1,h2{border-bottom:1px solid ${hex(line)};padding-bottom:.3em}
code{background:${hex(code)};border-radius:6px;padding:.1em .35em;font-family:monospace;font-size:.92em}
pre{background:${hex(code)};border-radius:12px;padding:12px;overflow:auto}pre code{padding:0;background:none}
blockquote{margin:0;padding:0 1em;border-left:4px solid ${hex(accent)};opacity:.85}
table{border-collapse:collapse;display:block;overflow:auto}td,th{border:1px solid ${hex(line)};padding:6px 10px}
img{max-width:100%} hr{border:none;border-top:1px solid ${hex(line)}} li input{margin-right:.5em}
</style></head><body>$body</body></html>
""".trimIndent()
