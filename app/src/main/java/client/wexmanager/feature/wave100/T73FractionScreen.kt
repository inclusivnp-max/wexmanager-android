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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

/** Числитель и знаменатель в десятичную дробь и в проценты. Ноль в знаменателе — ошибка. */
@Composable
fun T73FractionScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var numeratorText by rememberSaveable { mutableStateOf("") }
    var denominatorText by rememberSaveable { mutableStateOf("") }
    val numerator = parseFraction(numeratorText)
    val denominator = parseFraction(denominatorText)
    val zeroDenominator = denominator is FractionNumber.Ok &&
        denominator.value.compareTo(BigDecimal.ZERO) == 0
    val decimal = fractionDecimal(numerator, denominator)
    val percent = fractionPercent(numerator, denominator)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Дробь") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Числитель делится на знаменатель. Рядом — та же величина в процентах.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = numeratorText,
                onValueChange = { numeratorText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Числитель") },
                placeholder = { Text("1") },
                supportingText = { Text("Верхнее число дроби") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = numerator is FractionNumber.Invalid,
            )
            OutlinedTextField(
                value = denominatorText,
                onValueChange = { denominatorText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Знаменатель") },
                placeholder = { Text("4") },
                supportingText = {
                    Text(if (zeroDenominator) "Знаменатель не может быть нулём" else "Нижнее число дроби")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = denominator is FractionNumber.Invalid || zeroDenominator,
            )
            if (numerator is FractionNumber.Invalid || denominator is FractionNumber.Invalid) {
                Text(
                    "Нужно число. Допускаются запятая и точка.",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (zeroDenominator) {
                Text(
                    "Деление на ноль: укажите знаменатель, отличный от нуля.",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FractionResultCard(
                title = "Десятичная",
                value = decimal,
                hint = "числитель ÷ знаменатель",
            )
            FractionResultCard(
                title = "Процент",
                value = percent,
                hint = "числитель ÷ знаменатель × 100",
            )
        }
    }
}

@Composable
private fun FractionResultCard(title: String, value: FractionValue, hint: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Text(
                when (value) {
                    FractionValue.Empty -> "—"
                    FractionValue.ZeroDenominator -> "Деление на ноль"
                    is FractionValue.Ok -> value.text
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (value is FractionValue.ZeroDenominator) scheme.error else scheme.onSurface,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface FractionNumber {
    data object Empty : FractionNumber
    data object Invalid : FractionNumber
    data class Ok(val value: BigDecimal) : FractionNumber
}

private sealed interface FractionValue {
    data object Empty : FractionValue
    data object ZeroDenominator : FractionValue
    data class Ok(val text: String) : FractionValue
}

private fun fractionDecimal(numerator: FractionNumber, denominator: FractionNumber): FractionValue {
    val top = (numerator as? FractionNumber.Ok)?.value ?: return FractionValue.Empty
    val bottom = (denominator as? FractionNumber.Ok)?.value ?: return FractionValue.Empty
    if (bottom.compareTo(BigDecimal.ZERO) == 0) return FractionValue.ZeroDenominator
    val quotient = top.divide(bottom, SCALE, RoundingMode.HALF_UP)
    return FractionValue.Ok(formatFraction(quotient))
}

private fun fractionPercent(numerator: FractionNumber, denominator: FractionNumber): FractionValue {
    val top = (numerator as? FractionNumber.Ok)?.value ?: return FractionValue.Empty
    val bottom = (denominator as? FractionNumber.Ok)?.value ?: return FractionValue.Empty
    if (bottom.compareTo(BigDecimal.ZERO) == 0) return FractionValue.ZeroDenominator
    val percent = top.multiply(HUNDRED).divide(bottom, SCALE, RoundingMode.HALF_UP)
    return FractionValue.Ok("${formatFraction(percent)} %")
}

private fun parseFraction(raw: String): FractionNumber {
    val cleaned = raw.trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
        .replace(',', '.')
    if (cleaned.isEmpty()) return FractionNumber.Empty
    val body = cleaned.removePrefix("+")
    if (body.isEmpty() || body == "-" || body == "." || body == "-.") return FractionNumber.Empty
    val normalized = if (body.endsWith('.')) body.dropLast(1) else body
    if (normalized.count { it == '.' } > 1) return FractionNumber.Invalid
    return normalized.toBigDecimalOrNull()?.let { FractionNumber.Ok(it) } ?: FractionNumber.Invalid
}

private fun formatFraction(value: BigDecimal): String {
    val plain = value.stripTrailingZeros().toPlainString()
    val normalized = if (plain == "-0") "0" else plain
    return normalized.replace('.', ',')
}

private const val SCALE = 10
private val HUNDRED = BigDecimal(100)
