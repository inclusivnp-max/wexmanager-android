package client.wexmanager.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import client.wexmanager.appContainer
import client.wexmanager.core.apk.ApkSigning
import client.wexmanager.core.apk.SigningKeys
import client.wexmanager.core.settings.SigningKeyMeta
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Как runCatching, но отмену корутины не перехватывает. */
private suspend inline fun <T> attempt(crossinline block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

@Composable
fun SigningKeysScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val userData = context.appContainer.userData
    val data by userData.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val builtin by produceState<SigningKeys.Info?>(null) { value = attempt { SigningKeys.builtinInfo() }.getOrNull() }
    // Ключи, которых нет в Keystore (например, после переноса на другое устройство).
    val missing by produceState(emptySet<String>(), data.signingKeys) {
        value = withContext(Dispatchers.IO) { data.signingKeys.filterNot { SigningKeys.exists(it.alias) }.mapTo(HashSet()) { it.alias } }
    }
    var storeUri by remember { mutableStateOf<Uri?>(null) }
    var pk8Uri by remember { mutableStateOf<Uri?>(null) }
    var certUri by remember { mutableStateOf<Uri?>(null) }
    var deleteTarget by remember { mutableStateOf<SigningKeyMeta?>(null) }

    val pickStore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { storeUri = it }
    val pickCert = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) certUri = uri else pk8Uri = null
    }
    val pickPk8 = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pk8Uri = uri
            Toast.makeText(context, "Теперь выберите сертификат (.x509.pem)", Toast.LENGTH_SHORT).show()
            pickCert.launch(arrayOf("*/*"))
        }
    }

    fun shareCertificate(alias: String?, name: String) {
        scope.launch {
            val file = attempt {
                withContext(Dispatchers.IO) {
                    val cert = ApkSigning.certificate(alias)
                    val dir = File(context.cacheDir, "share").apply { mkdirs() }
                    File(dir, name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_") + ".x509.pem").apply { writeText(SigningKeys.toPem(cert)) }
                }
            }.getOrElse {
                Toast.makeText(context, it.message ?: "Не удалось получить сертификат", Toast.LENGTH_LONG).show()
                return@launch
            }
            FileOpener.share(context, listOf(file))
        }
    }

    SettingsScaffold("Ключи подписи APK", onBack, subtitle = "Каким ключом подписывать APK") {
        settingsGroup("Активный ключ", key = "keys") {
            custom { shapes ->
                KeyRow(
                    shapes = shapes,
                    title = "WexManager (встроенный)",
                    lines = builtin?.let { listOf("${it.algorithm} · до ${Format.day(it.notAfter)}", "SHA-256 ${it.sha256.take(17)}…") }
                        ?: listOf("Создаётся при первой подписи"),
                    sha256 = builtin?.sha256,
                    selected = data.activeSigningKey == null,
                    warning = null,
                    onSelect = { userData.setActiveSigningKey(null) },
                    onShare = { shareCertificate(null, "WexManager") },
                    onDelete = null,
                )
            }
            data.signingKeys.forEach { k ->
                custom { shapes ->
                    val lost = k.alias in missing
                    KeyRow(
                        shapes = shapes,
                        title = k.name,
                        lines = listOf("${k.algorithm} · до ${Format.day(k.notAfter)}", "SHA-256 ${k.sha256.take(17)}…"),
                        sha256 = k.sha256,
                        selected = data.activeSigningKey == k.alias,
                        warning = if (lost) "Ключа нет на этом устройстве — импортируйте заново" else null,
                        onSelect = { if (!lost) userData.setActiveSigningKey(k.alias) },
                        onShare = if (lost) null else ({ shareCertificate(k.alias, k.name) }),
                        onDelete = { deleteTarget = k },
                    )
                }
            }
        }
        settingsGroup("Импорт ключа", key = "import") {
            click("Хранилище ключей", Icons.Rounded.Key, ".p12, .pfx, .keystore, .bks — PKCS12 или BKS") { pickStore.launch(arrayOf("*/*")) }
            click("Ключ .pk8 + сертификат .x509.pem", Icons.Rounded.VpnKey, "Как testkey в AOSP: сначала ключ, затем сертификат") { pickPk8.launch(arrayOf("*/*")) }
        }
        item(key = "hint") {
            SettingsHint(
                "Ключ переносится в защищённое хранилище Android и не покидает устройство, пароли не сохраняются. " +
                    "APK, подписанный другим ключом, нельзя установить поверх уже установленного — только после удаления.",
            )
        }
    }

    storeUri?.let { uri ->
        KeyStoreImportDialog(
            uri = uri,
            onDismiss = { storeUri = null },
        ) { meta ->
            storeUri = null
            userData.addSigningKey(meta, makeActive = true)
            Toast.makeText(context, "Ключ «${meta.name}» добавлен и выбран", Toast.LENGTH_SHORT).show()
        }
    }
    val key = pk8Uri
    val cert = certUri
    if (key != null && cert != null) {
        Pk8ImportDialog(
            keyUri = key,
            certUri = cert,
            onDismiss = { pk8Uri = null; certUri = null },
        ) { meta ->
            pk8Uri = null
            certUri = null
            userData.addSigningKey(meta, makeActive = true)
            Toast.makeText(context, "Ключ «${meta.name}» добавлен и выбран", Toast.LENGTH_SHORT).show()
        }
    }
    deleteTarget?.let { k ->
        ConfirmDialog(
            title = "Удалить ключ «${k.name}»?",
            text = "Ключ будет стёрт из хранилища устройства. Восстановить его можно только повторным импортом из файла." +
                if (data.activeSigningKey == k.alias) "\n\nЭто активный ключ: дальше APK будут подписываться встроенным ключом WexManager — такие APK не установятся поверх подписанных этим ключом." else "",
            icon = Icons.Rounded.Delete,
            confirmLabel = "Удалить",
            destructive = true,
            onConfirm = {
                deleteTarget = null
                userData.removeSigningKey(k.alias)
                // В области приложения: удаление из Keystore не должно оборваться при уходе с экрана.
                context.appContainer.appScope.launch(Dispatchers.IO) { SigningKeys.delete(k.alias) }
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun KeyRow(
    shapes: androidx.compose.material3.ListItemShapes,
    title: String,
    lines: List<String>,
    sha256: String?,
    selected: Boolean,
    warning: String?,
    onSelect: () -> Unit,
    onShare: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    SegmentedListItem(
        onClick = onSelect,
        shapes = shapes,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = {
            Column {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (warning != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                        Text(" $warning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        trailingContent = {
            Row {
                if (!sha256.isNullOrEmpty()) {
                    IconButton(onClick = { copySha256(context, sha256) }) {
                        Icon(Icons.Rounded.ContentCopy, "Скопировать SHA-256")
                    }
                }
                if (onShare != null) IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Поделиться сертификатом") }
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Удалить") }
            }
        },
    ) { Text(title) }
}

private fun copySha256(context: Context, sha256: String) {
    val copied = runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: error("clipboard")
        cm.setPrimaryClip(ClipData.newPlainText("SHA-256", sha256))
    }.isSuccess
    if (copied) Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

@Composable
private fun rememberDisplayName(uri: Uri): String {
    val context = LocalContext.current
    val name by produceState("", uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: ""
        }
    }
    return name
}

@Composable
private fun KeyStoreImportDialog(uri: Uri, onDismiss: () -> Unit, onImported: (SigningKeyMeta) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fileName = rememberDisplayName(uri)
    var name by remember { mutableStateOf<String?>(null) }
    var storePassword by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf<String?>(null) }
    var aliases by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Файл читаем один раз (у облачных провайдеров повторное чтение — это повторная загрузка).
    var readError by remember { mutableStateOf<String?>(null) }
    val bytes by produceState<ByteArray?>(null, uri) {
        value = attempt { SigningKeys.readStore(context, uri) }.onFailure { readError = it.message ?: "Не удалось прочитать файл" }.getOrNull()
    }
    // Проверяем пароль на лету: при совпадении показываем ключи хранилища.
    LaunchedEffect(storePassword, bytes) {
        val data = bytes ?: return@LaunchedEffect
        aliases = null
        delay(450)
        val result = attempt { SigningKeys.aliases(data, storePassword) }
        aliases = result.getOrNull()
        if (alias !in aliases.orEmpty()) alias = aliases?.firstOrNull()
        error = result.exceptionOrNull()?.takeIf { storePassword.isNotEmpty() || it.message?.contains("JKS") == true }?.message
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.Key, null) },
        title = { Text("Импорт хранилища") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fileName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = name ?: fileName.substringBeforeLast('.'),
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = storePassword,
                    onValueChange = { storePassword = it; error = null },
                    label = { Text("Пароль хранилища") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = { if (!aliases.isNullOrEmpty()) Icon(Icons.Rounded.CheckCircle, "Пароль подходит", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.fillMaxWidth(),
                )
                val list = aliases
                AnimatedVisibility(list != null && list.size > 1) {
                    Column {
                        Text("Ключ в хранилище", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.orEmpty().forEach { a -> FilterChip(selected = a == alias, onClick = { alias = a }, label = { Text(a) }) }
                        }
                    }
                }
                OutlinedTextField(
                    value = keyPassword,
                    onValueChange = { keyPassword = it; error = null },
                    label = { Text("Пароль ключа (если отличается)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                (error ?: readError)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val data = bytes
                        val r = if (data == null) Result.failure(IllegalStateException(readError ?: "Файл ещё читается")) else attempt {
                            SigningKeys.importKeyStore(data, name ?: fileName.substringBeforeLast('.'), storePassword, alias, keyPassword.ifEmpty { null })
                        }
                        busy = false
                        r.onSuccess(onImported).onFailure { error = it.message ?: "Не удалось импортировать ключ" }
                    }
                },
            ) {
                if (busy) Box(Modifier.size(20.dp)) { LoadingIndicator() } else Text("Импортировать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } },
    )
}

@Composable
private fun Pk8ImportDialog(keyUri: Uri, certUri: Uri, onDismiss: () -> Unit, onImported: (SigningKeyMeta) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyName = rememberDisplayName(keyUri)
    val certName = rememberDisplayName(certUri)
    var name by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.VpnKey, null) },
        title = { Text("Импорт ключа") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ключ: $keyName\nСертификат: $certName", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name ?: keyName.substringBefore('.'),
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val r = attempt { SigningKeys.importPk8(context, keyUri, certUri, name ?: keyName.substringBefore('.')) }
                        busy = false
                        r.onSuccess(onImported).onFailure { error = it.message ?: "Не удалось импортировать ключ" }
                    }
                },
            ) {
                if (busy) Box(Modifier.size(20.dp)) { LoadingIndicator() } else Text("Импортировать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } },
    )
}
