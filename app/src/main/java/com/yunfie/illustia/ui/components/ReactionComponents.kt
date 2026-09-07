package com.yunfie.illustia.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class FollowPillStage { UNFOLLOWED, CHECK, FOLLOWED }

private enum class BookmarkButtonStage { UNBOOKMARKED, CHECK, BOOKMARKED, REMOVING }

@Composable
fun FollowPill(
    isFollowed: Boolean,
    followAnimationTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    var animationStage by remember { mutableStateOf<FollowPillStage?>(null) }
    var lastHandledTrigger by remember { mutableStateOf(followAnimationTrigger) }
    var awaitingFollowConfirmation by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticMode = LocalAppHapticMode.current

    LaunchedEffect(followAnimationTrigger) {
        if (followAnimationTrigger == lastHandledTrigger) return@LaunchedEffect
        lastHandledTrigger = followAnimationTrigger
        awaitingFollowConfirmation = true
        animationStage = FollowPillStage.CHECK
        performAppHapticFeedback(context, haptic, hapticMode)
        delay(600)
        animationStage = FollowPillStage.FOLLOWED
        awaitingFollowConfirmation = false
    }

    LaunchedEffect(isFollowed) {
        if (!isFollowed) {
            animationStage = null
            awaitingFollowConfirmation = false
        }
    }

    val stage =
        animationStage ?: if (isFollowed) {
            FollowPillStage.FOLLOWED
        } else {
            FollowPillStage.UNFOLLOWED
        }

    val isActiveFollow = stage == FollowPillStage.FOLLOWED || stage == FollowPillStage.CHECK
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier =
            modifier
                .squircleSurface(
                    color = if (isActiveFollow) scheme.primary else scheme.surfaceContainerHigh,
                    cornerRadius = 24.dp,
                ).then(
                    if (!isActiveFollow) {
                        Modifier.squircleBorder(
                            width = 1.dp,
                            color = scheme.onSurface.copy(alpha = 0.15f),
                            cornerRadius = 24.dp,
                        )
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                when (targetState) {
                    FollowPillStage.CHECK -> {
                        (scaleIn(spring(dampingRatio = 0.45f, stiffness = 380f), initialScale = 0.3f) + fadeIn(tween(160))) togetherWith
                            (scaleOut(tween(120), targetScale = 0.5f) + fadeOut(tween(120)))
                    }

                    FollowPillStage.FOLLOWED -> {
                        (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f)) togetherWith
                            (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.1f))
                    }

                    FollowPillStage.UNFOLLOWED -> {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    }
                }
            },
            label = "follow-pill-stage",
        ) { s ->
            when (s) {
                FollowPillStage.CHECK -> {
                    Icon(
                        imageVector = MiuixIcons.Ok,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                FollowPillStage.FOLLOWED -> {
                    Text(
                        text = stringResource(R.string.action_following),
                        color = scheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }

                FollowPillStage.UNFOLLOWED -> {
                    Text(
                        text = stringResource(R.string.action_follow),
                        color = scheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }
            }
        }
    }
}

@Composable
fun BookmarkHeartButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 26.dp,
    cornerRadius: Dp = size / 2,
    activeBackground: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    inactiveBackground: Color = Color.Transparent,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val hapticMode = LocalAppHapticMode.current
    var userClicked by remember { mutableStateOf(false) }
    var previousBookmarked by remember { mutableStateOf(isBookmarked) }
    var stage by remember(isBookmarked) {
        val initial =
            when {
                isBookmarked && !previousBookmarked && userClicked -> BookmarkButtonStage.CHECK
                !isBookmarked && previousBookmarked && userClicked -> BookmarkButtonStage.REMOVING
                isBookmarked -> BookmarkButtonStage.BOOKMARKED
                else -> BookmarkButtonStage.UNBOOKMARKED
            }
        previousBookmarked = isBookmarked
        mutableStateOf(initial)
    }

    LaunchedEffect(stage) {
        when (stage) {
            BookmarkButtonStage.CHECK -> {
                delay(420)
                stage = BookmarkButtonStage.BOOKMARKED
                userClicked = false
            }

            BookmarkButtonStage.REMOVING -> {
                delay(220)
                stage = BookmarkButtonStage.UNBOOKMARKED
                userClicked = false
            }

            else -> {
                Unit
            }
        }
    }

    val active = remember(stage) { stage == BookmarkButtonStage.BOOKMARKED || stage == BookmarkButtonStage.CHECK }
    val scheme = MiuixTheme.colorScheme
    IconButton(
        onClick = {
            userClicked = true
            performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.Toggle)
            onClick()
        },
        modifier = modifier.size(size),
        minWidth = size,
        minHeight = size,
        cornerRadius = cornerRadius,
        backgroundColor = if (active) activeBackground else inactiveBackground,
    ) {
        Box(contentAlignment = Alignment.Center) {
            HeartBurst(
                visible = stage == BookmarkButtonStage.CHECK,
                modifier = Modifier.size(size * 1.5f),
                color = scheme.error,
            )

            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    when (targetState) {
                        BookmarkButtonStage.CHECK -> {
                            (scaleIn(spring(dampingRatio = 0.35f, stiffness = 300f), initialScale = 0.1f) + fadeIn(tween(100))) togetherWith
                                (scaleOut(tween(100), targetScale = 0.5f) + fadeOut(tween(100)))
                        }

                        BookmarkButtonStage.BOOKMARKED -> {
                            (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f)) togetherWith
                                (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 1.1f))
                        }

                        BookmarkButtonStage.REMOVING,
                        BookmarkButtonStage.UNBOOKMARKED,
                        -> {
                            fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                        }
                    }
                },
                label = "bookmark-heart-stage",
            ) { s ->
                Icon(
                    imageVector =
                        when (s) {
                            BookmarkButtonStage.CHECK, BookmarkButtonStage.BOOKMARKED -> MiuixIcons.FavoritesFill
                            BookmarkButtonStage.REMOVING, BookmarkButtonStage.UNBOOKMARKED -> MiuixIcons.Favorites
                        },
                    contentDescription = null,
                    tint =
                        when (s) {
                            BookmarkButtonStage.CHECK, BookmarkButtonStage.BOOKMARKED -> scheme.error
                            BookmarkButtonStage.REMOVING -> scheme.onSurface.copy(alpha = 0.42f)
                            BookmarkButtonStage.UNBOOKMARKED -> scheme.onSurface
                        },
                    modifier = Modifier.size(iconSize),
                )
            }
        }
    }
}

@Composable
private fun HeartBurst(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.error,
) {
    val burstProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) tween(450) else snap(),
        label = "burstProgress",
    )

    if (burstProgress > 0f && burstProgress < 1f) {
        Box(modifier = modifier) {
            repeat(8) { i ->
                val angle = i * 45f
                val distance = 16.dp + (14.dp * burstProgress)
                val size = 4.dp * (1f - burstProgress)

                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                val rad = Math.toRadians(angle.toDouble())
                                translationX = (distance.toPx() * kotlin.math.cos(rad)).toFloat()
                                translationY = (distance.toPx() * kotlin.math.sin(rad)).toFloat()
                                alpha = (1f - burstProgress) * 1.5f
                                scaleX = 1f - burstProgress
                                scaleY = 1f - burstProgress
                            }.size(size)
                            .background(color, androidx.compose.foundation.shape.CircleShape),
                )
            }
        }
    }
}
