package client.wexmanager.feature.browser

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.apk.AxmlFiles
import client.wexmanager.core.dex.ClassFiles
import client.wexmanager.core.archive.ArchiveEngine
import client.wexmanager.core.archive.ArchiveFormat
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.fs.Location
import client.wexmanager.core.ops.TaskEvent
import client.wexmanager.core.ops.TaskKind
import client.wexmanager.core.ops.TaskStatus
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.core.util.OpenAction
import client.wexmanager.core.util.OpenAs
import client.wexmanager.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface BrowserEvent {
    data class Message(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null) : BrowserEvent
    data class Open(val action: OpenAction) : BrowserEvent
    data class Share(val files: List<File>) : BrowserEvent
}

enum class ExtractTarget { Here, Subfolder, OtherPanel }

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer

    val settings = c.settings.settings
    val panels: List<PanelController> = List(2) { PanelController(it, viewModelScope, settings, c.storage) }

    private val _active = MutableStateFlow(0)
    val active: StateFlow<Int> = _active.asStateFlow()

    val clipboard = c.clipboard.content
    val tasks = c.taskManager.tasks
    val userData = c.userData.data
    val storage = c.storage
    val iconPacks = c.iconPacks.packs

    private val _events = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val activePanel: PanelController get() = panels[_active.value]
    val otherPanel: PanelController get() = panels[1 - _active.value]

    init {
        // Сообщения закрывшихся экранов (например, «подписать APK» после выхода из редактора).
        viewModelScope.launch {
            c.appMessages.collect { m ->
                message(m.text, m.actionLabel, m.action)
                @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
                c.appMessages.resetReplayCache()
            }
        }
        viewModelScope.launch {
            val s = c.settings.awaitLoaded()
            val u = c.userData.awaitLoaded()
            FileOpener.openWith = u.openWith // до первого открытия файла, не дожидаясь асинхронной подписки
            val primary = c.storage.primaryPath
            fun valid(p: String?) = p?.takeIf { s.rememberLastPaths && File(it).isDirectory }
            fun home(p: String?) = p?.takeIf { File(it).isDirectory }
            panels[0].open(Location.Local(valid(u.leftPath) ?: home(u.homeLeft) ?: primary), addToHistory = false)
            panels[1].open(Location.Local(valid(u.rightPath) ?: home(u.homeRight) ?: primary), addToHistory = false)
            _active.value = u.activePanel.coerceIn(0, 1)
            _ready.value = true
            // Запросы других экранов обрабатываем только после восстановления панелей.
            c.browserRequests.collect { req ->
                c.browserRequests.resetReplayCache()
                when (req) {
                    is client.wexmanager.BrowserRequest.Reveal -> revealPath(req.path)
                    is client.wexmanager.BrowserRequest.OpenArchive -> activePanel.open(Location.Archive(req.path))
                    client.wexmanager.BrowserRequest.Refresh -> panels.forEach { it.refresh() }
                }
            }
        }
        viewModelScope.launch {
            c.taskManager.events.collect { if (it is TaskEvent.Finished) onTaskFinished(it) }
        }
        viewModelScope.launch {
            combine(
                panels[0].state.map { it.location }.distinctUntilChanged(),
                panels[1].state.map { it.location }.distinctUntilChanged(),
                _active,
            ) { l, r, a -> Triple(l, r, a) }
                .debounce(600)
                .collect { (l, r, a) ->
                    if (!_ready.value) return@collect
                    c.userData.savePanels((l as? Location.Local)?.path, (r as? Location.Local)?.path, a)
                }
        }
    }

    override fun onCleared() {
        panels.forEach { it.dispose() }
    }

    private fun message(text: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        _events.trySend(BrowserEvent.Message(text, actionLabel, action))
    }

    private fun onTaskFinished(ev: TaskEvent.Finished) {
        val t = ev.task
        for (p in panels) {
            when (val loc = p.location) {
                is Location.Local -> if (loc.path in ev.touchedDirs || ev.touchedDirs.any { it.startsWith(loc.path + "/") && it.count { ch -> ch == '/' } == loc.path.count { ch -> ch == '/' } + 1 }) p.refresh()
                is Location.Archive -> if (File(loc.archivePath).parent in ev.touchedDirs) {
                    ArchiveEngine.invalidate(File(loc.archivePath))
                    p.refresh()
                }
            }
        }
        when (t.status) {
            TaskStatus.Done -> {
                val count = if (t.totalItems > 0) " (${Format.items(t.totalItems)})" else ""
                val result = t.resultPath
                if (result != null && (t.kind == TaskKind.Compress || t.kind == TaskKind.Extract || t.kind == TaskKind.Sign)) {
                    message("${t.kind.noun}$count", "Показать") { revealPath(result) }
                } else if (t.kind == TaskKind.Trash && t.trashed.isNotEmpty()) {
                    // Случайно удалили — вернуть одним касанием.
                    message("${t.kind.noun}$count", "Отменить") { c.taskManager.restoreFromTrash(t.trashed) }
                } else message("${t.kind.noun}$count")
            }
            // Часть файлов уже в корзине, даже если задача прервалась, — их тоже можно вернуть.
            TaskStatus.Failed -> if (!t.needsPassword) {
                if (t.kind == TaskKind.Trash && t.trashed.isNotEmpty()) {
                    message("${t.kind.verb}: ${t.error ?: "ошибка"}", "Вернуть ${t.trashed.size}") { c.taskManager.restoreFromTrash(t.trashed) }
                } else message("${t.kind.verb}: ${t.error ?: "ошибка"}")
            }
            TaskStatus.Cancelled -> if (t.kind == TaskKind.Trash && t.trashed.isNotEmpty()) {
                message("${t.kind.verb} отменено", "Вернуть ${t.trashed.size}") { c.taskManager.restoreFromTrash(t.trashed) }
            } else message("${t.kind.verb} отменено")
            else -> Unit
        }
    }

    private fun revealPath(path: String) {
        val rootShell = client.wexmanager.core.fs.RootShell
        if (rootShell.enabled && !File(path).exists()) {
            // Закрытый путь: папка это или файл, знает только root.
            val panel = activePanel
            viewModelScope.launch {
                val dir = runCatching { rootShell.run("[ -d ${rootShell.quote(path)} ]").code == 0 }.getOrDefault(false)
                if (dir) panel.open(Location.Local(path)) else panel.open(Location.Local(File(path).parent ?: "/"), focusKey = path)
            }
            return
        }
        val f = runCatching { File(path).canonicalFile }.getOrDefault(File(path))
        if (f.isDirectory) activePanel.open(Location.Local(path))
        else activePanel.open(Location.Local(f.parent ?: return), focusKey = f.path)
    }

    // ------------------------------------------------------------------ Панели

    fun setActive(index: Int) {
        if (index in 0..1 && _active.value != index) _active.value = index
    }

    fun swapPanels() {
        val l = panels[0].location
        val r = panels[1].location
        panels[0].open(r)
        panels[1].open(l)
    }

    /** «Синхронизация» MT: вторая панель открывает ту же папку. */
    fun syncOther() {
        otherPanel.open(activePanel.location)
    }

    fun openInOther(item: FileItem) {
        if (!item.isDirectory) return
        val loc = if (item.isInArchive) Location.Archive(item.archivePath!!, item.path) else Location.Local(item.path)
        otherPanel.open(loc)
        setActive(otherPanel.index)
    }

    fun goTo(path: String): Boolean {
        val trimmed = path.trim().trimEnd('/').ifEmpty { "/" }
        val f = runCatching { File(trimmed).canonicalFile }.getOrDefault(File(trimmed))
        return when {
            f.isDirectory -> { activePanel.open(Location.Local(f.absolutePath)); c.userData.addPathHistory(f.absolutePath); true }
            f.isFile -> { activePanel.open(Location.Local(f.parent ?: "/"), focusKey = f.absolutePath); true }
            // Закрытая папка: проверить её существование может только root — пусть разберётся листинг.
            client.wexmanager.core.fs.RootShell.enabled -> { activePanel.open(Location.Local(trimmed)); c.userData.addPathHistory(trimmed); true }
            else -> { message("Путь не найден: $trimmed"); false }
        }
    }

    /** Домашняя папка активной панели (или основная память). */
    fun goHome() {
        val u = c.userData.data.value
        val home = (if (_active.value == 0) u.homeLeft else u.homeRight)?.takeIf { File(it).isDirectory } ?: c.storage.primaryPath
        activePanel.open(Location.Local(home))
    }

    fun setHomeHere() {
        val loc = activePanel.location as? Location.Local ?: run { message("Домашней может быть только обычная папка"); return }
        c.userData.setHome(_active.value, loc.path)
        message("Домашняя папка панели ${_active.value + 1}: ${loc.title.ifEmpty { "/" }}")
    }

    fun openLocation(path: String) {
        activePanel.open(Location.Local(path))
    }

    // ---------------------------------------------------------------- Открытие

    fun onItemClick(panel: PanelController, item: FileItem) {
        setActive(panel.index)
        if (panel.state.value.selectionMode) {
            panel.toggleSelection(item)
            return
        }
        open(panel, item)
    }

    fun open(panel: PanelController, item: FileItem) {
        val loc = panel.location
        if (item.isInArchive && loc is Location.Archive) {
            if (item.isDirectory) {
                panel.open(loc.child(item.name))
            } else if (item.name.endsWith(".dex", ignoreCase = true) &&
                loc.archivePath.substringAfterLast('.').lowercase() in setOf("apk", "zip", "jar", "aab", "apex")
            ) {
                // DEX из APK — сразу в DEX-редактор: сохранение пойдёт обратно в архив, а не во временную копию.
                dispatch(panel, OpenAction.Navigate(Route.DexEditor(loc.archivePath, listOf(item.path))))
            } else if (item.path == "resources.arsc" && loc.archivePath.substringAfterLast('.').lowercase() == "apk") {
                dispatch(panel, OpenAction.Navigate(Route.ArscEditor(loc.archivePath)))
            } else {
                viewModelScope.launch {
                    val extracted = runCatching {
                        ArchiveEngine.extractToCache(File(loc.archivePath), item.path, getApplication<Application>().cacheDir)
                    }.getOrElse {
                        message("Не удалось извлечь: ${it.message}")
                        return@launch
                    }
                    // Бинарный XML из APK (AndroidManifest.xml, res/*.xml) — открываем расшифровку для правки.
                    if (withContext(Dispatchers.IO) { AxmlFiles.isBinaryXml(extracted) }) {
                        val text = runCatching { AxmlFiles.decodeToText(getApplication(), extracted) }
                            .onFailure { message("${it.message ?: "Не удалось расшифровать XML"} — открываю как есть") }
                            .getOrNull()
                        if (text != null) {
                            dispatch(panel, archiveAware(OpenAction.Navigate(Route.Editor(text.path, binarySource = extracted.path)), loc.archivePath, item.path))
                            return@launch
                        }
                    }
                    // JVM .class из jar/apk — csmali, сохранение собирает класс и кладёт обратно в архив.
                    if (item.extension == "class" && FileOpener.rememberedMode("class") == null && withContext(Dispatchers.IO) { ClassFiles.isClass(extracted) }) {
                        val text = runCatching { ClassFiles.decodeToText(getApplication(), extracted) }
                            .onFailure { message("${it.message} — открываю как есть") }
                            .getOrNull()
                        if (text != null) {
                            dispatch(panel, archiveAware(OpenAction.Navigate(Route.Editor(text.path, binarySource = extracted.path)), loc.archivePath, item.path))
                            return@launch
                        }
                    }
                    val action = FileOpener.actionFor(LocalFileSystem.stat(extracted.path), settings.value)
                    dispatch(panel, archiveAware(action, loc.archivePath, item.path))
                }
            }
            return
        }
        if (!item.isDirectory && !item.isInArchive && client.wexmanager.core.fs.SafAccess.needsSaf(getApplication(), item.path)) {
            val panelRef = panel
            viewModelScope.launch {
                val readable = withContext(Dispatchers.IO) { File(item.path).canRead() }
                if (readable) { openPlain(panelRef, item); return@launch }
                if (item.size > 256L * 1024 * 1024) {
                    message("Файл больше 256 МБ — откройте его копией в другую папку")
                    return@launch
                }
                val copy = File(getApplication<Application>().cacheDir, "saf/${java.util.UUID.randomUUID().toString().take(12)}/${item.name}")
                runCatching {
                    withContext(Dispatchers.IO) { client.wexmanager.core.fs.SafAccess.copyTreeBlocking(getApplication(), item.path, copy) }
                }.onFailure {
                    message(it.message ?: "Не удалось прочитать файл")
                    return@launch
                }
                val action = withContext(Dispatchers.IO) { FileOpener.actionFor(LocalFileSystem.stat(copy.path), settings.value) }
                val route = (action as? OpenAction.Navigate)?.route
                dispatch(
                    panelRef,
                    when (route) {
                        is Route.Editor -> OpenAction.Navigate(route.copy(readOnly = true, lockReadOnly = true))
                        is Route.HexViewer -> OpenAction.Navigate(route.copy(readOnly = true))
                        null -> OpenAction.Navigate(Route.HexViewer(copy.path, readOnly = true))
                        else -> action
                    },
                )
                message("Копия из закрытой папки — только просмотр")
            }
            return
        }
        if (!item.isDirectory && client.wexmanager.core.fs.RootShell.enabled && !item.isInArchive) {
            // Файл из закрытой папки: копия через root в кеш, открываем её только для чтения.
            val panelRef = panel
            viewModelScope.launch {
                val readable = withContext(Dispatchers.IO) { File(item.path).canRead() }
                if (readable) { openPlain(panelRef, item); return@launch }
                // Только обычные файлы разумного размера: устройства и каналы читались бы бесконечно.
                val regular = item.mode >= 0 && (item.mode and 0xF000) == 0x8000
                if (!regular || item.size > 256L * 1024 * 1024) {
                    message(if (!regular) "Это не обычный файл — открыть нельзя" else "Файл больше 256 МБ — открыть через root нельзя")
                    return@launch
                }
                val free = withContext(Dispatchers.IO) { getApplication<Application>().cacheDir.usableSpace }
                if (free < item.size + 64L * 1024 * 1024) {
                    message("Недостаточно места для копии файла")
                    return@launch
                }
                val copy = File(getApplication<Application>().cacheDir, "root/${java.util.UUID.randomUUID().toString().take(12)}/${item.name}")
                runCatching { client.wexmanager.core.fs.RootShell.readTo(item.path, copy) }.onFailure {
                    message(it.message ?: "Не удалось прочитать файл через root")
                    return@launch
                }
                val action = withContext(Dispatchers.IO) { FileOpener.actionFor(LocalFileSystem.stat(copy.path), settings.value) }
                val route = (action as? OpenAction.Navigate)?.route
                dispatch(
                    panelRef,
                    when (route) {
                        is Route.Editor -> OpenAction.Navigate(route.copy(readOnly = true, lockReadOnly = true))
                        is Route.HexViewer -> OpenAction.Navigate(route.copy(readOnly = true))
                        null -> OpenAction.Navigate(Route.HexViewer(copy.path, readOnly = true)) // не передаём копию чужого файла наружу
                        else -> action
                    },
                )
                message("Копия через root — только просмотр")
            }
            return
        }
        openPlain(panel, item)
    }

    private fun openPlain(panel: PanelController, item: FileItem) {
        if (!item.isDirectory && item.extension == "class" && FileOpener.rememberedMode("class") == null) {
            viewModelScope.launch {
                val route = ClassFiles.editorRoute(getApplication(), item.path)
                val action = if (route != null) OpenAction.Navigate(route) else FileOpener.actionFor(item, settings.value)
                c.userData.addRecent(item.path, false)
                dispatch(panel, action)
            }
            return
        }
        if (!item.isDirectory && item.extension == "xml" && FileOpener.rememberedMode("xml") == null) {
            // Отдельный бинарный AndroidManifest.xml на диске — тоже расшифровываем, сохранение закодирует обратно.
            viewModelScope.launch {
                val file = File(item.path)
                val route = AxmlFiles.editorRoute(getApplication(), file.path)
                // Не расшифровался (или обычный текстовый XML) — обычное открытие.
                val action = if (route.binarySource != null) OpenAction.Navigate(route) else FileOpener.actionFor(item, settings.value)
                c.userData.addRecent(item.path, false)
                dispatch(panel, action)
            }
            return
        }
        val action = FileOpener.actionFor(item, settings.value)
        if (!item.isDirectory) c.userData.addRecent(item.path, false)
        dispatch(panel, action)
    }

    fun setOpenWith(ext: String, mode: OpenAs?) {
        c.userData.setOpenWith(ext, mode?.name)
        message(if (mode == null) "Файлы .$ext снова открываются как обычно" else "Файлы .$ext теперь открываются: ${mode.title}")
    }

    fun openAs(item: FileItem, mode: OpenAs, archive: String? = null, entry: String? = null) {
        val action = FileOpener.openAs(item, mode)
        if (archive == null || entry == null) {
            dispatch(activePanel, action)
            return
        }
        val panel = activePanel
        viewModelScope.launch { dispatch(panel, archiveAware(action, archive, entry)) }
    }

    /**
     * Файл извлечён из архива во временную копию: редактор перепаковывает только ZIP,
     * остальное открываем только для чтения, чтобы «Сохранено» не врало.
     */
    private suspend fun archiveAware(action: OpenAction, archivePath: String, entry: String): OpenAction {
        val zip = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ArchiveEngine.detect(File(archivePath)) == client.wexmanager.core.archive.ArchiveType.Zip }.getOrDefault(false)
        }
        val route = (action as? OpenAction.Navigate)?.route
        return when {
            route is Route.Editor -> OpenAction.Navigate(
                route.copy(archive = archivePath.takeIf { zip }, entry = entry.takeIf { zip }, readOnly = !zip, lockReadOnly = !zip),
            )
            route is Route.HexViewer -> OpenAction.Navigate(route.copy(readOnly = true))
            action is OpenAction.ChooseOpenAs -> action.copy(archive = archivePath, entry = entry)
            else -> action
        }
    }

    private fun dispatch(panel: PanelController, action: OpenAction) {
        when (action) {
            is OpenAction.Enter -> panel.open(action.location)
            else -> _events.trySend(BrowserEvent.Open(action))
        }
    }

    // --------------------------------------------------------------- Операции

    private fun selectedOrSingle(panel: PanelController, item: FileItem?): List<FileItem> =
        item?.let { listOf(it) } ?: panel.state.value.selectedItems

    /** Копирование/перемещение в соседнюю панель (главная фишка MT). */
    fun transferToOther(move: Boolean, item: FileItem? = null) {
        val src = activePanel
        val dst = otherPanel
        val items = selectedOrSingle(src, item)
        if (items.isEmpty()) return
        val dstLoc = dst.location
        val srcLoc = src.location
        if (dstLoc is Location.Archive) {
            if (srcLoc is Location.Archive) {
                message("Перенос между архивами не поддерживается")
                return
            }
            if (ArchiveEngine.detect(File(dstLoc.archivePath)) != client.wexmanager.core.archive.ArchiveType.Zip) {
                message("Добавлять файлы можно только в ZIP-архивы")
                return
            }
            c.taskManager.addToArchive(items, File(dstLoc.archivePath), dstLoc.inner)
            if (move) message("Файлы добавлены в архив копией — исходники не удалены")
            src.clearSelection()
            return
        }
        if (dstLoc !is Location.Local) return
        if (srcLoc is Location.Archive) {
            c.taskManager.extract(File(srcLoc.archivePath), File(dstLoc.path), items.map { it.path }, basePath = srcLoc.inner)
            if (move) message("Файлы извлечены — архив не изменён")
        } else {
            if (srcLoc == dstLoc || (srcLoc is Location.Local && client.wexmanager.core.ops.TaskManager.sameFile(File(srcLoc.path), File(dstLoc.path)))) {
                message("Обе панели открыты в одной папке")
                return
            }
            c.taskManager.copy(items, dstLoc.path, move)
        }
        src.clearSelection()
    }

    fun copyToClipboard(cut: Boolean, item: FileItem? = null) {
        val panel = activePanel
        val items = selectedOrSingle(panel, item)
        if (items.isEmpty()) return
        if (items.any { it.isInArchive }) {
            message("Элементы архива можно только распаковать")
            return
        }
        c.clipboard.set(items, cut)
        panel.clearSelection()
        message("${if (cut) "Вырезано" else "Скопировано в буфер"}: ${Format.items(items.size)}")
    }

    fun paste() {
        val content = c.clipboard.content.value ?: return
        val loc = activePanel.location as? Location.Local ?: run {
            message("Вставка в архив пока не поддерживается")
            return
        }
        c.taskManager.copy(content.items, loc.path, content.cut)
        if (content.cut) c.clipboard.clear()
    }

    fun clearClipboard() = c.clipboard.clear()

    fun delete(items: List<FileItem>, permanently: Boolean) {
        if (items.isEmpty()) return
        if (items.any { it.isInArchive }) {
            val archive = items.first().archivePath!!
            if (ArchiveEngine.detect(File(archive)) != client.wexmanager.core.archive.ArchiveType.Zip) {
                message("Удалять можно только из ZIP-архивов")
                return
            }
            c.taskManager.deleteFromArchive(File(archive), items.map { it.path })
            panels.forEach { it.clearSelection() }
            return
        }
        c.taskManager.delete(items, toTrash = !permanently && settings.value.useTrash)
        panels.forEach { it.clearSelection() }
    }

    fun rename(item: FileItem, newName: String, onDone: (String?) -> Unit) {
        if (item.isInArchive) {
            onDone("Переименование внутри архива не поддерживается")
            return
        }
        val name = newName.trim()
        val error = validateName(name)
        if (error != null) {
            onDone(error)
            return
        }
        if (name == item.name) {
            onDone(null)
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val src = File(item.path)
                val dst = File(src.parentFile, name)
                val taken = dst.exists() || (client.wexmanager.core.fs.SafAccess.needsSaf(getApplication(), src.path) &&
                    client.wexmanager.core.fs.SafAccess.stat(getApplication(), dst.path) != null)
                when {
                    taken && !dst.path.equals(src.path, ignoreCase = true) -> "Файл с таким именем уже существует"
                    client.wexmanager.core.fs.SafAccess.needsSaf(getApplication(), src.path) ->
                        if (client.wexmanager.core.fs.SafAccess.rename(getApplication(), src.path, name)) null else "Не удалось переименовать"
                    src.renameTo(dst) -> null
                    else -> "Не удалось переименовать"
                }
            }
            onDone(result)
            if (result == null) {
                panels.forEach { if (it.location == activePanel.location || it.location == Location.Local(File(item.path).parent ?: "")) it.refresh() }
            }
        }
    }

    /** Пакетное переименование: шаблон с {name}, {ext}, {n}, {n:3}, {date}, + поиск/замена (regex). */
    fun batchRename(items: List<FileItem>, plan: List<Pair<FileItem, String>>) {
        if (items.any { it.isInArchive }) {
            message("Переименование внутри архива не поддерживается")
            return
        }
        viewModelScope.launch {
            var ok = 0
            var failed = 0
            val error = withContext(Dispatchers.IO) {
                val renames = plan.filter { (i, n) -> i.name != n }
                val srcNames = renames.mapTo(HashSet()) { it.first.name }
                // Проверка до любых изменений: корректные имена и отсутствие коллизий с файлами вне плана.
                renames.firstOrNull { (i, n) ->
                    validateName(n) != null || (File(File(i.path).parentFile, n).exists() && n !in srcNames)
                }?.let { return@withContext "Конфликт имени: ${it.second}" }
                if (renames.map { it.second }.toSet().size != renames.size) return@withContext "В плане есть одинаковые имена"
                // Двухфазное переименование — чтобы избежать коллизий при перестановке имён.
                val temp = renames.mapNotNull { (item, newName) ->
                    val src = File(item.path)
                    val tmp = File(src.parentFile, ".wex_rename_${System.nanoTime()}_${src.name.hashCode()}")
                    if (src.renameTo(tmp)) Triple(tmp, File(src.parentFile, newName), src) else { failed++; null }
                }
                for ((tmp, dst, original) in temp) {
                    if (!dst.exists() && tmp.renameTo(dst)) ok++
                    else {
                        // Откат только на свободное имя — никогда не затираем чужой файл.
                        val back = if (!original.exists()) original
                        else File(original.parentFile, LocalFileSystem.uniqueName(original.parent!!, original.name))
                        tmp.renameTo(back)
                        failed++
                    }
                }
                null
            }
            panels.forEach { it.refresh() }
            panels.forEach { it.clearSelection() }
            message(
                when {
                    error != null -> error
                    failed == 0 -> "Переименовано: ${Format.items(ok)}"
                    else -> "Переименовано: $ok, ошибок: $failed"
                },
            )
        }
    }

    fun createFolder(name: String, onDone: (String?) -> Unit) = create(name, true, onDone)
    fun createFile(name: String, onDone: (String?) -> Unit) = create(name, false, onDone)

    private fun create(rawName: String, dir: Boolean, onDone: (String?) -> Unit) {
        val name = rawName.trim()
        validateName(name)?.let { onDone(it); return }
        val loc = activePanel.location as? Location.Local ?: run { onDone("Нельзя создать в архиве"); return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val f = File(loc.path, name)
                val app = getApplication<Application>()
                val viaSaf = client.wexmanager.core.fs.SafAccess.needsSaf(app, loc.path)
                val exists = f.exists() || (viaSaf && client.wexmanager.core.fs.SafAccess.stat(app, f.path) != null)
                when {
                    exists -> "Уже существует"
                    viaSaf && dir && client.wexmanager.core.fs.SafAccess.mkdir(app, f.path) -> null
                    viaSaf && !dir && client.wexmanager.core.fs.SafAccess.createFile(app, f.path) -> null
                    dir && f.mkdirs() -> null
                    !dir && runCatching { f.createNewFile() }.getOrDefault(false) -> null
                    else -> "Не удалось создать (нет доступа?)"
                }
            }
            onDone(result)
            if (result == null) activePanel.open(loc, addToHistory = false, focusKey = File(loc.path, name).path).also {
                if (dir) Unit
            }
        }
    }

    fun validateName(name: String): String? = when {
        name.isEmpty() -> "Введите имя"
        name == "." || name == ".." -> "Недопустимое имя"
        name.contains('/') || name.contains('\u0000') -> "Имя не может содержать «/»"
        name.toByteArray().size > 255 -> "Слишком длинное имя"
        else -> null
    }

    fun compress(items: List<FileItem>, name: String, format: ArchiveFormat, level: Int, toOtherPanel: Boolean, password: String? = null) {
        val target = (if (toOtherPanel) otherPanel else activePanel).location as? Location.Local ?: return
        val fileName = if (name.endsWith(".${format.extension}")) name else "$name.${format.extension}"
        // Существующий архив не затираем — берём свободное имя.
        val file = File(target.path, LocalFileSystem.uniqueName(target.path, fileName))
        c.taskManager.compress(items, file, format, level, password)
        panels.forEach { it.clearSelection() }
    }

    fun verifyArchive(item: FileItem) {
        if (item.isInArchive) return
        c.taskManager.verifyArchive(File(item.path))
    }

    /** Пакетная распаковка: каждый архив — в свою папку рядом (отдельная задача на архив). */
    fun extractEach(items: List<FileItem>) {
        items.forEach { extract(it, ExtractTarget.Subfolder) }
        activePanel.clearSelection()
        message("Распаковка: ${Format.items(items.size)} в фоне")
    }

    fun extract(item: FileItem, target: ExtractTarget) {
        val archive = File(item.path)
        val here = File(item.path).parentFile ?: return
        val dest = when (target) {
            ExtractTarget.Here -> here
            ExtractTarget.Subfolder -> File(here, LocalFileSystem.uniqueName(here.path, archiveBaseName(archive.name)))
            ExtractTarget.OtherPanel -> (otherPanel.location as? Location.Local)?.let { File(it.path) } ?: run {
                message("Вторая панель должна быть обычной папкой")
                return
            }
        }
        c.taskManager.extract(archive, dest)
    }

    private fun archiveBaseName(name: String): String {
        val lower = name.lowercase()
        for (ext in listOf(".tar.gz", ".tar.xz", ".tar.bz2")) if (lower.endsWith(ext)) return name.dropLast(ext.length)
        return name.substringBeforeLast('.')
    }

    fun signingKeyName(): String {
        val d = c.userData.data.value
        return d.signingKeys.firstOrNull { it.alias == d.activeSigningKey }?.name ?: "WexManager"
    }

    fun signApk(item: FileItem) {
        if (item.isInArchive) return
        c.taskManager.signApk(File(item.path), c.userData.data.value.activeSigningKey)
    }

    fun share(items: List<FileItem>) {
        val files = items.filter { !it.isDirectory && !it.isInArchive }.map { File(it.path) }
        if (files.isEmpty()) {
            message("Можно поделиться только файлами")
            return
        }
        _events.trySend(BrowserEvent.Share(files))
    }

    fun toggleBookmark(path: String, title: String, isDirectory: Boolean) {
        val exists = c.userData.data.value.bookmarks.any { it.path == path }
        c.userData.toggleBookmark(path, title, isDirectory)
        message(if (exists) "Закладка удалена" else "Добавлено в закладки")
    }

    fun removeBookmark(path: String) = c.userData.removeBookmark(path)

    fun setFolderColor(path: String, color: Int?) = c.userData.setFolderColor(path, color)

    fun resolveConflict(taskId: Long, decision: client.wexmanager.core.ops.ConflictDecision) {
        c.taskManager.tasks.value.firstOrNull { it.id == taskId }?.conflict?.resolve(decision)
    }

    fun cancelTask(id: Long) = c.taskManager.cancel(id)
    fun dismissTask(id: Long) = c.taskManager.dismiss(id)
    fun dismissFinishedTasks() = c.taskManager.dismissFinished()

    fun updateSettings(transform: (client.wexmanager.core.settings.AppSettings) -> client.wexmanager.core.settings.AppSettings) =
        c.settings.update(transform)

    fun createSymlink(item: FileItem) {
        val dst = otherPanel.location as? Location.Local ?: return
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching { android.system.Os.symlink(item.path, File(dst.path, item.name).path) }
            }
            r.onSuccess { otherPanel.refresh(); message("Ссылка создана в соседней панели") }
                .onFailure { message("Не удалось создать ссылку: ${it.message}") }
        }
    }

    fun chmod(item: FileItem, mode: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { LocalFileSystem.chmod(item.path, mode) } }
            onDone(r.exceptionOrNull()?.message)
            if (r.isSuccess) activePanel.refresh()
        }
    }

    fun touch(item: FileItem, time: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!File(item.path).setLastModified(time)) message("Не удалось изменить дату")
            withContext(Dispatchers.Main) { panels.forEach { it.refresh() } }
        }
    }

    suspend fun readTextPreview(path: String, maxBytes: Int = 4096): String = withContext(Dispatchers.IO) {
        try {
            File(path).inputStream().use { s ->
                val buf = ByteArray(maxBytes)
                val n = s.read(buf)
                if (n <= 0) "" else String(buf, 0, n)
            }
        } catch (e: IOException) {
            ""
        }
    }
}
