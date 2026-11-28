@file:JvmName("AndroidGeneralSettingsScreen")

package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.GeneralSettingsScreen as SharedGeneralSettingsScreen

@Composable
fun GeneralSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenNavigationSettings: () -> Unit = { viewModel.openNavigationSettings() },
) {
    val actions =
        remember(viewModel) {
            object : GeneralSettingsActions {
                override fun updateThemeMode(value: String) = viewModel.updateThemeMode(value)

                override fun updateUseDynamicColor(enabled: Boolean) = viewModel.updateUseDynamicColor(enabled)

                override fun updateArtworkThemeEnabled(enabled: Boolean) = viewModel.updateArtworkThemeEnabled(enabled)

                override fun updateAppIconVariant(value: String) = viewModel.updateAppIconVariant(value)

                override fun updateAppLanguage(value: String) = viewModel.updateAppLanguage(value)

                override fun updateAppFont(value: String) = viewModel.updateAppFont(value)

                override fun updateAllowR18(allowed: Boolean) = viewModel.updateAllowR18(allowed)

                override fun updateAllowR18G(allowed: Boolean) = viewModel.updateAllowR18G(allowed)

                override fun updateHideAiWorks(value: Boolean) = viewModel.updateHideAiWorks(value)

                override fun updateAutoLoadMore(enabled: Boolean) = viewModel.updateAutoLoadMore(enabled)

                override fun updateDoubleBackToExit(enabled: Boolean) = viewModel.updateDoubleBackToExit(enabled)

                override fun updateClipboardAutoDetect(enabled: Boolean) = viewModel.updateClipboardAutoDetect(enabled)

                override fun updateSmoothTransitions(enabled: Boolean) = viewModel.updateSmoothTransitions(enabled)

                override fun updateHapticsEnabled(enabled: Boolean) = viewModel.updateHapticsEnabled(enabled)

                override fun updateHapticMode(value: String) = viewModel.updateHapticMode(value)

                override fun updateNotchOptimization(enabled: Boolean) = viewModel.updateNotchOptimization(enabled)

                override fun updateSecureWindow(enabled: Boolean) = viewModel.updateSecureWindow(enabled)

                override fun openAppLockSetup() = viewModel.openAppLockSetup()

                override fun openNavigationSettings() = viewModel.openNavigationSettings()

                override fun openPrivacyModeSettings() = viewModel.openPrivacyModeSettings()
            }
        }
    SharedGeneralSettingsScreen(
        settings = settings,
        actions = actions,
        onBack = onBack,
        onOpenNavigationSettings = onOpenNavigationSettings,
    )
}
