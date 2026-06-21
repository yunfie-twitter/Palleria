package com.yunfie.illustia.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.data.Illust
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IllustCard(
    illust: Illust,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    highQualityImages: Boolean = true,
    showAiBadge: Boolean = true,
) {
    val preferLowDataImages = LocalPreferLowDataImages.current
    val previewUrl = remember(illust.id, highQualityImages, preferLowDataImages) {
        if (highQualityImages && !preferLowDataImages) {
            illust.previewUrl
        } else {
            illust.thumbnailUrl
        }
    }
    val isAi = showAiBadge && illust.isAi

    IllustCardImpl(
        previewUrl = previewUrl,
        title = illust.title,
        artistName = illust.artistName,
        isBookmarked = illust.isBookmarked,
        cardBadgeText = if (isAi) illust.cardBadgeText else null,
        onBookmark = onBookmark,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IllustCardImpl(
    previewUrl: String,
    title: String,
    artistName: String,
    isBookmarked: Boolean,
    cardBadgeText: String?,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = if (onLongClick != null) {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            } else null
        ),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = Color.Transparent, contentColor = MiuixTheme.colorScheme.onBackground),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

            IllustCardThumbnail(
                previewUrl = previewUrl,
                title = title,
                badgeText = cardBadgeText,
            )

            IllustCardInfo(
                title = title,
                artistName = artistName,
                isBookmarked = isBookmarked,
                onBookmark = onBookmark
            )
        }
    }
}

@Composable
private fun IllustCardThumbnail(
    previewUrl: String,
    title: String,
    badgeText: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 14.dp),
    ) {
        PixivImage(
            url = previewUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            thumbnail = true,
        )
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = Color.White,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .squircleBackground(Color.Black.copy(alpha = 0.4f), 6.dp)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun IllustCardInfo(
    title: String,
    artistName: String,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.subtitle,
            )
            Text(
                text = artistName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.footnote1,
            )
        }
        BookmarkHeartButton(
            isBookmarked = isBookmarked,
            onClick = onBookmark,
            size = 32.dp,
            iconSize = 22.dp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IllustListRow(
    illust: Illust,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val pageBadgeText = remember(illust.id) {
        if (illust.pageCount > 1) "${illust.pageCount}P" else null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (onLongClick != null) {
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    }
                } else null
            ),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(12.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer, contentColor = MiuixTheme.colorScheme.onBackground),
        pressFeedbackType = PressFeedbackType.Sink,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .squircleSurface(MiuixTheme.colorScheme.surfaceContainerHigh, 8.dp),
            ) {
                PixivImage(
                    url = illust.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    thumbnail = true,
                )
                if (pageBadgeText != null) {
                    Text(
                        text = pageBadgeText,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MiuixTheme.textStyles.footnote2,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .squircleBackground(Color.Black.copy(alpha = 0.4f), 4.dp)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = illust.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.headline2,
                )
                Text(
                    text = illust.artistName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            
            BookmarkHeartButton(
                isBookmarked = illust.isBookmarked,
                onClick = onBookmark,
                size = 40.dp,
                iconSize = 26.dp,
            )
        }
    }
}

@Composable
fun HighlightCard(illust: Illust, onClick: () -> Unit) {
    Card(
        modifier = Modifier.width(232.dp).height(128.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer, contentColor = MiuixTheme.colorScheme.onBackground),
        pressFeedbackType = PressFeedbackType.Tilt,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PixivImage(
                url = illust.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbnail = true,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)))),
            )
            Text(
                text = illust.title,
                color = Color.White,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            )
        }
    }
}

@Composable
fun IllustGrid(
    items: List<Illust>,
    hasMore: Boolean,
    emptyMessage: String,
    onBookmark: (Illust) -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onLoadMore: () -> Unit,
) {
    if (items.isEmpty()) {
        EmptyState(emptyMessage)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 12.dp, bottom = MainNavigationContentPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(items, key = { it.id }) { illust ->
                IllustListRow(illust = illust, onBookmark = { onBookmark(illust) }, onClick = { onOpenIllust(illust) })
            }
            if (hasMore) {
                item {
                    Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.illust_load_more))
                    }
                }
            }
        }
    }
}

@Composable
fun TagTile(tag: String, imageUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.aspectRatio(1f),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer, contentColor = MiuixTheme.colorScheme.onBackground),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            if (imageUrl != null) {
                PixivImage(
                    url = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    thumbnail = true,
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
            }
            Text(
                text = tag,
                color = Color.White,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
