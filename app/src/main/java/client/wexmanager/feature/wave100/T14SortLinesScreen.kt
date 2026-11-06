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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.launch

/** Сортировка строк: А–Я или Я–А, без регистра и с числовым порядком. */
@Composable
fun T14SortLinesScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var ascending by rememberSaveable { mutableStateOf(true) }
    var ignoreCase by rememberSaveable { mutableStateOf(false) }
    var numeric by rememberSaveable { mutableStateOf(false) }
    val result = remember(text, ascending, ignoreCase, numeric) {
        sortLines(text, ascending, ignoreCase, numeric)
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
                title = { Text("Сортировка строк") },
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
                "Порядок А–Я или Я–А. «Без регистра» не различает прописные и строчные. «Числа» ставит 2 перед 10.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите строки") },
                trailingIcon = if (text.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { text = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, "Очистить")
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                minLines = 6,
                maxLines = 10,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ascending,
                    onClick = { ascending = true },
                    label = { Text("А–Я") },
                )
                FilterChip(
                    selected = !ascending,
                    onClick = { ascending = false },
                    label = { Text("Я–А") },
                )
                FilterChip(
                    selected = ignoreCase,
                    onClick = { ignoreCase = !ignoreCase },
                    label = { Text("Без регистра") },
                )
                FilterChip(
                    selected = numeric,
                    onClick = { numeric = !numeric },
                    label = { Text("Числа") },
                )
            }
            OutlinedTextField(
                value = result,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Результат") },
                textStyle = MaterialTheme.typography.bodyLarge,
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

private fun sortLines(
    text: String,
    ascending: Boolean,
    ignoreCase: Boolean,
    numeric: Boolean,
): String {
    if (text.isEmpty()) return ""
    val order = collator(ignoreCase)
    val comparator = Comparator<String> { left, right ->
        if (numeric) compareNatural(left, right, order) else order.compare(left, right)
    }
    val directed = if (ascending) comparator else comparator.reversed()
    val newline = newlineOf(text)
    val trailing = text.endsWith("\n") || text.endsWith("\r")
    val body = splitLines(text).sortedWith(directed).joinToString(newline)
    return if (trailing) body + newline else body
}

private fun collator(ignoreCase: Boolean): Collator =
    Collator.getInstance(Locale.getDefault()).apply {
        strength = if (ignoreCase) Collator.PRIMARY else Collator.TERTIARY
    }

/** Числовые куски сравниваются по значению, остальные — коллатором. Ведущие нули не меняют значение. */
private fun compareNatural(left: String, right: String, order: Collator): Int {
    var i = 0
    var j = 0
    while (i < left.length && j < right.length) {
        val leftDigit = left[i].isAsciiDigit()
        val rightDigit = right[j].isAsciiDigit()
        when {
            leftDigit && rightDigit -> {
                val leftStart = i
                while (i < left.length && left[i].isAsciiDigit()) i++
                val rightStart = j
                while (j < right.length && right[j].isAsciiDigit()) j++
                val byValue = compareDigitRuns(left, leftStart, i, right, rightStart, j)
                if (byValue != 0) return byValue
            }
            !leftDigit && !rightDigit -> {
                val leftStart = i
                while (i < left.length && !left[i].isAsciiDigit()) i++
                val rightStart = j
                while (j < right.length && !right[j].isAsciiDigit()) j++
                val byText = order.compare(left.substring(leftStart, i), right.substring(rightStart, j))
                if (byText != 0) return byText
            }
            else -> {
                val byChar = order.compare(left.substring(i, i + 1), right.substring(j, j + 1))
                if (byChar != 0) return byChar
                i++
                j++
            }
        }
    }
    return (left.length - i).compareTo(right.length - j)
}

private fun compareDigitRuns(
    left: String,
    leftStart: Int,
    leftEnd: Int,
    right: String,
    rightStart: Int,
    rightEnd: Int,
): Int {
    var i = leftStart
    var j = rightStart
    while (i < leftEnd && left[i] == '0') i++
    while (j < rightEnd && right[j] == '0') j++
    val leftLength = leftEnd - i
    val rightLength = rightEnd - j
    if (leftLength != rightLength) return leftLength.compareTo(rightLength)
    while (i < leftEnd) {
        val byDigit = left[i].compareTo(right[j])
        if (byDigit != 0) return byDigit
        i++
        j++
    }
    return (leftEnd - leftStart).compareTo(rightEnd - rightStart)
}

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun splitLines(text: String): List<String> {
    val lines = ArrayList<String>()
    var index = 0
    while (index < text.length) {
        val found = text.indexOfAny(LINE_BREAKS, index)
        if (found < 0) {
            lines.add(text.substring(index))
            break
        }
        lines.add(text.substring(index, found))
        index = if (
            text[found] == '\r' &&
            found + 1 < text.length &&
            text[found + 1] == '\n'
        ) {
            found + 2
        } else {
            found + 1
        }
    }
    return lines
}

private fun newlineOf(text: String): String = when {
    text.contains("\r\n") -> "\r\n"
    text.contains('\r') -> "\r"
    else -> "\n"
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Сортировка строк", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
