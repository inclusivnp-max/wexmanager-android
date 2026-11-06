package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

private const val FormatHint = "Четыре октета 0–255 через точку, без ведущих нулей"
private const val InvalidIpv4 = "Некорректный IPv4. Нужны четыре октета от 0 до 255"

/** Разбор IPv4 и отметка частных диапазонов 10/8, 172.16/12, 192.168/16 и 127/8. Сеть не используется. */
@Composable
fun T28Ipv4Screen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = parseIpv4(text)
    val invalid = text.isNotBlank() && parsed == null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("IPv4") },
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
                "Проверка адреса на месте: октеты и частные диапазоны 10/8, 172.16/12, 192.168/16, 127/8.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = invalid,
                label = { Text("Адрес") },
                placeholder = { Text("192.168.0.1", fontFamily = FontFamily.Monospace) },
                supportingText = { Text(if (invalid) InvalidIpv4 else FormatHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            if (parsed != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    parsed.octets.forEachIndexed { index, value ->
                        OctetValue(index + 1, value, Modifier.weight(1f))
                    }
                }
                val prefix = privatePrefix(parsed)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            if (prefix == null) "Публичный" else "Частный",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (prefix == null) "Не входит в 10/8, 172.16/12, 192.168/16 и 127/8" else "Диапазон $prefix",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OctetValue(index: Int, value: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                "Октет $index",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private data class Ipv4(val a: Int, val b: Int, val c: Int, val d: Int) {
    val octets: List<Int> get() = listOf(a, b, c, d)
}

private fun parseIpv4(raw: String): Ipv4? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val parts = text.split('.')
    if (parts.size != 4) return null
    val numbers = IntArray(4)
    for (i in 0..3) {
        val part = parts[i]
        if (part.isEmpty() || part.length > 3) return null
        if (part.length > 1 && part[0] == '0') return null
        if (part.any { it !in '0'..'9' }) return null
        val value = part.toInt()
        if (value !in 0..255) return null
        numbers[i] = value
    }
    return Ipv4(numbers[0], numbers[1], numbers[2], numbers[3])
}

/** 10/8, 172.16/12, 192.168/16 и 127/8. Остальные адреса считаются публичными. */
private fun privatePrefix(ip: Ipv4): String? = when {
    ip.a == 10 -> "10/8"
    ip.a == 172 && ip.b in 16..31 -> "172.16/12"
    ip.a == 192 && ip.b == 168 -> "192.168/16"
    ip.a == 127 -> "127/8"
    else -> null
}
