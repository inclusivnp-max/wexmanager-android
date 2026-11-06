package client.wexmanager.feature.search

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.FindInPage
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.settings.AppSettings
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.core.util.OpenAction
import client.wexmanager.feature.browser.ActionGroup
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.feature.browser.SheetAction
import client.wexmanager.feature.browser.SwitchRow
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.components.PressIconButton
import client.wexmanager.ui.navigation.Route
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Pane { Idle, Error, Searching, Empty, Results }

@Composable
fun SearchScreen(root: String, onBack: () -> Unit, onNavigate: (Route) -> Unit, onReveal: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: SearchViewModel = viewModel(key = "search:$root") { SearchViewModel(app, root) }
    val params by vm.params.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val recent by vm.recentQueries.collectAsStateWithLifecycle()
    val roots by vm.roots.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    val keyboard = LocalSoftwareKeyboardController.current

    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var sheetHit by remember { mutableStateOf<SearchHit?>(null) }

    val scope = rememberCoroutineScope()
    val open: (SearchHit) -> Unit = { hit ->
        keyboard?.hide()
        vm.rememberQuery()
        scope.launch { openHit(hit, settings, context, onNavigate, onReveal) }
    }

    BackHandler(filtersOpen) { filtersOpen = false }

    Scaffold(
        topBar = {
            SearchTopBar(
                queryState = { vm.query },
                params = params,
                sort = sort,
                isError = status.error != null && params.regex,
                onQueryChange = vm::onQueryChange,
                onSubmit = {
                    keyboard?.hide()
                    vm.submit()
                },
                onToggleRegex = { v -> vm.updateParams { it.copy(regex = v) } },
                onToggleCase = { v -> vm.updateParams { it.copy(caseSensitive = v) } },
                onSort = vm::setSort,
                onBack = onBack,
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
        ) {
            RecentQueryChips(queries = recent) { q ->
                keyboard?.hide()
                vm.applyRecent(q)
            }
            FilterChipsRow(
                params = params,
                open = filtersOpen,
                onToggleOpen = { filtersOpen = !filtersOpen },
                onToggleType = { t -> vm.updateParams { p -> p.copy(types = if (t in p.types) p.types - t else p.types + t) } },
            )
            AnimatedVisibility(
                visible = filtersOpen,
                enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
                FilterPanel(params = params, roots = roots, onChange = vm::updateParams)
            }
            AnimatedVisibility(
                visible = params.isRunnable && (status.running || status.done),
                enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                StatusBar(status = status, root = params.root, onStop = vm::stop, onRetry = vm::retry)
            }

            val pane = when {
                status.error != null && !status.running && results.isEmpty() -> Pane.Error
                !params.isRunnable -> Pane.Idle
                results.isNotEmpty() -> Pane.Results
                status.running || !status.done -> Pane.Searching
                else -> Pane.Empty
            }
            val primaryRoot = roots.firstOrNull { it.isVolume }
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = pane,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "searchPane",
            ) { p ->
                when (p) {
                    Pane.Idle -> IdlePane(
                        rootTitle = roots.firstOrNull { it.path == params.root }?.title ?: params.root.substringAfterLast('/'),
                        history = history,
                        onHistory = { q ->
                            keyboard?.hide()
                            vm.applyHistory(q)
                        },
                        onRemoveHistory = vm::removeHistory,
                        onClearHistory = vm::clearHistory,
                        onPreset = { preset -> preset.action(vm) },
                    )
                    Pane.Error -> MessagePane(
                        shape = MaterialShapes.Clover4Leaf,
                        icon = Icons.Rounded.ErrorOutline,
                        error = true,
                        title = if (params.regex) "Ошибка в выражении" else "Ошибка поиска",
                        text = status.error.orEmpty(),
                    )
                    Pane.Searching -> SearchingPane(status)
                    Pane.Empty -> MessagePane(
                        shape = MaterialShapes.Cookie9Sided,
                        icon = Icons.Rounded.SearchOff,
                        error = false,
                        title = "Ничего не найдено",
                        text = "Просмотрено ${Format.items(status.scanned)}. Попробуйте изменить запрос или фильтры.",
                        action = when {
                            params.hasFilters || params.extraFilterCount > 0 -> "Сбросить фильтры" to {
                                vm.updateParams { it.copy(types = emptySet(), size = null, date = null, includeHidden = false, inContent = false) }
                            }
                            primaryRoot != null && primaryRoot.path != params.root -> "Искать во всей памяти" to {
                                vm.updateParams { it.copy(root = primaryRoot.path) }
                            }
                            else -> null
                        },
                    )
                    Pane.Results -> ResultsList(
                        results = results,
                        onClick = open,
                        onLongClick = { sheetHit = it },
                        onScroll = { keyboard?.hide() },
                    )
                }
            }
        }
    }

    sheetHit?.let { hit ->
        ResultSheet(
            hit = hit,
            onOpen = { open(hit) },
            onReveal = {
                vm.rememberQuery()
                onReveal(hit.item.path)
            },
            onDismiss = { sheetHit = null },
        )
    }
}

private suspend fun openHit(hit: SearchHit, settings: AppSettings, context: Context, onNavigate: (Route) -> Unit, onReveal: (String) -> Unit) {
    val item = hit.item
    if (item.isDirectory) {
        onReveal(item.path)
        return
    }
    // actionFor может читать начало файла (определение текста/архива) — только в фоне.
    when (val action = withContext(Dispatchers.IO) { FileOpener.actionFor(item, settings) }) {
        is OpenAction.Navigate -> onNavigate(action.route)
        is OpenAction.External -> FileOpener.openExternal(context, File(action.path))
        is OpenAction.Enter -> onReveal(item.path)
        is OpenAction.ChooseOpenAs -> FileOpener.openExternal(context, File(item.path))
    }
}

// ------------------------------------------------------------------ Шапка

@Composable
private fun SearchTopBar(
    queryState: () -> String,
    params: SearchParams,
    sort: ResultSort,
    isError: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleRegex: (Boolean) -> Unit,
    onToggleCase: (Boolean) -> Unit,
    onSort: (ResultSort) -> Unit,
    onBack: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val query = queryState()
    val focus = remember { FocusRequester() }
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!autoFocused) {
            delay(120)
            runCatching { focus.requestFocus() }
            autoFocused = true
        }
    }
    Surface(color = scheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", onBack)
            TextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = {
                    Text(
                        if (params.inContent) "Текст в файлах…" else "Имя, *.mp4, IMG_??…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 4.dp)) {
                        AnimatedVisibility(query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, "Очистить", modifier = Modifier.size(20.dp))
                            }
                        }
                        ModeToggle(".*", "Регулярное выражение", params.regex, onToggleRegex)
                        ModeToggle("Aa", "Учитывать регистр", params.caseSensitive, onToggleCase)
                    }
                },
                isError = isError,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search,
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                textStyle = if (params.regex) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = scheme.surfaceContainerHigh,
                    unfocusedContainerColor = scheme.surfaceContainerHigh,
                    errorContainerColor = scheme.errorContainer.copy(alpha = 0.5f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focus),
            )
            SortMenuButton(sort, onSort)
        }
    }
}

@Composable
private fun ModeToggle(text: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    IconToggleButton(
        checked = checked,
        onCheckedChange = onChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 1.dp),
        )
    }
    Spacer(Modifier.width(2.dp))
}

@Composable
private fun SortMenuButton(sort: ResultSort, onSort: (ResultSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.Sort, "Сортировка")
        }
        DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                val all = ResultSort.entries
                all.forEachIndexed { i, s ->
                    SelectableDropdownMenuItem(
                        selected = s == sort,
                        onClick = {
                            onSort(s)
                            open = false
                        },
                        text = { Text(s.label) },
                        shapes = MenuDefaults.itemShape(i, all.size),
                        selectedLeadingIcon = { Icon(Icons.Rounded.Check, null) },
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Запросы сессии

@Composable
private fun RecentQueryChips(queries: List<String>, onPick: (String) -> Unit) {
    AnimatedVisibility(
        visible = queries.isNotEmpty(),
        enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(
                "Запросы сессии",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                queries.forEach { q ->
                    key(q) {
                        AssistChip(
                            onClick = { onPick(q) },
                            label = { Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(AssistChipDefaults.IconSize)) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .semantics { contentDescription = "Найти «$q»" },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Фильтры

@Composable
private fun FilterChipsRow(
    params: SearchParams,
    open: Boolean,
    onToggleOpen: () -> Unit,
    onToggleType: (TypeFilter) -> Unit,
) {
    val rotation by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "expand")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        item(key = "filters") {
            val extra = params.extraFilterCount
            FilterChip(
                selected = open || extra > 0,
                onClick = onToggleOpen,
                label = { Text(if (extra > 0) "Фильтры · $extra" else "Фильтры") },
                shapes = FilterChipDefaults.shapes(),
                leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(FilterChipDefaults.IconSize)) },
                trailingIcon = { Icon(Icons.Rounded.ExpandMore, null, Modifier.size(FilterChipDefaults.IconSize).rotate(rotation)) },
            )
        }
        items(TypeFilter.entries, key = { it.name }) { t ->
            val selected = t in params.types
            FilterChip(
                selected = selected,
                onClick = { onToggleType(t) },
                label = { Text(t.label) },
                shapes = FilterChipDefaults.shapes(),
                leadingIcon = {
                    AnimatedContent(selected, label = "chipIcon") { sel ->
                        Icon(if (sel) Icons.Rounded.Check else t.icon, null, Modifier.size(FilterChipDefaults.IconSize))
                    }
                },
            )
        }
    }
}

@Composable
private fun FilterPanel(params: SearchParams, roots: List<SearchRoot>, onChange: ((SearchParams) -> SearchParams) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = scheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(
            Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            PanelLabel("Размер", Icons.Rounded.Straighten)
            ChipLine {
                SizeFilter.entries.forEach { f ->
                    SelectChip(f.label, params.size == f) { onChange { it.copy(size = if (it.size == f) null else f) } }
                }
            }
            PanelLabel("Дата изменения", Icons.Rounded.Schedule)
            ChipLine {
                DateFilter.entries.forEach { f ->
                    SelectChip(f.label, params.date == f) { onChange { it.copy(date = if (it.date == f) null else f) } }
                }
            }
            PanelLabel("Где искать", Icons.Rounded.FolderOpen)
            ChipLine {
                roots.forEach { r ->
                    SelectChip(
                        label = r.title,
                        selected = params.root == r.path,
                        icon = when {
                            !r.isVolume -> Icons.Rounded.Folder
                            r.path == roots.firstOrNull { it.isVolume }?.path -> Icons.Rounded.PhoneAndroid
                            else -> Icons.Rounded.SdCard
                        },
                    ) { onChange { it.copy(root = r.path) } }
                }
            }
            Spacer(Modifier.height(6.dp))
            SwitchRow("Скрытые файлы", Icons.Rounded.VisibilityOff, params.includeHidden, supporting = "Имена, начинающиеся с точки") { v ->
                onChange { it.copy(includeHidden = v) }
            }
            SwitchRow("Искать в содержимом", Icons.Rounded.FindInPage, params.inContent, supporting = if (Ripgrep.available) "ripgrep: файлы до 20 МБ, двоичные пропускаются" else "Текстовые файлы до 5 МБ, построчно") { v ->
                onChange { it.copy(inContent = v) }
            }
            AnimatedVisibility(params.hasFilters || params.extraFilterCount > 0) {
                TextButton(
                    onClick = { onChange { it.copy(types = emptySet(), size = null, date = null, includeHidden = false, inContent = false) } },
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(Icons.Rounded.FilterAltOff, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Сбросить фильтры")
                }
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ChipLine(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        shapes = FilterChipDefaults.shapes(),
        leadingIcon = when {
            selected -> ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) })
            icon != null -> ({ Icon(icon, null, Modifier.size(FilterChipDefaults.IconSize)) })
            else -> null
        },
    )
}

// ------------------------------------------------------------------ Статус

@Composable
private fun StatusBar(status: SearchStatus, root: String, onStop: () -> Unit, onRetry: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (status.running) scheme.secondaryContainer else scheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val parts = buildList {
                        add("Найдено ${status.found}")
                        add("просмотрено ${status.scanned}")
                        if (!status.running && status.elapsedMs > 0) add(String.format(Format.RU, "%.1f с", status.elapsedMs / 1000f))
                        if (status.stopped) add("остановлено")
                        if (status.limitReached) add("лимит ${SearchEngine.MAX_RESULTS}")
                    }
                    Text(
                        parts.joinToString("  •  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (status.running) scheme.onSecondaryContainer else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (status.running) {
                        val rel = status.currentDir.removePrefix(root).ifEmpty { "/" }
                        Text(
                            rel,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                    }
                }
                AnimatedContent(status.running, label = "statusAction") { running ->
                    if (running) {
                        FilledTonalIconButton(onClick = onStop, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Stop, "Остановить")
                        }
                    } else {
                        IconButton(onClick = onRetry, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Rounded.Refresh, "Искать снова")
                        }
                    }
                }
            }
            AnimatedVisibility(status.running) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, end = 10.dp),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ Пустые состояния

private class Preset(val title: String, val icon: ImageVector, val action: (SearchViewModel) -> Unit)

private val presets = listOf(
    Preset("Большие > 100 МБ", Icons.Rounded.Straighten) { vm ->
        vm.updateParams { it.copy(types = emptySet(), size = SizeFilter.Over100M, date = null, inContent = false) }
    },
    Preset("Изменено сегодня", Icons.Rounded.Today) { vm ->
        vm.updateParams { it.copy(types = emptySet(), size = null, date = DateFilter.Today, inContent = false) }
    },
    Preset("Видео", TypeFilter.Video.icon) { vm ->
        vm.updateParams { it.copy(types = setOf(TypeFilter.Video), inContent = false) }
    },
    Preset("APK", TypeFilter.Apk.icon) { vm ->
        vm.updateParams { it.copy(types = setOf(TypeFilter.Apk), inContent = false) }
    },
    Preset("Архивы", TypeFilter.Archives.icon) { vm ->
        vm.updateParams { it.copy(types = setOf(TypeFilter.Archives), inContent = false) }
    },
    Preset("Скриншоты", Icons.Rounded.Screenshot) { vm -> vm.search("Screenshot") },
)

@Composable
private fun IdlePane(
    rootTitle: String,
    history: List<String>,
    onHistory: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPreset: (Preset) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottom + 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "hero") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val shape = remember { PolygonShape(MaterialShapes.Cookie9Sided) }
                Box(
                    Modifier
                        .size(104.dp)
                        .clip(shape)
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.TravelExplore, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Поиск в «$rootTitle»", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Подстрока, шаблон *.apk или IMG_??.jpg,\n«.*» — регулярное выражение, !текст — исключить",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
        item(key = "presetsLabel") {
            Text(
                "Быстрый поиск",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
            )
        }
        item(key = "presets") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(presets, key = { it.title }) { p ->
                    AssistChip(
                        onClick = { onPreset(p) },
                        label = { Text(p.title) },
                        leadingIcon = { Icon(p.icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        shape = RoundedCornerShape(12.dp),
                    )
                }
            }
        }
        if (history.isNotEmpty()) {
            item(key = "historyLabel") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Недавние запросы", style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClearHistory) { Text("Очистить") }
                }
            }
            itemsIndexed(history, key = { _, q -> "h:$q" }) { i, q ->
                SegmentedListItem(
                    onClick = { onHistory(q) },
                    shapes = ListItemDefaults.segmentedShapes(i, history.size),
                    leadingContent = { Icon(Icons.Rounded.History, null) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onRemoveHistory(q) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, "Удалить из истории", Modifier.size(18.dp))
                            }
                            Icon(Icons.Rounded.NorthWest, null, Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = ListItemDefaults.SegmentedGap)
                        .animateItem(),
                ) {
                    Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun SearchingPane(status: SearchStatus) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator(Modifier.size(72.dp))
        Spacer(Modifier.height(16.dp))
        Text("Ищем…", style = MaterialTheme.typography.titleMedium)
        if (status.scanned > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Просмотрено ${Format.items(status.scanned)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessagePane(
    shape: androidx.graphics.shapes.RoundedPolygon,
    icon: ImageVector,
    error: Boolean,
    title: String,
    text: String,
    action: Pair<String, () -> Unit>? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val clip = remember(shape) { PolygonShape(shape) }
        Box(
            Modifier
                .size(116.dp)
                .clip(clip)
                .background(if (error) scheme.errorContainer else scheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer, modifier = Modifier.size(50.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (text.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            FilledTonalButton(onClick = action.second) { Text(action.first) }
        }
    }
}

// ------------------------------------------------------------------ Результаты

@Composable
private fun ResultsList(
    results: List<SearchHit>,
    onClick: (SearchHit) -> Unit,
    onLongClick: (SearchHit) -> Unit,
    onScroll: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) onScroll() }
    // Пока пользователь у начала списка, держим его там: более релевантные результаты вставляются сверху.
    val seen = remember { arrayOfNulls<List<SearchHit>>(1) }
    SideEffect {
        if (seen[0] !== results) {
            seen[0] = results
            if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 && !listState.isScrollInProgress) {
                listState.requestScrollToItem(0)
            }
        }
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val motion = MaterialTheme.motionScheme
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(top = 4.dp, bottom = bottom + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(results, key = { it.item.path }, contentType = { if (it.content != null) 1 else 0 }) { hit ->
            ResultRow(
                hit = hit,
                onClick = { onClick(hit) },
                onLongClick = { onLongClick(hit) },
                modifier = Modifier.animateItem(
                    fadeInSpec = motion.defaultEffectsSpec(),
                    placementSpec = motion.defaultSpatialSpec(),
                    fadeOutSpec = motion.fastEffectsSpec(),
                ),
            )
        }
    }
}

private fun highlight(text: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    for (r in ranges) {
        if (r.isEmpty() || r.first < 0 || r.last >= text.length) continue
        addStyle(style, r.first, r.last + 1)
    }
}

@Composable
private fun ResultRow(hit: SearchHit, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val s = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val item = hit.item
    val accent = remember(scheme.primary) { SpanStyle(color = scheme.primary, fontWeight = FontWeight.Bold) }
    val name = remember(hit, accent) { highlight(item.name, hit.nameRanges, accent) }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        FileIcon(item = item, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isHidden) scheme.onSurface.copy(alpha = 0.65f) else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Folder, null, tint = scheme.outline, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    hit.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
            val meta = remember(item, s.sizeUnits, s.dateFormat) {
                val date = Format.date(item.lastModified, s.dateFormat)
                if (item.isDirectory) "Папка  •  $date" else "${Format.size(item.size, s.sizeUnits)}  •  $date"
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            hit.content?.let { ContentSnippet(it, accent) }
        }
        IconButton(
            onClick = { copyResultPath(context, item.path) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Rounded.ContentCopy, "Копировать путь", tint = scheme.outline, modifier = Modifier.size(18.dp))
        }
    }
}

private fun copyResultPath(context: Context, path: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("path", path))
    Toast.makeText(context, "Путь скопирован", Toast.LENGTH_SHORT).show()
}

@Composable
private fun ContentSnippet(match: ContentMatch, accent: SpanStyle) {
    val scheme = MaterialTheme.colorScheme
    val text = remember(match, accent) { highlight(match.snippet, match.ranges, accent) }
    Column(
        Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                "${match.line}",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = scheme.outline,
                modifier = Modifier.padding(top = 2.dp, end = 8.dp),
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (match.count > 1) {
            Text(
                "+ ещё ${match.count - 1} ${Format.plural(match.count - 1, "совпадение", "совпадения", "совпадений")}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ Действия

@Composable
private fun ResultSheet(hit: SearchHit, onOpen: () -> Unit, onReveal: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val item = hit.item
    val scheme = MaterialTheme.colorScheme
    AppBottomSheet(onDismiss) {
        Row(
            Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileIcon(item = item, size = 52.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    item.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        val actions = buildList {
            add(SheetAction("Показать в папке", Icons.Rounded.FolderOpen, supporting = item.path.substringBeforeLast('/')) { onReveal() })
            add(SheetAction(if (item.isDirectory) "Открыть папку" else "Открыть", Icons.AutoMirrored.Rounded.OpenInNew) { onOpen() })
            if (!item.isDirectory) add(SheetAction("Поделиться", Icons.Rounded.Share) { FileOpener.share(context, listOf(File(item.path))) })
            add(SheetAction("Копировать путь", Icons.Rounded.ContentCopy) { copyResultPath(context, item.path) })
        }
        ActionGroup(actions, onDismiss)
    }
}
