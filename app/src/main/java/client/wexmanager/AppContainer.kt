package client.wexmanager

import android.content.Context
import androidx.compose.runtime.Immutable
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.StorageRepository
import client.wexmanager.core.icons.IconPackRepository
import client.wexmanager.core.ops.TaskManager
import client.wexmanager.core.ops.TrashManager
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.core.settings.SettingsRepository
import client.wexmanager.core.settings.UserData
import client.wexmanager.core.settings.UserDataRepository
import client.wexmanager.core.settings.jsonDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Запросы к браузеру из других экранов (поиск, анализатор, APK…). */
sealed interface BrowserRequest {
    /** Папка — открыть; файл — открыть родителя и подсветить файл. */
    data class Reveal(val path: String) : BrowserRequest
    data class OpenArchive(val path: String) : BrowserRequest
    /** Перечитать открытые панели (после выдачи Android/data). */
    data object Refresh : BrowserRequest
}

/** Папка, для которой нужно открыть системный выбор дерева SAF. */
val safRequests = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 4)

@Immutable
data class ClipboardContent(val items: List<FileItem>, val cut: Boolean)

/** Глобальный буфер обмена файлов (живёт, пока жив процесс). */
class FileClipboard {
    private val _content = MutableStateFlow<ClipboardContent?>(null)
    val content: StateFlow<ClipboardContent?> = _content.asStateFlow()

    fun set(items: List<FileItem>, cut: Boolean) {
        _content.value = if (items.isEmpty()) null else ClipboardContent(items, cut)
    }

    fun clear() {
        _content.value = null
    }
}

/** Ручной DI: все синглтоны приложения. Хранит только applicationContext. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext.also {
        client.wexmanager.core.fs.LocalFileSystem.appContext = it
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(
        jsonDataStore(appContext, "settings.json", AppSettings(), AppSettings.serializer(), appScope),
        appScope,
    )

    val userData = UserDataRepository(
        jsonDataStore(appContext, "user_data.json", UserData(), UserData.serializer(), appScope),
        appScope,
    )

    init {
        // Выбор «всегда открывать так» нужен FileOpener синхронно — держим его копию.
        appScope.launch { userData.data.collect { client.wexmanager.core.util.FileOpener.openWith = it.openWith } }
        appScope.launch { userData.bookmarks.collect { client.wexmanager.core.util.AppShortcuts.update(appContext, it) } }
        appScope.launch { settings.settings.collect { client.wexmanager.core.fs.RootShell.enabled = it.rootAccess } }
        // Временные копии (расшифрованный XML, файлы из архивов, сравнения) старше суток — в мусор.
        appScope.launch(Dispatchers.IO) {
            // Копии закрытых файлов, прочитанных через root, не храним между запусками.
            client.wexmanager.core.fs.LocalFileSystem.deleteTree(java.io.File(appContext.cacheDir, "root"))
            val cutoff = System.currentTimeMillis() - 86_400_000L
            for (name in listOf("axml", "archive", "diff", "share", "incoming")) {
                java.io.File(appContext.cacheDir, name).listFiles()?.filter { it.lastModified() < cutoff }?.forEach {
                    client.wexmanager.core.fs.LocalFileSystem.deleteTree(it)
                }
            }
        }
    }

    val storage = StorageRepository(appContext)
    val trash = TrashManager(storage)
    val taskManager = TaskManager(appContext, appScope, trash)
    val clipboard = FileClipboard()
    val iconPacks = IconPackRepository(appContext, appScope)

    /** replay = 1: запрос, пришедший до создания браузера (холодный старт по интенту), не теряется. */
    /** Пути файлов, открытых в редакторе с несохранёнными изменениями. */
    val dirtyEditors = MutableStateFlow<Set<String>>(emptySet())
    private val dirtyEditorOwners = HashMap<String, MutableSet<Any>>()

    @Synchronized
    fun setEditorDirty(owner: Any, path: String, dirty: Boolean) {
        if (dirty) dirtyEditorOwners.getOrPut(path) { HashSet() }.add(owner)
        else dirtyEditorOwners[path]?.let { owners -> if (owners.remove(owner) && owners.isEmpty()) dirtyEditorOwners.remove(path) }
        dirtyEditors.value = dirtyEditorOwners.keys.toSet()
    }

    private val goWorkspaces = HashMap<String, client.wexmanager.feature.editor.EditorTabs>()
    fun goWorkspaceTabs(root: String): client.wexmanager.feature.editor.EditorTabs =
        goWorkspaces.getOrPut(root) { client.wexmanager.feature.editor.EditorTabs(appContext as android.app.Application) }


    /** Вкладки редактора. Живут, пока жив процесс, и не кладут второй экран в стек. */
    val editorTabs = client.wexmanager.feature.editor.EditorTabs(appContext as android.app.Application)

    val browserRequests = MutableSharedFlow<BrowserRequest>(replay = 1, extraBufferCapacity = 8)

    /** Сообщения, которые должен показать браузер (экран, где они возникли, уже закрыт). */
    val appMessages = MutableSharedFlow<AppMessage>(replay = 1, extraBufferCapacity = 4)
}

/** Сообщение с необязательным действием (например «Подписать» после правки APK). */
class AppMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)
