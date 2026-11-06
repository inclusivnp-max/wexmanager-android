package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Разбор строк KEY=VALUE. Пустые строки и комментарии с # не попадают в таблицу. */
@Composable
fun T64EnvScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val rows = remember(text) { parseEnv(text) }
    val keys = remember(rows) { rows.joinToString("\n") { it.key } }

    fun copyText(value: String, empty: String, ok: String) {
        val copied = value.isNotEmpty() && copyPlain(context, value)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    value.isEmpty() -> empty
                    copied -> ok
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
                title = { Text("Переменные") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { copyText(keys, "Нечего копировать", "Ключи скопированы") },
                        enabled = rows.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, "Копировать ключи")
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Строки KEY=VALUE. Пустые строки и строки, начинающиеся с #, пропускаются. " +
                    "Значение берётся после первого знака равенства.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("KEY=value", fontFamily = FontFamily.Monospace) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 5,
                maxLines = 8,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    countLabel(rows.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = { copyText(keys, "Нечего копировать", "Ключи скопированы") },
                    enabled = rows.isNotEmpty(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать ключи")
                }
            }
            if (rows.isEmpty()) {
                Text(
                    if (text.isBlank()) "Вставьте переменные — здесь будет таблица" else "Нет переменных",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        item(key = "header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 48.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Ключ",
                                    modifier = Modifier.weight(0.4f),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurfaceVariant,
                                )
                                Text(
                                    "Значение",
                                    modifier = Modifier.weight(0.6f),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                        itemsIndexed(rows, key = { index, _ -> index }) { _, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    row.key,
                                    modifier = Modifier.weight(0.4f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    row.value.ifEmpty { "—" },
                                    modifier = Modifier.weight(0.6f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (row.value.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = { copyText(row.key, "Нечего копировать", "Ключ скопирован") },
                                    shapes = IconButtonDefaults.shapes(),
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, "Копировать ключ")
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private data class EnvPair(val key: String, val value: String)

/** Первое «=» делит ключ и значение. Пробелы по краям снимаются, кавычки вокруг значения снимаются. */
private fun parseEnv(text: String): List<EnvPair> {
    if (text.isEmpty()) return emptyList()
    val rows = ArrayList<EnvPair>()
    for (raw in text.lineSequence()) {
        val line = raw.trim()
        if (line.isEmpty() || line.startsWith("#")) continue
        val body = stripExport(line)
        val eq = body.indexOf('=')
        if (eq <= 0) continue
        val key = body.substring(0, eq).trim()
        if (key.isEmpty() || key.any { it.isWhitespace() }) continue
        val value = unquote(body.substring(eq + 1).trim())
        rows.add(EnvPair(key, value))
    }
    return rows
}

private fun stripExport(line: String): String {
    if (line.length > 7 && line.startsWith("export") && line[6].isWhitespace()) {
        return line.substring(7).trimStart()
    }
    return line
}

private fun unquote(value: String): String {
    if (value.length < 2) return value
    val quote = value.first()
    if ((quote == '"' || quote == '\'') && value.last() == quote) {
        return value.substring(1, value.lastIndex)
    }
    return value
}

private fun countLabel(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "переменных"
        count % 10 == 1 -> "переменная"
        count % 10 in 2..4 -> "переменные"
        else -> "переменных"
    }
    return "$count $word"
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Ключи", value))
    true
} catch (_: RuntimeException) {
    false
}
