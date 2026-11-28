package com.yunfie.illustia.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
