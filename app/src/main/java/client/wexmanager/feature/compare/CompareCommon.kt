package client.wexmanager.feature.compare

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import client.wexmanager.R
import client.wexmanager.core.util.Format
import client.wexmanager.ui.components.PolygonShape
import client.wexmanager.ui.theme.LocalIsDarkTheme
import com.materialkolor.ktx.harmonize
import java.util.Locale

/** Моноширинный шрифт для diff и хешей. */
internal val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono), Font(R.font.jetbrains_mono_bold, FontWeight.Bold))

internal fun childPath(parent: String, name: String): String = if (parent == "/") "/$name" else "$parent/$name"

internal fun normalizeRoot(path: String): String = path.trimEnd('/').ifEmpty { "/" }

/** Путь относительно [root] без ведущего «/»; пустая строка — сам корень. */
internal fun relativeTo(path: String, root: String): String =
    if (root == "/") path.trimStart('/') else path.removePrefix(root).trimStart('/')

/** Отображаемое имя папки (последний сегмент пути). */
internal fun displayName(path: String): String = if (path == "/") "/" else path.substringAfterLast('/').ifEmpty { path }

/**
 * Набор расширений без точки и без «*».
 * «apk, jpg», «*.so» и «.so» — один и тот же набор; регистр не важен.
 * Пустая строка — пустой набор (фильтр не задан).
 */
internal fun parseExtensions(text: String): Set<String> {
    if (text.isBlank()) return emptySet()
    val out = LinkedHashSet<String>()
    for (part in text.split(',', ';', ' ', '\n', '\t', '\r')) {
        var token = part.trim().lowercase(Locale.ROOT)
        if (token.isEmpty()) continue
        if (token.startsWith("*")) token = token.substring(1).trim()
        if (token.startsWith(".")) token = token.substring(1).trim()
        if (token.isEmpty() || token.any { it == '/' || it == '\\' || it == '*' || it == '?' }) continue
        out += token
    }
    return out
}

/** Цвета отличий, гармонизированные с текущей темой. */
@Immutable
internal data class DiffPalette(
    val added: Color,
    val removed: Color,
    val changed: Color,
    val addedBg: Color,
    val removedBg: Color,
    val changedBg: Color,
    val addedMark: Color,
    val removedMark: Color,
)

@Composable
internal fun rememberDiffPalette(): DiffPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = LocalIsDarkTheme.current
    return remember(scheme.primary, dark) {
        val green = Color(if (dark) 0xFF7BD88F else 0xFF2E7D32).harmonize(scheme.primary, matchSaturation = false)
        val red = Color(if (dark) 0xFFFF8A80 else 0xFFC62828).harmonize(scheme.primary, matchSaturation = false)
        val yellow = Color(if (dark) 0xFFFFD54F else 0xFFB7860B).harmonize(scheme.primary, matchSaturation = false)
        val bgAlpha = if (dark) 0.16f else 0.12f
        val markAlpha = if (dark) 0.38f else 0.30f
        DiffPalette(
            added = green,
            removed = red,
            changed = yellow,
            addedBg = green.copy(alpha = bgAlpha),
            removedBg = red.copy(alpha = bgAlpha),
            changedBg = yellow.copy(alpha = bgAlpha),
            addedMark = green.copy(alpha = markAlpha),
            removedMark = red.copy(alpha = markAlpha),
        )
    }
}

/** Пустое/ошибочное состояние с фигурой Material Shapes и «пружинным» появлением. */
@Composable
internal fun ComparePane(
    polygon: RoundedPolygon,
    icon: ImageVector,
    title: String,
    text: String?,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    action: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = remember(polygon) { PolygonShape(polygon) }
    val appear = remember { Animatable(0f) }
    val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { appear.animateTo(1f, spatial) }
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(128.dp)
                .graphicsLayer {
                    val p = appear.value
                    scaleX = 0.6f + 0.4f * p
                    scaleY = 0.6f + 0.4f * p
                    alpha = p.coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = (1f - appear.value) * -45f }
                    .clip(shape)
                    .background(if (error) scheme.errorContainer else scheme.secondaryContainer),
            )
            Icon(
                icon, null,
                tint = if (error) scheme.onErrorContainer else scheme.onSecondaryContainer,
                modifier = Modifier.size(54.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, textAlign = TextAlign.Center)
        if (!text.isNullOrEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (action != null || secondary != null) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondary?.let { (label, onClick) ->
                    androidx.compose.material3.OutlinedButton(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text(label) }
                }
                action?.let { (label, onClick) ->
                    Button(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text(label) }
                }
            }
        }
    }
}

/** Иконка в декоративной фигуре. */
@Composable
internal fun ShapeIcon(
    polygon: RoundedPolygon,
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = size * 0.5f,
) {
    val shape = remember(polygon) { PolygonShape(polygon) }
    Box(modifier.size(size).clip(shape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size(iconSize))
    }
}

/** Компактная «таблетка» со значением и подписью. */
@Composable
internal fun StatPill(label: String, value: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .widthIn(min = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = content, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = content.copy(alpha = 0.8f), maxLines = 1)
    }
}

@Composable
internal fun CompareSectionHeader(title: String, trailing: String = "", modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Секунды с одним знаком: «3,2 с». */
internal fun secondsText(ms: Long): String = String.format(Format.RU, "%.1f с", ms / 1000f)

/**
 * Экран прогресса долгой операции: волновой индикатор вокруг «дышащей» фигуры,
 * шаги (если есть), счётчики, текущий путь и кнопка отмены.
 * [fraction] = `null` — неопределённый прогресс.
 */
@Composable
internal fun ProgressPane(
    headline: String,
    caption: String,
    fraction: Float?,
    stats: List<Pair<String, String>>,
    current: String,
    onCancel: () -> Unit,
    steps: List<String> = emptyList(),
    step: Int = 0,
) {
    val scheme = MaterialTheme.colorScheme
    val density = androidx.compose.ui.platform.LocalDensity.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(208.dp), contentAlignment = Alignment.Center) {
            val stroke = remember(density) {
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = with(density) { 6.dp.toPx() },
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
            if (fraction == null) {
                androidx.compose.material3.CircularWavyProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                    stroke = stroke,
                    trackStroke = stroke,
                )
            } else {
                val animated by androidx.compose.animation.core.animateFloatAsState(
                    fraction,
                    MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "progress",
                )
                androidx.compose.material3.CircularWavyProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxSize(),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHighest,
                    stroke = stroke,
                    trackStroke = stroke,
                )
            }
            androidx.compose.material3.ContainedLoadingIndicator(
                modifier = Modifier.size(100.dp),
                containerColor = scheme.primaryContainer,
                indicatorColor = scheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            headline,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
        )
        Text(caption, style = MaterialTheme.typography.titleMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            StepRow(steps, step)
        }
        if (stats.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.forEachIndexed { i, (label, value) ->
                    StatPill(
                        label, value,
                        if (i % 2 == 0) scheme.secondaryContainer else scheme.tertiaryContainer,
                        if (i % 2 == 0) scheme.onSecondaryContainer else scheme.onTertiaryContainer,
                    )
                }
            }
        }
        if (current.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.Surface(
                color = scheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 480.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        androidx.compose.material.icons.Icons.Rounded.FolderOpen, null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        current,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.MiddleEllipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.OutlinedButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text("Отмена") }
    }
}

/** Шаги многофазной операции: пройденные — с галочкой, текущий — выделен. */
@Composable
private fun StepRow(steps: List<String>, step: Int) {
    val scheme = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, title ->
            val active = i == step
            val done = i < step
            val bg by androidx.compose.animation.animateColorAsState(
                when {
                    active -> scheme.primary
                    done -> scheme.primaryContainer
                    else -> scheme.surfaceContainerHigh
                },
                motion.defaultEffectsSpec(),
                label = "stepBg",
            )
            val fg = when {
                active -> scheme.onPrimary
                done -> scheme.onPrimaryContainer
                else -> scheme.onSurfaceVariant
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (done) {
                    Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, tint = fg, modifier = Modifier.size(14.dp))
                } else {
                    Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
            }
        }
    }
}
