package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.platform.AppInfo
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.settings.SettingsNavigationActions
import com.yunfie.illustia.ui.screens.settings.SettingsScreen as SharedSettingsScreen

@Composable
fun SettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    appInfo: AppInfo,
    onBack: () -> Unit,
) {
    val actions =
        remember(viewModel) {
            object : SettingsNavigationActions {
                override fun openGeneralSettings() = viewModel.openGeneralSettings()

                override fun openImageSettings() = viewModel.openImageSettings()

                override fun openBookmarkSettings() = viewModel.openBookmarkSettings()

                override fun openAccountSettings() = viewModel.openAccountSettings()

                override fun openDataSettings() = viewModel.openDataSettings()

                override fun openUpdateSettings() = viewModel.openUpdateSettings()

                override fun openFeatureFlags() = viewModel.openFeatureFlags()

                override fun showMessage(message: String) = viewModel.showMessage(message)
            }
        }
    SharedSettingsScreen(
        settings = settings,
        actions = actions,
        appInfo = appInfo,
        onBack = onBack,
    )
}
