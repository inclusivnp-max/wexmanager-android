package client.wexmanager.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import kotlinx.coroutines.flow.update
import client.wexmanager.appContainer
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.icons.IconPack
import client.wexmanager.core.settings.AppJson
import client.wexmanager.core.settings.IconOverride
import client.wexmanager.core.settings.IconShapeOption
import client.wexmanager.core.settings.IconStyle
import client.wexmanager.feature.browser.AppBottomSheet
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.FileGlyphs
import client.wexmanager.ui.components.FileIcon
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.components.polygon
import client.wexmanager.ui.theme.LocalAppSettings
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun sample(name: String, dir: Boolean = false) =
    FileItem("/sample/$name", name, dir, 0, 0, archivePath = "sample")

private val previewItems = listOf(
    sample("Documents", true), sample("Download", true), sample("app.apk"), sample("photo.png"), sample("clip.mp4"),
    sample("song.mp3"), sample("backup.zip"), sample("Main.kt"), sample("book.pdf"), sample("data.json"), sample("model.glb"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val s = LocalAppSettings.current
    val packs by container.iconPacks.packs.collectAsStateWithLifecycle()
    val userData by container.userData.data.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var packQuery by remember { mutableStateOf("") }
    var onlyCustom by remember { mutableStateOf(false) }
    var editExt by remember { mutableStateOf<String?>(null) }
    var downloadOpen by remember { mutableStateOf(false) }
    var addExtOpen by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { container.iconPacks.importFromUri(uri) }
                .onSuccess { Toast.makeText(context, "Иконпак «${it.manifest.name}» установлен: ${it.icons.size} иконок", Toast.LENGTH_LONG).show() }
                .onFailure { Toast.makeText(context, "Ошибка: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }

    val allExts = remember(userData.iconOverrides) {
        (listOf("folder") + FileKind.knownExtensions.keys.sorted() + userData.iconOverrides.keys).distinct()
    }
    val filtered = allExts.filter { ext ->
        (!onlyCustom || ext in userData.iconOverrides) && (query.isBlank() || ext.contains(query.trim().removePrefix("."), ignoreCase = true))
    }
    val packNeedle = packQuery.trim()
    fun packHit(vararg fields: String) =
        packNeedle.isEmpty() || fields.any { it.contains(packNeedle, ignoreCase = true) }
    val catalogShown = client.wexmanager.core.icons.IconCatalog.packs.filter {
        packHit(it.name, it.author, it.description, it.id)
    }
    val installedShown = packs.filter {
        packHit(it.manifest.name, it.manifest.author, it.manifest.description, it.id)
    }

    SettingsScaffold("Иконки файлов", onBack) {
        item(key = "preview") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                previewItems.forEach { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FileIcon(item, size = 52.dp, showThumbnail = false)
                        Spacer(Modifier.height(4.dp))
                        Text(if (item.isDirectory) item.name else ".${item.extension}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        settingsGroup("Стиль") {
            custom { shapes ->
                SegmentedListItem(onClick = {}, shapes = shapes, leadingContent = { Icon(Icons.Rounded.Style, null) }, supportingContent = {
                    ConnectedChoice(
                        options = IconStyle.entries,
                        selected = s.iconStyle,
                        onSelect = { v -> container.settings.update { it.copy(iconStyle = v) } },
                        label = { when (it) { IconStyle.Tonal -> "Тон"; IconStyle.Filled -> "Заливка"; IconStyle.Outlined -> "Контур"; IconStyle.Mono -> "Моно" } },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }) { Text("Окраска") }
            }
            custom { shapes ->
                SegmentedListItem(onClick = {}, shapes = shapes, supportingContent = {
                    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconShapeOption.entries.forEach { opt ->
                            val selected = s.iconShape == opt
                            val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, label = "shape")
                            val shape = remember(opt) {
                                when (opt) {
                                    IconShapeOption.Rounded -> RoundedCornerShape(12.dp)
                                    IconShapeOption.Squircle -> RoundedCornerShape(30)
                                    else -> PolygonShape(opt.polygon())
                                }
                            }
                            Box(
                                Modifier.size(48.dp).clip(shape).background(bg)
                                    .clickable { container.settings.update { it.copy(iconShape = opt) } },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }) { Text("Форма") }
            }
            switch("Цветные папки", s.coloredFolderIcons, Icons.Rounded.Folder, "Иначе — цвет акцента темы") { v -> container.settings.update { it.copy(coloredFolderIcons = v) } }
        }
        item(key = "pack-filter") {
            OutlinedTextField(
                value = packQuery,
                onValueChange = { packQuery = it },
                singleLine = true,
                placeholder = { Text("Фильтр") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
        }
        if (catalogShown.isNotEmpty()) {
            settingsGroup("Каталог иконпаков") {
                catalogShown.forEach { entry ->
                    custom { shapes -> CatalogRow(entry, installed = packs.firstOrNull { it.id == entry.id }, active = s.activeIconPack == entry.id, shapes = shapes) }
                }
            }
        }
        settingsGroup("Иконпаки") {
            custom { shapes ->
                SegmentedListItem(
                    selected = s.activeIconPack == null,
                    onClick = { container.settings.update { it.copy(activeIconPack = null) } },
                    shapes = shapes,
                    leadingContent = { Icon(Icons.Rounded.Palette, null) },
                    supportingContent = { Text("Встроенные векторные иконки") },
                    trailingContent = { if (s.activeIconPack == null) Icon(Icons.Rounded.Check, null) },
                ) { Text("Стандартные") }
            }
            installedShown.forEach { pack ->
                custom { shapes -> PackRow(pack, selected = s.activeIconPack == pack.id, shapes = shapes) }
            }
            custom { shapes ->
                SegmentedListItem(onClick = {}, shapes = shapes, supportingContent = {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        FilledTonalButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }) {
                            Icon(Icons.Rounded.FileUpload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Из файла")
                        }
                        FilledTonalButton(onClick = { downloadOpen = true }) {
                            Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("По ссылке")
                        }
                        OutlinedButton(onClick = {
                            scope.launch {
                                val path = exportTemplate(container.storage.primaryPath)
                                Toast.makeText(context, if (path != null) "Шаблон сохранён: $path" else "Не удалось сохранить шаблон", Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(Icons.Rounded.FolderZip, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Шаблон")
                        }
                    }
                }) { Text("Добавить иконпак") }
            }
        }
        item(key = "types-header") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    "Иконки по типам (${filtered.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Найти расширение") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = onlyCustom, onClick = { onlyCustom = !onlyCustom }, label = { Text("Изменённые (${userData.iconOverrides.size})") })
                    FilterChip(selected = false, onClick = { addExtOpen = true }, label = { Text("Своё расширение") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)) })
                    if (userData.iconOverrides.isNotEmpty()) {
                        TextButton(onClick = { container.userData.clearIconOverrides() }) { Text("Сбросить все") }
                    }
                }
            }
        }
        val chunked = filtered.chunked(4)
        chunked.forEachIndexed { rowIndex, row ->
            item(key = "row:${row.first()}") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { ext ->
                        ExtCell(ext, custom = ext in userData.iconOverrides, modifier = Modifier.weight(1f)) { editExt = ext }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }

    editExt?.let { ext ->
        IconEditorSheet(ext, userData.iconOverrides[ext], onDismiss = { editExt = null })
    }
    if (downloadOpen) DownloadPackDialog(onDismiss = { downloadOpen = false })
    if (addExtOpen) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addExtOpen = false },
            title = { Text("Своё расширение") },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = { text = it.lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(12) },
                    singleLine = true, prefix = { Text(".") }, label = { Text("Расширение") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), shape = RoundedCornerShape(16.dp),
                )
            },
            confirmButton = { Button(enabled = text.isNotEmpty(), onClick = { addExtOpen = false; editExt = text }) { Text("Далее") } },
            dismissButton = { TextButton(onClick = { addExtOpen = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun ExtCell(ext: String, custom: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val item = remember(ext) { if (ext == "folder") sample("Папка", true) else sample("file.$ext") }
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (custom) scheme.secondaryContainer else scheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileIcon(item, size = 40.dp, showThumbnail = false)
        Spacer(Modifier.height(4.dp))
        Text(if (ext == "folder") "папка" else ".$ext", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun PackRow(pack: IconPack, selected: Boolean, shapes: androidx.compose.material3.ListItemShapes) {
    val context = LocalContext.current
    val container = context.appContainer
    var confirmDelete by remember { mutableStateOf(false) }
    SegmentedListItem(
        selected = selected,
        onClick = { container.settings.update { it.copy(activeIconPack = pack.id) } },
        shapes = shapes,
        leadingContent = { Icon(Icons.Rounded.Inventory2, null) },
        supportingContent = {
            Column {
                Text("${pack.icons.size} иконок" + if (pack.manifest.author.isNotBlank()) " • ${pack.manifest.author}" else "")
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pack.icons.values.take(6).forEach { f -> AsyncImage(model = f, contentDescription = null, modifier = Modifier.size(24.dp)) }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected) Icon(Icons.Rounded.Check, null)
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Удалить иконпак") }
            }
        },
    ) { Text(pack.manifest.name) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${pack.manifest.name}»?") },
            confirmButton = {
                Button(onClick = {
                    confirmDelete = false
                    if (selected) container.settings.update { it.copy(activeIconPack = null) }
                    container.iconPacks.delete(pack.id)
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconEditorSheet(ext: String, current: IconOverride?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val item = remember(ext) { if (ext == "folder") sample("Папка", true) else sample("file.$ext") }
    val kind = item.kind
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching { container.iconPacks.saveCustomIcon(uri, ext) }
                .onSuccess { path -> container.userData.setIconOverride(ext, IconOverride(imagePath = path)) }
                .onFailure { Toast.makeText(context, "Ошибка: ${it.message}", Toast.LENGTH_SHORT).show() }
        }
    }
    fun set(o: IconOverride?) = container.userData.setIconOverride(ext, o)

    AppBottomSheet(onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileIcon(item, size = 72.dp, showThumbnail = false)
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (ext == "folder") "Папки" else ".$ext", style = MaterialTheme.typography.headlineSmall)
                    Text(kind.label, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                }
            }
            Text("Цвет", style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                (listOf(FileGlyphs.colorFor(kind, ext)) + FileGlyphs.palette).distinct().forEach { c ->
                    val selected = current?.color == c.toArgb()
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(c)
                            .border(if (selected) 3.dp else 0.dp, scheme.onSurface, CircleShape)
                            .clickable { set((current ?: IconOverride()).copy(color = c.toArgb(), imagePath = null)) },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
            Text("Значок", style = MaterialTheme.typography.labelLarge, color = scheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FileGlyphs.all.forEach { (key, vector) ->
                    val selected = current?.glyph == key
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (selected) scheme.primary else scheme.surfaceContainerHigh)
                            .clickable { set((current ?: IconOverride()).copy(glyph = key, imagePath = null)) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(vector, key, tint = if (selected) scheme.onPrimary else scheme.onSurfaceVariant) }
                }
            }
            Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { pickImage.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Rounded.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Своя картинка")
                }
                OutlinedButton(onClick = { set(null) }, enabled = current != null) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Сбросить")
                }
            }
        }
    }
}

@Composable
private fun DownloadPackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://") }
    var progress by remember { mutableFloatStateOf(-1f) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (progress < 0) onDismiss() },
        icon = { Icon(Icons.Rounded.CloudDownload, null) },
        title = { Text("Скачать иконпак") },
        text = {
            Column {
                Text("Ссылка на zip-архив с pack.json и картинками (png/webp/svg).", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it; error = null }, singleLine = true, label = { Text("URL") },
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (progress >= 0) LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        },
        confirmButton = {
            Button(enabled = progress < 0 && url.length > 10, onClick = {
                progress = 0f
                scope.launch {
                    runCatching { container.iconPacks.downloadAndImport(url.trim()) { progress = it } }
                        .onSuccess {
                            Toast.makeText(context, "Установлен «${it.manifest.name}»", Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                        .onFailure { error = it.message ?: "Ошибка загрузки"; progress = -1f }
                }
            }) { Text("Скачать") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = progress < 0) { Text("Отмена") } },
    )
}

/** Шаблон иконпака: pack.json со всеми известными расширениями + инструкция. */
private suspend fun exportTemplate(root: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val dir = File(root, "WexManager").apply { mkdirs() }
        val target = File(dir, "IconPackTemplate.zip")
        val icons = linkedMapOf("folder" to "folder.png", "*" to "file.png")
        FileKind.knownExtensions.keys.sorted().forEach { icons[it] = "$it.png" }
        val manifest = client.wexmanager.core.icons.IconPackManifest(
            name = "Мой иконпак",
            author = "",
            version = "1.0",
            description = "Положите картинки рядом с pack.json. Лишние строки из icons можно удалить.",
            icons = icons,
        )
        ZipOutputStream(target.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("pack.json"))
            zos.write(AppJson.encodeToString(client.wexmanager.core.icons.IconPackManifest.serializer(), manifest).toByteArray())
            zos.closeEntry()
            zos.putNextEntry(ZipEntry("README.txt"))
            zos.write(
                """
                Иконпак WexManager
                ==================
                1. Отредактируйте pack.json: name, author, icons (расширение → файл картинки).
                   "folder" — иконка папок, "*" — иконка по умолчанию для файлов.
                2. Добавьте картинки (png, webp, jpg, svg, gif) рядом с pack.json.
                3. Упакуйте всё в zip и импортируйте: Настройки → Иконки файлов → Иконпаки → Из файла.
                Если pack.json не указывает icons, имя файла без расширения считается расширением (pdf.png → .pdf).
                """.trimIndent().toByteArray(),
            )
            zos.closeEntry()
        }
        target.path
    }.getOrNull()
}


/** Пак из каталога: «Скачать» (с прогрессом), затем «Применить» / «Обновить». */
@Composable
private fun CatalogRow(
    entry: client.wexmanager.core.icons.CatalogPack,
    installed: client.wexmanager.core.icons.IconPack?,
    active: Boolean,
    shapes: androidx.compose.material3.ListItemShapes,
) {
    val context = LocalContext.current.applicationContext // корутина переживает экран — не держим Activity
    val container = context.appContainer
    // Загрузка живёт в области приложения: уход с экрана её не обрывает.
    val progress by catalogProgress.collectAsStateWithLifecycle()
    val p = progress[entry.id]
    fun install(apply: Boolean) {
        // Атомарная проверка: два быстрых нажатия не запустят две установки в одну временную папку.
        var started = false
        catalogProgress.update { m -> if (entry.id in m) m else m.plus(entry.id to 0f).also { started = true } }
        if (!started) return
        container.appScope.launch {
            val r = runCatching { container.iconPacks.installFromCatalog(entry) { v -> catalogProgress.update { m -> m + (entry.id to v) } } }
            catalogProgress.update { it - entry.id }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                r.onSuccess { pack ->
                    if (apply) container.settings.update { it.copy(activeIconPack = pack.id) }
                    Toast.makeText(context, "«${entry.name}»: ${pack.icons.size} значков", Toast.LENGTH_SHORT).show()
                }.onFailure { e ->
                    Toast.makeText(context, "Не удалось скачать: ${e.message ?: "нет сети"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    SegmentedListItem(
        selected = active,
        onClick = { if (installed != null) container.settings.update { it.copy(activeIconPack = entry.id) } else install(apply = true) },
        shapes = shapes,
        leadingContent = { Icon(Icons.Rounded.CloudDownload, null) },
        supportingContent = {
            Column {
                Text("${entry.description} · ${entry.author} · ${entry.license}")
                if (p != null) {
                    LinearWavyProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        if (installed == null) {
                            FilledTonalButton(onClick = { install(apply = true) }) { Text("Скачать и применить") }
                        } else {
                            if (!active) FilledTonalButton(onClick = { container.settings.update { it.copy(activeIconPack = entry.id) } }) { Text("Применить") }
                            OutlinedButton(onClick = { install(apply = false) }) { Text("Обновить") }
                        }
                    }
                }
            }
        },
        trailingContent = { if (active) Icon(Icons.Rounded.Check, null) },
    ) { Text(entry.name) }
}

/** Прогресс загрузки паков каталога (id → 0..1); переживает уход с экрана настроек. */
private val catalogProgress = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Float>>(emptyMap())
