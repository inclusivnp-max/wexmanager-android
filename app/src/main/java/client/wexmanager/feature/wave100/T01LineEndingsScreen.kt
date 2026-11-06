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

@Composable
fun T01LineEndingsScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val counts = countLineEndings(text)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Окончания строк") },
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
                "LF — перевод строки, CRLF — возврат каретки и перевод, CR — только возврат каретки. Пара CRLF не входит в LF и CR.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EndingCard("LF", counts.lf, Modifier.weight(1f))
                EndingCard("CRLF", counts.crlf, Modifier.weight(1f))
                EndingCard("CR", counts.cr, Modifier.weight(1f))
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите текст") },
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
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Default,
                ),
                minLines = 6,
            )
        }
    }
}

@Composable
private fun EndingCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                formatCount(value),
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

private data class LineEndingCounts(
    val lf: Int,
    val crlf: Int,
    val cr: Int,
)

private fun countLineEndings(text: String): LineEndingCounts {
    var lf = 0
    var crlf = 0
    var cr = 0
    var index = 0
    while (index < text.length) {
        when (text[index]) {
            '\r' -> if (index + 1 < text.length && text[index + 1] == '\n') {
                crlf++
                index += 2
            } else {
                cr++
                index++
            }
            '\n' -> {
                lf++
                index++
            }
            else -> index++
        }
    }
    return LineEndingCounts(lf, crlf, cr)
}

private fun formatCount(value: Int): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return digits.reversed().chunked(3).joinToString("\u00A0").reversed()
}
