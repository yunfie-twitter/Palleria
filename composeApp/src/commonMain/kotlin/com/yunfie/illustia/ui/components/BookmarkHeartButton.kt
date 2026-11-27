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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_bookmark
import com.yunfie.illustia.resources.action_remove_bookmark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class BookmarkButtonStage { UNBOOKMARKED, CHECK, PRIVATE_CHECK, BOOKMARKED, REMOVING }

@Composable
fun BookmarkHeartButton(
    isBookmarked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = 40.dp,
    iconSize: Dp = 26.dp,
    cornerRadius: Dp = size / 2,
    activeBackground: Color = MiuixTheme.colorScheme.surfaceContainerHigh,
    inactiveBackground: Color = Color.Transparent,
    hapticBurstEnabled: Boolean = true,
) {
    val haptic = rememberAppHapticPerformer()
    var userClicked by remember { mutableStateOf(false) }
    var privateRequested by remember { mutableStateOf(false) }
    val actionLabel = stringResource(if (isBookmarked) Res.string.action_remove_bookmark else Res.string.action_bookmark)
    var previousBookmarked by remember { mutableStateOf(isBookmarked) }
    var stage by remember(isBookmarked) {
        val initial =
            when {
                isBookmarked && !previousBookmarked && userClicked -> {
                    if (privateRequested) BookmarkButtonStage.PRIVATE_CHECK else BookmarkButtonStage.CHECK
                }

                !isBookmarked && previousBookmarked && userClicked -> {
                    BookmarkButtonStage.REMOVING
                }

                isBookmarked -> {
                    BookmarkButtonStage.BOOKMARKED
                }

                else -> {
                    BookmarkButtonStage.UNBOOKMARKED
                }
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

            BookmarkButtonStage.PRIVATE_CHECK -> {
                delay(550)
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

    val active =
        remember(stage) {
            stage == BookmarkButtonStage.BOOKMARKED || stage == BookmarkButtonStage.CHECK || stage == BookmarkButtonStage.PRIVATE_CHECK
        }
    val scheme = MiuixTheme.colorScheme

    val buttonModifier =
        modifier
            .size(size)
            .squircleSurface(if (active) activeBackground else inactiveBackground, cornerRadius)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = actionLabel }
            .combinedClickable(
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = {
                    userClicked = true
                    privateRequested = false
                    val effect =
                        if (hapticBurstEnabled && !isBookmarked) {
                            AppHapticEffect.BookmarkBurst
                        } else {
                            AppHapticEffect.Toggle
                        }
                    haptic(effect)
                    onClick()
                },
                onLongClick =
                    if (onLongClick != null) {
                        {
                            userClicked = true
                            privateRequested = true
                            val effect =
                                if (hapticBurstEnabled) {
                                    AppHapticEffect.BookmarkBurst
                                } else {
                                    AppHapticEffect.Success
                                }
                            haptic(effect)
                            onLongClick()
                        }
                    } else {
                        null
                    },
            )

    Box(
        modifier = buttonModifier,
        contentAlignment = Alignment.Center,
    ) {
        HeartBurst(
            visible = stage == BookmarkButtonStage.CHECK || stage == BookmarkButtonStage.PRIVATE_CHECK,
            modifier = Modifier.size(size * 1.6f),
            color = scheme.error,
            hapticBurstEnabled = hapticBurstEnabled,
        )

        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                when (targetState) {
                    BookmarkButtonStage.CHECK -> {
                        (scaleIn(spring(dampingRatio = 0.35f, stiffness = 300f), initialScale = 0.1f) + fadeIn(tween(100))) togetherWith
                            (scaleOut(tween(100), targetScale = 0.5f) + fadeOut(tween(100)))
                    }

                    BookmarkButtonStage.PRIVATE_CHECK -> {
                        (scaleIn(spring(dampingRatio = 0.35f, stiffness = 320f), initialScale = 0.1f) + fadeIn(tween(100))) togetherWith
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
                        BookmarkButtonStage.PRIVATE_CHECK -> MiuixIcons.Lock
                        BookmarkButtonStage.CHECK, BookmarkButtonStage.BOOKMARKED -> MiuixIcons.FavoritesFill
                        BookmarkButtonStage.REMOVING, BookmarkButtonStage.UNBOOKMARKED -> MiuixIcons.Favorites
                    },
                contentDescription = null,
                tint =
                    when (s) {
                        BookmarkButtonStage.PRIVATE_CHECK -> scheme.primary
                        BookmarkButtonStage.CHECK, BookmarkButtonStage.BOOKMARKED -> scheme.error
                        BookmarkButtonStage.REMOVING -> scheme.onSurface.copy(alpha = 0.42f)
                        BookmarkButtonStage.UNBOOKMARKED -> scheme.onSurface
                    },
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun HeartBurst(
    visible: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.error,
    hapticBurstEnabled: Boolean = true,
) {
    val burstProgress =
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = if (visible) tween(450) else snap(),
            label = "burstProgress",
        )

    if (visible || (burstProgress.value > 0f && burstProgress.value < 1f)) {
        Spacer(
            modifier =
                modifier.drawBehind {
                    val progress = burstProgress.value
                    if (progress in 0.001f..0.999f) {
                        val baseDistance = 16.dp.toPx()
                        val addDistance = 14.dp.toPx() * progress
                        val distance = baseDistance + addDistance
                        val radius = (2.2.dp * (1f - progress)).toPx()
                        val alpha = ((1f - progress) * 1.5f).coerceIn(0f, 1f)
                        val burstColor = color.copy(alpha = color.alpha * alpha)
                        val centerX = size.width / 2f
                        val centerY = size.height / 2f

                        // Main ring particles
                        for (i in 0 until 8) {
                            val angle = i * 45f
                            val rad = Math.toRadians(angle.toDouble())
                            val cx = centerX + (distance * kotlin.math.cos(rad)).toFloat()
                            val cy = centerY + (distance * kotlin.math.sin(rad)).toFloat()
                            drawCircle(
                                color = burstColor,
                                radius = radius,
                                center = Offset(cx, cy),
                            )
                        }

                        // Sparkling secondary outer burst particles if rich burst is enabled
                        if (hapticBurstEnabled) {
                            val sparkleDistance = baseDistance + (18.dp.toPx() * progress)
                            val sparkleRadius = (1.4.dp * (1f - (progress * 0.9f))).toPx()
                            val sparkleColor = color.copy(alpha = (color.alpha * alpha * 0.75f).coerceIn(0f, 1f))
                            for (i in 0 until 8) {
                                val angle = (i * 45f) + 22.5f
                                val rad = Math.toRadians(angle.toDouble())
                                val cx = centerX + (sparkleDistance * kotlin.math.cos(rad)).toFloat()
                                val cy = centerY + (sparkleDistance * kotlin.math.sin(rad)).toFloat()
                                drawCircle(
                                    color = sparkleColor,
                                    radius = sparkleRadius,
                                    center = Offset(cx, cy),
                                )
                            }
                        }
                    }
                },
        )
    }
}
