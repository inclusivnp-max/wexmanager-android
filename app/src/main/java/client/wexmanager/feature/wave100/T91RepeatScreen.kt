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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val MaxTimes = 500
private const val MaxChars = 100_000

/** Строка повторяется N раз и склеивается разделителем. N ≤ 500, результат ≤ 100 000 символов. */
@Composable
fun T91RepeatScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var source by rememberSaveable { mutableStateOf("") }
    var timesText by rememberSaveable { mutableStateOf("3") }
    var separator by rememberSaveable { mutableStateOf(", ") }
    val times = timesText.toIntOrNull()
    val built = remember(source, separator, times) {
        if (times == null || times < 1) {
            RepeatedText("", 0, false)
        } else {
            repeatText(source, times, separator)
        }
    }
    val timesCapped = times != null && times > MaxTimes

    fun copyResult() {
        val copied = built.text.isNotEmpty() && copyPlain(context, built.text)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    times == null || times < 1 -> "Укажите число от 1 до $MaxTimes"
                    built.text.isEmpty() -> "Нечего копировать"
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
                title = { Text("Повтор строки") },
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
                "Строка повторяется указанное число раз и склеивается разделителем. Не больше $MaxTimes повторов и $MaxChars символов в результате.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Строка") },
                placeholder = { Text("Текст для повтора") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            OutlinedTextField(
                value = timesText,
                onValueChange = { timesText = it.filter(Char::isDigit).take(6) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Число повторов") },
                placeholder = { Text("От 1 до $MaxTimes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        if (timesCapped) "Не больше $MaxTimes, взято $MaxTimes" else "От 1 до $MaxTimes",
                    )
                },
            )
            OutlinedTextField(
                value = separator,
                onValueChange = { separator = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Разделитель") },
                placeholder = { Text("Например, «, »") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            OutlinedTextField(
                value = built.text,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Результат") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 4,
                maxLines = 10,
                supportingText = {
                    val note = when {
                        times == null || times < 1 -> "Укажите число от 1"
                        built.charLimited -> "Обрезано до $MaxChars символов · ${built.text.length}"
                        else -> "${built.text.length} символов · повторов ${built.timesUsed}"
                    }
                    Text(note)
                },
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

private data class RepeatedText(
    val text: String,
    val timesUsed: Int,
    val charLimited: Boolean,
)

/** `source` склеивается `times` раз через [separator]. Лишнее по длине отбрасывается. */
private fun repeatText(source: String, times: Int, separator: String): RepeatedText {
    if (times < 1) return RepeatedText("", 0, false)
    val n = times.coerceAtMost(MaxTimes)
    if (source.isEmpty() && separator.isEmpty()) return RepeatedText("", n, false)
    val out = StringBuilder()
    var charLimited = false
    for (i in 0 until n) {
        if (i > 0 && !appendWithin(out, separator)) {
            charLimited = true
            break
        }
        if (!appendWithin(out, source)) {
            charLimited = true
            break
        }
    }
    return RepeatedText(out.toString(), n, charLimited)
}

/** false, если [piece] не поместился целиком в оставшийся лимит. */
private fun appendWithin(out: StringBuilder, piece: String): Boolean {
    val room = MaxChars - out.length
    if (piece.length <= room) {
        out.append(piece)
        return true
    }
    if (room > 0) out.append(piece, 0, room)
    return false
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Повтор строки", value))
    true
} catch (_: RuntimeException) {
    false
}
