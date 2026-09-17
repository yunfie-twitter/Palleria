package com.yunfie.illustia.ui.app

import androidx.compose.runtime.Immutable
import com.yunfie.illustia.BookmarkChromeState
import com.yunfie.illustia.HomeChromeState
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.NovelChromeState
import com.yunfie.illustia.RankingChromeState

@Immutable
internal class IllustiaAppStateBundle(
    val state: IllustiaUiState,
) {
    val settings = state.settings
    val loadState = state.loadState
    val homeItems = state.homeItems
    val novelItems = state.novelItems
    val timelineItems = state.timelineItems
    val rankingItems = state.rankingItems
    val bookmarkItems = state.bookmarkItems
    val watchlistItems = state.watchlistItems
    val followingUsers = state.followingUsers

    val homeChrome =
        HomeChromeState(
            homeKind = state.homeKind,
            homeNextUrl = state.homeNextUrl,
            timelineNextUrl = state.timelineNextUrl,
        )
    val novelChrome =
        NovelChromeState(
            novelNextUrl = state.novelNextUrl,
        )
    val rankingChrome =
        RankingChromeState(
            rankingMode = state.rankingMode,
            rankingNextUrl = state.rankingNextUrl,
        )
    val bookmarkChrome =
        BookmarkChromeState(
            bookmarkNextUrl = state.bookmarkNextUrl,
            timelineNextUrl = state.timelineNextUrl,
            watchlistNextUrl = state.watchlistNextUrl,
            activeWatchlistTag = state.activeWatchlistTag,
            followingUsersNextUrl = state.followingUsersNextUrl,
            selectedTab = state.bookmarkSelectedTab,
        )
}
