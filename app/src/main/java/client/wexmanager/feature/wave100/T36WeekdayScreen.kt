package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    .withResolverStyle(ResolverStyle.STRICT)

private val weekdays = arrayOf(
    "понедельник",
    "вторник",
    "среда",
    "четверг",
    "пятница",
    "суббота",
    "воскресенье",
)

/** Дата yyyy-MM-dd: русский день недели и сколько дней до сегодня. */
@Composable
fun T36WeekdayScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val today = remember { LocalDate.now() }
    val parsed = remember(text) { parseDate(text) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("День недели") },
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
                "Дата в формате yyyy-MM-dd. Показывается день недели и сколько дней осталось до сегодня.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Дата") },
                placeholder = { Text("yyyy-MM-dd") },
                supportingText = { Text("Например, ${today.format(dateFormat)}") },
                singleLine = true,
                isError = parsed is DateRead.Invalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            when (parsed) {
                DateRead.Empty -> Unit
                DateRead.Invalid -> Text(
                    "Формат: yyyy-MM-dd",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                is DateRead.Ok -> {
                    val days = ChronoUnit.DAYS.between(parsed.date, today)
                    WeekdayCard(label = "День недели", value = russianWeekday(parsed.date))
                    WeekdayCard(label = "Дней до сегодня", value = daysPhrase(days))
                }
            }
        }
    }
}

@Composable
private fun WeekdayCard(label: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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

private sealed interface DateRead {
    data object Empty : DateRead
    data object Invalid : DateRead
    data class Ok(val date: LocalDate) : DateRead
}

private fun parseDate(text: String): DateRead {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return DateRead.Empty
    return try {
        DateRead.Ok(LocalDate.parse(trimmed, dateFormat))
    } catch (_: DateTimeParseException) {
        DateRead.Invalid
    }
}

/** ISO: понедельник = 1 … воскресенье = 7. */
private fun russianWeekday(date: LocalDate): String = weekdays[date.dayOfWeek.value - 1]

/** Положительное — дата в прошлом, отрицательное — в будущем, ноль — сегодня. */
private fun daysPhrase(days: Long): String {
    if (days == 0L) return "сегодня"
    val n = kotlin.math.abs(days)
    val word = dayWord(n)
    return if (days > 0L) "$days $word" else "через ${-days} $word"
}

private fun dayWord(n: Long): String {
    val mod100 = (n % 100).toInt()
    val mod10 = (n % 10).toInt()
    return when {
        mod100 in 11..14 -> "дней"
        mod10 == 1 -> "день"
        mod10 in 2..4 -> "дня"
        else -> "дней"
    }
}
