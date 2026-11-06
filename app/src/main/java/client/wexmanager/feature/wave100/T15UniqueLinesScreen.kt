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

/** Оставляет первое вхождение каждой строки и считает отброшенные повторы. */
@Composable
fun T15UniqueLinesScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val unique = remember(text) { uniqueLines(text) }

    fun copyResult() {
        val copied = unique.text.isNotEmpty() && copyPlain(context, unique.text)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    unique.text.isEmpty() -> "Нечего копировать"
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
                title = { Text("Уникальные строки") },
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
                "Повторяющиеся строки отбрасываются. Остаётся первое вхождение, порядок не меняется.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите строки") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                minLines = 6,
                maxLines = 10,
            )
            Text(
                removedLabel(unique.removed),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = unique.text,
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

private data class UniqueLines(val text: String, val removed: Int)

private fun uniqueLines(source: String): UniqueLines {
    if (source.isEmpty()) return UniqueLines("", 0)
    val trailingBreak = source.last() == '\n' || source.last() == '\r'
    val seen = HashSet<String>()
    val kept = ArrayList<String>()
    var removed = 0
    for (line in splitLines(source)) {
        if (seen.add(line)) kept.add(line) else removed++
    }
    return UniqueLines(joinLines(kept, trailingBreak), removed)
}

/** Строки без завершающего перевода. Пустая строка в конце из-за перевода не считается отдельной. */
private fun splitLines(source: String): List<String> {
    val out = ArrayList<String>()
    var start = 0
    var index = 0
    while (index < source.length) {
        val ch = source[index]
        if (ch != '\n' && ch != '\r') {
            index++
            continue
        }
        out.add(source.substring(start, index))
        index += if (ch == '\r' && index + 1 < source.length && source[index + 1] == '\n') 2 else 1
        start = index
        if (index == source.length) return out
    }
    out.add(source.substring(start))
    return out
}

private fun joinLines(lines: List<String>, trailingBreak: Boolean): String {
    if (lines.isEmpty()) return ""
    val body = lines.joinToString("\n")
    val keepBreak = trailingBreak || lines.last().isEmpty()
    return if (keepBreak) "$body\n" else body
}

private fun removedLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "дубликатов"
        mod10 == 1 -> "дубликат"
        mod10 in 2..4 -> "дубликата"
        else -> "дубликатов"
    }
    val verb = if (mod100 !in 11..14 && mod10 == 1) "Удалён" else "Удалено"
    return "$verb $count $word"
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Уникальные строки", value))
    true
} catch (_: RuntimeException) {
    false
}
