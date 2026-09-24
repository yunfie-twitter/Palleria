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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun rememberSkeletonShimmer(externalValue: Float? = null): Modifier {
    val defaultTransition = rememberInfiniteTransition(label = "skeletonTransition")
    val defaultAnim by defaultTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "skeletonShimmer",
    )
    val shimmerFloat = externalValue ?: defaultAnim
    val base = MiuixTheme.colorScheme.surfaceContainer
    val highlight = MiuixTheme.colorScheme.surfaceContainerHigh
    val shimmerColors = remember(base, highlight) { listOf(base, highlight, base) }
    return Modifier.drawWithCache {
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
}

@Composable
fun UserResultCardSkeleton(
    modifier: Modifier = Modifier,
    shimmerValue: Float? = null,
) {
    val shimmerModifier = rememberSkeletonShimmer(shimmerValue)
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors =
            CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(3) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(118.dp)
                                .then(shimmerModifier),
                    )
                }
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .then(shimmerModifier),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.55f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .then(shimmerModifier),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(0.35f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .then(shimmerModifier),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .width(68.dp)
                            .height(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .then(shimmerModifier),
                )
            }
        }
    }
}

@Composable
fun CommentItemSkeleton(
    modifier: Modifier = Modifier,
    shimmerValue: Float? = null,
) {
    val shimmerModifier = rememberSkeletonShimmer(shimmerValue)
    ElevatedPanel(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(shimmerModifier),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .width(96.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .then(shimmerModifier),
                    )
                    Box(
                        modifier =
                            Modifier
                                .width(64.dp)
                                .height(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .then(shimmerModifier),
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(shimmerModifier),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .then(shimmerModifier),
            )
        }
    }
}

@Composable
fun NotificationCardSkeleton(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    shimmerValue: Float? = null,
) {
    val shimmerModifier = rememberSkeletonShimmer(shimmerValue)
    Card(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = if (compact) 14.dp else 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .then(shimmerModifier),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.85f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .then(shimmerModifier),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.55f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .then(shimmerModifier),
                )
            }
        }
    }
}
