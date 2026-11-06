package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 1e11 — ещё секунды до 5138 года и уже миллисекунды с марта 1973-го. */
private const val MILLIS_THRESHOLD = 100_000_000_000L

private val localFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** Каждая строка — Unix-время в секундах или миллисекундах. Местное дата-время и счёт плохих строк. */
@Composable
fun T90StampListScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val zone = remember { ZoneId.systemDefault() }
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text, zone) { parseStamps(text, zone) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Метки времени") },
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
                "Каждая строка — целое Unix-время: секунды или миллисекунды. Показывается местное дата-время (${zone.id}). Пустые строки не считаются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Метки") },
                placeholder = { Text("1700000000\n1700000000000") },
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
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                minLines = 4,
            )
            Text(
                if (text.isBlank()) "Вставьте метки, по одной в строке." else stampSummary(parsed),
                style = MaterialTheme.typography.bodyMedium,
                color = if (parsed.bad > 0) scheme.error else scheme.onSurfaceVariant,
            )
            if (parsed.rows.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(parsed.rows, key = { index, _ -> index }) { _, row ->
                        StampRowCard(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun StampRowCard(row: StampRow) {
    val scheme = MaterialTheme.colorScheme
    val bad = row.local == null
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = scheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SelectionContainer {
                Text(
                    if (bad) "Не разобрано" else row.local,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (bad) scheme.error else scheme.onSurface,
                )
            }
            Text(
                if (bad) row.source else "${row.source} · ${row.unit}",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private data class StampRow(
    val source: String,
    val local: String?,
    val unit: String?,
)

private data class StampParse(
    val rows: List<StampRow>,
    val ok: Int,
    val bad: Int,
)

private data class ParsedStamp(
    val millis: Long,
    val fromMilliseconds: Boolean,
)

private fun parseStamps(text: String, zone: ZoneId): StampParse {
    if (text.isBlank()) return StampParse(emptyList(), 0, 0)
    val rows = ArrayList<StampRow>()
    var ok = 0
    var bad = 0
    for (raw in text.lineSequence()) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) continue
        val parsed = parseStamp(trimmed)
        if (parsed == null) {
            bad++
            rows += StampRow(trimmed, null, null)
        } else {
            val zoned = Instant.ofEpochMilli(parsed.millis).atZone(zone)
            val offset = zoned.offset.id.let { if (it == "Z") "UTC" else "UTC$it" }
            ok++
            rows += StampRow(
                source = trimmed,
                local = "${zoned.format(localFormat)} ($offset)",
                unit = if (parsed.fromMilliseconds) "миллисекунды" else "секунды",
            )
        }
    }
    return StampParse(rows, ok, bad)
}

private fun parseStamp(raw: String): ParsedStamp? {
    val cleaned = raw.replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
    if (cleaned.isEmpty()) return null
    val negative = cleaned.startsWith("-")
    val body = cleaned.removePrefix("-").removePrefix("+")
    if (body.isEmpty() || body.any { !it.isDigit() }) return null
    val value = (if (negative) "-$body" else body).toLongOrNull() ?: return null
    val fromMilliseconds = value >= MILLIS_THRESHOLD || value <= -MILLIS_THRESHOLD
    val millis = if (fromMilliseconds) {
        value
    } else if (value > Long.MAX_VALUE / 1000L || value < Long.MIN_VALUE / 1000L) {
        return null
    } else {
        value * 1000L
    }
    if (!instantFits(millis)) return null
    return ParsedStamp(millis, fromMilliseconds)
}

private fun instantFits(millis: Long): Boolean = try {
    Instant.ofEpochMilli(millis)
    true
} catch (_: DateTimeException) {
    false
} catch (_: ArithmeticException) {
    false
}

private fun stampSummary(parsed: StampParse): String {
    val ok = parsed.ok
    val bad = parsed.bad
    return "${ok} ${ruPlural(ok, "метка", "метки", "меток")}, " +
        "${bad} ${ruPlural(bad, "плохая строка", "плохие строки", "плохих строк")}"
}

private fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
