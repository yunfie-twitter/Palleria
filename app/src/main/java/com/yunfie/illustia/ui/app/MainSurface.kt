package com.yunfie.illustia.ui.app

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.visibleWith
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.screens.AccountSwitchSheet
import com.yunfie.illustia.ui.screens.AppLockScreen
import com.yunfie.illustia.ui.screens.BookmarkScreen
import com.yunfie.illustia.ui.screens.CalculatorScreen
import com.yunfie.illustia.ui.screens.HomeScreen
import com.yunfie.illustia.ui.screens.MoreScreen
import com.yunfie.illustia.ui.screens.RankingScreen
import com.yunfie.illustia.ui.screens.SearchScreen
import com.yunfie.illustia.ui.screens.ShortsFeedScreen
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainSurface(
    appState: IllustiaAppStateBundle,
    viewModel: IllustiaViewModel,
    selectedTab: AppTab,
    pagerState: PagerState,
    onTabSelected: (Int, AppTab) -> Unit,
    onSearch: () -> Unit,
    onOpenNovels: () -> Unit,
    onOpenComments: (Long) -> Unit,
    onOpenWatchlistSeries: (Long) -> Unit,
) {
    val tabs = mainTabs(appState.settings.shortsFeedEnabled)
    val navigationTabs = visibleTabs(appState.settings.shortsFeedEnabled)
    val context = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    val isSearchResultMode = selectedTab == AppTab.Search &&
        appState.state.activeSearchWord.isNotBlank()
    val doubleBackExitMessage = stringResource(R.string.msg_double_back_exit)

    LaunchedEffect(selectedTab) {
        if (appState.state.showAccountSwitcher) {
            viewModel.closeAccountSwitcher()
        }
    }

    PredictiveBackGestureHandler(enabled = appState.settings.doubleBackToExit) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt < 1800L) {
            (context as? Activity)?.finish()
        } else {
            lastBackAt = now
            viewModel.showMessage(doubleBackExitMessage)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        AccountSwitchSheet(
            show = appState.state.showAccountSwitcher,
            accounts = appState.state.settings.accounts,
            activeAccountIndex = appState.state.settings.activeAccountIndex,
            viewModel = viewModel,
            onDismiss = viewModel::closeAccountSwitcher,
            onAddAccount = viewModel::openAccountLoginMethod,
        )

        val useNavigationRail = maxWidth >= 600.dp
        Scaffold(
            containerColor = MiuixTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!useNavigationRail && !isSearchResultMode) {
                    NavigationBar(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        showDivider = true,
                    ) {
                        navigationTabs.forEach { tab ->
                            val pageIndex = tabs.indexOf(tab)
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    viewModel.closeAccountSwitcher()
                                    onTabSelected(pageIndex, tab)
                                },
                                icon = tab.icon,
                                label = stringResource(tab.labelResId),
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                if (useNavigationRail && !isSearchResultMode) {
                    NavigationRail(
                        color = MiuixTheme.colorScheme.surfaceContainer,
                        showDivider = true,
                    ) {
                        navigationTabs.forEach { tab ->
                            val pageIndex = tabs.indexOf(tab)
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    viewModel.closeAccountSwitcher()
                                    onTabSelected(pageIndex, tab)
                                },
                                icon = tab.icon,
                                label = stringResource(tab.labelResId),
                            )
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = appState.settings.swipeToSwitchWorks &&
                        !(selectedTab == AppTab.ShortsFeed && appState.settings.disableHorizontalSwipeInShortsFeed),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MiuixTheme.colorScheme.surface),
                ) { page ->
                    when (tabs[page]) {
                        AppTab.Home -> HomeScreen(
                            items = appState.homeItems,
                            timelineItems = appState.timelineItems,
                            loadState = appState.loadState,
                            nextUrl = appState.homeChrome.homeNextUrl,
                            timelineNextUrl = appState.homeChrome.timelineNextUrl,
                            settings = appState.settings,
                            currentAccount = appState.state.currentAccount,
                            viewModel = viewModel,
                            onSearch = onSearch,
                            onOpenNovels = onOpenNovels,
                        )

                        AppTab.Novel -> Unit

                        AppTab.Ranking -> RankingScreen(
                            items = appState.rankingItems,
                            loadState = appState.loadState,
                            nextUrl = appState.rankingChrome.rankingNextUrl,
                            mode = appState.rankingChrome.rankingMode,
                            settings = appState.settings,
                            viewModel = viewModel,
                        )

                        AppTab.Bookmarks -> BookmarkScreen(
                            settings = appState.settings,
                            loadState = appState.loadState,
                            bookmarkItems = appState.bookmarkItems,
                            timelineItems = appState.timelineItems,
                            followingUsers = appState.followingUsers,
                            chrome = appState.bookmarkChrome,
                            viewModel = viewModel,
                            onOpenWatchlistSeries = onOpenWatchlistSeries,
                        )

                        AppTab.Search -> SearchScreen(state = appState.state, viewModel = viewModel)

                        AppTab.ShortsFeed -> ShortsFeedScreen(
                            items = appState.state.shortsFeedItems.visibleWith(appState.state),
                            currentIllustId = appState.state.shortsFeedCurrentIllustId,
                            viewModel = viewModel,
                            onOpenComments = onOpenComments,
                        )

                        AppTab.More -> MoreScreen(
                            state = appState.state,
                            viewModel = viewModel,
                            onOpenWatchlistSeries = onOpenWatchlistSeries,
                        )
                    }
                }
            }
        }

        if (appState.state.privacyLocked) {
            CalculatorScreen(
                buffer = appState.state.calculatorBuffer,
                history = appState.state.calculatorHistory,
                isTransitioning = appState.state.isTransitioningToIllustia,
                viewModel = viewModel,
            )
        } else if (appState.state.appLocked && appState.state.settings.appLockEnabled) {
            AppLockScreen(
                biometricEnabled = appState.state.settings.biometricEnabled,
                failCount = appState.state.settings.appLockFailCount,
                cooldownUntil = appState.state.settings.appLockCooldownUntil,
                viewModel = viewModel,
            )
        }
    }
}
