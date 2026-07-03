package com.yunfie.illustia.ui

import android.app.Activity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import com.yunfie.illustia.IllustiaNavigationRequest
import com.yunfie.illustia.BookmarkChromeState
import com.yunfie.illustia.HomeChromeState
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.NovelChromeState
import com.yunfie.illustia.RankingChromeState
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.AppHapticMode
import com.yunfie.illustia.ui.components.*
import com.yunfie.illustia.ui.screens.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Search as MiuixSearch
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.window.WindowBottomSheet

enum class AppTab(
    @androidx.annotation.StringRes val labelResId: Int,
    @androidx.annotation.StringRes val titleResId: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, R.string.nav_home, MiuixIcons.VerticalSplit),
    Novel(R.string.nav_novel, R.string.nav_novel, MiuixIcons.Photos),
    Ranking(R.string.nav_ranking, R.string.nav_ranking, MiuixIcons.TopDownloads),
    Bookmarks(R.string.nav_bookmarks, R.string.nav_bookmarks_full, MiuixIcons.FavoritesFill),
    Search(R.string.nav_search, R.string.nav_search, MiuixIcons.MiuixSearch),
    More(R.string.nav_more, R.string.nav_more, MiuixIcons.More),
}

private val SwipeTabs = listOf(AppTab.Home, AppTab.Search, AppTab.Bookmarks, AppTab.Ranking, AppTab.More)
private val VisibleTabs = SwipeTabs

private fun startupTabFor(value: String): AppTab {
    return when (value) {
        "ranking" -> AppTab.Ranking
        "bookmarks" -> AppTab.Bookmarks
        "search" -> AppTab.Search
        "more" -> AppTab.More
        else -> AppTab.Home
    }
}

private sealed interface AppRoute : NavKey {
    data object Main : AppRoute
    data object Onboarding : AppRoute
    data object Detail : AppRoute
    data object ImageViewer : AppRoute
    data object NovelList : AppRoute
    data object NovelReader : AppRoute
    data object Settings : AppRoute
    data object GeneralSettings : AppRoute
    data object ImageSettings : AppRoute
    data object BookmarkSettings : AppRoute
    data object AccountSettings : AppRoute
    data object AccountLoginMethod : AppRoute
    data object DataSettings : AppRoute
    data object ViewHistory : AppRoute
    data object MuteSettings : AppRoute
    data object AppData : AppRoute
    data object DownloadQueue : AppRoute
    data object OfflineLibrary : AppRoute
    data object SavedIllustViewer : AppRoute
    data object About : AppRoute
    data object FavoriteTags : AppRoute
    data object WatchlistSeries : AppRoute
    data object UserProfile : AppRoute
    data object AppLockSetup : AppRoute
    data object AppLockPinEntry : AppRoute
    data object PrivacyModeSettings : AppRoute
    data object IllustSeries : AppRoute
    data object Comments : AppRoute
}

@Composable
fun IllustiaApp(viewModel: IllustiaViewModel) {
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
    val initialTab = remember(startupScreen) { startupTabFor(startupScreen) }
    val initialPage = remember(initialTab) { SwipeTabs.indexOf(initialTab).coerceAtLeast(0) }
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var showTokenLogin by remember { mutableStateOf(false) }
    var selectedWatchlistSeriesId by remember { mutableStateOf<Long?>(null) }
    var selectedCommentTarget by remember { mutableStateOf<Pair<Long, CommentArtworkType>?>(null) }
    val backStack = remember { mutableStateListOf<NavKey>(AppRoute.Main) }
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { SwipeTabs.size })
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

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

    fun popRoute() {
        if (backStack.size <= 1) return
        if (selectedCommentTarget != null) {
            selectedCommentTarget = null
            return
        }
        when (val route = backStack.removeAt(backStack.lastIndex)) {
            AppRoute.Detail -> viewModel.closeIllust()
            AppRoute.ImageViewer -> viewModel.closeImageViewer()
            AppRoute.NovelList -> Unit
            AppRoute.NovelReader -> viewModel.closeNovel()
            AppRoute.IllustSeries -> selectedWatchlistSeriesId = null
            AppRoute.UserProfile -> {
                // ここでは単に非表示フラグを立てるだけにし、
                // 実際のデータクリアはバックスタックの監視(LaunchedEffect)で行われるようにする
                viewModel.hideUserPage()
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
            }
        } else {
            backStack.clear()
            backStack.add(AppRoute.Onboarding)
        }
    }

    // When app becomes locked, collapse back stack to Main so no screens
    // are reachable behind the lock overlay.
    LaunchedEffect(state.appLocked) {
        if (state.appLocked && state.settings.appLockEnabled) {
            backStack.clear()
            backStack.add(AppRoute.Main)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = SwipeTabs[pagerState.currentPage]
    }

    LaunchedEffect(state.activeSearchWord) {
        if (state.activeSearchWord.isNotBlank() && selectedTab != AppTab.Search) {
            selectedTab = AppTab.Search
            pagerState.scrollToPage(SwipeTabs.indexOf(AppTab.Search))
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
        } else {
            if (backStack.lastOrNull() == AppRoute.UserProfile) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }

    // UserProfile ルートがバックスタックから削除されたら遅延クリーンアップ
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
        val entryProvider = entryProvider<NavKey> {
            entry(AppRoute.Main) {
                MainSurface(
                    settings = settings,
                    activeSearchWord = state.activeSearchWord,
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
                    state = state,
                    viewModel = viewModel,
                    selectedTab = selectedTab,
                    pagerState = pagerState,
                    onTabSelected = { index, tab ->
                        selectedTab = tab
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                        if (tab == AppTab.Bookmarks) viewModel.refreshBookmarks()
                    },
                    onSearch = {
                        selectedTab = AppTab.Search
                        coroutineScope.launch { pagerState.animateScrollToPage(SwipeTabs.indexOf(AppTab.Search)) }
                    },
                    onOpenNovels = {
                        navigate(AppRoute.NovelList)
                    },
                    onOpenWatchlistSeries = {
                        navigate(AppRoute.WatchlistSeries)
                    },
                )
            }
            entry(AppRoute.Onboarding) {
                var showTokenLogin by remember { mutableStateOf(false) }
                OnboardingScreen(
                    state = state,
                    viewModel = viewModel,
                    onRefreshTokenLogin = { showTokenLogin = true },
                    showTokenLogin = showTokenLogin,
                    onTokenLoginDismiss = { showTokenLogin = false },
                )
            }
            entry(AppRoute.Detail) {
                state.selectedIllust?.let { illust ->
                    IllustDetailScreen(
                        illust = illust,
                        relatedIllusts = state.relatedIllusts,
                        firstComment = state.selectedIllustFirstComment,
                        onBack = ::popRoute,
                        onBookmark = { viewModel.toggleBookmark(illust) },
                        onOpenUser = viewModel::openUser,
                        onOpenComments = {
                            selectedCommentTarget = illust.id to CommentArtworkType.ILLUST
                        },
                        onOpenSeries = illust.series?.id?.let { seriesId ->
                            { selectedWatchlistSeriesId = seriesId; navigate(AppRoute.IllustSeries) }
                        },
                        onOpenImage = { page -> viewModel.openImageViewer(illust, page) },
                        onSearchTag = { tag ->
                            popRoute()
                            viewModel.submitSearch(tag)
                        },
                        isArtistFollowed = state.selectedIllustUser?.isFollowed == true,
                        isArtistMuted = state.settings.mutedUsers.contains(illust.artistId),
                        onToggleFollow = { state.selectedIllustUser?.let { viewModel.toggleFollow(it) } ?: viewModel.openUser(illust.artistId) },
                        onUnmuteUser = { viewModel.unmuteUser(illust.artistId) },
                        onMuteIllust = { viewModel.muteIllust(illust.id) },
                        onMuteUser = { viewModel.muteUser(illust.artistId) },
                        onMuteTag = { tag -> viewModel.muteTag(tag) },
                        onOpenIllust = viewModel::openIllust,
                        onOpenIllustById = viewModel::openIllust,
                        onSaveImage = viewModel::saveImage,
                        onSaveAllImages = viewModel::saveImages,
                        onMessage = viewModel::showMessage,
                        highQualityImages = state.settings.highQualityImages,
                        detailQuality = if (illust.type == "manga") state.settings.mangaDetailQuality else state.settings.illustDetailQuality,
                        prefetchImages = state.settings.prefetchImages,
                        confirmOnLongPressSave = state.settings.confirmOnLongPressSave,
                        skipConfirmOnDetailSave = state.settings.skipConfirmOnDetailSave,
                    )
                }
            }
            entry(AppRoute.ImageViewer) {
                state.imageViewerIllust?.let { illust ->
                    ImageViewerScreen(
                        illust = illust,
                        startPage = state.imageViewerStartPage,
                        onBack = ::popRoute,
                        onSaveImage = viewModel::saveImage,
                        onMessage = viewModel::showMessage,
                        fullscreenQuality = state.settings.fullscreenQuality,
                        prefetchImages = state.settings.prefetchImages,
                        thumbnailsInToolbar = state.settings.viewerThumbnailsInToolbar,
                    )
                }
            }
            entry(AppRoute.NovelList) {
                NovelScreen(
                    items = novelItems,
                    loadState = loadState,
                    nextUrl = novelChrome.novelNextUrl,
                    settings = settings,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.NovelReader) {
                NovelReaderScreen(
                    novel = state.selectedNovel,
                    text = state.selectedNovelText,
                    loadState = loadState,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                    onRetry = {
                        state.selectedNovel?.let(viewModel::openNovel)
                    },
                )
            }
            entry(AppRoute.Settings) {
                SettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.GeneralSettings) {
                GeneralSettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.ImageSettings) {
                ImageSettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.BookmarkSettings) {
                BookmarkSettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.AccountSettings) {
                AccountSettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.AccountLoginMethod) {
                AccountLoginMethodScreen(
                    onBack = ::popRoute,
                    onWebLogin = viewModel::openWebLogin,
                    onRefreshTokenLogin = { showTokenLogin = true },
                )
            }
            entry(AppRoute.DataSettings) {
                DataSettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.ViewHistory) {
                ViewHistoryScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.MuteSettings) {
                MuteSettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.AppData) {
                AppDataScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.DownloadQueue) {
                DownloadQueueScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.OfflineLibrary) {
                OfflineLibraryScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.SavedIllustViewer) {
                SavedIllustViewerScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.About) {
                AboutScreen(onBack = ::popRoute)
            }
            entry(AppRoute.FavoriteTags) {
                FavoriteTagsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.WatchlistSeries) {
                WatchlistSeriesScreen(
                    viewModel = viewModel,
                    onBack = ::popRoute,
                    onOpenSeries = { seriesId ->
                        selectedWatchlistSeriesId = seriesId
                        navigate(AppRoute.IllustSeries)
                    },
                )
            }
            entry(AppRoute.IllustSeries) {
                val seriesId = selectedWatchlistSeriesId
                if (seriesId != null) {
                    IllustSeriesScreen(
                        seriesId = seriesId,
                        viewModel = viewModel,
                        onBack = ::popRoute,
                        onOpenIllust = { illustId -> viewModel.openIllust(illustId) },
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }
            }
            entry(AppRoute.UserProfile) {
                if (state.selectedUser != null) {
                    val user = state.selectedUser!!
                    UserProfileScreen(
                        user = user,
                        settings = state.settings,
                        illusts = state.selectedUserIllusts,
                        bookmarks = state.selectedUserBookmarks,
                        hasMore = state.selectedUserNextUrl != null,
                        bookmarkHasMore = state.selectedUserBookmarksNextUrl != null,
                        onBack = {
                            if (state.userPageFromSheet) {
                                viewModel.collapseUserPageToSheet()
                            } else {
                                viewModel.hideUserPage()
                                popRoute()
                            }
                        },
                        onOpenIllust = { illust ->
                            viewModel.openIllust(illust)
                        },
                        onBookmark = viewModel::toggleBookmark,
                        onLoadMore = viewModel::loadMoreUserIllusts,
                        onLoadBookmarks = viewModel::loadSelectedUserBookmarks,
                        onLoadMoreBookmarks = viewModel::loadMoreSelectedUserBookmarks,
                        onToggleFollow = { viewModel.toggleFollow(user) },
                        onMuteUser = { viewModel.muteUser(user.id) },
                        isMuted = state.settings.mutedUsers.contains(user.id),
                        onUnmuteUser = { viewModel.unmuteUser(user.id) },
                        showHeaderControls = true,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }
            }
            entry(AppRoute.AppLockSetup) {
                AppLockSetupScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.AppLockPinEntry) {
                PinSetupScreen(
                    isChange = state.settings.appLockEnabled,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
            entry(AppRoute.PrivacyModeSettings) {
                PrivacyModeSettingsScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::popRoute,
                )
            }
        }
        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryProvider = entryProvider,
        )
        NavDisplay(
            entries = entries,
            onBack = ::popRoute,
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
        )

        selectedCommentTarget?.let { target ->
            CommentScreen(
                show = true,
                id = target.first,
                type = target.second,
                viewModel = viewModel,
                onDismiss = {
                    selectedCommentTarget = null
                },
                onBack = {
                    selectedCommentTarget = null
                },
                onOpenUser = { userId ->
                    selectedCommentTarget = null
                    viewModel.openUser(userId)
                },
            )
        }

        state.longPressedIllust?.let { illust ->
            OverlayBottomSheet(
                show = true,
                title = illust.title.ifBlank { stringResource(R.string.dialog_work_options) },
                onDismissRequest = viewModel::closeIllustOptions,
                backgroundColor = LocalBottomSheetBackgroundColor.current,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_artist_label, illust.artistName),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                        Button(
                            onClick = {
                                viewModel.closeIllustOptions()
                                viewModel.openIllust(illust)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.dialog_show_detail))
                        }
                        Button(
                            onClick = {
                                viewModel.closeIllustOptions()
                                viewModel.toggleBookmark(illust)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(if (illust.isBookmarked) stringResource(R.string.action_remove_bookmark) else stringResource(R.string.action_bookmark))
                        }
                        Button(
                            onClick = {
                                viewModel.closeIllustOptions()
                                viewModel.saveImage(illust.originalImageUrl ?: illust.imageUrl, "illustia_${illust.id}")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = overlayActionButtonColors(),
                        ) {
                            Text(stringResource(R.string.detail_save_image))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.closeIllustOptions()
                                    viewModel.muteIllust(illust.id)
                                },
                                modifier = Modifier.weight(1f),
                                colors = overlayActionButtonColors(),
                            ) {
                                Text(stringResource(R.string.detail_mute_work), color = MiuixTheme.colorScheme.error)
                            }
                            Button(
                                onClick = {
                                    viewModel.closeIllustOptions()
                                    viewModel.muteUser(illust.artistId)
                                },
                                modifier = Modifier.weight(1f),
                                colors = overlayActionButtonColors(),
                            ) {
                                Text(stringResource(R.string.detail_mute_artist), color = MiuixTheme.colorScheme.error)
                            }
                        }
                }
            }
        }

        state.selectedUser?.let { user ->
            if (!state.showUserPage && !state.userPageDismissed) {
                val userSheetBackground = LocalBottomSheetBackgroundColor.current
                val userSheetHeight = minOf(configuration.screenHeightDp.dp * 0.68f, 560.dp)
                WindowBottomSheet(
                    show = true,
                    title = user.name.ifBlank { "@${user.account}" },
                    backgroundColor = userSheetBackground,
                    startAction = {
                        IconButton(onClick = viewModel::closeUser) {
                            Icon(imageVector = MiuixIcons.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    endAction = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = viewModel::expandUserSheetToPage) {
                                Icon(imageVector = MiuixIcons.TopDownloads, contentDescription = stringResource(R.string.user_open_full_page))
                            }
                            WindowIconDropdownMenu(
                                entry = DropdownEntry(
                                    items = listOf(
                                        DropdownItem(
                                            text = stringResource(R.string.dialog_mute),
                                            onClick = {
                                                viewModel.muteUser(user.id)
                                                viewModel.closeUser()
                                            },
                                        ),
                                    ),
                                ),
                            ) {
                                Icon(imageVector = MiuixIcons.More, contentDescription = stringResource(R.string.detail_more))
                            }
                        }
                    },
                    onDismissRequest = viewModel::closeUser,
                ) {
                    NonAmoledDarkTheme {
                        UserProfileScreen(
                            user = user,
                            settings = state.settings,
                            illusts = state.selectedUserIllusts,
                            bookmarks = state.selectedUserBookmarks,
                            hasMore = state.selectedUserNextUrl != null,
                            bookmarkHasMore = state.selectedUserBookmarksNextUrl != null,
                            onBack = viewModel::closeUser,
                            onOpenIllust = { illust ->
                                viewModel.closeUser()
                                viewModel.openIllust(illust)
                            },
                            onBookmark = viewModel::toggleBookmark,
                            onLoadMore = viewModel::loadMoreUserIllusts,
                            onLoadBookmarks = viewModel::loadSelectedUserBookmarks,
                            onLoadMoreBookmarks = viewModel::loadMoreSelectedUserBookmarks,
                            onToggleFollow = { viewModel.toggleFollow(user) },
                            onMuteUser = { viewModel.muteUser(user.id) },
                            isMuted = state.settings.mutedUsers.contains(user.id),
                            onUnmuteUser = { viewModel.unmuteUser(user.id) },
                            showHeaderControls = false,
                            backgroundColor = userSheetBackground,
                            contentHeight = userSheetHeight,
                        )
                    }
                }
            }
        }

        if (showTokenLogin) {
            RefreshTokenLoginBottomSheet(
                state = state,
                viewModel = viewModel,
                onDismiss = { showTokenLogin = false },
            )
        }

        MiuixConfirmDialog(
            show = state.showReloginRequiredDialog,
            title = stringResource(R.string.dialog_relogin_title),
            summary = stringResource(R.string.dialog_relogin_summary),
            confirmText = stringResource(R.string.dialog_relogin_button),
            onConfirm = viewModel::openWebLogin,
            onDismiss = viewModel::dismissReloginRequiredDialog,
        )

        state.pendingBookmarkRemoval?.let { illust ->
            MiuixConfirmDialog(
                show = true,
                title = stringResource(R.string.dialog_unbookmark_title),
                summary = stringResource(R.string.dialog_unbookmark_confirm, illust.title.ifBlank { stringResource(R.string.detail_muted_artist_blur_default) }),
                confirmText = stringResource(R.string.action_remove_bookmark),
                destructive = true,
                onConfirm = viewModel::confirmBookmarkRemoval,
                onDismiss = viewModel::cancelBookmarkRemoval,
            )
        }
                }
            }
        }
        } else {
            // Privacy locked & not transitioning: show calculator without composing the main UI.
            CalculatorScreen(
                buffer = state.calculatorBuffer,
                history = state.calculatorHistory,
                isTransitioning = false,
                viewModel = viewModel,
            )
        }

    }
}

@Composable
private fun MainSurface(
    settings: AppSettings,
    activeSearchWord: String,
    loadState: LoadState,
    homeItems: List<Illust>,
    novelItems: List<com.yunfie.illustia.models.NovelPreview>,
    timelineItems: List<Illust>,
    rankingItems: List<Illust>,
    bookmarkItems: List<Illust>,
    watchlistItems: List<Illust>,
    followingUsers: List<UserPreview>,
    homeChrome: HomeChromeState,
    novelChrome: NovelChromeState,
    rankingChrome: RankingChromeState,
    bookmarkChrome: BookmarkChromeState,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    selectedTab: AppTab,
    pagerState: androidx.compose.foundation.pager.PagerState,
    onTabSelected: (Int, AppTab) -> Unit,
    onSearch: () -> Unit,
    onOpenNovels: () -> Unit,
    onOpenWatchlistSeries: () -> Unit,
) {
    val context = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    val isSearchResultMode = selectedTab == AppTab.Search &&
        activeSearchWord.isNotBlank()
    val doubleBackExitMessage = stringResource(R.string.msg_double_back_exit)

    PredictiveBackGestureHandler(enabled = settings.doubleBackToExit) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt < 1800L) {
            (context as? Activity)?.finish()
        } else {
            lastBackAt = now
            viewModel.showMessage(doubleBackExitMessage)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
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
                        VisibleTabs.forEach { tab ->
                            val pageIndex = SwipeTabs.indexOf(tab)
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = { onTabSelected(pageIndex, tab) },
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
                        VisibleTabs.forEach { tab ->
                            val pageIndex = SwipeTabs.indexOf(tab)
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = { onTabSelected(pageIndex, tab) },
                                icon = tab.icon,
                                label = stringResource(tab.labelResId),
                            )
                        }
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = settings.swipeToSwitchWorks,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MiuixTheme.colorScheme.surface),
                ) { page ->
            when (SwipeTabs[page]) {
                AppTab.Home -> HomeScreen(
                    items = homeItems,
                    timelineItems = timelineItems,
                    loadState = loadState,
                    nextUrl = homeChrome.homeNextUrl,
                    timelineNextUrl = homeChrome.timelineNextUrl,
                    settings = settings,
                    viewModel = viewModel,
                    onSearch = onSearch,
                    onOpenNovels = onOpenNovels,
                )
                AppTab.Novel -> Unit
                AppTab.Ranking -> RankingScreen(
                    items = rankingItems,
                    loadState = loadState,
                    nextUrl = rankingChrome.rankingNextUrl,
                    mode = rankingChrome.rankingMode,
                    settings = settings,
                    viewModel = viewModel
                )
                AppTab.Bookmarks -> BookmarkScreen(
                    settings = settings,
                    loadState = loadState,
                    bookmarkItems = bookmarkItems,
                    timelineItems = timelineItems,
                    followingUsers = followingUsers,
                    chrome = bookmarkChrome,
                    viewModel = viewModel,
                )
                AppTab.Search -> SearchScreen(state = state, viewModel = viewModel)
                AppTab.More -> MoreScreen(state = state, viewModel = viewModel, onOpenWatchlistSeries = onOpenWatchlistSeries)
            }
                }
            }
        }

        // Privacy mode overlay — full-screen calculator shown when privacyLocked
        if (state.privacyLocked) {
            CalculatorScreen(
                buffer = state.calculatorBuffer,
                history = state.calculatorHistory,
                isTransitioning = state.isTransitioningToIllustia,
                viewModel = viewModel,
            )
        } else if (state.appLocked && state.settings.appLockEnabled) {
            // App lock overlay covers everything only when privacy mode is not active.
            AppLockScreen(
                biometricEnabled = state.settings.biometricEnabled,
                failCount = state.settings.appLockFailCount,
                cooldownUntil = state.settings.appLockCooldownUntil,
                viewModel = viewModel,
            )
        }

        // Lock recovery dialog — shown after 12 consecutive PIN failures
        if (state.showLockRecoveryDialog) {
            OverlayDialog(
                show = true,
                title = stringResource(R.string.app_lock_recovery_title),
                summary = stringResource(R.string.app_lock_recovery_summary),
                backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
                onDismissRequest = {},
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = viewModel::resetAppLockData,
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(
                            stringResource(R.string.app_lock_recovery_reset),
                            color = MiuixTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = viewModel::openRecoveryWebLogin,
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        colors = overlayActionButtonColors(),
                    ) {
                        Text(
                            stringResource(R.string.app_lock_recovery_verify),
                            color = MiuixTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

