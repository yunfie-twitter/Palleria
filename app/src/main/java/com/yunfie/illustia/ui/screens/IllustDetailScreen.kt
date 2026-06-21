package com.yunfie.illustia.ui.screens

import android.content.ClipData
import android.content.Intent
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.BookmarkHeartButton
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.FlowButtons
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.miuixClickable
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
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onOpenUser: () -> Unit,
    onOpenImage: (Int) -> Unit,
    onSearchTag: (String) -> Unit,
    isArtistFollowed: Boolean,
    isArtistMuted: Boolean,
    onToggleFollow: () -> Unit,
    onUnmuteUser: () -> Unit,
    onMuteIllust: () -> Unit,
    onMuteUser: () -> Unit,
    onMuteTag: (String) -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onSaveImage: (String, String) -> Unit,
    onSaveAllImages: (List<String>, String) -> Unit,
    onMessage: (String) -> Unit,
    highQualityImages: Boolean,
    detailQuality: String,
    prefetchImages: Boolean,
    confirmOnLongPressSave: Boolean,
    skipConfirmOnDetailSave: Boolean,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    var pendingSave by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showUnfollowConfirm by remember { mutableStateOf(false) }
    var revealMutedArtwork by remember(illust.id, isArtistMuted) { mutableStateOf(!isArtistMuted) }
    val pixivUrl = remember(illust.id) { "https://www.pixiv.net/artworks/${illust.id}" }
    
    // 詳細画面を開く際の重さを軽減するために重いコンテンツを遅延レンダリングする
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

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onBookmark,
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
        Surface(
            modifier = Modifier.fillMaxSize().padding(bottom = scaffoldPadding.calculateBottomPadding()),
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
                    onSaveImage = { url, name, confirm -> requestSave(url, name, confirm) },
                    onSaveAllImages = onSaveAllImages,
                    onMuteIllust = onMuteIllust,
                    onMuteUser = onMuteUser,
                    onMessage = onMessage,
                    showImage = showHeavyContent,
                    maskMutedArtwork = isArtistMuted && !revealMutedArtwork,
                    onRevealMutedArtwork = { revealMutedArtwork = true },
                )
            }
            item {
                IllustDetailInfo(
                    illust = illust,
                    isArtistFollowed = isArtistFollowed,
                    isArtistMuted = isArtistMuted,
                    onOpenUser = onOpenUser,
                    onToggleFollow = {
                        if (isArtistFollowed) showUnfollowConfirm = true else onToggleFollow()
                    },
                    onUnmuteUser = onUnmuteUser,
                    onSearchTag = onSearchTag
                )
            }
            
            if (showHeavyContent) {
                item {
                    RelatedIllustsList(
                        relatedIllusts = relatedIllusts,
                        onOpenIllust = onOpenIllust
                    )
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IllustDetailHeader(
    illust: Illust,
    highQualityImages: Boolean,
    detailQuality: String,
    prefetchImages: Boolean,
    confirmOnLongPressSave: Boolean,
    skipConfirmOnDetailSave: Boolean,
    pixivUrl: String,
    onBack: () -> Unit,
    onOpenImage: (Int) -> Unit,
    onSaveImage: (String, String, Boolean) -> Unit,
    onSaveAllImages: (List<String>, String) -> Unit,
    onMuteIllust: () -> Unit,
    onMuteUser: () -> Unit,
    onMessage: (String) -> Unit,
    showImage: Boolean,
    maskMutedArtwork: Boolean,
    onRevealMutedArtwork: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboard = remember(context) { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }
    val openInBrowserLabel = stringResource(R.string.detail_open_in_browser)
    val shareLabel = stringResource(R.string.detail_share)
    val saveImageLabel = stringResource(R.string.detail_save_image)
    val saveAllPagesLabel = stringResource(R.string.detail_save_all_pages)
    val copyUrlLabel = stringResource(R.string.detail_copy_url)
    val muteWorkLabel = stringResource(R.string.detail_mute_work)
    val muteArtistLabel = stringResource(R.string.detail_mute_artist)
    val moreLabel = stringResource(R.string.detail_more)
    val browserFailedMessage = stringResource(R.string.error_browser_failed)
    val shareFailedMessage = stringResource(R.string.error_share_failed)
    val urlCopiedMessage = stringResource(R.string.msg_url_copied)
    val imageUrls = remember(illust.id, highQualityImages, detailQuality) {
        when {
            !highQualityImages || detailQuality == "low" -> illust.mediumImagePages.ifEmpty {
                listOf(illust.mediumImageUrl.ifBlank { illust.squareImageUrl.ifBlank { illust.imageUrl } })
            }
            detailQuality == "medium" -> illust.imagePages.ifEmpty { listOf(illust.imageUrl) }
            else -> illust.originalImagePages.ifEmpty {
                illust.imagePages.ifEmpty { listOfNotNull(illust.originalImageUrl ?: illust.imageUrl) }
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .heightIn(min = 320.dp)
            .background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        if (showImage) {
            val pagerState = rememberPagerState(pageCount = { imageUrls.size })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                beyondViewportPageCount = if (prefetchImages) 1 else 0,
                key = { it }
            ) { page ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    PixivImage(
                        url = imageUrls[page],
                        contentDescription = illust.title,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (maskMutedArtwork) Modifier.blur(18.dp) else Modifier)
                            .combinedClickable(
                                enabled = !maskMutedArtwork,
                                onClick = { onOpenImage(page) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSaveImage(imageUrls[page], "illustia_${illust.id}_p$page", confirmOnLongPressSave)
                                },
                            ),
                        crossfade = true,
                    )
                    if (maskMutedArtwork) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.38f)),
                        )
                    }
                }
            }

            if (maskMutedArtwork) {
                MutedArtworkOverlay(
                    artistName = illust.artistName,
                    onReveal = onRevealMutedArtwork,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (imageUrls.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("${pagerState.currentPage + 1} / ${imageUrls.size}", color = MiuixTheme.colorScheme.onSurface, style = MiuixTheme.textStyles.footnote1, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // 画像読み込み前のプレースホルダー
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderOverlayIcon(MiuixIcons.Back, onBack)
            WindowIconDropdownMenu(
                entries = listOf(
                    DropdownEntry(
                        items = listOfNotNull(
                            DropdownItem(
                                text = openInBrowserLabel,
                                onClick = {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pixivUrl)))
                                    }.onFailure { onMessage(browserFailedMessage) }
                                },
                            ),
                            DropdownItem(
                                text = shareLabel,
                                onClick = {
                                    runCatching {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, "${illust.title} by ${illust.artistName}\n$pixivUrl")
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, shareLabel))
                                    }.onFailure { onMessage(shareFailedMessage) }
                                },
                            ),
                            DropdownItem(
                                text = saveImageLabel,
                                onClick = {
                                    onSaveImage(illust.originalImageUrl ?: illust.imageUrl, "illustia_${illust.id}", !skipConfirmOnDetailSave)
                                },
                            ),
                            if (imageUrls.size > 1) {
                                DropdownItem(
                                    text = saveAllPagesLabel,
                                    onClick = {
                                        onSaveAllImages(imageUrls, "illustia_${illust.id}")
                                    },
                                )
                            } else null,
                            DropdownItem(
                                text = copyUrlLabel,
                                onClick = {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Pixiv URL", pixivUrl))
                                    onMessage(urlCopiedMessage)
                                },
                            ),
                        ),
                    ),
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = muteWorkLabel,
                                onClick = {
                                    onMuteIllust()
                                    onBack()
                                },
                            ),
                            DropdownItem(
                                text = muteArtistLabel,
                                onClick = {
                                    onMuteUser()
                                    onBack()
                                },
                            ),
                        ),
                    ),
                ),
                backgroundColor = Color.Black.copy(alpha = 0.35f),
                cornerRadius = 19.dp,
                minWidth = 38.dp,
                minHeight = 38.dp,
            ) {
                Icon(MiuixIcons.More, contentDescription = moreLabel, tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun MutedArtworkOverlay(
    artistName: String,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.detail_muted_artist),
            color = Color.White,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = stringResource(R.string.detail_muted_artist_blur, artistName.ifBlank { stringResource(R.string.detail_muted_artist_blur_default) }),
            color = Color.White.copy(alpha = 0.82f),
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Bold,
        )
        Button(
            onClick = onReveal,
            colors = ButtonDefaults.buttonColors(
                color = MiuixTheme.colorScheme.error,
            ),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(stringResource(R.string.action_show), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IllustDetailInfo(
    illust: Illust,
    isArtistFollowed: Boolean,
    isArtistMuted: Boolean,
    onOpenUser: () -> Unit,
    onToggleFollow: () -> Unit,
    onUnmuteUser: () -> Unit,
    onSearchTag: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.surface)
            .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(illust.title, color = MiuixTheme.colorScheme.onBackground, style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ID ${illust.id}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.footnote1)
                Text(illust.type, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.footnote1)
                if (illust.pageCount > 1) {
                    Text("${illust.pageCount}P", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.footnote1)
                }
            }
            Text(
                text = stringResource(R.string.detail_illust_id) + " ${illust.id}    " + stringResource(R.string.detail_resolution) + "  ${if (illust.originalImageUrl != null) "original" else "large"}",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
            )
        }
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .miuixClickable(onClick = onOpenUser)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            AvatarImage(
                url = illust.artistAvatarUrl,
                name = illust.artistName,
                size = 44.dp,
            )
            Text(
                text = illust.artistName,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .miuixClickable(
                        pressedScale = 0.94f,
                        haptic = true,
                        onClick = if (isArtistMuted) onUnmuteUser else onToggleFollow,
                    ),
            ) {
                if (isArtistMuted) {
                    MutedUserPill()
                } else {
                    FollowPill(isFollowed = isArtistFollowed)
                }
            }
        }
        
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            FlowButtons(
                values = illust.tags.take(12),
                label = { "#$it" },
                onClick = onSearchTag,
            )
        }

        ElevatedPanel(
            modifier = Modifier.padding(horizontal = 12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            Text(
                text = illust.caption.ifBlank { stringResource(R.string.detail_no_caption) },
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.body1,
                lineHeight = 23.sp,
            )
        }
        
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.detail_related),
            color = MiuixTheme.colorScheme.onBackground,
            style = MiuixTheme.textStyles.title4,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun MutedUserPill(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MiuixTheme.colorScheme.error)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.detail_unmute),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun RelatedIllustsList(
    relatedIllusts: List<Illust>,
    onOpenIllust: (Illust) -> Unit,
    modifier: Modifier = Modifier
) {
    val relatedRows = remember(relatedIllusts) { relatedIllusts.chunked(3) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        relatedRows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEach { related ->
                    Box(modifier = Modifier.weight(1f)) {
                        key(related.id) {
                            PixivImage(
                                url = related.squareImageUrl.ifBlank { related.mediumImageUrl.ifBlank { related.imageUrl } },
                                contentDescription = related.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .miuixClickable { onOpenIllust(related) },
                                thumbnail = true
                            )
                        }
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
