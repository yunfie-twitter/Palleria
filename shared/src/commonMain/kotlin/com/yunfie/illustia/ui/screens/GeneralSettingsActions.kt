package com.yunfie.illustia.ui.screens

/** 一般設定画面が ViewModel に要求する操作。 */
interface GeneralSettingsActions {
    fun updateThemeMode(value: String)

    fun updateUseDynamicColor(enabled: Boolean)

    fun updateArtworkThemeEnabled(enabled: Boolean)

    fun updateAppIconVariant(value: String)

    fun updateAppLanguage(value: String)

    fun updateAppFont(value: String)

    fun updateAllowR18(allowed: Boolean)

    fun updateAllowR18G(allowed: Boolean)

    fun updateHideAiWorks(value: Boolean)

    fun updateAutoLoadMore(enabled: Boolean)

    fun updateDoubleBackToExit(enabled: Boolean)

    fun updateClipboardAutoDetect(enabled: Boolean)

    fun updateSmoothTransitions(enabled: Boolean)

    fun updateHapticsEnabled(enabled: Boolean)

    fun updateHapticMode(value: String)

    fun updateNotchOptimization(enabled: Boolean)

    fun updateSecureWindow(enabled: Boolean)

    fun openAppLockSetup()

    fun openNavigationSettings()

    fun openPrivacyModeSettings()
}
