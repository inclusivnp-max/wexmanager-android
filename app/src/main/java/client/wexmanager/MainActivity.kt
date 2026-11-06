package client.wexmanager

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import client.wexmanager.feature.browser.ArchivePasswordDialog
import client.wexmanager.feature.browser.ConflictDialog
import client.wexmanager.ui.navigation.openRoute
import client.wexmanager.ui.navigation.popToBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.rememberNavBackStack
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.util.FileOpener
import client.wexmanager.core.util.IncomingIntents
import client.wexmanager.core.util.OpenAction
import client.wexmanager.core.util.StoragePermission
import kotlinx.coroutines.flow.MutableStateFlow
import client.wexmanager.ui.components.IconCustomization
import client.wexmanager.ui.components.LocalIconCustomization
import client.wexmanager.ui.navigation.AppNavigation
import client.wexmanager.ui.navigation.Route
import client.wexmanager.ui.theme.WexTheme

class MainActivity : ComponentActivity() {
    private var pendingSafPath: String? = null

    private val safLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val asked = pendingSafPath
        pendingSafPath = null
        val uri = result.data?.data
        if (result.resultCode != RESULT_OK || uri == null) return@registerForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Не удалось сохранить доступ: ${e.message}", Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        if (asked != null && client.wexmanager.core.fs.SafAccess.bestGrant(this, asked) == null) {
            Toast.makeText(this, "Нужна именно папка Android/data или Android/obb", Toast.LENGTH_LONG).show()
        }
        appContainer.browserRequests.tryEmit(BrowserRequest.Refresh)
    }

    private suspend fun handleOpenIntent(intent: Intent, backStack: NavBackStack<NavKey>) {
        val container = appContainer
        val resolved = IncomingIntents.resolve(this, intent) ?: return
        val path = resolved.path
        // Закрытый для приложения путь (через root): папку открываем в браузере, файл — показываем в папке.
        if (client.wexmanager.core.fs.RootShell.enabled && resolved.sourceUri == null && withContext(Dispatchers.IO) { !java.io.File(path).exists() }) {
            container.browserRequests.tryEmit(BrowserRequest.Reveal(path))
            backStack.popToBrowser(this)
            return
        }
        val (item, textLike) = withContext(Dispatchers.IO) {
            val st = LocalFileSystem.stat(path)
            st to (st.kind.isTextLike || (!st.isDirectory && FileOpener.looksLikeText(java.io.File(path))))
        }
        // Холодный старт по интенту: дождаться пользовательских данных («всегда открывать так»).
        FileOpener.openWith = container.userData.awaitLoaded().openWith
        // Бинарный XML (AndroidManifest.xml из APK) — расшифровка; правки копии из чужого приложения не сохранить обратно.
        if (!item.isDirectory && item.extension == "xml" && FileOpener.rememberedMode("xml") == null) {
            val route = client.wexmanager.core.apk.AxmlFiles.editorRoute(this, path, readOnly = resolved.sourceUri != null)
            if (route.binarySource != null) {
                backStack.openRoute(this, route)
                return
            }
        }
        if (!item.isDirectory && item.extension == "class" && FileOpener.rememberedMode("class") == null) {
            client.wexmanager.core.dex.ClassFiles.editorRoute(this, path, readOnly = resolved.sourceUri != null)?.let {
                backStack.openRoute(this, it)
                return
            }
        }
        when (val action = withContext(Dispatchers.IO) { FileOpener.actionFor(item, container.settings.current) }) {
            is OpenAction.Navigate -> backStack.openRoute(this,
                when (val route = action.route) {
                    is Route.Editor -> if (resolved.sourceUri != null) route.copy(sourceUri = resolved.sourceUri) else route
                    // Копия чужого файла: правки в HEX до источника не дойдут — только просмотр.
                    is Route.HexViewer -> if (resolved.sourceUri != null) route.copy(readOnly = true) else route
                    else -> route
                },
            )
            is OpenAction.Enter -> {
                container.browserRequests.tryEmit(
                    if (item.isDirectory) BrowserRequest.Reveal(path) else BrowserRequest.OpenArchive(path),
                )
                backStack.popToBrowser(this)
            }
            else -> backStack.openRoute(this,
                if (textLike) Route.Editor(path, sourceUri = resolved.sourceUri) else Route.HexViewer(path, readOnly = resolved.sourceUri != null),
            )
        }
    }

    /** Входящий интент «открыть файл», ещё не обработанный навигацией. */
    private val pendingOpen = MutableStateFlow<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (IncomingIntents.isOpenIntent(intent)) pendingOpen.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState == null && IncomingIntents.isOpenIntent(intent)) pendingOpen.value = intent
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val container = appContainer
        splash.setKeepOnScreenCondition { container.settings.loaded.value == null }

        setContent {
            val loaded by container.settings.loaded.collectAsStateWithLifecycle()
            val settings = loaded ?: return@setContent
            val userData by container.userData.data.collectAsStateWithLifecycle()
            val packs by container.iconPacks.packs.collectAsStateWithLifecycle()
            val customization = remember(userData.iconOverrides, userData.folderColors, packs, settings.activeIconPack) {
                IconCustomization(
                    overrides = userData.iconOverrides,
                    pack = packs.firstOrNull { it.id == settings.activeIconPack },
                    folderColors = userData.folderColors,
                )
            }
            WexTheme(settings) {
                CompositionLocalProvider(LocalIconCustomization provides customization) {
                    val start = remember {
                        if (settings.onboardingDone && StoragePermission.isGranted(this)) Route.Browser else Route.Onboarding
                    }
                    val backStack = rememberNavBackStack(start)
                    // Интенты обрабатываются по очереди; эффект не перезапускается при сбросе pendingOpen.
                    LaunchedEffect(Unit) {
                        launch {
                            safRequests.collect { path ->
                                val intent = client.wexmanager.core.fs.SafAccess.requestIntent(this@MainActivity, path)
                                if (intent == null) {
                                    Toast.makeText(this@MainActivity, "Эту папку нельзя открыть через доступ к каталогу", Toast.LENGTH_LONG).show()
                                    return@collect
                                }
                                pendingSafPath = path
                                runCatching { safLauncher.launch(intent) }.onFailure {
                                    pendingSafPath = null
                                    Toast.makeText(this@MainActivity, "Не удалось открыть выбор папки", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                        snapshotFlow { backStack.lastOrNull() }.first { it != null && it != Route.Onboarding }
                        pendingOpen.filterNotNull().collect { intent ->
                            pendingOpen.value = null
                            handleOpenIntent(intent, backStack)
                        }
                    }
                    // Surface задаёт LocalContentColor = onSurface для всех экранов (важно для тёмной темы).
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        AppNavigation(
                            backStack = backStack,
                            onRequestPermission = {
                                runCatching { startActivity(StoragePermission.manageAllFilesIntent(this@MainActivity)) }
                            },
                        )
                        // Конфликт любой фоновой задачи — поверх любого экрана.
                        val tasks by container.taskManager.tasks.collectAsStateWithLifecycle()
                        val conflict = tasks.firstNotNullOfOrNull { it.conflict }
                        if (conflict != null) key(conflict) { ConflictDialog(conflict) { conflict.resolve(it) } }
                        // Пароль архива — тоже поверх любого экрана.
                        val pwTask = tasks.firstOrNull { it.needsPassword }
                        if (pwTask != null) {
                            val saved by container.userData.data.collectAsStateWithLifecycle()
                            key(pwTask.id) {
                                ArchivePasswordDialog(
                                    title = pwTask.title,
                                    wrong = pwTask.passwordWrong,
                                    saved = saved.archivePasswords,
                                    onSubmit = { pw, rememberPw ->
                                        val newId = container.taskManager.retryWithPassword(pwTask.id, pw)
                                        // Запоминаем только подошедший пароль — неверные в список не копятся.
                                        if (rememberPw && newId != null) container.appScope.launch {
                                            val done = container.taskManager.tasks.first { list -> list.none { it.id == newId && it.isActive } }
                                            if (done.any { it.id == newId && it.status == client.wexmanager.core.ops.TaskStatus.Done }) {
                                                container.userData.addArchivePassword(pw)
                                            }
                                        }
                                    },
                                    onDismiss = { container.taskManager.dismiss(pwTask.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
