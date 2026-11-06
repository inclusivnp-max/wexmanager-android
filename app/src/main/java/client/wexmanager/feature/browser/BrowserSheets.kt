package client.wexmanager.feature.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkRemove
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.fs.Location
import client.wexmanager.core.settings.Bookmark
import client.wexmanager.core.settings.ListDensity
import client.wexmanager.core.settings.SortBy
import client.wexmanager.core.settings.ViewMode
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.SectionLabel
import client.wexmanager.ui.theme.LocalAppSettings

sealed interface BrowserSheet {
    data class Actions(val item: FileItem) : BrowserSheet
    data object Selection : BrowserSheet
    data object Create : BrowserSheet
    data object SortView : BrowserSheet
    data object History : BrowserSheet
    data object Bookmarks : BrowserSheet
}

data class SheetAction(
    val title: String,
    val icon: ImageVector,
    val supporting: String? = null,
    val onClick: () -> Unit,
)

@Composable
fun ActionGroup(actions: List<SheetAction>, onDismiss: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        actions.forEachIndexed { i, a ->
            SegmentedListItem(
                onClick = { onDismiss(); a.onClick() },
                shapes = ListItemDefaults.segmentedShapes(i, actions.size),
                leadingContent = { Icon(a.icon, null) },
                supportingContent = a.supporting?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            ) { Text(a.title) }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(80.dp)) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp), shapes = androidx.compose.material3.IconButtonDefaults.shapes()) {
            Icon(icon, label)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AppBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) { content() }
    }
}

fun copyToSystemClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("path", text))
    Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
}

@Composable
fun FileActionsSheet(
    item: FileItem,
    vm: BrowserViewModel,
    isBookmarked: Boolean,
    onDialog: (BrowserDialog) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalAppSettings.current
    val context = LocalContext.current
    AppBottomSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FileIcon(item, size = 56.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(item.subtitle(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.mode >= 0) Text(
                    item.permissionString,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        val inArchive = item.isInArchive
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (inArchive) {
                QuickAction(Icons.Rounded.Unarchive, "Распаковать") { onDismiss(); vm.transferToOther(false, item) }
                QuickAction(Icons.Rounded.Delete, "Удалить") { onDismiss(); onDialog(BrowserDialog.Delete(listOf(item))) }
                QuickAction(Icons.Rounded.Info, "Свойства") { onDismiss(); onDialog(BrowserDialog.Properties(listOf(item))) }
            } else {
                QuickAction(Icons.Rounded.ContentCopy, "Копировать") { onDismiss(); vm.transferToOther(false, item) }
                QuickAction(Icons.AutoMirrored.Rounded.DriveFileMove, "Переместить") { onDismiss(); vm.transferToOther(true, item) }
                QuickAction(Icons.Rounded.DriveFileRenameOutline, "Переименовать") { onDismiss(); onDialog(BrowserDialog.Rename(item)) }
                QuickAction(Icons.Rounded.Delete, "Удалить") { onDismiss(); onDialog(BrowserDialog.Delete(listOf(item))) }
                if (!item.isDirectory) QuickAction(Icons.Rounded.Share, "Отправить") { onDismiss(); vm.share(listOf(item)) }
            }
        }
        Spacer(Modifier.height(16.dp))
        val open = buildList {
            add(SheetAction("Открыть", Icons.AutoMirrored.Rounded.OpenInNew) { vm.open(vm.activePanel, item) })
            if (!item.isDirectory && !inArchive) {
                add(SheetAction("Открыть как…", Icons.Rounded.Category) { onDialog(BrowserDialog.OpenAsChooser(item)) })
                add(SheetAction("Открыть в другом приложении", Icons.Rounded.OpenInBrowser) { vm.openAs(item, client.wexmanager.core.util.OpenAs.External) })
                if (item.kind != FileKind.Folder) add(SheetAction("Редактировать как текст", Icons.Rounded.Edit) { vm.openAs(item, client.wexmanager.core.util.OpenAs.Text) })
            }
            if (item.isDirectory) add(SheetAction("Открыть в соседней панели", Icons.Rounded.CallSplit) { vm.openInOther(item) })
        }
        ActionGroup(open, onDismiss)
        if (!inArchive) {
            val edit = buildList {
                add(SheetAction("Копировать в буфер", Icons.Rounded.ContentCopy) { vm.copyToClipboard(false, item) })
                add(SheetAction("Вырезать", Icons.Rounded.ContentCut) { vm.copyToClipboard(true, item) })
                add(SheetAction("Сжать", Icons.Rounded.Archive) { onDialog(BrowserDialog.Compress(listOf(item))) })
                if (item.kind == FileKind.Archive || item.kind == FileKind.Apk || item.extension in setOf("jar", "aar", "epub")) {
                    add(SheetAction("Распаковать…", Icons.Rounded.Unarchive) { onDialog(BrowserDialog.Extract(item)) })
                    add(SheetAction("Просмотреть содержимое", Icons.Rounded.Folder) { vm.activePanel.open(Location.Archive(item.path)) })
                    add(SheetAction("Проверить архив", Icons.Rounded.FactCheck, "Целостность всех записей без распаковки") { vm.verifyArchive(item) })
                }
                if (client.wexmanager.core.apk.SplitInstaller.isBundle(item.name)) {
                    add(SheetAction("Установить пакет", Icons.Rounded.Android, "Split APK: выбор частей под устройство") { vm.open(vm.activePanel, item) })
                }
                if (item.extension == "apk") {
                    add(SheetAction("Установить", Icons.Rounded.Android) {
                        client.wexmanager.core.util.FileOpener.installApk(context, java.io.File(item.path))
                    })
                    add(SheetAction("Подписать APK", Icons.Rounded.Verified, "Подпись v1+v2+v3 · ключ «${vm.signingKeyName()}»") { vm.signApk(item) })
                }
                if (!item.isDirectory) add(SheetAction("HEX-просмотр", Icons.Rounded.Memory) { vm.openAs(item, client.wexmanager.core.util.OpenAs.Hex) })
            }
            ActionGroup(edit, onDismiss)
        }
        val more = buildList {
            add(SheetAction("Свойства", Icons.Rounded.Info, "Размер, права, контрольные суммы") { onDialog(BrowserDialog.Properties(listOf(item))) })
            if (!inArchive) {
                add(
                    SheetAction(
                        if (isBookmarked) "Убрать из закладок" else "В закладки",
                        if (isBookmarked) Icons.Rounded.BookmarkRemove else Icons.Rounded.BookmarkAdd,
                    ) { vm.toggleBookmark(item.path, item.name, item.isDirectory) },
                )
                if (item.isDirectory) add(SheetAction("Цвет папки…", Icons.Rounded.Palette) { onDialog(BrowserDialog.FolderColor(item)) })
                add(SheetAction("Создать ссылку в соседней панели", Icons.Rounded.Link) { vm.createSymlink(item) })
                add(SheetAction("Ярлык на рабочий стол", Icons.Rounded.AddToHomeScreen) {
                    if (!client.wexmanager.core.util.FileOpener.pinShortcut(context, item.path, item.name, item.isDirectory)) {
                        Toast.makeText(context, "Лаунчер не поддерживает ярлыки", Toast.LENGTH_SHORT).show()
                    }
                })
            }
            add(SheetAction("Копировать путь", Icons.Rounded.Route, item.key) { copyToSystemClipboard(context, item.key) })
            add(SheetAction("Копировать имя", Icons.Rounded.TextSnippet) { copyToSystemClipboard(context, item.name) })
        }
        ActionGroup(more, onDismiss)
    }
}

@Composable
fun SelectionSheet(
    vm: BrowserViewModel,
    state: PanelState,
    onDialog: (BrowserDialog) -> Unit,
    onNavigate: (client.wexmanager.ui.navigation.Route) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = state.selectedItems
    val panel = vm.activePanel
    val inArchive = items.any { it.isInArchive }
    AppBottomSheet(onDismiss) {
        Text(
            "Выбрано: ${items.size}",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        ActionGroup(
            listOf(
                SheetAction("Выделить всё", Icons.Rounded.SelectAll) { panel.selectAll() },
                SheetAction("Инвертировать выделение", Icons.Rounded.Flip) { panel.invertSelection() },
                SheetAction("Выделить такие же типы", Icons.Rounded.Category, "По расширениям выбранных файлов") { panel.selectSameType() },
            ),
            onDismiss,
        )
        if (!inArchive) {
            ActionGroup(
                listOf(
                    SheetAction("Копировать в буфер", Icons.Rounded.ContentCopy) { vm.copyToClipboard(false) },
                    SheetAction("Вырезать", Icons.Rounded.ContentCut) { vm.copyToClipboard(true) },
                    SheetAction("Сжать", Icons.Rounded.Archive) { onDialog(BrowserDialog.Compress(items)) },
                    SheetAction("Поделиться", Icons.Rounded.Share) { vm.share(items) },
                    SheetAction("Пакетное переименование", Icons.Rounded.DriveFileRenameOutline) { onDialog(BrowserDialog.BatchRename(items)) },
                ),
                onDismiss,
            )
            val archives = items.filter { !it.isDirectory && it.kind == client.wexmanager.core.fs.FileKind.Archive }
            if (archives.size >= 2 && archives.size == items.size) {
                ActionGroup(
                    listOf(
                        SheetAction("Распаковать каждый в свою папку", Icons.Rounded.Unarchive, "${archives.size} ${client.wexmanager.core.util.Format.plural(archives.size, "архив", "архива", "архивов")} — рядом, в папки с их именами") {
                            vm.extractEach(archives)
                        },
                    ),
                    onDismiss,
                )
            }
        } else {
            ActionGroup(
                listOf(SheetAction("Распаковать в соседнюю панель", Icons.Rounded.Unarchive) { vm.transferToOther(false) }),
                onDismiss,
            )
        }
        ActionGroup(
            buildList {
                if (items.size == 2 && items.none { it.isDirectory || it.isInArchive }) {
                    add(SheetAction("Сравнить файлы", Icons.Rounded.Difference, "${items[0].name} ↔ ${items[1].name}") {
                        onNavigate(client.wexmanager.ui.navigation.Route.Diff(items[0].path, items[1].path))
                    })
                }
                add(SheetAction("Свойства", Icons.Rounded.Info) { onDialog(BrowserDialog.Properties(items)) })
            },
            onDismiss,
        )
    }
}

@Composable
fun CreateSheet(vm: BrowserViewModel, hasClipboard: Boolean, onDialog: (BrowserDialog) -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet(onDismiss) {
        Text("Создать", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        ActionGroup(
            buildList {
                add(SheetAction("Папку", Icons.Rounded.CreateNewFolder) { onDialog(BrowserDialog.Create(true)) })
                add(SheetAction("Файл", Icons.Rounded.NoteAdd) { onDialog(BrowserDialog.Create(false)) })
                if (hasClipboard) add(SheetAction("Вставить из буфера", Icons.Rounded.ContentPaste) { vm.paste() })
            },
            onDismiss,
        )
    }
}

@Composable
fun SortViewSheet(vm: BrowserViewModel, onDismiss: () -> Unit) {
    val s = LocalAppSettings.current
    AppBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Text("Сортировка и вид", style = MaterialTheme.typography.titleLarge)
            SectionLabel("Сортировать по")
            ConnectedChoice(
                options = SortBy.entries,
                selected = s.sortBy,
                onSelect = { v -> vm.updateSettings { it.copy(sortBy = v) } },
                label = {
                    when (it) {
                        SortBy.Name -> "Имя"
                        SortBy.Date -> "Дата"
                        SortBy.Size -> "Размер"
                        SortBy.Type -> "Тип"
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            ConnectedChoice(
                options = listOf(false, true),
                selected = s.sortDescending,
                onSelect = { v -> vm.updateSettings { it.copy(sortDescending = v) } },
                label = { if (it) "По убыванию" else "По возрастанию" },
            )
            SectionLabel("Вид")
            ConnectedChoice(
                options = ViewMode.entries,
                selected = s.viewMode,
                onSelect = { v -> vm.updateSettings { it.copy(viewMode = v) } },
                label = { if (it == ViewMode.List) "Список" else "Сетка" },
                icon = { if (it == ViewMode.List) Icons.Rounded.ViewList else Icons.Rounded.GridView },
            )
            Spacer(Modifier.height(8.dp))
            ConnectedChoice(
                options = ListDensity.entries,
                selected = s.listDensity,
                onSelect = { v -> vm.updateSettings { it.copy(listDensity = v) } },
                label = {
                    when (it) {
                        ListDensity.Compact -> "Плотно"
                        ListDensity.Comfortable -> "Обычно"
                        ListDensity.Spacious -> "Просторно"
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            SwitchRow("Папки сначала", Icons.Rounded.Folder, s.foldersFirst) { v -> vm.updateSettings { it.copy(foldersFirst = v) } }
            SwitchRow("Скрытые файлы", if (s.showHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, s.showHidden) { v -> vm.updateSettings { it.copy(showHidden = v) } }
            SwitchRow("Миниатюры", Icons.Rounded.GridView, s.showThumbnails) { v -> vm.updateSettings { it.copy(showThumbnails = v) } }
            SwitchRow("Права доступа", Icons.AutoMirrored.Rounded.Sort, s.showPermissions) { v -> vm.updateSettings { it.copy(showPermissions = v) } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun SwitchRow(title: String, icon: ImageVector?, checked: Boolean, supporting: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun HistorySheet(history: List<Location>, onOpen: (Location) -> Unit, onDismiss: () -> Unit) {
    AppBottomSheet(onDismiss) {
        Text("История", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        if (history.isEmpty()) {
            Text("Пока пусто", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ActionGroup(history.take(40).map { loc ->
                SheetAction(loc.title.ifEmpty { "/" }, if (loc is Location.Archive) Icons.Rounded.Archive else Icons.Rounded.History, loc.displayPath) { onOpen(loc) }
            }, onDismiss)
        }
    }
}

@Composable
fun BookmarksSheet(
    bookmarks: List<Bookmark>,
    currentPath: String?,
    onOpen: (Bookmark) -> Unit,
    onAddCurrent: () -> Unit,
    onRemove: (Bookmark) -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismiss) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Закладки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (currentPath != null && bookmarks.none { it.path == currentPath }) {
                androidx.compose.material3.FilledTonalButton(onClick = { onAddCurrent(); onDismiss() }) {
                    Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Добавить эту папку")
                }
            }
        }
        if (bookmarks.isEmpty()) {
            Text(
                "Сохраняйте часто используемые папки и файлы — долгое нажатие на кнопку закладок добавит текущую папку.",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                bookmarks.forEachIndexed { i, b ->
                    SegmentedListItem(
                        onClick = { onDismiss(); onOpen(b) },
                        shapes = ListItemDefaults.segmentedShapes(i, bookmarks.size),
                        leadingContent = { Icon(if (b.isDirectory) Icons.Rounded.Bookmark else Icons.Rounded.TextSnippet, null) },
                        supportingContent = { Text(b.path, maxLines = 1, overflow = TextOverflow.StartEllipsis) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(b) }) { Icon(Icons.Rounded.Close, "Удалить закладку") }
                        },
                    ) { Text(b.title) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

val SheetContentPadding = PaddingValues(horizontal = 16.dp)
