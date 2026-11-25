package com.yunfie.illustia.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.ui.components.AppHapticEffect
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 作品カード。グリッドのホットパスに置かれるため、
 * サムネイルは [PixivImage] 契約経由で描画する。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IllustCard(
    illust: Illust,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onBookmarkLongClick: (() -> Unit)? = null,
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
    val ageRestrictionBadgeText =
        remember(illust.id, illust.isR18, illust.isR18G, cardPreferences.showR18Badge) {
            if (cardPreferences.showR18Badge) illust.ageRestrictionBadgeText else null
        }

    IllustCardImpl(
        previewUrl = previewUrl,
        title = illust.title,
        illustId = illust.id,
        artistName = illust.artistName,
        tags = illust.tags,
        totalBookmarks = illust.totalBookmarks,
        isBookmarked = illust.isBookmarked,
        cardBadgeText = cardBadgeText,
        ageRestrictionBadgeText = ageRestrictionBadgeText,
        onBookmark = onBookmark,
        onBookmarkLongClick = onBookmarkLongClick,
        onClick = onClick,
        onLongClick = onLongClick,
        showBookmarkButton = showBookmarkButton && cardPreferences.showBookmarkButton,
        modifier = modifier.artworkCardDecoration(illust),
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
    illustId: Long,
    artistName: String,
    tags: List<String>,
    totalBookmarks: Int,
    isBookmarked: Boolean,
    cardBadgeText: String?,
    ageRestrictionBadgeText: String?,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    showBookmarkButton: Boolean,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isMutedByTag: Boolean,
    preferences: ArtworkCardPreferences,
    onBookmarkLongClick: (() -> Unit)? = null,
) {
    val performHaptic = rememberAppHapticPerformer()
    val cardModifier =
        if (isSelected) {
            modifier.border(2.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(14.dp))
        } else {
            modifier
        }
    val finalModifier =
        if (preferences.doubleTapToBookmark) {
            cardModifier
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(
                    onClick = onClick,
                    onDoubleClick = {
                        performHaptic(AppHapticEffect.Click)
                        onBookmark()
                    },
                    onLongClick =
                        if (onLongClick != null) {
                            {
                                performHaptic(AppHapticEffect.Click)
                                onLongClick()
                            }
                        } else {
                            null
                        },
                )
        } else {
            cardModifier
        }
    Card(
        modifier = finalModifier,
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = Color.Transparent, contentColor = MiuixTheme.colorScheme.onBackground),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = if (preferences.doubleTapToBookmark) null else onClick,
        onLongPress =
            if (!preferences.doubleTapToBookmark && onLongClick != null) {
                {
                    performHaptic(AppHapticEffect.Click)
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
                    illustId = illustId,
                    badgeText = cardBadgeText,
                    ageRestrictionBadgeText = ageRestrictionBadgeText,
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
                        onBookmarkLongClick = onBookmarkLongClick,
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
    illustId: Long,
    badgeText: String?,
    ageRestrictionBadgeText: String?,
    isMutedByTag: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .sharedArtworkElement(illustId)
                .clip(IllustCardCornerShape)
                .background(MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PixivImage(
                url = previewUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbnail = true,
            )
            // A scrim hides muted artwork without Modifier.blur's per-item RenderEffect, which
            // would force an offscreen blur pass for every muted card in the scrolling grid.
            if (isMutedByTag) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            }
        }
        if (ageRestrictionBadgeText != null) {
            Text(
                text = ageRestrictionBadgeText,
                color = Color.White,
                style = MiuixTheme.textStyles.footnote2,
                fontWeight = FontWeight.Black,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .squircleBackground(Color(0xFFFA383E), 6.dp)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
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
    onBookmarkLongClick: (() -> Unit)? = null,
) {
    // These are shown per grid cell and would otherwise be re-built on every recomposition,
    // allocating a Formatter/String per card in the hot scroll path.
    val tagLine =
        remember(tags) {
            when {
                tags.size >= 2 -> "#${tags[0]}  #${tags[1]}"
                tags.size == 1 -> "#${tags[0]}"
                else -> ""
            }
        }
    val bookmarkLine = remember(totalBookmarks) { "♥ ${formatCompactCount(totalBookmarks)}" }
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
                            text = tagLine,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                    }
                    if (preferences.showBookmarkCount) {
                        Text(
                            text = bookmarkLine,
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
            LocalIllustCardBookmarkButtonRenderer.current?.Render(
                isBookmarked = isBookmarked,
                onClick = onBookmark,
                modifier = Modifier,
                onLongClick = onBookmarkLongClick,
                size = 32.dp,
                iconSize = 22.dp,
            )
        }
    }
}

private const val COMPACT_COUNT_THOUSAND = 1_000
private const val COMPACT_COUNT_MILLION = 1_000_000
private const val COMPACT_COUNT_TENTH_THOUSAND = 100
private const val COMPACT_COUNT_TENTH_MILLION = 100_000

private fun formatCompactCount(value: Int): String =
    when {
        value >= COMPACT_COUNT_MILLION -> {
            "${value / COMPACT_COUNT_MILLION}.${(value % COMPACT_COUNT_MILLION) / COMPACT_COUNT_TENTH_MILLION}M"
        }

        value >= COMPACT_COUNT_THOUSAND -> {
            "${value / COMPACT_COUNT_THOUSAND}.${(value % COMPACT_COUNT_THOUSAND) / COMPACT_COUNT_TENTH_THOUSAND}K"
        }

        else -> {
            value.toString()
        }
    }
