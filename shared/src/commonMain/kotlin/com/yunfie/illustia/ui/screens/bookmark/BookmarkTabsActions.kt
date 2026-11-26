package com.yunfie.illustia.ui.screens.bookmark

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserPreview

/**
 * ブックマーク系タブが ViewModel に要求する操作。
 */
interface BookmarkTabsActions {
    fun reloadBookmarks(forceRefresh: Boolean)

    fun loadMoreBookmarks()

    fun refreshTimeline(forceRefresh: Boolean)

    fun loadMoreTimeline()

    fun refreshFollowingUsers(forceRefresh: Boolean)

    fun loadMoreFollowingUsers()

    fun toggleBookmarkById(illustId: Long)

    fun openBookmarkTagPickerFor(illust: Illust)

    fun openIllustById(illustId: Long)

    fun onIllustLongPressById(illustId: Long)

    fun onFollowingUserLongPress(user: UserPreview)

    fun openUserPageFor(user: UserPreview)

    fun updateColumnsFromGrid(columns: Int)
}
