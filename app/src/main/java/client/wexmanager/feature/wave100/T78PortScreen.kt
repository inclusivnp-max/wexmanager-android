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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val PortMin = 1L
private const val PortMax = 65535L
private const val FormatHint = "Один порт или диапазон a-b, например 80 или 80-443"
private const val FormatError = "Нужен порт или диапазон a-b"

/** Порт или диапазон a-b: каждый конец в 1..65535 и сколько портов включительно. Сокетов нет. */
@Composable
fun T78PortScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val trimmed = text.trim()
    val span = if (trimmed.isEmpty()) null else parsePortSpan(trimmed)
    val invalid = trimmed.isNotEmpty() && (span == null || !span.bothInside)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Порты") },
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
                "Порт или диапазон a-b. Для каждого конца — входит ли он в 1–65535, и сколько портов между ними.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = invalid,
                label = { Text("Порт или диапазон") },
                placeholder = { Text("80-443", fontFamily = FontFamily.Monospace) },
                supportingText = { Text(if (span == null && trimmed.isNotEmpty()) FormatError else FormatHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            if (span != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (span.single) {
                            Text(
                                "Один порт: начало и конец совпадают.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        EndLine("Начало", span.leftText, span.left)
                        EndLine("Конец", span.rightText, span.right)
                        Text(
                            portCountLine(span),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EndLine(label: String, raw: String, value: Long?) {
    val status = when {
        value == null -> "не число, вне 1–65535"
        value in PortMin..PortMax -> "в пределах 1–65535"
        else -> "вне пределов 1–65535"
    }
    val shown = if (value != null && value in 0L..PortMax) value.toString() else raw
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "$shown — $status",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun portCountLine(span: PortSpan): String {
    val left = span.left
    val right = span.right
    if (left == null || right == null || left !in PortMin..PortMax || right !in PortMin..PortMax) {
        return "Портов: нет — оба конца должны быть в 1–65535"
    }
    val low = minOf(left, right)
    val high = maxOf(left, right)
    val count = high - low + 1
    return "Портов: $count (от $low до $high включительно)"
}

private data class PortSpan(
    val leftText: String,
    val rightText: String,
    val left: Long?,
    val right: Long?,
    val single: Boolean,
) {
    val bothInside: Boolean
        get() = left != null && right != null && left in PortMin..PortMax && right in PortMin..PortMax
}

private fun parsePortSpan(text: String): PortSpan? {
    val dash = text.indexOf('-')
    if (dash < 0) {
        val value = portNumber(text)
        return PortSpan(text, text, value, value, single = true)
    }
    if (dash == 0 || dash != text.lastIndexOf('-')) return null
    val leftText = text.substring(0, dash).trim()
    val rightText = text.substring(dash + 1).trim()
    if (leftText.isEmpty() || rightText.isEmpty()) return null
    return PortSpan(leftText, rightText, portNumber(leftText), portNumber(rightText), single = false)
}

private fun portNumber(token: String): Long? {
    if (token.isEmpty() || token.any { it !in '0'..'9' }) return null
    val normalized = token.trimStart('0').ifEmpty { "0" }
    if (normalized.length > 6) return Long.MAX_VALUE
    return normalized.toLongOrNull()
}
