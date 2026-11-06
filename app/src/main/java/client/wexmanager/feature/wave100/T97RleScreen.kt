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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val BAD_PLAIN = "Допускаются только латинские буквы"
private const val BAD_RLE = "Некорректная RLE-запись"

/** RLE для одиночных латинских букв: aaabbc → a3b2c1 и обратно. */
@Composable
fun T97RleScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var encoded by rememberSaveable { mutableStateOf("") }
    var textError by rememberSaveable { mutableStateOf<String?>(null) }
    var encodedError by rememberSaveable { mutableStateOf<String?>(null) }

    fun copy(label: String, value: String) {
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
                title = { Text("RLE") },
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
                "Сжатие повторов латинских букв: aaabbc становится a3b2c1. " +
                    "Регистр сохраняется, серия из одной буквы тоже пишется с числом.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    textError = null
                    encodedError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("aaabbc") },
                isError = textError != null,
                supportingText = textError?.let { message -> { Text(message) } },
                minLines = 4,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        try {
                            encoded = encodeRle(text)
                            textError = null
                            encodedError = null
                        } catch (e: IllegalArgumentException) {
                            textError = e.message ?: BAD_PLAIN
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Закодировать") }
                FilledTonalButton(
                    onClick = { copy("Текст", text) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            OutlinedTextField(
                value = encoded,
                onValueChange = {
                    encoded = it
                    textError = null
                    encodedError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("RLE") },
                placeholder = { Text("a3b2c1") },
                isError = encodedError != null,
                supportingText = encodedError?.let { message -> { Text(message) } },
                minLines = 4,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        try {
                            text = decodeRle(encoded)
                            textError = null
                            encodedError = null
                        } catch (e: IllegalArgumentException) {
                            encodedError = e.message ?: BAD_RLE
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Раскодировать") }
                FilledTonalButton(
                    onClick = { copy("RLE", encoded) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
        }
    }
}

/** Серии одинаковых латинских букв: буква и длина без ведущих нулей. */
private fun encodeRle(text: String): String {
    if (text.any { !it.isLatinLetter() }) throw IllegalArgumentException(BAD_PLAIN)
    if (text.isEmpty()) return ""
    val out = StringBuilder()
    var index = 0
    while (index < text.length) {
        val letter = text[index]
        var end = index + 1
        while (end < text.length && text[end] == letter) end++
        out.append(letter).append(end - index)
        index = end
    }
    return out.toString()
}

/** Форма буква + положительное число без ведущего нуля. Иные символы — ошибка. */
private fun decodeRle(encoded: String): String {
    if (encoded.isEmpty()) return ""
    val out = StringBuilder()
    var index = 0
    while (index < encoded.length) {
        val letter = encoded[index]
        if (!letter.isLatinLetter()) throw IllegalArgumentException(BAD_RLE)
        index++
        if (index >= encoded.length || encoded[index] !in '1'..'9') {
            throw IllegalArgumentException(BAD_RLE)
        }
        val start = index
        index++
        while (index < encoded.length && encoded[index].isDigit()) index++
        val count = encoded.substring(start, index).toIntOrNull() ?: throw IllegalArgumentException(BAD_RLE)
        if (count < 1 || out.length > Int.MAX_VALUE - count) throw IllegalArgumentException(BAD_RLE)
        repeat(count) { out.append(letter) }
    }
    return out.toString()
}

private fun Char.isLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun copyPlain(context: Context, label: String, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
    true
} catch (_: RuntimeException) {
    false
}
