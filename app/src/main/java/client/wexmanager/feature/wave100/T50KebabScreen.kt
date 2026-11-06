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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Текст в kebab-case: строчные, слова через дефис, границы CamelCase и разделители. */
@Composable
fun T50KebabScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var source by rememberSaveable { mutableStateOf("") }
    val kebab = remember(source) { toKebabCase(source) }

    fun copyKebab() {
        val copied = kebab.isNotEmpty() && copyPlain(context, kebab)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    kebab.isEmpty() -> "Нечего копировать"
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
                title = { Text("Kebab-case") },
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
                "Строчные буквы, слова через дефис. Пробелы, подчёркивания и прочие разделители схлопываются. CamelCase разбивается по границам слов.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = source,
                onValueChange = { source = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Введите текст") },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 3,
                maxLines = 6,
            )
            OutlinedTextField(
                value = kebab,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Kebab-case") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 6,
            )
            FilledTonalButton(
                onClick = ::copyKebab,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать")
            }
        }
    }
}

/**
 * Строчные буквы и цифры, слова через один дефис.
 * Граница: строчная/цифра перед заглавной, буква перед цифрой и цифра перед буквой,
 * а также конец серии заглавных перед словом (HTMLParser → html-parser).
 */
private fun toKebabCase(raw: String): String {
    if (raw.isEmpty()) return ""
    val out = StringBuilder(raw.length + 8)
    var index = 0
    var prev = -1
    while (index < raw.length) {
        val codePoint = raw.codePointAt(index)
        val count = Character.charCount(codePoint)
        val letter = Character.isLetter(codePoint)
        val digit = Character.isDigit(codePoint)
        if (prev >= 0 && (letter || digit)) {
            val prevLetter = Character.isLetter(prev)
            val prevDigit = Character.isDigit(prev)
            val prevLower = Character.isLowerCase(prev)
            val prevUpper = Character.isUpperCase(prev)
            val upper = Character.isUpperCase(codePoint)
            val next = if (index + count < raw.length) raw.codePointAt(index + count) else -1
            val nextLower = next >= 0 && Character.isLowerCase(next)
            val boundary = (prevLower && upper) ||
                (prevLetter && digit) ||
                (prevDigit && letter) ||
                (prevUpper && upper && nextLower)
            if (boundary && out.isNotEmpty() && out.last() != '-') out.append('-')
        }
        when {
            letter || digit -> out.appendCodePoint(Character.toLowerCase(codePoint))
            out.isNotEmpty() && out.last() != '-' -> out.append('-')
        }
        prev = codePoint
        index += count
    }
    var start = 0
    var end = out.length
    while (start < end && out[start] == '-') start++
    while (end > start && out[end - 1] == '-') end--
    return out.substring(start, end)
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Kebab-case", value))
    true
} catch (_: RuntimeException) {
    false
}
