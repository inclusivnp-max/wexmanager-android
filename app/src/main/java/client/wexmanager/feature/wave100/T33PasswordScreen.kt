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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import java.security.SecureRandom
import kotlinx.coroutines.launch

private const val MinLength = 8
private const val MaxLength = 64
private const val Letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
private const val Digits = "0123456789"
private const val Symbols = "!@#\$%^&*()-_=+[]{}.,?"

private val passwordRandom = SecureRandom()

/** Случайный пароль на экране. В хранилище приложения не пишется. */
@Composable
fun T33PasswordScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var length by remember { mutableFloatStateOf(16f) }
    var digits by remember { mutableStateOf(true) }
    var symbols by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    val count = length.toInt().coerceIn(MinLength, MaxLength)

    fun copy() {
        val copied = password.isNotEmpty() && copyPlain(context, password)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    password.isEmpty() -> "Нечего копировать"
                    copied -> "Скопировано"
                    else -> "Не удалось скопировать"
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Пароль") },
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Длина от 8 до 64. Буквы всегда есть, цифры и знаки — по переключателям. Строка остаётся только на экране и копируется в буфер. Приложение её не сохраняет.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Длина",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.primary,
                )
            }
            Slider(
                value = length,
                onValueChange = { length = it },
                valueRange = MinLength.toFloat()..MaxLength.toFloat(),
                steps = MaxLength - MinLength - 1,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Цифры",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = digits, onCheckedChange = { digits = it })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Символы",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = symbols, onCheckedChange = { symbols = it })
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { password = generatePassword(count, digits, symbols) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Сгенерировать") }
                FilledTonalButton(
                    onClick = { copy() },
                    enabled = password.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Копировать",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            Text(
                text = password.ifEmpty { "Пароль появится здесь" },
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                color = if (password.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
            )
        }
    }
}

private fun generatePassword(length: Int, digits: Boolean, symbols: Boolean): String {
    val size = length.coerceIn(MinLength, MaxLength)
    val alphabet = buildString {
        append(Letters)
        if (digits) append(Digits)
        if (symbols) append(Symbols)
    }
    val chars = CharArray(size)
    var index = 0
    if (digits && index < size) chars[index++] = Digits.randomChar()
    if (symbols && index < size) chars[index++] = Symbols.randomChar()
    while (index < size) chars[index++] = alphabet.randomChar()
    shuffle(chars)
    return chars.concatToString()
}

private fun String.randomChar(): Char = this[passwordRandom.nextInt(length)]

private fun shuffle(chars: CharArray) {
    for (i in chars.lastIndex downTo 1) {
        val j = passwordRandom.nextInt(i + 1)
        val tmp = chars[i]
        chars[i] = chars[j]
        chars[j] = tmp
    }
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText("Пароль", value))
    true
} catch (_: RuntimeException) {
    false
}
