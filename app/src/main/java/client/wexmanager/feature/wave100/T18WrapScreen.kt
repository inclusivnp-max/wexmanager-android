package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val DEFAULT_COLUMNS = 80

/** Перенос по колонкам: слово остаётся целым, пока помещается в ширину. */
@Composable
fun T18WrapScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var columnsText by rememberSaveable { mutableStateOf(DEFAULT_COLUMNS.toString()) }
    val columns = columnsText.toIntOrNull()?.takeIf { it >= 1 }
    val wrapped = if (columns == null) "" else wrapColumns(text, columns)

    fun copyResult() {
        val copied = columns != null && wrapped.isNotEmpty() && copyPlain(context, wrapped)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    columns == null -> "Укажите ширину от 1"
                    wrapped.isEmpty() -> "Нечего копировать"
                    copied -> "Скопировано"
                    else -> "Не удалось скопировать"
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Перенос по колонкам") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = ::copyResult, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.ContentCopy, "Копировать")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surface,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Строка длиннее N колонок переносится по пробелу. Слово длиннее ширины режется. Одна кодовая точка — одна колонка.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = columnsText,
                onValueChange = { raw ->
                    columnsText = raw.filter(Char::isDigit).take(5)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Колонки") },
                placeholder = { Text(DEFAULT_COLUMNS.toString()) },
                supportingText = {
                    Text(if (columns == null) "Укажите ширину от 1" else "По умолчанию $DEFAULT_COLUMNS")
                },
                isError = columns == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите текст") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false,
                ),
                minLines = 6,
                maxLines = 10,
            )
            OutlinedTextField(
                value = wrapped,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Результат") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 10,
            )
            FilledTonalButton(
                onClick = ::copyResult,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать")
            }
        }
    }
}

private fun wrapColumns(text: String, columns: Int): String {
    val width = columns.coerceAtLeast(1)
    if (text.isEmpty()) return text
    val out = StringBuilder(text.length + text.length / width)
    var index = 0
    while (index < text.length) {
        val found = text.indexOfAny(LINE_BREAKS, index)
        val lineEnd = if (found < 0) text.length else found
        wrapLine(text.substring(index, lineEnd), width, out)
        if (found < 0) break
        val separatorEnd = if (
            text[found] == '\r' &&
            found + 1 < text.length &&
            text[found + 1] == '\n'
        ) {
            found + 2
        } else {
            found + 1
        }
        out.append(text, found, separatorEnd)
        index = separatorEnd
    }
    return out.toString()
}

/** Пробел между словами — точка переноса. Слово длиннее ширины режется по кодовым точкам. */
private fun wrapLine(line: String, width: Int, out: StringBuilder) {
    if (line.isEmpty()) return
    val tokens = tokenize(line)
    var column = 0
    var hasWord = false
    var index = 0
    while (index < tokens.size) {
        val token = tokens[index]
        if (token.space) {
            val next = tokens.getOrNull(index + 1)
            when {
                next == null || !hasWord -> {
                    column = appendMeasured(out, token.text, column, width)
                    index++
                }
                next.cols > width && column + token.cols <= width -> {
                    column = appendMeasured(out, token.text, column, width)
                    index++
                }
                column + token.cols + next.cols <= width -> {
                    out.append(token.text)
                    column += token.cols
                    index++
                }
                else -> {
                    out.append('\n')
                    column = 0
                    hasWord = false
                    index++
                }
            }
        } else if (token.cols <= width && column + token.cols <= width) {
            out.append(token.text)
            column += token.cols
            hasWord = true
            index++
        } else if (token.cols <= width) {
            if (column > 0) out.append('\n')
            out.append(token.text)
            column = token.cols
            hasWord = true
            index++
        } else {
            if (column > 0) {
                out.append('\n')
                column = 0
            }
            column = appendMeasured(out, token.text, 0, width)
            hasWord = true
            index++
        }
    }
}

private fun appendMeasured(out: StringBuilder, text: String, column: Int, width: Int): Int {
    var col = column
    var index = 0
    while (index < text.length) {
        if (col >= width) {
            out.append('\n')
            col = 0
        }
        val point = text.codePointAt(index)
        out.appendCodePoint(point)
        index += Character.charCount(point)
        col++
    }
    return col
}

private fun tokenize(line: String): List<Token> {
    val tokens = ArrayList<Token>(8)
    var index = 0
    while (index < line.length) {
        val start = index
        val space = Character.isWhitespace(line.codePointAt(index))
        var cols = 0
        while (index < line.length && Character.isWhitespace(line.codePointAt(index)) == space) {
            index += Character.charCount(line.codePointAt(index))
            cols++
        }
        tokens.add(Token(line.substring(start, index), space, cols))
    }
    return tokens
}

private data class Token(val text: String, val space: Boolean, val cols: Int)

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Перенос", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
