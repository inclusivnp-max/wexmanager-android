package client.wexmanager.feature.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import client.wexmanager.appContainer
import client.wexmanager.core.settings.AppJson
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.core.settings.UserData
import client.wexmanager.core.util.FileOpener
import client.wexmanager.ui.components.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
private data class Backup(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val settings: AppSettings,
    val userData: UserData,
)

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    var savedBackup by remember { mutableStateOf<File?>(null) }
    var offerShare by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Не удалось прочитать файл")
                }
                val backup = AppJson.decodeFromString(Backup.serializer(), text)
                container.settings.replace(backup.settings.copy(onboardingDone = true))
                container.userData.restore(backup.userData)
            }
            Toast.makeText(
                context,
                result.fold({ "Настройки восстановлены" }, { "Ошибка импорта: ${it.message}" }),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    SettingsScaffold("Резервная копия", onBack, subtitle = "Настройки, закладки, свои иконки") {
        settingsGroup("Копия") {
            click("Экспорт", Icons.Rounded.Upload, "Сохранить в WexManager/Backups") {
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        runCatching {
                            val dir = File(container.storage.primaryPath, "WexManager/Backups").apply { mkdirs() }
                            val name = "wex_backup_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".json"
                            // Секреты и привязанные к устройству ключи в файл не пишем.
                            val backup = Backup(
                                settings = container.settings.current,
                                userData = container.userData.data.value.copy(archivePasswords = emptyList(), signingKeys = emptyList(), activeSigningKey = null),
                            )
                            File(dir, name).apply { writeText(AppJson.encodeToString(Backup.serializer(), backup)) }
                        }.getOrNull()
                    }
                    if (file != null) {
                        savedBackup = file
                        offerShare = true
                        Toast.makeText(context, "Сохранено: ${file.path}", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Не удалось сохранить", Toast.LENGTH_LONG).show()
                    }
                }
            }
            savedBackup?.let { file ->
                click("Поделиться", Icons.Rounded.Share, file.name) { shareBackup(context, file) }
            }
            click("Импорт", Icons.Rounded.Download, "Восстановить из JSON-файла") {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
            }
        }
        settingsGroup("Сброс") {
            click("Сбросить настройки", Icons.Rounded.RestartAlt, "Вернуть значения по умолчанию (закладки сохранятся)") { confirmReset = true }
        }
    }
    if (confirmReset) {
        ConfirmDialog(
            title = "Сбросить настройки?",
            text = "Внешний вид, поведение и редактор вернутся к значениям по умолчанию.",
            icon = Icons.Rounded.RestartAlt,
            confirmLabel = "Сбросить",
            destructive = true,
            onConfirm = { scope.launch { container.settings.replace(AppSettings(onboardingDone = true)) } },
            onDismiss = { confirmReset = false },
        )
    }
    val pending = savedBackup
    if (offerShare && pending != null) {
        AlertDialog(
            onDismissRequest = { offerShare = false },
            icon = { Icon(Icons.Rounded.Share, null) },
            title = { Text("Копия сохранена") },
            text = { Text(pending.path) },
            confirmButton = {
                TextButton(onClick = {
                    offerShare = false
                    shareBackup(context, pending)
                }) { Text("Поделиться") }
            },
            dismissButton = {
                TextButton(onClick = { offerShare = false }) { Text("Закрыть") }
            },
        )
    }
}

private fun shareBackup(context: Context, file: File) {
    runCatching { FileOpener.uriFor(context, file) }.onFailure {
        Toast.makeText(context, "Не удалось поделиться: ${it.message}", Toast.LENGTH_LONG).show()
        return
    }
    runCatching { FileOpener.share(context, listOf(file)) }.onFailure {
        Toast.makeText(context, "Не удалось поделиться: ${it.message}", Toast.LENGTH_LONG).show()
    }
}
