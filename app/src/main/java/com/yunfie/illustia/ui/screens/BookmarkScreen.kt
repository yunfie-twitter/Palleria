package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
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
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.OverlayIconCascadingDropdownMenu
import com.yunfie.illustia.ui.components.PrefetchPixivImages
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Community
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Tune
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal enum class BookmarkSort {
    Newest,
    Oldest,
    Popular,
    Title,
}

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
    var bookmarkSort by rememberSaveable { mutableStateOf(BookmarkSort.Newest) }
    var followingUserSort by rememberSaveable { mutableStateOf(FollowingUserSort.Newest) }
    var showSortPopup by remember { mutableStateOf(false) }
    val repository = remember(viewModel) { viewModel.uiRepository() }
    val watchlistStore = remember(repository) { WatchlistStore(repository) }
    val watchlistState by watchlistStore.state.collectAsStateWithLifecycle()
    val pagerState =
        rememberPagerState(
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
    val activeItems =
        when (selectedTopTab) {
            0 -> timelineItems
            1 -> sortedBookmarkItems
            else -> emptyList()
        }
    val prefetchUrls =
        remember(activeItems, feedHighQuality) {
            activeItems
                .asSequence()
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
    val performHaptic = rememberHapticFeedbackAction()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = stringResource(R.string.nav_bookmarks_full),
            largeTitle = stringResource(R.string.nav_bookmarks_full),
            scrollBehavior = scrollBehavior,
            modifier =
                Modifier.pointerInput(selectedTopTab) {
                    detectTapGestures {
                        performHaptic(AppHapticEffect.Click)
                        coroutineScope.launch {
                            scrollBehavior.state.heightOffset = 0f
                            scrollBehavior.state.contentOffset = 0f
                            when (selectedTopTab) {
                                0 -> viewModel.bookmarkTimelineGridState.animateScrollToItem(0)
                                1 -> viewModel.bookmarkMainGridState.animateScrollToItem(0)
                                2 -> viewModel.bookmarkWatchlistGridState.animateScrollToItem(0)
                                3 -> viewModel.bookmarkFollowingGridState.animateScrollToItem(0)
                            }
                        }
                    }
                },
            actions = {
                if (selectedTopTab == 1) {
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
                                                                bookmarkSort = BookmarkSort.Newest
                                                            },
                                                        ),
                                                        DropdownItem(
                                                            text = sortOldestLabel,
                                                            selected = bookmarkSort == BookmarkSort.Oldest,
                                                            onClick = {
                                                                performHaptic(AppHapticEffect.Toggle)
                                                                bookmarkSort = BookmarkSort.Oldest
                                                            },
                                                        ),
                                                        DropdownItem(
                                                            text = sortPopularLabel,
                                                            selected = bookmarkSort == BookmarkSort.Popular,
                                                            onClick = {
                                                                performHaptic(AppHapticEffect.Toggle)
                                                                bookmarkSort = BookmarkSort.Popular
                                                            },
                                                        ),
                                                        DropdownItem(
                                                            text = sortTitleLabel,
                                                            selected = bookmarkSort == BookmarkSort.Title,
                                                            onClick = {
                                                                performHaptic(AppHapticEffect.Toggle)
                                                                bookmarkSort = BookmarkSort.Title
                                                            },
                                                        ),
                                                    ),
                                            ),
                                        ),
                                ),
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
                    val sortOptions =
                        listOf(
                            stringResource(R.string.sort_date_desc),
                            stringResource(R.string.sort_date_asc),
                            stringResource(R.string.sort_name_asc),
                        )
                    Box {
                        IconButton(
                            onClick = {
                                performHaptic(AppHapticEffect.Click)
                                showSortPopup = true
                            },
                        ) {
                            Icon(
                                MiuixIcons.Filter,
                                contentDescription = stringResource(R.string.action_sort),
                            )
                        }
                        OverlayListPopup(
                            show = showSortPopup,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showSortPopup = false },
                        ) {
                            ListPopupColumn {
                                sortOptions.forEachIndexed { index, string ->
                                    DropdownImpl(
                                        text = string,
                                        optionSize = sortOptions.size,
                                        isSelected = followingUserSort.ordinal == index,
                                        index = index,
                                        onSelectedIndexChange = {
                                            performHaptic(AppHapticEffect.Toggle)
                                            followingUserSort = FollowingUserSort.entries[index]
                                            showSortPopup = false
                                        },
                                    )
                                }
                            }
                        }
                    }
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
                    selectedTab = selectedTopTab,
                    onSelect = { index ->
                        if (index != selectedTopTab) {
                            performHaptic(AppHapticEffect.Toggle)
                        }
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
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
                    0 -> {
                        BookmarkTimelineTab(
                            timelineItems = timelineItems,
                            loadState = loadState,
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
                        )
                    }

                    3 -> {
                        BookmarkFollowingTab(
                            settings = settings,
                            followingUsers = followingUsers,
                            sort = followingUserSort,
                            loadState = loadState,
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
