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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

/** Процентное изменение от исходного числа к новому: (до − от) ÷ от × 100. */
@Composable
fun T72PercentChangeScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var fromText by rememberSaveable { mutableStateOf("") }
    var toText by rememberSaveable { mutableStateOf("") }
    val from = remember(fromText) { parseChangeAmount(fromText) }
    val to = remember(toText) { parseChangeAmount(toText) }
    val result = remember(from, to) { percentChange(from, to) }
    val fromIsZero = result is ChangeResult.ZeroBase

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Изменение в процентах") },
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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Два числа: исходное и новое. Плюс — рост, минус — падение. Запятая и точка — десятичный разделитель.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = fromText,
                onValueChange = { fromText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("От") },
                placeholder = { Text("100") },
                supportingText = {
                    Text(
                        if (fromIsZero) "Исходное число не может быть нулём" else "Исходное число",
                    )
                },
                isError = from is ChangeAmount.Invalid || fromIsZero,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = toText,
                onValueChange = { toText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("До") },
                placeholder = { Text("125") },
                supportingText = { Text("Новое число") },
                isError = to is ChangeAmount.Invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            if (from is ChangeAmount.Invalid || to is ChangeAmount.Invalid) {
                Text(
                    "Нужно число. Допускаются запятая и точка.",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (fromIsZero) {
                Text(
                    "Нельзя посчитать изменение: исходное число равно нулю.",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
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
                        "Изменение",
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        when (result) {
                            ChangeResult.Empty -> "—"
                            ChangeResult.Invalid -> "—"
                            ChangeResult.ZeroBase -> "Ошибка"
                            is ChangeResult.Ok -> result.text
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (fromIsZero) scheme.error else scheme.onSurface,
                    )
                    Text(
                        "(до − от) ÷ от × 100",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private sealed interface ChangeAmount {
    data object Empty : ChangeAmount
    data object Invalid : ChangeAmount
    data class Ok(val value: BigDecimal) : ChangeAmount
}

private sealed interface ChangeResult {
    data object Empty : ChangeResult
    data object Invalid : ChangeResult
    data object ZeroBase : ChangeResult
    data class Ok(val text: String) : ChangeResult
}

private fun percentChange(from: ChangeAmount, to: ChangeAmount): ChangeResult {
    val fromValue = (from as? ChangeAmount.Ok)?.value
    if (fromValue != null && fromValue.compareTo(BigDecimal.ZERO) == 0) return ChangeResult.ZeroBase
    if (from is ChangeAmount.Invalid || to is ChangeAmount.Invalid) return ChangeResult.Invalid
    if (fromValue == null) return ChangeResult.Empty
    val toValue = (to as? ChangeAmount.Ok)?.value ?: return ChangeResult.Empty
    val percent = toValue.subtract(fromValue)
        .multiply(HUNDRED)
        .divide(fromValue, SCALE, RoundingMode.HALF_UP)
    return ChangeResult.Ok(formatChange(percent))
}

private fun parseChangeAmount(raw: String): ChangeAmount {
    val cleaned = raw.trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
        .replace(',', '.')
    if (cleaned.isEmpty()) return ChangeAmount.Empty
    val body = cleaned.removePrefix("+")
    if (body.isEmpty() || body == "-" || body == "." || body == "-.") return ChangeAmount.Empty
    val normalized = if (body.endsWith('.')) body.dropLast(1) else body
    if (normalized.count { it == '.' } > 1) return ChangeAmount.Invalid
    return normalized.toBigDecimalOrNull()?.let { ChangeAmount.Ok(it) } ?: ChangeAmount.Invalid
}

private fun formatChange(value: BigDecimal): String {
    val plain = value.stripTrailingZeros().toPlainString()
    val normalized = if (plain == "-0") "0" else plain
    val signed = if (normalized.startsWith("-") || normalized == "0") normalized else "+$normalized"
    return signed.replace('.', ',') + " %"
}

private const val SCALE = 10
private val HUNDRED = BigDecimal(100)
