package com.yunfie.illustia.ui.app

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplay.popTransitionSpec
import androidx.navigation3.ui.NavDisplay.predictivePopTransitionSpec
import androidx.navigation3.ui.NavDisplay.transitionSpec
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import com.yunfie.illustia.isMutedByTags
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.screens.AboutScreen
import com.yunfie.illustia.ui.screens.AccountLoginMethodScreen
import com.yunfie.illustia.ui.screens.AccountSettingsScreen
import com.yunfie.illustia.ui.screens.AppDataScreen
import com.yunfie.illustia.ui.screens.AppLockSetupScreen
import com.yunfie.illustia.ui.screens.BookmarkSettingsScreen
import com.yunfie.illustia.ui.screens.CardCustomizationSettingsScreen
import com.yunfie.illustia.ui.screens.DataSettingsScreen
import com.yunfie.illustia.ui.screens.DetailSectionSettingsScreen
import com.yunfie.illustia.ui.screens.DiscordLoginScreen
import com.yunfie.illustia.ui.screens.DiscordSettingsScreen
import com.yunfie.illustia.ui.screens.DownloadQueueScreen
import com.yunfie.illustia.ui.screens.DownloadSettingsScreen
import com.yunfie.illustia.ui.screens.FavoriteTagsScreen
import com.yunfie.illustia.ui.screens.FeatureFlagsScreen
import com.yunfie.illustia.ui.screens.GeneralSettingsScreen
import com.yunfie.illustia.ui.screens.IllustDetailScreen
import com.yunfie.illustia.ui.screens.IllustSeriesScreen
import com.yunfie.illustia.ui.screens.ImageSettingsScreen
import com.yunfie.illustia.ui.screens.ImageViewerScreen
import com.yunfie.illustia.ui.screens.MuteSettingsScreen
import com.yunfie.illustia.ui.screens.NavigationSettingsScreen
import com.yunfie.illustia.ui.screens.NetworkSettingsScreen
import com.yunfie.illustia.ui.screens.NotificationScreen
import com.yunfie.illustia.ui.screens.NovelReaderScreen
import com.yunfie.illustia.ui.screens.NovelScreen
import com.yunfie.illustia.ui.screens.OfflineLibraryScreen
import com.yunfie.illustia.ui.screens.OnboardingScreen
import com.yunfie.illustia.ui.screens.PinSetupScreen
import com.yunfie.illustia.ui.screens.PrivacyModeSettingsScreen
import com.yunfie.illustia.ui.screens.SavedIllustViewerScreen
import com.yunfie.illustia.ui.screens.SearchScreen
import com.yunfie.illustia.ui.screens.SettingsScreen
import com.yunfie.illustia.ui.screens.UpdateSettingsScreen
import com.yunfie.illustia.ui.screens.UserProfileScreen
import com.yunfie.illustia.ui.screens.ViewHistoryScreen
import com.yunfie.illustia.ui.screens.WallpaperPlaylistSettingsScreen
import com.yunfie.illustia.ui.screens.WatchlistSeriesScreen
import com.yunfie.illustia.ui.screens.profile.RelatedUsersScreen
import com.yunfie.illustia.ui.screens.profile.UserProfileSkeletonScreen
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppNavHost(
    appState: IllustiaAppStateBundle,
    viewModel: IllustiaViewModel,
    backStack: MutableList<NavKey>,
    detailSnapshots: Map<Long, DetailEntrySnapshot>,
    searchSnapshots: Map<String, SearchEntrySnapshot> = emptyMap(),
    selectedTab: AppTab,
    pagerState: androidx.compose.foundation.pager.PagerState,
    homeScrollBehavior: ScrollBehavior,
    showTokenLogin: Boolean,
    onShowTokenLoginChange: (Boolean) -> Unit,
    selectedWatchlistSeriesId: Long?,
    onSelectedWatchlistSeriesIdChange: (Long?) -> Unit,
    selectedCommentTarget: Pair<Long, CommentArtworkType>?,
    onSelectedCommentTargetChange: (Pair<Long, CommentArtworkType>?) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onPopRoute: () -> Unit,
    onSearchTag: (String) -> Unit,
    onTabSelected: (Int, AppTab) -> Unit,
) {
    val entryProvider =
        entryProvider<NavKey> {
            entry(AppRoute.Main) {
                MainSurface(
                    appState = appState,
                    viewModel = viewModel,
                    selectedTab = selectedTab,
                    pagerState = pagerState,
                    homeScrollBehavior = homeScrollBehavior,
                    onTabSelected = onTabSelected,
                    onSearch = {
                        if (appState.settings.shortsFeedEnabled) {
                            onNavigate(AppRoute.Search)
                        } else {
                            onTabSelected(mainTabs(appState.settings).indexOf(AppTab.Search), AppTab.Search)
                        }
                    },
                    onOpenNovels = {
                        onNavigate(AppRoute.NovelList)
                    },
                    onOpenComments = { illustId ->
                        onSelectedCommentTargetChange(illustId to CommentArtworkType.ILLUST)
                    },
                    onOpenWatchlistSeries = { seriesId ->
                        onSelectedWatchlistSeriesIdChange(seriesId)
                        onNavigate(AppRoute.IllustSeries)
                    },
                    onNavigateToResults = { query ->
                        viewModel.submitSearch(query)
                        onNavigate(AppRoute.SearchResults(query))
                    },
                )
            }
            entry(
                AppRoute.Search,
                metadata = artworkPageTransitionMetadata(appState.settings.smoothTransitions),
            ) {
                SearchScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    isResultRoute = false,
                    onBack = onPopRoute,
                    onNavigateToResults = { query ->
                        viewModel.submitSearch(query)
                        onNavigate(AppRoute.SearchResults(query))
                    },
                )
            }
            entry<AppRoute.TagSearch>(
                metadata = artworkPageTransitionMetadata(appState.settings.smoothTransitions),
            ) { route ->
                val query = route.word
                val isCurrentActive = appState.state.activeSearchWord == query
                val snapshot =
                    if (isCurrentActive) {
                        SearchEntrySnapshot(
                            query = query,
                            searchItems = appState.state.searchItems,
                            searchNextUrl = appState.state.searchNextUrl,
                            searchNovelItems = appState.state.searchNovelItems,
                            searchNovelNextUrl = appState.state.searchNovelNextUrl,
                            userSearchItems = appState.state.userSearchItems,
                            userSearchNextUrl = appState.state.userSearchNextUrl,
                        )
                    } else {
                        searchSnapshots[query]
                    }
                val effectiveState =
                    if (isCurrentActive) {
                        appState.state
                    } else {
                        appState.state.copy(
                            activeSearchWord = query,
                            searchDraft = query,
                            searchItems = snapshot?.searchItems.orEmpty(),
                            searchNextUrl = snapshot?.searchNextUrl,
                            searchNovelItems = snapshot?.searchNovelItems.orEmpty(),
                            searchNovelNextUrl = snapshot?.searchNovelNextUrl,
                            userSearchItems = snapshot?.userSearchItems.orEmpty(),
                            userSearchNextUrl = snapshot?.userSearchNextUrl,
                        )
                    }
                SearchScreen(
                    state = effectiveState,
                    viewModel = viewModel,
                    isResultRoute = true,
                    onBackFromResults = onPopRoute,
                    onNavigateToResults = { nextQuery ->
                        viewModel.submitSearch(nextQuery)
                        onNavigate(AppRoute.SearchResults(nextQuery))
                    },
                )
            }
            entry<AppRoute.SearchResults>(
                metadata = artworkPageTransitionMetadata(appState.settings.smoothTransitions),
            ) { route ->
                val query = route.query
                val isCurrentActive = appState.state.activeSearchWord == query
                val snapshot =
                    if (isCurrentActive) {
                        SearchEntrySnapshot(
                            query = query,
                            searchItems = appState.state.searchItems,
                            searchNextUrl = appState.state.searchNextUrl,
                            searchNovelItems = appState.state.searchNovelItems,
                            searchNovelNextUrl = appState.state.searchNovelNextUrl,
                            userSearchItems = appState.state.userSearchItems,
                            userSearchNextUrl = appState.state.userSearchNextUrl,
                        )
                    } else {
                        searchSnapshots[query]
                    }
                val effectiveState =
                    if (isCurrentActive) {
                        appState.state
                    } else {
                        appState.state.copy(
                            activeSearchWord = query,
                            searchDraft = query,
                            searchItems = snapshot?.searchItems.orEmpty(),
                            searchNextUrl = snapshot?.searchNextUrl,
                            searchNovelItems = snapshot?.searchNovelItems.orEmpty(),
                            searchNovelNextUrl = snapshot?.searchNovelNextUrl,
                            userSearchItems = snapshot?.userSearchItems.orEmpty(),
                            userSearchNextUrl = snapshot?.userSearchNextUrl,
                        )
                    }
                SearchScreen(
                    state = effectiveState,
                    viewModel = viewModel,
                    isResultRoute = true,
                    onBackFromResults = onPopRoute,
                    onNavigateToResults = { nextQuery ->
                        viewModel.submitSearch(nextQuery)
                        onNavigate(AppRoute.SearchResults(nextQuery))
                    },
                )
            }
            entry(AppRoute.Onboarding) {
                var showTokenLoginSheet by remember { mutableStateOf(false) }
                OnboardingScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onRefreshTokenLogin = { showTokenLoginSheet = true },
                    showTokenLogin = showTokenLoginSheet,
                    onTokenLoginDismiss = { showTokenLoginSheet = false },
                )
            }
            entry<AppRoute.Detail>(
                metadata = artworkPageTransitionMetadata(appState.settings.smoothTransitions),
            ) { route ->
                val selectedIllust = appState.state.selectedIllust
                val snapshot =
                    if (selectedIllust?.id == route.illustId) {
                        DetailEntrySnapshot(
                            illust = selectedIllust,
                            relatedIllusts = appState.state.relatedIllusts,
                            firstComment = appState.state.selectedIllustFirstComment,
                            user = appState.state.selectedIllustUser,
                        )
                    } else {
                        detailSnapshots[route.illustId]
                    }
                snapshot?.let { detail ->
                    val illust = detail.illust
                    IllustDetailScreen(
                        illust = illust,
                        relatedIllusts = detail.relatedIllusts,
                        firstComment = detail.firstComment,
                        onBack = onPopRoute,
                        onBookmark = { viewModel.toggleBookmark(illust) },
                        onRefresh = { viewModel.refreshIllustDetail(illust.id) },
                        onOpenUser = viewModel::openUser,
                        onOpenComments = {
                            onSelectedCommentTargetChange(illust.id to CommentArtworkType.ILLUST)
                        },
                        onOpenSeries =
                            illust.series?.id?.let { seriesId ->
                                {
                                    onSelectedWatchlistSeriesIdChange(seriesId)
                                    onNavigate(AppRoute.IllustSeries)
                                }
                            },
                        onOpenImage = { page -> viewModel.openImageViewer(illust, page) },
                        onSearchTag = onSearchTag,
                        onLongPressTag = { tag ->
                            viewModel.openTagOptions(
                                rawTag = tag,
                                imageUrl =
                                    illust.squareImageUrl.ifBlank {
                                        illust.thumbnailUrl.ifBlank { illust.imageUrl }
                                    },
                            )
                        },
                        isArtistFollowed = detail.user?.isFollowed == true,
                        isArtistMuted =
                            appState.state.settings.mutedUsers
                                .contains(illust.artistId),
                        isTagMuted = illust.isMutedByTags(appState.state.settings),
                        onToggleFollow = {
                            detail.user?.let { viewModel.toggleFollow(it) }
                                ?: viewModel.openUser(illust.artistId)
                        },
                        onUnmuteUser = { viewModel.unmuteUser(illust.artistId) },
                        onMuteIllust = { viewModel.muteIllust(illust.id) },
                        onMuteUser = { viewModel.muteUser(illust.artistId) },
                        onMuteTag = { tag -> viewModel.muteTag(tag) },
                        onOpenIllust = viewModel::openIllust,
                        onLongPressIllust = viewModel::onIllustLongPress,
                        onOpenIllustById = viewModel::openIllust,
                        onSaveImage = viewModel::saveImage,
                        onSaveAllImages = viewModel::saveImages,
                        onMessage = viewModel::showMessage,
                        loadUgoiraPlayback = viewModel::loadUgoiraPlayback,
                        highQualityImages = appState.state.settings.highQualityImages,
                        detailQuality =
                            if (illust.type == "manga") {
                                appState.state.settings.mangaDetailQuality
                            } else {
                                appState.state.settings.illustDetailQuality
                            },
                        prefetchImages = appState.state.settings.prefetchImages,
                        confirmOnLongPressSave = appState.state.settings.confirmOnLongPressSave,
                        skipConfirmOnDetailSave = appState.state.settings.skipConfirmOnDetailSave,
                        detailSectionOrder = appState.state.settings.detailSectionOrder,
                        relatedIllustColumnCount = appState.state.settings.relatedIllustColumnCount,
                    )
                } ?: Box(
                    modifier = Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }
            }
            entry(
                AppRoute.ImageViewer,
                metadata = artworkPageTransitionMetadata(appState.settings.smoothTransitions),
            ) {
                appState.state.imageViewerIllust?.let { illust ->
                    ImageViewerScreen(
                        illust = illust,
                        startPage = appState.state.imageViewerStartPage,
                        onBack = onPopRoute,
                        isBookmarked = illust.isBookmarked,
                        onBookmark = { viewModel.toggleBookmark(illust) },
                        onMessage = viewModel::showMessage,
                        fullscreenQuality = appState.state.settings.fullscreenQuality,
                        prefetchImages = appState.state.settings.prefetchImages,
                        mangaReaderMode = appState.state.settings.mangaReaderMode,
                        onPageChanged = viewModel::updateImageViewerPage,
                        loadUgoiraPlayback = viewModel::loadUgoiraPlayback,
                    )
                }
            }
            entry(AppRoute.NovelList) {
                NovelScreen(
                    items = appState.novelItems,
                    loadState = appState.loadState,
                    nextUrl = appState.novelChrome.novelNextUrl,
                    settings = appState.settings,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                )
            }
            entry(AppRoute.NovelReader) {
                NovelReaderScreen(
                    novel = appState.state.selectedNovel,
                    text = appState.state.selectedNovelText,
                    loadState = appState.loadState,
                    settings = appState.settings,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onRetry = {
                        appState.state.selectedNovel?.let(viewModel::openNovel)
                    },
                )
            }
            entry(AppRoute.Settings) {
                SettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.GeneralSettings) {
                GeneralSettingsScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onOpenNavigationSettings = { onNavigate(AppRoute.NavigationSettings) },
                )
            }
            entry(AppRoute.NavigationSettings) {
                NavigationSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.FeatureFlags) {
                FeatureFlagsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.ImageSettings) {
                ImageSettingsScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onOpenWallpaperPlaylistSettings = { onNavigate(AppRoute.WallpaperPlaylistSettings) },
                    onOpenCardCustomizationSettings = { onNavigate(AppRoute.CardCustomizationSettings) },
                    onOpenDetailSectionSettings = { onNavigate(AppRoute.DetailSectionSettings) },
                    onOpenDownloadSettings = { onNavigate(AppRoute.DownloadSettings) },
                    onOpenNetworkSettings = { onNavigate(AppRoute.NetworkSettings) },
                )
            }
            entry(AppRoute.CardCustomizationSettings) {
                CardCustomizationSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.DetailSectionSettings) {
                DetailSectionSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.DownloadSettings) {
                DownloadSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.NetworkSettings) {
                NetworkSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.WallpaperPlaylistSettings) {
                WallpaperPlaylistSettingsScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                )
            }
            entry(AppRoute.BookmarkSettings) {
                BookmarkSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.AccountSettings) {
                AccountSettingsScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onOpenPallaSync = { onNavigate(AppRoute.PallaSyncSettings) },
                    onOpenDiscordSettings = { onNavigate(AppRoute.DiscordSettings) },
                )
            }
            entry(AppRoute.DiscordSettings) {
                DiscordSettingsScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onOpenDiscordLogin = { onNavigate(AppRoute.DiscordLogin) },
                )
            }
            entry(AppRoute.DiscordLogin) {
                DiscordLoginScreen(
                    viewModel = viewModel,
                    onBack = onPopRoute,
                )
            }
            entry(AppRoute.PallaSyncSettings) {
                com.yunfie.illustia.ui.screens.pallasync.PallaSyncSettingsScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onPairDevice = { onNavigate(AppRoute.DevicePairing) },
                    onDeviceClick = { deviceId, deviceName ->
                        onNavigate(AppRoute.DeviceViewHistory(deviceId, deviceName))
                    },
                )
            }
            entry(AppRoute.PallaSyncDevices) {
                com.yunfie.illustia.ui.screens.pallasync.PallaSyncDevicesScreen(
                    state = appState.state,
                    onBack = onPopRoute,
                    onDeviceClick = { deviceId, deviceName ->
                        onNavigate(AppRoute.DeviceViewHistory(deviceId, deviceName))
                    },
                )
            }
            entry(AppRoute.DevicePairing) {
                com.yunfie.illustia.ui.screens.pallasync.DevicePairingScreen(
                    serverUrl = appState.state.settings.pallaSyncServerUrl,
                    onBack = onPopRoute,
                    onPairSuccess = {
                        onPopRoute()
                    },
                )
            }
            entry(AppRoute.AccountLoginMethod) {
                AccountLoginMethodScreen(
                    onBack = onPopRoute,
                    onWebLogin = viewModel::openWebLogin,
                    onRefreshTokenLogin = { onShowTokenLoginChange(true) },
                )
            }
            entry(AppRoute.DataSettings) {
                DataSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.ViewHistory) {
                ViewHistoryScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.Notifications) {
                NotificationScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.MuteSettings) {
                MuteSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.AppData) {
                AppDataScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.DownloadQueue) {
                DownloadQueueScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.OfflineLibrary) {
                OfflineLibraryScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.SavedIllustViewer) {
                SavedIllustViewerScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.About) {
                AboutScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                )
            }
            entry(AppRoute.UpdateSettings) {
                UpdateSettingsScreen(
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                )
            }
            entry(AppRoute.FavoriteTags) {
                FavoriteTagsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.WatchlistSeries) {
                WatchlistSeriesScreen(
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onOpenSeries = { seriesId ->
                        onSelectedWatchlistSeriesIdChange(seriesId)
                        onNavigate(AppRoute.IllustSeries)
                    },
                )
            }
            entry(AppRoute.IllustSeries) {
                val currentSeriesId = selectedWatchlistSeriesId
                if (currentSeriesId != null) {
                    IllustSeriesScreen(
                        seriesId = currentSeriesId,
                        viewModel = viewModel,
                        onBack = onPopRoute,
                        onOpenIllust = { illustId -> viewModel.openIllust(illustId) },
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MiuixTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator()
                    }
                }
            }
            entry<AppRoute.UserProfile> { route ->
                val selectedUser = appState.state.selectedUser
                if (selectedUser?.id == route.userId) {
                    val user = selectedUser
                    UserProfileScreen(
                        user = user,
                        settings = appState.state.settings,
                        illusts = appState.state.selectedUserIllusts,
                        bookmarks = appState.state.selectedUserBookmarks,
                        hasMore = appState.state.selectedUserNextUrl != null,
                        bookmarkHasMore = appState.state.selectedUserBookmarksNextUrl != null,
                        onBack = {
                            if (appState.state.userPageFromSheet) {
                                viewModel.collapseUserPageToSheet()
                            } else {
                                viewModel.hideUserPage()
                                onPopRoute()
                            }
                        },
                        onOpenIllust = { illust ->
                            viewModel.openIllust(illust)
                        },
                        onBookmark = viewModel::toggleBookmark,
                        onLoadMore = viewModel::loadMoreUserIllusts,
                        onLoadBookmarks = viewModel::loadSelectedUserBookmarks,
                        onLoadMoreBookmarks = viewModel::loadMoreSelectedUserBookmarks,
                        onOpenRelatedUsers = {
                            onNavigate(AppRoute.RelatedUsers(user.id, user.name))
                        },
                        onToggleFollow = { viewModel.toggleFollow(user) },
                        onMuteUser = { viewModel.muteUser(user.id) },
                        onMessage = viewModel::showMessage,
                        isMuted =
                            appState.state.settings.mutedUsers
                                .contains(user.id),
                        onUnmuteUser = { viewModel.unmuteUser(user.id) },
                        gridState = viewModel.userProfileGridState(user.id),
                        onIllustLongClick = viewModel::onIllustLongPress,
                        showHeaderControls = true,
                    )
                } else {
                    LaunchedEffect(route.userId) {
                        viewModel.openUserPage(route.userId)
                    }
                    UserProfileSkeletonScreen(
                        onBack = {
                            if (appState.state.userPageFromSheet) {
                                viewModel.collapseUserPageToSheet()
                            } else {
                                viewModel.hideUserPage()
                                onPopRoute()
                            }
                        },
                    )
                }
            }
            entry<AppRoute.RelatedUsers> { route ->
                RelatedUsersScreen(
                    userId = route.userId,
                    userName = route.userName,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onOpenUser = { userId ->
                        onNavigate(AppRoute.UserProfile(userId))
                    },
                )
            }
            entry(AppRoute.AppLockSetup) {
                AppLockSetupScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry(AppRoute.AppLockPinEntry) {
                PinSetupScreen(
                    isChange = appState.state.settings.appLockEnabled,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                )
            }
            entry(AppRoute.PrivacyModeSettings) {
                PrivacyModeSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
            }
            entry<AppRoute.DeviceViewHistory> { route ->
                com.yunfie.illustia.ui.screens.pallasync.DeviceViewHistoryScreen(
                    deviceId = route.deviceId,
                    deviceName = route.deviceName,
                    state = appState.state,
                    viewModel = viewModel,
                    onBack = onPopRoute,
                    onIllustClick = { illust ->
                        viewModel.openIllust(illust)
                        onNavigate(AppRoute.Detail(illust.id))
                    },
                )
            }
        }

    val entries =
        rememberDecoratedNavEntries(
            backStack = backStack,
            entryProvider = entryProvider,
        )
    NavDisplay(
        entries = entries,
        onBack = onPopRoute,
        modifier =
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface),
    )

    AppOverlayHost(
        appState = appState,
        viewModel = viewModel,
        showTokenLogin = showTokenLogin,
        onDismissTokenLogin = { onShowTokenLoginChange(false) },
        selectedCommentTarget = selectedCommentTarget,
        onDismissComments = { onSelectedCommentTargetChange(null) },
        onSearchTag = onSearchTag,
        onNavigate = onNavigate,
    )
}

private fun artworkPageTransitionMetadata(smoothTransitions: Boolean = true): Map<String, Any> {
    val duration = if (smoothTransitions) 320 else 0
    val popDuration = if (smoothTransitions) 280 else 0
    return transitionSpec {
        ContentTransform(
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(duration),
            ),
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(duration),
            ),
        )
    } +
        popTransitionSpec {
            ContentTransform(
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(popDuration),
                ),
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(popDuration),
                ),
            )
        } +
        predictivePopTransitionSpec {
            ContentTransform(
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(popDuration),
                ),
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(popDuration),
                ),
            )
        }
}
