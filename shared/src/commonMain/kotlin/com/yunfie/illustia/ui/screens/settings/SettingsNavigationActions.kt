package com.yunfie.illustia.ui.screens.settings

/**
 * 設定トップ画面が ViewModel に要求する操作。
 *
 * 画面遷移だけを要求し、状態は [com.yunfie.illustia.settings.AppSettings] から受け取る。
 */
interface SettingsNavigationActions {
    fun openGeneralSettings()

    fun openImageSettings()

    fun openBookmarkSettings()

    fun openAccountSettings()

    fun openDataSettings()

    fun openUpdateSettings()

    fun openFeatureFlags()

    fun showMessage(message: String)
}
