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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.launch

/** px = dp × плотность, dp = px ÷ плотность. Пустая плотность — LocalDensity экрана. */
@Composable
fun T60DpPxScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val screenFactor = remember(density.density) { decimalOf(density.density) }
    var valueText by rememberSaveable { mutableStateOf("") }
    var densityText by rememberSaveable { mutableStateOf(formatDecimal(screenFactor)) }
    val value = parsePxAmount(valueText)
    val typedDensity = parseDensity(densityText)
    val factor = when (typedDensity) {
        PxAmount.Empty -> screenFactor
        is PxAmount.Ok -> typedDensity.value
        PxAmount.Invalid -> null
    }
    val pxText = if (value is PxAmount.Ok && factor != null) toPx(value.value, factor) else null
    val dpText = if (value is PxAmount.Ok && factor != null) toDp(value.value, factor) else null

    fun copyNumber(number: String?) {
        val copied = number != null && copyPlain(context, number)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    number == null -> "Нечего копировать"
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
                title = { Text("px и dp") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Одно число переводится в обе стороны. Пиксели — значение, умноженное на плотность. " +
                    "dp — значение, делённое на плотность. На этом экране ${formatDecimal(screenFactor)} px на 1 dp.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = valueText,
                onValueChange = { valueText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Значение") },
                placeholder = { Text("48") },
                supportingText = {
                    Text(
                        if (value is PxAmount.Invalid) {
                            "Нужно число. Допускаются запятая и точка."
                        } else {
                            "Считается и как dp, и как px"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = value is PxAmount.Invalid,
            )
            OutlinedTextField(
                value = densityText,
                onValueChange = { densityText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Плотность") },
                placeholder = { Text(formatDecimal(screenFactor)) },
                supportingText = {
                    Text(
                        if (typedDensity is PxAmount.Invalid) {
                            "Плотность должна быть числом больше нуля."
                        } else {
                            "Пикселей на 1 dp. Пустое поле — плотность экрана."
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = typedDensity is PxAmount.Invalid,
            )
            FilledTonalButton(
                onClick = { densityText = formatDecimal(screenFactor) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Плотность экрана")
            }
            NumberCard(
                title = "Пиксели",
                value = pxText ?: EMPTY,
                hint = "значение × плотность",
                onCopy = { copyNumber(pxText) },
            )
            NumberCard(
                title = "dp",
                value = dpText ?: EMPTY,
                hint = "значение ÷ плотность",
                onCopy = { copyNumber(dpText) },
            )
        }
    }
}

@Composable
private fun NumberCard(title: String, value: String, hint: String, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
                FilledTonalButton(
                    onClick = onCopy,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface PxAmount {
    data object Empty : PxAmount
    data object Invalid : PxAmount
    data class Ok(val value: BigDecimal) : PxAmount
}

private fun toPx(value: BigDecimal, density: BigDecimal): String =
    formatDecimal(value.multiply(density))

private fun toDp(value: BigDecimal, density: BigDecimal): String =
    formatDecimal(value.divide(density, SCALE, RoundingMode.HALF_UP))

private fun decimalOf(density: Float): BigDecimal = BigDecimal(density.toString())

private fun parseDensity(raw: String): PxAmount {
    val parsed = parsePxAmount(raw)
    if (parsed is PxAmount.Ok && parsed.value.compareTo(BigDecimal.ZERO) <= 0) return PxAmount.Invalid
    return parsed
}

private fun parsePxAmount(raw: String): PxAmount {
    val cleaned = raw.trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
        .replace(',', '.')
    if (cleaned.isEmpty()) return PxAmount.Empty
    val body = cleaned.removePrefix("+")
    if (body.isEmpty() || body == "-" || body == "." || body == "-.") return PxAmount.Empty
    val normalized = if (body.endsWith('.')) body.dropLast(1) else body
    if (normalized.count { it == '.' } > 1) return PxAmount.Invalid
    return normalized.toBigDecimalOrNull()?.let { PxAmount.Ok(it) } ?: PxAmount.Invalid
}

private fun formatDecimal(value: BigDecimal): String {
    val plain = value.stripTrailingZeros().toPlainString()
    val normalized = if (plain == "-0") "0" else plain
    return normalized.replace('.', ',')
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("px и dp", value))
    true
} catch (_: RuntimeException) {
    false
}

private const val SCALE = 10
private const val EMPTY = "—"
