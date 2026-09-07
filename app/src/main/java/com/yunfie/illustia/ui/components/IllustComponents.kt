package com.yunfie.illustia.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun IllustCardSkeleton(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "illustSkeleton")
    val shimmer =
        transition.animateFloat(
            initialValue = -1f,
            targetValue = 2f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "illustSkeletonShimmer",
        )
    val base = MiuixTheme.colorScheme.surfaceContainer
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val shimmerColors = remember(base, highlight) { listOf(base, highlight, base) }
    val shimmerModifier =
        Modifier.drawWithCache {
            onDrawBehind {
                val startX = shimmer.value * size.width
                drawRect(
                    brush =
                        Brush.linearGradient(
                            colors = shimmerColors,
                            start = Offset(startX, 0f),
                            end = Offset(startX + size.width * 0.52f, size.height),
                        ),
                )
            }
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .clip(RoundedCornerShape(14.dp))
                .then(shimmerModifier),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.82f)
                        .height(14.dp)
                        .clip(CircleShape)
                        .then(shimmerModifier),
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.56f)
                        .height(10.dp)
                        .clip(CircleShape)
                        .then(shimmerModifier),
                )
            }
            Box(Modifier.size(28.dp).clip(CircleShape).then(shimmerModifier))
        }
    }
}

@Composable
fun IllustGridSkeleton(
    columns: Int,
    modifier: Modifier = Modifier,
    itemCount: Int = 6,
    contentPadding: PaddingValues = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        items(itemCount, contentType = { "illust_skeleton" }) {
            IllustCardSkeleton()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IllustCard(
    illust: Illust,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    highQualityImages: Boolean = true,
    showAiBadge: Boolean = true,
    showBookmarkButton: Boolean = true,
    isMutedByTag: Boolean = false,
) {
    val cardPreferences = LocalArtworkCardPreferences.current
    val preferLowDataImages = LocalPreferLowDataImages.current
    val previewUrl =
        remember(illust.id, highQualityImages, preferLowDataImages) {
            if (highQualityImages && !preferLowDataImages) {
                illust.previewUrl
            } else {
                illust.thumbnailUrl
            }
        }
    val cardBadgeText =
        remember(illust.id, showAiBadge, cardPreferences.showAiBadge) {
            if (illust.isAi && (!showAiBadge || !cardPreferences.showAiBadge)) null else illust.cardBadgeText
        }

    IllustCardImpl(
        previewUrl = previewUrl,
        title = illust.title,
        artistName = illust.artistName,
        tags = illust.tags,
        totalBookmarks = illust.totalBookmarks,
        isBookmarked = illust.isBookmarked,
        cardBadgeText = cardBadgeText,
        onBookmark = onBookmark,
        onClick = onClick,
        onLongClick = onLongClick,
        showBookmarkButton = showBookmarkButton,
        modifier = modifier,
        isSelected = isSelected,
        isMutedByTag = isMutedByTag,
        preferences = cardPreferences,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IllustCardImpl(
    previewUrl: String,
    title: String,
    artistName: String,
    tags: List<String>,
    totalBookmarks: Int,
    isBookmarked: Boolean,
    cardBadgeText: String?,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    showBookmarkButton: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isMutedByTag: Boolean,
    preferences: ArtworkCardPreferences,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticMode = LocalAppHapticMode.current
    val cardModifier =
        if (isSelected) {
            modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(14.dp))
        } else {
            modifier
        }
    Card(
        modifier = cardModifier,
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = Color.Transparent, contentColor = MiuixTheme.colorScheme.onBackground),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
        onLongPress =
            if (onLongClick != null) {
                {
                    performAppHapticFeedback(context, haptic, hapticMode)
                    onLongClick()
                }
            } else {
                null
            },
    ) {
        Box {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IllustCardThumbnail(
                    previewUrl = previewUrl,
                    title = title,
                    badgeText = cardBadgeText,
                    isMutedByTag = isMutedByTag,
                )

                if (
                    preferences.showTitle || preferences.showArtist || preferences.showTags ||
                    preferences.showBookmarkCount || showBookmarkButton
                ) {
                    IllustCardInfo(
                        title = title,
                        artistName = artistName,
                        tags = tags,
                        totalBookmarks = totalBookmarks,
                        isBookmarked = isBookmarked,
                        onBookmark = onBookmark,
                        showBookmarkButton = showBookmarkButton,
                        preferences = preferences,
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.08f)),
                )
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MiuixTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✓",
                        color = Color.White,
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Black,
                    )
                }
            }
        }
    }
}

@Composable
private fun IllustCardThumbnail(
    previewUrl: String,
    title: String,
    badgeText: String?,
    isMutedByTag: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 14.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize().then(if (isMutedByTag) Modifier.blur(12.dp) else Modifier)) {
            PixivImage(
                url = previewUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbnail = true,
            )
        }
        if (badgeText != null) {
            Text(
                text = badgeText,
                color = Color.White,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier
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
    tags: List<String>,
    totalBookmarks: Int,
    isBookmarked: Boolean,
    onBookmark: () -> Unit,
    showBookmarkButton: Boolean,
    preferences: ArtworkCardPreferences,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (preferences.showTitle) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onBackground,
                    style = MiuixTheme.textStyles.subtitle,
                )
            }
            if (preferences.showArtist) {
                Text(
                    text = artistName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            if (preferences.showTags || preferences.showBookmarkCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (preferences.showTags && tags.isNotEmpty()) {
                        Text(
                            text = tags.take(2).joinToString("  ") { "#$it" },
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                    }
                    if (preferences.showBookmarkCount) {
                        Text(
                            text = "♥ ${formatCompactCount(totalBookmarks)}",
                            maxLines = 1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        if (showBookmarkButton) {
            BookmarkHeartButton(
                isBookmarked = isBookmarked,
                onClick = onBookmark,
                size = 32.dp,
                iconSize = 22.dp,
            )
        }
    }
}

private fun formatCompactCount(value: Int): String =
    when {
        value >= 1_000_000 -> "%.1fM".format(value / 1_000_000f)
        value >= 1_000 -> "%.1fK".format(value / 1_000f)
        else -> value.toString()
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IllustListRow(
    illust: Illust,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticMode = LocalAppHapticMode.current
    val pageBadgeText =
        remember(illust.id) {
            if (illust.pageCount > 1) "${illust.pageCount}P" else null
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(12.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onBackground,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
        onLongPress =
            if (onLongClick != null) {
                {
                    performAppHapticFeedback(context, haptic, hapticMode)
                    onLongClick()
                }
            } else {
                null
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier =
                    Modifier
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
                        modifier =
                            Modifier
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
fun HighlightCard(
    illust: Illust,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.width(232.dp).height(128.dp),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onBackground,
            ),
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
                modifier =
                    Modifier
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
    illusts: List<Illust>,
    emptyMessage: String,
    onOpenIllust: (Illust) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    onBookmark: (Illust) -> Unit = {},
    hasMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    showBookmarkButton: Boolean = true,
    isMutedByTag: (Illust) -> Boolean = { false },
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = MainNavigationContentPadding),
) {
    if (illusts.isEmpty()) {
        EmptyState(emptyMessage)
    } else {
        LazyVerticalGrid(
            state = rememberLazyGridState(),
            columns = GridCells.Fixed(columns.coerceAtLeast(1)),
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(illusts, key = { it.id }, contentType = { "illust_card" }) { illust ->
                IllustCard(
                    illust = illust,
                    onBookmark = { onBookmark(illust) },
                    onClick = { onOpenIllust(illust) },
                    showBookmarkButton = showBookmarkButton,
                    isMutedByTag = isMutedByTag(illust),
                )
            }
            if (hasMore && onLoadMore != null) {
                item {
                    Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(R.string.illust_load_more))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TagTile(
    tag: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current
    Card(
        modifier =
            modifier
                .aspectRatio(1f)
                .combinedClickable(
                    onClick = onClick,
                    role = Role.Button,
                    onLongClick =
                        onLongClick?.let { longClick ->
                            {
                                performAppHapticFeedback(context, haptic, hapticMode)
                                longClick()
                            }
                        },
                ),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onBackground,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
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
