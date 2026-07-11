package com.yunfie.illustia.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
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
import com.yunfie.illustia.ui.screens.DataSettingsScreen
import com.yunfie.illustia.ui.screens.DownloadQueueScreen
import com.yunfie.illustia.ui.screens.FavoriteTagsScreen
import com.yunfie.illustia.ui.screens.GeneralSettingsScreen
import com.yunfie.illustia.ui.screens.IllustDetailScreen
import com.yunfie.illustia.ui.screens.IllustSeriesScreen
import com.yunfie.illustia.ui.screens.ImageSettingsScreen
import com.yunfie.illustia.ui.screens.ImageViewerScreen
import com.yunfie.illustia.ui.screens.MuteSettingsScreen
import com.yunfie.illustia.ui.screens.NovelReaderScreen
import com.yunfie.illustia.ui.screens.NovelScreen
import com.yunfie.illustia.ui.screens.OfflineLibraryScreen
import com.yunfie.illustia.ui.screens.OnboardingScreen
import com.yunfie.illustia.ui.screens.PinSetupScreen
import com.yunfie.illustia.ui.screens.PrivacyModeSettingsScreen
import com.yunfie.illustia.ui.screens.SavedIllustViewerScreen
import com.yunfie.illustia.ui.screens.SearchScreen
import com.yunfie.illustia.ui.screens.SettingsScreen
import com.yunfie.illustia.ui.screens.UserProfileScreen
import com.yunfie.illustia.ui.screens.ViewHistoryScreen
import com.yunfie.illustia.ui.screens.WatchlistSeriesScreen
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AppNavHost(
    appState: IllustiaAppStateBundle,
    viewModel: IllustiaViewModel,
    backStack: MutableList<NavKey>,
    selectedTab: AppTab,
    pagerState: androidx.compose.foundation.pager.PagerState,
    showTokenLogin: Boolean,
    onShowTokenLoginChange: (Boolean) -> Unit,
    selectedWatchlistSeriesId: Long?,
    onSelectedWatchlistSeriesIdChange: (Long?) -> Unit,
    selectedCommentTarget: Pair<Long, CommentArtworkType>?,
    onSelectedCommentTargetChange: (Pair<Long, CommentArtworkType>?) -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onPopRoute: () -> Unit,
    onTabSelected: (Int, AppTab) -> Unit,
) {
    val entryProvider = entryProvider<NavKey> {
        entry(AppRoute.Main) {
            MainSurface(
                appState = appState,
                viewModel = viewModel,
                selectedTab = selectedTab,
                pagerState = pagerState,
                onTabSelected = onTabSelected,
                onSearch = {
                    if (appState.settings.shortsFeedEnabled) {
                        onNavigate(AppRoute.Search)
                    } else {
                        onTabSelected(mainTabs(false).indexOf(AppTab.Search), AppTab.Search)
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
            )
        }
        entry(AppRoute.Search) {
            SearchScreen(state = appState.state, viewModel = viewModel)
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
        entry(
            AppRoute.Detail,
            metadata = artworkPageTransitionMetadata(),
        ) {
            appState.state.selectedIllust?.let { illust ->
                IllustDetailScreen(
                    illust = illust,
                    relatedIllusts = appState.state.relatedIllusts,
                    firstComment = appState.state.selectedIllustFirstComment,
                    onBack = onPopRoute,
                    onBookmark = { viewModel.toggleBookmark(illust) },
                    onOpenUser = viewModel::openUser,
                    onOpenComments = {
                        onSelectedCommentTargetChange(illust.id to CommentArtworkType.ILLUST)
                    },
                    onOpenSeries = illust.series?.id?.let { seriesId ->
                        {
                            onSelectedWatchlistSeriesIdChange(seriesId)
                            onNavigate(AppRoute.IllustSeries)
                        }
                    },
                    onOpenImage = { page -> viewModel.openImageViewer(illust, page) },
                    onSearchTag = { tag ->
                        onPopRoute()
                        viewModel.submitSearch(tag)
                    },
                    isArtistFollowed = appState.state.selectedIllustUser?.isFollowed == true,
                    isArtistMuted = appState.state.settings.mutedUsers.contains(illust.artistId),
                    isTagMuted = illust.isMutedByTags(appState.state.settings),
                    onToggleFollow = {
                        appState.state.selectedIllustUser?.let { viewModel.toggleFollow(it) }
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
                    detailQuality = if (illust.type == "manga") {
                        appState.state.settings.mangaDetailQuality
                    } else {
                        appState.state.settings.illustDetailQuality
                    },
                    prefetchImages = appState.state.settings.prefetchImages,
                    confirmOnLongPressSave = appState.state.settings.confirmOnLongPressSave,
                    skipConfirmOnDetailSave = appState.state.settings.skipConfirmOnDetailSave,
                )
            }
        }
        entry(
            AppRoute.ImageViewer,
            metadata = artworkPageTransitionMetadata(),
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
            GeneralSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
        }
        entry(AppRoute.ImageSettings) {
            ImageSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
        }
        entry(AppRoute.BookmarkSettings) {
            BookmarkSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
        }
        entry(AppRoute.AccountSettings) {
            AccountSettingsScreen(state = appState.state, viewModel = viewModel, onBack = onPopRoute)
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
            AboutScreen(onBack = onPopRoute)
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
            if (appState.state.selectedUser != null) {
                val user = appState.state.selectedUser
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
                    onToggleFollow = { viewModel.toggleFollow(user) },
                    onMuteUser = { viewModel.muteUser(user.id) },
                    onMessage = viewModel::showMessage,
                    isMuted = appState.state.settings.mutedUsers.contains(user.id),
                    onUnmuteUser = { viewModel.unmuteUser(user.id) },
                    gridState = viewModel.userProfileGridState(user.id),
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
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider,
    )
    NavDisplay(
        entries = entries,
        onBack = onPopRoute,
        modifier = Modifier
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
    )
}

private fun artworkPageTransitionMetadata(): Map<String, Any> =
    transitionSpec {
        ContentTransform(
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(320),
            ),
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(320),
            ),
        )
    } +
        popTransitionSpec {
            ContentTransform(
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(280),
                ),
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(280),
                ),
            )
        } +
        predictivePopTransitionSpec {
            ContentTransform(
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(280),
                ),
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(280),
                ),
            )
        }
