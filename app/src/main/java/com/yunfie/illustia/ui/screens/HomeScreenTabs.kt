package com.yunfie.illustia.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class HomeTab(
    @param:StringRes val labelResId: Int,
) {
    Feed(R.string.home_tab_feed),
    Following(R.string.home_tab_following),
}

@Composable
internal fun HomeTabRow(
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    isBlurEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tabs = HomeTab.entries.map { stringResource(it.labelResId) }
    val isDark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val colors =
        if (isBlurEnabled) {
            TabRowDefaults.tabRowColors(
                backgroundColor =
                    if (isDark) {
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.45f)
                    } else {
                        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)
                    },
                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                selectedBackgroundColor =
                    if (isDark) {
                        Color.White.copy(alpha = 0.18f)
                    } else {
                        Color.White.copy(alpha = 0.95f)
                    },
                selectedContentColor = MiuixTheme.colorScheme.onSurface,
            )
        } else {
            TabRowDefaults.tabRowColors(
                backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                selectedBackgroundColor =
                    if (isDark) {
                        Color.White.copy(alpha = 0.16f)
                    } else {
                        Color.White.copy(alpha = 0.92f)
                    },
                selectedContentColor = MiuixTheme.colorScheme.onSurface,
            )
        }
    val borderModifier =
        if (isBlurEnabled) {
            Modifier.squircleBorder(
                width = 0.75.dp,
                color = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f),
                cornerRadius = TabRowDefaults.TabRowWithContourCornerRadius,
            )
        } else {
            Modifier
        }
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier.then(borderModifier).pointerHoverIcon(PointerIcon.Hand),
        colors = colors,
    )
}
