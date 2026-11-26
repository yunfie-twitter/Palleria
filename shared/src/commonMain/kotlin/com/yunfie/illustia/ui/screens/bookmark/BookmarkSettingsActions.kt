package com.yunfie.illustia.ui.screens.bookmark

import com.yunfie.illustia.models.Restrict

/**
 * ブックマーク設定画面が ViewModel に要求する操作。
 */
interface BookmarkSettingsActions {
    fun updatePrivateBookmarkDefault(value: Boolean)

    fun updateFollowRestrict(value: Restrict)

    fun refreshTimeline(forceRefresh: Boolean = false)

    fun refreshFollowingUsers(forceRefresh: Boolean = false)

    fun updateFollowOnLike(value: Boolean)

    fun updateAutoTagOnBookmark(value: Boolean)

    fun updateAutoDownloadOnBookmark(value: Boolean)

    fun updateAutoBookmarkOnDownload(value: Boolean)

    fun updateConfirmOnLongPressSave(value: Boolean)

    fun updateSkipConfirmOnDetailSave(value: Boolean)
}
