package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import kotlinx.coroutines.launch

private data class QueryRow(val key: String, val value: String)

private data class ParsedQuery(
    val parts: List<QueryRow>,
    val params: List<QueryRow>,
    val fragment: QueryRow?,
) {
    val isEmpty: Boolean get() = parts.isEmpty() && params.isEmpty() && fragment == null
}

/** Разбор ссылки или строки запроса на пары ключ/значение. */
@Composable
fun T26QueryScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    val parsed = remember(text) { parseQueryInput(text) }

    fun notify(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }

    fun copy(label: String, value: String) {
        val payload = value.ifBlank { label }
        val copied = payload.isNotEmpty() && copyPlain(context, label, payload)
        notify(
            when {
                payload.isEmpty() -> "Нечего копировать"
                copied -> "Скопировано"
                else -> "Не удалось скопировать"
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Параметры запроса") },
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
                "Ссылка или строка вида ключ=значение. Плюс и проценты раскодируются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Ссылка или запрос") },
                placeholder = {
                    Text(
                        "https://сайт/путь?ключ=значение",
                        fontFamily = FontFamily.Monospace,
                    )
                },
                trailingIcon = if (text.isEmpty()) {
                    null
                } else {
                    {
                        IconButton(onClick = { text = "" }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Close, "Очистить")
                        }
                    }
                },
                minLines = 2,
                maxLines = 6,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            when {
                text.isBlank() -> Text(
                    "Вставьте ссылку или строку запроса",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                parsed.isEmpty -> Text(
                    "В строке нет адреса и параметров",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                else -> {
                    if (parsed.parts.isNotEmpty()) {
                        QuerySection("Адрес", parsed.parts) { row -> copy(row.key, row.value) }
                    }
                    if (parsed.params.isNotEmpty()) {
                        QuerySection("Параметры (${parsed.params.size})", parsed.params) { row ->
                            copy(row.key, row.value)
                        }
                    }
                    parsed.fragment?.let { row ->
                        QuerySection("Фрагмент", listOf(row)) { copy(row.key, row.value) }
                    }
                    FilledTonalButton(
                        onClick = { copy("Запрос", formatCopy(parsed)) },
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Копировать")
                    }
                }
            }
        }
    }
}

@Composable
private fun QuerySection(
    title: String,
    rows: List<QueryRow>,
    onCopy: (QueryRow) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = scheme.surfaceContainer,
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.key.ifEmpty { "—" },
                                style = MaterialTheme.typography.titleSmall,
                                color = scheme.onSurfaceVariant,
                            )
                            Text(
                                row.value.ifEmpty { "—" },
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        IconButton(onClick = { onCopy(row) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.ContentCopy, "Копировать")
                        }
                    }
                    if (index != rows.lastIndex) {
                        HorizontalDivider(color = scheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private fun parseQueryInput(raw: String): ParsedQuery {
    val text = raw.trim()
    if (text.isEmpty()) return ParsedQuery(emptyList(), emptyList(), null)
    if (isBareQuery(text)) return parseBare(text)
    val implicit = needsImplicitScheme(text)
    val uri = Uri.parse(if (implicit) "http://$text" else text)
    return fromUri(uri, showScheme = !implicit)
}

/** Строка без адреса: ?a=1, a=1&b=2. Точка в первом сегменте — это уже хост. */
private fun isBareQuery(text: String): Boolean {
    if (text.startsWith("?")) return true
    if (text.contains("://") || text.startsWith("//") || text.startsWith("/")) return false
    val head = text.substringBefore('?').substringBefore('#')
    if (head.isEmpty() || head.contains('/') || head.contains(':') || head.contains('.')) return false
    if (head.equals("localhost", ignoreCase = true)) return false
    return text.contains('=') || text.contains('&')
}

/** example.com/path и localhost:8080 без схемы. http:// только для разбора, в строки не попадает. */
private fun needsImplicitScheme(text: String): Boolean {
    if (text.contains("://") || text.startsWith("//") || text.startsWith("/") || text.startsWith("?")) {
        return false
    }
    val first = text.substringBefore('?').substringBefore('#').substringBefore('/')
    if (first.isEmpty() || first.any { it.isWhitespace() }) return false
    val host = first.substringBefore(':')
    val port = first.substringAfter(':', "")
    if (first.contains(':') && (port.isEmpty() || port.any { !it.isDigit() })) return false
    if (host.startsWith("[") && host.endsWith("]")) return true
    if (host.equals("localhost", ignoreCase = true)) return true
    return host.contains('.')
}

private fun parseBare(text: String): ParsedQuery {
    val body = text.removePrefix("?")
    val hash = body.indexOf('#')
    val query = if (hash >= 0) body.substring(0, hash) else body
    val fragment = if (hash >= 0 && hash < body.lastIndex) {
        QueryRow("Фрагмент", Uri.decode(body.substring(hash + 1)))
    } else {
        null
    }
    return ParsedQuery(emptyList(), parseParams(query), fragment)
}

private fun fromUri(uri: Uri, showScheme: Boolean): ParsedQuery {
    val parts = mutableListOf<QueryRow>()
    if (showScheme) {
        uri.scheme?.takeIf { it.isNotEmpty() }?.let { parts += QueryRow("Схема", it) }
    }
    if (uri.isHierarchical) {
        uri.encodedUserInfo?.takeIf { it.isNotEmpty() }?.let {
            parts += QueryRow("Пользователь", Uri.decode(it))
        }
        uri.host?.takeIf { it.isNotEmpty() }?.let { parts += QueryRow("Хост", it) }
        if (uri.port != -1) parts += QueryRow("Порт", uri.port.toString())
        uri.encodedPath?.takeIf { it.isNotEmpty() }?.let { parts += QueryRow("Путь", Uri.decode(it)) }
    } else {
        val address = uri.encodedSchemeSpecificPart
            ?.substringBefore('?')
            ?.substringBefore('#')
            .orEmpty()
        if (address.isNotEmpty()) parts += QueryRow("Адрес", Uri.decode(address))
    }
    val fragment = uri.encodedFragment?.takeIf { it.isNotEmpty() }?.let {
        QueryRow("Фрагмент", Uri.decode(it))
    }
    return ParsedQuery(parts, uri.encodedQuery?.let(::parseParams).orEmpty(), fragment)
}

private fun parseParams(query: String): List<QueryRow> {
    if (query.isEmpty()) return emptyList()
    return query.split('&').mapNotNull { pair ->
        if (pair.isEmpty()) return@mapNotNull null
        val eq = pair.indexOf('=')
        val row = if (eq < 0) {
            QueryRow(decodeQuery(pair), "")
        } else {
            QueryRow(decodeQuery(pair.substring(0, eq)), decodeQuery(pair.substring(eq + 1)))
        }
        if (row.key.isEmpty() && row.value.isEmpty()) null else row
    }
}

private fun decodeQuery(raw: String): String = try {
    URLDecoder.decode(raw, "UTF-8")
} catch (_: IllegalArgumentException) {
    raw
} catch (_: UnsupportedEncodingException) {
    raw
}

private fun formatCopy(parsed: ParsedQuery): String = buildString {
    parsed.parts.forEach { append(it.key).append(": ").append(it.value).append('\n') }
    parsed.params.forEach { append(it.key).append('=').append(it.value).append('\n') }
    parsed.fragment?.let { append(it.key).append(": ").append(it.value) }
}.trimEnd()

private fun copyPlain(context: Context, label: String, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText(label, value))
    true
} catch (_: RuntimeException) {
    false
}
