package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.SearchUiState
import com.yunfie.illustia.data.IllustiaRepositoryApi
import com.yunfie.illustia.data.pixiv.SuggestionStore
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.SearchAgeRestriction
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.SearchWorkType
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.ui.screens.search.SearchActions
import com.yunfie.illustia.ui.screens.search.SearchScreen as SharedSearchScreen

@Composable
fun SearchScreen(
    state: SearchUiState,
    viewModel: IllustiaViewModel,
    repository: IllustiaRepositoryApi,
    focusRequest: Int = 0,
    onFocusRequestHandled: () -> Unit = {},
    widgetSelectionMode: Boolean = false,
    isResultRoute: Boolean = false,
    onIllustSelected: ((Illust) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onBackFromResults: (() -> Unit)? = null,
    onNavigateToResults: ((String) -> Unit)? = null,
) {
    val actions =
        remember(viewModel) {
            object : SearchActions {
                override fun clearSearchResults() = viewModel.clearSearchResults()

                override fun updateSearchDraft(draft: String) = viewModel.updateSearchDraft(draft)

                override fun updateSearchDraftNow(draft: String) = viewModel.updateSearchDraftNow(draft)

                override fun submitSearch(
                    word: String,
                    forceRefresh: Boolean,
                ) = viewModel.submitSearch(word, forceRefresh = forceRefresh)

                override fun removeSearchHistoryItem(item: String) = viewModel.removeSearchHistoryItem(item)

                override fun updateSearchSelectedTab(index: Int) = viewModel.updateSearchSelectedTab(index)

                override fun loadMoreSearch() = viewModel.loadMoreSearch()

                override fun loadMoreUserSearch() = viewModel.loadMoreUserSearch()

                override fun updateVerticalColumnCount(count: Int) = viewModel.updateVerticalColumnCount(count)

                override fun openNovel(novel: NovelPreview) = viewModel.openNovel(novel)

                override fun toggleNovelBookmark(novel: NovelPreview) = viewModel.toggleNovelBookmark(novel)

                override fun toggleBookmark(illust: Illust) = viewModel.toggleBookmark(illust)

                override fun openBookmarkTagPicker(illust: Illust) = viewModel.openBookmarkTagPicker(illust)

                override fun openIllust(illust: Illust) = viewModel.openIllust(illust)

                override fun onIllustLongPress(illustId: Long) = viewModel.onIllustLongPress(illustId)

                override fun openUserPage(user: UserPreview) = viewModel.openUserPage(user)

                override fun onUserLongPress(user: UserPreview) = viewModel.onUserLongPress(user)

                override fun openViewHistory() = viewModel.openViewHistory()

                override fun refreshRecommendedTags(force: Boolean) = viewModel.refreshRecommendedTags(force = force)

                override fun submitSearchFromBrowse(query: String) = viewModel.submitSearchFromBrowse(query)

                override fun openTagOptions(
                    rawTag: String,
                    imageUrl: String?,
                ) = viewModel.openTagOptions(rawTag, imageUrl)

                override fun toggleBookmarkFromCard(illust: Illust) = viewModel.toggleBookmarkFromCard(illust)

                override fun openIllustFromCard(illust: Illust) = viewModel.openIllustFromCard(illust)

                override fun resetSearchOptions() = viewModel.resetSearchOptions()

                override fun updateSearchSort(value: SearchSort) = viewModel.updateSearchSort(value)

                override fun updateSearchTarget(value: SearchTarget) = viewModel.updateSearchTarget(value)

                override fun updateSearchWorkType(value: SearchWorkType) = viewModel.updateSearchWorkType(value)

                override fun updateSearchDuration(value: SearchDuration) = viewModel.updateSearchDuration(value)

                override fun updateSearchBookmarkFilter(value: SearchBookmarkFilter) = viewModel.updateSearchBookmarkFilter(value)

                override fun updateSearchAgeRestriction(value: SearchAgeRestriction) = viewModel.updateSearchAgeRestriction(value)

                override fun updateHideAiWorks(value: Boolean) = viewModel.updateHideAiWorks(value)
            }
        }
    val suggestionStore = remember(repository) { SuggestionStore(repository) }
    SharedSearchScreen(
        state = state,
        actions = actions,
        suggestionStore = suggestionStore,
        focusRequest = focusRequest,
        onFocusRequestHandled = onFocusRequestHandled,
        widgetSelectionMode = widgetSelectionMode,
        isResultRoute = isResultRoute,
        onIllustSelected = onIllustSelected,
        onBack = onBack,
        onBackFromResults = onBackFromResults,
        onNavigateToResults = onNavigateToResults,
    )
}
