package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** TSV в CSV с кавычками по RFC 4180 и обратный разбор. Переводы строк на краях текста сохраняются. */
@Composable
fun T22TsvCsvScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun apply(transform: (String) -> String) {
        try {
            result = transform(text)
            error = null
        } catch (e: CsvParseException) {
            result = ""
            error = e.message
        }
    }

    fun copyResult() {
        val copied = result.isNotEmpty() && copyPlain(context, result)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    result.isEmpty() -> "Нечего копировать"
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
                title = { Text("TSV и CSV") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
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
                "Поля через табуляцию становятся CSV через запятую. Кавычки ставятся, если в поле есть запятая, кавычка или перевод строки. Обратно кавычки разбираются, а удвоенная кавычка становится одной.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Исходный текст") },
                placeholder = { Text("TSV или CSV") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 10,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { apply(::tsvToCsv) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("TSV в CSV") }
                FilledTonalButton(
                    onClick = { apply(::csvToTsv) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("CSV в TSV") }
            }
            OutlinedTextField(
                value = result,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Результат") },
                isError = error != null,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 10,
            )
            if (error != null) {
                Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            }
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

private class CsvParseException(message: String) : IllegalArgumentException(message)

private fun tsvToCsv(text: String): String = renderTable(parseTsv(text), text, ::joinCsvRow)

private fun csvToTsv(text: String): String = renderTable(parseCsv(text), text, ::joinTsvRow)

private fun parseTsv(text: String): List<List<String>> {
    if (text.isEmpty()) return emptyList()
    val rows = ArrayList<List<String>>()
    var start = 0
    var index = 0
    while (index < text.length) {
        val ch = text[index]
        if (ch != '\n' && ch != '\r') {
            index++
            continue
        }
        rows.add(text.substring(start, index).split('\t'))
        val next = if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index + 2 else index + 1
        index = next
        start = next
    }
    if (start < text.length) rows.add(text.substring(start).split('\t'))
    return rows
}

/** Запятая вне кавычек делит поля. Перевод строки внутри кавычек остаётся в ячейке. */
private fun parseCsv(text: String): List<List<String>> {
    if (text.isEmpty()) return emptyList()
    val rows = ArrayList<List<String>>()
    val row = ArrayList<String>()
    val field = StringBuilder()
    var index = 0
    var quoted = false
    var closed = false
    while (index < text.length) {
        val ch = text[index]
        if (quoted) {
            if (ch == '"') {
                if (index + 1 < text.length && text[index + 1] == '"') {
                    field.append('"')
                    index += 2
                } else {
                    quoted = false
                    closed = true
                    index++
                }
            } else {
                field.append(ch)
                index++
            }
            continue
        }
        when (ch) {
            '"' -> {
                if (closed || field.isNotEmpty()) {
                    throw CsvParseException("Кавычка внутри поля без экранирования")
                }
                quoted = true
                index++
            }
            ',' -> {
                row.add(field.toString())
                field.setLength(0)
                closed = false
                index++
            }
            '\n', '\r' -> {
                row.add(field.toString())
                rows.add(row.toList())
                row.clear()
                field.setLength(0)
                closed = false
                index = if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index + 2 else index + 1
            }
            else -> {
                if (closed) throw CsvParseException("Лишний символ после закрывающей кавычки")
                field.append(ch)
                index++
            }
        }
    }
    if (quoted) throw CsvParseException("Не закрыта кавычка")
    if (field.isNotEmpty() || closed || row.isNotEmpty()) {
        row.add(field.toString())
        rows.add(row.toList())
    }
    return rows
}

private fun renderTable(
    rows: List<List<String>>,
    source: String,
    joinRow: (List<String>) -> String,
): String {
    if (source.isEmpty()) return ""
    val separator = preferredBreak(source)
    val body = rows.joinToString(separator, transform = joinRow)
    val trailing = source.last() == '\n' || source.last() == '\r'
    return if (trailing) body + separator else body
}

private fun joinCsvRow(fields: List<String>): String = fields.joinToString(",") { quoteCsvField(it) }

private fun joinTsvRow(fields: List<String>): String = fields.joinToString("\t")

/** Кавычки нужны для запятой, кавычки и перевода строки. Внутренняя кавычка удваивается. */
private fun quoteCsvField(field: String): String {
    val special = field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    if (!special) return field
    return buildString(field.length + 2) {
        append('"')
        for (ch in field) {
            if (ch == '"') append('"')
            append(ch)
        }
        append('"')
    }
}

private fun preferredBreak(text: String): String {
    var index = 0
    var crLf = false
    var lf = false
    var cr = false
    while (index < text.length) {
        when (text[index]) {
            '\r' -> if (index + 1 < text.length && text[index + 1] == '\n') {
                crLf = true
                index += 2
            } else {
                cr = true
                index++
            }
            '\n' -> {
                lf = true
                index++
            }
            else -> index++
        }
    }
    return when {
        lf -> "\n"
        crLf -> "\r\n"
        cr -> "\r"
        else -> "\n"
    }
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("TSV/CSV", value))
    true
} catch (_: RuntimeException) {
    false
}
