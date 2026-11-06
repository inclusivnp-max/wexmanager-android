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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val DEFAULT_LINE_COUNT = 10

/** Первые N и последние N строк. По умолчанию 10. Переводы строк сохраняются. */
@Composable
fun T57HeadTailScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var countText by rememberSaveable { mutableStateOf(DEFAULT_LINE_COUNT.toString()) }
    val count = parseLineCount(countText)
    val parts = remember(text, count) { headAndTail(text, count) }

    fun copyPart(value: String, emptyMessage: String) {
        val copied = value.isNotEmpty() && copyPlain(context, value)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    value.isEmpty() -> emptyMessage
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
                title = { Text("Начало и конец") },
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
                "Первые N строк и последние N строк. Пустое поле — 10. Завершающий перевод не добавляет лишнюю строку.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = countText,
                onValueChange = { countText = sanitizeLineCount(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Число строк") },
                placeholder = { Text(DEFAULT_LINE_COUNT.toString()) },
                supportingText = { Text(headTailSummary(text, count)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите строки") },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 6,
                maxLines = 10,
            )
            OutlinedTextField(
                value = parts.head,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Первые $count") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 4,
                maxLines = 8,
            )
            OutlinedTextField(
                value = parts.tail,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Последние $count") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 4,
                maxLines = 8,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { copyPart(parts.head, "Начало пустое") },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать начало")
                }
                FilledTonalButton(
                    onClick = { copyPart(parts.tail, "Конец пустой") },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать конец")
                }
            }
        }
    }
}

private data class HeadTail(val head: String, val tail: String)

/** Пустое поле — 10. Ноль оставляет оба результата пустыми. */
private fun parseLineCount(raw: String): Int {
    if (raw.isBlank()) return DEFAULT_LINE_COUNT
    return raw.toIntOrNull()?.coerceAtLeast(0) ?: DEFAULT_LINE_COUNT
}

private fun sanitizeLineCount(raw: String): String = raw.filter(Char::isDigit).take(6)

private fun headTailSummary(text: String, count: Int): String {
    if (text.isEmpty()) return "По умолчанию $DEFAULT_LINE_COUNT. 0 — пустой результат."
    val total = lineCount(text)
    return when {
        count <= 0 -> "Всего строк: $total. Число 0 — ничего не показано."
        total <= count -> "Всего строк: $total. Показан весь текст."
        total < count * 2 -> "Всего строк: $total. Первые $count и последние $count пересекаются."
        else -> "Всего строк: $total. Первые $count и последние $count."
    }
}

private fun lineCount(text: String): Int {
    if (text.isEmpty()) return 0
    val lines = ArrayList<String>()
    val separators = ArrayList<String>()
    collectLines(text, lines, separators)
    return lines.size
}

/** Если строк меньше N, начало и конец совпадают со всем текстом. */
private fun headAndTail(text: String, count: Int): HeadTail {
    if (text.isEmpty() || count <= 0) return HeadTail("", "")
    val lines = ArrayList<String>()
    val separators = ArrayList<String>()
    collectLines(text, lines, separators)
    if (lines.isEmpty()) return HeadTail("", "")
    val take = minOf(count, lines.size)
    val head = joinSlice(lines, separators, 0, take)
    val tailStart = maxOf(0, lines.size - take)
    val tail = joinSlice(lines, separators, tailStart, lines.size)
    return HeadTail(head, tail)
}

private fun joinSlice(
    lines: List<String>,
    separators: List<String>,
    from: Int,
    until: Int,
): String {
    val out = StringBuilder()
    for (index in from until until) {
        out.append(lines[index])
        out.append(separators[index])
    }
    return out.toString()
}

/** Завершающий перевод не добавляет пустую строку. `\n`, `\r\n` и `\r` остаются как были. */
private fun collectLines(text: String, lines: MutableList<String>, separators: MutableList<String>) {
    var index = 0
    while (index < text.length) {
        val found = text.indexOfAny(LINE_BREAKS, index)
        if (found < 0) {
            lines.add(text.substring(index))
            separators.add("")
            return
        }
        lines.add(text.substring(index, found))
        val separatorEnd = if (
            text[found] == '\r' &&
            found + 1 < text.length &&
            text[found + 1] == '\n'
        ) {
            found + 2
        } else {
            found + 1
        }
        separators.add(text.substring(found, separatorEnd))
        if (separatorEnd == text.length) return
        index = separatorEnd
    }
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Начало и конец", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
