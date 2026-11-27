package com.yunfie.illustia.ui.screens.search

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.UserPreview

/** 検索画面が ViewModel に要求する操作。 */
interface SearchActions :
    SearchBrowseActions,
    SearchOptionsActions {
    fun clearSearchResults()

    fun updateSearchDraft(draft: String)

    fun updateSearchDraftNow(draft: String)

    fun submitSearch(
        word: String,
        forceRefresh: Boolean = false,
    )

    fun removeSearchHistoryItem(item: String)

    fun updateSearchSelectedTab(index: Int)

    fun loadMoreSearch()

    fun loadMoreUserSearch()

    fun updateVerticalColumnCount(count: Int)

    fun openNovel(novel: NovelPreview)

    fun toggleNovelBookmark(novel: NovelPreview)

    fun toggleBookmark(illust: Illust)

    fun openBookmarkTagPicker(illust: Illust)

    fun openIllust(illust: Illust)

    fun onIllustLongPress(illustId: Long)

    fun openUserPage(user: UserPreview)

    fun onUserLongPress(user: UserPreview)
}
