package client.wexmanager.feature.network

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.network.RemoteConnection
import client.wexmanager.core.network.RemoteConnections
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.ConfirmDialog
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NetworkViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RemoteConnections.get(app)
    val connections: StateFlow<List<RemoteConnection>> = repo.connections
    val loaded: StateFlow<Boolean> = repo.loaded

    fun duplicate(id: String) {
        viewModelScope.launch { repo.duplicate(id) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }
}

@Immutable
private data class EditTarget(val connection: RemoteConnection?)

@Composable
fun NetworkScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm: NetworkViewModel = viewModel()
    val list by vm.connections.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    var deleting by remember { mutableStateOf<RemoteConnection?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim()
    val shown = remember(list, needle) {
        if (needle.isEmpty()) list
        else list.filter { c ->
            c.name.contains(needle, ignoreCase = true) || c.host.contains(needle, ignoreCase = true)
        }
    }
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val scheme = MaterialTheme.colorScheme

    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Сетевые хранилища") },
                subtitle = {
                    Text(
                        when {
                            list.isEmpty() -> "SFTP • FTP • FTPS • SMB"
                            needle.isEmpty() -> "${list.size} ${Format.plural(list.size, "подключение", "подключения", "подключений")}"
                            else -> "${shown.size} из ${list.size}"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = scheme.surfaceContainer,
                    scrolledContainerColor = scheme.surfaceContainer,
                ),
            )
        },
        floatingActionButton = {
            if (loaded) {
                ExtendedFloatingActionButton(
                    text = { Text("Добавить") },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    onClick = { editing = EditTarget(null) },
                    expanded = fabExpanded || list.isEmpty(),
                )
            }
        },
        containerColor = scheme.surfaceContainer,
    ) { padding ->
        when {
            !loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(64.dp))
            }
            list.isEmpty() -> NetworkStateView(
                polygon = MaterialShapes.Cookie9Sided,
                icon = Icons.Rounded.Hub,
                title = "Нет подключений",
                text = "Добавьте сервер SFTP, FTP, FTPS или общую папку SMB — и работайте с файлами по сети как с локальными.",
                modifier = Modifier.padding(padding),
                actions = {
                    Button(onClick = { editing = EditTarget(null) }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text("Добавить")
                    }
                },
            )
            else -> Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                ConnectionFilterField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (shown.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Нет совпадений",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = padding.calculateBottomPadding() + 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        itemsIndexed(shown, key = { _, c -> c.id }) { i, c ->
                            ConnectionItem(
                                connection = c,
                                shapes = ListItemDefaults.segmentedShapes(i, shown.size),
                                onClick = { onOpen(c.id) },
                                onEdit = { editing = EditTarget(c) },
                                onDuplicate = { vm.duplicate(c.id) },
                                onDelete = { deleting = c },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                    fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                ),
                            )
                        }
                        item(key = "hint") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp).navigationBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Shield, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Пароли хранятся зашифрованными ключом Android Keystore. Отпечатки серверов запоминаются при первом подключении.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { target ->
        ConnectionEditorSheet(
            initial = target.connection,
            onDismiss = { editing = null },
            onSaved = { editing = null },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = "Удалить подключение?",
            text = "«${c.title}» будет удалено вместе с сохранённым паролем и ключом.",
            icon = Icons.Rounded.Delete,
            confirmLabel = "Удалить",
            onConfirm = { vm.delete(c.id) },
            onDismiss = { deleting = null },
            destructive = true,
        )
    }
}

@Composable
private fun ConnectionFilterField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text("Фильтр") },
        leadingIcon = { Icon(Icons.Rounded.FilterList, null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, "Очистить")
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ConnectionItem(
    connection: RemoteConnection,
    shapes: ListItemShapes,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        onLongClick = { menu = true },
        onLongClickLabel = "Действия",
        shapes = shapes,
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = scheme.surfaceContainerLowest),
        leadingContent = { TypeBadge(connection.type, size = 46.dp) },
        overlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(connection.type.title)
                if (connection.privateKeyPath != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Key, "Вход по ключу", Modifier.size(14.dp))
                }
            }
        },
        supportingContent = {
            Text(
                connection.address,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.MoreVert, "Ещё")
                }
                DropdownMenuPopup(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                        DropdownMenuItem(
                            text = { Text("Изменить") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = { menu = false; onEdit() },
                            shape = MenuDefaults.leadingItemShape,
                        )
                        DropdownMenuItem(
                            text = { Text("Дублировать") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                            onClick = { menu = false; onDuplicate() },
                            shape = MenuDefaults.trailingItemShape,
                        )
                    }
                    Spacer(Modifier.size(MenuDefaults.GroupSpacing))
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                        DropdownMenuItem(
                            text = { Text("Удалить", color = scheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = scheme.error) },
                            onClick = { menu = false; onDelete() },
                            shape = MenuDefaults.standaloneItemShape,
                        )
                    }
                }
            }
        },
    ) {
        Text(connection.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
