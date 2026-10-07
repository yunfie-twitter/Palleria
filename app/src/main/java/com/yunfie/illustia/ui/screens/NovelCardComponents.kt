package com.yunfie.illustia.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.LocalArtworkCardPreferences
import com.yunfie.illustia.ui.components.PixivImage
import com.yunfie.illustia.ui.components.cachedVerticalGradient
import com.yunfie.illustia.ui.components.miuixClickable
import com.yunfie.illustia.ui.components.rememberIllustSkeletonShimmer
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
internal fun NovelCardSkeleton(
    modifier: Modifier = Modifier,
    shimmerValue: State<Float>? = null,
) {
    val shimmer = shimmerValue ?: rememberIllustSkeletonShimmer()
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(14.dp))
                    .then(shimmerModifier),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.85f)
                    .height(16.dp)
                    .clip(CircleShape)
                    .then(shimmerModifier),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(12.dp)
                    .clip(CircleShape)
                    .then(shimmerModifier),
        )
    }
}

@Composable
internal fun NovelCard(
    novel: NovelPreview,
    progress: com.yunfie.illustia.models.NovelReadingProgress? = null,
    onClick: () -> Unit,
    onStatusToggle: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cardPreferences = LocalArtworkCardPreferences.current
    Card(
        modifier = modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Hand),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = Color.Transparent,
                contentColor = MiuixTheme.colorScheme.onBackground,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.72f)
                        .clip(RoundedCornerShape(14.dp)),
            ) {
                PixivImage(
                    url = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    crossfade = true,
                    thumbnail = true,
                    maxDecodeDimensionPx = 384,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .cachedVerticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                            ),
                )
                if (cardPreferences.showR18Badge && novel.ageRestrictionBadgeText != null) {
                    Text(
                        text = novel.ageRestrictionBadgeText,
                        color = Color.White,
                        style = MiuixTheme.textStyles.footnote2,
                        fontWeight = FontWeight.Black,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .squircleBackground(Color(0xFFFA383E), 6.dp)
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.novel_page_count, novel.pageCount),
                    color = Color.White,
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .squircleBackground(Color.Black.copy(alpha = 0.6f), 6.dp)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                )

                if (progress != null && progress.status != com.yunfie.illustia.models.NovelReadingStatus.Unread) {
                    val (statusText, statusBg, statusFg) =
                        when (progress.status) {
                            com.yunfie.illustia.models.NovelReadingStatus.Reading -> {
                                Triple(
                                    if (progress.lastReadPage > 0) {
                                        stringResource(R.string.novel_resume_reading, progress.lastReadPage + 1, progress.totalPages)
                                    } else {
                                        stringResource(R.string.novel_status_reading)
                                    },
                                    MiuixTheme.colorScheme.primaryContainer,
                                    MiuixTheme.colorScheme.onPrimaryContainer,
                                )
                            }

                            com.yunfie.illustia.models.NovelReadingStatus.Completed -> {
                                Triple(
                                    stringResource(R.string.novel_status_completed),
                                    MiuixTheme.colorScheme.primary,
                                    MiuixTheme.colorScheme.onPrimary,
                                )
                            }

                            com.yunfie.illustia.models.NovelReadingStatus.Later -> {
                                Triple(
                                    stringResource(R.string.novel_status_later),
                                    MiuixTheme.colorScheme.secondaryContainer,
                                    MiuixTheme.colorScheme.onSecondaryContainer,
                                )
                            }

                            com.yunfie.illustia.models.NovelReadingStatus.Unread -> {
                                Triple("", Color.Transparent, Color.Transparent)
                            }
                        }
                    if (statusText.isNotEmpty()) {
                        NovelMetaPill(
                            text = statusText,
                            backgroundColor = statusBg,
                            textColor = statusFg,
                            onClick = onStatusToggle,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 3.dp),
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (cardPreferences.showTitle) {
                    Text(
                        text = novel.title,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (cardPreferences.showArtist) {
                        Text(
                            text = novel.userName,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (cardPreferences.showBookmarkCount && novel.totalBookmarks > 0) {
                        Text(
                            text = "♥ ${novel.totalBookmarks}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovelMetaPill(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.surfaceContainerHighest,
    textColor: Color = MiuixTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(999.dp))
                .background(backgroundColor)
                .then(
                    if (onClick != null) {
                        Modifier.miuixClickable(pressedScale = 0.94f, haptic = true, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}
