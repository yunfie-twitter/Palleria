package com.yunfie.illustia.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val AUTO_DISMISS_DELAY_MS = 1200L

/**
 * Interactive mini-guide banner showing quick-peek and pinch-grid microinteractions.
 * Automatically dismisses when both actions are completed.
 */
@Composable
fun InteractiveGuideBanner(
    quickPeekCompleted: Boolean,
    pinchGridCompleted: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allCompleted = quickPeekCompleted && pinchGridCompleted

    LaunchedEffect(allCompleted) {
        if (allCompleted) {
            delay(AUTO_DISMISS_DELAY_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = !allCompleted,
        enter = expandVertically(animationSpec = tween(300)) + fadeIn(),
        exit = shrinkVertically(animationSpec = tween(300)) + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.guide_quick_peek_title) + " / " + stringResource(R.string.guide_pinch_grid_title),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = stringResource(R.string.guide_skip),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                GuideStepRow(
                    title = stringResource(R.string.guide_quick_peek_title),
                    desc = stringResource(R.string.guide_quick_peek_desc),
                    icon = MiuixIcons.Favorites,
                    completed = quickPeekCompleted,
                )

                GuideStepRow(
                    title = stringResource(R.string.guide_pinch_grid_title),
                    desc = stringResource(R.string.guide_pinch_grid_desc),
                    icon = MiuixIcons.Tune,
                    completed = pinchGridCompleted,
                )
            }
        }
    }
}

@Composable
private fun GuideStepRow(
    title: String,
    desc: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    completed: Boolean,
) {
    val completedColor = Color(0xFF2E7D32)
    val iconBgColor by animateColorAsState(
        targetValue =
            if (completed) {
                completedColor.copy(alpha = 0.15f)
            } else {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
            },
        animationSpec = tween(250),
        label = "guideIconBg",
    )
    val iconTint by animateColorAsState(
        targetValue =
            if (completed) {
                completedColor
            } else {
                MiuixTheme.colorScheme.primary
            },
        animationSpec = tween(250),
        label = "guideIconTint",
    )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (completed) MiuixIcons.Ok else icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        AnimatedVisibility(
            visible = completed,
            enter = scaleIn(animationSpec = tween(250)) + fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(completedColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.guide_completed),
                    style = MiuixTheme.textStyles.footnote2,
                    fontWeight = FontWeight.Bold,
                    color = completedColor,
                )
            }
        }
    }
}
