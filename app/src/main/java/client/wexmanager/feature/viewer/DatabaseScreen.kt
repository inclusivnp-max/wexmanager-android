package client.wexmanager.feature.viewer

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditOff
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.db.DbResult
import client.wexmanager.core.db.DbTable
import client.wexmanager.core.db.SqliteBrowser
import client.wexmanager.feature.apk.ShapeMessage
import client.wexmanager.feature.apk.ShapedIcon
import client.wexmanager.feature.browser.copyToSystemClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class DbState(
    val loading: Boolean = true,
    val error: String? = null,
    val writable: Boolean = false,
    val tables: List<DbTable> = emptyList(),
    val table: String? = null,
    val result: DbResult? = null,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val sqlResult: DbResult? = null,
    val sqlHistory: List<String> = emptyList(),
    val message: String? = null,
)

class DatabaseViewModel(app: Application, private val path: String) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(DbState())
    val state: StateFlow<DbState> = _state.asStateFlow()
    private var db: SqliteBrowser? = null
    private val lock = Mutex()

    init { open(false) }

    fun open(writable: Boolean) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    lock.withLock {
                        db?.close()
                        SqliteBrowser.open(File(path), writable, getApplication<Application>().cacheDir).also { db = it }.let { it to it.tables() }
                    }
                }
            }
            result.onSuccess { (b, tables) ->
                _state.update { it.copy(loading = false, writable = b.writable, tables = tables) }
                state.value.table?.let { select(it) }
            }.onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: e.javaClass.simpleName) } }
        }
    }

    private var loadJob: kotlinx.coroutines.Job? = null

    fun select(table: String?) {
        // Страница прежней таблицы не должна лечь в новую (другие колонки, чужие rowid при правке).
        loadJob?.cancel()
        _state.update { it.copy(table = table, result = null, endReached = false, loadingMore = false) }
        if (table != null) loadMore()
    }

    fun loadMore() {
        val s = state.value
        val table = s.table ?: return
        if (s.loadingMore || s.endReached) return
        _state.update { it.copy(loadingMore = true) }
        val offset = s.result?.rows?.size ?: 0
        loadJob = viewModelScope.launch {
            val page = runCatching { withContext(Dispatchers.IO) { lock.withLock { db!!.page(table, offset, PAGE) } } }
            page.onSuccess { p ->
                _state.update {
                    if (it.table != table || (it.result?.rows?.size ?: 0) != offset) return@update it.copy(loadingMore = false)
                    val prev = it.result
                    val merged = if (prev == null) p else prev.copy(rows = prev.rows + p.rows, rowIds = prev.rowIds + p.rowIds)
                    it.copy(result = merged, loadingMore = false, endReached = p.rows.size < PAGE)
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(loadingMore = false, message = e.message) }
            }
        }
    }

    fun run(sql: String) {
        viewModelScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { lock.withLock { db!!.execute(sql) } } }
            r.onSuccess { res ->
                val statement = sql.trim()
                _state.update { s ->
                    val history = if (statement.isEmpty()) s.sqlHistory
                    else (listOf(statement) + s.sqlHistory.filterNot { it == statement }).take(8)
                    s.copy(sqlResult = res, sqlHistory = history, message = res.changed?.let { n -> "Изменено строк: $n" })
                }
                if (res.changed != null) refreshTables()
            }.onFailure { e -> _state.update { it.copy(message = e.message ?: "Ошибка SQL") } }
        }
    }

    fun update(column: String, rowId: Long, text: String?, setNull: Boolean) {
        val table = state.value.table ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { lock.withLock { db!!.update(table, column, rowId, text, setNull) } } }
                .onSuccess { select(table) }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    fun deleteRow(rowId: Long) {
        val table = state.value.table ?: return
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { lock.withLock { db!!.deleteRow(table, rowId) } } }
                .onSuccess { refreshTables(); select(table) }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    fun schema(table: String, onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(withContext(Dispatchers.IO) { lock.withLock { runCatching { db!!.schema(table) }.getOrDefault("") } }) }
    }

    private fun refreshTables() {
        viewModelScope.launch {
            val t = runCatching { withContext(Dispatchers.IO) { lock.withLock { db!!.tables() } } }.getOrNull() ?: return@launch
            _state.update { it.copy(tables = t) }
        }
    }

    /** CSV текущего результата SQL в папку базы. Режим правки не меняется. */
    fun exportCsv() {
        val result = state.value.sqlResult
        if (result == null || result.columns.isEmpty()) {
            _state.update { it.copy(message = "Нет результата для экспорта") }
            return
        }
        viewModelScope.launch {
            val saved = runCatching { withContext(Dispatchers.IO) { SqliteBrowser.exportCsv(File(path), result) } }
            saved.onSuccess { file -> _state.update { it.copy(message = "Сохранено: ${file.name}") } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "Не удалось экспортировать CSV") } }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun closeSql() = _state.update { it.copy(sqlResult = null) }

    override fun onCleared() {
        runCatching { db?.close() }
    }

    companion object { const val PAGE = 200 }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DatabaseScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: DatabaseViewModel = viewModel(key = "db:$path") { DatabaseViewModel(app, path) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    var sqlOpen by rememberSaveable { mutableStateOf(false) }
    var confirmWrite by remember { mutableStateOf(false) }
    var cell by remember { mutableStateOf<Triple<Int, Int, String>?>(null) } // строка, колонка, текст
    var schemaText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { scope.launch { snackbar.showSnackbar(it) }; vm.consumeMessage() }
    }
    BackHandler(sqlOpen) { sqlOpen = false; vm.closeSql() }
    BackHandler(!sqlOpen && state.table != null) { vm.select(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            sqlOpen -> { sqlOpen = false; vm.closeSql() }
                            state.table != null -> vm.select(null)
                            else -> onBack()
                        }
                    }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                },
                title = {
                    Column {
                        Text(if (sqlOpen) "SQL" else state.table ?: File(path).name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (state.writable) "Правка включена — изменения пишутся в файл" else "Только чтение",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.writable) scheme.error else scheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (state.table != null && !sqlOpen) IconButton(onClick = { vm.schema(state.table!!) { schemaText = it } }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Code, "Схема") }
                    if (!sqlOpen) IconButton(onClick = { sqlOpen = true }, enabled = state.error == null, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.PlayArrow, "SQL") }
                    IconButton(
                        onClick = { if (state.writable) vm.open(false) else confirmWrite = true },
                        enabled = state.error == null,
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(if (state.writable) Icons.Rounded.EditOff else Icons.Rounded.Edit, if (state.writable) "Только чтение" else "Режим правки") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = scheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingIndicator(Modifier.size(64.dp).align(Alignment.Center))
                state.error != null -> ShapeMessage(Icons.Rounded.Error, "Не удалось открыть базу", state.error, error = true) {
                    FilledTonalButton(onClick = { vm.open(false) }, shapes = ButtonDefaults.shapes()) { Text("Повторить") }
                }
                sqlOpen -> SqlPanel(state.sqlResult, state.sqlHistory, onRun = vm::run, onExport = vm::exportCsv)
                state.table == null -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    itemsIndexed(state.tables, key = { _, t -> t.name }) { i, t ->
                        SegmentedListItem(
                            onClick = { vm.select(t.name) },
                            shapes = ListItemDefaults.segmentedShapes(i, state.tables.size),
                            leadingContent = {
                                ShapedIcon(
                                    if (t.type == "view") Icons.Rounded.Visibility else Icons.Rounded.TableChart,
                                    if (t.type == "view") MaterialShapes.Pill else MaterialShapes.Cookie9Sided,
                                    scheme.secondaryContainer, scheme.onSecondaryContainer,
                                )
                            },
                            supportingContent = { Text((if (t.type == "view") "Представление" else "Таблица") + if (t.rows >= 0) " · ${t.rows} строк" else "") },
                            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                        ) { Text(t.name, fontFamily = FontFamily.Monospace) }
                    }
                }
                else -> state.result?.let { r ->
                    Grid(r, loadingMore = state.loadingMore, onEnd = vm::loadMore) { row, col -> cell = Triple(row, col, r.rows[row][col].text) }
                } ?: LoadingIndicator(Modifier.size(64.dp).align(Alignment.Center))
            }
        }
    }

    if (confirmWrite) AlertDialog(
        onDismissRequest = { confirmWrite = false },
        icon = { Icon(Icons.Rounded.Edit, null) },
        title = { Text("Включить правку?") },
        text = { Text("Изменения будут записываться прямо в файл базы. Если её использует приложение, закройте его. Сделайте копию, если сомневаетесь.") },
        confirmButton = { Button(onClick = { confirmWrite = false; vm.open(true) }) { Text("Включить") } },
        dismissButton = { TextButton(onClick = { confirmWrite = false }) { Text("Отмена") } },
    )
    schemaText?.let { s ->
        AlertDialog(
            onDismissRequest = { schemaText = null },
            title = { Text("Схема") },
            text = { SelectionContainer { Text(s, fontFamily = FontFamily.Monospace, modifier = Modifier.verticalScroll(rememberScrollState())) } },
            confirmButton = { TextButton(onClick = { copyToSystemClipboard(context, s); schemaText = null }) { Text("Копировать") } },
            dismissButton = { TextButton(onClick = { schemaText = null }) { Text("Закрыть") } },
        )
    }
    cell?.let { (row, col, text) ->
        val r = state.result ?: return@let
        val rowId = r.rowIds.getOrNull(row)
        var value by remember(row, col) { val init = if (r.rows[row][col].isNull) "" else text; mutableStateOf(TextFieldValue(init, TextRange(init.length))) }
        AlertDialog(
            onDismissRequest = { cell = null },
            title = { Text(r.columns[col], fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.writable && rowId != null && !r.rows[row][col].isBlob) {
                        OutlinedTextField(value = value, onValueChange = { value = it }, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { vm.update(r.columns[col], rowId, null, true); cell = null }) { Text("NULL") }
                            TextButton(onClick = { vm.deleteRow(rowId); cell = null }) { Text("Удалить строку", color = scheme.error) }
                        }
                    } else {
                        SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) }
                    }
                }
            },
            confirmButton = {
                if (state.writable && rowId != null && !r.rows[row][col].isBlob) Button(onClick = { vm.update(r.columns[col], rowId, value.text, false); cell = null }) { Text("Сохранить") }
                else TextButton(onClick = { copyToSystemClipboard(context, text); cell = null }) {
                    Icon(Icons.Rounded.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Копировать")
                }
            },
            dismissButton = { TextButton(onClick = { cell = null }) { Text("Закрыть") } },
        )
    }
}

/** Таблица результата: шапка, строки с прокруткой в обе стороны, догрузка у конца. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Grid(r: DbResult, loadingMore: Boolean, onEnd: () -> Unit, onCell: (Int, Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val widths = remember(r.columns, r.rows.size.coerceAtMost(200)) {
        r.columns.indices.map { c ->
            val longest = maxOf(r.columns[c].length, r.rows.take(200).maxOfOrNull { it[c].text.length.coerceAtMost(40) } ?: 0)
            (longest * 10 + 24).coerceIn(72, 360).dp
        }
    }
    val total = widths.fold(0.dp) { a, b -> a + b } + 56.dp
    val h = rememberScrollState()
    val list = rememberLazyListState()
    LaunchedEffect(list, r.rows.size) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last -> if (last >= r.rows.size - 20) onEnd() }
    }
    Column(Modifier.fillMaxSize().horizontalScroll(h)) {
        Row(Modifier.width(total).background(scheme.surfaceContainerHigh).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("#", Modifier.width(56.dp).padding(horizontal = 8.dp), color = scheme.outline, style = MaterialTheme.typography.labelMedium)
            r.columns.forEachIndexed { i, name ->
                Text(name, Modifier.width(widths[i]).padding(horizontal = 8.dp), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LazyColumn(state = list, modifier = Modifier.width(total).fillMaxSize()) {
            itemsIndexed(r.rows) { row, cells ->
                Row(Modifier.width(total).background(if (row % 2 == 0) scheme.surface else scheme.surfaceContainerLow), verticalAlignment = Alignment.CenterVertically) {
                    Text("${row + 1}", Modifier.width(56.dp).padding(8.dp), color = scheme.outline, style = MaterialTheme.typography.labelSmall)
                    cells.forEachIndexed { col, c ->
                        Text(
                            c.text,
                            Modifier.width(widths[col]).clickable { onCell(row, col) }.padding(8.dp),
                            color = if (c.isNull || c.isBlob) scheme.outline else scheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                HorizontalDivider(Modifier.width(total), color = scheme.outlineVariant.copy(alpha = 0.4f))
            }
            if (loadingMore) item { Box(Modifier.width(total).height(64.dp), contentAlignment = Alignment.CenterStart) { LoadingIndicator(Modifier.padding(start = 24.dp).size(32.dp)) } }
            if (r.rows.isEmpty()) item { Text("Нет строк", Modifier.padding(24.dp), color = scheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun SqlPanel(result: DbResult?, history: List<String>, onRun: (String) -> Unit, onExport: () -> Unit) {
    var sql by rememberSaveable { mutableStateOf("SELECT name, type FROM sqlite_master") }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = sql, onValueChange = { sql = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            label = { Text("Запрос SQL") },
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 96.dp, max = 220.dp),
        )
        if (history.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { statement ->
                    SuggestionChip(
                        onClick = { sql = statement },
                        modifier = Modifier.widthIn(max = 280.dp),
                        label = {
                            Text(
                                statement.replace('\n', ' ').replace('\r', ' '),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onRun(sql) }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.width(ButtonDefaults.IconSpacing)); Text("Выполнить")
            }
            FilledTonalButton(
                onClick = onExport,
                enabled = result != null && result.columns.isNotEmpty(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(Icons.Rounded.Upload, null, Modifier.size(ButtonDefaults.IconSize)); Spacer(Modifier.width(ButtonDefaults.IconSpacing)); Text("Экспорт CSV")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (result != null && result.columns.isNotEmpty()) Grid(result, loadingMore = false, onEnd = {}) { _, _ -> }
        else if (result?.changed != null) Text("Изменено строк: ${result.changed}", Modifier.padding(16.dp))
        else Box(Modifier.fillMaxWidth().padding(24.dp)) { Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(48.dp)) }
    }
}
