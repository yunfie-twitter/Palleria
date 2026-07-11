package com.yunfie.illustia

import androidx.compose.runtime.Immutable
import com.yunfie.illustia.models.HomeFeedKind
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.models.NovelPreview
import com.yunfie.illustia.models.NovelTextContent
import com.yunfie.illustia.models.UserPreview
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.Comment
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.db.SavedIllustEntity

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
    val novelItems: List<NovelPreview> = emptyList(),
    val novelNextUrl: String? = null,
    val selectedNovel: NovelPreview? = null,
    val selectedNovelText: NovelTextContent? = null,
    val recommendedTags: List<String> = emptyList(),
    val recommendedTagTiles: List<RecommendedTagTile> = emptyList(),
    val recommendedTagsFetchedAtMillis: Long = 0L,
    val searchDraft: String = "",
    val activeSearchWord: String = "",
    val searchItems: List<Illust> = emptyList(),
    val searchNextUrl: String? = null,
    val userSearchItems: List<UserPreview> = emptyList(),
    val userSearchNextUrl: String? = null,
    val timelineItems: List<Illust> = emptyList(),
    val timelineNextUrl: String? = null,
    val shortsFeedItems: List<Illust> = emptyList(),
    val shortsFeedHomeNextUrl: String? = null,
    val shortsFeedFollowingNextUrl: String? = null,
    val shortsFeedCurrentIllustId: Long? = null,
    val watchlistItems: List<Illust> = emptyList(),
    val watchlistNextUrl: String? = null,
    val activeWatchlistTag: String? = null,
    val followingUsers: List<UserPreview> = emptyList(),
    val followingUsersNextUrl: String? = null,
    val bookmarkSelectedTab: Int = 1,
    val rankingItems: List<Illust> = emptyList(),
    val rankingNextUrl: String? = null,
    val rankingMode: String = "day",
    val bookmarkItems: List<Illust> = emptyList(),
    val bookmarkNextUrl: String? = null,
    val selectedIllust: Illust? = null,
    val selectedIllustUser: UserProfile? = null,
    val selectedIllustFirstComment: Comment? = null,
    val relatedIllusts: List<Illust> = emptyList(),
    val savedIllusts: List<SavedIllustEntity> = emptyList(),
    val selectedSavedIllustId: Long? = null,
    val selectedUser: UserProfile? = null,
    val selectedUserIllusts: List<Illust> = emptyList(),
    val selectedUserNextUrl: String? = null,
    val selectedUserBookmarks: List<Illust> = emptyList(),
    val selectedUserBookmarksNextUrl: String? = null,
    val showUserPage: Boolean = false,
    val userPageFromSheet: Boolean = false,
    val userPageDismissed: Boolean = false,
    val imageViewerIllust: Illust? = null,
    val imageViewerStartPage: Int = 0,
    val longPressedIllust: Illust? = null,
    val webLoginRequest: PixivWebLoginRequest? = null,
    val showReloginRequiredDialog: Boolean = false,
    val pendingBookmarkRemoval: Illust? = null,
    val activeDownloads: Int = 0,
    val downloadQueue: List<DownloadQueueEntry> = emptyList(),
    val showAccountSwitcher: Boolean = false,
    val appLocked: Boolean = false,
    val showLockRecoveryDialog: Boolean = false,
    val loadState: LoadState = LoadState.Idle,
    val message: String? = null,
    val privacyLocked: Boolean = false,
    val calculatorBuffer: String = "",
    val calculatorHistory: List<CalculatorHistoryEntry> = emptyList(),
    val isTransitioningToIllustia: Boolean = false,
)

data class CalculatorHistoryEntry(
    val expression: String,
    val result: String,
)

@Immutable
data class RecommendedTagTile(
    val tag: String,
    val imageUrl: String? = null,
)

internal data class MuteFilter(
    val illustIds: Set<Long> = emptySet(),
    val userIds: Set<Long> = emptySet(),
    val tags: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = illustIds.isEmpty() && userIds.isEmpty() && tags.isEmpty()
}

@Immutable
data class DownloadQueueEntry(
    val id: Long,
    val title: String,
    val subtitle: String,
    val status: DownloadQueueStatus,
    val timestampMillis: Long = System.currentTimeMillis(),
)

@Immutable
enum class DownloadQueueStatus {
    Waiting,
    Downloading,
    Completed,
    Failed,
}

internal fun AppSettings.toMuteFilter(): MuteFilter {
    return MuteFilter(
        illustIds = mutedIllusts.toSet(),
        userIds = mutedUsers.toSet(),
        tags = mutedTags.toSet(),
    )
}

