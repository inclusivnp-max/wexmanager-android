package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val isoDate: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd")
    .withResolverStyle(ResolverStyle.STRICT)

/** Абсолютное число календарных дней между двумя датами yyyy-MM-dd. */
@Composable
fun T37DaysBetweenScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    val start = remember(from) { parseIsoDate(from) }
    val end = remember(to) { parseIsoDate(to) }
    val days = if (start != null && end != null) abs(ChronoUnit.DAYS.between(start, end)) else null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Дней между датами") },
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
                "Две даты в формате yyyy-MM-dd. Считается абсолютная разница в календарных днях, порядок полей не важен.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            DateField(
                value = from,
                onValueChange = { from = it },
                label = "Первая дата",
                invalid = from.isNotBlank() && start == null,
            )
            DateField(
                value = to,
                onValueChange = { to = it },
                label = "Вторая дата",
                invalid = to.isNotBlank() && end == null,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = scheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        days?.toString() ?: "—",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        resultCaption(from, to, days),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    invalid: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = { Text("yyyy-MM-dd") },
        supportingText = {
            Text(if (invalid) "Неверная дата" else "yyyy-MM-dd")
        },
        isError = invalid,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

private fun parseIsoDate(text: String): LocalDate? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    return try {
        LocalDate.parse(trimmed, isoDate)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun resultCaption(from: String, to: String, days: Long?): String {
    if (from.isBlank() || to.isBlank()) return "Введите обе даты"
    if (days == null) return "Нужен формат yyyy-MM-dd"
    val n = (days % 100).toInt()
    val n10 = n % 10
    return when {
        n in 11..14 -> "дней"
        n10 == 1 -> "день"
        n10 in 2..4 -> "дня"
        else -> "дней"
    }
}
