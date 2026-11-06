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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

private enum class TempScale { Celsius, Fahrenheit, Kelvin }

/** Цельсий, Фаренгейт и Кельвин: правка одного поля пересчитывает два других. */
@Composable
fun T79TempScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var celsius by rememberSaveable { mutableStateOf("0") }
    var fahrenheit by rememberSaveable { mutableStateOf("32") }
    var kelvin by rememberSaveable { mutableStateOf("273,15") }

    fun edit(scale: TempScale, raw: String) {
        val clipped = raw.take(MAX_TEMP_CHARS)
        when (scale) {
            TempScale.Celsius -> celsius = clipped
            TempScale.Fahrenheit -> fahrenheit = clipped
            TempScale.Kelvin -> kelvin = clipped
        }
        when (val parsed = parseTemp(clipped)) {
            TempNumber.Empty -> {
                if (scale != TempScale.Celsius) celsius = ""
                if (scale != TempScale.Fahrenheit) fahrenheit = ""
                if (scale != TempScale.Kelvin) kelvin = ""
            }
            TempNumber.Partial, TempNumber.Invalid -> Unit
            is TempNumber.Ok -> {
                val c = toCelsius(scale, parsed.value)
                if (scale != TempScale.Celsius) celsius = formatTemp(c)
                if (scale != TempScale.Fahrenheit) fahrenheit = formatTemp(celsiusToFahrenheit(c))
                if (scale != TempScale.Kelvin) kelvin = formatTemp(celsiusToKelvin(c))
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Температура") },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Введите значение в любой шкале — две другие пересчитаются сразу. Допускаются запятая и точка.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            TempField(
                value = celsius,
                onValueChange = { edit(TempScale.Celsius, it) },
                label = "Цельсий",
                suffix = "°C",
                hint = "Вода замерзает при 0 °C",
                invalid = parseTemp(celsius) is TempNumber.Invalid,
            )
            TempField(
                value = fahrenheit,
                onValueChange = { edit(TempScale.Fahrenheit, it) },
                label = "Фаренгейт",
                suffix = "°F",
                hint = "Вода замерзает при 32 °F",
                invalid = parseTemp(fahrenheit) is TempNumber.Invalid,
            )
            TempField(
                value = kelvin,
                onValueChange = { edit(TempScale.Kelvin, it) },
                label = "Кельвин",
                suffix = "K",
                hint = kelvinHint(kelvin),
                invalid = parseTemp(kelvin) is TempNumber.Invalid,
            )
        }
    }
}

@Composable
private fun TempField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String,
    hint: String,
    invalid: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = invalid,
        label = { Text(label) },
        suffix = { Text(suffix) },
        placeholder = { Text("0", fontFamily = FontFamily.Monospace) },
        supportingText = {
            Text(if (invalid) "Нужно число" else hint)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    )
}

private sealed interface TempNumber {
    data object Empty : TempNumber
    data object Partial : TempNumber
    data object Invalid : TempNumber
    data class Ok(val value: BigDecimal) : TempNumber
}

private fun kelvinHint(raw: String): String {
    val parsed = parseTemp(raw)
    if (parsed is TempNumber.Ok && parsed.value < BigDecimal.ZERO) {
        return "Ниже абсолютного нуля"
    }
    return "0 K — абсолютный ноль"
}

private fun parseTemp(raw: String): TempNumber {
    val cleaned = raw.trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
        .replace(',', '.')
    if (cleaned.isEmpty()) return TempNumber.Empty
    val body = cleaned.removePrefix("+")
    if (body.isEmpty() || body == "-" || body == "." || body == "-." || body.endsWith('.')) {
        return TempNumber.Partial
    }
    if (body.count { it == '.' } > 1) return TempNumber.Invalid
    return body.toBigDecimalOrNull()?.let { TempNumber.Ok(it) } ?: TempNumber.Invalid
}

private fun toCelsius(scale: TempScale, value: BigDecimal): BigDecimal = when (scale) {
    TempScale.Celsius -> value
    TempScale.Fahrenheit -> value.subtract(THIRTY_TWO).multiply(FIVE).divide(NINE, SCALE, RoundingMode.HALF_UP)
    TempScale.Kelvin -> value.subtract(KELVIN_OFFSET)
}

private fun celsiusToFahrenheit(celsius: BigDecimal): BigDecimal =
    celsius.multiply(NINE).divide(FIVE, SCALE, RoundingMode.HALF_UP).add(THIRTY_TWO)

private fun celsiusToKelvin(celsius: BigDecimal): BigDecimal = celsius.add(KELVIN_OFFSET)

private fun formatTemp(value: BigDecimal): String {
    val plain = value.setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    val normalized = if (plain == "-0") "0" else plain
    return normalized.replace('.', ',')
}

private const val MAX_TEMP_CHARS = 40
private const val SCALE = 8
private val FIVE = BigDecimal(5)
private val NINE = BigDecimal(9)
private val THIRTY_TWO = BigDecimal(32)
private val KELVIN_OFFSET = BigDecimal("273.15")
