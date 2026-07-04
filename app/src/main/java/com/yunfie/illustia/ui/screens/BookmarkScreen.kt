package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.BookmarkChromeState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.WatchlistStore
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.performAppHapticFeedback
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class FollowingUserSort {
    Newest,
    Oldest,
    Name,
}

@Composable
fun BookmarkScreen(
    settings: AppSettings,
    loadState: LoadState,
    bookmarkItems: List<Illust>,
    timelineItems: List<Illust>,
    followingUsers: List<UserPreview>,
    chrome: BookmarkChromeState,
    viewModel: IllustiaViewModel,
    onOpenWatchlistSeries: (Long) -> Unit,
) {
    var followingUserSort by rememberSaveable { mutableStateOf(FollowingUserSort.Newest) }
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val watchlistStore = remember(repository) { WatchlistStore(repository) }
    val watchlistState by watchlistStore.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(
        initialPage = chrome.selectedTab,
        pageCount = { 4 },
    )
    val coroutineScope = rememberCoroutineScope()
    val selectedTopTab = pagerState.currentPage

    LaunchedEffect(selectedTopTab) {
        viewModel.updateBookmarkSelectedTab(selectedTopTab)
    }

    LaunchedEffect(selectedTopTab) {
        if (selectedTopTab == 2 && watchlistState.model == null && !watchlistState.isLoading) {
            watchlistStore.fetch()
        }
    }

    val feedHighQuality = remember(settings.highQualityImages, settings.feedPreviewQuality) {
        settings.highQualityImages && settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val activeItems = when (selectedTopTab) {
        0 -> timelineItems
        1 -> bookmarkItems
        else -> emptyList()
    }
    val prefetchUrls = remember(activeItems, feedHighQuality) {
        activeItems.asSequence()
            .take(16)
            .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
            .toList()
    }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)

    LaunchedEffect(selectedTopTab) {
        when (selectedTopTab) {
            0 -> if (timelineItems.isEmpty()) viewModel.refreshTimeline()
            1 -> if (bookmarkItems.isEmpty()) viewModel.refreshBookmarks()
            2 -> if (watchlistState.model == null && !watchlistState.isLoading) watchlistStore.fetch()
            3 -> if (followingUsers.isEmpty()) viewModel.refreshFollowingUsers()
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = androidx.compose.ui.res.stringResource(R.string.nav_bookmarks_full),
            largeTitle = androidx.compose.ui.res.stringResource(R.string.nav_bookmarks_full),
            scrollBehavior = scrollBehavior,
            actions = {
                if (selectedTopTab == 1) {
                    RestrictPill(
                        restrict = settings.bookmarkRestrict,
                        onClick = {
                            val next = if (settings.bookmarkRestrict == Restrict.Public) {
                                Restrict.Private
                            } else {
                                Restrict.Public
                            }
                            viewModel.updateRestrict(next)
                            viewModel.refreshBookmarks()
                        },
                    )
                }
                if (selectedTopTab == 2) {
                    IconButton(onClick = { coroutineScope.launch { watchlistStore.fetch() } }) {
                        Icon(MiuixIcons.Refresh, contentDescription = androidx.compose.ui.res.stringResource(R.string.dialog_reload))
                    }
                }
                if (selectedTopTab == 3) {
                    val newestLabel = androidx.compose.ui.res.stringResource(R.string.sort_date_desc)
                    val oldestLabel = androidx.compose.ui.res.stringResource(R.string.sort_date_asc)
                    val nameLabel = androidx.compose.ui.res.stringResource(R.string.sort_name_asc)
                    WindowIconDropdownMenu(
                        entry = DropdownEntry(
                            items = listOf(
                                DropdownItem(
                                    text = if (followingUserSort == FollowingUserSort.Newest) "✓ $newestLabel" else newestLabel,
                                    onClick = { followingUserSort = FollowingUserSort.Newest },
                                ),
                                DropdownItem(
                                    text = if (followingUserSort == FollowingUserSort.Oldest) "✓ $oldestLabel" else oldestLabel,
                                    onClick = { followingUserSort = FollowingUserSort.Oldest },
                                ),
                                DropdownItem(
                                    text = if (followingUserSort == FollowingUserSort.Name) "✓ $nameLabel" else nameLabel,
                                    onClick = { followingUserSort = FollowingUserSort.Name },
                                ),
                            ),
                        ),
                    ) {
                        Icon(MiuixIcons.Filter, contentDescription = androidx.compose.ui.res.stringResource(R.string.action_sort))
                    }
                }
                IconButton(onClick = {
                    performAppHapticFeedback(context, haptic, hapticMode)
                    when (selectedTopTab) {
                        0 -> viewModel.refreshTimeline()
                        2 -> coroutineScope.launch { watchlistStore.fetch() }
                        3 -> viewModel.refreshFollowingUsers()
                        else -> viewModel.refreshBookmarks()
                    }
                }) {
                    Icon(MiuixIcons.Refresh, contentDescription = androidx.compose.ui.res.stringResource(R.string.dialog_reload))
                }
            },
            bottomContent = {
                CompactBookmarkTabs(
                    selectedTab = selectedTopTab,
                    onSelect = { index -> coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                )
            },
        )
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = MiuixTheme.colorScheme.surface,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> BookmarkTimelineTab(
                        timelineItems = timelineItems,
                        loadState = loadState,
                        settings = settings,
                        feedHighQuality = feedHighQuality,
                        showAiBadge = showAiBadge,
                        viewModel = viewModel,
                        chrome = chrome,
                        scrollBehavior = scrollBehavior,
                    )
                    1 -> BookmarkMainTab(
                        bookmarkItems = bookmarkItems,
                        loadState = loadState,
                        settings = settings,
                        feedHighQuality = feedHighQuality,
                        showAiBadge = showAiBadge,
                        viewModel = viewModel,
                        chrome = chrome,
                        scrollBehavior = scrollBehavior,
                    )
                    2 -> BookmarkWatchlistTab(
                        settings = settings,
                        watchlistState = watchlistState,
                        watchlistStore = watchlistStore,
                        onOpenWatchlistSeries = onOpenWatchlistSeries,
                    )
                    3 -> BookmarkFollowingTab(
                        settings = settings,
                        followingUsers = followingUsers,
                        sort = followingUserSort,
                        loadState = loadState,
                        viewModel = viewModel,
                        chrome = chrome,
                    )
                }
            }
        }
    }
}
