package client.wexmanager.feature.browser

import android.os.Build
import android.os.FileObserver
import androidx.compose.runtime.Immutable
import client.wexmanager.core.archive.ArchiveEngine
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.fs.Location
import client.wexmanager.core.fs.NameFilter
import client.wexmanager.core.fs.StorageInfo
import client.wexmanager.core.fs.StorageRepository
import client.wexmanager.core.fs.depth
import client.wexmanager.core.fs.fileComparator
import client.wexmanager.core.fs.isAncestorOf
import client.wexmanager.core.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class NavDirection { None, Forward, Backward }

@Immutable
data class PanelState(
    val location: Location = Location.Local("/"),
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val selection: Set<String> = emptySet(),
    val filter: String = "",
    val filterVisible: Boolean = false,
    val filterValid: Boolean = true,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val direction: NavDirection = NavDirection.None,
    /** Ключ папки, из которой вернулись — для прокрутки и подсветки. */
    val focusKey: String? = null,
    val storage: StorageInfo? = null,
    val dirCount: Int = 0,
    val fileCount: Int = 0,
    val filesSize: Long = 0,
    val hiddenCount: Int = 0,
    val loadVersion: Long = 0,
    val computingSizes: Boolean = false,
) {
    val selectionMode: Boolean get() = selection.isNotEmpty()
    val selectedItems: List<FileItem> get() = if (selection.isEmpty()) emptyList() else items.filter { it.key in selection }
}

class PanelController(
    val index: Int,
    private val scope: CoroutineScope,
    private val settings: StateFlow<AppSettings>,
    private val storage: StorageRepository,
) {
    private val _state = MutableStateFlow(PanelState(location = Location.Local(storage.primaryPath)))
    val state: StateFlow<PanelState> = _state.asStateFlow()

    private var raw: List<FileItem> = emptyList()
    private val back = ArrayDeque<Location>()
    private val forward = ArrayDeque<Location>()
    private var loadJob: Job? = null
    private var countJob: Job? = null
    private var observer: FileObserver? = null
    private var refreshJob: Job? = null
    private var version = 0L
    /** Последняя успешно загруженная папка — опора для истории (не зависит от промежуточного состояния загрузки). */
    private var lastLoaded: Location? = null

    val location: Location get() = _state.value.location

    init {
        scope.launch {
            settings.map { Triple(it.showHidden, Triple(it.sortBy, it.sortDescending, it.foldersFirst), it.showFolderItemCount) }
                .distinctUntilChanged()
                .collect { rederive() }
        }
    }

    fun open(target: Location, addToHistory: Boolean = true, direction: NavDirection? = null, focusKey: String? = null) {
        val current = _state.value.location
        val dir = direction ?: when {
            current == target -> NavDirection.None
            current.isAncestorOf(target) -> NavDirection.Forward
            target.isAncestorOf(current) -> NavDirection.Backward
            target.depth > current.depth -> NavDirection.Forward
            else -> NavDirection.Backward
        }
        val from = lastLoaded
        if (addToHistory && from != null && from != target) {
            back.addLast(from)
            if (back.size > 100) back.removeFirst()
            forward.clear()
        }
        val focus = focusKey ?: if (target.isAncestorOf(current)) childKeyToward(target, current) else null
        load(target, dir, focus, keepSelection = false)
    }

    /** Ключ непосредственного потомка [ancestor], ведущего к [descendant]. */
    private fun childKeyToward(ancestor: Location, descendant: Location): String? {
        var p: Location? = descendant
        while (p != null) {
            val parent = p.parent
            if (parent == ancestor) {
                return when (p) {
                    is Location.Local -> p.path
                    is Location.Archive -> if (ancestor is Location.Local) p.archivePath else "${p.archivePath}!/${p.inner}"
                }
            }
            p = parent
        }
        return null
    }

    fun refresh() {
        load(_state.value.location, NavDirection.None, null, keepSelection = true)
    }

    fun goUp(): Boolean {
        val parent = _state.value.location.parent ?: return false
        open(parent)
        return true
    }

    fun goBack(): Boolean {
        val target = back.removeLastOrNull() ?: return false
        forward.addLast(lastLoaded ?: _state.value.location)
        load(target, NavDirection.Backward, childKeyToward(target, _state.value.location), keepSelection = false)
        return true
    }

    fun goForward(): Boolean {
        val target = forward.removeLastOrNull() ?: return false
        back.addLast(lastLoaded ?: _state.value.location)
        load(target, NavDirection.Forward, null, keepSelection = false)
        return true
    }

    fun history(): List<Location> = back.toList().asReversed()

    private fun load(target: Location, direction: NavDirection, focusKey: String?, keepSelection: Boolean) {
        loadJob?.cancel()
        countJob?.cancel()
        // Сравниваем с последней загруженной папкой: спиннер уже мог записать target в состояние.
        val sameLocation = target == lastLoaded && target == _state.value.location
        loadJob = scope.launch {
            val showSpinner = launch {
                delay(if (sameLocation) 600 else 160)
                // Старый список больше не показываем — и не даём пересчётам вернуть его на экран.
                if (!sameLocation) raw = emptyList()
                _state.update {
                    it.copy(
                        location = target,
                        loading = true,
                        error = null,
                        items = if (sameLocation) it.items else emptyList(),
                        direction = direction,
                        loadVersion = if (sameLocation) it.loadVersion else ++version,
                    )
                }
            }
            val result = runCatching { list(target) }
            if (!isActive) return@launch
            val storageInfo = if (target is Location.Local) withContext(Dispatchers.IO) { storage.infoFor(target.path) } else null
            // Сортируем и фильтруем в фоне, а состояние меняем одним обновлением — без кадра со старым списком.
            val usedFilter = if (sameLocation) _state.value.filter else ""
            val usedSettings = settings.value
            val sizesBefore = if (sameLocation) HashMap(dirSizes) else emptyMap()
            val derived = result.getOrNull()?.let { loaded ->
                val list = if (sizesBefore.isEmpty()) loaded else loaded.map { i -> sizesBefore[i.path]?.let { i.copy(size = it) } ?: i }
                list to withContext(Dispatchers.Default) { derive(list, usedSettings, usedFilter) }
            }
            showSpinner.cancel()
            if (!isActive) return@launch
            result.onSuccess {
                if (!sameLocation) {
                    dirSizes.clear()
                    sizesJob?.cancel()
                    _state.update { it.copy(computingSizes = false) }
                }
                var (list, d) = derived!!
                // Размеры папок, досчитанные пока шла сортировка, не должны пропасть.
                val sizesChanged = sameLocation && dirSizes.size != sizesBefore.size
                if (sizesChanged) list = list.map { i -> dirSizes[i.path]?.let { i.copy(size = it) } ?: i }
                raw = list
                deriveJob?.cancel()
                lastLoaded = target
                val prevSelection = _state.value.selection
                _state.update {
                    it.copy(
                        location = target,
                        loading = false,
                        error = null,
                        direction = direction,
                        focusKey = focusKey,
                        storage = storageInfo,
                        selection = if (keepSelection && prevSelection.isNotEmpty()) {
                            val keys = list.mapTo(HashSet(list.size)) { i -> i.key }
                            prevSelection.filterTo(HashSet()) { k -> k in keys }
                        } else emptySet(),
                        filter = if (sameLocation) it.filter else "",
                        filterVisible = if (sameLocation) it.filterVisible else false,
                        canGoBack = back.isNotEmpty(),
                        canGoForward = forward.isNotEmpty(),
                        loadVersion = if (sameLocation && it.location == target) it.loadVersion else ++version,
                    ).withDerived(d)
                }
                // Фильтр, настройки или размеры поменялись, пока список сортировался, — пересчитать.
                if (sizesChanged || _state.value.filter != usedFilter || settings.value.listKey() != usedSettings.listKey()) rederive()
                watch(target)
                loadChildCounts(target, list)
            }.onFailure { e ->
                raw = emptyList()
                _state.update {
                    it.copy(
                        location = target,
                        loading = false,
                        items = emptyList(),
                        error = e.message ?: "Ошибка чтения",
                        direction = direction,
                        storage = storageInfo,
                        selection = emptySet(),
                        canGoBack = back.isNotEmpty(),
                        canGoForward = forward.isNotEmpty(),
                        loadVersion = if (sameLocation) it.loadVersion else ++version,
                    )
                }
                stopWatching()
            }
        }
    }

    private suspend fun list(target: Location): List<FileItem> = when (target) {
        is Location.Local -> LocalFileSystem.list(target.path)
        is Location.Archive -> {
            val index = ArchiveEngine.index(File(target.archivePath))
            index.list(target.inner) ?: throw java.io.IOException("Папка не найдена в архиве")
        }
    }

    private fun loadChildCounts(target: Location, list: List<FileItem>) {
        if (target !is Location.Local || !settings.value.showFolderItemCount) return
        val dirs = list.filter { it.isDirectory && it.childCount < 0 }
        if (dirs.isEmpty() || dirs.size > 3000) return
        countJob = scope.launch {
            val showHidden = settings.value.showHidden
            val counts = withContext(Dispatchers.IO) {
                val m = HashMap<String, Int>(dirs.size)
                for (d in dirs) {
                    if (!isActive) break
                    m[d.path] = LocalFileSystem.countChildren(d.path, showHidden)
                }
                m
            }
            // Применяем только на главном потоке и только если папка и список не сменились.
            if (!isActive || _state.value.location != target) return@launch
            raw = raw.map { if (it.isDirectory && it.childCount < 0) counts[it.path]?.let { c -> it.copy(childCount = c) } ?: it else it }
            rederive()
        }
    }

    /** Отфильтрованный и отсортированный список со сводкой — считается вне главного потока для больших папок. */
    private companion object {
        const val ASYNC_DERIVE_THRESHOLD = 400
    }

    /** Настройки, от которых зависит отображаемый список. */
    private fun AppSettings.listKey() = listOf(showHidden, sortBy, sortDescending, foldersFirst)

    private class Derived(
        val items: List<FileItem>,
        val filterValid: Boolean,
        val dirs: Int,
        val files: Int,
        val size: Long,
        val hidden: Int,
    )

    private fun derive(source: List<FileItem>, s: AppSettings, filterText: String): Derived {
        val filter = NameFilter.parse(filterText)
        val visible = if (s.showHidden) source else source.filterNot { it.isHidden }
        val filtered = if (filterText.isEmpty()) visible else visible.filter { filter.matches(it.name) }
        val sorted = filtered.sortedWith(fileComparator(s.sortBy, s.sortDescending, s.foldersFirst))
        var dirs = 0
        var files = 0
        var size = 0L
        for (i in sorted) if (i.isDirectory) dirs++ else { files++; size += i.size }
        return Derived(sorted, filter.isValid, dirs, files, size, source.size - visible.size)
    }

    private fun PanelState.withDerived(d: Derived): PanelState = copy(
        items = d.items,
        filterValid = d.filterValid,
        dirCount = d.dirs,
        fileCount = d.files,
        filesSize = d.size,
        hiddenCount = d.hidden,
        selection = if (selection.isEmpty()) selection else {
            val keys = d.items.mapTo(HashSet(d.items.size)) { i -> i.key }
            selection.filterTo(HashSet()) { k -> k in keys }
        },
    )

    private var deriveJob: Job? = null

    private fun rederive() {
        val s = settings.value
        val filterText = _state.value.filter
        val source = raw
        deriveJob?.cancel()
        if (source.size < ASYNC_DERIVE_THRESHOLD) {
            val d = derive(source, s, filterText)
            _state.update { it.withDerived(d) }
            return
        }
        // Сортировка тысяч имён «натуральным» сравнением заметно подтормаживает главный поток.
        deriveJob = scope.launch {
            delay(16) // серия вызовов подряд (размеры папок, ввод фильтра) склеивается в один пересчёт
            val d = withContext(Dispatchers.Default) { derive(source, s, filterText) }
            if (raw === source && _state.value.filter == filterText) _state.update { it.withDerived(d) }
        }
    }

    // ----------------------------------------------------------- Размеры папок

    private var sizesJob: Job? = null
    /** Посчитанные размеры папок текущего каталога — переживают обновление списка. */
    private val dirSizes = HashMap<String, Long>()

    /** Рекурсивно считает размеры всех папок текущего списка (по запросу пользователя). */
    fun computeFolderSizes() {
        val target = _state.value.location as? Location.Local ?: return
        val dirs = raw.filter { it.isDirectory && !it.isSymlink }
        if (dirs.isEmpty()) return
        sizesJob?.cancel()
        _state.update { it.copy(computingSizes = true) }
        val job = scope.launch {
            try {
                for (d in dirs) {
                    val bytes = try {
                        LocalFileSystem.directorySize(d.path).third
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        continue
                    }
                    if (!isActive || _state.value.location != target) break
                    dirSizes[d.path] = bytes
                    raw = raw.map { if (it.path == d.path) it.copy(size = bytes) else it }
                    rederive()
                }
            } finally {
                // Индикатор гасит только последняя запущенная задача, а не отменённая предыдущая.
                if (sizesJob === coroutineContext[Job]) _state.update { it.copy(computingSizes = false) }
            }
        }
        sizesJob = job
    }

    // ------------------------------------------------------------------ Фильтр

    fun setFilter(query: String) {
        _state.update { it.copy(filter = query, filterVisible = true) }
        rederive()
    }

    fun showFilter(visible: Boolean) {
        _state.update { it.copy(filterVisible = visible, filter = if (visible) it.filter else "") }
        if (!visible) rederive()
    }

    // --------------------------------------------------------------- Выделение

    fun toggleSelection(item: FileItem) {
        _state.update { st ->
            val sel = st.selection.toMutableSet()
            if (!sel.add(item.key)) sel.remove(item.key)
            st.copy(selection = sel)
        }
    }

    fun setSelection(keys: Set<String>) = _state.update { it.copy(selection = keys) }

    fun selectRange(fromIndex: Int, toIndex: Int, base: Set<String>, select: Boolean) {
        val items = _state.value.items
        val range = (minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex)).mapNotNull { items.getOrNull(it)?.key }
        _state.update { it.copy(selection = if (select) base + range else base - range.toSet()) }
    }

    fun selectAll() = _state.update { st -> st.copy(selection = st.items.map { it.key }.toSet()) }

    fun invertSelection() = _state.update { st -> st.copy(selection = st.items.map { it.key }.toSet() - st.selection) }

    /** Выделить все файлы того же типа (расширения), что и уже выделенные — «выбор по типу» из MT. */
    fun selectSameType() = _state.update { st ->
        val selected = st.items.filter { it.key in st.selection }
        val exts = selected.map { if (it.isDirectory) "/" else it.extension }.toSet()
        st.copy(selection = st.items.filter { (if (it.isDirectory) "/" else it.extension) in exts }.map { it.key }.toSet())
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun consumeFocus() = _state.update { it.copy(focusKey = null) }

    // ------------------------------------------------------------ Наблюдение

    private fun watch(target: Location) {
        stopWatching()
        if (!settings.value.autoRefresh || target !is Location.Local) return
        val dir = File(target.path)
        val mask = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
        val obs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            object : FileObserver(dir, mask) {
                override fun onEvent(event: Int, path: String?) = scheduleRefresh()
            }
        } else {
            @Suppress("DEPRECATION")
            object : FileObserver(dir.path, mask) {
                override fun onEvent(event: Int, path: String?) = scheduleRefresh()
            }
        }
        obs.startWatching()
        observer = obs
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(350)
            if (loadJob?.isActive != true) refresh()
        }
    }

    private fun stopWatching() {
        observer?.stopWatching()
        observer = null
    }

    fun dispose() {
        stopWatching()
        loadJob?.cancel()
        countJob?.cancel()
        refreshJob?.cancel()
    }
}
