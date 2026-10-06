package com.yunfie.illustia.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Consume the system navigation region once, including side buttons in landscape. */
@Composable
fun Modifier.appNavigationSafeArea(insets: WindowInsets = WindowInsets.navigationBars): Modifier = windowInsetsPadding(insets)

@Composable
fun AppNavigationBar(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surfaceContainer,
    showDivider: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    NavigationBar(
        modifier = modifier,
        mode =
            if (settings.isFeatureEnabled(FeatureFlag.NavigationIconsOnly)) {
                NavigationBarDisplayMode.IconOnly
            } else {
                NavigationBarDisplayMode.IconAndText
            },
        defaultWindowInsetsPadding = false,
        color = color,
        showDivider = showDivider,
        content = content,
    )
}
