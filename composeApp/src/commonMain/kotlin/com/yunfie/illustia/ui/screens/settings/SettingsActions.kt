package com.yunfie.illustia.ui.screens.settings

/**
 * 設定系画面が ViewModel に要求する操作のうち、共有 UI から使える最小契約。
 */
interface SettingsActions {
    fun updateDetailSectionOrder(value: List<String>)

    fun resetSearchOptions()

    fun resetFeatureFlags()

    fun updateHiddenNavigationTabs(value: List<String>)

    fun updateNavigationOrder(value: List<String>)

    fun updateNavigationStyle(value: String)

    fun updateStartupScreen(value: String)

    fun updateShortsFeedEnabled(value: Boolean)

    fun updateHideHomeNovelButton(value: Boolean)
}
