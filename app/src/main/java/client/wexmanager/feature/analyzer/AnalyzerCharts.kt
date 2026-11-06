package client.wexmanager.feature.analyzer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import client.wexmanager.ui.components.FileGlyphs
import com.materialkolor.ktx.harmonize
import kotlin.math.cos
import kotlin.math.sin

@Immutable
class Slice(val label: String, val size: Long, val color: Color, val node: Node?)

/** Палитра сегментов диаграммы: цвета темы + гармонизированные с primary акценты. */
@Composable
fun slicePalette(): List<Color> {
    val s = MaterialTheme.colorScheme
    return remember(s.primary, s.secondary, s.tertiary, s.tertiaryContainer) {
        listOf(
            s.primary,
            s.tertiary,
            Color(0xFFE2A93B).harmonize(s.primary, matchSaturation = false),
            s.secondary,
            Color(0xFF2E9AA8).harmonize(s.primary, matchSaturation = false),
            s.tertiaryContainer,
        )
    }
}

@Composable
fun categoryColor(category: Category): Color {
    val scheme = MaterialTheme.colorScheme
    if (category == Category.Other) return scheme.outline
    return remember(category, scheme.primary) { FileGlyphs.colorFor(category.sample).harmonize(scheme.primary, matchSaturation = false) }
}

/** Доля меньше этой уходит в «Прочее», чтобы на кольце не было неразличимых крошек. */
private const val MIN_SLICE_FRACTION = 0.02

/** Топ-6 детей папки + «Прочее». */
fun buildSlices(dir: DirNode, palette: List<Color>, otherColor: Color): List<Slice> {
    if (dir.size <= 0L) return emptyList()
    val out = ArrayList<Slice>(7)
    var rest = 0L
    for (child in dir.children) {
        if (child.size <= 0L) continue
        if (out.size < 6 && child.size.toDouble() / dir.size >= MIN_SLICE_FRACTION) {
            out += Slice(child.name, child.size, palette[out.size % palette.size], child)
        } else {
            rest += child.size
        }
    }
    if (rest > 0L) out += Slice("Прочее", rest, otherColor, null)
    return out
}

/**
 * Кольцевая диаграмма: дуги со скруглёнными концами и зазорами между ними,
 * появление — разворачиванием по часовой стрелке.
 */
@Composable
fun DonutChart(
    slices: List<Slice>,
    trackColor: Color,
    modifier: Modifier = Modifier,
    thickness: Dp = 22.dp,
    gap: Dp = 5.dp,
    centerContent: @Composable () -> Unit,
) {
    val sweep = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(slices) {
        sweep.snapTo(0f)
        sweep.animateTo(1f, spec)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val pieceLabels = slices.joinToString(", ") { it.label }
        Canvas(
            if (pieceLabels.isEmpty()) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxSize().semantics { contentDescription = pieceLabels }
            },
        ) {
            val strokePx = thickness.toPx()
            val diameter = size.minDimension - strokePx
            if (diameter <= 0f) return@Canvas
            val radius = diameter / 2f
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawCircle(trackColor, radius, style = Stroke(strokePx))

            val total = slices.sumOf { it.size }.toFloat()
            if (total <= 0f) return@Canvas
            val limit = 360f * sweep.value.coerceIn(0f, 1f)
            if (slices.size == 1) {
                drawArc(slices[0].color, -90f, limit, false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                return@Canvas
            }
            val capDeg = Math.toDegrees((strokePx / 2f / radius).toDouble()).toFloat()
            val gapDeg = Math.toDegrees((gap.toPx() / radius).toDouble()).toFloat()
            var start = 0f
            for (s in slices) {
                val full = s.size / total * 360f
                val end = minOf(start + full, limit)
                if (end <= start) break
                val visible = end - start
                val rounded = visible - 2f * (capDeg + gapDeg / 2f)
                if (rounded > 0f) {
                    drawArc(
                        s.color, -90f + start + capDeg + gapDeg / 2f, rounded, false, topLeft, arcSize,
                        style = Stroke(strokePx, cap = StrokeCap.Round),
                    )
                } else if (visible - gapDeg > 0f) {
                    // Слишком узкий сегмент для скруглений — рисуем с прямыми концами.
                    drawArc(
                        s.color, -90f + start + gapDeg / 2f, visible - gapDeg, false, topLeft, arcSize,
                        style = Stroke(strokePx, cap = StrokeCap.Butt),
                    )
                } else if (visible > 0f) {
                    val a = Math.toRadians((-90f + start + visible / 2f).toDouble())
                    drawCircle(
                        s.color, strokePx / 4f,
                        Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat()),
                    )
                }
                start += full
            }
        }
        centerContent()
    }
}

/** Горизонтальная сегментированная полоса категорий со скруглёнными сегментами. */
@Composable
fun SegmentedBar(
    values: List<Pair<Long, Color>>,
    trackColor: Color,
    modifier: Modifier = Modifier,
    gap: Dp = 3.dp,
) {
    val grow = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
    LaunchedEffect(values) {
        grow.snapTo(0f)
        grow.animateTo(1f, spec)
    }
    Canvas(modifier) {
        val h = size.height
        val r = CornerRadius(h / 2f, h / 2f)
        drawRoundRect(trackColor, cornerRadius = r)
        val total = values.sumOf { it.first }.toFloat()
        if (total <= 0f) return@Canvas
        val gapPx = gap.toPx()
        val visible = values.filter { it.first > 0 }
        val usable = size.width - gapPx * (visible.size - 1).coerceAtLeast(0)
        val limit = size.width * grow.value.coerceIn(0f, 1f)
        var x = 0f
        for ((v, color) in visible) {
            val w = (v / total * usable).coerceAtLeast(h * 0.35f)
            val right = minOf(x + w, limit)
            if (right <= x) break
            val segW = right - x
            val cr = minOf(h / 2f, segW / 2f)
            drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(segW, h), cornerRadius = CornerRadius(cr, cr))
            x += w + gapPx
        }
    }
}
