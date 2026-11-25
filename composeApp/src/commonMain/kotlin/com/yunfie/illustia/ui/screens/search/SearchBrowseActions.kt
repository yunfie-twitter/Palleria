package com.yunfie.illustia.ui.screens.search

import com.yunfie.illustia.models.Illust

/**
 * 検索ブラウズ画面が ViewModel に要求する操作。
 */
interface SearchBrowseActions {
    fun openViewHistory()

    fun refreshRecommendedTags(force: Boolean)

    fun submitSearchFromBrowse(query: String)

    fun openTagOptions(
        rawTag: String,
        imageUrl: String?,
    )

    fun toggleBookmarkFromCard(illust: Illust)

    fun openIllustFromCard(illust: Illust)
}
