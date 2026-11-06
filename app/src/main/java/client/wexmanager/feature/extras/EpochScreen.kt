package client.wexmanager.feature.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import client.wexmanager.core.util.Format
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** 1e11 — ещё секунды до 5138 года и уже миллисекунды с марта 1973-го. */
private const val MILLIS_THRESHOLD = 100_000_000_000L

private val typedFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
    .withResolverStyle(ResolverStyle.STRICT)

private val prettyFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm:ss", Format.RU)

private val exactFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Composable
fun EpochScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val zone = ZoneId.systemDefault()
    var epochText by rememberSaveable { mutableStateOf("") }
    var dateText by rememberSaveable { mutableStateOf("") }
    val epoch = parseEpoch(epochText)
    val typed = parseTypedDate(dateText, zone)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Эпоха Unix") },
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
                "Секунды или миллисекунды с 1970-01-01 UTC переводятся в местное время. Обратно — дата в формате yyyy-MM-dd HH:mm.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = {
                    val now = LocalDateTime.now(zone)
                    epochText = now.atZone(zone).toEpochSecond().toString()
                    dateText = now.withSecond(0).withNano(0).format(typedFormat)
                },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Сейчас")
            }
            OutlinedTextField(
                value = epochText,
                onValueChange = { epochText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Unix-время") },
                placeholder = { Text("1700000000 или 1700000000000") },
                supportingText = { Text("Целое число: секунды или миллисекунды") },
                leadingIcon = { Icon(Icons.Rounded.Schedule, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = epoch is EpochParse.Invalid,
            )
            when (epoch) {
                EpochParse.Empty -> Unit
                EpochParse.Invalid -> Text(
                    "Некорректное число",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is EpochParse.Ok -> {
                    val zoned = Instant.ofEpochMilli(epoch.millis).atZone(zone)
                    EpochCard(
                        title = if (epoch.fromMilliseconds) "Распознано как миллисекунды" else "Распознано как секунды",
                    ) {
                        EpochLine("Местное время", zoned.format(prettyFormat), mono = false)
                        EpochLine("Дата и время", zoned.format(exactFormat))
                        EpochLine("Смещение", offsetLabel(zoned.offset))
                    }
                }
            }
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Дата и время") },
                placeholder = { Text("yyyy-MM-dd HH:mm") },
                supportingText = { Text("Местное время, секунды считаются 00") },
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
                isError = typed is DateParse.Invalid,
            )
            when (typed) {
                DateParse.Empty -> Unit
                DateParse.Invalid -> Text(
                    "Формат: yyyy-MM-dd HH:mm",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is DateParse.Ok -> EpochCard(title = "Unix-время") {
                    EpochLine("Секунды", typed.seconds.toString())
                    EpochLine("Миллисекунды", typed.millis.toString())
                }
            }
        }
    }
}

@Composable
private fun EpochCard(title: String, content: @Composable () -> Unit) {
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
            content()
        }
    }
}

@Composable
private fun EpochLine(label: String, value: String, mono: Boolean = true) {
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
                fontFamily = if (mono) FontFamily.Monospace else null,
            )
        }
    }
}

private sealed interface EpochParse {
    data object Empty : EpochParse
    data object Invalid : EpochParse
    data class Ok(val millis: Long, val fromMilliseconds: Boolean) : EpochParse
}

private sealed interface DateParse {
    data object Empty : DateParse
    data object Invalid : DateParse
    data class Ok(val seconds: Long, val millis: Long) : DateParse
}

private fun parseEpoch(raw: String): EpochParse {
    val cleaned = raw.trim()
        .replace(" ", "")
        .replace("\u00A0", "")
        .replace("_", "")
        .replace(",", "")
    if (cleaned.isEmpty()) return EpochParse.Empty
    val negative = cleaned.startsWith("-")
    val body = cleaned.removePrefix("-").removePrefix("+")
    if (body.isEmpty() || body.any { !it.isDigit() }) return EpochParse.Invalid
    val value = (if (negative) "-$body" else body).toLongOrNull() ?: return EpochParse.Invalid
    val fromMilliseconds = value >= MILLIS_THRESHOLD || value <= -MILLIS_THRESHOLD
    val millis = if (fromMilliseconds) {
        value
    } else if (value > Long.MAX_VALUE / 1000L || value < Long.MIN_VALUE / 1000L) {
        return EpochParse.Invalid
    } else {
        value * 1000L
    }
    return if (instantFits(millis)) EpochParse.Ok(millis, fromMilliseconds) else EpochParse.Invalid
}

private fun parseTypedDate(raw: String, zone: ZoneId): DateParse {
    val text = raw.trim()
    if (text.isEmpty()) return DateParse.Empty
    val local = try {
        LocalDateTime.parse(text, typedFormat)
    } catch (_: DateTimeParseException) {
        return DateParse.Invalid
    }
    val seconds = try {
        local.atZone(zone).toEpochSecond()
    } catch (_: DateTimeException) {
        return DateParse.Invalid
    } catch (_: ArithmeticException) {
        return DateParse.Invalid
    }
    if (seconds > Long.MAX_VALUE / 1000L || seconds < Long.MIN_VALUE / 1000L) return DateParse.Invalid
    return DateParse.Ok(seconds, seconds * 1000L)
}

private fun instantFits(millis: Long): Boolean = try {
    Instant.ofEpochMilli(millis)
    true
} catch (_: DateTimeException) {
    false
} catch (_: ArithmeticException) {
    false
}

private fun offsetLabel(offset: ZoneOffset): String {
    val id = offset.id
    return if (id == "Z") "UTC" else "UTC$id"
}
