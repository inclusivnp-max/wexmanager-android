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

/** Интерпретация \n, \t, \r, \\ и \u005CuXXXX. Неверная последовательность — ошибка, не обрезанный текст. */
@Composable
fun T99EscapesScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var source by rememberSaveable { mutableStateOf("") }
    val parsed = remember(source) { interpretEscapes(source) }

    fun copyResult() {
        val value = parsed.text
        val copied = value != null && value.isNotEmpty() && copyPlain(context, value)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    value.isNullOrEmpty() -> "Нечего копировать"
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
                title = { Text("Escape") },
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
                "Распознаёт \\n, \\t, \\r, \\\\ и \\" + "uXXXX (четыре шестнадцатеричные цифры). " +
                    "Оборванная или неизвестная последовательность — ошибка, результат не обрезается.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Строка") },
                placeholder = { Text("hello\\nworld \\" + "u0416", fontFamily = FontFamily.Monospace) },
                isError = parsed.error != null,
                supportingText = {
                    Text(parsed.error ?: "\\n  \\t  \\r  \\\\  \\" + "uXXXX")
                },
                minLines = 4,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            OutlinedTextField(
                value = parsed.text.orEmpty(),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Результат") },
                minLines = 4,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            FilledTonalButton(
                onClick = { copyResult() },
                enabled = !parsed.text.isNullOrEmpty(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать")
            }
        }
    }
}

private data class Interpreted(val text: String?, val error: String?)

private fun interpretEscapes(source: String): Interpreted {
    val out = StringBuilder(source.length)
    var index = 0
    while (index < source.length) {
        if (source[index] != '\\') {
            out.append(source[index])
            index++
            continue
        }
        if (index + 1 >= source.length) return Interpreted(null, errorAt(source, index))
        when (source[index + 1]) {
            'n' -> {
                out.append('\n')
                index += 2
            }
            't' -> {
                out.append('\t')
                index += 2
            }
            'r' -> {
                out.append('\r')
                index += 2
            }
            '\\' -> {
                out.append('\\')
                index += 2
            }
            'u' -> {
                if (index + 6 > source.length || !isHex4(source, index + 2)) {
                    return Interpreted(null, errorAt(source, index))
                }
                out.append(source.substring(index + 2, index + 6).toInt(16).toChar())
                index += 6
            }
            else -> return Interpreted(null, errorAt(source, index))
        }
    }
    return Interpreted(out.toString(), null)
}

private fun isHex4(source: String, start: Int): Boolean {
    for (offset in start until start + 4) {
        if (!isHex(source[offset])) return false
    }
    return true
}

private fun isHex(c: Char): Boolean = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

/** «\», «\x» или «\u005Cu» и до четырёх знаков следом. */
private fun errorAt(source: String, index: Int): String {
    val token = when {
        index + 1 >= source.length -> "\\"
        source[index + 1] != 'u' -> source.substring(index, index + 2)
        else -> source.substring(index, minOf(source.length, index + 6))
    }
    return "Неверная последовательность «$token»"
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Escape", value))
    true
} catch (_: RuntimeException) {
    false
}
