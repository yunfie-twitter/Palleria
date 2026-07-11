package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen(
    items: List<Illust>,
    timelineItems: List<Illust>,
    loadState: LoadState,
    nextUrl: String?,
    timelineNextUrl: String?,
    settings: AppSettings,
    currentAccount: UserProfile?,
    viewModel: IllustiaViewModel,
    onSearch: () -> Unit,
    onOpenNovels: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = HomeTab.Feed.ordinal,
        pageCount = { HomeTab.entries.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = HomeTab.entries[pagerState.currentPage]

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            HomeTab.Feed -> Unit
            HomeTab.Following -> {
                if (timelineItems.isEmpty()) {
                    viewModel.refreshTimeline()
                }
            }
        }
    }

    val scheme = MiuixTheme.colorScheme
    val scrollBehavior = MiuixScrollBehavior()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.surface),
    ) {
        TopAppBar(
            title = stringResource(R.string.nav_home),
            largeTitle = stringResource(R.string.nav_home),
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = viewModel::openAccountSwitcher) {
                    HomeAccountAvatar(account = currentAccount)
                }
            },
            actions = {
                if (settings.shortsFeedEnabled) {
                    IconButton(onClick = onSearch) {
                        Icon(MiuixIcons.Search, contentDescription = stringResource(R.string.nav_search))
                    }
                }
                IconButton(onClick = onOpenNovels) {
                    Icon(
                        MiuixIcons.Photos,
                        contentDescription = stringResource(R.string.nav_novel),
                    )
                }
                IconButton(
                    onClick = {
                        when (selectedTab) {
                            HomeTab.Feed -> viewModel.refreshHome()
                            HomeTab.Following -> viewModel.refreshTimeline()
                        }
                    },
                ) {
                    Icon(
                        MiuixIcons.Refresh,
                        contentDescription = stringResource(R.string.dialog_reload),
                    )
                }
            },
            bottomContent = {
                HomeTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    onTabSelected = { index ->
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                )
            },
        )
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
