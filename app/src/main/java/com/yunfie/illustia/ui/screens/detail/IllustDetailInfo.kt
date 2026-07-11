package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.ui.components.AvatarImage
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.FlowButtons
import com.yunfie.illustia.ui.components.FollowPill
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun IllustDetailInfo(
    illust: Illust,
    isArtistFollowed: Boolean,
    isArtistMuted: Boolean,
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
                if (illust.series != null && onOpenSeries != null) {
                    Text(
                        text = illust.series.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.detail_series),
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier
                            .weight(1f)
                            .miuixClickable(
                                pressedScale = 0.96f,
                                haptic = true,
                                onClick = onOpenSeries,
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(illust.type, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold, style = MiuixTheme.textStyles.footnote1)
                }
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
            AvatarImage(url = illust.artistAvatarUrl, name = illust.artistName, size = 44.dp)
            Text(
                text = illust.artistName,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.main,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
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

        CompositionLocalProvider(LocalUriHandler provides customUriHandler) {
            ElevatedPanel(
                modifier = Modifier.padding(horizontal = 12.dp),
                contentPadding = PaddingValues(16.dp),
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

        CommentEntryButton(
            onClick = onOpenComments,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

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

@Composable
private fun CommentEntryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val commentsLabel = stringResource(R.string.detail_show_comments)
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(color = MiuixTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Messages,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = commentsLabel,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                )
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
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RelatedIllustsList(
    relatedIllusts: List<Illust>,
    onOpenIllust: (Illust) -> Unit,
    onLongPressIllust: (Illust) -> Unit,
    modifier: Modifier = Modifier,
) {
    val relatedRows = remember(relatedIllusts) { relatedIllusts.chunked(3) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        relatedRows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                                    .combinedClickable(
                                        onClick = { onOpenIllust(related) },
                                        onLongClick = { onLongPressIllust(related) },
                                    ),
                                thumbnail = true,
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
