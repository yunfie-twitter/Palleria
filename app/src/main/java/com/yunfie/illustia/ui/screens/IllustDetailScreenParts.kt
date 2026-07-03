package com.yunfie.illustia.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.FlowButtons
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.performAppHapticFeedback
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun IllustDetailHeader(
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
    val hapticMode = LocalAppHapticMode.current
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
            val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { imageUrls.size })

            androidx.compose.foundation.pager.HorizontalPager(
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
                                    performAppHapticFeedback(context, haptic, hapticMode)
                                    onSaveImage(imageUrls[page], "illustia_${illust.id}_p$page", confirmOnLongPressSave)
                                },
                            ),
                        crossfade = true,
                    )
                    if (maskMutedArtwork) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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
            Box(
                modifier = Modifier.fillMaxWidth(),
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
internal fun MutedArtworkOverlay(
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
internal fun IllustDetailInfo(
    illust: Illust,
    isArtistFollowed: Boolean,
    isArtistMuted: Boolean,
    firstComment: Comment?,
    onOpenUser: () -> Unit,
    onOpenUserById: (Long) -> Unit,
    onOpenIllustById: (Long) -> Unit,
    onOpenComments: () -> Unit,
    onOpenSeries: (() -> Unit)? = null,
    onToggleFollow: () -> Unit,
    onUnmuteUser: () -> Unit,
    onSearchTag: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var followAnimationTrigger by remember(illust.artistId) { mutableIntStateOf(0) }
    val customUriHandler = remember(uriHandler) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val event = NativeIntentRouter.parseText(uri)
                when (event) {
                    is NativeIntentEvent.Artwork -> onOpenIllustById(event.id)
                    is NativeIntentEvent.User -> onOpenUserById(event.id)
                    else -> try {
                        uriHandler.openUri(uri)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

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
                        onClick = if (isArtistMuted) {
                            onUnmuteUser
                        } else {
                            {
                                if (!isArtistFollowed) {
                                    followAnimationTrigger += 1
                                }
                                onToggleFollow()
                            }
                        },
                    ),
            ) {
                if (isArtistMuted) {
                    DetailMutedUserPill()
                } else {
                    FollowPill(isFollowed = isArtistFollowed, followAnimationTrigger = followAnimationTrigger)
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

        CommentPreviewCard(
            comment = firstComment,
            onClick = onOpenComments,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        if (illust.series != null && onOpenSeries != null) {
            Button(
                onClick = onOpenSeries,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
            ) {
                Text(
                    text = stringResource(R.string.detail_series),
                    color = MiuixTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        CompositionLocalProvider(LocalUriHandler provides customUriHandler) {
            ElevatedPanel(
                modifier = Modifier.padding(horizontal = 12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                val annotatedCaption = remember(illust.caption) {
                    if (illust.caption.isBlank()) null
                    else {
                        try {
                            AnnotatedString.fromHtml(illust.caption)
                        } catch (e: Exception) {
                            AnnotatedString(illust.caption)
                        }
                    }
                }
                SelectionContainer {
                    Text(
                        text = annotatedCaption ?: AnnotatedString(stringResource(R.string.detail_no_caption)),
                        color = MiuixTheme.colorScheme.onBackground,
                        style = MiuixTheme.textStyles.body1,
                        lineHeight = 23.sp,
                    )
                }
            }
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
private fun CommentPreviewCard(
    comment: Comment?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val user = comment?.user
    val avatarUrl = user?.profileImageUrls?.medium.orEmpty()
    val userName = user?.name.orEmpty()
    val commentText = remember(comment?.comment) {
        comment?.comment?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    }.lineSequence().firstOrNull().orEmpty().take(80)
    val fallbackLabel = stringResource(R.string.detail_comments)
    val emptySummary = stringResource(R.string.detail_comments_empty)
    ElevatedPanel(
        modifier = modifier
            .fillMaxWidth()
            .miuixClickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarUrl.isNotBlank()) {
                    AvatarImage(url = avatarUrl, name = userName.ifBlank { fallbackLabel }, size = 42.dp)
                } else {
                    Icon(
                        imageVector = MiuixIcons.Messages,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = userName.ifBlank { fallbackLabel },
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                if (commentText.isNotBlank()) {
                    Text(
                        text = commentText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                } else if (comment == null) {
                    Text(
                        text = emptySummary,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = MiuixIcons.ChevronForward,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
@Composable
internal fun DetailMutedUserPill(modifier: Modifier = Modifier) {
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
internal fun RelatedIllustsList(
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
