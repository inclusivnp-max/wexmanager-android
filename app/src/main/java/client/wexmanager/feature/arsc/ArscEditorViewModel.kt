package client.wexmanager.feature.arsc

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import client.wexmanager.appContainer
import client.wexmanager.core.apk.ArscEntry
import client.wexmanager.core.apk.ArscPackage
import client.wexmanager.core.apk.ArscTable
import client.wexmanager.core.apk.ResValue
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
import java.util.zip.ZipFile

/** Строка списка: ресурс в выбранной конфигурации. */
@Immutable
data class ResRow(
    val index: Int,
    val name: String,
    val id: Int,
    val type: Int,
    val text: String,
    val complex: Boolean,
    val bag: List<Pair<String, String>> = emptyList(),
)

/** Конфигурация типа: квалификатор («» — по умолчанию) и сколько в ней ресурсов. */
@Immutable
data class ConfigItem(val position: Int, val qualifier: String, val count: Int)

/** Строка перевода: ключ, текст на исходном языке и текущий перевод. */
@Immutable
data class TranslationRow(val index: Int, val name: String, val source: String, val target: String?)

@Immutable
data class ArscState(
    val loading: Boolean = true,
    val error: String? = null,
    val packageName: String = "",
    val types: List<Pair<String, Int>> = emptyList(),
    val type: String = "",
    val configs: List<ConfigItem> = emptyList(),
    val config: Int = 0,
    val rows: List<ResRow> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val isApk: Boolean = true,
    val locales: List<String> = emptyList(),
    val version: Int = 0,
)

sealed interface ArscEvent {
    data class Message(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null) : ArscEvent
}

class ArscEditorViewModel(app: Application, private val path: String) : AndroidViewModel(app) {
    private val c = app.appContainer
    private val isApk = !path.endsWith(".arsc", ignoreCase = true)
    private val _state = MutableStateFlow(ArscState(isApk = isApk))
    val state: StateFlow<ArscState> = _state.asStateFlow()
    private val _events = Channel<ArscEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var table: ArscTable? = null

    /** Куда сохранять: сам APK или его копия в «Загрузки/WexManager», если в исходную папку писать нельзя. */
    @Volatile private var target: File = File(path)
    private val pkg: ArscPackage? get() = table?.packages?.firstOrNull()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = if (isApk) ZipFile(path).use { z ->
                        val e = z.getEntry("resources.arsc") ?: throw java.io.IOException("В APK нет resources.arsc")
                        z.getInputStream(e).use { it.readBytes() }
                    } else File(path).readBytes()
                    ArscTable.parse(bytes)
                }
            }
            result.onSuccess { t ->
                table = t
                val p = t.packages.firstOrNull()
                if (p == null) {
                    _state.update { it.copy(loading = false, error = "В таблице нет пакетов") }
                    return@onSuccess
                }
                val types = p.typeNames.mapIndexedNotNull { i, n ->
                    val chunks = p.types(i + 1)
                    if (chunks.isEmpty()) null else n to (p.specEntryCount(i + 1) ?: chunks.maxOf { it.entries.size })
                }
                val first = types.firstOrNull { it.first == "string" }?.first ?: types.firstOrNull()?.first.orEmpty()
                _state.update { it.copy(loading = false, packageName = p.name, types = types, dirty = false) }
                select(first, 0)
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    fun select(type: String, config: Int) {
        val t = table ?: return
        val p = pkg ?: return
        val typeId = p.typeIdOf(type) ?: return
        val chunks = p.types(typeId)
        val configs = chunks.mapIndexed { i, ch -> ConfigItem(i, ch.config.qualifier(), ch.entries.count { it != null }) }
        val pos = config.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        val rows = chunks.getOrNull(pos)?.entries?.filterNotNull()?.map { e -> row(t, p, e) }.orEmpty()
        val locales = p.types(p.typeIdOf("string") ?: -1).filter { it.config.onlyLocale() && !it.config.isDefault() }.map { it.config.qualifier() }.distinct().sorted()
        _state.update { it.copy(type = type, configs = configs, config = pos, rows = rows, locales = locales, version = it.version + 1) }
    }

    private fun row(t: ArscTable, p: ArscPackage, e: ArscEntry): ResRow = ResRow(
        index = e.index,
        name = p.key(e),
        id = e.resId(p),
        type = e.value.type,
        text = if (e.complex) "${e.bag.size} эл." else t.format(e.value),
        complex = e.complex,
        bag = if (e.complex) e.bag.map { (name, v) -> "0x" + Integer.toHexString(name) to t.format(v) } else emptyList(),
    )

    private fun entry(index: Int): ArscEntry? {
        val p = pkg ?: return null
        val s = state.value
        return p.types(p.typeIdOf(s.type) ?: return null).getOrNull(s.config)?.entries?.getOrNull(index)
    }

    /** Новое значение ресурса; ошибка формата — сообщением. */
    fun setValue(index: Int, text: String): Boolean {
        val t = table ?: return false
        val e = entry(index) ?: return false
        return try {
            t.setValue(e, text)
            _state.update { it.copy(dirty = true) }
            select(state.value.type, state.value.config)
            true
        } catch (ex: IllegalArgumentException) {
            _events.trySend(ArscEvent.Message(ex.message ?: "Неверное значение"))
            false
        }
    }

    /** Строки для перевода на [locale]: исходный текст — из конфигурации по умолчанию. */
    fun translationRows(locale: String): List<TranslationRow> {
        val t = table ?: return emptyList()
        val p = pkg ?: return emptyList()
        val typeId = p.typeIdOf("string") ?: return emptyList()
        val chunks = p.types(typeId)
        val base = chunks.firstOrNull { it.config.isDefault() } ?: chunks.firstOrNull() ?: return emptyList()
        val target = chunks.firstOrNull { it.config.onlyLocale() && it.config.qualifier() == locale }
        return base.entries.filterNotNull().filter { !it.complex && it.value.type == ResValue.TYPE_STRING }.map { e ->
            val tr = target?.entries?.getOrNull(e.index)?.takeIf { !it.complex }?.let { t.format(it.value) }
            TranslationRow(e.index, p.key(e), t.format(e.value), tr)
        }
    }

    fun applyTranslation(locale: String, values: Map<Int, String>) {
        val t = table ?: return
        val p = pkg ?: return
        if (values.isEmpty()) return
        runCatching { t.translate(p, locale, values) }
            .onSuccess {
                _state.update { it.copy(dirty = true) }
                select(state.value.type, state.value.config)
                _events.trySend(ArscEvent.Message("Переведено строк: ${values.size} ($locale)"))
            }
            .onFailure { _events.trySend(ArscEvent.Message(it.message ?: "Не удалось применить перевод")) }
    }

    fun save(then: () -> Unit = {}) {
        val t = table ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = t.encode()
                    ArscTable.parse(bytes) // самопроверка: результат обязан читаться
                    if (isApk) {
                        val src = File(path)
                        if (target == src && !(src.canWrite() && src.parentFile?.canWrite() == true)) {
                            // Установленное или системное приложение: правим копию.
                            @Suppress("DEPRECATION")
                            val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "WexManager").apply { mkdirs() }
                            var copy = File(dir, src.name)
                            var n = 2
                            while (copy.exists()) copy = File(dir, src.nameWithoutExtension + " ($n).apk").also { n++ }
                            src.copyTo(copy)
                            target = copy
                        }
                        val staged = File(getApplication<Application>().cacheDir, "arsc-${System.nanoTime()}.arsc")
                        try {
                            staged.writeBytes(bytes)
                            ArchiveEngine.modifyZip(target, mapOf("resources.arsc" to staged))
                        } finally {
                            staged.delete()
                        }
                    } else {
                        val target = File(path)
                        val tmp = File(target.parentFile, ".${target.name}.wexpart")
                        tmp.writeBytes(bytes)
                        if (!tmp.renameTo(target)) { tmp.delete(); throw java.io.IOException("Не удалось заменить ${target.name}") }
                    }
                }
            }
            _state.update { it.copy(saving = false) }
            result.onSuccess {
                _state.update { it.copy(dirty = false) }
                val where = if (target.path != path) "копию Загрузки/WexManager/${target.name}" else target.name
                if (isApk) _events.trySend(ArscEvent.Message("Ресурсы сохранены в $where. Подпишите APK перед установкой", "Подписать") { sign() })
                else _events.trySend(ArscEvent.Message("Сохранено"))
                then()
            }.onFailure { e -> _events.trySend(ArscEvent.Message("Не удалось сохранить: ${e.message ?: e.javaClass.simpleName}")) }
        }
    }

    fun sign() {
        c.taskManager.signApk(target, c.userData.data.value.activeSigningKey)
        _events.trySend(ArscEvent.Message("Подпись запущена — результат появится рядом как *_signed.apk"))
    }
}
