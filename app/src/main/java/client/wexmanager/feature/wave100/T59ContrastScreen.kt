package client.wexmanager.feature.wave100

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.pow

private val HexRgb = Regex("^#([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})([0-9A-Fa-f]{2})$")
private const val AA_NORMAL = 4.5
private const val EMPTY = "—"

/** Контраст двух цветов #RRGGBB по WCAG и порог AA для обычного текста (4,5 : 1). */
@Composable
fun T59ContrastScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var firstText by rememberSaveable { mutableStateOf("") }
    var secondText by rememberSaveable { mutableStateOf("") }
    val first = remember(firstText) { parseRgb(firstText) }
    val second = remember(secondText) { parseRgb(secondText) }
    val result = remember(first, second) { contrastOf(first, second) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Контраст") },
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
                "Два цвета #RRGGBB. Коэффициент по WCAG 2. Обычный текст проходит AA от 4,5 : 1.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            ColorField(
                value = firstText,
                onValueChange = { firstText = it },
                label = "Первый цвет",
                placeholder = "#000000",
                color = first,
            )
            ColorField(
                value = secondText,
                onValueChange = { secondText = it },
                label = "Второй цвет",
                placeholder = "#FFFFFF",
                color = second,
            )
            if (first is Rgb.Invalid || second is Rgb.Invalid) {
                Text(
                    "Нужен цвет #RRGGBB. Регистр не важен.",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (first is Rgb.Ok && second is Rgb.Ok) {
                ContrastPreview(text = first, background = second)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainer,
            ) {
                Column {
                    ResultRow("Коэффициент", result.ratioText)
                    HorizontalDivider(color = scheme.outlineVariant)
                    ResultRow(
                        label = "AA, обычный текст",
                        value = result.aaText,
                        valueColor = when (result) {
                            is Contrast.Ok -> if (result.passesAa) scheme.primary else scheme.error
                            else -> scheme.onSurface
                        },
                    )
                }
            }
            Text(
                "Порог 4,5 : 1. Сравнивается точное значение, до округления.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    color: Rgb,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = color is Rgb.Invalid,
        label = { Text(label) },
        placeholder = { Text(placeholder, fontFamily = FontFamily.Monospace) },
        supportingText = { Text("#RRGGBB") },
        leadingIcon = { Icon(Icons.Rounded.Palette, null) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun ContrastPreview(text: Rgb.Ok, background: Rgb.Ok) {
    val shape = RoundedCornerShape(16.dp)
    val ink = text.toColor()
    val paper = background.toColor()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        shape = shape,
        color = paper,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "Пример текста",
                color = ink,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
        )
    }
}

private sealed interface Rgb {
    data object Empty : Rgb
    data object Invalid : Rgb
    data class Ok(val r: Int, val g: Int, val b: Int) : Rgb
}

private sealed interface Contrast {
    data object Empty : Contrast
    data class Ok(val ratio: Double, val passesAa: Boolean) : Contrast
}

private val Contrast.ratioText: String
    get() = when (this) {
        Contrast.Empty -> EMPTY
        is Contrast.Ok -> formatRatio(ratio)
    }

private val Contrast.aaText: String
    get() = when (this) {
        Contrast.Empty -> EMPTY
        is Contrast.Ok -> if (passesAa) "Проходит" else "Не проходит"
    }

private fun Rgb.Ok.toColor(): Color = Color(red = r / 255f, green = g / 255f, blue = b / 255f)

private fun contrastOf(first: Rgb, second: Rgb): Contrast {
    val a = first as? Rgb.Ok ?: return Contrast.Empty
    val b = second as? Rgb.Ok ?: return Contrast.Empty
    val lighter = maxOf(a.relativeLuminance(), b.relativeLuminance())
    val darker = minOf(a.relativeLuminance(), b.relativeLuminance())
    val ratio = (lighter + 0.05) / (darker + 0.05)
    return Contrast.Ok(ratio = ratio, passesAa = ratio >= AA_NORMAL)
}

/** sRGB → относительная яркость, WCAG 2. */
private fun Rgb.Ok.relativeLuminance(): Double {
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

private fun linearize(channel: Int): Double {
    val srgb = channel / 255.0
    return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
}

private fun parseRgb(raw: String): Rgb {
    val text = raw.trim()
    if (text.isEmpty()) return Rgb.Empty
    val match = HexRgb.matchEntire(text) ?: return Rgb.Invalid
    return Rgb.Ok(
        r = match.groupValues[1].toInt(16),
        g = match.groupValues[2].toInt(16),
        b = match.groupValues[3].toInt(16),
    )
}

private fun formatRatio(ratio: Double): String {
    val text = BigDecimal.valueOf(ratio)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()
        .replace('.', ',')
    return "$text : 1"
}
