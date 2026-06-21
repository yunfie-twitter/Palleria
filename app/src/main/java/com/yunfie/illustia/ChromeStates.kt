package com.yunfie.illustia

import com.yunfie.illustia.data.HomeFeedKind

data class HomeChromeState(
    val homeKind: HomeFeedKind = HomeFeedKind.Recommended,
    val homeNextUrl: String? = null,
    val timelineNextUrl: String? = null,
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
)

data class PixivWebLoginRequest(
    val authorizationUrl: String,
    val codeVerifier: String,
)
