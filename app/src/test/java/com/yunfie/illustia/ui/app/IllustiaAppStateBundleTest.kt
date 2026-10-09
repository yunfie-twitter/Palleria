package com.yunfie.illustia.ui.app

import com.yunfie.illustia.BookmarkChromeState
import com.yunfie.illustia.HomeChromeState
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.settings.AppSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class IllustiaAppStateBundleTest :
    FunSpec({
        test("derives navigation state from one UI state snapshot") {
            val settings = AppSettings(shortsFeedEnabled = true)
            val state =
                IllustiaUiState(
                    settings = settings,
                    loadState = LoadState.Loading,
                    homeKind = HomeFeedKind.New,
                    homeNextUrl = "home-next",
                    timelineNextUrl = "timeline-next",
                    novelNextUrl = "novel-next",
                    rankingMode = "week",
                    rankingNextUrl = "ranking-next",
                    bookmarkNextUrl = "bookmark-next",
                    watchlistNextUrl = "watchlist-next",
                    activeWatchlistTag = "tag",
                    followingUsersNextUrl = "users-next",
                    bookmarkSelectedTab = 2,
                    isOfflineCached = true,
                )

            val bundle = IllustiaAppStateBundle(state)

            bundle.state shouldBe state
            bundle.settings shouldBe settings
            bundle.loadState shouldBe LoadState.Loading
            bundle.isOfflineCached shouldBe true
            bundle.homeChrome shouldBe
                HomeChromeState(
                    homeKind = HomeFeedKind.New,
                    homeNextUrl = "home-next",
                    timelineNextUrl = "timeline-next",
                    isOfflineCached = true,
                )
            bundle.novelChrome.novelNextUrl shouldBe "novel-next"
            bundle.rankingChrome.rankingMode shouldBe "week"
            bundle.rankingChrome.rankingNextUrl shouldBe "ranking-next"
            bundle.bookmarkChrome shouldBe
                BookmarkChromeState(
                    bookmarkNextUrl = "bookmark-next",
                    timelineNextUrl = "timeline-next",
                    watchlistNextUrl = "watchlist-next",
                    activeWatchlistTag = "tag",
                    followingUsersNextUrl = "users-next",
                    selectedTab = 2,
                )
        }

        test("centralizes high quality feed selection") {
            AppSettings(
                highQualityImages = true,
                feedPreviewQuality = "high",
            ).useHighQualityFeedImages shouldBe true

            AppSettings(
                highQualityImages = true,
                feedPreviewQuality = "low",
            ).useHighQualityFeedImages shouldBe false

            AppSettings(
                highQualityImages = false,
                feedPreviewQuality = "high",
            ).useHighQualityFeedImages shouldBe false
        }
    })
