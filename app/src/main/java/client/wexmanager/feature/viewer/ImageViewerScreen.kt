package client.wexmanager.feature.viewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Iso
import androidx.compose.material.icons.rounded.Lens
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ShutterSpeed
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import client.wexmanager.BrowserRequest
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.feature.browser.copyToSystemClipboard
import client.wexmanager.ui.components.ConfirmDialog
import client.wexmanager.ui.theme.LocalAppSettings
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Полноэкранный просмотр изображений: зум (telephoto), листание соседних картинок папки,
 * иммерсивный режим по тапу, поворот, информация с EXIF, удаление, слайд-шоу.
 */
@Composable
fun ImageViewerScreen(path: String, siblings: Boolean, onBack: () -> Unit) {
    ViewerDarkTheme {
        val settings = LocalAppSettings.current
        var items by remember(path) { mutableStateOf<List<FileItem>?>(null) }
        var startIndex by remember(path) { mutableIntStateOf(0) }
        LaunchedEffect(path, siblings) {
            val list = try {
                if (siblings) {
                    val includeHidden = settings.showHidden || File(path).name.startsWith('.')
                    loadSiblings(path, includeHidden) { it == FileKind.Image || it == FileKind.Vector }
                } else null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: listOf(withContext(Dispatchers.IO) { LocalFileSystem.stat(path) })
            startIndex = list.indexOfFirst { it.path == path }.coerceAtLeast(0)
            items = list
        }
        val list = items
        if (list == null) {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(64.dp))
            }
        } else {
            ImageViewerContent(
                items = list,
                startIndex = startIndex,
                onDeleted = { removed ->
                    val rest = list.filterNot { it.path == removed.path }
                    if (rest.isEmpty()) onBack() else items = rest
                },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun ImageViewerContent(
    items: List<FileItem>,
    startIndex: Int,
    onDeleted: (FileItem) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    // Поворот — только визуальный, в четвертях оборота для каждого файла.
    val rotations = remember { mutableStateMapOf<String, Int>() }
    var infoFor by remember { mutableStateOf<FileItem?>(null) }
    var deleteFor by remember { mutableStateOf<FileItem?>(null) }
    var slideshow by rememberSaveable { mutableStateOf(false) }
    var intervalSec by rememberSaveable { mutableIntStateOf(4) }
    val userDragging by pagerState.interactionSource.collectIsDraggedAsState()
    // Свайп пейджера. Программный animateScrollToPage drag не присылает.
    LaunchedEffect(userDragging) { if (userDragging) slideshow = false }
    LaunchedEffect(Unit) {
        var wasZoomed = false
        snapshotFlow { zoomed }.collect { zoom ->
            if (zoom) slideshow = false
            else if (wasZoomed && !pagerState.isScrollInProgress && abs(pagerState.currentPageOffsetFraction) > 0.02f) {
                val page = pagerState.currentPage
                scope.launch { pagerState.animateScrollToPage(page) }
            }
            wasZoomed = zoom
        }
    }
    LaunchedEffect(slideshow, intervalSec, items.size) {
        if (!slideshow || items.size < 2) {
            if (items.size < 2) slideshow = false
            return@LaunchedEffect
        }
        val seconds = if (intervalSec == 2 || intervalSec == 8) intervalSec else 4
        var expected = pagerState.currentPage
        try {
            while (true) {
                if (expected >= items.lastIndex) {
                    slideshow = false
                    return@LaunchedEffect
                }
                delay(seconds * 1000L)
                val interrupted = zoomed || userDragging || pagerState.isScrollInProgress || pagerState.currentPage != expected
                if (!slideshow || interrupted) {
                    slideshow = false
                    return@LaunchedEffect
                }
                val next = expected + 1
                if (next > items.lastIndex) {
                    slideshow = false
                    return@LaunchedEffect
                }
                pagerState.animateScrollToPage(next)
                if (zoomed || userDragging || pagerState.currentPage != next) {
                    slideshow = false
                    return@LaunchedEffect
                }
                expected = next
                if (expected >= items.lastIndex) {
                    slideshow = false
                    return@LaunchedEffect
                }
            }
        } catch (e: CancellationException) {
            // Пауза и смена интервала отменяют анимацию. Зум и свайп не перехватываем.
            if (!userDragging && !zoomed) {
                scope.launch {
                    delay(50)
                    if (!userDragging && !zoomed && !pagerState.isScrollInProgress &&
                        abs(pagerState.currentPageOffsetFraction) > 0.02f
                    ) {
                        pagerState.animateScrollToPage(pagerState.currentPage)
                    }
                }
            }
            throw e
        }
    }

    val page = pagerState.currentPage.coerceIn(0, items.lastIndex)
    val current = items[page]

    ImmersiveSystemBars(hidden = !chromeVisible)

    val background by animateColorAsState(
        if (chromeVisible) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Black,
        motion.defaultEffectsSpec(),
        label = "viewerBg",
    )

    val deleteNow: (FileItem) -> Unit = { item ->
        context.appContainer.taskManager.delete(listOf(item), toTrash = settings.useTrash)
        Toast.makeText(context, if (settings.useTrash) "Перемещено в корзину" else "Удалено", Toast.LENGTH_SHORT).show()
        onDeleted(item)
    }

    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxSize().background(background)) {
            HorizontalPager(
                state = pagerState,
                key = { items[it].path },
                userScrollEnabled = !zoomed,
                beyondViewportPageCount = 1,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val item = items[index]
                ImagePage(
                    item = item,
                    quarterTurns = rotations[item.path] ?: 0,
                    isCurrent = index == pagerState.currentPage,
                    onTap = { chromeVisible = !chromeVisible },
                    onZoomChange = { zoomed = it },
                )
            }

            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(motion.defaultSpatialSpec()) { -it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { -it } + fadeOut(motion.fastEffectsSpec()),
            ) {
                ImageTopBar(
                    item = current,
                    index = page,
                    total = items.size,
                    onBack = onBack,
                    onShare = { FileOpener.share(context, listOf(File(current.path))) },
                    onOpenExternal = { FileOpener.openExternal(context, File(current.path)) },
                    onRotate = { rotations[current.path] = (rotations[current.path] ?: 0) + 1 },
                    onInfo = { infoFor = current },
                    onDelete = { if (settings.confirmDelete) deleteFor = current else deleteNow(current) },
                    onSetAs = { setAs(context, current) },
                    onReveal = {
                        context.appContainer.browserRequests.tryEmit(BrowserRequest.Reveal(current.path))
                        onBack()
                    },
                    onCopyPath = { copyToSystemClipboard(context, current.path) },
                )
            }

            AnimatedVisibility(
                visible = chromeVisible && items.size > 1,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
            ) {
                ViewerBottomChrome(
                    items = items,
                    current = page,
                    playing = slideshow,
                    intervalSec = if (intervalSec == 2 || intervalSec == 8) intervalSec else 4,
                    canPlay = !zoomed && page < items.lastIndex,
                    onToggle = {
                        if (slideshow) slideshow = false
                        else if (!zoomed && page < items.lastIndex) slideshow = true
                    },
                    onInterval = { seconds -> if (seconds == 2 || seconds == 4 || seconds == 8) intervalSec = seconds },
                    onSelect = { target ->
                        slideshow = false
                        scope.launch { pagerState.animateScrollToPage(target) }
                    },
                )
            }
        }
    }

    infoFor?.let { item -> ImageInfoSheet(item, onDismiss = { infoFor = null }) }
    deleteFor?.let { item ->
        ConfirmDialog(
            title = "Удалить изображение?",
            text = if (settings.useTrash) "«${item.name}» будет перемещён в корзину." else "«${item.name}» будет удалён безвозвратно.",
            icon = Icons.Rounded.Delete,
            confirmLabel = "Удалить",
            destructive = true,
            onConfirm = { deleteNow(item) },
            onDismiss = { deleteFor = null },
        )
    }
}

// ------------------------------------------------------------------ Страница с изображением

@Composable
private fun ImagePage(
    item: FileItem,
    quarterTurns: Int,
    isCurrent: Boolean,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val motion = MaterialTheme.motionScheme
    var error by remember(item.path) { mutableStateOf<String?>(null) }
    val request = remember(item.path, item.lastModified) {
        ImageRequest.Builder(context)
            .data(File(item.path))
            .crossfade(true)
            .listener(onError = { _, result -> error = result.throwable.message ?: "Неизвестная ошибка" })
            .build()
    }
    val zoomState = rememberZoomableImageState(rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = 8f)))

    if (isCurrent) {
        val zoomedNow by remember(zoomState) {
            derivedStateOf { (zoomState.zoomableState.zoomFraction ?: 0f) > 0.01f }
        }
        LaunchedEffect(zoomedNow) { onZoomChange(zoomedNow) }
    }

    val odd = quarterTurns % 2 != 0
    var size by remember(item.path) { mutableStateOf<IntSize?>(null) }
    LaunchedEffect(item.path, odd) {
        if (odd && size == null) size = withContext(Dispatchers.IO) { readImageSize(item.path) }
    }
    val rotation by animateFloatAsState(quarterTurns * 90f, motion.defaultSpatialSpec(), label = "rotation")

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fit = if (odd) rotatedFitFactor(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat(), size) else 1f
        val scale by animateFloatAsState(fit, motion.defaultSpatialSpec(), label = "rotationScale")
        ZoomableAsyncImage(
            model = request,
            contentDescription = item.name,
            state = zoomState,
            contentScale = ContentScale.Fit,
            onClick = { onTap() },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                },
        )
        when {
            error != null -> ViewerMessage(
                icon = Icons.Rounded.BrokenImage,
                title = "Не удалось открыть изображение",
                message = error,
                polygon = MaterialShapes.Clover4Leaf,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
            !zoomState.isImageDisplayed -> LoadingIndicator(Modifier.size(56.dp))
        }
    }
}

/** Во сколько раз уменьшить картинку, повёрнутую на 90°, чтобы она снова вписалась в экран. */
private fun rotatedFitFactor(cw: Float, ch: Float, image: IntSize?): Float {
    if (cw <= 0f || ch <= 0f) return 1f
    if (image == null || image.width <= 0 || image.height <= 0) return min(cw, ch) / max(cw, ch)
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val fit = min(cw / iw, ch / ih)
    val w0 = iw * fit
    val h0 = ih * fit
    return min(cw / h0, ch / w0)
}

// ------------------------------------------------------------------ Верхняя панель

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageTopBar(
    item: FileItem,
    index: Int,
    total: Int,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onRotate: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onSetAs: () -> Unit,
    onReveal: () -> Unit,
    onCopyPath: () -> Unit,
) {
    val settings = LocalAppSettings.current
    val motion = MaterialTheme.motionScheme
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.35f), Color.Transparent),
                ),
            )
            .windowInsetsPadding(
                WindowInsets.statusBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 24.dp),
    ) {
        val wide = maxWidth >= 520.dp
        var menu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
            }
            Column(Modifier.weight(1f).padding(start = 4.dp, end = 4.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (total > 1) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                            AnimatedContent(
                                targetState = index,
                                transitionSpec = {
                                    val dir = if (targetState > initialState) 1 else -1
                                    (slideInVertically(motion.fastSpatialSpec()) { it * dir } + fadeIn(motion.fastEffectsSpec()))
                                        .togetherWith(slideOutVertically(motion.fastSpatialSpec()) { -it * dir } + fadeOut(motion.fastEffectsSpec()))
                                },
                                label = "counter",
                            ) { i ->
                                Text(
                                    "${i + 1} / $total",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        Format.size(item.size, settings.sizeUnits),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                }
            }
            BarAction(Icons.Rounded.Share, "Поделиться", onShare)
            if (wide) BarAction(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть в другом приложении", onOpenExternal)
            BarAction(Icons.AutoMirrored.Rounded.RotateRight, "Повернуть", onRotate)
            BarAction(Icons.Rounded.Info, "Информация", onInfo)
            if (wide) BarAction(Icons.Rounded.Delete, "Удалить", onDelete)
            Box {
                BarAction(Icons.Rounded.MoreVert, "Ещё") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(20.dp)) {
                    if (!wide) {
                        MenuRow("Открыть в другом приложении", Icons.AutoMirrored.Rounded.OpenInNew) { menu = false; onOpenExternal() }
                    }
                    MenuRow("Установить как…", Icons.Rounded.Wallpaper) { menu = false; onSetAs() }
                    MenuRow("Показать в папке", Icons.Rounded.FolderOpen) { menu = false; onReveal() }
                    MenuRow("Копировать путь", Icons.Rounded.Route) { menu = false; onCopyPath() }
                    if (!wide) {
                        MenuRow("Удалить", Icons.Rounded.Delete, MaterialTheme.colorScheme.error) { menu = false; onDelete() }
                    }
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) { Icon(icon, description) }
}

@Composable
private fun MenuRow(text: String, icon: ImageVector, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, color = if (tint == Color.Unspecified) Color.Unspecified else tint) },
        onClick = onClick,
        leadingIcon = { Icon(icon, null, tint = if (tint == Color.Unspecified) LocalContentColor.current else tint) },
    )
}

// ------------------------------------------------------------------ Низ: слайд-шоу и миниатюры

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewerBottomChrome(
    items: List<FileItem>,
    current: Int,
    playing: Boolean,
    intervalSec: Int,
    canPlay: Boolean,
    onToggle: () -> Unit,
    onInterval: (Int) -> Unit,
    onSelect: (Int) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.8f)),
                ),
            )
            .windowInsetsPadding(
                WindowInsets.navigationBarsIgnoringVisibility.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(top = 20.dp, bottom = 12.dp),
    ) {
        SlideshowBar(
            playing = playing,
            intervalSec = intervalSec,
            canPlay = canPlay,
            onToggle = onToggle,
            onInterval = onInterval,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        ThumbnailStrip(items = items, current = current, onSelect = onSelect)
    }
}

@Composable
private fun SlideshowBar(
    playing: Boolean,
    intervalSec: Int,
    canPlay: Boolean,
    onToggle: () -> Unit,
    onInterval: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onToggle,
            enabled = playing || canPlay,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Пауза" else "Воспроизвести",
            )
        }
        Text(
            "Слайд-шоу",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(2, 4, 8).forEach { sec ->
                val selected = intervalSec == sec
                Text(
                    "$sec с",
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.14f),
                        )
                        .clickable { onInterval(sec) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ThumbnailStrip(items: List<FileItem>, current: Int, onSelect: (Int) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    LaunchedEffect(current) {
        // Ждём первую раскладку, иначе ширина вьюпорта ещё неизвестна.
        snapshotFlow { listState.layoutInfo.viewportSize.width }.first { it > 0 }
        val viewport = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        val itemPx = with(density) { 76.dp.roundToPx() }
        listState.animateScrollToItem(current, -(viewport / 2 - itemPx / 2).coerceAtLeast(0))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(items, key = { _, it -> it.path }) { i, item ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 76.dp else 52.dp, motion.fastSpatialSpec(), label = "thumbW")
            val corner by animateDpAsState(if (selected) 16.dp else 10.dp, motion.fastSpatialSpec(), label = "thumbCorner")
            val shape = RoundedCornerShape(corner)
            Box(
                Modifier
                    .animateItem(
                        fadeInSpec = motion.defaultEffectsSpec(),
                        placementSpec = motion.defaultSpatialSpec(),
                        fadeOutSpec = motion.fastEffectsSpec(),
                    )
                    .width(width)
                    .height(60.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                AsyncImage(
                    model = File(item.path),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Информация

private class ImageDetails(
    val width: Int,
    val height: Int,
    val mime: String,
    val exif: ExifData?,
)

private class ExifData(
    val camera: String?,
    val lens: String?,
    val dateTaken: Long?,
    val exposure: String?,
    val aperture: String?,
    val iso: Int?,
    val focal: String?,
    val flash: String?,
    val orientation: String?,
    val software: String?,
    val lat: Double?,
    val lon: Double?,
    val altitude: Double?,
) {
    val hasCameraInfo: Boolean
        get() = camera != null || lens != null || dateTaken != null || exposure != null || aperture != null ||
            iso != null || focal != null || flash != null
}

private class InfoEntry(val icon: ImageVector, val label: String, val value: String, val onClick: (() -> Unit)? = null)

@Composable
private fun ImageInfoSheet(item: FileItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = LocalAppSettings.current
    val details by produceState<ImageDetails?>(null, item.path) {
        value = withContext(Dispatchers.IO) { readImageDetails(item) }
    }
    AppBottomSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = File(item.path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    buildString {
                        append(item.extension.uppercase().ifEmpty { item.kind.label })
                        append(" • ")
                        append(Format.size(item.size, settings.sizeUnits))
                        details?.takeIf { it.width > 0 }?.let { append(" • ${it.width}×${it.height}") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val d = details
        if (d == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@AppBottomSheet
        }
        val copy: (String) -> () -> Unit = { text -> { copyToSystemClipboard(context, text) } }
        val file = buildList {
            add(
                InfoEntry(
                    Icons.Rounded.Straighten, "Размер",
                    Format.size(item.size, settings.sizeUnits) + if (item.size >= 1024) "  (${Format.bytesExact(item.size)})" else "",
                ),
            )
            if (d.width > 0) {
                val mp = d.width.toLong() * d.height / 1_000_000.0
                val mpText = if (mp >= 0.1) " • %.1f Мп".format(Format.RU, mp) else ""
                add(InfoEntry(Icons.Rounded.AspectRatio, "Разрешение", "${d.width} × ${d.height}$mpText"))
            }
            add(InfoEntry(Icons.Rounded.Image, "Тип", "${item.kind.label} • ${d.mime}"))
            add(InfoEntry(Icons.Rounded.Schedule, "Изменён", Format.fullDate(item.lastModified)))
            add(InfoEntry(Icons.Rounded.FolderOpen, "Путь", item.path, copy(item.path)))
        }
        InfoGroup("Файл", file)

        val exif = d.exif
        if (exif != null && exif.hasCameraInfo) {
            val shot = buildList {
                exif.camera?.let { add(InfoEntry(Icons.Rounded.PhotoCamera, "Камера", it)) }
                exif.lens?.let { add(InfoEntry(Icons.Rounded.Lens, "Объектив", it)) }
                exif.dateTaken?.let { add(InfoEntry(Icons.Rounded.CalendarToday, "Дата съёмки", Format.fullDate(it))) }
                exif.exposure?.let { add(InfoEntry(Icons.Rounded.ShutterSpeed, "Выдержка", it)) }
                exif.aperture?.let { add(InfoEntry(Icons.Rounded.Camera, "Диафрагма", it)) }
                exif.iso?.let { add(InfoEntry(Icons.Rounded.Iso, "ISO", it.toString())) }
                exif.focal?.let { add(InfoEntry(Icons.Rounded.CenterFocusStrong, "Фокусное расстояние", it)) }
                exif.flash?.let { add(InfoEntry(Icons.Rounded.FlashOn, "Вспышка", it)) }
                exif.orientation?.let { add(InfoEntry(Icons.Rounded.ScreenRotation, "Ориентация", it)) }
                exif.software?.let { add(InfoEntry(Icons.Rounded.Tune, "Программа", it)) }
            }
            InfoGroup("Съёмка", shot)
        }
        if (exif?.lat != null && exif.lon != null) {
            val lat = exif.lat
            val lon = exif.lon
            val coords = "%.6f, %.6f".format(Locale.US, lat, lon)
            val geo = buildList {
                add(InfoEntry(Icons.Rounded.LocationOn, "Координаты • открыть на карте", coords) { openMap(context, lat, lon) })
                exif.altitude?.let { add(InfoEntry(Icons.Rounded.Height, "Высота", "%.1f м".format(Format.RU, it))) }
            }
            InfoGroup("Местоположение", geo)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoGroup(title: String, entries: List<InfoEntry>) {
    if (entries.isEmpty()) return
    val context = LocalContext.current
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 6.dp),
    )
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        entries.forEachIndexed { i, e ->
            SegmentedListItem(
                onClick = e.onClick ?: { copyToSystemClipboard(context, e.value) },
                shapes = ListItemDefaults.segmentedShapes(i, entries.size),
                leadingContent = { Icon(e.icon, null) },
                overlineContent = { Text(e.label) },
            ) { Text(e.value) }
        }
    }
    Spacer(Modifier.height(8.dp))
}

// ------------------------------------------------------------------ Чтение метаданных

private fun readImageSize(path: String): IntSize? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(path, opts) }
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    return if (orientation.swapsDimensions()) IntSize(opts.outHeight, opts.outWidth) else IntSize(opts.outWidth, opts.outHeight)
}

private fun Int.swapsDimensions(): Boolean =
    this == ExifInterface.ORIENTATION_ROTATE_90 || this == ExifInterface.ORIENTATION_ROTATE_270 ||
        this == ExifInterface.ORIENTATION_TRANSPOSE || this == ExifInterface.ORIENTATION_TRANSVERSE

private fun readImageDetails(item: FileItem): ImageDetails {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { BitmapFactory.decodeFile(item.path, opts) }
    val mime = opts.outMimeType ?: FileOpener.mimeType(item.name)
    val exif = readExif(item.path)
    return ImageDetails(
        width = opts.outWidth.coerceAtLeast(0),
        height = opts.outHeight.coerceAtLeast(0),
        mime = mime,
        exif = exif,
    )
}

private fun readExif(path: String): ExifData? = runCatching {
    val e = ExifInterface(path)
    fun s(tag: String): String? = e.getAttribute(tag)?.trim()?.trimEnd('\u0000')?.takeIf { it.isNotEmpty() }
    val make = s(ExifInterface.TAG_MAKE)
    val model = s(ExifInterface.TAG_MODEL)
    val camera = when {
        model == null -> make
        make != null && !model.startsWith(make, ignoreCase = true) -> "$make $model"
        else -> model
    }
    val dateText = s(ExifInterface.TAG_DATETIME_ORIGINAL) ?: s(ExifInterface.TAG_DATETIME)
    val date = dateText?.let { runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }.getOrNull() }
    val exposure = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let { v ->
        if (v >= 1.0) "${trimNumber(v)} с" else "1/${(1.0 / v).roundToInt()} с"
    }
    val aperture = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { "ƒ/${trimNumber(it)}" }
    val iso = e.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0).takeIf { it > 0 }
    val focal = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { f ->
        val eq = e.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
        "${trimNumber(f)} мм" + if (eq > 0) " (экв. $eq мм)" else ""
    }
    val flash = if (e.hasAttribute(ExifInterface.TAG_FLASH)) {
        if (e.getAttributeInt(ExifInterface.TAG_FLASH, 0) and 1 != 0) "Сработала" else "Не сработала"
    } else null
    val orientation = when (e.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> "Повёрнуто на 90°"
        ExifInterface.ORIENTATION_ROTATE_180 -> "Повёрнуто на 180°"
        ExifInterface.ORIENTATION_ROTATE_270 -> "Повёрнуто на 270°"
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Отражено по горизонтали"
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Отражено по вертикали"
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE -> "Повёрнуто и отражено"
        else -> null
    }
    val latLong = FloatArray(2)
    val hasLatLong = e.getLatLong(latLong)
    val altitude = e.getAltitude(Double.NaN).takeUnless { it.isNaN() }
    ExifData(
        camera = camera,
        lens = s("LensModel"),
        dateTaken = date,
        exposure = exposure,
        aperture = aperture,
        iso = iso,
        focal = focal,
        flash = flash,
        orientation = orientation,
        software = s(ExifInterface.TAG_SOFTWARE),
        lat = if (hasLatLong) latLong[0].toDouble() else null,
        lon = if (hasLatLong) latLong[1].toDouble() else null,
        altitude = if (hasLatLong) altitude else null,
    )
}.getOrNull()

private fun trimNumber(v: Double): String {
    val r = (v * 10).roundToInt() / 10.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

private fun openMap(context: Context, lat: Double, lon: Double) {
    val uri = Uri.parse("geo:%f,%f?q=%f,%f".format(Locale.US, lat, lon, lat, lon))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения карт", Toast.LENGTH_SHORT).show()
    }
}

private fun setAs(context: Context, item: FileItem) {
    val uri = runCatching { FileOpener.uriFor(context, File(item.path)) }.getOrNull() ?: return
    val mime = FileOpener.mimeType(item.name)
    val intent = Intent(Intent.ACTION_ATTACH_DATA)
        .setDataAndType(uri, mime)
        .putExtra("mimeType", mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(intent, "Установить как").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Нет подходящих приложений", Toast.LENGTH_SHORT).show()
    }
}
