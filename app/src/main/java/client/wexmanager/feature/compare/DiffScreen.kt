package client.wexmanager.feature.compare

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.ConnectedChoice
import client.wexmanager.ui.components.PressIconButton
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun DiffScreen(left: String, right: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: DiffViewModel = viewModel(key = "diff:$left|$right") { DiffViewModel(app, left, right) }
    val state by vm.state.collectAsStateWithLifecycle()

    val leftName = state.left?.name ?: displayName(left)
    val rightName = state.right?.name ?: displayName(right)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сравнение файлов", maxLines = 1) },
                subtitle = { Text("$leftName ↔ $rightName", maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(force = false) }, enabled = !state.loading, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.Refresh, "Сравнить заново")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        val motion = MaterialTheme.motionScheme
        val phase = when {
            state.loading -> 0
            state.error != null -> 1
            state.binary -> 2
            state.identical -> 3
            else -> 4
        }
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 1.04f))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            label = "diffPhase",
        ) { p ->
            when (p) {
                0 -> LoadingPane()
                1 -> ComparePane(
                    polygon = MaterialShapes.Clover4Leaf,
                    icon = Icons.Rounded.ErrorOutline,
                    title = "Не удалось сравнить",
                    text = state.error,
                    error = true,
                    action = "Повторить" to { vm.load(force = false) },
                )
                2 -> ComparePane(
                    polygon = MaterialShapes.Cookie7Sided,
                    icon = Icons.Rounded.Memory,
                    title = "Похоже на двоичные файлы",
                    text = "Построчное сравнение двоичных данных обычно бессмысленно. " +
                        "Размеры: ${Format.size(state.left?.size ?: 0)} и ${Format.size(state.right?.size ?: 0)}.",
                    action = "Сравнить как текст" to { vm.load(force = true) },
                    secondary = "Назад" to onBack,
                )
                3 -> ComparePane(
                    polygon = MaterialShapes.Sunny,
                    icon = Icons.Rounded.DoneAll,
                    title = "Файлы совпадают",
                    text = buildString {
                        append("Все строки одинаковы")
                        state.left?.let { append(" (").append(it.lines).append(' ').append(Format.plural(it.lines, "строка", "строки", "строк")).append(')') }
                        append('.')
                        if (state.notes.isNotEmpty()) append("\n\n").append(state.notes.joinToString("\n"))
                    },
                )
                else -> DiffContent(state, vm)
            }
        }
    }
}

@Composable
private fun LoadingPane() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ContainedLoadingIndicator(
            modifier = Modifier.size(96.dp),
            containerColor = scheme.primaryContainer,
            indicatorColor = scheme.onPrimaryContainer,
        )
        Spacer(Modifier.height(20.dp))
        Text("Сравнение…", style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ Основной вид

@Composable
private fun DiffContent(state: DiffUiState, vm: DiffViewModel) {
    val scheme = MaterialTheme.colorScheme
    val palette = rememberDiffPalette()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val side = state.mode == DiffMode.Side

    val fontSize = if (side) 11.5.sp else 12.5.sp
    val codeStyle = remember(fontSize) {
        TextStyle(fontFamily = JetBrainsMono, fontSize = fontSize, lineHeight = fontSize * 1.5f)
    }
    val numStyle = remember(fontSize) {
        TextStyle(fontFamily = JetBrainsMono, fontSize = fontSize * 0.85f, lineHeight = fontSize * 1.5f, textAlign = TextAlign.End)
    }
    val measurer = rememberTextMeasurer()
    val charW = remember(codeStyle, density) { measurer.measure("0000000000", codeStyle).size.width / 10f }
    val numCharW = remember(numStyle, density) { measurer.measure("0000000000", numStyle).size.width / 10f }
    val rowH = with(density) { (codeStyle.lineHeight).toDp() }
    val digits = state.maxLineNo.coerceAtLeast(1).toString().length
    val gutterW = with(density) { (numCharW * digits).toDp() } + 12.dp
    val signW = with(density) { (charW * 2).toDp() }

    // Общая горизонтальная прокрутка всех строк.
    var hOffset by remember { mutableFloatStateOf(0f) }
    var viewportW by remember { mutableIntStateOf(0) }
    val textPad = with(density) { 8.dp.toPx() }
    val maxChars = if (side) maxOf(state.maxLeftChars, state.maxRightChars) else state.maxLeftChars
    val textAreaW = if (side) {
        (viewportW - with(density) { 1.dp.toPx() }) / 2f - with(density) { gutterW.toPx() }
    } else {
        viewportW - with(density) { (gutterW * 2 + signW).toPx() }
    }
    val maxOffset = (maxChars * charW + textPad * 2 - textAreaW).coerceAtLeast(0f)
    val maxOffsetState = rememberUpdatedState(maxOffset)
    LaunchedEffect(maxOffset) { if (hOffset > maxOffset) hOffset = maxOffset }
    val hScroll = rememberScrollableState { delta ->
        val old = hOffset
        val new = (old + delta).coerceIn(0f, maxOffsetState.value)
        hOffset = new
        new - old
    }
    val offsetProvider = remember { { hOffset } }

    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var hideTrailing by remember { mutableStateOf(false) }
    val shown = remember(state.items, state.hunks, hideTrailing) {
        if (hideTrailing) withoutTrailingWhitespace(state.items, state.hunks) else state.items to state.hunks
    }
    val shownItems = shown.first
    val hunks = shown.second

    // Навигация по блокам отличий — относительно первой видимой строки (с запасом в 2 строки контекста).
    val nav by remember(hunks) {
        derivedStateOf {
            val pos = listState.firstVisibleItemIndex + HUNK_LEAD
            var current = -1
            var prev = -1
            var next = -1
            for (k in hunks.indices) {
                val h = hunks[k]
                if (h <= pos) current = k
                if (h < pos) prev = k
                if (h > pos) { next = k; break }
            }
            Triple(current, prev, next)
        }
    }
    fun goTo(k: Int) {
        if (k !in hunks.indices) return
        scope.launch { listState.animateScrollToItem((hunks[k] - HUNK_LEAD).coerceAtLeast(0)) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            DiffHeader(state, palette, hideTrailing, onMode = vm::setMode, onToggleTrailing = { hideTrailing = !hideTrailing })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(scheme.surfaceContainerLow)
                    .onSizeChanged { viewportW = it.width }
                    .scrollable(hScroll, Orientation.Horizontal, reverseDirection = true),
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 6.dp, bottom = bottom + 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(shownItems, key = { it.key }, contentType = { it.javaClass.simpleName }) { item ->
                        when (item) {
                            is SideItem -> SideLine(item.row, palette, codeStyle, numStyle, rowH, gutterW, offsetProvider)
                            is UniItem -> UniLine(item, palette, codeStyle, numStyle, rowH, gutterW, signW, offsetProvider)
                            is FoldItem -> FoldLine(item, rowH) { vm.expand(item) }
                        }
                    }
                }
                if (maxOffset > 0f) {
                    HorizontalIndicator(
                        fraction = { if (maxOffset > 0f) hOffset / maxOffset else 0f },
                        visibleFraction = (textAreaW / (textAreaW + maxOffset)).coerceIn(0.08f, 1f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 84.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }
        }

        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            val (current, prev, next) = nav
            PressIconButton(
                Icons.Rounded.KeyboardArrowUp, "Предыдущее отличие",
                onClick = { goTo(prev) },
                enabled = prev >= 0,
            )
            Box(Modifier.align(Alignment.CenterVertically).widthIn(min = 72.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (hunks.isEmpty()) "—" else "${(current + 1).coerceAtLeast(0)} из ${hunks.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onPrimaryContainer,
                    maxLines = 1,
                )
            }
            PressIconButton(
                Icons.Rounded.KeyboardArrowDown, "Следующее отличие",
                onClick = { goTo(next) },
                enabled = next >= 0,
            )
            Spacer(Modifier.width(4.dp))
            PressIconButton(
                if (state.onlyDiffs) Icons.Rounded.UnfoldMore else Icons.Rounded.UnfoldLess,
                if (state.onlyDiffs) "Показать все строки" else "Только отличия",
                onClick = vm::toggleOnlyDiffs,
                containerColor = if (state.onlyDiffs) scheme.onPrimaryContainer else Color.Transparent,
                contentColor = if (state.onlyDiffs) scheme.primaryContainer else scheme.onPrimaryContainer,
            )
        }
    }
}

/** Строк контекста над началом блока при переходе к отличию. */
private const val HUNK_LEAD = 2

/** Убирает из списка отличия, которые сводятся только к пробелам в конце строки. Индексы блоков пересчитываются. */
private fun withoutTrailingWhitespace(items: List<DiffItem>, hunks: IntArray): Pair<List<DiffItem>, IntArray> {
    val drop = HashSet<String>()
    val removed = HashMap<String, String>()
    val added = HashMap<String, String>()
    for (item in items) {
        when (item) {
            is SideItem -> if (trailingWhitespaceChange(item.row.left, item.row.right)) drop += item.key
            is UniItem -> {
                val pair = uniChangePair(item.key) ?: continue
                when (item.kind) {
                    RowKind.Removed -> removed[pair] = item.text
                    RowKind.Added -> added[pair] = item.text
                    else -> Unit
                }
            }
            is FoldItem -> Unit
        }
    }
    for ((pair, left) in removed) {
        val right = added[pair] ?: continue
        if (trailingWhitespaceChange(left, right)) {
            drop += pair + "a"
            drop += pair + "b"
        }
    }
    if (drop.isEmpty()) return items to hunks
    val out = ArrayList<DiffItem>(items.size - drop.size)
    val next = ArrayList<Int>()
    var inBlock = false
    for (item in items) {
        if (item.key in drop) continue
        if (isDiffLine(item)) {
            if (!inBlock) {
                next += out.size
                inBlock = true
            }
        } else {
            inBlock = false
        }
        out += item
    }
    return out to next.toIntArray()
}

private fun isDiffLine(item: DiffItem): Boolean = when (item) {
    is SideItem -> item.row.kind != RowKind.Equal
    is UniItem -> item.kind != RowKind.Equal
    is FoldItem -> false
}

/** Пара «удалено/добавлено» одного изменения единого вида: ключи `u12a` и `u12b`. */
private fun uniChangePair(key: String): String? {
    val n = key.length
    if (n < 3 || key[0] != 'u') return null
    val last = key[n - 1]
    if (last != 'a' && last != 'b') return null
    return key.substring(0, n - 1)
}

private fun trailingWhitespaceChange(left: String?, right: String?): Boolean {
    if (left == null || right == null || left == right) return false
    return stripTrailingSpaces(left) == stripTrailingSpaces(right)
}

private fun stripTrailingSpaces(s: String): String {
    var end = s.length
    while (end > 0 && s[end - 1].isWhitespace()) end--
    return if (end == s.length) s else s.substring(0, end)
}

@Composable
private fun DiffHeader(
    state: DiffUiState,
    palette: DiffPalette,
    hideTrailing: Boolean,
    onMode: (DiffMode) -> Unit,
    onToggleTrailing: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val s = LocalAppSettings.current
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilePill(state.left, scheme.secondaryContainer, scheme.onSecondaryContainer, s.sizeUnits, Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Rounded.CompareArrows, null,
                tint = scheme.outline,
                modifier = Modifier.padding(horizontal = 6.dp).size(20.dp),
            )
            FilePill(state.right, scheme.tertiaryContainer, scheme.onTertiaryContainer, s.sizeUnits, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatBadge("+${state.stats.added}", "добавлено", palette.added)
            Spacer(Modifier.width(6.dp))
            StatBadge("−${state.stats.removed}", "удалено", palette.removed)
            Spacer(Modifier.width(6.dp))
            StatBadge("~${state.stats.changed}", "изменено", palette.changed)
            Spacer(Modifier.width(12.dp))
            ConnectedChoice(
                options = DiffMode.entries,
                selected = state.mode,
                onSelect = onMode,
                label = { it.label },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        val trailingIcon: (@Composable () -> Unit)? = if (hideTrailing) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        }
        FilterChip(
            selected = hideTrailing,
            onClick = onToggleTrailing,
            label = { Text("Пробелы в конце") },
            leadingIcon = trailingIcon,
        )
        if (state.notes.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.notes.joinToString("  •  "),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FilePill(info: DiffFileInfo?, container: Color, content: Color, units: client.wexmanager.core.settings.SizeUnits, modifier: Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Difference, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    info?.name.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
                Text(
                    if (info == null) "" else "${Format.size(info.size, units)} • ${info.lines} ${Format.plural(info.lines, "строка", "строки", "строк")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatBadge(value: String, desc: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f))
            .semantics(mergeDescendants = true) { contentDescription = "$value $desc" }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

// ------------------------------------------------------------------ Строки

private fun annotate(text: String, marks: IntArray?, color: Color): AnnotatedString {
    if (marks == null || marks.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var k = 0
        while (k + 1 < marks.size) {
            val s = marks[k].coerceIn(0, text.length)
            val e = marks[k + 1].coerceIn(s, text.length)
            if (e > s) addStyle(SpanStyle(background = color), s, e)
            k += 2
        }
    }
}

@Composable
private fun SideLine(
    row: DiffRow,
    palette: DiffPalette,
    codeStyle: TextStyle,
    numStyle: TextStyle,
    rowH: Dp,
    gutterW: Dp,
    offset: () -> Float,
) {
    val scheme = MaterialTheme.colorScheme
    val empty = scheme.surfaceContainerHighest.copy(alpha = 0.55f)
    val (leftBg, rightBg) = when (row.kind) {
        RowKind.Equal -> Color.Transparent to Color.Transparent
        RowKind.Removed -> palette.removedBg to empty
        RowKind.Added -> empty to palette.addedBg
        RowKind.Changed -> palette.changedBg to palette.changedBg
    }
    val leftText = remember(row, palette) { row.left?.let { annotate(it, row.leftMarks, palette.removedMark) } }
    val rightText = remember(row, palette) { row.right?.let { annotate(it, row.rightMarks, palette.addedMark) } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowH),
    ) {
        CodeCell(row.leftNo, leftText, leftBg, codeStyle, numStyle, gutterW, offset, Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(scheme.outlineVariant),
        )
        CodeCell(row.rightNo, rightText, rightBg, codeStyle, numStyle, gutterW, offset, Modifier.weight(1f))
    }
}

@Composable
private fun CodeCell(
    no: Int,
    text: AnnotatedString?,
    bg: Color,
    codeStyle: TextStyle,
    numStyle: TextStyle,
    gutterW: Dp,
    offset: () -> Float,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxHeight()
            .background(bg),
    ) {
        Text(
            if (no > 0) no.toString() else "",
            style = numStyle,
            color = scheme.outline,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .width(gutterW)
                .padding(end = 6.dp),
        )
        CodeText(text, codeStyle, offset, Modifier.weight(1f))
    }
}

@Composable
private fun CodeText(text: AnnotatedString?, style: TextStyle, offset: () -> Float, modifier: Modifier) {
    Box(
        modifier
            .fillMaxHeight()
            .clipToBounds(),
    ) {
        if (text != null && text.isNotEmpty()) {
            Text(
                text,
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .wrapContentWidth(Alignment.Start, unbounded = true)
                    .offset { IntOffset(-offset().roundToInt(), 0) }
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun UniLine(
    item: UniItem,
    palette: DiffPalette,
    codeStyle: TextStyle,
    numStyle: TextStyle,
    rowH: Dp,
    gutterW: Dp,
    signW: Dp,
    offset: () -> Float,
) {
    val scheme = MaterialTheme.colorScheme
    val (bg, sign, signColor, mark) = when (item.kind) {
        RowKind.Removed -> Quad(palette.removedBg, "−", palette.removed, palette.removedMark)
        RowKind.Added -> Quad(palette.addedBg, "+", palette.added, palette.addedMark)
        else -> Quad(Color.Transparent, "", scheme.outline, Color.Transparent)
    }
    val text = remember(item, palette) { annotate(item.text, item.marks, mark) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowH)
            .background(bg),
    ) {
        Text(
            if (item.oldNo > 0) item.oldNo.toString() else "",
            style = numStyle,
            color = scheme.outline,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(gutterW).padding(end = 6.dp),
        )
        Text(
            if (item.newNo > 0) item.newNo.toString() else "",
            style = numStyle,
            color = scheme.outline,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(gutterW).padding(end = 6.dp),
        )
        Text(
            sign,
            style = codeStyle.copy(fontWeight = FontWeight.Bold),
            color = signColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.width(signW),
        )
        CodeText(text, codeStyle, offset, Modifier.weight(1f))
    }
}

private data class Quad(val bg: Color, val sign: String, val signColor: Color, val mark: Color)

@Composable
private fun FoldLine(item: FoldItem, rowH: Dp, onExpand: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.secondaryContainer.copy(alpha = 0.7f))
            .clickable(onClick = onExpand)
            .height(rowH + 10.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.UnfoldMore, null, tint = scheme.onSecondaryContainer, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Показать ${item.count} ${Format.plural(item.count, "совпадающую строку", "совпадающие строки", "совпадающих строк")}",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSecondaryContainer,
            maxLines = 1,
        )
    }
}

/** Тонкий индикатор горизонтальной прокрутки (рисуется на этапе отрисовки, без рекомпозиции). */
@Composable
private fun HorizontalIndicator(fraction: () -> Float, visibleFraction: Float, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val thumb = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawBehind {
                val r = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                val w = size.width * visibleFraction
                val x = (size.width - w) * fraction().coerceIn(0f, 1f)
                drawRoundRect(thumb, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = r)
            },
    )
}
