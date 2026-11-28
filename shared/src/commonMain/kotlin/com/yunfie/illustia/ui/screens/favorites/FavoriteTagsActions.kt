package com.yunfie.illustia.ui.screens.favorites

import com.yunfie.illustia.models.Illust

/**
 * お気に入りタグ画面が ViewModel に要求する操作。
 */
interface FavoriteTagsActions {
    fun loadWatchlistTag(
        tag: String,
        forceRefresh: Boolean,
    )

    fun loadMoreWatchlist()

    fun toggleFavoriteTag(rawTag: String)

    fun toggleBookmarkFromCard(illust: Illust)

    fun openIllustFromCard(illust: Illust)

    fun onIllustLongPressById(illustId: Long)
}
