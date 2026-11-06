package client.wexmanager.feature.tools

import android.app.Application
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.ops.TaskEvent
import client.wexmanager.core.ops.TaskKind
import client.wexmanager.core.ops.TaskManager
import client.wexmanager.core.ops.TaskStatus
import client.wexmanager.core.ops.TrashManager
import client.wexmanager.core.ops.TrashManager.TrashEntry
import client.wexmanager.core.settings.SizeUnits
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.ActionGroup
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.feature.browser.SheetAction
import client.wexmanager.ui.components.ConfirmDialog
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.PressIconButton
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

// ------------------------------------------------------------------ ViewModel

@Immutable
data class TrashUiState(
    val entries: List<TrashEntry> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val selection: Set<String> = emptySet(),
    /** Размеры папок (считаются в фоне); для файлов размер есть в самой записи. */
    val dirSizes: Map<String, Long> = emptyMap(),
    val sizing: Boolean = false,
    /** Идёт наша задача восстановления/удаления. */
    val busy: Boolean = false,
    /** Подстрока имени без учёта регистра. Пустая строка — весь список. */
    val filter: String = "",
) {
    fun sizeOf(e: TrashEntry): Long = if (e.isDirectory) dirSizes[e.id] ?: -1L else e.size
    val totalSize: Long get() = entries.sumOf { sizeOf(it).coerceAtLeast(0L) }
    val visibleEntries: List<TrashEntry> get() = entries.matching(filter)
    /** Только видимые строки: скрытые фильтром не входят в восстановление и удаление. */
    val selectedEntries: List<TrashEntry> get() = visibleEntries.filter { it.id in selection }
}

private fun List<TrashEntry>.matching(filter: String): List<TrashEntry> =
    if (filter.isEmpty()) this else filter { it.name.contains(filter, ignoreCase = true) }

class TrashMessage(val text: String, val revealPath: String? = null, val isError: Boolean = false)

class TrashViewModel(app: Application) : AndroidViewModel(app) {
    private val trash = app.appContainer.trash
    private val tasks = app.appContainer.taskManager

    private val _state = MutableStateFlow(TrashUiState())
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    private val _messages = Channel<TrashMessage>(Channel.BUFFERED)
    val messages: Flow<TrashMessage> = _messages.receiveAsFlow()

    // Поля ниже меняются только на главном потоке (viewModelScope).
    private var loaded: List<TrashEntry> = emptyList()
    /** id задачи → id записей, которые она обрабатывает (скрыты из списка до завершения). */
    private val pending = HashMap<Long, Set<String>>()
    private val sizeCache = HashMap<String, Long>()
    private var loadJob: Job? = null
    private var sizeJob: Job? = null

    init {
        viewModelScope.launch {
            tasks.events.collect { e ->
                if (e !is TaskEvent.Finished || e.task.kind !in WATCHED_KINDS) return@collect
                pending.remove(e.task.id)?.let { ids -> report(e, ids.size) }
                load()
            }
        }
        load()
    }

    fun refresh() = load(manual = true)

    private fun load(manual: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (manual) _state.update { it.copy(refreshing = true) }
            val list = try {
                // id — ключ строк LazyColumn и выделения: дубли (скопированные .json, том, смонтированный
                // дважды) уронили бы список, поэтому оставляем по одной записи на id.
                withContext(Dispatchers.IO) { trash.entries().distinctBy { it.id } }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            loaded = list
            val ids = list.mapTo(HashSet()) { it.id }
            sizeCache.keys.retainAll(ids)
            publish { it.copy(loading = false, refreshing = false) }
            measureFolders(list)
        }
    }

    private fun publish(transform: (TrashUiState) -> TrashUiState = { it }) {
        val hidden = pending.values.flatMapTo(HashSet()) { it }
        val visible = if (hidden.isEmpty()) loaded else loaded.filter { it.id !in hidden }
        val sizes = HashMap(sizeCache)
        _state.update { s ->
            val shownIds = visible.matching(s.filter).mapTo(HashSet()) { it.id }
            transform(
                s.copy(
                    entries = visible,
                    selection = if (s.selection.all { it in shownIds }) s.selection else s.selection.filterTo(HashSet()) { it in shownIds },
                    dirSizes = sizes,
                    busy = pending.isNotEmpty(),
                ),
            )
        }
    }

    /** Размеры папок считаются в фоне, с отменой при перезагрузке. */
    private fun measureFolders(list: List<TrashEntry>) {
        sizeJob?.cancel()
        val dirs = list.filter { it.isDirectory && it.id !in sizeCache }
        if (dirs.isEmpty()) {
            _state.update { it.copy(sizing = false) }
            return
        }
        _state.update { it.copy(sizing = true) }
        sizeJob = viewModelScope.launch {
            var mark = SystemClock.uptimeMillis()
            for (d in dirs) {
                sizeCache[d.id] = withContext(Dispatchers.IO) { folderSize(d.storedFile) }
                if (SystemClock.uptimeMillis() - mark > 300) {
                    publish()
                    mark = SystemClock.uptimeMillis()
                }
            }
            publish { it.copy(sizing = false) }
        }
    }

    fun toggle(id: String) = _state.update { s ->
        if (s.visibleEntries.none { it.id == id }) s
        else s.copy(selection = if (id in s.selection) s.selection - id else s.selection + id)
    }

    fun selectAll() = _state.update { s -> s.copy(selection = s.visibleEntries.mapTo(HashSet()) { it.id }) }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun setFilter(value: String) = _state.update { s ->
        val ids = s.entries.matching(value).mapTo(HashSet()) { it.id }
        s.copy(
            filter = value,
            selection = if (s.selection.all { it in ids }) s.selection else s.selection.filterTo(HashSet()) { it in ids },
        )
    }

    fun restore(entries: List<TrashEntry>) = start(entries) { tasks.restoreFromTrash(it) }

    fun purge(entries: List<TrashEntry>) = start(entries) { tasks.emptyTrash(it) }

    fun purgeAll() = purge(_state.value.entries)

    private inline fun start(entries: List<TrashEntry>, launch: (List<TrashEntry>) -> Long) {
        if (entries.isEmpty()) return
        // События задач приходят на главный поток, поэтому id успеет попасть в pending раньше Finished.
        val id = launch(entries)
        pending[id] = entries.mapTo(HashSet()) { it.id }
        _state.update { it.copy(selection = emptySet()) }
        publish()
    }

    private fun report(e: TaskEvent.Finished, count: Int) {
        val t = e.task
        val msg = when (t.status) {
            TaskStatus.Done -> if (t.kind == TaskKind.Restore) {
                TrashMessage("Восстановлено: ${Format.items(count)}", e.touchedDirs.firstOrNull { it.isNotBlank() })
            } else {
                TrashMessage("Удалено навсегда: ${Format.items(count)}")
            }
            TaskStatus.Cancelled -> TrashMessage("Операция отменена")
            else -> TrashMessage(
                t.failedItems.firstOrNull()?.let { "Ошибка — $it" } ?: t.error ?: "Не удалось выполнить операцию",
                isError = true,
            )
        }
        _messages.trySend(msg)
    }

    private companion object {
        val WATCHED_KINDS = setOf(TaskKind.Restore, TaskKind.Delete, TaskKind.Trash)

        suspend fun folderSize(root: File): Long {
            var total = 0L
            val stack = ArrayDeque<File>().apply { add(root) }
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val f = stack.removeLast()
                if (f.isDirectory && !TaskManager.isSymlink(f)) f.listFiles()?.let { stack.addAll(it) } else total += f.length()
            }
            return total
        }
    }
}

// ------------------------------------------------------------------ Группировка по дням

@Immutable
private data class DayGroup(val day: Long, val title: String, val entries: List<TrashEntry>)

private fun groupByDay(entries: List<TrashEntry>, now: Long): List<DayGroup> {
    val cal = Calendar.getInstance()
    fun startOfDay(t: Long): Long {
        cal.timeInMillis = t
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    val today = startOfDay(now)
    cal.timeInMillis = today
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = cal.timeInMillis
    cal.timeInMillis = now
    val year = cal.get(Calendar.YEAR)
    val weekday = SimpleDateFormat("EEEE, d MMMM", Format.RU)
    val full = SimpleDateFormat("d MMMM yyyy", Format.RU)
    return entries.groupBy { startOfDay(it.deletedAt) }.map { (day, list) ->
        cal.timeInMillis = day
        val title = when {
            day >= today -> "Сегодня"
            day == yesterday -> "Вчера"
            cal.get(Calendar.YEAR) == year -> weekday.format(Date(day))
            else -> full.format(Date(day))
        }.replaceFirstChar { it.titlecase(Format.RU) }
        DayGroup(day, title, list)
    }
}

private fun TrashEntry.toFileItem(): FileItem = FileItem(
    path = "$trashRoot/files/$id",
    name = name,
    isDirectory = isDirectory,
    size = size,
    lastModified = deletedAt,
)

private val TrashEntry.originalParent: String get() = File(originalPath).parent ?: "/"

private fun sizeLabel(state: TrashUiState, e: TrashEntry, units: SizeUnits): String {
    val size = state.sizeOf(e)
    return when {
        size >= 0 -> Format.size(size, units)
        e.isDirectory -> "Папка"
        else -> "—"
    }
}

/** Ближайшая существующая папка — исходная могла быть уже удалена. */
private fun nearestExistingDir(start: File?): String {
    var f = start
    while (f != null && !f.isDirectory) f = f.parentFile
    return f?.path ?: "/"
}

private sealed interface TrashConfirm {
    data class Purge(val entries: List<TrashEntry>) : TrashConfirm
    data object EmptyAll : TrashConfirm
}

// ------------------------------------------------------------------ Экран

@Composable
fun TrashScreen(onBack: () -> Unit, onReveal: (String) -> Unit) {
    val vm: TrashViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val settings = LocalAppSettings.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val reveal by rememberUpdatedState(onReveal)

    var sheet by remember { mutableStateOf<TrashEntry?>(null) }
    var confirm by remember { mutableStateOf<TrashConfirm?>(null) }
    val selecting = state.selection.isNotEmpty()

    BackHandler(enabled = selecting) { vm.clearSelection() }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            val result = snackbar.showSnackbar(
                message = m.text,
                actionLabel = if (m.revealPath != null) "Показать" else null,
                withDismissAction = m.isError,
                duration = if (m.revealPath != null || m.isError) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) m.revealPath?.let { reveal(it) }
        }
    }

    val revealOriginal: (TrashEntry) -> Unit = { e ->
        scope.launch {
            val path = withContext(Dispatchers.IO) { nearestExistingDir(File(e.originalPath).parentFile) }
            reveal(path)
        }
    }
    val longPress: (TrashEntry) -> Unit = { e ->
        if (settings.haptics) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.toggle(e.id)
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val barColor by animateColorAsState(
        if (selecting) scheme.secondaryContainer else scheme.surface,
        motion.defaultEffectsSpec(),
        label = "barColor",
    )
    val barScrolledColor by animateColorAsState(
        if (selecting) scheme.secondaryContainer else scheme.surfaceContainer,
        motion.defaultEffectsSpec(),
        label = "barScrolledColor",
    )

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    AnimatedContent(
                        targetState = selecting,
                        transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                        label = "trashTitle",
                    ) { sel ->
                        Text(if (sel) "Выбрано: ${state.selection.size}" else "Корзина", maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (selecting) vm.clearSelection() else onBack() },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        AnimatedContent(selecting, label = "navIcon") { sel ->
                            if (sel) Icon(Icons.Rounded.Close, "Снять выделение")
                            else Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                        }
                    }
                },
                actions = {
                    if (selecting) {
                        val shown = state.visibleEntries.size
                        val all = shown > 0 && state.selection.size == shown
                        IconButton(
                            onClick = { if (all) vm.clearSelection() else vm.selectAll() },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(if (all) Icons.Rounded.Deselect else Icons.Rounded.SelectAll, if (all) "Снять выделение" else "Выделить всё")
                        }
                    } else if (state.entries.isNotEmpty()) {
                        IconButton(
                            onClick = { vm.selectAll() },
                            enabled = state.visibleEntries.isNotEmpty(),
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(Icons.Rounded.SelectAll, "Выделить всё")
                        }
                        IconButton(onClick = { confirm = TrashConfirm.EmptyAll }, enabled = !state.busy, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.DeleteSweep, "Очистить корзину")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = barColor, scrolledContainerColor = barScrolledColor),
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = {
            SnackbarHost(snackbar, Modifier.padding(bottom = if (selecting) 88.dp else 0.dp))
        },
        containerColor = scheme.surface,
    ) { padding ->
        val mode = when {
            state.loading && state.entries.isEmpty() -> 0
            state.entries.isEmpty() -> 1
            else -> 2
        }
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "trashContent",
            ) { m ->
                when (m) {
                    0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator(Modifier.size(64.dp))
                    }
                    1 -> ToolsEmptyState(
                        polygon = MaterialShapes.SoftBurst,
                        icon = Icons.Rounded.DeleteOutline,
                        title = "Корзина пуста",
                        text = "Удалённые файлы попадают сюда, и их можно восстановить, пока корзина не очищена",
                        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                    )
                    else -> TrashList(
                        state = state,
                        units = settings.sizeUnits,
                        bottomPadding = padding.calculateBottomPadding() + if (selecting) 104.dp else 24.dp,
                        onRefresh = vm::refresh,
                        onFilterChange = vm::setFilter,
                        onClick = { e -> if (selecting) vm.toggle(e.id) else sheet = e },
                        onToggle = { e -> vm.toggle(e.id) },
                        onLongClick = longPress,
                        onEmptyTrash = { confirm = TrashConfirm.EmptyAll },
                    )
                }
            }
            AnimatedVisibility(
                visible = state.busy,
                enter = fadeIn(motion.fastEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp))
            }
            AnimatedVisibility(
                visible = selecting,
                enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
            ) {
                SelectionToolbar(
                    count = state.selection.size,
                    onRestore = { vm.restore(state.selectedEntries) },
                    onPurge = { confirm = TrashConfirm.Purge(state.selectedEntries) },
                )
            }
        }
    }

    sheet?.let { e ->
        TrashEntrySheet(
            entry = e,
            sizeText = sizeLabel(state, e, settings.sizeUnits),
            onDismiss = { sheet = null },
            onRestore = { vm.restore(listOf(e)) },
            onPurge = { confirm = TrashConfirm.Purge(listOf(e)) },
            onRevealOriginal = { revealOriginal(e) },
        )
    }

    when (val c = confirm) {
        is TrashConfirm.Purge -> ConfirmDialog(
            title = "Удалить навсегда?",
            text = if (c.entries.size == 1) {
                "Элемент «${c.entries[0].name}» будет удалён без возможности восстановления."
            } else {
                "Выбранные элементы (${Format.items(c.entries.size)}) будут удалены без возможности восстановления."
            },
            icon = Icons.Rounded.DeleteForever,
            confirmLabel = "Удалить",
            onConfirm = { vm.purge(c.entries) },
            onDismiss = { confirm = null },
            destructive = true,
        )
        TrashConfirm.EmptyAll -> ConfirmDialog(
            title = "Очистить корзину?",
            text = "Все элементы (${Format.items(state.entries.size)}, ${Format.size(state.totalSize, settings.sizeUnits)}) " +
                "будут удалены навсегда. Это действие нельзя отменить.",
            icon = Icons.Rounded.DeleteSweep,
            confirmLabel = "Очистить",
            onConfirm = { vm.purgeAll() },
            onDismiss = { confirm = null },
            destructive = true,
        )
        null -> Unit
    }
}

@Composable
private fun TrashList(
    state: TrashUiState,
    units: SizeUnits,
    bottomPadding: Dp,
    onRefresh: () -> Unit,
    onFilterChange: (String) -> Unit,
    onClick: (TrashEntry) -> Unit,
    onToggle: (TrashEntry) -> Unit,
    onLongClick: (TrashEntry) -> Unit,
    onEmptyTrash: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val shown = state.visibleEntries
    val groups = remember(shown) { groupByDay(shown, System.currentTimeMillis()) }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    Column(Modifier.fillMaxSize()) {
        TrashFilterField(
            value = state.filter,
            onValueChange = onFilterChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            state = pullState,
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "summary", contentType = "summary") {
                    TrashSummary(
                        count = state.entries.size,
                        size = state.totalSize,
                        sizing = state.sizing,
                        busy = state.busy,
                        units = units,
                        onEmptyTrash = onEmptyTrash,
                        modifier = Modifier.animateItem(),
                    )
                }
                groups.forEach { group ->
                    stickyHeader(key = "day:${group.day}", contentType = "header") {
                        DayHeader(group.title, group.entries.size)
                    }
                    itemsIndexed(group.entries, key = { _, e -> e.id }, contentType = { _, _ -> "entry" }) { i, e ->
                        TrashRow(
                            entry = e,
                            sizeText = sizeLabel(state, e, units),
                            selected = e.id in state.selection,
                            shapes = ListItemDefaults.segmentedShapes(i, group.entries.size),
                            onClick = { onClick(e) },
                            onToggle = { onToggle(e) },
                            onLongClick = { onLongClick(e) },
                            modifier = Modifier.animateItem(
                                fadeInSpec = motion.defaultEffectsSpec(),
                                placementSpec = motion.defaultSpatialSpec(),
                                fadeOutSpec = motion.fastEffectsSpec(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashFilterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("Фильтр") },
        leadingIcon = { Icon(Icons.Rounded.FilterList, null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, "Очистить")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TrashSummary(
    count: Int,
    size: Long,
    sizing: Boolean,
    busy: Boolean,
    units: SizeUnits,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    polygon = MaterialShapes.Cookie9Sided,
                    icon = Icons.Rounded.Delete,
                    container = scheme.primary,
                    content = scheme.onPrimary,
                    size = 56.dp,
                    iconSize = 28.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        Format.items(count),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        (if (sizing) "≈ " else "") + Format.size(size, units),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Rounded.Info, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(top = 1.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Файлы переносятся в скрытую папку ${TrashManager.TRASH_DIR} в корне своего тома (память устройства, SD-карта) " +
                        "и продолжают занимать место, пока корзина не очищена.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(
                onClick = onEmptyTrash,
                enabled = !busy && count > 0,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = scheme.errorContainer,
                    contentColor = scheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Очистить корзину")
            }
        }
    }
}

@Composable
private fun DayHeader(title: String, count: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.surface)
            .padding(start = 28.dp, end = 28.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(Format.items(count), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun TrashRow(
    entry: TrashEntry,
    sizeText: String,
    selected: Boolean,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = remember(entry) { entry.toFileItem() }
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "Выделить",
        shapes = shapes,
        modifier = modifier.padding(horizontal = 16.dp),
        leadingContent = {
            FileIcon(
                item = item,
                selected = selected,
                size = 44.dp,
                showThumbnail = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(30))
                    .combinedClickable(onClick = onToggle, onLongClick = onLongClick),
            )
        },
        trailingContent = {
            Text(sizeText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        },
        supportingContent = {
            Column {
                Text(
                    entry.originalParent,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.StartEllipsis,
                )
                Text(
                    "Удалено ${Format.relative(entry.deletedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        },
    ) {
        Text(entry.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
    }
}

@Composable
private fun SelectionToolbar(count: Int, onRestore: () -> Unit, onPurge: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
    ) {
        Button(onClick = onRestore, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Restore, null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            AnimatedContent(
                targetState = count,
                transitionSpec = {
                    (scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f) + fadeIn())
                        .togetherWith(scaleOut(targetScale = 0.8f) + fadeOut())
                },
                label = "restoreCount",
            ) { n -> Text("Восстановить ($n)") }
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onPurge,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.error, contentColor = scheme.onError),
        ) {
            Icon(Icons.Rounded.DeleteForever, "Удалить навсегда")
        }
    }
}

@Composable
private fun TrashEntrySheet(
    entry: TrashEntry,
    sizeText: String,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
    onRevealOriginal: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val item = remember(entry) { entry.toFileItem() }
    AppBottomSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FileIcon(item, size = 56.dp, showThumbnail = false)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    "$sizeText  •  удалено ${Format.relative(entry.deletedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    entry.originalPath,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.outline,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ActionGroup(
            listOf(
                SheetAction("Восстановить", Icons.Rounded.Restore, "В исходное расположение", onRestore),
                SheetAction("Показать исходную папку", Icons.Rounded.FolderOpen, entry.originalParent, onRevealOriginal),
            ),
            onDismiss,
        )
        ActionGroup(
            listOf(SheetAction("Удалить навсегда", Icons.Rounded.DeleteForever, "Без возможности восстановления", onPurge)),
            onDismiss,
        )
    }
}
