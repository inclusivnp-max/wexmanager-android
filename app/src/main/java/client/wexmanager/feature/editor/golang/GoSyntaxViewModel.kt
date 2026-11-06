package client.wexmanager.feature.editor.golang

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A small bundled go/parser process; no SDK, Termux, network, saves or user-code execution. */
internal class GoSyntaxViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("go_syntax", Context.MODE_PRIVATE)
    private val worker = GoSyntaxWorker(File(app.applicationInfo.nativeLibraryDir, "libgosyntax.so"))
    private val revision = GoSyntaxRevision()
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(GoSyntaxState(enabled = prefs.getBoolean("enabled", true)))
    val state = _state.asStateFlow()
    private var job: Job? = null

    fun schedule(path: String, snapshot: () -> String) {
        val id = revision.advance(path)
        job?.cancel()
        if (!_state.value.enabled) {
            _state.value = GoSyntaxState(enabled = false, path = path, request = id, phase = GoSyntaxPhase.DISABLED)
            return
        }
        _state.value = GoSyntaxState(path = path, request = id, phase = GoSyntaxPhase.PENDING)
        job = viewModelScope.launch {
            delay(450)
            try {
                val source = snapshot()
                require(source.length <= 1_048_576) { "Автопроверка поддерживает Go-файлы до 1 MiB" }
                if (!revision.accepts(id, path)) return@launch
                _state.value = _state.value.copy(phase = GoSyntaxPhase.CHECKING)
                val result = withTimeout(4000) {
                    suspendCancellableCoroutine { continuation ->
                        continuation.invokeOnCancellation { cleanup.launch { worker.cancel(id) } }
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val line = worker.exchange(id, syntaxRequest(id, path, source)) { !continuation.isActive }
                                val result = parseSyntaxResult(line, id, source.length)
                                if (continuation.isActive) continuation.resume(result)
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                            }
                        }
                    }
                }
                if (revision.accepts(id, path)) _state.value = _state.value.copy(phase = GoSyntaxPhase.READY, problems = result.problems)
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) throw e
                if (revision.accepts(id, path)) _state.value = _state.value.copy(phase = GoSyntaxPhase.ERROR,
                    error = if (e is kotlinx.coroutines.TimeoutCancellationException) "Проверка заняла слишком много времени. Повторите её." else e.message ?: "Ошибка проверки синтаксиса")
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("enabled", enabled).apply()
        revision.advance(_state.value.path)
        job?.cancel()
        _state.value = _state.value.copy(enabled = enabled, phase = if (enabled) GoSyntaxPhase.PENDING else GoSyntaxPhase.DISABLED,
            problems = emptyList(), error = null)
        if (!enabled) { val id = _state.value.request; cleanup.launch { worker.closeUpTo(id) } }
    }

    /**
     * Разовые запросы к встроенному сервису (формат, символы). Идут через тот же процесс, что и
     * автопроверка, поэтому сериализованы с ней и не создают второй экземпляр go/parser.
     */
    private suspend fun <T> request(id: Long, payload: () -> String, parse: (String) -> T): T =
        withTimeout(8000) {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cleanup.launch { worker.cancel(id) } }
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val line = worker.exchange(id, payload()) { !continuation.isActive }
                        val result = parse(line)
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
        }

    /** gofmt из встроенного сервиса: код возвращается уже отформатированным, без Termux. */
    suspend fun format(path: String, source: String): String {
        require(source.length <= 1_048_576) { "Форматирование поддерживает Go-файлы до 1 MiB" }
        val id = nextRequestId()
        return request(id, { syntaxRequest(id, path, source, "format") }) { parseSyntaxFormat(it, id) }
    }

    /** Символы файла (функции, типы, поля, импорты) — для навигатора структуры и подсказок. */
    suspend fun symbols(path: String, source: String): List<GoSymbol> {
        if (source.length > 1_048_576) return emptyList()
        val id = nextRequestId()
        return request(id, { syntaxRequest(id, path, source, "symbols") }) { parseSyntaxSymbols(it, id, source.length) }
    }

    /** Отдельный диапазон идентификаторов: разовые запросы не сбивают ревизию автопроверки. */
    private val extraId = java.util.concurrent.atomic.AtomicLong(1_000_000_000L)
    private fun nextRequestId(): Long = extraId.incrementAndGet()

    fun pause() {
        revision.advance("")
        job?.cancel()
        val id = _state.value.request
        cleanup.launch { worker.closeUpTo(id) }
    }

    override fun onCleared() {
        revision.advance("")
        job?.cancel()
        cleanup.launch { worker.close() }.invokeOnCompletion { cleanup.cancel() }
    }
}

private class GoSyntaxWorker(private val executable: File) {
    private val life = Any()
    private val exchange = Any()
    private var process: Process? = null
    private var input: BufferedWriter? = null
    private var output: BufferedReader? = null
    private var active: Long? = null
    private var lastId = 0L

    fun exchange(id: Long, request: String, cancelled: () -> Boolean): String = synchronized(exchange) {
        val pair = synchronized(life) {
            if (cancelled()) throw CancellationException()
            if (process == null) {
                check(executable.isFile && executable.canExecute()) { "Встроенный Go-парсер отсутствует. Установите обновлённый APK." }
                val started = ProcessBuilder(executable.absolutePath).redirectError(ProcessBuilder.Redirect.INHERIT).start()
                process = started
                input = started.outputStream.bufferedWriter(Charsets.UTF_8)
                output = started.inputStream.bufferedReader(Charsets.UTF_8)
            }
            active = id
            lastId = id
            input!! to output!!
        }
        try {
            pair.first.write(request); pair.first.newLine(); pair.first.flush()
            pair.second.readLine() ?: error("Встроенный Go-парсер завершил работу")
        } catch (e: Exception) {
            cancel(id)
            throw e
        } finally {
            synchronized(life) { if (active == id) active = null }
        }
    }

    fun cancel(id: Long) = synchronized(life) { if (active == id) closeLocked() }
    fun close() = synchronized(life) { closeLocked() }
    fun closeUpTo(id: Long) = synchronized(life) { if (lastId <= id) closeLocked() }
    private fun closeLocked() {
        // Destroy before closing buffered streams: their read/write locks may be held by IO.
        process?.destroyForcibly()
        process = null; input = null; output = null; active = null
    }
}
