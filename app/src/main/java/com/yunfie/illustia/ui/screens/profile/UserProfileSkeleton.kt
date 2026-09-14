package com.yunfie.illustia.ui.screens.profile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.ui.components.HeaderOverlayIcon
import com.yunfie.illustia.ui.components.IllustCardSkeleton
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.ProfileGridHorizontalSpacing
import com.yunfie.illustia.ui.components.ProfileGridVerticalSpacing
import com.yunfie.illustia.ui.components.adaptiveProfileGridColumns
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val BANNER_HEIGHT = 236.dp
private val AVATAR_SIZE = 104.dp
private val AVATAR_BORDER_WIDTH = 4.dp
private val HEADER_INFO_OFFSET_Y = (-48).dp
private const val SHIMMER_DURATION_MILLIS = 1250
private const val SKELETON_ITEM_COUNT = 6
private const val SKELETON_SHIMMER_GRADIENT_WIDTH = 0.52f
private const val SKELETON_SHIMMER_START_X_INITIAL = -1f
private const val SKELETON_SHIMMER_START_X_TARGET = 2f

@Composable
fun UserProfileSkeletonScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.background,
) {
    PredictiveBackGestureHandler(onBack = onBack)

    val transition = rememberInfiniteTransition(label = "userProfileSkeletonTransition")
    val shimmer =
        transition.animateFloat(
            initialValue = SKELETON_SHIMMER_START_X_INITIAL,
            targetValue = SKELETON_SHIMMER_START_X_TARGET,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = SHIMMER_DURATION_MILLIS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "userProfileSkeletonShimmer",
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
                            end = Offset(startX + size.width * SKELETON_SHIMMER_GRADIENT_WIDTH, size.height),
                        ),
                )
            }
        }

    Box(modifier = modifier.fillMaxSize().background(backgroundColor)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(adaptiveProfileGridColumns()),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(ProfileGridHorizontalSpacing),
            verticalArrangement = Arrangement.spacedBy(ProfileGridVerticalSpacing),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "skeleton_banner") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(BANNER_HEIGHT)
                            .then(shimmerModifier),
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }, contentType = "skeleton_profile_info") {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .offset(y = HEADER_INFO_OFFSET_Y),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(AVATAR_SIZE)
                                    .clip(CircleShape)
                                    .border(BorderStroke(AVATAR_BORDER_WIDTH, backgroundColor), CircleShape)
                                    .then(shimmerModifier),
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 88.dp, height = 36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .then(shimmerModifier),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 160.dp, height = 24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .then(shimmerModifier),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 96.dp, height = 14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(shimmerModifier),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.82f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(shimmerModifier),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(shimmerModifier),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        repeat(3) {
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .then(shimmerModifier),
                            )
                        }
                    }
                }
            }
            items(SKELETON_ITEM_COUNT, contentType = { "illust_skeleton" }) {
                IllustCardSkeleton(modifier = Modifier.padding(horizontal = 4.dp))
            }
        }

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderOverlayIcon(
                icon = MiuixIcons.Back,
                onClick = onBack,
                backgroundColor = Color.White.copy(alpha = 0.92f),
                contentColor = Color.Black,
            )
        }
    }
}
