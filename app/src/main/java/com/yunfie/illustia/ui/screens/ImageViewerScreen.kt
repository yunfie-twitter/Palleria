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
import com.yunfie.illustia.models.Illust
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
                    navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.action_close),
                                    tint = Color.White,
                                )
                            }
                        },
                )
            }
        },
        floatingToolbar = {
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                FloatingToolbar(
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        if (imageUrls.size > 1 && thumbnailsInToolbar) {
                            ViewerThumbnailStrip(
                                imageUrls = imageUrls,
                                thumbnailUrls = thumbnailUrls,
                                currentPage = pagerState.currentPage,
                                onPageSelected = { index ->
                                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
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
        }
        }
    }
}

