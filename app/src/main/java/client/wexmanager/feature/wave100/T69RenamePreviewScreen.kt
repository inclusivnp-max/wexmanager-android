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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val TOKEN_N = "{n}"
private const val TOKEN_NAME = "{name}"

/** Предпросмотр новых имён по шаблону. Файлы не читаются и не записываются. */
@Composable
fun T69RenamePreviewScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var names by rememberSaveable { mutableStateOf("") }
    var pattern by rememberSaveable { mutableStateOf("{n}_{name}") }
    val result = remember(names, pattern) { previewRenames(names, pattern) }

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
                title = { Text("Предпросмотр переименования") },
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
                "Имена по одному в строке. В шаблоне {n} — номер с единицы, {name} — исходное имя. На диск ничего не пишется.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Шаблон") },
                placeholder = { Text("{n}_{name}", fontFamily = FontFamily.Monospace) },
                supportingText = { Text("{n} начинается с 1. {name} подставляется как есть.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedTextField(
                value = names,
                onValueChange = { names = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имена") },
                placeholder = { Text("photo.jpg\nnotes.txt") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                minLines = 6,
                maxLines = 10,
            )
            OutlinedTextField(
                value = result,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                label = { Text("Новые имена") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
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

/** Пустые строки пропускаются. Номер растёт только у непустых имён и начинается с 1. */
private fun previewRenames(names: String, pattern: String): String {
    if (names.isEmpty() || pattern.isEmpty()) return ""
    val out = ArrayList<String>()
    var number = 1
    var index = 0
    while (index < names.length) {
        val found = names.indexOfAny(LINE_BREAKS, index)
        val breakAt = if (found < 0) names.length else found
        val line = names.substring(index, breakAt).trim()
        if (line.isNotEmpty()) {
            out.add(applyPattern(pattern, number, line))
            number++
        }
        if (breakAt >= names.length) break
        val separatorEnd = if (
            names[breakAt] == '\r' &&
            breakAt + 1 < names.length &&
            names[breakAt + 1] == '\n'
        ) {
            breakAt + 2
        } else {
            breakAt + 1
        }
        if (separatorEnd >= names.length) break
        index = separatorEnd
    }
    return out.joinToString("\n")
}

/** Токены заменяются один раз. Символы из имени не разбираются как шаблон. */
private fun applyPattern(pattern: String, number: Int, name: String): String {
    val out = StringBuilder(pattern.length + name.length + 8)
    var index = 0
    while (index < pattern.length) {
        when {
            pattern.startsWith(TOKEN_N, index) -> {
                out.append(number)
                index += TOKEN_N.length
            }
            pattern.startsWith(TOKEN_NAME, index) -> {
                out.append(name)
                index += TOKEN_NAME.length
            }
            else -> {
                out.append(pattern[index])
                index++
            }
        }
    }
    return out.toString()
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("Новые имена", value))
    true
} catch (_: RuntimeException) {
    false
}

private val LINE_BREAKS = charArrayOf('\n', '\r')
