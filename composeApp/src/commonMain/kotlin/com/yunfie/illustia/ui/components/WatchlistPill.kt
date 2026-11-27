package com.yunfie.illustia.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import com.yunfie.illustia.resources.action_add
import com.yunfie.illustia.resources.action_remove_bookmark
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class WatchlistPillStage { UNADDED, CHECK, ADDED, REMOVING }

@Composable
fun WatchlistPill(
    isAdded: Boolean,
    animationTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    var animationStage by remember { mutableStateOf<WatchlistPillStage?>(null) }
    var lastHandledTrigger by remember { mutableStateOf(animationTrigger) }
    var wasAdded by remember { mutableStateOf(isAdded) }

    val haptic = rememberAppHapticPerformer()

    LaunchedEffect(animationTrigger) {
        if (animationTrigger == lastHandledTrigger) return@LaunchedEffect
        lastHandledTrigger = animationTrigger
        animationStage = WatchlistPillStage.CHECK
        haptic(AppHapticEffect.Click)
        delay(600)
        animationStage = WatchlistPillStage.ADDED
    }

    LaunchedEffect(isAdded) {
        if (wasAdded && !isAdded) {
            animationStage = WatchlistPillStage.REMOVING
            haptic(AppHapticEffect.Click)
            delay(180)
            animationStage = WatchlistPillStage.UNADDED
        } else if (!wasAdded && isAdded && animationStage == null) {
            animationStage = WatchlistPillStage.ADDED
        }
        wasAdded = isAdded
    }

    val stage =
        animationStage ?: if (isAdded) {
            WatchlistPillStage.ADDED
        } else {
            WatchlistPillStage.UNADDED
        }

    val isActive = stage == WatchlistPillStage.ADDED || stage == WatchlistPillStage.CHECK
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier =
            modifier
                .squircleSurface(
                    color = if (isActive) scheme.primary else scheme.surfaceContainerHighest,
                    cornerRadius = 24.dp,
                ).then(
                    if (!isActive) {
                        Modifier.squircleBorder(
                            width = 1.dp,
                            color = scheme.onSurface.copy(alpha = 0.15f),
                            cornerRadius = 24.dp,
                        )
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                when (targetState) {
                    WatchlistPillStage.CHECK -> {
                        (scaleIn(spring(dampingRatio = 0.45f, stiffness = 380f), initialScale = 0.3f) + fadeIn(tween(160))) togetherWith
                            (scaleOut(tween(120), targetScale = 0.5f) + fadeOut(tween(120)))
                    }

                    WatchlistPillStage.ADDED -> {
                        (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f)) togetherWith
                            (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.1f))
                    }

                    WatchlistPillStage.REMOVING -> {
                        (scaleIn(tween(140), initialScale = 0.8f) + fadeIn(tween(140))) togetherWith
                            (scaleOut(tween(140), targetScale = 0.5f) + fadeOut(tween(140)))
                    }

                    WatchlistPillStage.UNADDED -> {
                        (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.85f)) togetherWith
                            (fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 1.05f))
                    }
                }
            },
            label = "watchlist-pill-stage",
        ) { s ->
            when (s) {
                WatchlistPillStage.CHECK -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Ok,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(Res.string.action_remove_bookmark),
                            color = scheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                }

                WatchlistPillStage.ADDED -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.FavoritesFill,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(Res.string.action_remove_bookmark),
                            color = scheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                }

                WatchlistPillStage.REMOVING,
                WatchlistPillStage.UNADDED,
                -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Favorites,
                            contentDescription = null,
                            tint = scheme.onSurface,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(Res.string.action_add),
                            color = scheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                    }
                }
            }
        }
    }
}
