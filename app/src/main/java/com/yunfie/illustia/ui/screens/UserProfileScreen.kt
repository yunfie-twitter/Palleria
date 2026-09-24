package com.yunfie.illustia.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.screens.profile.UserProfilePagerContent
import com.yunfie.illustia.ui.screens.profile.UserProfileSmallTopAppBar
import com.yunfie.illustia.ui.screens.profile.UserWorkSortOrder
import com.yunfie.illustia.ui.screens.profile.UserWorkTypeFilter
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UserProfileScreen(
    user: UserProfile,
    settings: AppSettings,
    illusts: List<Illust>,
    bookmarks: List<Illust>,
    hasMore: Boolean,
    bookmarkHasMore: Boolean,
    nextUrl: String? = null,
    bookmarkNextUrl: String? = null,
    isPaginating: Boolean = false,
    isBookmarkPaginating: Boolean = false,
    onBack: () -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onBookmark: (Illust) -> Unit,
    onLoadMore: () -> Unit,
    onLoadBookmarks: () -> Unit,
    onLoadMoreBookmarks: () -> Unit,
    onOpenRelatedUsers: (() -> Unit)? = null,
    onToggleFollow: () -> Unit,
    onMuteUser: () -> Unit,
    onMessage: (String) -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    gridState: LazyGridState,
    onIllustLongClick: (Illust) -> Unit = {},
    showHeaderControls: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.background,
    contentHeight: Dp? = null,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val activity = context as? Activity
    val isDarkTheme = backgroundColor.luminance() < 0.5f

    var showUnfollowConfirm by remember(user.id) { mutableStateOf(false) }
    var followAnimationTrigger by remember(user.id) { mutableIntStateOf(0) }
    var sortOrder by remember(user.id) { mutableStateOf(UserWorkSortOrder.Newest) }
    var typeFilter by remember(user.id) { mutableStateOf(UserWorkTypeFilter.All) }

    val processedIllusts =
        remember(illusts, sortOrder, typeFilter) {
            val filtered =
                when (typeFilter) {
                    UserWorkTypeFilter.All -> illusts
                    UserWorkTypeFilter.IllustOnly -> illusts.filter { it.type != "manga" }
                    UserWorkTypeFilter.MangaOnly -> illusts.filter { it.type == "manga" }
                }
            when (sortOrder) {
                UserWorkSortOrder.Newest -> filtered.sortedByDescending { it.id }
                UserWorkSortOrder.Oldest -> filtered.sortedBy { it.id }
                UserWorkSortOrder.MostBookmarks -> filtered.sortedByDescending { it.totalBookmarks }
            }
        }

    val processedBookmarks =
        remember(bookmarks, sortOrder, typeFilter) {
            val filtered =
                when (typeFilter) {
                    UserWorkTypeFilter.All -> bookmarks
                    UserWorkTypeFilter.IllustOnly -> bookmarks.filter { it.type != "manga" }
                    UserWorkTypeFilter.MangaOnly -> bookmarks.filter { it.type == "manga" }
                }
            when (sortOrder) {
                UserWorkSortOrder.Newest -> filtered.sortedByDescending { it.id }
                UserWorkSortOrder.Oldest -> filtered.sortedBy { it.id }
                UserWorkSortOrder.MostBookmarks -> filtered.sortedByDescending { it.totalBookmarks }
            }
        }

    val bookmarkGridState = remember(user.id) { LazyGridState() }
    val infoListState = remember(user.id) { LazyListState() }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage
    var isHeaderCollapsed by remember(user.id) { mutableStateOf(false) }

    val activeIsAtTop by remember(selectedTab, gridState, bookmarkGridState, infoListState) {
        derivedStateOf {
            when (selectedTab) {
                0 -> gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset <= 0
                1 -> bookmarkGridState.firstVisibleItemIndex == 0 && bookmarkGridState.firstVisibleItemScrollOffset <= 0
                else -> infoListState.firstVisibleItemIndex == 0 && infoListState.firstVisibleItemScrollOffset <= 0
            }
        }
    }

    // Coordinate collapsing/expanding across all tabs and short content lists without flapping
    val profileScrollConnection =
        remember(activeIsAtTop) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y < -10f) {
                        isHeaderCollapsed = true
                    } else if (available.y > 8f && activeIsAtTop) {
                        isHeaderCollapsed = false
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y > 8f && activeIsAtTop) {
                        isHeaderCollapsed = false
                    }
                    return Offset.Zero
                }
            }
        }

    val isContentScrolled by remember(isHeaderCollapsed, activeIsAtTop) {
        derivedStateOf {
            isHeaderCollapsed || !activeIsAtTop
        }
    }

    DisposableEffect(isContentScrolled, isDarkTheme) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars =
                if (isContentScrolled) {
                    !isDarkTheme
                } else {
                    false
                }
        }
        onDispose {
            val window = activity?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !isDarkTheme
            }
        }
    }

    LaunchedEffect(selectedTab, user.id, isMuted) {
        if (!isMuted && selectedTab == 1) onLoadBookmarks()
    }

    if (showUnfollowConfirm) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.detail_unfollow_title),
            summary = stringResource(R.string.detail_unfollow_confirm, user.name.ifBlank { "@${user.account}" }),
            confirmText = stringResource(R.string.action_unfollow),
            destructive = true,
            onConfirm = {
                showUnfollowConfirm = false
                onToggleFollow()
            },
            onDismiss = { showUnfollowConfirm = false },
        )
    }

    val performHaptic =
        com.yunfie.illustia.ui.components
            .rememberHapticFeedbackAction()
    val toggleFollow = {
        if (user.isFollowed) {
            showUnfollowConfirm = true
        } else {
            followAnimationTrigger += 1
            onToggleFollow()
        }
    }
    val selectTab: (Int) -> Unit = { index ->
        if (index != selectedTab) {
            performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Toggle)
        }
        coroutineScope.launch { pagerState.animateScrollToPage(index) }
    }
    val scrollToTop: () -> Unit = {
        isHeaderCollapsed = false
        coroutineScope.launch {
            when (selectedTab) {
                0 -> gridState.animateScrollToItem(0)
                1 -> bookmarkGridState.animateScrollToItem(0)
                else -> infoListState.animateScrollToItem(0)
            }
        }
    }
    val contentModifier =
        modifier
            .then(if (contentHeight != null) Modifier.height(contentHeight) else Modifier.fillMaxSize())
            .nestedScroll(profileScrollConnection)
            .background(backgroundColor)

    val content: @Composable (Modifier) -> Unit = { pageModifier ->
        UserProfilePagerContent(
            user = user,
            settings = settings,
            illusts = processedIllusts,
            bookmarks = processedBookmarks,
            hasMore = hasMore,
            bookmarkHasMore = bookmarkHasMore,
            nextUrl = nextUrl,
            bookmarkNextUrl = bookmarkNextUrl,
            isPaginating = isPaginating,
            isBookmarkPaginating = isBookmarkPaginating,
            onOpenIllust = onOpenIllust,
            onBookmark = onBookmark,
            onLoadMore = onLoadMore,
            onLoadMoreBookmarks = onLoadMoreBookmarks,
            onToggleFollow = toggleFollow,
            isMuted = isMuted,
            onUnmuteUser = onUnmuteUser,
            followAnimationTrigger = followAnimationTrigger,
            backgroundColor = backgroundColor,
            pagerState = pagerState,
            worksGridState = gridState,
            bookmarksGridState = bookmarkGridState,
            infoListState = infoListState,
            modifier = pageModifier,
            onTabSelected = selectTab,
            showProfileHeader = !isContentScrolled,
            onIllustLongClick = onIllustLongClick,
            onCollapseHeader = { isHeaderCollapsed = true },
        )
    }

    if (showHeaderControls) {
        Box(modifier = contentModifier) {
            content(Modifier.fillMaxSize())
            UserProfileSmallTopAppBar(
                user = user,
                sortOrder = sortOrder,
                typeFilter = typeFilter,
                onSortOrderChange = { sortOrder = it },
                onTypeFilterChange = { typeFilter = it },
                onBack = onBack,
                onMuteUser = onMuteUser,
                onMessage = onMessage,
                onOpenRelatedUsers = onOpenRelatedUsers ?: {},
                onTitleClick = scrollToTop,
                compact = isContentScrolled,
            )
        }
    } else {
        content(contentModifier)
    }
}
