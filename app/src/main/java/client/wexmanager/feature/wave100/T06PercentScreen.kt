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
import androidx.compose.material.icons.rounded.Percent
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

@Composable
fun T06PercentScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var partText by rememberSaveable { mutableStateOf("") }
    var wholeText by rememberSaveable { mutableStateOf("") }
    val part = parseAmount(partText)
    val whole = parseAmount(wholeText)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Проценты") },
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
                "Часть и целое дают процент. Обратный расчёт берёт часть как процент и находит эту долю от числа.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = partText,
                onValueChange = { partText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Часть") },
                placeholder = { Text("25") },
                supportingText = { Text("Доля от целого или процент в обратном расчёте") },
                leadingIcon = { Icon(Icons.Rounded.Percent, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = part is Amount.Invalid,
            )
            OutlinedTextField(
                value = wholeText,
                onValueChange = { wholeText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Целое") },
                placeholder = { Text("200") },
                supportingText = { Text("Число, от которого считается процент") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = whole is Amount.Invalid,
            )
            if (part is Amount.Invalid || whole is Amount.Invalid) {
                Text(
                    "Нужно число. Допускаются запятая и точка.",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ResultCard(
                title = "Процент",
                value = percentOfWhole(part, whole),
                hint = "часть ÷ целое × 100",
            )
            ResultCard(
                title = "Процент от числа",
                value = percentOfNumber(part, whole),
                hint = "целое × часть ÷ 100",
            )
        }
    }
}

@Composable
private fun ResultCard(title: String, value: PercentValue, hint: String) {
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
                    PercentValue.Empty -> "—"
                    PercentValue.DivideByZero -> "Деление на ноль"
                    is PercentValue.Ok -> value.text
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                color = if (value is PercentValue.DivideByZero) scheme.error else scheme.onSurface,
            )
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface Amount {
    data object Empty : Amount
    data object Invalid : Amount
    data class Ok(val value: BigDecimal) : Amount
}

private sealed interface PercentValue {
    data object Empty : PercentValue
    data object DivideByZero : PercentValue
    data class Ok(val text: String) : PercentValue
}

private fun percentOfWhole(part: Amount, whole: Amount): PercentValue {
    val partValue = (part as? Amount.Ok)?.value ?: return PercentValue.Empty
    val wholeValue = (whole as? Amount.Ok)?.value ?: return PercentValue.Empty
    if (wholeValue.compareTo(BigDecimal.ZERO) == 0) return PercentValue.DivideByZero
    val percent = partValue.multiply(HUNDRED).divide(wholeValue, SCALE, RoundingMode.HALF_UP)
    return PercentValue.Ok("${formatDecimal(percent)} %")
}

private fun percentOfNumber(part: Amount, whole: Amount): PercentValue {
    val percent = (part as? Amount.Ok)?.value ?: return PercentValue.Empty
    val number = (whole as? Amount.Ok)?.value ?: return PercentValue.Empty
    val result = number.multiply(percent).divide(HUNDRED, SCALE, RoundingMode.HALF_UP)
    return PercentValue.Ok(formatDecimal(result))
}

private fun parseAmount(raw: String): Amount {
    val cleaned = raw.trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
        .replace(',', '.')
    if (cleaned.isEmpty()) return Amount.Empty
    val body = cleaned.removePrefix("+")
    if (body.isEmpty() || body == "-" || body == "." || body == "-.") return Amount.Empty
    val normalized = if (body.endsWith('.')) body.dropLast(1) else body
    if (normalized.count { it == '.' } > 1) return Amount.Invalid
    return normalized.toBigDecimalOrNull()?.let { Amount.Ok(it) } ?: Amount.Invalid
}

private fun formatDecimal(value: BigDecimal): String {
    val plain = value.stripTrailingZeros().toPlainString()
    val normalized = if (plain == "-0") "0" else plain
    return normalized.replace('.', ',')
}

private const val SCALE = 10
private val HUNDRED = BigDecimal(100)
