package client.wexmanager.feature.wave100

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Разбор строк hosts на IP и имена. Комментарии с # пропускаются. Сеть не используется. */
@Composable
fun T65HostsScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { parseHosts(text) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Hosts") },
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
                "Каждая строка — адрес и имена через пробел. Строки с # и пустые пропускаются. Разбор только в тексте, без сети.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                label = { Text("Текст hosts") },
                placeholder = { Text("127.0.0.1 localhost", fontFamily = FontFamily.Monospace) },
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
            if (parsed.rows.isEmpty()) {
                Text(
                    when {
                        text.isBlank() -> "Вставьте содержимое файла hosts"
                        parsed.invalid > 0 -> "Нет записей. Не разобрано: ${parsed.invalid}"
                        parsed.comments > 0 -> "Нет записей. Комментарии пропущены"
                        else -> "Нет записей"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            } else {
                Text(
                    hostsSummary(parsed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(parsed.rows, key = { index, row -> "$index:${row.ip}:${row.names.size}" }) { _, row ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                row.ip,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                row.names.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

private data class HostRow(val ip: String, val names: List<String>)

private data class HostsParse(
    val rows: List<HostRow>,
    val comments: Int,
    val invalid: Int,
)

/** Первый токен — IPv4 или IPv6, остальные — имена. Текст после # отбрасывается. */
private fun parseHosts(text: String): HostsParse {
    if (text.isEmpty()) return HostsParse(emptyList(), 0, 0)
    val rows = ArrayList<HostRow>()
    var comments = 0
    var invalid = 0
    for (line in text.lineSequence()) {
        val hash = line.indexOf('#')
        val body = (if (hash >= 0) line.substring(0, hash) else line).trim()
        if (body.isEmpty()) {
            if (hash >= 0) comments++
            continue
        }
        val tokens = body.split(Whitespace)
        val names = if (tokens.size > 1) tokens.subList(1, tokens.size) else emptyList()
        if (tokens.isEmpty() || !isHostAddress(tokens[0]) || names.isEmpty() || names.any { !isHostName(it) }) {
            invalid++
            continue
        }
        rows.add(HostRow(tokens[0], names))
    }
    return HostsParse(rows, comments, invalid)
}

private val Whitespace = Regex("[ \\t]+")

private fun isHostAddress(token: String): Boolean = isIpv4(token) || isIpv6(token)

private fun isIpv4(text: String): Boolean {
    val parts = text.split('.')
    if (parts.size != 4) return false
    for (part in parts) {
        if (part.isEmpty() || part.length > 3) return false
        if (part.any { it !in '0'..'9' }) return false
        val value = part.toInt()
        if (value !in 0..255) return false
    }
    return true
}

/** Сжатие «::» заменяет хотя бы одну группу. Хвост IPv4 считается за две группы. */
private fun isIpv6(text: String): Boolean {
    if (text.length !in 2..45) return false
    if ('%' in text || '/' in text) return false
    val pieces = text.split("::")
    if (pieces.size > 2) return false
    val compressed = pieces.size == 2
    val head = pieceGroups(pieces[0], allowIpv4 = !compressed) ?: return false
    val tail = if (compressed) pieceGroups(pieces[1], allowIpv4 = true) ?: return false else 0
    val total = head + tail
    return if (compressed) total < 8 else total == 8
}

private fun pieceGroups(piece: String, allowIpv4: Boolean): Int? {
    if (piece.isEmpty()) return 0
    val bits = piece.split(':')
    var count = 0
    for (index in bits.indices) {
        val bit = bits[index]
        if (bit.isEmpty()) return null
        if ('.' in bit) {
            if (!allowIpv4 || index != bits.lastIndex || !isIpv4(bit)) return null
            count += 2
        } else {
            if (!isHexGroup(bit)) return null
            count += 1
        }
    }
    return count
}

private fun isHexGroup(text: String): Boolean {
    if (text.length !in 1..4) return false
    for (ch in text) {
        if (ch !in '0'..'9' && ch !in 'a'..'f' && ch !in 'A'..'F') return false
    }
    return true
}

private fun isHostName(name: String): Boolean {
    if (name.length > 253) return false
    val labels = name.split('.')
    if (labels.isEmpty()) return false
    for (label in labels) {
        if (label.isEmpty() || label.length > 63) return false
        if (label.first() == '-' || label.last() == '-' || label.first() == '_' || label.last() == '_') return false
        for (ch in label) {
            val ok = ch == '-' || ch == '_' || ch in '0'..'9' || ch in 'a'..'z' || ch in 'A'..'Z'
            if (!ok) return false
        }
    }
    return true
}

private fun hostsSummary(parsed: HostsParse): String {
    val rows = parsed.rows.size
    return buildString {
        append(rows)
        append(' ')
        append(ruPlural(rows, "запись", "записи", "записей"))
        if (parsed.comments > 0) {
            append(" · пропущено ")
            append(parsed.comments)
            append(' ')
            append(ruPlural(parsed.comments, "комментарий", "комментария", "комментариев"))
        }
        if (parsed.invalid > 0) {
            append(" · не разобрано: ")
            append(parsed.invalid)
        }
    }
}

private fun ruPlural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
