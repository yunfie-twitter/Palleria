package com.yunfie.illustia.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    illust: Illust,
    startPage: Int,
    onBack: () -> Unit,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    onMessage: (String) -> Unit,
    fullscreenQuality: String,
    prefetchImages: Boolean,
    onPageChanged: (Int) -> Unit,
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
    val coroutineScope = rememberCoroutineScope()
    var isZoomed by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var comicMode by remember(illust.id) { mutableStateOf(illust.type == "manga" && imageUrls.size > 1) }
    val swipePageThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        onPageChanged(pagerState.currentPage)
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

    fun movePage(direction: Int) {
        val targetPage = (pagerState.currentPage + direction).coerceIn(0, imageUrls.lastIndex)
        if (targetPage == pagerState.currentPage) return
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetPage)
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
                    modifier = Modifier.fillMaxWidth(),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    cornerRadius = 24.dp,
                    outSidePadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    shadowElevation = 12.dp,
                    showDivider = false,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Photos,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${imageUrls.size}",
                                    color = MiuixTheme.colorScheme.onSurface,
                                    style = MiuixTheme.textStyles.title4,
                                )
                            }
                        }
                        Button(onClick = { comicMode = !comicMode }) {
                            Text(if (comicMode) stringResource(R.string.viewer_page_mode) else stringResource(R.string.viewer_comic_mode))
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isBookmarked) {
                                        MiuixTheme.colorScheme.primaryContainer
                                    } else {
                                        MiuixTheme.colorScheme.surfaceContainerHighest
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(onClick = onBookmark) {
                                Icon(
                                    imageVector = if (isBookmarked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                    contentDescription = stringResource(R.string.action_bookmark),
                                    tint = if (isBookmarked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (comicMode) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 72.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(imageUrls, key = { index, _ -> index }) { page, url ->
                        PixivImage(
                            url = url,
                            contentDescription = "${illust.title} ${page + 1}",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clickable { showControls = !showControls },
                        )
                    }
                }
            } else HorizontalPager(
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
                        swipeThresholdPx = swipePageThresholdPx,
                        onSwipePrevious = { movePage(-1) },
                        onSwipeNext = { movePage(1) },
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

