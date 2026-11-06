package client.wexmanager.feature.apk

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.LruCache
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.apk.ApkAnalyzer
import client.wexmanager.core.apk.InstalledApp
import client.wexmanager.core.apk.InstalledApps
import client.wexmanager.core.fs.NaturalOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

enum class AppFilter(val title: String) { User("Пользовательские"), System("Системные"), All("Все") }

enum class AppSort(val title: String) { Name("Имя"), Size("Размер"), Installed("Дата установки"), Updated("Дата обновления") }

@Immutable
data class AppsViewOptions(
    val filter: AppFilter = AppFilter.User,
    val sort: AppSort = AppSort.Name,
    val descending: Boolean = false,
    val query: String = "",
)

@Immutable
data class AppsUiState(
    val loading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val totalCount: Int = 0,
    val userCount: Int = 0,
    val systemCount: Int = 0,
    val options: AppsViewOptions = AppsViewOptions(),
    val selection: Set<String> = emptySet(),
)

@Immutable
data class ExportProgress(val title: String, val index: Int, val total: Int, val fraction: Float, val sharing: Boolean)

sealed interface AppsEvent {
    /** [path] — файл (одно приложение) или папка (пакетное извлечение). */
    data class Exported(val path: String, val count: Int, val failed: Int) : AppsEvent
    data class Share(val file: File) : AppsEvent
    data class Message(val text: String) : AppsEvent
}

class AppIcon(val image: ImageBitmap, val adaptive: Boolean)

class AppsViewModel(app: Application) : AndroidViewModel(app) {

    private val pm = app.packageManager
    private val all = MutableStateFlow<List<InstalledApp>?>(null)
    private val loading = MutableStateFlow(true)
    private val options = MutableStateFlow(AppsViewOptions())
    private val selection = MutableStateFlow<Set<String>>(emptySet())

    val state: StateFlow<AppsUiState> = combine(all, options, selection, loading) { list, opt, sel, busy ->
        val source = list.orEmpty()
        val q = opt.query.trim()
        val filtered = source.filter { a ->
            val byType = when (opt.filter) {
                AppFilter.User -> !a.isSystem
                AppFilter.System -> a.isSystem
                AppFilter.All -> true
            }
            byType && (q.isEmpty() || a.label.contains(q, ignoreCase = true) || a.packageName.contains(q, ignoreCase = true))
        }
        val comparator: Comparator<InstalledApp> = when (opt.sort) {
            AppSort.Name -> compareBy(NaturalOrder) { it.label }
            AppSort.Size -> compareBy { it.apkSize }
            AppSort.Installed -> compareBy { it.firstInstallTime }
            AppSort.Updated -> compareBy { it.lastUpdateTime }
        }
        val sorted = filtered.sortedWith(if (opt.descending) comparator.reversed() else comparator)
        AppsUiState(
            loading = busy || list == null,
            apps = sorted,
            totalCount = source.size,
            userCount = source.count { !it.isSystem },
            systemCount = source.count { it.isSystem },
            options = opt,
            selection = sel,
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    private val _progress = MutableStateFlow<ExportProgress?>(null)
    val progress: StateFlow<ExportProgress?> = _progress.asStateFlow()

    private val _events = Channel<AppsEvent>(Channel.BUFFERED)
    val events: Flow<AppsEvent> = _events.receiveAsFlow()

    private var loadJob: Job? = null
    private var exportJob: Job? = null

    // ------------------------------------------------------------------ Иконки

    private val iconSizePx = (app.resources.displayMetrics.density * 48).toInt().coerceIn(48, 256)
    private val iconCache = object : LruCache<String, AppIcon>(
        minOf(Runtime.getRuntime().maxMemory() / 16, 32L * 1024 * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: AppIcon) = value.image.width * value.image.height * 4
    }
    private val iconLimiter = Semaphore(4)

    private fun iconKey(a: InstalledApp) = "${a.packageName}:${a.lastUpdateTime}"

    fun cachedIcon(a: InstalledApp): AppIcon? = iconCache.get(iconKey(a))

    suspend fun loadIcon(a: InstalledApp): AppIcon? {
        cachedIcon(a)?.let { return it }
        return iconLimiter.withPermit {
            withContext(Dispatchers.IO) {
                cachedIcon(a) ?: runCatching {
                    val rendered = ApkAnalyzer.renderIcon(pm.getApplicationIcon(a.packageName), iconSizePx)
                    AppIcon(rendered.bitmap.asImageBitmap(), rendered.adaptive).also { iconCache.put(iconKey(a), it) }
                }.getOrNull()
            }
        }
    }

    // ------------------------------------------------------------------ Изменения пакетов

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            refreshPackage(pkg)
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        load()
    }

    override fun onCleared() {
        runCatching { getApplication<Application>().unregisterReceiver(packageReceiver) }
        iconCache.evictAll()
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loading.value = true
            try {
                all.value = InstalledApps.loadAll(pm)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (all.value == null) all.value = emptyList()
                _events.trySend(AppsEvent.Message("Не удалось получить список: ${e.message}"))
            } finally {
                loading.value = false
            }
        }
    }

    private fun refreshPackage(pkg: String) {
        viewModelScope.launch {
            val entry = withContext(Dispatchers.IO) { InstalledApps.load(pm, pkg) }
            all.update { list -> list?.filterNot { it.packageName == pkg }?.let { if (entry != null) it + entry else it } }
            if (entry == null) selection.update { it - pkg }
        }
    }

    // ------------------------------------------------------------------ Вид

    fun setFilter(f: AppFilter) {
        options.update { it.copy(filter = f) }
        selection.value = emptySet()
    }

    fun setSort(s: AppSort) = options.update {
        // Повторный выбор того же поля меняет направление.
        if (it.sort == s) it.copy(descending = !it.descending)
        else it.copy(sort = s, descending = s != AppSort.Name)
    }

    fun setDescending(d: Boolean) = options.update { it.copy(descending = d) }
    fun setQuery(q: String) = options.update { it.copy(query = q) }

    fun toggleSelection(pkg: String) = selection.update { if (pkg in it) it - pkg else it + pkg }
    fun selectAll() = selection.update { it + state.value.apps.map { a -> a.packageName } }
    fun clearSelection() {
        selection.value = emptySet()
    }

    fun find(pkg: String): InstalledApp? = all.value?.firstOrNull { it.packageName == pkg }

    // ------------------------------------------------------------------ Извлечение

    val exportDir: File get() = File(getApplication<Application>().appContainer.storage.primaryPath, "WexManager/Apps")

    fun extractSelected() {
        val sel = selection.value
        val apps = all.value.orEmpty().filter { it.packageName in sel }.sortedWith(compareBy(NaturalOrder) { it.label })
        if (apps.isNotEmpty()) extract(apps)
    }

    fun extract(apps: List<InstalledApp>) {
        if (exportJob?.isActive == true) {
            _events.trySend(AppsEvent.Message("Дождитесь завершения текущего извлечения"))
            return
        }
        val dir = exportDir
        exportJob = viewModelScope.launch {
            var last: File? = null
            var ok = 0
            var failed = 0
            var lastError: String? = null
            try {
                apps.forEachIndexed { i, a ->
                    _progress.value = ExportProgress(a.label, i, apps.size, 0f, sharing = false)
                    try {
                        last = InstalledApps.export(a, dir) { f -> _progress.value = ExportProgress(a.label, i, apps.size, f, sharing = false) }
                        ok++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        failed++
                        lastError = e.message ?: e.javaClass.simpleName
                    }
                }
            } finally {
                _progress.value = null
            }
            val event = when {
                ok == 0 -> AppsEvent.Message("Не удалось извлечь: $lastError")
                apps.size == 1 -> AppsEvent.Exported(last!!.absolutePath, 1, 0)
                else -> AppsEvent.Exported(dir.absolutePath, ok, failed)
            }
            _events.send(event)
            if (ok > 0) selection.value = emptySet()
        }
    }

    /** Извлекает во временную папку cacheDir/share и просит UI поделиться файлом. */
    fun share(a: InstalledApp) {
        if (exportJob?.isActive == true) {
            _events.trySend(AppsEvent.Message("Дождитесь завершения текущего извлечения"))
            return
        }
        val dir = File(getApplication<Application>().cacheDir, "share")
        exportJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Чистим старые временные файлы (свежие могут ещё читаться получателем).
                    val border = System.currentTimeMillis() - 10 * 60_000L
                    dir.listFiles()?.forEach { if (it.lastModified() < border) it.delete() }
                }
                _progress.value = ExportProgress(a.label, 0, 1, 0f, sharing = true)
                val file = InstalledApps.export(a, dir) { f -> _progress.value = ExportProgress(a.label, 0, 1, f, sharing = true) }
                _events.send(AppsEvent.Share(file))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _events.send(AppsEvent.Message("Не удалось подготовить APK: ${e.message}"))
            } finally {
                _progress.value = null
            }
        }
    }

    fun cancelExport() {
        if (exportJob?.isActive == true) {
            exportJob?.cancel()
            _events.trySend(AppsEvent.Message("Извлечение отменено"))
        }
    }
}
