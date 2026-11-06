package client.wexmanager.feature.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.InvertColors
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.appContainer
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** [defaultRatio] — пропорция первой страницы; точные пропорции остальных узнаём при отрисовке. */
data class PdfDoc(val pageCount: Int, val defaultRatio: Float)

class RenderedPage(val bitmap: Bitmap, val ratio: Float)

sealed interface PdfState {
    data object Loading : PdfState
    data class Ready(val doc: PdfDoc) : PdfState
    data class Error(val message: String) : PdfState
}

/** Держит один PdfRenderer (он однопоточный) и кеш отрисованных страниц. */
class PdfViewModel(app: Application, private val path: String) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<PdfState>(PdfState.Loading)
    val state = _state.asStateFlow()

    // Все три поля меняются только под [lock].
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var closed = false
    private val lock = Mutex()
    private val cache = object : LruCache<String, RenderedPage>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: RenderedPage) = value.bitmap.allocationByteCount
    }

    /** Миниатюры отдельно: иначе они вытесняют полноразмерные страницы. Не больше восьми. */
    private val thumbCache = LruCache<Int, RenderedPage>(8)

    /** Уточнённые пропорции страниц (обновляются на главном потоке после отрисовки). */
    val ratios = androidx.compose.runtime.mutableStateMapOf<Int, Float>()

    init {
        viewModelScope.launch {
            _state.value = try {
                withContext(Dispatchers.IO) {
                    val f = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                    val r = try {
                        PdfRenderer(f)
                    } catch (e: SecurityException) {
                        f.close()
                        throw java.io.IOException("Документ защищён паролем — откройте его в другом приложении")
                    } catch (e: Throwable) {
                        f.close()
                        throw e
                    }
                    val first = lock.withLock {
                        if (closed) { // экран закрыли, пока документ открывался
                            runCatching { r.close() }
                            runCatching { f.close() }
                            throw kotlinx.coroutines.CancellationException()
                        }
                        fd = f
                        renderer = r
                        // Открываем только первую страницу: открытие всех у больших документов заняло бы секунды.
                        if (r.pageCount == 0) A4 else runCatching { r.openPage(0).use { it.width.toFloat() / it.height.coerceAtLeast(1) } }.getOrDefault(A4)
                    }
                    PdfState.Ready(PdfDoc(r.pageCount, first))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                PdfState.Error(e.message ?: "Не удалось открыть PDF")
            }
        }
    }

    /** Отрисовка страницы; null — страница повреждена или не хватило памяти. */
    suspend fun render(index: Int, widthPx: Int): RenderedPage? {
        val w = widthPx.coerceIn(200, MAX_WIDTH)
        val key = "$index@$w"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            lock.withLock {
                cache.get(key)?.let { return@withLock it }
                val r = renderer ?: return@withLock null
                try {
                    r.openPage(index).use { page ->
                        val pw = page.width.coerceAtLeast(1)
                        val ph = page.height.coerceAtLeast(1)
                        var bw = w
                        var bh = (bw.toLong() * ph / pw).coerceAtLeast(1)
                        // Очень длинные страницы (чеки, «длинные скриншоты») ограничиваем по числу пикселей.
                        if (bw * bh > MAX_PIXELS) {
                            val k = kotlin.math.sqrt(MAX_PIXELS.toDouble() / (bw * bh))
                            bw = (bw * k).toInt().coerceAtLeast(1)
                            bh = (bh * k).toLong().coerceAtLeast(1)
                        }
                        val bmp = Bitmap.createBitmap(bw, bh.toInt(), Bitmap.Config.ARGB_8888)
                        try {
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            RenderedPage(bmp, pw.toFloat() / ph).also { cache.put(key, it) }
                        } catch (t: Throwable) {
                            bmp.recycle()
                            throw t
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    cache.evictAll()
                    thumbCache.evictAll()
                    null
                }
            }
        }
    }

    /**
     * Маленькая копия страницы для полосы миниатюр. Неудачный bitmap уничтожается сразу,
     * удачный живёт в [thumbCache], пока его не вытеснит более новая миниатюра или экран не закроется.
     */
    suspend fun renderThumb(index: Int): RenderedPage? {
        thumbCache.get(index)?.let { return it }
        return withContext(Dispatchers.Default) {
            lock.withLock {
                thumbCache.get(index)?.let { return@withLock it }
                val r = renderer ?: return@withLock null
                try {
                    r.openPage(index).use { page ->
                        val pw = page.width.coerceAtLeast(1)
                        val ph = page.height.coerceAtLeast(1)
                        var bw = THUMB_WIDTH
                        var bh = (bw.toLong() * ph / pw).coerceAtLeast(1)
                        if (bw * bh > THUMB_MAX_PIXELS) {
                            val k = kotlin.math.sqrt(THUMB_MAX_PIXELS.toDouble() / (bw * bh))
                            bw = (bw * k).toInt().coerceAtLeast(1)
                            bh = (bh * k).toLong().coerceAtLeast(1)
                        }
                        val bmp = Bitmap.createBitmap(bw, bh.toInt(), Bitmap.Config.ARGB_8888)
                        try {
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            RenderedPage(bmp, pw.toFloat() / ph).also { thumbCache.put(index, it) }
                        } catch (t: Throwable) {
                            bmp.recycle()
                            throw t
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    cache.evictAll()
                    thumbCache.evictAll()
                    null
                }
            }
        }
    }

    /**
     * Видимый фрагмент страницы в полном разрешении (для сильного увеличения): страница шириной
     * [pageWidthPx] пикселей, фрагмент [left],[top] размером [w]×[h]. Не кешируется — живёт, пока виден.
     */
    suspend fun renderRegion(index: Int, pageWidthPx: Int, left: Int, top: Int, w: Int, h: Int): Bitmap? {
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_PIXELS) return null
        // withContext при отмене выбрасывает результат. Фрагмент не в кеше — иначе bitmap останется без владельца.
        val orphan = arrayOfNulls<Bitmap>(1)
        try {
            return withContext(Dispatchers.Default) {
                lock.withLock {
                    val r = renderer ?: return@withLock null
                    try {
                        r.openPage(index).use { page ->
                            val scale = pageWidthPx.toFloat() / page.width.coerceAtLeast(1)
                            val m = android.graphics.Matrix().apply {
                                setScale(scale, scale)
                                postTranslate(-left.toFloat(), -top.toFloat())
                            }
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            try {
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                orphan[0] = bmp
                                bmp
                            } catch (t: Throwable) {
                                bmp.recycle()
                                throw t
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    } catch (e: OutOfMemoryError) {
                        cache.evictAll()
                        thumbCache.evictAll()
                        null
                    }
                }
            }.also { returned ->
                if (returned == null) orphan[0]?.let { if (!it.isRecycled) it.recycle() }
                orphan[0] = null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            orphan[0]?.let { if (!it.isRecycled) it.recycle() }
            throw e
        }
    }

    override fun onCleared() {
        cache.evictAll()
        thumbCache.evictAll()
        // Рендер мог ещё идти — закрываем под тем же замком в фоне.
        getApplication<Application>().appContainer.appScope.launch(Dispatchers.IO) {
            lock.withLock {
                closed = true
                runCatching { renderer?.close() }
                runCatching { fd?.close() }
                renderer = null
                fd = null
            }
        }
    }

    companion object {
        const val MAX_WIDTH = 2400
        const val MAX_PIXELS = 6_000_000L // ≈ 24 МБ на страницу
        const val THUMB_WIDTH = 180
        const val THUMB_MAX_PIXELS = 180L * 720
        const val A4 = 0.707f
    }
}

/** Инверсия RGB фильтром при отрисовке: отдельный bitmap не создаём и нечем течь. */
private val PdfInvertFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

@Composable
fun PdfViewerScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: PdfViewModel = viewModel { PdfViewModel(app, path) }
    val state by vm.state.collectAsStateWithLifecycle()
    val file = remember(path) { File(path) }
    var gotoOpen by remember { mutableStateOf(false) }
    var inverted by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    Column {
                        Text(file.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        val sub = (state as? PdfState.Ready)?.doc?.pageCount?.let { "$it ${Format.plural(it, "страница", "страницы", "страниц")} · ${Format.size(file.length())}" }
                        if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (state is PdfState.Ready) {
                        IconButton(onClick = { inverted = !inverted }) {
                            Icon(
                                Icons.Rounded.InvertColors,
                                if (inverted) "Обычные цвета" else "Инвертировать цвета",
                                tint = if (inverted) scheme.primary else scheme.onSurface,
                            )
                        }
                        IconButton(onClick = { gotoOpen = true }) { Icon(Icons.Rounded.FormatListNumbered, "Перейти к странице") }
                    }
                    IconButton(onClick = { FileOpener.share(context, listOf(file)) }) { Icon(Icons.Rounded.Share, "Поделиться") }
                    IconButton(onClick = { FileOpener.openExternal(context, file) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть в другом приложении") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surfaceContainer),
            )
        },
        containerColor = scheme.surfaceContainerHighest,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val s = state) {
                PdfState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp))
                is PdfState.Error -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.PictureAsPdf, null, Modifier.size(56.dp), tint = scheme.error)
                    Text(s.message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { FileOpener.openExternal(context, file) }) { Text("Открыть в другом приложении") }
                }
                is PdfState.Ready -> PdfPages(vm, s.doc, listState, inverted)
            }
        }
    }

    val ready = state as? PdfState.Ready
    if (gotoOpen && ready != null) {
        var text by remember { mutableStateOf("") }
        val n = text.toIntOrNull()
        val valid = n != null && n in 1..ready.doc.pageCount
        AlertDialog(
            onDismissRequest = { gotoOpen = false },
            icon = { Icon(Icons.Rounded.FormatListNumbered, null) },
            title = { Text("Перейти к странице") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter(Char::isDigit).take(6) },
                    label = { Text("Страница (1–${ready.doc.pageCount})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    gotoOpen = false
                    scope.launch { listState.scrollToItem(n!! - 1) }
                }) { Text("Перейти") }
            },
            dismissButton = { TextButton(onClick = { gotoOpen = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun PdfPages(vm: PdfViewModel, doc: PdfDoc, listState: androidx.compose.foundation.lazy.LazyListState, inverted: Boolean) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    val animatedZoom by animateFloatAsState(zoom, label = "pdfZoom")
    val hScroll = rememberScrollState()
    // Якорь масштабирования: доля ширины документа под пальцем и экранные координаты точки.
    var anchor by remember { mutableStateOf<Triple<Float, Float, Float>?>(null) }
    var prevZoom by remember { mutableFloatStateOf(1f) }
    var viewWidthPx by remember { mutableFloatStateOf(1f) }
    // Перерисовываем страницы в новом разрешении, когда масштаб «устоялся».
    var renderZoom by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(zoom) {
        delay(250)
        renderZoom = zoom
    }
    // Держим якорь на месте на каждом кадре анимации и после каждой перекомпоновки ширины.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { Triple(animatedZoom, hScroll.maxValue, anchor) }.collect { (z, _, a) ->
            if (a == null) return@collect
            // dispatchRawDelta, а не scrollTo: не конфликтует с пальцем пользователя и не отменяет сбор.
            val targetX = (a.first * viewWidthPx * z - a.second).coerceAtLeast(0f)
            hScroll.dispatchRawDelta(targetX - hScroll.value)
            // По вертикали страницы растут вместе с масштабом — сдвигаем список, чтобы точка осталась под пальцем.
            if (z != prevZoom) {
                listState.dispatchRawDelta((listState.firstVisibleItemScrollOffset + a.third) * (z / prevZoom - 1f))
                prevZoom = z
            }
        }
    }
    fun zoomAt(newZoom: Float, x: Float, y: Float) {
        val content = viewWidthPx * animatedZoom
        anchor = Triple((hScroll.value + x) / content, x, y)
        zoom = newZoom.coerceIn(1f, 4f)
    }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val currentIndex = (currentPage - 1).coerceAtLeast(0)
    // Окно миниатюр — после паузы: во время листания PdfRenderer занят полноразмерными страницами.
    var thumbCenter by remember { mutableStateOf(0) }
    LaunchedEffect(currentPage, listState.isScrollInProgress) {
        if (listState.isScrollInProgress) delay(180)
        thumbCenter = currentIndex.coerceAtMost((doc.pageCount - 1).coerceAtLeast(0))
    }
    var showBadge by remember { mutableStateOf(true) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) showBadge = true else { delay(1500); showBadge = false }
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(
            Modifier
                .weight(1f)
                // Щипок перехватываем до прокрутки списка (Initial), одиночные касания не трогаем.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                val z = event.calculateZoom()
                                if (z != 1f) event.calculateCentroid(useCurrent = true).let { c -> zoomAt(zoom * z, c.x, c.y) }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { pos -> zoomAt(if (zoom > 1.2f) 1f else 2.5f, pos.x, pos.y) })
                },
        ) {
            val viewWidth = maxWidth
            viewWidthPx = with(density) { viewWidth.toPx() }
            val pageWidth = viewWidth * animatedZoom
            val renderPx = with(density) { (viewWidth * renderZoom).roundToPx() }
            // Дорисовка видимой части в полном разрешении нужна, когда базовая картинка уже растянута.
            val pageWidthPx = with(density) { ((viewWidth - 24.dp) * renderZoom).roundToPx() }
            val hiRes = pageWidthPx > PdfViewModel.MAX_WIDTH * 0.9f
            val settled = !listState.isScrollInProgress && !hScroll.isScrollInProgress && zoom == renderZoom
            var viewport by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { viewport = it.boundsInWindow() }
                    .horizontalScroll(hScroll, enabled = zoom > 1.01f)
                    .width(pageWidth),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(doc.pageCount, key = { it }) { index ->
                    PdfPage(vm, index, vm.ratios[index] ?: doc.defaultRatio, renderPx, hiRes && settled, viewport, inverted)
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .then(if (doc.pageCount > 0) Modifier else Modifier.navigationBarsPadding())
                    .padding(bottom = 16.dp),
            ) {
            androidx.compose.animation.AnimatedVisibility(visible = showBadge, enter = fadeIn(), exit = fadeOut()) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.inverseSurface, shadowElevation = 4.dp) {
                    Text(
                        "$currentPage / ${doc.pageCount}" + if (zoom > 1.01f) "  ·  ${(zoom * 100).roundToInt()}%" else "",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            }
        }
        if (doc.pageCount > 0) {
            PdfThumbStrip(
                vm = vm,
                pageCount = doc.pageCount,
                currentIndex = currentIndex,
                windowCenter = thumbCenter,
                inverted = inverted,
                onJump = { index -> scope.launch { listState.scrollToItem(index) } },
            )
        }
    }
}

@Composable
private fun PdfPage(
    vm: PdfViewModel,
    index: Int,
    ratio: Float,
    widthPx: Int,
    sharpen: Boolean,
    viewport: androidx.compose.ui.geometry.Rect,
    inverted: Boolean,
) {
    // Состояние produceState переживает смену ключей: пока рендерится новое разрешение, видна прежняя картинка.
    var failed by remember(index) { mutableStateOf(false) }
    val page by produceState<RenderedPage?>(null, index, widthPx) {
        val r = vm.render(index, widthPx)
        if (r == null) failed = true else {
            failed = false
            if (vm.ratios[index] != r.ratio) vm.ratios[index] = r.ratio
            value = r
        }
    }
    // Положение страницы на экране — обычная ссылка, не состояние: иначе каждый кадр прокрутки
    // перекомпоновывал бы страницу. Читаем его, только когда прокрутка остановилась.
    val pageRect = remember { arrayOf(androidx.compose.ui.geometry.Rect.Zero) }
    class Region(val bitmap: Bitmap, val left: Int, val top: Int, val widthKey: Int, val pageWidth: Float)
    var region by remember(index) { mutableStateOf<Region?>(null) }
    LaunchedEffect(sharpen, viewport, widthPx) {
        // Другое разрешение страницы — прежний фрагмент не нужен (и не держим до 24 МБ на обычном масштабе).
        if (region?.widthKey != widthPx) region = null
        if (!sharpen) return@LaunchedEffect
        delay(120) // дать раскладке устояться
        val rect = pageRect[0]
        val visible = if (rect.isEmpty) return@LaunchedEffect else rect.intersect(viewport)
        if (visible.width <= 0f || visible.height <= 0f) return@LaunchedEffect
        val target = androidx.compose.ui.unit.IntRect(
            (visible.left - rect.left).toInt(), (visible.top - rect.top).toInt(),
            (visible.right - rect.left).toInt(), (visible.bottom - rect.top).toInt(),
        )
        val current = region
        // Уже нарисованный фрагмент покрывает видимую часть — перерисовывать не нужно.
        if (current != null && current.widthKey == widthPx && target.left >= current.left && target.top >= current.top &&
            target.right <= current.left + current.bitmap.width && target.bottom <= current.top + current.bitmap.height
        ) return@LaunchedEffect
        vm.renderRegion(index, rect.width.toInt(), target.left, target.top, target.width, target.height)?.let {
            region = Region(it, target.left, target.top, widthPx, rect.width)
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .aspectRatio(ratio.coerceIn(0.01f, 100f))
            .onGloballyPositioned { c -> pageRect[0] = androidx.compose.ui.geometry.Rect(c.positionInWindow(), c.size.toSize()) }
            .shadow(2.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(if (inverted) Color.Black else Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val p = page
        val colorFilter = if (inverted) PdfInvertFilter else null
        when {
            p != null -> {
                val image = remember(p) { p.bitmap.asImageBitmap() }
                Image(
                    image,
                    contentDescription = "Страница ${index + 1}",
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.PictureAsPdf, null, tint = if (inverted) Color(0xFFFFB4AB) else Color(0xFFB3261E), modifier = Modifier.size(40.dp))
                Text(
                    "Страница ${index + 1} не отрисовалась",
                    color = if (inverted) Color(0xFFE6E0E9) else Color(0xFF49454F),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            else -> LoadingIndicator(Modifier.size(40.dp))
        }
        // Резкий фрагмент поверх: двигается вместе со страницей, пока не перерисован.
        region?.takeIf { it.widthKey == widthPx }?.let { r ->
            val img = remember(r) { r.bitmap.asImageBitmap() }
            // Рисуем в координатах страницы на момент отрисовки фрагмента и масштабируем к текущей ширине:
            // во время щипка фрагмент растёт вместе со страницей, а не «плывёт».
            androidx.compose.foundation.layout.Spacer(
                Modifier.matchParentSize().drawWithContent {
                    val k = size.width / r.pageWidth
                    scale(k, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                        drawImage(
                            img,
                            topLeft = androidx.compose.ui.geometry.Offset(r.left.toFloat(), r.top.toFloat()),
                            colorFilter = colorFilter,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun PdfThumbStrip(
    vm: PdfViewModel,
    pageCount: Int,
    currentIndex: Int,
    windowCenter: Int,
    inverted: Boolean,
    onJump: (Int) -> Unit,
) {
    val from = (windowCenter - 2).coerceAtLeast(0)
    val to = (windowCenter + 2).coerceAtMost(pageCount - 1)
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceContainer, shadowElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .horizontalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in from..to) {
                key(index) {
                    PdfThumb(
                        vm = vm,
                        index = index,
                        selected = index == currentIndex,
                        inverted = inverted,
                        onClick = { onJump(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfThumb(
    vm: PdfViewModel,
    index: Int,
    selected: Boolean,
    inverted: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var failed by remember(index) { mutableStateOf(false) }
    val page by produceState<RenderedPage?>(null, index) {
        val rendered = vm.renderThumb(index)
        failed = rendered == null
        value = rendered
    }
    val shape = RoundedCornerShape(6.dp)
    Column(
        Modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .size(width = 52.dp, height = 72.dp)
                .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape)
                .clip(shape)
                .background(if (inverted) Color.Black else Color.White),
            contentAlignment = Alignment.Center,
        ) {
            val rendered = page
            when {
                rendered != null -> {
                    val image = remember(rendered) { rendered.bitmap.asImageBitmap() }
                    Image(
                        image,
                        contentDescription = "Страница ${index + 1}",
                        contentScale = ContentScale.Fit,
                        colorFilter = if (inverted) PdfInvertFilter else null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                failed -> Icon(Icons.Rounded.PictureAsPdf, "Страница ${index + 1}", Modifier.size(22.dp), tint = scheme.outline)
                else -> LoadingIndicator(Modifier.size(22.dp))
            }
        }
        Text(
            "${index + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
