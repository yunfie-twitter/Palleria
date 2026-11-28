package com.yunfie.illustia.ui.screens.bookmark

/**
 * ウォッチリストタブが要求する取得操作。実装は Android 側の WatchlistStore。
 */
interface WatchlistPager {
    suspend fun fetch(forceRefresh: Boolean)

    suspend fun loadMore()
}
