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

/** Ищет простые адреса вида имя@домен.зона. Повторы без учёта регистра отбрасываются. Сеть не используется. */
@Composable
fun T56EmailsScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val emails = remember(text) { uniqueEmails(text) }
    val result = remember(emails) { emails.joinToString("\n") }

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
                title = { Text("Адреса почты") },
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
                "Простые адреса из текста: имя@домен.зона. Каждый уникальный — с новой строки. Порядок первого вхождения сохраняется.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите текст") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                minLines = 6,
                maxLines = 10,
            )
            Text(
                foundLabel(text, emails.size),
                style = MaterialTheme.typography.titleMedium,
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

private fun uniqueEmails(source: String): List<String> {
    if (source.isEmpty()) return emptyList()
    val seen = HashSet<String>()
    val out = ArrayList<String>()
    var index = 0
    while (index < source.length) {
        if (source[index] != '@') {
            index++
            continue
        }
        val start = localStart(source, index)
        val end = if (start < 0) -1 else domainEnd(source, index + 1)
        if (start >= 0 && end > index + 1) {
            val token = source.substring(start, end)
            if (seen.add(token.lowercase())) out.add(token)
            index = end
        } else {
            index++
        }
    }
    return out
}

/** Слева от «@»: буквы, цифры и . _ % + -. Начинается и кончается буквой или цифрой, без «..». */
private fun localStart(text: String, at: Int): Int {
    if (at == 0 || !isAlnum(text[at - 1])) return -1
    var index = at - 1
    while (index >= 0 && isLocalChar(text[index])) index--
    var start = index + 1
    while (start < at && !isAlnum(text[start])) start++
    val length = at - start
    if (length !in 1..64) return -1
    for (cursor in start until at - 1) {
        if (text[cursor] == '.' && text[cursor + 1] == '.') return -1
    }
    return start
}

/**
 * Справа от «@»: минимум две метки через точку.
 * Последняя — только буквы, от 2 до 24. Метка не начинается и не кончается дефисом.
 */
private fun domainEnd(text: String, start: Int): Int {
    var index = start
    var labels = 0
    var lastLetters = false
    var lastLength = 0
    if (start >= text.length || !isAlnum(text[start])) return -1
    while (index < text.length) {
        if (!isAlnum(text[index])) return -1
        val labelStart = index
        index++
        while (index < text.length && (isAlnum(text[index]) || text[index] == '-')) index++
        if (text[index - 1] == '-') return -1
        val length = index - labelStart
        if (length !in 1..63) return -1
        labels++
        lastLength = length
        lastLetters = true
        for (cursor in labelStart until index) {
            if (!isLetter(text[cursor])) {
                lastLetters = false
                break
            }
        }
        if (index < text.length && text[index] == '.') {
            if (index + 1 >= text.length || !isAlnum(text[index + 1])) break
            index++
            continue
        }
        break
    }
    if (labels < 2 || !lastLetters || lastLength !in 2..24) return -1
    return index
}

private fun isLocalChar(char: Char): Boolean =
    isAlnum(char) || char == '.' || char == '_' || char == '%' || char == '+' || char == '-'

private fun isAlnum(char: Char): Boolean =
    char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9'

private fun isLetter(char: Char): Boolean = char in 'A'..'Z' || char in 'a'..'z'

private fun foundLabel(source: String, count: Int): String {
    if (source.isEmpty()) return "Нет текста"
    if (count == 0) return "Адресов нет"
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "адресов"
        mod10 == 1 -> "адрес"
        mod10 in 2..4 -> "адреса"
        else -> "адресов"
    }
    return "Найдено $count $word"
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Адреса почты", value))
    true
} catch (_: RuntimeException) {
    false
}
