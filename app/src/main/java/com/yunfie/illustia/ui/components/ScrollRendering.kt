package com.yunfie.illustia.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

val LocalFastScrolling = compositionLocalOf { false }
val LocalScrolling = compositionLocalOf { false }

internal data class ScrollRenderingResult(
    val connection: NestedScrollConnection,
    val isFastScrolling: Boolean,
    val isScrolling: Boolean,
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
    val scrolling = remember { mutableStateOf(false) }

    val connection =
        remember(enabled, density) {
            object : NestedScrollConnection {
                val speed = ScrollSpeedTracker()
                var fastResetJob: Job? = null
                var scrollResetJob: Job? = null

                private fun markScrolling() {
                    scrolling.value = true
                    scrollResetJob?.cancel()
                    scrollResetJob =
                        scope.launch {
                            delay(SCROLL_SETTLE_MILLIS)
                            scrolling.value = false
                        }
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (abs(available.y) > 0.5f) {
                        markScrolling()
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (abs(consumed.y) > 0.5f) {
                        markScrolling()
                    }
                    if (enabled && speed.isFast(consumed.y / density, SystemClock.uptimeMillis())) {
                        fast.value = true
                        fastResetJob?.cancel()
                        fastResetJob =
                            scope.launch {
                                delay(SCROLL_SETTLE_MILLIS)
                                fast.value = false
                            }
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (abs(available.y) > 50f) {
                        markScrolling()
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    scrollResetJob?.cancel()
                    scrollResetJob =
                        scope.launch {
                            delay(SCROLL_SETTLE_MILLIS)
                            scrolling.value = false
                            fast.value = false
                        }
                    return Velocity.Zero
                }
            }
        }
    DisposableEffect(connection) {
        onDispose {
            connection.fastResetJob?.cancel()
            connection.scrollResetJob?.cancel()
        }
    }
    return ScrollRenderingResult(
        connection = connection,
        isFastScrolling = fast.value,
        isScrolling = scrolling.value,
    )
}

/** Stable grid keys animate to their new cells; scrolling itself never starts a layout animation. */
@Composable
fun LazyGridItemScope.animatedGridPlacement(): Modifier =
    if (LocalScrolling.current || LocalFastScrolling.current) {
        Modifier
    } else {
        Modifier
            .animateItem(fadeInSpec = null, placementSpec = tween(GRID_ANIMATION_MILLIS), fadeOutSpec = null)
    }

private const val SCROLL_SETTLE_MILLIS = 180L
private const val GRID_ANIMATION_MILLIS = 180
