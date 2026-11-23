package com.yunfie.illustia.ui.scroll

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.yunfie.illustia.ui.scroll.ScrollStateKeys

/**
 * タブ切替などで再利用する固定キー。
 * トップレベルに置くことで `ScrollStateRegistry.Keys` の入れ子解決に依存しない。
 */
object ScrollStateKeys {
    const val BOOKMARK_TIMELINE = "bookmark_timeline"
    const val BOOKMARK_MAIN = "bookmark_main"
    const val BOOKMARK_WATCHLIST = "bookmark_watchlist"
    const val BOOKMARK_FOLLOWING = "bookmark_following"
    const val HOME_FEED = "home_feed"
    const val HOME_TIMELINE = "home_timeline"
    const val SEARCH_RESULT = "search_result"
    const val USER_SEARCH_RESULT = "user_search_result"
    const val SEARCH_BROWSE = "search_browse"

    fun userProfile(userId: Long): String = "user_profile_$userId"

    fun userProfileBookmarks(userId: Long): String = "user_profile_bookmarks_$userId"

    fun illustDetail(illustId: Long): String = "illust_detail_$illustId"

    fun ranking(mode: String): String = "ranking_$mode"
}

/**
 * UI レイヤーが所有するスクロール状態の置き場。
 *
 * 以前は `IllustiaViewModel` が `LazyGridState` / `LazyListState` を直接保持していたが、
 * これらは UI 状態であり、ViewModel が `compose.foundation` に依存する唯一の理由だった。
 * ここへ移すことで ViewModel 側はプリミティブな値だけを参照できる。
 */
class ScrollStateRegistry {
    private val cached = LinkedHashMap<String, LazyGridState>(MAX_CACHED, 0.75f, true)
    private val cachedLists = LinkedHashMap<String, LazyListState>(MAX_CACHED, 0.75f, true)

    fun grid(key: String): LazyGridState = cached.getOrPut(key) { LazyGridState() }

    fun list(key: String): LazyListState = cachedLists.getOrPut(key) { LazyListState() }

    fun clearCached() {
        cached.clear()
        cachedLists.clear()
    }

    companion object {
        const val MAX_CACHED = 16
    }
}
