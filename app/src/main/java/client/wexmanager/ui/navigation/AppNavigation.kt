package client.wexmanager.ui.navigation

import android.app.Activity
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import client.wexmanager.BrowserRequest
import client.wexmanager.appContainer
import client.wexmanager.feature.analyzer.AnalyzerScreen
import client.wexmanager.feature.compare.DiffScreen
import client.wexmanager.feature.compare.DuplicatesScreen
import client.wexmanager.feature.compare.FolderCompareScreen
import client.wexmanager.feature.apk.ApkInfoScreen
import client.wexmanager.feature.apk.AppsScreen
import client.wexmanager.feature.network.NetworkScreen
import client.wexmanager.feature.network.RemoteBrowserScreen
import client.wexmanager.feature.search.SearchScreen
import client.wexmanager.feature.apk.ComponentsScreen
import client.wexmanager.feature.extras.ExtrasScreen
import client.wexmanager.feature.wave100.WaveHubScreen
import client.wexmanager.feature.logcat.LogcatScreen
import client.wexmanager.feature.share.ShareServerScreen
import client.wexmanager.feature.terminal.TerminalScreen
import client.wexmanager.feature.tools.AboutScreen
import client.wexmanager.feature.tools.SmaliReferenceScreen
import client.wexmanager.feature.viewer.HexViewerScreen
import client.wexmanager.feature.viewer.ImageViewerScreen
import client.wexmanager.feature.viewer.MediaPlayerScreen
import client.wexmanager.feature.tools.TasksScreen
import client.wexmanager.feature.tools.TrashScreen
import client.wexmanager.feature.browser.BrowserScreen
import client.wexmanager.feature.editor.EditorScreen
import client.wexmanager.feature.settings.AppearanceSettingsScreen
import client.wexmanager.feature.settings.BackupScreen
import client.wexmanager.feature.settings.BehaviorSettingsScreen
import client.wexmanager.feature.settings.EditorSettingsScreen
import client.wexmanager.feature.settings.FileListSettingsScreen
import client.wexmanager.feature.settings.IconSettingsScreen
import client.wexmanager.feature.settings.SettingsHomeScreen
import client.wexmanager.feature.browser.BrowserViewModel
import client.wexmanager.feature.onboarding.OnboardingScreen

@Composable
fun AppNavigation(backStack: NavBackStack<NavKey>, onRequestPermission: () -> Unit) {
    val context = LocalContext.current
    val motion = MaterialTheme.motionScheme
    val navigate: (Route) -> Unit = { route -> backStack.openRoute(context, route) }
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    // Показать путь в браузере: вернуться к нему по стеку и отправить запрос.
    val reveal: (String) -> Unit = { path ->
        context.appContainer.browserRequests.tryEmit(BrowserRequest.Reveal(path))
        backStack.popToBrowser(context)
    }
    val browseArchive: (String) -> Unit = { path ->
        context.appContainer.browserRequests.tryEmit(BrowserRequest.OpenArchive(path))
        backStack.popToBrowser(context)
    }

    NavDisplay(
        backStack = backStack,
        onBack = pop,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            ContentTransform(
                slideInHorizontally(motion.defaultSpatialSpec()) { it / 4 } + fadeIn(motion.defaultEffectsSpec()),
                slideOutHorizontally(motion.defaultSpatialSpec()) { -it / 8 } + fadeOut(motion.fastEffectsSpec()),
            )
        },
        popTransitionSpec = {
            ContentTransform(
                slideInHorizontally(motion.defaultSpatialSpec()) { -it / 8 } + fadeIn(motion.defaultEffectsSpec()),
                slideOutHorizontally(motion.defaultSpatialSpec()) { it / 4 } + fadeOut(motion.fastEffectsSpec()),
            )
        },
        predictivePopTransitionSpec = {
            (fadeIn(tween(200)) + scaleIn(initialScale = 0.94f))
                .togetherWith(scaleOut(targetScale = 0.88f) + fadeOut(tween(250)))
        },
        entryProvider = entryProvider {
            entry<Route.Onboarding> {
                OnboardingScreen(onDone = {
                    context.appContainer.settings.update { it.copy(onboardingDone = true) }
                    backStack.clear()
                    backStack.add(Route.Browser)
                })
            }
            entry<Route.Browser> {
                val vm: BrowserViewModel = viewModel()
                BrowserScreen(
                    vm = vm,
                    onNavigate = navigate,
                    onRequestPermission = onRequestPermission,
                    onExit = { (context as? Activity)?.finish() },
                )
            }
            entry<Route.GoWorkspace> {
                client.wexmanager.feature.editor.golang.GoWorkspaceScreen(onBack = pop, onNavigate = navigate)
            }
            entry<Route.Editor> {
                EditorScreen(it, onBack = pop, onShowDiff = { l, r -> navigate(Route.Diff(l, r)) }, onPreview = { p, b -> navigate(Route.Preview(p, b)) }, onOpenFile = { route ->
                    // «Сохранить как» и недавние — вкладка на этом же экране, не второй редактор в стеке.
                    navigate(route)
                    context.appContainer.userData.addRecent(route.binarySource ?: route.path, false)
                })
            }
            entry<Route.ImageViewer> { ImageViewerScreen(it.path, it.siblings, onBack = pop) }
            entry<Route.MediaPlayer> { MediaPlayerScreen(it.path, onBack = pop) }
            entry<Route.HexViewer> { HexViewerScreen(it.path, onBack = pop, readOnly = it.readOnly) }
            entry<Route.PdfViewer> { client.wexmanager.feature.viewer.PdfViewerScreen(it.path, onBack = pop) }
            entry<Route.FontViewer> { client.wexmanager.feature.viewer.FontViewerScreen(it.path, onBack = pop) }
            entry<Route.ApkInfo> {
                ApkInfoScreen(
                    path = it.path,
                    onBack = pop,
                    onReveal = reveal,
                    onBrowseArchive = browseArchive,
                    onOpenText = { route -> navigate(route) },
                    onOpenDex = { entries -> navigate(Route.DexEditor(it.path, entries)) },
                    onOpenResources = { navigate(Route.ArscEditor(it.path)) },
                )
            }
            entry<Route.ArscEditor> { client.wexmanager.feature.arsc.ArscEditorScreen(it.path, onBack = pop) }
            entry<Route.ElfViewer> { client.wexmanager.feature.viewer.ElfScreen(it.path, onBack = pop, onHex = { navigate(Route.HexViewer(it.path)) }) }
            entry<Route.SplitInstall> {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                client.wexmanager.feature.apk.SplitInstallScreen(it.path, onBack = pop, onOpenApp = { pkg ->
                    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { i -> runCatching { ctx.startActivity(i) } }
                })
            }
            entry<Route.DatabaseViewer> { client.wexmanager.feature.viewer.DatabaseScreen(it.path, onBack = pop) }
            entry<Route.Preview> { client.wexmanager.feature.viewer.PreviewScreen(it.path, it.base, onBack = pop) }
            entry<Route.DexEditor> {
                client.wexmanager.feature.dex.DexEditorScreen(it, onBack = pop, onOpenEditor = { route -> navigate(route) })
            }
            entry<Route.Search> { SearchScreen(it.root, onBack = pop, onNavigate = navigate, onReveal = reveal) }
            entry<Route.Analyzer> { AnalyzerScreen(it.root, onBack = pop, onReveal = reveal, onCleanup = { navigate(Route.Cleanup(it.root)) }) }
            entry<Route.Apps> { AppsScreen(onBack = pop, onReveal = reveal, onOpenApk = { p -> navigate(Route.ApkInfo(p)) }) }
            entry<Route.Trash> { TrashScreen(onBack = pop, onReveal = reveal) }
            entry<Route.Tasks> { TasksScreen(onBack = pop) }
            entry<Route.Terminal> { TerminalScreen(it.cwd, onBack = pop) }
            entry<Route.SmaliReference> { SmaliReferenceScreen(onBack = pop) }
            entry<Route.Logcat> { LogcatScreen(onBack = pop) }
            entry<Route.ShareServer> { ShareServerScreen(onBack = pop) }
            entry<Route.Components> { ComponentsScreen(onBack = pop) }
            entry<Route.Extras> { ExtrasScreen(onBack = pop) }
            entry<Route.WaveHub> { WaveHubScreen(onBack = pop) }
            entry<Route.Cleanup> { client.wexmanager.feature.tools.CleanupScreen(it.root, onBack = pop) }
            entry<Route.Network> { NetworkScreen(onBack = pop, onOpen = { id -> navigate(Route.RemoteBrowser(id)) }) }
            entry<Route.RemoteBrowser> { RemoteBrowserScreen(it.connectionId, onBack = pop, onNavigate = navigate) }
            entry<Route.Duplicates> { DuplicatesScreen(it.root, onBack = pop, onReveal = reveal) }
            entry<Route.Diff> { DiffScreen(it.left, it.right, onBack = pop) }
            entry<Route.FolderCompare> { FolderCompareScreen(it.left, it.right, onBack = pop, onReveal = reveal) }
            entry<Route.Settings> { SettingsHomeScreen(onBack = pop, onNavigate = navigate) }
            entry<Route.SettingsAppearance> { AppearanceSettingsScreen(onBack = pop) }
            entry<Route.SettingsFileList> { FileListSettingsScreen(onBack = pop) }
            entry<Route.SettingsBehavior> { BehaviorSettingsScreen(onBack = pop) }
            entry<Route.SettingsEditor> { EditorSettingsScreen(onBack = pop) }
            entry<Route.SettingsIcons> { IconSettingsScreen(onBack = pop) }
            entry<Route.SettingsBackup> { BackupScreen(onBack = pop) }
            entry<Route.SettingsSigning> { client.wexmanager.feature.settings.SigningKeysScreen(onBack = pop) }
            entry<Route.About> { AboutScreen(onBack = pop) }
        },
    )
}

/**
 * Редактор в стеке один. Второй текстовый файл становится вкладкой на уже открытом экране,
 * а не новым [Route.Editor]. Остальные маршруты кладутся как раньше.
 */
fun NavBackStack<NavKey>.openRoute(context: android.content.Context, route: Route) {
    if (route !is Route.Editor) {
        add(route)
        return
    }
    val editorAt = indexOfLast { it is Route.Editor }
    if (editorAt >= 0) {
        context.appContainer.editorTabs.open(route)
        while (lastIndex > editorAt) removeAt(lastIndex)
        return
    }
    add(route)
}

/** Возврат к браузеру по стеку; редактор с несохранёнными правками не закрывается. */
fun NavBackStack<NavKey>.popToBrowser(context: android.content.Context): Boolean {
    val dirty = context.appContainer.dirtyEditors.value
    while (size > 1 && last() != Route.Browser) {
        val top = last()
        if (top is Route.Editor && context.appContainer.editorTabs.tabs.value.any {
            it.route.path in dirty && context.appContainer.editorTabs.viewModel(it.id).state.value.modified
        }) {
            android.widget.Toast.makeText(context, "Сначала сохраните изменения в редакторе", android.widget.Toast.LENGTH_SHORT).show()
            return false
        }
        removeAt(lastIndex)
    }
    return true
}

