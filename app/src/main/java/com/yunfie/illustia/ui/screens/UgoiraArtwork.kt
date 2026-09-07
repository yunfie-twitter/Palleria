package com.yunfie.illustia.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.normalizedUgoiraDelayMillis
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PixivImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun UgoiraArtwork(
    previewUrl: String,
    contentDescription: String,
    loadPlayback: suspend () -> UgoiraPlayback,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = false,
    onZoomChanged: (Boolean) -> Unit = {},
    onTap: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val animationScope = rememberCoroutineScope()
    var reloadKey by remember { mutableIntStateOf(0) }
    val playbackResult by produceState<Result<UgoiraPlayback>?>(initialValue = null, reloadKey) {
        value =
            withContext(Dispatchers.IO) {
                runCatching { loadPlayback() }
            }
    }
    val playback = playbackResult?.getOrNull()
    val decodedBitmaps = remember(playback) { mutableStateMapOf<Int, ImageBitmap>() }
    var currentFrameIndex by remember(playback, reloadKey) { mutableIntStateOf(0) }
    var scale by remember(previewUrl) { mutableFloatStateOf(1f) }
    var offset by remember(previewUrl) { mutableStateOf(Offset.Zero) }
    var localScale by remember(previewUrl) { mutableFloatStateOf(1f) }
    var localOffset by remember(previewUrl) { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomAnimation = remember { arrayOfNulls<Job>(1) }

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
        val maxX = viewportSize.width * (atScale - 1f) / 2f
        val maxY = viewportSize.height * (atScale - 1f) / 2f
        return Offset(
            candidate.x.coerceIn(-maxX, maxX),
            candidate.y.coerceIn(-maxY, maxY),
        )
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
                    localScale = scale
                    localOffset = offset
                    notifyZoomChanged(previous, scale)
                }
            }
    }

    LaunchedEffect(zoomEnabled) {
        if (!zoomEnabled) {
            zoomAnimation[0]?.cancel()
            val previous = scale
            scale = 1f
            offset = Offset.Zero
            localScale = 1f
            localOffset = Offset.Zero
            notifyZoomChanged(previous, scale)
        }
    }

    LaunchedEffect(playback) {
        val frames = playback?.frames ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            frames.forEachIndexed { index, frame ->
                if (!isActive) return@withContext
                if (!decodedBitmaps.containsKey(index)) {
                    val bitmap =
                        runCatching {
                            BitmapFactory.decodeFile(frame.filePath)?.asImageBitmap()
                        }.getOrNull()
                    if (bitmap != null) {
                        decodedBitmaps[index] = bitmap
                    }
                }
            }
        }
    }

    LaunchedEffect(playback) {
        val frames = playback?.frames ?: return@LaunchedEffect
        if (frames.isEmpty()) return@LaunchedEffect
        var index = 0
        var nextTargetTime = System.currentTimeMillis()
        while (isActive) {
            val frame = frames[index]
            currentFrameIndex = index
            val delayDuration = normalizedUgoiraDelayMillis(frame.delayMillis)
            nextTargetTime += delayDuration
            val waitTime = nextTargetTime - System.currentTimeMillis()
            if (waitTime > 0) {
                delay(waitTime)
            } else {
                if (waitTime < -delayDuration) {
                    nextTargetTime = System.currentTimeMillis()
                }
                yield()
            }
            index = (index + 1) % frames.size
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(previewUrl, zoomEnabled, onTap) {
                        detectTapGestures(
                            onTap = { onTap?.invoke() },
                            onDoubleTap =
                                if (zoomEnabled) {
                                    {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (scale > 1.02f) {
                                            animateTo(1f, Offset.Zero)
                                        } else {
                                            animateTo(2.5f, Offset.Zero)
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    }.then(
                        if (zoomEnabled) {
                            Modifier.pointerInput(previewUrl) {
                                detectTransformGestures { centroid, pan, zoom, _ ->
                                    zoomAnimation[0]?.cancel()
                                    val previousScale = localScale
                                    val nextScale = (localScale * zoom).coerceIn(1f, 6f)
                                    val appliedZoom = nextScale / localScale
                                    val viewportCenter =
                                        Offset(
                                            viewportSize.width / 2f,
                                            viewportSize.height / 2f,
                                        )
                                    val focalPoint = centroid - viewportCenter
                                    val transformedOffset =
                                        localOffset + pan +
                                            (focalPoint - localOffset) * (1f - appliedZoom)

                                    localScale = nextScale
                                    localOffset =
                                        if (localScale > 1.02f) {
                                            clampedOffset(transformedOffset, localScale)
                                        } else {
                                            Offset.Zero
                                        }
                                    scale = localScale
                                    offset = localOffset
                                    notifyZoomChanged(previousScale, localScale)
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
            ) {
                val contentScale = if (zoomEnabled) ContentScale.Fit else ContentScale.FillWidth
                val currentBitmap = decodedBitmaps[currentFrameIndex]
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    PixivImage(
                        url = previewUrl,
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        when {
            playbackResult == null -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            playback != null && playback.frames.isNotEmpty() -> {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.Black.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "UGOIRA ${currentFrameIndex + 1}/${playback.frames.size}",
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }

            playbackResult?.isFailure == true -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.ugoira_load_failed),
                        color = MiuixTheme.colorScheme.onSurface,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
            }
        }
    }
}
