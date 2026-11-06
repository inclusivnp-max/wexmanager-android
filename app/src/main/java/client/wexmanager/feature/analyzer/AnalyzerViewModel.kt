package client.wexmanager.feature.analyzer

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.NaturalOrder
import client.wexmanager.core.ops.TaskEvent
import client.wexmanager.core.ops.TaskStatus
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

/** Элемент, над которым выполняется действие (строка дерева, крупный файл или пустая папка). */
@Immutable
data class Target(val path: String, val name: String, val isDir: Boolean, val size: Long, val lastModified: Long) {
    fun toFileItem(): FileItem = FileItem(path = path, name = name, isDirectory = isDir, size = size, lastModified = lastModified)
}

/** Порядок вкладки «Крупные». Список уже отобран сканером — смена порядка его не пересобирает. */
enum class LargestSort(val title: String) {
    Size("Размер"),
    Name("Имя"),
    Modified("Изменён"),
}

private val largestByName: Comparator<FileEntry> = Comparator { a, b ->
    val byName = NaturalOrder.compare(a.name, b.name)
    if (byName != 0) byName else a.path.compareTo(b.path)
}

@Immutable
data class AnalyzerState(
    val rootPath: String,
    val scanning: Boolean = true,
    val progress: ScanProgress = ScanProgress(current = rootPath),
    val tree: DirNode? = null,
    /** Цепочка папок от корня до текущей (первый элемент — корень). */
    val stack: List<DirNode> = emptyList(),
    val largest: List<FileEntry> = emptyList(),
    /** Порядок показа [largest]. По умолчанию — как отдал сканер (размер, по убыванию). */
    val largestSort: LargestSort = LargestSort.Size,
    val emptyDirs: List<String> = emptyList(),
    val unreadable: Int = 0,
    val durationMs: Long = 0,
    val error: String? = null,
) {
    val current: DirNode? get() = stack.lastOrNull()
    val depth: Int get() = (stack.size - 1).coerceAtLeast(0)

    /** Абсолютный путь текущей папки. */
    val currentPath: String
        get() = stack.drop(1).fold(rootPath) { acc, n -> if (acc == "/") "/${n.name}" else "$acc/${n.name}" }

    fun pathOf(child: Node): String = currentPath.let { if (it == "/") "/${child.name}" else "$it/${child.name}" }

    /** Те же файлы, другой порядок. Размер не пересортировывает: сканер уже отдал убывание. */
    fun orderedLargest(): List<FileEntry> = when (largestSort) {
        LargestSort.Size -> largest
        LargestSort.Name -> largest.sortedWith(largestByName)
        LargestSort.Modified -> largest.sortedWith(compareByDescending<FileEntry> { it.lastModified }.then(largestByName))
    }
}

class AnalyzerViewModel(app: Application, root: String) : AndroidViewModel(app) {
    private val c = app.appContainer
    private val rootPath = root.trimEnd('/').ifEmpty { "/" }

    private val _state = MutableStateFlow(AnalyzerState(rootPath))
    val state: StateFlow<AnalyzerState> = _state.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var scanJob: Job? = null
    @Volatile private var scanGen = 0
    private val pendingDeletes = HashMap<Long, String>()

    init {
        rescan()
        viewModelScope.launch {
            c.taskManager.events.collect { e ->
                if (e !is TaskEvent.Finished) return@collect
                val what = pendingDeletes.remove(e.task.id) ?: return@collect
                if (e.task.status == TaskStatus.Failed || e.task.failedItems.isNotEmpty()) {
                    _messages.trySend("Не удалось удалить «$what». Пересканируйте папку")
                }
            }
        }
    }

    fun rescan() {
        scanJob?.cancel()
        val previous = _state.value
        val keepPath = previous.stack.drop(1).map { it.name }
        _state.value = AnalyzerState(rootPath, largestSort = previous.largestSort)
        val gen = ++scanGen
        scanJob = viewModelScope.launch {
            try {
                val result = DiskScanner.scan(rootPath) { p -> if (gen == scanGen) _state.update { it.copy(progress = p) } }
                if (result.root.unreadable) {
                    _state.update { it.copy(scanning = false, error = "Нет доступа к папке $rootPath") }
                    return@launch
                }
                _state.update {
                    it.copy(
                        scanning = false,
                        tree = result.root,
                        stack = resolve(result.root, keepPath),
                        largest = result.largest,
                        emptyDirs = result.emptyDirs.filter { p -> p != rootPath },
                        unreadable = result.unreadable,
                        durationMs = result.durationMs,
                        error = null,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(scanning = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun open(dir: DirNode) = _state.update { it.copy(stack = it.stack + dir) }

    /** Подняться на уровень вверх. `false` — уже в корне. */
    fun up(): Boolean {
        val s = _state.value
        if (s.stack.size <= 1) return false
        _state.value = s.copy(stack = s.stack.dropLast(1))
        return true
    }

    fun jumpTo(level: Int) = _state.update { it.copy(stack = it.stack.take((level + 1).coerceAtLeast(1))) }

    /** Только переставляет уже найденные крупные файлы, без нового сканирования. */
    fun setLargestSort(sort: LargestSort) {
        if (_state.value.largestSort == sort) return
        _state.update { it.copy(largestSort = sort) }
    }

    fun delete(targets: List<Target>) {
        if (targets.isEmpty()) return
        val useTrash = c.settings.settings.value.useTrash
        val id = c.taskManager.delete(targets.map { it.toFileItem() }, toTrash = useTrash)
        pendingDeletes[id] = if (targets.size == 1) targets[0].name else "${targets.size} элементов"
        // Пересчёт дерева для сотен элементов («Удалить все» пустые папки) — O(n²), не на главном потоке.
        // Если за время расчёта состояние сменилось (навигация, пересканирование) — считаем заново.
        viewModelScope.launch {
            while (true) {
                val before = _state.value
                val after = withContext(Dispatchers.Default) { targets.fold(before) { acc, t -> acc.removed(t) } }
                if (_state.compareAndSet(before, after)) break
            }
        }
    }

    /** Убирает узел из дерева (с пересчётом размеров предков) и из списков. */
    private fun AnalyzerState.removed(t: Target): AnalyzerState {
        val tree = tree ?: return this
        val rel = t.path.removePrefix(rootPath).trim('/')
        if (rel.isEmpty()) return this
        val names = rel.split('/')
        val newTree = tree.without(names) ?: tree
        val keep = stack.drop(1).map { it.name }
        val prefix = t.path + "/"
        var empties = emptyDirs.filter { it != t.path && !it.startsWith(prefix) }
        // Родитель мог опустеть — тогда он сам становится пустой папкой.
        val parentNames = names.dropLast(1)
        val chain = resolve(newTree, parentNames)
        val parentPath = t.path.substringBeforeLast('/')
        if (chain.size == parentNames.size + 1 && chain.last().children.isEmpty() && parentPath != rootPath && parentPath !in empties) {
            empties = empties + parentPath
        }
        return copy(
            tree = newTree,
            stack = resolve(newTree, keep),
            largest = largest.filter { it.path != t.path && !it.path.startsWith(prefix) },
            emptyDirs = empties,
        )
    }

    /** Восстанавливает цепочку папок по именам; обрывается на первой отсутствующей. */
    private fun resolve(root: DirNode, names: List<String>): List<DirNode> {
        val out = ArrayList<DirNode>(names.size + 1)
        out += root
        var cur = root
        for (n in names) {
            val next = cur.child(n) as? DirNode ?: break
            out += next
            cur = next
        }
        return out
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }
}
