package com.yunfie.illustia.ui.screens

import com.yunfie.illustia.models.Illust

/** ショートフィード画面が ViewModel に要求する操作。 */
interface ShortsFeedActions {
    fun refreshShortsFeed(forceRefresh: Boolean = false)

    fun loadMoreShortsFeed()

    fun updateShortsFeedCurrentIllust(illustId: Long)

    fun openIllust(illust: Illust)

    fun onIllustLongPress(illust: Illust)

    fun toggleBookmark(illust: Illust)
}
