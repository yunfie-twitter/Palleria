package com.yunfie.illustia.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.BookmarkHeartButton
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.FlowButtons
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.performAppHapticFeedback
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IllustDetailScreen(
    illust: Illust,
    relatedIllusts: List<Illust>,
    firstComment: Comment?,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onRefresh: () -> Unit = {},
    onOpenUser: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenSeries: (() -> Unit)? = null,
    onOpenImage: (Int) -> Unit,
    onSearchTag: (String) -> Unit,
    onLongPressTag: (String) -> Unit,
    isArtistFollowed: Boolean,
    isArtistMuted: Boolean,
    isTagMuted: Boolean,
    onToggleFollow: () -> Unit,
    onUnmuteUser: () -> Unit,
    onMuteIllust: () -> Unit,
    onMuteUser: () -> Unit,
    onMuteTag: (String) -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
    onOpenIllustById: (Long) -> Unit,
    onSaveImage: (String, String) -> Unit,
    onSaveAllImages: (List<String>, String) -> Unit,
    onMessage: (String) -> Unit,
    loadUgoiraPlayback: suspend (Long) -> UgoiraPlayback,
    highQualityImages: Boolean,
    detailQuality: String,
    prefetchImages: Boolean,
    confirmOnLongPressSave: Boolean,
    skipConfirmOnDetailSave: Boolean,
    detailSectionOrder: List<String>,
    relatedIllustColumnCount: Int = 3,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current
    PredictiveBackGestureHandler(onBack = onBack)
    var pendingSave by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showUnfollowConfirm by remember { mutableStateOf(false) }
    var showLikeAnimation by remember(illust.id) { mutableStateOf(false) }
    val isArtworkMuted = isArtistMuted || isTagMuted
    var revealMutedArtwork by remember(illust.id, isArtistMuted, isTagMuted) { mutableStateOf(!isArtworkMuted) }
    val pixivUrl = remember(illust.id) { "https://www.pixiv.net/artworks/${illust.id}" }
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    var lastMouseScrollTime by remember { mutableStateOf(0L) }
    val mouseScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (System.currentTimeMillis() - lastMouseScrollTime < 400L && available.y > 0f) {
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }
            }
        }
    val pullToRefreshState = rememberPullToRefreshState()
    val detailListState = rememberLazyListState()
    var useDarkHeaderIcons by remember(illust.id) { mutableStateOf(false) }
    val isArtworkOffScreen by remember {
        derivedStateOf {
            detailListState.firstVisibleItemIndex > 0
        }
    }
    val activity = context as? Activity
    val surfaceColor = MiuixTheme.colorScheme.surface
    val isDarkTheme = surfaceColor.luminance() < 0.5f

    val isPulling by remember {
        derivedStateOf { pullToRefreshState.pullProgress > 0.05f || isRefreshing }
    }

    DisposableEffect(isArtworkOffScreen, isDarkTheme, useDarkHeaderIcons, isPulling) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            insetsController.isAppearanceLightStatusBars =
                if (isArtworkOffScreen || isPulling) {
                    !isDarkTheme
                } else {
                    useDarkHeaderIcons
                }
        }
        onDispose { }
    }

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRefresh()
            delay(300)
            isRefreshing = false
        }
    }

    // 詳細画面を開く際の重さを軽減するために、関連作品だけを遅延レンダリングする
    var showHeavyContent by remember { mutableStateOf(false) }
    LaunchedEffect(illust.id) {
        delay(240) // 遷移アニメーションの完了を待つ
        showHeavyContent = true
    }

    if (pendingSave != null) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.detail_save_image_title),
            summary = stringResource(R.string.detail_save_image_confirm),
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                pendingSave?.let { (url, filename) -> onSaveImage(url, filename) }
                pendingSave = null
            },
            onDismiss = { pendingSave = null },
        )
    }

    if (showUnfollowConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.detail_unfollow_title),
            summary = stringResource(R.string.detail_unfollow_confirm, illust.artistName),
            confirmText = stringResource(R.string.action_unfollow),
            destructive = true,
            onConfirm = {
                showUnfollowConfirm = false
                onToggleFollow()
            },
            onDismiss = { showUnfollowConfirm = false },
        )
    }

    fun requestSave(
        url: String,
        filename: String,
        requireConfirm: Boolean,
    ) {
        if (requireConfirm) {
            pendingSave = url to filename
        } else {
            onSaveImage(url, filename)
        }
    }

    fun likeFromDoubleTap() {
        showLikeAnimation = false
        showLikeAnimation = true
        performAppHapticFeedback(context, haptic, hapticMode)
        if (!illust.isBookmarked) onBookmark()
    }

    LaunchedEffect(showLikeAnimation) {
        if (showLikeAnimation) {
            delay(720)
            showLikeAnimation = false
        }
    }

    val mutedArtworkTitle =
        if (isArtistMuted) {
            stringResource(R.string.detail_muted_artist)
        } else {
            stringResource(R.string.detail_muted_work)
        }
    val mutedArtworkSummary =
        if (isArtistMuted) {
            stringResource(
                R.string.detail_muted_artist_blur,
                illust.artistName.ifBlank { stringResource(R.string.detail_muted_artist_blur_default) },
            )
        } else {
            stringResource(R.string.detail_muted_work_blur)
        }
    val detailHeaderContent: @Composable (Boolean, Modifier) -> Unit = { expanded, modifier ->
        IllustDetailHeader(
            illust = illust,
            highQualityImages = highQualityImages,
            detailQuality = detailQuality,
            prefetchImages = prefetchImages,
            confirmOnLongPressSave = confirmOnLongPressSave,
            skipConfirmOnDetailSave = skipConfirmOnDetailSave,
            pixivUrl = pixivUrl,
            onBack = onBack,
            onOpenImage = onOpenImage,
            onDoubleTapImage = ::likeFromDoubleTap,
            onSaveImage = { url, name, confirm -> requestSave(url, name, confirm) },
            onSaveAllImages = onSaveAllImages,
            onMuteIllust = onMuteIllust,
            onMuteUser = onMuteUser,
            onMessage = onMessage,
            loadUgoiraPlayback = loadUgoiraPlayback,
            showImage = true,
            maskMutedArtwork = isArtworkMuted && !revealMutedArtwork,
            onRevealMutedArtwork = { revealMutedArtwork = true },
            mutedArtworkTitle = mutedArtworkTitle,
            mutedArtworkSummary = mutedArtworkSummary,
            showLikeAnimation = showLikeAnimation,
            expanded = expanded,
            onHeaderIconsThemeChanged = { useDarkHeaderIcons = it },
            modifier = modifier,
        )
    }
    val detailInfoContent: @Composable () -> Unit = {
        IllustDetailInfo(
            illust = illust,
            isArtistFollowed = isArtistFollowed,
            isArtistMuted = isArtistMuted,
            onOpenUser = { onOpenUser(illust.artistId) },
            onOpenUserById = onOpenUser,
            onOpenIllustById = onOpenIllustById,
            onOpenComments = onOpenComments,
            onOpenSeries = onOpenSeries,
            onToggleFollow = {
                if (isArtistFollowed) showUnfollowConfirm = true else onToggleFollow()
            },
            onUnmuteUser = onUnmuteUser,
            onSearchTag = onSearchTag,
            onLongPressTag = onLongPressTag,
            sectionOrder = detailSectionOrder,
            relatedContent = {
                if (showHeavyContent) {
                    RelatedIllustsList(
                        relatedIllusts = relatedIllusts,
                        onOpenIllust = onOpenIllust,
                        onLongPressIllust = onLongPressIllust,
                        configuredColumns = relatedIllustColumnCount,
                    )
                } else {
                    LoadingIndicator(modifier = Modifier.padding(vertical = 24.dp))
                }
            },
        )
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (illust.isBookmarked) {
                        performAppHapticFeedback(context, haptic, hapticMode)
                        onBookmark()
                    } else {
                        performAppHapticFeedback(context, haptic, hapticMode)
                        onBookmark()
                    }
                },
                shape = RoundedCornerShape(18.dp),
                containerColor = MiuixTheme.colorScheme.surfaceContainerHigh,
            ) {
                AnimatedContent(targetState = illust.isBookmarked, label = "detail-bookmark-fab") { bookmarked ->
                    Icon(
                        imageVector = if (bookmarked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                        contentDescription = stringResource(R.string.action_bookmark),
                        tint = if (bookmarked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface),
        ) {
            val rawPullProgress = pullToRefreshState.pullProgress
            val pullProgress =
                if (rawPullProgress > 0.06f) {
                    ((rawPullProgress - 0.06f) / 0.94f).coerceIn(0f, 1f)
                } else {
                    0f
                }
            if (pullProgress > 0.001f) {
                PixivImage(
                    url = illust.squareImageUrl.ifBlank { illust.imageUrl },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(36.dp)
                            .graphicsLayer { alpha = pullProgress * 0.55f },
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface.copy(alpha = pullProgress * 0.42f)),
                )
            }

            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = scaffoldPadding.calculateBottomPadding().coerceAtLeast(0.dp)),
                color = Color.Transparent,
            ) {
                PullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = true },
                    pullToRefreshState = pullToRefreshState,
                    circleSize = 0.dp,
                    color = Color.Transparent,
                    refreshTexts = emptyList(),
                    contentPadding = PaddingValues(top = statusBarTopPadding + 8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.type == PointerEventType.Scroll ||
                                                event.changes.any { it.type == PointerType.Mouse }
                                            ) {
                                                lastMouseScrollTime = System.currentTimeMillis()
                                            }
                                        }
                                    }
                                }.nestedScroll(mouseScrollConnection),
                    ) {
                        val useTwoPaneLayout = maxWidth >= 840.dp && maxWidth > maxHeight
                        if (useTwoPaneLayout) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                detailHeaderContent(true, Modifier.weight(1.1f).fillMaxHeight())
                                LazyColumn(
                                    modifier =
                                        Modifier
                                            .weight(0.9f)
                                            .fillMaxHeight()
                                            .background(MiuixTheme.colorScheme.surface),
                                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                                ) {
                                    item { detailInfoContent() }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = detailListState,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(MiuixTheme.colorScheme.surface),
                                contentPadding = PaddingValues(bottom = 96.dp),
                            ) {
                                item { detailHeaderContent(false, Modifier) }
                                item { detailInfoContent() }
                            }
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = isRefreshing,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(),
                modifier = Modifier.align(androidx.compose.ui.Alignment.TopCenter),
            ) {
                top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
                    // Draw in the edge-to-edge layer so the refresh bar remains visible
                    // over the transparent status bar instead of below the artwork header.
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
