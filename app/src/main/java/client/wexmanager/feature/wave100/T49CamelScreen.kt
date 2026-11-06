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
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch

/** Слова из пробелов, «_» и «-» собираются в camelCase и PascalCase. Каждая строка отдельно. */
@Composable
fun T49CamelScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val forms = remember(text) { toCaseForms(text) }

    fun copyResult(label: String, value: String) {
        val copied = value.isNotEmpty() && copyPlain(context, label, value)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    value.isEmpty() -> "Нечего копировать"
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
                title = { Text("camelCase") },
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
                "Пробелы, _ и - делят слова. Границы уже написанного camelCase тоже. Каждая строка даёт обе формы, переносы сохраняются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Слова") },
                placeholder = { Text("hello world, hello_world или hello-world") },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 4,
                maxLines = 8,
            )
            OutlinedTextField(
                value = forms.camel,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("camelCase") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 6,
            )
            FilledTonalButton(
                onClick = { copyResult("camelCase", forms.camel) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать camelCase")
            }
            OutlinedTextField(
                value = forms.pascal,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("PascalCase") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 6,
            )
            FilledTonalButton(
                onClick = { copyResult("PascalCase", forms.pascal) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать PascalCase")
            }
        }
    }
}

private data class CaseForms(val camel: String, val pascal: String)

/** Locale.ROOT: регистр идентификатора не зависит от языка устройства. */
private fun toCaseForms(text: String): CaseForms {
    if (text.isEmpty()) return CaseForms("", "")
    val camel = StringBuilder(text.length)
    val pascal = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val found = text.indexOfAny(LINE_BREAKS, index)
        val breakAt = if (found < 0) text.length else found
        val forms = convertLine(text.substring(index, breakAt))
        camel.append(forms.camel)
        pascal.append(forms.pascal)
        if (breakAt >= text.length) break
        val separatorEnd = if (
            text[breakAt] == '\r' &&
            breakAt + 1 < text.length &&
            text[breakAt + 1] == '\n'
        ) {
            breakAt + 2
        } else {
            breakAt + 1
        }
        camel.append(text, breakAt, separatorEnd)
        pascal.append(text, breakAt, separatorEnd)
        index = separatorEnd
    }
    return CaseForms(camel.toString(), pascal.toString())
}

private fun convertLine(line: String): CaseForms {
    val words = splitWords(line)
    if (words.isEmpty()) return CaseForms("", "")
    val camel = StringBuilder(line.length)
    val pascal = StringBuilder(line.length)
    for (i in words.indices) {
        val lower = words[i].lowercase(Locale.ROOT)
        val titled = capitalizeFirst(lower)
        if (i == 0) camel.append(lower) else camel.append(titled)
        pascal.append(titled)
    }
    return CaseForms(camel.toString(), pascal.toString())
}

/** Пробел, «_», «-» и стык строчная/цифра→прописная. Серия прописных рвётся перед строчной: XMLParser. */
private fun splitWords(line: String): List<String> {
    val words = ArrayList<String>()
    val current = StringBuilder()
    var index = 0
    while (index < line.length) {
        val codePoint = line.codePointAt(index)
        if (isSeparator(codePoint)) {
            flushWord(words, current)
        } else {
            if (current.isNotEmpty() && startsNewWord(line, index, codePoint)) {
                flushWord(words, current)
            }
            current.appendCodePoint(codePoint)
        }
        index += Character.charCount(codePoint)
    }
    flushWord(words, current)
    return words
}

private fun startsNewWord(line: String, index: Int, codePoint: Int): Boolean {
    if (!Character.isUpperCase(codePoint)) return false
    val previous = line.codePointBefore(index)
    if (Character.isLowerCase(previous) || Character.isDigit(previous)) return true
    if (!Character.isUpperCase(previous)) return false
    val nextIndex = index + Character.charCount(codePoint)
    if (nextIndex >= line.length) return false
    return Character.isLowerCase(line.codePointAt(nextIndex))
}

private fun isSeparator(codePoint: Int): Boolean {
    return codePoint == '_'.code || codePoint == '-'.code || Character.isWhitespace(codePoint)
}

private fun flushWord(words: MutableList<String>, current: StringBuilder) {
    if (current.isEmpty()) return
    words.add(current.toString())
    current.setLength(0)
}

private fun capitalizeFirst(lower: String): String {
    if (lower.isEmpty()) return lower
    val firstEnd = lower.offsetByCodePoints(0, 1)
    return lower.substring(0, firstEnd).uppercase(Locale.ROOT) + lower.substring(firstEnd)
}

private fun copyPlain(context: Context, label: String, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
