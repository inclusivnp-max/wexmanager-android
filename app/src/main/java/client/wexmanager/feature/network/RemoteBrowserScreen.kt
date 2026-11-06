package client.wexmanager.feature.network

import android.app.Application
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.BrowserRequest
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.fileComparator
import client.wexmanager.core.network.RemoteConnection
import client.wexmanager.core.network.RemotePath
import client.wexmanager.core.network.RemoteType
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.ActionGroup
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.feature.browser.SheetAction
import client.wexmanager.feature.browser.iconSize
import client.wexmanager.feature.browser.subtitle
import client.wexmanager.feature.browser.verticalPadding
import client.wexmanager.ui.components.ConfirmDialog
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.components.PressIconButton
import client.wexmanager.ui.components.TextInputDialog
import client.wexmanager.ui.navigation.Route
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Immutable
private sealed interface RemoteDialog {
    data class Delete(val items: List<FileItem>) : RemoteDialog
    data class Rename(val item: FileItem) : RemoteDialog
    data object NewFolder : RemoteDialog
}

/** Снимок папки для анимированной смены содержимого. */
@Immutable
private data class FolderSnapshot(
    val path: String,
    val items: List<FileItem>,
    val version: Long,
    val backward: Boolean,
    val focus: String?,
    val hidden: Int,
)

@Composable
fun RemoteBrowserScreen(connectionId: String, onBack: () -> Unit, onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: RemoteBrowserViewModel = viewModel(key = "remote:$connectionId") { RemoteBrowserViewModel(app, connectionId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val transfer by vm.transfer.collectAsStateWithLifecycle()
    val uploadPrompt by vm.uploadPrompt.collectAsStateWithLifecycle()
    val conflict by vm.conflict.collectAsStateWithLifecycle()
    val syncConflict by vm.syncConflict.collectAsStateWithLifecycle()
    val clipboard by vm.clipboard.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<RemoteDialog?>(null) }
    var uploadSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnNavigate by rememberUpdatedState(onNavigate)

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is RemoteEvent.Message -> scope.launch {
                    val r = snackbar.showSnackbar(
                        ev.text,
                        actionLabel = ev.actionLabel,
                        withDismissAction = ev.actionLabel == null,
                        duration = if (ev.actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) ev.action?.invoke()
                }
                is RemoteEvent.Navigate -> currentOnNavigate(ev.route)
                is RemoteEvent.OpenExternal -> FileOpener.openExternal(context, ev.file)
                is RemoteEvent.Reveal -> {
                    context.appContainer.browserRequests.tryEmit(BrowserRequest.Reveal(ev.path))
                    currentOnBack()
                }
            }
        }
    }
    // Вернулись из редактора/другого приложения — проверяем, не изменили ли копию.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.checkEditedCopies() }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        vm.uploadUris(uris)
    }

    val showHidden = state.showHidden ?: settings.showHidden
    val visible = remember(state.items, showHidden, settings.sortBy, settings.sortDescending, settings.foldersFirst) {
        state.items
            .filter { showHidden || !it.isHidden }
            .sortedWith(fileComparator(settings.sortBy, settings.sortDescending, settings.foldersFirst))
    }
    val selectionMode = state.selection.isNotEmpty()
    val ready = state.status == ConnectionStatus.Ready
    val canGoUp = ready && RemotePath.parent(state.path) != null

    BackHandler(selectionMode) { vm.clearSelection() }
    BackHandler(!selectionMode && canGoUp) { vm.goUp() }

    val startUpload: () -> Unit = {
        val clip = clipboard?.items.orEmpty().filter { !it.isInArchive }
        if (clip.isEmpty()) {
            pickFiles.launch(arrayOf("*/*"))
        } else {
            uploadSheet = true
        }
    }

    Scaffold(
        topBar = {
            RemoteTopBar(
                state = state,
                selectionMode = selectionMode,
                showHidden = showHidden,
                onBack = { if (selectionMode) vm.clearSelection() else onBack() },
                onSelectAll = { vm.selectAll(visible) },
                onToggleHidden = { vm.setShowHidden(!showHidden) },
                onEdit = { editing = true },
                onRefresh = { if (ready) vm.refresh() else vm.connect() },
                onOpenDownloads = {
                    val dir = vm.downloadDir()
                    context.appContainer.browserRequests.tryEmit(BrowserRequest.Reveal(dir.absolutePath))
                    onBack()
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        val motion = MaterialTheme.motionScheme
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AnimatedContent(
                targetState = state.status,
                contentKey = {
                    when (it) {
                        ConnectionStatus.Loading, ConnectionStatus.Connecting -> 0
                        ConnectionStatus.Ready -> 1
                        ConnectionStatus.Missing -> 2
                        is ConnectionStatus.Failed -> 3
                    }
                },
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                label = "status",
            ) { status ->
                when (status) {
                    ConnectionStatus.Loading, ConnectionStatus.Connecting -> ConnectingView(
                        connection = state.connection,
                        actions = {
                            OutlinedButton(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text("Отмена") }
                        },
                    )
                    ConnectionStatus.Missing -> NetworkStateView(
                        polygon = MaterialShapes.Clover4Leaf,
                        icon = Icons.Rounded.Hub,
                        title = "Подключение не найдено",
                        text = "Возможно, оно было удалено.",
                        actions = { Button(onClick = onBack, shapes = ButtonDefaults.shapes()) { Text("Назад") } },
                    )
                    is ConnectionStatus.Failed -> FailedView(
                        status = status,
                        connection = state.connection,
                        onRetry = vm::connect,
                        onEdit = { editing = true },
                        onTrust = { status.hostKey?.let(vm::trustHostKey) },
                    )
                    ConnectionStatus.Ready -> ReadyContent(
                        state = state,
                        visible = visible,
                        hiddenCount = state.items.size - visible.size,
                        vm = vm,
                        bottomInset = padding.calculateBottomPadding(),
                        hasTransfer = transfer != null,
                    )
                }
            }

            // Низ экрана: снекбары, карточка передачи, плавающая панель.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SnackbarHost(snackbar)
                AnimatedVisibility(
                    visible = transfer != null,
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 2 } + fadeIn(),
                    exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 2 } + fadeOut(),
                ) {
                    // Держим последнее значение, чтобы карточка не пустела во время ухода.
                    val last = remember { arrayOfNulls<TransferUi>(1) }
                    transfer?.let { last[0] = it }
                    (transfer ?: last[0])?.let { TransferCard(it, onCancel = vm::cancelTransfers) }
                }
                AnimatedVisibility(
                    visible = ready,
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
                    exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(),
                ) {
                    RemoteToolbar(
                        selectionMode = selectionMode,
                        selectionCount = state.selection.size,
                        canGoUp = canGoUp,
                        canRename = state.selection.size == 1,
                        showHidden = showHidden,
                        onUp = { vm.goUp() },
                        onUpload = startUpload,
                        onNewFolder = { dialog = RemoteDialog.NewFolder },
                        onRefresh = vm::refresh,
                        onToggleHidden = { vm.setShowHidden(!showHidden) },
                        onCloseSelection = vm::clearSelection,
                        onDownload = { vm.download(vm.selectedItems()) },
                        onRename = { vm.selectedItems().singleOrNull()?.let { dialog = RemoteDialog.Rename(it) } },
                        onDelete = { vm.selectedItems().takeIf { it.isNotEmpty() }?.let { dialog = RemoteDialog.Delete(it) } },
                        onSelectAll = { vm.selectAll(visible) },
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ Диалоги и шторки

    when (val d = dialog) {
        is RemoteDialog.Delete -> ConfirmDialog(
            title = "Удалить с сервера?",
            text = if (d.items.size == 1) {
                "«${d.items[0].name}»" + (if (d.items[0].isDirectory) " и всё её содержимое" else "") + " будет удалено без возможности восстановления."
            } else {
                "${Format.items(d.items.size)} будут удалены без возможности восстановления."
            },
            icon = Icons.Rounded.Delete,
            confirmLabel = "Удалить",
            onConfirm = { vm.delete(d.items) },
            onDismiss = { dialog = null },
            destructive = true,
        )
        is RemoteDialog.Rename -> TextInputDialog(
            title = "Переименовать",
            icon = Icons.Rounded.DriveFileRenameOutline,
            initial = d.item.name,
            label = "Новое имя",
            confirmLabel = "Готово",
            selection = TextRange(0, d.item.baseName.length),
            onDismiss = { dialog = null },
        ) { value, setError ->
            val err = vm.validateName(value, d.item)
            if (err != null) setError(err) else {
                dialog = null
                vm.rename(d.item, value)
            }
        }
        RemoteDialog.NewFolder -> TextInputDialog(
            title = "Новая папка",
            icon = Icons.Rounded.CreateNewFolder,
            initial = "",
            label = "Имя папки",
            confirmLabel = "Создать",
            onDismiss = { dialog = null },
        ) { value, setError ->
            val err = vm.validateName(value)
            if (err != null) setError(err) else {
                dialog = null
                vm.mkdir(value)
            }
        }
        null -> Unit
    }

    uploadPrompt?.let { copy ->
        ConfirmDialog(
            title = "Загрузить изменения на сервер?",
            text = "Файл «${copy.local.name}» был изменён. Отправить новую версию на сервер? Файл на сервере будет перезаписан.",
            icon = Icons.Rounded.CloudUpload,
            confirmLabel = "Загрузить",
            onConfirm = { vm.answerUploadPrompt(true) },
            onDismiss = { vm.answerUploadPrompt(false) },
        )
    }

    syncConflict?.let { t ->
        ConfirmDialog(
            title = "Файл на сервере изменился",
            text = "Файл «${RemotePath.name(t.remotePath)}» на сервере изменился после скачивания. Перезаписать его вашей версией?",
            icon = Icons.Rounded.CloudUpload,
            confirmLabel = "Перезаписать",
            onConfirm = { vm.resolveSyncConflict(true) },
            onDismiss = { vm.resolveSyncConflict(false) },
            destructive = true,
        )
    }

    conflict?.let { c ->
        val list = c.names.take(5).joinToString(", ") { "«$it»" } + if (c.names.size > 5) " и ещё ${c.names.size - 5}" else ""
        ConfirmDialog(
            title = "Заменить файлы?",
            text = "В этой папке уже есть: $list. Они будут перезаписаны.",
            icon = Icons.Rounded.CloudUpload,
            confirmLabel = "Заменить",
            onConfirm = { vm.resolveConflict(true) },
            onDismiss = { vm.resolveConflict(false) },
        )
    }

    if (uploadSheet) {
        val clipItems = clipboard?.items.orEmpty().filter { !it.isInArchive }
        AppBottomSheet(onDismiss = { uploadSheet = false }) {
            Text(
                "Загрузить в «${RemotePath.name(state.path)}»",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            ActionGroup(
                listOf(
                    SheetAction(
                        "Из буфера обмена",
                        Icons.Rounded.ContentPaste,
                        clipItems.take(3).joinToString(", ") { it.name } + if (clipItems.size > 3) " и ещё ${clipItems.size - 3}" else "",
                    ) { vm.uploadClipboard() },
                    SheetAction("Выбрать файлы…", Icons.Rounded.UploadFile, "Из памяти устройства или других приложений") {
                        pickFiles.launch(arrayOf("*/*"))
                    },
                ),
                onDismiss = { uploadSheet = false },
            )
        }
    }

    if (editing) {
        ConnectionEditorSheet(
            initial = state.connection,
            onDismiss = { editing = false },
            onSaved = {
                editing = false
                vm.connect()
            },
        )
    }
}

// ------------------------------------------------------------------ Верхняя панель

@Composable
private fun RemoteTopBar(
    state: RemoteBrowserState,
    selectionMode: Boolean,
    showHidden: Boolean,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleHidden: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val conn = state.connection
    TopAppBar(
        title = {
            AnimatedContent(selectionMode, label = "title") { sel ->
                Text(
                    if (sel) "Выбрано: ${state.selection.size}" else conn?.title ?: "Сеть",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        subtitle = {
            Text(
                conn?.let { "${it.type.title} • ${it.address}" } ?: "",
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(if (selectionMode) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, if (selectionMode) "Снять выделение" else "Назад")
            }
        },
        actions = {
            if (selectionMode) {
                IconButton(onClick = onSelectAll, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.SelectAll, "Выделить всё") }
            } else {
                IconButton(onClick = onRefresh, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Refresh, "Обновить")
                }
                Box {
                    IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                    DropdownMenuPopup(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                            DropdownMenuItem(
                                text = { Text("Обновить") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                onClick = { menu = false; onRefresh() },
                                shape = MenuDefaults.leadingItemShape,
                            )
                            DropdownMenuItem(
                                text = { Text(if (showHidden) "Не показывать скрытые" else "Показать скрытые") },
                                leadingIcon = { Icon(if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) },
                                onClick = { menu = false; onToggleHidden() },
                                shape = MenuDefaults.middleItemShape,
                            )
                            DropdownMenuItem(
                                text = { Text("Папка загрузок") },
                                leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                                onClick = { menu = false; onOpenDownloads() },
                                shape = MenuDefaults.trailingItemShape,
                            )
                        }
                        Spacer(Modifier.size(MenuDefaults.GroupSpacing))
                        DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                            DropdownMenuItem(
                                text = { Text("Изменить подключение") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = { menu = false; onEdit() },
                                shape = MenuDefaults.standaloneItemShape,
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

// ------------------------------------------------------------------ Ошибка подключения

@Composable
private fun FailedView(
    status: ConnectionStatus.Failed,
    connection: RemoteConnection?,
    onRetry: () -> Unit,
    onEdit: () -> Unit,
    onTrust: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hk = status.hostKey
    val icon = when {
        hk != null -> Icons.Rounded.GppMaybe
        status.message.contains("логин", true) || status.message.contains("доступ", true) ||
            status.message.contains("пароль", true) -> Icons.Rounded.Lock
        status.message.contains("Хост не найден") -> Icons.Rounded.SearchOff
        status.message.contains("разорвано") || status.message.contains("время ожидания") -> Icons.Rounded.WifiOff
        else -> Icons.Rounded.CloudOff
    }
    NetworkStateView(
        polygon = if (hk != null) MaterialShapes.Burst else MaterialShapes.SoftBurst,
        icon = icon,
        title = if (hk != null) "Ключ сервера изменился" else "Не удалось подключиться",
        text = if (hk != null) hostKeyText(hk) else status.message,
        container = scheme.errorContainer,
        content = scheme.onErrorContainer,
        extra = {
            connection?.let { c ->
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(scheme.surfaceContainerHigh)
                        .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TypeBadge(c.type, size = 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        c.address,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
            }
        },
        actions = {
            FilledTonalButton(onClick = onEdit, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Edit, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Изменить")
            }
            if (hk != null) {
                Button(
                    onClick = onTrust,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
                ) { Text("Доверять") }
            } else {
                Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Повторить")
                }
            }
        },
    )
}

// ------------------------------------------------------------------ Содержимое папки

@Composable
private fun ReadyContent(
    state: RemoteBrowserState,
    visible: List<FileItem>,
    hiddenCount: Int,
    vm: RemoteBrowserViewModel,
    bottomInset: androidx.compose.ui.unit.Dp,
    hasTransfer: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(state.loading) { if (!state.loading) pulled = false }

    Column(Modifier.fillMaxSize()) {
        PathHeader(state, visible, hiddenCount, onNavigate = { path ->
            val back = state.path.startsWith(path.trimEnd('/') + "/") || path == "/"
            // Подсвечиваем папку, через которую шли вниз.
            val focus = if (back) {
                val rest = RemotePath.segments(state.path.removePrefix(path.trimEnd('/')))
                rest.firstOrNull()?.let { RemotePath.join(path, it) }
            } else null
            vm.open(path, backward = back, focus = focus)
        })
        AnimatedVisibility(state.loading && !pulled, enter = fadeIn(), exit = fadeOut()) {
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp))
        }
        AnimatedVisibility(
            state.busy != null,
            enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.secondaryContainer)
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoadingIndicator(Modifier.size(32.dp), color = scheme.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    state.busy.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = vm::cancelOperation) { Text("Отменить") }
            }
        }

        val pullState = rememberPullToRefreshState()
        val motion = MaterialTheme.motionScheme
        PullToRefreshBox(
            isRefreshing = pulled && state.loading,
            onRefresh = {
                pulled = true
                vm.refresh()
            },
            state = pullState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = pulled && state.loading,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            val snapshot = FolderSnapshot(state.path, visible, state.version, state.backward, state.focus, hiddenCount)
            AnimatedContent(
                targetState = snapshot,
                contentKey = { it.version },
                transitionSpec = {
                    val dir = if (targetState.backward) -1 else 1
                    (slideInHorizontally(motion.defaultSpatialSpec()) { w -> dir * w / 5 } + fadeIn(motion.defaultEffectsSpec()))
                        .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { w -> -dir * w / 8 } + fadeOut(motion.fastEffectsSpec()))
                },
                label = "folder",
            ) { snap ->
                val bottom = bottomInset + 96.dp + (if (hasTransfer) 120.dp else 0.dp)
                if (snap.items.isEmpty()) {
                    NetworkStateView(
                        polygon = MaterialShapes.Cookie9Sided,
                        icon = Icons.Rounded.FolderOff,
                        title = "Папка пуста",
                        text = if (snap.hidden > 0) "Скрытых элементов: ${snap.hidden}" else null,
                        modifier = Modifier.padding(bottom = bottom / 2),
                    )
                } else {
                    FolderList(snap, state.selection, vm, PaddingValues(top = 4.dp, bottom = bottom))
                }
            }
        }
    }
}

@Composable
private fun FolderList(snap: FolderSnapshot, selection: Set<String>, vm: RemoteBrowserViewModel, contentPadding: PaddingValues) {
    val focusIndex = remember(snap.version) {
        snap.focus?.let { f -> snap.items.indexOfFirst { it.path == f } }?.takeIf { it >= 0 }
    }
    val saved = remember(snap.version) { vm.scrollFor(snap.path) }
    val initialIndex = when {
        snap.backward && saved != null -> saved.first
        focusIndex != null -> (focusIndex - 3).coerceAtLeast(0)
        else -> 0
    }
    val listState = rememberLazyListState(initialIndex, if (snap.backward && saved != null) saved.second else 0)
    DisposableEffect(snap.path) {
        onDispose { vm.saveScroll(snap.path, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) }
    }
    var highlight by remember(snap.version) { mutableStateOf(snap.focus) }
    LaunchedEffect(snap.version) {
        delay(2000)
        highlight = null
    }
    val highlightOn = LocalAppSettings.current.highlightReturnedFolder
    LazyColumn(state = listState, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(snap.items, key = { it.path }, contentType = { 0 }) { item ->
            RemoteFileRow(
                item = item,
                selected = item.path in selection,
                highlight = highlightOn && item.path == highlight,
                onClick = { vm.onItemClick(item) },
                onLongClick = { vm.toggleSelection(item) },
                onIconClick = { vm.toggleSelection(item) },
                modifier = Modifier.animateItem(
                    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                ),
            )
        }
    }
}

@Composable
private fun PathHeader(state: RemoteBrowserState, visible: List<FileItem>, hiddenCount: Int, onNavigate: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    val conn = state.connection
    val crumbs = remember(state.path, conn?.share) {
        val list = ArrayList<Pair<String, String>>()
        val rootTitle = if (conn?.type == RemoteType.SMB) conn.share?.trim('/', '\\').orEmpty().ifEmpty { "/" } else "/"
        list += rootTitle to "/"
        var acc = ""
        RemotePath.segments(state.path).forEach { part ->
            acc = "$acc/$part"
            list += part to acc
        }
        list
    }
    val rowState = rememberLazyListState()
    LaunchedEffect(crumbs) { rowState.animateScrollToItem((crumbs.size - 1).coerceAtLeast(0)) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceContainer),
    ) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            itemsIndexed(crumbs, key = { _, c -> c.second }) { i, (title, path) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.outline, modifier = Modifier.size(16.dp))
                    val last = i == crumbs.lastIndex
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !last) { onNavigate(path) }
                            .background(if (last) scheme.secondaryContainer else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (i == 0 && conn != null) {
                            Icon(conn.type.icon, null, modifier = Modifier.size(16.dp), tint = if (last) scheme.onSecondaryContainer else scheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (last) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                            fontWeight = if (last) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        val dirs = visible.count { it.isDirectory }
        val files = visible.size - dirs
        val bytes = visible.sumOf { if (it.isDirectory) 0L else it.size }
        val parts = buildList {
            if (dirs > 0) add(Format.folders(dirs))
            if (files > 0) add(Format.files(files) + " (${Format.size(bytes, s.sizeUnits)})")
            if (hiddenCount > 0) add("скрыто $hiddenCount")
        }
        Text(
            if (parts.isEmpty()) (if (state.loading) "Загрузка…" else "Пусто") else parts.joinToString("  •  "),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun RemoteFileRow(
    item: FileItem,
    selected: Boolean,
    highlight: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onIconClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        if (selected) scheme.secondaryContainer else Color.Transparent,
        MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "rowBg",
    )
    val glow = remember { Animatable(0f) }
    LaunchedEffect(highlight) {
        if (highlight) {
            glow.animateTo(1f, tween(220))
            delay(650)
            glow.animateTo(0f, tween(900))
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(if (selected) 22.dp else 18.dp))
            .drawBehind {
                drawRect(bg)
                if (glow.value > 0f) drawRect(scheme.tertiary.copy(alpha = glow.value * 0.22f))
            }
            .combinedClickable(
                interactionSource = null,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 10.dp, vertical = s.listDensity.verticalPadding()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(
            item = item,
            selected = selected,
            size = s.listDensity.iconSize(),
            showThumbnail = false,
            modifier = Modifier
                .clip(RoundedCornerShape(30))
                .combinedClickable(onClick = onIconClick, onLongClick = onLongClick),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            val base = MaterialTheme.typography.bodyLarge
            val nameColor = when {
                selected -> scheme.onSecondaryContainer
                item.isHidden -> scheme.onSurface.copy(alpha = 0.6f)
                else -> scheme.onSurface
            }
            val ext = item.extension
            val name = if (item.isDirectory || ext.isEmpty() || item.name.startsWith('.') && item.name.count { it == '.' } == 1) {
                buildAnnotatedString { append(item.name) }
            } else if (!s.showExtensions) {
                buildAnnotatedString { append(item.baseName) }
            } else {
                buildAnnotatedString {
                    append(item.baseName)
                    withStyle(SpanStyle(color = nameColor.copy(alpha = 0.55f))) { append("." + item.name.substringAfterLast('.')) }
                }
            }
            Text(
                name,
                style = base.copy(fontSize = base.fontSize * s.fileListFontScale, lineHeight = base.lineHeight * s.fileListFontScale),
                color = nameColor,
                maxLines = s.fileNameMaxLines,
                overflow = if (s.fileNameMaxLines == 1) TextOverflow.MiddleEllipsis else TextOverflow.Ellipsis,
            )
            val sub = remember(item, s.dateFormat, s.sizeUnits) { item.subtitle(s) }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) scheme.onSecondaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.isSymlink && !item.linkTarget.isNullOrEmpty()) {
                Text(
                    "→ ${item.linkTarget}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Передачи

@Composable
private fun TransferCard(t: TransferUi, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    val fraction = if (t.total > 0) (t.done.toFloat() / t.total).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(fraction, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "progress")
    val badge = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) spin.animateTo(spin.value + 360f, tween(6000, easing = androidx.compose.animation.core.LinearEasing))
    }
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).widthIn(max = 560.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = spin.value }
                            .clip(badge)
                            .background(scheme.primaryContainer),
                    )
                    Icon(
                        if (t.upload) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDownload,
                        null,
                        tint = scheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        buildString {
                            append(t.title)
                            if (t.count > 1) append(" • ${t.index} из ${t.count}")
                            if (t.queued > 0) append(" • ещё ${t.queued} в очереди")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (t.preparing) "Подготовка…" else t.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                FilledTonalIconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, "Отменить передачу")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (t.preparing || t.total <= 0) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(end = 6.dp))
            } else {
                LinearWavyProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().padding(end = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(end = 6.dp)) {
                Text(
                    when {
                        t.preparing -> "Составляем список файлов"
                        t.total > 0 -> "${Format.size(t.done, s.sizeUnits)} из ${Format.size(t.total, s.sizeUnits)}"
                        else -> Format.size(t.done, s.sizeUnits)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!t.preparing && t.speed > 0) {
                    Text(
                        Format.size(t.speed, s.sizeUnits) + "/с",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Плавающая панель

@Composable
private fun RemoteToolbar(
    selectionMode: Boolean,
    selectionCount: Int,
    canGoUp: Boolean,
    canRename: Boolean,
    showHidden: Boolean,
    onUp: () -> Unit,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    onRefresh: () -> Unit,
    onToggleHidden: () -> Unit,
    onCloseSelection: () -> Unit,
    onDownload: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = selectionMode,
        transitionSpec = {
            (scaleIn(motion.fastSpatialSpec(), initialScale = 0.85f) + fadeIn(motion.fastEffectsSpec()))
                .togetherWith(scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f) + fadeOut(motion.fastEffectsSpec()))
        },
        contentAlignment = Alignment.Center,
        label = "toolbar",
    ) { selecting ->
        if (!selecting) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onUpload) {
                        Icon(Icons.Rounded.CloudUpload, "Загрузить сюда")
                    }
                },
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            ) {
                PressIconButton(Icons.Rounded.ArrowUpward, "Вверх", onUp, enabled = canGoUp)
                PressIconButton(Icons.Rounded.CreateNewFolder, "Новая папка", onNewFolder)
                PressIconButton(Icons.Rounded.Refresh, "Обновить", onRefresh)
                PressIconButton(
                    if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (showHidden) "Не показывать скрытые" else "Показать скрытые",
                    onToggleHidden,
                )
            }
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                leadingContent = { PressIconButton(Icons.Rounded.Close, "Снять выделение", onCloseSelection) },
            ) {
                Box(Modifier.align(Alignment.CenterVertically).widthIn(min = 20.dp), contentAlignment = Alignment.Center) {
                    AnimatedContent(selectionCount, label = "count") { n ->
                        Text("$n", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(Modifier.width(4.dp))
                PressIconButton(Icons.Rounded.Download, "Скачать", onDownload)
                PressIconButton(Icons.Rounded.DriveFileRenameOutline, "Переименовать", onRename, enabled = canRename)
                PressIconButton(Icons.Rounded.Delete, "Удалить", onDelete)
                PressIconButton(Icons.Rounded.SelectAll, "Выделить всё", onSelectAll)
            }
        }
    }
}
