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

private const val FormatHint = "Шесть октетов: двоеточия, дефисы, точки Cisco или 12 hex-символов"
private const val InvalidMac = "Некорректный MAC. Нужны шесть октетов"

/** Приведение MAC к виду AA:BB:CC:DD:EE:FF и проверка бита multicast. Сеть не используется. */
@Composable
fun T77MacScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = parseMac(text)
    val invalid = parsed is MacParse.Invalid

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("MAC-адрес") },
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
                "Адрес приводится к форме с двоеточиями. Младший бит первого октета — признак multicast.",
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
                placeholder = { Text("AA:BB:CC:DD:EE:FF", fontFamily = FontFamily.Monospace) },
                supportingText = { Text(if (invalid) InvalidMac else FormatHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            if (parsed is MacParse.Ok) {
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
                            parsed.colon,
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Форма с двоеточиями",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
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
                            if (parsed.multicast) "Бит установлен" else "Бит сброшен",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (parsed.multicast) {
                                "Multicast: младший бит первого октета равен 1"
                            } else {
                                "Unicast: младший бит первого октета равен 0"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface MacParse {
    data object Empty : MacParse
    data object Invalid : MacParse
    data class Ok(val colon: String, val multicast: Boolean) : MacParse
}

private fun parseMac(raw: String): MacParse {
    val text = raw.trim()
    if (text.isEmpty()) return MacParse.Empty
    val bytes = decodeMac(text) ?: return MacParse.Invalid
    val colon = buildString(17) {
        for (index in bytes.indices) {
            if (index > 0) append(':')
            append(HEX[bytes[index] ushr 4])
            append(HEX[bytes[index] and 0x0F])
        }
    }
    return MacParse.Ok(colon, (bytes[0] and 0x01) != 0)
}

private fun decodeMac(text: String): IntArray? {
    if (text.length == 12 && text.all(::isHex)) return pairs(text)
    splitOctets(text, ':')?.let { return it }
    splitOctets(text, '-')?.let { return it }
    splitOctets(text, '.')?.let { return it }
    splitCisco(text)?.let { return it }
    return splitSpaces(text)
}

/** Шесть групп по 1–2 hex-символа с одним разделителем. */
private fun splitOctets(text: String, separator: Char): IntArray? {
    if (separator !in text) return null
    if (text.any { it != separator && !isHex(it) }) return null
    val parts = text.split(separator)
    if (parts.size != 6) return null
    val bytes = IntArray(6)
    for (index in parts.indices) {
        val part = parts[index]
        if (part.length !in 1..2 || part.any { !isHex(it) }) return null
        bytes[index] = part.toInt(16)
    }
    return bytes
}

/** Cisco: три группы по четыре hex-символа, например AABB.CCDD.EEFF. */
private fun splitCisco(text: String): IntArray? {
    val parts = text.split('.')
    if (parts.size != 3) return null
    if (parts.any { it.length != 4 || it.any { char -> !isHex(char) } }) return null
    return pairs(parts[0] + parts[1] + parts[2])
}

private fun splitSpaces(text: String): IntArray? {
    if (text.any { it == ':' || it == '-' || it == '.' }) return null
    val parts = text.split(Regex("\\s+"))
    if (parts.size != 6) return null
    val bytes = IntArray(6)
    for (index in parts.indices) {
        val part = parts[index]
        if (part.length !in 1..2 || part.any { !isHex(it) }) return null
        bytes[index] = part.toInt(16)
    }
    return bytes
}

private fun pairs(hex: String): IntArray {
    val bytes = IntArray(6)
    for (index in 0..5) {
        val start = index * 2
        bytes[index] = hex.substring(start, start + 2).toInt(16)
    }
    return bytes
}

private fun isHex(char: Char): Boolean =
    char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

private const val HEX = "0123456789ABCDEF"
