package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import kotlin.math.PI

/** Градусы и радианы. Правка одного поля пересчитывает другое. */
@Composable
fun T80AngleScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var degreesText by rememberSaveable { mutableStateOf("") }
    var radiansText by rememberSaveable { mutableStateOf("") }
    var sourceDegrees by rememberSaveable { mutableStateOf(true) }
    val degrees = parseAngle(degreesText)
    val radians = parseAngle(radiansText)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Углы") },
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
                "Градусы и радианы. 180° равны π радиан, полный оборот — 360° или 2π.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = degreesText,
                onValueChange = { raw ->
                    sourceDegrees = true
                    degreesText = raw
                    radiansText = converted(raw) { it * PI / 180.0 }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Градусы") },
                placeholder = { Text("180") },
                suffix = { Text("°") },
                supportingText = { Text("Полный оборот — 360°") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = degrees is AngleNumber.Invalid,
            )
            OutlinedTextField(
                value = radiansText,
                onValueChange = { raw ->
                    sourceDegrees = false
                    radiansText = raw
                    degreesText = converted(raw) { it * 180.0 / PI }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Радианы") },
                placeholder = { Text("3,1415926536") },
                suffix = { Text("рад") },
                supportingText = { Text("Полный оборот — 2π") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = radians is AngleNumber.Invalid,
            )
            if (degrees is AngleNumber.Invalid || radians is AngleNumber.Invalid) {
                Text(
                    "Нужно число. Допускаются запятая и точка.",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val pair = anglePair(degrees, radians, sourceDegrees)
            AngleCard(title = "Градусы", value = pair.degreesText, hint = "значение в °")
            AngleCard(title = "Радианы", value = pair.radiansText, hint = "значение в рад")
            AngleCard(title = "Доли π", value = pair.piText, hint = "радианы ÷ π")
            AngleCard(title = "0…360°", value = pair.wrappedDegreesText, hint = "тот же угол на окружности")
            AngleCard(title = "0…2π", value = pair.wrappedRadiansText, hint = "тот же угол в радианах")
        }
    }
}

@Composable
private fun AngleCard(title: String, value: String, hint: String) {
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
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurface,
                )
            }
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface AngleNumber {
    data object Empty : AngleNumber
    data object Invalid : AngleNumber
    data class Ok(val value: Double) : AngleNumber
}

private data class AnglePair(
    val degreesText: String,
    val radiansText: String,
    val piText: String,
    val wrappedDegreesText: String,
    val wrappedRadiansText: String,
) {
    companion object {
        val Empty = AnglePair("—", "—", "—", "—", "—")
    }
}

private fun converted(raw: String, convert: (Double) -> Double): String {
    return when (val parsed = parseAngle(raw)) {
        AngleNumber.Empty, AngleNumber.Invalid -> ""
        is AngleNumber.Ok -> formatAngle(convert(parsed.value))
    }
}

private fun anglePair(degrees: AngleNumber, radians: AngleNumber, sourceDegrees: Boolean): AnglePair {
    val deg: Double
    val rad: Double
    when {
        sourceDegrees && degrees is AngleNumber.Ok -> {
            deg = degrees.value
            rad = deg * PI / 180.0
        }
        !sourceDegrees && radians is AngleNumber.Ok -> {
            rad = radians.value
            deg = rad * 180.0 / PI
        }
        degrees is AngleNumber.Ok -> {
            deg = degrees.value
            rad = deg * PI / 180.0
        }
        radians is AngleNumber.Ok -> {
            rad = radians.value
            deg = rad * 180.0 / PI
        }
        else -> return AnglePair.Empty
    }
    if (!deg.isFinite() || !rad.isFinite()) return AnglePair.Empty
    val piMultiple = rad / PI
    return AnglePair(
        degreesText = "${formatAngle(deg)}°",
        radiansText = "${formatAngle(rad)} рад",
        piText = "${formatAngle(piMultiple)} π",
        wrappedDegreesText = "${formatAngle(wrapPositive(deg, 360.0))}°",
        wrappedRadiansText = "${formatAngle(wrapPositive(rad, 2.0 * PI))} рад",
    )
}

private fun wrapPositive(value: Double, period: Double): Double {
    val mod = value % period
    val positive = if (mod < 0.0) mod + period else mod
    return if (positive == 0.0 || positive == period) 0.0 else positive
}

private fun parseAngle(raw: String): AngleNumber {
    val cleaned = raw.trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
        .replace(',', '.')
    if (cleaned.isEmpty()) return AngleNumber.Empty
    val body = cleaned.removePrefix("+")
    if (body.isEmpty() || body == "-" || body == "." || body == "-.") return AngleNumber.Empty
    val normalized = if (body.endsWith('.')) body.dropLast(1) else body
    if (normalized.count { it == '.' } > 1) return AngleNumber.Invalid
    val value = normalized.toDoubleOrNull() ?: return AngleNumber.Invalid
    if (!value.isFinite()) return AngleNumber.Invalid
    return AngleNumber.Ok(value)
}

private fun formatAngle(value: Double): String {
    if (!value.isFinite()) return "—"
    val plain = BigDecimal.valueOf(value)
        .setScale(SCALE, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    val normalized = if (plain == "-0") "0" else plain
    return normalized.replace('.', ',')
}

private const val SCALE = 10
