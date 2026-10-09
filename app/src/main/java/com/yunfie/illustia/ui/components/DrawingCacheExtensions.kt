package com.yunfie.illustia.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Applies a vertically-oriented gradient background cached via [drawWithCache].
 *
 * Prevents allocating new [Brush] objects on every frame during scrolling
 * and avoids GPU pipeline shader state flushes.
 */
fun Modifier.cachedVerticalGradient(
    colors: List<Color>,
    startYRatio: Float = 0f,
    endYRatio: Float = 1f,
): Modifier =
    drawWithCache {
        val startY = size.height * startYRatio.coerceIn(0f, 1f)
        val endY = size.height * endYRatio.coerceIn(0f, 1f)
        val brush =
            Brush.verticalGradient(
                colors = colors,
                startY = startY,
                endY = endY,
            )
        onDrawBehind {
            drawRect(brush = brush)
        }
    }
