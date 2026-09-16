package com.yunfie.illustia.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import com.yunfie.illustia.ui.components.PixivImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private suspend fun PointerInputScope.detectZoomAndPanGestures(
    isZoomed: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: () -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var isTransforming = isZoomed()

        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (canceled) break

            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
                isTransforming = true
            }

            if (isTransforming) {
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = true)

                if (isZoomed() || pressedCount >= 2) {
                    val validZoom = zoomChange.isFinite() && zoomChange > 0f
                    val hasMovement = zoomChange != 1f || panChange != Offset.Zero
                    if (validZoom && hasMovement) {
                        onGesture(centroid, panChange, zoomChange)
                    }
                    event.changes.forEach { change ->
                        if (change.positionChanged()) {
                            change.consume()
                        }
                    }
                }
            }
        } while (event.changes.any { it.pressed })

        onGestureEnd()
    }
}

@Composable
internal fun ZoomablePixivImage(
    url: String,
    contentDescription: String,
    isActive: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val animationScope = rememberCoroutineScope()
    val zoomAnimation = remember { arrayOfNulls<Job>(1) }
    val haptic = LocalHapticFeedback.current

    fun notifyZoomChanged(
        previous: Float,
        current: Float,
    ) {
        val wasZoomed = previous > 1.02f
        val zoomed = current > 1.02f
        if (wasZoomed != zoomed) onZoomChanged(zoomed)
    }

    fun clampedOffset(
        candidate: Offset,
        atScale: Float,
    ): Offset {
        if (atScale <= 1f || viewportSize.width == 0 || viewportSize.height == 0) {
            return Offset.Zero
        }
        val maxX = (viewportSize.width * (atScale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (viewportSize.height * (atScale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun animateTo(
        targetScale: Float,
        targetOffset: Offset,
    ) {
        val startScale = scale
        val startOffset = offset
        zoomAnimation[0]?.cancel()
        zoomAnimation[0] =
            animationScope.launch {
                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                ) { progress, _ ->
                    val previous = scale
                    scale = startScale + (targetScale - startScale) * progress
                    offset =
                        Offset(
                            startOffset.x + (targetOffset.x - startOffset.x) * progress,
                            startOffset.y + (targetOffset.y - startOffset.y) * progress,
                        )
                    notifyZoomChanged(previous, scale)
                }
            }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            zoomAnimation[0]?.cancel()
            val previous = scale
            scale = 1f
            offset = Offset.Zero
            notifyZoomChanged(previous, 1f)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
                .pointerInput(url) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { tapOffset ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (scale > 1.02f) {
                                animateTo(1f, Offset.Zero)
                            } else {
                                val targetScale = 2.5f
                                val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                val focalPoint = tapOffset - viewportCenter
                                val targetOffset = clampedOffset(-focalPoint * (targetScale - 1f), targetScale)
                                animateTo(targetScale, targetOffset)
                            }
                        },
                    )
                }.pointerInput(url) {
                    detectZoomAndPanGestures(
                        isZoomed = { scale > 1.02f },
                        onGesture = { centroid, pan, zoom ->
                            if (!zoom.isFinite() || zoom <= 0f) return@detectZoomAndPanGestures
                            zoomAnimation[0]?.cancel()
                            val previousScale = scale
                            val nextScale = (scale * zoom).coerceIn(1f, 6f)
                            if (!nextScale.isFinite() || scale <= 0f) return@detectZoomAndPanGestures
                            val appliedZoom = nextScale / scale
                            val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                            val focalPoint = centroid - viewportCenter
                            val transformedOffset =
                                offset + pan + (focalPoint - offset) * (1f - appliedZoom)

                            scale = nextScale
                            offset =
                                if (scale > 1.02f) {
                                    clampedOffset(transformedOffset, scale)
                                } else {
                                    Offset.Zero
                                }
                            notifyZoomChanged(previousScale, scale)
                        },
                        onGestureEnd = {
                            if (scale < 1.02f) {
                                animateTo(1f, Offset.Zero)
                            } else {
                                offset = clampedOffset(offset, scale)
                            }
                        },
                    )
                },
    ) {
        PixivImage(
            url = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            crossfade = false,
        )
    }
}
