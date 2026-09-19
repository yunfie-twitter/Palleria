package com.yunfie.illustia.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingToolbar
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    mangaReaderMode: String,
    onPageChanged: (Int) -> Unit,
    loadUgoiraPlayback: suspend (Long) -> UgoiraPlayback,
) {
    val context = LocalContext.current
    val shareFailedMessage = stringResource(R.string.viewer_share_failed)
    val imageUrls =
        remember(illust, fullscreenQuality) {
            when (fullscreenQuality) {
                "low" -> {
                    illust.mediumImagePages.ifEmpty {
                        listOf(
                            illust.mediumImageUrl.ifBlank {
                                illust.squareImageUrl.ifBlank { illust.imageUrl }
                            },
                        )
                    }
                }

                "medium" -> {
                    illust.imagePages.ifEmpty { listOf(illust.imageUrl) }
                }

                else -> {
                    illust.originalImagePages.ifEmpty {
                        illust.imagePages.ifEmpty { listOfNotNull(illust.originalImageUrl ?: illust.imageUrl) }
                    }
                }
            }
        }
    val pagerState =
        rememberPagerState(initialPage = startPage.coerceIn(0, imageUrls.lastIndex.coerceAtLeast(0)), pageCount = { imageUrls.size })
    val coroutineScope = rememberCoroutineScope()
    var isZoomed by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val comicMode = illust.type == "manga" && imageUrls.size > 1 && mangaReaderMode == "vertical"

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

    val activity = remember(context) { context.findActivity() }
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.statusBars())

            onDispose {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        } else {
            onDispose {}
        }
    }

    fun shareCurrentPage() {
        val url = imageUrls.getOrNull(pagerState.currentPage) ?: return
        val sendIntent =
            Intent().apply {
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
        if (imageUrls.isEmpty()) return
        val targetPage = (pagerState.currentPage + direction).coerceIn(0, imageUrls.lastIndex)
        if (targetPage == pagerState.currentPage) return
        coroutineScope.launch {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    PredictiveBackGestureHandler(onBack = onBack)

    val performHaptic = rememberHapticFeedbackAction()

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
                val safeTop = maxOf(cutoutTop, 24.dp)
                SmallTopAppBar(
                    title = illust.title,
                    color = Color.Transparent,
                    titleColor = Color.White,
                    modifier = Modifier.padding(top = safeTop),
                    navigationIcon = {
                        IconButton(onClick = {
                            performHaptic(AppHapticEffect.Click)
                            onBack()
                        }) {
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
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
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
                        Box(
                            modifier =
                                Modifier
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
                            IconButton(onClick = {
                                performHaptic(AppHapticEffect.Toggle)
                                onBookmark()
                            }) {
                                Icon(
                                    imageVector = if (isBookmarked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                    contentDescription = stringResource(R.string.action_bookmark),
                                    tint = if (isBookmarked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MiuixTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            IconButton(onClick = {
                                performHaptic(AppHapticEffect.Click)
                                shareCurrentPage()
                            }) {
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black),
        ) {
            if (illust.type == "ugoira") {
                UgoiraArtwork(
                    previewUrl = imageUrls.firstOrNull().orEmpty(),
                    contentDescription = illust.title,
                    loadPlayback = { loadUgoiraPlayback(illust.id) },
                    modifier = Modifier.fillMaxSize(),
                    zoomEnabled = true,
                    onZoomChanged = { isZoomed = it },
                    onTap = { showControls = !showControls },
                )
            } else if (comicMode) {
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
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .clickable { showControls = !showControls },
                        )
                    }
                }
            } else {
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
                            onTap = { showControls = !showControls },
                        )
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
