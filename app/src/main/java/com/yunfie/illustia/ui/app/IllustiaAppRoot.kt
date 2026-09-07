package com.yunfie.illustia.ui.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import com.yunfie.illustia.AppShortcutDestination
import com.yunfie.illustia.AppShortcutRouter
import com.yunfie.illustia.IllustiaNavigationRequest
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import com.yunfie.illustia.platform.DesktopEnvironment
import com.yunfie.illustia.platform.WindowSizeClass
import com.yunfie.illustia.settings.AppHapticMode
import com.yunfie.illustia.settings.effectiveAppHapticMode
import com.yunfie.illustia.ui.components.ArtworkCardPreferences
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.LocalArtworkCardPreferences
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.LocalPixivImageProxyBaseUrl
import com.yunfie.illustia.ui.components.LocalPreferLowDataImages
import com.yunfie.illustia.ui.components.NoOpHapticFeedback
import com.yunfie.illustia.ui.components.isActiveNetworkMetered
import com.yunfie.illustia.ui.components.isAppHapticsSupported
import com.yunfie.illustia.ui.screens.CalculatorScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.SnackbarResult
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

internal val LocalUseNavigationRail = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
internal fun IllustiaAppRoot(viewModel: IllustiaViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val startupScreen = state.settings.startupScreen
    val tabs = mainTabs(settings)
    val initialTab = remember(startupScreen, tabs) {
        viewModel.activeTab?.takeIf { it in tabs } ?: startupTabFor(startupScreen, tabs)
    }
    val initialPage = remember(initialTab, tabs) { tabs.indexOf(initialTab).coerceAtLeast(0) }
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var previousTab by remember { mutableStateOf<AppTab?>(null) }
    var showTokenLogin by remember { mutableStateOf(false) }
    val selectedWatchlistSeriesIds = viewModel.selectedWatchlistSeriesIds
    var selectedCommentTarget by remember { mutableStateOf<Pair<Long, CommentArtworkType>?>(null) }
    val backStack = viewModel.navigationBackStack
    val detailSnapshots = viewModel.detailSnapshots
    val pagerState =
        androidx.compose.foundation.pager.rememberPagerState(
            initialPage = initialPage,
            pageCount = { tabs.size },
        )
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val homeScrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val pendingShortcut by AppShortcutRouter.pending.collectAsStateWithLifecycle()
    val discordRpcManager =
        remember {
            com.yunfie.illustia.discord
                .DiscordRpcManager(context.applicationContext)
        }

    val appState = IllustiaAppStateBundle(state)

    LaunchedEffect(state.webLoginRequest) {
        val request = state.webLoginRequest ?: return@LaunchedEffect
        runCatching {
            CustomTabsIntent
                .Builder()
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
        val removed = backStack.removeAt(backStack.lastIndex)
        when (removed) {
            is AppRoute.Detail -> {
                if (backStack.none { it == removed }) {
                    detailSnapshots.remove(removed.illustId)
                }
            }

            AppRoute.ImageViewer -> {
                viewModel.closeImageViewer()
            }

            is AppRoute.TagSearch -> {
                viewModel.clearSearchResults()
            }

            is AppRoute.SearchResults -> {
                viewModel.clearSearchResults()
            }

            AppRoute.NovelList -> {
                Unit
            }

            AppRoute.NovelReader -> {
                viewModel.closeNovel()
            }

            AppRoute.IllustSeries -> {
                if (selectedWatchlistSeriesIds.isNotEmpty()) {
                    selectedWatchlistSeriesIds.removeAt(selectedWatchlistSeriesIds.lastIndex)
                }
            }

            is AppRoute.UserProfile -> {
                viewModel.hideUserPage()
            }

            else -> {
                Unit
            }
        }
        when (val revealed = backStack.lastOrNull()) {
            is AppRoute.Detail -> {
                val snapshot = detailSnapshots[revealed.illustId]
                if (snapshot != null) {
                    viewModel.restoreIllustDetail(
                        illust = snapshot.illust,
                        user = snapshot.user,
                        firstComment = snapshot.firstComment,
                        relatedIllusts = snapshot.relatedIllusts,
                    )
                } else {
                    viewModel.openIllust(revealed.illustId)
                }
            }

            is AppRoute.UserProfile -> {
                viewModel.openUserPage(revealed.userId)
            }

            else -> {
                if (removed is AppRoute.Detail) viewModel.closeIllust()
            }
        }
    }

    fun searchFromDetail(tag: String) {
        viewModel.submitSearch(tag)
        navigate(AppRoute.SearchResults(tag))
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

    LaunchedEffect(pagerState.settledPage) {
        selectedTab = tabs[pagerState.settledPage]
        viewModel.activeTab = selectedTab
    }

    LaunchedEffect(tabs) {
        val targetTab = selectedTab.takeIf { it in tabs } ?: initialTab
        val targetIndex = tabs.indexOf(targetTab).coerceAtLeast(0)
        selectedTab = targetTab
        viewModel.activeTab = targetTab
        if (pagerState.currentPage != targetIndex) pagerState.scrollToPage(targetIndex)
    }

    LaunchedEffect(selectedTab) {
        viewModel.activeTab = selectedTab
        if (previousTab == AppTab.Search && selectedTab != AppTab.Search) {
            viewModel.clearSearchResults()
        }
        if (selectedTab == AppTab.ShortsFeed && previousTab != AppTab.ShortsFeed) {
            viewModel.refreshShortsFeed()
        }
        previousTab = selectedTab
    }

    LaunchedEffect(state.activeSearchWord) {
        if (state.activeSearchWord.isNotBlank()) {
            if (
                backStack.lastOrNull() != AppRoute.Search &&
                backStack.lastOrNull() !is AppRoute.TagSearch &&
                backStack.lastOrNull() !is AppRoute.SearchResults
            ) {
                navigate(AppRoute.SearchResults(state.activeSearchWord))
            }
        }
    }

    LaunchedEffect(
        pendingShortcut,
        state.settingsLoaded,
        state.settings.refreshToken,
        state.appLocked,
        state.privacyLocked,
        settings.shortsFeedEnabled,
    ) {
        val destination = pendingShortcut ?: return@LaunchedEffect
        if (
            !state.settingsLoaded ||
            state.settings.refreshToken.isBlank() ||
            state.appLocked ||
            state.privacyLocked
        ) {
            return@LaunchedEffect
        }

        backStack.clear()
        backStack.add(AppRoute.Main)
        selectedWatchlistSeriesIds.clear()
        when (destination) {
            AppShortcutDestination.Search -> {
                if (settings.shortsFeedEnabled) {
                    navigate(AppRoute.Search)
                } else {
                    selectedTab = AppTab.Search
                    tabs
                        .indexOf(AppTab.Search)
                        .takeIf { it >= 0 }
                        ?.let { pagerState.scrollToPage(it) }
                }
            }

            AppShortcutDestination.Ranking -> {
                selectedTab = AppTab.Ranking
                tabs
                    .indexOf(AppTab.Ranking)
                    .takeIf { it >= 0 }
                    ?.let { pagerState.scrollToPage(it) }
            }

            AppShortcutDestination.Bookmarks -> {
                selectedTab = AppTab.Bookmarks
                tabs
                    .indexOf(AppTab.Bookmarks)
                    .takeIf { it >= 0 }
                    ?.let { pagerState.scrollToPage(it) }
                viewModel.refreshBookmarks()
            }

            AppShortcutDestination.ViewHistory -> {
                navigate(AppRoute.ViewHistory)
            }
        }
        AppShortcutRouter.consume(destination)
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

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("freedroidwarn_prefs", Context.MODE_PRIVATE)
        val lastWarnedVersion = prefs.getLong("version_code_warn", 0L)
        val currentVersion =
            runCatching {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                androidx.core.content.pm.PackageInfoCompat
                    .getLongVersionCode(pInfo)
            }.getOrDefault(0L)
        if (currentVersion > 0L && currentVersion > lastWarnedVersion) {
            delay(1200L)
            val warnMessage = context.getString(org.woheller69.freeDroidWarn.R.string.dialog_Warning)
            val moreInfoLabel = context.getString(org.woheller69.freeDroidWarn.R.string.dialog_more_info)

            val result =
                snackbarHostState.showSnackbar(
                    message = warnMessage,
                    actionLabel = moreInfoLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Custom(9000L),
                )
            prefs.edit().putLong("version_code_warn", currentVersion).apply()

            if (result == SnackbarResult.ActionPerformed) {
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://keepandroidopen.org"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
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
                    IllustiaNavigationRequest.Notifications -> AppRoute.Notifications
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
                    IllustiaNavigationRequest.ExperimentalSettings -> AppRoute.ExperimentalSettings
                    IllustiaNavigationRequest.PallaSyncSettings -> AppRoute.PallaSyncSettings
                    IllustiaNavigationRequest.PallaSyncDevices -> AppRoute.PallaSyncDevices
                    IllustiaNavigationRequest.UpdateSettings -> AppRoute.UpdateSettings
                    IllustiaNavigationRequest.DiscordSettings -> AppRoute.DiscordSettings
                    IllustiaNavigationRequest.DiscordLogin -> AppRoute.DiscordLogin
                },
            )
        }
    }

    val currentRoute = backStack.lastOrNull()
    val viewingIllust = state.selectedIllust.takeIf { currentRoute is AppRoute.Detail }
    LaunchedEffect(
        state.settings.discordRpcEnabled,
        state.settings.discordToken,
        state.settings.discordApplicationId,
        state.settings.discordRpcShowArtworkDetails,
        state.settings.discordRpcShowButtons,
        currentRoute,
        viewingIllust,
    ) {
        discordRpcManager.updatePresence(
            settings = state.settings,
            selectedIllust = viewingIllust,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            discordRpcManager.close()
        }
    }

    LaunchedEffect(state.selectedIllust?.id) {
        if (state.selectedIllust != null) {
            navigate(AppRoute.Detail(state.selectedIllust!!.id))
        } else if (backStack.lastOrNull() is AppRoute.Detail) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    LaunchedEffect(
        state.selectedIllust,
        state.relatedIllusts,
        state.selectedIllustFirstComment,
        state.selectedIllustUser,
    ) {
        state.selectedIllust?.let { illust ->
            detailSnapshots[illust.id] =
                DetailEntrySnapshot(
                    illust = illust,
                    relatedIllusts = state.relatedIllusts,
                    firstComment = state.selectedIllustFirstComment,
                    user = state.selectedIllustUser,
                )
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.detailNavigationRequests.collect { illustId ->
            navigate(AppRoute.Detail(illustId))
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.userNavigationRequests.collect { userId ->
            navigate(AppRoute.UserProfile(userId))
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

    val activeUserId = state.selectedUserId ?: state.selectedUser?.id
    LaunchedEffect(state.showUserPage, state.userPageDismissed, activeUserId) {
        if (state.showUserPage && !state.userPageDismissed && activeUserId != null) {
            val route = AppRoute.UserProfile(activeUserId)
            if (backStack.lastOrNull() != route) {
                val existingIndex = backStack.indexOfLast { it == route }
                if (existingIndex >= 0 && backStack.drop(existingIndex + 1).all { it is AppRoute.UserProfile }) {
                    while (backStack.lastIndex > existingIndex) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                } else {
                    backStack.add(route)
                }
            }
        } else if (backStack.lastOrNull() is AppRoute.UserProfile) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { backStack.any { it is AppRoute.UserProfile } }
            .collect { hasUserProfile ->
                if (!hasUserProfile) {
                    delay(350)
                    if (backStack.none { it is AppRoute.UserProfile }) {
                        viewModel.closeUserPage()
                    }
                }
            }
    }

    val preferLowDataImages = remember(context) { context.isActiveNetworkMetered() }
    val platformHapticFeedback = LocalHapticFeedback.current
    val hapticsSupported = remember(context) { isAppHapticsSupported(context) }
    val effectiveHapticMode = effectiveAppHapticMode(state.settings.hapticMode, hapticsSupported)
    CompositionLocalProvider(
        LocalPixivImageProxyBaseUrl provides state.settings.pixivImageProxyBaseUrl,
        LocalPreferLowDataImages provides preferLowDataImages,
        LocalBottomSheetBackgroundColor provides MiuixTheme.colorScheme.surfaceContainerHigh,
        LocalArtworkCardPreferences provides
            ArtworkCardPreferences(
                showTitle = settings.showCardTitle,
                showArtist = settings.showCardArtist,
                showTags = settings.showCardTags,
                showBookmarkCount = settings.showCardBookmarkCount,
                showAiBadge = settings.showAiBadge,
            ),
        LocalAppHapticMode provides effectiveHapticMode,
        LocalHapticFeedback provides
            if (effectiveHapticMode == AppHapticMode.Off) {
                NoOpHapticFeedback
            } else {
                platformHapticFeedback
            },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .then(
                        if (effectiveHapticMode == AppHapticMode.Off) Modifier else Modifier.scrollEndHaptic(),
                    ),
        ) {
            if (!state.privacyLocked || state.isTransitioningToIllustia) {
                Scaffold(
                    containerColor = MiuixTheme.colorScheme.surface,
                    contentWindowInsets = WindowInsets(0),
                    snackbarHost = {
                        SnackbarHost(state = snackbarHostState)
                    },
                ) { rootPadding ->
                    val isDesktop = remember(context) { DesktopEnvironment.isDesktop(context) }
                    val rootRailState = rememberNavigationRailState()
                    val isFullscreenRoute = backStack.lastOrNull() == AppRoute.ImageViewer
                    BoxWithConstraints(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(rootPadding),
                    ) {
                        val isLandscape = (maxWidth > maxHeight && maxWidth >= 480.dp) || maxWidth >= 600.dp
                        val showRootNavigationRail = ((isDesktop && maxWidth >= 480.dp) || isLandscape) && !isFullscreenRoute
                        val navigationTabs = visibleTabs(appState.settings)

                        CompositionLocalProvider(LocalUseNavigationRail provides showRootNavigationRail) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                if (showRootNavigationRail) {
                                    NavigationRail(
                                        state = rootRailState,
                                        color = MiuixTheme.colorScheme.surfaceContainer,
                                        showDivider = true,
                                    ) {
                                        navigationTabs.forEach { tab ->
                                            val pageIndex = tabs.indexOf(tab)
                                            val isSelected = (backStack.lastOrNull() == AppRoute.Main) && (selectedTab == tab)
                                            NavigationRailItem(
                                                selected = isSelected,
                                                onClick = {
                                                    viewModel.closeAccountSwitcher()
                                                    if (backStack.lastOrNull() != AppRoute.Main) {
                                                        backStack.clear()
                                                        backStack.add(AppRoute.Main)
                                                        selectedWatchlistSeriesIds.clear()
                                                    }
                                                    if (tab == AppTab.ShortsFeed && selectedTab == AppTab.ShortsFeed) {
                                                        viewModel.refreshShortsFeed()
                                                    } else {
                                                        selectedTab = tab
                                                        coroutineScope.launch { pagerState.animateScrollToPage(pageIndex) }
                                                        if (tab == AppTab.Bookmarks) viewModel.refreshBookmarks()
                                                    }
                                                },
                                                icon = tab.icon,
                                                label = stringResource(tab.labelResId),
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    color = MiuixTheme.colorScheme.surface,
                                ) {
                                    AppNavHost(
                                        appState = appState,
                                        viewModel = viewModel,
                                        backStack = backStack,
                                        detailSnapshots = detailSnapshots,
                                        selectedTab = selectedTab,
                                        pagerState = pagerState,
                                        homeScrollBehavior = homeScrollBehavior,
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
                                        onSearchTag = ::searchFromDetail,
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
                    modifier =
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                )
            }
        }
    }
}
