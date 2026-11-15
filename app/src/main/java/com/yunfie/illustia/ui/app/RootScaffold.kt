package com.yunfie.illustia.ui.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.data.pixiv.CommentArtworkType
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * アプリシェル（ルート Scaffold + レール + AppNavHost）を [IllustiaAppRoot] から切り出したもの。
 *
 * 以前は `BoxWithConstraints` の中にレール表示判定・タブ選択・画面ホスト呼び出しが
 * 入り混じっていた。レールは [RootNavigationRail]、中身は [AppNavHost] として分離している。
 */
@Composable
internal fun RootScaffold(
    appState: IllustiaAppStateBundle,
    viewModel: IllustiaViewModel,
    backStack: MutableList<NavKey>,
    detailSnapshots: Map<Long, DetailEntrySnapshot>,
    searchSnapshots: Map<String, SearchEntrySnapshot>,
    tabs: List<AppTab>,
    visibleTabs: List<AppTab>,
    selectedTab: AppTab,
    pagerState: PagerState,
    homeScrollBehavior: ScrollBehavior,
    artworkPopSettling: Boolean,
    artworkEnterSettling: Boolean,
    showTokenLogin: Boolean,
    onShowTokenLoginChange: (Boolean) -> Unit,
    selectedWatchlistSeriesId: Long?,
    onSelectedWatchlistSeriesIdChange: (Long?) -> Unit,
    selectedCommentTarget: Pair<Long, CommentArtworkType>?,
    onSelectedCommentTargetChange: (Pair<Long, CommentArtworkType>?) -> Unit,
    searchFocusRequest: Int,
    onSearchFocusHandled: () -> Unit,
    viewerRefreshRequest: Int,
    snackbarHostState: SnackbarHostState,
    isDesktopDevice: Boolean,
    onNavigate: (AppRoute) -> Unit,
    onPopRoute: () -> Unit,
    onSearchTag: (String) -> Unit,
    onTabSelected: (Int, AppTab) -> Unit,
    onNavigateRailTab: (AppTab, Int) -> Unit,
) {
    // onNavigateRailTab receives the tab and its page index; the caller owns the
    // "already on this tab at this page" scroll-to-top decision.
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
    ) { rootPadding ->
        val rootRailState = rememberNavigationRailState()
        val currentRoute = backStack.lastOrNull()
        // 小説リーダーは本文に集中する画面なので、レールも表示しない。
        val isFullscreenRoute = currentRoute == AppRoute.ImageViewer || currentRoute == AppRoute.NovelReader
        val isPreLogin =
            appState.state.settings.refreshToken
                .isBlank() || currentRoute == AppRoute.Onboarding
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(rootPadding),
        ) {
            val isLandscape = (maxWidth > maxHeight && maxWidth >= 480.dp) || maxWidth >= 600.dp
            val showRootNavigationRail =
                ((isDesktopDevice && maxWidth >= 480.dp) || isLandscape) &&
                    !isFullscreenRoute &&
                    !isPreLogin

            CompositionLocalProvider(LocalUseNavigationRail provides showRootNavigationRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (showRootNavigationRail) {
                        RootNavigationRail(
                            state = rootRailState,
                            tabs = tabs,
                            navigationTabs = visibleTabs,
                            selectedTab = selectedTab,
                            isOnMain = currentRoute == AppRoute.Main,
                            onTabClick = onNavigateRailTab,
                        )
                    }

                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = MiuixTheme.colorScheme.surface,
                    ) {
                        AppNavHost(
                            searchFocusRequest = searchFocusRequest,
                            onSearchFocusHandled = onSearchFocusHandled,
                            viewerRefreshRequest = viewerRefreshRequest,
                            appState = appState,
                            viewModel = viewModel,
                            backStack = backStack,
                            detailSnapshots = detailSnapshots,
                            searchSnapshots = searchSnapshots,
                            selectedTab = selectedTab,
                            pagerState = pagerState,
                            homeScrollBehavior = homeScrollBehavior,
                            artworkPopSettling = artworkPopSettling,
                            artworkEnterSettling = artworkEnterSettling,
                            showTokenLogin = showTokenLogin,
                            onShowTokenLoginChange = onShowTokenLoginChange,
                            selectedWatchlistSeriesId = selectedWatchlistSeriesId,
                            onSelectedWatchlistSeriesIdChange = onSelectedWatchlistSeriesIdChange,
                            selectedCommentTarget = selectedCommentTarget,
                            onSelectedCommentTargetChange = onSelectedCommentTargetChange,
                            onNavigate = onNavigate,
                            onPopRoute = onPopRoute,
                            onSearchTag = onSearchTag,
                            onTabSelected = onTabSelected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RootNavigationRail(
    state: top.yukonga.miuix.kmp.basic.NavigationRailState,
    tabs: List<AppTab>,
    navigationTabs: List<AppTab>,
    selectedTab: AppTab,
    isOnMain: Boolean,
    onTabClick: (AppTab, Int) -> Unit,
) {
    NavigationRail(
        state = state,
        color = MiuixTheme.colorScheme.surfaceContainer,
        showDivider = true,
    ) {
        navigationTabs.forEach { tab ->
            val pageIndex = tabs.indexOf(tab)
            val isSelected = isOnMain && selectedTab == tab
            NavigationRailItem(
                modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
                selected = isSelected,
                onClick = { onTabClick(tab, pageIndex) },
                icon = tab.icon,
                label = stringResource(tab.labelResId),
            )
        }
    }
}
