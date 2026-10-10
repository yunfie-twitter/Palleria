package com.yunfie.illustia.ui.components

import android.os.SystemClock
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

val LocalFastScrolling = compositionLocalOf { false }

internal data class ScrollRenderingResult(
    val connection: NestedScrollConnection,
    val isFastScrolling: Boolean,
    val fastScrollingState: State<Boolean>,
)

internal class ScrollSpeedTracker {
    private var lastMillis = 0L

    fun isFast(
        deltaDp: Float,
        nowMillis: Long,
    ): Boolean {
        if (deltaDp == 0f) return false
        val elapsed = if (lastMillis == 0L) FRAME_MILLIS else (nowMillis - lastMillis).coerceAtLeast(MIN_SAMPLE_MILLIS)
        lastMillis = nowMillis
        return abs(deltaDp) * MILLIS_PER_SECOND / elapsed > FAST_DP_PER_SECOND
    }

    private companion object {
        const val MIN_SAMPLE_MILLIS = 4L
        const val FRAME_MILLIS = 16L
        const val MILLIS_PER_SECOND = 1000f
        const val FAST_DP_PER_SECOND = 1400f
    }
}

@Composable
internal fun rememberScrollRendering(enabled: Boolean): ScrollRenderingResult {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val fast = remember(enabled) { mutableStateOf(false) }

    val connection =
        remember(enabled, density) {
            object : NestedScrollConnection {
                val speed = ScrollSpeedTracker()
                var fastResetJob: Job? = null
                var lastFastScrollAtMillis = 0L

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (
                        enabled && speed.isFast(consumed.y / density, SystemClock.uptimeMillis())
                    ) {
                        lastFastScrollAtMillis = SystemClock.uptimeMillis()
                        if (!fast.value) fast.value = true
                        if (fastResetJob?.isActive != true) {
                            fastResetJob =
                                scope.launch {
                                    while (true) {
                                        val remaining =
                                            SCROLL_SETTLE_MILLIS -
                                                (SystemClock.uptimeMillis() - lastFastScrollAtMillis)
                                        if (remaining <= 0L) break
                                        delay(remaining)
                                    }
                                    fast.value = false
                                }
                        }
                    }
                    return Offset.Zero
                }
            }
        }
    DisposableEffect(connection) {
        onDispose {
            connection.fastResetJob?.cancel()
        }
    }
    return ScrollRenderingResult(
        connection = connection,
        isFastScrolling = fast.value,
        fastScrollingState = fast,
    )
}

/** Static zero-cost modifier for grid item placement, avoiding layout animations and recomposition overhead during scroll. */
@Composable
fun LazyGridItemScope.animatedGridPlacement(): Modifier = Modifier

private const val SCROLL_SETTLE_MILLIS = 180L
