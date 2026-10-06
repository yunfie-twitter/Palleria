package com.yunfie.illustia.ui.app

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.AppNavigationBar
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
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
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MainSurface(
    appState: IllustiaAppStateBundle,
    searchFocusRequest: Int = 0,
    onSearchFocusHandled: () -> Unit = {},
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
    val performHaptic = rememberHapticFeedbackAction()
    val tabs = remember(appState.settings.shortsFeedEnabled, appState.settings.navigationOrder) { mainTabs(appState.settings) }
    val navigationTabs =
        remember(appState.settings.shortsFeedEnabled, appState.settings.navigationOrder) { visibleTabs(appState.settings) }
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

    BackHandler(enabled = appState.settings.doubleBackToExit) {
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
        val isBlurEnabled = appState.settings.isFeatureEnabled(FeatureFlag.TopScrollBlur)
        val supportsBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val navBackdrop = rememberLayerBackdrop()

        Scaffold(
            modifier = Modifier.nestedScroll(navigationScrollConnection),
            containerColor = MiuixTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (!useNavigationRail && appState.settings.navigationStyle == "standard" && !isBlurEnabled) {
                    Box(
                        modifier = Modifier.background(MiuixTheme.colorScheme.surfaceContainer),
                    ) {
                        AppNavigationBar(settings = appState.settings) {
                            navigationTabs.forEach { tab ->
                                val pageIndex = tabs.indexOf(tab)
                                NavigationBarItem(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    selected = selectedTab == tab,
                                    onClick = {
                                        performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Click)
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
            },
        ) { paddingValues ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom =
                                if (isBlurEnabled && !useNavigationRail && appState.settings.navigationStyle == "standard") {
                                    0.dp
                                } else {
                                    paddingValues.calculateBottomPadding()
                                },
                            start = paddingValues.calculateStartPadding(LocalLayoutDirection.current),
                            end = paddingValues.calculateEndPadding(LocalLayoutDirection.current),
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (isBlurEnabled && supportsBlur && !useNavigationRail) {
                                    Modifier.layerBackdrop(navBackdrop)
                                } else {
                                    Modifier
                                },
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
                                    isHomeRefreshing = appState.homeChrome.isHomeRefreshing,
                                    isHomePaginating = appState.homeChrome.isHomePaginating,
                                    isTimelineRefreshing = appState.homeChrome.isTimelineRefreshing,
                                    isTimelinePaginating = appState.homeChrome.isTimelinePaginating,
                                    initialTab = appState.homeChrome.selectedTab,
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
                                SearchTabContent(
                                    onFocusRequestHandled = onSearchFocusHandled,
                                    focusRequest = if (selectedTab == AppTab.Search) searchFocusRequest else 0,
                                    viewModel = viewModel,
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
                }

                if (!useNavigationRail && appState.settings.navigationStyle == "standard" && isBlurEnabled) {
                    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
                    val outline = MiuixTheme.colorScheme.outline
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .then(
                                    if (supportsBlur) {
                                        Modifier.progressiveTextureBlur(
                                            backdrop = navBackdrop,
                                            shape = RectangleShape,
                                            blurRadius = 30f,
                                            gradient = ProgressiveBlur.Bottom,
                                        )
                                    } else {
                                        Modifier
                                    },
                                ).drawWithContent {
                                    val baseAlpha = if (supportsBlur) 0.72f else 0.92f
                                    drawRect(color = surfaceContainer.copy(alpha = baseAlpha))
                                    val dividerHeightPx = 1.dp.toPx()
                                    drawRect(
                                        color = outline.copy(alpha = 0.08f),
                                        topLeft =
                                            androidx.compose.ui.geometry
                                                .Offset(0f, 0f),
                                        size =
                                            androidx.compose.ui.geometry
                                                .Size(size.width, dividerHeightPx),
                                    )
                                    drawContent()
                                },
                    ) {
                        AppNavigationBar(
                            settings = appState.settings,
                            color = Color.Transparent,
                            showDivider = false,
                        ) {
                            navigationTabs.forEach { tab ->
                                val pageIndex = tabs.indexOf(tab)
                                NavigationBarItem(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    selected = selectedTab == tab,
                                    onClick = {
                                        performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Click)
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

                if (!useNavigationRail && appState.settings.navigationStyle != "standard") {
                    AnimatedVisibility(
                        visible = appState.settings.navigationStyle != "auto" || navigationVisible,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    ) {
                        FloatingNavigationBar(
                            color =
                                if (isBlurEnabled) {
                                    MiuixTheme.colorScheme.surfaceContainerHigh.copy(
                                        alpha = if (supportsBlur) 0.78f else 0.92f,
                                    )
                                } else {
                                    MiuixTheme.colorScheme.surfaceContainerHigh
                                },
                            showDivider = true,
                        ) {
                            navigationTabs.forEach { tab ->
                                val pageIndex = tabs.indexOf(tab)
                                FloatingNavigationBarItem(
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                                    selected = selectedTab == tab,
                                    onClick = {
                                        performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Click)
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

@Composable
private fun SearchTabContent(
    focusRequest: Int,
    onFocusRequestHandled: () -> Unit,
    viewModel: IllustiaViewModel,
    onNavigateToResults: (String) -> Unit,
) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    SearchScreen(
        focusRequest = focusRequest,
        onFocusRequestHandled = onFocusRequestHandled,
        state = searchState,
        viewModel = viewModel,
        isResultRoute = false,
        onNavigateToResults = onNavigateToResults,
    )
}
