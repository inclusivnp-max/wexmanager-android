package client.wexmanager.feature.editor.golang

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

internal data class GoWorkspaceState(
    val projects: List<String> = emptyList(), val root: String? = null,
    val nodes: List<GoTreeNode> = emptyList(), val expanded: Set<String> = emptySet(),
    val selectedFile: String? = null, val openVersion: Long = 0,
    val selectedDirectory: String? = null, val filter: String = "", val busy: Boolean = true,
    val truncated: Boolean = false, val error: String? = null, val treeVisible: Boolean = true,
    val revealVersion: Long = 0,
)

internal class GoWorkspaceViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("go_workspace", Context.MODE_PRIVATE)
    val storage: File = Environment.getExternalStorageDirectory()
    val projectsDirectory = File(storage, "WexManager/GoProjects")
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val _state = MutableStateFlow(GoWorkspaceState())
    val state = _state.asStateFlow()
    private var filterJob: Job? = null

    init {
        viewModelScope.launch(io) {
            val projects = runCatching { JSONArray(prefs.getString("projects", "[]")).let { arr -> (0 until arr.length()).map { arr.getString(it) } } }.getOrDefault(emptyList())
            _state.update { it.copy(projects = projects, busy = false) }
            prefs.getString("active", null)?.let { path ->
                runCatching { openRoot(File(path)) }.onFailure { e -> _state.update { it.copy(error = e.message) } }
            }
        }
    }

    fun openProject(path: String, done: (String?) -> Unit = {}) = operation(done) { openRoot(File(path)) }
    fun createProject(name: String, module: String, done: (String?) -> Unit = {}) = operation(done) {
        openRoot(GoWorkspaceFiles.createProject(projectsDirectory, name, module))
    }

    private fun openRoot(folder: File) {
        val root = folder.canonicalFile
        require(root.isDirectory && root.canRead() && GoWorkspaceFiles.contained(storage, root) && !root.path.contains("/Android/")) {
            "Выберите доступную папку проекта в общем хранилище"
        }
        val recent = prefs.getString("${root.path}.file", null)?.let(::File)?.takeIf { it.isFile && GoWorkspaceFiles.contained(root, it) }
            ?: File(root, "main.go").takeIf { it.isFile }
        val projects = (listOf(root.path) + _state.value.projects).distinct().take(24)
        val expanded = prefs.getStringSet("${root.path}.expanded", emptySet()).orEmpty().toSet() +
            (recent?.let { GoWorkspaceFiles.parents(root, it) } ?: emptySet())
        prefs.edit().putString("projects", JSONArray(projects).toString()).putString("active", root.path).apply()
        _state.update { it.copy(projects = projects, root = root.path, selectedFile = recent?.path,
            selectedDirectory = recent?.parent ?: root.path, openVersion = it.openVersion + 1,
            expanded = expanded, filter = "", error = null, treeVisible = prefs.getBoolean("${root.path}.tree", true)) }
        loadTree()
    }

    fun projectsHome() = operation {
        prefs.edit().remove("active").apply()
        _state.update { it.copy(root = null, selectedFile = null, nodes = emptyList(), filter = "", error = null) }
    }

    fun forgetProject(path: String) {
        val projects = _state.value.projects - path
        prefs.edit().putString("projects", JSONArray(projects).toString()).apply()
        _state.update { it.copy(projects = projects) }
    }

    fun selectFile(path: String, done: (String?) -> Unit = {}) = operation(done) {
        val root = _state.value.root ?: return@operation
        val file = GoWorkspaceFiles.textFile(File(root), File(path))
        prefs.edit().putString("$root.file", file.path).apply()
        _state.update { it.copy(selectedFile = file.path, selectedDirectory = file.parent, openVersion = it.openVersion + 1, error = null) }
    }

    fun trackActiveFile(path: String) {
        val root = _state.value.root ?: return
        if (File(path).toPath().normalize().startsWith(File(root).toPath())) {
            prefs.edit().putString("$root.file", path).apply()
            _state.update { it.copy(selectedDirectory = File(path).parent) }
        }
    }

    fun setTreeVisible(visible: Boolean) {
        val root = _state.value.root ?: return
        prefs.edit().putBoolean("$root.tree", visible).apply()
        _state.update { it.copy(treeVisible = visible) }
    }

    fun toggleFolder(path: String) = operation {
        val root = _state.value.root?.let(::File) ?: return@operation
        require(GoWorkspaceFiles.contained(root, File(path))) { "Папка вне проекта" }
        _state.update { it.copy(expanded = if (path in it.expanded) it.expanded - path else it.expanded + path, selectedDirectory = path) }
        saveExpanded(); loadTree()
    }

    fun reveal(path: String) = operation {
        val root = _state.value.root?.let(::File) ?: return@operation
        val file = File(path)
        _state.update { it.copy(expanded = it.expanded + GoWorkspaceFiles.parents(root, file), filter = "",
            selectedDirectory = if (file.isDirectory) file.path else file.parent, treeVisible = true, revealVersion = it.revealVersion + 1) }
        saveExpanded(); loadTree()
    }

    fun collapseAll() = operation {
        _state.update { it.copy(expanded = emptySet(), filter = "") }; saveExpanded(); loadTree()
    }

    fun setFilter(filter: String) {
        _state.update { it.copy(filter = filter) }
        filterJob?.cancel()
        filterJob = viewModelScope.launch { delay(180); operation { loadTree() } }
    }

    fun refresh() = operation { loadTree() }
    fun consumeError(message: String) { _state.update { if (it.error == message) it.copy(error = null) else it } }

    fun createEntry(name: String, folder: Boolean, done: (String?) -> Unit = {}) = operation(done) {
        val st = _state.value
        val root = st.root?.let(::File) ?: return@operation
        val parent = st.selectedDirectory?.let(::File) ?: root
        val file = GoWorkspaceFiles.createEntry(root, parent, name, folder)
        _state.update { it.copy(expanded = it.expanded + parent.path, selectedFile = if (folder) it.selectedFile else file.path,
            selectedDirectory = if (folder) file.path else parent.path, openVersion = it.openVersion + if (folder) 0 else 1) }
        saveExpanded(); loadTree()
    }

    private fun saveExpanded() {
        val st = _state.value
        st.root?.let { prefs.edit().putStringSet("$it.expanded", st.expanded).apply() }
    }
    private fun loadTree() {
        val st = _state.value
        val root = st.root?.let(::File) ?: return
        val tree = if (st.filter.isBlank()) GoWorkspaceFiles.tree(root, st.expanded) else GoWorkspaceFiles.search(root, st.filter)
        _state.update { if (it.root == st.root && it.filter == st.filter) it.copy(nodes = tree.nodes, truncated = tree.truncated) else it }
    }
    private fun operation(done: ((String?) -> Unit)? = null, action: () -> Unit) {
        viewModelScope.launch(io) {
            _state.update { it.copy(busy = true, error = null) }
            var failure: String? = null
            try { action() } catch (e: Exception) { failure = e.message ?: "Ошибка проекта"; _state.update { it.copy(error = failure) } }
            finally { _state.update { it.copy(busy = false) } }
            withContext(Dispatchers.Main) { done?.invoke(failure) }
        }
    }
}
