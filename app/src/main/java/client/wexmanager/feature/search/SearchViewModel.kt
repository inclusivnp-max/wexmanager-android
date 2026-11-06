package client.wexmanager.feature.search

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.fs.NaturalOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@Immutable
data class SearchRoot(val path: String, val title: String, val isVolume: Boolean)

@Immutable
data class SearchStatus(
    val running: Boolean = false,
    /** Поиск по текущим параметрам выполнялся (завершён или остановлен). */
    val done: Boolean = false,
    val stopped: Boolean = false,
    val scanned: Int = 0,
    val found: Int = 0,
    val currentDir: String = "",
    val elapsedMs: Long = 0,
    val limitReached: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(app: Application, private val initialRoot: String) : AndroidViewModel(app) {
    private val c = app.appContainer

    /** Текст запроса держим в snapshot-состоянии, чтобы поле ввода не «прыгало» курсором. */
    var query by mutableStateOf("")
        private set

    private val _params = MutableStateFlow(SearchParams(root = initialRoot))
    val params: StateFlow<SearchParams> = _params.asStateFlow()

    private val _status = MutableStateFlow(SearchStatus())
    val status: StateFlow<SearchStatus> = _status.asStateFlow()

    private val _sort = MutableStateFlow(ResultSort.Relevance)
    val sort: StateFlow<ResultSort> = _sort.asStateFlow()

    private val raw = MutableStateFlow<List<SearchHit>>(emptyList())

    val results: StateFlow<List<SearchHit>> = combine(raw, _sort) { list, sort -> sorted(list, sort) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // distinct: запрос — ключ строки истории в LazyColumn, дубль из старых данных уронил бы список.
    val history: StateFlow<List<String>> = c.userData.data.map { it.searchHistory.distinct() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, c.userData.data.value.searchHistory.distinct())

    /** Чипы сессии, отдельно от истории в настройках: filesDir/search-queries.json, максимум 8. */
    private val filesDir = app.filesDir
    private val recentLock = Mutex()
    private var recentLoaded = false
    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    private val _roots = MutableStateFlow(listOf(SearchRoot(initialRoot, titleOf(initialRoot), false)))
    val roots: StateFlow<List<SearchRoot>> = _roots.asStateFlow()

    private var job: Job? = null
    @Volatile private var generation = 0L

    init {
        c.appScope.launch(Dispatchers.IO) {
            recentLock.withLock { ensureRecentLoaded() }
        }
        viewModelScope.launch {
            val volumes = withContext(Dispatchers.IO) { runCatching { c.storage.volumes() }.getOrDefault(emptyList()) }
            val list = buildList {
                if (volumes.none { it.path == initialRoot }) add(SearchRoot(initialRoot, titleOf(initialRoot), false))
                volumes.forEach { add(SearchRoot(it.path, it.title, true)) }
            }
            _roots.value = list.ifEmpty { listOf(SearchRoot(initialRoot, titleOf(initialRoot), false)) }
        }
    }

    fun onQueryChange(value: String) {
        if (value == query) return
        query = value
        _params.update { it.copy(query = value) }
        restart(debounceMs = 300)
    }

    /** Нажатие «Найти» на клавиатуре: запуск без задержки и запись в историю. */
    fun submit() {
        rememberQuery()
        restart(debounceMs = 0)
    }

    fun applyHistory(value: String) {
        search(value)
        rememberQuery()
    }

    /** Подставить запрос и сразу искать (без записи в историю). */
    fun search(value: String) {
        query = value
        _params.update { it.copy(query = value) }
        restart(debounceMs = 0)
    }

    /** Чип сессии: подставить запрос и искать так же, как кнопка «Найти». */
    fun applyRecent(value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        query = v
        _params.update { it.copy(query = v) }
        submit()
    }

    fun rememberQuery() {
        val q = query.trim()
        if (q.isNotEmpty()) {
            c.userData.addSearchQuery(q)
            rememberSession(q)
        }
    }

    private fun rememberSession(raw: String) {
        var changed = false
        _recentQueries.update { current ->
            val next = SessionQueries.remember(current, raw)
            changed = next != current
            next
        }
        if (changed) persistRecent()
    }

    /** Догрузка файла не должна затереть запрос, уже отправленный в этой сессии. */
    private fun ensureRecentLoaded() {
        if (recentLoaded) return
        val fromDisk = SessionQueries.load(filesDir)
        _recentQueries.update { SessionQueries.mergeLoaded(it, fromDisk) }
        recentLoaded = true
    }

    private fun persistRecent() {
        // appScope: запись доживает уход с экрана, иначе последний запрос теряется.
        c.appScope.launch(Dispatchers.IO) {
            recentLock.withLock {
                ensureRecentLoaded()
                if (recentLoaded) runCatching { SessionQueries.save(filesDir, _recentQueries.value) }
            }
        }
    }

    fun removeHistory(value: String) = c.userData.update { d -> d.copy(searchHistory = d.searchHistory.filterNot { it == value }) }

    fun clearHistory() = c.userData.clearSearchHistory()

    fun updateParams(transform: (SearchParams) -> SearchParams) {
        val old = _params.value
        val new = transform(old)
        if (new == old) return
        _params.value = new
        restart(debounceMs = 0)
    }

    fun setSort(sort: ResultSort) {
        _sort.value = sort
    }

    fun stop() {
        job?.cancel()
        job = null
        generation++
        _status.update { it.copy(running = false, done = true, stopped = true) }
    }

    fun retry() = restart(debounceMs = 0)

    private fun restart(debounceMs: Long) {
        job?.cancel()
        val gen = ++generation
        val p = _params.value
        if (!p.isRunnable) {
            raw.value = emptyList()
            _status.value = SearchStatus()
            return
        }
        val matcher = QueryMatcher.create(p.query, p.regex, p.caseSensitive)
        if (!matcher.isValid) {
            raw.value = emptyList()
            _status.value = SearchStatus(error = matcher.error)
            return
        }
        _status.update { it.copy(error = null) }
        job = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            raw.value = emptyList()
            _status.value = SearchStatus(running = true, currentDir = p.root)
            val started = SystemClock.elapsedRealtime()
            val acc = ArrayList<SearchHit>()
            try {
                val (scanned, limit) = SearchEngine.run(p, matcher) { batch, progress ->
                    if (gen != generation) return@run
                    if (batch.isNotEmpty()) {
                        acc.addAll(batch)
                        raw.value = ArrayList(acc)
                    }
                    _status.update {
                        it.copy(
                            scanned = progress.scanned,
                            found = acc.size,
                            currentDir = progress.currentDir,
                            elapsedMs = SystemClock.elapsedRealtime() - started,
                        )
                    }
                }
                if (gen == generation) {
                    _status.value = SearchStatus(
                        running = false,
                        done = true,
                        scanned = scanned,
                        found = acc.size,
                        elapsedMs = SystemClock.elapsedRealtime() - started,
                        limitReached = limit,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen == generation) _status.update { it.copy(running = false, done = true, error = e.message ?: "Ошибка поиска") }
            }
        }
    }

    private fun sorted(list: List<SearchHit>, sort: ResultSort): List<SearchHit> = when (sort) {
        ResultSort.Relevance -> list.sortedByDescending { it.score }
        ResultSort.Name -> list.sortedWith { a, b -> NaturalOrder.compare(a.item.name, b.item.name) }
        ResultSort.Size -> list.sortedByDescending { if (it.item.isDirectory) -1L else it.item.size }
        ResultSort.Date -> list.sortedByDescending { it.item.lastModified }
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }

    private companion object {
        fun titleOf(path: String): String = when {
            path == "/" -> "/"
            path == android.os.Environment.getExternalStorageDirectory().path -> "Внутренняя память"
            else -> File(path).name.ifEmpty { path }
        }
    }
}
