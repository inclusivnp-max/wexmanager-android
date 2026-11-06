package client.wexmanager.feature.dex

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.ManageSearch
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.dex.DexClassInfo
import client.wexmanager.core.dex.DexClassKind
import client.wexmanager.feature.browser.copyToSystemClipboard
import androidx.compose.ui.focus.focusRequester
import client.wexmanager.feature.apk.ShapeMessage
import client.wexmanager.feature.apk.ShapedIcon
import client.wexmanager.ui.navigation.Route
import kotlinx.coroutines.launch
import java.io.File

/** Узел обзора: подпакеты (имя → число классов в глубину) и классы текущего пакета. */
private class PackageLevel(val packages: List<Pair<String, Int>>, val classes: List<DexClassInfo>)

private fun levelOf(all: List<DexClassInfo>, pkg: String): PackageLevel {
    val prefix = if (pkg.isEmpty()) "" else "$pkg/"
    val sub = LinkedHashMap<String, Int>()
    val here = ArrayList<DexClassInfo>()
    for (c in all) {
        val p = c.packagePath
        if (p == pkg) here += c
        else if (prefix.isEmpty() || p.startsWith(prefix)) {
            val rest = p.removePrefix(prefix)
            val name = rest.substringBefore('/')
            sub[name] = (sub[name] ?: 0) + 1
        }
    }
    return PackageLevel(sub.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value }, here.sortedBy { it.simpleName.lowercase() })
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DexEditorScreen(route: Route.DexEditor, onBack: () -> Unit, onOpenEditor: (Route.Editor) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: DexEditorViewModel = viewModel(key = "dex:${route.path}:${route.entries}") { DexEditorViewModel(app, route.path, route.entries) }
    val state by vm.state.collectAsStateWithLifecycle()
    val hits by vm.hits.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var pkg by rememberSaveable { mutableStateOf("") }
    var classFilter by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var inCode by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<DexClassInfo?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.syncEdited() }
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is DexEvent.Open -> onOpenEditor(ev.route)
                is DexEvent.Message -> scope.launch {
                    val r = snackbar.showSnackbar(
                        ev.text, actionLabel = ev.action, withDismissAction = ev.action != null,
                        duration = if (ev.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
                    )
                    if (r == SnackbarResult.ActionPerformed) ev.onAction?.invoke()
                }
            }
        }
    }

    val leave: () -> Unit = { if (state.dirty) dialog = "unsaved" else onBack() }
    BackHandler(searchOpen) { searchOpen = false; query = ""; vm.clearCodeSearch() }
    BackHandler(!searchOpen && pkg.isNotEmpty()) { pkg = pkg.substringBeforeLast('/', "") }
    BackHandler(!searchOpen && pkg.isEmpty() && state.dirty) { dialog = "unsaved" }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = leave, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                title = {
                    if (searchOpen) {
                        SearchField(
                            query = query,
                            placeholder = if (inCode) "Строка, метод, поле, инструкция…" else "Имя класса",
                            onChange = { query = it; if (!inCode) vm.clearCodeSearch() },
                            onSubmit = { if (inCode) vm.searchCode(query, regex = false, ignoreCase = true) },
                        )
                    } else {
                        Column {
                            Text("DEX-редактор", maxLines = 1)
                            Text(
                                buildString {
                                    append(state.title)
                                    if (state.classes.isNotEmpty()) append(" · ").append(state.classes.size).append(" классов")
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.MiddleEllipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (!searchOpen) {
                        IconButton(onClick = { searchOpen = true }, enabled = !state.loading, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Search, "Поиск")
                        }
                    }
                    AnimatedVisibility(state.dirty || state.saving) {
                        if (state.saving) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(28.dp)) }
                        else FilledTonalIconButton(onClick = { vm.save() }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Save, "Сохранить") }
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = !state.loading, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.MoreVert, "Ещё") }
                        DropdownMenuPopup(expanded = menu, onDismissRequest = { menu = false }) {
                            val items = buildList<Triple<String, androidx.compose.ui.graphics.vector.ImageVector, () -> Unit>> {
                                add(Triple("Новый класс", Icons.Rounded.Add) { dialog = "new" })
                                if (state.isArchive) add(Triple("Сохранить и подписать", Icons.Rounded.Key) { vm.save { vm.sign() } })
                                add(Triple("Перечитать", Icons.Rounded.Refresh) { if (state.dirty) dialog = "reload" else vm.load() })
                            }
                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                                items.forEachIndexed { i, (title, icon, action) ->
                                    DropdownMenuItem(
                                        text = { Text(title) },
                                        leadingIcon = { Icon(icon, null) },
                                        onClick = { menu = false; action() },
                                        shape = when {
                                            items.size == 1 -> MenuDefaults.standaloneItemShape
                                            i == 0 -> MenuDefaults.leadingItemShape
                                            i == items.lastIndex -> MenuDefaults.trailingItemShape
                                            else -> MenuDefaults.middleItemShape
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = scheme.surface,
    ) { padding ->
        val motion = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = when {
                state.loading -> 0
                state.error != null -> 1
                else -> 2
            },
            transitionSpec = { fadeIn(motion.defaultEffectsSpec()).togetherWith(fadeOut(motion.fastEffectsSpec())) },
            label = "dexState",
            modifier = Modifier.padding(padding),
        ) { phase ->
            when (phase) {
                0 -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    LoadingIndicator(Modifier.size(72.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Дизассемблирование…", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(state.progress, style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                }
                1 -> ShapeMessage(Icons.Rounded.Error, "Не удалось открыть DEX", state.error, error = true) {
                    FilledTonalButton(onClick = vm::load, shapes = ButtonDefaults.shapes()) { Text("Повторить") }
                }
                else -> if (searchOpen) {
                    SearchResults(
                        state = state,
                        query = query,
                        inCode = inCode,
                        onInCode = { inCode = it; if (it && query.isNotBlank()) vm.searchCode(query, false, true) else vm.clearCodeSearch() },
                        hits = hits,
                        searching = searching,
                        onOpen = { d, line -> vm.openClass(d, line) },
                        onLongClass = { menuFor = it },
                        onReplaceAll = { repl -> vm.replaceInCode(query, repl, regex = false, ignoreCase = true) },
                    )
                } else {
                    Browser(
                        state = state,
                        pkg = pkg,
                        classFilter = classFilter,
                        onClassFilter = { classFilter = it },
                        onPkg = { pkg = it },
                        onOpen = { vm.openClass(it.descriptor) },
                        onLong = { menuFor = it },
                    )
                }
            }
        }
    }

    menuFor?.let { cls ->
        ClassActionsDialog(
            cls = cls,
            onDismiss = { menuFor = null },
            onCopy = { text -> copyToSystemClipboard(context, text); menuFor = null },
            onDelete = { vm.deleteClass(cls.descriptor); menuFor = null },
            onJava = { vm.openJava(cls.descriptor); menuFor = null },
        )
    }
    state.decompiling?.let { d ->
        AlertDialog(
            onDismissRequest = {},
            icon = { LoadingIndicator(Modifier.size(48.dp)) },
            title = { Text("Декомпиляция в Java") },
            text = { Text(d.removePrefix("L").removeSuffix(";").replace('/', '.'), fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.MiddleEllipsis) },
            confirmButton = {},
        )
    }
    when (dialog) {
        "unsaved" -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Rounded.Save, null) },
            title = { Text("Сохранить изменения?") },
            text = { Text("Правленые классы ещё не собраны в DEX.") },
            confirmButton = { Button(onClick = { dialog = null; vm.save(onBack) }) { Text("Сохранить") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { dialog = null; onBack() }) { Text("Не сохранять") }
                    TextButton(onClick = { dialog = null }) { Text("Отмена") }
                }
            },
        )
        "reload" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Перечитать DEX?") },
            text = { Text("Несохранённые правки будут потеряны.") },
            confirmButton = { Button(onClick = { dialog = null; vm.load() }) { Text("Перечитать") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Отмена") } },
        )
        "new" -> NewClassDialog(
            pkg = pkg,
            dexNames = state.dexNames,
            onDismiss = { dialog = null },
            onCreate = { name, index -> dialog = null; vm.createClass(name, index) },
        )
    }
}

@Composable
private fun Browser(
    state: DexEditorState,
    pkg: String,
    classFilter: String,
    onClassFilter: (String) -> Unit,
    onPkg: (String) -> Unit,
    onOpen: (DexClassInfo) -> Unit,
    onLong: (DexClassInfo) -> Unit,
) {
    val needle = classFilter.trim()
    val shown = remember(state.classes, pkg, needle) {
        val level = levelOf(state.classes, pkg)
        if (needle.isEmpty()) level.packages to level.classes
        else {
            val prefix = if (pkg.isEmpty()) "" else "$pkg/"
            emptyList<Pair<String, Int>>() to state.classes.filter { c ->
                val path = c.packagePath
                val inScope = pkg.isEmpty() || path == pkg || path.startsWith(prefix)
                inScope && (c.simpleName.contains(needle, ignoreCase = true) || c.javaName.contains(needle, ignoreCase = true))
            }.sortedBy { it.javaName.lowercase() }
        }
    }
    val packages = shown.first
    val classes = shown.second
    Column(Modifier.fillMaxSize()) {
        Breadcrumbs(pkg, onPkg)
        ClassFilterField(classFilter, onClassFilter)
        if (needle.isNotEmpty() && classes.isEmpty()) {
            Text(
                "Нет классов",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        val total = packages.size + classes.size
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(packages, key = { _, p -> "p:" + p.first }) { i, (name, count) ->
                SegmentedListItem(
                    onClick = { onPkg(if (pkg.isEmpty()) name else "$pkg/$name") },
                    shapes = ListItemDefaults.segmentedShapes(i, total),
                    leadingContent = {
                        ShapedIcon(Icons.Rounded.Folder, MaterialShapes.Cookie6Sided, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    },
                    supportingContent = { Text("$count ${plural(count)}") },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                    modifier = Modifier.animateItem(),
                ) { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            itemsIndexed(classes, key = { _, c -> c.descriptor }) { i, c ->
                val subtitle = if (needle.isEmpty()) null else c.packagePath.replace('/', '.').ifEmpty { "(корневой пакет)" }
                ClassRow(
                    c,
                    c.descriptor in state.modified,
                    ListItemDefaults.segmentedShapes(packages.size + i, total),
                    onOpen,
                    onLong,
                    Modifier.animateItem(),
                    subtitle = subtitle,
                )
            }
        }
    }
}

@Composable
private fun ClassFilterField(value: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Фильтр классов") },
        leadingIcon = { Icon(Icons.Rounded.FilterList, null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, "Очистить")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHighest,
            unfocusedContainerColor = scheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ClassRow(
    c: DexClassInfo,
    modified: Boolean,
    shapes: androidx.compose.material3.ListItemShapes,
    onOpen: (DexClassInfo) -> Unit,
    onLong: (DexClassInfo) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = { onOpen(c) },
        onLongClick = { onLong(c) },
        shapes = shapes,
        leadingContent = { KindBadge(c.kind) },
        supportingContent = {
            Text(
                subtitle ?: "${c.methods} ${methods(c.methods)} · ${c.fields} ${fields(c.fields)}",
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        },
        trailingContent = if (modified) {
            { Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary)) }
        } else null,
        modifier = modifier,
    ) { Text(c.simpleName, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

@Composable
private fun KindBadge(kind: DexClassKind) {
    val scheme = MaterialTheme.colorScheme
    val (letter, container, content, shape) = when (kind) {
        DexClassKind.Class -> Quad("C", scheme.primaryContainer, scheme.onPrimaryContainer, MaterialShapes.Cookie9Sided)
        DexClassKind.Abstract -> Quad("A", scheme.primaryContainer, scheme.onPrimaryContainer, MaterialShapes.Cookie7Sided)
        DexClassKind.Interface -> Quad("I", scheme.tertiaryContainer, scheme.onTertiaryContainer, MaterialShapes.Pill)
        DexClassKind.Enum -> Quad("E", scheme.secondaryContainer, scheme.onSecondaryContainer, MaterialShapes.Square)
        DexClassKind.Annotation -> Quad("@", scheme.errorContainer, scheme.onErrorContainer, MaterialShapes.Clover4Leaf)
    }
    val polygonShape = remember(shape) { client.wexmanager.ui.components.PolygonShape(shape) }
    Box(Modifier.size(40.dp).clip(polygonShape).background(container), contentAlignment = Alignment.Center) {
        Text(letter, style = MaterialTheme.typography.titleMedium, color = content, fontFamily = FontFamily.Monospace)
    }
}

private data class Quad(val a: String, val b: Color, val c: Color, val d: androidx.graphics.shapes.RoundedPolygon)

@Composable
private fun Breadcrumbs(pkg: String, onPkg: (String) -> Unit) {
    val parts = if (pkg.isEmpty()) emptyList() else pkg.split('/')
    val scroll = rememberScrollState()
    LaunchedEffect(pkg) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(selected = parts.isEmpty(), onClick = { onPkg("") }, label = { Text("/") })
        parts.forEachIndexed { i, part ->
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
            FilterChip(
                selected = i == parts.lastIndex,
                onClick = { onPkg(parts.take(i + 1).joinToString("/")) },
                label = { Text(part, fontFamily = FontFamily.Monospace) },
            )
        }
    }
}

@Composable
private fun SearchResults(
    state: DexEditorState,
    query: String,
    inCode: Boolean,
    onInCode: (Boolean) -> Unit,
    hits: List<CodeHit>?,
    searching: Boolean,
    onOpen: (String, Int?) -> Unit,
    onLongClass: (DexClassInfo) -> Unit,
    onReplaceAll: (String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    var replacement by rememberSaveable { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !inCode, onClick = { onInCode(false) }, label = { Text("Классы") }, leadingIcon = { Icon(Icons.Rounded.DataObject, null, Modifier.size(18.dp)) })
            FilterChip(selected = inCode, onClick = { onInCode(true) }, label = { Text("В коде") }, leadingIcon = { Icon(Icons.Rounded.ManageSearch, null, Modifier.size(18.dp)) })
            if (searching) LoadingIndicator(Modifier.size(28.dp))
        }
        if (!inCode) {
            val found = remember(state.classes, query) {
                if (query.isBlank()) emptyList()
                else state.classes.filter { it.javaName.contains(query, ignoreCase = true) }.take(1000)
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(found, key = { _, c -> c.descriptor }) { i, c ->
                    ClassRow(
                        c, c.descriptor in state.modified, ListItemDefaults.segmentedShapes(i, found.size),
                        onOpen = { onOpen(it.descriptor, null) }, onLong = onLongClass, subtitle = c.packagePath.replace('/', '.').ifEmpty { "(корневой пакет)" },
                    )
                }
            }
        } else {
            val list = hits.orEmpty()
            if (list.isNotEmpty() && !searching) {
                Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replacement, onValueChange = { replacement = it }, singleLine = true,
                        label = { Text("Заменить на") },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = { confirmReplace = true }, shapes = ButtonDefaults.shapes()) { Text("Все") }
                }
            }
            if (confirmReplace) {
                val classes = list.map { it.descriptor }.distinct().size
                AlertDialog(
                    onDismissRequest = { confirmReplace = false },
                    title = { Text("Заменить во всех классах?") },
                    text = { Text("«$query» → «$replacement»: ${list.size}${if (list.size >= 2000) "+" else ""} совпадений в $classes+ классах. Каждый класс проверяется сборкой; изменения попадут в APK после «Сохранить».") },
                    confirmButton = { Button(onClick = { confirmReplace = false; onReplaceAll(replacement) }) { Text("Заменить") } },
                    dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Отмена") } },
                )
            }
            if (hits == null) {
                Text(
                    "Введите текст и нажмите «Поиск» на клавиатуре — ищется по smali всех классов: строки, имена методов и полей, инструкции.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(list, key = { i, h -> "${h.descriptor}:${h.line}:$i" }) { i, h ->
                    SegmentedListItem(
                        onClick = { onOpen(h.descriptor, h.line) },
                        shapes = ListItemDefaults.segmentedShapes(i, list.size),
                        supportingContent = { Text(h.text, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        trailingContent = { Text("${h.line + 1}", style = MaterialTheme.typography.labelMedium, color = scheme.outline) },
                    ) {
                        Text(
                            h.descriptor.removePrefix("L").removeSuffix(";").substringAfterLast('/'),
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, placeholder: String, onChange: (String) -> Unit, onSubmit: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Очистить") }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHighest,
            unfocusedContainerColor = scheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus),
    )
}

@Composable
private fun ClassActionsDialog(cls: DexClassInfo, onDismiss: () -> Unit, onCopy: (String) -> Unit, onDelete: () -> Unit, onJava: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Code, null) },
        title = { Text(cls.simpleName, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                SegmentedListItem(
                    onClick = onJava,
                    shapes = ListItemDefaults.segmentedShapes(0, 4),
                    leadingContent = { Icon(Icons.Rounded.Coffee, null) },
                    supportingContent = { Text("Декомпиляция jadx, только чтение") },
                ) { Text("Java-код") }
                val rows = listOf(
                    Triple("Копировать имя", cls.javaName, Icons.Rounded.ContentCopy),
                    Triple("Копировать дескриптор", cls.descriptor, Icons.Rounded.ContentCopy),
                )
                rows.forEachIndexed { i, (title, value, icon) ->
                    SegmentedListItem(
                        onClick = { onCopy(value) },
                        shapes = ListItemDefaults.segmentedShapes(i + 1, 4),
                        leadingContent = { Icon(icon, null) },
                        supportingContent = { Text(value, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                    ) { Text(title) }
                }
                SegmentedListItem(
                    onClick = { if (confirm) onDelete() else confirm = true },
                    shapes = ListItemDefaults.segmentedShapes(3, 4),
                    leadingContent = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    supportingContent = { if (confirm) Text("Нажмите ещё раз для подтверждения") },
                ) { Text("Удалить класс", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun NewClassDialog(pkg: String, dexNames: List<String>, onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf(if (pkg.isEmpty()) "" else pkg.replace('/', '.') + ".") }
    var dex by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Add, null) },
        title = { Text("Новый класс") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Полное имя") },
                    placeholder = { Text("com.example.MyClass") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (dexNames.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        dexNames.forEachIndexed { i, n -> FilterChip(selected = dex == i, onClick = { dex = i }, label = { Text(n) }) }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onCreate(name, dex) }, enabled = name.isNotBlank() && !name.endsWith(".")) { Text("Создать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun plural(n: Int): String = client.wexmanager.core.util.Format.plural(n, "класс", "класса", "классов")
private fun methods(n: Int): String = client.wexmanager.core.util.Format.plural(n, "метод", "метода", "методов")
private fun fields(n: Int): String = client.wexmanager.core.util.Format.plural(n, "поле", "поля", "полей")
