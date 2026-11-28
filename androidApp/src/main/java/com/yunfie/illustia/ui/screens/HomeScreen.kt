package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.home.HomeActions
import com.yunfie.illustia.ui.scroll.ScrollPositionProbe
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import com.yunfie.illustia.ui.screens.home.HomeScreen as SharedHomeScreen

@Composable
fun HomeScreen(
    items: List<Illust>,
    timelineItems: List<Illust>,
    loadState: LoadState,
    timelineLoadState: LoadState = loadState,
    nextUrl: String?,
    timelineNextUrl: String?,
    settings: AppSettings,
    currentAccount: UserProfile?,
    viewModel: IllustiaViewModel,
    scrollBehavior: ScrollBehavior,
    onSearch: () -> Unit,
    onOpenNovels: () -> Unit,
    isHomeRefreshing: Boolean = false,
    isHomePaginating: Boolean = false,
    isTimelineRefreshing: Boolean = false,
    isTimelinePaginating: Boolean = false,
    isOfflineCached: Boolean = false,
    initialTab: Int = 0,
    pendingHomeItemsCount: Int = 0,
) {
    val actions =
        remember(viewModel) {
            object : HomeActions {
                override fun updateHomeSelectedTab(index: Int) = viewModel.updateHomeSelectedTab(index)

                override fun refreshHome(forceRefresh: Boolean) = viewModel.refreshHome(forceRefresh = forceRefresh)

                override fun loadMoreHome() = viewModel.loadMoreHome()

                override fun refreshTimeline(forceRefresh: Boolean) = viewModel.refreshTimeline(forceRefresh = forceRefresh)

                override fun loadMoreTimeline() = viewModel.loadMoreTimeline()

                override fun applyPendingHomeItems() = viewModel.applyPendingHomeItems()

                override fun updateVerticalColumnCount(count: Int) = viewModel.updateVerticalColumnCount(count)

                override fun toggleBookmark(illust: Illust) = viewModel.toggleBookmark(illust)

                override fun openBookmarkTagPicker(illust: Illust) = viewModel.openBookmarkTagPicker(illust)

                override fun openIllust(illust: Illust) = viewModel.openIllust(illust)

                override fun onIllustLongPress(illustId: Long) = viewModel.onIllustLongPress(illustId)

                override fun openAccountSwitcher() = viewModel.openAccountSwitcher()

                override fun openNotifications() = viewModel.openNotifications()

                override fun onHomeScrollProbeChanged(probe: ScrollPositionProbe) {
                    viewModel.homeScrollProbe = probe
                }
            }
        }
    SharedHomeScreen(
        items = items,
        timelineItems = timelineItems,
        loadState = loadState,
        timelineLoadState = timelineLoadState,
        nextUrl = nextUrl,
        timelineNextUrl = timelineNextUrl,
        settings = settings,
        currentAccount = currentAccount,
        actions = actions,
        scrollBehavior = scrollBehavior,
        onSearch = onSearch,
        onOpenNovels = onOpenNovels,
        isHomeRefreshing = isHomeRefreshing,
        isHomePaginating = isHomePaginating,
        isTimelineRefreshing = isTimelineRefreshing,
        isTimelinePaginating = isTimelinePaginating,
        isOfflineCached = isOfflineCached,
        initialTab = initialTab,
        pendingHomeItemsCount = pendingHomeItemsCount,
    )
}
