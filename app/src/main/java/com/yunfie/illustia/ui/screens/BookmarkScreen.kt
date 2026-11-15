package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.BookmarkChromeState
import com.yunfie.illustia.BookmarkSort
import com.yunfie.illustia.FollowingUserSort
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.WatchlistStore
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.app.AppTab
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.OverlayIconCascadingDropdownMenu
import com.yunfie.illustia.ui.components.PrefetchIllustGridImages
import com.yunfie.illustia.ui.components.ScrollBlurScaffold
import com.yunfie.illustia.ui.components.onTopBarTap
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.ui.components.smoothScrollToTop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BookmarkScreen(
    settings: AppSettings,
    loadState: LoadState,
    timelineLoadState: LoadState = loadState,
    followingUsersLoadState: LoadState = loadState,
    bookmarkItems: List<Illust>,
    bookmarkTag: String?,
    timelineItems: List<Illust>,
    followingUsers: List<UserPreview>,
    chrome: BookmarkChromeState,
    viewModel: IllustiaViewModel,
    onOpenWatchlistSeries: (Long) -> Unit,
    onOpenBookmarkTagSort: () -> Unit,
) {
    val bookmarkSort = chrome.bookmarkSort
    var followingUserSort by rememberSaveable { mutableStateOf(FollowingUserSort.Newest) }
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val context = LocalContext.current
    val watchlistStore = remember(repository, context) { WatchlistStore(repository, context) }
    val watchlistState by watchlistStore.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, watchlistStore) {
        viewModel.watchlistRefreshRequests.collect { watchlistStore.fetch() }
    }
    val pagerState =
        rememberPagerState(
            initialPage = chrome.selectedTab,
            pageCount = { 4 },
        )
    val coroutineScope = rememberCoroutineScope()
    // Drive the tab indicator from targetPage so it reacts immediately on tap,
    // like RankingScreen. Commit selected tab only when the pager settles, so
    // swiping does not recompose the whole screen for every intermediate page.
    val indicatorTab = pagerState.targetPage.coerceIn(0, 3)
    val selectedTopTab = pagerState.settledPage

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { viewModel.updateBookmarkSelectedTab(it) }
    }

    LaunchedEffect(selectedTopTab) {
        if (selectedTopTab == 2 && watchlistState.model == null && !watchlistState.isLoading) {
            watchlistStore.fetch()
        }
    }

    val feedHighQuality = settings.useHighQualityFeedImages
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val sortedBookmarkItems =
        remember(bookmarkItems, bookmarkSort) {
            when (bookmarkSort) {
                BookmarkSort.Newest -> bookmarkItems
                BookmarkSort.Oldest -> bookmarkItems.reversed()
                BookmarkSort.Popular -> bookmarkItems.sortedByDescending { it.totalBookmarks }
                BookmarkSort.Title -> bookmarkItems.sortedBy { it.title }
            }
        }
    when (selectedTopTab) {
        0 -> {
            PrefetchIllustGridImages(
                items = timelineItems,
                gridState = viewModel.bookmarkTimelineGridState,
                enabled = settings.prefetchImages,
                highQualityImages = feedHighQuality,
                keyPrefix = "timeline_",
            )
        }

        1 -> {
            PrefetchIllustGridImages(
                items = sortedBookmarkItems,
                gridState = viewModel.bookmarkMainGridState,
                enabled = settings.prefetchImages,
                highQualityImages = feedHighQuality,
            )
        }
    }

    LaunchedEffect(selectedTopTab) {
        when (selectedTopTab) {
            0 -> if (timelineItems.isEmpty()) viewModel.refreshTimeline()
            1 -> if (bookmarkItems.isEmpty()) viewModel.refreshBookmarks()
            2 -> if (watchlistState.model == null && !watchlistState.isLoading) watchlistStore.fetch()
            3 -> if (followingUsers.isEmpty()) viewModel.refreshFollowingUsers()
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val performHaptic = rememberHapticFeedbackAction()

    LaunchedEffect(viewModel) {
        viewModel.tabScrollToTopRequests.collect { tab ->
            if (tab == AppTab.Bookmarks) {
                val targetState =
                    when (pagerState.currentPage) {
                        0 -> viewModel.bookmarkTimelineGridState
                        1 -> viewModel.bookmarkMainGridState
                        2 -> viewModel.bookmarkWatchlistGridState
                        3 -> viewModel.bookmarkFollowingGridState
                        else -> null
                    }
                targetState?.smoothScrollToTop(scrollBehavior)
            }
        }
    }

    ScrollBlurScaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
        enabled = settings.isFeatureEnabled(FeatureFlag.TopScrollBlur),
        scrollBehavior = scrollBehavior,
        header = {
            TopAppBar(
                color = if (settings.isFeatureEnabled(FeatureFlag.TopScrollBlur)) Color.Transparent else MiuixTheme.colorScheme.surface,
                title = stringResource(R.string.nav_bookmarks_full),
                largeTitle = stringResource(R.string.nav_bookmarks_full),
                scrollBehavior = scrollBehavior,
                modifier =
                    Modifier.onTopBarTap(
                        navIconWidth = 0.dp,
                        actionsWidth = 144.dp,
                        hasBottomContent = true,
                    ) {
                        performHaptic(AppHapticEffect.Click)
                        coroutineScope.launch {
                            val targetState =
                                when (pagerState.currentPage) {
                                    0 -> viewModel.bookmarkTimelineGridState
                                    1 -> viewModel.bookmarkMainGridState
                                    2 -> viewModel.bookmarkWatchlistGridState
                                    3 -> viewModel.bookmarkFollowingGridState
                                    else -> null
                                }
                            targetState?.smoothScrollToTop(scrollBehavior)
                        }
                    },
                actions = {
                    if (selectedTopTab == 1) {
                        val tagSortLabel = stringResource(R.string.bookmark_tag_sort_button)
                        val publicLabel = stringResource(R.string.restrict_public)
                        val privateLabel = stringResource(R.string.restrict_private)
                        val sortLabel = stringResource(R.string.action_sort)
                        val sortNewestLabel = stringResource(R.string.sort_date_desc)
                        val sortOldestLabel = stringResource(R.string.sort_date_asc)
                        val sortPopularLabel = stringResource(R.string.sort_popular_desc)
                        val sortTitleLabel = stringResource(R.string.sort_name_asc)

                        val bookmarkMenuEntries =
                            remember(
                                settings.bookmarkRestrict,
                                bookmarkSort,
                                publicLabel,
                                privateLabel,
                                sortLabel,
                                sortNewestLabel,
                                sortOldestLabel,
                                sortPopularLabel,
                                sortTitleLabel,
                            ) {
                                listOf(
                                    DropdownEntry(
                                        items =
                                            listOf(
                                                DropdownItem(
                                                    text = publicLabel,
                                                    selected = settings.bookmarkRestrict == Restrict.Public,
                                                    onClick = {
                                                        if (settings.bookmarkRestrict != Restrict.Public) {
                                                            performHaptic(AppHapticEffect.Toggle)
                                                            viewModel.updateRestrict(Restrict.Public)
                                                            viewModel.refreshBookmarks()
                                                        }
                                                    },
                                                ),
                                                DropdownItem(
                                                    text = privateLabel,
                                                    selected = settings.bookmarkRestrict == Restrict.Private,
                                                    onClick = {
                                                        if (settings.bookmarkRestrict != Restrict.Private) {
                                                            performHaptic(AppHapticEffect.Toggle)
                                                            viewModel.updateRestrict(Restrict.Private)
                                                            viewModel.refreshBookmarks()
                                                        }
                                                    },
                                                ),
                                            ),
                                    ),
                                    DropdownEntry(
                                        items =
                                            listOf(
                                                DropdownItem(
                                                    text = sortLabel,
                                                    children =
                                                        listOf(
                                                            DropdownItem(
                                                                text = sortNewestLabel,
                                                                selected = bookmarkSort == BookmarkSort.Newest,
                                                                onClick = {
                                                                    performHaptic(AppHapticEffect.Toggle)
                                                                    viewModel.updateBookmarkSort(BookmarkSort.Newest)
                                                                },
                                                            ),
                                                            DropdownItem(
                                                                text = sortOldestLabel,
                                                                selected = bookmarkSort == BookmarkSort.Oldest,
                                                                onClick = {
                                                                    performHaptic(AppHapticEffect.Toggle)
                                                                    viewModel.updateBookmarkSort(BookmarkSort.Oldest)
                                                                },
                                                            ),
                                                            DropdownItem(
                                                                text = sortPopularLabel,
                                                                selected = bookmarkSort == BookmarkSort.Popular,
                                                                onClick = {
                                                                    performHaptic(AppHapticEffect.Toggle)
                                                                    viewModel.updateBookmarkSort(BookmarkSort.Popular)
                                                                },
                                                            ),
                                                            DropdownItem(
                                                                text = sortTitleLabel,
                                                                selected = bookmarkSort == BookmarkSort.Title,
                                                                onClick = {
                                                                    performHaptic(AppHapticEffect.Toggle)
                                                                    viewModel.updateBookmarkSort(BookmarkSort.Title)
                                                                },
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                                )
                            }

                        IconButton(onClick = {
                            performHaptic(AppHapticEffect.Click)
                            onOpenBookmarkTagSort()
                        }, modifier = Modifier.semantics { contentDescription = tagSortLabel }) {
                            Icon(
                                imageVector = MiuixIcons.Folder,
                                contentDescription = tagSortLabel,
                                tint =
                                    if (bookmarkTag != null) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurface
                                    },
                            )
                        }
                        OverlayIconCascadingDropdownMenu(
                            entries = bookmarkMenuEntries,
                            icon = MiuixIcons.Tune,
                            backgroundColor = Color.Transparent,
                            contentColor = MiuixTheme.colorScheme.onBackground,
                            contentDescription = stringResource(R.string.nav_bookmarks_full),
                        )
                    }
                    if (selectedTopTab == 3) {
                        val publicLabel = stringResource(R.string.restrict_public)
                        val privateLabel = stringResource(R.string.restrict_private)
                        val sortLabel = stringResource(R.string.action_sort)
                        val sortNewestLabel = stringResource(R.string.sort_date_desc)
                        val sortOldestLabel = stringResource(R.string.sort_date_asc)
                        val sortNameLabel = stringResource(R.string.sort_name_asc)
                        val followingSortEntries =
                            remember(
                                settings.followRestrict,
                                followingUserSort,
                                publicLabel,
                                privateLabel,
                                sortLabel,
                                sortNewestLabel,
                                sortOldestLabel,
                                sortNameLabel,
                            ) {
                                listOf(
                                    DropdownEntry(
                                        items =
                                            listOf(
                                                DropdownItem(
                                                    text = publicLabel,
                                                    selected = settings.followRestrict == Restrict.Public,
                                                    onClick = {
                                                        if (settings.followRestrict != Restrict.Public) {
                                                            performHaptic(AppHapticEffect.Toggle)
                                                            viewModel.updateFollowRestrict(Restrict.Public)
                                                            viewModel.refreshTimeline(forceRefresh = true)
                                                            viewModel.refreshFollowingUsers(forceRefresh = true)
                                                        }
                                                    },
                                                ),
                                                DropdownItem(
                                                    text = privateLabel,
                                                    selected = settings.followRestrict == Restrict.Private,
                                                    onClick = {
                                                        if (settings.followRestrict != Restrict.Private) {
                                                            performHaptic(AppHapticEffect.Toggle)
                                                            viewModel.updateFollowRestrict(Restrict.Private)
                                                            viewModel.refreshTimeline(forceRefresh = true)
                                                            viewModel.refreshFollowingUsers(forceRefresh = true)
                                                        }
                                                    },
                                                ),
                                            ),
                                    ),
                                    DropdownEntry(
                                        items =
                                            listOf(
                                                DropdownItem(
                                                    text = sortLabel,
                                                    children =
                                                        listOf(
                                                            DropdownItem(
                                                                text = sortNewestLabel,
                                                                selected = followingUserSort == FollowingUserSort.Newest,
                                                                onClick = {
                                                                    performHaptic(AppHapticEffect.Toggle)
                                                                    followingUserSort = FollowingUserSort.Newest
                                                                },
                                                            ),
                                                            DropdownItem(
                                                                text = sortOldestLabel,
                                                                selected = followingUserSort == FollowingUserSort.Oldest,
                                                                onClick = {
                                                                    performHaptic(AppHapticEffect.Toggle)
                                                                    followingUserSort = FollowingUserSort.Oldest
                                                                },
                                                            ),
                                                            DropdownItem(
                                                                text = sortNameLabel,
                                                                selected = followingUserSort == FollowingUserSort.Name,
                                                                onClick = {
                                                                    performHaptic(AppHapticEffect.Toggle)
                                                                    followingUserSort = FollowingUserSort.Name
                                                                },
                                                            ),
                                                        ),
                                                ),
                                            ),
                                    ),
                                )
                            }
                        OverlayIconCascadingDropdownMenu(
                            entries = followingSortEntries,
                            icon = MiuixIcons.Tune,
                            backgroundColor = Color.Transparent,
                            contentColor = MiuixTheme.colorScheme.onBackground,
                            contentDescription = sortLabel,
                        )
                    }
                    IconButton(onClick = {
                        performHaptic(AppHapticEffect.Click)
                        when (selectedTopTab) {
                            0 -> viewModel.refreshTimeline(forceRefresh = true)
                            2 -> coroutineScope.launch { watchlistStore.fetch() }
                            3 -> viewModel.refreshFollowingUsers(forceRefresh = true)
                            else -> viewModel.refreshBookmarks(forceRefresh = true)
                        }
                    }) {
                        Icon(
                            MiuixIcons.Refresh,
                            contentDescription =
                                androidx.compose.ui.res
                                    .stringResource(R.string.dialog_reload),
                        )
                    }
                },
                bottomContent = {
                    CompactBookmarkTabs(
                        selectedTab = indicatorTab,
                        onSelect = { index ->
                            if (index != pagerState.currentPage) {
                                performHaptic(AppHapticEffect.Toggle)
                                coroutineScope.launch { pagerState.animateScrollToPage(index) }
                            } else {
                                performHaptic(AppHapticEffect.Click)
                                coroutineScope.launch {
                                    val targetState =
                                        when (index) {
                                            0 -> viewModel.bookmarkTimelineGridState
                                            1 -> viewModel.bookmarkMainGridState
                                            2 -> viewModel.bookmarkWatchlistGridState
                                            3 -> viewModel.bookmarkFollowingGridState
                                            else -> null
                                        }
                                    targetState?.smoothScrollToTop(scrollBehavior)
                                }
                            }
                        },
                        isBlurEnabled = settings.isFeatureEnabled(FeatureFlag.TopScrollBlur),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                    )
                },
            )
        },
    ) {
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = MiuixTheme.colorScheme.surface,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                when (page) {
                    0 -> {
                        BookmarkTimelineTab(
                            timelineItems = timelineItems,
                            loadState = timelineLoadState,
                            settings = settings,
                            feedHighQuality = feedHighQuality,
                            showAiBadge = showAiBadge,
                            viewModel = viewModel,
                            chrome = chrome,
                            scrollBehavior = scrollBehavior,
                        )
                    }

                    1 -> {
                        BookmarkMainTab(
                            bookmarkItems = sortedBookmarkItems,
                            loadState = loadState,
                            settings = settings,
                            feedHighQuality = feedHighQuality,
                            showAiBadge = showAiBadge,
                            viewModel = viewModel,
                            chrome = chrome,
                            scrollBehavior = scrollBehavior,
                        )
                    }

                    2 -> {
                        BookmarkWatchlistTab(
                            settings = settings,
                            watchlistState = watchlistState,
                            watchlistStore = watchlistStore,
                            onOpenWatchlistSeries = onOpenWatchlistSeries,
                            scrollBehavior = scrollBehavior,
                            gridState = viewModel.bookmarkWatchlistGridState,
                            onColumnsChange = viewModel::updateVerticalColumnCount,
                        )
                    }

                    3 -> {
                        BookmarkFollowingTab(
                            settings = settings,
                            followingUsers = followingUsers,
                            sort = followingUserSort,
                            loadState = followingUsersLoadState,
                            viewModel = viewModel,
                            chrome = chrome,
                            scrollBehavior = scrollBehavior,
                        )
                    }
                }
            }
        }
    }
}
