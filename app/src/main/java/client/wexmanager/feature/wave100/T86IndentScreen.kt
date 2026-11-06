package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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

private const val DEFAULT_WIDTH = 4

/** Ведущий отступ: табы в N пробелов по позициям табуляции и обратно. Дальше по строке символы не меняются. */
@Composable
fun T86IndentScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var widthText by rememberSaveable { mutableStateOf(DEFAULT_WIDTH.toString()) }
    var tabsToSpaces by rememberSaveable { mutableStateOf(true) }
    val width = widthText.toIntOrNull()?.takeIf { it >= 1 }
    val result = if (width == null) "" else convertIndent(text, width, tabsToSpaces)

    fun copyResult() {
        val copied = width != null && result.isNotEmpty() && copyPlain(context, result)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    width == null -> "Укажите ширину от 1"
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
                title = { Text("Замена отступа") },
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
                "Ведущий таб заменяется на пробелы с шагом N и доходит до следующей позиции, кратной N. " +
                    "Обратно отступ собирается в табы по тому же шагу, а хвост короче N остаётся пробелами. " +
                    "Пробелы и табы после первого другого символа строки не меняются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = widthText,
                onValueChange = { raw ->
                    widthText = raw.filter(Char::isDigit).take(2)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Пробелов на таб") },
                placeholder = { Text(DEFAULT_WIDTH.toString()) },
                supportingText = {
                    Text(if (width == null) "Укажите ширину от 1" else "По умолчанию $DEFAULT_WIDTH")
                },
                isError = width == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tabsToSpaces,
                    onClick = { tabsToSpaces = true },
                    label = { Text("Табы в пробелы") },
                    leadingIcon = if (tabsToSpaces) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    shapes = FilterChipDefaults.shapes(),
                )
                FilterChip(
                    selected = !tabsToSpaces,
                    onClick = { tabsToSpaces = false },
                    label = { Text("Пробелы в табы") },
                    leadingIcon = if (!tabsToSpaces) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    shapes = FilterChipDefaults.shapes(),
                )
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите текст") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                minLines = 6,
                maxLines = 10,
            )
            OutlinedTextField(
                value = result,
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

/** Меняет только ведущие пробелы и табы. Переводы строк `\n`, `\r\n` и `\r` сохраняются. */
private fun convertIndent(text: String, width: Int, tabsToSpaces: Boolean): String {
    if (text.isEmpty() || width < 1) return text
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val found = text.indexOfAny(LINE_BREAKS, index)
        val lineEnd = if (found < 0) text.length else found
        if (tabsToSpaces) appendTabsAsSpaces(text, index, lineEnd, width, out)
        else appendSpacesAsTabs(text, index, lineEnd, width, out)
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

/** Таб в отступе доводит колонку до следующей позиции, кратной [width]. Остаток строки копируется как есть. */
private fun appendTabsAsSpaces(
    text: String,
    start: Int,
    end: Int,
    width: Int,
    out: StringBuilder,
) {
    var index = start
    var column = 0
    while (index < end) {
        when (text[index]) {
            ' ' -> {
                out.append(' ')
                column++
                index++
            }
            '\t' -> {
                val pad = width - (column % width)
                repeat(pad) { out.append(' ') }
                column += pad
                index++
            }
            else -> break
        }
    }
    if (index < end) out.append(text, index, end)
}

/** Колонки отступа снова собираются в табы. Хвост короче [width] остаётся пробелами. */
private fun appendSpacesAsTabs(
    text: String,
    start: Int,
    end: Int,
    width: Int,
    out: StringBuilder,
) {
    var index = start
    var column = 0
    while (index < end) {
        when (text[index]) {
            ' ' -> {
                column++
                index++
            }
            '\t' -> {
                column += width - (column % width)
                index++
            }
            else -> break
        }
    }
    repeat(column / width) { out.append('\t') }
    repeat(column % width) { out.append(' ') }
    if (index < end) out.append(text, index, end)
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Отступ", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
