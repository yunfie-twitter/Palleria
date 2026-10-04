package com.yunfie.illustia.ui.app

import androidx.navigation3.runtime.NavKey
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel

internal fun refreshDesktopDestination(
    route: NavKey?,
    tab: AppTab,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
): Boolean {
    when (route) {
        is AppRoute.Detail -> viewModel.refreshIllustDetail(route.illustId)
        is AppRoute.UserProfile -> viewModel.openUserPage(route.userId)
        is AppRoute.SearchResults -> viewModel.submitSearch(route.query, forceRefresh = true)
        is AppRoute.TagSearch -> viewModel.submitSearch(route.word, forceRefresh = true)
        AppRoute.Search -> viewModel.refreshRecommendedTags(force = true)
        AppRoute.NovelList -> viewModel.refreshNovels(forceRefresh = true)
        AppRoute.NovelReader -> state.selectedNovel?.let(viewModel::openNovel)
        AppRoute.Notifications -> viewModel.refreshNotifications()
        AppRoute.Main -> when (tab) {
            AppTab.Home -> if (state.homeSelectedTab == 0) {
                viewModel.refreshHome(forceRefresh = true)
            } else {
                viewModel.refreshTimeline(forceRefresh = true)
            }
            AppTab.Ranking -> viewModel.refreshRanking(forceRefresh = true)
            AppTab.Bookmarks -> viewModel.refreshBookmarks(forceRefresh = true)
            AppTab.ShortsFeed -> viewModel.refreshShortsFeed(forceRefresh = true)
            AppTab.Novel -> viewModel.refreshNovels(forceRefresh = true)
            AppTab.Search -> viewModel.refreshRecommendedTags(force = true)
            AppTab.More -> return false
        }
        else -> return false
    }
    return true
}
