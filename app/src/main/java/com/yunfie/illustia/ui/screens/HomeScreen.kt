package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.HomeFeedKind
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.data.LoadState
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.*
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Refresh

// ホーム画面のタブ定義
private enum class HomeTab(@androidx.annotation.StringRes val labelResId: Int) {
    Feed(R.string.home_tab_feed),
    Following(R.string.home_tab_following),
}

@Composable
fun HomeScreen(
    items: List<Illust>,
    timelineItems: List<Illust>,
    loadState: LoadState,
    nextUrl: String?,
    timelineNextUrl: String?,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onSearch: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Feed) }
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = HomeTab.entries.indexOf(selectedTab),
        pageCount = { HomeTab.entries.size },
    )

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = HomeTab.entries[pagerState.currentPage]
    }

    // タブ切り替え時にデータを自動取得
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            HomeTab.Feed -> {
                // フィードはすでにロード済みの場合はスキップ
            }
            HomeTab.Following -> {
                if (timelineItems.isEmpty()) {
                    viewModel.refreshTimeline()
                }
            }
        }
    }

    val scheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = scheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.nav_home),
                largeTitle = stringResource(R.string.nav_home),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            when (selectedTab) {
                                HomeTab.Feed -> viewModel.refreshHome()
                                HomeTab.Following -> viewModel.refreshTimeline()
                            }
                        },
                    ) {
                        Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.dialog_reload))
                    }
                },
                bottomContent = {
                    TabRow(
                        tabs = HomeTab.entries.map { stringResource(it.labelResId) },
                        selectedTabIndex = HomeTab.entries.indexOf(selectedTab),
                        onTabSelected = { index ->
                            selectedTab = HomeTab.entries[index]
                            coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    )
                },
            )
        },
    ) { scaffoldPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
            color = scheme.surface,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (HomeTab.entries[page]) {
                    HomeTab.Feed -> FeedTabContent(
                        items = items,
                        loadState = loadState,
                        nextUrl = nextUrl,
                        settings = settings,
                        viewModel = viewModel,
                        scrollBehavior = scrollBehavior,
                    )
                    HomeTab.Following -> FollowingTabContent(
                        items = timelineItems,
                        loadState = loadState,
                        nextUrl = timelineNextUrl,
                        settings = settings,
                        viewModel = viewModel,
                        scrollBehavior = scrollBehavior,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedTabContent(
    items: List<Illust>,
    loadState: LoadState,
    nextUrl: String?,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val feedHighQuality = remember(settings.highQualityImages, settings.feedPreviewQuality) {
        settings.highQualityImages && settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val prefetchUrls = remember(items, feedHighQuality) {
        items.asSequence()
            .take(16)
            .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
            .toList()
    }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)

    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && items.isNotEmpty(),
        onRefresh = { viewModel.refreshHome() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StateBanner(loadState)
            }

            if (items.isEmpty() && loadState == LoadState.Idle) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.home_feed_loading))
                }
            }

            gridItems(items, key = { it.id }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId) } }
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }

                IllustCard(
                    illust = illust,
                    onBookmark = onBookmark,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                )
            }

            if (nextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreHome,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowingTabContent(
    items: List<Illust>,
    loadState: LoadState,
    nextUrl: String?,
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    scrollBehavior: ScrollBehavior = MiuixScrollBehavior(),
) {
    val feedHighQuality = remember(settings.highQualityImages, settings.feedPreviewQuality) {
        settings.highQualityImages && settings.feedPreviewQuality != "low"
    }
    val showAiBadge = remember(settings.showAiBadge) { settings.showAiBadge }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) {
        LazyGridState()
    }
    val prefetchUrls = remember(items, feedHighQuality) {
        items.asSequence()
            .take(16)
            .map { if (feedHighQuality) it.previewUrl else it.thumbnailUrl }
            .toList()
    }
    PrefetchPixivImages(prefetchUrls, enabled = settings.prefetchImages)

    PullToRefresh(
        isRefreshing = loadState == LoadState.Loading && items.isNotEmpty(),
        onRefresh = { viewModel.refreshTimeline() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(settings)),
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 14.dp, end = 14.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StateBanner(loadState)
            }

            if (items.isEmpty() && loadState != LoadState.Loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.home_following_empty))
                }
            }

            gridItems(items, key = { "tl_${it.id}" }, contentType = { "illust_card" }) { illust ->
                val illustId = illust.id
                val onBookmark = remember(illustId) { { viewModel.toggleBookmark(illustId) } }
                val onClick = remember(illustId) { { viewModel.openIllust(illustId) } }
                val onLongClick = remember(illustId) { { viewModel.onIllustLongPress(illustId) } }

                IllustCard(
                    illust = illust,
                    onBookmark = onBookmark,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    highQualityImages = feedHighQuality,
                    showAiBadge = showAiBadge,
                )
            }

            if (nextUrl != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Button(
                        onClick = viewModel::loadMoreTimeline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_load_more))
                    }
                }
            }
        }
    }
}
