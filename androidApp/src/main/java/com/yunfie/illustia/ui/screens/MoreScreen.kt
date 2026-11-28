package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.platform.AppInfo
import com.yunfie.illustia.ui.screens.more.MoreNavigationActions
import com.yunfie.illustia.ui.screens.more.MoreScreen as SharedMoreScreen

@Composable
fun MoreScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    appInfo: AppInfo,
) {
    val actions =
        remember(viewModel) {
            object : MoreNavigationActions {
                override fun openCurrentUserProfile() = viewModel.openCurrentUserProfile()

                override fun openAccountSwitcher() = viewModel.openAccountSwitcher()

                override fun openSettings() = viewModel.openSettings()

                override fun openViewHistory() = viewModel.openViewHistory()

                override fun openNotifications() = viewModel.openNotifications()

                override fun openFavoriteTags() = viewModel.openFavoriteTags()

                override fun openNovels() = viewModel.openNovels()

                override fun openMuteSettings() = viewModel.openMuteSettings()

                override fun openDownloadQueue() = viewModel.openDownloadQueue()

                override fun openAbout() = viewModel.openAbout()
            }
        }
    SharedMoreScreen(
        state = state,
        actions = actions,
        appInfo = appInfo,
    )
}
