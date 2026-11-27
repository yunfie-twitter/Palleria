package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.yunfie.illustia.BookmarkChromeState
import com.yunfie.illustia.BookmarkSort
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.uiRepository
import com.yunfie.illustia.ui.screens.BookmarkScreen as SharedBookmarkScreen

@Composable
fun BookmarkScreen(
    settings: AppSettings,
    loadState: LoadState,
    timelineLoadState: LoadState = loadState,
    followingUsersLoadState: LoadState = loadState,
    bookmarkItems: List<Illust>,
    bookmarkTag: String?,
    timelineItems: List<Illust>,
    followingUsers: List<UserPreview>,
    chrome: BookmarkChromeState,
    viewModel: IllustiaViewModel,
    onOpenWatchlistSeries: (Long) -> Unit,
    onOpenBookmarkTagSort: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context) { context.uiRepository() }
    val actions =
        remember(viewModel) {
            object : BookmarkActions {
                override val watchlistRefreshRequests = viewModel.watchlistRefreshRequests

                override fun reloadBookmarks(forceRefresh: Boolean) = viewModel.refreshBookmarks(forceRefresh)

                override fun loadMoreBookmarks() = viewModel.loadMoreBookmarks()

                override fun refreshTimeline(forceRefresh: Boolean) = viewModel.refreshTimeline(forceRefresh)

                override fun loadMoreTimeline() = viewModel.loadMoreTimeline()

                override fun refreshFollowingUsers(forceRefresh: Boolean) = viewModel.refreshFollowingUsers(forceRefresh)

                override fun loadMoreFollowingUsers() = viewModel.loadMoreFollowingUsers()

                override fun toggleBookmarkById(illustId: Long) = viewModel.toggleBookmarkById(illustId)

                override fun openBookmarkTagPickerFor(illust: Illust) = viewModel.openBookmarkTagPickerFor(illust)

                override fun openIllustById(illustId: Long) = viewModel.openIllustById(illustId)

                override fun onIllustLongPressById(illustId: Long) = viewModel.onIllustLongPressById(illustId)

                override fun onFollowingUserLongPress(user: UserPreview) = viewModel.onFollowingUserLongPress(user)

                override fun openUserPageFor(user: UserPreview) = viewModel.openUserPageFor(user)

                override fun updateColumnsFromGrid(columns: Int) = viewModel.updateColumnsFromGrid(columns)

                override fun updateVerticalColumnCount(count: Int) = viewModel.updateVerticalColumnCount(count)

                override fun refreshBookmarks(forceRefresh: Boolean) = viewModel.refreshBookmarks(forceRefresh)

                override fun updateBookmarkSelectedTab(index: Int) = viewModel.updateBookmarkSelectedTab(index)

                override fun updateRestrict(restrict: Restrict) = viewModel.updateRestrict(restrict)

                override fun updateFollowRestrict(restrict: Restrict) = viewModel.updateFollowRestrict(restrict)

                override fun updateBookmarkSort(sort: BookmarkSort) = viewModel.updateBookmarkSort(sort)
            }
        }
    SharedBookmarkScreen(
        settings = settings,
        loadState = loadState,
        timelineLoadState = timelineLoadState,
        followingUsersLoadState = followingUsersLoadState,
        bookmarkItems = bookmarkItems,
        bookmarkTag = bookmarkTag,
        timelineItems = timelineItems,
        followingUsers = followingUsers,
        chrome = chrome,
        repository = repository,
        actions = actions,
        onOpenWatchlistSeries = onOpenWatchlistSeries,
        onOpenBookmarkTagSort = onOpenBookmarkTagSort,
    )
}
