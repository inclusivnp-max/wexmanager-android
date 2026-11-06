package client.wexmanager.feature.viewer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.BrowserRequest
import client.wexmanager.appContainer
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.copyToSystemClipboard
import client.wexmanager.ui.components.ConfirmDialog
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.FastScroller
import client.wexmanager.ui.components.asScrollTarget
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val PAGE_SIZE = 64 * 1024
private const val MAX_PAGES = 48
private const val SEARCH_CHUNK = 1 shl 20

/** Тип файла, определённый по сигнатуре. */
internal class FileTypeInfo(val label: String, val detail: String?, val icon: ImageVector)

internal class SearchResult(val offset: Long, val wrapped: Boolean)

/**
 * Состояние HEX-просмотра/редактора. Файл читается страницами по 64 КБ через [RandomAccessFile]
 * с LRU-кешем — целиком в память не загружается. Правки хранятся разреженной картой [HexEdits]
 * и накладываются поверх страниц при отрисовке, поиске и чтении значений.
 */
internal class HexViewModel(val path: String, private val forceReadOnly: Boolean = false) : ViewModel() {
    private val file = File(path)

    var fileSize by mutableLongStateOf(-1L)
    /** Время изменения файла при открытии — защита от записи поверх чужих изменений. */
    private var loadedMtime = 0L
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var fileType by mutableStateOf<FileTypeInfo?>(null)
        private set

    /** Увеличивается при загрузке очередной страницы — по нему строки перерисовываются. */
    var pageVersion by mutableIntStateOf(0)
        private set

    var selected by mutableStateOf<Long?>(null)
    var match by mutableStateOf<LongRange?>(null)

    var searching by mutableStateOf(false)
        private set
    var searchProgress by mutableFloatStateOf(0f)
        private set
    private var searchJob: Job? = null

    // ---- правка
    val edits = HexEdits()

    /** Файл доступен на запись и не больше 2 ГБ. */
    var editable by mutableStateOf(false)
        private set
    var editBlockReason by mutableStateOf<String?>(null)
        private set
    var editMode by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var saveProgress by mutableFloatStateOf(0f)
        private set
    var backupOnSave by mutableStateOf(true)

    // Кеш и множество загружаемых страниц трогаются только из главного потока.
    private val cache = object : LinkedHashMap<Long, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?) = size > MAX_PAGES
    }
    private val pending = HashSet<Long>()

    /** Поколение кеша: чтения, начатые до сохранения, отбрасываются. */
    private var generation = 0
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(2)

    private val rafLock = Any()
    private var raf: RandomAccessFile? = null
    private var closed = false

    init {
        viewModelScope.launch {
            try {
                val (len, head, writable) = withContext(Dispatchers.IO) {
                    if (!file.exists()) throw FileNotFoundException("Файл не найден")
                    if (!file.canRead()) throw IOException("Нет доступа к файлу")
                    val len = file.length()
                    loadedMtime = file.lastModified()
                    Triple(len, if (len > 0) readPage(0, len) else ByteArray(0), file.canWrite())
                }
                if (head.isNotEmpty()) cache[0L] = head
                fileType = detectFileType(head, file.name)
                editBlockReason = when {
                    forceReadOnly -> "копия из архива — только просмотр"
                    len == 0L -> "файл пуст"
                    !writable -> "нет прав на запись"
                    len > MAX_EDITABLE_SIZE -> "файл больше 2 ГБ"
                    else -> null
                }
                editable = editBlockReason == null
                fileSize = len
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadError = e.message ?: e.javaClass.simpleName
            }
        }
    }

    /** Страница [index] из кеша или null (тогда нужно вызвать [request]). */
    fun page(index: Long): ByteArray? = cache[index]

    fun request(index: Long) {
        if (cache.containsKey(index) || !pending.add(index)) return
        val size = fileSize
        val gen = generation
        viewModelScope.launch {
            val data = try {
                withContext(ioDispatcher) { readPage(index, size) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            pending.remove(index)
            if (gen != generation) {
                // Файл перезаписан, пока шло чтение, — читаем заново.
                request(index)
                return@launch
            }
            if (data != null) {
                cache[index] = data
                pageVersion++
            }
        }
    }

    private fun readPage(index: Long, size: Long): ByteArray {
        val start = index * PAGE_SIZE
        val len = min(PAGE_SIZE.toLong(), size - start).toInt().coerceAtLeast(0)
        val buf = ByteArray(len)
        synchronized(rafLock) {
            if (closed) throw CancellationException()
            val r = raf ?: RandomAccessFile(file, "r").also { raf = it }
            r.seek(start)
            r.readFully(buf)
        }
        return buf
    }

    /** Исходные (без правок) байты файла. Блокирующий вызов — только на IO. */
    private fun readRawBlocking(offset: Long, count: Int): ByteArray {
        val n = min(count.toLong(), fileSize - offset).toInt().coerceAtLeast(0)
        val buf = ByteArray(n)
        synchronized(rafLock) {
            if (closed) return ByteArray(0)
            val r = raf ?: RandomAccessFile(file, "r").also { raf = it }
            r.seek(offset)
            r.readFully(buf)
        }
        return buf
    }

    /** Чтение до [count] байт с позиции [offset] с наложенными правками (для панели значений). */
    suspend fun readAt(offset: Long, count: Int): ByteArray = withContext(Dispatchers.IO) {
        val buf = readRawBlocking(offset, count)
        edits.patches.overlay(offset, buf, 0, buf.size)
        buf
    }

    /** Исходный байт из кеша страниц или -1, если страница не загружена. */
    fun cachedOriginal(offset: Long): Int {
        val p = cache[offset / PAGE_SIZE] ?: return -1
        val i = (offset % PAGE_SIZE).toInt()
        return if (i < p.size) p[i].toInt() and 0xFF else -1
    }

    suspend fun readOriginalByte(offset: Long): Int {
        if (offset !in 0 until fileSize) return -1
        cachedOriginal(offset).let { if (it >= 0) return it }
        return try {
            withContext(Dispatchers.IO) { readRawBlocking(offset, 1) }.firstOrNull()?.toInt()?.and(0xFF) ?: -1
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            -1
        }
    }

    /** Текущее значение байта с учётом правок. */
    suspend fun byteAt(offset: Long): Int = edits.get(offset).takeIf { it >= 0 } ?: readOriginalByte(offset)

    fun find(
        pattern: ByteArray,
        ignoreCase: Boolean,
        forward: Boolean,
        from: Long,
        onResult: (SearchResult?) -> Unit,
    ) {
        if (pattern.isEmpty() || fileSize <= 0) return
        searchJob?.cancel()
        searching = true
        searchProgress = 0f
        val size = fileSize
        val patches = edits.patches
        searchJob = viewModelScope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    RandomAccessFile(file, "r").use { r ->
                        val searcher = Searcher(r, size, pattern, ignoreCase, patches) { scanned ->
                            searchProgress = (scanned.toFloat() / size).coerceIn(0f, 1f)
                        }
                        if (forward) {
                            val start = from.coerceIn(0, size)
                            searcher.forward(start, size)?.let { SearchResult(it, false) }
                                ?: searcher.forward(0, start)?.let { SearchResult(it, true) }
                        } else {
                            val end = from.coerceIn(0, size)
                            searcher.backward(end, 0)?.let { SearchResult(it, false) }
                                ?: searcher.backward(size, end)?.let { SearchResult(it, true) }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            searching = false
            if (result != null) {
                match = result.offset until result.offset + pattern.size
                selected = result.offset
            }
            onResult(result)
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        searching = false
    }

    // ------------------------------------------------------------------ Правка

    private val canEdit: Boolean get() = editMode && !edits.busy && !saving

    fun setEditMode(on: Boolean, firstVisible: Long): Boolean {
        if (on && !editable) return false
        editMode = on
        edits.nibble = -1
        if (on && selected == null && fileSize > 0) selected = firstVisible.coerceIn(0, fileSize - 1)
        return true
    }

    fun onByteTap(offset: Long) {
        if (editMode) {
            selected = offset
            edits.nibble = -1
        } else {
            selected = if (selected == offset) null else offset
        }
    }

    /** Ввод hex-цифры: первая запоминается, вторая записывает байт и сдвигает курсор. Возвращает новый курсор. */
    fun typeDigit(d: Int): Long? {
        val off = selected ?: return null
        if (!canEdit) return null
        val hi = edits.nibble
        if (hi < 0) {
            edits.nibble = d
            return off
        }
        edits.nibble = -1
        edits.setByte(off, (hi shl 4) or d, cachedOriginal(off))
        val next = min(off + 1, fileSize - 1)
        selected = next
        return next
    }

    fun moveCursor(delta: Long): Long? {
        val off = selected ?: return null
        edits.nibble = -1
        val n = (off + delta).coerceIn(0, max(0, fileSize - 1))
        selected = n
        return n
    }

    /**
     * ⌫: сбрасывает набранную тетраду; иначе возвращает исходное значение байта под курсором.
     * Если байт под курсором не изменён — шаг назад (как Backspace) и возврат предыдущего.
     */
    fun revertAtCursor(): Long? {
        val off = selected ?: return null
        if (!canEdit) return null
        when {
            edits.nibble >= 0 -> edits.nibble = -1
            edits.get(off) >= 0 -> edits.revertByte(off)
            off > 0 -> {
                selected = off - 1
                edits.revertByte(off - 1)
            }
        }
        return selected
    }

    /** Перезапись [data] с позиции [start] одной операцией истории. */
    fun writeBytes(start: Long, data: ByteArray, label: String, onDone: (ok: Boolean, message: String) -> Unit) {
        bulk(start, data.size, label, "Записано", onDone) { data[it].toInt() and 0xFF }
    }

    fun fill(start: Long, length: Int, value: Int, onDone: (ok: Boolean, message: String) -> Unit) {
        bulk(start, length, "Заполнение", "Заполнено", onDone) { value }
    }

    private fun bulk(
        start: Long,
        length: Int,
        label: String,
        verb: String,
        onDone: (Boolean, String) -> Unit,
        value: (Int) -> Int,
    ) {
        if (!editable || edits.busy || saving) return
        if (start < 0 || length <= 0 || start + length > fileSize) {
            onDone(false, "Диапазон выходит за пределы файла")
            return
        }
        if (length > MAX_BULK) {
            onDone(false, "Слишком большой диапазон: не больше ${Format.size(MAX_BULK.toLong())} за раз")
            return
        }
        edits.busy = true
        viewModelScope.launch {
            val orig = try {
                withContext(Dispatchers.IO) { readRawBlocking(start, length) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                edits.busy = false
                onDone(false, "Не удалось прочитать файл: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            edits.busy = false
            val changed = edits.commit(start, length, label, orig, value)
            onDone(changed, if (changed) "$verb: ${bytesLabel(length.toLong())}" else "Значения не изменились")
        }
    }

    fun undo(onDone: (EditOp) -> Unit) {
        if (saving) return
        viewModelScope.launch {
            edits.undo()?.let {
                selected = it.start
                onDone(it)
            }
        }
    }

    fun redo(onDone: (EditOp) -> Unit) {
        if (saving) return
        viewModelScope.launch {
            edits.redo()?.let {
                selected = it.start
                onDone(it)
            }
        }
    }

    fun discardAll() {
        if (!saving && !edits.busy) edits.reset()
    }

    /** Переход к началу следующего/предыдущего блока изменений (по кругу). */
    fun jumpToChange(forward: Boolean): Long? {
        val p = edits.patches
        if (p.size == 0L) return null
        val cur = selected
        val target = if (forward) {
            val from = when {
                cur == null -> -1L
                p.get(cur) >= 0 -> p.nextUnpatched(cur) - 1
                else -> cur
            }
            p.next(from).takeIf { it >= 0 } ?: p.next(-1)
        } else {
            val before = when {
                cur == null -> fileSize
                p.get(cur) >= 0 -> p.prevUnpatched(cur) + 1
                else -> cur
            }
            val last = p.prev(before).takeIf { it >= 0 } ?: p.prev(fileSize)
            if (last < 0) -1 else p.prevUnpatched(last) + 1
        }
        if (target < 0) return null
        selected = target
        edits.nibble = -1
        return target
    }

    /** Сохранение: опциональная копия «имя.bak», затем запись только изменённых отрезков. */
    fun save(backup: Boolean, onDone: (ok: Boolean, message: String) -> Unit) {
        if (saving) return
        if (edits.busy) {
            onDone(false, "Дождитесь завершения текущей операции")
            return
        }
        if (edits.modified == 0L) {
            onDone(true, "Нет изменений")
            return
        }
        saving = true
        saveProgress = 0f
        edits.busy = true
        edits.nibble = -1
        val expected = fileSize
        viewModelScope.launch {
            var touched = false
            try {
                val (written, bakName) = withContext(Dispatchers.IO) {
                    if (!file.exists()) throw FileNotFoundException("Файл больше не существует")
                    if (file.length() != expected) throw IOException("Файл изменён другой программой: размер не совпадает")
                    if (loadedMtime > 0 && file.lastModified() != loadedMtime) throw IOException("Файл изменён другой программой после открытия — откройте его заново")
                    val runs = edits.patches.runs()
                    val total = runs.sumOf { it.data.size.toLong() }
                    val writeShare = if (backup) 0.1f else 1f
                    val bak = if (backup) {
                        // Копия пишется во временный файл: прежний .bak не теряется при ошибке.
                        val target = File(file.path + ".bak")
                        val part = File(file.path + ".bak.wexpart")
                        copyWithProgress(file, part) { p -> saveProgress = p * (1f - writeShare) }
                        if (!part.renameTo(target)) {
                            target.delete()
                            if (!part.renameTo(target)) { part.delete(); throw IOException("Не удалось сохранить резервную копию") }
                        }
                        target
                    } else null
                    // Копия большого файла делается долго — убеждаемся, что его не изменили за это время.
                    if (file.length() != expected || (loadedMtime > 0 && file.lastModified() != loadedMtime)) {
                        throw IOException("Файл изменён другой программой во время сохранения — откройте его заново")
                    }
                    // Запись не прерываем на полпути, чтобы не оставить файл в смешанном состоянии.
                    try { withContext(NonCancellable) {
                        RandomAccessFile(file, "rw").use { w ->
                            touched = true
                            var done = 0L
                            for (r in runs) {
                                w.seek(r.start)
                                w.write(r.data)
                                done += r.data.size
                                saveProgress = 1f - writeShare + writeShare * done / total.coerceAtLeast(1)
                            }
                            runCatching { w.fd.sync() }
                        }
                    } } finally {
                        // Даже частичная запись — наша: иначе повторное сохранение отклонялось бы как «чужое изменение».
                        if (touched) loadedMtime = file.lastModified()
                    }
                    total to bak?.name
                }
                // Кешированные страницы содержат старые данные — накладываем записанные правки, чтобы не перечитывать.
                for ((index, page) in cache) edits.patches.overlay(index * PAGE_SIZE, page, 0, page.size)
                generation++
                edits.busy = false
                edits.reset()
                pageVersion++
                cache[0L]?.let { fileType = detectFileType(it, file.name) }
                onDone(true, "Сохранено: ${bytesLabel(written)}" + (bakName?.let { " • копия «$it»" } ?: ""))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (touched) {
                    // Файл мог измениться частично — перечитываем страницы, правки остаются поверх.
                    cache.clear()
                    generation++
                    pageVersion++
                }
                onDone(false, "Не удалось сохранить: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                edits.busy = false
                saving = false
            }
        }
    }

    private suspend fun copyWithProgress(src: File, dst: File, onProgress: (Float) -> Unit) {
        val total = src.length().coerceAtLeast(1)
        try {
            FileInputStream(src).use { input ->
                FileOutputStream(dst).use { out ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(done.toFloat() / total)
                    }
                    out.fd.sync()
                }
            }
            dst.setLastModified(src.lastModified())
        } catch (e: Throwable) {
            runCatching { dst.delete() }
            if (e is CancellationException) throw e
            throw IOException("Не удалось создать резервную копию: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        synchronized(rafLock) {
            closed = true
            runCatching { raf?.close() }
            raf = null
        }
    }
}

/** Потоковый поиск последовательности байтов блоками по 1 МБ с перекрытием (с учётом правок). */
private class Searcher(
    private val raf: RandomAccessFile,
    private val size: Long,
    private val pattern: ByteArray,
    private val ignoreCase: Boolean,
    private val patches: PatchMap,
    private val onProgress: (Long) -> Unit,
) {
    private val buf = ByteArray(SEARCH_CHUNK + pattern.size - 1)
    private var scanned = 0L

    private fun lower(b: Byte): Int {
        val v = b.toInt() and 0xFF
        return if (ignoreCase && v in 0x41..0x5A) v + 0x20 else v
    }

    private val pat = IntArray(pattern.size) { lower(pattern[it]) }

    private fun matchAt(i: Int, n: Int): Boolean {
        if (i + pat.size > n) return false
        for (k in pat.indices) if (lower(buf[i + k]) != pat[k]) return false
        return true
    }

    private fun read(pos: Long, want: Int): Int {
        raf.seek(pos)
        var total = 0
        while (total < want) {
            val r = raf.read(buf, total, want - total)
            if (r <= 0) break
            total += r
        }
        patches.overlay(pos, buf, 0, total)
        return total
    }

    /** Первое вхождение с началом в [start, end). */
    suspend fun forward(start: Long, end: Long): Long? {
        var pos = start
        while (pos < end) {
            currentCoroutineContext().ensureActive()
            val want = min(buf.size.toLong(), size - pos).toInt()
            val n = read(pos, want)
            val last = min((n - pat.size).toLong(), end - pos - 1).toInt()
            for (i in 0..last) if (matchAt(i, n)) return pos + i
            val step = min(SEARCH_CHUNK.toLong(), end - pos)
            pos += step
            scanned += step
            onProgress(scanned)
        }
        return null
    }

    /** Последнее вхождение с началом в [low, high). */
    suspend fun backward(high: Long, low: Long): Long? {
        var chunkEnd = high
        while (chunkEnd > low) {
            currentCoroutineContext().ensureActive()
            val chunkStart = max(low, chunkEnd - SEARCH_CHUNK)
            val want = min(chunkEnd - chunkStart + pat.size - 1, size - chunkStart).toInt()
            val n = read(chunkStart, want)
            for (i in (chunkEnd - chunkStart - 1).toInt() downTo 0) if (matchAt(i, n)) return chunkStart + i
            scanned += chunkEnd - chunkStart
            onProgress(scanned)
            chunkEnd = chunkStart
        }
        return null
    }
}

// ------------------------------------------------------------------ Экран

private enum class SearchMode { Text, Hex }

private sealed interface HexDialog {
    data object Save : HexDialog
    data object Fill : HexDialog
    data object DiscardAll : HexDialog

    /** Выход с несохранёнными правками; [after] — что сделать после сохранения/отказа. */
    class Unsaved(val after: () -> Unit) : HexDialog
}

private fun hexDigitOf(key: Key): Int = when (key) {
    Key.Zero, Key.NumPad0 -> 0
    Key.One, Key.NumPad1 -> 1
    Key.Two, Key.NumPad2 -> 2
    Key.Three, Key.NumPad3 -> 3
    Key.Four, Key.NumPad4 -> 4
    Key.Five, Key.NumPad5 -> 5
    Key.Six, Key.NumPad6 -> 6
    Key.Seven, Key.NumPad7 -> 7
    Key.Eight, Key.NumPad8 -> 8
    Key.Nine, Key.NumPad9 -> 9
    Key.A -> 10
    Key.B -> 11
    Key.C -> 12
    Key.D -> 13
    Key.E -> 14
    Key.F -> 15
    else -> -1
}

@Composable
fun HexViewerScreen(path: String, onBack: () -> Unit, readOnly: Boolean = false) {
    val vm: HexViewModel = viewModel(key = "hex:$path") { HexViewModel(path, readOnly) }
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val focus = remember { FocusRequester() }
    var bytesPerRow by remember { mutableIntStateOf(16) }
    var searchOpen by remember { mutableStateOf(false) }
    var gotoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<HexDialog?>(null) }
    var editSheet by remember { mutableStateOf(false) }
    var markDialog by remember { mutableStateOf(false) }
    var marksOpen by remember { mutableStateOf(false) }
    var markOffset by remember { mutableLongStateOf(0L) }
    var markFromCursor by remember { mutableStateOf(false) }
    /** Метки только в композиции экрана: уход с просмотра их стирает. */
    val marks = remember { mutableStateListOf<HexMark>() }
    var nextMarkId by remember { mutableLongStateOf(1L) }
    val name = remember(path) { File(path).name }
    val size = vm.fileSize
    val edits = vm.edits
    val modified = edits.modified

    fun message(text: String, action: String? = null, onAction: () -> Unit = {}) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(
                text,
                actionLabel = action,
                withDismissAction = action == null,
                duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) onAction()
        }
    }

    val firstVisibleOffset = { listState.firstVisibleItemIndex.toLong() * bytesPerRow }

    val scrollToOffset: (Long) -> Unit = { offset ->
        scope.launch {
            val row = (offset / bytesPerRow).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            listState.scrollToItem((row - 4).coerceAtLeast(0))
        }
    }

    // Прокрутка только если строка курсора не видна полностью.
    val ensureVisible: (Long) -> Unit = { offset ->
        val row = (offset / bytesPerRow).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val first = listState.firstVisibleItemIndex
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
        when {
            row < first -> scope.launch { listState.scrollToItem(row) }
            row >= last -> scope.launch { listState.scrollToItem((row - (last - first) + 1).coerceAtLeast(0)) }
        }
    }

    val undo: () -> Unit = {
        vm.undo { op ->
            ensureVisible(op.start)
            if (op.length > 1) message("Отменено: ${op.label}")
        }
    }
    val redo: () -> Unit = {
        vm.redo { op ->
            ensureVisible(op.start)
            if (op.length > 1) message("Повторено: ${op.label}")
        }
    }
    val bulkResult: (Long) -> (Boolean, String) -> Unit = { start ->
        { ok, msg ->
            if (ok) {
                ensureVisible(start)
                message(msg, "Отменить", undo)
            } else {
                message(msg)
            }
        }
    }

    fun requestExit(after: () -> Unit) {
        when {
            vm.saving -> Unit
            edits.modified > 0 -> dialog = HexDialog.Unsaved(after)
            else -> after()
        }
    }

    fun doSave(after: (() -> Unit)?) {
        vm.save(vm.backupOnSave) { ok, msg ->
            if (ok && after != null) {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                after()
            } else {
                message(msg)
            }
        }
    }

    fun toggleEdit() {
        if (!vm.editMode && !vm.editable) {
            message("Правка недоступна: ${vm.editBlockReason ?: "файл не загружен"}")
            return
        }
        val on = !vm.editMode
        vm.setEditMode(on, firstVisibleOffset())
        if (on) {
            vm.selected?.let(ensureVisible)
            runCatching { focus.requestFocus() }
        }
    }

    fun handleKey(ev: KeyEvent): Boolean {
        if (ev.type != KeyEventType.KeyDown || !vm.editMode) return false
        if (ev.isCtrlPressed) {
            when (ev.key) {
                Key.Z -> if (ev.isShiftPressed) redo() else undo()
                Key.Y -> redo()
                Key.S -> if (edits.modified > 0) dialog = HexDialog.Save
                else -> return false
            }
            return true
        }
        val digit = hexDigitOf(ev.key)
        val moved = when {
            digit >= 0 -> vm.typeDigit(digit)
            ev.key == Key.DirectionLeft -> vm.moveCursor(-1)
            ev.key == Key.DirectionRight -> vm.moveCursor(1)
            ev.key == Key.DirectionUp -> vm.moveCursor(-bytesPerRow.toLong())
            ev.key == Key.DirectionDown -> vm.moveCursor(bytesPerRow.toLong())
            ev.key == Key.Backspace || ev.key == Key.Delete -> vm.revertAtCursor()
            else -> return false
        }
        moved?.let(ensureVisible)
        return true
    }

    fun copyByte(dec: Boolean) {
        val off = vm.selected ?: return
        scope.launch {
            val v = vm.byteAt(off)
            if (v >= 0) copyToSystemClipboard(context, if (dec) v.toString() else "%02X".format(v))
        }
    }

    // Приоритет: последний зарегистрированный включённый обработчик.
    BackHandler(modified > 0) { requestExit(onBack) }
    BackHandler(searchOpen) {
        vm.cancelSearch()
        vm.match = null
        searchOpen = false
    }
    BackHandler(vm.saving) { /* не уходим, пока идёт запись */ }

    LaunchedEffect(vm.editMode) {
        if (vm.editMode) runCatching { focus.requestFocus() }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.titleMedium)
                            if (size >= 0) {
                                Text(
                                    buildString {
                                        append(if (vm.editMode) "Правка" else "HEX")
                                        append(" • ")
                                        append(Format.size(size, settings.sizeUnits))
                                        if (modified > 0) append(" • изменено ${bytesLabel(modified)}")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (modified > 0) scheme.tertiary else scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { requestExit(onBack) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                        }
                    },
                    actions = {
                        if (size > 0) {
                            IconButton(onClick = { searchOpen = !searchOpen }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Search, "Поиск")
                            }
                            IconButton(onClick = { gotoOpen = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.Pin, "Перейти к смещению")
                            }
                            FilledIconToggleButton(
                                checked = vm.editMode,
                                onCheckedChange = { toggleEdit() },
                                enabled = !vm.saving,
                                shapes = IconButtonDefaults.toggleableShapes(),
                                colors = IconButtonDefaults.filledIconToggleButtonColors(
                                    containerColor = Color.Transparent,
                                    contentColor = if (vm.editable) scheme.onSurfaceVariant else scheme.onSurfaceVariant.copy(alpha = 0.38f),
                                    disabledContainerColor = Color.Transparent,
                                    checkedContainerColor = scheme.primary,
                                    checkedContentColor = scheme.onPrimary,
                                ),
                            ) {
                                Icon(if (vm.editable) Icons.Rounded.Edit else Icons.Rounded.EditOff, "Правка")
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.MoreVert, "Ещё")
                            }
                            HexMenu(
                                expanded = menuOpen,
                                vm = vm,
                                onDismiss = { menuOpen = false },
                                onSave = { dialog = HexDialog.Save },
                                onDiscardAll = { dialog = HexDialog.DiscardAll },
                                onFill = {
                                    if (!vm.editMode) vm.setEditMode(true, firstVisibleOffset())
                                    dialog = HexDialog.Fill
                                },
                                onJump = { forward ->
                                    val target = vm.jumpToChange(forward)
                                    if (target != null) ensureVisible(target) else message("Изменений нет")
                                },
                                onCopyByte = ::copyByte,
                                onOpenExternal = { FileOpener.openExternal(context, File(path)) },
                                onReveal = {
                                    requestExit {
                                        context.appContainer.browserRequests.tryEmit(BrowserRequest.Reveal(path))
                                        onBack()
                                    }
                                },
                                onCopyPath = { copyToSystemClipboard(context, path) },
                            )
                        }
                    },
                )
                AnimatedVisibility(
                    visible = vm.saving,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Сохранение…", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        LinearWavyProgressIndicator(progress = { vm.saveProgress }, modifier = Modifier.weight(1f))
                    }
                }
                AnimatedVisibility(
                    visible = searchOpen && size > 0,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    SearchPanel(
                        vm = vm,
                        firstVisibleOffset = firstVisibleOffset,
                        onFound = scrollToOffset,
                        onClose = {
                            vm.cancelSearch()
                            vm.match = null
                            searchOpen = false
                        },
                    )
                }
            }
        },
        bottomBar = {
            val sel = vm.selected
            Box {
                AnimatedVisibility(
                    visible = vm.editMode && size > 0,
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    @Suppress("UNUSED_VARIABLE")
                    val subscribe = edits.version + vm.pageVersion // подписка на правки и загрузку страниц
                    val patched = sel?.let { edits.get(it) } ?: -1
                    val original = sel?.let { vm.cachedOriginal(it) } ?: -1
                    HexKeypad(
                        offset = sel,
                        current = if (patched >= 0) patched else original,
                        original = original,
                        patched = patched >= 0,
                        nibble = edits.nibble,
                        modified = modified,
                        enabled = !edits.busy && !vm.saving,
                        canUndo = edits.canUndo,
                        canRedo = edits.canRedo,
                        onDigit = { d -> vm.typeDigit(d)?.let(ensureVisible) },
                        onLeft = { vm.moveCursor(-1)?.let(ensureVisible) },
                        onRight = { vm.moveCursor(1)?.let(ensureVisible) },
                        onRevert = { vm.revertAtCursor()?.let(ensureVisible) },
                        onUndo = undo,
                        onRedo = redo,
                        onInspector = { editSheet = true },
                        onSave = { dialog = HexDialog.Save },
                    )
                }
                AnimatedVisibility(
                    visible = !vm.editMode && sel != null && size > 0,
                    enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    // Держим последнее значение, чтобы панель не опустела во время анимации скрытия.
                    val last = remember { longArrayOf(0L) }
                    if (sel != null) last[0] = sel
                    ByteInspector(vm, last[0], onClose = { vm.selected = null })
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .onPreviewKeyEvent(::handleKey)
                .focusRequester(focus)
                .focusable(),
        ) {
            val error = vm.loadError
            when {
                error != null -> ViewerMessage(
                    icon = Icons.Rounded.Lock,
                    title = "Не удалось открыть файл",
                    message = error,
                    polygon = MaterialShapes.Clover4Leaf,
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
                size < 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                size == 0L -> ViewerMessage(Icons.AutoMirrored.Rounded.InsertDriveFile, "Файл пуст", "Размер файла — 0 байт")
                else -> HexContent(
                    vm = vm,
                    size = size,
                    listState = listState,
                    onBytesPerRow = { bytesPerRow = it },
                    onTap = { off ->
                        vm.onByteTap(off)
                        if (vm.editMode) runCatching { focus.requestFocus() }
                    },
                    markCount = marks.size,
                    onAddMark = {
                        markFromCursor = vm.selected != null
                        markOffset = (vm.selected ?: firstVisibleOffset()).coerceIn(0, size - 1)
                        markDialog = true
                    },
                    onOpenMarks = { marksOpen = true },
                )
            }
        }
    }

    if (gotoOpen && size > 0) {
        GoToDialog(
            size = size,
            onDismiss = { gotoOpen = false },
            onGo = { offset ->
                gotoOpen = false
                vm.selected = offset
                edits.nibble = -1
                scrollToOffset(offset)
            },
        )
    }

    if (markDialog && size > 0) {
        MarkDialog(
            offset = markOffset,
            fromCursor = markFromCursor,
            onDismiss = { markDialog = false },
            onAdd = { rawName ->
                markDialog = false
                val clean = cleanMarkName(rawName)
                if (marks.any { it.offset == markOffset && it.name == clean }) {
                    message("Такая метка уже есть", "Список") { marksOpen = true }
                } else {
                    val id = nextMarkId
                    nextMarkId = id + 1
                    marks.add(HexMark(id, markOffset, clean))
                    message("Метка добавлена", "Список") { marksOpen = true }
                }
            },
        )
    }

    if (marksOpen) {
        MarksSheet(
            marks = marks,
            onJump = { mark ->
                marksOpen = false
                if (mark.offset in 0 until size) {
                    vm.selected = mark.offset
                    edits.nibble = -1
                    scrollToOffset(mark.offset)
                }
            },
            onDelete = { id -> marks.removeAll { it.id == id } },
            onDismiss = { marksOpen = false },
        )
    }

    val sel = vm.selected
    if (editSheet && sel != null && size > 0) {
        HexEditSheet(
            vm = vm,
            offset = sel,
            onDismiss = { editSheet = false },
            onWrite = { start, data, label ->
                editSheet = false
                vm.writeBytes(start, data, label, bulkResult(start))
            },
        )
    }

    when (val d = dialog) {
        HexDialog.Save -> SaveChangesDialog(
            path = path,
            modified = modified,
            exiting = false,
            backup = vm.backupOnSave,
            onBackupChange = { vm.backupOnSave = it },
            onSave = {
                dialog = null
                doSave(null)
            },
            onDiscard = {},
            onDismiss = { dialog = null },
        )
        is HexDialog.Unsaved -> SaveChangesDialog(
            path = path,
            modified = modified,
            exiting = true,
            backup = vm.backupOnSave,
            onBackupChange = { vm.backupOnSave = it },
            onSave = {
                dialog = null
                doSave(d.after)
            },
            onDiscard = {
                dialog = null
                vm.discardAll()
                d.after()
            },
            onDismiss = { dialog = null },
        )
        HexDialog.Fill -> FillRangeDialog(
            size = size,
            initialStart = vm.selected ?: firstVisibleOffset(),
            onDismiss = { dialog = null },
            onFill = { start, length, value ->
                dialog = null
                vm.fill(start, length, value, bulkResult(start))
            },
        )
        HexDialog.DiscardAll -> ConfirmDialog(
            title = "Отменить все правки?",
            text = "Изменения (${bytesLabel(modified)}) и история правок будут сброшены. Файл на диске не изменится.",
            icon = Icons.Rounded.RestartAlt,
            confirmLabel = "Сбросить",
            onConfirm = {
                dialog = null
                vm.discardAll()
            },
            onDismiss = { dialog = null },
            destructive = true,
        )
        null -> Unit
    }
}

@Composable
private fun HexMenu(
    expanded: Boolean,
    vm: HexViewModel,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDiscardAll: () -> Unit,
    onFill: () -> Unit,
    onJump: (forward: Boolean) -> Unit,
    onCopyByte: (dec: Boolean) -> Unit,
    onOpenExternal: () -> Unit,
    onReveal: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val modified = vm.edits.modified
    val idle = !vm.saving && !vm.edits.busy
    val hasSel = vm.selected != null && vm.fileSize > 0
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(20.dp)) {
        @Composable
        fun item(text: String, icon: ImageVector, enabled: Boolean = true, action: () -> Unit) {
            DropdownMenuItem(
                text = { Text(text) },
                leadingIcon = { Icon(icon, null) },
                enabled = enabled,
                onClick = {
                    onDismiss()
                    action()
                },
            )
        }
        if (modified > 0) {
            item("Сохранить", Icons.Rounded.Save, idle, onSave)
            item("Сбросить все правки", Icons.Rounded.RestartAlt, idle, onDiscardAll)
            item("Следующее изменение", Icons.Rounded.SkipNext) { onJump(true) }
            item("Предыдущее изменение", Icons.Rounded.SkipPrevious) { onJump(false) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        if (vm.fileSize > 0) {
            item("Заполнить диапазон…", Icons.Rounded.FormatColorFill, vm.editable && idle, onFill)
            item("Копировать байт (HEX)", Icons.Rounded.Tag, hasSel) { onCopyByte(false) }
            item("Копировать байт (DEC)", Icons.Rounded.Numbers, hasSel) { onCopyByte(true) }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
        }
        item("Открыть в другом приложении", Icons.AutoMirrored.Rounded.OpenInNew, action = onOpenExternal)
        item("Показать в папке", Icons.Rounded.FolderOpen, action = onReveal)
        item("Копировать путь", Icons.Rounded.Route, action = onCopyPath)
    }
}

// ------------------------------------------------------------------ Содержимое

/** Параметры сетки: байт в строке, размер шрифта, ширина символа и высота строки в px. */
private class HexMetrics(
    val bytesPerRow: Int,
    val fontSize: TextUnit,
    val charW: Float,
    val rowH: Float,
    val rowHDp: Dp,
    val offsetDigits: Int,
    val padStart: Float,
) {
    val hexStart: Float get() = padStart + (offsetDigits + 2) * charW
    fun hexX(i: Int): Float = hexStart + (i * 3 + i / 8) * charW
    val asciiStart: Float get() = hexX(bytesPerRow - 1) + 4 * charW

    /** Индекс байта в строке по координате X, либо null. */
    fun hit(x: Float): Int? {
        for (i in 0 until bytesPerRow) {
            val cx = hexX(i)
            if (x >= cx - charW * 0.5f && x < cx + charW * 2.5f) return i
        }
        val a = asciiStart
        if (x >= a - charW * 0.3f && x < a + bytesPerRow * charW + charW * 0.3f) {
            return ((x - a) / charW).toInt().coerceIn(0, bytesPerRow - 1)
        }
        return null
    }
}

/** Предизмеренные глифы; жирные варианты (для изменённых байтов) измеряются по требованию. */
private class Glyphs(
    val hex: Array<TextLayoutResult>,
    val ascii: Array<TextLayoutResult>,
    val dot: TextLayoutResult,
    private val measurer: TextMeasurer,
    private val bold: TextStyle,
) {
    private val hexB = arrayOfNulls<TextLayoutResult>(256)
    private val asciiB = arrayOfNulls<TextLayoutResult>(256)
    private val pendingB = arrayOfNulls<TextLayoutResult>(16)

    fun hexBold(b: Int) = hexB[b] ?: measurer.measure("%02X".format(b), bold).also { hexB[b] = it }
    fun asciiBold(b: Int) = asciiB[b] ?: measurer.measure(if (isPrintable(b)) b.toChar().toString() else ".", bold).also { asciiB[b] = it }

    /** Набранная старшая тетрада: «A_». */
    fun pending(n: Int) = pendingB[n] ?: measurer.measure("%X_".format(n), bold).also { pendingB[n] = it }
}

private fun isPrintable(b: Int) = b in 0x20..0x7E

@Composable
private fun HexContent(
    vm: HexViewModel,
    size: Long,
    listState: LazyListState,
    onBytesPerRow: (Int) -> Unit,
    onTap: (Long) -> Unit,
    markCount: Int,
    onAddMark: () -> Unit,
    onOpenMarks: () -> Unit,
) {
    val measurer = rememberTextMeasurer(cacheSize = 256)
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val type = vm.fileType

    Column(Modifier.fillMaxSize()) {
        // Кнопка метки остаётся на месте; чипы типа и размера прокручиваются рядом.
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onAddMark,
                shapes = ButtonDefaults.shapes(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Метка")
            }
            if (markCount > 0) {
                TextButton(
                    onClick = onOpenMarks,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                ) { Text("Метки · $markCount") }
            }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (type != null) {
                    Surface(shape = CircleShape, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
                        Row(Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(type.icon, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(type.label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    type.detail?.let { HexPill(it) }
                }
                HexPill(Format.bytesExact(size))
            }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val widthPx = constraints.maxWidth.toFloat()
            val offsetDigits = if (size > 0xFFFFFFFFL) 10 else 8
            val metrics = remember(widthPx, offsetDigits, density) {
                computeMetrics(measurer, density, widthPx, offsetDigits)
            }
            val glyphs = remember(metrics) { buildGlyphs(measurer, metrics) }
            val bpr = metrics.bytesPerRow
            val rows = ((size + bpr - 1) / bpr).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

            // При смене ширины (поворот) сохраняем позицию по смещению, а не по номеру строки.
            val prevBpr = remember { intArrayOf(bpr) }
            val anchor = remember { longArrayOf(-1L) }
            if (prevBpr[0] != bpr) {
                anchor[0] = Snapshot.withoutReadObservation { listState.firstVisibleItemIndex }.toLong() * prevBpr[0]
                prevBpr[0] = bpr
            }
            LaunchedEffect(bpr) {
                onBytesPerRow(bpr)
                if (anchor[0] >= 0) {
                    listState.scrollToItem((anchor[0] / bpr).toInt())
                    anchor[0] = -1L
                }
            }

            Column(Modifier.fillMaxSize()) {
                HexHeader(metrics, measurer)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(count = rows, contentType = { 0 }) { row ->
                            HexRow(vm, row, size, metrics, glyphs, measurer, onTap)
                        }
                    }
                    FastScroller(
                        target = remember(listState) { listState.asScrollTarget() },
                        label = { idx -> "${(idx.toLong() * 100 / rows.coerceAtLeast(1)).coerceIn(0, 100)}%" },
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun HexPill(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun monoStyle(size: TextUnit) = TextStyle(fontFamily = FontFamily.Monospace, fontSize = size)

private fun computeMetrics(
    measurer: TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    widthPx: Float,
    offsetDigits: Int,
): HexMetrics {
    val base = 13f
    val cw13 = measurer.measure("0", monoStyle(base.sp)).size.width.toFloat().coerceAtLeast(1f)
    val pad = with(density) { 12.dp.toPx() }
    val reserve = with(density) { 16.dp.toPx() } // место под ползунок прокрутки
    val avail = (widthPx - pad - reserve).coerceAtLeast(cw13 * 20)
    fun chars(bpr: Int) = offsetDigits + 2 + (bpr * 3 - 1 + (bpr / 8 - 1)) + 3 + bpr
    val need16 = chars(16) * cw13
    val (bpr, font) = when {
        avail >= need16 -> 16 to base
        avail >= need16 * (10.5f / base) -> 16 to base * avail / need16
        else -> 8 to (base * avail / (chars(8) * cw13)).coerceIn(8f, base)
    }
    val fontSize = font.sp
    val cw = measurer.measure("0", monoStyle(fontSize)).size.width.toFloat().coerceAtLeast(1f)
    val rowHDp = with(density) { (fontSize.toPx() * 1.75f).toDp() }
    return HexMetrics(
        bytesPerRow = bpr,
        fontSize = fontSize,
        charW = cw,
        rowH = with(density) { rowHDp.toPx() },
        rowHDp = rowHDp,
        offsetDigits = offsetDigits,
        padStart = pad,
    )
}

private fun buildGlyphs(measurer: TextMeasurer, m: HexMetrics): Glyphs {
    val style = monoStyle(m.fontSize)
    val hex = Array(256) { measurer.measure("%02X".format(it), style) }
    val ascii = Array(256) { b -> measurer.measure(if (isPrintable(b)) b.toChar().toString() else ".", style) }
    return Glyphs(hex, ascii, measurer.measure("·", style), measurer, style.copy(fontWeight = FontWeight.Bold))
}

@Composable
private fun HexHeader(m: HexMetrics, measurer: TextMeasurer) {
    val scheme = MaterialTheme.colorScheme
    val style = monoStyle(m.fontSize).copy(fontWeight = FontWeight.Bold)
    val labels = remember(m) {
        Triple(
            measurer.measure("Адрес", style),
            Array(m.bytesPerRow) { measurer.measure("%02X".format(it), style) },
            measurer.measure("ASCII", style),
        )
    }
    Surface(color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(m.rowHDp)) {
            val y = (m.rowH - labels.first.size.height) / 2
            drawText(labels.first, color = scheme.onSurfaceVariant, topLeft = Offset(m.padStart, y))
            for (i in 0 until m.bytesPerRow) {
                drawText(labels.second[i], color = scheme.primary, topLeft = Offset(m.hexX(i), y))
            }
            drawText(labels.third, color = scheme.onSurfaceVariant, topLeft = Offset(m.asciiStart, y))
        }
    }
}

@Composable
private fun HexRow(
    vm: HexViewModel,
    row: Int,
    size: Long,
    m: HexMetrics,
    g: Glyphs,
    measurer: TextMeasurer,
    onTap: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bpr = m.bytesPerRow
    val rowOffset = row.toLong() * bpr
    val count = min(bpr.toLong(), size - rowOffset).toInt()
    val pageIndex = rowOffset / PAGE_SIZE
    @Suppress("UNUSED_VARIABLE")
    val version = vm.pageVersion // подписка на загрузку страниц
    val page = vm.page(pageIndex)
    if (page == null) LaunchedEffect(pageIndex) { vm.request(pageIndex) }
    val selected = vm.selected
    val match = vm.match
    val editMode = vm.editMode
    val edits = vm.edits
    val tap by rememberUpdatedState(onTap)
    val offsetLayout = remember(rowOffset, m) {
        measurer.measure("%0${m.offsetDigits}X".format(rowOffset), monoStyle(m.fontSize))
    }
    val zero = scheme.onSurfaceVariant.copy(alpha = 0.38f)
    val text = scheme.onSurface
    val special = scheme.tertiary
    val stripe = scheme.onSurface.copy(alpha = 0.035f)
    val selBg = scheme.primary
    val selFg = scheme.onPrimary
    val matchBg = scheme.secondaryContainer
    val matchFg = scheme.onSecondaryContainer
    val modBg = scheme.tertiaryContainer
    val modFg = scheme.onTertiaryContainer

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(m.rowHDp)
            .pointerInput(rowOffset, m) {
                detectTapGestures { pos ->
                    val i = m.hit(pos.x) ?: return@detectTapGestures
                    if (i < count) tap(rowOffset + i)
                }
            },
    ) {
        // Правки читаются в фазе отрисовки — при вводе перерисовываются только строки, без рекомпозиции.
        val hasEdits = edits.modified > 0
        @Suppress("UNUSED_VARIABLE")
        val editVersion = edits.version
        val nibble = if (editMode) edits.nibble else -1

        if (row % 2 == 1) drawRect(stripe)
        val ty = (m.rowH - offsetLayout.size.height) / 2
        drawText(offsetLayout, color = scheme.primary, topLeft = Offset(m.padStart, ty))
        val inPage = (rowOffset - pageIndex * PAGE_SIZE).toInt()
        for (i in 0 until count) {
            val off = rowOffset + i
            val hx = m.hexX(i)
            val ax = m.asciiStart + i * m.charW
            val patched = if (hasEdits) edits.get(off) else -1
            val b = when {
                patched >= 0 -> patched
                page == null || inPage + i >= page.size -> -1
                else -> page[inPage + i].toInt() and 0xFF
            }
            if (b < 0) {
                drawText(g.dot, color = zero, topLeft = Offset(hx + m.charW * 0.5f, ty))
                continue
            }
            val isSel = off == selected
            val isMatch = match != null && off in match
            val isMod = patched >= 0
            val bg = when {
                isSel -> selBg
                isMatch -> matchBg
                isMod -> modBg
                else -> null
            }
            if (bg != null) {
                highlight(Offset(hx - m.charW * 0.4f, 2f), Size(m.charW * 2.8f, m.rowH - 4f), bg)
                highlight(Offset(ax - m.charW * 0.1f, 2f), Size(m.charW * 1.2f, m.rowH - 4f), bg)
            }
            val hexFg = when {
                isSel -> selFg
                isMatch -> matchFg
                isMod -> modFg
                b == 0 -> zero
                isPrintable(b) -> text
                else -> special
            }
            val asciiFg = when {
                isSel -> selFg
                isMatch -> matchFg
                isMod -> modFg
                isPrintable(b) -> text
                else -> zero
            }
            val hexGlyph = when {
                isSel && nibble >= 0 -> g.pending(nibble)
                isMod -> g.hexBold(b)
                else -> g.hex[b]
            }
            drawText(hexGlyph, color = hexFg, topLeft = Offset(hx, ty))
            drawText(if (isMod) g.asciiBold(b) else g.ascii[b], color = asciiFg, topLeft = Offset(ax, ty))
            // Курсор правки — подчёркивание под ячейкой.
            if (isSel && editMode) {
                drawRect(selFg, Offset(hx, m.rowH - 6f), Size(m.charW * 2f, 2f))
            }
        }
    }
}

private fun DrawScope.highlight(topLeft: Offset, size: Size, color: Color) {
    drawRoundRect(color, topLeft, size, CornerRadius(size.height * 0.3f))
}

// ------------------------------------------------------------------ Панель значений выбранного байта

@Composable
private fun ByteInspector(vm: HexViewModel, offset: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Смещение", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(
                        "0x%X  •  %,d".format(Format.RU, offset, offset),
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.primary,
                    )
                }
                IconButton(onClick = { copyToSystemClipboard(context, "0x%X".format(offset)) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.ContentCopy, "Копировать смещение")
                }
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, "Снять выделение")
                }
            }
            Spacer(Modifier.height(8.dp))
            InspectorGrid(vm, offset, Modifier.padding(end = 8.dp))
        }
    }
}

/** Сетка значений байта/слов с позиции [offset] (с учётом правок); тап по ячейке копирует значение. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InspectorGrid(vm: HexViewModel, offset: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val editVersion = vm.edits.version
    val bytes by produceState(ByteArray(0), offset, editVersion) {
        value = try {
            vm.readAt(offset, 8)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ByteArray(0)
        }
    }
    if (bytes.isEmpty()) {
        LinearWavyProgressIndicator(modifier.fillMaxWidth())
        return
    }
    val values = byteValues(bytes)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val perRow = if (maxWidth >= 560.dp) 4 else 2
        FlowRow(
            maxItemsInEachRow = perRow,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            values.forEach { (label, value) ->
                ValueCell(label, value, Modifier.weight(1f)) { copyToSystemClipboard(context, value) }
            }
            // Выравниваем последнюю строку сетки пустыми ячейками.
            repeat((perRow - values.size % perRow) % perRow) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ValueCell(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun byteValues(b: ByteArray): List<Pair<String, String>> {
    fun u(i: Int) = b[i].toInt() and 0xFF
    val v = u(0)
    val out = ArrayList<Pair<String, String>>()
    out += "HEX" to "0x%02X".format(v)
    out += "DEC" to if (v > 127) "$v (${b[0].toInt()})" else "$v"
    out += "BIN" to Integer.toBinaryString(v).padStart(8, '0').let { "${it.take(4)} ${it.drop(4)}" }
    out += "Символ" to charName(v)
    if (b.size >= 2) {
        val le = (u(0) or (u(1) shl 8))
        val be = ((u(0) shl 8) or u(1))
        out += "int16 LE" to signedPair(le.toShort().toLong(), le.toLong())
        out += "int16 BE" to signedPair(be.toShort().toLong(), be.toLong())
    }
    if (b.size >= 4) {
        val le = u(0) or (u(1) shl 8) or (u(2) shl 16) or (u(3) shl 24)
        val be = (u(0) shl 24) or (u(1) shl 16) or (u(2) shl 8) or u(3)
        out += "int32 LE" to signedPair(le.toLong(), le.toLong() and 0xFFFFFFFFL)
        out += "int32 BE" to signedPair(be.toLong(), be.toLong() and 0xFFFFFFFFL)
        out += "float32 LE" to Float.fromBits(le).let { if (it.isFinite()) "%g".format(Locale.US, it) else it.toString() }
    }
    if (b.size >= 8) {
        var le = 0L
        for (i in 7 downTo 0) le = (le shl 8) or u(i).toLong()
        out += "int64 LE" to le.toString()
    }
    return out
}

private fun signedPair(signed: Long, unsigned: Long) = if (signed != unsigned) "$signed ($unsigned)" else "$signed"

private fun charName(v: Int): String = when (v) {
    0 -> "NUL"
    9 -> "TAB"
    10 -> "LF"
    13 -> "CR"
    27 -> "ESC"
    32 -> "пробел"
    127 -> "DEL"
    in 0x21..0x7E -> "'${v.toChar()}'"
    in 1..31 -> "^${(v + 64).toChar()}"
    else -> "—"
}

// ------------------------------------------------------------------ Поиск

@Composable
private fun SearchPanel(
    vm: HexViewModel,
    firstVisibleOffset: () -> Long,
    onFound: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf(SearchMode.Text) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun search(forward: Boolean) {
        val pattern = when (mode) {
            SearchMode.Text -> query.toByteArray(Charsets.UTF_8)
            SearchMode.Hex -> parseHex(query) ?: run {
                error = "Нужно чётное число HEX-цифр"
                return
            }
        }
        if (pattern.isEmpty()) return
        error = null
        val anchor = vm.match?.first ?: vm.selected
        val from = if (forward) (anchor?.plus(1) ?: firstVisibleOffset()) else (anchor ?: vm.fileSize)
        vm.find(pattern, ignoreCase = mode == SearchMode.Text, forward = forward, from = from) { result ->
            if (result == null) {
                Toast.makeText(context, "Не найдено", Toast.LENGTH_SHORT).show()
            } else {
                if (result.wrapped) {
                    Toast.makeText(context, if (forward) "Поиск продолжен с начала файла" else "Поиск продолжен с конца файла", Toast.LENGTH_SHORT).show()
                }
                onFound(result.offset)
            }
        }
    }

    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = query,
                    onValueChange = {
                        query = if (mode == SearchMode.Hex) it.filter { c -> c.isDigit() || c in 'a'..'f' || c in 'A'..'F' || c == ' ' } else it
                        error = null
                    },
                    singleLine = true,
                    isError = error != null,
                    placeholder = { Text(if (mode == SearchMode.Hex) "4D 5A 90 00" else "Текст для поиска") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                        keyboardType = if (mode == SearchMode.Hex) KeyboardType.Ascii else KeyboardType.Text,
                        capitalization = if (mode == SearchMode.Hex) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { search(true) }),
                    shape = RoundedCornerShape(20.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { search(false) }, enabled = query.isNotBlank() && !vm.searching, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Найти предыдущее")
                }
                IconButton(onClick = { search(true) }, enabled = query.isNotBlank() && !vm.searching, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Найти следующее")
                }
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, "Закрыть поиск")
                }
            }
            error?.let {
                Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))
            ConnectedChoice(
                options = SearchMode.entries,
                selected = mode,
                onSelect = {
                    mode = it
                    error = null
                },
                label = { if (it == SearchMode.Text) "Текст (ASCII/UTF-8)" else "HEX-байты" },
                icon = { if (it == SearchMode.Text) Icons.AutoMirrored.Rounded.Article else Icons.Rounded.DataObject },
            )
            AnimatedVisibility(vm.searching) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LinearWavyProgressIndicator(progress = { vm.searchProgress }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = vm::cancelSearch) { Text("Отмена") }
                }
            }
        }
    }
}

internal fun parseHex(text: String): ByteArray? {
    val digits = text.filter { !it.isWhitespace() }
    if (digits.isEmpty() || digits.length % 2 != 0) return null
    return runCatching { ByteArray(digits.length / 2) { digits.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
}

// ------------------------------------------------------------------ Метки смещения

private const val MARK_NAME_MAX = 32

/** Смещение, запомненное на этом экране. На диск не пишется. */
private class HexMark(val id: Long, val offset: Long, val name: String)

private fun cleanMarkName(raw: String): String =
    raw.filter { !it.isISOControl() }.trim().take(MARK_NAME_MAX)

@Composable
private fun MarkDialog(
    offset: Long,
    fromCursor: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val submit = { onAdd(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.BookmarkAdd, null) },
        title = { Text("Метка") },
        text = {
            Column {
                Text(
                    if (fromCursor) "Курсор" else "Верхняя видимая строка",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "0x%X  •  %,d".format(Format.RU, offset, offset),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.filter { c -> !c.isISOControl() }.take(MARK_NAME_MAX) },
                    singleLine = true,
                    label = { Text("Имя (необязательно)") },
                    supportingText = { Text("До $MARK_NAME_MAX символов. Пустое имя тоже сохранится.") },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { submit() }, shapes = ButtonDefaults.shapes()) { Text("Добавить") } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Отмена") } },
    )
}

@Composable
private fun MarksSheet(
    marks: List<HexMark>,
    onJump: (HexMark) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.padding(bottom = 12.dp)) {
            Text(
                "Метки",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Text(
                "Нажмите, чтобы перейти. Смахните или значок — удалить.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (marks.isEmpty()) {
                Text(
                    "Меток нет",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(scroll)) {
                    marks.forEach { mark ->
                        key(mark.id) {
                            MarkRow(mark, onJump = { onJump(mark) }, onDelete = { onDelete(mark.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkRow(mark: HexMark, onJump: () -> Unit, onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dismissState = rememberSwipeToDismissBoxState()
    val title = mark.name.ifBlank { "0x%X".format(mark.offset) }
    val detail = if (mark.name.isBlank()) {
        "%,d".format(Format.RU, mark.offset)
    } else {
        "0x%X  •  %,d".format(Format.RU, mark.offset, mark.offset)
    }
    SwipeToDismissBox(
        state = dismissState,
        onDismiss = { onDelete() },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bg by animateColorAsState(
                if (direction == SwipeToDismissBoxValue.Settled) Color.Transparent else scheme.errorContainer,
                MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "markDismiss",
            )
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(bg).padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (direction != SwipeToDismissBoxValue.Settled) {
                    Icon(Icons.Rounded.Delete, "Удалить метку", tint = scheme.onErrorContainer)
                }
            }
        },
    ) {
        Surface(shape = RoundedCornerShape(16.dp), color = scheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier.weight(1f).clickable(onClick = onJump).padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = scheme.primary,
                        maxLines = 1,
                    )
                }
                IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Delete, "Удалить метку", tint = scheme.error)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Переход к смещению

@Composable
private fun GoToDialog(size: Long, onDismiss: () -> Unit, onGo: (Long) -> Unit) {
    var hex by remember { mutableStateOf(true) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val submit = {
        val t = text.trim()
        val value = when {
            t.startsWith("0x", ignoreCase = true) -> t.drop(2).toLongOrNull(16)
            hex -> t.toLongOrNull(16)
            else -> t.toLongOrNull()
        }
        when {
            value == null -> error = "Некорректное число"
            value !in 0 until size -> error = "Вне файла: допустимо 0…0x%X".format(size - 1)
            else -> onGo(value)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Pin, null) },
        title = { Text("Перейти к смещению") },
        text = {
            Column {
                ConnectedChoice(
                    options = listOf(true, false),
                    selected = hex,
                    onSelect = {
                        hex = it
                        error = null
                    },
                    label = { if (it) "HEX" else "DEC" },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text(if (hex) "Смещение (hex)" else "Смещение (десятичное)") },
                    prefix = if (hex) {
                        { Text("0x") }
                    } else null,
                    isError = error != null,
                    supportingText = { Text(error ?: "Размер файла: 0x%X (%,d байт)".format(Format.RU, size, size)) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Go,
                        keyboardType = if (hex) KeyboardType.Ascii else KeyboardType.Number,
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { submit() }) { Text("Перейти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// ------------------------------------------------------------------ Определение типа по сигнатуре

private fun detectFileType(h: ByteArray, name: String): FileTypeInfo {
    val ext = name.substringAfterLast('.', "").lowercase()
    fun u(i: Int) = if (i < h.size) h[i].toInt() and 0xFF else -1
    fun at(off: Int, vararg b: Int) = h.size >= off + b.size && b.indices.all { u(off + it) == b[it] }
    fun str(off: Int, s: String) = h.size >= off + s.length && s.indices.all { u(off + it) == s[it].code }
    fun le16(i: Int) = u(i) or (u(i + 1) shl 8)
    fun be16(i: Int) = (u(i) shl 8) or u(i + 1)
    fun le32(i: Int) = le16(i) or (le16(i + 2) shl 16)
    fun be32(i: Int) = (be16(i) shl 16) or be16(i + 2)
    fun ascii(off: Int, len: Int) = if (h.size >= off + len) String(h, off, len, Charsets.ISO_8859_1) else ""

    return when {
        at(0, 0x7F, 0x45, 0x4C, 0x46) -> elfInfo(h)
        str(0, "dex\n") -> FileTypeInfo("DEX", "Dalvik, версия ${ascii(4, 3)}", Icons.Rounded.Android)
        str(0, "dey\n") -> FileTypeInfo("ODEX", "Оптимизированный DEX", Icons.Rounded.Android)
        str(0, "vdex") -> FileTypeInfo("VDEX", "Android ART", Icons.Rounded.Android)
        at(0, 0x50, 0x4B, 0x03, 0x04) || at(0, 0x50, 0x4B, 0x05, 0x06) -> zipInfo(h, ext)
        at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) ->
            FileTypeInfo("PNG", if (h.size >= 24) "${be32(16)}×${be32(20)}" else null, Icons.Rounded.Image)
        at(0, 0xFF, 0xD8, 0xFF) -> FileTypeInfo("JPEG", if (str(6, "Exif")) "с EXIF" else null, Icons.Rounded.Image)
        str(0, "GIF87a") || str(0, "GIF89a") -> FileTypeInfo("GIF", "${le16(6)}×${le16(8)}", Icons.Rounded.Image)
        str(0, "RIFF") && str(8, "WEBP") -> FileTypeInfo("WebP", null, Icons.Rounded.Image)
        str(0, "RIFF") && str(8, "WAVE") -> FileTypeInfo("WAV", null, Icons.Rounded.AudioFile)
        str(0, "RIFF") && str(8, "AVI ") -> FileTypeInfo("AVI", null, Icons.Rounded.VideoFile)
        str(4, "ftyp") -> isoMediaInfo(ascii(8, 4))
        at(0, 0x1A, 0x45, 0xDF, 0xA3) -> FileTypeInfo(if (ext == "webm") "WebM" else "Matroska", null, Icons.Rounded.VideoFile)
        str(0, "%PDF-") -> FileTypeInfo("PDF", "версия ${ascii(5, 3)}", Icons.Rounded.PictureAsPdf)
        str(0, "SQLite format 3") -> FileTypeInfo("SQLite", "страница ${be16(16).let { if (it == 1) 65536 else it }} байт", Icons.Rounded.Storage)
        at(0, 0xCA, 0xFE, 0xBA, 0xBE) -> {
            val major = be16(6)
            if (major in 45..90) FileTypeInfo("Java class", "Java ${major - 44}", Icons.Rounded.Coffee)
            else FileTypeInfo("Mach-O", "универсальный бинарник", Icons.Rounded.Memory)
        }
        at(0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) -> FileTypeInfo("7-Zip", "версия ${u(6)}.${u(7)}", Icons.Rounded.FolderZip)
        str(0, "Rar!\u001A\u0007") -> FileTypeInfo("RAR", if (u(6) == 1) "RAR5" else "RAR4", Icons.Rounded.FolderZip)
        at(0, 0x1F, 0x8B) -> FileTypeInfo("GZIP", null, Icons.Rounded.FolderZip)
        str(0, "BZh") -> FileTypeInfo("BZIP2", null, Icons.Rounded.FolderZip)
        at(0, 0xFD, 0x37, 0x7A, 0x58, 0x5A, 0x00) -> FileTypeInfo("XZ", null, Icons.Rounded.FolderZip)
        at(0, 0x28, 0xB5, 0x2F, 0xFD) -> FileTypeInfo("Zstandard", null, Icons.Rounded.FolderZip)
        at(0, 0x04, 0x22, 0x4D, 0x18) -> FileTypeInfo("LZ4", null, Icons.Rounded.FolderZip)
        str(0, "MSCF") -> FileTypeInfo("CAB", null, Icons.Rounded.FolderZip)
        str(0, "!<arch>\n") -> FileTypeInfo(if (ext == "deb") "DEB" else "AR", null, Icons.Rounded.FolderZip)
        str(257, "ustar") -> FileTypeInfo("TAR", null, Icons.Rounded.FolderZip)
        str(0, "ID3") -> FileTypeInfo("MP3", "ID3v2.${u(3)}", Icons.Rounded.AudioFile)
        at(0, 0xFF, 0xFB) || at(0, 0xFF, 0xF3) || at(0, 0xFF, 0xF2) -> FileTypeInfo("MP3", null, Icons.Rounded.AudioFile)
        at(0, 0xFF, 0xF1) || at(0, 0xFF, 0xF9) -> FileTypeInfo("AAC", "ADTS", Icons.Rounded.AudioFile)
        str(0, "OggS") -> FileTypeInfo("OGG", null, Icons.Rounded.AudioFile)
        str(0, "fLaC") -> FileTypeInfo("FLAC", null, Icons.Rounded.AudioFile)
        str(0, "MThd") -> FileTypeInfo("MIDI", null, Icons.Rounded.AudioFile)
        str(0, "8BPS") -> FileTypeInfo("PSD", null, Icons.Rounded.Image)
        at(0, 0x49, 0x49, 0x2A, 0x00) || at(0, 0x4D, 0x4D, 0x00, 0x2A) ->
            FileTypeInfo(if (ext in setOf("dng", "cr2", "nef", "arw")) ext.uppercase() else "TIFF", if (u(0) == 0x49) "Little-endian" else "Big-endian", Icons.Rounded.Image)
        str(0, "BM") && u(14) in setOf(12, 40, 56, 108, 124) -> FileTypeInfo("BMP", "${le32(18)}×${le32(22)}", Icons.Rounded.Image)
        at(0, 0x00, 0x00, 0x01, 0x00) && ext == "ico" -> FileTypeInfo("ICO", "${le16(4)} изобр.", Icons.Rounded.Image)
        str(0, "MZ") -> FileTypeInfo("PE", "исполняемый файл Windows", Icons.Rounded.Apps)
        str(0, "\u0000asm") -> FileTypeInfo("WebAssembly", "версия ${le32(4)}", Icons.Rounded.Code)
        at(0, 0x03, 0x00, 0x08, 0x00) -> FileTypeInfo("AXML", "бинарный XML Android", Icons.Rounded.Android)
        at(0, 0x02, 0x00, 0x0C, 0x00) -> FileTypeInfo("ARSC", "таблица ресурсов Android", Icons.Rounded.Android)
        str(0, "ANDROID!") -> FileTypeInfo("Boot image", "Android", Icons.Rounded.DeveloperBoard)
        at(0, 0x3A, 0xFF, 0x26, 0xED) -> FileTypeInfo("Sparse image", "Android", Icons.Rounded.SdCard)
        str(0x8001, "CD001") -> FileTypeInfo("ISO 9660", "образ диска", Icons.Rounded.SdCard)
        at(0, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1) -> FileTypeInfo("OLE2", "документ MS Office (старый формат)", Icons.AutoMirrored.Rounded.Article)
        str(0, "wOFF") -> FileTypeInfo("WOFF", null, Icons.Rounded.FontDownload)
        str(0, "wOF2") -> FileTypeInfo("WOFF2", null, Icons.Rounded.FontDownload)
        str(0, "OTTO") -> FileTypeInfo("OpenType", null, Icons.Rounded.FontDownload)
        at(0, 0x00, 0x01, 0x00, 0x00, 0x00) -> FileTypeInfo("TrueType", null, Icons.Rounded.FontDownload)
        str(0, "-----BEGIN") -> FileTypeInfo("PEM", ascii(11, 40).substringBefore('-').trim().ifEmpty { null }, Icons.Rounded.Key)
        str(0, "{\\rtf") -> FileTypeInfo("RTF", null, Icons.AutoMirrored.Rounded.Article)
        str(0, "<?xml") -> FileTypeInfo("XML", null, Icons.Rounded.Code)
        str(0, "#!") -> FileTypeInfo("Скрипт", ascii(2, min(40, h.size - 2)).substringBefore('\n').trim().ifEmpty { null }, Icons.Rounded.Terminal)
        at(0, 0xEF, 0xBB, 0xBF) -> FileTypeInfo("Текст", "UTF-8 с BOM", Icons.AutoMirrored.Rounded.Article)
        at(0, 0xFF, 0xFE) || at(0, 0xFE, 0xFF) -> FileTypeInfo("Текст", "UTF-16 с BOM", Icons.AutoMirrored.Rounded.Article)
        looksLikeText(h) -> FileTypeInfo("Текст", null, Icons.AutoMirrored.Rounded.Article)
        else -> FileTypeInfo("Двоичные данные", null, Icons.Rounded.DataObject)
    }
}

private fun looksLikeText(h: ByteArray): Boolean {
    if (h.isEmpty()) return false
    val n = min(h.size, 8192)
    var control = 0
    for (i in 0 until n) {
        val b = h[i].toInt() and 0xFF
        if (b == 0) return false
        if (b < 0x09 || b in 0x0E..0x1F) control++
    }
    return control * 100 / n < 5
}

private fun elfInfo(h: ByteArray): FileTypeInfo {
    fun u(i: Int) = if (i < h.size) h[i].toInt() and 0xFF else 0
    val bits = when (u(4)) {
        1 -> "32-бит"
        2 -> "64-бит"
        else -> null
    }
    val little = u(5) != 2
    fun half(i: Int) = if (little) u(i) or (u(i + 1) shl 8) else (u(i) shl 8) or u(i + 1)
    val type = when (half(16)) {
        1 -> "объектный файл"
        2 -> "исполняемый"
        3 -> "разделяемая библиотека"
        4 -> "дамп памяти"
        else -> null
    }
    val machine = when (half(18)) {
        0x03 -> "x86"
        0x08 -> "MIPS"
        0x28 -> "ARM"
        0x3E -> "x86-64"
        0xB7 -> "AArch64"
        0xF3 -> "RISC-V"
        else -> null
    }
    return FileTypeInfo("ELF", listOfNotNull(bits, machine, type).joinToString(" • ").ifEmpty { null }, Icons.Rounded.Memory)
}

private fun zipInfo(h: ByteArray, ext: String): FileTypeInfo {
    fun u(i: Int) = if (i < h.size) h[i].toInt() and 0xFF else 0
    val nameLen = u(26) or (u(27) shl 8)
    val extraLen = u(28) or (u(29) shl 8)
    val first = if (h.size >= 30 + nameLen) String(h, 30, nameLen, Charsets.UTF_8) else ""
    if (first == "mimetype") {
        val start = 30 + nameLen + extraLen
        val mime = if (h.size > start) String(h, start, min(60, h.size - start), Charsets.ISO_8859_1).takeWhile { it.code in 0x21..0x7E } else ""
        when {
            mime == "application/epub+zip" -> return FileTypeInfo("EPUB", "ZIP-контейнер", Icons.AutoMirrored.Rounded.Article)
            mime.startsWith("application/vnd.oasis.opendocument") -> return FileTypeInfo("OpenDocument", mime.substringAfterLast('.'), Icons.AutoMirrored.Rounded.Article)
        }
    }
    return when {
        ext in setOf("apk", "apks", "xapk", "apkm") || first == "AndroidManifest.xml" || first == "classes.dex" || first == "resources.arsc" ->
            FileTypeInfo("APK", "ZIP-архив приложения", Icons.Rounded.Android)
        ext == "aab" -> FileTypeInfo("AAB", "Android App Bundle", Icons.Rounded.Android)
        ext == "jar" || first == "META-INF/MANIFEST.MF" -> FileTypeInfo("JAR", "ZIP-архив Java", Icons.Rounded.Coffee)
        ext == "aar" -> FileTypeInfo("AAR", "библиотека Android", Icons.Rounded.Android)
        ext in setOf("docx", "xlsx", "pptx") || first == "[Content_Types].xml" -> FileTypeInfo("Office Open XML", ext.uppercase().ifEmpty { null }, Icons.AutoMirrored.Rounded.Article)
        else -> FileTypeInfo("ZIP", first.takeIf { it.isNotEmpty() }?.let { "первый файл: $it" }, Icons.Rounded.FolderZip)
    }
}

private fun isoMediaInfo(brand: String): FileTypeInfo = when (brand.trim().lowercase()) {
    "heic", "heix", "hevc", "mif1", "msf1" -> FileTypeInfo("HEIF", brand.trim(), Icons.Rounded.Image)
    "avif", "avis" -> FileTypeInfo("AVIF", null, Icons.Rounded.Image)
    "m4a" -> FileTypeInfo("M4A", "AAC/ALAC", Icons.Rounded.AudioFile)
    "qt" -> FileTypeInfo("QuickTime", null, Icons.Rounded.VideoFile)
    "3gp4", "3gp5", "3gp6", "3g2a" -> FileTypeInfo("3GP", brand.trim(), Icons.Rounded.VideoFile)
    else -> FileTypeInfo("MP4", brand.trim().ifEmpty { null }, Icons.Rounded.VideoFile)
}
