package client.wexmanager.feature.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.Location
import client.wexmanager.core.fs.StorageRepository
import client.wexmanager.core.settings.ViewMode
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.FastScroller
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.components.asScrollTarget
import client.wexmanager.ui.theme.LocalAppSettings

/** Снимок содержимого для AnimatedContent — чтобы уходящая папка показывала свои файлы. */
private data class PanelSnapshot(
    val location: Location,
    val items: List<FileItem>,
    val loading: Boolean,
    val error: String?,
    val filterActive: Boolean,
    val direction: NavDirection,
    val version: Long,
)

class ScrollMemory {
    val positions = HashMap<String, Pair<Int, Int>>()
}

@Composable
fun PanelView(
    panel: PanelController,
    state: PanelState,
    isActive: Boolean,
    showActiveIndicator: Boolean,
    storage: StorageRepository,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onActivate: () -> Unit,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /** Создать в этой папке (true — папку); null — нельзя (архив, нет прав). */
    onCreate: ((Boolean) -> Unit)? = null,
) {
    val memory = remember { ScrollMemory() }
    Column(modifier.fillMaxSize()) {
        PanelHeader(
            state = state,
            isActive = isActive,
            showActiveIndicator = showActiveIndicator,
            storage = storage,
            onNavigate = { panel.open(it) },
            onActivate = onActivate,
        )
        AnimatedVisibility(
            visible = state.filterVisible,
            enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
            exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        ) {
            FilterField(state, onChange = panel::setFilter, onClose = { panel.showFilter(false) })
        }
        val localPath = (state.location as? Location.Local)?.path
        val context = androidx.compose.ui.platform.LocalContext.current
        val offerGrant = localPath != null &&
            client.wexmanager.core.fs.SafAccess.isRestricted(localPath) &&
            client.wexmanager.core.fs.SafAccess.bestGrant(context, localPath) == null
        if (offerGrant && state.error == null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    "Чужие папки здесь скрыты",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { client.wexmanager.safRequests.tryEmit(localPath) }) { Text("Разрешить") }
            }
        }
        val snapshot = PanelSnapshot(
            state.location, state.items, state.loading, state.error, state.filter.isNotEmpty(), state.direction, state.loadVersion,
        )
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = snapshot,
            contentKey = { it.version },
            transitionSpec = {
                val dir = targetState.direction
                val spec: ContentTransform = when (dir) {
                    NavDirection.Forward -> (slideInHorizontally(motion.defaultSpatialSpec()) { it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                        .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { -it / 4 } + fadeOut(motion.fastEffectsSpec()))
                    NavDirection.Backward -> (slideInHorizontally(motion.defaultSpatialSpec()) { -it / 3 } + fadeIn(motion.defaultEffectsSpec()))
                        .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { it / 4 } + fadeOut(motion.fastEffectsSpec()))
                    NavDirection.None -> fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec()))
                }
                spec
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "panelContent",
        ) { snap ->
            PanelBody(
                snap = snap,
                panel = panel,
                state = state,
                memory = memory,
                onItemClick = onItemClick,
                onItemLongClick = onItemLongClick,
                onRequestPermission = onRequestPermission,
                contentPadding = contentPadding,
                onCreate = onCreate,
            )
        }
    }
}

@Composable
private fun PanelBody(
    snap: PanelSnapshot,
    panel: PanelController,
    state: PanelState,
    memory: ScrollMemory,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues,
    onCreate: ((Boolean) -> Unit)?,
) {
    val isCurrent = snap.location == state.location
    when {
        snap.loading && snap.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(64.dp))
        }
        snap.error != null -> ErrorState(
            snap.error,
            onRetry = { panel.refresh() },
            onRequestPermission = onRequestPermission,
            onGrantFolder = (state.location as? Location.Local)?.path?.let { path ->
                if (client.wexmanager.core.fs.SafAccess.isRestricted(path)) {
                    { client.wexmanager.safRequests.tryEmit(path) }
                } else null
            },
        )
        else -> {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isCurrent && state.loading && state.items.isNotEmpty(),
                onRefresh = { panel.refresh() },
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = isCurrent && state.loading && state.items.isNotEmpty(),
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (snap.items.isEmpty()) {
                    EmptyState(filtered = snap.filterActive, hidden = state.hiddenCount, onCreate = onCreate)
                } else {
                    FileListContent(
                        snap = snap,
                        state = state,
                        panel = panel,
                        memory = memory,
                        isCurrent = isCurrent,
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileListContent(
    snap: PanelSnapshot,
    state: PanelState,
    panel: PanelController,
    memory: ScrollMemory,
    isCurrent: Boolean,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit,
    contentPadding: PaddingValues,
) {
    val s = LocalAppSettings.current
    val key = snap.location.key
    val focusIndex = remember(snap.version) {
        state.focusKey?.let { fk -> snap.items.indexOfFirst { it.key == fk || it.path == fk } }?.takeIf { it >= 0 }
    }
    val saved = memory.positions[key]
    val initialIndex = when {
        snap.direction == NavDirection.Backward && saved != null -> saved.first
        focusIndex != null -> (focusIndex - 3).coerceAtLeast(0)
        else -> 0
    }
    val initialOffset = if (snap.direction == NavDirection.Backward && saved != null) saved.second else 0
    val items = if (isCurrent) state.items else snap.items
    val selection = if (isCurrent) state.selection else emptySet()
    var highlightKey by remember(snap.version) { mutableStateOf(state.focusKey) }
    LaunchedEffect(snap.version) {
        kotlinx.coroutines.delay(2000)
        highlightKey = null
    }
    val currentItems by rememberUpdatedState(items)
    var dragBase by remember { mutableStateOf<Set<String>>(emptySet()) }
    val listState = rememberLazyListState(initialIndex, initialOffset)
    val gridState = rememberLazyGridState(initialIndex, initialOffset)

    // Фокус в той же папке (после «Создать», «Показать в папке»): прокрутить и подсветить.
    LaunchedEffect(state.focusKey, isCurrent) {
        val fk = state.focusKey ?: return@LaunchedEffect
        if (!isCurrent) return@LaunchedEffect
        val idx = currentItems.indexOfFirst { it.key == fk || it.path == fk }
        if (idx >= 0) {
            val to = (idx - 3).coerceAtLeast(0)
            if (s.viewMode == ViewMode.List) listState.animateScrollToItem(to) else gridState.animateScrollToItem(to)
            highlightKey = fk
        }
        panel.consumeFocus()
        kotlinx.coroutines.delay(2000)
        if (highlightKey == fk) highlightKey = null
    }

    Box(Modifier.fillMaxSize()) {
        if (s.viewMode == ViewMode.List) {
            DisposableEffect(key) {
                onDispose { memory.positions[key] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            }
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                modifier = Modifier
                    .fillMaxSize()
                    .dragToSelect(
                        hitTester = listState.hitTester(),
                        isSelected = { idx -> currentItems.getOrNull(idx)?.key in selection },
                        onRange = { a, b, select -> panel.selectRange(a, b, dragBase, select) },
                        onStart = { dragBase = panel.state.value.selection },
                        onEnd = {},
                    ),
            ) {
                itemsIndexed(items, key = { _, it -> it.key }, contentType = { _, _ -> 0 }) { _, item ->
                    FileRow(
                        item = item,
                        selected = item.key in selection,
                        highlight = s.highlightReturnedFolder && item.key == highlightKey,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        onIconClick = { panel.toggleSelection(item) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                    )
                }
            }
            if (s.showScrollbar) {
                FastScroller(
                    target = remember(listState) { listState.asScrollTarget() },
                    label = { idx -> currentItems.getOrNull(idx)?.name?.firstOrNull()?.uppercase() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, bottom = contentPadding.calculateBottomPadding()),
                )
            }
        } else {
            DisposableEffect(key) {
                onDispose { memory.positions[key] = gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(s.gridMinCellDp.dp),
                state = gridState,
                contentPadding = PaddingValues(
                    start = 8.dp, end = 8.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, it -> it.key }) { _, item ->
                    FileGridCell(
                        item = item,
                        selected = item.key in selection,
                        highlight = s.highlightReturnedFolder && item.key == highlightKey,
                        onClick = { onItemClick(item) },
                        onLongClick = { onItemLongClick(item) },
                        onIconClick = { panel.toggleSelection(item) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                    )
                }
            }
            if (s.showScrollbar) {
                FastScroller(
                    target = remember(gridState) { gridState.asScrollTarget() },
                    label = { idx -> currentItems.getOrNull(idx)?.name?.firstOrNull()?.uppercase() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, bottom = contentPadding.calculateBottomPadding()),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Шапка панели

private data class Crumb(val title: String, val location: Location, val icon: ImageVector?)

private fun crumbs(location: Location, storage: StorageRepository): List<Crumb> {
    fun localCrumbs(path: String): MutableList<Crumb> {
        val volumes = storage.roots()
        val vol = volumes.filter { path == it.path || path.startsWith(it.path + "/") }.maxByOrNull { it.path.length }
        val result = mutableListOf<Crumb>()
        val rest: String
        if (vol != null) {
            result += Crumb(if (vol.isPrimary) "Память" else vol.title, Location.Local(vol.path), if (vol.isPrimary) Icons.Rounded.PhoneAndroid else Icons.Rounded.SdCard)
            rest = path.removePrefix(vol.path)
        } else {
            result += Crumb("/", Location.Local("/"), Icons.Rounded.Storage)
            rest = path
        }
        var acc = vol?.path ?: ""
        rest.split('/').filter { it.isNotEmpty() }.forEach { part ->
            acc = "$acc/$part"
            result += Crumb(part, Location.Local(acc), null)
        }
        return result
    }
    return when (location) {
        is Location.Local -> localCrumbs(location.path)
        is Location.Archive -> {
            val list = localCrumbs(java.io.File(location.archivePath).parent ?: "/")
            list += Crumb(location.archivePath.substringAfterLast('/'), Location.Archive(location.archivePath), Icons.Rounded.FolderZip)
            var acc = ""
            location.inner.split('/').filter { it.isNotEmpty() }.forEach { part ->
                acc = if (acc.isEmpty()) part else "$acc/$part"
                list += Crumb(part, Location.Archive(location.archivePath, acc), null)
            }
            list
        }
    }
}

@Composable
private fun PanelHeader(
    state: PanelState,
    isActive: Boolean,
    showActiveIndicator: Boolean,
    storage: StorageRepository,
    onNavigate: (Location) -> Unit,
    onActivate: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    val bg by animateColorAsState(
        if (showActiveIndicator && isActive) {
            // primaryContainer в Expresssive светлый: 55% альфы затирает тёмный текст шапки.
            scheme.primary.copy(alpha = 0.18f).compositeOver(scheme.surfaceContainer)
        } else scheme.surfaceContainer,
        label = "headerBg",
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    val list = remember(state.location) { crumbs(state.location, storage) }
    val rowState = rememberLazyListState()
    LaunchedEffect(list) { rowState.animateScrollToItem((list.size - 1).coerceAtLeast(0)) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onActivate),
    ) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            itemsIndexed(list, key = { i, c -> "$i:${c.location.key}" }) { i, crumb ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (i > 0) Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                        tint = scheme.outline, modifier = Modifier.size(16.dp),
                    )
                    val last = i == list.lastIndex
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                onClick = { onActivate(); onNavigate(crumb.location) },
                                onLongClick = { copyToSystemClipboard(context, crumb.location.displayPath) },
                            )
                            .background(if (last) scheme.secondaryContainer else Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        crumb.icon?.let {
                            Icon(it, null, modifier = Modifier.size(16.dp), tint = if (last) scheme.onSecondaryContainer else scheme.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            crumb.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (last) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                            fontWeight = if (last) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Папка открыта через root: подсказываем, что здесь только просмотр.
            val viaRoot = (state.location as? client.wexmanager.core.fs.Location.Local)?.path?.let { p ->
                remember(p, state.loadVersion) { client.wexmanager.core.fs.RootShell.enabled && !java.io.File(p).canRead() }
            } == true
            if (viaRoot) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = scheme.tertiaryContainer,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Text(
                        "root · чтение",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            val parts = buildList {
                if (state.computingSizes) add("подсчёт размеров…")
                if (state.dirCount > 0) add(Format.folders(state.dirCount))
                if (state.fileCount > 0) add(Format.files(state.fileCount) + " (${Format.size(state.filesSize, s.sizeUnits)})")
                if (state.hiddenCount > 0) add("скрыто ${state.hiddenCount}")
            }
            Text(
                if (parts.isEmpty()) (if (state.loading) "Загрузка…" else "Пусто") else parts.joinToString("  •  "),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            val st = state.storage
            if (s.showStorageInfo && st != null && st.total > 0) {
                Text(
                    "${Format.size(st.free, s.sizeUnits)} своб.",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { st.usedFraction },
                    modifier = Modifier.width(44.dp).height(5.dp),
                    color = if (st.usedFraction > 0.9f) scheme.error else scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                    gapSize = 2.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun FilterField(state: PanelState, onChange: (String) -> Unit, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TextField(
        value = state.filter,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Фильтр: текст, *.apk, /regex, !исключить") },
        leadingIcon = { Icon(Icons.Rounded.FilterList, null) },
        trailingIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть фильтр") } },
        isError = !state.filterValid,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .focusRequester(focus),
    )
}

@Composable
private fun EmptyState(filtered: Boolean, hidden: Int, onCreate: ((Boolean) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    // Фигура несколько раз «вздыхает» при появлении и замирает: живо, но без вечной перерисовки.
    val breathAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        repeat(2) {
            breathAnim.animateTo(1f, androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            breathAnim.animateTo(0f, androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        }
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val shape = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
        Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val breath = breathAnim.value
                        rotationZ = breath * 20f
                        val sc = 0.94f + breath * 0.06f
                        scaleX = sc
                        scaleY = sc
                    }
                    .clip(shape)
                    .background(if (filtered) scheme.tertiaryContainer else scheme.primaryContainer),
            )
            Icon(
                if (filtered) Icons.Rounded.SearchOff else Icons.Rounded.FolderOff, null,
                tint = if (filtered) scheme.onTertiaryContainer else scheme.onPrimaryContainer, modifier = Modifier.size(54.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            if (filtered) "Ничего не найдено" else "Папка пуста",
            style = MaterialTheme.typography.titleLarge,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                filtered -> "Измените условие фильтра"
                hidden > 0 -> "Скрытых элементов: $hidden — включите их показ в меню «⋮»"
                else -> "Здесь пока ничего нет"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (!filtered && onCreate != null) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(onClick = { onCreate(true) }) { Text("Создать папку") }
                androidx.compose.material3.OutlinedButton(onClick = { onCreate(false) }) { Text("Создать файл") }
            }
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onRequestPermission: () -> Unit,
    onGrantFolder: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val denied = message.startsWith("Нет доступа")
    val context = androidx.compose.ui.platform.LocalContext.current
    val canGrant = denied && !client.wexmanager.core.util.StoragePermission.isGranted(context)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val shape = remember { PolygonShape(MaterialShapes.Clover4Leaf) }
        Box(
            Modifier.size(112.dp).clip(shape).background(scheme.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (denied) Icons.Rounded.Lock else Icons.Rounded.Error, null, tint = scheme.onErrorContainer, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(if (denied) "Нет доступа к папке" else "Не удалось открыть", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRetry) { Text("Повторить") }
            if (canGrant) FilledTonalButton(onClick = onRequestPermission) { Text("Выдать доступ") }
            if (onGrantFolder != null) FilledTonalButton(onClick = onGrantFolder) { Text("Разрешить папку") }
        }
    }
}
