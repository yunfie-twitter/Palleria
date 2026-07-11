package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.screens.profile.UserProfilePagerContent
import com.yunfie.illustia.ui.screens.profile.UserProfileSmallTopAppBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.foundation.layout.WindowInsets
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UserProfileScreen(
    user: UserProfile,
    settings: AppSettings,
    illusts: List<Illust>,
    bookmarks: List<Illust>,
    hasMore: Boolean,
    bookmarkHasMore: Boolean,
    onBack: () -> Unit,
    onOpenIllust: (Illust) -> Unit,
    onBookmark: (Illust) -> Unit,
    onLoadMore: () -> Unit,
    onLoadBookmarks: () -> Unit,
    onLoadMoreBookmarks: () -> Unit,
    onToggleFollow: () -> Unit,
    onMuteUser: () -> Unit,
    onMessage: (String) -> Unit,
    isMuted: Boolean,
    onUnmuteUser: () -> Unit,
    gridState: LazyGridState,
    showHeaderControls: Boolean = true,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MiuixTheme.colorScheme.background,
    contentHeight: Dp? = null,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    var showUnfollowConfirm by remember(user.id) { mutableStateOf(false) }
    var followAnimationTrigger by remember(user.id) { mutableIntStateOf(0) }
    val bookmarkGridState = remember(user.id) { LazyGridState() }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val selectedTab = pagerState.currentPage
    val isContentScrolled by remember(selectedTab, gridState, bookmarkGridState) {
        derivedStateOf {
            val state = if (selectedTab == 1) bookmarkGridState else gridState
            selectedTab != 2 && (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 24)
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

    val toggleFollow = {
        if (user.isFollowed) {
            showUnfollowConfirm = true
        } else {
            followAnimationTrigger += 1
            onToggleFollow()
        }
    }
    val selectTab: (Int) -> Unit = { index ->
        coroutineScope.launch { pagerState.animateScrollToPage(index) }
    }
    val contentModifier = modifier
        .then(if (contentHeight != null) Modifier.height(contentHeight) else Modifier.fillMaxSize())
        .background(backgroundColor)

    val content: @Composable (Modifier) -> Unit = { pageModifier ->
        UserProfilePagerContent(
            user = user,
            settings = settings,
            illusts = illusts,
            bookmarks = bookmarks,
            hasMore = hasMore,
            bookmarkHasMore = bookmarkHasMore,
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
            modifier = pageModifier,
            onTabSelected = selectTab,
            showProfileHeader = !isContentScrolled,
        )
    }

    if (showHeaderControls) {
        Scaffold(
            modifier = contentModifier,
            containerColor = backgroundColor,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                UserProfileSmallTopAppBar(user, onBack, onMuteUser, onMessage)
            },
        ) {
            content(Modifier.fillMaxSize())
        }
    } else {
        content(contentModifier)
    }
}
