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

/** Имя в snake_case и SCREAMING_SNAKE: CamelCase, пробелы и дефисы схлопываются в _. */
@Composable
fun T48SnakeScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var name by rememberSaveable { mutableStateOf("") }
    val snake = remember(name) { toSnakeCase(name) }
    val screaming = remember(snake) { toScreamingSnake(snake) }

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
                title = { Text("Snake-регистр") },
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
                "CamelCase, пробелы, дефисы и точки становятся подчёркиваниями. Аббревиатуры не рвутся: HTTPResponse → http_response.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имя") },
                placeholder = { Text("Например, T48SnakeScreen") },
                textStyle = MaterialTheme.typography.bodyLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            OutlinedTextField(
                value = snake,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("snake_case") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 4,
            )
            FilledTonalButton(
                onClick = { copyResult("snake_case", snake) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать snake_case")
            }
            OutlinedTextField(
                value = screaming,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("SCREAMING_SNAKE") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 4,
            )
            FilledTonalButton(
                onClick = { copyResult("SCREAMING_SNAKE", screaming) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать SCREAMING_SNAKE")
            }
        }
    }
}

private enum class NameChunk { Start, Lower, Upper, Digit, Sep }

/** Граница перед заглавной: после строчной или цифры, и внутри аббревиатуры перед строчной. */
private fun toSnakeCase(name: String): String {
    if (name.isEmpty()) return ""
    val out = StringBuilder(name.length + 8)
    var prev = NameChunk.Start
    var index = 0
    while (index < name.length) {
        val codePoint = name.codePointAt(index)
        val count = Character.charCount(codePoint)
        val next = if (index + count < name.length) name.codePointAt(index + count) else -1
        val chunk = chunkOf(codePoint)
        when (chunk) {
            NameChunk.Sep -> {
                if (out.isNotEmpty() && out.last() != '_') out.append('_')
            }
            NameChunk.Upper -> {
                val splitAcronym = prev == NameChunk.Upper && next >= 0 && isLowerLetter(next)
                val boundary = prev == NameChunk.Lower || prev == NameChunk.Digit || splitAcronym
                if (boundary && out.isNotEmpty() && out.last() != '_') out.append('_')
                out.appendCodePoint(Character.toLowerCase(codePoint))
            }
            NameChunk.Lower, NameChunk.Digit -> out.appendCodePoint(Character.toLowerCase(codePoint))
            NameChunk.Start -> Unit
        }
        prev = chunk
        index += count
    }
    var end = out.length
    while (end > 0 && out[end - 1] == '_') end--
    out.setLength(end)
    return out.toString()
}

private fun toScreamingSnake(snake: String): String {
    if (snake.isEmpty()) return ""
    val out = StringBuilder(snake.length)
    var index = 0
    while (index < snake.length) {
        val codePoint = snake.codePointAt(index)
        out.appendCodePoint(Character.toUpperCase(codePoint))
        index += Character.charCount(codePoint)
    }
    return out.toString()
}

private fun chunkOf(codePoint: Int): NameChunk = when {
    codePoint == '_'.code ||
        codePoint == '-'.code ||
        codePoint == '.'.code ||
        codePoint == '/'.code ||
        codePoint == '\\'.code ||
        Character.isWhitespace(codePoint) -> NameChunk.Sep
    Character.isUpperCase(codePoint) || Character.isTitleCase(codePoint) -> NameChunk.Upper
    Character.isDigit(codePoint) -> NameChunk.Digit
    Character.isLetter(codePoint) -> NameChunk.Lower
    else -> NameChunk.Sep
}

private fun isLowerLetter(codePoint: Int): Boolean =
    Character.isLetter(codePoint) &&
        !Character.isUpperCase(codePoint) &&
        !Character.isTitleCase(codePoint)

private fun copyPlain(context: Context, label: String, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
    true
} catch (_: RuntimeException) {
    false
}
