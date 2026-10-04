package com.yunfie.illustia.ui.components

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Applied as lazy content padding, so scrolled items can move behind the header.
val LocalScrollHeaderInset = staticCompositionLocalOf { 0.dp }

/** Keeps header controls sharp while blurring only the content behind their bounds. */
@Composable
fun ScrollBlurScaffold(
    enabled: Boolean,
    scrollBehavior: ScrollBehavior,
    scrollFraction: () -> Float = { scrollBehavior.state.collapsedFraction },
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!enabled) {
        Column(modifier = modifier) {
            header()
            content()
        }
        return
    }
    var headerHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    ScrollBlurOverlay(
        enabled = true,
        fraction = scrollFraction,
        modifier = modifier,
        onHeaderHeight = { headerHeight = it },
        header = header,
    ) {
        CompositionLocalProvider(LocalScrollHeaderInset provides with(density) { headerHeight.toDp() }) {
            Column(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
fun ScrollBlurOverlay(
    enabled: Boolean,
    fraction: () -> Float,
    modifier: Modifier = Modifier,
    opaqueAtTop: Boolean = true,
    onHeaderHeight: (Int) -> Unit = {},
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier) {
            content()
            header()
        }
        return
    }
    val surface = MiuixTheme.colorScheme.surface
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !LocalFastScrolling.current
    var measuredHeaderHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val maxFadeHeightPx = with(density) { 44.dp.toPx() }
    val maxFadeOverlapPx = with(density) { 16.dp.toPx() }

    val targetFraction = fraction().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec =
            spring(
                stiffness = Spring.StiffnessMedium,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        label = "scrollBlurFraction",
    )
    val progress = (animatedFraction * animatedFraction * (3f - 2f * animatedFraction)).coerceIn(0f, 1f)

    Box(modifier = modifier.clipToBounds()) {
        Box(
            Modifier.fillMaxSize().then(
                if (supportsBlur) {
                    Modifier.drawWithContent {
                        source.record { this@drawWithContent.drawContent() }
                        drawLayer(source)
                    }
                } else {
                    Modifier
                },
            ),
        ) { content() }

        if (measuredHeaderHeight > 0) {
            val currentFadeHeightPx = maxFadeHeightPx * progress
            val totalBackdropHeightPx = measuredHeaderHeight + currentFadeHeightPx
            val totalBackdropHeightDp = with(density) { totalBackdropHeightPx.toDp() }
            val baseAlpha = ((if (opaqueAtTop) 1f else progress) - 0.18f * progress).coerceIn(0f, 1f)

            if (baseAlpha > 0f || (supportsBlur && progress > 0f)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(totalBackdropHeightDp)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }.drawWithContent {
                                val fadeOverlapPx = maxFadeOverlapPx * progress
                                val fadeStartPx = (measuredHeaderHeight - fadeOverlapPx).coerceAtLeast(0f)
                                val fadeStartRatio =
                                    if (totalBackdropHeightPx > 0f) {
                                        (fadeStartPx / totalBackdropHeightPx).coerceIn(0f, 1f)
                                    } else {
                                        1f
                                    }

                                if (supportsBlur && progress > 0f) {
                                    val blurRadius = 16.dp.toPx() * progress
                                    blurred.renderEffect = BlurEffect(blurRadius, blurRadius)
                                    blurred.record { drawLayer(source) }
                                    drawLayer(blurred)

                                    val blurMaskBrush =
                                        Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to Color.Black,
                                                    fadeStartRatio to Color.Black,
                                                    1f to Color.Transparent,
                                                ),
                                            startY = 0f,
                                            endY = totalBackdropHeightPx,
                                        )
                                    drawRect(
                                        brush = blurMaskBrush,
                                        size = size,
                                        blendMode = BlendMode.DstIn,
                                    )
                                }

                                if (baseAlpha > 0f) {
                                    val surfaceBrush =
                                        Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to surface.copy(alpha = baseAlpha),
                                                    fadeStartRatio to surface.copy(alpha = baseAlpha),
                                                    1f to Color.Transparent,
                                                ),
                                            startY = 0f,
                                            endY = totalBackdropHeightPx,
                                        )
                                    drawRect(brush = surfaceBrush, size = size)
                                }
                            },
                )
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    measuredHeaderHeight = it.height
                    onHeaderHeight(it.height)
                },
        ) { header() }
    }
}
