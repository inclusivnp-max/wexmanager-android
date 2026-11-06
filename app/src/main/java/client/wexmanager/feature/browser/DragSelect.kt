package client.wexmanager.feature.browser

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import client.wexmanager.ui.theme.LocalAppSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Абстракция над списком и сеткой для поиска элемента под пальцем. */
interface HitTester {
    fun indexAt(offset: Offset): Int?
    suspend fun scrollBy(px: Float)
    val viewportHeight: Int
}

fun LazyListState.hitTester() = object : HitTester {
    override fun indexAt(offset: Offset): Int? =
        layoutInfo.visibleItemsInfo.firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }?.index
    override suspend fun scrollBy(px: Float) { this@hitTester.scrollBy(px) }
    override val viewportHeight: Int get() = layoutInfo.viewportSize.height
}

fun LazyGridState.hitTester() = object : HitTester {
    override fun indexAt(offset: Offset): Int? =
        layoutInfo.visibleItemsInfo.firstOrNull {
            offset.x.toInt() in it.offset.x..(it.offset.x + it.size.width) &&
                offset.y.toInt() in it.offset.y..(it.offset.y + it.size.height)
        }?.index
    override suspend fun scrollBy(px: Float) { this@hitTester.scrollBy(px) }
    override val viewportHeight: Int get() = layoutInfo.viewportSize.height
}

/**
 * Выделение протягиванием как в MT Manager: провести пальцем вправо от иконки файла,
 * затем вести вверх/вниз — выделяется диапазон, у краёв список автопрокручивается.
 * Если первый элемент был выделен — жест снимает выделение.
 */
@Composable
fun Modifier.dragToSelect(
    hitTester: HitTester,
    isSelected: (Int) -> Boolean,
    onRange: (anchor: Int, current: Int, select: Boolean) -> Unit,
    onStart: () -> Unit,
    onEnd: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val hapticsEnabled = LocalAppSettings.current.haptics
    val slop = LocalViewConfiguration.current.touchSlop
    val tester by rememberUpdatedState(hitTester)
    val selectedFn by rememberUpdatedState(isSelected)
    val rangeFn by rememberUpdatedState(onRange)
    val startFn by rememberUpdatedState(onStart)
    val endFn by rememberUpdatedState(onEnd)
    return this.pointerInput(Unit) {
        val zone = 84.dp.toPx()
        val edge = 72.dp.toPx()
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (down.position.x > zone) return@awaitEachGesture
                val anchor = tester.indexAt(down.position) ?: return@awaitEachGesture
                var total = Offset.Zero
                var active = false
                var select = true
                var last = anchor
                var pointer = down.position
                var scrollJob: Job? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    pointer = change.position
                    if (!active) {
                        total += change.positionChange()
                        if (abs(total.y) > slop && abs(total.y) > abs(total.x)) break // обычная прокрутка
                        if (total.x > slop) {
                            active = true
                            select = !selectedFn(anchor)
                            startFn()
                            if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            rangeFn(anchor, anchor, select)
                            scrollJob = launch {
                                while (isActive) {
                                    val h = tester.viewportHeight
                                    val p = pointer.y
                                    val speed = when {
                                        p < edge -> -(edge - p) / edge * 28f
                                        p > h - edge -> (p - (h - edge)) / edge * 28f
                                        else -> 0f
                                    }
                                    if (speed != 0f) {
                                        tester.scrollBy(speed)
                                        tester.indexAt(pointer)?.let { idx ->
                                            if (idx != last) { last = idx; rangeFn(anchor, idx, select) }
                                        }
                                    }
                                    delay(16)
                                }
                            }
                        }
                    }
                    if (active) {
                        change.consume()
                        tester.indexAt(change.position)?.let { idx ->
                            if (idx != last) {
                                last = idx
                                rangeFn(anchor, idx, select)
                                if (hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        }
                    }
                }
                scrollJob?.cancel()
                if (active) endFn()
            }
        }
    }
}
