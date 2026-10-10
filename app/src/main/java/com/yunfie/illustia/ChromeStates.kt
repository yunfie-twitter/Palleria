package com.yunfie.illustia

import com.yunfie.illustia.models.HomeFeedKind

data class HomeChromeState(
    val homeKind: HomeFeedKind = HomeFeedKind.Recommended,
    val homeNextUrl: String? = null,
    val timelineNextUrl: String? = null,
    val isHomeRefreshing: Boolean = false,
    val isHomePaginating: Boolean = false,
    val isTimelineRefreshing: Boolean = false,
    val isTimelinePaginating: Boolean = false,
    val isOfflineCached: Boolean = false,
    val selectedTab: Int = 0,
    val pendingHomeItemsCount: Int = 0,
)

data class NovelChromeState(
    val novelNextUrl: String? = null,
    val isNovelRefreshing: Boolean = false,
    val isNovelPaginating: Boolean = false,
)

data class RankingChromeState(
    val rankingMode: String = "day",
    val rankingNextUrl: String? = null,
)

data class BookmarkChromeState(
    val bookmarkNextUrl: String? = null,
    val timelineNextUrl: String? = null,
    val watchlistNextUrl: String? = null,
    val activeWatchlistTag: String? = null,
    val followingUsersNextUrl: String? = null,
    val selectedTab: Int = 1,
    val isBookmarkRefreshing: Boolean = false,
    val isBookmarkPaginating: Boolean = false,
    val isTimelineRefreshing: Boolean = false,
    val isTimelinePaginating: Boolean = false,
    val isFollowingRefreshing: Boolean = false,
    val isFollowingPaginating: Boolean = false,
)

data class PixivWebLoginRequest(
    val authorizationUrl: String,
    val codeVerifier: String,
)
