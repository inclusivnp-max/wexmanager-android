package client.wexmanager.feature.tools

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.AltRoute
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DataArray
import androidx.compose.material.icons.rounded.DataObject
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.RoundedPolygon
import client.wexmanager.R
import client.wexmanager.core.util.Format
import client.wexmanager.feature.browser.copyToSystemClipboard
import client.wexmanager.ui.components.FastScroller
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.components.asScrollTarget
import kotlinx.coroutines.launch

// ------------------------------------------------------------------ Модель экрана

private enum class SmaliMode(val title: String) { Opcodes("Инструкции"), Reference("Справка") }

private val SmaliMono = FontFamily(Font(R.font.jetbrains_mono), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))
private val MnemonicStyle = TextStyle(fontFamily = SmaliMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
private val CodeStyle = TextStyle(fontFamily = SmaliMono, fontSize = 13.sp, lineHeight = 19.sp)

private val ExpandedSaver = Saver<Set<Int>, IntArray>(save = { it.toIntArray() }, restore = { it.toSet() })

private val OpcodeTotal = SmaliOpcodes.count { !it.unused }

/** В списке справки перед строками идёт подсказка — индексы LazyColumn сдвинуты на 1. */
private const val RefListOffset = 1

/** Разобранный поисковый запрос: слова (все должны встретиться) и, если похоже, hex-код опкода. */
@Immutable
private class SmaliQuery(val tokens: List<String>, val hex: Int?) {
    val isEmpty: Boolean get() = tokens.isEmpty()

    fun matches(op: SmaliOpcode): Boolean =
        isEmpty || (hex != null && op.covers(hex)) || tokens.all { op.searchText.contains(it) }

    fun matches(text: String): Boolean = tokens.all { text.contains(it) }

    companion object {
        private val Hex = Regex("^(?:0x)?([0-9a-f]{1,2})$")
        private val Spaces = Regex("\\s+")

        fun parse(raw: String): SmaliQuery {
            val tokens = raw.trim().lowercase().split(Spaces).filter { it.isNotEmpty() }
            val hex = tokens.singleOrNull()?.let { Hex.find(it)?.groupValues?.get(1)?.toInt(16) }
            return SmaliQuery(tokens, hex)
        }
    }
}

/** Строки списка опкодов: заголовок группы подряд идущих опкодов одной категории или карточка. */
@Immutable
private sealed interface OpRow {
    val key: String

    class Header(val category: SmaliCategory, val first: SmaliOpcode, val last: SmaliOpcode, val count: Int) : OpRow {
        override val key = "h${first.code}"
    }

    class Item(val op: SmaliOpcode, val index: Int, val count: Int) : OpRow {
        override val key = "o${op.code}"
    }
}

private fun buildOpRows(ops: List<SmaliOpcode>): List<OpRow> {
    val rows = ArrayList<OpRow>(ops.size + 40)
    var start = 0
    while (start < ops.size) {
        val cat = ops[start].category
        var end = start
        while (end + 1 < ops.size && ops[end + 1].category == cat) end++
        val count = end - start + 1
        rows += OpRow.Header(cat, ops[start], ops[end], count)
        for (i in start..end) rows += OpRow.Item(ops[i], i - start, count)
        start = end + 1
    }
    return rows
}

@Immutable
private sealed interface RefRow {
    val key: String
    val section: SmaliRefSection

    class Header(override val section: SmaliRefSection) : RefRow {
        override val key = "s:${section.id}"
    }

    class Item(override val section: SmaliRefSection, val entry: SmaliRefEntry, val id: Int, val index: Int, val count: Int) : RefRow {
        override val key = "e:${section.id}:$id"
    }
}

private fun buildRefRows(query: SmaliQuery): List<RefRow> {
    val rows = ArrayList<RefRow>(128)
    for (section in SmaliReferenceSections) {
        val title = section.title.lowercase()
        val matched = section.entries.withIndex().filter { (_, e) -> query.isEmpty || query.matches(e.searchText + "\n" + title) }
        if (matched.isEmpty()) continue
        rows += RefRow.Header(section)
        matched.forEachIndexed { i, (id, e) -> rows += RefRow.Item(section, e, id, i, matched.size) }
    }
    return rows
}

private val SmaliCategory.icon: ImageVector
    get() = when (this) {
        SmaliCategory.Move -> Icons.Rounded.SwapHoriz
        SmaliCategory.Const -> Icons.Rounded.Numbers
        SmaliCategory.Monitor -> Icons.Rounded.Lock
        SmaliCategory.Array -> Icons.Rounded.DataArray
        SmaliCategory.Flow -> Icons.AutoMirrored.Rounded.AltRoute
        SmaliCategory.Compare -> Icons.Rounded.Balance
        SmaliCategory.Field -> Icons.Rounded.DataObject
        SmaliCategory.Invoke -> Icons.Rounded.Functions
        SmaliCategory.Unary -> Icons.Rounded.Exposure
        SmaliCategory.Binary -> Icons.Rounded.Calculate
        SmaliCategory.Addr2 -> Icons.Rounded.SyncAlt
        SmaliCategory.Lit -> Icons.Rounded.Tag
        SmaliCategory.Other -> Icons.Rounded.MoreHoriz
    }

private val SmaliCategory.polygon: RoundedPolygon
    get() = when (this) {
        SmaliCategory.Move -> MaterialShapes.Cookie4Sided
        SmaliCategory.Const -> MaterialShapes.Gem
        SmaliCategory.Monitor -> MaterialShapes.Pentagon
        SmaliCategory.Array -> MaterialShapes.Square
        SmaliCategory.Flow -> MaterialShapes.SoftBurst
        SmaliCategory.Compare -> MaterialShapes.Clover4Leaf
        SmaliCategory.Field -> MaterialShapes.Cookie6Sided
        SmaliCategory.Invoke -> MaterialShapes.Sunny
        SmaliCategory.Unary -> MaterialShapes.Circle
        SmaliCategory.Binary -> MaterialShapes.Cookie7Sided
        SmaliCategory.Addr2 -> MaterialShapes.Cookie9Sided
        SmaliCategory.Lit -> MaterialShapes.Cookie12Sided
        SmaliCategory.Other -> MaterialShapes.Puffy
    }

private fun ColorScheme.badgeColors(op: SmaliOpcode): Pair<Color, Color> = when {
    op.unused -> surfaceContainerHighest to outline
    else -> when (op.category.ordinal % 3) {
        0 -> primaryContainer to onPrimaryContainer
        1 -> tertiaryContainer to onTertiaryContainer
        else -> secondaryContainer to onSecondaryContainer
    }
}

private fun sectionIcon(id: String): ImageVector = when (id) {
    "types" -> Icons.Rounded.Category
    "registers" -> Icons.Rounded.Memory
    "directives" -> Icons.Rounded.Code
    "modifiers" -> Icons.Rounded.Key
    else -> Icons.Rounded.Straighten
}

private fun sectionPolygon(id: String): RoundedPolygon = when (id) {
    "types" -> MaterialShapes.Cookie9Sided
    "registers" -> MaterialShapes.Cookie6Sided
    "directives" -> MaterialShapes.Clover4Leaf
    "modifiers" -> MaterialShapes.Pentagon
    else -> MaterialShapes.Sunny
}

// ------------------------------------------------------------------ Экран

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmaliReferenceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(SmaliMode.Opcodes) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<SmaliCategory?>(null) }
    var expanded by rememberSaveable(stateSaver = ExpandedSaver) { mutableStateOf(emptySet<Int>()) }

    val parsed = remember(query) { SmaliQuery.parse(query) }
    val matched = remember(parsed) { SmaliOpcodes.filter(parsed::matches) }
    // Счётчики — только реальные инструкции; present — категории, где есть хоть одна строка.
    val counts = remember(matched) { matched.filter { !it.unused }.groupingBy { it.category }.eachCount() }
    val present = remember(matched) { matched.mapTo(HashSet()) { it.category } }
    val opRows = remember(matched, category) {
        buildOpRows(if (category == null) matched else matched.filter { it.category == category })
    }
    val refRows = remember(parsed) { buildRefRows(parsed) }

    val opState = rememberLazyListState()
    val refState = rememberLazyListState()
    val copy: (String) -> Unit = { copyToSystemClipboard(context, it) }

    val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val toolbarBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)

    val subtitle = when (mode) {
        SmaliMode.Opcodes -> {
            val shown = opRows.count { it is OpRow.Item && !it.op.unused }
            if (parsed.isEmpty && category == null) {
                "$OpcodeTotal ${Format.plural(OpcodeTotal, "инструкция", "инструкции", "инструкций")} Dalvik · 0x00–0xFF"
            } else {
                "Найдено: $shown"
            }
        }
        SmaliMode.Reference ->
            if (parsed.isEmpty) "Типы, регистры, директивы, модификаторы"
            else "Найдено: ${refRows.count { it is RefRow.Item }}"
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
        topBar = {
            Column(Modifier.background(scheme.surfaceContainer)) {
                LargeFlexibleTopAppBar(
                    title = { Text("Справочник Smali", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            visible = mode == SmaliMode.Opcodes && expanded.isNotEmpty(),
                            enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
                            exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
                        ) {
                            IconButton(onClick = { expanded = emptySet() }, shapes = IconButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.UnfoldLess, "Свернуть все")
                            }
                        }
                    },
                    scrollBehavior = behavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = scheme.surfaceContainer,
                        scrolledContainerColor = scheme.surfaceContainer,
                    ),
                )
                SmaliSearchField(
                    query = query,
                    placeholder = if (mode == SmaliMode.Opcodes) "Мнемоника, описание или hex (6e)" else "Поиск по справке",
                    onChange = {
                        query = it
                        opState.requestScrollToItem(0)
                        refState.requestScrollToItem(0)
                    },
                    onSubmit = {
                        keyboard?.hide()
                        focus.clearFocus()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                    label = "smaliChips",
                ) { m ->
                    when (m) {
                        SmaliMode.Opcodes -> CategoryChips(
                            selected = category,
                            counts = counts,
                            present = present,
                            total = counts.values.sum(),
                            onSelect = {
                                category = it
                                opState.requestScrollToItem(0)
                            },
                        )
                        SmaliMode.Reference -> SectionChips(
                            rows = refRows,
                            state = refState,
                            onSelect = { index -> scope.launch { refState.animateScrollToItem(index + RefListOffset) } },
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = scheme.surfaceContainer,
    ) { padding ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val listBottom = navBottom + 104.dp
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                .nestedScroll(toolbarBehavior),
        ) {
            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f))
                        .togetherWith(fadeOut(motion.fastEffectsSpec()))
                },
                modifier = Modifier.fillMaxSize(),
                label = "smaliMode",
            ) { m ->
                when (m) {
                    SmaliMode.Opcodes -> OpcodeList(
                        rows = opRows,
                        state = opState,
                        parsed = parsed,
                        expanded = expanded,
                        bottomPadding = listBottom,
                        keyboard = keyboard,
                        focus = focus,
                        onToggle = { code -> expanded = if (code in expanded) expanded - code else expanded + code },
                        onCopy = copy,
                    )
                    SmaliMode.Reference -> ReferenceList(
                        rows = refRows,
                        state = refState,
                        bottomPadding = listBottom,
                        keyboard = keyboard,
                        focus = focus,
                        onCopy = copy,
                    )
                }
            }

            AnimatedVisibility(
                visible = !WindowInsets.isImeVisible,
                enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.defaultEffectsSpec()),
                exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    scrollBehavior = toolbarBehavior,
                ) {
                    SmaliMode.entries.forEachIndexed { i, m ->
                        if (i > 0) Spacer(Modifier.width(4.dp))
                        ModeButton(
                            mode = m,
                            selected = m == mode,
                            onClick = {
                                if (mode != m) {
                                    mode = m
                                    behavior.state.heightOffset = 0f
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Шапка: поиск и чипы

@Composable
private fun SmaliSearchField(
    query: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotEmpty(),
                enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
                exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
            ) {
                IconButton(onClick = { onChange("") }, shapes = IconButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.Close, "Очистить")
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            autoCorrectEnabled = false,
            capitalization = KeyboardCapitalization.None,
        ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHighest,
            unfocusedContainerColor = scheme.surfaceContainerHighest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryChips(
    selected: SmaliCategory?,
    counts: Map<SmaliCategory, Int>,
    present: Set<SmaliCategory>,
    total: Int,
    onSelect: (SmaliCategory?) -> Unit,
) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
    ) {
        item(key = "all") {
            SmaliChip(label = "Все", count = total, selected = selected == null, icon = Icons.Rounded.Apps) { onSelect(null) }
        }
        items(SmaliCategory.entries, key = { it.name }) { c ->
            val n = counts[c] ?: 0
            SmaliChip(
                label = c.title,
                count = n,
                selected = selected == c,
                icon = c.icon,
                enabled = c in present || selected == c,
            ) { onSelect(if (selected == c) null else c) }
        }
    }
}

@Composable
private fun SectionChips(rows: List<RefRow>, state: LazyListState, onSelect: (Int) -> Unit) {
    val headers = remember(rows) { rows.withIndex().filter { it.value is RefRow.Header } }
    val current by remember(rows, state) {
        derivedStateOf { rows.getOrNull((state.firstVisibleItemIndex - RefListOffset).coerceAtLeast(0))?.section?.id }
    }
    val rowState = rememberLazyListState()
    LaunchedEffect(current, headers) {
        val i = headers.indexOfFirst { it.value.section.id == current }
        if (i >= 0) rowState.animateScrollToItem(i)
    }
    LazyRow(
        state = rowState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp),
    ) {
        items(headers, key = { it.value.key }) { (index, row) ->
            SmaliChip(
                label = row.section.title,
                count = null,
                selected = row.section.id == current,
                icon = sectionIcon(row.section.id),
            ) { onSelect(index) }
        }
    }
}

@Composable
private fun SmaliChip(
    label: String,
    count: Int?,
    selected: Boolean,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        shapes = FilterChipDefaults.shapes(),
        label = {
            Text(label, maxLines = 1)
            if (count != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = SmaliMono),
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        },
        leadingIcon = {
            AnimatedContent(selected, label = "chipIcon") { sel ->
                Icon(if (sel) Icons.Rounded.Check else icon, null, Modifier.size(FilterChipDefaults.IconSize))
            }
        },
    )
}

@Composable
private fun ModeButton(mode: SmaliMode, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        colors = ToggleButtonDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = scheme.onPrimaryContainer,
            checkedContainerColor = scheme.primary,
            checkedContentColor = scheme.onPrimary,
        ),
        elevation = null,
    ) {
        Icon(
            if (mode == SmaliMode.Opcodes) Icons.Rounded.Terminal else Icons.AutoMirrored.Rounded.MenuBook,
            contentDescription = if (selected) null else mode.title,
            modifier = Modifier.size(ToggleButtonDefaults.IconSize),
        )
        AnimatedVisibility(
            visible = selected,
            enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
            exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
        ) {
            Row {
                Spacer(Modifier.width(ToggleButtonDefaults.IconSpacing))
                Text(mode.title, maxLines = 1)
            }
        }
    }
}

// ------------------------------------------------------------------ Список опкодов

@Composable
private fun HideKeyboardOnScroll(state: LazyListState, keyboard: SoftwareKeyboardController?, focus: FocusManager) {
    LaunchedEffect(state.isScrollInProgress) {
        if (state.isScrollInProgress) {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
}

@Composable
private fun OpcodeList(
    rows: List<OpRow>,
    state: LazyListState,
    parsed: SmaliQuery,
    expanded: Set<Int>,
    bottomPadding: Dp,
    keyboard: SoftwareKeyboardController?,
    focus: FocusManager,
    onToggle: (Int) -> Unit,
    onCopy: (String) -> Unit,
) {
    HideKeyboardOnScroll(state, keyboard, focus)
    if (rows.isEmpty()) {
        ToolsEmptyState(
            polygon = MaterialShapes.Cookie9Sided,
            icon = Icons.Rounded.SearchOff,
            title = "Ничего не найдено",
            text = "Попробуйте другую мнемонику, hex-код (например, 6e или 0x22) или выберите другую категорию.",
        )
        return
    }
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    // Формы значков создаются один раз на категорию.
    val badgeShapes = remember { SmaliCategory.entries.associateWith { PolygonShape(it.polygon) } }
    val accent = remember(scheme.primary) { SpanStyle(color = scheme.primary, background = scheme.primary.copy(alpha = 0.12f)) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(rows, key = { it.key }, contentType = { if (it is OpRow.Header) 0 else 1 }) { row ->
                val placement = Modifier.animateItem(
                    fadeInSpec = motion.defaultEffectsSpec(),
                    placementSpec = motion.defaultSpatialSpec(),
                    fadeOutSpec = motion.fastEffectsSpec(),
                )
                when (row) {
                    is OpRow.Header -> OpGroupHeader(row, placement)
                    is OpRow.Item -> {
                        val op = row.op
                        val name = remember(op, parsed, accent) { highlight(op.name, parsed.tokens, accent) }
                        OpcodeCard(
                            op = op,
                            name = name,
                            shapes = ListItemDefaults.segmentedShapes(row.index, row.count),
                            badgeShape = badgeShapes.getValue(op.category),
                            expanded = op.code in expanded,
                            onToggle = { onToggle(op.code) },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onCopy(op.name)
                            },
                            onCopy = onCopy,
                            modifier = placement
                                .padding(horizontal = 16.dp)
                                .padding(bottom = if (row.index < row.count - 1) ListItemDefaults.SegmentedGap else 0.dp),
                        )
                    }
                }
            }
        }
        FastScroller(
            target = remember(state) { state.asScrollTarget() },
            label = { idx ->
                when (val r = rows.getOrNull(idx)) {
                    is OpRow.Header -> r.first.hex.take(2)
                    is OpRow.Item -> r.op.hex.take(2)
                    null -> null
                }
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, bottom = bottomPadding),
        )
    }
}

@Composable
private fun OpGroupHeader(row: OpRow.Header, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val range = remember(row) {
        val a = "0x%02X".format(row.first.code)
        val b = "0x%02X".format(row.last.lastCode)
        if (row.first.code == row.last.lastCode) a else "$a–$b"
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(row.category.icon, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            row.category.title,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        Text(range, style = MaterialTheme.typography.labelMedium.copy(fontFamily = SmaliMono), color = scheme.outline, maxLines = 1)
    }
}

@Composable
private fun OpcodeCard(
    op: SmaliOpcode,
    name: AnnotatedString,
    shapes: ListItemShapes,
    badgeShape: Shape,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (op.unused) {
        SegmentedListItem(shapes = shapes, modifier = modifier) {
            OpcodeHeadline(op, name, badgeShape, expanded = false, showChevron = false)
        }
        return
    }
    val motion = MaterialTheme.motionScheme
    SegmentedListItem(
        selected = expanded,
        onClick = onToggle,
        onLongClick = onLongPress,
        onLongClickLabel = "Копировать мнемонику",
        shapes = shapes,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxWidth()) {
            OpcodeHeadline(op, name, badgeShape, expanded, showChevron = true)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                OpcodeDetails(op, onCopy)
            }
        }
    }
}

@Composable
private fun OpcodeHeadline(op: SmaliOpcode, name: AnnotatedString, badgeShape: Shape, expanded: Boolean, showChevron: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val content = LocalContentColor.current
    Row(verticalAlignment = Alignment.Top) {
        HexBadge(op, badgeShape, expanded)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = if (op.unused) MnemonicStyle.copy(fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic) else MnemonicStyle,
                    color = if (op.unused) scheme.outline else content,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                FormatChip(op.format, expanded)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                op.description,
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = 0.78f),
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.animateContentSize(motion.defaultSpatialSpec()),
            )
        }
        if (showChevron) {
            val rotation by animateFloatAsState(if (expanded) 180f else 0f, motion.defaultSpatialSpec(), label = "chevron")
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Подробнее",
                tint = content.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 2.dp).size(22.dp).rotate(rotation),
            )
        }
    }
}

@Composable
private fun HexBadge(op: SmaliOpcode, shape: Shape, expanded: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val (container, content) = scheme.badgeColors(op)
    val bg = animateColorAsState(if (expanded) scheme.primary else container, motion.defaultEffectsSpec(), label = "badgeBg")
    val fg by animateColorAsState(if (expanded) scheme.onPrimary else content, motion.defaultEffectsSpec(), label = "badgeFg")
    val rotation = animateFloatAsState(if (expanded) 45f else 0f, motion.slowSpatialSpec(), label = "badgeRot")
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation.value
                    this.shape = shape
                    clip = true
                }
                .drawBehind { drawRect(bg.value) },
        )
        Text(
            op.hex,
            fontFamily = SmaliMono,
            fontWeight = FontWeight.Bold,
            fontSize = if (op.hex.length > 2) 11.sp else 17.sp,
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun FormatChip(format: String, expanded: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = if (expanded) scheme.surface else scheme.surfaceContainerHighest) {
        Text(
            format,
            style = TextStyle(fontFamily = SmaliMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpcodeDetails(op: SmaliOpcode, onCopy: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Синтаксис", style = MaterialTheme.typography.labelMedium, color = LocalContentColor.current.copy(alpha = 0.7f))
            SmaliCode(op.syntax, container = scheme.surface)
        }
        InfoLine(Icons.Rounded.Straighten, "Формат ${op.format}", SmaliFormats[op.format] ?: "—", scheme.primary)
        op.throws?.let { t ->
            val text = remember(t) { t.split("; ").joinToString("\n") { "• $it" } }
            InfoLine(Icons.Rounded.Warning, "Исключения", text, scheme.error)
        }
        op.since?.let { InfoLine(Icons.Rounded.NewReleases, "Доступность", it, scheme.tertiary) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onCopy(op.name) }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.ContentCopy, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Копировать мнемонику", maxLines = 1)
            }
            OutlinedButton(onClick = { onCopy(op.syntax) }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Code, null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Пример", maxLines = 1)
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, title: String, text: String, accent: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = accent, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent)
            Text(text, style = MaterialTheme.typography.bodySmall, color = LocalContentColor.current.copy(alpha = 0.85f))
        }
    }
}

// ------------------------------------------------------------------ Справка

@Composable
private fun ReferenceList(
    rows: List<RefRow>,
    state: LazyListState,
    bottomPadding: Dp,
    keyboard: SoftwareKeyboardController?,
    focus: FocusManager,
    onCopy: (String) -> Unit,
) {
    HideKeyboardOnScroll(state, keyboard, focus)
    if (rows.isEmpty()) {
        ToolsEmptyState(
            polygon = MaterialShapes.Clover4Leaf,
            icon = Icons.Rounded.SearchOff,
            title = "Ничего не найдено",
            text = "В справке нет совпадений. Попробуйте, например, «.locals», «wide» или «static».",
        )
        return
    }
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = state,
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hint", contentType = 2) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.TouchApp, null, tint = scheme.outline, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Нажмите на пункт, чтобы скопировать пример", style = MaterialTheme.typography.bodySmall, color = scheme.outline)
                }
            }
            itemsIndexed(rows, key = { _, r -> r.key }, contentType = { _, r -> if (r is RefRow.Header) 0 else 1 }) { _, row ->
                when (row) {
                    is RefRow.Header -> RefSectionHeader(row.section)
                    is RefRow.Item -> RefEntryItem(
                        entry = row.entry,
                        shapes = ListItemDefaults.segmentedShapes(row.index, row.count),
                        onClick = { onCopy(row.entry.example ?: row.entry.term) },
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = if (row.index < row.count - 1) ListItemDefaults.SegmentedGap else 0.dp),
                    )
                }
            }
        }
        FastScroller(
            target = remember(state) { state.asScrollTarget() },
            label = { idx -> rows.getOrNull((idx - RefListOffset).coerceAtLeast(0))?.section?.title?.take(1) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, bottom = bottomPadding),
        )
    }
}

@Composable
private fun RefSectionHeader(section: SmaliRefSection) {
    val scheme = MaterialTheme.colorScheme
    val polygon = remember(section.id) { sectionPolygon(section.id) }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShapeBadge(
            polygon = polygon,
            icon = sectionIcon(section.id),
            container = scheme.tertiaryContainer,
            content = scheme.onTertiaryContainer,
            size = 44.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
            Text(section.intro, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RefEntryItem(entry: SmaliRefEntry, shapes: ListItemShapes, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = modifier,
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                entry.example?.let { SmaliCode(it, container = scheme.surfaceContainerHigh) }
            }
        },
    ) {
        Text(entry.term, style = MnemonicStyle.copy(fontSize = 15.sp), color = scheme.primary)
    }
}

// ------------------------------------------------------------------ Код с подсветкой

@Composable
private fun SmaliCode(code: String, container: Color, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val text = remember(code, scheme) { highlightSmali(code, scheme) }
    Surface(shape = RoundedCornerShape(14.dp), color = container, modifier = modifier.fillMaxWidth()) {
        Text(
            text,
            style = CodeStyle,
            color = scheme.onSurface,
            softWrap = false,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

private val SmaliLead = Regex("""(?m)^\s*(\.?[a-z][\w\-/]*)""")
private val SmaliTokens = Regex(
    "(#.*)" +                                                        // 1 комментарий
        "|(\"(?:[^\"\\\\]|\\\\.)*\")" +                               // 2 строка
        "|(\\[*L[\\w/\$\\-]+;)" +                                      // 3 дескриптор класса
        "|(\\b[vp]\\d+\\b)" +                                         // 4 регистр
        "|((?<![\\w;>)\\]\"]):[A-Za-z_]\\w*)" +                        // 5 метка
        "|(-?\\b0x[0-9a-fA-F]+L?\\b|\\b\\d+\\b)" +                     // 6 число
        "|\\b(public|private|protected|static|final|constructor|runtime|build|system|abstract|native|synchronized)\\b", // 7 модификатор
)

private fun highlightSmali(code: String, scheme: ColorScheme): AnnotatedString = buildAnnotatedString {
    append(code)
    val lead = SpanStyle(color = scheme.primary, fontWeight = FontWeight.Bold)
    val comment = SpanStyle(color = scheme.outline, fontStyle = FontStyle.Italic)
    val string = SpanStyle(color = scheme.tertiary)
    val type = SpanStyle(color = scheme.secondary)
    val register = SpanStyle(color = scheme.tertiary, fontWeight = FontWeight.Bold)
    val label = SpanStyle(color = scheme.primary)
    val number = SpanStyle(color = scheme.tertiary)
    val keyword = SpanStyle(color = scheme.secondary, fontWeight = FontWeight.Bold)
    for (m in SmaliLead.findAll(code)) {
        val g = m.groups[1] ?: continue
        addStyle(lead, g.range.first, g.range.last + 1)
    }
    for (m in SmaliTokens.findAll(code)) {
        val style = when {
            m.groups[1] != null -> comment
            m.groups[2] != null -> string
            m.groups[3] != null -> type
            m.groups[4] != null -> register
            m.groups[5] != null -> label
            m.groups[6] != null -> number
            else -> keyword
        }
        addStyle(style, m.range.first, m.range.last + 1)
    }
}

/** Подсвечивает вхождения слов запроса в мнемонике. */
private fun highlight(text: String, tokens: List<String>, style: SpanStyle): AnnotatedString {
    if (tokens.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    return buildAnnotatedString {
        append(text)
        for (t in tokens) {
            var from = 0
            while (true) {
                val i = lower.indexOf(t, from)
                if (i < 0) break
                addStyle(style, i, i + t.length)
                from = i + t.length
            }
        }
    }
}
