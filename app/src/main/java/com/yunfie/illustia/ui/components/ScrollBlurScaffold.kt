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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

// Applied as lazy content padding, so scrolled items can move behind the header.
val LocalScrollHeaderInset = staticCompositionLocalOf { 0.dp }

/** Keeps header controls sharp while blurring only the content behind their bounds. */
@Composable
fun ScrollBlurScaffold(
    enabled: Boolean,
    scrollBehavior: ScrollBehavior,
    scrollFraction: () -> Float = {
        // Track both collapsedFraction and contentOffset so micro-scrolls immediately blur
        val scrollOffsetPx = -scrollBehavior.state.contentOffset
        val overlapProgress = (scrollOffsetPx / 60f).coerceIn(0f, 1f)
        maxOf(scrollBehavior.state.collapsedFraction, overlapProgress).coerceIn(0f, 1f)
    },
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
    val outline = MiuixTheme.colorScheme.outline
    val backdrop = rememberLayerBackdrop()
    val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var measuredHeaderHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current

    val targetFraction = fraction().coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec =
            spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioNoBouncy,
            ),
        label = "scrollBlurFraction",
    )
    val progress = (animatedFraction * animatedFraction * (3f - 2f * animatedFraction)).coerceIn(0f, 1f)

    Box(modifier = modifier.clipToBounds()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (supportsBlur) {
                        Modifier.layerBackdrop(backdrop)
                    } else {
                        Modifier
                    },
                ),
        ) { content() }

        if (measuredHeaderHeight > 0) {
            val totalBackdropHeightDp = with(density) { measuredHeaderHeight.toDp() }
            val baseAlpha = ((if (opaqueAtTop) 1f else progress) - 0.28f * progress).coerceIn(0f, 1f)

            if (baseAlpha > 0f || (supportsBlur && progress > 0f)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(totalBackdropHeightDp)
                            .then(
                                if (supportsBlur && progress > 0f) {
                                    Modifier.progressiveTextureBlur(
                                        backdrop = backdrop,
                                        shape = RectangleShape,
                                        blurRadius = 20f * progress,
                                        gradient = ProgressiveBlur.Top,
                                    )
                                } else {
                                    Modifier
                                },
                            ).drawBehind {
                                if (baseAlpha > 0f) {
                                    drawRect(color = surface.copy(alpha = baseAlpha))
                                }

                                // Subtle, clean hairline divider on scroll instead of heavy shadow
                                if (progress > 0f) {
                                    val dividerHeightPx = 1.dp.toPx()
                                    drawRect(
                                        color = outline.copy(alpha = 0.08f * progress),
                                        topLeft =
                                            androidx.compose.ui.geometry
                                                .Offset(0f, size.height - dividerHeightPx),
                                        size =
                                            androidx.compose.ui.geometry
                                                .Size(size.width, dividerHeightPx),
                                    )
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
