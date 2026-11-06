package client.wexmanager.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import client.wexmanager.core.elf.Elf
import client.wexmanager.core.elf.ElfInfo
import client.wexmanager.core.util.Format
import client.wexmanager.feature.apk.ShapeMessage
import client.wexmanager.feature.browser.copyToSystemClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Сведения о ELF (.so, исполняемые): архитектура, зависимости, JNI, символы, сегменты и проверка страниц 16 КБ. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ElfScreen(path: String, onBack: () -> Unit, onHex: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var info by remember { mutableStateOf<Result<ElfInfo>?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(path) { info = runCatching { withContext(Dispatchers.IO) { Elf.parse(File(path)) } } }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    Column {
                        Text(File(path).name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text("ELF", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = onHex, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Memory, "HEX-просмотр") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface),
            )
        },
        containerColor = scheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val r = info
            when {
                r == null -> LoadingIndicator(Modifier.size(64.dp).align(Alignment.Center))
                r.isFailure -> ShapeMessage(Icons.Rounded.Error, "Не удалось разобрать ELF", r.exceptionOrNull()?.message, error = true)
                else -> {
                    val e = r.getOrThrow()
                    Column(Modifier.fillMaxSize()) {
                        PrimaryTabRow(selectedTabIndex = tab, containerColor = scheme.surface) {
                            listOf("Обзор", "Символы · ${e.symbols.size}", "Секции").forEachIndexed { i, t ->
                                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, maxLines = 1) })
                            }
                        }
                        when (tab) {
                            0 -> Overview(e) { copyToSystemClipboard(context, it) }
                            1 -> Symbols(e) { copyToSystemClipboard(context, it) }
                            else -> Sections(e)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Overview(e: ElfInfo, copy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        item {
            // Страницы 16 КБ: сегменты LOAD должны быть выровнены не меньше чем на 16384 байт.
            val align = e.loadAlignment
            val ok = align != null && align >= 16384
            Surface(color = if (ok) scheme.primaryContainer else scheme.errorContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null, tint = if (ok) scheme.onPrimaryContainer else scheme.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (ok) "Совместим со страницами 16 КБ" else "Не совместим со страницами 16 КБ", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (align == null) "Нет сегментов LOAD" else "Выравнивание LOAD: ${align} (0x${align.toString(16)})" + if (!ok) " — на Android 15+ с такими страницами библиотека не загрузится" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        val rows = listOfNotNull(
            "Архитектура" to e.machine,
            "Тип" to e.type,
            "Разрядность" to (if (e.is64) "64 бита" else "32 бита") + if (e.littleEndian) ", little-endian" else ", big-endian",
            e.soname?.let { "SONAME" to it },
            e.interpreter?.let { "Загрузчик" to it },
            "Точка входа" to "0x" + e.entry.toString(16),
            e.runpath?.let { "RUNPATH" to it },
            e.buildId?.let { "Build ID" to it },
            "Отладочные символы" to if (e.stripped) "вычищены (strip)" else "есть (.symtab)",
        )
        rowsSection(rows, copy)
        if (e.needed.isNotEmpty()) {
            header("Зависимости · ${e.needed.size}")
            rowsSection(e.needed.map { it to "" }, copy)
        }
        val jni = e.jniFunctions
        if (jni.isNotEmpty()) {
            header("JNI · ${jni.size}")
            rowsSection(jni.map { (if (it.name.startsWith("Java_")) jniName(it.name) else it.name) to it.name }, copy, mono = true)
        }
        header("Сегменты")
        items(e.segments) { s ->
            Text(
                "%-12s %s  off 0x%x  vaddr 0x%x  размер %s  align 0x%x".format(s.type, s.flags, s.offset, s.vaddr, Format.size(s.fileSize), s.align),
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                color = if (s.type == "LOAD" && s.align < 16384) scheme.error else scheme.onSurface,
                modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp),
            )
        }
    }
}

/** Java_com_a_B_method → com.a.B.method (с раскодированием _1 → _, _0xxxx → символ). */
private fun jniName(sym: String): String {
    val body = sym.removePrefix("Java_").substringBefore("__")
    val sb = StringBuilder()
    var i = 0
    while (i < body.length) {
        val c = body[i]
        if (c == '_' && i + 1 < body.length) {
            when (body[i + 1]) {
                '1' -> { sb.append('_'); i += 2; continue }
                '2' -> { sb.append(';'); i += 2; continue }
                '3' -> { sb.append('['); i += 2; continue }
                '0' -> if (i + 6 <= body.length) {
                    body.substring(i + 2, i + 6).toIntOrNull(16)?.let { sb.append(it.toChar()); i += 6; continue }
                }
            }
            sb.append('.')
        } else sb.append(c)
        i++
    }
    return sb.toString()
}

private fun LazyListScope.header(text: String) {
    item(key = "h:$text") {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LazyListScope.rowsSection(rows: List<Pair<String, String>>, copy: (String) -> Unit, mono: Boolean = false) {
    items(rows.size) { i ->
        val (k, v) = rows[i]
        SegmentedListItem(
            onClick = { copy(v.ifEmpty { k }) },
            shapes = ListItemDefaults.segmentedShapes(i, rows.size),
            supportingContent = if (v.isNotEmpty()) { { Text(v, fontFamily = FontFamily.Monospace, maxLines = 3, overflow = TextOverflow.Ellipsis) } } else null,
        ) { Text(k, fontFamily = if (mono || v.isEmpty()) FontFamily.Monospace else null, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun Symbols(e: ElfInfo, copy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(0) } // 0 — все, 1 — экспорт, 2 — импорт
    val shown = remember(e, query, mode) {
        e.symbols.filter { s ->
            (mode == 0 || (mode == 1) == s.defined) && (query.isBlank() || s.name.contains(query.trim(), ignoreCase = true))
        }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Фильтр") },
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Все", "Экспорт", "Импорт").forEachIndexed { i, t -> FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(t) }) }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp)) {
            items(shown.take(20_000), key = { "${it.name}:${it.value}:${it.defined}" }) { s ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(28.dp).background(if (s.defined) scheme.primaryContainer else scheme.surfaceContainerHighest, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (s.type == "FUNC") "ƒ" else if (s.type == "OBJECT") "o" else "·", color = if (s.defined) scheme.onPrimaryContainer else scheme.onSurfaceVariant) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (s.defined) "0x" + s.value.toString(16) + " · " + s.size + " Б" else "импорт") + " · " + s.bind.lowercase(),
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { copy(s.name) }) { Text("⧉", color = scheme.outline) }
                }
            }
        }
    }
}

@Composable
private fun Sections(e: ElfInfo) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        items(e.sections) { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(s.name.ifEmpty { "—" }, Modifier.weight(1f), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${s.type} ${s.flags}", Modifier.width(150.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.size(s.size), Modifier.width(80.dp), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
