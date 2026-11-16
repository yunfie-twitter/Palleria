package com.yunfie.illustia.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.TopTabBar

internal enum class HomeTab(
    @param:StringRes val labelResId: Int,
) {
    Feed(R.string.home_tab_feed),
    Following(R.string.home_tab_following),
}

@Composable
internal fun HomeTabRow(
    selectedTabIndex: Float,
    onTabSelected: (Int) -> Unit,
    isBlurEnabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val tabs = HomeTab.entries.map { stringResource(it.labelResId) }
    TopTabBar(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected,
        modifier = modifier,
        isBlurEnabled = isBlurEnabled,
    )
}
