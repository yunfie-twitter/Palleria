package com.yunfie.illustia.ui.components

import android.os.SystemClock
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

val LocalFastScrolling = staticCompositionLocalOf { false }

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
internal fun rememberScrollRendering(enabled: Boolean): Pair<NestedScrollConnection, Boolean> {
    val density = LocalDensity.current.density
    val scope = rememberCoroutineScope()
    val fast = remember(enabled) { mutableStateOf(false) }
    val connection =
        remember(enabled, density) {
            object : NestedScrollConnection {
                val speed = ScrollSpeedTracker()
                var reset: Job? = null

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (enabled && speed.isFast(consumed.y / density, SystemClock.uptimeMillis())) {
                        fast.value = true
                        reset?.cancel()
                        reset =
                            scope.launch {
                                delay(SCROLL_SETTLE_MILLIS)
                                fast.value = false
                            }
                    }
                    return Offset.Zero
                }
            }
        }
    DisposableEffect(connection) { onDispose { connection.reset?.cancel() } }
    return connection to fast.value
}

/** Stable grid keys animate to their new cells; scrolling itself never starts a layout animation. */
@Composable
fun LazyGridItemScope.animatedGridPlacement(): Modifier =
    if (LocalFastScrolling.current) {
        Modifier
    } else {
        Modifier
            .animateItem(fadeInSpec = null, placementSpec = tween(GRID_ANIMATION_MILLIS), fadeOutSpec = null)
    }

private const val SCROLL_SETTLE_MILLIS = 180L
private const val GRID_ANIMATION_MILLIS = 180
