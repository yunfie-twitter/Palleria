package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import top.yukonga.miuix.kmp.overlay.OverlayCascadingListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
            Column(
                modifier =
                    modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = loadState.message,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                if (onRetry != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRetry,
                    ) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }

        LoadState.Idle,
        LoadState.Loaded,
        -> {
            Unit
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
    IconButton(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = modifier,
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
    IconButton(
        onClick = onClick,
        modifier = modifier.size(38.dp),
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
