package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/** 1 KB = 1024 байт, как двоичные единицы менеджера. МиБ на экране — байты / 1024². */
private const val KIB = 1024L
private const val MIB = 1024L * 1024L
private const val GIB = 1024L * 1024L * 1024L
private const val TIB = 1024L * 1024L * 1024L * 1024L

private val SIZE_LINE = Regex("""(\d+(?:[.,]\d+)?)(?:\s*([A-Za-zА-Яа-яЁё]+))?""")
private val MIB_DECIMAL = BigDecimal.valueOf(MIB)

@Composable
fun T30SizeSumScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val sum = sumSizes(text)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Сумма размеров") },
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Каждая строка — байты или размер: 12 KB, 3 MB. 1 KB = 1024 байт. Пустые строки не считаются, неразобранные пропускаются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            SizeCard("Байты", formatCount(sum.bytes))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SizeCard("МиБ", formatMib(sum.bytes), Modifier.weight(1f))
                SizeCard("Пропущено", formatCount(sum.skipped.toLong()), Modifier.weight(1f))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Размеры") },
                placeholder = { Text("1024\n12 KB\n3 MB") },
                trailingIcon = if (text.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { text = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, "Очистить")
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Default,
                ),
                minLines = 6,
            )
        }
    }
}

@Composable
private fun SizeCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class SizeSum(val bytes: Long, val skipped: Int)

private fun sumSizes(text: String): SizeSum {
    if (text.isEmpty()) return SizeSum(0L, 0)
    var bytes = 0L
    var skipped = 0
    for (raw in text.split('\n')) {
        val line = raw.trimEnd('\r').trim()
        if (line.isEmpty()) continue
        val parsed = parseSize(line)
        if (parsed == null) {
            skipped++
            continue
        }
        val next = bytes + parsed
        if (parsed < 0L || next < bytes) {
            skipped++
            continue
        }
        bytes = next
    }
    return SizeSum(bytes, skipped)
}

/** Целое число байт или величина с единицей. Дробь, не сходящаяся в целые байты, — ошибка. */
private fun parseSize(line: String): Long? {
    val match = SIZE_LINE.matchEntire(line) ?: return null
    val factor = unitFactor(match.groupValues[2]) ?: return null
    return scaleToBytes(match.groupValues[1], factor)
}

private fun unitFactor(raw: String): Long? {
    if (raw.isEmpty()) return 1L
    return when (raw.lowercase(Locale.ROOT)) {
        "b", "byte", "bytes", "б", "байт", "байта", "байтов" -> 1L
        "k", "kb", "kib", "kilobyte", "kilobytes", "kibibyte", "kibibytes",
        "к", "кб", "киб",
        -> KIB
        "m", "mb", "mib", "megabyte", "megabytes", "mebibyte", "mebibytes",
        "м", "мб", "миб",
        -> MIB
        "g", "gb", "gib", "gigabyte", "gigabytes", "gibibyte", "gibibytes",
        "г", "гб", "гиб",
        -> GIB
        "t", "tb", "tib", "terabyte", "terabytes", "tebibyte", "tebibytes",
        "т", "тб", "тиб",
        -> TIB
        else -> null
    }
}

private fun scaleToBytes(number: String, factor: Long): Long? {
    val parts = number.replace(',', '.').split('.')
    if (parts.size > 2) return null
    val whole = parts[0].toLongOrNull() ?: return null
    if (whole < 0L) return null
    val wholeBytes = try {
        Math.multiplyExact(whole, factor)
    } catch (_: ArithmeticException) {
        return null
    }
    val frac = if (parts.size == 2) parts[1] else ""
    if (frac.isEmpty()) return wholeBytes
    if (frac.any { it !in '0'..'9' }) return null
    val fracVal = frac.toLongOrNull() ?: return null
    val scale = pow10(frac.length) ?: return null
    val product = try {
        Math.multiplyExact(fracVal, factor)
    } catch (_: ArithmeticException) {
        return null
    }
    if (product % scale != 0L) return null
    return try {
        Math.addExact(wholeBytes, product / scale)
    } catch (_: ArithmeticException) {
        return null
    }
}

private fun pow10(digits: Int): Long? {
    if (digits !in 0..18) return null
    var value = 1L
    repeat(digits) {
        value = try {
            Math.multiplyExact(value, 10L)
        } catch (_: ArithmeticException) {
            return null
        }
    }
    return value
}

private fun formatCount(value: Long): String {
    val negative = value < 0L
    val digits = if (negative) value.toString().drop(1) else value.toString()
    val grouped = if (digits.length <= 3) digits else digits.reversed().chunked(3).joinToString("\u00A0").reversed()
    return if (negative) "-$grouped" else grouped
}

private fun formatMib(bytes: Long): String {
    val value = BigDecimal.valueOf(bytes).divide(MIB_DECIMAL, 6, RoundingMode.HALF_UP).stripTrailingZeros()
    val plain = if (value.scale() < 0) value.setScale(0).toPlainString() else value.toPlainString()
    val dot = plain.indexOf('.')
    val intPart = if (dot < 0) plain else plain.substring(0, dot)
    val frac = if (dot < 0) "" else plain.substring(dot + 1)
    val grouped = formatGroupedInt(intPart)
    return if (frac.isEmpty()) grouped else "$grouped,$frac"
}

private fun formatGroupedInt(intPart: String): String {
    val negative = intPart.startsWith("-")
    val digits = if (negative) intPart.substring(1) else intPart
    val grouped = if (digits.length <= 3) digits else digits.reversed().chunked(3).joinToString("\u00A0").reversed()
    return if (negative) "-$grouped" else grouped
}
