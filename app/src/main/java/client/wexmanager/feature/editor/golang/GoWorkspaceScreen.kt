package client.wexmanager.feature.editor.golang

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.appContainer
import client.wexmanager.feature.editor.EditorScreen
import client.wexmanager.ui.navigation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun GoWorkspaceScreen(onBack: () -> Unit, onNavigate: (Route) -> Unit) {
    val context = LocalContext.current
    val vm: GoWorkspaceViewModel = viewModel { GoWorkspaceViewModel(context.applicationContext as Application) }
    val state by vm.state.collectAsStateWithLifecycle()
    val dirty by context.appContainer.dirtyEditors.collectAsStateWithLifecycle()
    var createProject by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }
    var createEntry by remember { mutableStateOf<Boolean?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val motion = MaterialTheme.motionScheme
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (state.root != null) vm.refresh() }
    LaunchedEffect(state.error) {
        state.error?.let { message -> snackbar.showSnackbar(message, withDismissAction = true); vm.consumeError(message) }
    }
    Scaffold(
        // The embedded editor owns status/navigation/IME insets. Do not apply them twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.root == null) AnimatedVisibility(!imeVisible) {
                NavigationBar {
                    NavigationBarItem(false, onBack, icon = { Icon(Icons.Rounded.Folder, null) }, label = { Text("Файлы") })
                    NavigationBarItem(true, {}, icon = { Icon(Icons.Rounded.Code, null) }, label = { Text("GO") })
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val latest = state
            AnimatedContent(targetState = state.root, label = "go-workspace", modifier = Modifier.fillMaxSize(), transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 24 })
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.defaultSpatialSpec()) { -it / 24 })
            }) { root ->
                val previous = remember(root) { arrayOf(latest) }
                if (latest.root == root) previous[0] = latest
                val page = previous[0]
                if (root == null) {
                    var query by rememberSaveable { mutableStateOf("") }
                    Column(Modifier.fillMaxSize()) {
                        TopAppBar(title = { Text("Go IDE", style = MaterialTheme.typography.headlineMedium) }, actions = {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.padding(end = 16.dp)) {
                                Icon(Icons.Rounded.Code, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        })
                        val projects = page.projects.filter { it.contains(query, ignoreCase = true) }
                        LazyColumn(Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth().align(Alignment.CenterHorizontally), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            item {
                                GoTextField(query, onValueChange = { query = it }, placeholder = { Text("Найти проект") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, null) }, shape = MaterialTheme.shapes.extraLarge,
                                    trailingIcon = { if (query.isNotEmpty()) GoIconButton(onClick = { query = "" }, tooltip = "Очистить поиск") { Icon(Icons.Rounded.Close, "Очистить поиск") } },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                                GoProjectActions(enabled = !page.busy, onCreate = { createProject = true }, onOpen = { openFolder = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                            }
                            if (projects.isNotEmpty()) item {
                                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Недавние проекты", style = MaterialTheme.typography.titleSmall)
                                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { Text(projects.size.toString()) }
                                }
                            }
                            itemsIndexed(projects, key = { _, path -> path }) { index, path ->
                                ListItem(onClick = { vm.openProject(path) }, enabled = !page.busy,
                                    shapes = ListItemDefaults.segmentedShapes(index, projects.size),
                                    modifier = Modifier.fillMaxWidth().animateItem(fadeInSpec = motion.fastEffectsSpec(), fadeOutSpec = motion.fastEffectsSpec(), placementSpec = motion.defaultSpatialSpec()),
                                    leadingContent = {
                                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
                                            Icon(Icons.Rounded.FolderOpen, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                        }
                                    }, supportingContent = {
                                        Column {
                                            Text(path, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                                            if (dirty.any { File(it).toPath().startsWith(File(path).toPath()) }) Text("Есть несохранённые правки", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }, trailingContent = {
                                        GoIconButton(onClick = { vm.forgetProject(path) }, enabled = !page.busy, tooltip = "Убрать из недавних") { Icon(Icons.Rounded.Close, "Убрать из списка; файлы сохранятся") }
                                    }) { Text(File(path).name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            if (projects.isEmpty()) item {
                                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                        Icon(Icons.Rounded.Code, null, Modifier.padding(20.dp).size(40.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                    Text(if (query.isBlank()) "Начните с папки проекта" else "Проекты не найдены", style = MaterialTheme.typography.titleMedium)
                                    Text(if (query.isBlank()) "Создайте Go-проект или подключите существующий. Ваши файлы останутся в общей папке." else "Попробуйте другое название или путь.", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                } else key(root) {
                    val tabs = remember(root) { context.appContainer.goWorkspaceTabs(root) }
                    val tabList by tabs.tabs.collectAsStateWithLifecycle()
                    val activeId by tabs.activeId.collectAsStateWithLifecycle()
                    val active = tabList.firstOrNull { it.id == activeId }
                    LaunchedEffect(root, page.openVersion) { if (vm.state.value.root == root) page.selectedFile?.let { tabs.open(Route.Editor(it)) } }
                    LaunchedEffect(active?.route?.path) { if (vm.state.value.root == root) active?.route?.path?.let(vm::trackActiveFile) }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wide = maxWidth >= 840.dp
                        fun showTree() { if (wide) vm.setTreeVisible(!page.treeVisible) else scope.launch { drawer.open() } }
                        @Composable fun tree() {
                            GoProjectTree(page, active?.route?.path, vm, onProjects = vm::projectsHome,
                                onQuickOpen = { quickOpen = true }, onCreate = { createEntry = it }, onOpenFile = { path ->
                                    vm.selectFile(path) { error -> if (error == null) scope.launch { drawer.close() } }
                                })
                        }
                        @Composable fun editor() {
                            if (active != null) EditorScreen(active.route, onBack = onBack,
                                onOpenFile = { tabs.open(it) }, onShowDiff = { l, r -> onNavigate(Route.Diff(l, r)) },
                                onPreview = { p, b -> onNavigate(Route.Preview(p, b)) }, tabsOverride = tabs, sessionKey = "go:$root", workspaceRoot = root,
                                onQuickOpen = { quickOpen = true }, navigationContent = { GoIconButton(onClick = ::showTree) { Icon(Icons.Rounded.Menu, "Дерево и проекты Go") } })
                            else Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(48.dp))
                                Text(File(root).name, style = MaterialTheme.typography.headlineSmall)
                                Text("Откройте исходник, чтобы начать работу", Modifier.padding(vertical = 12.dp))
                                Button(onClick = { quickOpen = true }) { Text("Найти файл · Ctrl+P") }
                                TextButton(onClick = { if (wide) vm.setTreeVisible(true) else scope.launch { drawer.open() } }) { Text("Дерево файлов") }
                                TextButton(onClick = vm::projectsHome) { Text("Все проекты") }
                            }
                        }
                        if (wide) Row(Modifier.fillMaxSize()) {
                            AnimatedVisibility(page.treeVisible, enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                                exit = shrinkHorizontally(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec())) {
                                Row(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
                                    Box(Modifier.width(280.dp)) { tree() }; VerticalDivider()
                                }
                            }
                            Box(Modifier.weight(1f)) { editor() }
                        } else ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = drawer.isOpen,
                            drawerContent = { ModalDrawerSheet(Modifier.widthIn(max = 360.dp).fillMaxWidth(0.9f)) { tree() } }) { editor() }
                    }
                }
            }
            AnimatedVisibility(state.busy, modifier = Modifier.align(Alignment.TopCenter)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.statusBarsPadding().padding(top = 8.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(32.dp)); Text("Работа с проектом…", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
    if (createProject) CreateGoProjectDialog(vm.projectsDirectory.path, onDismiss = { createProject = false }) { name, module, done ->
        vm.createProject(name, module) { error -> done(error); if (error == null) createProject = false }
    }
    if (openFolder) GoFolderPicker(vm.storage, onDismiss = { openFolder = false }) { path, done ->
        vm.openProject(path) { error -> done(error); if (error == null) openFolder = false }
    }
    createEntry?.let { folder ->
        CreateGoEntryDialog(folder, state.selectedDirectory.orEmpty(), onDismiss = { createEntry = null }) { name, done ->
            vm.createEntry(name, folder) { error -> done(error); if (error == null) createEntry = null }
        }
    }
    if (quickOpen && state.root != null) {
        val root = state.root!!
        val tabs = context.appContainer.goWorkspaceTabs(root)
        GoQuickOpenDialog(File(root), tabs.tabs.value.map { it.route.path }, onDismiss = { quickOpen = false }) { path, done ->
            vm.selectFile(path) { error -> done(error); if (error == null) { quickOpen = false; scope.launch { drawer.close() } } }
        }
    }
}

@Composable
private fun GoProjectTree(state: GoWorkspaceState, activeFile: String?, vm: GoWorkspaceViewModel, onProjects: () -> Unit,
    onQuickOpen: () -> Unit, onCreate: (Boolean) -> Unit, onOpenFile: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    var menu by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    var handledReveal by remember(state.root) { mutableLongStateOf(0) }
    LaunchedEffect(state.revealVersion, state.nodes) {
        if (state.revealVersion > handledReveal) {
            val index = state.nodes.indexOfFirst { it.file.path == activeFile }
            if (index >= 0) { list.animateScrollToItem(index); handledReveal = state.revealVersion }
        }
    }
    Column(Modifier.fillMaxSize().background(scheme.surfaceContainerLow).imePadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GoIconButton(onClick = onProjects) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Все проекты") }
            Text(File(state.root.orEmpty()).name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            GoIconButton(onClick = { activeFile?.let(vm::reveal) }, enabled = activeFile != null) { Icon(Icons.Rounded.MyLocation, "Показать текущий файл в дереве") }
            Box {
                GoIconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Действия проекта") }
                DropdownMenuPopup(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                        fun action(block: () -> Unit) { menu = false; block() }
                        DropdownMenuItem(text = { Text("Новый файл") }, onClick = { action { onCreate(false) } }, shape = MenuDefaults.leadingItemShape)
                        DropdownMenuItem(text = { Text("Новая папка") }, onClick = { action { onCreate(true) } }, shape = MenuDefaults.middleItemShape)
                        DropdownMenuItem(text = { Text("Найти файл · Ctrl+P") }, onClick = { action(onQuickOpen) }, shape = MenuDefaults.middleItemShape)
                        DropdownMenuItem(text = { Text("Свернуть все папки") }, onClick = { action(vm::collapseAll) }, shape = MenuDefaults.middleItemShape)
                        DropdownMenuItem(text = { Text("Обновить") }, onClick = { action(vm::refresh) }, shape = MenuDefaults.trailingItemShape)
                    }
                }
            }
        }
        GoTextField(state.filter, onValueChange = vm::setFilter, singleLine = true, placeholder = { Text("Поиск по всему проекту") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = { if (state.filter.isNotEmpty()) GoIconButton(onClick = { vm.setFilter("") }) { Icon(Icons.Rounded.Close, "Очистить поиск") } },
            shape = MaterialTheme.shapes.extraLarge, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
        TextButton(onClick = { state.root?.let { vm.reveal(it) } }, modifier = Modifier.fillMaxWidth()) {
            Text("Проект / " + File(state.selectedDirectory.orEmpty()).relativeToOrNull(File(state.root.orEmpty()))?.invariantSeparatorsPath.orEmpty(),
                maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.labelSmall)
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(state.nodes, key = { _, node -> node.file.absolutePath }) { index, node ->
                val angle by animateFloatAsState(if (node.expanded) 90f else 0f, motion.fastSpatialSpec(), label = "go-tree-chevron")
                ListItem(selected = node.file.path == activeFile,
                    onClick = { if (node.directory) vm.toggleFolder(node.file.path) else onOpenFile(node.file.path) },
                    shapes = ListItemDefaults.segmentedShapes(index, state.nodes.size),
                    modifier = Modifier.fillMaxWidth().animateItem(fadeInSpec = motion.fastEffectsSpec(), fadeOutSpec = motion.fastEffectsSpec(), placementSpec = motion.defaultSpatialSpec()),
                    contentPadding = PaddingValues(start = (8 + node.depth.coerceAtMost(8) * 12).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (node.directory) Icon(Icons.Rounded.ChevronRight, if (node.expanded) "Свернуть" else "Раскрыть", Modifier.size(18.dp).rotate(angle)) else Spacer(Modifier.width(18.dp))
                            Icon(if (node.directory) if (node.expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder else if (node.file.extension == "go") Icons.Rounded.Code else Icons.AutoMirrored.Rounded.InsertDriveFile,
                                null, Modifier.size(20.dp), tint = if (node.directory || node.file.extension == "go") scheme.primary else scheme.onSurfaceVariant)
                        }
                    }, supportingContent = if (state.filter.isNotBlank()) ({
                        Text(node.file.relativeTo(File(state.root!!)).invariantSeparatorsPath, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    }) else null,
                ) { Text(node.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
            }
            if (state.nodes.isEmpty()) item { Text(if (state.filter.isBlank()) "Папка пока пуста" else "Файлы не найдены", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
            if (state.truncated) item { Text("Результаты ограничены. Уточните запрос или сверните папки.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onCreate(false) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Файл")
            }
            FilledTonalButton(onClick = { onCreate(true) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Rounded.CreateNewFolder, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Папка")
            }
        }
    }
}

@Composable
private fun GoProjectActions(enabled: Boolean, onCreate: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val stacked = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.2f
        @Composable fun create(modifier: Modifier) {
            Button(onClick = onCreate, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = modifier.heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text("Новый проект", style = MaterialTheme.typography.titleSmall)
            }
        }
        @Composable fun open(modifier: Modifier) {
            FilledTonalButton(onClick = onOpen, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = modifier.heightIn(min = 56.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(22.dp)); Spacer(Modifier.width(8.dp)); Text("Открыть папку", style = MaterialTheme.typography.titleSmall)
            }
        }
        if (stacked) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { create(Modifier.fillMaxWidth()); open(Modifier.fillMaxWidth()) }
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { create(Modifier.weight(1f)); open(Modifier.weight(1f)) }
    }
}

@Composable
private fun CreateGoProjectDialog(base: String, onDismiss: () -> Unit, onCreate: (String, String, (String?) -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var module by rememberSaveable { mutableStateOf("example.com/app") }
    var moduleEdited by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun submit() {
        if (busy) return
        runCatching { GoWorkspaceFiles.validName(name); GoWorkspaceFiles.validModule(module) }.onSuccess {
            busy = true; onCreate(name, module) { failure -> error = failure; busy = false }
        }.onFailure { error = it.message }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, icon = { Icon(Icons.Rounded.Code, null) }, title = { Text("Новый Go-проект") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GoTextField(name, onValueChange = {
                name = it; error = null
                if (!moduleEdited) module = "example.com/" + it.lowercase().replace(Regex("[^a-z0-9._-]+"), "-").trim('-').ifEmpty { "app" }
            }, enabled = !busy, singleLine = true, label = { Text("Название") }, placeholder = { Text("hello-go") }, modifier = Modifier.fillMaxWidth())
            GoTextField(module, onValueChange = { module = it; moduleEdited = true; error = null }, enabled = !busy, singleLine = true,
                label = { Text("Go-модуль") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
            Text("$base/${name.ifBlank { "…" }}\nСоздадим go.mod и готовый main.go.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(shapes = ButtonDefaults.shapes(), onClick = ::submit, enabled = !busy && name.isNotBlank()) { Text(if (busy) "Создание…" else "Создать") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } })
}

@Composable
private fun CreateGoEntryDialog(folder: Boolean, parent: String, onDismiss: () -> Unit, onCreate: (String, (String?) -> Unit) -> Unit) {
    var name by rememberSaveable { mutableStateOf(if (folder) "" else "new.go") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun submit() { if (!busy) runCatching { GoWorkspaceFiles.validName(name) }.onSuccess { busy = true; onCreate(name) { error = it; busy = false } }.onFailure { error = it.message } }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, icon = { Icon(if (folder) Icons.Rounded.CreateNewFolder else Icons.Rounded.NoteAdd, null) }, title = { Text(if (folder) "Новая папка" else "Новый файл") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(parent, style = MaterialTheme.typography.bodySmall)
            GoTextField(name, onValueChange = { name = it; error = null }, enabled = !busy, singleLine = true, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(shapes = ButtonDefaults.shapes(), onClick = ::submit, enabled = !busy && name.isNotBlank()) { Text(if (busy) "Создание…" else "Создать") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } })
}

@Composable
private fun GoFolderPicker(storage: File, onDismiss: () -> Unit, onOpen: (String, (String?) -> Unit) -> Unit) {
    var path by rememberSaveable { mutableStateOf(storage.path) }
    var typed by rememberSaveable { mutableStateOf(path) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val folders by produceState<List<File>?>(null, path) {
        value = null
        value = withContext(Dispatchers.IO) { File(path).listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }.orEmpty() }
    }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, icon = { Icon(Icons.Rounded.FolderOpen, null) }, title = { Text("Папка проекта") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            GoTextField(typed, onValueChange = { typed = it; error = null }, enabled = !busy, label = { Text("Путь") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { path = typed.trim() }))
            Row {
                TextButton(onClick = { path = typed.trim() }, enabled = !busy) { Text("Перейти") }
                TextButton(onClick = { File(path).parentFile?.takeIf { GoWorkspaceFiles.contained(storage, it) }?.let { path = it.path; typed = path } },
                    enabled = !busy && File(path).path != storage.path) { Text("Выше") }
            }
            val visibleFolders = folders.orEmpty()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(visibleFolders, key = { _, folder -> folder.path }) { index, folder ->
                    ListItem(onClick = { path = folder.path; typed = path }, enabled = !busy,
                        shapes = ListItemDefaults.segmentedShapes(index, visibleFolders.size),
                        leadingContent = { Icon(Icons.Rounded.Folder, null) }) {
                        Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (folders?.isEmpty() == true) item { Text("Вложенных папок нет или нужен доступ к хранилищу", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.bodySmall) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(shapes = ButtonDefaults.shapes(), onClick = { busy = true; onOpen(typed.trim()) { error = it; busy = false } }, enabled = !busy) { Text(if (busy) "Открытие…" else "Открыть папку") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") } })
}

@Composable
private fun GoQuickOpenDialog(root: File, openFiles: List<String>, onDismiss: () -> Unit, onOpen: (String, (String?) -> Unit) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val result by produceState<GoTreeSnapshot?>(null, root.path, query) {
        value = null; delay(150)
        val found = withContext(Dispatchers.IO) { runCatching { if (query.isBlank()) GoTreeSnapshot(openFiles.map { GoTreeNode(File(it), 0, false, false) }, false) else GoWorkspaceFiles.search(root, query) } }
        value = found.getOrDefault(GoTreeSnapshot(emptyList(), false))
        found.exceptionOrNull()?.let { error = it.message ?: "Ошибка поиска" }
    }
    fun open(path: String) { if (!busy) { busy = true; onOpen(path) { error = it; busy = false } } }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Открыть файл") }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            GoTextField(query, onValueChange = { query = it; error = null }, singleLine = true, placeholder = { Text("Имя или часть пути") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { result?.nodes?.firstOrNull()?.file?.path?.let(::open) }))
            Text(if (query.isBlank()) "Открытые файлы · поиск во всех папках проекта" else "${result?.nodes?.size ?: "…"} совпадений", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.labelSmall)
            val nodes = result?.nodes.orEmpty()
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(nodes, key = { _, node -> node.file.path }) { index, node ->
                    ListItem(onClick = { open(node.file.path) }, enabled = !busy, shapes = ListItemDefaults.segmentedShapes(index, nodes.size),
                        leadingContent = { Icon(if (node.file.extension == "go") Icons.Rounded.Code else Icons.AutoMirrored.Rounded.InsertDriveFile, null) },
                        supportingContent = { Text(node.file.relativeToOrNull(root)?.invariantSeparatorsPath.orEmpty(), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) }) {
                        Text(node.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Закрыть") } })
}
