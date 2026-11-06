package client.wexmanager.feature.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Сведения из таблицы `name` шрифта TrueType/OpenType. */
data class FontNames(val family: String?, val full: String?, val style: String?, val version: String?, val copyright: String?)

object FontInfo {
    /** Смещение таблицы [tag] (для TTC — в первом шрифте коллекции) или -1. */
    private fun tableOffset(f: RandomAccessFile, tag: Int): Long {
        f.seek(0)
        if (f.readInt() == 0x74746366) { // 'ttcf'
            f.readInt()
            if (f.readInt() <= 0) return -1
            f.seek(f.readInt().toLong() and 0xFFFFFFFFL)
            f.readInt()
        }
        val numTables = f.readUnsignedShort()
        f.skipBytes(6)
        repeat(numTables) {
            val t = f.readInt()
            f.readInt()
            val off = f.readInt().toLong() and 0xFFFFFFFFL
            f.readInt()
            if (t == tag) return off
        }
        return -1
    }

    /**
     * Какие символы есть в самом шрифте — по таблице `cmap` (форматы 4 и 12).
     * Paint.hasGlyph тут не годится: он учитывает системные шрифты-заменители.
     */
    fun coverage(file: File): ((Int) -> Boolean)? = runCatching {
        RandomAccessFile(file, "r").use { f ->
            val cmap = tableOffset(f, 0x636D6170) // 'cmap'
            if (cmap < 0) return null
            f.seek(cmap + 2)
            val n = f.readUnsignedShort()
            var best = -1L
            var bestScore = 0
            repeat(n) {
                val platform = f.readUnsignedShort()
                val encoding = f.readUnsignedShort()
                val off = cmap + (f.readInt().toLong() and 0xFFFFFFFFL)
                val score = when {
                    platform == 3 && encoding == 10 || platform == 0 && encoding >= 4 -> 3
                    platform == 3 && encoding == 1 || platform == 0 -> 2
                    else -> 0
                }
                if (score > bestScore) { bestScore = score; best = off }
            }
            if (best < 0) return null
            f.seek(best)
            when (f.readUnsignedShort()) {
                4 -> {
                    // Подтаблицу целиком в память: проверка символа — бинарный поиск без ввода-вывода.
                    val length = f.readUnsignedShort()
                    val buf = java.nio.ByteBuffer.wrap(ByteArray(length).also { f.seek(best); f.readFully(it) })
                    val seg = buf.getShort(6).toInt().and(0xFFFF) / 2
                    val endAt = 14
                    val startAt = endAt + seg * 2 + 2
                    val deltaAt = startAt + seg * 2
                    val rangeAt = deltaAt + seg * 2
                    fun u16(pos: Int) = buf.getShort(pos).toInt() and 0xFFFF
                    return@use { cp: Int ->
                        if (cp !in 0..0xFFFE) false else {
                            var lo = 0
                            var hi = seg - 1
                            while (lo < hi) { // первый сегмент с endCode >= cp
                                val mid = (lo + hi) ushr 1
                                if (u16(endAt + mid * 2) < cp) lo = mid + 1 else hi = mid
                            }
                            val i = lo
                            val startCode = u16(startAt + i * 2)
                            if (seg == 0 || cp < startCode || cp > u16(endAt + i * 2)) false else {
                                val delta = buf.getShort(deltaAt + i * 2).toInt()
                                val range = u16(rangeAt + i * 2)
                                val glyph = if (range == 0) (cp + delta) and 0xFFFF else {
                                    val pos = rangeAt + i * 2 + range + (cp - startCode) * 2
                                    if (pos + 1 >= length) 0 else u16(pos).let { g -> if (g == 0) 0 else (g + delta) and 0xFFFF }
                                }
                                glyph != 0
                            }
                        }
                    }
                }
                12 -> {
                    f.readUnsignedShort()
                    f.readInt()
                    f.readInt()
                    val groups = (f.readInt().toLong() and 0xFFFFFFFFL).coerceAtMost(100_000).toInt()
                    val starts = LongArray(groups)
                    val ends = LongArray(groups)
                    for (i in 0 until groups) {
                        starts[i] = f.readInt().toLong() and 0xFFFFFFFFL
                        ends[i] = f.readInt().toLong() and 0xFFFFFFFFL
                        f.readInt()
                    }
                    return@use { cp: Int -> (0 until groups).any { cp >= starts[it] && cp <= ends[it] } }
                }
                else -> null
            }
        }
    }.getOrNull()

    /** Читает таблицу `name` без сторонних библиотек (TTF/OTF; для TTC — первый шрифт коллекции). */
    fun read(file: File): FontNames? = runCatching {
        RandomAccessFile(file, "r").use { f ->
            var base = 0L
            val tag = f.readInt()
            if (tag == 0x74746366) { // 'ttcf'
                f.readInt() // версия
                if (f.readInt() <= 0) return null
                base = f.readInt().toLong() and 0xFFFFFFFFL
                f.seek(base)
                f.readInt()
            }
            val numTables = f.readUnsignedShort()
            f.skipBytes(6)
            var nameOffset = -1L
            repeat(numTables) {
                val t = f.readInt()
                f.readInt() // контрольная сумма
                val off = f.readInt().toLong() and 0xFFFFFFFFL
                f.readInt() // длина
                if (t == 0x6E616D65) nameOffset = off // 'name'
            }
            if (nameOffset < 0) return null
            f.seek(nameOffset)
            f.readUnsignedShort()
            val count = f.readUnsignedShort()
            val strings = nameOffset + f.readUnsignedShort()
            val found = HashMap<Int, Pair<Int, String>>() // nameId → (приоритет, строка)
            repeat(count.coerceAtMost(512)) {
                val platform = f.readUnsignedShort()
                val encoding = f.readUnsignedShort()
                val language = f.readUnsignedShort()
                val nameId = f.readUnsignedShort()
                val length = f.readUnsignedShort()
                val offset = f.readUnsignedShort()
                if (nameId !in setOf(0, 1, 2, 4, 5)) return@repeat
                val unicode = platform == 0 || platform == 3 && (encoding == 1 || encoding == 10)
                val mac = platform == 1 && encoding == 0
                if (!unicode && !mac) return@repeat
                // Предпочитаем английские записи Windows, затем любые Unicode, затем Mac Roman.
                val priority = when {
                    platform == 3 && language == 0x409 -> 3
                    unicode -> 2
                    else -> 1
                }
                if ((found[nameId]?.first ?: 0) >= priority) return@repeat
                val pos = f.filePointer
                f.seek(strings + offset)
                val bytes = ByteArray(length.coerceAtMost(1024)).also { f.readFully(it) }
                f.seek(pos)
                val text = if (unicode) String(bytes, Charsets.UTF_16BE) else String(bytes, Charsets.ISO_8859_1)
                found[nameId] = priority to text.trim()
            }
            fun get(id: Int) = found[id]?.second?.takeIf { it.isNotBlank() }
            FontNames(family = get(1), full = get(4), style = get(2), version = get(5), copyright = get(0))
        }
    }.getOrNull()
}

private const val DEFAULT_SAMPLE = "Съешь же ещё этих мягких французских булок, да выпей чаю. The quick brown fox jumps over the lazy dog. 0123456789"

private val GLYPHS: List<String> = buildList {
    ('А'..'Я').forEach { add(it.toString()) }
    add("Ё")
    ('а'..'я').forEach { add(it.toString()) }
    add("ё")
    ('A'..'Z').forEach { add(it.toString()) }
    ('a'..'z').forEach { add(it.toString()) }
    ('0'..'9').forEach { add(it.toString()) }
    "!?.,:;«»\"'()[]{}<>@#№$%&*+-=/\\|_~^`€₽©®™°±×÷→←↑↓•…—–".forEach { add(it.toString()) }
}

private sealed interface FontState {
    data object Loading : FontState
    data class Ready(val family: FontFamily, val names: FontNames?, val missing: Set<String>) : FontState
    data class Error(val message: String) : FontState
}

@Composable
fun FontViewerScreen(path: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    val scheme = MaterialTheme.colorScheme
    val state by produceState<FontState>(FontState.Loading, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val tf = android.graphics.Typeface.createFromFile(file)
                // Каких символов нет в самом шрифте (иначе их подставил бы системный и ввёл в заблуждение).
                val has = FontInfo.coverage(file)
                val missing = if (has == null) emptySet() else GLYPHS.filterTo(HashSet()) { !has(it.codePointAt(0)) }
                FontState.Ready(FontFamily(androidx.compose.ui.text.font.Typeface(tf)), FontInfo.read(file), missing)
            } catch (e: Exception) {
                FontState.Error("Не удалось загрузить шрифт: ${e.message ?: "формат не поддерживается"}")
            }
        }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sample by rememberSaveable { mutableStateOf(DEFAULT_SAMPLE) }
    var size by rememberSaveable { mutableFloatStateOf(28f) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    val names = (state as? FontState.Ready)?.names
                    Column {
                        Text(names?.full ?: names?.family ?: file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(names?.style, file.extension.uppercase(), Format.size(file.length())).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    val fullName = (state as? FontState.Ready)?.names?.full?.takeIf { it.isNotBlank() }
                    if (fullName != null) {
                        IconButton(onClick = { copyText(context, fullName) }) {
                            Icon(Icons.Rounded.ContentCopy, "Копировать полное имя")
                        }
                    }
                    IconButton(onClick = { FileOpener.share(context, listOf(file)) }) { Icon(Icons.Rounded.Share, "Поделиться") }
                    IconButton(onClick = { FileOpener.openExternal(context, file) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Открыть в другом приложении") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surfaceContainer),
            )
        },
        containerColor = scheme.surface,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when (val s = state) {
                FontState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center).size(64.dp))
                is FontState.Error -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FontDownload, null, Modifier.size(56.dp), tint = scheme.error)
                    Text(s.message, modifier = Modifier.padding(top = 12.dp))
                    TextButton(onClick = { FileOpener.openExternal(context, file) }) { Text("Открыть в другом приложении") }
                }
                is FontState.Ready -> Column(Modifier.fillMaxSize()) {
                    SecondaryTabRow(selectedTabIndex = tab, containerColor = scheme.surfaceContainer) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Образец") })
                        Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Символы") })
                        Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Сведения") })
                    }
                    when (tab) {
                        0 -> SampleTab(s.family, sample, { sample = it }, size, { size = it })
                        1 -> GlyphsTab(s.family, s.missing)
                        else -> InfoTab(file, s.names)
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleTab(family: FontFamily, sample: String, onSample: (String) -> Unit, size: Float, onSize: (Float) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.foundation.lazy.LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = sample,
                onValueChange = onSample,
                label = { Text("Текст образца") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${size.toInt()} sp", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
                Slider(value = size, onValueChange = onSize, valueRange = 8f..96f, modifier = Modifier.weight(1f))
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = scheme.surfaceContainerLow) {
                Text(sample, style = TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = (size * 1.3f).sp), modifier = Modifier.fillMaxWidth().padding(20.dp))
            }
        }
        val sizes = listOf(12, 16, 20, 24, 32, 48, 64)
        items(sizes.size) { i ->
            Column {
                Text("${sizes[i]} sp", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                Text(
                    sample.ifBlank { DEFAULT_SAMPLE },
                    style = TextStyle(fontFamily = family, fontSize = sizes[i].sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GlyphsTab(family: FontFamily, missing: Set<String>) {
    val scheme = MaterialTheme.colorScheme
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var highlightCp by rememberSaveable { mutableIntStateOf(-1) }
    var jumpMessage by rememberSaveable { mutableStateOf<String?>(null) }
    fun covered(range: CharRange) = range.none { it.toString() in missing }
    fun jump() {
        val cp = parseHexCodePoint(code)
        if (cp == null) {
            highlightCp = -1
            jumpMessage = "Неверный код"
            return
        }
        val index = GLYPHS.indexOfFirst { it.codePointAt(0) == cp }
        if (index < 0) {
            highlightCp = -1
            jumpMessage = "Нет в сетке"
            return
        }
        highlightCp = cp
        jumpMessage = "U+${cp.toString(16).uppercase().padStart(4, '0')}"
        // Первая ячейка — шапка на всю ширину, символы начинаются с 1.
        scope.launch { gridState.animateScrollToItem(index + 1) }
    }
    Column(Modifier.fillMaxSize()) {
        val shownMessage = jumpMessage
        OutlinedTextField(
            value = code,
            onValueChange = {
                code = it.take(12)
                jumpMessage = null
                highlightCp = -1
            },
            label = { Text("Код символа") },
            placeholder = { Text("0416") },
            singleLine = true,
            isError = shownMessage != null && highlightCp < 0,
            supportingText = if (shownMessage == null) null else {
                { Text(shownMessage) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { jump() }),
            trailingIcon = {
                IconButton(onClick = { jump() }) { Icon(Icons.Rounded.Search, "Перейти") }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(56.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(8.dp)) {
                    Text("Аа Bb Цц 123", style = TextStyle(fontFamily = family, fontSize = 56.sp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        listOf("Кириллица" to covered('А'..'я'), "Латиница" to (covered('A'..'Z') && covered('a'..'z')), "Цифры" to covered('0'..'9'))
                            .forEach { (label, ok) ->
                                androidx.compose.material3.AssistChip(
                                    onClick = {},
                                    label = { Text(label) },
                                    leadingIcon = {
                                        Icon(
                                            if (ok) androidx.compose.material.icons.Icons.Rounded.Check else androidx.compose.material.icons.Icons.Rounded.Close,
                                            null,
                                            tint = if (ok) scheme.primary else scheme.error,
                                        )
                                    },
                                )
                            }
                    }
                    Text(
                        "Есть ${GLYPHS.size - missing.size} из ${GLYPHS.size} символов" + if (missing.isNotEmpty()) " · бледные — подставлены системным шрифтом" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            items(GLYPHS) { g ->
                val absent = g in missing
                val highlighted = highlightCp >= 0 && g.codePointAt(0) == highlightCp
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .then(if (highlighted) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (absent) scheme.surfaceContainerLow else scheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(g, style = TextStyle(fontFamily = family, fontSize = 26.sp, color = if (absent) scheme.onSurface.copy(alpha = 0.22f) else scheme.onSurface))
                }
            }
        }
    }
}

private fun parseHexCodePoint(raw: String): Int? {
    var s = raw.trim()
    if (s.length >= 2 && (s[0] == 'U' || s[0] == 'u') && s[1] == '+') s = s.substring(2)
    if (s.startsWith("0x", ignoreCase = true)) s = s.substring(2)
    s = s.trim()
    if (s.isEmpty() || s.length > 6) return null
    if (s.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    val cp = s.toIntOrNull(16) ?: return null
    if (cp !in 0..0x10FFFF || cp in 0xD800..0xDFFF) return null
    return cp
}

private fun copyText(context: Context, text: String) {
    runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: error("clipboard")
        cm.setPrimaryClip(ClipData.newPlainText("font", text))
    }.onSuccess {
        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Не удалось скопировать", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun InfoTab(file: File, names: FontNames?) {
    val context = LocalContext.current
    val rows = listOfNotNull(
        names?.family?.let { "Семейство" to it },
        names?.style?.let { "Начертание" to it },
        names?.full?.let { "Полное имя" to it },
        names?.version?.let { "Версия" to it },
        names?.copyright?.let { "Права" to it },
        "Файл" to file.path,
        "Размер" to Format.size(file.length()),
        "Изменён" to Format.fullDate(file.lastModified()),
    )
    androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(rows.size) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(rows[i].first, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(rows[i].second, style = MaterialTheme.typography.bodyLarge)
                }
                if (rows[i].first == "Полное имя") {
                    IconButton(onClick = { copyText(context, rows[i].second) }) {
                        Icon(Icons.Rounded.ContentCopy, "Копировать полное имя")
                    }
                }
            }
        }
    }
}
