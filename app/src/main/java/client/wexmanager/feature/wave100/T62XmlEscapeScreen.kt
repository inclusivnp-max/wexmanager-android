package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Экранирование и снятие экранирования &, <, >, " и '. Остальные символы не трогаются. */
@Composable
fun T62XmlEscapeScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var source by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }

    fun copy(value: String) {
        val copied = value.isNotEmpty() && copyPlain(context, value)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    value.isEmpty() -> "Нечего копировать"
                    copied -> "Скопировано"
                    else -> "Не удалось скопировать"
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("XML") },
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Экранирует и снимает экранирование символов & < > \" '. " +
                    "Апостроф пишется как &apos;; при снятии также принимаются &#39; и числовые ссылки на эти пять символов. " +
                    "Неизвестные сущности остаются как есть.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("a < b & \"c\" 'd'") },
                minLines = 5,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { result = escapeXml(source) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Экранировать") }
                FilledTonalButton(
                    onClick = { result = unescapeXml(source) },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Снять экранирование") }
            }
            OutlinedTextField(
                value = result,
                onValueChange = { result = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Результат") },
                minLines = 5,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            FilledTonalButton(
                onClick = { copy(result) },
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать")
            }
        }
    }
}

private fun escapeXml(text: String): String = buildString(text.length) {
    for (c in text) {
        when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&apos;")
            else -> append(c)
        }
    }
}

/** Один проход: &amp;lt; становится &lt;, а не <. */
private fun unescapeXml(text: String): String = buildString(text.length) {
    var index = 0
    while (index < text.length) {
        if (text[index] != '&') {
            append(text[index])
            index++
            continue
        }
        val semi = text.indexOf(';', index + 1)
        val body = if (semi > index && semi - index <= MAX_ENTITY) text.substring(index + 1, semi) else null
        val decoded = body?.let(::decodeEntity)
        if (decoded == null) {
            append('&')
            index++
        } else {
            append(decoded)
            index = semi + 1
        }
    }
}

private fun decodeEntity(body: String): Char? = when (body) {
    "amp" -> '&'
    "lt" -> '<'
    "gt" -> '>'
    "quot" -> '"'
    "apos" -> '\''
    else -> decodeNumeric(body)
}

private fun decodeNumeric(body: String): Char? {
    if (body.length < 2 || body[0] != '#') return null
    val value = if (body[1] == 'x' || body[1] == 'X') {
        if (body.length == 2) return null
        body.substring(2).toIntOrNull(16)
    } else {
        body.substring(1).toIntOrNull()
    } ?: return null
    return when (value) {
        '&'.code -> '&'
        '<'.code -> '<'
        '>'.code -> '>'
        '"'.code -> '"'
        '\''.code -> '\''
        else -> null
    }
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("XML", value))
    true
} catch (_: RuntimeException) {
    false
}

private const val MAX_ENTITY = 16
