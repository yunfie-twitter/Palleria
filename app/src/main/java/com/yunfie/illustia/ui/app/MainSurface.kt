package com.yunfie.illustia.ui.app

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
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
import com.yunfie.illustia.visibleWith
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainSurface(
    appState: IllustiaAppStateBundle,
    viewModel: IllustiaViewModel,
    selectedTab: AppTab,
    pagerState: PagerState,
    homeScrollBehavior: ScrollBehavior,
    onTabSelected: (Int, AppTab) -> Unit,
    onSearch: () -> Unit,
    onOpenNovels: () -> Unit,
    onOpenComments: (Long) -> Unit,
    onOpenWatchlistSeries: (Long) -> Unit,
    onNavigateToResults: (String) -> Unit,
) {
    val tabs = mainTabs(appState.settings)
    val navigationTabs = visibleTabs(appState.settings)
    val context = LocalContext.current
    val surfaceColor = MiuixTheme.colorScheme.surface
    val isDarkTheme = surfaceColor.luminance() < 0.5f
    DisposableEffect(isDarkTheme) {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
        onDispose {}
    }
    var lastBackAt by remember { mutableStateOf(0L) }
    var navigationVisible by remember(appState.settings.navigationStyle) { mutableStateOf(true) }
    val navigationScrollConnection =
        remember(appState.settings.navigationStyle) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (appState.settings.navigationStyle == "auto") {
                        if (available.y < -2f) navigationVisible = false
                        if (available.y > 2f) navigationVisible = true
                    }
                    return Offset.Zero
                }
            }
        }
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

        val useNavigationRail = LocalUseNavigationRail.current
        Scaffold(
            modifier = Modifier.nestedScroll(navigationScrollConnection),
            containerColor = MiuixTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!useNavigationRail && appState.settings.navigationStyle == "standard") {
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
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding(),
                            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
                        ),
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 0,
                    userScrollEnabled =
                        appState.settings.swipeToSwitchWorks &&
                            !(selectedTab == AppTab.ShortsFeed && appState.settings.disableHorizontalSwipeInShortsFeed),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface),
                ) { page ->
                    when (tabs[page]) {
                        AppTab.Home -> {
                            HomeScreen(
                                items = appState.homeItems,
                                timelineItems = appState.timelineItems,
                                loadState = appState.loadState,
                                nextUrl = appState.homeChrome.homeNextUrl,
                                timelineNextUrl = appState.homeChrome.timelineNextUrl,
                                settings = appState.settings,
                                currentAccount = appState.state.currentAccount,
                                viewModel = viewModel,
                                scrollBehavior = homeScrollBehavior,
                                onSearch = onSearch,
                                onOpenNovels = onOpenNovels,
                            )
                        }

                        AppTab.Novel -> {
                            Unit
                        }

                        AppTab.Ranking -> {
                            RankingScreen(
                                items = appState.rankingItems,
                                loadState = appState.loadState,
                                nextUrl = appState.rankingChrome.rankingNextUrl,
                                mode = appState.rankingChrome.rankingMode,
                                settings = appState.settings,
                                viewModel = viewModel,
                            )
                        }

                        AppTab.Bookmarks -> {
                            BookmarkScreen(
                                settings = appState.settings,
                                loadState = appState.loadState,
                                bookmarkItems = appState.bookmarkItems,
                                timelineItems = appState.timelineItems,
                                followingUsers = appState.followingUsers,
                                chrome = appState.bookmarkChrome,
                                viewModel = viewModel,
                                onOpenWatchlistSeries = onOpenWatchlistSeries,
                            )
                        }

                        AppTab.Search -> {
                            SearchScreen(
                                state = appState.state,
                                viewModel = viewModel,
                                isResultRoute = false,
                                onNavigateToResults = onNavigateToResults,
                            )
                        }

                        AppTab.ShortsFeed -> {
                            ShortsFeedScreen(
                                items = appState.state.shortsFeedItems.visibleWith(appState.state),
                                currentIllustId = appState.state.shortsFeedCurrentIllustId,
                                viewModel = viewModel,
                                onOpenComments = onOpenComments,
                            )
                        }

                        AppTab.More -> {
                            MoreScreen(
                                state = appState.state,
                                viewModel = viewModel,
                                onOpenWatchlistSeries = onOpenWatchlistSeries,
                            )
                        }
                    }
                }

                if (!useNavigationRail && appState.settings.navigationStyle != "standard") {
                    AnimatedVisibility(
                        visible = appState.settings.navigationStyle != "auto" || navigationVisible,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        FloatingNavigationBar(
                            color = MiuixTheme.colorScheme.surfaceContainerHigh,
                            showDivider = true,
                        ) {
                            navigationTabs.forEach { tab ->
                                val pageIndex = tabs.indexOf(tab)
                                FloatingNavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = {
                                        navigationVisible = true
                                        viewModel.closeAccountSwitcher()
                                        onTabSelected(pageIndex, tab)
                                    },
                                    icon = tab.icon,
                                    label = stringResource(tab.labelResId),
                                )
                            }
                        }
                    }
                }
            }
        }

        if (appState.state.privacyLocked) {
            CalculatorScreen(
                viewModel = viewModel,
                isTransitioning = appState.state.isTransitioningToIllustia,
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
