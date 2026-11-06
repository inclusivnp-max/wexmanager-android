package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val MS_SECOND = 1_000L
private const val MS_MINUTE = 60_000L
private const val MS_HOUR = 3_600_000L
private const val COMPACT_ERROR = "Некорректная длительность. Пример: 1h2m3s"
private const val COMPACT_HINT = "Например, 1h2m3s или 1h2m3s500ms"
private const val MILLIS_HINT = "Неотрицательное целое число"

/** Разбор 1h2m3s и миллисекунд в часы, минуты и секунды и сборка обратно. */
@Composable
fun T08DurationScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var compactText by rememberSaveable { mutableStateOf("") }
    var millisText by rememberSaveable { mutableStateOf("") }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var minutesText by rememberSaveable { mutableStateOf("") }
    var secondsText by rememberSaveable { mutableStateOf("") }
    val fromCompact = parseCompact(compactText)
    val fromMillis = parseMillis(millisText)
    val fromParts = parseParts(hoursText, minutesText, secondsText)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Длительность") },
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
                "Строка вида 1h2m3s и число миллисекунд переводятся в часы, минуты и секунды. Обратно из часов, минут и секунд собираются строка и миллисекунды.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = compactText,
                onValueChange = { compactText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Строка") },
                placeholder = { Text("1h2m3s") },
                supportingText = {
                    Text(
                        when (fromCompact) {
                            is DurationParse.Invalid -> fromCompact.message
                            DurationParse.Incomplete -> "Добавьте единицу: h, m, s или ms"
                            else -> COMPACT_HINT
                        },
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                isError = fromCompact is DurationParse.Invalid,
            )
            if (fromCompact is DurationParse.Ok) {
                DurationCard("Разобрано", breakdownLines(fromCompact))
            }
            OutlinedTextField(
                value = millisText,
                onValueChange = { millisText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Миллисекунды") },
                placeholder = { Text("3723000") },
                supportingText = {
                    Text(
                        if (fromMillis is DurationParse.Invalid) fromMillis.message else MILLIS_HINT,
                    )
                },
                leadingIcon = { Icon(Icons.Rounded.HourglassEmpty, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = fromMillis is DurationParse.Invalid,
            )
            if (fromMillis is DurationParse.Ok) {
                DurationCard("Разобрано", breakdownLines(fromMillis))
            }
            Text(
                "Часы, минуты и секунды",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PartField(
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    label = "Часы",
                    isError = fromParts.hoursInvalid,
                    modifier = Modifier.weight(1f),
                )
                PartField(
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    label = "Минуты",
                    isError = fromParts.minutesInvalid,
                    modifier = Modifier.weight(1f),
                )
                PartField(
                    value = secondsText,
                    onValueChange = { secondsText = it },
                    label = "Секунды",
                    isError = fromParts.secondsInvalid,
                    modifier = Modifier.weight(1f),
                )
            }
            when (val parsed = fromParts.result) {
                DurationParse.Empty, DurationParse.Incomplete -> Unit
                is DurationParse.Invalid -> Text(
                    parsed.message,
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is DurationParse.Ok -> DurationCard(
                    "Собрано",
                    listOf(
                        "Строка" to parsed.compact,
                        "Миллисекунды" to parsed.totalMillis.toString(),
                    ),
                )
            }
        }
    }
}

@Composable
private fun PartField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.widthIn(min = 0.dp),
        singleLine = true,
        label = { Text(label, maxLines = 1) },
        placeholder = { Text("0") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = isError,
    )
}

@Composable
private fun DurationCard(title: String, lines: List<Pair<String, String>>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { (label, value) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SelectionContainer {
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

private fun breakdownLines(parsed: DurationParse.Ok): List<Pair<String, String>> {
    val lines = mutableListOf(
        "Часы" to parsed.parts.hours.toString(),
        "Минуты" to parsed.parts.minutes.toString(),
        "Секунды" to parsed.parts.seconds.toString(),
    )
    if (parsed.parts.millis != 0) {
        lines += "Остаток, мс" to parsed.parts.millis.toString()
    }
    lines += "Всего, мс" to parsed.totalMillis.toString()
    lines += "Строка" to parsed.compact
    return lines
}

private data class Hms(
    val hours: Long,
    val minutes: Int,
    val seconds: Int,
    val millis: Int,
)

private sealed interface DurationParse {
    data object Empty : DurationParse
    data object Incomplete : DurationParse
    data class Invalid(val message: String) : DurationParse
    data class Ok(val parts: Hms, val totalMillis: Long, val compact: String) : DurationParse
}

private data class PartsParse(
    val result: DurationParse,
    val hoursInvalid: Boolean = false,
    val minutesInvalid: Boolean = false,
    val secondsInvalid: Boolean = false,
)

private enum class DurationUnit(val rank: Int, val duplicate: String) {
    Hour(0, "Часы указаны дважды"),
    Minute(1, "Минуты указаны дважды"),
    Second(2, "Секунды указаны дважды"),
    Millis(3, "Миллисекунды указаны дважды"),
}

private data class PartRead(val value: Long, val error: String?)

/** ms проверяется раньше m и s: иначе суффикс ms читается как минуты или секунды. */
private fun parseCompact(raw: String): DurationParse {
    val text = raw.filterNot { it.isWhitespace() }
    if (text.isEmpty()) return DurationParse.Empty
    val source = text.lowercase()
    var index = 0
    var lastRank = -1
    var hours = 0L
    var minutes = 0L
    var seconds = 0L
    var millis = 0L
    while (index < source.length) {
        if (!source[index].isDigit()) return DurationParse.Invalid(COMPACT_ERROR)
        val start = index
        while (index < source.length && source[index].isDigit()) index++
        val digits = source.substring(start, index)
        if (index >= source.length) return DurationParse.Incomplete
        val unit = when {
            source.startsWith("ms", index) -> {
                index += 2
                DurationUnit.Millis
            }
            source[index] == 'h' -> {
                index += 1
                DurationUnit.Hour
            }
            source[index] == 'm' -> {
                index += 1
                DurationUnit.Minute
            }
            source[index] == 's' -> {
                index += 1
                DurationUnit.Second
            }
            else -> return DurationParse.Invalid(COMPACT_ERROR)
        }
        if (unit.rank < lastRank) {
            return DurationParse.Invalid("Единицы должны идти в порядке h, m, s, ms")
        }
        if (unit.rank == lastRank) return DurationParse.Invalid(unit.duplicate)
        lastRank = unit.rank
        val number = digits.toLongOrNull() ?: return DurationParse.Invalid("Слишком большое число")
        when (unit) {
            DurationUnit.Hour -> hours = number
            DurationUnit.Minute -> minutes = number
            DurationUnit.Second -> seconds = number
            DurationUnit.Millis -> millis = number
        }
    }
    return combine(hours, minutes, seconds, millis)
}

private fun parseMillis(raw: String): DurationParse {
    if (raw.isBlank()) return DurationParse.Empty
    val cleaned = raw.filterNot { it.isWhitespace() || it == '_' || it == ',' }
    if (cleaned.startsWith("-")) {
        return DurationParse.Invalid("Отрицательные миллисекунды не допускаются")
    }
    if (cleaned.isEmpty() || cleaned.any { !it.isDigit() }) {
        return DurationParse.Invalid("Ожидается целое число миллисекунд")
    }
    val value = cleaned.toLongOrNull()
        ?: return DurationParse.Invalid("Слишком большое число миллисекунд")
    return combine(0L, 0L, 0L, value)
}

private fun parseParts(hoursRaw: String, minutesRaw: String, secondsRaw: String): PartsParse {
    if (hoursRaw.isBlank() && minutesRaw.isBlank() && secondsRaw.isBlank()) {
        return PartsParse(DurationParse.Empty)
    }
    val hours = readPart(hoursRaw, "Часы", max = null)
    val minutes = readPart(minutesRaw, "Минуты", max = 59L)
    val seconds = readPart(secondsRaw, "Секунды", max = 59L)
    val message = hours.error ?: minutes.error ?: seconds.error
    if (message != null) {
        return PartsParse(
            result = DurationParse.Invalid(message),
            hoursInvalid = hours.error != null,
            minutesInvalid = minutes.error != null,
            secondsInvalid = seconds.error != null,
        )
    }
    return PartsParse(combine(hours.value, minutes.value, seconds.value, 0L))
}

private fun readPart(raw: String, label: String, max: Long?): PartRead {
    if (raw.isBlank()) return PartRead(0L, null)
    val cleaned = raw.filterNot { it.isWhitespace() }
    if (cleaned.startsWith("-")) {
        return PartRead(0L, "«$label»: отрицательные значения не допускаются")
    }
    if (cleaned.isEmpty() || cleaned.any { !it.isDigit() }) {
        return PartRead(0L, "«$label»: ожидается целое число")
    }
    val value = cleaned.toLongOrNull() ?: return PartRead(0L, "«$label»: слишком большое число")
    if (max != null && value > max) {
        return PartRead(0L, "«$label»: ожидается число от 0 до $max")
    }
    return PartRead(value, null)
}

private fun combine(hours: Long, minutes: Long, seconds: Long, millis: Long): DurationParse {
    val total = totalMillis(hours, minutes, seconds, millis)
        ?: return DurationParse.Invalid("Слишком большое значение")
    val parts = splitMillis(total)
    return DurationParse.Ok(parts, total, formatCompact(parts))
}

private fun totalMillis(hours: Long, minutes: Long, seconds: Long, millis: Long): Long? {
    var total = 0L
    total = addUnit(total, hours, MS_HOUR) ?: return null
    total = addUnit(total, minutes, MS_MINUTE) ?: return null
    total = addUnit(total, seconds, MS_SECOND) ?: return null
    total = addUnit(total, millis, 1L) ?: return null
    return total
}

private fun addUnit(total: Long, count: Long, unitMs: Long): Long? {
    if (count == 0L) return total
    if (count < 0L) return null
    if (unitMs > 1L && count > Long.MAX_VALUE / unitMs) return null
    val chunk = count * unitMs
    if (total > Long.MAX_VALUE - chunk) return null
    return total + chunk
}

private fun splitMillis(total: Long): Hms {
    val hours = total / MS_HOUR
    var rest = total % MS_HOUR
    val minutes = (rest / MS_MINUTE).toInt()
    rest %= MS_MINUTE
    val seconds = (rest / MS_SECOND).toInt()
    val millis = (rest % MS_SECOND).toInt()
    return Hms(hours, minutes, seconds, millis)
}

private fun formatCompact(parts: Hms): String {
    val out = StringBuilder()
    if (parts.hours != 0L) out.append(parts.hours).append('h')
    if (parts.minutes != 0) out.append(parts.minutes).append('m')
    if (parts.seconds != 0) out.append(parts.seconds).append('s')
    if (parts.millis != 0) out.append(parts.millis).append("ms")
    if (out.isEmpty()) out.append("0s")
    return out.toString()
}
