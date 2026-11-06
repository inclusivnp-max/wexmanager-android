package client.wexmanager.feature.apk

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import client.wexmanager.core.apk.ComponentPackage
import client.wexmanager.core.apk.ComponentRecord
import client.wexmanager.core.apk.PackageComponents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Список пакетов и переключатели их компонентов. */
@Composable
fun ComponentsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<ComponentPackage>>(emptyList()) }
    var packageLoading by remember { mutableStateOf(true) }
    var packageError by remember { mutableStateOf<String?>(null) }
    var packageToken by remember { mutableIntStateOf(0) }
    var packageQuery by rememberSaveable { mutableStateOf("") }
    var packageSearch by rememberSaveable { mutableStateOf(false) }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var openedLabel by rememberSaveable { mutableStateOf("") }
    var components by remember { mutableStateOf<List<ComponentRecord>>(emptyList()) }
    var componentLoading by remember { mutableStateOf(false) }
    var componentError by remember { mutableStateOf<String?>(null) }
    var componentToken by remember { mutableIntStateOf(0) }
    var componentQuery by rememberSaveable { mutableStateOf("") }
    var componentSearch by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(packageToken) {
        packageLoading = true
        packageError = null
        try {
            packages = PackageComponents.loadPackages(pm)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            packageError = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось прочитать пакеты"
        } finally {
            packageLoading = false
        }
    }
    LaunchedEffect(packages, opened) {
        val id = opened ?: return@LaunchedEffect
        packages.firstOrNull { it.packageName == id }?.let { openedLabel = it.label }
    }
    LaunchedEffect(opened, componentToken) {
        val pkg = opened ?: return@LaunchedEffect
        componentLoading = true
        componentError = null
        components = emptyList()
        pending = emptySet()
        try {
            components = PackageComponents.loadComponents(pm, pkg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            componentError = e.message?.takeIf { it.isNotBlank() } ?: "Не удалось прочитать компоненты"
        } finally {
            componentLoading = false
        }
    }

    val inComponents = opened != null
    val searchOpen = if (inComponents) componentSearch else packageSearch
    val query = if (inComponents) componentQuery else packageQuery
    val shownPackages = remember(packages, packageQuery) {
        val q = packageQuery.trim()
        if (q.isEmpty()) packages
        else packages.filter { it.label.contains(q, true) || it.packageName.contains(q, true) }
    }
    val shownComponents = remember(components, componentQuery) {
        val q = componentQuery.trim()
        val matched = if (q.isEmpty()) components
        else components.filter { it.className.contains(q, true) }
        matched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.className })
    }

    fun closeSearch() {
        if (inComponents) {
            componentSearch = false
            componentQuery = ""
        } else {
            packageSearch = false
            packageQuery = ""
        }
    }

    fun leaveComponents() {
        opened = null
        componentSearch = false
        componentQuery = ""
        componentError = null
        components = emptyList()
        pending = emptySet()
    }

    fun goBack() {
        when {
            searchOpen -> closeSearch()
            inComponents -> leaveComponents()
            else -> onBack()
        }
    }

    fun toggle(record: ComponentRecord) {
        val pkg = opened ?: return
        if (record.listKey in pending) return
        val want = !record.enabled
        pending = pending + record.listKey
        components = components.map { if (it.listKey == record.listKey) it.copy(enabled = want) else it }
        scope.launch {
            val err = try {
                PackageComponents.setEnabled(pm, pkg, record.className, want)
            } catch (e: CancellationException) {
                throw e
            }
            // Уход в другой пакет не должен откатывать уже новый список.
            if (opened != pkg) {
                if (err != null) snackbar.showSnackbar(err, withDismissAction = true, duration = SnackbarDuration.Long)
                return@launch
            }
            pending = pending - record.listKey
            if (err != null) {
                components = components.map { if (it.listKey == record.listKey) it.copy(enabled = !want) else it }
                snackbar.showSnackbar(err, withDismissAction = true, duration = SnackbarDuration.Long)
            }
        }
    }

    BackHandler(searchOpen || inComponents) { goBack() }

    val scheme = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) {
        if (searchOpen) runCatching { focus.requestFocus() }
    }
    val searchEnabled = if (inComponents) !componentLoading && componentError == null else !packageLoading && packageError == null
    val backDesc = when {
        searchOpen -> "Закрыть поиск"
        inComponents -> "К списку пакетов"
        else -> "Назад"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { goBack() }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, backDesc)
                    }
                },
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query,
                            onValueChange = { if (inComponents) componentQuery = it else packageQuery = it },
                            singleLine = true,
                            placeholder = { Text(if (inComponents) "Класс компонента" else "Имя или пакет") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = scheme.surfaceContainerHigh,
                                unfocusedContainerColor = scheme.surfaceContainerHigh,
                            ),
                            trailingIcon = if (query.isNotEmpty()) {
                                {
                                    IconButton(onClick = { if (inComponents) componentQuery = "" else packageQuery = "" }) {
                                        Icon(Icons.Rounded.Close, "Очистить")
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp).focusRequester(focus),
                        )
                    } else if (inComponents) {
                        Column {
                            Text(openedLabel.ifBlank { opened.orEmpty() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (openedLabel.isNotBlank() && openedLabel != opened) {
                                Text(
                                    opened.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    } else {
                        Text("Компоненты")
                    }
                },
                actions = {
                    if (!searchOpen) {
                        IconButton(
                            onClick = { if (inComponents) componentSearch = true else packageSearch = true },
                            enabled = searchEnabled,
                            shapes = IconButtonDefaults.shapes(),
                        ) { Icon(Icons.Rounded.Search, "Поиск") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = scheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !inComponents && packageLoading -> LoadingBox("Загрузка пакетов…")
                !inComponents && packageError != null -> MessageBox(
                    Icons.Rounded.Error,
                    "Не удалось прочитать пакеты",
                    packageError,
                ) { FilledTonalButton(onClick = { packageToken++ }, shapes = ButtonDefaults.shapes()) { Text("Повторить") } }
                !inComponents && shownPackages.isEmpty() -> if (packageQuery.isNotBlank()) {
                    MessageBox(Icons.Rounded.SearchOff, "Ничего не найдено", "По запросу «${packageQuery.trim()}» пакетов нет")
                } else {
                    MessageBox(Icons.Rounded.Apps, "Нет пакетов", "Установленных приложений не видно")
                }
                !inComponents -> PackageList(shownPackages) { pkg ->
                    componentQuery = ""
                    componentSearch = false
                    componentError = null
                    components = emptyList()
                    componentLoading = true
                    openedLabel = pkg.label
                    opened = pkg.packageName
                }
                componentLoading -> LoadingBox("Загрузка компонентов…")
                componentError != null -> MessageBox(
                    Icons.Rounded.Error,
                    "Не удалось прочитать компоненты",
                    componentError,
                ) { FilledTonalButton(onClick = { componentToken++ }, shapes = ButtonDefaults.shapes()) { Text("Повторить") } }
                shownComponents.isEmpty() -> if (componentQuery.isNotBlank()) {
                    MessageBox(Icons.Rounded.SearchOff, "Ничего не найдено", "По запросу «${componentQuery.trim()}» компонентов нет")
                } else {
                    MessageBox(Icons.Rounded.Apps, "Компонентов нет", "PackageManager не вернул активности, службы, приёмники и провайдеры")
                }
                else -> ComponentList(shownComponents, pending) { toggle(it) }
            }
        }
    }
}

@Composable
private fun PackageList(packages: List<ComponentPackage>, onOpen: (ComponentPackage) -> Unit) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
    ) {
        itemsIndexed(packages, key = { _, pkg -> pkg.packageName }, contentType = { _, _ -> "pkg" }) { i, pkg ->
            SegmentedListItem(
                onClick = { onOpen(pkg) },
                shapes = ListItemDefaults.segmentedShapes(i, packages.size),
                supportingContent = {
                    Text(
                        if (pkg.launchable) pkg.packageName else "${pkg.packageName} · без лаунчера",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = if (i == 0) 0.dp else ListItemDefaults.SegmentedGap),
            ) {
                Text(pkg.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ComponentList(
    items: List<ComponentRecord>,
    pending: Set<String>,
    onToggle: (ComponentRecord) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.listKey }, contentType = { _, _ -> "cmp" }) { i, item ->
            val busy = item.listKey in pending
            SegmentedListItem(
                onClick = { if (!busy) onToggle(item) },
                enabled = !busy,
                shapes = ListItemDefaults.segmentedShapes(i, items.size),
                supportingContent = {
                    Text(item.kind.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailingContent = {
                    Switch(checked = item.enabled, onCheckedChange = null, enabled = !busy)
                },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = if (i == 0) 0.dp else ListItemDefaults.SegmentedGap),
            ) {
                Text(
                    item.className,
                    maxLines = 2,
                    overflow = TextOverflow.MiddleEllipsis,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }
    }
}

@Composable
private fun LoadingBox(text: String) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        LoadingIndicator(Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageBox(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}
