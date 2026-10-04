package com.yunfie.illustia.ui.components

import android.os.Build
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val fadeHeightPx = with(density) { 24.dp.toPx() }

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
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged {
                    measuredHeaderHeight = it.height
                    onHeaderHeight(it.height)
                }.clipToBounds()
                .drawWithContent {
                    val progress = fraction().coerceIn(0f, 1f)
                    val baseAlpha = ((if (opaqueAtTop) 1f else progress) - 0.18f * progress).coerceIn(0f, 1f)
                    val edgeAlpha = baseAlpha * 0.42f

                    if (supportsBlur && progress > 0f) {
                        blurred.renderEffect = BlurEffect(16.dp.toPx() * progress, 16.dp.toPx() * progress)
                        blurred.record { drawLayer(source) }
                        drawLayer(blurred)
                    }

                    if (baseAlpha > 0f) {
                        if (progress > 0f && size.height > fadeHeightPx) {
                            val fadeStartRatio = ((size.height - fadeHeightPx) / size.height).coerceIn(0f, 1f)
                            val gradientBrush =
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0f to surface.copy(alpha = baseAlpha),
                                            fadeStartRatio to surface.copy(alpha = baseAlpha),
                                            1f to surface.copy(alpha = edgeAlpha),
                                        ),
                                    startY = 0f,
                                    endY = size.height,
                                )
                            drawRect(brush = gradientBrush, size = size)
                        } else {
                            drawRect(surface.copy(alpha = baseAlpha))
                        }
                    }
                    drawContent()
                },
        ) { header() }

        val progress = fraction().coerceIn(0f, 1f)
        if (progress > 0f && measuredHeaderHeight > 0) {
            val baseAlpha = ((if (opaqueAtTop) 1f else progress) - 0.18f * progress).coerceIn(0f, 1f)
            val edgeAlpha = baseAlpha * 0.42f
            if (edgeAlpha > 0f) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .graphicsLayer {
                                translationY = measuredHeaderHeight.toFloat()
                            }.background(
                                Brush.verticalGradient(
                                    colors =
                                        listOf(
                                            surface.copy(alpha = edgeAlpha),
                                            Color.Transparent,
                                        ),
                                ),
                            ),
                )
            }
        }
    }
}
