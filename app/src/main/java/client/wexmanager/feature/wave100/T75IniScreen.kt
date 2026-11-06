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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class IniEntry(
    val section: String,
    val key: String,
    val value: String,
)

/** Разбор INI: секции [имя] и пары ключ=значение. Строки-комментарии с ; и # пропускаются. */
@Composable
fun T75IniScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val entries = remember(text) { parseIni(text) }

    fun copyEntries() {
        val payload = entries.joinToString("\n") { entry ->
            val section = entry.section.ifEmpty { "без секции" }
            "$section\t${entry.key}\t${entry.value}"
        }
        val copied = entries.isNotEmpty() && copyPlain(context, payload)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    entries.isEmpty() -> "Нечего копировать"
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
                title = { Text("Разбор INI") },
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
                "Секции в квадратных скобках и пары ключ=значение. Строки, которые начинаются с ; или #, пропускаются. Комментарий в конце значения тоже отбрасывается, если перед ; или # есть пробел.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Текст INI") },
                placeholder = { Text("[окно]\nширина=800\n; комментарий") },
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
                    when {
                        text.isEmpty() -> "Записей нет"
                        entries.isEmpty() -> "Пар ключ=значение нет"
                        else -> "Записей: ${entries.size}"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = ::copyEntries,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            if (entries.isEmpty()) {
                Text(
                    if (text.isEmpty()) {
                        "Вставьте INI, чтобы увидеть секцию, ключ и значение."
                    } else {
                        "Подходящих строк нет. Нужны [секция] и ключ=значение."
                    },
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
                    itemsIndexed(entries, key = { index, entry -> "$index\u0000${entry.section}\u0000${entry.key}" }) { _, entry ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = scheme.surfaceContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                IniField("Секция", entry.section.ifEmpty { "без секции" })
                                IniField("Ключ", entry.key)
                                IniField("Значение", entry.value)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IniField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            label,
            modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Clip,
        )
    }
}

/** [section] меняет текущую секцию. key=value попадает в список. ; и # в начале строки — комментарий. */
private fun parseIni(text: String): List<IniEntry> {
    if (text.isEmpty()) return emptyList()
    val entries = ArrayList<IniEntry>()
    var section = ""
    var index = 0
    val source = if (text[0] == '\uFEFF') text.substring(1) else text
    val length = source.length
    while (index < length) {
        var end = index
        while (end < length && source[end] != '\n' && source[end] != '\r') end++
        val line = source.substring(index, end).trim()
        when {
            line.isEmpty() || line[0] == ';' || line[0] == '#' -> Unit
            line[0] == '[' -> {
                val close = line.indexOf(']')
                if (close > 1) section = line.substring(1, close).trim()
            }
            else -> {
                val eq = line.indexOf('=')
                if (eq > 0) {
                    val key = line.substring(0, eq).trim()
                    if (key.isNotEmpty() && key[0] != ';' && key[0] != '#') {
                        val rawValue = line.substring(eq + 1).trim()
                        entries.add(IniEntry(section, key, stripInlineComment(rawValue)))
                    }
                }
            }
        }
        index = when {
            end >= length -> length
            source[end] == '\r' && end + 1 < length && source[end + 1] == '\n' -> end + 2
            else -> end + 1
        }
    }
    return entries
}

/** Комментарий с ; или # отрезается, только если перед ним пробел или он занимает всё значение. */
private fun stripInlineComment(value: String): String {
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if ((ch == ';' || ch == '#') && (index == 0 || value[index - 1].isWhitespace())) {
            return value.substring(0, index).trimEnd()
        }
        index++
    }
    return value
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("INI", value))
    true
} catch (_: RuntimeException) {
    false
}
