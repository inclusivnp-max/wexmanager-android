package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigInteger
import java.util.Locale

private const val MaxDigits = 512
private val QuickBases = listOf(2, 8, 10, 16, 36)

private val ShownBases = listOf(
    2 to "Двоичное",
    8 to "Восьмеричное",
    10 to "Десятичное",
    16 to "Шестнадцатеричное",
)

/** Целое между основаниями 2–36. Рядом двоичная, восьмеричная, десятичная и шестнадцатеричная запись. */
@Composable
fun T40BaseConvertScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var numberText by rememberSaveable { mutableStateOf("") }
    var fromText by rememberSaveable { mutableStateOf("10") }
    var toText by rememberSaveable { mutableStateOf("16") }
    val fromRadix = resolveRadix(fromText)
    val toRadix = resolveRadix(toText)
    val trimmed = numberText.trim()
    val value = if (fromRadix != null && trimmed.isNotEmpty()) parseInteger(trimmed, fromRadix) else null
    val numberInvalid = fromRadix != null && trimmed.isNotEmpty() && value == null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Системы счисления") },
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
                "Целое число в основании от 2 до 36. Буквы A–Z — цифры 10–35.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = numberText,
                onValueChange = { numberText = it.take(MaxDigits) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = numberInvalid,
                label = { Text("Число") },
                placeholder = { Text("255", fontFamily = FontFamily.Monospace) },
                supportingText = {
                    Text(
                        when {
                            numberInvalid -> "В этом основании нет такой цифры"
                            fromRadix != null -> "Допустимы ${digitSpan(fromRadix)}"
                            else -> "Сначала укажите исходное основание"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                "Из основания",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickBases.forEach { base ->
                    FilterChip(
                        selected = fromRadix == base,
                        onClick = { fromText = base.toString() },
                        label = { Text(base.toString()) },
                    )
                }
            }
            Text(
                "В основание",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickBases.forEach { base ->
                    FilterChip(
                        selected = toRadix == base,
                        onClick = { toText = base.toString() },
                        label = { Text(base.toString()) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadixField(
                    value = fromText,
                    onValueChange = { fromText = it },
                    label = "Из",
                    modifier = Modifier.weight(1f),
                )
                RadixField(
                    value = toText,
                    onValueChange = { toText = it },
                    label = "В",
                    modifier = Modifier.weight(1f),
                )
            }
            BaseValue(
                title = if (toRadix == null) "Результат" else "Результат · основание $toRadix",
                value = if (value != null && toRadix != null) render(value, toRadix) else "—",
            )
            ShownBases.forEach { (radix, title) ->
                BaseValue(
                    title = "$title ($radix)",
                    value = if (value != null) render(value, radix) else "—",
                )
            }
        }
    }
}

@Composable
private fun RadixField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() }.take(2)) },
        modifier = modifier,
        singleLine = true,
        isError = radixIsError(value),
        label = { Text(label) },
        supportingText = { Text(if (radixIsError(value)) "От 2 до 36" else "2–36") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun BaseValue(title: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun resolveRadix(raw: String): Int? {
    val text = raw.trim()
    if (text.isEmpty() || text.any { it !in '0'..'9' }) return null
    return text.toIntOrNull()?.takeIf { it in 2..36 }
}

/** «1» и «3» ещё могут стать 10–36, поэтому ошибка только у явно неверного основания. */
private fun radixIsError(raw: String): Boolean {
    val text = raw.trim()
    if (text.isEmpty() || resolveRadix(text) != null) return false
    return !(text.length == 1 && text[0] in '0'..'3')
}

private fun digitSpan(radix: Int): String = when {
    radix <= 10 -> "0–${'0' + radix - 1}"
    else -> "0–9 и A–${'A' + (radix - 11)}"
}

private fun isBaseDigit(c: Char, radix: Int): Boolean {
    if (Character.digit(c, radix) < 0) return false
    return c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z'
}

private fun parseInteger(raw: String, radix: Int): BigInteger? {
    var start = 0
    if (raw[0] == '+' || raw[0] == '-') {
        if (raw.length == 1) return null
        start = 1
    }
    for (i in start until raw.length) {
        if (!isBaseDigit(raw[i], radix)) return null
    }
    return try {
        BigInteger(raw, radix)
    } catch (_: NumberFormatException) {
        null
    }
}

private fun render(value: BigInteger, radix: Int): String =
    value.toString(radix).uppercase(Locale.ROOT)
