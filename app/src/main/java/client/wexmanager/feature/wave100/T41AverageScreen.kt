package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode

/** Числа по одному в строке или через пробел: количество, сумма, среднее, мин и макс. */
@Composable
fun T41AverageScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val stats = remember(text) { averageStats(text) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Среднее") },
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
                "Числа по одному в строке или через пробел. Запятая и точка — десятичный разделитель. Нечисловые токены пропускаются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Числа") },
                placeholder = { Text("1 2,5\n-3") },
                minLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainer,
            ) {
                Column {
                    StatRow("Количество", stats.count.toString())
                    HorizontalDivider(color = scheme.outlineVariant)
                    StatRow("Сумма", stats.sumText)
                    HorizontalDivider(color = scheme.outlineVariant)
                    StatRow("Среднее", stats.averageText)
                    HorizontalDivider(color = scheme.outlineVariant)
                    StatRow("Минимум", stats.minText)
                    HorizontalDivider(color = scheme.outlineVariant)
                    StatRow("Максимум", stats.maxText)
                    HorizontalDivider(color = scheme.outlineVariant)
                    StatRow("Пропущено", stats.skipped.toString())
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
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
            color = scheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private data class AverageStats(
    val count: Int,
    val skipped: Int,
    val sumText: String,
    val averageText: String,
    val minText: String,
    val maxText: String,
)

private fun averageStats(raw: String): AverageStats {
    if (raw.isBlank()) {
        return AverageStats(0, 0, EMPTY, EMPTY, EMPTY, EMPTY)
    }
    var count = 0
    var skipped = 0
    var sum = BigDecimal.ZERO
    var min: BigDecimal? = null
    var max: BigDecimal? = null
    for (token in raw.split(WHITESPACE)) {
        if (token.isEmpty()) continue
        val value = parseNumber(token)
        if (value == null) {
            skipped++
            continue
        }
        count++
        sum = sum.add(value)
        val currentMin = min
        val currentMax = max
        if (currentMin == null || value < currentMin) min = value
        if (currentMax == null || value > currentMax) max = value
    }
    if (count == 0) {
        return AverageStats(0, skipped, EMPTY, EMPTY, EMPTY, EMPTY)
    }
    val average = sum.divide(BigDecimal(count), SCALE, RoundingMode.HALF_UP)
    return AverageStats(
        count = count,
        skipped = skipped,
        sumText = formatDecimal(sum),
        averageText = formatDecimal(average),
        minText = formatDecimal(min!!),
        maxText = formatDecimal(max!!),
    )
}

private fun parseNumber(token: String): BigDecimal? {
    val cleaned = token.trim().replace('\u00A0', ' ').replace(" ", "").replace("_", "")
    if (cleaned.isEmpty()) return null
    val normalized = cleaned.replace(',', '.')
    return normalized.toBigDecimalOrNull()
}

private fun formatDecimal(value: BigDecimal): String {
    val plain = value.setScale(SCALE, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    val normalized = if (plain == "-0") "0" else plain
    return normalized.replace('.', ',')
}

private val WHITESPACE = Regex("\\s+")
private const val EMPTY = "—"
private const val SCALE = 10
