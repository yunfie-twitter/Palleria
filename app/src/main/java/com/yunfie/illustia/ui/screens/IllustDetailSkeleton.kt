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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val SHIMMER_DURATION_MILLIS = 1250
private const val SKELETON_SHIMMER_GRADIENT_WIDTH = 0.52f
private const val SKELETON_SHIMMER_START_X_INITIAL = -1f
private const val SKELETON_SHIMMER_START_X_TARGET = 2f

@Composable
fun IllustDetailSkeletonScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.background,
) {
    PredictiveBackGestureHandler(onBack = onBack)

    val transition = rememberInfiniteTransition(label = "illustDetailSkeletonTransition")
    val shimmer =
        transition.animateFloat(
            initialValue = SKELETON_SHIMMER_START_X_INITIAL,
            targetValue = SKELETON_SHIMMER_START_X_TARGET,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = SHIMMER_DURATION_MILLIS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "illustDetailSkeletonShimmer",
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

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = "",
                navigationIcon = {
                    HeaderIcon(
                        icon = MiuixIcons.Back,
                        onClick = onBack,
                    )
                },
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 8.dp,
                    bottom = 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(contentType = "skeleton_preview") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.75f)
                            .clip(RoundedCornerShape(16.dp))
                            .then(shimmerModifier),
                )
            }
            item(contentType = "skeleton_author") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .then(shimmerModifier),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 180.dp, height = 20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(shimmerModifier),
                        )
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 100.dp, height = 14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .then(shimmerModifier),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier =
                            Modifier
                                .size(width = 72.dp, height = 32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .then(shimmerModifier),
                    )
                }
            }
            item(contentType = "skeleton_tags") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(3) {
                        Box(
                            modifier =
                                Modifier
                                    .size(width = 70.dp, height = 26.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(shimmerModifier),
                        )
                    }
                }
            }
        }
    }
}
