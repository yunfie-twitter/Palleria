package com.yunfie.illustia.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import com.yunfie.illustia.ui.components.smoothScrollToTop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Messages
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
    scrollBehavior: ScrollBehavior,
    onSearch: () -> Unit,
    onOpenNovels: () -> Unit,
) {
    val pagerState =
        rememberPagerState(
            initialPage = HomeTab.Feed.ordinal,
            pageCount = { HomeTab.entries.size },
        )
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = HomeTab.entries[pagerState.currentPage]

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            HomeTab.Feed -> {
                if (items.isEmpty()) {
                    viewModel.refreshHome()
                }
            }

            HomeTab.Following -> {
                if (timelineItems.isEmpty()) {
                    viewModel.refreshTimeline()
                }
            }
        }
    }

    val scheme = MiuixTheme.colorScheme
    val context = LocalContext.current
    val isDarkTheme = scheme.surface.luminance() < 0.5f
    DisposableEffect(isDarkTheme) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
        onDispose {}
    }
    val performHaptic = rememberHapticFeedbackAction()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(scheme.surface),
    ) {
        TopAppBar(
            title = stringResource(R.string.nav_home),
            largeTitle = stringResource(R.string.nav_home),
            scrollBehavior = scrollBehavior,
            modifier =
                Modifier.pointerInput(Unit) {
                    detectTapGestures {
                        performHaptic(AppHapticEffect.Click)
                        coroutineScope.launch {
                            val currentTab = HomeTab.entries[pagerState.currentPage]
                            val gridState =
                                when (currentTab) {
                                    HomeTab.Feed -> viewModel.homeFeedGridState
                                    HomeTab.Following -> viewModel.homeTimelineGridState
                                }
                            gridState.smoothScrollToTop(scrollBehavior)
                        }
                    }
                },
            navigationIcon = {
                IconButton(onClick = {
                    performHaptic(AppHapticEffect.Click)
                    viewModel.openAccountSwitcher()
                }) {
                    HomeAccountAvatar(account = currentAccount)
                }
            },
            actions = {
                if (settings.shortsFeedEnabled) {
                    IconButton(onClick = {
                        performHaptic(AppHapticEffect.Click)
                        onSearch()
                    }) {
                        Icon(MiuixIcons.Search, contentDescription = stringResource(R.string.nav_search))
                    }
                }
                if (!settings.hideHomeNovelButton) {
                    IconButton(onClick = {
                        performHaptic(AppHapticEffect.Click)
                        onOpenNovels()
                    }) {
                        Icon(
                            MiuixIcons.Photos,
                            contentDescription = stringResource(R.string.nav_novel),
                        )
                    }
                } else {
                    IconButton(onClick = {
                        performHaptic(AppHapticEffect.Click)
                        viewModel.openNotifications()
                    }) {
                        Icon(
                            MiuixIcons.Messages,
                            contentDescription = stringResource(R.string.more_notifications),
                        )
                    }
                }
                IconButton(
                    onClick = {
                        performHaptic(AppHapticEffect.Click)
                        when (selectedTab) {
                            HomeTab.Feed -> viewModel.refreshHome(forceRefresh = true)
                            HomeTab.Following -> viewModel.refreshTimeline(forceRefresh = true)
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
                        if (index != selectedTab.ordinal) {
                            performHaptic(AppHapticEffect.Toggle)
                        }
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    modifier =
                        Modifier
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
                    HomeTab.Feed -> {
                        FeedTabContent(
                            items = items,
                            loadState = loadState,
                            nextUrl = nextUrl,
                            settings = settings,
                            viewModel = viewModel,
                            scrollBehavior = scrollBehavior,
                        )
                    }

                    HomeTab.Following -> {
                        FollowingTabContent(
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
}
