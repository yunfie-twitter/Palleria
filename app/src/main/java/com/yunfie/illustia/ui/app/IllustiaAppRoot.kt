package com.yunfie.illustia.ui.app

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.yunfie.illustia.IllustiaNavigationRequest
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import com.yunfie.illustia.settings.AppHapticMode
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.LocalPixivImageProxyBaseUrl
import com.yunfie.illustia.ui.components.LocalPreferLowDataImages
import com.yunfie.illustia.ui.components.isActiveNetworkMetered
import com.yunfie.illustia.ui.screens.CalculatorScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun IllustiaAppRoot(viewModel: IllustiaViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val loadState by viewModel.loadStateState.collectAsStateWithLifecycle()
    val homeItems by viewModel.homeItemsState.collectAsStateWithLifecycle()
    val novelItems by viewModel.novelItemsState.collectAsStateWithLifecycle()
    val timelineItems by viewModel.timelineItemsState.collectAsStateWithLifecycle()
    val rankingItems by viewModel.rankingItemsState.collectAsStateWithLifecycle()
    val bookmarkItems by viewModel.bookmarkItemsState.collectAsStateWithLifecycle()
    val watchlistItems by viewModel.watchlistItemsState.collectAsStateWithLifecycle()
    val followingUsers by viewModel.followingUsersState.collectAsStateWithLifecycle()
    val homeChrome by viewModel.homeChromeState.collectAsStateWithLifecycle()
    val novelChrome by viewModel.novelChromeState.collectAsStateWithLifecycle()
    val rankingChrome by viewModel.rankingChromeState.collectAsStateWithLifecycle()
    val bookmarkChrome by viewModel.bookmarkChromeState.collectAsStateWithLifecycle()
    val startupScreen = state.settings.startupScreen
    val tabs = mainTabs(settings.shortsFeedEnabled)
    val initialTab = remember(startupScreen) { startupTabFor(startupScreen) }
    val initialPage = remember(initialTab, tabs) { tabs.indexOf(initialTab).coerceAtLeast(0) }
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var previousTab by remember { mutableStateOf<AppTab?>(null) }
    var showTokenLogin by remember { mutableStateOf(false) }
    val selectedWatchlistSeriesIds = remember { mutableStateListOf<Long>() }
    var selectedCommentTarget by remember { mutableStateOf<Pair<Long, CommentArtworkType>?>(null) }
    val backStack = remember { mutableStateListOf<NavKey>(AppRoute.Main) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = initialPage,
        pageCount = { tabs.size },
    )
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val appState = IllustiaAppStateBundle(
        state = state,
        settings = settings,
        loadState = loadState,
        homeItems = homeItems,
        novelItems = novelItems,
        timelineItems = timelineItems,
        rankingItems = rankingItems,
        bookmarkItems = bookmarkItems,
        watchlistItems = watchlistItems,
        followingUsers = followingUsers,
        homeChrome = homeChrome,
        novelChrome = novelChrome,
        rankingChrome = rankingChrome,
        bookmarkChrome = bookmarkChrome,
    )

    LaunchedEffect(state.webLoginRequest) {
        val request = state.webLoginRequest ?: return@LaunchedEffect
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()
                .launchUrl(context, Uri.parse(request.authorizationUrl))
        }.onFailure {
            viewModel.failWebLogin(context.getString(R.string.error_browser_failed))
        }
    }

    fun navigate(route: AppRoute) {
        if (backStack.lastOrNull() != route) {
            backStack.add(route)
        }
    }

    fun openWatchlistSeries(seriesId: Long) {
        selectedWatchlistSeriesIds.add(seriesId)
        navigate(AppRoute.IllustSeries)
    }

    fun popRoute() {
        if (backStack.size <= 1) return
        if (selectedCommentTarget != null) {
            selectedCommentTarget = null
            return
        }
        when (backStack.removeAt(backStack.lastIndex)) {
            AppRoute.Detail -> viewModel.closeIllust()
            AppRoute.ImageViewer -> viewModel.closeImageViewer()
            AppRoute.NovelList -> Unit
            AppRoute.NovelReader -> viewModel.closeNovel()
            AppRoute.IllustSeries -> {
                if (selectedWatchlistSeriesIds.isNotEmpty()) {
                    selectedWatchlistSeriesIds.removeAt(selectedWatchlistSeriesIds.lastIndex)
                }
            }
            AppRoute.UserProfile -> {
                viewModel.hideUserPage()
                viewModel.restoreProfileReturnDetail()
            }
            else -> Unit
        }
    }

    LaunchedEffect(state.settingsLoaded, state.settings.refreshToken) {
        if (!state.settingsLoaded) return@LaunchedEffect
        if (state.settings.refreshToken.isNotBlank()) {
            delay(120)
            viewModel.loadInitialHomeIfNeeded()
            if (backStack.lastOrNull() == AppRoute.Onboarding) {
                backStack.clear()
                backStack.add(AppRoute.Main)
                selectedWatchlistSeriesIds.clear()
            }
        } else {
            backStack.clear()
            backStack.add(AppRoute.Onboarding)
            selectedWatchlistSeriesIds.clear()
        }
    }

    LaunchedEffect(state.appLocked) {
        if (state.appLocked && state.settings.appLockEnabled) {
            backStack.clear()
            backStack.add(AppRoute.Main)
            selectedWatchlistSeriesIds.clear()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = tabs[pagerState.currentPage]
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == AppTab.ShortsFeed && previousTab != AppTab.ShortsFeed) {
            viewModel.refreshShortsFeed()
        }
        previousTab = selectedTab
    }

    LaunchedEffect(state.activeSearchWord) {
        if (!settings.shortsFeedEnabled && state.activeSearchWord.isNotBlank() && selectedTab != AppTab.Search) {
            selectedTab = AppTab.Search
            val searchIndex = tabs.indexOf(AppTab.Search)
            if (searchIndex >= 0) pagerState.scrollToPage(searchIndex)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Custom(2400L),
            )
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.navigationRequests.collect { request ->
            navigate(
                when (request) {
                    IllustiaNavigationRequest.Settings -> AppRoute.Settings
                    IllustiaNavigationRequest.GeneralSettings -> AppRoute.GeneralSettings
                    IllustiaNavigationRequest.ImageSettings -> AppRoute.ImageSettings
                    IllustiaNavigationRequest.BookmarkSettings -> AppRoute.BookmarkSettings
                    IllustiaNavigationRequest.AccountSettings -> AppRoute.AccountSettings
                    IllustiaNavigationRequest.AccountLoginMethod -> AppRoute.AccountLoginMethod
                    IllustiaNavigationRequest.DataSettings -> AppRoute.DataSettings
                    IllustiaNavigationRequest.ViewHistory -> AppRoute.ViewHistory
                    IllustiaNavigationRequest.MuteSettings -> AppRoute.MuteSettings
                    IllustiaNavigationRequest.AppData -> AppRoute.AppData
                    IllustiaNavigationRequest.DownloadQueue -> AppRoute.DownloadQueue
                    IllustiaNavigationRequest.OfflineLibrary -> AppRoute.OfflineLibrary
                    IllustiaNavigationRequest.SavedIllustViewer -> AppRoute.SavedIllustViewer
                    IllustiaNavigationRequest.About -> AppRoute.About
                    IllustiaNavigationRequest.FavoriteTags -> AppRoute.FavoriteTags
                    IllustiaNavigationRequest.AppLockSetup -> AppRoute.AppLockSetup
                    IllustiaNavigationRequest.AppLockPinEntry -> AppRoute.AppLockPinEntry
                    IllustiaNavigationRequest.PrivacyModeSettings -> AppRoute.PrivacyModeSettings
                },
            )
        }
    }

    LaunchedEffect(state.selectedIllust?.id) {
        if (state.selectedIllust != null) {
            navigate(AppRoute.Detail)
        } else if (backStack.lastOrNull() == AppRoute.Detail) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    LaunchedEffect(state.imageViewerIllust?.id, state.imageViewerStartPage) {
        if (state.imageViewerIllust != null) {
            navigate(AppRoute.ImageViewer)
        } else if (backStack.lastOrNull() == AppRoute.ImageViewer) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    LaunchedEffect(state.selectedNovel?.id) {
        if (state.selectedNovel != null) {
            navigate(AppRoute.NovelReader)
        } else if (backStack.lastOrNull() == AppRoute.NovelReader) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    LaunchedEffect(state.showUserPage) {
        if (state.showUserPage) {
            if (backStack.lastOrNull() != AppRoute.UserProfile) {
                backStack.add(AppRoute.UserProfile)
            }
        } else if (backStack.lastOrNull() == AppRoute.UserProfile) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { AppRoute.UserProfile in backStack }
            .collect { hasUserProfile ->
                if (!hasUserProfile) {
                    delay(350)
                    if (AppRoute.UserProfile !in backStack) {
                        viewModel.closeUserPage()
                    }
                }
            }
    }

    val preferLowDataImages = remember(context) { context.isActiveNetworkMetered() }

    CompositionLocalProvider(
        LocalPixivImageProxyBaseUrl provides state.settings.pixivImageProxyBaseUrl,
        LocalPreferLowDataImages provides preferLowDataImages,
        LocalBottomSheetBackgroundColor provides MiuixTheme.colorScheme.surfaceContainerHigh,
        LocalAppHapticMode provides AppHapticMode.fromValue(state.settings.hapticMode),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!state.privacyLocked || state.isTransitioningToIllustia) {
                Scaffold(
                    containerColor = MiuixTheme.colorScheme.surface,
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = {
                        SnackbarHost(state = snackbarHostState)
                    },
                ) { rootPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(rootPadding),
                        color = MiuixTheme.colorScheme.surface,
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavHost(
                                appState = appState,
                                viewModel = viewModel,
                                backStack = backStack,
                                selectedTab = selectedTab,
                                pagerState = pagerState,
                                showTokenLogin = showTokenLogin,
                                onShowTokenLoginChange = { showTokenLogin = it },
                                selectedWatchlistSeriesId = selectedWatchlistSeriesIds.lastOrNull(),
                                onSelectedWatchlistSeriesIdChange = { seriesId ->
                                    if (seriesId == null) {
                                        if (selectedWatchlistSeriesIds.isNotEmpty()) {
                                            selectedWatchlistSeriesIds.removeAt(selectedWatchlistSeriesIds.lastIndex)
                                        }
                                    } else {
                                        selectedWatchlistSeriesIds.add(seriesId)
                                    }
                                },
                                selectedCommentTarget = selectedCommentTarget,
                                onSelectedCommentTargetChange = { selectedCommentTarget = it },
                                onNavigate = ::navigate,
                                onPopRoute = ::popRoute,
                                onTabSelected = { index, tab ->
                                    if (tab == AppTab.ShortsFeed && selectedTab == AppTab.ShortsFeed) {
                                        viewModel.refreshShortsFeed()
                                    } else {
                                        selectedTab = tab
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                        if (tab == AppTab.Bookmarks) viewModel.refreshBookmarks()
                                    }
                                },
                            )
                        }
                    }
                }
            } else {
                CalculatorScreen(
                    buffer = state.calculatorBuffer,
                    history = state.calculatorHistory,
                    isTransitioning = false,
                    viewModel = viewModel,
                )
            }

            if (state.activeDownloads > 0) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                )
            }
        }
    }
}
