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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlin.random.Random
import kotlinx.coroutines.launch

private const val NameLength = 8
private const val NameAlphabet = "abcdefghijklmnopqrstuvwxyz0123456789"

@Composable
fun T04RandomNameScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var stem by rememberSaveable { mutableStateOf("") }
    var extension by rememberSaveable { mutableStateOf("txt") }
    val fileName = joinName(stem, extension)

    fun copy() {
        val copied = fileName.isNotEmpty() && copyPlain(context, fileName)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(
                when {
                    fileName.isEmpty() -> "Нечего копировать"
                    copied -> "Скопировано"
                    else -> "Не удалось скопировать"
                },
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Случайное имя") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Назад")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Кнопка собирает имя из 8 случайных символов и указанного расширения.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = extension,
                onValueChange = { raw ->
                    extension = raw.trim().trimStart('.').filter { ch ->
                        ch.isLetterOrDigit() || ch == '.' || ch == '_' || ch == '-'
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Расширение") },
                placeholder = { Text("txt", fontFamily = FontFamily.Monospace) },
                prefix = { Text(".") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { stem = randomStem() },
                    shapes = ButtonDefaults.shapes(),
                ) { Text("Сгенерировать") }
                FilledTonalButton(
                    onClick = { copy() },
                    enabled = fileName.isNotEmpty(),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        Icons.Rounded.ContentCopy,
                        contentDescription = "Копировать",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать")
                }
            }
            Text(
                text = fileName.ifEmpty { "Имя появится здесь" },
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace,
                color = if (fileName.isEmpty()) scheme.onSurfaceVariant else scheme.onSurface,
            )
        }
    }
}

private fun randomStem(): String = buildString(NameLength) {
    repeat(NameLength) {
        append(NameAlphabet[Random.nextInt(NameAlphabet.length)])
    }
}

private fun joinName(stem: String, extension: String): String {
    if (stem.isEmpty()) return ""
    val ext = extension.trim().trimStart('.')
    return if (ext.isEmpty()) stem else "$stem.$ext"
}

private fun copyPlain(context: Context, value: String): Boolean = try {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: error("clipboard")
    clipboard.setPrimaryClip(ClipData.newPlainText("Имя файла", value))
    true
} catch (_: RuntimeException) {
    false
}
