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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.platform.PlatformCapabilities
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.BookmarkHeartButton
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.FlowButtons
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.LocalArtworkCardPreferences
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.ScrollablePullToRefresh
import com.yunfie.illustia.ui.components.SystemBarAppearanceEffect
import com.yunfie.illustia.ui.components.TransparentStatusBarColor
import com.yunfie.illustia.ui.components.adaptiveRelatedIllustColumns
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.performAppHapticFeedback
import com.yunfie.illustia.ui.components.rememberLiquidGlassBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
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
    isActive: Boolean = true,
    relatedIllusts: List<Illust>,
    firstComment: Comment?,
    settings: IllustDetailSettings,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onBookmarkLongClick: (() -> Unit)? = null,
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
    onReport: (String?, (Boolean) -> Unit) -> Unit,
    onMuteTag: (String) -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
    onOpenIllustById: (Long) -> Unit,
    onSaveImage: (String, String) -> Unit,
    onSaveAllImages: (List<String>, String) -> Unit,
    onMessage: (String) -> Unit,
    loadUgoiraPlayback: suspend (Long) -> UgoiraPlayback,
    onRelatedSectionReady: () -> Unit = {},
    onPageChanged: (Int) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val highQualityImages = settings.highQualityImages
    val detailQuality = settings.detailQuality
    val prefetchImages = settings.prefetchImages
    val confirmOnLongPressSave = settings.confirmOnLongPressSave
    val skipConfirmOnDetailSave = settings.skipConfirmOnDetailSave
    val smartDownloadNaming = settings.smartDownloadNaming
    val ugoiraPowerSave = settings.ugoiraPowerSave
    val mangaAdaptivePreload = settings.mangaAdaptivePreload
    val detailSectionOrder = settings.detailSectionOrder
    val relatedIllustColumnCount = settings.relatedIllustColumnCount
    val liquidGlassButtons = settings.liquidGlassButtons
    val liquidGlassFollowButton = settings.liquidGlassFollowButton
    val progressiveQuality = settings.progressiveQuality
    val relatedSectionInitiallyReady = settings.relatedSectionInitiallyReady
    val initialPage = settings.initialPage

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
    val lastMouseScrollTime = remember { LongArray(1) }
    val mouseScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (System.currentTimeMillis() - lastMouseScrollTime[0] < 400L && available.y > 0f) {
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }
            }
        }
    val pullToRefreshState = rememberPullToRefreshState()
    val detailListState = listState
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

    SystemBarAppearanceEffect(
        lightStatusBars =
            if (isArtworkOffScreen || isPulling) {
                !isDarkTheme
            } else {
                useDarkHeaderIcons
            },
        lightNavigationBars = !isDarkTheme,
        restoreLightStatusBars = !isDarkTheme,
        restoreLightNavigationBars = !isDarkTheme,
        statusBarColor = TransparentStatusBarColor,
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            onRefresh()
            delay(300)
            isRefreshing = false
        }
    }

    if (pendingSave != null) {
        val isUgoira = illust.type == "ugoira"
        MiuixConfirmDialog(
            show = true,
            title = stringResource(if (isUgoira) R.string.detail_save_ugoira_title else R.string.detail_save_image_title),
            summary = stringResource(if (isUgoira) R.string.detail_save_ugoira_confirm else R.string.detail_save_image_confirm),
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                pendingSave?.let { (url, filename) ->
                    performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.Success)
                    onSaveImage(url, filename)
                }
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
            performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.Success)
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
        DetailHeaderSlot(
            illust = illust,
            highQualityImages = highQualityImages,
            detailQuality = detailQuality,
            prefetchImages = prefetchImages,
            confirmOnLongPressSave = confirmOnLongPressSave,
            skipConfirmOnDetailSave = skipConfirmOnDetailSave,
            smartDownloadNaming = smartDownloadNaming,
            ugoiraPowerSave = ugoiraPowerSave,
            isActive = isActive,
            mangaAdaptivePreload = mangaAdaptivePreload,
            progressiveQuality = progressiveQuality,
            pixivUrl = pixivUrl,
            onBack = onBack,
            onOpenImage = onOpenImage,
            onDoubleTapImage = ::likeFromDoubleTap,
            onSaveImage = { url, name, confirm -> requestSave(url, name, confirm) },
            onSaveAllImages = { urls, prefix ->
                performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.Success)
                onSaveAllImages(urls, prefix)
            },
            onMuteIllust = onMuteIllust,
            onMuteUser = onMuteUser,
            onReport = onReport,
            onMessage = onMessage,
            loadUgoiraPlayback = loadUgoiraPlayback,
            isArtworkMuted = isArtworkMuted,
            revealMutedArtwork = revealMutedArtwork,
            onRevealMutedArtwork = { revealMutedArtwork = true },
            mutedArtworkTitle = mutedArtworkTitle,
            mutedArtworkSummary = mutedArtworkSummary,
            showLikeAnimation = showLikeAnimation,
            expanded = expanded,
            onHeaderIconsThemeChanged = { useDarkHeaderIcons = it },
            liquidGlassButtons = liquidGlassButtons,
            initialPage = initialPage,
            onPageChanged = onPageChanged,
            modifier = modifier,
        )
    }
    val orderedSections = remember(detailSectionOrder) { orderedDetailSections(detailSectionOrder) }
    val relatedSectionIndex = orderedSections.indexOf("related")
    val relatedColumns = adaptiveRelatedIllustColumns(relatedIllustColumnCount)
    val relatedRows = remember(relatedIllusts, relatedColumns) { relatedIllustRows(relatedIllusts, relatedColumns) }
    val relatedCardPreferences = LocalArtworkCardPreferences.current
    val followGlassBackdrop =
        rememberLiquidGlassBackdrop(
            enabled = liquidGlassFollowButton,
            backgroundColor = MiuixTheme.colorScheme.surface,
        )
    val detailInfoContent: @Composable (List<String>?, Boolean) -> Unit = { sections, showHeader ->
        DetailInfoSlot(
            illust = illust,
            isArtistFollowed = isArtistFollowed,
            isArtistMuted = isArtistMuted,
            relatedIllusts = relatedIllusts,
            onOpenUser = onOpenUser,
            onOpenIllustById = onOpenIllustById,
            onOpenComments = onOpenComments,
            onOpenSeries = onOpenSeries,
            onToggleFollow = onToggleFollow,
            onUnmuteUser = onUnmuteUser,
            onSearchTag = onSearchTag,
            onLongPressTag = onLongPressTag,
            detailSectionOrder = detailSectionOrder,
            relatedIllustColumnCount = relatedIllustColumnCount,
            onOpenIllust = onOpenIllust,
            onLongPressIllust = onLongPressIllust,
            onRequestUnfollowConfirm = { showUnfollowConfirm = true },
            relatedSectionInitiallyReady = relatedSectionInitiallyReady,
            onRelatedSectionReady = onRelatedSectionReady,
            sectionsOverride = sections,
            showHeader = showHeader,
            liquidGlassFollowButton = liquidGlassFollowButton,
            followBackdrop = followGlassBackdrop,
        )
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        floatingActionButton = {
            BookmarkHeartButton(
                isBookmarked = illust.isBookmarked,
                onClick = onBookmark,
                onLongClick = onBookmarkLongClick,
                size = 56.dp,
                iconSize = 26.dp,
                cornerRadius = 18.dp,
                activeBackground = MiuixTheme.colorScheme.surfaceContainerHigh,
                inactiveBackground = MiuixTheme.colorScheme.surfaceContainerHigh,
            )
        },
    ) { scaffoldPadding ->
        val statusBarTopPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surface),
        ) {
            if (isActive && isPulling && PlatformCapabilities.supportsHardwareBlur(context)) {
                PixivImage(
                    url = illust.thumbnailUrl.ifBlank { illust.squareImageUrl.ifBlank { illust.previewUrl } },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(36.dp)
                            .graphicsLayer {
                                val raw = pullToRefreshState.pullProgress
                                val progress = if (raw > 0.06f) ((raw - 0.06f) / 0.94f).coerceIn(0f, 1f) else 0f
                                alpha = progress * 0.55f
                            },
                    thumbnail = true,
                )
            }
            if (isPulling) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val raw = pullToRefreshState.pullProgress
                                val progress = if (raw > 0.06f) ((raw - 0.06f) / 0.94f).coerceIn(0f, 1f) else 0f
                                alpha = progress * 0.42f
                            }.background(MiuixTheme.colorScheme.surface),
                )
            }

            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = scaffoldPadding.calculateBottomPadding().coerceAtLeast(0.dp)),
                color = Color.Transparent,
            ) {
                ScrollablePullToRefresh(
                    isRefreshing = isRefreshing,
                    onRefresh = { isRefreshing = true },
                    pullToRefreshState = pullToRefreshState,
                    circleSize = 0.dp,
                    color = Color.Transparent,
                    refreshTexts = emptyList(),
                    contentPadding = PaddingValues(top = statusBarTopPadding + 8.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (followGlassBackdrop != null) {
                                    Modifier.layerBackdrop(followGlassBackdrop)
                                } else {
                                    Modifier
                                },
                            ),
                ) {
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    // Inactive (background) pages keep receiving pointer events while a
                                    // new artwork slides in; only the visible page may drive the
                                    // mouse-wheel coalescing timestamp shared by both lists.
                                    if (!isActive) return@pointerInput
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            if (event.type == PointerEventType.Scroll ||
                                                event.changes.any { it.type == PointerType.Mouse }
                                            ) {
                                                lastMouseScrollTime[0] = System.currentTimeMillis()
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
                                    state = detailListState,
                                    modifier =
                                        Modifier
                                            .weight(0.9f)
                                            .fillMaxHeight()
                                            .background(MiuixTheme.colorScheme.surface),
                                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                                ) {
                                    item(key = "detail_info", contentType = "detail_info") { detailInfoContent(null, true) }
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
                                item(key = "detail_header", contentType = "detail_header") { detailHeaderContent(false, Modifier) }
                                if (relatedSectionIndex >= 0) {
                                    val beforeSections = orderedSections.take(relatedSectionIndex)
                                    val afterSections = orderedSections.drop(relatedSectionIndex + 1)
                                    item(key = "detail_info_before", contentType = "detail_info") {
                                        detailInfoContent(beforeSections, true)
                                    }
                                    item(key = "related_header", contentType = "related_header") {
                                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(R.string.detail_related),
                                                color = MiuixTheme.colorScheme.onBackground,
                                                style = MiuixTheme.textStyles.title4,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                            )
                                        }
                                    }
                                    if (relatedRows.isEmpty()) {
                                        item(key = "related_loading", contentType = "related_loading") {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                LoadingIndicator()
                                            }
                                        }
                                    } else {
                                        itemsIndexed(
                                            items = relatedRows,
                                            key = { index, row -> "related_row_${row.firstOrNull()?.id ?: index}" },
                                            contentType = { _, _ -> "related_row" },
                                        ) { _, row ->
                                            RelatedIllustRow(
                                                rowItems = row,
                                                columns = relatedColumns,
                                                cardPreferences = relatedCardPreferences,
                                                onOpenIllust = onOpenIllust,
                                                onLongPressIllust = onLongPressIllust,
                                                modifier = Modifier.padding(bottom = 4.dp),
                                            )
                                        }
                                    }
                                    if (afterSections.isNotEmpty()) {
                                        item(key = "detail_info_after", contentType = "detail_info") {
                                            detailInfoContent(afterSections, false)
                                        }
                                    }
                                } else {
                                    item(key = "detail_info", contentType = "detail_info") { detailInfoContent(null, true) }
                                }
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

/**
 * Header slot. Kept as its own composable so the header (pager, ugoira playback, decode effects)
 * keeps one stable recomposition scope when settings or state change in [IllustDetailScreen].
 * Callers re-create the argument lambdas on every parent recomposition, so they are re-read
 * through [rememberUpdatedState] to keep identity stable for the subtree.
 */
@Composable
private fun DetailHeaderSlot(
    illust: Illust,
    highQualityImages: Boolean,
    detailQuality: String,
    prefetchImages: Boolean,
    confirmOnLongPressSave: Boolean,
    skipConfirmOnDetailSave: Boolean,
    smartDownloadNaming: Boolean,
    ugoiraPowerSave: Boolean,
    isActive: Boolean,
    mangaAdaptivePreload: Boolean,
    progressiveQuality: Boolean,
    pixivUrl: String,
    onBack: () -> Unit,
    onOpenImage: (Int) -> Unit,
    onDoubleTapImage: () -> Unit,
    onSaveImage: (String, String, Boolean) -> Unit,
    onSaveAllImages: (List<String>, String) -> Unit,
    onMuteIllust: () -> Unit,
    onMuteUser: () -> Unit,
    onReport: (String?, (Boolean) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
    loadUgoiraPlayback: suspend (Long) -> UgoiraPlayback,
    isArtworkMuted: Boolean,
    revealMutedArtwork: Boolean,
    onRevealMutedArtwork: () -> Unit,
    mutedArtworkTitle: String,
    mutedArtworkSummary: String,
    showLikeAnimation: Boolean,
    expanded: Boolean,
    onHeaderIconsThemeChanged: (Boolean) -> Unit,
    liquidGlassButtons: Boolean,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenImage by rememberUpdatedState(onOpenImage)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTapImage)
    val currentOnSaveImage by rememberUpdatedState(onSaveImage)
    val currentOnSaveAllImages by rememberUpdatedState(onSaveAllImages)
    val currentOnMuteIllust by rememberUpdatedState(onMuteIllust)
    val currentOnMuteUser by rememberUpdatedState(onMuteUser)
    val currentOnReport by rememberUpdatedState(onReport)
    val currentOnMessage by rememberUpdatedState(onMessage)
    val currentOnReveal by rememberUpdatedState(onRevealMutedArtwork)
    val currentOnTheme by rememberUpdatedState(onHeaderIconsThemeChanged)
    IllustDetailHeader(
        illust = illust,
        highQualityImages = highQualityImages,
        detailQuality = detailQuality,
        prefetchImages = prefetchImages && isActive,
        confirmOnLongPressSave = confirmOnLongPressSave,
        skipConfirmOnDetailSave = skipConfirmOnDetailSave,
        smartDownloadNaming = smartDownloadNaming,
        ugoiraPowerSave = ugoiraPowerSave,
        isActive = isActive,
        mangaAdaptivePreload = mangaAdaptivePreload,
        progressiveQuality = progressiveQuality,
        pixivUrl = pixivUrl,
        onBack = { currentOnBack() },
        onOpenImage = { currentOnOpenImage(it) },
        onDoubleTapImage = { currentOnDoubleTap() },
        onSaveImage = { url, name, confirm -> currentOnSaveImage(url, name, confirm) },
        onSaveAllImages = { urls, prefix -> currentOnSaveAllImages(urls, prefix) },
        onMuteIllust = { currentOnMuteIllust() },
        onMuteUser = { currentOnMuteUser() },
        onReport = { reason, complete -> currentOnReport(reason, complete) },
        onMessage = { currentOnMessage(it) },
        loadUgoiraPlayback = loadUgoiraPlayback,
        showImage = true,
        maskMutedArtwork = isArtworkMuted && !revealMutedArtwork,
        onRevealMutedArtwork = { currentOnReveal() },
        mutedArtworkTitle = mutedArtworkTitle,
        mutedArtworkSummary = mutedArtworkSummary,
        showLikeAnimation = showLikeAnimation,
        expanded = expanded,
        onHeaderIconsThemeChanged = { currentOnTheme(it) },
        liquidGlassButtons = liquidGlassButtons,
        initialPage = initialPage,
        onPageChanged = onPageChanged,
        modifier = modifier,
    )
}

/** Info slot; see [DetailHeaderSlot] for why the lambdas are re-read via [rememberUpdatedState]. */
@Composable
private fun DetailInfoSlot(
    illust: Illust,
    isArtistFollowed: Boolean,
    isArtistMuted: Boolean,
    relatedIllusts: List<Illust>,
    onOpenUser: (Long) -> Unit,
    onOpenIllustById: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenSeries: (() -> Unit)?,
    onToggleFollow: () -> Unit,
    onUnmuteUser: () -> Unit,
    onSearchTag: (String) -> Unit,
    onLongPressTag: (String) -> Unit,
    detailSectionOrder: List<String>,
    relatedIllustColumnCount: Int,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
    onRequestUnfollowConfirm: () -> Unit,
    relatedSectionInitiallyReady: Boolean,
    onRelatedSectionReady: () -> Unit,
    sectionsOverride: List<String>? = null,
    showHeader: Boolean = true,
    liquidGlassFollowButton: Boolean,
    followBackdrop: Backdrop?,
) {
    val currentOnOpenUser by rememberUpdatedState(onOpenUser)
    val currentOnOpenIllustById by rememberUpdatedState(onOpenIllustById)
    val currentOnOpenComments by rememberUpdatedState(onOpenComments)
    val currentOnOpenSeries by rememberUpdatedState(onOpenSeries)
    val currentOnToggleFollow by rememberUpdatedState(onToggleFollow)
    val currentOnUnmuteUser by rememberUpdatedState(onUnmuteUser)
    val currentOnSearchTag by rememberUpdatedState(onSearchTag)
    val currentOnLongPressTag by rememberUpdatedState(onLongPressTag)
    val currentOnOpenIllust by rememberUpdatedState(onOpenIllust)
    val currentOnLongPressIllust by rememberUpdatedState(onLongPressIllust)
    val currentOnUnfollowConfirm by rememberUpdatedState(onRequestUnfollowConfirm)
    IllustDetailInfo(
        illust = illust,
        isArtistFollowed = isArtistFollowed,
        isArtistMuted = isArtistMuted,
        onOpenUser = { currentOnOpenUser(illust.artistId) },
        onOpenUserById = { currentOnOpenUser(it) },
        onOpenIllustById = { currentOnOpenIllustById(it) },
        onOpenComments = { currentOnOpenComments() },
        onOpenSeries = currentOnOpenSeries?.let { openSeries -> { openSeries() } },
        onToggleFollow = {
            if (isArtistFollowed) currentOnUnfollowConfirm() else currentOnToggleFollow()
        },
        onUnmuteUser = { currentOnUnmuteUser() },
        onSearchTag = { currentOnSearchTag(it) },
        onLongPressTag = { currentOnLongPressTag(it) },
        sectionOrder = detailSectionOrder,
        relatedContent = { isNearViewport ->
            if (!isNearViewport) {
                Spacer(Modifier.height(24.dp))
            } else if (relatedIllusts.isNotEmpty()) {
                RelatedIllustsList(
                    relatedIllusts = relatedIllusts,
                    onOpenIllust = { currentOnOpenIllust(it) },
                    onLongPressIllust = { currentOnLongPressIllust(it) },
                    configuredColumns = relatedIllustColumnCount,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }
        },
        relatedSectionInitiallyReady = relatedSectionInitiallyReady,
        onRelatedSectionReady = onRelatedSectionReady,
        sectionsOverride = sectionsOverride,
        showHeader = showHeader,
        liquidGlassFollowButton = liquidGlassFollowButton,
        followBackdrop = followBackdrop,
    )
}
