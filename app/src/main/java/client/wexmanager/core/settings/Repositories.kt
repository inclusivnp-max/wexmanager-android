package client.wexmanager.core.settings

import androidx.datastore.core.DataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsRepository(
    private val store: DataStore<AppSettings>,
    private val scope: CoroutineScope,
) {
    /** null до первой загрузки — пока держим splash-экран, чтобы не мигала тема. */
    val loaded: StateFlow<AppSettings?> = store.data.stateIn(scope, SharingStarted.Eagerly, null)

    val settings: StateFlow<AppSettings> = loaded.filterNotNull()
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    val current: AppSettings get() = settings.value

    suspend fun awaitLoaded(): AppSettings = loaded.filterNotNull().first()

    fun update(transform: (AppSettings) -> AppSettings) {
        scope.launch { store.updateData(transform) }
    }

    suspend fun replace(value: AppSettings) {
        store.updateData { value }
    }
}

class UserDataRepository(
    private val store: DataStore<UserData>,
    private val scope: CoroutineScope,
) {
    val data: StateFlow<UserData> = store.data.stateIn(scope, SharingStarted.Eagerly, UserData())

    val bookmarks = data.map { it.bookmarks }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun awaitLoaded(): UserData = store.data.first()

    fun update(transform: (UserData) -> UserData) {
        scope.launch { store.updateData(transform) }
    }

    suspend fun replace(value: UserData) {
        store.updateData { value }
    }

    /**
     * Восстановление из резервной копии одной атомарной операцией: пароли и ключи подписи
     * этого устройства сохраняются (ключи Keystore с другого устройства не переносятся).
     */
    suspend fun restore(backup: UserData) {
        store.updateData { cur ->
            backup.copy(
                archivePasswords = (cur.archivePasswords + backup.archivePasswords).distinct(),
                signingKeys = cur.signingKeys,
                activeSigningKey = cur.activeSigningKey,
            )
        }
    }

    fun toggleBookmark(path: String, title: String, isDirectory: Boolean) = update { d ->
        if (d.bookmarks.any { it.path == path }) {
            d.copy(bookmarks = d.bookmarks.filterNot { it.path == path })
        } else {
            d.copy(bookmarks = d.bookmarks + Bookmark(path, title, isDirectory))
        }
    }

    fun removeBookmark(path: String) = update { d -> d.copy(bookmarks = d.bookmarks.filterNot { it.path == path }) }

    fun renameBookmark(path: String, title: String) = update { d ->
        d.copy(bookmarks = d.bookmarks.map { if (it.path == path) it.copy(title = title) else it })
    }

    fun moveBookmark(from: Int, to: Int) = update { d ->
        val list = d.bookmarks.toMutableList()
        if (from !in list.indices || to !in list.indices) return@update d
        list.add(to, list.removeAt(from))
        d.copy(bookmarks = list)
    }

    fun addRecent(path: String, isDirectory: Boolean) = update { d ->
        val entry = RecentEntry(path, System.currentTimeMillis(), isDirectory)
        d.copy(recents = (listOf(entry) + d.recents.filterNot { it.path == path }).take(MAX_RECENTS))
    }

    fun clearRecents() = update { it.copy(recents = emptyList()) }

    fun addPathHistory(path: String) = update { d ->
        d.copy(pathHistory = (listOf(path) + d.pathHistory.filterNot { it == path }).take(MAX_PATH_HISTORY))
    }

    fun addSearchQuery(query: String) = update { d ->
        if (query.isBlank()) d
        else d.copy(searchHistory = (listOf(query) + d.searchHistory.filterNot { it == query }).take(20))
    }

    fun clearSearchHistory() = update { it.copy(searchHistory = emptyList()) }

    fun addArchivePassword(pw: String) = update { d ->
        if (pw.isBlank()) d else d.copy(archivePasswords = (listOf(pw) + d.archivePasswords.filterNot { it == pw }).take(30))
    }

    fun removeArchivePassword(pw: String) = update { d -> d.copy(archivePasswords = d.archivePasswords.filterNot { it == pw }) }

    fun setIconOverride(ext: String, override: IconOverride?) = update { d ->
        val map = d.iconOverrides.toMutableMap()
        if (override == null) map.remove(ext) else map[ext] = override
        d.copy(iconOverrides = map)
    }

    fun clearIconOverrides() = update { it.copy(iconOverrides = emptyMap()) }

    fun setFolderColor(path: String, color: Int?) = update { d ->
        d.copy(folderColors = if (color == null) d.folderColors - path else d.folderColors + (path to color))
    }

    fun saveEditorPosition(path: String, line: Int) = update { d ->
        val m = LinkedHashMap(d.editorPositions)
        m.remove(path)
        m[path] = line
        while (m.size > 100) m.remove(m.keys.first())
        d.copy(editorPositions = m)
    }

    fun setOpenWith(ext: String, mode: String?) = update { d ->
        d.copy(openWith = if (mode == null) d.openWith - ext else d.openWith + (ext to mode))
    }

    fun addSigningKey(meta: SigningKeyMeta, makeActive: Boolean) = update { d ->
        d.copy(signingKeys = d.signingKeys + meta, activeSigningKey = if (makeActive) meta.alias else d.activeSigningKey)
    }

    fun removeSigningKey(alias: String) = update { d ->
        d.copy(
            signingKeys = d.signingKeys.filterNot { it.alias == alias },
            activeSigningKey = d.activeSigningKey.takeUnless { it == alias },
        )
    }

    fun setActiveSigningKey(alias: String?) = update { it.copy(activeSigningKey = alias) }

    fun setHome(panel: Int, path: String) = update { if (panel == 0) it.copy(homeLeft = path) else it.copy(homeRight = path) }

    fun savePanels(left: String?, right: String?, active: Int) = update {
        it.copy(leftPath = left, rightPath = right, activePanel = active)
    }

    private companion object {
        const val MAX_RECENTS = 60
        const val MAX_PATH_HISTORY = 30
    }
}
