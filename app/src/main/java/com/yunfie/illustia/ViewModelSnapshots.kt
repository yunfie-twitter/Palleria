package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.Comment

internal data class DetailSnapshot(
    val illust: Illust,
    val user: UserProfile?,
    val firstComment: Comment?,
    val relatedIllusts: List<Illust>,
)

internal data class SearchSnapshot(
    val searchDraft: String,
    val activeSearchWord: String,
    val searchItems: List<Illust>,
    val searchNextUrl: String?,
    val searchNovelItems: List<NovelPreview>,
    val searchNovelNextUrl: String?,
    val userSearchItems: List<UserPreview>,
    val userSearchNextUrl: String?,
    val searchSelectedTab: Int = 0,
)

internal data class UserPageSnapshot(
    val selectedUserId: Long?,
    val selectedUser: UserProfile?,
    val selectedUserIllusts: List<Illust>,
    val selectedUserNextUrl: String?,
    val selectedUserBookmarks: List<Illust>,
    val selectedUserBookmarksNextUrl: String?,
    val selectedRelatedUsers: List<UserPreview>,
    val selectedRelatedUsersNextUrl: String?,
    val selectedRelatedUsersUserId: Long?,
    val selectedRelatedUsersLoading: Boolean,
    val showUserPage: Boolean,
    val userPageDismissed: Boolean,
)

internal fun IllustiaUiState.toSearchSnapshot(): SearchSnapshot =
    SearchSnapshot(
        searchDraft = searchDraft,
        activeSearchWord = activeSearchWord,
        searchItems = searchItems,
        searchNextUrl = searchNextUrl,
        searchNovelItems = searchNovelItems,
        searchNovelNextUrl = searchNovelNextUrl,
        userSearchItems = userSearchItems,
        userSearchNextUrl = userSearchNextUrl,
        searchSelectedTab = searchSelectedTab,
    )

internal fun IllustiaUiState.toUserPageSnapshot(): UserPageSnapshot =
    UserPageSnapshot(
        selectedUserId = selectedUserId,
        selectedUser = selectedUser,
        selectedUserIllusts = selectedUserIllusts,
        selectedUserNextUrl = selectedUserNextUrl,
        selectedUserBookmarks = selectedUserBookmarks,
        selectedUserBookmarksNextUrl = selectedUserBookmarksNextUrl,
        selectedRelatedUsers = selectedRelatedUsers,
        selectedRelatedUsersNextUrl = selectedRelatedUsersNextUrl,
        selectedRelatedUsersUserId = selectedRelatedUsersUserId,
        selectedRelatedUsersLoading = selectedRelatedUsersLoading,
        showUserPage = showUserPage,
        userPageDismissed = userPageDismissed,
    )

internal fun IllustiaUiState.restore(snapshot: UserPageSnapshot): IllustiaUiState =
    copy(
        selectedUserId = snapshot.selectedUserId,
        selectedUser = snapshot.selectedUser,
        selectedUserIllusts = snapshot.selectedUserIllusts,
        selectedUserNextUrl = snapshot.selectedUserNextUrl,
        selectedUserBookmarks = snapshot.selectedUserBookmarks,
        selectedUserBookmarksNextUrl = snapshot.selectedUserBookmarksNextUrl,
        selectedRelatedUsers = snapshot.selectedRelatedUsers,
        selectedRelatedUsersNextUrl = snapshot.selectedRelatedUsersNextUrl,
        selectedRelatedUsersUserId = snapshot.selectedRelatedUsersUserId,
        selectedRelatedUsersLoading = snapshot.selectedRelatedUsersLoading,
        showUserPage = snapshot.showUserPage,
        userPageDismissed = snapshot.userPageDismissed,
    )
