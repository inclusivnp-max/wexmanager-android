package client.wexmanager.feature.viewer

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.graphics.shapes.RoundedPolygon
import client.wexmanager.core.fs.FileItem
import client.wexmanager.core.fs.FileKind
import client.wexmanager.core.fs.LocalFileSystem
import client.wexmanager.core.fs.NaturalOrder
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.theme.LocalAppSettings
import client.wexmanager.ui.theme.LocalIsDarkTheme
import client.wexmanager.ui.theme.toKolor
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Тёмная схема для полноэкранных просмотрщиков. Если приложение уже в тёмной теме — ничего не меняет,
 * иначе генерирует тёмную схему из текущего primary, чтобы акценты совпадали с остальным UI.
 */
@Composable
internal fun ViewerDarkTheme(content: @Composable () -> Unit) {
    if (LocalIsDarkTheme.current) {
        content()
        return
    }
    val settings = LocalAppSettings.current
    val scheme = rememberDynamicColorScheme(
        seedColor = MaterialTheme.colorScheme.primary,
        isDark = true,
        isAmoled = false,
        style = settings.paletteStyle.toKolor(),
        contrastLevel = settings.contrast.toDouble(),
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    CompositionLocalProvider(LocalIsDarkTheme provides true) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * Управление системными барами для иммерсивных экранов: светлые значки на тёмном фоне,
 * скрытие баров при [hidden]. При уходе с экрана всё возвращается как было.
 */
@Composable
internal fun ImmersiveSystemBars(hidden: Boolean, darkContent: Boolean = true) {
    val activity = LocalActivity.current ?: return
    val view = LocalView.current
    val controller = remember(activity, view) { WindowCompat.getInsetsController(activity.window, view) }
    DisposableEffect(controller, darkContent) {
        val prevStatus = controller.isAppearanceLightStatusBars
        val prevNav = controller.isAppearanceLightNavigationBars
        val prevBehavior = controller.systemBarsBehavior
        if (darkContent) {
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = prevStatus
            controller.isAppearanceLightNavigationBars = prevNav
            controller.systemBarsBehavior = prevBehavior
        }
    }
    LaunchedEffect(controller, hidden) {
        if (hidden) controller.hide(WindowInsetsCompat.Type.systemBars())
        else controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/** Не даёт экрану гаснуть, пока [enabled] (например, во время воспроизведения видео). */
@Composable
internal fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Соседние файлы той же папки, подходящие под [accept], в естественном порядке имён.
 * Сам файл [path] всегда присутствует в результате. Скрытые файлы — только если [includeHidden].
 */
internal suspend fun loadSiblings(
    path: String,
    includeHidden: Boolean,
    accept: (FileKind) -> Boolean,
): List<FileItem> = withContext(Dispatchers.IO) {
    val current = LocalFileSystem.stat(path)
    val parent = File(path).parent ?: return@withContext listOf(current)
    val names = File(parent).list() ?: return@withContext listOf(current)
    val currentName = current.name
    val result = ArrayList<FileItem>()
    for ((i, name) in names.withIndex()) {
        if (i and 255 == 0) ensureActive()
        if (name == currentName) continue
        if (!includeHidden && name.startsWith('.')) continue
        if (!accept(FileKind.of(name, false))) continue
        val item = LocalFileSystem.stat(if (parent == "/") "/$name" else "$parent/$name", name)
        if (!item.isDirectory) result += item
    }
    result += current
    result.sortWith { a, b -> NaturalOrder.compare(a.name, b.name) }
    result
}

/** Пустое/ошибочное состояние в общем стиле приложения: фигура MaterialShapes с иконкой. */
@Composable
internal fun ViewerMessage(
    icon: ImageVector,
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
    polygon: RoundedPolygon = MaterialShapes.Cookie9Sided,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    actions: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val shape = remember(polygon) { PolygonShape(polygon) }
        Box(Modifier.size(112.dp).clip(shape).background(container), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = content, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actions != null) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
        }
    }
}

@Composable
internal fun ViewerTonalAction(text: String, icon: ImageVector, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick) {
        Icon(icon, null, Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text(text)
    }
}
