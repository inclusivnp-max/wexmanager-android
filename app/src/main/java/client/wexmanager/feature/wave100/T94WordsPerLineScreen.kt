package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Число слов в каждой строке и сумма по всему тексту. */
@Composable
fun T94WordsPerLineScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val rows = remember(text) { wordsPerLine(text) }
    val total = remember(rows) { rows.sumOf { it.words } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Слова по строкам") },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Слово — последовательность символов без пробелов. Пустая строка даёт ноль. Внизу сумма по всем строкам.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите текст") },
                trailingIcon = if (text.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { text = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, "Очистить")
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    autoCorrectEnabled = false,
                ),
                minLines = 6,
            )
            if (rows.isEmpty()) {
                Text(
                    "Вставьте текст — здесь будет число слов в каждой строке",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            formatCount(total),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Всего ${ruPlural(total, "слово", "слова", "слов")} " +
                                "в ${formatCount(rows.size)} ${ruPlural(rows.size, "строке", "строках", "строках")}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                formatCount(index + 1),
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                row.preview.ifEmpty { "пустая строка" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontStyle = if (row.preview.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                                color = if (row.preview.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatCount(row.words),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private data class LineWords(val words: Int, val preview: String)

/**
 * Завершающий перевод строки остаётся пустой строкой: в поле она видна.
 * `\n`, `\r\n` и `\r` считаются границей.
 */
private fun wordsPerLine(text: String): List<LineWords> {
    if (text.isEmpty()) return emptyList()
    val rows = ArrayList<LineWords>()
    var index = 0
    while (index <= text.length) {
        val found = if (index == text.length) -1 else text.indexOfAny(LINE_BREAKS, index)
        if (found < 0) {
            val line = text.substring(index)
            rows.add(LineWords(countWords(line), compactPreview(line)))
            return rows
        }
        val line = text.substring(index, found)
        rows.add(LineWords(countWords(line), compactPreview(line)))
        val next = if (
            text[found] == '\r' &&
            found + 1 < text.length &&
            text[found + 1] == '\n'
        ) {
            found + 2
        } else {
            found + 1
        }
        if (next > text.length) return rows
        if (next == text.length) {
            rows.add(LineWords(0, ""))
            return rows
        }
        index = next
    }
    return rows
}

private fun countWords(line: String): Int {
    var count = 0
    var inWord = false
    for (ch in line) {
        if (ch.isWhitespace()) {
            inWord = false
        } else if (!inWord) {
            inWord = true
            count++
        }
    }
    return count
}

/** Пробелы схлопываются. Строка из одних пробелов остаётся пустой для подписи. */
private fun compactPreview(line: String): String = buildString {
    var pendingSpace = false
    for (ch in line) {
        if (ch.isWhitespace()) {
            pendingSpace = true
        } else {
            if (pendingSpace && isNotEmpty()) append(' ')
            pendingSpace = false
            append(ch)
        }
    }
}

private fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val abs = kotlin.math.abs(n)
    val mod100 = abs % 100
    val mod10 = abs % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

private fun formatCount(value: Int): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return digits.reversed().chunked(3).joinToString("\u00A0").reversed()
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
