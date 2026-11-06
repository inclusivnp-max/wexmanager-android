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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Строки, которые встречаются больше одного раза, и сколько раз. */
@Composable
fun T13DupLinesScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val duplicates = remember(text) { duplicateLines(text) }

    fun copyDuplicates() {
        val payload = duplicates.joinToString("\n") { it.text }
        val copied = duplicates.isNotEmpty() && copyPlain(context, payload)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    duplicates.isEmpty() -> "Повторов нет"
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
                title = { Text("Повторы строк") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Строки, которые встречаются больше одного раза. Пустые тоже считаются, пробелы не обрезаются.",
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
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                minLines = 4,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    duplicateSummary(text, duplicates),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = ::copyDuplicates,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            if (duplicates.isEmpty()) {
                Text(
                    if (text.isEmpty()) "Вставьте текст, чтобы найти повторы." else "Одинаковых строк нет.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(duplicates, key = { it.text }) { line ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = scheme.surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (line.text.isEmpty()) "пустая строка" else line.text,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontFamily = if (line.text.isEmpty()) null else FontFamily.Monospace,
                                    fontStyle = if (line.text.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                                    color = if (line.text.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = scheme.secondaryContainer,
                                ) {
                                    Text(
                                        plural(line.count, "раз", "раза", "раз"),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = scheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DupLine(val text: String, val count: Int)

private fun duplicateLines(text: String): List<DupLine> {
    val counts = LinkedHashMap<String, Int>()
    for (line in fieldLines(text)) {
        counts[line] = (counts[line] ?: 0) + 1
    }
    return counts.mapNotNull { (line, count) ->
        if (count > 1) DupLine(line, count) else null
    }
}

private fun duplicateSummary(text: String, duplicates: List<DupLine>): String {
    if (text.isEmpty()) return "Нет текста"
    if (duplicates.isEmpty()) return "Повторов нет"
    val hits = duplicates.sumOf { it.count }
    return "${plural(duplicates.size, "строка", "строки", "строк")} · ${plural(hits, "вхождение", "вхождения", "вхождений")}"
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
 * Строки как в поле: завершающий перевод даёт ещё одну пустую строку.
 * Пара «\r\n» — один разделитель.
 */
private fun fieldLines(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val lines = ArrayList<String>()
    var start = 0
    var index = 0
    while (index < text.length) {
        val ch = text[index]
        if (ch != '\n' && ch != '\r') {
            index++
            continue
        }
        lines.add(text.substring(start, index))
        if (ch == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
        index++
        start = index
        if (start == text.length) lines.add("")
    }
    if (start < text.length) lines.add(text.substring(start))
    return lines
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Повторы строк", value))
    true
} catch (_: RuntimeException) {
    false
}
