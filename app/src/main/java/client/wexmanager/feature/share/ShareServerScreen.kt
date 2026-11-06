package client.wexmanager.feature.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

@Composable
fun ShareServerScreen(onBack: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var rootText by rememberSaveable { mutableStateOf(defaultShareRoot()) }
    var portText by rememberSaveable { mutableStateOf("8080") }
    var url by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val server = remember { mutableStateOf<ShareHttpServer?>(null) }
    val requestCount by (server.value?.requests ?: idleRequests).collectAsState(initial = 0)
    val running = url != null

    fun stop() {
        server.value?.close()
        server.value = null
        url = null
    }

    DisposableEffect(Unit) {
        onDispose { server.value?.close() }
    }

    fun start() {
        if (server.value != null) return
        val port = portText.toIntOrNull()
        if (port == null || port !in 1..65535) {
            error = "Порт должен быть от 1 до 65535"
            return
        }
        val path = rootText.trim()
        if (path.isEmpty()) {
            error = "Укажите папку"
            return
        }
        val dir = try {
            File(path).canonicalFile
        } catch (_: IOException) {
            error = "Не удалось открыть папку"
            return
        }
        if (!dir.isDirectory) {
            error = "Папка не найдена"
            return
        }
        val created = ShareHttpServer(dir, port)
        server.value = created
        try {
            created.start(scope)
        } catch (failure: ShareBindException) {
            created.close()
            server.value = null
            url = null
            error = failure.message ?: "Не удалось запустить сервер"
            return
        } catch (cancelled: CancellationException) {
            created.close()
            server.value = null
            url = null
            throw cancelled
        } catch (_: Exception) {
            created.close()
            server.value = null
            url = null
            error = "Не удалось запустить сервер"
            return
        }
        rootText = dir.path
        url = created.url
        error = null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Раздача файлов") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            stop()
                            onBack()
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Папка открывается по HTTP в локальной сети. Доступны только GET и HEAD, выйти за её пределы нельзя. Сервер останавливается кнопкой или при уходе с экрана.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = rootText,
                onValueChange = {
                    rootText = it
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                singleLine = true,
                label = { Text("Корневая папка") },
                supportingText = { Text("Только эта папка и её содержимое") },
                leadingIcon = { Icon(Icons.Rounded.Folder, null) },
            )
            OutlinedTextField(
                value = portText,
                onValueChange = { value ->
                    portText = value.filter { it.isDigit() }.take(5)
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                singleLine = true,
                label = { Text("Порт") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            if (error != null) {
                Text(error!!, color = scheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { if (running) stop() else start() },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
                colors = if (running) {
                    ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError)
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(if (running) "Остановить" else "Запустить")
            }
            if (url != null) {
                val shareUrl = url!!
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = scheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Сервер запущен", style = MaterialTheme.typography.titleMedium)
                        Text("Запросов: $requestCount", style = MaterialTheme.typography.titleMedium)
                        SelectionContainer {
                            Text(shareUrl, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                        }
                        Text(
                            "Откройте ссылку на другом устройстве в той же сети.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        FilledTonalButton(
                            onClick = {
                                val copied = copyUrl(context, shareUrl)
                                scope.launch {
                                    snackbar.showSnackbar(if (copied) "Ссылка скопирована" else "Не удалось скопировать")
                                }
                            },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Копировать ссылку")
                            }
                        }
                    }
                }
            }
        }
    }
}

private val idleRequests = flowOf(0)

private fun defaultShareRoot(): String {
    val primary = Environment.getExternalStorageDirectory()
    val download = File(primary, Environment.DIRECTORY_DOWNLOADS)
    return if (download.isDirectory) download.absolutePath else primary.absolutePath
}

private fun copyUrl(context: Context, url: String): Boolean = try {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
    manager.setPrimaryClip(ClipData.newPlainText("url", url))
    true
} catch (_: RuntimeException) {
    false
}
