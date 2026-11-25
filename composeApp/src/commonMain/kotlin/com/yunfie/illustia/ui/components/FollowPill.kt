package com.yunfie.illustia.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.action_follow
import com.yunfie.illustia.resources.action_following
import com.yunfie.illustia.ui.components.AppHapticEffect
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class FollowPillStage { UNFOLLOWED, CHECK, FOLLOWED, REMOVING }

@Composable
fun FollowPill(
    isFollowed: Boolean,
    followAnimationTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    var animationStage by remember { mutableStateOf<FollowPillStage?>(null) }
    var lastHandledTrigger by remember { mutableStateOf(followAnimationTrigger) }
    var awaitingFollowConfirmation by remember { mutableStateOf(false) }
    var wasFollowed by remember { mutableStateOf(isFollowed) }

    val performHaptic = rememberAppHapticPerformer()

    LaunchedEffect(followAnimationTrigger) {
        if (followAnimationTrigger == lastHandledTrigger) return@LaunchedEffect
        lastHandledTrigger = followAnimationTrigger
        awaitingFollowConfirmation = true
        animationStage = FollowPillStage.CHECK
        performHaptic(AppHapticEffect.Click)
        delay(600)
        animationStage = FollowPillStage.FOLLOWED
        awaitingFollowConfirmation = false
    }

    LaunchedEffect(isFollowed) {
        if (wasFollowed && !isFollowed) {
            animationStage = FollowPillStage.REMOVING
            performHaptic(AppHapticEffect.Click)
            delay(180)
            animationStage = FollowPillStage.UNFOLLOWED
            awaitingFollowConfirmation = false
        } else if (!wasFollowed && isFollowed && animationStage == null) {
            animationStage = FollowPillStage.FOLLOWED
        }
        wasFollowed = isFollowed
    }

    val stage =
        animationStage ?: if (isFollowed) {
            FollowPillStage.FOLLOWED
        } else {
            FollowPillStage.UNFOLLOWED
        }

    val isActiveFollow = stage == FollowPillStage.FOLLOWED || stage == FollowPillStage.CHECK
    val scheme = MiuixTheme.colorScheme
    val containerModifier =
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
            ).padding(horizontal = 22.dp, vertical = 11.dp)
    Box(
        modifier = containerModifier,
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

                    FollowPillStage.REMOVING -> {
                        (scaleIn(tween(140), initialScale = 0.8f) + fadeIn(tween(140))) togetherWith
                            (scaleOut(tween(140), targetScale = 0.6f) + fadeOut(tween(140)))
                    }

                    FollowPillStage.UNFOLLOWED -> {
                        (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f)) togetherWith
                            (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.05f))
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
                        text = stringResource(Res.string.action_following),
                        color = scheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }

                FollowPillStage.REMOVING,
                FollowPillStage.UNFOLLOWED,
                -> {
                    Text(
                        text = stringResource(Res.string.action_follow),
                        color = scheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MiuixTheme.textStyles.subtitle,
                    )
                }
            }
        }
    }
}
