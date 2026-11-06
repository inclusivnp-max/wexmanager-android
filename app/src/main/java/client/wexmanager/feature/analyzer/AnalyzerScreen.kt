package client.wexmanager.feature.analyzer

import android.app.Application
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.ActionGroup
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.feature.browser.SheetAction
import client.wexmanager.ui.components.ConfirmDialog
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.theme.LocalAppSettings

private enum class AnalyzerTab(val title: String, val icon: ImageVector) {
    Overview("Обзор", Icons.Rounded.DonutLarge),
    Largest("Крупные", Icons.Rounded.Leaderboard),
    Empty("Пустые", Icons.Rounded.FolderOff),
}

private enum class Phase { Scanning, Error, Ready }

/** Снимок уровня дерева для AnimatedContent — уходящий уровень рисует свои данные. */
private class Level(val node: DirNode, val path: String, val depth: Int, val crumbs: List<String>)

private class ScrollMemory {
    val positions = HashMap<String, Pair<Int, Int>>()
}

@Composable
fun AnalyzerScreen(root: String, onBack: () -> Unit, onReveal: (String) -> Unit, onCleanup: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: AnalyzerViewModel = viewModel(key = "analyzer:$root") { AnalyzerViewModel(app, root) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val primaryPath = remember { context.appContainer.storage.primaryPath }

    var tab by rememberSaveable { mutableStateOf(AnalyzerTab.Overview) }
    var menuTarget by remember { mutableStateOf<Target?>(null) }
    var confirm by remember { mutableStateOf<List<Target>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val memory = remember { ScrollMemory() }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val ready = state.tree != null && !state.scanning
    BackHandler(ready && tab == AnalyzerTab.Overview && state.depth > 0) { vm.up() }
    BackHandler(ready && tab != AnalyzerTab.Overview) { tab = AnalyzerTab.Overview }

    val rootTitle = when {
        state.rootPath == primaryPath -> "Внутренняя память"
        state.rootPath == "/" -> "Корень"
        else -> state.rootPath.substringAfterLast('/')
    }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Анализ памяти", maxLines = 1) },
                subtitle = {
                    Text(
                        if (ready) state.currentPath else state.rootPath,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                ready && tab != AnalyzerTab.Overview -> tab = AnalyzerTab.Overview
                                ready && vm.up() -> Unit
                                else -> onBack()
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                actions = {
                    IconButton(onClick = onCleanup, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.CleaningServices, "Очистка памяти")
                    }
                    IconButton(onClick = vm::rescan, enabled = !state.scanning, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Refresh, "Пересканировать")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        val phase = when {
            state.scanning -> Phase.Scanning
            state.tree == null -> Phase.Error
            else -> Phase.Ready
        }
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.04f))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            label = "analyzerPhase",
        ) { p ->
            when (p) {
                Phase.Scanning -> ScanningPane(state.progress, state.rootPath, onCancel = onBack)
                Phase.Error -> MessagePane(
                    polygon = MaterialShapes.Clover4Leaf,
                    icon = if (state.error?.startsWith("Нет доступа") == true) Icons.Rounded.Lock else Icons.Rounded.ErrorOutline,
                    error = true,
                    title = "Не удалось просканировать",
                    text = state.error.orEmpty(),
                    action = "Повторить" to vm::rescan,
                )
                Phase.Ready -> Column(Modifier.fillMaxSize()) {
                    ConnectedChoice(
                        options = AnalyzerTab.entries,
                        selected = tab,
                        onSelect = { tab = it },
                        label = { it.title },
                        icon = { it.icon },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val forward = targetState.ordinal > initialState.ordinal
                            (slideInHorizontally(motion.defaultSpatialSpec()) { if (forward) it / 4 else -it / 4 } + fadeIn(motion.defaultEffectsSpec()))
                                .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { if (forward) -it / 6 else it / 6 } + fadeOut(motion.fastEffectsSpec()))
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        label = "analyzerTab",
                    ) { t ->
                        when (t) {
                            AnalyzerTab.Overview -> OverviewTab(
                                state = state,
                                rootTitle = rootTitle,
                                rootIsPrimary = state.rootPath == primaryPath,
                                memory = memory,
                                onOpen = vm::open,
                                onJump = vm::jumpTo,
                                onMenu = { menuTarget = it },
                            )
                            AnalyzerTab.Largest -> LargestTab(
                                state = state,
                                onSort = vm::setLargestSort,
                                onMenu = { menuTarget = it },
                            )
                            AnalyzerTab.Empty -> EmptyTab(
                                state = state,
                                onMenu = { menuTarget = it },
                                onReveal = onReveal,
                                onDeleteAll = { list -> confirm = list },
                            )
                        }
                    }
                }
            }
        }
    }

    menuTarget?.let { t ->
        TargetSheet(
            target = t,
            rootPath = state.rootPath,
            useTrash = settings.useTrash,
            onReveal = { onReveal(t.path) },
            onDelete = { confirm = listOf(t) },
            onDismiss = { menuTarget = null },
        )
    }

    confirm?.let { list ->
        val single = list.singleOrNull()
        ConfirmDialog(
            title = if (single != null) "Удалить «${single.name}»?" else "Удалить ${Format.items(list.size)}?",
            text = buildString {
                val size = list.sumOf { it.size }
                if (size > 0) append("Освободится ${Format.size(size, settings.sizeUnits)}. ")
                append(if (settings.useTrash) "Элементы будут перемещены в корзину." else "Действие нельзя отменить.")
            },
            icon = Icons.Rounded.Delete,
            confirmLabel = "Удалить",
            onConfirm = { vm.delete(list) },
            onDismiss = { confirm = null },
            destructive = true,
        )
    }
}

// ------------------------------------------------------------------ Сканирование

@Composable
private fun ScanningPane(progress: ScanProgress, rootPath: String, onCancel: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    val density = LocalDensity.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            val stroke = remember(density) { Stroke(width = with(density) { 6.dp.toPx() }, cap = StrokeCap.Round) }
            CircularWavyProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = scheme.primary,
                trackColor = scheme.surfaceContainerHighest,
                stroke = stroke,
                trackStroke = stroke,
            )
            ContainedLoadingIndicator(
                modifier = Modifier.size(104.dp),
                containerColor = scheme.primaryContainer,
                indicatorColor = scheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(
            Format.size(progress.bytes, s.sizeUnits),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Text("Сканирование…", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatPill("Файлов", progress.files.toString(), scheme.secondaryContainer, scheme.onSecondaryContainer)
            StatPill("Папок", progress.dirs.toString(), scheme.tertiaryContainer, scheme.onTertiaryContainer)
        }
        Spacer(Modifier.height(16.dp))
        Surface(
            color = scheme.surfaceContainer,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 480.dp),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    progress.current.removePrefix(rootPath).ifEmpty { "/" },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text("Отмена") }
    }
}

@Composable
private fun StatPill(label: String, value: String, container: Color, content: Color) {
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .widthIn(min = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = content)
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f))
    }
}

// ------------------------------------------------------------------ Обзор

@Composable
private fun OverviewTab(
    state: AnalyzerState,
    rootTitle: String,
    rootIsPrimary: Boolean,
    memory: ScrollMemory,
    onOpen: (DirNode) -> Unit,
    onJump: (Int) -> Unit,
    onMenu: (Target) -> Unit,
) {
    val current = state.current ?: return
    val level = Level(current, state.currentPath, state.depth, state.stack.drop(1).map { it.name })
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = level,
        contentKey = { it.path },
        transitionSpec = {
            val spec: ContentTransform = when {
                targetState.depth > initialState.depth ->
                    (slideInHorizontally(motion.defaultSpatialSpec()) { it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                        .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { -it / 4 } + fadeOut(motion.fastEffectsSpec()))
                targetState.depth < initialState.depth ->
                    (slideInHorizontally(motion.defaultSpatialSpec()) { -it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                        .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { it / 4 } + fadeOut(motion.fastEffectsSpec()))
                else -> fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
            }
            spec
        },
        modifier = Modifier.fillMaxSize(),
        label = "analyzerLevel",
    ) { lv ->
        LevelList(
            level = lv,
            rootTitle = rootTitle,
            rootIsPrimary = rootIsPrimary,
            unreadable = if (lv.depth == 0) state.unreadable else 0,
            durationMs = if (lv.depth == 0) state.durationMs else 0,
            memory = memory,
            onOpen = onOpen,
            onJump = onJump,
            onMenu = onMenu,
        )
    }
}

private fun childPath(parent: String, name: String) = if (parent == "/") "/$name" else "$parent/$name"

private fun Node.toTarget(parentPath: String) = Target(childPath(parentPath, name), name, isDir, size, lastModified)

private fun percent(part: Long, total: Long): String {
    if (total <= 0L) return "0%"
    val p = part * 100.0 / total
    return when {
        p <= 0.0 -> "0%"
        p < 0.1 -> "<0,1%"
        p < 10 -> String.format(Format.RU, "%.1f%%", p)
        else -> String.format(Format.RU, "%.0f%%", p)
    }
}

@Composable
private fun LevelList(
    level: Level,
    rootTitle: String,
    rootIsPrimary: Boolean,
    unreadable: Int,
    durationMs: Long,
    memory: ScrollMemory,
    onOpen: (DirNode) -> Unit,
    onJump: (Int) -> Unit,
    onMenu: (Target) -> Unit,
) {
    val dir = level.node
    val openedAt = remember { SystemClock.uptimeMillis() }
    val saved = memory.positions[level.path]
    val listState = rememberLazyListState(saved?.first ?: 0, saved?.second ?: 0)
    DisposableEffect(level.path) {
        onDispose { memory.positions[level.path] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottom + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "crumbs", contentType = "crumbs") {
            Crumbs(rootTitle, rootIsPrimary, level.crumbs, onJump)
        }
        item(key = "hero", contentType = "hero") {
            HeroCard(dir, unreadable, durationMs, level.path, onOpen, onMenu)
        }
        if (dir.size > 0L) {
            item(key = "categories", contentType = "categories") { CategoryCard(dir) }
        }
        item(key = "header", contentType = "header") {
            SectionHeader(
                "Содержимое",
                if (dir.children.isEmpty()) "" else Format.items(dir.children.size),
            )
        }
        if (dir.children.isEmpty()) {
            item(key = "empty", contentType = "emptyLevel") {
                Text(
                    if (dir.unreadable) "Нет доступа к содержимому" else "Папка пуста",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
        } else {
            childRows(dir, level.path, openedAt, onOpen, onMenu)
        }
    }
}

/** Строки, появившиеся в первые мгновения после открытия списка, анимируют полоску заполнения. */
private const val GROW_WINDOW_MS = 700L

private fun LazyListScope.childRows(dir: DirNode, path: String, openedAt: Long, onOpen: (DirNode) -> Unit, onMenu: (Target) -> Unit) {
    val maxSize = dir.children.firstOrNull()?.size ?: 0L
    val count = dir.children.size
    itemsIndexed(dir.children, key = { _, n -> "c:" + n.name }, contentType = { _, n -> if (n.isDir) 1 else 2 }) { i, node ->
        val item = remember(node, path) {
            FileItem(path = childPath(path, node.name), name = node.name, isDirectory = node.isDir, size = node.size, lastModified = node.lastModified)
        }
        SizeRow(
            item = item,
            title = node.name,
            supporting = buildString {
                append(Format.size(node.size, LocalAppSettings.current.sizeUnits))
                append("  •  ")
                append(percent(node.size, dir.size))
                if (node is DirNode) {
                    append("  •  ")
                    append(Format.files(node.fileCount))
                }
            },
            fraction = if (maxSize > 0) node.size.toFloat() / maxSize else 0f,
            index = i,
            count = count,
            showChevron = node is DirNode,
            onClick = { if (node is DirNode) onOpen(node) else onMenu(node.toTarget(path)) },
            onLongClick = { onMenu(node.toTarget(path)) },
            animateIn = remember { SystemClock.uptimeMillis() - openedAt < GROW_WINDOW_MS },
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun Crumbs(rootTitle: String, rootIsPrimary: Boolean, names: List<String>, onJump: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rowState = rememberLazyListState()
    LaunchedEffect(names.size) { rowState.animateScrollToItem(names.size) }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        val all = listOf(rootTitle) + names
        itemsIndexed(all, key = { i, n -> "$i:$n" }) { i, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (i > 0) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = scheme.outline, modifier = Modifier.size(16.dp))
                val last = i == all.lastIndex
                Row(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (last) scheme.secondaryContainer else Color.Transparent)
                        .clickable(enabled = !last) { onJump(i) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (i == 0) {
                        Icon(
                            if (rootIsPrimary) Icons.Rounded.PhoneAndroid else Icons.Rounded.Storage, null,
                            tint = if (last) scheme.onSecondaryContainer else scheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        name,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (last) FontWeight.Bold else FontWeight.Medium,
                        color = if (last) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    dir: DirNode,
    unreadable: Int,
    durationMs: Long,
    path: String,
    onOpen: (DirNode) -> Unit,
    onMenu: (Target) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    val palette = slicePalette()
    val otherColor = scheme.outlineVariant
    val slices = remember(dir, palette, otherColor) { buildSlices(dir, palette, otherColor) }
    Surface(
        color = scheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DonutChart(
                slices = slices,
                trackColor = scheme.surfaceContainerHighest,
                modifier = Modifier.size(212.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        Format.size(dir.size, s.sizeUnits),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        Format.files(dir.fileCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        Format.folders(dir.dirCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (unreadable > 0 || durationMs > 0) {
                Spacer(Modifier.height(10.dp))
                Text(
                    buildList {
                        if (durationMs > 0) add("Просканировано за ${String.format(Format.RU, "%.1f", durationMs / 1000f)} с")
                        if (unreadable > 0) add("недоступно папок: $unreadable")
                    }.joinToString("  •  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    textAlign = TextAlign.Center,
                )
            }
            if (slices.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                slices.forEach { slice ->
                    val node = slice.node
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(enabled = node != null) {
                                when (node) {
                                    is DirNode -> onOpen(node)
                                    is FileNode -> onMenu(node.toTarget(path))
                                    null -> Unit
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(slice.color),
                        )
                        Spacer(Modifier.width(12.dp))
                        if (node is DirNode) {
                            Icon(Icons.Rounded.Folder, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            slice.label,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(Format.size(slice.size, s.sizeUnits), style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                        Text(
                            percent(slice.size, dir.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(52.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(dir: DirNode) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    val colors = Category.entries.map { categoryColor(it) }
    val entries = remember(dir, colors) {
        Category.entries
            .map { Triple(it, dir.categories[it.ordinal], colors[it.ordinal]) }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
    }
    val bar = remember(entries) { entries.map { it.second to it.third } }
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("По типам файлов", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(14.dp))
            SegmentedBar(
                values = bar,
                trackColor = scheme.surfaceContainerHighest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp),
            )
            Spacer(Modifier.height(10.dp))
            entries.forEach { (cat, bytes, color) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(cat.icon, null, tint = color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(cat.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(Format.size(bytes, s.sizeUnits), style = MaterialTheme.typography.labelLarge)
                    Text(
                        percent(bytes, dir.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(52.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SizeRow(
    item: FileItem,
    title: String,
    supporting: String,
    fraction: Float,
    index: Int,
    count: Int,
    showChevron: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    location: String? = null,
    animateIn: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { FileIcon(item = item, size = 40.dp) },
        trailingContent = if (showChevron) ({ Icon(Icons.Rounded.ChevronRight, null) }) else null,
        supportingContent = {
            Column {
                if (location != null) {
                    Text(location, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.labelSmall, color = scheme.outline)
                }
                Text(supporting, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                GrowingBar(fraction, animateIn)
            }
        },
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(title, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
    }
}

/** Полоска заполнения; [animateIn] — «вырасти» из нуля (только для строк, видимых при открытии списка). */
@Composable
private fun GrowingBar(fraction: Float, animateIn: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val anim = remember { androidx.compose.animation.core.Animatable(if (animateIn) 0f else fraction.coerceIn(0f, 1f)) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), spec) }
    LinearProgressIndicator(
        progress = { anim.value },
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp),
        color = scheme.primary,
        trackColor = scheme.surfaceContainerHighest,
        gapSize = 2.dp,
        drawStopIndicator = {},
    )
}

// ------------------------------------------------------------------ Крупнейшие файлы

@Composable
private fun LargestTab(state: AnalyzerState, onSort: (LargestSort) -> Unit, onMenu: (Target) -> Unit) {
    val s = LocalAppSettings.current
    val list = remember(state.largest, state.largestSort) { state.orderedLargest() }
    if (list.isEmpty()) {
        MessagePane(MaterialShapes.Cookie9Sided, Icons.Rounded.Leaderboard, false, "Файлов нет", "В этой папке нет файлов")
        return
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxSize = list.maxOf { it.size }
    val openedAt = remember { SystemClock.uptimeMillis() }
    val heading = when (state.largestSort) {
        LargestSort.Size -> "Топ-${list.size} по размеру"
        LargestSort.Name -> "Крупные по имени"
        LargestSort.Modified -> "Крупные по дате изменения"
    }
    Column(Modifier.fillMaxSize()) {
        ConnectedChoice(
            options = LargestSort.entries,
            selected = state.largestSort,
            onSelect = onSort,
            label = { it.title },
            icon = { largestSortIcon(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = bottom + 24.dp), modifier = Modifier.weight(1f).fillMaxWidth()) {
            item(key = "header") {
                SectionHeader(heading, "всего ${Format.size(list.sumOf { it.size }, s.sizeUnits)}")
            }
            itemsIndexed(list, key = { _, e -> e.path }) { i, e ->
                val item = remember(e) { FileItem(path = e.path, name = e.name, isDirectory = false, size = e.size, lastModified = e.lastModified) }
                val target = Target(e.path, e.name, false, e.size, e.lastModified)
                SizeRow(
                    item = item,
                    title = e.name,
                    location = relativeParent(e.path, state.rootPath),
                    supporting = "${Format.size(e.size, s.sizeUnits)}  •  ${Format.date(e.lastModified, s.dateFormat)}",
                    fraction = if (maxSize > 0) e.size.toFloat() / maxSize else 0f,
                    index = i,
                    count = list.size,
                    showChevron = false,
                    onClick = { onMenu(target) },
                    onLongClick = { onMenu(target) },
                    animateIn = remember { SystemClock.uptimeMillis() - openedAt < GROW_WINDOW_MS },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

private fun largestSortIcon(sort: LargestSort): ImageVector = when (sort) {
    LargestSort.Size -> Icons.Rounded.DataUsage
    LargestSort.Name -> Icons.Rounded.SortByAlpha
    LargestSort.Modified -> Icons.Rounded.Schedule
}

private fun relativeParent(path: String, root: String): String {
    val parent = path.substringBeforeLast('/', "")
    val rel = parent.removePrefix(root).trimStart('/')
    val rootName = if (root == "/") "" else root.substringAfterLast('/')
    return if (rel.isEmpty()) "$rootName/" else "$rootName/$rel"
}

// ------------------------------------------------------------------ Пустые папки

@Composable
private fun EmptyTab(
    state: AnalyzerState,
    onMenu: (Target) -> Unit,
    onReveal: (String) -> Unit,
    onDeleteAll: (List<Target>) -> Unit,
) {
    val list = state.emptyDirs
    if (list.isEmpty()) {
        MessagePane(MaterialShapes.Sunny, Icons.Rounded.CleaningServices, false, "Пустых папок нет", "Здесь всё чисто")
        return
    }
    val scheme = MaterialTheme.colorScheme
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(contentPadding = PaddingValues(bottom = bottom + 24.dp), modifier = Modifier.fillMaxSize()) {
        item(key = "header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Найдено: ${Format.folders(list.size)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = { onDeleteAll(list.map { Target(it, it.substringAfterLast('/'), true, 0L, 0L) }) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Удалить все")
                }
            }
        }
        itemsIndexed(list, key = { _, p -> p }) { i, p ->
            val name = p.substringAfterLast('/')
            val item = remember(p) { FileItem(path = p, name = name, isDirectory = true, size = 0L, lastModified = 0L) }
            val target = Target(p, name, true, 0L, 0L)
            SegmentedListItem(
                onClick = { onMenu(target) },
                onLongClick = { onMenu(target) },
                shapes = ListItemDefaults.segmentedShapes(i, list.size),
                leadingContent = { FileIcon(item = item, size = 40.dp, showThumbnail = false) },
                trailingContent = {
                    IconButton(onClick = { onReveal(p) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.FolderOpen, "Показать в папке")
                    }
                },
                supportingContent = { Text(relativeParent(p, state.rootPath), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = ListItemDefaults.SegmentedGap)
                    .animateItem(),
            ) {
                Text(name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            }
        }
    }
}

// ------------------------------------------------------------------ Общие элементы

@Composable
private fun MessagePane(
    polygon: RoundedPolygon,
    icon: ImageVector,
    error: Boolean,
    title: String,
    text: String,
    action: Pair<String, () -> Unit>? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val shape = remember(polygon) { PolygonShape(polygon) }
        Box(
            Modifier
                .size(116.dp)
                .clip(shape)
                .background(if (error) scheme.errorContainer else scheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer, modifier = Modifier.size(50.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (text.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = action.second, shapes = ButtonDefaults.shapes()) { Text(action.first) }
        }
    }
}

@Composable
private fun TargetSheet(
    target: Target,
    rootPath: String,
    useTrash: Boolean,
    onReveal: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    val item = remember(target) { target.toFileItem() }
    AppBottomSheet(onDismiss) {
        Row(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIcon(item = item, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(target.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    if (target.isDir && target.size == 0L) relativeParent(target.path, rootPath)
                    else "${Format.size(target.size, s.sizeUnits)}  •  ${relativeParent(target.path, rootPath)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        ActionGroup(
            listOf(
                SheetAction("Показать в папке", Icons.Rounded.FolderOpen, supporting = target.path.substringBeforeLast('/')) { onReveal() },
                SheetAction(
                    "Удалить",
                    Icons.Rounded.Delete,
                    supporting = if (useTrash) "Переместить в корзину" else "Безвозвратно",
                ) { onDelete() },
            ),
            onDismiss,
        )
    }
}
