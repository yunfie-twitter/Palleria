package com.yunfie.illustia.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.screens.profile.UserProfileAvatarPreviewOverlay
import com.yunfie.illustia.ui.screens.profile.UserProfilePagerContent
import com.yunfie.illustia.ui.screens.profile.UserProfileSmallTopAppBar
import com.yunfie.illustia.ui.screens.profile.UserWorkSortOrder
import com.yunfie.illustia.ui.screens.profile.UserWorkTypeFilter
import com.yunfie.illustia.ui.screens.profile.rememberUserWorks
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
    worksLoaded: Boolean = true,
    bookmarksLoaded: Boolean = true,
    onBack: () -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onBookmark: (Illust) -> Unit,
    onLoadMore: () -> Unit,
    onLoadBookmarks: () -> Unit,
    onLoadMoreBookmarks: () -> Unit,
    onOpenRelatedUsers: (() -> Unit)? = null,
    onEditProfile: (() -> Unit)? = null,
    onToggleFollow: () -> Unit,
    onMuteUser: () -> Unit,
    onReport: (String?, (Boolean) -> Unit) -> Unit,
    onMessage: (String) -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    gridState: LazyGridState,
    bookmarkGridState: LazyGridState = remember(user.id) { LazyGridState() },
    onIllustLongClick: (Illust) -> Unit = {},
    onColumnsChange: (Int) -> Unit = {},
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
    var sortOrder by rememberSaveable(user.id) { mutableStateOf(UserWorkSortOrder.Newest) }
    var typeFilter by rememberSaveable(user.id) { mutableStateOf(UserWorkTypeFilter.All) }
    var showAvatarPreview by rememberSaveable(user.id) { mutableStateOf(false) }

    BackHandler(enabled = showAvatarPreview) {
        showAvatarPreview = false
    }

    val infoListState = remember(user.id) { LazyListState() }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val isIllustActive by remember { derivedStateOf { pagerState.settledPage == 0 } }
    val isBookmarkActive by remember { derivedStateOf { pagerState.settledPage == 1 } }
    val processedIllusts = rememberUserWorks(user.id, illusts, sortOrder, typeFilter, isIllustActive)
    val processedBookmarks = rememberUserWorks(user.id, bookmarks, sortOrder, typeFilter, isBookmarkActive)
    var isHeaderCollapsed by rememberSaveable(user.id) { mutableStateOf(false) }

    val isOwnProfile =
        remember(user.id, settings.accounts, settings.activeAccountIndex, settings.bookmarkUserId) {
            (settings.accounts.getOrNull(settings.activeAccountIndex)?.userId ?: settings.bookmarkUserId) == user.id ||
                settings.accounts.any { it.userId == user.id }
        }
    val isProfileEditEnabled = settings.isFeatureEnabled(FeatureFlag.UserProfileEdit)

    val activeIsAtTop by remember(gridState, bookmarkGridState, infoListState) {
        derivedStateOf {
            when (pagerState.currentPage) {
                0 -> gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset <= 0
                1 -> bookmarkGridState.firstVisibleItemIndex == 0 && bookmarkGridState.firstVisibleItemScrollOffset <= 0
                else -> infoListState.firstVisibleItemIndex == 0 && infoListState.firstVisibleItemScrollOffset <= 0
            }
        }
    }

    val currentActiveIsAtTop by rememberUpdatedState(activeIsAtTop)

    // Coordinate collapsing/expanding across all tabs and short content lists without flapping
    val profileScrollConnection =
        remember {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y < -10f) {
                        isHeaderCollapsed = true
                    } else if (available.y > 8f && currentActiveIsAtTop) {
                        isHeaderCollapsed = false
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y > 8f && currentActiveIsAtTop) {
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

    DisposableEffect(isContentScrolled, isDarkTheme, showAvatarPreview) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.isAppearanceLightStatusBars =
                if (showAvatarPreview) {
                    false
                } else if (isContentScrolled) {
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

    LaunchedEffect(pagerState, user.id, isMuted) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (!isMuted && settled == 1) onLoadBookmarks()
        }
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
        if (index != pagerState.currentPage) {
            performHaptic(com.yunfie.illustia.ui.components.AppHapticEffect.Toggle)
        }
        coroutineScope.launch { pagerState.animateScrollToPage(index) }
    }
    val scrollToTop: () -> Unit = {
        isHeaderCollapsed = false
        coroutineScope.launch {
            when (pagerState.currentPage) {
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
            illusts = processedIllusts.items,
            bookmarks = processedBookmarks.items,
            hasMore = hasMore,
            bookmarkHasMore = bookmarkHasMore,
            nextUrl = nextUrl,
            bookmarkNextUrl = bookmarkNextUrl,
            isPaginating = isPaginating || processedIllusts.processing,
            worksLoaded = worksLoaded,
            bookmarksLoaded = bookmarksLoaded,
            allowAutoLoadMore = typeFilter == UserWorkTypeFilter.All && sortOrder == UserWorkSortOrder.Newest,
            isBookmarkPaginating = isBookmarkPaginating || processedBookmarks.processing,
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
            isOwnProfile = isOwnProfile,
            isProfileEditEnabled = isProfileEditEnabled,
            onEditProfile = onEditProfile,
            onColumnsChange = onColumnsChange,
            onAvatarClick = { showAvatarPreview = true },
        )
    }

    Box(modifier = contentModifier) {
        content(Modifier.fillMaxSize())
        if (showHeaderControls) {
            UserProfileSmallTopAppBar(
                user = user,
                showWorkControls = pagerState.currentPage < 2,
                sortOrder = sortOrder,
                typeFilter = typeFilter,
                onSortOrderChange = { sortOrder = it },
                onTypeFilterChange = { typeFilter = it },
                onBack = onBack,
                onMuteUser = onMuteUser,
                onReport = onReport,
                onMessage = onMessage,
                onOpenRelatedUsers = onOpenRelatedUsers ?: {},
                onTitleClick = scrollToTop,
                compact = isContentScrolled,
            )
        }
        UserProfileAvatarPreviewOverlay(
            visible = showAvatarPreview,
            user = user,
            onDismiss = { showAvatarPreview = false },
        )
    }
}
