package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.screens.profile.LocalProfileEditIcon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.yunfie.illustia.ui.screens.UserProfileScreen as SharedUserProfileScreen

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
    worksError: String? = null,
    bookmarksError: String? = null,
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
    val editIcon = painterResource(R.drawable.ic_edit_pen)
    CompositionLocalProvider(LocalProfileEditIcon provides editIcon) {
        SharedUserProfileScreen(
            user = user,
            settings = settings,
            illusts = illusts,
            bookmarks = bookmarks,
            hasMore = hasMore,
            bookmarkHasMore = bookmarkHasMore,
            nextUrl = nextUrl,
            bookmarkNextUrl = bookmarkNextUrl,
            isPaginating = isPaginating,
            isBookmarkPaginating = isBookmarkPaginating,
            worksLoaded = worksLoaded,
            bookmarksLoaded = bookmarksLoaded,
            worksError = worksError,
            bookmarksError = bookmarksError,
            onBack = onBack,
            onOpenIllust = onOpenIllust,
            onBookmark = onBookmark,
            onLoadMore = onLoadMore,
            onLoadBookmarks = onLoadBookmarks,
            onLoadMoreBookmarks = onLoadMoreBookmarks,
            onOpenRelatedUsers = onOpenRelatedUsers,
            onEditProfile = onEditProfile,
            onToggleFollow = onToggleFollow,
            onMuteUser = onMuteUser,
            onReport = onReport,
            onMessage = onMessage,
            isMuted = isMuted,
            onUnmuteUser = onUnmuteUser,
            gridState = gridState,
            bookmarkGridState = bookmarkGridState,
            onIllustLongClick = onIllustLongClick,
            onColumnsChange = onColumnsChange,
            showHeaderControls = showHeaderControls,
            modifier = modifier,
            backgroundColor = backgroundColor,
            contentHeight = contentHeight,
            platform = androidUserProfilePlatform(),
        )
    }
}
