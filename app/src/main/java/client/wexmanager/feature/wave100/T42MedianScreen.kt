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
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

/** Медиана введённых чисел и размер отсортированного списка. */
@Composable
fun T42MedianScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val sorted = remember(text) { parseNumbers(text).sorted() }
    val median = remember(sorted) { if (sorted.isEmpty()) null else medianOf(sorted) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Медиана") },
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
                "Числа по одному в строке или через пробел. Запятая и точка — десятичный разделитель.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Числа") },
                placeholder = { Text("1\n2,5\n4") },
                trailingIcon = if (text.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { text = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, "Очистить")
                        }
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                minLines = 6,
            )
            if (sorted.isEmpty()) {
                Text(
                    "Нет чисел",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            } else {
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
                            "Медиана",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            formatDecimal(median!!),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            sortedCountLabel(sorted.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

private fun medianOf(sorted: List<BigDecimal>): BigDecimal {
    val mid = sorted.size / 2
    if (sorted.size % 2 == 1) return sorted[mid]
    return sorted[mid - 1]
        .add(sorted[mid])
        .divide(BigDecimal(2), SCALE, RoundingMode.HALF_UP)
        .stripTrailingZeros()
}

private fun parseNumbers(raw: String): List<BigDecimal> {
    val numbers = ArrayList<BigDecimal>()
    for (token in raw.split(TOKEN_SPLIT)) {
        val piece = token.trim().trim(',')
        if (piece.isEmpty()) continue
        if (piece.count { it == ',' } > 1 && '.' !in piece) {
            for (part in piece.split(',')) addNumber(numbers, part)
        } else {
            addNumber(numbers, piece)
        }
    }
    return numbers
}

private fun addNumber(out: MutableList<BigDecimal>, raw: String) {
    val cleaned = raw.trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
    if (cleaned.isEmpty()) return
    val normalized = if (',' in cleaned && '.' !in cleaned && cleaned.count { it == ',' } == 1) {
        cleaned.replace(',', '.')
    } else {
        cleaned
    }
    val body = normalized.removePrefix("+")
    if (body.isEmpty() || body == "-" || body == "." || body == "-.") return
    body.toBigDecimalOrNull()?.let(out::add)
}

private fun sortedCountLabel(count: Int): String {
    val word = when {
        count % 100 in 11..14 -> "чисел"
        count % 10 == 1 -> "число"
        count % 10 in 2..4 -> "числа"
        else -> "чисел"
    }
    return "$count $word в отсортированном списке"
}

private fun formatDecimal(value: BigDecimal): String {
    val plain = value.stripTrailingZeros().toPlainString()
    val normalized = if (plain == "-0") "0" else plain
    return normalized.replace('.', ',')
}

private const val SCALE = 16
private val TOKEN_SPLIT = Regex("[\\s;]+")
