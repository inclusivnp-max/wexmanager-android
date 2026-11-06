package client.wexmanager.feature.editor

import client.wexmanager.feature.editor.lang.Language
import client.wexmanager.feature.editor.lang.Languages
import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.archive.ArchiveEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class LineEnding(val label: String, val value: String) { LF("LF", "\n"), CRLF("CRLF", "\r\n"), CR("CR", "\r") }

@Immutable
data class EditorState(
    val loading: Boolean = true,
    val error: String? = null,
    val fileName: String = "",
    val charset: String = "UTF-8",
    val hasBom: Boolean = false,
    val lineEnding: LineEnding = LineEnding.LF,
    val language: Language = Languages.PLAIN,
    val modified: Boolean = false,
    val saving: Boolean = false,
    val readOnly: Boolean = false,
    val sizeBytes: Long = 0,
    /** Число строк при открытии — для режима «большой файл» (без прохода по тексту на каждую правку). */
    val lineCountHint: Int = 0,
    /** Текст для загрузки в редактор; увеличение [textVersion] — сигнал заменить содержимое. */
    val text: String = "",
    val textVersion: Int = 0,
)

sealed interface EditorEvent {
    data class Message(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null) : EditorEvent
    data object Saved : EditorEvent
    /** Файл изменили снаружи, а в редакторе есть несохранённые правки. */
    data object DiskChanged : EditorEvent
}

class EditorViewModel(
    app: Application,
    private val path: String,
    private val archive: String?,
    private val entry: String?,
    readOnly: Boolean,
    private val sourceUri: String? = null,
    private val initialCharset: String? = null,
    /** Исходный бинарный XML: сохраняем не текст, а его кодировку обратно в AXML. */
    private val binarySource: String? = null,
) : AndroidViewModel(app) {

    private var ownsDirty = false

    private fun setDirty(value: Boolean) {
        if (ownsDirty == value) return
        ownsDirty = value
        getApplication<Application>().appContainer.setEditorDirty(this, path, value)
    }

    override fun onCleared() {
        setDirty(false)
    }

    private val _state = MutableStateFlow(EditorState(fileName = File(path).name, readOnly = readOnly))
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val isArchiveEntry: Boolean get() = archive != null && entry != null

    init {
        load(initialCharset)
        if (binarySource != null && archive != null) {
            viewModelScope.launch(Dispatchers.IO) { archiveStamp = stamp(File(archive)) }
        }
    }

    fun load(forcedCharset: String?) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { withContext(Dispatchers.IO) { readFile(forcedCharset).also { diskStamp = stamp(File(path)) } } }
            result.onSuccess { r ->
                _state.update {
                    it.copy(
                        loading = false,
                        charset = r.charset,
                        hasBom = r.bom,
                        lineEnding = r.lineEnding,
                        language = if (it.textVersion == 0) Languages.detect(File(path).name, r.text.take(20_000)) else it.language,
                        text = r.text,
                        textVersion = it.textVersion + 1,
                        modified = false,
                        sizeBytes = r.size,
                        lineCountHint = r.lines,
                        readOnly = it.readOnly || !File(path).canWrite(),
                    )
                }
                setDirty(false)
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Не удалось открыть файл") }
            }
        }
    }

    private class ReadResult(val text: String, val charset: String, val bom: Boolean, val lineEnding: LineEnding, val size: Long) {
        val lines: Int = text.count { it == '\n' } + 1 // один раз при открытии, в фоне
    }

    private fun readFile(forced: String?): ReadResult {
        val f = File(path)
        if (!f.exists()) throw IOException("Файл не найден")
        if (f.length() > MAX_SIZE) throw IOException("Файл слишком большой для редактора (${f.length() / 1024 / 1024} МБ). Откройте его в HEX-просмотре.")
        val bytes = f.readBytes()
        var offset = 0
        var bom = false
        val charset: Charset = when {
            forced != null -> Charset.forName(forced)
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> {
                offset = 3; bom = true; Charsets.UTF_8
            }
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> { offset = 2; bom = true; Charsets.UTF_16LE }
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> { offset = 2; bom = true; Charsets.UTF_16BE }
            isValidUtf8(bytes) -> Charsets.UTF_8
            else -> fallbackCharset(bytes)
        }
        val text = String(bytes, offset, bytes.size - offset, charset)
        val ending = when {
            text.contains("\r\n") -> LineEnding.CRLF
            text.contains('\r') && !text.contains('\n') -> LineEnding.CR
            else -> LineEnding.LF
        }
        val normalized = if (ending == LineEnding.LF) text else text.replace("\r\n", "\n").replace('\r', '\n')
        return ReadResult(normalized, charset.name(), bom, ending, f.length())
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    /** Если не UTF-8: много байтов 0xC0–0xFF — вероятно кириллица (windows-1251), иначе — настройка по умолчанию. */
    private fun fallbackCharset(bytes: ByteArray): Charset {
        val sample = bytes.take(64 * 1024)
        val high = sample.count { (it.toInt() and 0xFF) >= 0xC0 }
        if (high > sample.size / 20) return Charset.forName("windows-1251")
        val def = getApplication<Application>().appContainer.settings.current.editorDefaultEncoding
        return runCatching { Charset.forName(def) }.getOrDefault(Charsets.ISO_8859_1)
    }

    /** Растёт с каждой правкой: сохранение снимает «изменён», только если за время записи правок не было. */
    @Volatile private var editVersion = 0L

    /** «Не сохранять»: следующий stash из снимаемого виджета не возвращает отброшенный текст. */
    @Volatile private var skipNextStash = false

    fun markModified() {
        editVersion++
        if (!_state.value.modified) _state.update { it.copy(modified = true) }
        setDirty(true)
    }

    /** Откатить несохранённые правки. В состоянии остаётся последний загруженный или записанный текст. */
    fun discardEdits() {
        skipNextStash = true
        _state.update { it.copy(modified = false) }
        setDirty(false)
    }

    /**
     * Текст из виджета в состояние, без смены [EditorState.textVersion]:
     * тот же редактор не перечитывает файл, новый виджет после поворота берёт уже набранное.
     */
    fun stash(text: String) {
        if (skipNextStash) {
            skipNextStash = false
            return
        }
        if (_state.value.text != text) _state.update { it.copy(text = text) }
    }

    fun setLanguage(language: Language) = _state.update { it.copy(language = language) }
    fun setReadOnly(readOnly: Boolean) = _state.update { it.copy(readOnly = readOnly) }
    fun setLineEnding(ending: LineEnding) {
        editVersion++
        _state.update { it.copy(lineEnding = ending, modified = true) }
        setDirty(true)
    }

    /** Перечитать файл в другой кодировке (если есть несохранённые изменения — они теряются, UI спрашивает заранее). */
    fun reopenWithCharset(name: String) = load(name)

    /** Сохранить в другой кодировке без перечитывания. */
    fun convertCharset(name: String) {
        editVersion++
        _state.update { it.copy(charset = name, hasBom = false, modified = true) }
        setDirty(true)
    }

    /** [leaving] — после сохранения экран закроется: сообщения отдаём браузеру, а не своему snackbar. */
    fun save(text: String, leaving: Boolean = false, onDone: (Boolean) -> Unit = {}) {
        val st = _state.value
        if (st.readOnly || st.saving) {
            onDone(false)
            return
        }
        val savedVersion = editVersion
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    if (binarySource != null) return@withContext saveBinary(text)
                    val bytes = encode(st, text)
                    val target = File(path)
                    val tmp = File(target.parentFile, ".${target.name}.wexsave")
                    try {
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(target)) {
                            // Некоторые ФС не позволяют rename поверх — пишем напрямую.
                            target.writeBytes(bytes)
                        }
                    } finally {
                        tmp.delete() // после успешного rename его уже нет; после ошибки — не оставляем мусор у пользователя
                    }
                    diskStamp = stamp(target)
                    if (archive != null && entry != null) {
                        ArchiveEngine.modifyZip(File(archive), mapOf(entry to target))
                    }
                    if (sourceUri != null) {
                        // Файл пришёл из другого приложения: записываем изменения обратно в источник.
                        val out2 = getApplication<Application>().contentResolver.openOutputStream(android.net.Uri.parse(sourceUri), "wt")
                            ?: throw IOException("источник не разрешает запись")
                        out2.use { it.write(bytes) }
                    }
                    bytes.size.toLong()
                }
            }
            result.onSuccess { size ->
                // Набрали что-то, пока шла запись, — это ещё не на диске.
                val stillModified = editVersion != savedVersion
                _state.update { it.copy(saving = false, modified = stillModified, sizeBytes = size) }
                setDirty(stillModified)
                _events.send(EditorEvent.Saved)
                val apk = archive?.takeIf { binarySource != null && it.lowercase().endsWith(".apk") }
                if (apk != null) {
                    // Подпись после правки обязательна: без неё APK не установится.
                    val c = getApplication<Application>().appContainer
                    val sign = { c.taskManager.signApk(File(apk), c.userData.data.value.activeSigningKey); Unit }
                    val text = "Сохранено в APK — подпишите его, чтобы установить"
                    if (leaving) c.appMessages.tryEmit(client.wexmanager.AppMessage(text, "Подписать", sign))
                    else _events.send(EditorEvent.Message(text, "Подписать", sign))
                    onDone(true)
                    return@onSuccess
                }
                _events.send(
                    EditorEvent.Message(
                        when {
                            isArchiveEntry -> "Сохранено и обновлено в архиве"
                            sourceUri != null -> "Сохранено в исходный файл"
                            else -> "Сохранено"
                        },
                    ),
                )
                onDone(true)
            }.onFailure { e ->
                _state.update { it.copy(saving = false) }
                _events.send(EditorEvent.Message("Ошибка сохранения: ${e.message}"))
                onDone(false)
            }
        }
    }

    /**
     * Сохранение расшифровки бинарного файла: AXML кодируется обратно (типы значений — из исходника),
     * csmali собирается в `.class`; результат пишется в исходный файл или в архив. Текстовая копия тоже обновляется.
     */
    private suspend fun saveBinary(text: String): Long {
        val source = File(binarySource!!)
        val bytes = if (client.wexmanager.core.dex.ClassFiles.isClass(source)) {
            client.wexmanager.core.dex.ClassFiles.encode(text)
        } else try {
            client.wexmanager.core.apk.AxmlFiles.encode(text, source)
        } catch (e: client.wexmanager.core.apk.AxmlEncodeException) {
            throw IOException(e.message)
        }
        val apk = if (archive != null && entry != null) File(archive) else null
        // APK заменили другой сборкой после открытия — не вставляем в неё старый манифест.
        if (apk != null && archiveStamp != null && archiveStamp != stamp(apk)) {
            throw IOException("APK изменился после открытия — откройте манифест заново")
        }
        val staged = File(source.parentFile, ".wex-${java.util.UUID.randomUUID().toString().take(12)}.part")
        try {
            staged.writeBytes(bytes)
            // Сначала архив, и только после успеха — кешированная копия: иначе при сбое
            // повторное открытие показало бы «сохранённые» правки, которых в APK нет.
            if (apk != null) {
                ArchiveEngine.modifyZip(apk, mapOf(entry!! to staged))
                archiveStamp = stamp(apk) // своё изменение — новая отметка, даже если дальше что-то не запишется
            }
            if (!staged.renameTo(source)) source.writeBytes(bytes)
        } finally {
            staged.delete()
        }
        if (apk != null) archiveStamp = stamp(apk)
        File(path).writeText(text)
        return bytes.size.toLong()
    }

    /** Время и размер файла, каким его прочитали или записали, — чтобы заметить правку другим приложением. */
    @Volatile private var diskStamp: Pair<Long, Long>? = null

    /**
     * Вернулись в редактор: если файл изменили снаружи (другое приложение, синхронизация), без своих правок
     * перечитываем его, а со своими — спрашиваем. Копии из архивов, расшифровки и content:// не отслеживаются.
     */
    fun checkDisk() {
        if (archive != null || binarySource != null || sourceUri != null) return
        val known = diskStamp ?: return
        val st = _state.value
        if (st.loading || st.saving) return
        viewModelScope.launch {
            val now = withContext(Dispatchers.IO) { File(path).takeIf { it.isFile }?.let { stamp(it) } } ?: return@launch
            if (now == known || _state.value.saving) return@launch
            if (!_state.value.modified) {
                reloadFromDisk()
                _events.send(EditorEvent.Message("Файл изменён другим приложением — перечитан"))
            } else {
                diskStamp = now // одно предупреждение на одно изменение
                _events.send(EditorEvent.DiskChanged)
            }
        }
    }

    /** Перечитать с диска в той же кодировке (с BOM — определяется заново). */
    fun reloadFromDisk() {
        val st = _state.value
        load(if (st.hasBom) null else st.charset)
    }

    /** Отметка архива (время и размер) на момент открытия — защита от записи в подменённый APK. */
    @Volatile private var archiveStamp: Pair<Long, Long>? = null
    private fun stamp(f: File) = f.lastModified() to f.length()

    /** Текст → байты с окончаниями строк, кодировкой и BOM файла. */
    private fun encode(st: EditorState, text: String): ByteArray {
        // Редактор хранит переводы строк как LF, но одиночный CR мог прийти из вставки или инструментов.
        val lf = if ('\r' !in text) text else text.replace("\r\n", "\n").replace('\r', '\n')
        val out = if (st.lineEnding == LineEnding.LF) lf else lf.replace("\n", st.lineEnding.value)
        val charset = Charset.forName(st.charset)
        // Строгое кодирование: непредставимые символы не превращаются молча в «?».
        val body = try {
            val buf = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(java.nio.CharBuffer.wrap(out))
            ByteArray(buf.remaining()).also { buf.get(it) }
        } catch (e: CharacterCodingException) {
            throw IOException("в кодировке ${st.charset} нельзя сохранить некоторые символы — выберите UTF-8")
        }
        return if (st.hasBom) bom(charset) + body else body
    }

    /** «Сохранить как»: пишет текст в новый файл; исходный остаётся без изменений. */
    val charset: String get() = _state.value.charset

    /**
     * Готовит сравнение «на диске ↔ правки»: оба текста пишутся в кеш в UTF-8,
     * чтобы экран сравнения не зависел от кодировки исходного файла.
     */
    fun prepareDiff(current: String, onReady: (left: String, right: String) -> Unit) {
        val st = _state.value
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val disk = File(path)
                    if (!disk.exists()) throw IOException("файла на диске больше нет")
                    if (disk.length() > MAX_SIZE) throw IOException("файл слишком большой для сравнения")
                    val bytes = disk.readBytes()
                    val skip = if (st.hasBom) bom(Charset.forName(st.charset)).size else 0
                    val saved = String(bytes, skip, bytes.size - skip, Charset.forName(st.charset))
                        .replace("\r\n", "\n").replace('\r', '\n')
                    val dir = File(getApplication<Application>().cacheDir, "diff").apply { deleteRecursively(); mkdirs() }
                    val name = disk.name
                    val left = File(dir, "$name · на диске").apply { writeText(saved) }
                    val right = File(dir, "$name · правки").apply { writeText(current.replace("\r\n", "\n").replace('\r', '\n')) }
                    left.path to right.path
                }
            }
            r.onSuccess { (l, rr) -> onReady(l, rr) }
                .onFailure { _events.send(EditorEvent.Message("Не удалось сравнить: ${it.message}")) }
        }
    }

    /** Текущий текст (с несохранёнными правками) во временный файл для предпросмотра; ссылки — от папки оригинала. */
    fun preparePreview(current: String, onReady: (path: String, base: String?) -> Unit) {
        viewModelScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = File(getApplication<Application>().cacheDir, "preview").apply { deleteRecursively(); mkdirs() }
                    File(dir, File(path).name).apply { writeText(current) }.path
                }
            }
            r.onSuccess { onReady(it, File(path).parent) }
                .onFailure { _events.send(EditorEvent.Message("Не удалось открыть предпросмотр: ${it.message}")) }
        }
    }

    fun saveAs(text: String, targetPath: String, onDone: (Boolean) -> Unit) {
        val st = _state.value
        if (st.saving) {
            onDone(false)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = encode(st, text)
                    val target = File(targetPath)
                    val dir = target.parentFile ?: throw IOException("некорректный путь")
                    if (!dir.isDirectory && !dir.mkdirs()) throw IOException("не удалось создать папку ${dir.path}")
                    val tmp = File(dir, ".${target.name}.wexsave")
                    try {
                        tmp.writeBytes(bytes)
                        // rename не удался (ФС не разрешает замену) — пишем напрямую, не удаляя существующий файл заранее.
                        if (!tmp.renameTo(target)) target.writeBytes(bytes)
                    } finally {
                        tmp.delete()
                    }
                }
            }
            _state.update { it.copy(saving = false) }
            result.onSuccess {
                setDirty(false) // правки ушли в новый файл, исходный не изменён
                onDone(true)
            }.onFailure { e ->
                _events.send(EditorEvent.Message("Ошибка сохранения: ${e.message}"))
                onDone(false)
            }
        }
    }

    private fun bom(charset: Charset): ByteArray = when (charset) {
        Charsets.UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        Charsets.UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        Charsets.UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        else -> ByteArray(0)
    }

    companion object {
        const val MAX_SIZE = 24L * 1024 * 1024
        val CHARSETS = listOf("UTF-8", "UTF-16LE", "UTF-16BE", "windows-1251", "KOI8-R", "IBM866", "ISO-8859-1", "windows-1252", "GBK", "Shift_JIS", "US-ASCII")
    }
}
