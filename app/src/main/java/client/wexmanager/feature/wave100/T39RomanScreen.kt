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

private const val ARABIC_HINT = "От 1 до 3999"
private const val ROMAN_HINT = "I, V, X, L, C, D, M"
private const val NOT_INTEGER = "Введите целое число"
private const val OUT_OF_RANGE = "Число должно быть от 1 до 3999"
private const val BAD_ROMAN = "Некорректная римская запись"

private val ROMAN_PARTS = listOf(
    1000 to "M",
    900 to "CM",
    500 to "D",
    400 to "CD",
    100 to "C",
    90 to "XC",
    50 to "L",
    40 to "XL",
    10 to "X",
    9 to "IX",
    5 to "V",
    4 to "IV",
    1 to "I",
)

/** Целое от 1 до 3999 в римскую запись и обратно. */
@Composable
fun T39RomanScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var arabic by rememberSaveable { mutableStateOf("") }
    var roman by rememberSaveable { mutableStateOf("") }
    var arabicError by rememberSaveable { mutableStateOf<String?>(null) }
    var romanError by rememberSaveable { mutableStateOf<String?>(null) }

    fun onArabicChange(value: String) {
        arabic = value
        romanError = null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            arabicError = null
            roman = ""
            return
        }
        val error = arabicErrorOf(trimmed)
        arabicError = error
        roman = if (error == null) toRoman(trimmed.toInt()) else ""
    }

    fun onRomanChange(value: String) {
        roman = value
        arabicError = null
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            romanError = null
            arabic = ""
            return
        }
        val parsed = parseRoman(trimmed)
        if (parsed == null) {
            romanError = BAD_ROMAN
            arabic = ""
        } else {
            romanError = null
            arabic = parsed.toString()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Римские числа") },
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
                "Целое от 1 до 3999 и римская запись. Перевод сразу в обе стороны.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = arabic,
                onValueChange = ::onArabicChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Число") },
                placeholder = { Text("1994") },
                supportingText = { Text(arabicError ?: ARABIC_HINT) },
                isError = arabicError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = roman,
                onValueChange = ::onRomanChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Римская запись") },
                placeholder = { Text("MCMXCIV") },
                supportingText = { Text(romanError ?: ROMAN_HINT) },
                isError = romanError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

private fun arabicErrorOf(text: String): String? {
    if (text.any { !it.isDigit() }) return NOT_INTEGER
    val value = text.toIntOrNull() ?: return OUT_OF_RANGE
    if (value !in 1..3999) return OUT_OF_RANGE
    return null
}

private fun toRoman(value: Int): String {
    val out = StringBuilder()
    var rest = value
    for ((amount, symbol) in ROMAN_PARTS) {
        while (rest >= amount) {
            out.append(symbol)
            rest -= amount
        }
    }
    return out.toString()
}

/** Каноническая запись: повторный перевод должен совпасть с исходной строкой. */
private fun parseRoman(raw: String): Int? {
    val text = raw.trim().uppercase()
    if (text.isEmpty() || text.any { it !in "IVXLCDM" }) return null
    var index = 0
    var total = 0
    for ((amount, symbol) in ROMAN_PARTS) {
        while (text.startsWith(symbol, index)) {
            total += amount
            index += symbol.length
            if (total > 3999) return null
        }
    }
    if (index != text.length || total !in 1..3999) return null
    if (toRoman(total) != text) return null
    return total
}
