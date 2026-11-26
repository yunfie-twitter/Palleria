package com.yunfie.illustia.ui.screens.more

/** その他タブが ViewModel に要求する遷移操作。 */
interface MoreNavigationActions {
    fun openCurrentUserProfile()

    fun openAccountSwitcher()

    fun openSettings()

    fun openViewHistory()

    fun openNotifications()

    fun openFavoriteTags()

    fun openNovels()

    fun openMuteSettings()

    fun openDownloadQueue()

    fun openAbout()
}
