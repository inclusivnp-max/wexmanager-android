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

private const val FormatHint = "Шестнадцатеричное число в диапазоне Long. Префикс 0x необязателен"
private const val InvalidHex = "Некорректное шестнадцатеричное число"
private const val OverflowLong = "Число не помещается в Long"

private val LongSignMagnitude = 1UL shl 63

/** Два hex-числа в диапазоне Long: AND, OR и XOR в шестнадцатеричном виде. */
@Composable
fun T43BitwiseScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var firstText by rememberSaveable { mutableStateOf("") }
    var secondText by rememberSaveable { mutableStateOf("") }
    val first = parseHexLong(firstText)
    val second = parseHexLong(secondText)
    val both = if (first is HexLong.Ok && second is HexLong.Ok) first.value to second.value else null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Побитовые") },
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
                "Два шестнадцатеричных числа. Если оба помещаются в Long, ниже AND, OR и XOR.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            HexField(
                value = firstText,
                onValueChange = { firstText = it },
                parsed = first,
                label = "Число A",
                placeholder = "0xFF",
            )
            HexField(
                value = secondText,
                onValueChange = { secondText = it },
                parsed = second,
                label = "Число B",
                placeholder = "0x0F",
            )
            BitRow("AND", both?.let { (a, b) -> a and b })
            BitRow("OR", both?.let { (a, b) -> a or b })
            BitRow("XOR", both?.let { (a, b) -> a xor b })
        }
    }
}

@Composable
private fun HexField(
    value: String,
    onValueChange: (String) -> Unit,
    parsed: HexLong,
    label: String,
    placeholder: String,
) {
    val failed = parsed is HexLong.Invalid || parsed is HexLong.Overflow
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = failed,
        label = { Text(label) },
        placeholder = { Text(placeholder, fontFamily = FontFamily.Monospace) },
        supportingText = {
            Text(
                when (parsed) {
                    HexLong.Invalid -> InvalidHex
                    HexLong.Overflow -> OverflowLong
                    HexLong.Empty, is HexLong.Ok -> FormatHint
                },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
    )
}

@Composable
private fun BitRow(title: String, value: Long?) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
            )
            Text(
                value?.let(::formatHex) ?: "—",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private sealed interface HexLong {
    data object Empty : HexLong
    data object Invalid : HexLong
    data object Overflow : HexLong
    data class Ok(val value: Long) : HexLong
}

private fun parseHexLong(raw: String): HexLong {
    val text = raw.trim()
    if (text.isEmpty()) return HexLong.Empty
    val negative = text[0] == '-'
    var body = if (text[0] == '-' || text[0] == '+') text.substring(1) else text
    if (body.length >= 2 && body[0] == '0' && (body[1] == 'x' || body[1] == 'X')) {
        body = body.substring(2)
    }
    if (body.isEmpty() || body.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return HexLong.Invalid
    }
    val digits = body.dropWhile { it == '0' }.ifEmpty { "0" }
    if (digits.length > 16) return HexLong.Overflow
    val magnitude = digits.toULong(16)
    if (!negative) {
        if (magnitude > Long.MAX_VALUE.toULong()) return HexLong.Overflow
        return HexLong.Ok(magnitude.toLong())
    }
    if (magnitude == 0UL) return HexLong.Ok(0L)
    if (magnitude > LongSignMagnitude) return HexLong.Overflow
    return if (magnitude == LongSignMagnitude) {
        HexLong.Ok(Long.MIN_VALUE)
    } else {
        HexLong.Ok(-magnitude.toLong())
    }
}

/** Битовый шаблон Long без знака, чтобы старший бит не превращался в минус. */
private fun formatHex(value: Long): String = "0x" + value.toULong().toString(16).uppercase()
