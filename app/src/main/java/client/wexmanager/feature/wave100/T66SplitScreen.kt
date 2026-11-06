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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Текст режется по разделителю: каждый кусок — отдельная строка. */
@Composable
fun T66SplitScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var separator by rememberSaveable { mutableStateOf(",") }
    val pieces = remember(text, separator) { splitPieces(text, separator) }
    val result = remember(pieces) { pieces.joinToString("\n") }

    fun copyResult() {
        val copied = separator.isNotEmpty() && result.isNotEmpty() && copyPlain(context, result)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    separator.isEmpty() -> "Укажите разделитель"
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
                title = { Text("Разбить на строки") },
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
                "Текст режется по разделителю, каждый кусок становится строкой. Разделитель в конце не даёт пустую строку. Пустые куски в середине остаются. Точка и черта ищутся как есть, без шаблона.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = separator,
                onValueChange = { separator = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Разделитель") },
                placeholder = { Text("Например, «,»") },
                supportingText = separatorHint(separator)?.let { hint -> { Text(hint) } },
                isError = separator.isEmpty(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { separator = "," },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Запятая") }
                FilledTonalButton(
                    onClick = { separator = " " },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Пробел") }
                FilledTonalButton(
                    onClick = { separator = ";" },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Точка с запятой") }
                FilledTonalButton(
                    onClick = { separator = "|" },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Черта") }
                FilledTonalButton(
                    onClick = { separator = "\t" },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Табуляция") }
                FilledTonalButton(
                    onClick = { separator = "\n" },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Перевод строки") }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите текст") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                minLines = 6,
                maxLines = 10,
            )
            OutlinedTextField(
                value = result,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Строки") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 4,
                maxLines = 8,
            )
            Text(
                splitSummary(text, separator, pieces),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
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

private fun separatorHint(separator: String): String? = when {
    separator.isEmpty() -> "Укажите разделитель"
    separator == "\t" -> "Сейчас табуляция"
    separator == "\n" || separator == "\r" || separator == "\r\n" ->
        "Сейчас перевод строки: режет и по \\n, и по \\r\\n, и по \\r"
    separator.any { it == '\n' || it == '\r' || it == '\t' } -> "В разделителе есть невидимый символ"
    else -> null
}

private fun splitSummary(text: String, separator: String, pieces: List<String>): String = when {
    text.isEmpty() -> "Нет текста"
    separator.isEmpty() -> "Укажите разделитель"
    else -> plural(pieces.size, "строка", "строки", "строк")
}

/** Одна / несколько / много: 1 строка, 2 строки, 5 строк, 11 строк. */
private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$n $word"
}

/**
 * Делит [text] по буквальному [delimiter]. Хвост из одного пустого куска отбрасывается.
 * Разделитель из одного перевода строки режет `\n`, `\r\n` и `\r`.
 */
private fun splitPieces(text: String, delimiter: String): List<String> {
    if (text.isEmpty() || delimiter.isEmpty()) return emptyList()
    if (delimiter == "\n" || delimiter == "\r" || delimiter == "\r\n") return splitNewlines(text)
    val pieces = ArrayList<String>()
    var start = 0
    while (start <= text.length) {
        val found = text.indexOf(delimiter, start)
        if (found < 0) {
            pieces.add(text.substring(start))
            break
        }
        pieces.add(text.substring(start, found))
        start = found + delimiter.length
        if (start == text.length) break
    }
    return pieces
}

/** `\n`, `\r\n` и `\r` делят строки. Завершающий перевод не порождает пустой хвост. */
private fun splitNewlines(text: String): List<String> {
    val lines = ArrayList<String>()
    var index = 0
    while (index < text.length) {
        val found = text.indexOfAny(LINE_BREAKS, index)
        if (found < 0) {
            lines.add(text.substring(index))
            break
        }
        lines.add(text.substring(index, found))
        val next = if (
            text[found] == '\r' &&
            found + 1 < text.length &&
            text[found + 1] == '\n'
        ) {
            found + 2
        } else {
            found + 1
        }
        if (next >= text.length) break
        index = next
    }
    return lines
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Разбить на строки", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
