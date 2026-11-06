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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.nio.charset.StandardCharsets

/** Длина Kotlin (`String.length`), число кодовых точек и размер в байтах UTF-8. */
@Composable
fun T45Utf8Screen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val measures = utf8Measures(text)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("UTF-8") },
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
                "Длина Kotlin — число единиц UTF-16. Кодовые точки считают символы Unicode, " +
                    "в том числе те, что занимают суррогатную пару. Байты — размер той же строки в UTF-8.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            MeasureCard("Длина Kotlin", measures.kotlinLength)
            MeasureCard("Кодовые точки", measures.codePoints)
            MeasureCard("Байты UTF-8", measures.utf8Bytes)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
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
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                minLines = 6,
            )
        }
    }
}

@Composable
private fun MeasureCard(label: String, value: Int) {
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

private data class Utf8Measures(
    val kotlinLength: Int,
    val codePoints: Int,
    val utf8Bytes: Int,
)

private fun utf8Measures(text: String): Utf8Measures = Utf8Measures(
    kotlinLength = text.length,
    codePoints = text.codePointCount(0, text.length),
    utf8Bytes = text.toByteArray(StandardCharsets.UTF_8).size,
)

private fun formatCount(value: Int): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return digits.reversed().chunked(3).joinToString("\u00A0").reversed()
}
