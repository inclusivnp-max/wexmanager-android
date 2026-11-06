package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import client.wexmanager.core.util.Format
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

private val isoDate: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    .withResolverStyle(ResolverStyle.STRICT)

private val prettyDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Format.RU)

/** Начальная дата плюс или минус N дней. */
@Composable
fun T38AddDaysScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var dateText by rememberSaveable { mutableStateOf("") }
    var daysText by rememberSaveable { mutableStateOf("") }
    var add by rememberSaveable { mutableStateOf(true) }
    val start = parseStartDate(dateText)
    val days = parseDayCount(daysText)
    val dateError = dateText.isNotBlank() && start == null
    val daysError = daysText.isNotBlank() && days == null
    val shifted = if (start != null && days != null) shiftDays(start, days, add) else null
    val overflow = start != null && days != null && shifted == null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Прибавить дни") },
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
                "К начальной дате прибавляется или вычитается целое число дней. Формат даты — гггг-мм-дд.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            FilledTonalButton(
                onClick = { dateText = LocalDate.now().format(isoDate) },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Сегодня")
            }
            OutlinedTextField(
                value = dateText,
                onValueChange = { dateText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Начальная дата") },
                placeholder = { Text("гггг-мм-дд") },
                supportingText = {
                    Text(if (dateError) "Формат: гггг-мм-дд" else "Например, 2026-10-04")
                },
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
                isError = dateError,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = daysText,
                onValueChange = { daysText = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Дней") },
                placeholder = { Text("0") },
                supportingText = {
                    Text(if (daysError) "Целое число от 0" else "Сколько дней сдвинуть")
                },
                isError = daysError,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = add,
                    onClick = { add = true },
                    label = { Text("Прибавить") },
                    leadingIcon = if (add) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    shapes = FilterChipDefaults.shapes(),
                )
                FilterChip(
                    selected = !add,
                    onClick = { add = false },
                    label = { Text("Вычесть") },
                    leadingIcon = if (!add) {
                        { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    shapes = FilterChipDefaults.shapes(),
                )
            }
            if (overflow) {
                Text(
                    "Результат вне диапазона дат",
                    color = scheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (shifted != null && days != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    SelectionContainer {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                shifted.format(prettyDate),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                shifted.format(isoDate),
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                shiftCaption(add, days),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Пустая строка — null без ошибки. Неразобранная дата — тоже null. */
private fun parseStartDate(text: String): LocalDate? {
    val raw = text.trim()
    if (raw.isEmpty()) return null
    return try {
        LocalDate.parse(raw, isoDate)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** Пустая строка — null. Отрицательные и нецелые — null. */
private fun parseDayCount(text: String): Long? {
    val raw = text.trim()
    if (raw.isEmpty()) return null
    val n = raw.toLongOrNull() ?: return null
    return n.takeIf { it >= 0L }
}

private fun shiftDays(start: LocalDate, days: Long, add: Boolean): LocalDate? = try {
    start.plusDays(if (add) days else -days)
} catch (_: DateTimeException) {
    null
}

private fun shiftCaption(add: Boolean, days: Long): String {
    if (days == 0L) return "Без сдвига"
    val formatted = "%,d".format(Format.RU, days)
    val word = dayWord(days)
    return if (add) "На $formatted $word позже" else "На $formatted $word раньше"
}

private fun dayWord(days: Long): String {
    val mod100 = (days % 100).toInt()
    val mod10 = mod100 % 10
    return when {
        mod10 == 1 && mod100 != 11 -> "день"
        mod10 in 2..4 && mod100 !in 12..14 -> "дня"
        else -> "дней"
    }
}
