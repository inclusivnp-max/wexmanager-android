package client.wexmanager.feature.editor

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import client.wexmanager.ui.navigation.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Вкладки редактора на время процесса: один экран в стеке, свой [EditorViewModel] на файл.
 * Повторное открытие того же файла только переключает вкладку.
 */
class EditorTabs(private val app: Application) {
    data class Tab(val id: Long, val route: Route.Editor) {
        val title: String get() = File(route.path).name.ifEmpty { route.path }
    }

    private val stores = HashMap<Long, ViewModelStore>()
    private val vms = HashMap<Long, EditorViewModel>()
    private var nextId = 1L

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeId = MutableStateFlow<Long?>(null)
    val activeId: StateFlow<Long?> = _activeId.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun consumeNotice() {
        _notice.value = null
    }

    /** true — вкладка открыта или уже была открыта. false — лимит, текст в [notice]. */
    fun open(route: Route.Editor): Boolean {
        val key = keyOf(route)
        val existing = _tabs.value.find { keyOf(it.route) == key }
        if (existing != null) {
            _activeId.value = existing.id
            return true
        }
        if (_tabs.value.size >= MAX) {
            _notice.value = "Открыто $MAX файлов — закройте вкладку, чтобы открыть ещё"
            return false
        }
        val id = nextId++
        val tab = Tab(id, route)
        stores[id] = ViewModelStore()
        _tabs.value = _tabs.value + tab
        _activeId.value = id
        return true
    }

    fun select(id: Long) {
        if (_tabs.value.any { it.id == id }) _activeId.value = id
    }

    fun viewModel(id: Long): EditorViewModel {
        vms[id]?.let { return it }
        val tab = _tabs.value.first { it.id == id }
        val store = stores.getValue(id)
        val vm = ViewModelProvider.create(store, factory(tab.route))[EditorViewModel::class.java]
        vms[id] = vm
        return vm
    }

    /** Снимает вкладку и её ViewModel (onCleared убирает файл из dirtyEditors). */
    fun close(id: Long) {
        val left = _tabs.value.filter { it.id != id }
        _tabs.value = left
        if (_activeId.value == id) _activeId.value = left.lastOrNull()?.id
        vms.remove(id)
        stores.remove(id)?.clear()
    }

    private fun factory(route: Route.Editor) = object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EditorViewModel(
                app,
                route.path,
                route.archive,
                route.entry,
                route.readOnly,
                route.sourceUri,
                route.charset,
                route.binarySource,
            ) as T
        }
    }

    companion object {
        const val MAX = 20

        /** path + archive + entry + binarySource. Кодировка и readOnly в ключ не входят. */
        fun keyOf(route: Route.Editor): String =
            "${route.path}\u0000${route.archive.orEmpty()}\u0000${route.entry.orEmpty()}\u0000${route.binarySource.orEmpty()}"
    }
}
