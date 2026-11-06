package client.wexmanager.feature.wave100

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Base64
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Средняя часть JWT: Base64 URL и отступы JSON. Подпись не читается и не проверяется. */
@Composable
fun T74JwtScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var source by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }

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
                title = { Text("JWT") },
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
                "Подпись не проверяется. Токен только делится по точкам, средняя часть " +
                    "раскодируется из Base64 URL и, если это JSON, показывается с отступами. " +
                    "Сеть не используется: подлинность, издатель и срок действия не подтверждаются.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = source,
                onValueChange = {
                    source = it
                    error = ""
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("JWT") },
                placeholder = { Text("заголовок.нагрузка.подпись") },
                minLines = 4,
                maxLines = 10,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
            )
            Button(
                onClick = {
                    try {
                        val decoded = decodeJwtMiddle(source)
                        result = decoded.text
                        note = if (decoded.json) {
                            "JSON с отступами. Подпись не проверялась."
                        } else {
                            "Это не JSON — показан текст как есть. Подпись не проверялась."
                        }
                        error = ""
                    } catch (e: IllegalArgumentException) {
                        result = ""
                        note = ""
                        error = e.message ?: "Не удалось разобрать JWT"
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) { Text("Разобрать") }
            if (error.isNotEmpty()) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.error,
                )
            }
            if (note.isNotEmpty()) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            SelectionContainer {
                OutlinedTextField(
                    value = result,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Средняя часть") },
                    minLines = 6,
                    maxLines = 16,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                )
            }
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

private data class DecodedMiddle(val text: String, val json: Boolean)

/** Вторая из трёх частей. Первую и третью не раскодируем и подпись не сверяем. */
private fun decodeJwtMiddle(raw: String): DecodedMiddle {
    val compact = raw.trim().filterNot { it.isWhitespace() }
    if (compact.isEmpty()) throw IllegalArgumentException("Вставьте JWT")
    val parts = compact.split('.')
    if (parts.size < 3) {
        throw IllegalArgumentException(
            "Нужны три части через точку. Подпись не проверяется — третья часть только отделяет нагрузку.",
        )
    }
    val middle = parts[1]
    if (middle.isEmpty()) throw IllegalArgumentException("Средняя часть пустая")
    val text = utf8(decodeBase64Url(middle))
    val pretty = prettyJson(text)
    return if (pretty != null) DecodedMiddle(pretty, true) else DecodedMiddle(text, false)
}

private fun decodeBase64Url(segment: String): ByteArray {
    if (segment.any { it == '+' || it == '/' }) {
        throw IllegalArgumentException("Нужен Base64 URL (− и _), а не обычный Base64")
    }
    val body = segment.trimEnd('=')
    if (body.isEmpty() || body.any { it == '=' }) {
        throw IllegalArgumentException("Средняя часть — не Base64 URL")
    }
    val remainder = body.length % 4
    if (remainder == 1) throw IllegalArgumentException("Средняя часть — не Base64 URL")
    val padded = body + "=".repeat((4 - remainder) % 4)
    return try {
        Base64.getUrlDecoder().decode(padded)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Средняя часть — не Base64 URL")
    }
}

private fun utf8(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
} catch (_: CharacterCodingException) {
    throw IllegalArgumentException("Средняя часть — не текст UTF-8")
}

/** Отступы только для объекта или массива. Иначе null — показываем исходный текст. */
private fun prettyJson(text: String): String? {
    val trimmed = text.trim()
    return try {
        when {
            trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
            else -> null
        }
    } catch (_: JSONException) {
        null
    }
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("JWT", value))
    true
} catch (_: RuntimeException) {
    false
}
