package client.wexmanager.feature.extras

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.launch

/** Смена регистра: прописные, строчные и как в предложении — по строкам, локаль по умолчанию. */
@Composable
fun CaseScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var text by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }

    fun apply(transform: (String) -> String) {
        result = transform(text)
    }

    fun copyResult() {
        val copied = result.isNotEmpty() && copyPlain(context, result)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    result.isEmpty() -> "Нечего копировать"
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
                title = { Text("Регистр") },
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
                "Прописные и строчные меняют весь текст. «Как в предложении» поднимает первый символ каждой строки, остальные делает строчными.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Текст") },
                placeholder = { Text("Вставьте или введите текст") },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 6,
                maxLines = 10,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { apply { it.uppercase(Locale.getDefault()) } },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("ПРОПИСНЫЕ") }
                FilledTonalButton(
                    onClick = { apply { it.lowercase(Locale.getDefault()) } },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("строчные") }
                FilledTonalButton(
                    onClick = { apply { sentenceCase(it) } },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Как в предложении") }
            }
            OutlinedTextField(
                value = result,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Результат") },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 6,
                maxLines = 10,
            )
            FilledTonalButton(
                onClick = ::copyResult,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Копировать")
            }
        }
    }
}

/** Первая кодовая точка строки — верхний регистр, остальное — нижний. Разделители строк сохраняются. */
private fun sentenceCase(text: String): String {
    val locale = Locale.getDefault()
    if (text.isEmpty()) return text
    val out = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val found = text.indexOfAny(LINE_BREAKS, index)
        val breakAt = if (found < 0) text.length else found
        out.append(sentenceLine(text.substring(index, breakAt), locale))
        if (breakAt >= text.length) break
        val separatorEnd = if (
            text[breakAt] == '\r' &&
            breakAt + 1 < text.length &&
            text[breakAt + 1] == '\n'
        ) {
            breakAt + 2
        } else {
            breakAt + 1
        }
        out.append(text, breakAt, separatorEnd)
        index = separatorEnd
    }
    return out.toString()
}

private fun sentenceLine(line: String, locale: Locale): String {
    if (line.isEmpty()) return line
    val firstEnd = line.offsetByCodePoints(0, 1)
    return line.substring(0, firstEnd).uppercase(locale) + line.substring(firstEnd).lowercase(locale)
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Регистр", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
