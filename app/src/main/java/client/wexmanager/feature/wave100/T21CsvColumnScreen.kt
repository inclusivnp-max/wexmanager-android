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

/** Один столбец CSV, нумерация с единицы. Кавычки вокруг поля снимаются. */
@Composable
fun T21CsvColumnScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var csv by rememberSaveable { mutableStateOf("") }
    var columnText by rememberSaveable { mutableStateOf("1") }
    val column = columnText.toIntOrNull()?.takeIf { it >= 1 }
    val extract = remember(csv, column) {
        if (csv.isEmpty() || column == null) null else extractCsvColumn(csv, column)
    }
    val result = extract?.text.orEmpty()
    val columnError = when {
        columnText.isEmpty() -> "Укажите номер столбца"
        column == null -> "Номер столбца — целое число от 1"
        else -> null
    }
    val resultError = if (extract?.missing == true) "В строках нет столбца $column" else null

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
                title = { Text("Столбец CSV") },
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
                "Вставьте CSV и укажите столбец с единицы. Каждое значение — с новой строки. Запятая делит поля, текст в кавычках остаётся одним значением, две кавычки подряд внутри — одна.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = csv,
                onValueChange = { csv = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("CSV") },
                placeholder = { Text("Вставьте CSV") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                minLines = 6,
                maxLines = 10,
            )
            OutlinedTextField(
                value = columnText,
                onValueChange = { next ->
                    columnText = next.filter { it.isDigit() }.take(6)
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Столбец") },
                placeholder = { Text("1") },
                supportingText = { Text(columnError ?: "Нумерация с 1") },
                isError = columnError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedTextField(
                value = result,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Значения") },
                isError = resultError != null,
                supportingText = resultError?.let { message -> { Text(message) } },
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

private data class CsvColumn(val text: String, val missing: Boolean)

private fun extractCsvColumn(csv: String, column: Int): CsvColumn {
    val rows = parseCsvRows(csv)
    if (rows.isEmpty()) return CsvColumn("", false)
    val cells = rows.map { it.getOrNull(column - 1) }
    if (cells.all { it == null }) return CsvColumn("", true)
    return CsvColumn(cells.joinToString("\n") { it.orEmpty() }, false)
}

/**
 * Запятая вне кавычек делит поля. Кавычки по краям снимаются, "" внутри — одна кавычка.
 * Перенос строки всегда начинает новую запись.
 */
private fun parseCsvRows(text: String): List<List<String>> {
    if (text.isEmpty()) return emptyList()
    val source = if (text[0] == '\uFEFF') text.substring(1) else text
    if (source.isEmpty()) return emptyList()
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val field = StringBuilder()
    var inQuotes = false
    var pending = false
    var i = 0

    fun endRow() {
        row.add(field.toString())
        field.clear()
        inQuotes = false
        pending = false
        rows.add(row.toList())
        row = ArrayList()
    }

    while (i < source.length) {
        val c = source[i]
        if (inQuotes) {
            when {
                c == '"' && i + 1 < source.length && source[i + 1] == '"' -> {
                    field.append('"')
                    i += 2
                }
                c == '"' -> {
                    inQuotes = false
                    i++
                    while (i < source.length && (source[i] == ' ' || source[i] == '\t')) i++
                }
                c == '\n' -> {
                    i++
                    endRow()
                }
                c == '\r' -> {
                    i += if (i + 1 < source.length && source[i + 1] == '\n') 2 else 1
                    endRow()
                }
                else -> {
                    field.append(c)
                    i++
                }
            }
        } else {
            when (c) {
                '"' -> {
                    if (field.isEmpty()) inQuotes = true else field.append('"')
                    pending = true
                    i++
                }
                ',' -> {
                    row.add(field.toString())
                    field.clear()
                    pending = true
                    i++
                }
                '\n' -> {
                    i++
                    endRow()
                }
                '\r' -> {
                    i += if (i + 1 < source.length && source[i + 1] == '\n') 2 else 1
                    endRow()
                }
                ' ', '\t' -> {
                    if (field.isEmpty()) {
                        var j = i
                        while (j < source.length && (source[j] == ' ' || source[j] == '\t')) j++
                        if (j < source.length && source[j] == '"') {
                            i = j
                        } else {
                            field.append(c)
                            pending = true
                            i++
                        }
                    } else {
                        field.append(c)
                        pending = true
                        i++
                    }
                }
                else -> {
                    field.append(c)
                    pending = true
                    i++
                }
            }
        }
    }
    if (pending || inQuotes || field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Столбец CSV", value))
    true
} catch (_: RuntimeException) {
    false
}
