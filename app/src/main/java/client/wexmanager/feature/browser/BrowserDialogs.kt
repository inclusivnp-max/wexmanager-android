package client.wexmanager.feature.browser

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import client.wexmanager.core.archive.ArchiveFormat
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.ops.ConflictChoice
import client.wexmanager.core.ops.ConflictDecision
import client.wexmanager.core.ops.ConflictRequest
import client.wexmanager.core.util.Format
import client.wexmanager.core.util.OpenAs
import client.wexmanager.ui.components.CheckRow
import client.wexmanager.ui.components.ConfirmDialog
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.SectionLabel
import client.wexmanager.ui.theme.LocalAppSettings

sealed interface BrowserDialog {
    data class Create(val folder: Boolean) : BrowserDialog
    data class Rename(val item: FileItem) : BrowserDialog
    data class BatchRename(val items: List<FileItem>) : BrowserDialog
    data class Delete(val items: List<FileItem>) : BrowserDialog
    data object PathJump : BrowserDialog
    data class Compress(val items: List<FileItem>) : BrowserDialog
    data class Extract(val item: FileItem) : BrowserDialog
    data class OpenAsChooser(val item: FileItem, val archive: String? = null, val entry: String? = null) : BrowserDialog
    data class Properties(val items: List<FileItem>) : BrowserDialog
    data class FolderColor(val item: FileItem) : BrowserDialog
}

@Composable
fun CreateDialog(folder: Boolean, vm: BrowserViewModel, onDismiss: () -> Unit) {
    NamePromptDialog(
        title = if (folder) "Новая папка" else "Новый файл",
        icon = if (folder) Icons.Rounded.CreateNewFolder else Icons.Rounded.NoteAdd,
        initial = if (folder) "Новая папка" else "new.txt",
        selection = if (folder) TextRange(0, "Новая папка".length) else TextRange(0, 3),
        label = "Имя",
        confirmLabel = "Создать",
        onDismiss = onDismiss,
    ) { name, setError ->
        val cb: (String?) -> Unit = { err -> if (err == null) onDismiss() else setError(err) }
        if (folder) vm.createFolder(name, cb) else vm.createFile(name, cb)
    }
}

@Composable
fun RenameDialog(item: FileItem, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val end = if (item.isDirectory || item.extension.isEmpty()) item.name.length else item.name.length - item.extension.length - 1
    NamePromptDialog(
        title = "Переименовать",
        icon = Icons.Rounded.DriveFileRenameOutline,
        initial = item.name,
        selection = TextRange(0, end.coerceAtLeast(0)),
        label = "Новое имя",
        confirmLabel = "Готово",
        onDismiss = onDismiss,
    ) { name, setError ->
        vm.rename(item, name) { err -> if (err == null) onDismiss() else setError(err) }
    }
}

/** Поле имени: фокус и клавиатура при открытии. Проверка имени остаётся у вызывающего. */
@Composable
private fun NamePromptDialog(
    title: String,
    icon: ImageVector,
    initial: String,
    selection: TextRange,
    label: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (value: String, setError: (String?) -> Unit) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, selection)) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    val submit = { onConfirm(value.text) { error = it } }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = {
            val keyboard = LocalSoftwareKeyboardController.current
            val view = LocalView.current
            LaunchedEffect(Unit) {
                var parent = view.parent
                while (parent != null && parent !is DialogWindowProvider) parent = parent.parent
                (parent as? DialogWindowProvider)?.window?.let { window ->
                    val adjust = window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or adjust)
                }
                kotlinx.coroutines.delay(120)
                runCatching { focus.requestFocus() }
                keyboard?.show()
            }
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        error = null
                    },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                        .onFocusChanged { if (it.isFocused) keyboard?.show() },
                )
            }
        },
        confirmButton = { Button(onClick = { submit() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun DeleteDialog(items: List<FileItem>, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val s = LocalAppSettings.current
    val inArchive = items.any { it.isInArchive }
    var permanent by remember { mutableStateOf(!s.useTrash || inArchive) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (permanent) Icons.Rounded.DeleteForever else Icons.Rounded.Delete, null) },
        title = { Text(if (permanent) "Удалить навсегда?" else "Переместить в корзину?") },
        text = {
            Column {
                Text(
                    if (items.size == 1) "«${items[0].name}»" else "Будет удалено: ${Format.items(items.size)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (inArchive) {
                    Spacer(Modifier.size(8.dp))
                    Text("Записи будут удалены из архива. Подпись APK станет недействительной.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (s.useTrash) {
                    Spacer(Modifier.size(8.dp))
                    CheckRow("Удалить без корзины", permanent, { permanent = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { vm.delete(items, permanent); onDismiss() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun PathJumpDialog(current: String, history: List<String>, vm: BrowserViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Route, null) },
        title = { Text("Перейти к пути") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Путь") },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { if (vm.goTo(text)) onDismiss() }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions by produceState(emptyList<String>(), text) {
                    kotlinx.coroutines.delay(120)
                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val dir = java.io.File(text.substringBeforeLast('/', "/").ifEmpty { "/" })
                        val prefix = text.substringAfterLast('/')
                        dir.listFiles { f -> f.isDirectory && f.name.startsWith(prefix, ignoreCase = true) }
                            ?.sortedBy { it.name.lowercase() }?.take(8)?.map { it.path } ?: emptyList()
                    }
                }
                val list = (suggestions + history.filter { it != text }).distinct().take(12)
                if (list.isNotEmpty()) {
                    SectionLabel(if (suggestions.isNotEmpty()) "Подсказки и история" else "История")
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(list) { p ->
                            Text(
                                p,
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1,
                                overflow = TextOverflow.StartEllipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { text = "$p/" }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (vm.goTo(text)) onDismiss() }) { Text("Перейти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun CompressDialog(items: List<FileItem>, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val defaultName = if (items.size == 1) items[0].baseName else (java.io.File(items[0].path).parentFile?.name ?: "archive")
    var name by remember { mutableStateOf(defaultName) }
    var format by remember { mutableStateOf(ArchiveFormat.Zip) }
    var level by remember { mutableFloatStateOf(6f) }
    var toOther by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Archive, null) },
        title = { Text("Создать архив") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Имя архива") },
                    suffix = { Text(".${format.extension}") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLabel("Формат")
                ConnectedChoice(
                    options = listOf(ArchiveFormat.Zip, ArchiveFormat.SevenZ, ArchiveFormat.TarGz),
                    selected = format.takeIf { it in listOf(ArchiveFormat.Zip, ArchiveFormat.SevenZ, ArchiveFormat.TarGz) } ?: ArchiveFormat.Zip,
                    onSelect = { format = it },
                    label = { it.label },
                )
                Spacer(Modifier.size(6.dp))
                ConnectedChoice(
                    options = listOf(ArchiveFormat.Tar, ArchiveFormat.TarXz, ArchiveFormat.TarBz2),
                    selected = format.takeIf { it in listOf(ArchiveFormat.Tar, ArchiveFormat.TarXz, ArchiveFormat.TarBz2) } ?: ArchiveFormat.Zip,
                    onSelect = { format = it },
                    label = { it.label },
                )
                if (format.supportsLevel) {
                    SectionLabel("Степень сжатия: ${level.toInt()}${if (level.toInt() == 0) " (без сжатия)" else if (level >= 9) " (максимум)" else ""}")
                    Slider(value = level, onValueChange = { level = it }, valueRange = 0f..9f, steps = 8)
                }
                if (format == ArchiveFormat.Zip) {
                    SectionLabel("Защита")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text("Пароль (необязательно)") },
                        supportingText = { Text(if (password.isEmpty()) "Без пароля" else "Шифрование AES-256") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SectionLabel("Куда сохранить")
                ConnectedChoice(
                    options = listOf(false, true),
                    selected = toOther,
                    onSelect = { toOther = it },
                    label = { if (it) "Соседняя панель" else "Эта папка" },
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (vm.validateName(name) == null) {
                    vm.compress(items, name.trim(), format, level.toInt(), toOther, password.takeIf { format == ArchiveFormat.Zip && it.isNotEmpty() })
                    onDismiss()
                }
            }) { Text("Сжать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ExtractDialog(item: FileItem, vm: BrowserViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Unarchive, null) },
        title = { Text("Распаковать «${item.name}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val opts = listOf(
                    Triple(ExtractTarget.Subfolder, Icons.Rounded.CreateNewFolder, "В отдельную папку"),
                    Triple(ExtractTarget.Here, Icons.Rounded.FolderOpen, "В текущую папку"),
                    Triple(ExtractTarget.OtherPanel, Icons.Rounded.CallSplit, "В соседнюю панель"),
                )
                opts.forEachIndexed { i, (target, icon, title) ->
                    SegmentedListItem(
                        onClick = { vm.extract(item, target); onDismiss() },
                        shapes = ListItemDefaults.segmentedShapes(i, opts.size),
                        leadingContent = { Icon(icon, null) },
                    ) { Text(title) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun OpenAsDialog(item: FileItem, vm: BrowserViewModel, onDismiss: () -> Unit, archive: String? = null, entry: String? = null) {
    val ext = item.extension.lowercase()
    val current = remember(ext) { if (ext.isEmpty()) null else client.wexmanager.core.util.FileOpener.rememberedMode(ext) }
    var remember by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.OpenInNew, null) },
        title = { Text("Открыть как") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val opts = OpenAs.entries
                opts.forEachIndexed { i, mode ->
                    SegmentedListItem(
                        onClick = {
                            if (remember && ext.isNotEmpty()) vm.setOpenWith(ext, mode)
                            vm.openAs(item, mode, archive, entry)
                            onDismiss()
                        },
                        shapes = ListItemDefaults.segmentedShapes(i, opts.size),
                        leadingContent = {
                            Icon(
                                when (mode) {
                                    OpenAs.Text -> Icons.Rounded.Description
                                    OpenAs.Image -> Icons.Rounded.Image
                                    OpenAs.Media -> Icons.Rounded.PlayCircle
                                    OpenAs.Hex -> Icons.Rounded.Memory
                                    OpenAs.Pdf -> Icons.Rounded.PictureAsPdf
                                    OpenAs.Preview -> Icons.Rounded.Preview
                                    OpenAs.Database -> Icons.Rounded.TableChart
                                    OpenAs.Archive -> Icons.Rounded.FolderZip
                                    OpenAs.Apk -> Icons.Rounded.Android
                                    OpenAs.External -> Icons.Rounded.Apps
                                },
                                null,
                            )
                        },
                        trailingContent = if (mode == current) { { Text("по умолчанию", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } } else null,
                    ) { Text(mode.title) }
                }
                if (ext.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    CheckRow("Всегда открывать .$ext так", remember, { remember = it })
                    if (current != null) {
                        TextButton(onClick = { vm.setOpenWith(ext, null); onDismiss() }) { Text("Сбросить выбор для .$ext") }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ConflictDialog(conflict: ConflictRequest, onDecision: (ConflictDecision) -> Unit) {
    var applyAll by remember { mutableStateOf(false) }
    val s = LocalAppSettings.current
    val bothDirs = conflict.source.isDirectory && conflict.target.isDirectory
    val typeMismatch = conflict.source.isDirectory != conflict.target.isDirectory
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Rounded.Warning, null) },
        title = {
            Text(
                when {
                    typeMismatch -> "Конфликт: файл и папка"
                    bothDirs -> "Папка уже существует"
                    else -> "Файл уже существует"
                },
            )
        },
        text = {
            Column {
                @Composable
                fun Row(label: String, item: FileItem) {
                    ListItem(
                        headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                        overlineContent = { Text(label) },
                        supportingContent = {
                            Text(
                                buildString {
                                    if (!item.isDirectory && item.size >= 0) append(Format.size(item.size, s.sizeUnits)).append("  •  ")
                                    append(Format.date(item.lastModified, s.dateFormat))
                                },
                            )
                        },
                        leadingContent = { FileIcon(item, size = 40.dp, showThumbnail = !item.isInArchive) },
                        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                    )
                }
                Row("Новый", conflict.source)
                Row("Существующий", conflict.target)
                if (typeMismatch) {
                    Text(
                        if (conflict.target.isDirectory) "«Заменить» удалит существующую папку целиком." else "«Заменить» удалит существующий файл.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                } else {
                    CheckRow(if (bothDirs) "Применить ко всем папкам" else "Применить ко всем файлам", applyAll, { applyAll = it })
                }
                Spacer(Modifier.size(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (bothDirs) {
                        Button(onClick = { onDecision(ConflictDecision(ConflictChoice.Merge, applyAll)) }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Folder, null); Spacer(Modifier.width(8.dp)); Text("Объединить")
                        }
                    }
                    if (!bothDirs) {
                        Button(
                            onClick = { onDecision(ConflictDecision(ConflictChoice.Replace, applyAll && !typeMismatch)) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (typeMismatch) androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ) else androidx.compose.material3.ButtonDefaults.buttonColors(),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null); Spacer(Modifier.width(8.dp)); Text("Заменить")
                        }
                    }
                    OutlinedButton(onClick = { onDecision(ConflictDecision(ConflictChoice.KeepBoth, applyAll && !typeMismatch)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.FileCopy, null); Spacer(Modifier.width(8.dp)); Text("Оставить оба")
                    }
                    OutlinedButton(onClick = { onDecision(ConflictDecision(ConflictChoice.Skip, applyAll && !typeMismatch)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Пропустить")
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** Пакетное переименование с предпросмотром. */
@Composable
fun BatchRenameDialog(items: List<FileItem>, vm: BrowserViewModel, onDismiss: () -> Unit) {
    var pattern by remember { mutableStateOf("{name}") }
    var find by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf("1") }
    val preview = remember(items, pattern, find, replace, regex, start) {
        BatchRename.plan(items, pattern, find, replace, regex, start.toIntOrNull() ?: 1)
    }
    val diskTaken by produceState(emptySet<String>(), preview) {
        // Имена, уже занятые на диске файлами вне плана (проверка в фоне).
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val srcNames = items.mapTo(HashSet()) { it.name }
            preview.filter { (i, n) -> i.name != n && n !in srcNames && java.io.File(java.io.File(i.path).parentFile, n).exists() }
                .mapTo(HashSet()) { it.second }
        }
    }
    val invalid = preview.filter { (_, n) -> vm.validateName(n) != null }.mapTo(HashSet()) { it.second }
    val conflicts = preview.groupBy { it.second }.filter { it.value.size > 1 }.keys + diskTaken + invalid
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
        title = { Text("Пакетное переименование") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = pattern, onValueChange = { pattern = it }, singleLine = true,
                    label = { Text("Шаблон") },
                    supportingText = { Text("{name} {ext} {n} {n:3} {date} {parent}") },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = find, onValueChange = { find = it }, singleLine = true,
                        label = { Text("Найти") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = replace, onValueChange = { replace = it }, singleLine = true,
                        label = { Text("Заменить") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CheckRow("Регулярное выражение", regex, { regex = it }, Modifier.weight(1f))
                    OutlinedTextField(
                        value = start, onValueChange = { start = it.filter(Char::isDigit).take(6) }, singleLine = true,
                        label = { Text("{n} с") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.width(96.dp),
                    )
                }
                SectionLabel("Предпросмотр")
                preview.take(30).forEach { (item, newName) ->
                    Text(
                        "${item.name}  →  $newName",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (newName in conflicts) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                    )
                }
                if (preview.size > 30) Text("…и ещё ${preview.size - 30}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = conflicts.isEmpty(), onClick = { vm.batchRename(items, preview); onDismiss() }) { Text("Переименовать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

object BatchRename {
    fun plan(items: List<FileItem>, pattern: String, find: String, replace: String, regex: Boolean, start: Int): List<Pair<FileItem, String>> {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
        val rx = if (regex && find.isNotEmpty()) runCatching { Regex(find) }.getOrNull() else null
        val numberRx = Regex("\\{n(?::(\\d+))?\\}")
        return items.mapIndexed { i, item ->
            val n = start + i
            var name = pattern
                .replace("{name}", item.baseName)
                .replace("{ext}", item.extension)
                .replace("{date}", fmt.format(java.util.Date(item.lastModified)))
                .replace("{parent}", java.io.File(item.path).parentFile?.name ?: "")
            name = numberRx.replace(name) { m ->
                val width = m.groupValues[1].toIntOrNull() ?: 0
                n.toString().padStart(width, '0')
            }
            if (find.isNotEmpty()) name = if (rx != null) rx.replace(name, replace) else name.replace(find, replace)
            if (!pattern.contains("{ext}") && item.extension.isNotEmpty() && !item.isDirectory) name = "$name.${item.extension}"
            item to name.trim()
        }
    }
}

@Composable
fun ConfirmSimple(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) =
    ConfirmDialog(title, text, null, "OK", onConfirm, onDismiss)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FolderColorDialog(item: FileItem, current: Int?, vm: BrowserViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { FileIcon(item, size = 48.dp) },
        title = { Text("Цвет папки «${item.name}»") },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                client.wexmanager.ui.components.FileGlyphs.palette.forEach { c ->
                    val argb = c.toArgb()
                    val selected = current == argb
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(40.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(c)
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, androidx.compose.foundation.shape.CircleShape)
                            .clickable { vm.setFolderColor(item.path, argb); onDismiss() },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = if (current != null) ({ TextButton(onClick = { vm.setFolderColor(item.path, null); onDismiss() }) { Text("Сбросить") } }) else null,
    )
}

/** Запрос пароля архива: частые пароли — чипами, «запомнить» сохраняет новый. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ArchivePasswordDialog(
    title: String,
    wrong: Boolean,
    saved: List<String>,
    onSubmit: (password: String, remember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var remember by remember { mutableStateOf(true) }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(if (wrong) "Неверный пароль" else "Нужен пароль") },
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Пароль архива") },
                    isError = wrong && text.isEmpty(),
                    visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "Показать пароль")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (text.isNotEmpty()) onSubmit(text, remember) }),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (saved.isNotEmpty()) {
                    SectionLabel("Частые пароли")
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        saved.forEach { pw ->
                            androidx.compose.material3.SuggestionChip(
                                onClick = { onSubmit(pw, false) },
                                label = { Text(if (pw.length <= 2) pw else pw.take(1) + "•".repeat((pw.length - 2).coerceAtMost(6)) + pw.last()) },
                            )
                        }
                    }
                }
                CheckRow("Запомнить пароль", remember, { remember = it })
            }
        },
        confirmButton = { Button(enabled = text.isNotEmpty(), onClick = { onSubmit(text, remember) }) { Text("Открыть") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
