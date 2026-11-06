package client.wexmanager.feature.extras

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private val HexColor = Regex("^#(?:[0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
private const val FormatHint = "#RGB, #RRGGBB или #AARRGGBB"
private const val InvalidColor = "Это не цвет. Укажите #RGB, #RRGGBB или #AARRGGBB"

@Composable
fun ColorHexScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = parseHexColor(text)
    val invalid = text.isNotBlank() && parsed == null
    val swatchShape = RoundedCornerShape(28.dp)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("HEX-цвет") },
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
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = invalid,
                label = { Text("Цвет") },
                placeholder = { Text("#RRGGBB", fontFamily = FontFamily.Monospace) },
                supportingText = { Text(if (invalid) InvalidColor else FormatHint) },
                leadingIcon = { Icon(Icons.Rounded.Palette, null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            ColorSwatch(
                color = parsed?.toComposeColor(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(swatchShape)
                    .border(1.dp, scheme.outlineVariant, swatchShape),
            )
            if (parsed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ArgbValue("A", parsed.a, Modifier.weight(1f))
                    ArgbValue("R", parsed.r, Modifier.weight(1f))
                    ArgbValue("G", parsed.g, Modifier.weight(1f))
                    ArgbValue("B", parsed.b, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color?, modifier: Modifier = Modifier) {
    val light = MaterialTheme.colorScheme.surfaceContainerHigh
    val dark = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier) {
        val cell = 16.dp.toPx()
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = 0f
            var column = 0
            while (x < size.width) {
                drawRect(
                    color = if ((row + column) % 2 == 0) light else dark,
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                )
                x += cell
                column++
            }
            y += cell
            row++
        }
        if (color != null) drawRect(color)
    }
}

@Composable
private fun ArgbValue(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class Argb(val a: Int, val r: Int, val g: Int, val b: Int) {
    fun toComposeColor(): Color = Color(
        red = r / 255f,
        green = g / 255f,
        blue = b / 255f,
        alpha = a / 255f,
    )
}

private fun parseHexColor(raw: String): Argb? {
    val text = raw.trim()
    if (!HexColor.matches(text)) return null
    val body = text.substring(1)
    val expanded = if (body.length == 3) buildString(6) { body.forEach { append(it).append(it) } } else body
    val bits = expanded.toLong(16)
    return Argb(
        a = if (body.length == 8) ((bits shr 24) and 0xFF).toInt() else 255,
        r = ((bits shr 16) and 0xFF).toInt(),
        g = ((bits shr 8) and 0xFF).toInt(),
        b = (bits and 0xFF).toInt(),
    )
}
