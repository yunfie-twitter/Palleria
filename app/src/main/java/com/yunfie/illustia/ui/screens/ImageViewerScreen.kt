package com.yunfie.illustia.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
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
import com.yunfie.illustia.platform.ImageClipboardHelper
import com.yunfie.illustia.platform.PlatformCapabilities
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
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.ToolbarPosition
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import android.view.KeyEvent as AndroidKeyEvent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(
    illust: Illust,
    startPage: Int,
    onSave: (String, String) -> Unit,
    onBack: () -> Unit,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    onMessage: (String) -> Unit,
    fullscreenQuality: String,
    prefetchImages: Boolean,
    mangaReaderMode: String,
    onPageChanged: (Int) -> Unit,
    loadUgoiraPlayback: suspend (Long) -> UgoiraPlayback,
    ambientLightEnabled: Boolean = false,
    volumeKeyPageTurnerEnabled: Boolean = false,
    swipeToDismissEnabled: Boolean = true,
) {
    val context = LocalContext.current
    if (!illust.isUgoira) {
        com.yunfie.illustia.platform
            .RequestDynamicHzMode(com.yunfie.illustia.platform.DynamicHzMode.PowerSaving)
    }
    val shareFailedMessage = stringResource(R.string.viewer_share_failed)
    val copiedMessage = stringResource(R.string.copied_image_to_clipboard)
    val copyFailedMessage = stringResource(R.string.copy_image_failed)
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
    val ambientUrls =
        remember(illust) {
            illust.mediumImagePages.ifEmpty {
                illust.imagePages.ifEmpty {
                    listOf(
                        illust.mediumImageUrl.ifBlank {
                            illust.squareImageUrl.ifBlank { illust.imageUrl }
                        },
                    )
                }
            }
        }
    val pagerState =
        rememberPagerState(initialPage = startPage.coerceIn(0, imageUrls.lastIndex.coerceAtLeast(0)), pageCount = { imageUrls.size })
    val coroutineScope = rememberCoroutineScope()
    var isZoomed by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var fullscreen by remember { mutableStateOf(true) }
    var showSeekSlider by remember { mutableStateOf(false) }
    val comicMode = illust.type == "manga" && imageUrls.size > 1 && mangaReaderMode == "vertical"

    val comicListState =
        androidx.compose.foundation.lazy
            .rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val currentPage = if (comicMode) comicListState.firstVisibleItemIndex else pagerState.currentPage

    var localBookmarked by remember(illust.id, isBookmarked) { mutableStateOf(isBookmarked) }
    LaunchedEffect(isBookmarked) {
        localBookmarked = isBookmarked
    }
    var triggerPop by remember { mutableStateOf(false) }
    val popScale by animateFloatAsState(
        targetValue = if (triggerPop) 1.25f else 1.0f,
        animationSpec =
            spring(
                dampingRatio = 0.4f,
                stiffness = 400f,
            ),
        finishedListener = { triggerPop = false },
        label = "bookmark-button-pop",
    )
    val buttonBgColor by animateColorAsState(
        targetValue =
            if (localBookmarked) {
                MiuixTheme.colorScheme.primaryContainer
            } else {
                MiuixTheme.colorScheme.surfaceContainerHighest
            },
        animationSpec = tween(200),
        label = "bookmark-button-bg",
    )
    val iconColor by animateColorAsState(
        targetValue =
            if (localBookmarked) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurface
            },
        animationSpec = tween(200),
        label = "bookmark-button-icon",
    )

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        } else {
            showSeekSlider = false
        }
    }

    LaunchedEffect(currentPage) {
        isZoomed = false
        onPageChanged(currentPage)
    }

    com.yunfie.illustia.ui.components
        .ReaderFullscreen(fullscreen)

    fun shareCurrentPage() {
        val url = imageUrls.getOrNull(currentPage) ?: return
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

    val performHaptic = rememberHapticFeedbackAction()
    val dismissOffsetY = remember { Animatable(0f) }
    val density = LocalDensity.current
    val dismissThresholdPx = remember(density) { with(density) { 140.dp.toPx() } }

    fun copyCurrentPage() {
        val url = imageUrls.getOrNull(currentPage) ?: return
        coroutineScope.launch {
            val success = ImageClipboardHelper.copyImageToClipboard(context, url)
            if (success) {
                performHaptic(AppHapticEffect.Success)
                onMessage(copiedMessage)
            } else {
                onMessage(copyFailedMessage)
            }
        }
    }

    fun movePage(direction: Int) {
        if (imageUrls.isEmpty()) return
        val targetPage = (currentPage + direction).coerceIn(0, imageUrls.lastIndex)
        if (targetPage == currentPage) return
        coroutineScope.launch {
            if (comicMode) comicListState.animateScrollToItem(targetPage) else pagerState.animateScrollToPage(targetPage)
        }
    }

    PredictiveBackGestureHandler(onBack = onBack)

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onKeyEvent { keyEvent ->
                    val event = keyEvent.nativeKeyEvent
                    if (keyEvent.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        val direction =
                            com.yunfie.illustia.platform
                                .readerPageDirection(event, volumeKeyPageTurnerEnabled)
                        when {
                            direction != 0 -> {
                                movePage(direction)
                                true
                            }

                            !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed &&
                                event.keyCode == AndroidKeyEvent.KEYCODE_F -> {
                                if (event.repeatCount == 0) fullscreen = !fullscreen
                                true
                            }

                            event.isCtrlPressed && event.keyCode == AndroidKeyEvent.KEYCODE_S -> {
                                if (event.repeatCount == 0) {
                                    val page = currentPage
                                    val url =
                                        illust.originalImagePages.getOrNull(page)
                                            ?: illust.originalImageUrl?.takeIf { page == 0 } ?: imageUrls.getOrNull(page)
                                    if (url != null) onSave(url, "${illust.id}_p$page")
                                }
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    }
                }.focusable(),
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                SmallTopAppBar(
                    title = illust.title,
                    color = Color.Transparent,
                    titleColor = Color.White,
                    // Consume the safe inset so SmallTopAppBar does not add the status bar twice.
                    modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
                    navigationIcon = {
                        IconButton(modifier = Modifier.pointerHoverIcon(PointerIcon.Hand), onClick = {
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
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                    color = MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    cornerRadius = 24.dp,
                    outSidePadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    shadowElevation = 12.dp,
                    showDivider = false,
                ) {
                    if (showSeekSlider && imageUrls.size > 1) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${currentPage + 1} / ${imageUrls.size}",
                                    color = MiuixTheme.colorScheme.onSurface,
                                    style = MiuixTheme.textStyles.title4,
                                )
                                IconButton(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    onClick = {
                                        performHaptic(AppHapticEffect.Click)
                                        showSeekSlider = false
                                    },
                                    minWidth = 32.dp,
                                    minHeight = 32.dp,
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Close,
                                        contentDescription = stringResource(R.string.action_close),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Slider(
                                value = (currentPage + 1).toFloat(),
                                onValueChange = { targetPage ->
                                    val targetIndex = (targetPage.toInt() - 1).coerceIn(0, imageUrls.lastIndex)
                                    if (targetIndex != currentPage) {
                                        performHaptic(AppHapticEffect.WheelTick)
                                        coroutineScope.launch {
                                            if (comicMode) {
                                                comicListState.scrollToItem(
                                                    targetIndex,
                                                )
                                            } else {
                                                pagerState.scrollToPage(targetIndex)
                                            }
                                        }
                                    }
                                },
                                valueRange = 1f..imageUrls.size.toFloat(),
                                steps = (imageUrls.size - 2).coerceAtLeast(0),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
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
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .clickable(
                                            enabled = imageUrls.size > 1,
                                            onClick = {
                                                performHaptic(AppHapticEffect.Click)
                                                showSeekSlider = true
                                            },
                                        ),
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
                                        text = "${currentPage + 1} / ${imageUrls.size}",
                                        color = MiuixTheme.colorScheme.onSurface,
                                        style = MiuixTheme.textStyles.title4,
                                    )
                                }
                            }
                            Box(
                                modifier =
                                    Modifier
                                        .size(46.dp)
                                        .graphicsLayer {
                                            scaleX = popScale
                                            scaleY = popScale
                                        }.clip(RoundedCornerShape(16.dp))
                                        .background(buttonBgColor),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(modifier = Modifier.pointerHoverIcon(PointerIcon.Hand), onClick = {
                                    performHaptic(AppHapticEffect.Toggle)
                                    triggerPop = true
                                    localBookmarked = !localBookmarked
                                    onBookmark()
                                }) {
                                    AnimatedContent(
                                        targetState = localBookmarked,
                                        transitionSpec = {
                                            (
                                                scaleIn(spring(dampingRatio = 0.4f, stiffness = 400f), initialScale = 0.4f) +
                                                    fadeIn(tween(150))
                                            ).togetherWith(
                                                scaleOut(tween(100), targetScale = 0.4f) +
                                                    fadeOut(tween(100)),
                                            )
                                        },
                                        label = "bookmark-icon-switch",
                                    ) { isLiked ->
                                        Icon(
                                            imageVector = if (isLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                            contentDescription = stringResource(R.string.action_bookmark),
                                            tint = iconColor,
                                        )
                                    }
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
                                IconButton(modifier = Modifier.pointerHoverIcon(PointerIcon.Hand), onClick = {
                                    performHaptic(AppHapticEffect.Click)
                                    copyCurrentPage()
                                }) {
                                    Icon(
                                        imageVector = MiuixIcons.Copy,
                                        contentDescription = stringResource(R.string.action_copy_image),
                                        tint = MiuixTheme.colorScheme.primary,
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
                                IconButton(modifier = Modifier.pointerHoverIcon(PointerIcon.Hand), onClick = {
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
            }
        },
        floatingToolbarPosition = ToolbarPosition.BottomCenter,
    ) {
        val dismissProgress = (dismissOffsetY.value / (dismissThresholdPx * 2f)).coerceIn(0f, 1f)
        val bgAlpha = (1f - dismissProgress * 1.2f).coerceIn(0f, 1f)
        val contentScale = (1f - dismissProgress * 0.2f).coerceIn(0.7f, 1f)

        val swipeModifier =
            if (swipeToDismissEnabled && !comicMode && !isZoomed) {
                Modifier.pointerInput(swipeToDismissEnabled, isZoomed, comicMode) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (dismissOffsetY.value > dismissThresholdPx) {
                                    onBack()
                                } else {
                                    dismissOffsetY.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.8f, stiffness = 600f),
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                dismissOffsetY.animateTo(
                                    0f,
                                    spring(dampingRatio = 0.8f, stiffness = 600f),
                                )
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0f || dismissOffsetY.value > 0f) {
                                change.consume()
                                val currentOffset = dismissOffsetY.value
                                val newOffset = (currentOffset + dragAmount).coerceAtLeast(0f)
                                val wasOverThreshold = currentOffset >= dismissThresholdPx
                                val isOverThreshold = newOffset >= dismissThresholdPx
                                if (wasOverThreshold != isOverThreshold) {
                                    performHaptic(AppHapticEffect.ThresholdSnap)
                                }
                                coroutineScope.launch {
                                    dismissOffsetY.snapTo(newOffset)
                                }
                            }
                        },
                    )
                }
            } else {
                Modifier
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = bgAlpha)),
        ) {
            if (ambientLightEnabled && ambientUrls.isNotEmpty() && PlatformCapabilities.supportsHardwareBlur(context)) {
                val ambientUrl = ambientUrls.getOrNull(currentPage) ?: ambientUrls.first()
                PixivImage(
                    url = ambientUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    thumbnail = true,
                    maxDecodeDimensionPx = 128,
                    allowRgb565 = true,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = 0.40f * bgAlpha
                                scaleX = 1.35f
                                scaleY = 1.35f
                            }.blur(48.dp),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.30f * bgAlpha)),
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(swipeModifier)
                        .graphicsLayer {
                            translationY = dismissOffsetY.value
                            scaleX = contentScale
                            scaleY = contentScale
                        },
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
                        state = comicListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(
                            imageUrls,
                            key = { index, _ -> index },
                            contentType = { _, _ -> "comic_page" },
                        ) { page, url ->
                            PixivImage(
                                url = url,
                                contentDescription = "${illust.title} ${page + 1}",
                                contentScale = ContentScale.FillWidth,
                                showLoadingSpinner = true,
                                maxDecodeDimensionPx = 1920,
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
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
