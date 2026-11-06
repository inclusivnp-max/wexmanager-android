package client.wexmanager.feature.terminal

import android.app.Application
import android.content.Context
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.R
import client.wexmanager.feature.browser.copyToSystemClipboard


private val QUICK = listOf("ls -la", "cd ..", "pwd", "|", "/", "-", "~", "&&", ">", "*", "grep ", "cat ", "du -sh *", "df -h")
private const val HOLD = "\u200B"
private const val MAX_CLIP_CHARS = 200_000

private val ANSI = intArrayOf(
    0xFF000000.toInt(),
    0xFFAA0000.toInt(),
    0xFF00AA00.toInt(),
    0xFFAA5500.toInt(),
    0xFF0000AA.toInt(),
    0xFFAA00AA.toInt(),
    0xFF00AAAA.toInt(),
    0xFFAAAAAA.toInt(),
    0xFF555555.toInt(),
    0xFFFF5555.toInt(),
    0xFF55FF55.toInt(),
    0xFFFFFF55.toInt(),
    0xFF5555FF.toInt(),
    0xFFFF55FF.toInt(),
    0xFF55FFFF.toInt(),
    0xFFFFFFFF.toInt(),
)

@Composable
fun TerminalScreen(cwd: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: TerminalViewModel = viewModel { TerminalViewModel(app, cwd) }
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    val mono = remember { FontFamily(Font(R.font.jetbrains_mono), Font(R.font.jetbrains_mono_bold, FontWeight.Bold)) }
    val fontSp = state.fontSp.coerceIn(TERMINAL_MIN_FONT_SP, TERMINAL_MAX_FONT_SP)
    val textStyle = remember(mono, fontSp) {
        TextStyle(
            fontFamily = mono,
            fontSize = fontSp.sp,
            lineHeight = (fontSp + 3).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
    }
    val measurer = rememberTextMeasurer()
    val cell = remember(textStyle, measurer) { measurer.measure("0", textStyle).size }
    val density = LocalDensity.current
    val rowHeight = with(density) { cell.height.coerceAtLeast(1).toDp() }
    var field by remember { mutableStateOf(TextFieldValue(HOLD, TextRange(HOLD.length))) }
    var ctrl by remember { mutableStateOf(false) }
    var composing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val followHolder = remember { mutableStateOf(true) }
    var follow by followHolder
    val stick = remember(listState, followHolder) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) followHolder.value = atBottom(listState)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                followHolder.value = atBottom(listState)
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(state.revision) {
        if (!follow) return@LaunchedEffect
        val last = state.scrollback.size + state.screen.size - 1
        if (last >= 0) listState.scrollToItem(last)
    }
    LaunchedEffect(state.error == null) {
        if (state.error == null) runCatching { focus.requestFocus() }
    }

    fun showKeyboard() {
        focus.requestFocus()
        keyboard?.show()
    }

    Scaffold(
        modifier = Modifier.onPreviewKeyEvent { event -> onHardwareKey(event, vm, composing) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") } },
                title = {
                    Column {
                        Text("Терминал")
                        Text(
                            state.cwd,
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.StartEllipsis,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { copyOutput(context, vm.textForCopy()) }) { Icon(Icons.Rounded.ContentCopy, "Копировать") }
                    IconButton(onClick = vm::clear) { Icon(Icons.Rounded.ClearAll, "Очистить") }
                    IconButton(onClick = vm::restart) { Icon(Icons.Rounded.RestartAlt, "Перезапустить") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surfaceContainer),
            )
        },
        containerColor = scheme.surfaceContainerLowest,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()) {
            KeepTerminalScreenOn(state.keepScreenOn)
            val error = state.error
            if (error != null) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(error, color = scheme.error, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                BoxWithGrid(
                    charWidth = cell.width.coerceAtLeast(1),
                    charHeight = cell.height.coerceAtLeast(1),
                    onGrid = vm::resize,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    if (!state.alive && !state.exited && state.screen.isEmpty()) {
                        Text(
                            "Запуск…",
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(stick)) {
                        items(state.scrollback, key = { it.id }) { line ->
                            TermRowText(line.spans, textStyle, rowHeight, scheme.onSurface, scheme.surfaceContainerLowest, scheme.surfaceContainerLowest, scheme.primary, ::showKeyboard)
                        }
                        items(state.screen.size, key = { "s$it" }) { index ->
                            val line = state.screen.getOrNull(index) ?: return@items
                            TermRowText(line.spans, textStyle, rowHeight, scheme.onSurface, scheme.surfaceContainerLowest, scheme.surfaceContainerLowest, scheme.primary, ::showKeyboard)
                        }
                    }
                }
            }
            if (state.exited) {
                Text(
                    "Процесс завершён",
                    color = scheme.tertiary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            Surface(color = scheme.surfaceContainer) {
                Column(Modifier.navigationBarsPadding()) {
                    TerminalFontRow(
                        fontSp = fontSp,
                        keepScreenOn = state.keepScreenOn,
                        onFont = vm::setFontSp,
                        onKeep = vm::setKeepScreenOn,
                    )
                    if (error == null) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            item { QuickKey("Esc") { vm.esc() } }
                            item { QuickKey("Tab") { vm.tab() } }
                            item { QuickKey("Ctrl", ctrl) { ctrl = !ctrl } }
                            item { QuickKey("Ctrl+C") { ctrl = false; vm.ctrlC() } }
                            item { QuickKey("←") { vm.arrow('D') } }
                            item { QuickKey("↓") { vm.arrow('B') } }
                            item { QuickKey("↑") { vm.arrow('A') } }
                            item { QuickKey("→") { vm.arrow('C') } }
                            item { QuickKey("Home") { vm.home() } }
                            item { QuickKey("End") { vm.end() } }
                            item { QuickKey("↵") { vm.enter() } }
                        }
                        LazyRow(
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(QUICK) { snippet ->
                                QuickKey(snippet) { vm.type(snippet, ctrl = false) }
                            }
                        }
                        BasicTextField(
                            value = field,
                            onValueChange = { next ->
                                composing = next.composition != null
                                if (next.composition != null) {
                                    field = next
                                    return@BasicTextField
                                }
                                val edited = applyField(field, next, vm, ctrl)
                                if (edited) ctrl = false
                                field = TextFieldValue(HOLD, TextRange(HOLD.length))
                            },
                            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                            cursorBrush = SolidColor(Color.Transparent),
                            singleLine = false,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Text,
                            ),
                            modifier = Modifier
                                .focusRequester(focus)
                                .fillMaxWidth()
                                .height(1.dp)
                                .alpha(0f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeepTerminalScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun TerminalFontRow(
    fontSp: Int,
    keepScreenOn: Boolean,
    onFont: (Int) -> Unit,
    onKeep: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onFont(fontSp - 1) }, enabled = fontSp > TERMINAL_MIN_FONT_SP) {
            Icon(Icons.Rounded.TextDecrease, "Уменьшить шрифт")
        }
        Text(
            fontSp.toString(),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp).semantics { contentDescription = "Размер шрифта $fontSp" },
        )
        IconButton(onClick = { onFont(fontSp + 1) }, enabled = fontSp < TERMINAL_MAX_FONT_SP) {
            Icon(Icons.Rounded.TextIncrease, "Увеличить шрифт")
        }
        Text(
            "Не гасить экран",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp).clearAndSetSemantics {},
        )
        Switch(
            checked = keepScreenOn,
            onCheckedChange = onKeep,
            modifier = Modifier.semantics { contentDescription = "Не гасить экран" },
        )
    }
}

@Composable
private fun BoxWithGrid(
    charWidth: Int,
    charHeight: Int,
    onGrid: (Int, Int) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        if (width >= charWidth * 2 && height >= charHeight * 2) {
            val cols = (width / charWidth).coerceIn(2, 500)
            val rows = (height / charHeight).coerceIn(2, 200)
            LaunchedEffect(cols, rows) { onGrid(cols, rows) }
        }
        content()
    }
}

@Composable
private fun TermRowText(
    spans: List<TermSpan>,
    style: TextStyle,
    rowHeight: Dp,
    defaultFg: Color,
    defaultBg: Color,
    cursorFg: Color,
    cursorBg: Color,
    onTap: () -> Unit,
) {
    val annotated = buildAnnotatedString {
        for (span in spans) {
            var fg = termColor(span.fg, foreground = true, defaultFg, defaultBg)
            var bg = termColor(span.bg, foreground = false, defaultFg, defaultBg)
            if (span.reverse) {
                val swap = fg
                fg = bg
                bg = swap
            }
            if (span.cursor) {
                fg = cursorFg
                bg = cursorBg
            }
            val paintBg = span.cursor || span.reverse || span.bg != COLOR_DEFAULT
            withStyle(
                SpanStyle(
                    color = fg,
                    background = if (paintBg) bg else Color.Transparent,
                    fontWeight = if (span.bold) FontWeight.Bold else FontWeight.Normal,
                ),
            ) {
                append(span.text)
            }
        }
    }
    Text(
        annotated,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clipToBounds()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap),
    )
}

private fun termColor(value: Int, foreground: Boolean, defaultFg: Color, defaultBg: Color): Color {
    if (value == COLOR_DEFAULT) return if (foreground) defaultFg else defaultBg
    if (value in 0..15) return argb(ANSI[value])
    if (value and 0x01000000 != 0) {
        return Color(
            red = (value shr 16) and 0xFF,
            green = (value shr 8) and 0xFF,
            blue = value and 0xFF,
        )
    }
    return if (foreground) defaultFg else defaultBg
}

private fun argb(value: Int): Color = Color(
    red = (value shr 16) and 0xFF,
    green = (value shr 8) and 0xFF,
    blue = value and 0xFF,
    alpha = (value ushr 24) and 0xFF,
)

private fun applyField(previous: TextFieldValue, next: TextFieldValue, vm: TerminalViewModel, ctrl: Boolean): Boolean {
    if (next.composition != null) return false
    if (previous.composition != null) {
        val committed = next.text.replace(HOLD, "").replace('\n', '\r')
        if (committed.isNotEmpty()) vm.type(committed, ctrl)
        return committed.isNotEmpty()
    }
    val oldText = previous.text.replace(HOLD, "")
    val newText = next.text.replace(HOLD, "")
    if (oldText == newText) {
        if (previous.text.contains(HOLD) && !next.text.contains(HOLD)) vm.backspace()
        return false
    }
    val prefix = oldText.commonPrefixWith(newText).length
    val deleted = oldText.length - prefix
    val inserted = newText.substring(prefix).replace('\n', '\r')
    repeat(deleted) { vm.backspace() }
    if (inserted.isNotEmpty()) vm.type(inserted, ctrl)
    return deleted > 0 || inserted.isNotEmpty()
}

private fun onHardwareKey(
    event: androidx.compose.ui.input.key.KeyEvent,
    vm: TerminalViewModel,
    composing: Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val code = event.nativeKeyEvent.keyCode
    val ctrl = event.nativeKeyEvent.isCtrlPressed
    if (composing && !ctrl) return false
    if (ctrl && code in AndroidKeyEvent.KEYCODE_A..AndroidKeyEvent.KEYCODE_Z) {
        vm.write(byteArrayOf((code - AndroidKeyEvent.KEYCODE_A + 1).toByte()))
        return true
    }
    when (code) {
        AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> vm.enter()
        AndroidKeyEvent.KEYCODE_TAB -> vm.tab()
        AndroidKeyEvent.KEYCODE_ESCAPE -> vm.esc()
        AndroidKeyEvent.KEYCODE_DEL -> vm.backspace()
        AndroidKeyEvent.KEYCODE_DPAD_UP -> vm.arrow('A')
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> vm.arrow('B')
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> vm.arrow('C')
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> vm.arrow('D')
        AndroidKeyEvent.KEYCODE_MOVE_HOME -> vm.home()
        AndroidKeyEvent.KEYCODE_MOVE_END -> vm.end()
        else -> return false
    }
    return true
}

private fun atBottom(listState: androidx.compose.foundation.lazy.LazyListState): Boolean {
    val info = listState.layoutInfo
    if (info.totalItemsCount == 0) return true
    val last = info.visibleItemsInfo.lastOrNull() ?: return false
    if (last.index < info.totalItemsCount - 1) return false
    return last.offset + last.size <= info.viewportEndOffset + last.size / 2
}

private fun copyOutput(context: Context, text: String) {
    val clip = if (text.length > MAX_CLIP_CHARS) text.takeLast(MAX_CLIP_CHARS) else text
    runCatching { copyToSystemClipboard(context, clip) }
        .onFailure { Toast.makeText(context, "Не удалось скопировать: ${it.message}", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun QuickKey(text: String, selected: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primary else scheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) scheme.onPrimary else scheme.onSurface,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

