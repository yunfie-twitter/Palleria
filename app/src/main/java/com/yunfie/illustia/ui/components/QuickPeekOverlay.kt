package com.yunfie.illustia.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun QuickPeekOverlay(
    illust: Illust,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onBookmark: () -> Unit,
    onCopyImage: () -> Unit,
    useSharedElementTransition: Boolean = false,
) {
    val performHaptic = rememberHapticFeedbackAction()
    var isVisible by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            performHaptic(AppHapticEffect.Dismiss)
        }
    }

    LaunchedEffect(Unit) {
        performHaptic(AppHapticEffect.Peek)
        isVisible = true
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.65f else 0f,
        animationSpec = tween(if (useSharedElementTransition) 220 else 160),
        label = "quick_peek_scrim",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val enterSpec =
                if (useSharedElementTransition) {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(
                            initialScale = 0.35f,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                        )
                } else {
                    fadeIn(tween(180)) +
                        scaleIn(
                            initialScale = 0.85f,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                        )
                }

            val exitSpec =
                if (useSharedElementTransition) {
                    fadeOut(tween(160)) +
                        scaleOut(
                            targetScale = 0.35f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        )
                } else {
                    fadeOut(tween(140)) + scaleOut(targetScale = 0.85f, animationSpec = tween(140))
                }

            AnimatedVisibility(
                visible = isVisible,
                enter = enterSpec,
                exit = exitSpec,
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(24.dp)
                            .widthIn(max = 400.dp)
                            .heightIn(max = 620.dp)
                            .shadow(if (LocalFastScrolling.current) 0.dp else 24.dp, RoundedCornerShape(24.dp))
                            .clip(RoundedCornerShape(24.dp))
                            .background(MiuixTheme.colorScheme.surface)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}, // Consume clicks inside card
                            ),
                ) {
                    Column {
                        // Artwork image container with gradient overlay
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .heightIn(min = 240.dp, max = 460.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            PixivImage(
                                url = illust.imageUrl,
                                contentDescription = illust.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )

                            // Top action: Bookmark badge
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                IconButton(
                                    onClick = {
                                        performHaptic(AppHapticEffect.Toggle)
                                        onBookmark()
                                    },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        imageVector = if (illust.isBookmarked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                        contentDescription = stringResource(R.string.action_bookmark),
                                        tint = if (illust.isBookmarked) MiuixTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }

                        // Info section
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(MiuixTheme.colorScheme.surface)
                                    .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = illust.title.ifBlank { stringResource(R.string.dialog_work_options) },
                                style = MiuixTheme.textStyles.title3,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Text(
                                text = stringResource(R.string.dialog_artist_label, illust.artistName),
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Action buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = onOpenDetail,
                                    modifier = Modifier.weight(1f),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            color = MiuixTheme.colorScheme.primary,
                                            contentColor = MiuixTheme.colorScheme.onPrimary,
                                        ),
                                ) {
                                    Text(stringResource(R.string.dialog_show_detail))
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MiuixTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IconButton(onClick = {
                                        performHaptic(AppHapticEffect.Click)
                                        onCopyImage()
                                    }) {
                                        Icon(
                                            imageVector = MiuixIcons.Copy,
                                            contentDescription = stringResource(R.string.action_copy_image),
                                            tint = MiuixTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
