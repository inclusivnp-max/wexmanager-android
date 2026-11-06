package client.wexmanager.feature.extras

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import client.wexmanager.R
import client.wexmanager.core.util.HashAlgorithm
import client.wexmanager.core.util.Hashing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val Mono = FontFamily(Font(R.font.jetbrains_mono))

/** Файлы этого размера и больше не хешируем. */
private const val MAX_FILE_BYTES = 200L * 1024 * 1024

private val Algorithms = listOf(HashAlgorithm.MD5, HashAlgorithm.SHA256, HashAlgorithm.CRC32)

private class ChecksumException(message: String) : Exception(message)

private data class Sums(val md5: String, val sha256: String, val crc32: String)

@Composable
fun ChecksumScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    var path by rememberSaveable { mutableStateOf("") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }
    var sums by remember { mutableStateOf<Sums?>(null) }
    var working by remember { mutableStateOf(false) }
    var ticket by remember { mutableIntStateOf(0) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun compute() {
        focus.clearFocus()
        val trimmed = path.trim()
        if (trimmed.isEmpty()) {
            job?.cancel()
            job = null
            ticket++
            working = false
            sums = null
            errorText = "Укажите путь к файлу"
            return
        }
        val mine = ++ticket
        working = true
        errorText = null
        job?.cancel()
        job = scope.launch {
            try {
                val computed = checksumsOf(trimmed)
                if (mine != ticket) return@launch
                sums = computed
                errorText = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: ChecksumException) {
                if (mine != ticket) return@launch
                sums = null
                errorText = e.message
            } catch (_: Exception) {
                if (mine != ticket) return@launch
                sums = null
                errorText = "Не удалось прочитать файл"
            } finally {
                if (mine == ticket) working = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Контрольные суммы") },
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
                "MD5, SHA-256 и CRC32 для обычного файла меньше 200 МБ.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = path,
                onValueChange = {
                    path = it
                    errorText = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
                singleLine = true,
                label = { Text("Путь к файлу") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { compute() }),
            )
            if (errorText != null) {
                Text(errorText!!, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { compute() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !working,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(if (working) "Считаю…" else "Посчитать")
            }
            if (working) {
                LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
            val ready = sums
            if (ready != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        listOf(
                            HashAlgorithm.MD5.label to ready.md5,
                            HashAlgorithm.SHA256.label to ready.sha256,
                            HashAlgorithm.CRC32.label to ready.crc32,
                        ).forEach { (label, hex) ->
                            HashRow(label, hex) {
                                val copied = copyHex(context, label, hex)
                                scope.launch {
                                    snackbar.showSnackbar(if (copied) "Скопировано" else "Не удалось скопировать")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HashRow(label: String, hex: String, onCopy: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.primary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelectionContainer(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                Text(
                    hex,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = Mono,
                    softWrap = false,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onCopy, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.ContentCopy, "Копировать")
            }
        }
    }
}

private suspend fun checksumsOf(path: String): Sums = withContext(Dispatchers.IO) {
    val file = File(path)
    when {
        !file.exists() -> throw ChecksumException("Файл не найден")
        !file.isFile -> throw ChecksumException("Это не обычный файл")
        file.length() >= MAX_FILE_BYTES -> throw ChecksumException("Файл должен быть меньше 200 МБ")
    }
    val computed = Hashing.compute(file, Algorithms)
    fun hex(alg: HashAlgorithm): String =
        computed[alg] ?: throw ChecksumException("Не удалось посчитать ${alg.label}")
    Sums(hex(HashAlgorithm.MD5), hex(HashAlgorithm.SHA256), hex(HashAlgorithm.CRC32))
}

private fun copyHex(context: Context, label: String, hex: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: error("clipboard")
    manager.setPrimaryClip(ClipData.newPlainText(label, hex))
    true
} catch (_: Exception) {
    false
}
