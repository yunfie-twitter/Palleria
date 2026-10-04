package com.yunfie.illustia.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.TabRowWithContour
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
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val tabs = HomeTab.entries.map { stringResource(it.labelResId) }
    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier.pointerHoverIcon(PointerIcon.Hand),
        colors =
            TabRowDefaults.tabRowColors(
                backgroundColor = scheme.surfaceContainer.copy(alpha = 0.44f),
                contentColor = scheme.onSurfaceVariantSummary,
                selectedBackgroundColor = scheme.surfaceContainerHigh,
                selectedContentColor = scheme.onBackground,
            ),
        minWidth = 110.dp,
        maxWidth = 160.dp,
        height = 45.dp,
    )
}
