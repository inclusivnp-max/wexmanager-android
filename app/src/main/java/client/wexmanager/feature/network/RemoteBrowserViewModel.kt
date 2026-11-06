package client.wexmanager.feature.network

import android.app.Application
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.ClipboardContent
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.network.HostKeyChangedException
import client.wexmanager.core.network.KnownHosts
import client.wexmanager.core.network.RemoteConnection
import client.wexmanager.core.network.RemoteConnections
import client.wexmanager.core.network.RemoteErrors
import client.wexmanager.core.network.RemoteFileSystem
import client.wexmanager.core.network.RemotePath
import client.wexmanager.core.network.RemoteSessions
import client.wexmanager.core.network.SavedPasswordLostException
import client.wexmanager.core.network.causes
import client.wexmanager.core.network.closeQuietly
import client.wexmanager.core.network.isConnectionLost
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.core.util.OpenAction
import client.wexmanager.ui.navigation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
import java.io.IOException

@Immutable
sealed interface ConnectionStatus {
    data object Loading : ConnectionStatus
    data object Connecting : ConnectionStatus
    data object Ready : ConnectionStatus
    data object Missing : ConnectionStatus
    data class Failed(val message: String, val hostKey: HostKeyChangedException?) : ConnectionStatus
}

@Immutable
data class RemoteBrowserState(
    val connection: RemoteConnection? = null,
    val status: ConnectionStatus = ConnectionStatus.Loading,
    val path: String = "/",
    val items: List<FileItem> = emptyList(),
    val loading: Boolean = false,
    /** Длительная операция на сервере (удаление и т.п.). */
    val busy: String? = null,
    val selection: Set<String> = emptySet(),
    /** null — как в настройках. */
    val showHidden: Boolean? = null,
    /** Растёт при каждой смене содержимого папки — для сброса прокрутки. */
    val version: Long = 0,
    val backward: Boolean = false,
    /** Элемент, к которому прокрутить и подсветить (папка, из которой поднялись). */
    val focus: String? = null,
)

@Immutable
data class TransferUi(
    val upload: Boolean,
    val title: String,
    val fileName: String,
    val done: Long,
    val total: Long,
    val speed: Long,
    val index: Int,
    val count: Int,
    val queued: Int,
    val preparing: Boolean,
)

sealed interface RemoteEvent {
    data class Message(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null) : RemoteEvent
    data class Navigate(val route: Route) : RemoteEvent
    data class OpenExternal(val file: File) : RemoteEvent
    data class Reveal(val path: String) : RemoteEvent
}

/** Состояние файла на сервере: size < 0 / modified <= 0 — неизвестно. */
@Immutable
data class RemoteStamp(val size: Long, val modified: Long) {
    /** [now] == null — файла больше нет. Неизвестные поля не сравниваются. */
    fun sameAs(now: RemoteStamp?): Boolean {
        if (now == null) return false
        val sizeOk = size < 0 || now.size < 0 || size == now.size
        val timeOk = modified <= 0 || now.modified <= 0 || modified == now.modified
        return sizeOk && timeOk
    }
}

/**
 * Локальная копия, открытая в редакторе/другом приложении.
 * [remote] — состояние файла на сервере при скачивании (или после нашей загрузки); null — неизвестно.
 */
@Immutable
data class EditedCopy(val local: File, val remotePath: String, val lastModified: Long, val size: Long, val remote: RemoteStamp?)

@Immutable
data class UploadConflict(val names: List<String>, val transfer: Transfer)

class UriSource(val uri: Uri, val name: String, val size: Long)

sealed interface Transfer {
    data class Download(val items: List<FileItem>, val targetDir: File) : Transfer
    data class UploadFiles(val files: List<File>, val remoteDir: String) : Transfer
    data class UploadUris(val sources: List<UriSource>, val remoteDir: String) : Transfer
    data class Open(val item: FileItem) : Transfer
    /** [force] — пользователь согласился перезаписать изменившийся на сервере файл. */
    data class SyncBack(val local: File, val remotePath: String, val force: Boolean = false) : Transfer
}

/** Закрытие соединений после смерти viewModelScope. Ничего не держит. */
private val closeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun RemoteFileSystem.closeAsync() {
    closeScope.launch { closeQuietly() }
}

private val BadLocalChars = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

private fun safeLocalName(name: String): String = name.replace(BadLocalChars, "_").trim().ifEmpty { "_" }.take(200)

private const val UNSAFE_NAME = "Недопустимое имя файла на сервере"

/**
 * Локальный файл для удалённых имён [segments] в [parent] (по умолчанию — [root]): каждое имя
 * очищается, «.»/«..» запрещены, итоговый путь обязан остаться внутри [root]. Вызывать на IO.
 */
private fun localChild(root: File, segments: List<String>, parent: File = root): File {
    if (segments.isEmpty()) throw IOException(UNSAFE_NAME)
    var f = parent
    for (seg in segments) {
        val n = safeLocalName(seg)
        if (n == "." || n == "..") throw IOException(UNSAFE_NAME)
        f = File(f, n)
    }
    val base = root.canonicalPath
    val c = f.canonicalPath
    if (c != base && !c.startsWith(base + File.separator)) throw IOException(UNSAFE_NAME)
    return f
}

/** Итог передачи с ошибками отдельных файлов. */
private fun withFailures(head: String, failures: List<Pair<String, String>>): String {
    val shown = failures.take(2).joinToString("; ") { (name, err) -> "«$name» — $err" }
    val more = if (failures.size > 2) " и ещё ${failures.size - 2}" else ""
    return "$head. Ошибки: $shown$more"
}

class RemoteBrowserViewModel(app: Application, private val connectionId: String) : AndroidViewModel(app) {
    private val ctx: Application = app
    private val repo = RemoteConnections.get(app)
    private val container = app.appContainer

    private val _state = MutableStateFlow(RemoteBrowserState())
    val state: StateFlow<RemoteBrowserState> = _state.asStateFlow()

    private val _transfer = MutableStateFlow<TransferUi?>(null)
    val transfer: StateFlow<TransferUi?> = _transfer.asStateFlow()

    private val _uploadPrompt = MutableStateFlow<EditedCopy?>(null)
    val uploadPrompt: StateFlow<EditedCopy?> = _uploadPrompt.asStateFlow()

    private val _conflict = MutableStateFlow<UploadConflict?>(null)
    val conflict: StateFlow<UploadConflict?> = _conflict.asStateFlow()

    /** Файл на сервере изменился после скачивания — ждём решения пользователя. */
    private val _syncConflict = MutableStateFlow<Transfer.SyncBack?>(null)
    val syncConflict: StateFlow<Transfer.SyncBack?> = _syncConflict.asStateFlow()

    val clipboard: StateFlow<ClipboardContent?> = container.clipboard.content

    private val _events = Channel<RemoteEvent>(Channel.BUFFERED)
    val events: Flow<RemoteEvent> = _events.receiveAsFlow()

    private var listJob: Job? = null
    private var opJob: Job? = null
    private val scroll = HashMap<String, Pair<Int, Int>>()
    private val watches = LinkedHashMap<String, EditedCopy>()

    // Очередь передач: всё обращение — с главного потока.
    private val queue = ArrayDeque<Transfer>()
    private var worker: Job? = null
    private var current: Job? = null
    private var transferFs: RemoteFileSystem? = null

    /** Размер очереди для потока прогресса (сама очередь — только с главного потока). */
    @Volatile
    private var queued = 0

    private val cacheRoot = File(app.cacheDir, "remote/$connectionId")

    init {
        RemoteSessions.acquire(connectionId)
        connect()
        // Старые временные копии (старше суток) больше не нужны.
        viewModelScope.launch(Dispatchers.IO) {
            val limit = System.currentTimeMillis() - 24 * 3600_000L
            cacheRoot.walkBottomUp().forEach { f ->
                if (f != cacheRoot && (f.isFile && f.lastModified() < limit || f.isDirectory && f.list()?.isEmpty() == true)) f.delete()
            }
        }
    }

    private fun message(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _events.trySend(RemoteEvent.Message(text, actionLabel, action))
    }

    // ------------------------------------------------------------------ Подключение и навигация

    fun connect() {
        listJob?.cancel()
        listJob = viewModelScope.launch {
            val conn = repo.get(connectionId)
            if (conn == null) {
                _state.update { it.copy(status = ConnectionStatus.Missing) }
                return@launch
            }
            _state.update { it.copy(connection = conn, status = ConnectionStatus.Connecting, loading = false) }
            try {
                val wanted = conn.initialPath.takeIf { it.isNotBlank() }?.let(RemotePath::normalize)
                val (path, items, fellBack) = RemoteSessions.use(ctx, conn) { fs ->
                    val start = wanted ?: fs.home
                    try {
                        Triple(start, fs.list(start), false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        // Начальная папка недоступна — пробуем домашнюю.
                        if (wanted == null || wanted == fs.home || e.isConnectionLost()) throw e
                        Triple(fs.home, fs.list(fs.home), true)
                    }
                }
                _state.update {
                    it.copy(
                        status = ConnectionStatus.Ready,
                        path = path,
                        items = items,
                        selection = emptySet(),
                        version = it.version + 1,
                        backward = false,
                        focus = null,
                    )
                }
                if (fellBack) message("Начальная папка недоступна — открыта домашняя")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update {
                    it.copy(
                        status = ConnectionStatus.Failed(
                            RemoteErrors.message(e),
                            e.causes().firstNotNullOfOrNull { c -> c as? HostKeyChangedException },
                        ),
                    )
                }
            }
        }
    }

    fun trustHostKey(e: HostKeyChangedException) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { KnownHosts.accept(ctx, e) }
            connect()
        }
    }

    /** Открыть папку. [focus] — что подсветить после загрузки. */
    fun open(path: String, backward: Boolean = false, focus: String? = null) {
        val conn = _state.value.connection ?: return
        listJob?.cancel()
        _state.update { it.copy(loading = true) }
        listJob = viewModelScope.launch {
            try {
                val items = RemoteSessions.use(ctx, conn) { it.list(path) }
                _state.update { s ->
                    val same = s.path == path
                    s.copy(
                        path = path,
                        items = items,
                        loading = false,
                        status = ConnectionStatus.Ready,
                        selection = if (same) s.selection.filterTo(HashSet()) { k -> items.any { it.path == k } } else emptySet(),
                        version = if (same) s.version else s.version + 1,
                        backward = backward,
                        focus = focus,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false) }
                handleError(e)
            }
        }
    }

    /** Ошибка операции: обрыв, который не удалось восстановить, ведёт на экран ошибки. */
    private fun handleError(e: Throwable, prefix: String? = null) {
        val text = RemoteErrors.message(e)
        if (e.isConnectionLost() || e.causes().any { it is java.net.UnknownHostException || it is java.net.ConnectException }) {
            _state.update {
                it.copy(status = ConnectionStatus.Failed(text, e.causes().firstNotNullOfOrNull { c -> c as? HostKeyChangedException }))
            }
        } else {
            message(if (prefix != null) "$prefix: $text" else text)
        }
    }

    fun refresh() = open(_state.value.path)

    fun goUp(): Boolean {
        val s = _state.value
        val parent = RemotePath.parent(s.path) ?: return false
        open(parent, backward = true, focus = s.path)
        return true
    }

    fun onItemClick(item: FileItem) {
        val s = _state.value
        if (s.selection.isNotEmpty()) {
            toggleSelection(item)
            return
        }
        when {
            item.isDirectory -> open(item.path)
            item.isSymlink -> probeLink(item)
            else -> enqueue(Transfer.Open(item))
        }
    }

    /** Ссылка неизвестного типа: пробуем открыть как папку, иначе — как файл. */
    private fun probeLink(item: FileItem) {
        val conn = _state.value.connection ?: return
        listJob?.cancel()
        _state.update { it.copy(loading = true) }
        listJob = viewModelScope.launch {
            val items = try {
                RemoteSessions.use(ctx, conn) { it.list(item.path) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (items != null) {
                _state.update {
                    it.copy(path = item.path, items = items, loading = false, selection = emptySet(), version = it.version + 1, backward = false, focus = null)
                }
            } else {
                _state.update { it.copy(loading = false) }
                enqueue(Transfer.Open(item))
            }
        }
    }

    fun saveScroll(path: String, index: Int, offset: Int) {
        scroll[path] = index to offset
    }

    fun scrollFor(path: String): Pair<Int, Int>? = scroll[path]

    fun setShowHidden(show: Boolean) = _state.update { it.copy(showHidden = show) }

    // ------------------------------------------------------------------ Выделение

    fun toggleSelection(item: FileItem) = _state.update { s ->
        s.copy(selection = if (item.path in s.selection) s.selection - item.path else s.selection + item.path)
    }

    fun selectAll(visible: List<FileItem>) = _state.update { s -> s.copy(selection = visible.mapTo(HashSet()) { it.path }) }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun selectedItems(): List<FileItem> {
        val s = _state.value
        return s.items.filter { it.path in s.selection }
    }

    // ------------------------------------------------------------------ Операции на сервере

    private fun runOp(busy: String, errorPrefix: String, block: suspend (RemoteFileSystem) -> Unit, after: () -> Unit) {
        val conn = _state.value.connection ?: return
        if (opJob?.isActive == true) {
            message("Подождите завершения текущей операции")
            return
        }
        _state.update { it.copy(busy = busy) }
        opJob = viewModelScope.launch {
            try {
                // Удаление/переименование/создание неидемпотентны — без автоповтора после обрыва.
                RemoteSessions.use(ctx, conn, retryOnLost = false) { block(it) }
                after()
            } catch (e: CancellationException) {
                // Часть операции могла выполниться — показываем актуальное содержимое.
                refresh()
                throw e
            } catch (e: Throwable) {
                handleError(e, errorPrefix)
                refresh()
            } finally {
                _state.update { it.copy(busy = null) }
            }
        }
    }

    fun cancelOperation() {
        opJob?.cancel()
    }

    fun delete(items: List<FileItem>) {
        if (items.isEmpty()) return
        clearSelection()
        runOp(
            busy = if (items.size == 1) "Удаление «${items[0].name}»…" else "Удаление ${Format.items(items.size)}…",
            errorPrefix = "Не удалось удалить",
            block = { fs ->
                for (item in items) {
                    currentCoroutineContext().ensureActive()
                    fs.delete(item.path, item.isDirectory && !item.isSymlink)
                }
            },
            after = {
                message(if (items.size == 1) "«${items[0].name}» удалено" else "Удалено: ${Format.items(items.size)}")
                refresh()
            },
        )
    }

    /** Проверка имени; null — всё в порядке. */
    fun validateName(name: String, except: FileItem? = null): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Введите имя"
            n == "." || n == ".." -> "Недопустимое имя"
            '/' in n || '\u0000' in n -> "Имя не может содержать «/»"
            _state.value.items.any { it.name == n && it.path != except?.path } -> "Объект с таким именем уже существует"
            else -> null
        }
    }

    fun rename(item: FileItem, newName: String) {
        val name = newName.trim()
        if (name == item.name) return
        val parent = RemotePath.parent(item.path) ?: return
        val target = RemotePath.join(parent, name)
        clearSelection()
        runOp(
            busy = "Переименование…",
            errorPrefix = "Не удалось переименовать",
            block = { fs -> fs.rename(item.path, target) },
            after = { open(_state.value.path, focus = target) },
        )
    }

    fun mkdir(name: String) {
        val dir = _state.value.path
        val target = RemotePath.join(dir, name.trim())
        runOp(
            busy = "Создание папки…",
            errorPrefix = "Не удалось создать папку",
            block = { fs -> fs.mkdir(target) },
            after = { open(dir, focus = target) },
        )
    }

    // ------------------------------------------------------------------ Передачи

    fun downloadDir(): File {
        val name = safeLocalName(_state.value.connection?.title ?: "Сервер")
        return File(container.storage.primaryPath, "Download/WexManager/$name")
    }

    fun download(items: List<FileItem>) {
        if (items.isEmpty()) return
        clearSelection()
        enqueue(Transfer.Download(items, downloadDir()))
    }

    /** Загрузить файлы из буфера обмена приложения в текущую папку. */
    fun uploadClipboard() {
        val files = clipboard.value?.items.orEmpty().filter { !it.isInArchive }.map { File(it.path) }
        if (files.isEmpty()) {
            message("В буфере нет файлов для загрузки")
            return
        }
        requestUpload(Transfer.UploadFiles(files, _state.value.path), files.map { it.name })
    }

    fun uploadUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val dir = _state.value.path
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) {
                uris.map { uri ->
                    var name: String? = null
                    var size = -1L
                    runCatching {
                        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                name = c.getString(0)
                                if (!c.isNull(1)) size = c.getLong(1)
                            }
                        }
                    }
                    UriSource(uri, (name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file").replace('/', '_'), size)
                }
            }
            requestUpload(Transfer.UploadUris(sources, dir), sources.map { it.name })
        }
    }

    private fun requestUpload(t: Transfer, names: List<String>) {
        val s = _state.value
        val existing = s.items.mapTo(HashSet()) { it.name }
        val dir = when (t) {
            is Transfer.UploadFiles -> t.remoteDir
            is Transfer.UploadUris -> t.remoteDir
            else -> null
        }
        val conflicts = if (dir == s.path) names.filter { it in existing } else emptyList()
        if (conflicts.isNotEmpty()) _conflict.value = UploadConflict(conflicts, t) else enqueue(t)
    }

    fun resolveConflict(replace: Boolean) {
        val c = _conflict.value ?: return
        _conflict.value = null
        if (replace) enqueue(c.transfer)
    }

    fun cancelTransfers() {
        queue.clear()
        queued = 0
        current?.cancel()
    }

    private fun enqueue(t: Transfer) {
        queue.addLast(t)
        queued = queue.size
        _transfer.update { it?.copy(queued = queue.size) }
        if (worker?.isActive != true) worker = viewModelScope.launch { runQueue() }
    }

    private suspend fun runQueue() {
        try {
            while (queue.isNotEmpty()) {
                val t = queue.removeFirst()
                queued = queue.size
                val job = viewModelScope.launch { runTransfer(t) }
                current = job
                job.join()
                current = null
            }
        } finally {
            current = null
            transferFs?.closeAsync()
            transferFs = null
            _transfer.value = null
        }
    }

    private suspend fun transferFs(): RemoteFileSystem {
        transferFs?.takeIf { it.isAlive }?.let { return it }
        transferFs?.closeAsync()
        transferFs = null
        val conn = _state.value.connection ?: throw IOException("Подключение не загружено")
        return RemoteSessions.openDedicated(ctx, conn).also { transferFs = it }
    }

    private suspend fun runTransfer(t: Transfer) {
        val upload = t is Transfer.UploadFiles || t is Transfer.UploadUris || t is Transfer.SyncBack
        val title = when (t) {
            is Transfer.Download -> "Скачивание"
            is Transfer.Open -> "Открытие"
            is Transfer.SyncBack -> "Загрузка изменений"
            else -> "Загрузка"
        }
        val progress = Progress(title, upload)
        progress.preparing(firstName(t))
        var fs: RemoteFileSystem? = null
        // Соединение получено: дальнейшие ошибки — уже не «не удалось подключиться».
        var connected = false
        suspend fun execute(target: RemoteFileSystem) {
            connected = true
            when (t) {
                is Transfer.Download -> runDownload(target, t, progress)
                is Transfer.Open -> runOpen(target, t.item, progress)
                is Transfer.UploadFiles -> runUploadFiles(target, t, progress)
                is Transfer.UploadUris -> runUploadUris(target, t, progress)
                is Transfer.SyncBack -> runSyncBack(target, t, progress)
            }
        }
        try {
            val dedicated = try {
                transferFs()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Сервер может не пускать второе соединение (лимит FTP) — передаём через общее.
                val conn = _state.value.connection ?: throw e
                if (e.causes().any { it is HostKeyChangedException || it is SavedPasswordLostException || it is java.net.UnknownHostException }) throw e
                // Автоповтор после обрыва безопасен только для открытия: копия в кеше просто перезапишется.
                // Скачивание создаёт «имя (1)», загрузка меняет сервер — их не повторяем.
                RemoteSessions.use(ctx, conn, retryOnLost = t is Transfer.Open) { shared -> execute(shared) }
                return
            }
            fs = dedicated
            execute(dedicated)
        } catch (e: CancellationException) {
            // Передача прервана посреди потока — соединение в неопределённом состоянии.
            fs?.closeAsync()
            if (transferFs === fs) transferFs = null
            message("$title отменено")
            throw e
        } catch (e: Throwable) {
            if (fs != null && (!fs.isAlive || e.isConnectionLost())) {
                fs.closeAsync()
                if (transferFs === fs) transferFs = null
            }
            var text = "$title: ошибка — ${RemoteErrors.message(e)}"
            // Не удалось подключиться — остальное в очереди тоже не выйдет. Ошибка файла очередь не трогает.
            if (!connected && queue.isNotEmpty()) {
                text += ". Остальные передачи отменены"
                queue.clear()
                queued = 0
            }
            message(text, "ОК")
        }
    }

    /**
     * Ошибка одного файла не прерывает передачу: запоминается для итогового сообщения.
     * Обрыв связи (или «сломанное» соединение) прерывает — остальное всё равно не выйдет.
     */
    private inline fun eachFile(fs: RemoteFileSystem, name: String, failures: MutableList<Pair<String, String>>, block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e.isConnectionLost() || !fs.isAlive) throw e
            failures += name to RemoteErrors.message(e)
            false
        }

    private fun firstName(t: Transfer): String = when (t) {
        is Transfer.Download -> t.items.firstOrNull()?.name.orEmpty()
        is Transfer.Open -> t.item.name
        is Transfer.UploadFiles -> t.files.firstOrNull()?.name.orEmpty()
        is Transfer.UploadUris -> t.sources.firstOrNull()?.name.orEmpty()
        is Transfer.SyncBack -> t.local.name
    }

    /** Прогресс с плавной скоростью; обновления — из IO-потока (StateFlow потокобезопасен). */
    private inner class Progress(private val title: String, private val upload: Boolean) {
        private var count = 1
        private var totalBytes = -1L
        private var index = 0
        private var name = ""
        private var base = 0L
        private var speed = 0.0
        private var sampleTime = 0L
        private var sampleBytes = 0L

        fun preparing(first: String) {
            _transfer.value = TransferUi(upload, title, first, 0, -1, 0, 0, 0, queued, preparing = true)
        }

        fun plan(files: Int, bytes: Long) {
            count = files.coerceAtLeast(1)
            totalBytes = bytes
            sampleTime = System.nanoTime()
            sampleBytes = 0
        }

        fun startFile(i: Int, fileName: String) {
            index = i
            name = fileName
            emit(0, -1)
        }

        fun finishFile(size: Long) {
            base += size.coerceAtLeast(0)
        }

        fun onBytes(done: Long, total: Long) = emit(done, total)

        private fun emit(done: Long, total: Long) {
            val all = base + done
            val now = System.nanoTime()
            val dt = now - sampleTime
            if (dt > 400_000_000L) {
                val inst = (all - sampleBytes) * 1_000_000_000.0 / dt
                speed = if (speed <= 0.0) inst else speed * 0.6 + inst * 0.4
                sampleTime = now
                sampleBytes = all
            }
            val multi = count > 1 && totalBytes > 0
            _transfer.value = TransferUi(
                upload = upload,
                title = title,
                fileName = name,
                done = if (multi) all else done,
                total = if (multi) totalBytes else total,
                speed = speed.toLong().coerceAtLeast(0),
                index = index + 1,
                count = count,
                queued = queued,
                preparing = false,
            )
        }
    }

    /**
     * Скачать во временный файл и переименовать. [overwrite] = false — занятое имя не трогаем,
     * берём «имя (1)». Возвращает итоговый файл.
     */
    private suspend fun downloadAtomic(
        fs: RemoteFileSystem,
        remote: String,
        local: File,
        overwrite: Boolean,
        onProgress: (Long, Long) -> Unit,
    ): File {
        val dir = local.parentFile ?: throw IOException("Нет папки для «${local.name}»")
        val tmp = File(dir, ".${local.name}.wexpart")
        try {
            withContext(Dispatchers.IO) { dir.mkdirs() }
            fs.download(remote, tmp, onProgress)
            return withContext(Dispatchers.IO) {
                val target = if (!overwrite && local.exists()) File(dir, LocalFileSystem.uniqueName(dir.path, local.name)) else local
                // rename поверх существующего атомарен; удаляем, только если он не удался.
                if (!tmp.renameTo(target)) {
                    if (!overwrite || (target.exists() && !target.delete()) || !tmp.renameTo(target)) {
                        throw IOException("Не удалось сохранить «${target.name}»")
                    }
                }
                target
            }
        } catch (e: Throwable) {
            withContext(Dispatchers.IO + NonCancellable) { tmp.delete() }
            throw e
        }
    }

    private suspend fun runDownload(fs: RemoteFileSystem, t: Transfer.Download, progress: Progress) {
        val plan = ArrayList<Triple<FileItem, File, Long>>()
        val dirs = ArrayList<File>()
        val failures = ArrayList<Pair<String, String>>()
        val root = t.targetDir
        suspend fun walk(item: FileItem, local: File, depth: Int) {
            currentCoroutineContext().ensureActive()
            if (item.isDirectory) {
                dirs += local
                // Внутрь ссылок на папки не заходим (кроме явно выбранных) — защита от циклов.
                if (depth > 0 && item.isSymlink || depth > 64) return
                val children = ArrayList<FileItem>()
                eachFile(fs, item.name, failures) { children += fs.list(item.path) }
                for (c in children) {
                    // Имя от сервера — только безопасный сегмент внутри папки загрузки.
                    val child = try {
                        localChild(root, listOf(c.name), parent = local)
                    } catch (e: IOException) {
                        failures += c.name to (e.message ?: UNSAFE_NAME)
                        continue
                    }
                    walk(c, child, depth + 1)
                }
            } else {
                plan += Triple(item, local, item.size)
            }
        }
        // Верхний уровень: занятые имена не перезаписываем — «имя (1)».
        val tops = HashMap<FileItem, File>()
        withContext(Dispatchers.IO) {
            root.mkdirs()
            if (!root.isDirectory) throw IOException("Нет доступа к папке загрузок")
            for (item in t.items) {
                val local = try {
                    localChild(root, listOf(item.name))
                } catch (e: IOException) {
                    failures += item.name to (e.message ?: UNSAFE_NAME)
                    continue
                }
                val top = if (item.isDirectory && local.exists()) File(root, LocalFileSystem.uniqueName(root.path, local.name)) else local
                tops[item] = top
                walk(item, top, 0)
            }
            dirs.forEach { it.mkdirs() }
        }
        progress.plan(plan.size, plan.sumOf { it.third.coerceAtLeast(0) })
        val saved = ArrayList<File>()
        plan.forEachIndexed { i, (item, local, size) ->
            currentCoroutineContext().ensureActive()
            progress.startFile(i, local.name)
            eachFile(fs, item.name, failures) { saved += downloadAtomic(fs, item.path, local, overwrite = false, progress::onBytes) }
            progress.finishFile(size)
        }
        val paths = saved.map { it.absolutePath }.toTypedArray()
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(ctx, paths, null, null)
        val single = t.items.singleOrNull()
        val revealPath = when {
            single == null -> root.absolutePath
            !single.isDirectory -> saved.singleOrNull()?.absolutePath ?: root.absolutePath
            else -> tops[single]?.absolutePath ?: root.absolutePath
        }
        val head = when {
            failures.isNotEmpty() -> "Скачано ${saved.size} из ${Format.files(plan.size)}"
            plan.isEmpty() -> "Скачано: пустые папки"
            plan.size == 1 && single != null && !single.isDirectory -> "«${saved.firstOrNull()?.name ?: single.name}» скачан"
            else -> "Скачано: ${Format.files(plan.size)}"
        }
        if (failures.isEmpty()) {
            message(head, "Показать") { _events.trySend(RemoteEvent.Reveal(revealPath)) }
        } else if (saved.isEmpty()) {
            message(withFailures("Ничего не скачано", failures), "ОК")
        } else {
            message(withFailures(head, failures), "Показать") { _events.trySend(RemoteEvent.Reveal(revealPath)) }
        }
    }

    /** Состояние файла на сервере; при ошибке — «неизвестно», null — файла нет. */
    private suspend fun remoteStamp(fs: RemoteFileSystem, path: String): RemoteStamp? = try {
        fs.stat(path)?.let { RemoteStamp(it.size, it.lastModified) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        RemoteStamp(-1, 0)
    }

    private suspend fun runOpen(fs: RemoteFileSystem, item: FileItem, progress: Progress) {
        // Кеш повторяет удалённый путь — только из безопасных сегментов и строго внутри cacheRoot.
        val local = withContext(Dispatchers.IO) { localChild(cacheRoot, RemotePath.segments(item.path)) }
        progress.plan(1, item.size)
        progress.startFile(0, item.name)
        downloadAtomic(fs, item.path, local, overwrite = true, progress::onBytes)
        val remote = remoteStamp(fs, item.path)?.takeIf { it.size >= 0 || it.modified > 0 }
        val (mtime, size) = withContext(Dispatchers.IO) { local.lastModified() to local.length() }
        watches[local.absolutePath] = EditedCopy(local, item.path, mtime, size, remote)
        val settings = container.settings.settings.value
        val action = withContext(Dispatchers.IO) { FileOpener.actionFor(LocalFileSystem.stat(local.absolutePath), settings) }
        when (action) {
            is OpenAction.Navigate -> {
                val route = action.route.let { r -> if (r is Route.ImageViewer) r.copy(siblings = false) else r }
                _events.trySend(RemoteEvent.Navigate(route))
            }
            is OpenAction.External -> _events.trySend(RemoteEvent.OpenExternal(File(action.path)))
            else -> {
                val text = withContext(Dispatchers.IO) { FileOpener.looksLikeText(local) }
                _events.trySend(RemoteEvent.Navigate(if (text) Route.Editor(local.absolutePath) else Route.HexViewer(local.absolutePath)))
            }
        }
    }

    private suspend fun runUploadFiles(fs: RemoteFileSystem, t: Transfer.UploadFiles, progress: Progress) {
        val plan = ArrayList<Pair<File, String>>() // локальный файл → удалённая папка
        val dirs = ArrayList<String>()
        withContext(Dispatchers.IO) {
            fun walk(f: File, remoteParent: String, depth: Int) {
                if (f.isDirectory) {
                    val dir = RemotePath.join(remoteParent, f.name)
                    dirs += dir
                    if (depth > 64) return
                    f.listFiles()?.sortedBy { it.name }?.forEach { walk(it, dir, depth + 1) }
                } else if (f.isFile) {
                    plan += f to remoteParent
                }
            }
            t.files.forEach { walk(it, t.remoteDir, 0) }
        }
        if (plan.isEmpty() && dirs.isEmpty()) throw IOException("Нет доступа к выбранным файлам")
        val failures = ArrayList<Pair<String, String>>()
        for (d in dirs) {
            currentCoroutineContext().ensureActive()
            eachFile(fs, RemotePath.name(d), failures) { fs.mkdir(d) }
        }
        progress.plan(plan.size, plan.sumOf { it.first.length() })
        var sent = 0
        plan.forEachIndexed { i, (file, dir) ->
            currentCoroutineContext().ensureActive()
            progress.startFile(i, file.name)
            if (eachFile(fs, file.name, failures) { fs.upload(file, dir, progress::onBytes) }) sent++
            progress.finishFile(file.length())
        }
        val head = when {
            failures.isNotEmpty() -> "Загружено $sent из ${Format.files(plan.size)}"
            plan.size == 1 && t.files.size == 1 -> "«${plan[0].first.name}» загружен"
            else -> "Загружено: ${Format.files(plan.size)}"
        }
        afterUpload(t.remoteDir, if (failures.isEmpty()) head else withFailures(head, failures), failures.isNotEmpty())
    }

    private suspend fun runUploadUris(fs: RemoteFileSystem, t: Transfer.UploadUris, progress: Progress) {
        val sources = t.sources
        val failures = ArrayList<Pair<String, String>>()
        progress.plan(sources.size, if (sources.all { it.size >= 0 }) sources.sumOf { it.size } else -1)
        var ok = 0
        sources.forEachIndexed { i, src ->
            currentCoroutineContext().ensureActive()
            progress.startFile(i, src.name)
            var sent = 0L
            val done = eachFile(fs, src.name, failures) {
                val input = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(src.uri) }
                    ?: throw IOException("Не удалось открыть «${src.name}»")
                input.use { fs.uploadStream(it, src.size, RemotePath.join(t.remoteDir, src.name)) { d, total -> sent = d; progress.onBytes(d, total) } }
            }
            if (done) ok++
            progress.finishFile(if (src.size >= 0) src.size else sent)
        }
        val head = when {
            failures.isNotEmpty() -> "Загружено $ok из ${Format.files(sources.size)}"
            sources.size == 1 -> "«${sources[0].name}» загружен"
            else -> "Загружено: ${Format.files(sources.size)}"
        }
        afterUpload(t.remoteDir, if (failures.isEmpty()) head else withFailures(head, failures), failures.isNotEmpty())
    }

    private suspend fun runSyncBack(fs: RemoteFileSystem, t: Transfer.SyncBack, progress: Progress) {
        val dir = RemotePath.parent(t.remotePath) ?: "/"
        val name = RemotePath.name(t.remotePath)
        val key = t.local.absolutePath
        // Кто-то изменил файл на сервере после скачивания — без согласия не затираем.
        val baseline = watches[key]?.remote
        if (!t.force && baseline != null && !baseline.sameAs(remoteStamp(fs, t.remotePath))) {
            _syncConflict.value = t
            return
        }
        val size = withContext(Dispatchers.IO) { t.local.length() }
        progress.plan(1, size)
        progress.startFile(0, name)
        // Точно в исходный путь: локальное имя могло быть очищено от недопустимых символов.
        val input = withContext(Dispatchers.IO) { t.local.inputStream() }
        input.use { fs.uploadStream(it, size, t.remotePath, progress::onBytes) }
        // Новая точка отсчёта — только что загруженная версия.
        val stamp = remoteStamp(fs, t.remotePath)?.takeIf { it.size >= 0 || it.modified > 0 }
        watches[key]?.let { watches[key] = it.copy(remote = stamp) }
        afterUpload(dir, "Изменения «$name» загружены на сервер")
    }

    private fun afterUpload(dir: String, text: String, long: Boolean = false) {
        if (_state.value.path == dir && _state.value.status == ConnectionStatus.Ready) refresh()
        if (long) message(text, "ОК") else message(text)
    }

    // ------------------------------------------------------------------ Изменённые копии

    /** Вызывается при возврате на экран: копия могла быть изменена в редакторе. */
    fun checkEditedCopies() {
        if (_uploadPrompt.value != null || watches.isEmpty()) return
        val snapshot = watches.values.toList()
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) {
                snapshot.firstOrNull { it.local.exists() && (it.local.lastModified() != it.lastModified || it.local.length() != it.size) }
            }
            if (changed != null && _uploadPrompt.value == null) _uploadPrompt.value = changed
        }
    }

    fun resolveSyncConflict(overwrite: Boolean) {
        val t = _syncConflict.value ?: return
        _syncConflict.value = null
        if (overwrite) enqueue(t.copy(force = true)) else message("Изменения не загружены — копия осталась на устройстве")
    }

    fun answerUploadPrompt(upload: Boolean) {
        val copy = _uploadPrompt.value ?: return
        _uploadPrompt.value = null
        viewModelScope.launch {
            val (mtime, size) = withContext(Dispatchers.IO) { copy.local.lastModified() to copy.local.length() }
            val key = copy.local.absolutePath
            watches[key] = (watches[key] ?: copy).copy(lastModified = mtime, size = size)
            if (upload) enqueue(Transfer.SyncBack(copy.local, copy.remotePath))
            checkEditedCopies()
        }
    }

    override fun onCleared() {
        queue.clear()
        current?.cancel()
        transferFs?.closeAsync()
        transferFs = null
        RemoteSessions.release(connectionId)
        super.onCleared()
    }
}
