package client.wexmanager.feature.tools

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.system.Os
import android.system.OsConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.appContainer
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.ops.TrashManager
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

enum class JunkKind(val title: String, val hint: String, val icon: ImageVector) {
    Temp("Временные и недокачанные", ".tmp, .part, .crdownload, незавершённые копии", Icons.Rounded.HourglassEmpty),
    Thumbs("Кеш миниатюр", "Папки .thumbnails — система создаст их заново", Icons.Rounded.PhotoLibrary),
    Trash("Служебный мусор", "Thumbs.db, .DS_Store, desktop.ini с других систем", Icons.Rounded.AutoDelete),
    Apk("Установленные APK", "Приложение уже стоит той же или более новой версии", Icons.Rounded.Android),
    StaleInstallers(
        "Старые установщики в «Загрузках»",
        "APK, APKS и XAPK прямо в папке Download, старше 30 дней. Вложенные папки и Android/ не смотрим",
        Icons.Rounded.FileDownload,
    ),
}

@Immutable
data class JunkItem(val path: String, val name: String, val size: Long, val kind: JunkKind, val note: String? = null)

data class CleanupState(
    val scanning: Boolean = true,
    val scanned: Int = 0,
    val items: List<JunkItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val expanded: Set<JunkKind> = emptySet(),
)

class CleanupViewModel(app: Application, private val root: String) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(CleanupState())
    val state = _state.asStateFlow()
    private var job: Job? = null

    init {
        scan()
    }

    fun scan() {
        job?.cancel()
        _state.value = CleanupState(scanning = true)
        job = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { collect() }
            _state.update {
                // Установленные APK и старые установщики в Download по умолчанию не отмечены.
                it.copy(
                    scanning = false,
                    items = found,
                    selected = found.filter { j -> j.kind != JunkKind.Apk && j.kind != JunkKind.StaleInstallers }.mapTo(HashSet()) { j -> j.path },
                )
            }
        }
    }

    private suspend fun collect(): List<JunkItem> {
        val out = ArrayList<JunkItem>()
        val apks = ArrayList<File>()
        val stale = ArrayList<JunkItem>()
        val skip = setOf("$root/Android/data", "$root/Android/obb", "$root/${TrashManager.TRASH_DIR}")
        val downloadDir = "$root/${Environment.DIRECTORY_DOWNLOADS}"
        val stack = ArrayDeque<String>().apply { add(root) }
        var count = 0
        val now = System.currentTimeMillis()
        // Свежие временные файлы могут ещё писаться (загрузка браузера, камера, наша задача) — не трогаем.
        val tempCutoff = now - 24 * 3600_000L
        val ownCutoff = now - 3600_000L
        val installerCutoff = now - 30L * 24 * 3600_000L
        val tasksBusy = getApplication<Application>().appContainer.taskManager.hasActive
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dir = stack.removeLast()
            val names = File(dir).list() ?: continue
            for (name in names) {
                val path = "$dir/$name"
                if (path in skip) continue
                val st = runCatching { Os.lstat(path) }.getOrNull() ?: continue
                if (OsConstants.S_ISLNK(st.st_mode)) continue // по ссылкам не ходим
                if (++count % 500 == 0) _state.update { it.copy(scanned = count) }
                if (OsConstants.S_ISDIR(st.st_mode)) {
                    if (name == ".thumbnails") {
                        val size = runCatching { LocalFileSystem.directorySize(path).third }.getOrDefault(0L)
                        if (size > 0) out += JunkItem(path, "$name — ${File(dir).name}", size, JunkKind.Thumbs)
                    } else stack.add(path)
                    continue
                }
                val lower = name.lowercase()
                val ext = lower.substringAfterLast('.', "")
                val mtime = st.st_mtime * 1000L
                val own = lower.startsWith(".wex-") && (ext == "part" || ext == "old")
                // .pending-<секунды>-имя (MediaStore): в имени — срок, до которого файл считается занятым.
                val pendingUntil = if (lower.startsWith(".pending-")) lower.removePrefix(".pending-").substringBefore('-').toLongOrNull()?.times(1000) else null
                when {
                    own -> if (!tasksBusy && mtime < ownCutoff) out += JunkItem(path, name, st.st_size, JunkKind.Temp)
                    pendingUntil != null -> if (pendingUntil < now && mtime < tempCutoff) out += JunkItem(path, name, st.st_size, JunkKind.Temp)
                    ext in TEMP_EXT || lower.endsWith("~") -> if (mtime < tempCutoff) out += JunkItem(path, name, st.st_size, JunkKind.Temp)
                    lower in SYSTEM_JUNK -> out += JunkItem(path, name, st.st_size, JunkKind.Trash)
                    ext == "apk" -> apks += File(path)
                }
                // Только прямые файлы Download. Подпапки и Android/ в эту категорию не попадают.
                if (dir == downloadDir && OsConstants.S_ISREG(st.st_mode) && ext in INSTALLER_EXT && mtime < installerCutoff) {
                    stale += JunkItem(path, name, st.st_size, JunkKind.StaleInstallers)
                }
            }
        }
        _state.update { it.copy(scanned = count) }
        val stalePaths = stale.mapTo(HashSet(stale.size)) { it.path }
        out += stale
        // APK: сравниваем с установленной версией (разбор APK недешёвый — только для найденных).
        val pm = getApplication<Application>().packageManager
        for (f in apks) {
            coroutineContext.ensureActive()
            if (f.path in stalePaths) continue
            val info = runCatching { pm.getPackageArchiveInfo(f.path, 0) }.getOrNull() ?: continue
            val installed = runCatching { pm.getPackageInfo(info.packageName, 0) }.getOrNull() ?: continue
            if (installed.versionCodeCompat() >= info.versionCodeCompat()) {
                out += JunkItem(f.path, f.name, f.length(), JunkKind.Apk, "${info.packageName} · установлена ${installed.versionName ?: installed.versionCodeCompat()}")
            }
        }
        return out.sortedByDescending { it.size }
    }

    private fun android.content.pm.PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()

    fun toggle(path: String) = _state.update { s -> s.copy(selected = if (path in s.selected) s.selected - path else s.selected + path) }

    fun toggleKind(kind: JunkKind) = _state.update { s ->
        val paths = s.items.filter { it.kind == kind }.map { it.path }
        val all = paths.all { it in s.selected }
        s.copy(selected = if (all) s.selected - paths.toSet() else s.selected + paths)
    }

    fun expand(kind: JunkKind) = _state.update { s -> s.copy(expanded = if (kind in s.expanded) s.expanded - kind else s.expanded + kind) }

    fun clean() {
        val s = _state.value
        val chosen = s.items.filter { it.path in s.selected }
        if (chosen.isEmpty()) return
        val c = getApplication<Application>().appContainer
        _state.update { st -> st.copy(items = st.items.filterNot { it.path in st.selected }, selected = emptySet()) }
        viewModelScope.launch {
            // Мусор удаляем сразу; установщики — по настройке корзины (вдруг пригодятся). stat — не на главном потоке.
            val (apks, junk) = chosen.partition { it.kind == JunkKind.Apk || it.kind == JunkKind.StaleInstallers }
            val junkItems = withContext(Dispatchers.IO) { junk.map { LocalFileSystem.stat(it.path) } }
            val apkItems = withContext(Dispatchers.IO) { apks.map { LocalFileSystem.stat(it.path) } }
            if (junkItems.isNotEmpty()) c.taskManager.delete(junkItems, toTrash = false)
            if (apkItems.isNotEmpty()) c.taskManager.delete(apkItems, toTrash = c.settings.current.useTrash)
        }
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        val TEMP_EXT = setOf("tmp", "temp", "part", "partial", "crdownload", "download", "bak~", "swp")
        val SYSTEM_JUNK = setOf("thumbs.db", ".ds_store", "desktop.ini", "ehthumbs.db")
        val INSTALLER_EXT = setOf("apk", "apks", "xapk")
    }
}

@Composable
fun CleanupScreen(root: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: CleanupViewModel = viewModel(key = "cleanup:$root") { CleanupViewModel(app, root) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirm by remember { mutableStateOf(false) }
    val selectedSize = remember(state.items, state.selected) { state.items.filter { it.path in state.selected }.sumOf { it.size } }

    Scaffold(
        modifier = Modifier.nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Очистка памяти") },
                subtitle = {
                    Text(
                        if (state.scanning) "Проверено ${state.scanned} объектов…"
                        else "Найдено ${Format.size(state.items.sumOf { it.size })}",
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                actions = { IconButton(onClick = vm::scan, enabled = !state.scanning) { Icon(Icons.Rounded.Refresh, "Проверить снова") } },
                scrollBehavior = behavior,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surfaceContainer, scrolledContainerColor = scheme.surfaceContainer),
            )
        },
        floatingActionButton = {
            AnimatedVisibility(!state.scanning && state.selected.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
                ExtendedFloatingActionButton(
                    onClick = { confirm = true },
                    icon = { Icon(Icons.Rounded.CleaningServices, null) },
                    text = { Text("Очистить ${Format.size(selectedSize)}") },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
        containerColor = scheme.surfaceContainer,
    ) { padding ->
        when {
            state.scanning -> Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LoadingIndicator(Modifier.size(72.dp))
                Text("Ищем ненужные файлы…", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
            state.items.isEmpty() -> ToolsEmptyState(
                polygon = MaterialShapes.Sunny,
                icon = Icons.Rounded.TaskAlt,
                title = "Чисто",
                text = "Временных файлов, кеша миниатюр, лишних APK и старых установщиков в Download не найдено",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = 120.dp),
            ) {
                JunkKind.entries.forEach { kind ->
                    val group = state.items.filter { it.kind == kind }
                    if (group.isEmpty()) return@forEach
                    val expanded = kind in state.expanded
                    val rows = if (expanded) group.size + 1 else 1
                    item(key = kind.name) {
                        JunkHeader(kind, group, state.selected, expanded, rows, onToggleAll = { vm.toggleKind(kind) }, onExpand = { vm.expand(kind) })
                    }
                    // Строки группы — ленивые: в раскрытой группе могут быть тысячи файлов.
                    if (expanded) items(group.size, key = { "${kind.name}:${group[it].path}" }) { i ->
                        JunkRow(group[i], group[i].path in state.selected, i + 1, rows, vm::toggle)
                    }
                    item(key = "${kind.name}:gap") { androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp)) }
                }
            }
        }
    }
    if (confirm) {
        ConfirmDialog(
            title = "Очистить ${Format.size(selectedSize)}?",
            text = "Временные файлы (старше суток), кеш миниатюр и служебный мусор удаляются сразу. Установленные APK и старые установщики из Download — " +
                if (context.appContainer.settings.current.useTrash) "в корзину." else "навсегда.",
            icon = Icons.Rounded.CleaningServices,
            confirmLabel = "Очистить",
            onConfirm = { confirm = false; vm.clean() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
private fun JunkHeader(
    kind: JunkKind,
    items: List<JunkItem>,
    selected: Set<String>,
    expanded: Boolean,
    rows: Int,
    onToggleAll: () -> Unit,
    onExpand: () -> Unit,
) {
    val chosen = items.count { it.path in selected }
    SegmentedListItem(
        onClick = onExpand,
        shapes = ListItemDefaults.segmentedShapes(0, rows),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = chosen == items.size, onCheckedChange = { onToggleAll() })
                Icon(kind.icon, null)
            }
        },
        supportingContent = {
            val count = if (kind == JunkKind.Thumbs) Format.folders(items.size) else Format.files(items.size)
            Text("${Format.size(items.sumOf { it.size })} · $count · ${kind.hint}")
        },
        trailingContent = { Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) },
    ) { Text(kind.title) }
}

@Composable
private fun JunkRow(j: JunkItem, checked: Boolean, index: Int, rows: Int, onToggle: (String) -> Unit) {
    SegmentedListItem(
        onClick = { onToggle(j.path) },
        shapes = ListItemDefaults.segmentedShapes(index, rows),
        modifier = Modifier.padding(top = ListItemDefaults.SegmentedGap),
        leadingContent = { Checkbox(checked = checked, onCheckedChange = { onToggle(j.path) }) },
        supportingContent = { Text(j.note ?: j.path.substringBeforeLast('/'), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        trailingContent = { Text(Format.size(j.size), style = MaterialTheme.typography.labelMedium) },
    ) { Text(j.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) }
}
