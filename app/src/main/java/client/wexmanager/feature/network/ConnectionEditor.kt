package client.wexmanager.feature.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import client.wexmanager.core.network.HostKeyChangedException
import client.wexmanager.core.network.KnownHosts
import client.wexmanager.core.network.RemoteConnection
import client.wexmanager.core.network.RemoteConnections
import client.wexmanager.core.network.RemoteErrors
import client.wexmanager.core.network.RemoteFs
import client.wexmanager.core.network.RemotePath
import client.wexmanager.core.network.RemoteType
import client.wexmanager.core.network.causes
import client.wexmanager.core.network.closeQuietly
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.SwitchRow
import client.wexmanager.ui.components.ConnectedChoice
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID

@Immutable
private sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val text: String, val title: String = "Соединение установлено") : TestState
    data class Failed(val text: String, val hostKey: HostKeyChangedException?) : TestState
}

private val Encodings = listOf("UTF-8", "windows-1251", "CP866")

private fun encodingLabel(e: String) = when (e) {
    "windows-1251" -> "Win-1251"
    else -> e
}

/**
 * Шторка создания/редактирования подключения с проверкой соединения.
 * [initial] = null — новое подключение.
 */
@Composable
fun ConnectionEditorSheet(
    initial: RemoteConnection?,
    onDismiss: () -> Unit,
    onSaved: (RemoteConnection) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val repo = remember { RemoteConnections.get(app) }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var type by rememberSaveable { mutableStateOf(initial?.type ?: RemoteType.SFTP) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(initial?.host.orEmpty()) }
    var port by rememberSaveable { mutableStateOf((initial?.port ?: type.defaultPort).toString()) }
    var user by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    // Пароль не кладём в сохраняемое состояние (Bundle может попасть на диск).
    var password by remember { mutableStateOf("") }
    var passwordTouched by remember { mutableStateOf(false) }
    // Сохранённый пароль не расшифровался (ключ Keystore утерян после восстановления из копии).
    var passwordLost by remember { mutableStateOf(false) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var anonymous by rememberSaveable { mutableStateOf(initial?.anonymous ?: false) }
    var keyPath by rememberSaveable { mutableStateOf(initial?.privateKeyPath) }
    var keyInfo by rememberSaveable { mutableStateOf<String?>(null) }
    var share by rememberSaveable { mutableStateOf(initial?.share.orEmpty()) }
    // Найденные на сервере шары: null — не искали; ошибка — текстом.
    var shares by remember { mutableStateOf<List<client.wexmanager.core.network.SmbShareInfo>?>(null) }
    var sharesError by remember { mutableStateOf<String?>(null) }
    var sharesLoading by remember { mutableStateOf(false) }
    var initialPath by rememberSaveable { mutableStateOf(initial?.initialPath.orEmpty()) }
    var passive by rememberSaveable { mutableStateOf(initial?.passiveMode ?: true) }
    var encoding by rememberSaveable { mutableStateOf(initial?.encoding ?: "UTF-8") }
    var useTls by rememberSaveable { mutableStateOf(initial?.useTls ?: true) }
    var showErrors by remember { mutableStateOf(false) }
    var test by remember { mutableStateOf<TestState>(TestState.Idle) }
    var testJob by remember { mutableStateOf<Job?>(null) }
    // true — крутится проверка порта, false — полный вход.
    var portProbe by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val invalidateTest = {
        testJob?.cancel()
        test = TestState.Idle
    }
    // Ключи, скопированные в этой шторке: лишние удалим при закрытии.
    val createdKeys = remember { mutableStateListOf<String>() }
    var saved by remember { mutableStateOf(false) }

    LaunchedEffect(initial?.id) {
        if (initial != null) {
            val p = repo.decryptPassword(initial)
            if (p == null) passwordLost = true else if (!passwordTouched) password = p
        }
    }
    LaunchedEffect(keyPath) {
        keyInfo = keyPath?.let { describeKey(it) }
    }

    val ftp = type == RemoteType.FTP || type == RemoteType.FTPS
    val anon = ftp && anonymous
    val portValue = port.toIntOrNull()
    val hostError = host.isBlank()
    val portError = portValue == null || portValue !in 1..65535
    val dav = type == RemoteType.WEBDAV
    val userError = !anon && type != RemoteType.SMB && !dav && user.isBlank()
    val shareError = type == RemoteType.SMB && share.trim('/', '\\', ' ').isEmpty()
    val valid = !hostError && !portError && !userError && !shareError

    /** Полный адрес WebDAV в поле хоста (вставили ссылку из Nextcloud) — раскладываем по полям перед проверкой/сохранением. */
    fun applyDavUrl() {
        if (!dav || !host.contains("://")) return
        val url = runCatching { java.net.URI(host.trim()) }.getOrNull() ?: return
        val h = url.host ?: return
        host = h
        useTls = url.scheme.equals("https", true)
        port = (if (url.port > 0) url.port else if (useTls) 443 else 80).toString()
        url.rawPath?.takeIf { it.length > 1 }?.let { initialPath = java.net.URLDecoder.decode(it, "UTF-8") }
    }

    fun build(): RemoteConnection = (initial ?: RemoteConnection(type = type)).copy(
        name = name.trim(),
        type = type,
        host = host.trim(),
        port = port.toIntOrNull() ?: type.defaultPort, // не portValue: applyDavUrl() мог только что сменить порт
        username = if (anon) "" else user.trim(),
        privateKeyPath = if (type == RemoteType.SFTP) keyPath else null,
        initialPath = initialPath.trim().let { if (it.isEmpty()) "" else RemotePath.normalize(it) },
        share = if (type == RemoteType.SMB) share.trim().trim('/', '\\') else null,
        anonymous = anon,
        passiveMode = passive,
        encoding = encoding,
        useTls = useTls,
    )

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(Unit) {
        onDispose {
            // Закрыли без сохранения — удаляем скопированные ключи.
            if (!saved) repo.deleteUnreferencedKeysAsync(createdKeys.toList())
        }
    }

    val pickKey = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val r = runCatching { importKey(app, uri) }
            r.onSuccess { path ->
                createdKeys += path
                keyPath = path
                invalidateTest()
            }.onFailure { e ->
                test = TestState.Failed(e.message ?: "Не удалось прочитать ключ", null)
            }
        }
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val motion = MaterialTheme.motionScheme
    ModalBottomSheet(onDismissRequest = { currentOnDismiss() }, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(type, transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) }, label = "badge") { t ->
                    TypeBadge(t, size = 52.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (initial == null) "Новое подключение" else "Изменить подключение",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        type.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ConnectedChoice(
                options = RemoteType.entries.toList(),
                selected = type,
                onSelect = { t ->
                    // Порт по умолчанию меняем, только если пользователь его не трогал.
                    if (port.toIntOrNull() == type.defaultPort || port.isBlank()) port = t.defaultPort.toString()
                    type = t
                    invalidateTest()
                },
                label = { it.title },
            )
            Spacer(Modifier.height(16.dp))

            Field(name, { name = it }, "Название", Icons.AutoMirrored.Rounded.Label, placeholder = host.ifBlank { "Мой сервер" })
            Row(verticalAlignment = Alignment.Top) {
                Field(
                    host, { host = it; invalidateTest() }, "Хост", Icons.Rounded.Dns,
                    modifier = Modifier.weight(1f),
                    placeholder = if (dav) "cloud.example.com или полный URL" else "192.168.1.10 или example.com",
                    error = if (showErrors && hostError) "Укажите адрес" else null,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.width(10.dp))
                Field(
                    port, { v -> port = v.filter { it.isDigit() }.take(5); invalidateTest() }, "Порт", null,
                    modifier = Modifier.width(104.dp),
                    error = if (showErrors && portError) "1–65535" else null,
                    keyboardType = KeyboardType.Number,
                    mono = true,
                )
            }

            AnimatedVisibility(ftp, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                SwitchRow("Анонимный вход", Icons.Rounded.Person, anonymous, "Без логина и пароля") { anonymous = it }
            }
            AnimatedVisibility(!anon, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Field(
                        user, { user = it; invalidateTest() }, "Пользователь", Icons.Rounded.Person,
                        placeholder = if (type == RemoteType.SMB) "Пусто — гостевой доступ" else null,
                        supporting = if (type == RemoteType.SMB) "Можно указать домен: ДОМЕН\\пользователь" else null,
                        error = if (showErrors && userError) "Укажите пользователя" else null,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; passwordTouched = true; invalidateTest() },
                        label = { Text(if (type == RemoteType.SFTP && keyPath != null) "Пароль или фраза ключа" else "Пароль") },
                        leadingIcon = { Icon(Icons.Rounded.Password, null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }, shapes = androidx.compose.material3.IconButtonDefaults.shapes()) {
                                Icon(
                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    if (showPassword) "Скрыть пароль" else "Показать пароль",
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                        supportingText = if (passwordLost && !passwordTouched) {
                            { Text("Сохранённый пароль не удалось расшифровать — введите его заново") }
                        } else {
                            null
                        },
                        isError = passwordLost && !passwordTouched,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
            }

            AnimatedVisibility(type == RemoteType.SFTP, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                KeyRow(
                    path = keyPath,
                    info = keyInfo,
                    onPick = { pickKey.launch(arrayOf("*/*")) },
                    onClear = { keyPath = null; invalidateTest() },
                )
            }
            AnimatedVisibility(type == RemoteType.SMB, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(
                            share, { share = it; invalidateTest() }, "Общая папка (шара)", Icons.Rounded.FolderShared,
                            modifier = Modifier.weight(1f),
                            placeholder = "Public",
                            supporting = sharesError ?: "Имя общего ресурса на сервере",
                            error = if (showErrors && shareError) "Укажите имя шары или нажмите «Найти»" else null,
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                if (host.isBlank() || portValue == null) { showErrors = true; return@FilledTonalButton }
                                val conn = build().copy(share = null)
                                val pass = if (anon) "" else password
                                sharesLoading = true
                                sharesError = null
                                scope.launch {
                                    try {
                                        val all = withContext(Dispatchers.IO) { client.wexmanager.core.network.SmbFs.listShares(conn, pass) }
                                        // Служебные (IPC$, ADMIN$, C$) и принтеры не показываем.
                                        val found = all.filter { it.isDisk && !it.name.endsWith("$") }
                                        shares = found
                                        if (found.isEmpty()) sharesError = "Сервер не сообщил ни одной общей папки"
                                        else if (found.size == 1 && share.isBlank()) share = found[0].name
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        sharesError = "Не удалось получить список: ${RemoteErrors.message(e)}"
                                    } finally {
                                        sharesLoading = false
                                    }
                                }
                            },
                            enabled = !sharesLoading,
                            modifier = Modifier.padding(bottom = 18.dp),
                        ) {
                            if (sharesLoading) LoadingIndicator(Modifier.size(20.dp)) else Text("Найти")
                        }
                    }
                    val list = shares
                    AnimatedVisibility(!list.isNullOrEmpty()) {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            list.orEmpty().forEach { sh ->
                                androidx.compose.material3.FilterChip(
                                    selected = share.equals(sh.name, ignoreCase = true),
                                    onClick = { share = sh.name; invalidateTest() },
                                    label = { Text(if (sh.remark.isNotBlank()) "${sh.name} · ${sh.remark}" else sh.name, maxLines = 1) },
                                    leadingIcon = { Icon(Icons.Rounded.FolderShared, null, Modifier.size(18.dp)) },
                                )
                            }
                        }
                    }
                }
            }
            Field(
                initialPath, { initialPath = it; invalidateTest() }, if (dav) "Путь WebDAV" else "Начальная папка", Icons.Rounded.Folder,
                placeholder = when (type) {
                    RemoteType.SFTP -> "Домашняя папка"
                    RemoteType.WEBDAV -> "/remote.php/dav/files/имя"
                    else -> "/"
                },
                supporting = if (dav) "Nextcloud: /remote.php/dav/files/<имя>, Яндекс.Диск: / (хост webdav.yandex.ru)" else null,
                mono = true,
            )
            if (dav) {
                SwitchRow(
                    "HTTPS", Icons.Rounded.Lock, useTls,
                    if (useTls) "Шифрованное соединение" else "Пароль и файлы передаются открыто — только для домашней сети",
                ) { v ->
                    if (port.toIntOrNull() == (if (useTls) 443 else 80)) port = (if (v) 443 else 80).toString()
                    useTls = v
                    invalidateTest()
                }
            }

            if (ftp) {
                SwitchRow("Пассивный режим", null, passive, "Рекомендуется при NAT и мобильной сети") { passive = it }
            }
            if (type != RemoteType.SMB && !dav) {
                Text(
                    "Кодировка имён",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                )
                ConnectedChoice(Encodings, encoding, { encoding = it }, ::encodingLabel)
            }

            AnimatedContent(
                targetState = test.takeIf { it is TestState.Ok || it is TestState.Failed },
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()))
                        .using(SizeTransform(clip = false))
                },
                label = "testResult",
            ) { t ->
                when (t) {
                    is TestState.Ok -> ResultCard(Icons.Rounded.CheckCircle, t.title, t.text, ok = true)
                    is TestState.Failed -> ResultCard(
                        if (t.hostKey != null) Icons.Rounded.GppMaybe else Icons.Rounded.Error,
                        if (t.hostKey != null) "Ключ сервера изменился" else "Не удалось подключиться",
                        if (t.hostKey != null) hostKeyText(t.hostKey) else t.text,
                        ok = false,
                        actionLabel = if (t.hostKey != null) "Доверять новому ключу" else null,
                        onAction = {
                            val hk = t.hostKey
                            if (hk != null) scope.launch {
                                withContext(Dispatchers.IO) { KnownHosts.accept(app, hk) }
                                invalidateTest()
                            }
                        },
                    )
                    else -> Spacer(Modifier.fillMaxWidth())
                }
            }

            Spacer(Modifier.height(20.dp))
            val running = test is TestState.Running
            val tcpRunning = running && portProbe
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        if (tcpRunning) {
                            invalidateTest()
                            return@FilledTonalButton
                        }
                        applyDavUrl()
                        val targetHost = host.trim()
                        val targetPort = port.toIntOrNull()
                        if (targetHost.isBlank() || targetPort == null || targetPort !in 1..65535) {
                            showErrors = true
                            return@FilledTonalButton
                        }
                        testJob?.cancel()
                        portProbe = true
                        test = TestState.Running
                        testJob = scope.launch {
                            test = try {
                                val ms = probeTcp(targetHost, targetPort)
                                if (!isActive) throw CancellationException()
                                TestState.Ok("$ms мс", "Порт доступен")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                if (!isActive) throw CancellationException()
                                TestState.Failed(tcpErrorRu(e), null)
                            }
                        }
                    },
                    enabled = !running || portProbe,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    AnimatedContent(tcpRunning, label = "testBtn") { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (r) {
                                LoadingIndicator(Modifier.size(24.dp), color = scheme.onSecondaryContainer)
                                Spacer(Modifier.width(8.dp))
                                Text("Отменить", maxLines = 1)
                            } else {
                                Icon(Icons.Rounded.NetworkCheck, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Проверить", maxLines = 1)
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        applyDavUrl()
                        showErrors = true
                        if (!valid || saving) return@Button
                        saving = true
                        scope.launch {
                            try {
                                val enc = if (anon) null else repo.encryptPassword(password)
                                val conn = build().copy(encryptedPassword = enc)
                                repo.upsert(conn)
                                saved = true
                                // Старый и лишние новые ключи больше не нужны.
                                val unused = (createdKeys + listOfNotNull(initial?.privateKeyPath)).distinct()
                                    .filter { it != conn.privateKeyPath }
                                unused.forEach { if (!repo.isKeyReferenced(it)) repo.deleteKeyFile(it) }
                                onSaved(conn)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Throwable) {
                                test = TestState.Failed("Не удалось сохранить: ${RemoteErrors.message(e)}", null)
                            } finally {
                                saving = false
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Сохранить", maxLines = 1) }
            }
            TextButton(
                onClick = {
                    if (running && !portProbe) {
                        invalidateTest()
                        return@TextButton
                    }
                    applyDavUrl()
                    showErrors = true
                    if (!valid) return@TextButton
                    val conn = build()
                    val pass = if (anon) "" else password
                    testJob?.cancel()
                    portProbe = false
                    test = TestState.Running
                    testJob = scope.launch {
                        test = try {
                            TestState.Ok(testConnection(app, conn, pass))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            if (!isActive) throw CancellationException()
                            TestState.Failed(
                                RemoteErrors.message(e),
                                e.causes().firstNotNullOfOrNull { it as? HostKeyChangedException },
                            )
                        }
                    }
                },
                enabled = !running || !portProbe,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (running && !portProbe) "Отменить" else "Проверить вход", maxLines = 1)
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    placeholder: String? = null,
    supporting: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingIcon = icon?.let { { Icon(it, null) } },
        supportingText = (error ?: supporting)?.let { { Text(it) } },
        isError = error != null,
        singleLine = true,
        textStyle = if (mono) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun KeyRow(path: String?, info: String?, onPick: () -> Unit, onClear: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Key, null, tint = if (path != null) scheme.primary else scheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Приватный ключ", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (path == null) "Не выбран — вход по паролю" else info ?: File(path).name.substringAfter('_'),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onPick, shapes = ButtonDefaults.shapes()) { Text(if (path == null) "Выбрать" else "Заменить") }
        if (path != null) {
            IconButton(onClick = onClear, shapes = androidx.compose.material3.IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Close, "Убрать ключ")
            }
        }
    }
}

@Composable
private fun ResultCard(
    icon: ImageVector,
    title: String,
    text: String,
    ok: Boolean,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (ok) scheme.primaryContainer else scheme.errorContainer
    val content = if (ok) scheme.onPrimaryContainer else scheme.onErrorContainer
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = content)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = content)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
            }
        }
        if (actionLabel != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onAction, shapes = ButtonDefaults.shapes()) { Text(actionLabel, color = content) }
            }
        }
    }
}

/** Текст предупреждения о смене ключа сервера. */
internal fun hostKeyText(e: HostKeyChangedException): String =
    "Если вы не переустанавливали сервер, соединение может быть перехвачено.\n" +
        "Было: ${e.oldFingerprint}\nСтало: ${e.newFingerprint}"

/** TCP к [host]:[port], таймаут соединения 3 с. Сокет закрывается сразу. Пароль не читается и не пишется в журнал. */
private suspend fun probeTcp(host: String, port: Int): Long = withContext(Dispatchers.IO) {
    val socket = Socket()
    try {
        coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) {
                try {
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
        val started = System.nanoTime()
        socket.connect(InetSocketAddress(host, port), 3_000)
        (System.nanoTime() - started) / 1_000_000L
    } finally {
        try {
            socket.close()
        } catch (_: Exception) {
        }
    }
}

private fun tcpErrorRu(error: Throwable): String {
    for (c in error.causes()) {
        val raw = c.message.orEmpty()
        when (c) {
            is UnknownHostException -> return "Хост не найден"
            is SocketTimeoutException -> return "Сервер не ответил за 3 секунды"
            is NoRouteToHostException -> return "Нет маршрута до хоста"
            is PortUnreachableException -> return "Порт недоступен"
            is ConnectException -> return if (raw.contains("refused", true)) {
                "Соединение отклонено"
            } else {
                "Не удалось подключиться: порт закрыт или сервер не отвечает"
            }
            is SocketException -> when {
                raw.contains("timed out", true) || raw.contains("timeout", true) ->
                    return "Сервер не ответил за 3 секунды"
                raw.contains("refused", true) -> return "Соединение отклонено"
                raw.contains("unreachable", true) || raw.contains("ENETUNREACH", true) ->
                    return "Сеть недоступна"
                raw.contains("reset", true) || raw.contains("Broken pipe", true) ->
                    return "Соединение разорвано"
                raw.contains("Permission denied", true) || raw.contains("EACCES", true) ->
                    return "Нет разрешения на соединение"
            }
        }
        if (raw.contains("timed out", true) || raw.contains("timeout", true)) {
            return "Сервер не ответил за 3 секунды"
        }
        if (raw.contains("Connection refused", true)) return "Соединение отклонено"
        if (raw.contains("Network is unreachable", true)) return "Сеть недоступна"
    }
    return "Не удалось подключиться"
}

/** Подключается, читает стартовую папку и сразу закрывает соединение. */
private suspend fun testConnection(context: Context, conn: RemoteConnection, password: String): String {
    val started = System.currentTimeMillis()
    val fs = RemoteFs.open(context, conn, password)
    try {
        val path = conn.initialPath.ifBlank { fs.home }
        val items = fs.list(path)
        val ms = System.currentTimeMillis() - started
        return "${Format.items(items.size)} в «$path» • $ms мс"
    } finally {
        withContext(Dispatchers.IO + NonCancellable) { fs.closeQuietly() }
    }
}

/** Копирует ключ из content:// в filesDir/keys и проверяет, что это приватный ключ. */
private suspend fun importKey(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val display = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: "key"
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val buf = input.readBytes()
        if (buf.size > 256 * 1024) throw IllegalArgumentException("Файл слишком большой для ключа")
        buf
    } ?: throw IllegalArgumentException("Не удалось открыть файл")
    val kp = runCatching { KeyPair.load(JSch(), bytes, null) }.getOrNull()
        ?: throw IllegalArgumentException("Это не приватный ключ (поддерживаются OpenSSH, PEM и PuTTY)")
    kp.dispose()
    val dir = File(context.filesDir, "keys").apply { mkdirs() }
    val safe = display.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48)
    val dst = File(dir, UUID.randomUUID().toString().take(8) + "_" + safe)
    dst.writeBytes(bytes)
    dst.setReadable(false, false)
    dst.setReadable(true, true)
    dst.absolutePath
}

private suspend fun describeKey(path: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val name = File(path).name.substringAfter('_')
        val kp = KeyPair.load(JSch(), path)
        val text = buildString {
            append(name)
            append(" • ").append(kp.keyTypeString)
            if (kp.isEncrypted) append(" • зашифрован, укажите фразу в поле пароля")
        }
        kp.dispose()
        text
    }.getOrNull()
}
