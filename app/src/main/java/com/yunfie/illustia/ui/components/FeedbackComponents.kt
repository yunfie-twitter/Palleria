package com.yunfie.illustia.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.yunfie.illustia.R
import com.yunfie.illustia.models.LoadState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OfflineCachedChip(
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            modifier =
                Modifier
                    .clip(shape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = MiuixIcons.Refresh,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(R.string.offline_cached_banner_label),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (onRetry != null) {
                Text(
                    text = stringResource(R.string.offline_retry_button),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun StateBanner(
    loadState: LoadState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    when (loadState) {
        LoadState.Loading -> {
            Box(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator()
            }
        }

        is LoadState.Error -> {
            RequestErrorState(message = loadState.message, onRetry = onRetry, modifier = modifier)
        }

        LoadState.Idle,
        LoadState.Loaded,
        -> {
            Unit
        }
    }
}

@Composable
fun RequestErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val errorColor = MiuixTheme.colorScheme.error
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val strokeWidth = 2.4.dp.toPx()
            drawCircle(
                color = errorColor,
                radius = size.minDimension / 2f - strokeWidth,
                style =
                    Stroke(
                        width = strokeWidth,
                    ),
            )
            val centerX = size.width / 2f
            drawLine(
                color = errorColor,
                start = Offset(centerX, size.height * 0.27f),
                end = Offset(centerX, size.height * 0.56f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = errorColor,
                radius = strokeWidth * 0.65f,
                center = Offset(centerX, size.height * 0.73f),
            )
        }
        Text(
            text = message,
            color = errorColor,
            textAlign = TextAlign.Center,
            style = MiuixTheme.textStyles.body2,
        )
        if (onRetry != null) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

/**
 * Error card shown dead centre of the screen.
 *
 * Rendered in a full-screen [Dialog] rather than as a Box inside the app tree: the app root
 * applies [appNavigationSafeArea] and the Scaffold's content padding, so a Box there centres
 * inside the *inset* area and lands visibly off-centre.
 *
 * The dialog surface is forced to the whole display and consumes no window insets, so the card
 * centres on the true horizontal and vertical middle of the screen instead of on the area left
 * between the status and navigation bars.
 */
@Composable
fun CenteredErrorOverlay(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    if (message == null) return
    val view = LocalView.current
    val containerSize = LocalWindowInfo.current.containerSize
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                decorFitsSystemWindows = false,
            ),
    ) {
        // Match the dialog surface to the full display so "centre" means the real screen centre,
        // not the space between the status and navigation bars.
        (view.parent as? DialogWindowProvider)?.window?.setLayout(
            containerSize.width,
            containerSize.height,
        )
        val dismissState = remember { MutableInteractionSource() }
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = dismissState,
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 32.dp)
                        .clip(shape)
                        .background(MiuixTheme.colorScheme.surfaceContainerHighest)
                        // Swallow taps on the card itself so they don't reach the dismissing scrim.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                RequestErrorState(message = message, onRetry = onRetry)
                Button(onClick = onDismiss, colors = overlayActionButtonColors()) {
                    Text(stringResource(R.string.action_close))
                }
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = message, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
fun HeaderIcon(
    icon: ImageVector,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val performHaptic = rememberHapticFeedbackAction()
    IconButton(
        onClick = {
            if (onClick != null) {
                performHaptic(AppHapticEffect.Click)
                onClick()
            }
        },
        enabled = onClick != null,
        modifier = modifier.then(if (onClick != null) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier),
        minWidth = 44.dp,
        minHeight = 44.dp,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.size(28.dp),
        )
    }
}

@Composable
fun HeaderOverlayIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Black.copy(alpha = 0.35f),
    contentColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    contentDescription: String? = null,
) {
    val performHaptic = rememberHapticFeedbackAction()
    IconButton(
        onClick = {
            performHaptic(AppHapticEffect.Click)
            onClick()
        },
        modifier = modifier.pointerHoverIcon(PointerIcon.Hand).size(38.dp),
        backgroundColor = backgroundColor,
        cornerRadius = 19.dp,
        minWidth = 38.dp,
        minHeight = 38.dp,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * OverlayIconCascadingDropdownMenu is an IconButton wrapper that opens an OverlayCascadingListPopup when clicked.
 * It is intended for toolbar action slots — such as the actions area of TopAppBar — where a single icon needs to expand
 * into a menu that contains submenus. Items whose DropdownItem.children is non-empty become submenu triggers; cascading depth
 * is limited to 2.
 */
@Composable
fun OverlayIconCascadingDropdownMenu(
    entries: List<DropdownEntry>,
    modifier: Modifier = Modifier,
    icon: ImageVector = MiuixIcons.More,
    backgroundColor: Color = Color.Black.copy(alpha = 0.35f),
    contentColor: Color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    contentDescription: String? = null,
    onPopupDismiss: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        HeaderOverlayIcon(
            icon = icon,
            onClick = { expanded = true },
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            contentDescription = contentDescription,
        )
        OverlayCascadingListPopup(
            show = expanded,
            entries = entries,
            onDismissRequest = {
                expanded = false
                onPopupDismiss?.invoke()
            },
        )
    }
}

@Composable
fun DividerLine() {
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = MiuixTheme.colorScheme.dividerLine,
    )
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    InfiniteProgressIndicator(
        modifier = modifier,
        color = MiuixTheme.colorScheme.onBackground,
        size = 36.dp,
        strokeWidth = 3.dp,
        orbitingDotSize = 4.dp,
    )
}

@Composable
fun CenteredLoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator()
    }
}
