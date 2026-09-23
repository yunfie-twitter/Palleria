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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.yunfie.illustia.ui.components.miuixClickable
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun NovelCardSkeleton(
    modifier: Modifier = Modifier,
    shimmerValue: Float? = null,
) {
    val defaultTransition = rememberInfiniteTransition(label = "novelSkeleton")
    val defaultAnim by defaultTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "novelSkeletonShimmer",
    )
    val shimmerFloat = shimmerValue ?: defaultAnim
    val base = MiuixTheme.colorScheme.surfaceContainer
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val shimmerColors = remember(base, highlight) { listOf(base, highlight, base) }
    val shimmerModifier =
        Modifier.drawWithCache {
            onDrawBehind {
                val startX = shimmerFloat * size.width
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

    ElevatedPanel(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(104.dp)
                        .aspectRatio(0.76f)
                        .clip(RoundedCornerShape(18.dp))
                        .then(shimmerModifier),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(18.dp)
                            .clip(CircleShape)
                            .then(shimmerModifier),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.45f)
                            .height(14.dp)
                            .clip(CircleShape)
                            .then(shimmerModifier),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.9f)
                            .height(14.dp)
                            .clip(CircleShape)
                            .then(shimmerModifier),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .width(60.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .then(shimmerModifier),
                    )
                    Box(
                        modifier =
                            Modifier
                                .width(70.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .then(shimmerModifier),
                    )
                }
            }
        }
    }
}

@Composable
internal fun NovelCard(
    novel: NovelPreview,
    progress: com.yunfie.illustia.models.NovelReadingProgress? = null,
    onClick: () -> Unit,
    onStatusToggle: (() -> Unit)? = null,
) {
    val cardPreferences = LocalArtworkCardPreferences.current
    ElevatedPanel(modifier = Modifier.fillMaxWidth().miuixClickable(onClick = onClick)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(104.dp)
                        .aspectRatio(0.76f)
                        .clip(RoundedCornerShape(18.dp)),
            ) {
                PixivImage(
                    url = novel.coverUrl,
                    contentDescription = novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    crossfade = true,
                    thumbnail = true,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                                ),
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
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = novel.title,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = novel.userName,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text =
                        if (novel.caption.isBlank()) {
                            stringResource(R.string.novel_length_label, novel.textLength)
                        } else {
                            novel.caption
                        },
                    style = MiuixTheme.textStyles.footnote1,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NovelMetaPill(text = stringResource(R.string.novel_page_count, novel.pageCount))
                    NovelMetaPill(text = stringResource(R.string.novel_bookmark_count, novel.totalBookmarks))
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
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovelMetaPill(
    text: String,
    backgroundColor: Color = MiuixTheme.colorScheme.surfaceContainerHighest,
    textColor: Color = MiuixTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(backgroundColor)
                .then(
                    if (onClick != null) {
                        Modifier.miuixClickable(pressedScale = 0.94f, haptic = true, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 10.dp, vertical = 6.dp),
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
