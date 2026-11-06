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

private val TO_MORSE: Map<Char, String> = mapOf(
    'A' to ".-",
    'B' to "-...",
    'C' to "-.-.",
    'D' to "-..",
    'E' to ".",
    'F' to "..-.",
    'G' to "--.",
    'H' to "....",
    'I' to "..",
    'J' to ".---",
    'K' to "-.-",
    'L' to ".-..",
    'M' to "--",
    'N' to "-.",
    'O' to "---",
    'P' to ".--.",
    'Q' to "--.-",
    'R' to ".-.",
    'S' to "...",
    'T' to "-",
    'U' to "..-",
    'V' to "...-",
    'W' to ".--",
    'X' to "-..-",
    'Y' to "-.--",
    'Z' to "--..",
    '0' to "-----",
    '1' to ".----",
    '2' to "..---",
    '3' to "...--",
    '4' to "....-",
    '5' to ".....",
    '6' to "-....",
    '7' to "--...",
    '8' to "---..",
    '9' to "----.",
)

private val FROM_MORSE: Map<String, Char> = TO_MORSE.entries.associate { (letter, code) -> code to letter }

/** Латинские буквы и цифры в азбуку Морзе ITU и обратно. Неизвестный символ — «?». */
@Composable
fun T83MorseScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var morse by rememberSaveable { mutableStateOf("") }

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
                title = { Text("Морзе") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Латинские буквы и цифры в азбуку Морзе ITU. Буквы через пробел, слова через « / ». Неизвестные символы становятся «?».",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("SOS 123") },
                minLines = 4,
                maxLines = 8,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { morse = encodeMorse(text) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Закодировать") }
                FilledTonalButton(
                    onClick = { copy("Текст", text) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            OutlinedTextField(
                value = morse,
                onValueChange = { morse = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Морзе") },
                placeholder = { Text("... --- ... / .---- ..--- ...--") },
                minLines = 4,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { text = decodeMorse(morse) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Раскодировать") }
                FilledTonalButton(
                    onClick = { copy("Морзе", morse) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
        }
    }
}

/** Буквы одного слова через пробел, слова через « / ». Регистр не важен. */
private fun encodeMorse(text: String): String {
    val out = StringBuilder()
    var needLetterGap = false
    var needWordGap = false
    for (ch in text) {
        if (ch.isWhitespace()) {
            if (needLetterGap) {
                needWordGap = true
                needLetterGap = false
            }
            continue
        }
        if (needWordGap) {
            out.append(" / ")
            needWordGap = false
        } else if (needLetterGap) {
            out.append(' ')
        }
        out.append(TO_MORSE[ch.uppercaseChar()] ?: "?")
        needLetterGap = true
    }
    return out.toString()
}

/** Токены из точек и тире. « / » и два пробела подряд разделяют слова. Иной токен — «?». */
private fun decodeMorse(raw: String): String {
    val text = raw.trim()
    if (text.isEmpty()) return ""
    return text.split(Regex("""\s*/\s*|\s{2,}"""))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.split(Regex("""\s+"""))
                .filter { it.isNotEmpty() }
                .joinToString("") { token -> (FROM_MORSE[token] ?: '?').toString() }
        }
}

private fun copyPlain(context: Context, label: String, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
    true
} catch (_: RuntimeException) {
    false
}
