package client.wexmanager.feature.editor

import android.app.Application
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardTab
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.WrapText
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EditOff
import androidx.compose.material.icons.rounded.FindReplace
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Preview
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.SubdirectoryArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import client.wexmanager.feature.editor.golang.GoRunScope
import client.wexmanager.feature.editor.golang.GoSyntaxViewModel
import client.wexmanager.feature.editor.golang.GoSyntaxPhase
import client.wexmanager.feature.editor.golang.GoIconButton
import client.wexmanager.feature.editor.golang.GoFormatButton
import client.wexmanager.feature.editor.golang.GoSyntaxButton
import client.wexmanager.feature.editor.golang.GoProblemsSheet
import client.wexmanager.feature.editor.golang.GoProblem
import client.wexmanager.feature.editor.golang.nextGoProblem
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import client.wexmanager.feature.editor.golang.GoAction
import client.wexmanager.feature.editor.golang.GoError
import client.wexmanager.feature.editor.golang.GoRunnerViewModel
import client.wexmanager.feature.editor.golang.GoToolbarActions
import client.wexmanager.feature.editor.golang.GoRunPanel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import client.wexmanager.R
import client.wexmanager.appContainer
import client.wexmanager.ui.components.TextInputDialog
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.FormatSize
import client.wexmanager.core.settings.EditorFont
import client.wexmanager.core.settings.EditorThemeOption
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.navigation.Route
import client.wexmanager.ui.theme.LocalAppSettings
import client.wexmanager.ui.theme.LocalIsDarkTheme
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import client.wexmanager.feature.editor.lang.Language
import client.wexmanager.feature.editor.lang.Languages
import java.io.File

private val SYMBOLS = listOf("{", "}", "(", ")", "[", "]", ";", "\"", "'", "=", "<", ">", "/", "\\", "|", "&", "!", ":", ".", ",", "+", "-", "*", "#", "$", "_", "?", "@", "%", "^", "~", "`")

@Composable
fun EditorScreen(
    route: Route.Editor,
    onBack: () -> Unit,
    onOpenFile: (Route.Editor) -> Unit = {},
    onShowDiff: (left: String, right: String) -> Unit = { _, _ -> },
    onPreview: (path: String, base: String?) -> Unit = { _, _ -> },
    tabsOverride: EditorTabs? = null,
    sessionKey: String = "files",
    navigationContent: (@Composable () -> Unit)? = null,
    workspaceRoot: String? = null,
    onQuickOpen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val goVm: GoRunnerViewModel = viewModel(key = "go-runner:$sessionKey") { GoRunnerViewModel(context.applicationContext as Application) }
    val goState by goVm.state.collectAsStateWithLifecycle()
    val syntaxVm: GoSyntaxViewModel = viewModel(key = "go-syntax:$sessionKey") { GoSyntaxViewModel(context.applicationContext as Application) }
    val syntaxState by syntaxVm.state.collectAsStateWithLifecycle()
    val ideMode = workspaceRoot != null
    var goPanelOpen by remember { mutableStateOf(false) }
    var goPreparing by remember { mutableStateOf(false) }
    val tabs = tabsOverride ?: context.appContainer.editorTabs
    // До подписки: иначе первый кадр пустой, пока LaunchedEffect не откроет вкладку.
    remember(route.path, route.archive, route.entry, route.binarySource) { tabs.open(route) }
    val tabList by tabs.tabs.collectAsStateWithLifecycle()
    val activeId by tabs.activeId.collectAsStateWithLifecycle()
    val notice by tabs.notice.collectAsStateWithLifecycle()
    val allDirtyPaths by context.appContainer.dirtyEditors.collectAsStateWithLifecycle()
    val dirtyPaths = tabList.filter { it.route.path in allDirtyPaths && tabs.viewModel(it.id).state.value.modified }.map { it.route.path }.toSet()
    // Виджеты вкладок живут, пока открыт экран: переключение не перечитывает файл и не теряет правки.
    val editorViews = remember { mutableMapOf<Long, WexCodeEditor>() }
    DisposableEffect(Unit) {
        onDispose {
            val views = editorViews.toMap()
            editorViews.clear()
            views.forEach { (id, view) ->
                if (view.tag != "released") runCatching { tabs.viewModel(id).stash(view.text.toString()) }
                releaseEditor(view)
            }
        }
    }
    val active = tabList.firstOrNull { it.id == activeId }
    val settings = LocalAppSettings.current
    val scheme = MaterialTheme.colorScheme
    val dark = LocalIsDarkTheme.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        tabs.consumeNotice()
    }
    if (active == null) {
        Box(Modifier.fillMaxSize().background(scheme.surface))
        return
    }
    val page = active.route
    val vm = tabs.viewModel(active.id)
    val state by vm.state.collectAsStateWithLifecycle()
    val isGoFile = state.fileName.endsWith(".go", ignoreCase = true)
    // selectFile по имени файла из route, не из state: на первом кадре state может быть от старой вкладки.
    LaunchedEffect(page.path) { if (page.path.endsWith(".go", ignoreCase = true)) goVm.selectFile(page.path) }
    /** Вкладка, которую закрываем после диалога. Не привязана к активной: смена вкладки его не сбрасывает. */
    var closingId by remember { mutableStateOf<Long?>(null) }
    /** Хвост «Закрыть другие» / «Закрыть все»: следующая грязная вкладка после ответа в диалоге. */
    var closeQueue by remember { mutableStateOf<List<Long>>(emptyList()) }
    var closeBatch by remember { mutableIntStateOf(0) }
    var closeBusy by remember { mutableStateOf(false) }
    var tabMenu by remember { mutableStateOf(false) }

    fun closeTabNow(id: Long) {
        if (tabs.tabs.value.none { it.id == id }) return
        val last = tabs.tabs.value.size <= 1
        val view = editorViews.remove(id)
        val tab = tabs.tabs.value.firstOrNull { it.id == id }
        if (view != null && tab != null) {
            runCatching {
                val lineNo = view.cursor.leftLine
                if (lineNo > 0 && !tab.route.path.startsWith(context.cacheDir.path)) {
                    context.appContainer.userData.saveEditorPosition(tab.route.path, lineNo)
                }
                tabs.viewModel(id).stash(view.text.toString())
            }
            releaseEditor(view)
        }
        tabs.close(id)
        if (last) onBack()
    }

    fun continueClose() {
        while (true) {
            val open = tabs.tabs.value
            val rest = closeQueue.filter { id -> open.any { it.id == id } }
            val next = rest.firstOrNull() ?: run {
                closeQueue = emptyList()
                closingId = null
                return
            }
            closeQueue = rest.drop(1)
            if (tabs.viewModel(next).state.value.modified) {
                closingId = next
                return
            }
            val last = open.size <= 1
            closeTabNow(next)
            if (last) return
        }
    }

    fun closeTab(id: Long) {
        closeBatch++
        closeQueue = emptyList()
        val tab = tabs.tabs.value.firstOrNull { it.id == id } ?: return
        if (tab.route.path in dirtyPaths) {
            closingId = id
            return
        }
        if (closingId == id) closingId = null
        closeTabNow(id)
    }

    /** Чистые вкладки закрываются сразу. Грязные — тем же диалогом, что и крестик, по одной. */
    fun closeTabs(ids: List<Long>) {
        if (ids.isEmpty() || closeBusy) return
        val pending = closingId
        if (pending != null && tabs.tabs.value.any { it.id == pending }) return
        closeBatch++
        val wanted = ids.toHashSet()
        val currentId = active.id
        val open = tabs.tabs.value
        val dirtySet = dirtyPaths
        val dirtyIds = ArrayList<Long>()
        var currentDirty = false
        for (tab in open) {
            if (tab.id !in wanted) continue
            if (tab.route.path in dirtySet) {
                if (tab.id == currentId) currentDirty = true else dirtyIds.add(tab.id)
            } else {
                closeTabNow(tab.id)
                if (tabs.tabs.value.isEmpty()) return
            }
        }
        if (currentDirty) dirtyIds.add(0, currentId)
        val still = dirtyIds.filter { id -> tabs.tabs.value.any { it.id == id } }
        if (still.isEmpty()) return
        closeQueue = still.drop(1)
        closingId = still.first()
    }

    var editor by remember(active.id) { mutableStateOf<CodeEditor?>(null) }
    var line by remember(active.id) { mutableIntStateOf(1) }
    var column by remember(active.id) { mutableIntStateOf(1) }
    var canUndo by remember(active.id) { mutableStateOf(false) }
    var canRedo by remember(active.id) { mutableStateOf(false) }
    var searchOpen by remember(active.id) { mutableStateOf(false) }
    var menuOpen by remember(active.id) { mutableStateOf(false) }
    var dialog by remember(active.id) { mutableStateOf<String?>(null) }
    /** Что сделать после ответа в диалоге «Сохранить изменения?» (по умолчанию — выйти). */
    var afterUnsaved by remember(active.id) { mutableStateOf<(() -> Unit)?>(null) }
    var toolsOpen by remember(active.id) { mutableStateOf(false) }
    var structureOpen by remember(active.id) { mutableStateOf(false) }
    var problemsOpen by remember(active.id) { mutableStateOf(false) }
    var goFormatting by remember(active.id) { mutableStateOf(false) }
    /** Ошибка сборки, к которой нужно перейти после открытия вкладки (файл, строка, столбец). */
    var pendingGoError by remember(active.id) { mutableStateOf<Pair<GoError, String>?>(null) }
    var recentOpen by remember(active.id) { mutableStateOf(false) }
    var minimap by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    // Пока тема не применена и нет первой подсветки, редактор закрыт фоном (без белой вспышки Sora).
    var themed by remember { mutableStateOf(false) }
    var painted by remember(active.id) { mutableStateOf(false) }
    // Большой файл: подсветка и дорогие украшения выключены, пока пользователь сам не включит.
    var heavyHighlight by remember(active.id) { mutableStateOf(false) }
    val bigFile = state.sizeBytes > BIG_FILE_BYTES || state.lineCountHint > BIG_FILE_LINES

    LaunchedEffect(vm) {
        vm.events.collect { ev ->
            when (ev) {
                is EditorEvent.Message -> scope.launch {
                    val r = snackbar.showSnackbar(ev.text, actionLabel = ev.actionLabel, withDismissAction = ev.action != null,
                        duration = if (ev.action != null) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short)
                    if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) ev.action?.invoke()
                }
                EditorEvent.Saved -> Unit
                EditorEvent.DiskChanged -> dialog = "diskchanged"
            }
        }
    }
    // Грамматики грузим один раз и вне главного потока.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { TextMateSetup.ensure(context) }
        ready = true
    }

    val typeface = remember(settings.editorFont) {
        if (settings.editorFont == EditorFont.JetBrainsMono) ResourcesCompat.getFont(context, R.font.jetbrains_mono) ?: Typeface.MONOSPACE
        else Typeface.MONOSPACE
    }

    // Тема подсветки (+ подгонка цветов под Material 3). Ключи — стабильные ARGB, а не объект схемы,
    // иначе анимация цветов и любая рекомпозиция перезапускали бы подсветку.
    val primaryArgb = scheme.primary.toArgb()
    val surfaceArgb = scheme.surface.toArgb()
    LaunchedEffect(editor, ready, settings.editorTheme, primaryArgb, surfaceArgb, dark) {
        val e = editor ?: return@LaunchedEffect
        if (!ready) return@LaunchedEffect
        val wex = e as? WexCodeEditor
        TextMateSetup.applyTheme(settings.editorTheme, scheme, dark)
        val cs = TextMateSetup.newColorScheme()
        TextMateSetup.release(e.colorScheme)
        e.colorScheme = cs // attachEditor сбрасывает цвета — правим после присвоения
        TextMateSetup.styleScheme(cs, scheme, settings.editorTheme == EditorThemeOption.MaterialYou)
        SyntaxPalette.apply(cs, context, settings.editorTheme, scheme, dark)
        wex?.applyScrollbarColors(scheme.outline.copy(alpha = 0.7f).toArgb(), scheme.primary.toArgb())
        wex?.folding?.markerColor = cs.getColor(io.github.rosemoe.sora.widget.schemes.EditorColorScheme.LINE_NUMBER)
        wex?.themeApplied = true
        e.invalidate()
        themed = true
    }
    // Язык: лексеры и подсказки WEXdesktop (разбор построчный и инкрементальный, в фоне).
    LaunchedEffect(editor, state.language, settings.editorTabSize, settings.editorUseSpaces, settings.editorAutoComplete, bigFile, heavyHighlight) {
        val e = editor ?: return@LaunchedEffect
        val wex = e as? WexCodeEditor
        val langKey = "${state.language.id}|${settings.editorTabSize}|${settings.editorUseSpaces}|${settings.editorAutoComplete}|$bigFile|$heavyHighlight|${page.path}"
        val current = e.editorLanguage as? WexLanguage
        if (wex != null && wex.langKey == langKey && current != null) {
            wex.bindFolds(current, enabled = !bigFile)
            return@LaunchedEffect
        }
        val lang = WexLanguage(
            language = state.language,
            highlight = !bigFile || heavyHighlight,
            tabSize = settings.editorTabSize,
            useSpaces = settings.editorUseSpaces,
            autoPopup = settings.editorAutoComplete,
            baseDir = File(page.path).parentFile,
        )
        wex?.bindFolds(lang, enabled = !bigFile)
        e.setEditorLanguage(lang)
        wex?.langKey = langKey
    }
    var keepLine by remember { mutableStateOf<Int?>(null) }
    // Текст из файла (+ возврат к строке, на которой файл закрыли в прошлый раз).
    LaunchedEffect(editor, state.textVersion) {
        val e = editor ?: return@LaunchedEffect
        if (state.textVersion == 0) return@LaunchedEffect
        val wex = e as? WexCodeEditor
        // Тот же виджет вкладки уже держит этот текст (и несохранённые правки). Не перечитывать.
        if (wex != null && wex.loadedTextVersion == state.textVersion) {
            canUndo = e.canUndo()
            canRedo = e.canRedo()
            line = e.cursor.leftLine + 1
            column = e.cursor.leftColumn + 1
            painted = true
            return@LaunchedEffect
        }
        wex?.applyingText = true
        val lang = e.editorLanguage as? WexLanguage
        val passesBefore = lang?.analysisPasses ?: 0
        keepLine = if (state.textVersion > 1) e.cursor.leftLine else null
        e.setText(state.text)
        e.diagnostics = null
        wex?.loadedTextVersion = state.textVersion
        wex?.applyingText = false
        canUndo = false
        canRedo = false
        if (!painted) {
            // Первая раскраска (разбор идёт в фоне): ждём её, но не дольше 400 мс.
            // Ждём конца разбора загруженного текста (стили приходят следующим кадром), не дольше 500 мс.
            withTimeoutOrNull(500) {
                // Проход по пустому документу (новый язык ставится раньше текста) не в счёт.
                while (lang != null && (lang.analysisPasses <= passesBefore || lang.analyzedLines != e.lineCount)) delay(16)
                delay(32)
            }
            painted = true
        }
        if (state.textVersion > 1 && keepLine != null) {
            // Перечитали файл (изменён снаружи, другая кодировка) — остаёмся на той же строке.
            val l = keepLine!!.coerceIn(0, e.lineCount - 1)
            e.jumpToLine(l)
            e.setSelection(l, 0)
        }
        if (state.textVersion == 1) {
            val saved = context.appContainer.userData.data.value.editorPositions[page.path]
            if (saved != null && saved in 1 until e.lineCount) {
                e.jumpToLine(saved)
                e.setSelection(saved, 0)
            }
        }
    }
    // Настройки отображения.
    // Размер шрифта — отдельно: иначе любая смена настроек сбрасывала бы масштаб, выставленный щипком.
    LaunchedEffect(editor, settings.editorFontSize) {
        editor?.setTextSize(settings.editorFontSize)
    }
    // Ключи — только то, что применяется: смена шрифта, переноса или табуляции перестраивает раскладку
    // всего текста, и делать это из-за посторонних настроек нельзя.
    LaunchedEffect(
        editor, state.readOnly, minimap, typeface, bigFile, settings.editorWordWrap, settings.editorLineNumbers,
        settings.editorPinLineNumbers, settings.editorHighlightLine, settings.editorTabSize, settings.editorBracketPairs,
        settings.editorShowWhitespace, settings.editorStickyScroll, settings.editorAutoIndent,
    ) {
        val e = editor ?: return@LaunchedEffect
        if (e.typefaceText !== typeface) e.typefaceText = typeface
        if (e.typefaceLineNumber !== typeface) e.typefaceLineNumber = typeface
        if (e.isWordwrap != settings.editorWordWrap) e.isWordwrap = settings.editorWordWrap
        e.isLineNumberEnabled = settings.editorLineNumbers
        e.setPinLineNumber(settings.editorPinLineNumbers)
        e.isHighlightCurrentLine = settings.editorHighlightLine
        if (e.tabWidth != settings.editorTabSize) e.tabWidth = settings.editorTabSize
        e.setHighlightBracketPair(settings.editorBracketPairs && !bigFile)
        e.nonPrintablePaintingFlags = if (settings.editorShowWhitespace) {
            CodeEditor.FLAG_DRAW_WHITESPACE_LEADING or CodeEditor.FLAG_DRAW_WHITESPACE_INNER or
                CodeEditor.FLAG_DRAW_WHITESPACE_TRAILING or CodeEditor.FLAG_DRAW_LINE_SEPARATOR
        } else 0
        e.isEditable = !state.readOnly
        e.props.stickyScroll = settings.editorStickyScroll && !bigFile
        e.props.autoIndent = settings.editorAutoIndent
        e.props.symbolPairAutoCompletion = true
        e.props.showMinimap = minimap
        e.props.enableRoundTextBackground = true
        e.invalidate()
    }

    LaunchedEffect(goState.running, goState.logPath) {
        if (!goState.running && goState.logPath != null) {
            // go mod tidy / console commands can change already-open buffers on disk.
            tabList.forEach { tab -> tabs.viewModel(tab.id).checkDisk() }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.checkDisk() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (!settings.editorAutoSave) return@LifecycleEventEffect
        editorViews.forEach { (id, view) ->
            val model = runCatching { tabs.viewModel(id) }.getOrNull() ?: return@forEach
            val st = model.state.value
            if (st.modified && !st.readOnly && view.tag != "released") model.save(view.text.toString())
        }
    }
    LaunchedEffect(editor, state.textVersion, isGoFile, syntaxState.enabled) {
        val e = editor ?: return@LaunchedEffect
        if (isGoFile && state.textVersion > 0 && (e as? WexCodeEditor)?.loadedTextVersion == state.textVersion) {
            syntaxVm.schedule(page.path) {
                require(e.text.length <= 1_048_576) { "Автопроверка поддерживает Go-файлы до 1 MiB" }
                e.text.toString()
            }
        } else { syntaxVm.pause(); e.diagnostics = null }
    }
    LaunchedEffect(editor, syntaxState) {
        val e = editor ?: return@LaunchedEffect
        val current = syntaxVm.state.value
        e.diagnostics = if (isGoFile && current.path == page.path && current.phase == GoSyntaxPhase.READY) {
            DiagnosticsContainer(false).apply {
                current.problems.filter { it.end > it.start && it.end <= e.text.length }.forEachIndexed { index, problem ->
                    addDiagnostic(DiagnosticRegion(problem.start, problem.end, DiagnosticRegion.SEVERITY_ERROR,
                        index.toLong(), DiagnosticDetail(problem.message)))
                }
            }
        } else null
    }
    DisposableEffect(editor) {
        onDispose { syntaxVm.pause() }
    }
    fun jumpProblem(problem: GoProblem) {
        val e = editor ?: return
        val l = (problem.line - 1).coerceIn(0, e.lineCount - 1)
        val c = (problem.column - 1).coerceIn(0, e.text.getColumnCount(l))
        e.setSelection(l, c)
        e.jumpToLine(l)
        e.requestFocus()
    }

    /** gofmt во встроенном сервисе (офлайн, без Termux): результат анимированно заменяет текст. */
    fun runGofmt() {
        val e = editor ?: return
        if (goFormatting) return
        if (e.text.length > 1_048_576) { scope.launch { snackbar.showSnackbar("Форматирование работает с файлами до 1 MiB") }; return }
        val source = e.text.toString()
        val version = e.text.documentVersion
        val cursor = e.cursor
        goFormatting = true
        scope.launch {
            val result = runCatching { syntaxVm.format(page.path, source) }
            goFormatting = false
            val formatted = result.getOrNull()
            result.exceptionOrNull()?.let { err ->
                snackbar.showSnackbar(err.message ?: "gofmt не смог разобрать файл")
                return@launch
            }
            if (formatted == null || formatted == source) { snackbar.showSnackbar("Код уже отформатирован"); return@launch }
            if (editor !== e || e.text.documentVersion != version) {
                snackbar.showSnackbar("Текст изменился во время форматирования — повторите")
                return@launch
            }
            e.text.replace(0, 0, e.text.lineCount - 1, e.text.getColumnCount(e.text.lineCount - 1), formatted)
            val targetLine = cursor.leftLine.coerceIn(0, e.lineCount - 1)
            e.jumpToLine(targetLine)
            e.requestFocus()
            snackbar.showSnackbar("Отформатировано по gofmt")
        }
    }
    fun navigateProblem(backwards: Boolean) {
        val e = editor ?: return
        nextGoProblem(syntaxVm.state.value.problems, e.cursor.left, backwards)?.let(::jumpProblem)
    }

    // Переход к ошибке сборки: ждём, пока откроется нужная вкладка и в ней появится текст.
    LaunchedEffect(pendingGoError, active.id, state.textVersion, editor) {
        val (error, path) = pendingGoError ?: return@LaunchedEffect
        if (page.path != path) return@LaunchedEffect
        val e = editor ?: return@LaunchedEffect
        if (state.textVersion == 0) return@LaunchedEffect
        val row = (error.line - 1).coerceIn(0, e.lineCount - 1)
        val column = (error.column - 1).coerceIn(0, e.text.getColumnCount(row))
        e.setSelection(row, column)
        e.jumpToLine(row)
        e.requestFocus()
        pendingGoError = null
    }

    val markEdited by rememberUpdatedState {
        vm.markModified()
        val e = editor
        canUndo = e?.canUndo() == true
        canRedo = e?.canRedo() == true
        if (isGoFile && e != null) {
            e.diagnostics = null
            syntaxVm.schedule(page.path) {
                require(e.text.length <= 1_048_576) { "Автопроверка поддерживает Go-файлы до 1 MiB" }
                e.text.toString()
            }
        }
    }
    val moveCaret by rememberUpdatedState { l: Int, c: Int ->
        line = l + 1
        column = c + 1
    }
    DisposableEffect(editor) {
        val e = editor as? WexCodeEditor
        val edited = { markEdited() }
        val caret = { l: Int, c: Int -> moveCaret(l, c) }
        e?.onEdited = edited
        e?.onCaret = caret
        onDispose {
            if (e?.onEdited === edited) e.onEdited = null
            if (e?.onCaret === caret) e.onCaret = null
        }
    }

    fun save(after: () -> Unit = {}, leaving: Boolean = false) {
        val e = editor ?: return
        vm.save(e.text.toString(), leaving) { ok -> if (ok) after() }
    }

    fun startGo(action: GoAction, value: String = "") {
        // FMT — офлайн во встроенном сервисе: Termux не нужен, правит активный буфер.
        if (action == GoAction.FMT) { runGofmt(); return }
        goPanelOpen = true
        if (goPreparing || goState.running) return
        if (isGoFile && (page.archive != null || page.sourceUri != null || page.lockReadOnly)) {
            goVm.message("Сначала сохраните файл или распакуйте весь Go-проект в общую папку через «Сохранить как».")
            return
        }
        // Race guard: если selectFile ещё не отработал для этой вкладки, ждём его — иначе запустится старый файл.
        goPreparing = true
        scope.launch {
            try {
                val project = goVm.ensureSelected(page.path) ?: run {
                    goVm.message("Не удалось открыть проект для ${File(page.path).name}")
                    return@launch
                }
                val requestedPath = project.file.absolutePath
                // Save every open project buffer, not just main.go: package builds use siblings too.
                val dirty = context.appContainer.dirtyEditors.value
                val pending = tabList.filter { tab ->
                    tab.route.path in dirty && tabs.viewModel(tab.id).state.value.modified && tab.route.archive == null && tab.route.sourceUri == null &&
                        File(tab.route.path).toPath().normalize().startsWith(project.root.toPath())
                }
                for (tab in pending) {
                    val model = tabs.viewModel(tab.id)
                    if (model.state.value.readOnly || model.state.value.saving) {
                        goVm.message("Сначала завершите сохранение ${tab.title}")
                        return@launch
                    }
                    val text = editorViews[tab.id]?.text?.toString() ?: model.state.value.text
                    val saved = suspendCancellableCoroutine<Boolean> { continuation ->
                        model.save(text) { ok -> if (continuation.isActive) continuation.resume(ok) }
                    }
                    if (!saved || model.state.value.modified) {
                        goVm.message("Файл ${tab.title} ещё содержит несохранённые изменения. Повторите запуск.")
                        return@launch
                    }
                }
                if (goVm.state.value.project?.file?.absolutePath != requestedPath) {
                    goVm.message("Активный файл изменился — повторите запуск")
                    return@launch
                }
                if (context.appContainer.dirtyEditors.value.any { File(it).toPath().normalize().startsWith(project.root.toPath()) }) {
                    goVm.message("В проекте появились новые несохранённые изменения. Повторите запуск.")
                    return@launch
                }
                goVm.start(action, value)
            } finally { goPreparing = false }
        }
    }

    // Горячие клавиши физической клавиатуры (планшеты, Chromebook, DeX).
    val shortcut by androidx.compose.runtime.rememberUpdatedState { code: Int, shift: Boolean ->
        when {
            code == android.view.KeyEvent.KEYCODE_F8 && isGoFile -> { navigateProblem(shift); true }
            code == android.view.KeyEvent.KEYCODE_P && onQuickOpen != null -> { onQuickOpen(); true }
            code == android.view.KeyEvent.KEYCODE_F5 && isGoFile -> {
                if (shift) goVm.stop() else startGo(if (state.fileName.endsWith("_test.go")) GoAction.TEST else GoAction.RUN)
                true
            }
            code == android.view.KeyEvent.KEYCODE_S && !state.readOnly -> { save(); true }
            code == android.view.KeyEvent.KEYCODE_F && shift -> { toolsOpen = true; true }
            code == android.view.KeyEvent.KEYCODE_F -> { searchOpen = true; true }
            code == android.view.KeyEvent.KEYCODE_G -> { dialog = "goto"; true }
            code == android.view.KeyEvent.KEYCODE_O -> { structureOpen = true; true }
            code == android.view.KeyEvent.KEYCODE_SPACE && !state.readOnly -> { requestCompletion(editor); true }
            else -> false
        }
    }

    BackHandler(searchOpen) { searchOpen = false; editor?.searcher?.stopSearch() }
    // «Назад» сперва закрывает окно подсказок. Окно — PopupWindow Sora, поэтому его видимость опрашиваем.
    var completionShown by remember { mutableStateOf(false) }
    LaunchedEffect(editor) {
        val popup = editor?.getComponent(EditorAutoCompletion::class.java) ?: return@LaunchedEffect
        while (true) {
            completionShown = popup.isShowing
            delay(150)
        }
    }
    BackHandler(completionShown) { editor?.getComponent(EditorAutoCompletion::class.java)?.hide(); completionShown = false }
    BackHandler(!searchOpen && state.modified) { afterUnsaved = null; dialog = "unsaved" }

    if (problemsOpen && isGoFile) GoProblemsSheet(syntaxState, state.fileName,
        onDismiss = { problemsOpen = false }, onEnabled = syntaxVm::setEnabled, onJump = ::jumpProblem)

    if (goPanelOpen) {
        GoRunPanel(vm = goVm, state = goState, busy = goPreparing,
            onDismiss = { goPanelOpen = false }, onAction = ::startGo,
            onJumpError = { error ->
                val project = goState.project
                val target = project?.let { File(it.root, error.file).takeIf(File::isFile) } ?: run {
                    scope.launch { snackbar.showSnackbar("Файл ошибки не найден: ${error.file}") }
                    null
                }
                if (target != null) {
                    // Вкладка откроется, а переход выполнится в LaunchedEffect после её активации.
                    pendingGoError = error to target.absolutePath
                    tabs.open(Route.Editor(target.absolutePath))
                    goPanelOpen = false
                }
            })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (navigationContent != null) navigationContent()
                    else IconButton(onClick = { if (state.modified) { afterUnsaved = null; dialog = "unsaved" } else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад")
                    }
                },
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(state.fileName, maxLines = 1, overflow = TextOverflow.MiddleEllipsis,
                                style = if (ideMode) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                modifier = Modifier.weight(1f, fill = false).then(if (onQuickOpen != null) Modifier.clickable(onClick = onQuickOpen) else Modifier))
                            AnimatedVisibility(state.modified) {
                                Box(Modifier.padding(start = 8.dp).size(8.dp).clip(RoundedCornerShape(50)).background(scheme.primary))
                            }
                        }
                        Text(
                            buildString {
                                if (workspaceRoot != null) {
                                    val relative = File(page.path).parentFile?.relativeToOrNull(File(workspaceRoot))?.invariantSeparatorsPath.orEmpty()
                                    append(File(workspaceRoot).name)
                                    if (relative.isNotBlank()) append('/').append(relative)
                                    append(" · ").append(line).append(':').append(column)
                                } else {
                                    append(line).append(':').append(column).append("  ").append(state.language.name)
                                    append(" · ").append(state.charset).append(" · ").append(state.lineEnding.label)
                                }
                                if (state.readOnly) append(" · чтение")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    if (ideMode) {
                        if (isGoFile || goState.running) GoToolbarActions(goState,
                            enabled = isGoFile && state.error == null && !state.loading && !state.saving && !goPreparing && !goState.discovering,
                            onRun = { startGo(if (state.fileName.endsWith("_test.go")) GoAction.TEST else GoAction.RUN) },
                            onStop = { goVm.stop(); goPanelOpen = true }, onOpen = { goPanelOpen = true }, compact = true)
                        if (isGoFile) GoFormatButton(enabled = !state.readOnly && state.error == null && !state.loading && !goFormatting, busy = goFormatting, onClick = ::runGofmt)
                        if (isGoFile) GoSyntaxButton(syntaxState) { problemsOpen = true }
                    } else {
                        IconButton(onClick = { editor?.undo() }, enabled = canUndo, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Rounded.Undo, "Отменить")
                        }
                        IconButton(onClick = { editor?.redo() }, enabled = canRedo, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Rounded.Redo, "Повторить")
                        }
                    }
                    AnimatedContent(state.saving, label = "save") { saving ->
                        if (saving) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(28.dp)) }
                        else GoIconButton(onClick = { save() }, enabled = state.modified && !state.readOnly, tonal = ideMode && state.modified, tooltip = "Сохранить · Ctrl+S") {
                            Icon(Icons.Rounded.Save, "Сохранить")
                        }
                    }
                    Box {
                        GoIconButton(onClick = { menuOpen = true }, tooltip = "Действия редактора") { Icon(Icons.Rounded.MoreVert, "Ещё") }
                        EditorMenu(
                            expanded = menuOpen,
                            onDismiss = { menuOpen = false },
                            wordWrap = settings.editorWordWrap,
                            readOnly = state.readOnly,
                            minimap = minimap,
                            fontSize = settings.editorFontSize,
                            onSearch = { searchOpen = true },
                            onGoToLine = { dialog = "goto" },
                            onStructure = { structureOpen = true },
                            onLanguage = { dialog = "language" },
                            onEncoding = { dialog = "encoding" },
                            onLineEnding = { dialog = "ending" },
                            onToggleWrap = { context.appContainer.settings.update { it.copy(editorWordWrap = !it.editorWordWrap) } },
                            onToggleReadOnly = { vm.setReadOnly(!state.readOnly) },
                            canToggleReadOnly = !page.lockReadOnly,
                            onToggleMinimap = { minimap = !minimap },
                            onFontBigger = { context.appContainer.settings.update { it.copy(editorFontSize = (it.editorFontSize + 1).coerceAtMost(32f)) } },
                            onFontSmaller = { context.appContainer.settings.update { it.copy(editorFontSize = (it.editorFontSize - 1).coerceAtLeast(8f)) } },
                            onSelectAll = { editor?.selectAll() },
                            onTools = { toolsOpen = true },
                            modified = state.modified,
                            onDiff = { editor?.let { e -> vm.prepareDiff(e.text.toString(), onShowDiff) } },
                            onPreview = if (client.wexmanager.feature.viewer.previewKindFor(state.fileName) != null) {
                                { editor?.let { e -> vm.preparePreview(e.text.toString(), onPreview) } }
                            } else null,
                            onRecent = { recentOpen = true },
                            onSaveAs = { dialog = "saveas" },
                            onShare = { FileOpener.share(context, listOf(File(page.path))) },
                            onOpenExternal = { FileOpener.openExternal(context, File(page.path)) },
                            onInfo = { dialog = "info" },
                            onGoPanel = if (ideMode || isGoFile) ({ goPanelOpen = true }) else null,
                            onProblems = if (isGoFile) ({ problemsOpen = true }) else null,
                            onQuickOpen = onQuickOpen,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = scheme.surface, scrolledContainerColor = scheme.surface),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = scheme.surface,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()) {
            AnimatedVisibility(
                searchOpen,
                enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
                exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                editor?.let { SearchPanel(it, readOnly = state.readOnly, onClose = { searchOpen = false; it.searcher.stopSearch() }) }
            }
            AnimatedVisibility(bigFile && !heavyHighlight && state.language !== Languages.PLAIN && state.error == null) {
                Row(
                    Modifier.fillMaxWidth().background(scheme.secondaryContainer).padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Большой файл — подсветка выключена для скорости",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { heavyHighlight = true }) { Text("Включить") }
                }
            }
            if (tabList.isNotEmpty()) {
                val batchBusy = closeBusy || (closingId != null && tabList.any { it.id == closingId })
                EditorFileTabs(tabs = tabList, selectedId = active.id, dirtyPaths = dirtyPaths, closeEnabled = !batchBusy,
                    onSelect = tabs::select, onClose = ::closeTab, actions = {
                    Box {
                        IconButton(onClick = { tabMenu = true }, enabled = !batchBusy, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.MoreVert, "Действия вкладок")
                        }
                        DropdownMenuPopup(expanded = tabMenu, onDismissRequest = { tabMenu = false }) {
                            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                                DropdownMenuItem(
                                    text = { Text("Закрыть другие") },
                                    enabled = !batchBusy && tabList.size > 1,
                                    onClick = {
                                        tabMenu = false
                                        closeTabs(tabList.mapNotNull { tab -> tab.id.takeIf { it != active.id } })
                                    },
                                    shape = MenuDefaults.leadingItemShape,
                                )
                                DropdownMenuItem(
                                    text = { Text("Закрыть все") },
                                    enabled = !batchBusy,
                                    onClick = {
                                        tabMenu = false
                                        closeTabs(tabList.map { it.id })
                                    },
                                    shape = MenuDefaults.trailingItemShape,
                                )
                            }
                        }
                    }
                })
            }
            if (!ideMode && (isGoFile || goState.running)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).clickable { goPanelOpen = true }.padding(vertical = 8.dp)) {
                        Text(
                            if (goState.running) "Go · ${goState.action?.label}" else "Go · ${if (goState.config.scope == GoRunScope.PACKAGE) "Пакет / проект" else "Этот файл"}",
                            style = MaterialTheme.typography.labelLarge, color = scheme.primary,
                        )
                        Text(
                            if (goState.running) File(goState.runPath).name else "Параметры, зависимости и консоль",
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant,
                        )
                    }
                    if (isGoFile) GoFormatButton(enabled = !state.readOnly && state.error == null && !state.loading && !goFormatting, busy = goFormatting, onClick = ::runGofmt)
                    if (isGoFile) GoSyntaxButton(syntaxState) { problemsOpen = true }
                    GoToolbarActions(
                        state = goState,
                        enabled = isGoFile && state.error == null && !state.loading && !state.saving && !goPreparing && !goState.discovering,
                        onRun = { startGo(if (state.fileName.endsWith("_test.go")) GoAction.TEST else GoAction.RUN) },
                        onStop = { goVm.stop(); goPanelOpen = true },
                        onOpen = { goPanelOpen = true },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                key(active.id) {
                AndroidView(
                    factory = { ctx ->
                        val cached = editorViews[active.id]
                        if (cached != null && cached.tag != "released") {
                            (cached.parent as? ViewGroup)?.removeView(cached)
                            line = cached.cursor.leftLine + 1
                            column = cached.cursor.leftColumn + 1
                            canUndo = cached.canUndo()
                            canRedo = cached.canRedo()
                            editor = cached
                            cached
                        } else {
                        WexCodeEditor(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            setScalable(true)
                            // Не отдавать горизонтальные жесты родителю (см. WexCodeEditor.dispatchTouchEvent).
                            setInterceptParentHorizontalScrollIfNeeded(false)
                            // Экранная клавиатура при физической — как решил пользователь в настройках Android.
                            isDisableSoftKbdIfHardKbdAvailable = android.provider.Settings.Secure.getInt(
                                ctx.contentResolver, "show_ime_with_hard_keyboard", 0,
                            ) == 0
                            // Анимация курсора давала ~7 лишних кадров на каждое нажатие.
                            setCursorAnimationEnabled(false)
                            // Клавиатура вводит символы сразу: без подсказок слово не держится «составным»
                            // и не заменяется целиком на каждую букву (так же делают редакторы кода и MT).
                            props.disallowSuggestions = true
                            props.minimizeComposingTextUpdate = true
                            props.autoCompletionOnComposing = false
                            // Подсказки на каждую букву даже при быстром наборе (по умолчанию Sora молчит,
                            // если нажатия чаще 70 мс): устаревший расчёт и так прерывается следующим.
                            props.cancelCompletionNs = 0L
                            setLineSpacing(2f, 1.1f)
                            val dp = ctx.resources.displayMetrics.density
                            setLineNumberMarginLeft(10 * dp)
                            setDividerMargin(8 * dp, 6 * dp)
                            setDividerWidth(0f)
                            isVerticalScrollBarEnabled = true
                            getComponent(EditorAutoCompletion::class.java).apply {
                                setAdapter(WexCompletionAdapter(ctx) { typefaceText })
                                setMaxHeight((ctx.resources.displayMetrics.density * 280).toInt())
                            }
                            subscribeAlways(io.github.rosemoe.sora.event.EditorKeyEvent::class.java) { ev ->
                                // Esc закрывает окно подсказок (Sora его не обрабатывает).
                                val completion = getComponent(EditorAutoCompletion::class.java)
                                if (ev.eventType == io.github.rosemoe.sora.event.EditorKeyEvent.Type.DOWN &&
                                    ev.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE && completion.isShowing
                                ) {
                                    completion.hide()
                                    ev.interceptAndSetResult(true)
                                    return@subscribeAlways
                                }
                                if (ev.eventType == io.github.rosemoe.sora.event.EditorKeyEvent.Type.DOWN &&
                                    (ev.isCtrlPressed || ev.keyCode == android.view.KeyEvent.KEYCODE_F5 || ev.keyCode == android.view.KeyEvent.KEYCODE_F8) && shortcut(ev.keyCode, ev.isShiftPressed)
                                ) ev.interceptAndSetResult(true)
                            }
                            editorViews[active.id] = this
                            editor = this
                        }
                        }
                    },
                    onRelease = { view ->
                        (view.parent as? ViewGroup)?.removeView(view)
                        val wex = view as? WexCodeEditor ?: return@AndroidView
                        if (wex.tag == "released") return@AndroidView
                        // vm в композиции уже может быть другой вкладкой — ищем модель по самому виджету.
                        val id = editorViews.entries.firstOrNull { it.value === wex }?.key ?: return@AndroidView
                        val tab = tabs.tabs.value.firstOrNull { it.id == id } ?: return@AndroidView
                        runCatching {
                            val lineNo = wex.cursor.leftLine
                            if (lineNo > 0 && !tab.route.path.startsWith(context.cacheDir.path)) {
                                context.appContainer.userData.saveEditorPosition(tab.route.path, lineNo)
                            }
                            tabs.viewModel(id).stash(wex.text.toString())
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                }
                when {
                    state.error != null -> ErrorBox(state.error!!, onHex = null)
                    state.loading || !ready || !themed || !painted -> Box(Modifier.fillMaxSize().background(scheme.surface), contentAlignment = Alignment.Center) {
                        // Индикатор — только если ждать заметно: короткое открытие без мелькания.
                        var spinner by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) { delay(250); spinner = true }
                        if (spinner) LoadingIndicator(Modifier.size(64.dp))
                    }
                }
            }
            if (!state.readOnly && settings.editorSymbolBar && state.error == null) {
                SymbolBar(
                    onUndo = if (ideMode) ({ editor?.undo(); Unit }) else null,
                    onRedo = if (ideMode) ({ editor?.redo(); Unit }) else null,
                    canUndo = canUndo, canRedo = canRedo,
                    onSymbol = { sym -> editor?.let { e -> if (e.isEditable) e.insertText(sym, sym.length) } },
                    onTab = {
                        editor?.let { e ->
                            val t = if (settings.editorUseSpaces) " ".repeat(settings.editorTabSize) else "\t"
                            e.insertText(t, t.length)
                        }
                    },
                )
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }

    if (toolsOpen) {
        val e = editor
        TextToolsSheet(
            hasSelection = e?.cursor?.isSelected == true,
            readOnly = state.readOnly || e == null,
            onDismiss = { toolsOpen = false },
        ) { tool ->
            toolsOpen = false
            if (e == null) return@TextToolsSheet
            if (tool == TextTool.Validate) {
                val text = e.text.toString()
                scope.launch {
                    val v = withContext(Dispatchers.Default) { TextTools.validate(text) { android.util.Xml.newPullParser() } }
                    if (!v.ok && v.line > 0) runCatching {
                        // Курсор — на место ошибки.
                        val line = (v.line - 1).coerceIn(0, e.lineCount - 1)
                        e.jumpToLine(line)
                        e.setSelection(line, (v.column - 1).coerceIn(0, e.text.getColumnCount(line)))
                    }
                    snackbar.showSnackbar((if (v.ok) "✓ " else "") + v.message)
                }
                return@TextToolsSheet
            }
            val indent = if (settings.editorUseSpaces) " ".repeat(settings.editorTabSize) else "\t"
            val c = e.cursor
            val selected = c.isSelected
            val content = e.text
            val sl = if (selected) c.leftLine else 0
            val sc = if (selected) c.leftColumn else 0
            val el = if (selected) c.rightLine else content.lineCount - 1
            val ec = if (selected) c.rightColumn else content.getColumnCount(content.lineCount - 1)
            val source = if (selected) content.subSequence(c.left, c.right).toString() else content.toString()
            val version = content.documentVersion
            // Сортировка или форматирование большого файла — не на главном потоке.
            scope.launch {
                val result = withContext(Dispatchers.Default) {
                    runCatching { TextTools.apply(tool, source, indent).replace("\r\n", "\n").replace('\r', '\n') }
                }
                if (editor !== e || e.text.documentVersion != version) {
                    snackbar.showSnackbar("Текст изменился во время обработки — повторите")
                    return@launch
                }
                result.onSuccess { out ->
                    if (out == source) {
                        snackbar.showSnackbar("Без изменений")
                    } else {
                        e.text.replace(sl, sc, el, ec, out)
                        if (selected) {
                            val nl = out.count { it == '\n' }
                            val endCol = if (nl == 0) sc + out.length else out.length - out.lastIndexOf('\n') - 1
                            runCatching { e.setSelectionRegion(sl, sc, sl + nl, endCol) }
                        }
                    }
                }.onFailure { err ->
                    snackbar.showSnackbar(err.message ?: "Не удалось применить")
                }
            }
        }
    }
    if (recentOpen) {
        RecentFilesSheet(
            currentPath = page.binarySource ?: page.path,
            onDismiss = { recentOpen = false },
        ) { path ->
            recentOpen = false
            if (state.modified) {
                afterUnsaved = { scope.launch { onOpenFile(client.wexmanager.core.apk.AxmlFiles.editorRoute(context, path)) } }
                dialog = "unsaved"
            } else {
                scope.launch { onOpenFile(client.wexmanager.core.apk.AxmlFiles.editorRoute(context, path)) }
            }
        }
    }

    if (structureOpen) {
        val e = editor
        StructureSheet(
            text = { e?.text?.toString().orEmpty() },
            language = state.language,
            currentLine = e?.cursor?.leftLine ?: 0,
            onJump = { line ->
                editor?.let { ed ->
                    val l = line.coerceIn(0, ed.lineCount - 1)
                    ed.jumpToLine(l)
                    ed.setSelection(l, 0)
                }
            },
            onDismiss = { structureOpen = false },
        )
    }
    closingId?.let { id -> tabList.firstOrNull { it.id == id } }?.let { tab ->
        val id = tab.id
        AlertDialog(
                onDismissRequest = {
                    closeBatch++
                    closingId = null
                    closeQueue = emptyList()
                },
                icon = { Icon(Icons.Rounded.Save, null) },
                title = { Text("Сохранить изменения?") },
                text = { Text("В файле «${tab.title}» есть несохранённые изменения.") },
                confirmButton = {
                    Button(onClick = {
                        val batched = closeQueue.isNotEmpty()
                        val batch = closeBatch
                        closingId = null
                        val target = tabs.viewModel(id)
                        val text = editorViews[id]?.takeIf { it.tag != "released" }?.text?.toString()
                            ?: (if (id == active.id) editor?.text?.toString() else null)
                            ?: target.state.value.text
                        val last = tabs.tabs.value.size <= 1
                        closeBusy = true
                        target.save(text, leaving = last) done@{ ok ->
                            closeBusy = false
                            if (closeBatch != batch) {
                                if (ok) closeTabNow(id)
                                return@done
                            }
                            if (!ok) {
                                closeQueue = emptyList()
                                return@done
                            }
                            closeTabNow(id)
                            if (!last && batched) continueClose()
                        }
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            val batched = closeQueue.isNotEmpty()
                            val batch = closeBatch
                            closingId = null
                            tabs.viewModel(id).discardEdits()
                            val last = tabs.tabs.value.size <= 1
                            closeTabNow(id)
                            if (closeBatch == batch && !last && batched) continueClose()
                        }) { Text("Не сохранять") }
                        TextButton(onClick = {
                            closeBatch++
                            closingId = null
                            closeQueue = emptyList()
                        }) { Text("Отмена") }
                    }
                },
            )
    }
    when (dialog) {
        "diskchanged" -> AlertDialog(
            onDismissRequest = { dialog = null },
            icon = { Icon(Icons.Rounded.Sync, null) },
            title = { Text("Файл изменён на диске") },
            text = { Text("Другое приложение изменило «${state.fileName}». Перечитать его? Несохранённые правки будут потеряны.") },
            confirmButton = { Button(onClick = { dialog = null; vm.reloadFromDisk() }) { Text("Перечитать") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Оставить мои") } },
        )
        "unsaved" -> AlertDialog(
            onDismissRequest = { dialog = null; afterUnsaved = null },
            icon = { Icon(Icons.Rounded.Save, null) },
            title = { Text("Сохранить изменения?") },
            text = { Text("В файле «${state.fileName}» есть несохранённые изменения.") },
            confirmButton = {
                Button(onClick = {
                    val next = afterUnsaved ?: onBack
                    dialog = null; afterUnsaved = null
                    save(next, leaving = true)
                }) { Text("Сохранить") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val next = afterUnsaved ?: onBack
                        dialog = null; afterUnsaved = null
                        vm.discardEdits()
                        next()
                    }) { Text("Не сохранять") }
                    TextButton(onClick = { dialog = null; afterUnsaved = null }) { Text("Отмена") }
                }
            },
        )
        "goto" -> GoToLineDialog(editor?.lineCount ?: 1, onDismiss = { dialog = null }) { n ->
            editor?.let { e ->
                val target = (n - 1).coerceIn(0, e.lineCount - 1)
                e.jumpToLine(target)
                e.setSelection(target, 0)
            }
            dialog = null
        }
        "language" -> LanguageDialog(state.language, onDismiss = { dialog = null }) { vm.setLanguage(it); dialog = null }
        "encoding" -> EncodingDialog(
            current = state.charset,
            modified = state.modified,
            onDismiss = { dialog = null },
            onReopen = { vm.reopenWithCharset(it); dialog = null },
            onConvert = { vm.convertCharset(it); dialog = null },
        )
        "ending" -> ChoiceDialog(
            title = "Окончания строк",
            icon = Icons.Rounded.SubdirectoryArrowLeft,
            options = LineEnding.entries,
            selected = state.lineEnding,
            label = { "${it.label} (${if (it == LineEnding.LF) "Unix" else if (it == LineEnding.CRLF) "Windows" else "Classic Mac"})" },
            onDismiss = { dialog = null },
        ) { vm.setLineEnding(it); dialog = null }
        "saveas" -> {
            var confirmOverwrite by remember { mutableStateOf<String?>(null) }
            val initial = if (page.archive != null || page.sourceUri != null) {
                android.os.Environment.getExternalStorageDirectory().path + "/Download/" + state.fileName
            } else page.path
            TextInputDialog(
                title = "Сохранить как",
                icon = Icons.Rounded.SaveAs,
                initial = initial,
                label = "Путь к новому файлу",
                confirmLabel = "Сохранить",
                selection = androidx.compose.ui.text.TextRange(initial.lastIndexOf('/') + 1, initial.lastIndexOf('.').takeIf { it > initial.lastIndexOf('/') + 1 } ?: initial.length),
                supporting = "Кодировка ${state.charset}, окончания ${state.lineEnding.label}",
                singleLine = false,
                onDismiss = { dialog = null },
            ) { value, setError ->
                val target = value.trim()
                when {
                    !target.startsWith("/") || target.endsWith("/") -> setError("Укажите полный путь к файлу")
                    target == page.path && page.archive == null -> setError("Это текущий файл — используйте «Сохранить»")
                    File(target).isDirectory -> setError("Это папка")
                    File(target).exists() && confirmOverwrite != target -> {
                        confirmOverwrite = target
                        setError("Файл существует — нажмите ещё раз, чтобы заменить")
                    }
                    state.saving -> setError("Идёт сохранение — подождите")
                    else -> {
                        val e = editor ?: return@TextInputDialog
                        dialog = null
                        val charset = vm.charset
                        vm.saveAs(e.text.toString(), target) { ok -> if (ok) onOpenFile(Route.Editor(target, charset = charset)) }
                    }
                }
            }
        }
        "info" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(state.fileName) },
            text = {
                Column {
                    InfoLine("Путь", page.path)
                    if (page.archive != null) InfoLine("Архив", page.archive)
                    InfoLine("Размер", Format.size(state.sizeBytes))
                    InfoLine("Строк", "${editor?.lineCount ?: 0}")
                    InfoLine("Символов", "${editor?.text?.length ?: 0}")
                    InfoLine("Кодировка", state.charset + if (state.hasBom) " (BOM)" else "")
                    InfoLine("Язык", state.language.name)
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorBox(message: String, onHex: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.surface).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val shape = remember { PolygonShape(MaterialShapes.Clover4Leaf) }
        Box(Modifier.size(104.dp).clip(shape).background(scheme.errorContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Error, null, tint = scheme.onErrorContainer, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (onHex != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onHex) { Text("HEX-просмотр") }
        }
    }
}

@Composable
private fun SymbolBar(onSymbol: (String) -> Unit, onTab: () -> Unit,
    onUndo: (() -> Unit)? = null, onRedo: (() -> Unit)? = null, canUndo: Boolean = false, canRedo: Boolean = false) {
    Surface(color = androidx.compose.ui.graphics.Color.Transparent, tonalElevation = 0.dp) {
        LazyRow(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onUndo != null) item { GoIconButton(onClick = onUndo, enabled = canUndo, tooltip = "Отменить") { Icon(Icons.AutoMirrored.Rounded.Undo, "Отменить") } }
            if (onRedo != null) item { GoIconButton(onClick = onRedo, enabled = canRedo, tooltip = "Повторить") { Icon(Icons.AutoMirrored.Rounded.Redo, "Повторить") } }
            item {
                GoIconButton(onClick = onTab, tonal = true, tooltip = "Табуляция") { Icon(Icons.AutoMirrored.Rounded.KeyboardTab, "Табуляция") }
            }
            items(SYMBOLS) { sym ->
                FilledTonalButton(onClick = { onSymbol(sym) }, shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
                    Text(sym, style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace))
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(editor: CodeEditor, readOnly: Boolean, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var regex by remember { mutableStateOf(false) }
    var caseSensitive by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(0) }
    var current by remember { mutableIntStateOf(-1) }

    DisposableEffect(editor) {
        // После stopSearch() Sora тоже публикует событие, а без запроса счётчики бросают «pattern not set».
        val receipt = editor.subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            val s = editor.searcher
            count = if (s.hasQuery()) runCatching { s.matchedPositionCount }.getOrDefault(0) else 0
            current = if (s.hasQuery()) runCatching { s.currentMatchedPositionIndex }.getOrDefault(-1) else -1
        }
        onDispose { receipt.unsubscribe() }
    }
    LaunchedEffect(query, regex, caseSensitive, wholeWord) {
        if (query.isEmpty()) {
            editor.searcher.stopSearch()
            count = 0
            current = -1
        } else {
            val type = when {
                regex -> EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                wholeWord -> EditorSearcher.SearchOptions.TYPE_WHOLE_WORD
                else -> EditorSearcher.SearchOptions.TYPE_NORMAL
            }
            // Неверный regex: Sora бросает исключение до смены запроса — убираем подсветку прежнего.
            runCatching { editor.searcher.search(query, EditorSearcher.SearchOptions(type, !caseSensitive)) }
                .onFailure { runCatching { editor.searcher.stopSearch() }; count = 0; current = -1 }
        }
    }
    fun refresh() {
        val s = editor.searcher
        current = if (s.hasQuery()) runCatching { s.currentMatchedPositionIndex }.getOrDefault(-1) else -1
        count = if (s.hasQuery()) runCatching { s.matchedPositionCount }.getOrDefault(0) else 0
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Найти") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    suffix = { if (query.isNotEmpty()) Text(if (count > 0) "${current + 1}/$count" else "0") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runCatching { editor.searcher.gotoNext() }; refresh() }),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { runCatching { editor.searcher.gotoPrevious() }; refresh() }, enabled = count > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Предыдущее") }
                IconButton(onClick = { runCatching { editor.searcher.gotoNext() }; refresh() }, enabled = count > 0) { Icon(Icons.Rounded.KeyboardArrowDown, "Следующее") }
                if (!readOnly) IconButton(onClick = { showReplace = !showReplace }) { Icon(Icons.Rounded.FindReplace, "Замена") }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Закрыть поиск") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                FilterChip(selected = caseSensitive, onClick = { caseSensitive = !caseSensitive }, label = { Text("Aa") })
                FilterChip(selected = wholeWord, onClick = { wholeWord = !wholeWord }, label = { Text("Слово") })
                FilterChip(selected = regex, onClick = { regex = !regex }, label = { Text(".* Regex") })
            }
            AnimatedVisibility(showReplace && !readOnly) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    OutlinedTextField(
                        value = replace,
                        onValueChange = { replace = it },
                        singleLine = true,
                        placeholder = { Text("Заменить на") },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { runCatching { editor.searcher.replaceCurrentMatch(replace) }; refresh() }, enabled = count > 0) { Text("Заменить") }
                    TextButton(onClick = { runCatching { editor.searcher.replaceAll(replace) }; refresh() }, enabled = count > 0) { Text("Все") }
                }
            }
        }
    }
}

@Composable
private fun EditorMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    wordWrap: Boolean,
    readOnly: Boolean,
    minimap: Boolean,
    fontSize: Float,
    onSearch: () -> Unit,
    onGoToLine: () -> Unit,
    onStructure: () -> Unit,
    onLanguage: () -> Unit,
    onEncoding: () -> Unit,
    onLineEnding: () -> Unit,
    onToggleWrap: () -> Unit,
    onToggleReadOnly: () -> Unit,
    canToggleReadOnly: Boolean,
    onToggleMinimap: () -> Unit,
    onFontBigger: () -> Unit,
    onFontSmaller: () -> Unit,
    onSelectAll: () -> Unit,
    onTools: () -> Unit,
    modified: Boolean,
    onDiff: () -> Unit,
    onPreview: (() -> Unit)?,
    onRecent: () -> Unit,
    onSaveAs: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    onInfo: () -> Unit,
    onGoPanel: (() -> Unit)? = null,
    onProblems: (() -> Unit)? = null,
    onQuickOpen: (() -> Unit)? = null,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        @Composable
        fun Item(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, shape: androidx.compose.ui.graphics.Shape, action: () -> Unit) =
            DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, onClick = { onDismiss(); action() }, shape = shape)
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 4)) {
            if (onQuickOpen != null) Item("Открыть файл проекта…", Icons.Rounded.Search, MenuDefaults.leadingItemShape, onQuickOpen)
            if (onGoPanel != null) Item("Go: запуск и консоль", Icons.Rounded.Code, MenuDefaults.middleItemShape, onGoPanel)
            if (onProblems != null) Item("Проблемы Go", Icons.Rounded.Error, MenuDefaults.middleItemShape, onProblems)
            Item("Поиск и замена", Icons.Rounded.Search, if (onQuickOpen != null || onGoPanel != null || onProblems != null) MenuDefaults.middleItemShape else MenuDefaults.leadingItemShape, onSearch)
            Item("Перейти к строке", Icons.Rounded.FormatListNumbered, MenuDefaults.middleItemShape, onGoToLine)
            Item("Структура", Icons.Rounded.AccountTree, MenuDefaults.middleItemShape, onStructure)
            Item("Инструменты текста", Icons.Rounded.AutoFixHigh, MenuDefaults.middleItemShape, onTools)
            Item("Выделить всё", Icons.Rounded.SelectAll, MenuDefaults.trailingItemShape, onSelectAll)
        }
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 4)) {
            if (onPreview != null) Item("Предпросмотр", Icons.Rounded.Preview, MenuDefaults.leadingItemShape, onPreview)
            if (modified) Item("Показать изменения", Icons.Rounded.Difference, if (onPreview != null) MenuDefaults.middleItemShape else MenuDefaults.leadingItemShape, onDiff)
            Item("Недавние файлы", Icons.Rounded.History, if (modified || onPreview != null) MenuDefaults.middleItemShape else MenuDefaults.leadingItemShape, onRecent)
            Item("Сохранить как…", Icons.Rounded.SaveAs, MenuDefaults.trailingItemShape, onSaveAs)
        }
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(2, 4)) {
            Item("Язык подсветки", Icons.Rounded.Code, MenuDefaults.leadingItemShape, onLanguage)
            Item("Кодировка", Icons.Rounded.Language, MenuDefaults.middleItemShape, onEncoding)
            Item("Окончания строк", Icons.Rounded.SubdirectoryArrowLeft, MenuDefaults.middleItemShape, onLineEnding)
            Item(if (wordWrap) "Без переноса строк" else "Перенос строк", Icons.AutoMirrored.Rounded.WrapText, MenuDefaults.middleItemShape, onToggleWrap)
            Item(if (minimap) "Скрыть мини-карту" else "Мини-карта", Icons.Rounded.Map, MenuDefaults.middleItemShape, onToggleMinimap)
            // Размер шрифта — одной строкой с кнопками, меню не закрывается между нажатиями.
            DropdownMenuItem(
                text = { Text("Шрифт ${fontSize.toInt()}") },
                leadingIcon = { Icon(Icons.Rounded.FormatSize, null) },
                trailingContent = {
                    Row {
                        IconButton(onClick = onFontSmaller, enabled = fontSize > 8f) { Icon(Icons.Rounded.TextDecrease, "Мельче") }
                        IconButton(onClick = onFontBigger, enabled = fontSize < 32f) { Icon(Icons.Rounded.TextIncrease, "Крупнее") }
                    }
                },
                onClick = {},
                shape = MenuDefaults.middleItemShape,
            )
            if (canToggleReadOnly) {
                Item(if (readOnly) "Разрешить правку" else "Только чтение", Icons.Rounded.EditOff, MenuDefaults.trailingItemShape, onToggleReadOnly)
            } else {
                DropdownMenuItem(
                    text = { Text("Только чтение: копия из архива") },
                    leadingIcon = { Icon(Icons.Rounded.EditOff, null) },
                    onClick = {},
                    enabled = false,
                    shape = MenuDefaults.trailingItemShape,
                )
            }
        }
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(3, 4)) {
            Item("Поделиться", Icons.Rounded.Share, MenuDefaults.leadingItemShape, onShare)
            Item("Открыть в другом приложении", Icons.AutoMirrored.Rounded.OpenInNew, MenuDefaults.middleItemShape, onOpenExternal)
            Item("О файле", Icons.Rounded.FormatListNumbered, MenuDefaults.trailingItemShape, onInfo)
        }
    }
}

@Composable
private fun GoToLineDialog(lines: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FormatListNumbered, null) },
        title = { Text("Перейти к строке") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter(Char::isDigit).take(9) },
                singleLine = true,
                label = { Text("Номер строки (1–$lines)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { text.toIntOrNull()?.let(onGo) }),
                shape = RoundedCornerShape(16.dp),
            )
        },
        confirmButton = { Button(onClick = { text.toIntOrNull()?.let(onGo) }) { Text("Перейти") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Выбор языка из ~80: поиск по имени и расширению. */
@Composable
private fun LanguageDialog(current: Language, onDismiss: () -> Unit, onSelect: (Language) -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = remember(query) {
        val q = query.trim().trimStart('.')
        if (q.isEmpty()) Languages.all
        else Languages.all.filter { l ->
            l.name.contains(q, ignoreCase = true) || l.id.contains(q, ignoreCase = true) || l.extensions.any { it.equals(q, ignoreCase = true) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Code, null) },
        title = { Text("Язык подсветки") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Имя или расширение") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    items(list.size, key = { list[it].id }) { i ->
                        val l = list[i]
                        SegmentedListItem(
                            selected = l === current,
                            onClick = { onSelect(l) },
                            shapes = ListItemDefaults.segmentedShapes(i, list.size),
                            supportingContent = l.extensions.takeIf { it.isNotEmpty() }?.let { ext ->
                                { Text(ext.take(6).joinToString(" ") { ".$it" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            },
                        ) { Text(l.name) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(icon, null) },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                options.forEachIndexed { i, o ->
                    SegmentedListItem(
                        selected = o == selected,
                        onClick = { onSelect(o) },
                        shapes = ListItemDefaults.segmentedShapes(i, options.size),
                    ) { Text(label(o)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun EncodingDialog(current: String, modified: Boolean, onDismiss: () -> Unit, onReopen: (String) -> Unit, onConvert: (String) -> Unit) {
    var choice by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Language, null) },
        title = { Text("Кодировка") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                val list = (listOf(current) + EditorViewModel.CHARSETS).distinct()
                list.forEachIndexed { i, cs ->
                    SegmentedListItem(
                        selected = cs == choice,
                        onClick = { choice = cs },
                        shapes = ListItemDefaults.segmentedShapes(i, list.size),
                    ) { Text(cs) }
                }
                if (modified) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        "«Перечитать» отменит несохранённые изменения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { onReopen(choice) }) { Text("Перечитать") } },
        dismissButton = { TextButton(onClick = { onConvert(choice) }) { Text("Сохранять в ней") } },
    )
}

/** Пороги «большого файла»: дальше подсветка и украшения заметно тормозят набор. */
private const val BIG_FILE_BYTES = 4L * 1024 * 1024
private const val BIG_FILE_LINES = 150_000

private fun releaseEditor(view: CodeEditor) {
    if (view.tag == "released") return
    view.tag = "released"
    TextMateSetup.release(view.colorScheme)
    view.release()
}

/** Подсказки по запросу (Ctrl+Space, кнопка): открываются и без набранного префикса. */
internal fun requestCompletion(editor: io.github.rosemoe.sora.widget.CodeEditor?) {
    val e = editor ?: return
    (e.editorLanguage as? WexLanguage)?.requestExplicit()
    e.getComponent(EditorAutoCompletion::class.java).requireCompletion()
}
