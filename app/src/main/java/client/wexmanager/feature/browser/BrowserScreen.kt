package client.wexmanager.feature.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.fs.Location
import client.wexmanager.core.settings.DualPaneMode
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.core.util.OpenAction
import client.wexmanager.ui.navigation.Route
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    onNavigate: (Route) -> Unit,
    onRequestPermission: () -> Unit,
    onExit: () -> Unit,
) {
    // Невидимое на первом кадре (соседняя панель, закрытое меню) компонуем чуть позже — быстрее старт.
    var warm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!warm) {
            androidx.compose.runtime.withFrameNanos { }
            kotlinx.coroutines.delay(400)
            warm = true
        }
    }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val left by vm.panels[0].state.collectAsStateWithLifecycle()
    val right by vm.panels[1].state.collectAsStateWithLifecycle()
    val clipboard by vm.clipboard.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val userData by vm.userData.collectAsStateWithLifecycle()
    val states = listOf(left, right)
    val activeState = states[active]
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<BrowserSheet?>(null) }
    var dialog by remember { mutableStateOf<BrowserDialog?>(null) }
    var exitArmed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is BrowserEvent.Message -> scope.launch {
                    // С действием — подольше, но не бесконечно (по умолчанию M3 держит такой снекбар вечно).
                    val r = snackbar.showSnackbar(
                        ev.text, ev.actionLabel, withDismissAction = true,
                        duration = if (ev.actionLabel != null) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) ev.action?.invoke()
                }
                is BrowserEvent.Open -> when (val a = ev.action) {
                    is OpenAction.Navigate -> onNavigate(a.route)
                    is OpenAction.External -> FileOpener.openExternal(context, File(a.path))
                    is OpenAction.ChooseOpenAs -> dialog = BrowserDialog.OpenAsChooser(a.item, a.archive, a.entry)
                    is OpenAction.Enter -> Unit
                }
                is BrowserEvent.Share -> FileOpener.share(context, ev.files)
            }
        }
    }

    // Одноразовая подсказка о скрытых жестах.
    LaunchedEffect(settings.tipGesturesShown) {
        if (settings.tipGesturesShown) return@LaunchedEffect
        delay(1500)
        val r = snackbar.showSnackbar(
            "Удерживайте кнопки нижней панели для доп. действий, а для выделения проведите вправо по иконке и ведите вниз",
            actionLabel = "Понятно",
            duration = androidx.compose.material3.SnackbarDuration.Indefinite,
        )
        if (r == SnackbarResult.ActionPerformed || r == SnackbarResult.Dismissed) vm.updateSettings { it.copy(tipGesturesShown = true) }
    }

    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce && settings.autoRefresh) vm.panels.forEach { it.refresh() }
        resumedOnce = true
    }

    // ---- Назад: меню → выделение → фильтр → родительская папка → выход
    BackHandler(drawerState.isOpen) { scope.launch { drawerState.close() } }
    BackHandler(!drawerState.isOpen && activeState.selectionMode) { vm.activePanel.clearSelection() }
    BackHandler(!drawerState.isOpen && !activeState.selectionMode && activeState.filterVisible) { vm.activePanel.showFilter(false) }
    // Предиктивный «назад» на уровень вверх: содержимое панели следует за жестом.
    val backProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    PredictiveBackHandler(!drawerState.isOpen && !activeState.selectionMode && !activeState.filterVisible && activeState.location.parent != null) { events ->
        try {
            events.collect { e -> backProgress.snapTo(e.progress) }
            vm.activePanel.goUp()
            backProgress.snapTo(0f)
        } catch (e: kotlinx.coroutines.CancellationException) {
            scope.launch { backProgress.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f)) }
            throw e
        }
    }
    BackHandler(
        !drawerState.isOpen && !activeState.selectionMode && !activeState.filterVisible && activeState.location.parent == null,
    ) {
        if (!settings.doubleBackToExit || exitArmed) onExit()
        else {
            exitArmed = true
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                launch { snackbar.showSnackbar("Нажмите «назад» ещё раз для выхода") }
                delay(2000)
                exitArmed = false
            }
        }
    }

    val volumes by produceState(emptyList<client.wexmanager.core.fs.StorageInfo>(), drawerState.isOpen) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.storage.volumes() }
    }
    val onDrawerDestination: (DrawerDestination) -> Unit = { dest ->
        scope.launch { drawerState.close() }
        val root = (activeState.location as? Location.Local)?.path ?: vm.storage.primaryPath
        when (dest) {
            is DrawerDestination.Path -> vm.openLocation(dest.path)
            is DrawerDestination.File -> if (File(dest.path).exists()) vm.open(vm.activePanel, LocalFileSystem.stat(dest.path))
            DrawerDestination.Search -> onNavigate(Route.Search(root))
            DrawerDestination.Analyzer -> onNavigate(Route.Analyzer(vm.storage.primaryPath))
            DrawerDestination.Apps -> onNavigate(Route.Apps)
            DrawerDestination.Trash -> onNavigate(Route.Trash)
            DrawerDestination.Tasks -> onNavigate(Route.Tasks)
            DrawerDestination.Terminal -> onNavigate(Route.Terminal(root))
            DrawerDestination.Duplicates -> onNavigate(Route.Duplicates(vm.storage.primaryPath))
            DrawerDestination.Cleanup -> onNavigate(Route.Cleanup(vm.storage.primaryPath))
            DrawerDestination.Network -> onNavigate(Route.Network)
            DrawerDestination.Smali -> onNavigate(Route.SmaliReference)
            DrawerDestination.Logcat -> onNavigate(Route.Logcat)
            DrawerDestination.ShareServer -> onNavigate(Route.ShareServer)
            DrawerDestination.Components -> onNavigate(Route.Components)
            DrawerDestination.Extras -> onNavigate(Route.Extras)
            DrawerDestination.WaveHub -> onNavigate(Route.WaveHub)
            DrawerDestination.Settings -> onNavigate(Route.Settings)
            DrawerDestination.About -> onNavigate(Route.About)
        }
    }
    val drawerContent: @Composable (Boolean) -> Unit = { permanent ->
        AppDrawerContent(
            storage = vm.storage,
            volumes = volumes,
            bookmarks = userData.bookmarks,
            recents = userData.recents,
            currentPath = (activeState.location as? Location.Local)?.path,
            activeTasks = tasks.count { it.isActive },
            permanent = permanent,
            onDestination = onDrawerDestination,
        )
    }
    val browserContent: @Composable (Boolean) -> Unit = { permanentDrawer ->
    Scaffold(
        topBar = {
            BrowserTopBar(
                state = activeState,
                active = active,
                showPanelSwitch = settings.dualPaneMode != DualPaneMode.Single,
                onMenu = { scope.launch { drawerState.open() } },
                showMenu = !permanentDrawer,
                onCloseSelection = { vm.activePanel.clearSelection() },
                onSearch = {
                    onNavigate(Route.Search((activeState.location as? Location.Local)?.path ?: vm.storage.primaryPath))
                },
                onSortView = { sheet = BrowserSheet.SortView },
                onSwitchPanel = { vm.setActive(it) },
                onSwapPanels = { vm.swapPanels() },
                onRefresh = { vm.activePanel.refresh() },
                onFilter = { vm.activePanel.showFilter(true) },
                onPathJump = { dialog = BrowserDialog.PathJump },
                onToggleHidden = { vm.updateSettings { it.copy(showHidden = !it.showHidden) } },
                showHidden = settings.showHidden,
                onSelectAll = { vm.activePanel.selectAll() },
                onInvert = { vm.activePanel.invertSelection() },
                onSettings = { onNavigate(Route.Settings) },
                onAnalyze = { (activeState.location as? Location.Local)?.let { onNavigate(Route.Analyzer(it.path)) } },
                onTerminal = { onNavigate(Route.Terminal((activeState.location as? Location.Local)?.path ?: vm.storage.primaryPath)) },
                onFolderSizes = { vm.activePanel.computeFolderSizes() },
                onHome = { vm.goHome() },
                onSetHome = { vm.setHomeHere() },
                onExit = onExit,
                onCompareFolders = {
                    val l = left.location as? Location.Local
                    val r = right.location as? Location.Local
                    if (l != null && r != null) onNavigate(Route.FolderCompare(l.path, r.path))
                },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = 88.dp))
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        val keyFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(Unit) { runCatching { keyFocus.requestFocus() } }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .focusRequester(keyFocus)
                .focusable()
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown || sheet != null || dialog != null) return@onPreviewKeyEvent false
                    handleShortcut(
                        e, vm, activeState,
                        onDialog = { dialog = it },
                        onSwitchPanel = { vm.setActive(1 - active) },
                    )
                },
        ) {
            val sideBySide = when (settings.dualPaneMode) {
                DualPaneMode.Always -> true
                DualPaneMode.Single -> false
                DualPaneMode.Auto -> maxWidth >= 600.dp
            }
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val listPadding = PaddingValues(top = 4.dp, bottom = 112.dp + navBottom)

            @Composable
            fun Panel(index: Int, modifier: Modifier) {
                val st = states[index]
                val p = if (index == active) backProgress.value else 0f
                PanelView(
                    panel = vm.panels[index],
                    state = st,
                    isActive = active == index,
                    showActiveIndicator = sideBySide,
                    storage = vm.storage,
                    onItemClick = { vm.onItemClick(vm.panels[index], it) },
                    onItemLongClick = {
                        vm.setActive(index)
                        if (st.selectionMode) vm.panels[index].toggleSelection(it) else sheet = BrowserSheet.Actions(it)
                    },
                    onActivate = { vm.setActive(index) },
                    onRequestPermission = onRequestPermission,
                    contentPadding = listPadding,
                    onCreate = if ((st.location as? client.wexmanager.core.fs.Location.Local)?.let { loc -> remember(loc.path) { File(loc.path).canWrite() } } == true) { folder ->
                        vm.setActive(index)
                        dialog = BrowserDialog.Create(folder)
                    } else null,
                    modifier = modifier.graphicsLayer {
                        if (p > 0f) {
                            translationX = p * 48.dp.toPx()
                            val sc = 1f - p * 0.06f
                            scaleX = sc
                            scaleY = sc
                            alpha = 1f - p * 0.35f
                        }
                    },
                )
            }

            if (sideBySide) {
                Row(Modifier.fillMaxSize()) {
                    Panel(0, Modifier.weight(1f))
                    VerticalDivider(Modifier.fillMaxHeight().padding(vertical = 8.dp))
                    Panel(1, Modifier.weight(1f))
                }
            } else if (settings.dualPaneMode == DualPaneMode.Single) {
                Panel(active, Modifier.fillMaxSize())
            } else {
                val pager = rememberPagerState(initialPage = active) { 2 }
                LaunchedEffect(active) {
                    if (pager.currentPage != active) pager.animateScrollToPage(active)
                }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.collect { vm.setActive(it) }
                }
                HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = if (warm) 1 else 0,
                    modifier = Modifier.fillMaxSize(),
                    key = { it },
                ) { page -> Panel(page, Modifier.fillMaxSize()) }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AnimatedVisibility(
                    visible = tasks.any { it.isActive },
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    TaskPill(tasks, onClick = { onNavigate(Route.Tasks) }, modifier = Modifier.widthIn(max = 420.dp))
                }
                AnimatedVisibility(
                    visible = clipboard != null && !activeState.selectionMode,
                    enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                    exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                ) {
                    val clip = clipboard
                    if (clip != null) ClipboardBar(
                        text = "${if (clip.cut) "Вырезано" else "В буфере"}: ${if (clip.items.size == 1) clip.items[0].name else Format.items(clip.items.size)}",
                        onPaste = { vm.paste() },
                        onClear = { vm.clearClipboard() },
                    )
                }
                BrowserToolbar(
                    selectionMode = activeState.selectionMode,
                    selectionCount = activeState.selection.size,
                    canGoUp = activeState.location.parent != null,
                    canGoBack = activeState.canGoBack,
                    actions = ToolbarActions(
                        onUp = { vm.activePanel.goUp() },
                        onPathJump = { dialog = BrowserDialog.PathJump },
                        onHistory = { sheet = BrowserSheet.History },
                        onHistoryBack = { vm.activePanel.goBack() },
                        onSync = { vm.syncOther() },
                        onFilter = { vm.activePanel.showFilter(true) },
                        onBookmarks = { sheet = BrowserSheet.Bookmarks },
                        onBookmarkHere = {
                            (activeState.location as? Location.Local)?.let { vm.toggleBookmark(it.path, it.title.ifEmpty { "/" }, true) }
                        },
                        onCreate = { sheet = BrowserSheet.Create },
                        onCloseSelection = { vm.activePanel.clearSelection() },
                        onCopyOther = { vm.transferToOther(move = false) },
                        onMoveOther = { vm.transferToOther(move = true) },
                        onCopyClipboard = { vm.copyToClipboard(cut = false) },
                        onCutClipboard = { vm.copyToClipboard(cut = true) },
                        onDelete = {
                            val sel = activeState.selectedItems
                            if (settings.confirmDelete || sel.any { it.isInArchive }) dialog = BrowserDialog.Delete(sel)
                            else vm.delete(sel, permanently = false)
                        },
                        onRename = {
                            val sel = activeState.selectedItems
                            if (sel.any { it.isInArchive }) {
                                scope.launch { snackbar.showSnackbar("Переименование внутри архива не поддерживается") }
                            } else {
                                dialog = if (sel.size == 1) BrowserDialog.Rename(sel[0]) else BrowserDialog.BatchRename(sel)
                            }
                        },
                        onMore = { sheet = BrowserSheet.Selection },
                        onGo = { onNavigate(Route.GoWorkspace) },
                    ),
                )
            }
        }
    }
    }
    // Планшеты и большие экраны: боковое меню — постоянная колонка слева.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                drawerContent(true)
                Box(Modifier.weight(1f)) { browserContent(true) }
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
                // До «прогрева» — пустая шторка той же ширины: от ширины зависят якоря закрытого положения.
                drawerContent = { if (warm || drawerState.isOpen || drawerState.isAnimationRunning) drawerContent(false) else androidx.compose.material3.ModalDrawerSheet { } },
            ) { browserContent(false) }
        }
    }

    // ---- Шторки
    when (val sh = sheet) {
        is BrowserSheet.Actions -> FileActionsSheet(
            item = sh.item,
            vm = vm,
            isBookmarked = userData.bookmarks.any { it.path == sh.item.path },
            onDialog = { dialog = it },
            onDismiss = { sheet = null },
        )
        BrowserSheet.Selection -> SelectionSheet(vm, activeState, onDialog = { dialog = it }, onNavigate = onNavigate, onDismiss = { sheet = null })
        BrowserSheet.Create -> CreateSheet(vm, clipboard != null, onDialog = { dialog = it }, onDismiss = { sheet = null })
        BrowserSheet.SortView -> SortViewSheet(vm, onDismiss = { sheet = null })
        BrowserSheet.History -> HistorySheet(vm.activePanel.history(), onOpen = { vm.activePanel.open(it) }, onDismiss = { sheet = null })
        BrowserSheet.Bookmarks -> BookmarksSheet(
            bookmarks = userData.bookmarks,
            currentPath = (activeState.location as? Location.Local)?.path,
            onOpen = { b ->
                if (b.isDirectory) vm.openLocation(b.path)
                else if (File(b.path).exists()) vm.open(vm.activePanel, LocalFileSystem.stat(b.path))
            },
            onAddCurrent = {
                (activeState.location as? Location.Local)?.let { vm.toggleBookmark(it.path, it.title.ifEmpty { "/" }, true) }
            },
            onRemove = { vm.removeBookmark(it.path) },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    // ---- Диалоги
    val close = { dialog = null }
    when (val d = dialog) {
        is BrowserDialog.Create -> CreateDialog(d.folder, vm, close)
        is BrowserDialog.Rename -> RenameDialog(d.item, vm, close)
        is BrowserDialog.BatchRename -> BatchRenameDialog(d.items, vm, close)
        is BrowserDialog.Delete -> DeleteDialog(d.items, vm, close)
        BrowserDialog.PathJump -> PathJumpDialog(
            current = (activeState.location as? Location.Local)?.path ?: vm.storage.primaryPath,
            history = userData.pathHistory,
            vm = vm,
            onDismiss = close,
        )
        is BrowserDialog.Compress -> CompressDialog(d.items, vm, close)
        is BrowserDialog.Extract -> ExtractDialog(d.item, vm, close)
        is BrowserDialog.OpenAsChooser -> OpenAsDialog(d.item, vm, close, d.archive, d.entry)
        is BrowserDialog.Properties -> PropertiesDialog(d.items, vm, close)
        is BrowserDialog.FolderColor -> FolderColorDialog(d.item, userData.folderColors[d.item.path], vm, close)
        null -> Unit
    }

    // Диалог конфликта показывает MainActivity поверх всех экранов.
}

@Composable
private fun ClipboardBar(text: String, onPaste: () -> Unit, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ContentPaste, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.widthIn(max = 220.dp),
            )
            TextButton(
                onClick = onPaste,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
            ) { Text("Вставить") }
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Очистить буфер", tint = MaterialTheme.colorScheme.onTertiaryContainer) }
        }
    }
}

/** Компактный переключатель активной панели: активная «пилюля» шире и залита primary. */
@Composable
private fun PanelSwitch(active: Int, onSelect: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (i in 0..1) {
            val selected = i == active
            val bg by animateColorAsState(if (selected) scheme.primary else Color.Transparent, label = "pbg")
            val fg by animateColorAsState(if (selected) scheme.onPrimary else scheme.onSurfaceVariant, label = "pfg")
            val w by animateDpAsState(if (selected) 36.dp else 26.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "pw")
            Box(
                Modifier
                    .height(30.dp)
                    .width(w)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(i) }
                    .semantics { contentDescription = "Панель ${i + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = fg)
            }
        }
    }
}

@Composable
private fun BrowserTopBar(
    state: PanelState,
    active: Int,
    showPanelSwitch: Boolean,
    onMenu: () -> Unit,
    showMenu: Boolean = true,
    onCloseSelection: () -> Unit,
    onSearch: () -> Unit,
    onSortView: () -> Unit,
    onSwitchPanel: (Int) -> Unit,
    onSwapPanels: () -> Unit,
    onRefresh: () -> Unit,
    onFilter: () -> Unit,
    onPathJump: () -> Unit,
    onToggleHidden: () -> Unit,
    showHidden: Boolean,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onSettings: () -> Unit,
    onAnalyze: () -> Unit,
    onTerminal: () -> Unit,
    onFolderSizes: () -> Unit,
    onHome: () -> Unit,
    onSetHome: () -> Unit,
    onExit: () -> Unit,
    onCompareFolders: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            AnimatedContent(state.selectionMode, transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() }, label = "nav") { sel ->
                if (sel) IconButton(onClick = onCloseSelection) { Icon(Icons.Rounded.Close, "Снять выделение") }
                else if (showMenu) IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, "Меню") }
            }
        },
        title = {
            AnimatedContent(
                targetState = if (state.selectionMode) "sel:${state.selection.size}" else state.location.title.ifEmpty { "/" },
                transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith (fadeOut() + slideOutVertically { -it / 3 }) },
                label = "title",
            ) { t ->
                if (t.startsWith("sel:")) {
                    val sel = state.selectedItems
                    val bytes = remember(state.selection, state.items) { sel.sumOf { maxOf(it.size, 0L) } }
                    val dirs = sel.count { it.isDirectory }
                    Column {
                        Text("Выбрано: ${t.removePrefix("sel:")}", maxLines = 1)
                        Text(
                            buildString {
                                append(Format.size(bytes, LocalAppSettings.current.sizeUnits))
                                if (dirs > 0) append("  •  ").append(Format.folders(dirs))
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                } else {
                    // Поле-пилюля поиска вместо заголовка: имя папки и так видно в хлебных крошках.
                    Surface(
                        onClick = onSearch,
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Поиск",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        actions = {
            if (state.selectionMode) {
                IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, "Выделить всё") }
                IconButton(onClick = onInvert) { Icon(Icons.Rounded.Flip, "Инвертировать") }
            } else {
                IconButton(onClick = onSortView) { Icon(Icons.AutoMirrored.Rounded.Sort, "Сортировка и вид") }
                if (showPanelSwitch) PanelSwitch(active, onSwitchPanel)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                    DropdownMenuPopup(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 3)) {
                            DropdownMenuItem(text = { Text("Домой") }, leadingIcon = { Icon(Icons.Rounded.Home, null) }, onClick = { menu = false; onHome() }, shape = MenuDefaults.leadingItemShape)
                            DropdownMenuItem(text = { Text("Сделать домашней") }, leadingIcon = { Icon(Icons.Rounded.AddHome, null) }, onClick = { menu = false; onSetHome() }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Обновить") }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) }, onClick = { menu = false; onRefresh() }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Фильтр") }, leadingIcon = { Icon(Icons.Rounded.FilterList, null) }, onClick = { menu = false; onFilter() }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Перейти к пути") }, leadingIcon = { Icon(Icons.Rounded.Route, null) }, onClick = { menu = false; onPathJump() }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(
                                text = { Text(if (showHidden) "Не показывать скрытые" else "Показать скрытые") },
                                leadingIcon = { Icon(if (showHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null) },
                                onClick = { menu = false; onToggleHidden() },
                                shape = MenuDefaults.trailingItemShape,
                            )
                        }
                        Spacer(Modifier.size(MenuDefaults.GroupSpacing))
                        DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 3)) {
                            DropdownMenuItem(text = { Text("Поменять панели местами") }, leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) }, onClick = { menu = false; onSwapPanels() }, shape = MenuDefaults.leadingItemShape)
                            DropdownMenuItem(text = { Text("Посчитать размеры папок") }, leadingIcon = { Icon(Icons.Rounded.Calculate, null) }, onClick = { menu = false; onFolderSizes() }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Сравнить папки панелей") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.CompareArrows, null) }, onClick = { menu = false; onCompareFolders() }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Анализ этой папки") }, leadingIcon = { Icon(Icons.Rounded.DonutLarge, null) }, onClick = { menu = false; onAnalyze() }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Терминал здесь") }, leadingIcon = { Icon(Icons.Rounded.Terminal, null) }, onClick = { menu = false; onTerminal() }, shape = MenuDefaults.middleItemShape)
                            DropdownMenuItem(text = { Text("Настройки") }, leadingIcon = { Icon(Icons.Rounded.Settings, null) }, onClick = { menu = false; onSettings() }, shape = MenuDefaults.trailingItemShape)
                        }
                        Spacer(Modifier.size(MenuDefaults.GroupSpacing))
                        DropdownMenuGroup(shapes = MenuDefaults.groupShape(2, 3)) {
                            DropdownMenuItem(text = { Text("Выход") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ExitToApp, null) }, onClick = { menu = false; onExit() }, shape = MenuDefaults.standaloneItemShape)
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

/** Горячие клавиши для аппаратной клавиатуры (планшеты, Chromebook, DeX). */
private fun handleShortcut(
    e: androidx.compose.ui.input.key.KeyEvent,
    vm: BrowserViewModel,
    st: PanelState,
    onDialog: (BrowserDialog) -> Unit,
    onSwitchPanel: () -> Unit,
): Boolean {
    val panel = vm.activePanel
    val ctrl = e.isCtrlPressed
    return when {
        ctrl && e.key == Key.A -> { panel.selectAll(); true }
        ctrl && e.key == Key.C -> { vm.copyToClipboard(cut = false); true }
        ctrl && e.key == Key.X -> { vm.copyToClipboard(cut = true); true }
        ctrl && e.key == Key.V -> { vm.paste(); true }
        ctrl && e.key == Key.F -> { panel.showFilter(true); true }
        ctrl && e.key == Key.L -> { onDialog(BrowserDialog.PathJump); true }
        ctrl && e.key == Key.H -> { vm.updateSettings { it.copy(showHidden = !it.showHidden) }; true }
        ctrl && e.key == Key.R -> { panel.refresh(); true }
        ctrl && e.key == Key.N && st.location is client.wexmanager.core.fs.Location.Local -> {
            onDialog(BrowserDialog.Create(folder = e.isShiftPressed)); true
        }
        e.key == Key.Delete -> {
            if (st.selectedItems.isNotEmpty()) onDialog(BrowserDialog.Delete(st.selectedItems)); true
        }
        e.key == Key.F2 -> {
            val sel = st.selectedItems
            if (sel.size == 1 && !sel[0].isInArchive) onDialog(BrowserDialog.Rename(sel[0]))
            else if (sel.size > 1 && sel.none { it.isInArchive }) onDialog(BrowserDialog.BatchRename(sel))
            true
        }
        e.key == Key.F5 -> { panel.refresh(); true }
        e.key == Key.Tab -> { onSwitchPanel(); true }
        e.key == Key.Escape && st.selectionMode -> { panel.clearSelection(); true }
        e.key == Key.Backspace || (e.isAltPressed && e.key == Key.DirectionUp) -> { panel.goUp(); true }
        e.isAltPressed && e.key == Key.DirectionLeft -> { panel.goBack(); true }
        e.isAltPressed && e.key == Key.DirectionRight -> { panel.goForward(); true }
        else -> false
    }
}
