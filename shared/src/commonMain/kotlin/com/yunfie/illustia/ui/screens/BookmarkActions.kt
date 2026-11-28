package com.yunfie.illustia.ui.screens

import com.yunfie.illustia.BookmarkSort
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.ui.screens.bookmark.BookmarkTabsActions
import kotlinx.coroutines.flow.Flow

/** ブックマーク画面が ViewModel に要求する操作。 */
interface BookmarkActions : BookmarkTabsActions {
    /** ウォッチリスト再取得の要求シグナル。 */
    val watchlistRefreshRequests: Flow<Unit>

    fun refreshBookmarks(forceRefresh: Boolean = false)

    override fun refreshTimeline(forceRefresh: Boolean)

    override fun refreshFollowingUsers(forceRefresh: Boolean)

    fun updateBookmarkSelectedTab(index: Int)

    fun updateRestrict(restrict: Restrict)

    fun updateFollowRestrict(restrict: Restrict)

    fun updateBookmarkSort(sort: BookmarkSort)
}
