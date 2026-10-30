package com.yunfie.illustia.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunfie.illustia.R
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.models.pixiv.normalizedUgoiraDelayMillis
import com.yunfie.illustia.platform.PlatformCapabilities
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalSystemUiCovered
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.ConcurrentHashMap

private const val PREFETCH_AHEAD = 18
private const val KEEP_BEHIND = 4
private const val UGOIRA_START_DELAY_MS = 150L
private const val POWER_SAVE_IDLE_DELAY_MS = 150L
private const val SEEKING_IDLE_DELAY_MS = 60L

/**
 * Fixed requester key for the high refresh-rate request. Keying by mode would collide with the
 * viewer's request for the same mode: its onDispose would release this page's boost too.
 */
private val GOING_UGOIRA_HZ_KEY = Any()

@Composable
internal fun UgoiraArtwork(
    previewUrl: String,
    contentDescription: String,
    loadPlayback: suspend () -> UgoiraPlayback,
    modifier: Modifier = Modifier,
    zoomEnabled: Boolean = false,
    powerSaveEnabled: Boolean = true,
    isActive: Boolean = true,
    onZoomChanged: (Boolean) -> Unit = {},
    onTap: (() -> Unit)? = null,
) {
    val performHaptic = rememberHapticFeedbackAction()
    val animationScope = rememberCoroutineScope()
    var reloadKey by remember { mutableIntStateOf(0) }

    var isDelayElapsed by remember(reloadKey, powerSaveEnabled) { mutableStateOf(!powerSaveEnabled) }
    LaunchedEffect(reloadKey, powerSaveEnabled) {
        if (powerSaveEnabled) {
            delay(UGOIRA_START_DELAY_MS)
            isDelayElapsed = true
        } else {
            isDelayElapsed = true
        }
    }

    val systemUiCovered = LocalSystemUiCovered.current
    val playbackActive = isActive && !systemUiCovered

    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleActive by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                isLifecycleActive = event.targetState.isAtLeast(Lifecycle.State.RESUMED)
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val playbackResult by produceState<Result<UgoiraPlayback>?>(initialValue = null, reloadKey, isDelayElapsed, playbackActive) {
        if (!isDelayElapsed || !playbackActive) return@produceState
        value =
            withContext(Dispatchers.IO) {
                runCatching { loadPlayback() }
            }
    }
    val playback = playbackResult?.getOrNull()
    val decodedBitmaps = remember(playback) { ConcurrentHashMap<Int, ImageBitmap>() }
    var currentBitmap by remember(playback, reloadKey) { mutableStateOf<ImageBitmap?>(null) }
    var currentFrameIndex by remember(playback, reloadKey) { mutableIntStateOf(0) }
    var isPlaying by remember(playback, reloadKey) { mutableStateOf(true) }
    var showSeekBar by remember(playback, reloadKey) { mutableStateOf(false) }
    var isSeeking by remember(playback, reloadKey) { mutableStateOf(false) }
    var scale by remember(previewUrl) { mutableFloatStateOf(1f) }
    var offset by remember(previewUrl) { mutableStateOf(Offset.Zero) }
    var localScale by remember(previewUrl) { mutableFloatStateOf(1f) }
    var localOffset by remember(previewUrl) { mutableStateOf(Offset.Zero) }
    var reportedZoomed by remember(previewUrl) { mutableStateOf(false) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomAnimation = remember { arrayOfNulls<Job>(1) }
    val currentOnTap by rememberUpdatedState(onTap)

    fun syncZoomChanged(current: Float = scale) {
        val zoomed = current > 1.02f
        if (reportedZoomed != zoomed) {
            reportedZoomed = zoomed
            onZoomChanged(zoomed)
        }
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
                    scale = startScale + (targetScale - startScale) * progress
                    offset =
                        Offset(
                            startOffset.x + (targetOffset.x - startOffset.x) * progress,
                            startOffset.y + (targetOffset.y - startOffset.y) * progress,
                        )
                    localScale = scale
                    localOffset = offset
                    syncZoomChanged(scale)
                }
            }
    }

    LaunchedEffect(zoomEnabled) {
        if (!zoomEnabled) {
            zoomAnimation[0]?.cancel()
            scale = 1f
            offset = Offset.Zero
            localScale = 1f
            localOffset = Offset.Zero
            syncZoomChanged(1f)
        }
    }

    val bitmapPool = remember(playback) { java.util.concurrent.ConcurrentLinkedQueue<Bitmap>() }
    if (playbackActive && playback != null && playback.frames.isNotEmpty()) {
        com.yunfie.illustia.platform
            .RequestDynamicHzMode(com.yunfie.illustia.platform.DynamicHzMode.Boost, key = GOING_UGOIRA_HZ_KEY)
    }
    val context = LocalContext.current
    val preferredConfig = remember(context) { PlatformCapabilities.recommendedBitmapConfig(context) }
    val maxCachedFrames = remember(context) { PlatformCapabilities.recommendedUgoiraMaxCachedFrames(context) }
    val prefetchAhead = remember(context) { PlatformCapabilities.recommendedUgoiraPrefetchAhead(context) }
    val keepBehind = remember(context) { PlatformCapabilities.recommendedUgoiraKeepBehind(context) }

    DisposableEffect(playbackActive, playback) {
        onDispose {
            if (playbackActive) return@onDispose
            // 画面外（非アクティブ）のページはデコード済みフレームを保持し続けるとメモリを圧迫する。
            // 復帰時は上の LaunchedEffect が現在位置から再デコードするため再生挙動は変わらない���
            decodedBitmaps.clear()
            currentBitmap = null
            while (true) {
                val queued = bitmapPool.poll() ?: break
                if (!queued.isRecycled) queued.recycle()
            }
        }
    }

    // フレームのデコードは ConcurrentHashMap に書き込む。
    // メモリ上限を超えないよう、フレーム数が多い場合は再生位置前後のスライディングウィンドウで管理する。
    LaunchedEffect(playback, maxCachedFrames, prefetchAhead, keepBehind, preferredConfig, playbackActive) {
        if (!playbackActive) return@LaunchedEffect
        val frames = playback?.frames ?: return@LaunchedEffect
        if (frames.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            if (frames.size <= maxCachedFrames) {
                frames.forEachIndexed { index, frame ->
                    if (!playbackActive) return@withContext
                    if (!decodedBitmaps.containsKey(index)) {
                        val bitmap =
                            runCatching {
                                val opts = BitmapFactory.Options().apply { inPreferredConfig = preferredConfig }
                                BitmapFactory.decodeFile(frame.filePath, opts)?.asImageBitmap()
                            }.getOrNull()
                        if (bitmap != null) {
                            decodedBitmaps[index] = bitmap
                        }
                    }
                }
            } else {
                while (playbackActive) {
                    val current = currentFrameIndex
                    val needed =
                        (-keepBehind..prefetchAhead)
                            .map {
                                (current + it + frames.size) % frames.size
                            }.toSet()

                    val removedKeys = decodedBitmaps.keys - needed
                    for (k in removedKeys) {
                        decodedBitmaps.remove(k)?.let { bmp ->
                            val androidBmp = bmp.asAndroidBitmap()
                            if (androidBmp.isMutable && !androidBmp.isRecycled) {
                                bitmapPool.add(androidBmp)
                            }
                        }
                    }

                    for (step in 0..prefetchAhead) {
                        if (!playbackActive) break
                        val targetIdx = (current + step) % frames.size
                        if (!decodedBitmaps.containsKey(targetIdx)) {
                            val pooled = bitmapPool.poll()
                            val options =
                                if (pooled != null && pooled.isMutable && !pooled.isRecycled) {
                                    BitmapFactory.Options().apply {
                                        inBitmap = pooled
                                        inMutable = true
                                        inPreferredConfig = preferredConfig
                                    }
                                } else {
                                    BitmapFactory.Options().apply {
                                        inMutable = true
                                        inPreferredConfig = preferredConfig
                                    }
                                }

                            var androidBitmap =
                                runCatching {
                                    BitmapFactory.decodeFile(frames[targetIdx].filePath, options)
                                }.getOrNull()

                            if (androidBitmap == null && pooled != null) {
                                // Fallback
                                androidBitmap =
                                    runCatching {
                                        BitmapFactory.decodeFile(
                                            frames[targetIdx].filePath,
                                            BitmapFactory.Options().apply {
                                                inMutable = true
                                                inPreferredConfig = preferredConfig
                                            },
                                        )
                                    }.getOrNull()
                            }

                            val bitmap = androidBitmap?.asImageBitmap()
                            if (bitmap != null) {
                                decodedBitmaps[targetIdx] = bitmap
                                if (targetIdx == currentFrameIndex && currentBitmap == null) {
                                    currentBitmap = bitmap
                                }
                            }
                        }
                    }

                    while (playbackActive && currentFrameIndex == current) {
                        delay(10)
                    }
                }
            }
        }
    }

    LaunchedEffect(playback, preferredConfig, isPlaying, isSeeking, powerSaveEnabled, isLifecycleActive, playbackActive) {
        if (!playbackActive) return@LaunchedEffect
        val frames = playback?.frames ?: return@LaunchedEffect
        if (frames.isEmpty()) return@LaunchedEffect
        var index = currentFrameIndex
        var nextTargetTime = System.currentTimeMillis()
        while (playbackActive) {
            if (powerSaveEnabled && !isLifecycleActive) {
                delay(POWER_SAVE_IDLE_DELAY_MS)
                nextTargetTime = System.currentTimeMillis()
            } else if (!isPlaying || isSeeking) {
                delay(SEEKING_IDLE_DELAY_MS)
                nextTargetTime = System.currentTimeMillis()
                index = currentFrameIndex
            } else {
                val frame = frames[index]
                currentFrameIndex = index
                var bitmap = decodedBitmaps[index]
                if (bitmap == null) {
                    bitmap =
                        withContext(Dispatchers.IO) {
                            val pooled = bitmapPool.poll()
                            val options =
                                if (pooled != null && pooled.isMutable && !pooled.isRecycled) {
                                    BitmapFactory.Options().apply {
                                        inBitmap = pooled
                                        inMutable = true
                                        inPreferredConfig = preferredConfig
                                    }
                                } else {
                                    BitmapFactory.Options().apply {
                                        inMutable = true
                                        inPreferredConfig = preferredConfig
                                    }
                                }

                            var androidBitmap =
                                runCatching {
                                    BitmapFactory.decodeFile(frame.filePath, options)
                                }.getOrNull()

                            if (androidBitmap == null && pooled != null) {
                                androidBitmap =
                                    runCatching {
                                        val fbOptions =
                                            BitmapFactory.Options().apply {
                                                inMutable = true
                                                inPreferredConfig = preferredConfig
                                            }
                                        BitmapFactory.decodeFile(frame.filePath, fbOptions)
                                    }.getOrNull()
                            }
                            androidBitmap?.asImageBitmap()
                        }
                    if (bitmap != null) {
                        decodedBitmaps[index] = bitmap
                    }
                }
                if (bitmap != null) {
                    currentBitmap = bitmap
                }
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
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { viewportSize = it }
                    .pointerInput(previewUrl, zoomEnabled) {
                        detectTapGestures(
                            onTap = { currentOnTap?.invoke() },
                            onDoubleTap =
                                if (zoomEnabled) {
                                    { tapOffset ->
                                        performHaptic(AppHapticEffect.Click)
                                        if (scale > 1.02f) {
                                            animateTo(1f, Offset.Zero)
                                        } else {
                                            val targetScale = 2.5f
                                            val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                            val focalPoint = tapOffset - viewportCenter
                                            val targetOffset = clampedOffset(-focalPoint * (targetScale - 1f), targetScale)
                                            animateTo(targetScale, targetOffset)
                                        }
                                    }
                                } else {
                                    null
                                },
                        )
                    }.then(
                        if (zoomEnabled) {
                            Modifier.pointerInput(previewUrl) {
                                var atMinLimit = false
                                var atMaxLimit = false
                                detectZoomAndPanGestures(
                                    isZoomed = { localScale > 1.02f },
                                    onGesture = { centroid, pan, zoom ->
                                        if (!zoom.isFinite() || zoom <= 0f) return@detectZoomAndPanGestures
                                        zoomAnimation[0]?.cancel()
                                        val unconstrainedScale = localScale * zoom
                                        val nextScale = unconstrainedScale.coerceIn(1f, 6f)
                                        if (!nextScale.isFinite() || localScale <= 0f) return@detectZoomAndPanGestures

                                        if (unconstrainedScale <= 1f) {
                                            if (!atMinLimit && zoom < 1f) {
                                                performHaptic(AppHapticEffect.BoundaryLimit)
                                                atMinLimit = true
                                            }
                                        } else {
                                            atMinLimit = false
                                        }

                                        if (unconstrainedScale >= 6f) {
                                            if (!atMaxLimit && zoom > 1f) {
                                                performHaptic(AppHapticEffect.BoundaryLimit)
                                                atMaxLimit = true
                                            }
                                        } else {
                                            atMaxLimit = false
                                        }

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
                                    },
                                    onGestureEnd = {
                                        atMinLimit = false
                                        atMaxLimit = false
                                        if (localScale < 1.02f) {
                                            animateTo(1f, Offset.Zero)
                                        } else {
                                            localOffset = clampedOffset(localOffset, localScale)
                                            offset = localOffset
                                            syncZoomChanged(localScale)
                                        }
                                    },
                                )
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
                contentAlignment = Alignment.Center,
            ) {
                val contentScale = if (zoomEnabled) ContentScale.Fit else ContentScale.FillWidth
                val bmp = currentBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
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
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showSeekBar) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.65f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Slider(
                                value = (currentFrameIndex + 1).toFloat(),
                                onValueChange = { targetVal ->
                                    isSeeking = true
                                    val targetIndex = (targetVal.toInt() - 1).coerceIn(0, playback.frames.lastIndex)
                                    if (targetIndex != currentFrameIndex) {
                                        currentFrameIndex = targetIndex
                                        decodedBitmaps[targetIndex]?.let { currentBitmap = it }
                                        performHaptic(AppHapticEffect.WheelTick)
                                    }
                                },
                                valueRange = 1f..playback.frames.size.toFloat(),
                                steps = (playback.frames.size - 2).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    Row(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        isPlaying = !isPlaying
                                        performHaptic(AppHapticEffect.Toggle)
                                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isPlaying) "⏸" else "▶",
                                color = MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }

                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable {
                                        showSeekBar = !showSeekBar
                                        performHaptic(AppHapticEffect.Click)
                                    }.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "UGOIRA ${currentFrameIndex + 1}/${playback.frames.size}",
                                color = MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                    }
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
