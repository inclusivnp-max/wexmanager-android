package client.wexmanager.feature.compare

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.ops.ConflictChoice
import client.wexmanager.core.ops.ConflictDecision
import client.wexmanager.core.ops.TaskEvent
import client.wexmanager.core.ops.TaskStatus
import client.wexmanager.core.util.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class CopyDirection { ToLeft, ToRight }

/** План копирования: что и куда, сколько новых и сколько заменяемых файлов. */
@Immutable
data class CopyPlan(
    val direction: CopyDirection,
    val entries: List<CmpEntry>,
    val newCount: Int,
    val replaceCount: Int,
    val bytes: Long,
    /** Пропущено элементов, которые нельзя скопировать в этом направлении. */
    val skipped: Int = 0,
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

@Immutable
data class FolderCompareState(
    val left: String,
    val right: String,
    val recursive: Boolean = true,
    val byContent: Boolean = false,
    /** Сырой фильтр расширений. Пусто — все файлы. */
    val extensions: String = "",
    /** Набор, с которым посчитаны [entries], счётчики и синхронизация. */
    val appliedExtensions: Set<String> = emptySet(),
    val running: Boolean = true,
    val progress: CmpProgress = CmpProgress(),
    val entries: List<CmpEntry> = emptyList(),
    val counts: Map<CmpStatus, Int> = emptyMap(),
    val filters: Set<CmpStatus> = setOf(CmpStatus.OnlyLeft, CmpStatus.OnlyRight, CmpStatus.Different),
    val visible: List<CmpEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val unreadable: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null,
    /** Активные задачи копирования, запущенные с этого экрана, и их общий прогресс. */
    val copying: Int = 0,
    val copyProgress: Float = 0f,
)

class FolderCompareViewModel(app: Application, left: String, right: String) : AndroidViewModel(app) {
    private val c = app.appContainer

    private val _state = MutableStateFlow(FolderCompareState(normalizeRoot(left), normalizeRoot(right)))
    val state: StateFlow<FolderCompareState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var scanJob: Job? = null
    private var extJob: Job? = null
    private var scanGen = 0
    /** Набор, который сейчас считается. Повторный ввод того же фильтра скан не перезапускает. */
    private var scanning: Set<String> = emptySet()
    /** Наши задачи копирования. */
    private val pending = HashSet<Long>()
    /** Пути назначения, замену которых пользователь уже подтвердил (конфликт решается автоматически). */
    private val approvedReplace = HashSet<String>()
    private var failures = 0

    init {
        rescan()
        viewModelScope.launch {
            c.taskManager.events.collect { e ->
                if (e !is TaskEvent.Finished || !pending.remove(e.task.id)) return@collect
                if (e.task.status == TaskStatus.Failed) failures += e.task.failedItems.size.coerceAtLeast(1)
                if (pending.isEmpty()) {
                    approvedReplace.clear()
                    if (failures > 0) _messages.trySend("Скопировано с ошибками: $failures")
                    failures = 0
                    rescan(keepSelection = false)
                }
            }
        }
        viewModelScope.launch {
            c.taskManager.tasks.collect { tasks ->
                if (pending.isEmpty()) {
                    if (_state.value.copying != 0) _state.update { it.copy(copying = 0, copyProgress = 0f) }
                    return@collect
                }
                val ours = tasks.filter { it.id in pending && it.isActive }
                // Замена файла, которую пользователь уже подтвердил в нашем диалоге, — без повторного вопроса.
                for (t in ours) {
                    val conflict = t.conflict ?: continue
                    if (!conflict.source.isDirectory && !conflict.target.isDirectory && conflict.target.path in approvedReplace) {
                        conflict.resolve(ConflictDecision(ConflictChoice.Replace, applyToAll = false))
                    }
                }
                val total = ours.sumOf { it.totalBytes }
                val done = ours.sumOf { it.processedBytes }
                _state.update {
                    it.copy(
                        copying = ours.size,
                        copyProgress = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f,
                    )
                }
            }
        }
    }

    fun rescan(keepSelection: Boolean = false) {
        scanJob?.cancel()
        val generation = ++scanGen
        val s = _state.value
        if (s.left == s.right) {
            _state.update { it.copy(running = false, error = "Выбрана одна и та же папка") }
            return
        }
        val extensions = FolderComparer.parseExtensions(s.extensions)
        scanning = extensions
        _state.update {
            it.copy(
                running = true,
                progress = CmpProgress(),
                error = null,
                selected = if (keepSelection) it.selected else emptySet(),
            )
        }
        scanJob = viewModelScope.launch {
            try {
                for (p in listOf(s.left, s.right)) {
                    if (!withContext(Dispatchers.IO) { File(p).isDirectory }) {
                        if (generation != scanGen) return@launch
                        _state.update { it.copy(running = false, error = "Папка недоступна: $p") }
                        return@launch
                    }
                }
                val result = FolderComparer.compare(s.left, s.right, s.recursive, s.byContent, extensions) { p ->
                    if (generation != scanGen) return@compare
                    _state.update { st -> if (st.running) st.copy(progress = p) else st }
                }
                if (generation != scanGen) return@launch
                val counts = result.entries.groupingBy { it.status }.eachCount()
                _state.update {
                    val visible = filter(result.entries, it.filters)
                    val keys = visible.mapTo(HashSet()) { e -> e.rel }
                    it.copy(
                        running = false,
                        entries = result.entries,
                        counts = counts,
                        visible = visible,
                        selected = it.selected.filterTo(HashSet()) { r -> r in keys },
                        unreadable = result.unreadable,
                        durationMs = result.durationMs,
                        appliedExtensions = extensions,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (generation != scanGen) return@launch
                _state.update { it.copy(running = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(running = false) }
    }

    fun setRecursive(on: Boolean) {
        _state.update { it.copy(recursive = on) }
        rescan()
    }

    fun setByContent(on: Boolean) {
        _state.update { it.copy(byContent = on) }
        rescan()
    }

    /** Фильтр расширений. Пустая строка — все файлы. Повтор того же набора сравнение не перезапускает. */
    fun setExtensions(raw: String) {
        if (raw == _state.value.extensions) return
        _state.update { it.copy(extensions = raw) }
        scheduleExtRescan()
    }

    /** Применить фильтр сразу, не дожидаясь паузы ввода. */
    fun commitExtensions() {
        extJob?.cancel()
        if (extensionsSettled(FolderComparer.parseExtensions(_state.value.extensions))) return
        rescan()
    }

    private fun scheduleExtRescan() {
        extJob?.cancel()
        val parsed = FolderComparer.parseExtensions(_state.value.extensions)
        if (extensionsSettled(parsed)) return
        extJob = viewModelScope.launch {
            delay(EXTENSION_DEBOUNCE_MS)
            val now = FolderComparer.parseExtensions(_state.value.extensions)
            if (extensionsSettled(now)) return@launch
            rescan()
        }
    }

    private fun extensionsSettled(parsed: Set<String>): Boolean =
        if (scanJob?.isActive == true) parsed == scanning else parsed == _state.value.appliedExtensions

    fun swap() {
        _state.update { it.copy(left = it.right, right = it.left, entries = emptyList(), visible = emptyList()) }
        rescan()
    }

    fun toggleFilter(status: CmpStatus) {
        _state.update {
            val f = if (status in it.filters) it.filters - status else it.filters + status
            val visible = filter(it.entries, f)
            val keys = visible.mapTo(HashSet()) { e -> e.rel }
            it.copy(filters = f, visible = visible, selected = it.selected.filterTo(HashSet()) { r -> r in keys })
        }
    }

    fun toggle(rel: String) = _state.update { it.copy(selected = if (rel in it.selected) it.selected - rel else it.selected + rel) }

    fun selectAllVisible() = _state.update { s -> s.copy(selected = s.visible.mapTo(HashSet()) { it.rel }) }

    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    private fun filter(entries: List<CmpEntry>, filters: Set<CmpStatus>) = entries.filter { it.status in filters }

    fun selectedEntries(): List<CmpEntry> {
        val s = _state.value
        return s.entries.filter { it.rel in s.selected }
    }

    /** Что можно скопировать из [entries] в направлении [dir]. */
    fun plan(dir: CopyDirection, entries: List<CmpEntry>): CopyPlan {
        val ok = entries.filter { e ->
            val src = if (dir == CopyDirection.ToRight) e.left else e.right
            src != null && e.status != CmpStatus.Same
        }
        var newCount = 0
        var replace = 0
        var bytes = 0L
        for (e in ok) {
            val src = (if (dir == CopyDirection.ToRight) e.left else e.right)!!
            val dst = if (dir == CopyDirection.ToRight) e.right else e.left
            if (src.isDir) newCount += src.files.coerceAtLeast(1) else if (dst == null) newCount++ else replace++
            bytes += src.size
        }
        return CopyPlan(dir, ok, newCount, replace, bytes, skipped = entries.size - ok.size)
    }

    /** «Синхронизировать →»: всё, чего нет справа, и всё, что слева новее. */
    fun syncPlan(): CopyPlan {
        val list = _state.value.entries.filter {
            it.status == CmpStatus.OnlyLeft ||
                (it.status == CmpStatus.Different && it.newer == Newer.Left && !it.typeMismatch)
        }
        return plan(CopyDirection.ToRight, list).copy(skipped = 0)
    }

    /**
     * Копирует элементы плана. `copy` кладёт элементы в папку назначения по имени,
     * поэтому группируем по относительной родительской папке и создаём её на другой стороне.
     */
    fun execute(plan: CopyPlan) {
        if (plan.isEmpty) return
        val s = _state.value
        val toRight = plan.direction == CopyDirection.ToRight
        val destRoot = if (toRight) s.right else s.left
        val groups = plan.entries.groupBy { it.parentRel }
        viewModelScope.launch {
            val dirs = withContext(Dispatchers.IO) {
                groups.keys.associateWith { rel ->
                    val dir = if (rel.isEmpty()) destRoot else childPath(destRoot, rel)
                    val f = File(dir)
                    if (f.isDirectory || f.mkdirs()) dir else null
                }
            }
            var launched = 0
            for ((rel, list) in groups) {
                val dir = dirs[rel]
                if (dir == null) {
                    _messages.trySend("Не удалось создать папку ${if (rel.isEmpty()) destRoot else rel}")
                    continue
                }
                val items = list.mapNotNull { (if (toRight) it.left else it.right)?.toFileItem() }
                if (items.isEmpty()) continue
                // Заменяем только файлы поверх файлов — конфликты «файл/папка» решает пользователь.
                for (e in list) {
                    val dst = if (toRight) e.right else e.left
                    val src = if (toRight) e.left else e.right
                    if (dst != null && src != null && !dst.isDir && !src.isDir) approvedReplace += dst.path
                }
                val id = c.taskManager.copy(items, dir, move = false)
                pending += id
                launched++
            }
            if (launched > 0) {
                _state.update { it.copy(selected = emptySet()) }
                _messages.trySend(
                    "${if (toRight) "Копирование вправо" else "Копирование влево"}: ${Format.items(plan.entries.size)}",
                )
            }
        }
    }

    override fun onCleared() {
        extJob?.cancel()
        scanJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val EXTENSION_DEBOUNCE_MS = 500L
    }
}
