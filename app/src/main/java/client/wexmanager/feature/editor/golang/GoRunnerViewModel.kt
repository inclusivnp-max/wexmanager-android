package client.wexmanager.feature.editor.golang

import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal const val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
internal const val GO_SETUP = """pkg update && pkg install golang coreutils util-linux
termux-setup-storage
mkdir -p ~/.termux
touch ~/.termux/termux.properties
sed -i '/^[[:space:]]*allow-external-apps[[:space:]]*=/d' ~/.termux/termux.properties
printf '%s\n' 'allow-external-apps = true' >> ~/.termux/termux.properties
termux-reload-settings"""

internal data class GoRunnerState(
    val project: GoProject? = null,
    val config: GoRunConfig = GoRunConfig(),
    val discovering: Boolean = false,
    val running: Boolean = false,
    val stopping: Boolean = false,
    val stdinClosed: Boolean = false,
    val action: GoAction? = null,
    val runPath: String = "",
    val preview: String = "",
    val output: String = "",
    val exitCode: Int? = null,
    val message: String? = null,
    val logPath: String? = null,
    val inputDraft: String = "",
    val commandHistory: List<String> = emptyList(),
    /** Кликабельные ошибки компилятора/линтера из последнего вывода. */
    val errors: List<GoError> = emptyList(),
)

/** Explicit mutable PendingIntent lets Termux report launch failures without exporting a receiver. */
class GoRunResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.data?.lastPathSegment ?: return
        val result = intent.getBundleExtra("result") ?: return
        val message = if (result.getInt("err", 0) != 0) result.getString("errmsg") ?: "Ошибка Termux" else null
        val code = result.getInt("exitCode", 1)
        val diagnostics = if (code != 0) result.getString("stderr").orEmpty().ifBlank { result.getString("stdout").orEmpty() }.take(4000).ifBlank { null } else null
        GoRunCallbacks.results[id] = GoRunResult(code, message ?: diagnostics)
    }
}

internal data class GoRunResult(val code: Int, val error: String?)
internal object GoRunCallbacks { val results = ConcurrentHashMap<String, GoRunResult>() }

internal class GoRunnerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("go_run_config", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(GoRunnerState())
    val state = _state.asStateFlow()
    private var selection: Job? = null
    private var selectedPath = ""
    @Volatile private var session: File? = null
    private var inputSequence = 0L
    private val io = Dispatchers.IO.limitedParallelism(1)

    fun selectFile(path: String) {
        if (selectedPath == path) return
        selectedPath = path
        selection?.cancel()
        _state.update { it.copy(project = null, discovering = true, message = null) }
        selection = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { GoProject.detect(path) } }
            if (selectedPath != path) return@launch
            result.onSuccess { project ->
                val config = GoRunConfig(
                    scope = runCatching { GoRunScope.valueOf(prefs.getString("$path.scope", null) ?: project.defaultScope.name) }.getOrDefault(project.defaultScope),
                    arguments = prefs.getString("$path.args", "").orEmpty(),
                    workingDirectory = prefs.getString("$path.cwd", "").orEmpty(),
                    syncImports = prefs.getBoolean("$path.sync", true),
                    buildTags = prefs.getString("$path.tags", "").orEmpty(),
                    testFlags = prefs.getString("$path.testFlags", "").orEmpty(),
                    envVars = prefs.getString("$path.env", "").orEmpty(),
                    buildOutput = prefs.getString("$path.bin", "").orEmpty(),
                )
                _state.update { it.copy(project = project, config = config, discovering = false) }
            }.onFailure { e -> _state.update { it.copy(discovering = false, message = e.message) } }
        }
    }

    fun refreshProject() {
        val path = selectedPath
        selectedPath = ""
        selectFile(path)
    }

    fun configure(config: GoRunConfig) {
        val path = selectedPath
        prefs.edit().putString("$path.scope", config.scope.name).putString("$path.args", config.arguments)
            .putString("$path.cwd", config.workingDirectory).putBoolean("$path.sync", config.syncImports)
            .putString("$path.tags", config.buildTags).putString("$path.testFlags", config.testFlags)
            .putString("$path.env", config.envVars).putString("$path.bin", config.buildOutput).apply()
        _state.update { it.copy(config = config, message = null) }
    }

    fun message(text: String) { _state.update { it.copy(message = text) } }

    fun termuxInstalled(): Boolean = runCatching {
        getApplication<Application>().packageManager.getApplicationInfo("com.termux", 0)
    }.isSuccess

    fun hasPermission() = ContextCompat.checkSelfPermission(getApplication(), TERMUX_PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** Если файл ещё не открыт (race между табом и кликом Run) — детектим синхронно и ждём выбора. */
    suspend fun ensureSelected(path: String): GoProject? {
        if (selectedPath == path) {
            selection?.join()
            return _state.value.project
        }
        selection?.cancel()
        selectedPath = path
        _state.update { it.copy(project = null, discovering = true, message = null) }
        val result = withContext(Dispatchers.IO) { runCatching { GoProject.detect(path) } }
        if (selectedPath != path) return null
        result.onSuccess { project ->
            val config = GoRunConfig(
                scope = runCatching { GoRunScope.valueOf(prefs.getString("$path.scope", null) ?: project.defaultScope.name) }.getOrDefault(project.defaultScope),
                arguments = prefs.getString("$path.args", "").orEmpty(),
                workingDirectory = prefs.getString("$path.cwd", "").orEmpty(),
                syncImports = prefs.getBoolean("$path.sync", true),
                buildTags = prefs.getString("$path.tags", "").orEmpty(),
                testFlags = prefs.getString("$path.testFlags", "").orEmpty(),
                envVars = prefs.getString("$path.env", "").orEmpty(),
                buildOutput = prefs.getString("$path.bin", "").orEmpty(),
            )
            _state.update { it.copy(project = project, config = config, discovering = false) }
        }.onFailure { e -> _state.update { it.copy(discovering = false, message = e.message) } }
        return _state.value.project
    }

    fun start(action: GoAction, value: String = "") {
        if (_state.value.running) return
        val st = _state.value
        val project = st.project ?: return
        if (!termuxInstalled()) { message("Установите Termux и настройте Go по инструкции ниже"); return }
        if (!hasPermission()) { message("Разрешите WexManager запуск команд в Termux"); return }
        _state.update { it.copy(running = true, stopping = false, stdinClosed = false, action = action,
            runPath = project.file.absolutePath, output = "", exitCode = null, message = null, logPath = null, inputDraft = "") }
        viewModelScope.launch(io) {
            var directory: File? = null
            var callback: PendingIntent? = null
            var id: String? = null
            try {
                val plan = GoRunPlan.create(project, st.config, action, value)
                val storage = Environment.getExternalStorageDirectory().canonicalFile
                require(project.file.canonicalFile.startsWith(storage) && !project.file.invariantSeparatorsPath.contains("/Android/")) {
                    "Сохраните Go-файл или проект в общей папке, например Download/Go. Termux читает файлы из общего хранилища."
                }
                val base = File(storage, "WexManager/.go-sessions")
                require(base.mkdirs() || base.isDirectory) { "Откройте доступ WexManager к файлам" }
                base.listFiles()?.filter {
                    it.name.matches(Regex("[a-f0-9-]{36}")) && !java.nio.file.Files.isSymbolicLink(it.toPath()) &&
                        System.currentTimeMillis() - it.lastModified() > 7 * 86400_000L
                }?.forEach { old ->
                    java.nio.file.Files.walk(old.toPath()).use { paths ->
                        paths.sorted(Comparator.reverseOrder()).forEach { runCatching { java.nio.file.Files.deleteIfExists(it) } }
                    }
                }
                id = UUID.randomUUID().toString()
                directory = File(base, id)
                require(directory.mkdir()) { "Ошибка создания консоли Go" }
                session = directory
                inputSequence = 0
                File(directory, "output").writeText("")
                _state.update { it.copy(preview = plan.preview, logPath = File(directory, "output").absolutePath) }
                val app = getApplication<Application>()
                val resultIntent = Intent(app, GoRunResultReceiver::class.java).setData(Uri.parse("wex-go://result/$id"))
                callback = PendingIntent.getBroadcast(app, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                val command = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
                    .putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                    .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-l", "-c", GoSessionScript.create(directory, plan.commands)))
                    .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
                    .putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "WexManager · Go · ${action.label}")
                    .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", callback)
                check(app.startService(command) != null) { "Откройте Termux один раз и повторите запуск" }
                observe(directory, id)
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.update { it.copy(running = false, stopping = false, message = error.message ?: "Ошибка запуска Go") }
            } finally {
                // Cancellation also stops compiler children; never remove a live bridge directory.
                directory?.let { runCatching { File(it, "stop").writeText("") } }
                if (session == directory) session = null
                callback?.cancel()
                id?.let { GoRunCallbacks.results.remove(it) }
            }
        }
    }

    private suspend fun observe(directory: File, id: String) {
        val started = System.currentTimeMillis()
        var lastLength = -1L
        var connected = false
        while (viewModelScope.isActive) {
            val log = File(directory, "output")
            if (log.length() != lastLength) {
                lastLength = log.length()
                val text = readGoOutput(log)
                _state.update { it.copy(output = text, errors = parseGoErrors(text)) }
            }
            val status = File(directory, "state").takeIf { it.isFile }?.readText().orEmpty()
            if (status.startsWith("exit:")) {
                val code = status.substringAfter(':').toIntOrNull() ?: 1
                val text = readGoOutput(log)
                _state.update { it.copy(running = false, stopping = false, exitCode = code, output = text, errors = parseGoErrors(text)) }
                withContext(Dispatchers.Main) {
                    if (code == 0) {
                        val action = _state.value.action
                        if (action == GoAction.TIDY || action == GoAction.GET || action == GoAction.INIT) {
                            client.wexmanager.feature.editor.lang.GoWorkspaceIndex.invalidate()
                        }
                        if (action == GoAction.INIT) refreshProject()
                    }
                }
                return
            }
            if (status.isNotEmpty()) connected = true
            val result = GoRunCallbacks.results.remove(id)
            if (result != null) {
                _state.update { it.copy(running = false, stopping = false, exitCode = result.code, message = result.error,
                    output = readGoOutput(log)) }
                return
            }
            val now = System.currentTimeMillis()
            if (!connected && now - started > 25_000) error("Termux не ответил. Проверьте allow-external-apps, доступ к хранилищу и откройте Termux.")
            val heartbeat = File(directory, "heartbeat").lastModified().takeIf { it > 0 } ?: started
            if (connected && now - heartbeat > 30_000) error("Связь с Termux прервана. Откройте Termux и повторите запуск.")
            delay(150)
        }
    }

    fun updateInputDraft(text: String) { _state.update { it.copy(inputDraft = text.take(4096)) } }

    fun sendInput(text: String) {
        if (!_state.value.running || _state.value.stdinClosed || _state.value.stopping) return
        _state.update { it.copy(inputDraft = "", commandHistory = if (it.action == GoAction.CONSOLE) rememberGoCommand(it.commandHistory, text) else it.commandHistory) }
        viewModelScope.launch(io) {
            val dir = session ?: return@launch
            runCatching {
                val number = (++inputSequence).toString().padStart(12, '0')
                val tmp = File(dir, ".input-$number.tmp")
                tmp.writeText(text + "\n")
                check(tmp.renameTo(File(dir, "input-$number"))) { "Ошибка отправки ввода" }
            }.onFailure { message(it.message ?: "Ошибка stdin") }
        }
    }

    fun closeInput() {
        if (!_state.value.running || _state.value.stdinClosed) return
        _state.update { it.copy(stdinClosed = true) }
        viewModelScope.launch(io) { session?.let { runCatching { File(it, "eof").writeText("") }.onFailure { e -> message(e.message.orEmpty()) } } }
    }

    fun stop() {
        if (!_state.value.running) return
        _state.update { it.copy(stopping = true) }
        viewModelScope.launch(io) { session?.let { runCatching { File(it, "stop").writeText("") }.onFailure { e -> message(e.message.orEmpty()) } } }
    }

    override fun onCleared() {
        session?.let { runCatching { File(it, "stop").writeText("") } }
    }
}

internal fun readGoOutput(file: File): String {
    if (!file.isFile) return ""
    return RandomAccessFile(file, "r").use {
        val length = it.length()
        val start = (length - 250_000).coerceAtLeast(0)
        it.seek(start)
        val bytes = ByteArray((length - start).toInt())
        it.readFully(bytes)
        (if (start > 0) "[Показан конец журнала; полный файл — в меню копирования пути]\n" else "") +
            bytes.toString(Charsets.UTF_8).replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"), "")
    }
}
