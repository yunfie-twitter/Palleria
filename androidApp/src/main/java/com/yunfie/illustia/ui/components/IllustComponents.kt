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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.illust_load_more
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

private val IllustCardCornerShape = RoundedCornerShape(14.dp)
private val IllustRowCornerShape = RoundedCornerShape(8.dp)

/** Read the shared animation only during drawing, not during composition. */
@Composable
fun IllustCardSkeleton(
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
fun rememberIllustSkeletonShimmer(): State<Float> {
    // LocalFastScrolling holds the State (not the value) so providing it never invalidates
    // composition. Reading .value here would recompose every skeleton each time fast-scroll
    // flips and would also destroy/recreate the InfiniteTransition on the branch change.
    // Instead keep one stable transition and gate only the emitted value at read time.
    val fastScrolling = LocalFastScrolling.current
    val transition = rememberInfiniteTransition(label = "illustSkeleton")
    val animated =
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
    return remember(animated, fastScrolling) {
        object : State<Float> {
            override val value: Float
                get() = if (fastScrolling.value) 0f else animated.value
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
    // 1つの InfiniteTransition をすべてのスケルトンで共有することで、
    // 複数スケルトン同時表示時のアニメーション計算コストを削減する。
    val shimmerValue = rememberIllustSkeletonShimmer()
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        userScrollEnabled = false,
    ) {
        items(itemCount, contentType = { "illust_skeleton" }) {
            IllustCardSkeleton(shimmerValue = shimmerValue)
        }
    }
}

@Composable
fun HighlightCard(
    illust: Illust,
    onClick: () -> Unit,
) {
    val cardPreferences = LocalArtworkCardPreferences.current
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
            CoilPixivImage(
                url = illust.previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                thumbnail = true,
            )
            val badgeText = illust.ageRestrictionBadgeText
            if (cardPreferences.showR18Badge && badgeText != null) {
                Text(
                    text = badgeText,
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
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(64.dp)
                        .cachedVerticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IllustListRow(
    illust: Illust,
    onBookmark: () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onBookmarkLongClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticMode = LocalAppHapticMode.current
    val cardPreferences = LocalArtworkCardPreferences.current
    val pageBadgeText =
        remember(illust.id, illust.pageCount) {
            if (illust.pageCount > 1) "${illust.pageCount}P" else null
        }
    val ageRestrictionBadgeText =
        remember(illust.id, illust.isR18, illust.isR18G, cardPreferences.showR18Badge) {
            if (cardPreferences.showR18Badge) illust.ageRestrictionBadgeText else null
        }

    val baseModifier = Modifier.fillMaxWidth().let { artworkDesktopMenuDecoration(illust, it) }
    val finalModifier =
        if (cardPreferences.doubleTapToBookmark) {
            baseModifier
                .clip(RoundedCornerShape(18.dp))
                .combinedClickable(
                    onClick = onClick,
                    onDoubleClick = {
                        performAppHapticFeedback(context, haptic, hapticMode)
                        onBookmark()
                    },
                    onLongClick =
                        if (onLongClick != null) {
                            {
                                performAppHapticFeedback(context, haptic, hapticMode)
                                onLongClick()
                            }
                        } else {
                            null
                        },
                )
        } else {
            baseModifier
        }

    Card(
        modifier = finalModifier,
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(12.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onBackground,
            ),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = if (cardPreferences.doubleTapToBookmark) null else onClick,
        onLongPress =
            if (!cardPreferences.doubleTapToBookmark && onLongClick != null) {
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
                        .clip(IllustRowCornerShape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
            ) {
                CoilPixivImage(
                    url = illust.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    thumbnail = true,
                )
                if (ageRestrictionBadgeText != null) {
                    Text(
                        text = ageRestrictionBadgeText,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        style = MiuixTheme.textStyles.footnote2,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .squircleBackground(Color(0xFFFA383E), 4.dp)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
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

            if (cardPreferences.showBookmarkButton) {
                BookmarkHeartButton(
                    isBookmarked = illust.isBookmarked,
                    onClick = onBookmark,
                    onLongClick = onBookmarkLongClick,
                    size = 40.dp,
                    iconSize = 26.dp,
                )
            }
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
    state: LazyGridState = rememberLazyGridState(),
) {
    if (illusts.isEmpty()) {
        EmptyState(emptyMessage)
    } else {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(columns.coerceAtLeast(1)),
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(illusts, key = { it.id }, contentType = { "illust_card" }) { illust ->
                val onClick = remember(illust, onOpenIllust) { { onOpenIllust(illust) } }
                val onBookmarkClick = remember(illust, onBookmark) { { onBookmark(illust) } }
                IllustCard(
                    illust = illust,
                    modifier = animatedGridPlacement(),
                    onBookmark = onBookmarkClick,
                    onClick = onClick,
                    showBookmarkButton = showBookmarkButton,
                    isMutedByTag = isMutedByTag(illust),
                )
            }
            if (hasMore && onLoadMore != null) {
                item(key = "illust_load_more", contentType = "load_more") {
                    Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(stringResource(Res.string.illust_load_more))
                    }
                }
            }
        }
    }
}
