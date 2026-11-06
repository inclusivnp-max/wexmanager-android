package client.wexmanager.feature.wave100

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

private val HexRgb = Regex("^#([0-9A-Fa-f]{6})$")

/** Два цвета #RRGGBB смешиваются 50/50 по каналам R, G и B. */
@Composable
fun T71MixColorScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var first by rememberSaveable { mutableStateOf("#FF0000") }
    var second by rememberSaveable { mutableStateOf("#0000FF") }
    val left = remember(first) { parseRgb(first) }
    val right = remember(second) { parseRgb(second) }
    val mixed = if (left != null && right != null) mixHalf(left, right) else null
    val swatchShape = RoundedCornerShape(28.dp)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Смешение 50/50") },
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
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Два цвета в формате #RRGGBB. Каждый канал усредняется поровну, без альфы.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            ColorField(
                value = first,
                onValueChange = { first = it },
                label = "Цвет A",
                invalid = first.isNotBlank() && left == null,
            )
            ColorField(
                value = second,
                onValueChange = { second = it },
                label = "Цвет B",
                invalid = second.isNotBlank() && right == null,
            )
            if (mixed != null) {
                val color = Color(0xFF000000 or mixed.toLong())
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(swatchShape)
                        .background(color)
                        .border(1.dp, scheme.outlineVariant, swatchShape),
                )
                Text(
                    toHex(mixed),
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ColorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    invalid: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = invalid,
        label = { Text(label) },
        placeholder = { Text("#RRGGBB", fontFamily = FontFamily.Monospace) },
        supportingText = { Text(if (invalid) "Нужен цвет #RRGGBB" else "#RRGGBB") },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    )
}

private fun parseRgb(raw: String): Int? {
    val match = HexRgb.matchEntire(raw.trim()) ?: return null
    return match.groupValues[1].toInt(16)
}

private fun mixHalf(a: Int, b: Int): Int {
    val r = (((a shr 16) and 0xFF) + ((b shr 16) and 0xFF)) / 2
    val g = (((a shr 8) and 0xFF) + ((b shr 8) and 0xFF)) / 2
    val blue = ((a and 0xFF) + (b and 0xFF)) / 2
    return (r shl 16) or (g shl 8) or blue
}

private fun toHex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)
