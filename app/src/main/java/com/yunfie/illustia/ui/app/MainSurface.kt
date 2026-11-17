package com.yunfie.illustia.ui.app

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.AppNavigationBar
import com.yunfie.illustia.ui.components.LocalSystemUiCovered
import com.yunfie.illustia.ui.components.LocalTabPageActive
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
import top.yukonga.miuix.kmp.anim.folmeSpring
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
    visitedPages: Set<Int>,
    onPageVisited: (Int) -> Unit,
    homeScrollBehavior: ScrollBehavior,
    onTabSelected: (Int, AppTab) -> Unit,
    onSearch: () -> Unit,
    onOpenNovels: () -> Unit,
    onOpenComments: (Long) -> Unit,
    onOpenWatchlistSeries: (Long) -> Unit,
    onOpenBookmarkTagSort: () -> Unit,
    onNavigateToResults: (String) -> Unit,
) {
    val performHaptic = rememberHapticFeedbackAction()
    val tabs = remember(appState.settings.shortsFeedEnabled, appState.settings.navigationOrder) { mainTabs(appState.settings) }
    val navigationTabs =
        remember(appState.settings.shortsFeedEnabled, appState.settings.navigationOrder, appState.settings.hiddenNavigationTabs) {
            visibleTabs(appState.settings)
        }
    val context = LocalContext.current
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

    // タップによるタブ切り替えのフェード進捗。0 = 切り替え直後（新ページが透明）、
    // 1 = 表示完了。Pager を横にスクロールさせる代わりに、この 1 本の値だけで
    // フェードと微スケールを完結させる。横スライドだと重量級の画面が 2 つ同時に
    // 描画されてフレーム落ちになるが、フェードなら遷移中も 1 ページ分の描画で済む。
    val tabFade = remember { Animatable(1f) }
    var lastSettledPage by remember { mutableIntStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != lastSettledPage) {
            lastSettledPage = pagerState.settledPage
            tabFade.snapTo(0f)
            tabFade.animateTo(1f, tabScrollAnimationSpec)
        }
    }

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
                    // NavigationBar 自身が surfaceContainer を塗るため、外側に背景 Box を重ねると
                    // 画面遷移中にバーの背後へ不透明な色が残る。追加の背景は持たせない。
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
                SystemUiAwareBackdropBox(
                    enabled = isBlurEnabled && supportsBlur && !useNavigationRail,
                    backdrop = navBackdrop,
                    backdropModifier = { Modifier.layerBackdrop(it) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LaunchedEffect(pagerState.currentPage) {
                        onPageVisited(pagerState.currentPage)
                    }
                    // 表示中のページは常にコンポーズ対象にする。visitedPages は Main エントリの
                    // 外で保持しているが、エントリ再生成と pagerState の scrollToPage が競合すると
                    // 「今いるページが未訪問」という状態を経由するため、ここで必ず含めておかないと
                    // 空白の Box が残る。
                    val currentPage = pagerState.currentPage
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 0,
                        // 下部ナビゲーションはタップ操作のみで切り替える。ここをスワイプ可能に
                        // すると、画面内の上部タブ（タイムライン/ブックマーク など）を横スワイプ
                        // したときに下部タブまで同時に動いてしまうため、常に無効化する。
                        userScrollEnabled = false,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.surface),
                    ) { page ->
                        // 一度も表示していないタブはコンポーズしない。ルート Pager はスワイプ中
                        // 隣接ページを必ずコンポーズするため、重量級の画面（ネスト Pager や
                        // Grid、ブラー用 backdrop）が同時に 2 つ生きるとスワイプが重くなる。
                        if (page !in visitedPages && page != currentPage) {
                            Box(modifier = Modifier.fillMaxSize())
                            return@HorizontalPager
                        }
                        // タブ切り替えは横スライドではなく、ページ自身のフェード＋微スケールで
                        // 見せる。横スライドだと重い画面が 2 つ同時に描画されてしまうが、
                        // フェードなら非アクティブページは透明になるだけで、実質 1 ページ分の
                        // 描画で済む。進捗（tabFade）の読み取りは graphicsLayer 内に閉じ込め、
                        // 遷移中に再コンポーズさせず draw 段階だけで補間する。
                        val pageAlphaProvider = { page == currentPage }
                        val pageModifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (page != currentPage) {
                                        // 表示されていないページは描画しない。
                                        alpha = 0f
                                        return@graphicsLayer
                                    }
                                    val progress = tabFade.value
                                    alpha = progress
                                    val scale = TAB_TRANSITION_MIN_SCALE + (1f - TAB_TRANSITION_MIN_SCALE) * progress
                                    scaleX = scale
                                    scaleY = scale
                                }
                        CompositionLocalProvider(LocalTabPageActive provides pageAlphaProvider) {
                            Box(modifier = pageModifier) {
                                when (tabs[page]) {
                                    AppTab.Home -> {
                                        HomeScreen(
                                            items = appState.homeItems,
                                            timelineItems = appState.timelineItems,
                                            loadState = appState.loadState,
                                            timelineLoadState = appState.timelineLoadState,
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
                                            isOfflineCached = appState.homeChrome.isOfflineCached,
                                            pendingHomeItemsCount = appState.homeChrome.pendingHomeItemsCount,
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
                                            timelineLoadState = appState.timelineLoadState,
                                            followingUsersLoadState = appState.followingUsersLoadState,
                                            bookmarkItems = appState.bookmarkItems,
                                            bookmarkTag = appState.state.bookmarkTag,
                                            timelineItems = appState.timelineItems,
                                            followingUsers = appState.followingUsers,
                                            chrome = appState.bookmarkChrome,
                                            viewModel = viewModel,
                                            onOpenWatchlistSeries = onOpenWatchlistSeries,
                                            onOpenBookmarkTagSort = onOpenBookmarkTagSort,
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
                                        val shortsFeedItems =
                                            remember(
                                                appState.state.shortsFeedItems,
                                                appState.state.mutedIllustsSet,
                                                appState.state.mutedUsersSet,
                                                appState.state.mutedTagsSet,
                                            ) {
                                                appState.state.shortsFeedItems.visibleWith(appState.state)
                                            }
                                        ShortsFeedScreen(
                                            items = shortsFeedItems,
                                            loadState = appState.state.loadState,
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
                    }
                }

                if (!useNavigationRail && appState.settings.navigationStyle == "standard" && isBlurEnabled) {
                    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
                    val outline = MiuixTheme.colorScheme.outline
                    // `enabled` must not depend on SystemUiCovered here: at launch the window has no
                    // focus yet, so toggling the wrapper's modifier remounts the whole bar inside the
                    // splash exit window and shows up as a flash of the navigation bar.
                    SystemUiAwareBackdropBox(
                        enabled = supportsBlur,
                        backdrop = navBackdrop,
                        backdropModifier = {
                            Modifier.progressiveTextureBlur(
                                backdrop = it,
                                shape = RectangleShape,
                                blurRadius = 30f,
                                gradient = ProgressiveBlur.Bottom,
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .drawWithContent {
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

/**
 * Keep SystemUI coverage changes local to the backdrop layer. Reading the coverage local in
 * MainSurface invalidated the large tab/pager composition whenever Quick Settings opened or
 * closed, even though the only required change was to stop recording this backdrop.
 */
@Composable
private fun <Backdrop> SystemUiAwareBackdropBox(
    enabled: Boolean,
    backdrop: Backdrop,
    backdropModifier: (Backdrop) -> Modifier,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Keep this state read in a small leaf instead of invalidating MainSurface and its whole
    // tab/pager tree when Quick Settings opens or closes.
    val systemCovered = LocalSystemUiCovered.current
    Box(
        modifier =
            modifier.then(
                if (enabled && !systemCovered) {
                    backdropModifier(backdrop)
                } else {
                    Modifier
                },
            ),
        content = { content() },
    )
}

/**
 * タブ切り替え時のフェードの時間感。MIUI の標準スプリング（folmeSpring）に合わせる。
 */
internal val tabScrollAnimationSpec = folmeSpring<Float>(damping = 0.86f, response = 0.32f)

/** 切り替え直後のスケール。1 に向かって広がりながらフェードインする。 */
private const val TAB_TRANSITION_MIN_SCALE = 0.94f

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
