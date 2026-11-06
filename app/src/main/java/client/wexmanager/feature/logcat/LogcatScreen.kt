package client.wexmanager.feature.logcat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import client.wexmanager.R
import client.wexmanager.core.fs.RootShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

private val LogcatFont = FontFamily(Font(R.font.jetbrains_mono))

private val TimeLevel = Regex("""^(?:\d{4}-)?\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+([VDIWEFS])/""")
private val ThreadLevel = Regex("""^(?:\d{4}-)?\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+\d+\s+\d+\s+([VDIWEFS])\b""")

private enum class LogLevel(val label: String, val letter: Char?) {
    All("Все", null),
    Error("E", 'E'),
    Warn("W", 'W'),
    Info("I", 'I'),
}

private data class Capture(val code: Int, val out: String, val err: String, val failed: Boolean) {
    fun lines(): List<String> = splitLines(out)

    /** Прямой logcat не удался или пришёл пустой вывод вместе с текстом ошибки. */
    fun needsRoot(): Boolean = failed || (lines().isEmpty() && err.isNotBlank())
}

private data class LoadResult(val lines: List<String>, val error: String?)

/**
 * Просмотр последних строк logcat.
 * «Очистить экран» сбрасывает только список на экране и не вызывает `logcat -c`.
 */
@Composable
fun LogcatScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var cleared by remember { mutableStateOf(false) }
    var request by remember { mutableIntStateOf(0) }
    var ticket by remember { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var levelName by rememberSaveable { mutableStateOf(LogLevel.All.name) }
    val level = remember(levelName) { runCatching { LogLevel.valueOf(levelName) }.getOrDefault(LogLevel.All) }
    val filtered = remember(lines, query, level) { lines.filter { matches(it, query, level) } }
    val listState = rememberLazyListState()

    LaunchedEffect(request) {
        val mine = ticket
        loading = true
        cleared = false
        error = null
        val result = loadLogcat()
        if (mine != ticket) return@LaunchedEffect
        lines = result.lines
        error = result.error
        loading = false
    }

    LaunchedEffect(lines) {
        val last = filtered.lastIndex
        if (last >= 0) listState.scrollToItem(last)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Журнал") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { copyVisible(context, filtered) },
                        enabled = filtered.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
                    if (loading) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(24.dp))
                        }
                    } else {
                        IconButton(
                            onClick = { request++ },
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(Icons.Rounded.Refresh, "Обновить") }
                    }
                    IconButton(
                        onClick = {
                            ticket++
                            lines = emptyList()
                            error = null
                            loading = false
                            cleared = true
                        },
                        enabled = lines.isNotEmpty() || error != null,
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(Icons.Rounded.ClearAll, "Очистить экран") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
        },
        containerColor = scheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Фильтр") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, "Очистить фильтр")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerHighest,
                    unfocusedContainerColor = scheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogLevel.entries.forEach { item ->
                    FilterChip(
                        selected = item == level,
                        onClick = { levelName = item.name },
                        label = { Text(item.label) },
                    )
                }
            }
            if (lines.isNotEmpty() && (query.isNotBlank() || level != LogLevel.All)) {
                Text(
                    "Показано ${filtered.size} из ${lines.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    loading && lines.isEmpty() -> StatusMessage(Icons.Rounded.Article, "Читаем журнал…", null)
                    lines.isEmpty() -> {
                        val (icon, title, text) = when {
                            error != null -> Triple(Icons.Rounded.ErrorOutline, "Не удалось прочитать журнал", error)
                            cleared -> Triple(Icons.Rounded.ClearAll, "Список очищен", "Журнал устройства не изменён")
                            else -> Triple(Icons.Rounded.Article, "Журнал пуст", "Нажмите кнопку обновления справа вверху")
                        }
                        StatusMessage(icon, title, text)
                    }
                    filtered.isEmpty() -> StatusMessage(Icons.Rounded.SearchOff, "Ничего не найдено", "Нет строк с таким фильтром")
                    else -> SelectionContainer(Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(filtered, contentType = { _, _ -> "line" }) { index, line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = LogcatFont),
                                    color = lineColor(levelOf(line)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (index % 2 == 0) Color.Transparent else scheme.surfaceContainerLow)
                                        .padding(horizontal = 12.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusMessage(icon: ImageVector, title: String, text: String?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(48.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        if (!text.isNullOrBlank()) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun lineColor(level: Char?): Color {
    val scheme = MaterialTheme.colorScheme
    return when (level) {
        'E', 'F' -> scheme.error
        'W' -> scheme.tertiary
        'I' -> scheme.primary
        'D', 'V' -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }
}

private fun copyVisible(context: Context, lines: List<String>) {
    if (lines.isEmpty()) return
    val copied = runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: error("clipboard")
        cm.setPrimaryClip(ClipData.newPlainText("Журнал", lines.joinToString("\n")))
    }.isSuccess
    if (copied) Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

private fun matches(line: String, query: String, level: LogLevel): Boolean {
    val letter = level.letter
    if (letter != null && levelOf(line) != letter) return false
    return query.isBlank() || line.contains(query, ignoreCase = true)
}

private fun levelOf(line: String): Char? =
    TimeLevel.find(line)?.groupValues?.getOrNull(1)?.firstOrNull()
        ?: ThreadLevel.find(line)?.groupValues?.getOrNull(1)?.firstOrNull()

private fun splitLines(text: String): List<String> =
    text.split('\n').map { it.trimEnd('\r') }.filter { it.isNotEmpty() }

private fun explain(err: String, code: Int): String {
    val clean = err.trim().take(500)
    return if (clean.isEmpty()) "Не удалось прочитать журнал (код $code)"
    else "Не удалось прочитать журнал.\n$clean"
}

private suspend fun loadLogcat(): LoadResult = withContext(Dispatchers.IO) {
    val direct = captureDirect()
    if (direct.needsRoot() && RootShell.enabled) {
        val root = try {
            val r = RootShell.run("logcat -d -t 400 -v time")
            Capture(r.code, r.out, r.err.trim(), r.code != 0)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (direct.lines().isNotEmpty()) return@withContext LoadResult(direct.lines(), null)
            return@withContext LoadResult(emptyList(), explain(e.message ?: direct.err, direct.code))
        }
        val rootLines = root.lines()
        if (rootLines.isNotEmpty() || !root.needsRoot()) return@withContext LoadResult(rootLines, null)
        if (direct.lines().isNotEmpty()) return@withContext LoadResult(direct.lines(), null)
        val err = root.err.ifBlank { direct.err }
        val code = if (root.err.isBlank()) direct.code else root.code
        return@withContext LoadResult(emptyList(), explain(err, code))
    }
    val lines = direct.lines()
    if (lines.isEmpty() && direct.needsRoot()) LoadResult(emptyList(), explain(direct.err, direct.code))
    else LoadResult(lines, null)
}

/** `/system/bin/logcat -d -t 400 -v time`. Отмена корутины убивает процесс. */
private suspend fun captureDirect(): Capture = withContext(Dispatchers.IO) {
    val process = try {
        ProcessBuilder("/system/bin/logcat", "-d", "-t", "400", "-v", "time").start()
    } catch (e: IOException) {
        return@withContext Capture(-1, "", e.message ?: "logcat недоступен", true)
    }
    val onCancel = coroutineContext.job.invokeOnCompletion { cause ->
        if (cause is CancellationException) runCatching { process.destroyForcibly() }
    }
    try {
        process.outputStream.close()
        val errBuf = ByteArrayOutputStream()
        val errThread = Thread({
            runCatching {
                process.errorStream.use { input ->
                    val buf = ByteArray(2048)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (errBuf.size() < 16_384) errBuf.write(buf, 0, n)
                    }
                }
            }
        }, "logcat-err").apply { isDaemon = true; start() }
        val out = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        ensureActive()
        val finished = process.waitFor(20, TimeUnit.SECONDS)
        ensureActive()
        if (!finished) {
            process.destroyForcibly()
            errThread.join(500)
            Capture(-1, out, "logcat не ответил вовремя", true)
        } else {
            errThread.join(1_000)
            val code = runCatching { process.exitValue() }.getOrDefault(-1)
            Capture(code, out, errBuf.toString(Charsets.UTF_8).trim(), code != 0)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Capture(-1, "", e.message ?: "logcat недоступен", true)
    } finally {
        onCancel.dispose()
        runCatching { process.destroyForcibly() }
    }
}
