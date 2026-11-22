package com.yunfie.illustia.ui.app

import androidx.navigation3.runtime.NavKey
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel

private const val BOOKMARK_WATCHLIST_TAB = 2
private const val BOOKMARK_FOLLOWING_TAB = 3

internal fun refreshDesktopDestination(
    route: NavKey?,
    tab: AppTab,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
): Boolean =
    if (route == AppRoute.Main) {
        refreshDesktopTab(tab, state, viewModel)
    } else {
        refreshDesktopRoute(route, viewModel)
    }

private fun refreshDesktopRoute(
    route: NavKey?,
    viewModel: IllustiaViewModel,
): Boolean {
    when (route) {
        is AppRoute.Detail -> viewModel.refreshIllustDetail(route.illustId)
        is AppRoute.UserProfile -> viewModel.refreshUserPage(route.userId)
        is AppRoute.SearchResults -> viewModel.submitSearch(route.query, forceRefresh = true)
        is AppRoute.TagSearch -> viewModel.submitSearch(route.word, forceRefresh = true)
        AppRoute.Search -> viewModel.refreshRecommendedTags(force = true)
        AppRoute.NovelList -> viewModel.refreshNovels(forceRefresh = true)
        AppRoute.NovelReader -> viewModel.refreshNovel()
        AppRoute.Notifications -> viewModel.refreshNotifications()
        else -> return false
    }
    return true
}

private fun refreshDesktopTab(
    tab: AppTab,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
): Boolean {
    when (tab) {
        AppTab.Home -> {
            if (state.homeSelectedTab == 0) {
                viewModel.refreshHome(forceRefresh = true)
            } else {
                viewModel.refreshTimeline(forceRefresh = true)
            }
        }

        AppTab.Ranking -> {
            viewModel.refreshRanking(forceRefresh = true)
        }

        AppTab.Bookmarks -> {
            refreshDesktopBookmarks(state.bookmarkSelectedTab, viewModel)
        }

        AppTab.ShortsFeed -> {
            viewModel.refreshShortsFeed(forceRefresh = true)
        }

        AppTab.Novel -> {
            viewModel.refreshNovels(forceRefresh = true)
        }

        AppTab.Search -> {
            viewModel.refreshRecommendedTags(force = true)
        }

        AppTab.More -> {
            return false
        }
    }
    return true
}

private fun refreshDesktopBookmarks(
    selectedTab: Int,
    viewModel: IllustiaViewModel,
) {
    when (selectedTab) {
        0 -> viewModel.refreshTimeline(forceRefresh = true)
        BOOKMARK_WATCHLIST_TAB -> viewModel.refreshWatchlist()
        BOOKMARK_FOLLOWING_TAB -> viewModel.refreshFollowingUsers(forceRefresh = true)
        else -> viewModel.refreshBookmarks(forceRefresh = true)
    }
}
