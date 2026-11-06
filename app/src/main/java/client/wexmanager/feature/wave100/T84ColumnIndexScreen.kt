package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val NumberHint = "1 — A, 26 — Z, 27 — AA"
private const val LetterHint = "A, Z, AA"
private const val NotInteger = "Введите целое число"
private const val NotPositive = "Номер столбца начинается с 1"
private const val TooBig = "Слишком большое число"
private const val BadLetters = "Только латинские буквы A–Z"
private const val TooLong = "Слишком длинное обозначение"

/** Номер столбца как в таблице: 1 — A, 27 — AA, и буквы обратно в номер. */
@Composable
fun T84ColumnIndexScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var numberText by rememberSaveable { mutableStateOf("") }
    var letterText by rememberSaveable { mutableStateOf("") }
    var numberError by rememberSaveable { mutableStateOf<String?>(null) }
    var letterError by rememberSaveable { mutableStateOf<String?>(null) }

    fun onNumberChange(value: String) {
        numberText = value.take(24)
        letterError = null
        val trimmed = numberText.trim()
        if (trimmed.isEmpty()) {
            numberError = null
            letterText = ""
            return
        }
        val error = numberErrorOf(trimmed)
        numberError = error
        letterText = if (error == null) indexToLetters(trimmed.toLong()) else ""
    }

    fun onLetterChange(value: String) {
        letterText = value.take(32)
        numberError = null
        val trimmed = letterText.trim()
        if (trimmed.isEmpty()) {
            letterError = null
            numberText = ""
            return
        }
        val error = letterErrorOf(trimmed)
        letterError = error
        val index = if (error == null) lettersToIndex(trimmed) else null
        numberText = index?.toString().orEmpty()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Индекс столбца") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Номер столбца и буквенное обозначение. 1 — A, 27 — AA. Перевод сразу в обе стороны.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = numberText,
                onValueChange = ::onNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Номер") },
                placeholder = { Text("27") },
                supportingText = { Text(numberError ?: NumberHint) },
                isError = numberError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedTextField(
                value = letterText,
                onValueChange = ::onLetterChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Буквы") },
                placeholder = { Text("AA") },
                supportingText = { Text(letterError ?: LetterHint) },
                isError = letterError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

private fun numberErrorOf(text: String): String? {
    if (text.any { !it.isDigit() }) return NotInteger
    val value = text.toLongOrNull() ?: return TooBig
    if (value < 1L) return NotPositive
    return null
}

private fun letterErrorOf(text: String): String? {
    if (text.any { it !in 'A'..'Z' && it !in 'a'..'z' }) return BadLetters
    if (lettersToIndex(text) == null) return TooLong
    return null
}

/** 1 → A, 26 → Z, 27 → AA. Номер уже проверен и больше нуля. */
private fun indexToLetters(value: Long): String {
    val out = StringBuilder()
    var rest = value
    while (rest > 0L) {
        rest -= 1L
        out.append('A' + (rest % 26L).toInt())
        rest /= 26L
    }
    return out.reverse().toString()
}

/** A → 1, AA → 27. null, если буква не из A–Z или номер не помещается в Long. */
private fun lettersToIndex(text: String): Long? {
    if (text.isEmpty()) return null
    var value = 0L
    for (ch in text) {
        val upper = when (ch) {
            in 'A'..'Z' -> ch
            in 'a'..'z' -> ch.uppercaseChar()
            else -> return null
        }
        val digit = (upper.code - 'A'.code + 1).toLong()
        if (value > (Long.MAX_VALUE - digit) / 26L) return null
        value = value * 26L + digit
    }
    return value.takeIf { it >= 1L }
}
