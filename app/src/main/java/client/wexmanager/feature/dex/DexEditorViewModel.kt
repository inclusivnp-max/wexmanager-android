package client.wexmanager.feature.dex

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.archive.ArchiveEngine
import client.wexmanager.core.dex.DexClassInfo
import client.wexmanager.core.dex.DexSession
import client.wexmanager.core.dex.SmaliEditException
import client.wexmanager.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Immutable
data class DexEditorState(
    val loading: Boolean = true,
    val progress: String = "",
    val error: String? = null,
    val title: String = "",
    val dexNames: List<String> = emptyList(),
    val classes: List<DexClassInfo> = emptyList(),
    val modified: Set<String> = emptySet(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val isArchive: Boolean = false,
    /** Класс, который сейчас декомпилируется в Java. */
    val decompiling: String? = null,
)

/** Совпадение поиска по коду: класс, строка (с 0) и сама строка. */
@Immutable
data class CodeHit(val descriptor: String, val line: Int, val text: String)

sealed interface DexEvent {
    data class Message(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null) : DexEvent
    data class Open(val route: Route.Editor) : DexEvent
}

class DexEditorViewModel(app: Application, private val path: String, private val entries: List<String>) : AndroidViewModel(app) {
    private val c = app.appContainer
    private val _state = MutableStateFlow(DexEditorState(title = File(path).name, isArchive = entries.isNotEmpty()))
    val state: StateFlow<DexEditorState> = _state.asStateFlow()
    private val _events = Channel<DexEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var session: DexSession? = null
    private val lock = Mutex()

    /** Классы, открытые в редакторе: временный файл → (дескриптор, время записи). */
    private val opened = HashMap<String, Pair<String, Long>>()
    private val workDir = File(app.cacheDir, "dex/" + UUID.randomUUID().toString().take(8))

    private val _hits = MutableStateFlow<List<CodeHit>?>(null)
    val hits: StateFlow<List<CodeHit>?> = _hits.asStateFlow()
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()
    private var searchJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.Default) {
                    DexSession.open(File(path), entries) { done, total ->
                        val name = if (entries.isEmpty()) File(path).name else entries.getOrNull(done) ?: ""
                        _state.update { it.copy(progress = if (done < total) "Разбор $name (${done + 1} из $total)" else "Готово") }
                    }
                }
            }
            result.onSuccess { s ->
                lock.withLock { session = s }
                publish(s)
                _state.update { it.copy(loading = false) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun publish(s: DexSession) {
        val classes = s.classes().sortedBy { it.descriptor }
        _state.update {
            it.copy(
                dexNames = s.units.map { u -> u.name },
                classes = classes,
                modified = s.modifiedClasses.toSet(),
                dirty = s.modifiedUnits.isNotEmpty(),
            )
        }
    }

    /** Открывает класс в редакторе кода (временный .smali); [line] — строка, к которой перейти. */
    fun openClass(descriptor: String, line: Int? = null) {
        viewModelScope.launch {
            val s = lock.withLock { session } ?: return@launch
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    val text = s.smali(descriptor)
                    val rel = descriptor.removePrefix("L").removeSuffix(";") + ".smali"
                    val f = File(workDir, rel)
                    f.parentFile?.mkdirs()
                    f.writeText(text)
                    f
                }
            }.getOrElse { e ->
                _events.trySend(DexEvent.Message("Не удалось открыть: ${e.message}"))
                return@launch
            }
            opened[file.path] = descriptor to file.lastModified()
            if (line != null) c.userData.saveEditorPosition(file.path, line)
            _events.trySend(DexEvent.Open(Route.Editor(path = file.path)))
        }
    }

    private val decompiler = client.wexmanager.core.dex.JavaDecompiler()

    /** Java-код класса (jadx) — открывается в редакторе только для чтения. */
    fun openJava(descriptor: String) {
        if (state.value.decompiling != null) return
        _state.update { it.copy(decompiling = descriptor) }
        viewModelScope.launch {
            val file = runCatching {
                val s = lock.withLock { session } ?: throw java.io.IOException("DEX не загружен")
                withContext(Dispatchers.Default) {
                    // Байты DEX берутся под замком сеанса: правка не должна менять дерево во время сборки.
                    val key = lock.withLock { s.dexKeyFor(descriptor) } ?: throw java.io.IOException("Класс не найден")
                    val java = decompiler.decompile(key, { kotlinx.coroutines.runBlocking { lock.withLock { s.dexBytesFor(descriptor) } } }, descriptor)
                    val rel = descriptor.removePrefix("L").removeSuffix(";").substringBefore('$') + ".java"
                    File(workDir, "java/$rel").apply { parentFile?.mkdirs(); writeText(java) }
                }
            }
            _state.update { it.copy(decompiling = null) }
            file.onSuccess { f -> _events.trySend(DexEvent.Open(Route.Editor(path = f.path, readOnly = true, lockReadOnly = true))) }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _events.trySend(DexEvent.Message("Не удалось декомпилировать: ${e.message ?: e.javaClass.simpleName}"))
                }
        }
    }

    /** Вернулись из редактора: правленные классы разбираются и попадают в дерево. */
    fun syncEdited() {
        if (opened.isEmpty()) return
        viewModelScope.launch {
            val s = lock.withLock { session } ?: return@launch
            var changed = false
            for ((filePath, entry) in opened.toMap()) {
                val (descriptor, stamp) = entry
                val f = File(filePath)
                val now = f.lastModified()
                if (!f.isFile || now == stamp) continue
                opened[filePath] = descriptor to now
                val result = withContext(Dispatchers.Default) {
                    runCatching {
                        val text = f.readText()
                        lock.withLock { s.apply(descriptor, text) }
                    }
                }
                result.onSuccess { info ->
                    opened[filePath] = info.descriptor to now
                    changed = true
                }.onFailure { e ->
                    val name = descriptor.removePrefix("L").removeSuffix(";").substringAfterLast('/')
                    val msg = if (e is SmaliEditException) e.message else e.message ?: e.javaClass.simpleName
                    _events.trySend(DexEvent.Message("$name: $msg", "Открыть") { reopen(filePath) })
                }
            }
            if (changed) publish(s)
        }
    }

    private fun reopen(filePath: String) {
        _events.trySend(DexEvent.Open(Route.Editor(path = filePath)))
    }

    fun deleteClass(descriptor: String) {
        viewModelScope.launch {
            val s = lock.withLock { session } ?: return@launch
            lock.withLock { s.delete(descriptor) }
            opened.entries.removeAll { it.value.first == descriptor }
            publish(s)
        }
    }

    fun createClass(javaName: String, dexIndex: Int) {
        viewModelScope.launch {
            val s = lock.withLock { session } ?: return@launch
            val name = javaName.trim().removePrefix("L").removeSuffix(";").replace('.', '/')
            if (name.isEmpty() || name.any { it.isWhitespace() }) {
                _events.trySend(DexEvent.Message("Неверное имя класса"))
                return@launch
            }
            runCatching { lock.withLock { s.create("L$name;", dexIndex) } }
                .onSuccess { publish(s); openClass(it.descriptor) }
                .onFailure { _events.trySend(DexEvent.Message(it.message ?: "Не удалось создать класс")) }
        }
    }

    /** Поиск по тексту smali всех классов (строки, методы, поля, код), как «Поиск» в Dex Editor. */
    /**
     * Замена во всех классах (как «Поиск и замена» в DEX-редакторе MT): каждый изменённый класс разбирается
     * и проверочно собирается; класс с ошибкой сборки пропускается и попадает в отчёт.
     */
    fun replaceInCode(query: String, replacement: String, regex: Boolean, ignoreCase: Boolean) {
        if (query.isEmpty() || _searching.value) return
        val pattern = runCatching {
            if (regex) Regex(query, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
            else Regex(Regex.escape(query), if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet())
        }.getOrElse {
            _events.trySend(DexEvent.Message("Неверное выражение: ${it.message}"))
            return
        }
        // Обычный текст: «$» и «\» в замене — буквально.
        val repl = if (regex) replacement else Regex.escapeReplacement(replacement)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val s = lock.withLock { session } ?: return@launch
            _searching.value = true
            var changed = 0
            var count = 0
            val failed = ArrayList<String>()
            try {
                withContext(Dispatchers.Default) {
                    for (cls in state.value.classes) {
                        ensureActive()
                        val text = lock.withLock { runCatching { s.smali(cls.descriptor) }.getOrNull() } ?: continue
                        val n = pattern.findAll(text).count()
                        if (n == 0) continue
                        val updated = runCatching { pattern.replace(text, repl) }.getOrElse { e -> failed += "${cls.javaName}: ${e.message}"; continue }
                        if (updated == text) continue
                        val ok = lock.withLock { runCatching { s.apply(cls.descriptor, updated) } }
                        ok.onSuccess { changed++; count += n }.onFailure { e -> failed += "${cls.javaName}: ${e.message ?: e.javaClass.simpleName}" }
                    }
                }
            } finally {
                _searching.value = false
            }
            publish(s)
            _hits.value = null
            val text = buildString {
                append("Заменено: $count в ${changed} ${client.wexmanager.core.util.Format.plural(changed, "классе", "классах", "классах")}")
                if (failed.isNotEmpty()) append(" · пропущено ${failed.size}: ").append(failed.first().take(120))
            }
            _events.trySend(DexEvent.Message(text))
        }
    }

    fun searchCode(query: String, regex: Boolean, ignoreCase: Boolean) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _hits.value = null
            return
        }
        val pattern = if (regex) {
            runCatching { Regex(query, if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()) }.getOrElse {
                _events.trySend(DexEvent.Message("Неверное выражение: ${it.message}"))
                return
            }
        } else null
        val matcher: (String) -> Boolean = { line -> pattern?.containsMatchIn(line) ?: line.contains(query, ignoreCase) }
        searchJob = viewModelScope.launch {
            val s = lock.withLock { session } ?: return@launch
            _searching.value = true
            _hits.value = emptyList()
            try {
                val classes = state.value.classes
                val found = ArrayList<CodeHit>()
                withContext(Dispatchers.Default) {
                    for ((k, cls) in classes.withIndex()) {
                        ensureActive()
                        val text = lock.withLock { runCatching { s.smali(cls.descriptor) }.getOrNull() } ?: continue
                        var lineNo = 0
                        for (line in text.lineSequence()) {
                            if (matcher(line)) {
                                found += CodeHit(cls.descriptor, lineNo, line.trim().take(160))
                                if (found.size >= MAX_HITS) break
                            }
                            lineNo++
                        }
                        if (found.size >= MAX_HITS) break
                        if (k % 200 == 0) _hits.value = found.toList()
                    }
                }
                _hits.value = found.toList()
                if (found.size >= MAX_HITS) _events.trySend(DexEvent.Message("Показаны первые $MAX_HITS совпадений"))
            } finally {
                _searching.value = false
            }
        }
    }

    fun clearCodeSearch() {
        searchJob?.cancel()
        _hits.value = null
    }

    /** Собирает изменённые DEX и записывает их на место (в APK — с перепаковкой). */
    fun save(then: () -> Unit = {}) {
        if (_state.value.saving) return
        viewModelScope.launch {
            val s = lock.withLock { session } ?: return@launch
            if (s.modifiedUnits.isEmpty()) {
                then()
                return@launch
            }
            _state.update { it.copy(saving = true) }
            // Сборка, запись и отметка «сохранено» — под одним замком: правка, сделанная во время записи,
            // дождётся её конца и останется несохранённой, а не будет молча сброшена markSaved().
            val result = runCatching {
                lock.withLock {
                    val built = withContext(Dispatchers.Default) { s.build() }
                    withContext(Dispatchers.IO) { write(s, built) }
                    s.markSaved()
                }
            }
            _state.update { it.copy(saving = false) }
            result.onSuccess {
                publish(s)
                if (s.isArchive) {
                    _events.trySend(DexEvent.Message("DEX сохранён в ${File(path).name}. Подпишите APK перед установкой", "Подписать") { sign() })
                } else {
                    _events.trySend(DexEvent.Message("Сохранено"))
                }
                then()
            }.onFailure { e ->
                _events.trySend(DexEvent.Message("Не удалось собрать DEX: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    private suspend fun write(s: DexSession, built: Map<String, ByteArray>) {
        val target = File(path)
        if (s.isArchive) {
            val staged = built.mapValues { (name, bytes) ->
                File(workDir, "out/$name").also { it.parentFile?.mkdirs(); it.writeBytes(bytes) }
            }
            try {
                ArchiveEngine.modifyZip(target, staged)
            } finally {
                staged.values.forEach { it.delete() }
            }
        } else {
            val bytes = built.values.single()
            val tmp = File(target.parentFile, ".${target.name}.wexpart")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                tmp.delete()
                throw java.io.IOException("Не удалось заменить ${target.name}")
            }
        }
    }

    fun sign() {
        c.taskManager.signApk(File(path), c.userData.data.value.activeSigningKey)
        _events.trySend(DexEvent.Message("Подпись запущена — результат появится рядом как *_signed.apk"))
    }

    override fun onCleared() {
        // close() ждёт идущую декомпиляцию (секунды на большом DEX) — не на главном потоке.
        Thread { decompiler.close() }.apply { isDaemon = true }.start()
        workDir.deleteRecursively()
    }

    companion object {
        const val MAX_HITS = 2000
    }
}
