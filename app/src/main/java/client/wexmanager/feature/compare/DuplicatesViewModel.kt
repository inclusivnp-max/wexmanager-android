package client.wexmanager.feature.compare

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.ops.TaskEvent
import client.wexmanager.core.ops.TaskKind
import client.wexmanager.core.util.Format
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class MinSize(val label: String, val bytes: Long) {
    Any("Любой", 1L),
    K1("1 КБ", 1024L),
    K100("100 КБ", 100L * 1024),
    M1("1 МБ", 1024L * 1024),
}

enum class AutoSelect { Newest, Oldest, FirstFolder }

/**
 * Копии, которые можно убрать, оставив в группе один самый новый файл (mtime).
 * Группы из одного файла пропускаются. При равном mtime остаётся первый файл
 * в порядке группы, остальные уходят на удаление.
 */
internal fun copiesExceptNewest(groups: List<DupGroup>): List<DupFile> {
    val out = ArrayList<DupFile>()
    for (g in groups) {
        if (g.files.size < 2) continue
        val keep = g.files.maxByOrNull { it.lastModified } ?: continue
        for (f in g.files) if (f.path != keep.path) out += f
    }
    return out
}

@Immutable
data class DupState(
    val root: String,
    val minSize: MinSize = MinSize.K1,
    val running: Boolean = true,
    val progress: DupProgress = DupProgress(),
    val groups: List<DupGroup> = emptyList(),
    val wasted: Long = 0,
    val selected: Set<String> = emptySet(),
    val selectedBytes: Long = 0,
    /** Групп, в которых выбраны все копии (удаление потеряет файл полностью). */
    val fullySelectedGroups: Int = 0,
    val scannedFiles: Int = 0,
    val unreadable: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null,
)

class DuplicatesViewModel(app: Application, root: String) : AndroidViewModel(app) {
    private val c = app.appContainer
    private val rootPath = normalizeRoot(root)

    private val _state = MutableStateFlow(DupState(rootPath))
    val state: StateFlow<DupState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Результат последнего сканирования и минимальный размер, с которым оно выполнено. */
    private var all: List<DupGroup> = emptyList()
    private var scannedMin = Long.MAX_VALUE
    /** Пути, отправленные на удаление (скрыты из групп). */
    private val removed = HashSet<String>()
    private val sizeOf = HashMap<String, Long>()
    private val pendingDeletes = HashMap<Long, List<String>>()
    private var scanJob: Job? = null

    init {
        rescan()
        viewModelScope.launch {
            c.taskManager.events.collect { e ->
                if (e !is TaskEvent.Finished) return@collect
                val paths = pendingDeletes.remove(e.task.id) ?: return@collect
                // Что не удалилось — возвращаем в группы.
                val left = withContext(Dispatchers.IO) { paths.filter { File(it).exists() } }
                if (left.isNotEmpty()) {
                    removed.removeAll(left.toSet())
                    refresh()
                    _messages.trySend("Не удалось удалить: ${Format.files(left.size)}")
                } else {
                    _messages.trySend("${if (e.task.kind == TaskKind.Trash) "В корзину перемещено" else "Удалено"}: ${Format.files(paths.size)}")
                }
            }
        }
    }

    fun rescan() {
        scanJob?.cancel()
        val min = _state.value.minSize
        _state.update { DupState(rootPath, minSize = min) }
        removed.clear()
        scanJob = viewModelScope.launch {
            try {
                if (!File(rootPath).isDirectory) {
                    _state.update { it.copy(running = false, error = "Папка недоступна: $rootPath") }
                    return@launch
                }
                val result = DuplicateFinder.find(rootPath, min.bytes) { p -> _state.update { s -> if (s.running) s.copy(progress = p) else s } }
                all = result.groups
                scannedMin = min.bytes
                _state.update {
                    it.copy(
                        running = false,
                        scannedFiles = result.scannedFiles,
                        unreadable = result.unreadable,
                        durationMs = result.durationMs,
                        error = null,
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(running = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    /** Больший порог — фильтруем готовый результат, меньший — нужно новое сканирование. */
    fun setMinSize(min: MinSize) {
        val s = _state.value
        if (s.minSize == min) return
        _state.update { it.copy(minSize = min) }
        if (s.running || min.bytes < scannedMin) rescan() else refresh()
    }

    fun toggle(path: String) {
        val sel = _state.value.selected
        applySelection(if (path in sel) sel - path else sel + path)
    }

    fun clearSelection() = applySelection(emptySet())

    /** Выделяет все копии, кроме одной в каждой группе. */
    fun autoSelect(mode: AutoSelect) {
        val out = HashSet<String>()
        for (g in _state.value.groups) {
            if (g.files.size < 2) continue
            val keep = when (mode) {
                AutoSelect.Newest -> g.files.maxByOrNull { it.lastModified }
                AutoSelect.Oldest -> g.files.minByOrNull { it.lastModified }
                AutoSelect.FirstFolder -> g.files.firstOrNull()
            } ?: continue
            for (f in g.files) if (f.path != keep.path) out += f.path
        }
        applySelection(out)
    }

    private fun applySelection(sel: Set<String>) {
        val groups = _state.value.groups
        var bytes = 0L
        for (p in sel) bytes += sizeOf[p] ?: 0L
        val full = if (sel.isEmpty()) 0 else groups.count { g -> g.files.all { it.path in sel } }
        _state.update { it.copy(selected = sel, selectedBytes = bytes, fullySelectedGroups = full) }
    }

    /** Отправляет выбранные файлы на удаление и сразу убирает их из групп. */
    fun deleteSelected() {
        val s = _state.value
        submitDelete(s.groups.flatMap { it.files }.filter { it.path in s.selected })
    }

    /** В каждой группе оставляет самый новый файл и отправляет остальные в корзину или на удаление. */
    fun deleteKeepingNewest() {
        val victims = copiesExceptNewest(_state.value.groups)
        val keepers = _state.value.groups.mapNotNull { g ->
            if (g.files.size < 2) null else g.files.maxByOrNull { it.lastModified }?.path
        }.toSet()
        submitDelete(victims.filter { it.path !in keepers })
    }

    private fun submitDelete(files: List<DupFile>) {
        if (files.isEmpty()) return
        val items = files.map { FileItem(path = it.path, name = it.name, isDirectory = false, size = it.size, lastModified = it.lastModified) }
        val id = c.taskManager.delete(items, toTrash = c.settings.settings.value.useTrash)
        pendingDeletes[id] = files.map { it.path }
        removed += files.map { it.path }
        refresh()
    }

    /** Пересчитывает видимые группы (порог размера и удалённые файлы). */
    private fun refresh() {
        val min = _state.value.minSize.bytes
        val groups = ArrayList<DupGroup>()
        sizeOf.clear()
        for (g in all) {
            if (g.size < min) continue
            val files = if (removed.isEmpty()) g.files else g.files.filter { it.path !in removed }
            if (files.size < 2) continue
            groups += if (files.size == g.files.size) g else g.copy(files = files)
            for (f in files) sizeOf[f.path] = f.size
        }
        val wasted = groups.sumOf { it.wasted }
        val sel = _state.value.selected.filterTo(HashSet()) { it in sizeOf }
        _state.update { it.copy(groups = groups, wasted = wasted) }
        applySelection(sel)
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }
}
