package com.yunfie.illustia.ui.screens

/** アプリデータ画面が ViewModel に要求する操作。 */
interface AppDataActions {
    fun clearAppCache()

    fun clearViewHistory()

    fun clearSearchHistory()

    fun clearFavoriteTags()

    fun clearMuteData()
}
