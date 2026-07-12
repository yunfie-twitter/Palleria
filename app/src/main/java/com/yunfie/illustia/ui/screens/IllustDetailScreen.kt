package com.yunfie.illustia.ui.screens

import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.pixiv.Comment
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
import com.yunfie.illustia.models.pixiv.UgoiraPlayback
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
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
    onOpenUser: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenSeries: (() -> Unit)? = null,
    onOpenImage: (Int) -> Unit,
    onSearchTag: (String) -> Unit,
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

    fun requestSave(url: String, filename: String, requireConfirm: Boolean) {
        if (requireConfirm) {
            pendingSave = url to filename
        } else {
            onSaveImage(url, filename)
        }
    }

    fun likeWithAnimation() {
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

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                    if (illust.isBookmarked) {
                        performAppHapticFeedback(context, haptic, hapticMode)
                        onBookmark()
                    } else {
                        likeWithAnimation()
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
        Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(bottom = scaffoldPadding.calculateBottomPadding().coerceAtLeast(0.dp)),
            color = MiuixTheme.colorScheme.surface,
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item {
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
                    onDoubleTapImage = ::likeWithAnimation,
                    onSaveImage = { url, name, confirm -> requestSave(url, name, confirm) },
                    onSaveAllImages = onSaveAllImages,
                    onMuteIllust = onMuteIllust,
                    onMuteUser = onMuteUser,
                    onMessage = onMessage,
                    loadUgoiraPlayback = loadUgoiraPlayback,
                    showImage = true,
                    maskMutedArtwork = isArtworkMuted && !revealMutedArtwork,
                    onRevealMutedArtwork = { revealMutedArtwork = true },
                    mutedArtworkTitle = if (isArtistMuted) {
                        stringResource(R.string.detail_muted_artist)
                    } else {
                        stringResource(R.string.detail_muted_work)
                    },
                    mutedArtworkSummary = if (isArtistMuted) {
                        stringResource(
                            R.string.detail_muted_artist_blur,
                            illust.artistName.ifBlank { stringResource(R.string.detail_muted_artist_blur_default) },
                        )
                    } else {
                        stringResource(R.string.detail_muted_work_blur)
                    },
                )
            }
            item {
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
                )
            }
            
            if (showHeavyContent) {
                item {
                    RelatedIllustsList(
                        relatedIllusts = relatedIllusts,
                        onOpenIllust = onOpenIllust,
                        onLongPressIllust = onLongPressIllust,
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = showLikeAnimation,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn() + scaleIn(
                initialScale = 0.25f,
                animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f),
            ),
            exit = fadeOut() + scaleOut(targetScale = 1.35f),
        ) {
            Icon(
                imageVector = MiuixIcons.FavoritesFill,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.error,
                modifier = Modifier.size(112.dp),
            )
        }
        }
    }
}
}

