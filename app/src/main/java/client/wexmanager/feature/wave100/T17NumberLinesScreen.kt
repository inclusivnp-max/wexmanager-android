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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val MAX_PAD_WIDTH = 8

/** Номер с единицы в начале каждой строки. Ширина необязательна: нули слева, не больше 8. */
@Composable
fun T17NumberLinesScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var padText by rememberSaveable { mutableStateOf("") }
    val padWidth = parsePadWidth(padText)
    val result = remember(text, padWidth) { numberLines(text, padWidth) }

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
                title = { Text("Нумерация строк") },
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
                "Каждая строка получает номер с единицы. Ширина необязательна: если указать её, номер дополняется нулями слева. Переводы строк сохраняются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = padText,
                onValueChange = { padText = sanitizePadWidth(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ширина номера") },
                placeholder = { Text("3") },
                supportingText = { Text("Необязательно. 3 превращает 1 в 001. Пусто или 0 — без нулей.") },
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

/** Пусто и 0 — без дополнения. Значение больше 8 ограничивается полем ввода. */
private fun parsePadWidth(raw: String): Int? {
    val value = raw.toIntOrNull() ?: return null
    if (value <= 0) return null
    return value
}

private fun sanitizePadWidth(raw: String): String {
    val digits = raw.filter(Char::isDigit).take(2)
    val value = digits.toIntOrNull() ?: return ""
    if (value > MAX_PAD_WIDTH) return MAX_PAD_WIDTH.toString()
    return if (value == 0) "0" else digits.trimStart('0')
}

/** Завершающий перевод не добавляет пустую строку. `\n`, `\r\n` и `\r` остаются как были. */
private fun numberLines(text: String, padWidth: Int?): String {
    if (text.isEmpty()) return ""
    val lines = ArrayList<String>()
    val separators = ArrayList<String>()
    collectLines(text, lines, separators)
    if (lines.isEmpty()) return ""
    val width = padWidth?.takeIf { it > 0 }
    val out = StringBuilder(text.length + lines.size * ((width ?: 4) + 1))
    for (index in lines.indices) {
        val label = (index + 1).toString()
        out.append(if (width == null) label else label.padStart(width, '0'))
        out.append(' ')
        out.append(lines[index])
        out.append(separators[index])
    }
    return out.toString()
}

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
    manager.setPrimaryClip(ClipData.newPlainText("Нумерация строк", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
