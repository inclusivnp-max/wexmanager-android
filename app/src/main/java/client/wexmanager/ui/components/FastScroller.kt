package client.wexmanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Общий интерфейс для списка/сетки. */
interface ScrollTarget {
    val firstVisible: Int
    val visibleCount: Int
    val totalCount: Int
    val isScrolling: Boolean
    suspend fun scrollToItem(index: Int)
}

fun LazyListState.asScrollTarget() = object : ScrollTarget {
    override val firstVisible get() = firstVisibleItemIndex
    override val visibleCount get() = layoutInfo.visibleItemsInfo.size
    override val totalCount get() = layoutInfo.totalItemsCount
    override val isScrolling get() = isScrollInProgress
    override suspend fun scrollToItem(index: Int) = this@asScrollTarget.scrollToItem(index)
}

fun LazyGridState.asScrollTarget() = object : ScrollTarget {
    override val firstVisible get() = firstVisibleItemIndex
    override val visibleCount get() = layoutInfo.visibleItemsInfo.size
    override val totalCount get() = layoutInfo.totalItemsCount
    override val isScrolling get() = isScrollInProgress
    override suspend fun scrollToItem(index: Int) = this@asScrollTarget.scrollToItem(index)
}

/**
 * Быстрая прокрутка: ползунок справа появляется при прокрутке длинного списка,
 * его можно тянуть — рядом показывается подсказка (первая буква имени).
 */
@Composable
fun FastScroller(
    target: ScrollTarget,
    modifier: Modifier = Modifier,
    label: (Int) -> String? = { null },
    minItems: Int = 40,
) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val enough by remember(target) { derivedStateOf { target.totalCount >= minItems && target.totalCount > target.visibleCount } }

    LaunchedEffect(target) {
        snapshotFlow { target.isScrolling || dragging }.collectLatest { active ->
            if (active) visible = true else {
                delay(1200)
                visible = false
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxHeight().width(48.dp)) {
        val density = LocalDensity.current
        val trackPx = with(density) { maxHeight.toPx() }
        val thumbHeight = 52.dp
        val thumbPx = with(density) { thumbHeight.toPx() }
        val fraction by remember(target) {
            derivedStateOf {
                val total = target.totalCount - target.visibleCount
                if (total <= 0) 0f else (target.firstVisible.toFloat() / total).coerceIn(0f, 1f)
            }
        }
        val shown = if (dragging) dragFraction else fraction
        val thumbWidth by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "thumbW")
        AnimatedVisibility(
            visible = enough && (visible || dragging),
            enter = fadeIn() + slideInHorizontally { it / 2 },
            exit = fadeOut() + slideOutHorizontally { it / 2 },
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(48.dp)
                    .pointerInput(target) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                dragFraction = ((offset.y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, amount ->
                            change.consume()
                            dragFraction = (dragFraction + amount / (trackPx - thumbPx)).coerceIn(0f, 1f)
                            val index = (dragFraction * (target.totalCount - 1)).roundToInt()
                            scope.launch { target.scrollToItem(index) }
                        }
                    },
            ) {
                val y = ((trackPx - thumbPx) * shown).roundToInt()
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset { IntOffset(0, y) }
                        .padding(end = 4.dp)
                        .size(thumbWidth, thumbHeight)
                        .background(
                            if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            RoundedCornerShape(50),
                        ),
                )
                if (dragging) {
                    val text = label((dragFraction * (target.totalCount - 1)).roundToInt())
                    if (text != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 28.dp, bottomEnd = 6.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset { IntOffset(-with(density) { 48.dp.roundToPx() }, y - with(density) { 4.dp.roundToPx() }) }
                                .size(56.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}
