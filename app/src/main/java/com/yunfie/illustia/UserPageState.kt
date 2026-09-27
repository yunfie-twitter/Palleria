package com.yunfie.illustia

internal fun IllustiaUiState.clearClosedUserPage(): IllustiaUiState =
    // RelatedUsers is a separate destination and may be revealed by closing this profile.
    // Keep its cached results, pagination and in-flight loading state together.
    copy(
        selectedUserId = null,
        selectedUser = null,
        selectedUserIllusts = emptyList(),
        selectedUserNextUrl = null,
        selectedUserBookmarks = emptyList(),
        selectedUserBookmarksNextUrl = null,
        selectedUserIllustsLoaded = false,
        selectedUserBookmarksLoaded = false,
        isSelectedUserIllustsPaginating = false,
        isSelectedUserBookmarksPaginating = false,
        userPageDismissed = false,
    )
