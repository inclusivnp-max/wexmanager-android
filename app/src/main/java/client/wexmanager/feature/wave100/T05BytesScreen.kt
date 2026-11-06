package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SdStorage
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
import androidx.compose.ui.unit.dp
import client.wexmanager.core.util.Format
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

/** «1.5 MB», «200 KB», «3 GiB» → байты, и обратно в двоичные и десятичные единицы. */
@Composable
fun T05BytesScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = parseByteSize(text)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Байты") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
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
                "KB, MB, GB — десятичные (1000). KiB, MiB, GiB — двоичные (1024). Число без единицы считается байтами.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = parsed is ByteSizeParse.Invalid,
                label = { Text("Размер") },
                placeholder = { Text("1.5 MB", fontFamily = FontFamily.Monospace) },
                supportingText = {
                    Text(if (parsed is ByteSizeParse.Invalid) BadSize else SizeHint)
                },
                leadingIcon = { Icon(Icons.Rounded.SdStorage, null) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            when (parsed) {
                ByteSizeParse.Empty -> Unit
                ByteSizeParse.Invalid -> Text(
                    BadSize,
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is ByteSizeParse.Ok -> SizeCard(parsed)
            }
        }
    }
}

@Composable
private fun SizeCard(parsed: ByteSizeParse.Ok) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(scaleLabel(parsed.scale), style = MaterialTheme.typography.titleMedium)
            SizeLine("Байты", Format.bytesExact(parsed.bytes))
            SizeLine("Двоичные", formatScaled(parsed.bytes, BinaryBase, BinaryLabels))
            SizeLine("Десятичные", formatScaled(parsed.bytes, DecimalBase, DecimalLabels))
        }
    }
}

@Composable
private fun SizeLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SelectionContainer {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
        }
    }
}

private const val SizeHint = "Например: 1.5 MB, 200 KB, 3 GiB или 1500000"
private const val BadSize = "Некорректный размер. Пример: 1.5 MB, 200 KB, 3 GiB"
private const val DecimalBase = 1_000L
private const val BinaryBase = 1_024L

private val BinaryLabels = arrayOf("Б", "КиБ", "МиБ", "ГиБ", "ТиБ", "ПиБ", "ЭиБ")
private val DecimalLabels = arrayOf("Б", "КБ", "МБ", "ГБ", "ТБ", "ПБ", "ЭБ")

private val SizePattern = Regex(
    """^\s*([+-]?(?:\d+(?:[ \u00A0\u202F]\d{3})+|\d+)(?:[.,]\d+)?(?:[eE][+-]?\d+)?)\s*([A-Za-zА-Яа-яЁё]+)?\s*$""",
)

private enum class ByteScale { Bytes, Decimal, Binary }

private sealed interface ByteSizeParse {
    data object Empty : ByteSizeParse
    data object Invalid : ByteSizeParse
    data class Ok(val bytes: Long, val scale: ByteScale) : ByteSizeParse
}

private fun parseByteSize(raw: String): ByteSizeParse {
    val text = raw.trim()
    if (text.isEmpty()) return ByteSizeParse.Empty
    val match = SizePattern.matchEntire(text) ?: return ByteSizeParse.Invalid
    val number = match.groupValues[1]
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("\u202F", "")
        .replace(',', '.')
    val unit = unitFactor(match.groupValues[2]) ?: return ByteSizeParse.Invalid
    val value = try {
        BigDecimal(number)
    } catch (_: NumberFormatException) {
        return ByteSizeParse.Invalid
    }
    if (value.signum() < 0) return ByteSizeParse.Invalid
    val bytes = try {
        value.multiply(BigDecimal.valueOf(unit.factor)).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (_: ArithmeticException) {
        return ByteSizeParse.Invalid
    }
    if (bytes < 0L) return ByteSizeParse.Invalid
    return ByteSizeParse.Ok(bytes, unit.scale)
}

private data class ByteUnit(val factor: Long, val scale: ByteScale)

private fun unitFactor(unit: String): ByteUnit? {
    if (unit.isEmpty()) return ByteUnit(1L, ByteScale.Bytes)
    return when (unit.lowercase(Format.RU)) {
        "b", "byte", "bytes", "б", "байт", "байта", "байтов" -> ByteUnit(1L, ByteScale.Bytes)
        "kb", "kbyte", "kbytes", "kilobyte", "kilobytes", "кб", "кбайт", "кбайта", "кбайтов" ->
            ByteUnit(pow(DecimalBase, 1), ByteScale.Decimal)
        "mb", "mbyte", "mbytes", "megabyte", "megabytes", "мб", "мбайт", "мбайта", "мбайтов" ->
            ByteUnit(pow(DecimalBase, 2), ByteScale.Decimal)
        "gb", "gbyte", "gbytes", "gigabyte", "gigabytes", "гб", "гбайт", "гбайта", "гбайтов" ->
            ByteUnit(pow(DecimalBase, 3), ByteScale.Decimal)
        "tb", "tbyte", "tbytes", "terabyte", "terabytes", "тб", "тбайт", "тбайта", "тбайтов" ->
            ByteUnit(pow(DecimalBase, 4), ByteScale.Decimal)
        "pb", "pbyte", "pbytes", "petabyte", "petabytes", "пб", "пбайт", "пбайта", "пбайтов" ->
            ByteUnit(pow(DecimalBase, 5), ByteScale.Decimal)
        "eb", "ebyte", "ebytes", "exabyte", "exabytes", "эб", "эбайт", "эбайта", "эбайтов" ->
            ByteUnit(pow(DecimalBase, 6), ByteScale.Decimal)
        "kib", "kibibyte", "kibibytes", "киб", "кибибайт", "кибибайта", "кибибайтов" ->
            ByteUnit(pow(BinaryBase, 1), ByteScale.Binary)
        "mib", "mebibyte", "mebibytes", "миб", "мебибайт", "мебибайта", "мебибайтов" ->
            ByteUnit(pow(BinaryBase, 2), ByteScale.Binary)
        "gib", "gibibyte", "gibibytes", "гиб", "гибибайт", "гибибайта", "гибибайтов" ->
            ByteUnit(pow(BinaryBase, 3), ByteScale.Binary)
        "tib", "tebibyte", "tebibytes", "тиб", "тебибайт", "тебибайта", "тебибайтов" ->
            ByteUnit(pow(BinaryBase, 4), ByteScale.Binary)
        "pib", "pebibyte", "pebibytes", "пиб", "пебибайт", "пебибайта", "пебибайтов" ->
            ByteUnit(pow(BinaryBase, 5), ByteScale.Binary)
        "eib", "exbibyte", "exbibytes", "эиб", "эксибайт", "эксибайта", "эксибайтов" ->
            ByteUnit(pow(BinaryBase, 6), ByteScale.Binary)
        else -> null
    }
}

private fun pow(base: Long, exp: Int): Long {
    var value = 1L
    repeat(exp) { value *= base }
    return value
}

private fun scaleLabel(scale: ByteScale): String = when (scale) {
    ByteScale.Bytes -> "Число принято как байты"
    ByteScale.Decimal -> "Десятичные единицы, 1 КБ = 1000 Б"
    ByteScale.Binary -> "Двоичные единицы, 1 КиБ = 1024 Б"
}

private fun formatScaled(bytes: Long, base: Long, labels: Array<String>): String {
    if (bytes < base) return "$bytes ${labels[0]}"
    var value = bytes.toDouble()
    var unit = 0
    while (value >= base && unit < labels.lastIndex) {
        value /= base.toDouble()
        unit++
    }
    val pattern = if (value < 10.0) "#,##0.##" else "#,##0.#"
    val formatted = DecimalFormat(pattern, DecimalFormatSymbols(Format.RU)).format(value)
    return "$formatted ${labels[unit]}"
}
