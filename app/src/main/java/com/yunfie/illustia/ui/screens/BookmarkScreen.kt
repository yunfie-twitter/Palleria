package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.BookmarkChromeState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.data.LoadState
import com.yunfie.illustia.data.Restrict
import com.yunfie.illustia.data.UserPreview
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.squircle.squircleSurface

private enum class FollowingUserSort {
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
) {
    var followingUserSort by rememberSaveable { mutableStateOf(FollowingUserSort.Newest) }
    val pagerState = rememberPagerState(
        initialPage = chrome.selectedTab,
        pageCount = { 3 },
    )
    val coroutineScope = rememberCoroutineScope()
    val selectedTopTab = pagerState.currentPage

    LaunchedEffect(selectedTopTab) {
        viewModel.updateBookmarkSelectedTab(selectedTopTab)
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
            2 -> if (followingUsers.isEmpty()) viewModel.refreshFollowingUsers()
        }
    }

    val scrollBehavior = MiuixScrollBehavior()
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface),
    ) {
        TopAppBar(
            title = stringResource(R.string.nav_bookmarks_full),
            largeTitle = stringResource(R.string.nav_bookmarks_full),
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
                    val newestLabel = stringResource(R.string.sort_date_desc)
                    val oldestLabel = stringResource(R.string.sort_date_asc)
                    val nameLabel = stringResource(R.string.sort_name_asc)
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
                        Icon(MiuixIcons.Filter, contentDescription = stringResource(R.string.action_sort))
                    }
                }
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    when (selectedTopTab) {
                        0 -> viewModel.refreshTimeline()
                        2 -> viewModel.refreshFollowingUsers()
                        else -> viewModel.refreshBookmarks()
                    }
                }) {
                    Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
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
                    else -> BookmarkFollowingTab(
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

@Composable
private fun BookmarkMainTab(
    bookmarkItems: List<Illust>,
    loadState: LoadState,
    settings: AppSettings,
    feedHighQuality: Boolean,
    showAiBadge: Boolean,
    viewModel: IllustiaViewModel,
    chrome: BookmarkChromeState,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val gridState = viewModel.bookmarkMainGridState
    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && bookmarkItems.isNotEmpty(),
        onRefresh = { viewModel.refreshBookmarks() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (bookmarkItems.isEmpty() && loadState == LoadState.Loading) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton() }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            }
            if (bookmarkItems.isEmpty() && loadState != LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.bookmark_empty))
                }
            }
            gridItems(bookmarkItems, key = { it.id }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId) } }
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }
                IllustCard(illust = illust, onBookmark = onBookmark, onClick = onClick, onLongClick = onLongClick, highQualityImages = feedHighQuality, showAiBadge = showAiBadge)
            }
            if (chrome.bookmarkNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = viewModel::loadMoreBookmarks, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) }
                }
            }
        }
    }
}

@Composable
private fun BookmarkTimelineTab(
    timelineItems: List<Illust>,
    loadState: LoadState,
    settings: AppSettings,
    feedHighQuality: Boolean,
    showAiBadge: Boolean,
    viewModel: IllustiaViewModel,
    chrome: BookmarkChromeState,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val gridState = viewModel.bookmarkTimelineGridState
    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && timelineItems.isNotEmpty(),
        onRefresh = { viewModel.refreshTimeline() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (timelineItems.isEmpty() && loadState == LoadState.Loading) {
                items(6, contentType = { "illust_skeleton" }) { IllustCardSkeleton() }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            }
            if (timelineItems.isEmpty() && loadState != LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.bookmark_following_empty))
                }
            }
            gridItems(timelineItems, key = { "timeline_${it.id}" }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId) } }
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }
                IllustCard(illust = illust, onBookmark = onBookmark, onClick = onClick, onLongClick = onLongClick, highQualityImages = feedHighQuality, showAiBadge = showAiBadge)
            }
            if (chrome.timelineNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = viewModel::loadMoreTimeline, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) }
                }
            }
        }
    }
}

@Composable
private fun BookmarkFollowingTab(
    followingUsers: List<UserPreview>,
    sort: FollowingUserSort,
    loadState: LoadState,
    viewModel: IllustiaViewModel,
    chrome: BookmarkChromeState,
) {
    val gridState = viewModel.bookmarkFollowingGridState
    val sortedUsers = remember(followingUsers, sort) {
        when (sort) {
            FollowingUserSort.Newest -> followingUsers
            FollowingUserSort.Oldest -> followingUsers.asReversed()
            FollowingUserSort.Name -> followingUsers.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.account } },
            )
        }
    }
    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && followingUsers.isNotEmpty(),
        onRefresh = { viewModel.refreshFollowingUsers() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(1),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = MainNavigationContentPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { StateBanner(loadState) }
            if (followingUsers.isEmpty() && loadState != LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.following_users_empty))
                }
            }
            gridItems(sortedUsers, key = { "follow_user_${it.id}" }, contentType = { "user_card" }) { user ->
                UserResultCard(user = user, onClick = { viewModel.openUserPage(user) })
            }
            if (chrome.followingUsersNextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(onClick = viewModel::loadMoreFollowingUsers, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_load_more)) }
                }
            }
        }
    }
}

@Composable
private fun CompactBookmarkTabs(
    selectedTab: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    TabRowWithContour(
        tabs = listOf(stringResource(R.string.bookmark_tab_timeline), stringResource(R.string.bookmark_tab_bookmarks), stringResource(R.string.bookmark_tab_following)),
        selectedTabIndex = selectedTab,
        onTabSelected = onSelect,
        modifier = modifier,
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = scheme.surfaceContainer.copy(alpha = 0.44f),
            contentColor = scheme.onSurfaceVariantSummary,
            selectedBackgroundColor = scheme.surfaceContainerHigh,
            selectedContentColor = scheme.onBackground,
        ),
        minWidth = 86.dp,
        maxWidth = 116.dp,
        height = 45.dp,
    )
}

@Composable
private fun RestrictPill(
    restrict: Restrict,
    onClick: () -> Unit,
) {
    val icon = if (restrict == Restrict.Public) MiuixIcons.Community else MiuixIcons.Lock
    Box(
        modifier = Modifier
            .height(32.dp)
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 16.dp)
            .miuixClickable(haptic = true, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MiuixTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(restrict.labelResId),
                color = MiuixTheme.colorScheme.onBackground,
                style = MiuixTheme.textStyles.footnote1,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
