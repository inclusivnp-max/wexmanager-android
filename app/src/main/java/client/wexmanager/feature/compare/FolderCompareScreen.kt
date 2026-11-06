package client.wexmanager.feature.compare

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.core.util.HashAlgorithm
import client.wexmanager.core.util.Hashing
import client.wexmanager.feature.browser.ActionGroup
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.feature.browser.PropRow
import client.wexmanager.feature.browser.SheetAction
import client.wexmanager.ui.components.ConfirmDialog
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.PressIconButton
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File

private enum class FcPhase { Scanning, Error, Ready }

@Composable
fun FolderCompareScreen(left: String, right: String, onBack: () -> Unit, onReveal: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: FolderCompareViewModel = viewModel(key = "folderCompare:$left|$right") { FolderCompareViewModel(app, left, right) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current

    var menu by remember { mutableStateOf<CmpEntry?>(null) }
    var details by remember { mutableStateOf<CmpEntry?>(null) }
    var confirm by remember { mutableStateOf<Pair<CopyPlan, Boolean>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val selecting = state.selected.isNotEmpty()
    BackHandler(selecting) { vm.clearSelection() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val scope = rememberCoroutineScope()

    /** Копирование: если что-то заменяется — сначала подтверждение. */
    fun requestCopy(plan: CopyPlan) {
        if (plan.isEmpty) {
            scope.launch { snackbar.showSnackbar("Нечего копировать в этом направлении") }
            return
        }
        if (plan.replaceCount > 0) confirm = plan to false else vm.execute(plan)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сравнение папок", maxLines = 1) },
                subtitle = {
                    Text(
                        "${displayName(state.left)} ⇄ ${displayName(state.right)}",
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = vm::swap, enabled = !state.running, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.SwapHoriz, "Поменять местами")
                    }
                    IconButton(onClick = { vm.rescan() }, enabled = !state.running, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Refresh, "Сравнить заново")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = if (selecting) 80.dp else 0.dp)) },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { padding ->
        val phase = when {
            state.running -> FcPhase.Scanning
            state.error != null -> FcPhase.Error
            else -> FcPhase.Ready
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
            label = "fcPhase",
        ) { p ->
            when (p) {
                FcPhase.Scanning -> ProgressPane(
                    headline = Format.items(state.progress.items),
                    caption = if (state.byContent && state.progress.compared > 0) {
                        "Сравнение содержимого… ${Format.size(state.progress.compared, settings.sizeUnits)}"
                    } else "Сравнение папок…",
                    fraction = null,
                    stats = emptyList(),
                    current = state.progress.current,
                    onCancel = { if (state.entries.isEmpty()) onBack() else vm.cancelScan() },
                )
                FcPhase.Error -> ComparePane(
                    polygon = MaterialShapes.Clover4Leaf,
                    icon = Icons.Rounded.ErrorOutline,
                    title = "Не удалось сравнить",
                    text = state.error,
                    error = true,
                    action = "Повторить" to { vm.rescan() },
                    secondary = "Назад" to onBack,
                )
                FcPhase.Ready -> Box(Modifier.fillMaxSize()) {
                    EntryList(
                        state = state,
                        settings = settings,
                        bottomExtra = if (selecting) 88.dp else 0.dp,
                        onRecursive = vm::setRecursive,
                        onContent = vm::setByContent,
                        onExtensions = vm::setExtensions,
                        onExtensionsCommit = vm::commitExtensions,
                        onSync = { confirm = vm.syncPlan() to true },
                        onFilter = vm::toggleFilter,
                        onToggle = vm::toggle,
                        onClick = { e -> if (selecting) vm.toggle(e.rel) else menu = e },
                    )
                    AnimatedVisibility(
                        visible = state.copying > 0,
                        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                        modifier = Modifier.align(Alignment.TopCenter),
                    ) {
                        CopyBanner(state.copying, state.copyProgress)
                    }
                    AnimatedVisibility(
                        visible = selecting,
                        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    ) {
                        SelectionToolbar(
                            count = state.selected.size,
                            onClear = vm::clearSelection,
                            onSelectAll = vm::selectAllVisible,
                            onCopyLeft = { requestCopy(vm.plan(CopyDirection.ToLeft, vm.selectedEntries())) },
                            onCopyRight = { requestCopy(vm.plan(CopyDirection.ToRight, vm.selectedEntries())) },
                        )
                    }
                }
            }
        }
    }

    menu?.let { e ->
        EntrySheet(
            entry = e,
            settings = settings,
            onReveal = onReveal,
            onCompare = { details = e },
            onCopy = { dir -> requestCopy(vm.plan(dir, listOf(e))) },
            onSelect = { vm.toggle(e.rel) },
            onDismiss = { menu = null },
        )
    }

    details?.let { e -> ContentCompareDialog(e, settings) { details = null } }

    confirm?.let { (plan, sync) ->
        val dirWord = if (plan.direction == CopyDirection.ToRight) "вправо" else "влево"
        if (plan.isEmpty) {
            ConfirmDialog(
                title = "Синхронизация не нужна",
                text = "Справа уже есть всё, что есть слева, и нет более старых версий файлов.",
                icon = Icons.Rounded.DoneAll,
                confirmLabel = "Понятно",
                onConfirm = {},
                onDismiss = { confirm = null },
            )
        } else {
            ConfirmDialog(
                title = if (sync) "Синхронизировать →" else "Копировать $dirWord?",
                text = buildString {
                    if (state.appliedExtensions.isNotEmpty()) {
                        append("Только расширения: ${state.appliedExtensions.sorted().joinToString(", ")}.\n")
                    }
                    if (sync) append("Отсутствующие справа и более новые слева элементы будут скопированы в «${displayName(state.right)}».\n\n")
                    append("Новых файлов: ${plan.newCount}")
                    if (plan.replaceCount > 0) append("\nБудет заменено: ${Format.files(plan.replaceCount)}")
                    append("\nОбъём: ${Format.size(plan.bytes, settings.sizeUnits)}")
                    if (plan.skipped > 0) append("\nПропущено (нет источника): ${plan.skipped}")
                },
                icon = if (sync) Icons.Rounded.Sync else Icons.AutoMirrored.Rounded.CompareArrows,
                confirmLabel = if (sync) "Синхронизировать" else "Копировать",
                onConfirm = { vm.execute(plan) },
                onDismiss = { confirm = null },
            )
        }
    }
}

// ------------------------------------------------------------------ Список

@Composable
private fun EntryList(
    state: FolderCompareState,
    settings: AppSettings,
    bottomExtra: androidx.compose.ui.unit.Dp,
    onRecursive: (Boolean) -> Unit,
    onContent: (Boolean) -> Unit,
    onExtensions: (String) -> Unit,
    onExtensionsCommit: () -> Unit,
    onSync: () -> Unit,
    onFilter: (CmpStatus) -> Unit,
    onToggle: (String) -> Unit,
    onClick: (CmpEntry) -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val list = state.visible
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottom + 24.dp + bottomExtra),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "roots", contentType = "roots") {
            RootsCard(state, onRecursive, onContent, onExtensions, onExtensionsCommit, onSync)
        }
        item(key = "filters", contentType = "filters") {
            FilterRow(state, onFilter)
        }
        if (list.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                val extActive = state.appliedExtensions.isNotEmpty()
                val nothingMatched = extActive && state.entries.isEmpty()
                val identical = state.counts.filterKeys { it != CmpStatus.Same }.values.sum() == 0 && !nothingMatched
                ComparePane(
                    polygon = if (identical) MaterialShapes.Sunny else MaterialShapes.Cookie9Sided,
                    icon = if (identical) Icons.Rounded.DoneAll else Icons.Rounded.FilterAlt,
                    title = when {
                        nothingMatched -> "Нет таких файлов"
                        identical -> "Папки совпадают"
                        else -> "Нет элементов"
                    },
                    text = when {
                        nothingMatched -> "Ни один файл не подошёл под фильтр расширений. Осталось: ${pairCountText(0)}"
                        identical -> if (state.byContent) "Все файлы одинаковы по содержимому" else "Все файлы совпадают по размеру и дате"
                        else -> "Включите другие категории в фильтре"
                    },
                    modifier = Modifier.height(340.dp),
                )
            }
        } else {
            itemsIndexed(list, key = { _, e -> e.rel }, contentType = { _, _ -> "entry" }) { i, e ->
                EntryRow(
                    entry = e,
                    settings = settings,
                    selected = e.rel in state.selected,
                    index = i,
                    count = list.size,
                    onToggle = { onToggle(e.rel) },
                    onClick = { onClick(e) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RootsCard(
    state: FolderCompareState,
    onRecursive: (Boolean) -> Unit,
    onContent: (Boolean) -> Unit,
    onExtensions: (String) -> Unit,
    onExtensionsCommit: () -> Unit,
    onSync: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            RootLine("Слева", state.left, scheme.secondaryContainer, scheme.onSecondaryContainer)
            Spacer(Modifier.height(8.dp))
            RootLine("Справа", state.right, scheme.tertiaryContainer, scheme.onTertiaryContainer)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OptionChip("С подпапками", Icons.Rounded.AccountTree, state.recursive) { onRecursive(!state.recursive) }
                OptionChip("Сравнивать содержимое", Icons.Rounded.Fingerprint, state.byContent) { onContent(!state.byContent) }
            }
            Spacer(Modifier.height(10.dp))
            val parsed = FolderComparer.parseExtensions(state.extensions)
            val pending = parsed != state.appliedExtensions
            OutlinedTextField(
                value = state.extensions,
                onValueChange = onExtensions,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                label = { Text("Расширения") },
                placeholder = { Text("apk, jpg или *.so") },
                leadingIcon = { Icon(Icons.Rounded.FilterAlt, null) },
                trailingIcon = if (state.extensions.isNotEmpty()) {
                    {
                        IconButton(onClick = {
                            onExtensions("")
                            onExtensionsCommit()
                        }) { Icon(Icons.Rounded.Close, "Очистить") }
                    }
                } else {
                    null
                },
                supportingText = {
                    Text(
                        when {
                            pending -> "Ещё не применено — «Готово» или обновить"
                            parsed.isEmpty() -> "Пусто — все файлы · ${pairCountText(state.entries.size)}"
                            else -> "Осталось: ${pairCountText(state.entries.size)}"
                        },
                    )
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { onExtensionsCommit() }),
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                buildList {
                    add("Сравнено за ${secondsText(state.durationMs)}")
                    add(if (state.byContent) "по содержимому" else "по размеру и дате")
                    if (state.unreadable > 0) add("недоступно папок: ${state.unreadable}")
                    if (state.appliedExtensions.isNotEmpty()) add("осталось ${pairCountText(state.entries.size)}")
                }.joinToString("  •  "),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onSync,
                enabled = state.copying == 0,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Sync, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Синхронизировать →")
            }
        }
    }
}

@Composable
private fun RootLine(label: String, path: String, container: Color, content: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Folder, null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.75f))
            Text(path, style = MaterialTheme.typography.bodyMedium, color = content, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

@Composable
private fun OptionChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(if (selected) Icons.Rounded.Check else icon, null, Modifier.size(FilterChipDefaults.IconSize))
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(state: FolderCompareState, onFilter: (CmpStatus) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        for (status in CmpStatus.entries) {
            val n = state.counts[status] ?: 0
            val colors = statusColors(status)
            val selected = status in state.filters
            FilterChip(
                selected = selected,
                onClick = { onFilter(status) },
                label = { Text("${status.label}  $n") },
                leadingIcon = {
                    Text(
                        status.glyph,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) colors.second else colors.first,
                        modifier = Modifier.widthIn(min = FilterChipDefaults.IconSize),
                        textAlign = TextAlign.Center,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.first,
                    selectedLabelColor = colors.second,
                ),
            )
        }
    }
}

/** Цвет значка статуса: (контейнер, содержимое). */
@Composable
private fun statusColors(status: CmpStatus): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val palette = rememberDiffPalette()
    return when (status) {
        CmpStatus.OnlyLeft -> scheme.secondaryContainer to scheme.onSecondaryContainer
        CmpStatus.OnlyRight -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        CmpStatus.Different -> palette.changed.copy(alpha = 0.28f) to scheme.onSurface
        CmpStatus.Same -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
}

@Composable
private fun StatusBadge(status: CmpStatus, selected: Boolean, onClick: () -> Unit) {
    val (container, content) = statusColors(status)
    val motion = MaterialTheme.motionScheme
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primary else container, motion.fastEffectsSpec(), label = "badge")
    val shape = remember(selected) {
        client.wexmanager.ui.components.PolygonShape(if (selected) MaterialShapes.Cookie9Sided else MaterialShapes.Square)
    }
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(selected, label = "badgeContent") { sel ->
            if (sel) {
                Icon(Icons.Rounded.Check, "Выбрано", tint = scheme.onPrimary, modifier = Modifier.size(22.dp))
            } else {
                Text(
                    status.glyph,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = content,
                )
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: CmpEntry,
    settings: AppSettings,
    selected: Boolean,
    index: Int,
    count: Int,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = onToggle,
        onLongClickLabel = if (selected) "Снять выделение" else "Выделить",
        shapes = ListItemDefaults.segmentedShapes(index, count),
        leadingContent = { StatusBadge(entry.status, selected, onToggle) },
        overlineContent = if (entry.parentRel.isNotEmpty()) ({
            Text(entry.parentRel + "/", maxLines = 1, overflow = TextOverflow.StartEllipsis)
        }) else null,
        supportingContent = {
            Column {
                SideLine("←", entry.left, entry.newer == Newer.Left && entry.right != null, settings)
                SideLine("→", entry.right, entry.newer == Newer.Right && entry.left != null, settings)
                if (entry.reason != null && entry.status == CmpStatus.Different) {
                    Text(
                        "Отличается: ${entry.reason}",
                        style = MaterialTheme.typography.labelSmall,
                        color = rememberDiffPalette().changed,
                        maxLines = 1,
                    )
                }
            }
        },
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.isDir) {
                Icon(Icons.Rounded.Folder, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(entry.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

@Composable
private fun SideLine(arrow: String, side: CmpSide?, newer: Boolean, settings: AppSettings) {
    val scheme = MaterialTheme.colorScheme
    Text(
        buildString {
            append(arrow).append("  ")
            if (side == null) {
                append("нет")
            } else {
                if (side.isDir) {
                    if (side.files > 0 || side.size > 0) append(Format.files(side.files)).append(", ").append(Format.size(side.size, settings.sizeUnits))
                    else append("папка")
                } else {
                    append(Format.size(side.size, settings.sizeUnits))
                }
                append("  •  ").append(Format.date(side.mtime, settings.dateFormat))
                if (newer) append("  •  новее")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = when {
            side == null -> scheme.outline
            newer -> scheme.primary
            else -> scheme.onSurfaceVariant
        },
        fontWeight = if (newer) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CopyBanner(count: Int, progress: Float) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.secondaryContainer,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                "Копирование… ${Format.plural(count, "задача", "задачи", "задач").let { "$count $it" }}",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SelectionToolbar(
    count: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit,
    onCopyLeft: () -> Unit,
    onCopyRight: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        leadingContent = { PressIconButton(Icons.Rounded.Close, "Снять выделение", onClear) },
    ) {
        Box(Modifier.align(Alignment.CenterVertically).widthIn(min = 28.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(count, label = "count") { n ->
                Text("$n", style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer)
            }
        }
        PressIconButton(Icons.Rounded.SelectAll, "Выделить все видимые", onSelectAll)
        Spacer(Modifier.width(4.dp))
        Button(onClick = onCopyLeft, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 14.dp)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(6.dp))
            Text("Влево")
        }
        Spacer(Modifier.width(6.dp))
        Button(onClick = onCopyRight, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text("Вправо")
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(ButtonDefaults.IconSize))
        }
    }
}

// ------------------------------------------------------------------ Меню и детали

@Composable
private fun EntrySheet(
    entry: CmpEntry,
    settings: AppSettings,
    onReveal: (String) -> Unit,
    onCompare: () -> Unit,
    onCopy: (CopyDirection) -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val item = remember(entry) { (entry.left ?: entry.right)!!.toFileItem() }
    AppBottomSheet(onDismiss) {
        Row(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIcon(item = item, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    "${entry.status.glyph}  ${entry.status.label}" + (entry.reason?.let { " ($it)" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                if (entry.parentRel.isNotEmpty()) {
                    Text(
                        entry.parentRel + "/",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.StartEllipsis,
                    )
                }
            }
        }
        val actions = buildList {
            entry.left?.let { add(SheetAction("Показать слева", Icons.Rounded.FolderOpen, supporting = it.path) { onReveal(it.path) }) }
            entry.right?.let { add(SheetAction("Показать справа", Icons.Rounded.FolderOpen, supporting = it.path) { onReveal(it.path) }) }
            if (entry.left?.isDir == false && entry.right?.isDir == false) {
                add(SheetAction("Сравнить содержимое", Icons.Rounded.Difference, supporting = "Размеры, даты, хеши") { onCompare() })
            }
            if (entry.left != null && entry.status != CmpStatus.Same) {
                add(SheetAction("Копировать вправо", Icons.AutoMirrored.Rounded.ArrowForward, supporting = if (entry.right != null) "С заменой" else null) { onCopy(CopyDirection.ToRight) })
            }
            if (entry.right != null && entry.status != CmpStatus.Same) {
                add(SheetAction("Копировать влево", Icons.AutoMirrored.Rounded.ArrowBack, supporting = if (entry.left != null) "С заменой" else null) { onCopy(CopyDirection.ToLeft) })
            }
            add(SheetAction("Выделить", Icons.Rounded.Check) { onSelect() })
        }
        ActionGroup(actions, onDismiss)
        // Сводка обеих сторон.
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
            SideLine("←", entry.left, entry.newer == Newer.Left && entry.right != null, settings)
            SideLine("→", entry.right, entry.newer == Newer.Right && entry.left != null, settings)
        }
    }
}

private class ContentReport(
    val leftHash: String,
    val rightHash: String,
    val equal: Boolean,
    val lineStats: DiffStats?,
    val linesNote: String?,
)

/** Диалог «Сравнить содержимое»: размеры, даты, SHA-1 и (для текста) построчная статистика. */
@Composable
private fun ContentCompareDialog(entry: CmpEntry, settings: AppSettings, onDismiss: () -> Unit) {
    val l = entry.left ?: return
    val r = entry.right ?: return
    val scheme = MaterialTheme.colorScheme
    val palette = rememberDiffPalette()
    val report by produceState<Result<ContentReport>?>(null, entry) {
        value = runCatching {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val lh = async { Hashing.compute(File(l.path), listOf(HashAlgorithm.SHA1))[HashAlgorithm.SHA1].orEmpty() }
                    val rh = async { Hashing.compute(File(r.path), listOf(HashAlgorithm.SHA1))[HashAlgorithm.SHA1].orEmpty() }
                    val leftHash = lh.await()
                    val rightHash = rh.await()
                    val equal = leftHash == rightHash && l.size == r.size
                    var stats: DiffStats? = null
                    var note: String? = null
                    val textual = isTextual(l.path) && isTextual(r.path)
                    if (!equal && textual) {
                        if (l.size <= DiffViewModel.MAX_BYTES && r.size <= DiffViewModel.MAX_BYTES) {
                            val a = readTextFile(l.path)
                            val b = readTextFile(r.path)
                            stats = withContext(Dispatchers.Default) {
                                val job = currentCoroutineContext()[Job]
                                val (del, ins) = DiffEngine.diff(a.lines, b.lines, job)
                                DiffEngine.stats(DiffEngine.align(a.lines, b.lines, del, ins, job))
                            }
                            if (stats.total == 0) note = "Строки совпадают — отличаются окончания строк или кодировка"
                        } else {
                            note = "Файлы больше ${Format.size(DiffViewModel.MAX_BYTES)} — построчно не сравниваются"
                        }
                    }
                    ContentReport(leftHash, rightHash, equal, stats, note)
                }
            }
        }.onFailure { if (it is CancellationException) throw it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Difference, null) },
        title = { Text(entry.name, maxLines = 2, overflow = TextOverflow.MiddleEllipsis) },
        text = {
            Column {
                PropRow("Размер ←", "${Format.size(l.size, settings.sizeUnits)} (${Format.bytesExact(l.size)})")
                PropRow("Размер →", "${Format.size(r.size, settings.sizeUnits)} (${Format.bytesExact(r.size)})")
                PropRow("Изменён ←", Format.fullDate(l.mtime))
                PropRow("Изменён →", Format.fullDate(r.mtime))
                Spacer(Modifier.height(8.dp))
                val rep = report
                when {
                    rep == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Вычисление хешей…", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                    rep.isFailure -> Text(
                        "Не удалось прочитать: ${rep.exceptionOrNull()?.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.error,
                    )
                    else -> {
                        val c = rep.getOrThrow()
                        PropRow("SHA-1 ←", c.leftHash, copy = true, mono = true)
                        PropRow("SHA-1 →", c.rightHash, copy = true, mono = true)
                        Spacer(Modifier.height(10.dp))
                        val onEqual = c.equal
                        val cardContent = if (onEqual) scheme.onPrimaryContainer else scheme.onSurface
                        Surface(
                            color = if (onEqual) scheme.primaryContainer else palette.changed.copy(alpha = 0.22f),
                            contentColor = cardContent,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    if (onEqual) "Содержимое совпадает" else "Содержимое различается",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = cardContent,
                                )
                                c.lineStats?.let { st ->
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("+${st.added}", color = if (onEqual) cardContent else palette.added, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono))
                                        Text("−${st.removed}", color = if (onEqual) cardContent else palette.removed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono))
                                        Text("~${st.changed}", color = if (onEqual) cardContent else palette.changed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono))
                                        Text("строк", style = MaterialTheme.typography.labelLarge, color = cardContent)
                                    }
                                }
                                c.linesNote?.let {
                                    Spacer(Modifier.height(4.dp))
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = cardContent)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

private fun pairCountText(n: Int): String = "$n ${Format.plural(n, "пара", "пары", "пар")}"

private fun isTextual(path: String): Boolean {
    val kind = FileKind.of(path.substringAfterLast('/'), false)
    return kind.isTextLike || (kind == FileKind.Unknown && FileOpener.looksLikeText(File(path)))
}
