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

/** Строки делятся по разделителю, столбцы дополняются пробелами до общей ширины. */
@Composable
fun T88AlignScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var delimiter by rememberSaveable { mutableStateOf("|") }
    val aligned = remember(text, delimiter) { alignColumns(text, delimiter) }
    val mono = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)

    fun copyResult() {
        val copied = aligned.isNotEmpty() && copyPlain(context, aligned)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    aligned.isEmpty() -> "Нечего копировать"
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
                title = { Text("Ровные столбцы") },
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
                "Каждая строка делится по разделителю. Ячейки дополняются пробелами, чтобы столбцы стояли друг под другом.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = delimiter,
                onValueChange = { delimiter = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Разделитель") },
                placeholder = { Text("|") },
                singleLine = true,
                isError = delimiter.isEmpty(),
                supportingText = { Text(if (delimiter.isEmpty()) "Укажите разделитель" else "Делит каждую строку") },
                textStyle = mono,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Исходный текст") },
                placeholder = { Text("имя|город\nАня|Москва") },
                textStyle = mono,
                minLines = 6,
                maxLines = 10,
            )
            OutlinedTextField(
                value = aligned,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Результат") },
                textStyle = mono,
                minLines = 6,
                maxLines = 10,
            )
            FilledTonalButton(onClick = ::copyResult, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать")
            }
        }
    }
}

/** Пустой разделитель ничего не меняет. Хвостовой перевод строки сохраняется. */
private fun alignColumns(text: String, delimiter: String): String {
    if (text.isEmpty() || delimiter.isEmpty()) return text
    val newline = if ("\r\n" in text) "\r\n" else if ('\r' in text && '\n' !in text) "\r" else "\n"
    val lines = ArrayList<String>()
    var index = 0
    while (index < text.length) {
        val found = text.indexOfAny(LINE_BREAKS, index)
        if (found < 0) {
            lines.add(text.substring(index))
            break
        }
        lines.add(text.substring(index, found))
        val pair = text[found] == '\r' && found + 1 < text.length && text[found + 1] == '\n'
        val next = found + if (pair) 2 else 1
        if (next >= text.length) break
        index = next
    }
    val rows = lines.map { splitFields(it, delimiter) }
    val width = IntArray(rows.maxOf { it.size })
    for (row in rows) for (i in row.indices) if (row[i].length > width[i]) width[i] = row[i].length
    val body = rows.joinToString(newline) { row ->
        row.mapIndexed { i, cell -> if (i < row.lastIndex) cell.padEnd(width[i]) else cell }.joinToString(" ")
    }
    val trailing = text.last() == '\n' || text.last() == '\r'
    return if (trailing) body + newline else body
}

/** Хвостовое пустое поле сохраняется: обычный split его отбрасывает. */
private fun splitFields(line: String, delimiter: String): List<String> {
    val fields = ArrayList<String>()
    var start = 0
    while (true) {
        val found = line.indexOf(delimiter, start)
        if (found < 0) {
            fields.add(line.substring(start))
            return fields
        }
        fields.add(line.substring(start, found))
        start = found + delimiter.length
        if (start == line.length) {
            fields.add("")
            return fields
        }
    }
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Ровные столбцы", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
