package com.yunfie.illustia.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.yunfie.illustia.R
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    illust: Illust,
    startPage: Int,
    onBack: () -> Unit,
    onSaveImage: (String, String) -> Unit,
    onMessage: (String) -> Unit,
    fullscreenQuality: String,
    prefetchImages: Boolean,
    thumbnailsInToolbar: Boolean,
) {
    val context = LocalContext.current
    val shareFailedMessage = stringResource(R.string.viewer_share_failed)
    val imageUrls = remember(illust, fullscreenQuality) {
        when (fullscreenQuality) {
            "low" -> illust.mediumImagePages.ifEmpty {
                listOf(
                    illust.mediumImageUrl.ifBlank {
                        illust.squareImageUrl.ifBlank { illust.imageUrl }
                    },
                )
            }
            "medium" -> illust.imagePages.ifEmpty { listOf(illust.imageUrl) }
            else -> illust.originalImagePages.ifEmpty {
                illust.imagePages.ifEmpty { listOfNotNull(illust.originalImageUrl ?: illust.imageUrl) }
            }
        }
    }
    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, imageUrls.lastIndex.coerceAtLeast(0)), pageCount = { imageUrls.size })
    val thumbnailUrls = remember(illust) {
        illust.mediumImagePages.ifEmpty {
            illust.imagePages.ifEmpty { imageUrls }
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var isZoomed by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
    }
    
    fun shareCurrentPage() {
        val url = imageUrls[pagerState.currentPage]
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "${illust.title} by ${illust.artistName}\n$url")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)
        runCatching {
            context.startActivity(shareIntent)
        }.onFailure {
            onMessage(shareFailedMessage)
        }
    }

    PredictiveBackGestureHandler(onBack = onBack)
    
    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                SmallTopAppBar(
                    title = illust.title,
                    color = Color.Transparent,
                    titleColor = Color.White,
                    navigationIcon = { HeaderOverlayIcon(MiuixIcons.Close, onBack) },
                )
            }
        },
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
        Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = if (prefetchImages) 1 else 0,
            userScrollEnabled = !isZoomed,
            key = { it },
        ) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ZoomablePixivImage(
                    url = imageUrls[page],
                    contentDescription = illust.title,
                    isActive = pagerState.currentPage == page,
                    onZoomChanged = { zoomed ->
                        if (pagerState.currentPage == page) isZoomed = zoomed
                    },
                    onTap = { showControls = !showControls }
                )
            }
        }
        
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.42f to Color.Black.copy(alpha = 0.56f),
                            1f to Color.Black.copy(alpha = 0.94f),
                        )
                    )
                    .padding(top = 56.dp)
                    .navigationBarsPadding()
            ) {
                if (imageUrls.size > 1 && !thumbnailsInToolbar) {
                    ViewerThumbnailStrip(
                        imageUrls = imageUrls,
                        thumbnailUrls = thumbnailUrls,
                        currentPage = pagerState.currentPage,
                        onPageSelected = { index ->
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                FloatingToolbar(
                    modifier = Modifier.fillMaxWidth(),
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    cornerRadius = 28.dp,
                    outSidePadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    shadowElevation = 8.dp,
                    showDivider = true,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (imageUrls.size > 1 && thumbnailsInToolbar) {
                            ViewerThumbnailStrip(
                                imageUrls = imageUrls,
                                thumbnailUrls = thumbnailUrls,
                                currentPage = pagerState.currentPage,
                                onPageSelected = { index ->
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Photos,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 8.dp).size(22.dp),
                            )
                            Text(
                                text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                                color = MiuixTheme.colorScheme.onSurface,
                                style = MiuixTheme.textStyles.title4,
                                modifier = Modifier.padding(start = 12.dp).weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    onSaveImage(
                                        imageUrls[pagerState.currentPage],
                                        "illustia_${illust.id}_p${pagerState.currentPage}",
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    contentDescription = stringResource(R.string.action_download),
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(onClick = { shareCurrentPage() }) {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = stringResource(R.string.action_share),
                                    tint = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }

                    }
                }
            }
        }
        }
        }
    }
}

@Composable
private fun ViewerThumbnailStrip(
    imageUrls: List<String>,
    thumbnailUrls: List<String>,
    currentPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(imageUrls, key = { index, _ -> index }) { index, _ ->
            val selected = currentPage == index
            Box(
                modifier = Modifier
                    .size(width = 46.dp, height = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh)
                    .then(
                        if (selected) Modifier.border(
                            width = 2.dp,
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                        ) else Modifier
                    )
                    .padding(if (selected) 3.dp else 1.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPageSelected(index) },
            ) {
                PixivImage(
                    url = thumbnailUrls.getOrElse(index) { imageUrls[index] },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    thumbnail = true,
                )
            }
        }
    }
}

@Composable
private fun ZoomablePixivImage(
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

    fun notifyZoomChanged(previous: Float, current: Float) {
        val wasZoomed = previous > 1.02f
        val zoomed = current > 1.02f
        if (wasZoomed != zoomed) onZoomChanged(zoomed)
    }

    fun clampedOffset(candidate: Offset, atScale: Float): Offset {
        val maxX = viewportSize.width * (atScale - 1f) / 2f
        val maxY = viewportSize.height * (atScale - 1f) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        val startScale = scale
        val startOffset = offset
        zoomAnimation[0]?.cancel()
        zoomAnimation[0] = animationScope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) { progress, _ ->
                val previous = scale
                scale = startScale + (targetScale - startScale) * progress
                offset = Offset(
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
            scale = 1f
            offset = Offset.Zero
            onZoomChanged(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .pointerInput(url) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (scale > 1.02f) {
                            animateTo(1f, Offset.Zero)
                        } else {
                            animateTo(2.5f, Offset.Zero)
                        }
                    },
                )
            }
            .pointerInput(url) {
                awaitEachGesture {
                    zoomAnimation[0]?.cancel()
                    var localScale = scale
                    var localOffset = offset
                    var pastTouchSlop = false
                    val touchSlop = viewConfiguration.touchSlop
                    var accumulatedZoom = 1f

                    // Wait for first touch
                    var event = awaitPointerEvent()
                    while (event.changes.fastAll { !it.pressed }) {
                        event = awaitPointerEvent()
                    }

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.fastAny { it.isConsumed }

                        if (!canceled) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)

                            if (!pastTouchSlop) {
                                accumulatedZoom *= zoomChange
                                val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                val zoomMotion = abs(1 - accumulatedZoom) * centroidSize
                                val panMotion = panChange.getDistance()

                                if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                    pastTouchSlop = true
                                }
                            }

                            if (pastTouchSlop) {
                                val nextScale = (localScale * zoomChange).coerceIn(1f, 6f)
                                val isPinching = zoomChange != 1f
                                val isZoomed = localScale > 1.02f

                                if (isPinching || isZoomed) {
                                    event.changes.fastForEach {
                                        if (it.positionChanged()) {
                                            it.consume()
                                        }
                                    }

                                    val appliedZoom = nextScale / localScale
                                    val viewportCenter = Offset(viewportSize.width / 2f, viewportSize.height / 2f)
                                    val focalPoint = centroid - viewportCenter
                                    val transformedOffset = localOffset + panChange +
                                        (focalPoint - localOffset) * (1f - appliedZoom)
                                    val previousScale = localScale
                                    localScale = nextScale
                                    localOffset = if (localScale > 1.02f) {
                                        clampedOffset(transformedOffset, localScale)
                                    } else {
                                        Offset.Zero
                                    }

                                    // State is updated during the gesture so graphicsLayer can render every frame.
                                    scale = localScale
                                    offset = localOffset
                                    notifyZoomChanged(previousScale, localScale)
                                }
                            }
                        }
                    } while (!canceled && event.changes.fastAny { it.pressed })

                    if (localScale <= 1.02f) {
                        scale = 1f
                        offset = Offset.Zero
                    }
                }
            }
    ) {
        PixivImage(
            url = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            crossfade = true,
        )
    }
}
