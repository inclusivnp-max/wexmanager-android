package client.wexmanager.feature.compare

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.ActionGroup
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.feature.browser.SheetAction
import client.wexmanager.ui.components.ConfirmDialog
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.PressIconButton
import client.wexmanager.ui.theme.LocalAppSettings
import java.io.File

private enum class DupPhaseUi { Scanning, Error, Ready }

@Composable
fun DuplicatesScreen(root: String, onBack: () -> Unit, onReveal: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: DuplicatesViewModel = viewModel(key = "duplicates:$root") { DuplicatesViewModel(app, root) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current

    var menuFile by remember { mutableStateOf<DupFile?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var confirmNewest by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val selecting = state.selected.isNotEmpty()
    BackHandler(selecting) { vm.clearSelection() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск дубликатов", maxLines = 1) },
                subtitle = { Text(state.root, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = vm::rescan, enabled = !state.running, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Refresh, "Искать заново")
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
            state.running -> DupPhaseUi.Scanning
            state.error != null -> DupPhaseUi.Error
            else -> DupPhaseUi.Ready
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
            label = "dupPhase",
        ) { p ->
            when (p) {
                DupPhaseUi.Scanning -> {
                    val pr = state.progress
                    ProgressPane(
                        headline = if (pr.phase == DupPhase.Scan) Format.files(pr.files)
                        else "${((pr.fraction ?: 0f) * 100).toInt()}%",
                        caption = when (pr.phase) {
                            DupPhase.Scan -> "Сканирование… ${Format.size(pr.bytes, settings.sizeUnits)}"
                            DupPhase.Compare -> "Сравнение начала файлов: ${pr.done} из ${pr.total}"
                            DupPhase.Verify -> "Проверка содержимого: ${Format.size(pr.done, settings.sizeUnits)} из ${Format.size(pr.total, settings.sizeUnits)}"
                        },
                        fraction = pr.fraction,
                        stats = emptyList(),
                        current = pr.current.let { relativeTo(it, state.root).ifEmpty { if (it.isEmpty()) "" else "/" } },
                        onCancel = onBack,
                        steps = DupPhase.entries.map { it.title },
                        step = pr.phase.ordinal,
                    )
                }
                DupPhaseUi.Error -> ComparePane(
                    polygon = MaterialShapes.Clover4Leaf,
                    icon = Icons.Rounded.ErrorOutline,
                    title = "Не удалось выполнить поиск",
                    text = state.error,
                    error = true,
                    action = "Повторить" to vm::rescan,
                )
                DupPhaseUi.Ready -> Box(Modifier.fillMaxSize()) {
                    DupList(
                        state = state,
                        settings = settings,
                        bottomExtra = if (selecting) 88.dp else 0.dp,
                        onMinSize = vm::setMinSize,
                        onAuto = vm::autoSelect,
                        onDeleteOld = { confirmNewest = true },
                        onReset = vm::clearSelection,
                        onToggle = vm::toggle,
                        onMenu = { menuFile = it },
                    )
                    AnimatedVisibility(
                        visible = selecting,
                        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 16.dp),
                    ) {
                        DeleteToolbar(
                            count = state.selected.size,
                            bytes = state.selectedBytes,
                            settings = settings,
                            onClear = vm::clearSelection,
                            onDelete = { confirm = true },
                        )
                    }
                }
            }
        }
    }

    menuFile?.let { f ->
        FileSheet(
            file = f,
            root = state.root,
            selected = f.path in state.selected,
            onReveal = { onReveal(f.path) },
            onOpen = { FileOpener.openExternal(context, File(f.path)) },
            onToggle = { vm.toggle(f.path) },
            onDismiss = { menuFile = null },
        )
    }

    if (confirm) {
        val n = state.selected.size
        ConfirmDialog(
            title = "Удалить ${Format.files(n)}?",
            text = buildString {
                append("Освободится ${Format.size(state.selectedBytes, settings.sizeUnits)}. ")
                if (state.fullySelectedGroups > 0) {
                    append("Внимание: в ${state.fullySelectedGroups} ")
                    append(Format.plural(state.fullySelectedGroups, "группе", "группах", "группах"))
                    append(" выбраны все копии — эти файлы не останутся нигде. ")
                }
                append(if (settings.useTrash) "Файлы будут перемещены в корзину." else "Действие нельзя отменить.")
            },
            icon = if (state.fullySelectedGroups > 0) Icons.Rounded.Warning else Icons.Rounded.Delete,
            confirmLabel = "Удалить",
            onConfirm = vm::deleteSelected,
            onDismiss = { confirm = false },
            destructive = true,
        )
    }

    if (confirmNewest) {
        val victims = copiesExceptNewest(state.groups)
        val n = victims.size
        val bytes = victims.sumOf { it.size }
        ConfirmDialog(
            title = "Удалить ${Format.files(n)}?",
            text = buildString {
                append("В каждой группе останется самый новый файл, он не удаляется. ")
                append("Будет удалено ${Format.files(n)}")
                if (n > 0) append(" — освободится ${Format.size(bytes, settings.sizeUnits)}")
                append(". ")
                append(if (settings.useTrash) "Файлы будут перемещены в корзину." else "Действие нельзя отменить.")
            },
            icon = Icons.Rounded.Delete,
            confirmLabel = "Удалить",
            onConfirm = vm::deleteKeepingNewest,
            onDismiss = { confirmNewest = false },
            destructive = true,
        )
    }
}

// ------------------------------------------------------------------ Список

@Composable
private fun DupList(
    state: DupState,
    settings: AppSettings,
    bottomExtra: androidx.compose.ui.unit.Dp,
    onMinSize: (MinSize) -> Unit,
    onAuto: (AutoSelect) -> Unit,
    onDeleteOld: () -> Unit,
    onReset: () -> Unit,
    onToggle: (String) -> Unit,
    onMenu: (DupFile) -> Unit,
) {
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottom + 24.dp + bottomExtra),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero", contentType = "hero") {
            HeroCard(state, settings, onMinSize, onAuto, onDeleteOld, onReset)
        }
        if (state.groups.isEmpty()) {
            item(key = "empty", contentType = "empty") {
                ComparePane(
                    polygon = MaterialShapes.Sunny,
                    icon = Icons.Rounded.Verified,
                    title = "Дубликатов не найдено",
                    text = if (state.minSize == MinSize.Any) "Все файлы уникальны"
                    else "Файлов от ${state.minSize.label} с одинаковым содержимым нет",
                    modifier = Modifier.height(360.dp),
                )
            }
        } else {
            item(key = "header", contentType = "header") {
                CompareSectionHeader(
                    "Группы",
                    "${state.groups.size} • ${Format.files(state.groups.sumOf { it.files.size })}",
                )
            }
            for (g in state.groups) groupItems(g, state.root, state.selected, settings, onToggle, onMenu)
        }
    }
}

private fun LazyListScope.groupItems(
    group: DupGroup,
    root: String,
    selected: Set<String>,
    settings: AppSettings,
    onToggle: (String) -> Unit,
    onMenu: (DupFile) -> Unit,
) {
    val count = group.files.size + 1
    // Отметка «новее» — только если самый новый файл в группе один.
    val newestTime = group.files.maxOf { it.lastModified }
    val newestUnique = group.files.count { it.lastModified == newestTime } == 1
    item(key = "g:" + group.key, contentType = "groupHeader") {
        GroupHeader(group, settings, count, Modifier.animateItem())
    }
    itemsIndexed(group.files, key = { _, f -> "f:" + group.key + ":" + f.path }, contentType = { _, _ -> "file" }) { i, f ->
        FileRow(
            file = f,
            root = root,
            checked = f.path in selected,
            settings = settings,
            index = i + 1,
            count = count,
            newest = newestUnique && f.lastModified == newestTime,
            onToggle = { onToggle(f.path) },
            onClick = { onMenu(f) },
            modifier = Modifier.animateItem(),
        )
    }
    item(key = "gap:" + group.key, contentType = "gap") { Spacer(Modifier.height(10.dp)) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(
    state: DupState,
    settings: AppSettings,
    onMinSize: (MinSize) -> Unit,
    onAuto: (AutoSelect) -> Unit,
    onDeleteOld: () -> Unit,
    onReset: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeIcon(
                    MaterialShapes.Cookie9Sided,
                    Icons.Rounded.FileCopy,
                    container = scheme.primaryContainer,
                    content = scheme.onPrimaryContainer,
                    size = 64.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    val n = state.groups.size
                    Text(
                        "Найдено $n ${Format.plural(n, "группа", "группы", "групп")}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface,
                    )
                    Text(
                        if (n == 0) "Освобождать нечего" else "можно освободить ${Format.size(state.wasted, settings.sizeUnits)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                buildList {
                    add("Просмотрено ${Format.files(state.scannedFiles)}")
                    if (state.durationMs > 0) add("за ${secondsText(state.durationMs)}")
                    if (state.unreadable > 0) add("недоступно папок: ${state.unreadable}")
                }.joinToString("  •  "),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text("Минимальный размер", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            ConnectedChoice(
                options = MinSize.entries,
                selected = state.minSize,
                onSelect = onMinSize,
                label = { it.label },
            )
            if (state.groups.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Автовыбор", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AutoButton("Оставить самые новые", Icons.Rounded.Update) { onAuto(AutoSelect.Newest) }
                    AutoButton("Оставить самые старые", Icons.Rounded.History) { onAuto(AutoSelect.Oldest) }
                    AutoButton("Оставить в первой папке", Icons.Rounded.FolderOpen) { onAuto(AutoSelect.FirstFolder) }
                    if (state.selected.isNotEmpty()) {
                        OutlinedButton(onClick = onReset, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Deselect, null, Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Сбросить")
                        }
                    }
                }
                val oldCopies = copiesExceptNewest(state.groups)
                if (oldCopies.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onDeleteOld,
                        modifier = Modifier.fillMaxWidth(),
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.error,
                            contentColor = scheme.onError,
                        ),
                    ) {
                        Icon(Icons.Rounded.Delete, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Удалить старые копии")
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(text, maxLines = 1)
    }
}

@Composable
private fun GroupHeader(group: DupGroup, settings: AppSettings, count: Int, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val first = group.files.first()
    val item = remember(first) {
        FileItem(path = first.path, name = first.name, isDirectory = false, size = first.size, lastModified = first.lastModified)
    }
    val sameName = remember(group) { group.files.all { it.name == first.name } }
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(0, count),
        colors = ListItemDefaults.segmentedColors(containerColor = scheme.surfaceContainerHigh),
        leadingContent = { FileIcon(item = item, size = 52.dp) },
        supportingContent = {
            Text(
                "${Format.size(group.size, settings.sizeUnits)} × ${group.files.size} • лишнее ${Format.size(group.wasted, settings.sizeUnits)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${group.files.size}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimary,
                )
            }
        },
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(
            if (sameName) first.name else "${first.name} и др.",
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

@Composable
private fun FileRow(
    file: DupFile,
    root: String,
    checked: Boolean,
    settings: AppSettings,
    index: Int,
    count: Int,
    newest: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        selected = checked,
        onClick = onClick,
        onLongClick = onToggle,
        onLongClickLabel = if (checked) "Снять отметку" else "Отметить для удаления",
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(
            selectedContainerColor = scheme.errorContainer,
            selectedContentColor = scheme.onErrorContainer,
            selectedSupportingContentColor = scheme.onErrorContainer.copy(alpha = 0.8f),
            selectedLeadingContentColor = scheme.onErrorContainer,
        ),
        leadingContent = {
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(checkedColor = scheme.error, checkmarkColor = scheme.onError),
            )
        },
        supportingContent = {
            Text(
                buildString {
                    append(relativeParentLabel(file.path, root))
                    append("  •  ")
                    append(Format.date(file.lastModified, settings.dateFormat))
                    if (newest) append("  •  новее")
                },
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        },
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(file.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
    }
}

/** «Папка/подпапка/» относительно корня поиска. */
internal fun relativeParentLabel(path: String, root: String): String {
    val parent = path.substringBeforeLast('/', "")
    val rel = relativeTo(parent, root)
    val rootName = when {
        root == "/" -> ""
        root == android.os.Environment.getExternalStorageDirectory().path -> "Память"
        else -> root.substringAfterLast('/')
    }
    return if (rel.isEmpty()) "$rootName/" else "$rootName/$rel"
}

@Composable
private fun DeleteToolbar(count: Int, bytes: Long, settings: AppSettings, onClear: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        leadingContent = { PressIconButton(Icons.Rounded.Close, "Снять выделение", onClear) },
    ) {
        Column(
            Modifier
                .align(Alignment.CenterVertically)
                .padding(horizontal = 6.dp)
                .widthIn(min = 64.dp),
        ) {
            Text("$count", style = MaterialTheme.typography.titleMedium, color = scheme.onPrimaryContainer, maxLines = 1)
            Text(
                Format.size(bytes, settings.sizeUnits),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(6.dp))
        Button(
            onClick = onDelete,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.buttonColors(containerColor = scheme.error, contentColor = scheme.onError),
        ) {
            Icon(Icons.Rounded.Delete, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Удалить выбранные")
        }
    }
}

@Composable
private fun FileSheet(
    file: DupFile,
    root: String,
    selected: Boolean,
    onReveal: () -> Unit,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    val item = remember(file) {
        FileItem(path = file.path, name = file.name, isDirectory = false, size = file.size, lastModified = file.lastModified)
    }
    AppBottomSheet(onDismiss) {
        Row(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIcon(item = item, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    "${Format.size(file.size, s.sizeUnits)}  •  ${Format.date(file.lastModified, s.dateFormat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    relativeParentLabel(file.path, root),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        ActionGroup(
            listOf(
                SheetAction("Показать в папке", Icons.Rounded.FolderOpen, supporting = file.parent) { onReveal() },
                SheetAction("Открыть", Icons.AutoMirrored.Rounded.OpenInNew, supporting = "В другом приложении") { onOpen() },
                SheetAction(
                    if (selected) "Снять отметку" else "Отметить для удаления",
                    if (selected) Icons.Rounded.Deselect else Icons.Rounded.Delete,
                ) { onToggle() },
            ),
            onDismiss,
        )
    }
}
