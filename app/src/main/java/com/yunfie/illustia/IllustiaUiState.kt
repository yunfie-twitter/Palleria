package com.yunfie.illustia

import androidx.compose.runtime.Immutable
import com.yunfie.illustia.data.HomeFeedKind
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.data.LoadState
import com.yunfie.illustia.data.UserPreview
import com.yunfie.illustia.data.UserProfile
import com.yunfie.illustia.settings.AppSettings

@Immutable
data class IllustiaUiState(
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val mutedIllustsSet: Set<Long> = emptySet(),
    val mutedUsersSet: Set<Long> = emptySet(),
    val mutedTagsSet: Set<String> = emptySet(),
    val sessionReady: Boolean = false,
    val currentAccount: UserProfile? = null,
    val homeKind: HomeFeedKind = HomeFeedKind.Recommended,
    val homeItems: List<Illust> = emptyList(),
    val homeNextUrl: String? = null,
    val searchDraft: String = "",
    val activeSearchWord: String = "",
    val searchItems: List<Illust> = emptyList(),
    val searchNextUrl: String? = null,
    val userSearchItems: List<UserPreview> = emptyList(),
    val userSearchNextUrl: String? = null,
    val timelineItems: List<Illust> = emptyList(),
    val timelineNextUrl: String? = null,
    val watchlistItems: List<Illust> = emptyList(),
    val watchlistNextUrl: String? = null,
    val activeWatchlistTag: String? = null,
    val followingUsers: List<UserPreview> = emptyList(),
    val followingUsersNextUrl: String? = null,
    val rankingItems: List<Illust> = emptyList(),
    val rankingNextUrl: String? = null,
    val rankingMode: String = "day",
    val bookmarkItems: List<Illust> = emptyList(),
    val bookmarkNextUrl: String? = null,
    val selectedIllust: Illust? = null,
    val selectedIllustUser: UserProfile? = null,
    val relatedIllusts: List<Illust> = emptyList(),
    val selectedUser: UserProfile? = null,
    val selectedUserIllusts: List<Illust> = emptyList(),
    val selectedUserNextUrl: String? = null,
    val selectedUserBookmarks: List<Illust> = emptyList(),
    val selectedUserBookmarksNextUrl: String? = null,
    val imageViewerIllust: Illust? = null,
    val imageViewerStartPage: Int = 0,
    val longPressedIllust: Illust? = null,
    val webLoginRequest: PixivWebLoginRequest? = null,
    val showReloginRequiredDialog: Boolean = false,
    val pendingBookmarkRemoval: Illust? = null,
    val activeDownloads: Int = 0,
    val showAccountSwitcher: Boolean = false,
    val loadState: LoadState = LoadState.Idle,
    val message: String? = null,
)

internal data class MuteFilter(
    val illustIds: Set<Long> = emptySet(),
    val userIds: Set<Long> = emptySet(),
    val tags: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = illustIds.isEmpty() && userIds.isEmpty() && tags.isEmpty()
}

internal fun AppSettings.toMuteFilter(): MuteFilter {
    return MuteFilter(
        illustIds = mutedIllusts.toSet(),
        userIds = mutedUsers.toSet(),
        tags = mutedTags.toSet(),
    )
}
