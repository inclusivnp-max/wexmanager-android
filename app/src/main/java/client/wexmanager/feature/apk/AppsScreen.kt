package client.wexmanager.feature.apk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.apk.ApkAnalyzer
import client.wexmanager.core.apk.InstalledApp
import client.wexmanager.core.fs.NaturalOrder
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.ActionGroup
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.feature.browser.SheetAction
import client.wexmanager.feature.browser.copyToSystemClipboard
import client.wexmanager.ui.components.iconShapeFor
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppsBarMode { Main, Search, Selection }

/** Локальный порядок уже загруженного списка: подпись или имя пакета. */
private enum class AppsNameOrder(val title: String) {
    Label("По имени"),
    Package("По пакету"),
}

@Composable
fun AppsScreen(onBack: () -> Unit, onReveal: (String) -> Unit, onOpenApk: (String) -> Unit) {
    val vm: AppsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var searching by rememberSaveable { mutableStateOf(false) }
    var sheetPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val selectionMode = state.selection.isNotEmpty()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val reveal by rememberUpdatedState(onReveal)

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is AppsEvent.Exported -> launch {
                    val text = when {
                        e.count == 1 && e.failed == 0 -> "Сохранено: ${e.path.substringAfterLast('/')}"
                        e.failed > 0 -> "Извлечено ${e.count}, с ошибкой ${e.failed}"
                        else -> "Извлечено приложений: ${e.count}"
                    }
                    val r = snackbar.showSnackbar(text, actionLabel = "Показать", withDismissAction = true, duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) reveal(e.path)
                }
                is AppsEvent.Share -> runCatching { FileOpener.share(context, listOf(e.file)) }
                    .onFailure { launch { snackbar.showSnackbar("Не удалось поделиться: ${it.message}") } }
                is AppsEvent.Message -> launch { snackbar.showSnackbar(e.text, withDismissAction = true) }
            }
        }
    }

    BackHandler(selectionMode) { vm.clearSelection() }
    BackHandler(!selectionMode && searching) {
        searching = false
        vm.setQuery("")
    }

    val mode = when {
        selectionMode -> AppsBarMode.Selection
        searching -> AppsBarMode.Search
        else -> AppsBarMode.Main
    }
    var nameOrder by rememberSaveable { mutableStateOf(AppsNameOrder.Label) }
    // Чипы имени/пакета сортируют уже показанный список. Размер и даты из меню не трогаем,
    // пока снова не выбран один из этих чипов.
    var nameOrderActive by rememberSaveable { mutableStateOf(true) }
    val orderedApps = remember(state.apps, nameOrder, nameOrderActive, state.options.descending) {
        if (!nameOrderActive) state.apps
        else {
            val comparator = compareBy(NaturalOrder) { app: InstalledApp ->
                when (nameOrder) {
                    AppsNameOrder.Label -> app.label
                    AppsNameOrder.Package -> app.packageName
                }
            }
            state.apps.sortedWith(if (state.options.descending) comparator.reversed() else comparator)
        }
    }
    val motion = MaterialTheme.motionScheme

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
                label = "appsBar",
            ) { m ->
                when (m) {
                    AppsBarMode.Main -> MainBar(
                        state,
                        vm,
                        onBack,
                        onSearch = { searching = true },
                        onSortApplied = { nameOrderActive = it == AppSort.Name },
                        scrollBehavior = scrollBehavior,
                    )
                    AppsBarMode.Search -> SearchBar(
                        query = state.options.query,
                        onQuery = vm::setQuery,
                        onClose = { searching = false; vm.setQuery("") },
                        scrollBehavior = scrollBehavior,
                    )
                    AppsBarMode.Selection -> SelectionBar(
                        count = state.selection.size,
                        onClose = vm::clearSelection,
                        onSelectAll = vm::selectAll,
                        onExtract = vm::extractSelected,
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedVisibility(
                visible = selectionMode && progress == null,
                enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text("Извлечь (${state.selection.size})") },
                    icon = { Icon(Icons.Rounded.Download, null) },
                    onClick = vm::extractSelected,
                )
            }
        },
        bottomBar = {
            ExportProgressBar(progress, onCancel = vm::cancelExport)
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            FilterRow(
                state,
                nameOrder,
                nameOrderActive,
                onFilter = vm::setFilter,
                onNameOrder = {
                    nameOrder = it
                    nameOrderActive = true
                },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.apps.isEmpty() -> Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        LoadingIndicator(Modifier.size(72.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Загрузка приложений…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    state.apps.isEmpty() -> if (state.options.query.isNotBlank()) {
                        ShapeMessage(Icons.Rounded.SearchOff, "Ничего не найдено", "По запросу «${state.options.query.trim()}» приложений нет")
                    } else {
                        ShapeMessage(Icons.Rounded.Apps, "Нет приложений", "В этой категории пусто")
                    }
                    else -> AppList(
                        state = state,
                        apps = orderedApps,
                        nameOrder = nameOrder,
                        vm = vm,
                        bottomPadding = padding.calculateBottomPadding(),
                        onClick = { a -> if (selectionMode) vm.toggleSelection(a.packageName) else sheetPackage = a.packageName },
                        onLongClick = { a -> vm.toggleSelection(a.packageName) },
                    )
                }
            }
        }
    }

    sheetPackage?.let { pkg ->
        val app = remember(pkg, state.apps) { vm.find(pkg) }
        if (app == null) {
            LaunchedEffect(pkg) { sheetPackage = null }
        } else {
            AppActionsSheet(
                app = app,
                vm = vm,
                onReveal = onReveal,
                onOpenApk = onOpenApk,
                onDismiss = { sheetPackage = null },
            )
        }
    }
}

// ------------------------------------------------------------------ Панели

@Composable
private fun MainBar(
    state: AppsUiState,
    vm: AppsViewModel,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onSortApplied: (AppSort) -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = { Text("Приложения") },
        subtitle = {
            val n = state.apps.size
            Text(
                if (state.loading && state.totalCount == 0) "Загрузка…"
                else "$n ${Format.plural(n, "приложение", "приложения", "приложений")}",
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
        },
        actions = {
            IconButton(onClick = onSearch, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Search, "Поиск") }
            SortMenu(state.options, vm, onSortApplied)
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun SortMenu(options: AppsViewOptions, vm: AppsViewModel, onSortApplied: (AppSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.Sort, "Сортировка") }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                val sorts = AppSort.entries
                sorts.forEachIndexed { i, s ->
                    SelectableDropdownMenuItem(
                        selected = options.sort == s,
                        onClick = { vm.setSort(s); onSortApplied(s); open = false },
                        text = { Text(s.title) },
                        shapes = MenuDefaults.itemShape(i, sorts.size),
                        leadingIcon = { Icon(sortIcon(s), null) },
                        selectedLeadingIcon = { Icon(Icons.Rounded.Check, null) },
                    )
                }
            }
            Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                CheckableDropdownMenuItem(
                    checked = options.descending,
                    onCheckedChange = { vm.setDescending(it) },
                    text = { Text("По убыванию") },
                    shapes = MenuDefaults.itemShape(0, 2),
                    leadingIcon = { Icon(Icons.Rounded.SwapVert, null) },
                    checkedLeadingIcon = { Icon(Icons.Rounded.Check, null) },
                )
                DropdownMenuItem(
                    text = { Text("Обновить список") },
                    leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                    onClick = { open = false; vm.load() },
                    shape = MenuDefaults.trailingItemShape,
                )
            }
        }
    }
}

private fun sortIcon(s: AppSort): ImageVector = when (s) {
    AppSort.Name -> Icons.Rounded.SortByAlpha
    AppSort.Size -> Icons.Rounded.DataUsage
    AppSort.Installed -> Icons.Rounded.InstallMobile
    AppSort.Updated -> Icons.Rounded.Update
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // Поле держит текст локально: состояние VM приходит асинхронно (combine на Default),
    // и при быстром наборе каретка «прыгала» бы, а символы терялись.
    var text by rememberSaveable { mutableStateOf(query) }
    val update: (String) -> Unit = {
        text = it
        onQuery(it)
    }
    TopAppBar(
        title = {
            TextField(
                value = text,
                onValueChange = update,
                singleLine = true,
                placeholder = { Text("Имя или пакет") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                trailingIcon = if (text.isNotEmpty()) {
                    { IconButton(onClick = { update("") }) { Icon(Icons.Rounded.Close, "Очистить") } }
                } else null,
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Закрыть поиск") }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}

@Composable
private fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onExtract: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
) {
    TopAppBar(
        title = { Text("Выбрано: $count") },
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Снять выделение") }
        },
        actions = {
            IconButton(onClick = onSelectAll, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.SelectAll, "Выбрать все") }
            IconButton(onClick = onExtract, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Download, "Извлечь выбранные") }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun FilterRow(
    state: AppsUiState,
    nameOrder: AppsNameOrder,
    nameOrderActive: Boolean,
    onFilter: (AppFilter) -> Unit,
    onNameOrder: (AppsNameOrder) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppFilter.entries.forEach { f ->
            val count = when (f) {
                AppFilter.User -> state.userCount
                AppFilter.System -> state.systemCount
                AppFilter.All -> state.totalCount
            }
            val selected = state.options.filter == f
            FilterChip(
                selected = selected,
                onClick = { onFilter(f) },
                label = { Text(if (state.totalCount > 0) "${f.title} · $count" else f.title) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                shape = RoundedCornerShape(12.dp),
            )
        }
        AppsNameOrder.entries.forEach { order ->
            val selected = nameOrderActive && nameOrder == order
            FilterChip(
                selected = selected,
                onClick = { onNameOrder(order) },
                label = { Text(order.title) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.SortByAlpha, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ Список

@Composable
private fun AppList(
    state: AppsUiState,
    apps: List<InstalledApp>,
    nameOrder: AppsNameOrder,
    vm: AppsViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onClick: (InstalledApp) -> Unit,
    onLongClick: (InstalledApp) -> Unit,
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    val refreshing = state.loading && state.apps.isNotEmpty()
    val motion = MaterialTheme.motionScheme
    LaunchedEffect(state.options.filter, state.options.sort, state.options.descending, nameOrder) {
        listState.scrollToItem(0)
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = vm::load,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        val count = apps.size
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding + 96.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(apps, key = { _, a -> a.packageName }, contentType = { _, _ -> "app" }) { i, a ->
                AppRow(
                    app = a,
                    index = i,
                    count = count,
                    selected = a.packageName in state.selection,
                    vm = vm,
                    onClick = { onClick(a) },
                    onLongClick = { onLongClick(a) },
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = motion.defaultEffectsSpec(),
                            placementSpec = motion.defaultSpatialSpec(),
                            fadeOutSpec = motion.fastEffectsSpec(),
                        )
                        .padding(horizontal = 16.dp)
                        .padding(top = if (i == 0) 0.dp else ListItemDefaults.SegmentedGap),
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    index: Int,
    count: Int,
    selected: Boolean,
    vm: AppsViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val units = LocalAppSettings.current.sizeUnits
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = "Выбрать",
        shapes = ListItemDefaults.segmentedShapes(index, count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = scheme.surfaceContainer,
            selectedContainerColor = scheme.secondaryContainer,
        ),
        leadingContent = { RowIcon(app, selected, vm) },
        // Три строки текста — по центру карточки, без «провала» снизу.
        verticalAlignment = Alignment.CenterVertically,
        supportingContent = {
            Column {
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    "${app.versionLabel} • ${Format.size(app.apkSize, units)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = if (app.hasSplits || app.isSystem || !app.enabled) {
            {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (app.hasSplits) TinyBadge("split", scheme.tertiaryContainer, scheme.onTertiaryContainer)
                    if (app.isSystem) TinyBadge("система", scheme.surfaceContainerHighest, scheme.onSurfaceVariant)
                    if (!app.enabled) TinyBadge("отключено", scheme.errorContainer, scheme.onErrorContainer)
                }
            }
        } else null,
        modifier = modifier,
    ) {
        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RowIcon(app: InstalledApp, selected: Boolean, vm: AppsViewModel) {
    val shape = iconShapeFor(LocalAppSettings.current.iconShape)
    val icon by produceState(vm.cachedIcon(app), app.packageName, app.lastUpdateTime) {
        if (value == null) value = vm.loadIcon(app)
    }
    val motion = MaterialTheme.motionScheme
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, motion.fastSpatialSpec(), label = "iconScale")
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        AppIconImage(
            image = icon?.image,
            adaptive = icon?.adaptive == true,
            size = 44.dp * scale,
            shape = shape,
        )
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
            exit = scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ Прогресс извлечения

@Composable
private fun ExportProgressBar(progress: ExportProgress?, onCancel: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    // Держим последнее значение (вне снапшот-состояния), чтобы карточка не пустела при исчезновении.
    val last = remember { arrayOfNulls<ExportProgress>(1) }
    if (progress != null) last[0] = progress
    AnimatedVisibility(
        visible = progress != null,
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
    ) {
        val p = last[0] ?: return@AnimatedVisibility
        val scheme = MaterialTheme.colorScheme
        val animated by animateFloatAsState(p.fraction, motion.defaultEffectsSpec(), label = "exportProgress")
        Surface(
            color = scheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapedIcon(
                        if (p.sharing) Icons.Rounded.Share else Icons.Rounded.Download,
                        MaterialShapes.Cookie6Sided,
                        scheme.primaryContainer,
                        scheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                p.sharing -> "Подготовка к отправке"
                                p.total > 1 -> "Извлечение ${p.index + 1} из ${p.total}"
                                else -> "Извлечение APK"
                            },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "${p.title} • ${(p.fraction * 100).toInt()} %",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Отменить") }
                }
                Spacer(Modifier.height(10.dp))
                LinearWavyProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Действия с приложением

@Composable
private fun AppActionsSheet(
    app: InstalledApp,
    vm: AppsViewModel,
    onReveal: (String) -> Unit,
    onOpenApk: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val units = LocalAppSettings.current.sizeUnits
    // Запрос к PackageManager (IPC) — не на главном потоке.
    val launchable by produceState(false, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getLaunchIntentForPackage(app.packageName) != null }.getOrDefault(false)
        }
    }
    val icon by produceState(vm.cachedIcon(app), app.packageName) { if (value == null) value = vm.loadIcon(app) }
    AppBottomSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(icon?.image, icon?.adaptive == true, 56.dp, iconShapeFor(LocalAppSettings.current.iconShape))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    buildString {
                        append(app.versionLabel)
                        append(" • ").append(Format.size(app.apkSize, units))
                        append(" • Android ").append(ApkAnalyzer.androidVersion(app.targetSdk))
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        ActionGroup(
            buildList {
                if (launchable) add(SheetAction("Открыть приложение", Icons.AutoMirrored.Rounded.OpenInNew) { launchApp(context, app.packageName) })
                add(SheetAction("Сведения о приложении", Icons.Rounded.Info, "Системные настройки") { openAppDetails(context, app.packageName) })
                add(SheetAction("Анализ APK", Icons.Rounded.QueryStats, "Разрешения, компоненты, подписи") { onOpenApk(app.sourceDir) })
            },
            onDismiss,
        )
        ActionGroup(
            listOf(
                SheetAction(
                    "Извлечь APK",
                    Icons.Rounded.Download,
                    if (app.hasSplits) "Архив .apks из ${app.allApks.size} частей → WexManager/Apps" else "→ WexManager/Apps/${app.exportFileName}",
                ) { vm.extract(listOf(app)) },
                SheetAction("Поделиться APK", Icons.Rounded.Share, if (app.hasSplits) "Будет отправлен .apks" else null) { vm.share(app) },
                SheetAction("Показать APK в папке", Icons.Rounded.FolderOpen, app.sourceDir) { onReveal(app.sourceDir) },
            ),
            onDismiss,
        )
        ActionGroup(
            listOf(
                SheetAction(
                    if (app.isUpdatedSystem) "Удалить обновления" else "Удалить",
                    Icons.Rounded.Delete,
                    if (app.isSystem && !app.isUpdatedSystem) "Системное приложение — может не удалиться" else null,
                ) { uninstall(context, app.packageName) },
                SheetAction("Копировать имя пакета", Icons.Rounded.ContentCopy) { copyToSystemClipboard(context, app.packageName) },
            ),
            onDismiss,
        )
    }
}

private fun uninstall(context: Context, packageName: String) {
    @Suppress("DEPRECATION")
    val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Не удалось открыть удаление", Toast.LENGTH_SHORT).show() }
}
