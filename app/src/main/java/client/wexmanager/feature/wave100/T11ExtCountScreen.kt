package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.util.Locale

/** Список имён, по одному в строке, и число файлов на каждое расширение. */
@Composable
fun T11ExtCountScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val counts = remember(text) { countExtensions(text) }
    val total = counts.sumOf { it.count }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Расширения") },
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Имена файлов") },
                placeholder = { Text("По одному имени в строке") },
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
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                minLines = 6,
            )
            if (counts.isEmpty()) {
                Text(
                    if (text.isBlank()) {
                        "Вставьте имена файлов — здесь будет число файлов каждого расширения"
                    } else {
                        "Нет имён файлов"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Text(
                    "${formatCount(total)} ${ruPlural(total, "файл", "файла", "файлов")}, " +
                        "${formatCount(counts.size)} ${ruPlural(counts.size, "группа", "группы", "групп")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(counts, key = { it.extension }) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                extLabel(row.extension),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                formatCount(row.count),
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

private data class ExtCount(val extension: String, val count: Int)

/** Суффикс после последней точки. Точка только в начале или в конце — без расширения. */
private fun countExtensions(raw: String): List<ExtCount> {
    val counts = HashMap<String, Int>()
    for (line in raw.lineSequence()) {
        val name = fileName(line) ?: continue
        val extension = extensionOf(name)
        counts[extension] = (counts[extension] ?: 0) + 1
    }
    return counts
        .map { ExtCount(it.key, it.value) }
        .sortedWith(
            compareByDescending<ExtCount> { it.count }
                .thenBy { it.extension.isEmpty() }
                .thenBy { it.extension },
        )
}

private fun fileName(line: String): String? {
    var trimmed = line.trim()
    if (trimmed.length >= 2) {
        val quote = trimmed.first()
        if ((quote == '"' || quote == '\'') && trimmed.last() == quote) {
            trimmed = trimmed.substring(1, trimmed.lastIndex).trim()
        }
    }
    val cleaned = trimmed.trimEnd('/', '\\').trimEnd('.')
    if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return null
    val slash = maxOf(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'))
    val name = if (slash >= 0) cleaned.substring(slash + 1) else cleaned
    return name.takeIf { it.isNotEmpty() && it != "." && it != ".." }
}

private fun extensionOf(name: String): String {
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return ""
    return name.substring(dot + 1).lowercase(Locale.ROOT)
}

private fun extLabel(extension: String): String =
    if (extension.isEmpty()) "без расширения" else ".$extension"

private fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
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
