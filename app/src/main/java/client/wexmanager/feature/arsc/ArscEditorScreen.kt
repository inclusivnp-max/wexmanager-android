package client.wexmanager.feature.arsc

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.apk.ResValue
import client.wexmanager.feature.apk.ShapeMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArscEditorScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: ArscEditorViewModel = viewModel(key = "arsc:$path") { ArscEditorViewModel(app, path) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme

    var query by rememberSaveable { mutableStateOf("") }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ResRow?>(null) }
    var translating by rememberSaveable { mutableStateOf<String?>(null) }
    var pickLocale by remember { mutableStateOf(false) }
    var unsaved by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            when (ev) {
                is ArscEvent.Message -> scope.launch {
                    val r = snackbar.showSnackbar(ev.text, ev.action, withDismissAction = ev.action != null,
                        duration = if (ev.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) ev.onAction?.invoke()
                }
            }
        }
    }
    val leave: () -> Unit = { if (state.dirty) unsaved = true else onBack() }
    BackHandler(translating != null) { translating = null }
    BackHandler(translating == null && searchOpen) { searchOpen = false; query = "" }
    BackHandler(translating == null && !searchOpen && state.dirty) { unsaved = true }

    translating?.let { locale ->
        TranslationMode(vm, locale, onClose = { translating = null })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = leave, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            placeholder = { Text("Фильтр") },
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = scheme.surfaceContainerHighest, unfocusedContainerColor = scheme.surfaceContainerHighest,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        )
                    } else Column {
                        Text("Ресурсы", maxLines = 1)
                        Text(state.packageName, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                actions = {
                    if (!searchOpen) IconButton(onClick = { searchOpen = true }, enabled = !state.loading, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Search, "Поиск") }
                    IconButton(onClick = { pickLocale = true }, enabled = !state.loading && state.error == null, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Translate, "Перевод") }
                    if (state.saving) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(28.dp)) }
                    else if (state.dirty) FilledTonalIconButton(onClick = { vm.save() }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Save, "Сохранить") }
                    if (state.isApk && !state.dirty && !state.saving) IconButton(onClick = vm::sign, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Key, "Подписать") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = scheme.surface,
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
            state.error != null -> ShapeMessage(Icons.Rounded.Error, "Не удалось разобрать ресурсы", state.error, Modifier.padding(padding), error = true) {
                FilledTonalButton(onClick = vm::load, shapes = ButtonDefaults.shapes()) { Text("Повторить") }
            }
            else -> Column(Modifier.fillMaxSize().padding(padding)) {
                // Ряды чипов сами доводят выбранный чип до видимой области (тип «string» по умолчанию обычно далеко справа).
                val typesRow = androidx.compose.foundation.lazy.rememberLazyListState()
                val typeIndex = state.types.indexOfFirst { it.first == state.type }
                LaunchedEffect(typeIndex) { if (typeIndex > 0) typesRow.animateScrollToItem((typeIndex - 1).coerceAtLeast(0)) }
                androidx.compose.foundation.lazy.LazyRow(
                    state = typesRow,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.types.size, key = { state.types[it].first }) { i ->
                        val (name, count) = state.types[i]
                        FilterChip(selected = name == state.type, onClick = { vm.select(name, 0) }, label = { Text("$name · $count") })
                    }
                }
                if (state.configs.size > 1) {
                    val configsRow = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(state.type, state.config) { configsRow.animateScrollToItem((state.config - 1).coerceAtLeast(0)) }
                    androidx.compose.foundation.lazy.LazyRow(
                        state = configsRow,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.configs.size, key = { state.configs[it].position }) { i ->
                            val c = state.configs[i]
                            FilterChip(
                                selected = c.position == state.config,
                                onClick = { vm.select(state.type, c.position) },
                                label = { Text(c.qualifier.ifEmpty { "по умолчанию" }, fontFamily = FontFamily.Monospace) },
                            )
                        }
                    }
                }
                val rows = remember(state.rows, query) {
                    if (query.isBlank()) state.rows else state.rows.filter { it.name.contains(query, true) || it.text.contains(query, true) }
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    itemsIndexed(rows, key = { _, r -> "${state.version}:${r.index}" }) { i, r ->
                        SegmentedListItem(
                            onClick = { editing = r },
                            shapes = ListItemDefaults.segmentedShapes(i, rows.size),
                            leadingContent = if (r.type in ResValue.TYPE_INT_COLOR_ARGB8..ResValue.TYPE_INT_COLOR_RGB4) {
                                { ColorSwatch(r.text) }
                            } else null,
                            supportingContent = { Text(r.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                            trailingContent = { Text("0x" + Integer.toHexString(r.id), style = MaterialTheme.typography.labelSmall, color = scheme.outline, fontFamily = FontFamily.Monospace) },
                        ) { Text(r.name, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }

    editing?.let { r ->
        if (r.complex) BagDialog(r) { editing = null }
        else EditValueDialog(r, onDismiss = { editing = null }) { text -> if (vm.setValue(r.index, text)) editing = null }
    }
    if (pickLocale) LocaleDialog(state.locales, onDismiss = { pickLocale = false }) { pickLocale = false; translating = it }
    if (unsaved) AlertDialog(
        onDismissRequest = { unsaved = false },
        icon = { Icon(Icons.Rounded.Save, null) },
        title = { Text("Сохранить изменения?") },
        text = { Text("Правки ресурсов ещё не записаны.") },
        confirmButton = { Button(onClick = { unsaved = false; vm.save(onBack) }) { Text("Сохранить") } },
        dismissButton = {
            Row {
                TextButton(onClick = { unsaved = false; onBack() }) { Text("Не сохранять") }
                TextButton(onClick = { unsaved = false }) { Text("Отмена") }
            }
        },
    )
}

@Composable
private fun ColorSwatch(text: String) {
    val color = runCatching {
        val h = text.removePrefix("#")
        val argb = when (h.length) { 6 -> 0xFF000000 or h.toLong(16); else -> h.toLong(16) }
        Color(argb.toInt())
    }.getOrDefault(Color.Transparent)
    Box(Modifier.size(36.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}

@Composable
private fun EditValueDialog(r: ResRow, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(r.text) }
    val hint = when (r.type) {
        ResValue.TYPE_STRING -> "Текст строки"
        ResValue.TYPE_DIMENSION -> "Например 16dp, 12sp, 1px"
        ResValue.TYPE_FRACTION -> "Например 50%"
        in ResValue.TYPE_INT_COLOR_ARGB8..ResValue.TYPE_INT_COLOR_RGB4 -> "#RRGGBB или #AARRGGBB"
        ResValue.TYPE_INT_DEC, ResValue.TYPE_INT_HEX -> "Целое: 42 или 0x2A"
        ResValue.TYPE_FLOAT -> "Число: 1.5"
        ResValue.TYPE_REFERENCE, ResValue.TYPE_DYNAMIC_REFERENCE -> "Ссылка: @0x7f010001"
        else -> "Значение"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, null) },
        title = { Text(r.name, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ResValue(r.type, 0).typeName, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (r.type == ResValue.TYPE_INT_BOOLEAN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Значение", Modifier.weight(1f))
                        Switch(checked = text == "true", onCheckedChange = { text = it.toString() })
                    }
                } else {
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text(hint) },
                        singleLine = r.type != ResValue.TYPE_STRING,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (r.type == ResValue.TYPE_STRING) FontFamily.Default else FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    )
                    if (r.type in ResValue.TYPE_INT_COLOR_ARGB8..ResValue.TYPE_INT_COLOR_RGB4) ColorSwatch(text)
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(text) }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun BagDialog(r: ResRow, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(r.name, fontFamily = FontFamily.Monospace) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(r.bag) { _, (name, value) ->
                    Column {
                        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, fontFamily = FontFamily.Monospace)
                        Text(value, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun LocaleDialog(existing: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Translate, null) },
        title = { Text("Перевод строк") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing.isNotEmpty()) {
                    Text("Языки в APK", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        existing.forEach { l -> FilterChip(selected = false, onClick = { onPick(l) }, label = { Text(l, fontFamily = FontFamily.Monospace) }) }
                    }
                }
                OutlinedTextField(
                    value = code, onValueChange = { code = it.trim() }, singleLine = true,
                    label = { Text("Новый язык") }, placeholder = { Text("ru, uk, pt-rBR, b+sr+Latn") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { Button(onClick = { onPick(code) }, enabled = code.length >= 2) { Text("Перевести") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Режим перевода (как в MT): исходная строка и поле перевода, фильтр непереведённых. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TranslationMode(vm: ArscEditorViewModel, locale: String, onClose: () -> Unit) {
    val rows = remember(locale) { vm.translationRows(locale) }
    val edits = remember(locale) { mutableStateMapOf<Int, String>() }
    var onlyMissing by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val shown = remember(rows, onlyMissing) { if (onlyMissing) rows.filter { it.target == null } else rows }
    val translated = rows.count { it.target != null || !edits[it.index].isNullOrEmpty() }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Закрыть") } },
                title = {
                    Column {
                        Text("Перевод: $locale")
                        Text("$translated из ${rows.size}", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = {
                            vm.applyTranslation(locale, edits.filterValues { it.isNotEmpty() })
                            onClose()
                        },
                        enabled = edits.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                    ) { Icon(Icons.Rounded.Check, "Применить") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
        containerColor = scheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                FilterChip(selected = onlyMissing, onClick = { onlyMissing = !onlyMissing }, label = { Text("Только непереведённые") })
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(shown, key = { _, r -> r.index }) { _, r ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surfaceContainer).padding(14.dp)) {
                        Text(r.name, style = MaterialTheme.typography.labelMedium, color = scheme.primary, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(4.dp))
                        Text(r.source, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = edits[r.index] ?: r.target.orEmpty(),
                            onValueChange = { edits[r.index] = it },
                            placeholder = { Text("Перевод") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
