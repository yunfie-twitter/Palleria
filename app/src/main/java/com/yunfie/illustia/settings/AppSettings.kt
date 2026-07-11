package com.yunfie.illustia.settings

import androidx.compose.runtime.Immutable
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.StoredAccount

private const val DEFAULT_SEED_COLOR = 0xFF42A5F5L

@Immutable
data class AppSettings(
    val refreshToken: String = "",
    val bookmarkUserId: Long? = null,
    val appLanguage: String = "system",
    val appFont: String = "system",
    val themeMode: String = "system",
    val useDynamicColor: Boolean = true,
    val seedColor: Long = DEFAULT_SEED_COLOR,
    val onboardingSetupCompleted: Boolean = false,
    val allowR18: Boolean = false,
    val highQualityImages: Boolean = true,
    val bookmarkRestrict: Restrict = Restrict.Public,
    val searchSort: SearchSort = SearchSort.DateDesc,
    val searchTarget: SearchTarget = SearchTarget.PartialTags,
    val searchDuration: SearchDuration = SearchDuration.All,
    val searchBookmarkFilter: SearchBookmarkFilter = SearchBookmarkFilter.None,
    val searchUsersEnabled: Boolean = true,
    val searchHistory: List<String> = emptyList(),
    val favoriteTags: List<String> = emptyList(),
    val saveViewHistory: Boolean = true,
    val saveSearchHistory: Boolean = true,
    val appLockEnabled: Boolean = false,
    val appLockTiming: String = "launch",
    val biometricEnabled: Boolean = false,
    val appLockFailCount: Int = 0,
    val appLockCooldownUntil: Long = 0L,
    val viewHistory: List<Illust> = emptyList(),
    val smoothTransitions: Boolean = true,
    val hapticMode: String = "rich",
    val prefetchImages: Boolean = false,
    val autoLoadMore: Boolean = false,
    val notchOptimization: Boolean = true,
    val confirmOnLongPressSave: Boolean = true,
    val doubleBackToExit: Boolean = false,
    val swipeToSwitchWorks: Boolean = true,
    val secureWindow: Boolean = false,
    val amoledMode: Boolean = false,
    val skipConfirmOnDetailSave: Boolean = false,
    val showAiBadge: Boolean = true,
    val followOnLike: Boolean = false,
    val privateBookmarkDefault: Boolean = false,
    val autoDownloadOnBookmark: Boolean = false,
    val autoBookmarkOnDownload: Boolean = false,
    val downloadFolderByArtist: Boolean = true,
    val downloadFolderByWork: Boolean = true,
    val autoTagOnBookmark: Boolean = false,
    val simultaneousDownloads: Int = 2,
    val offlineWifiOnly: Boolean = true,
    val offlineStorageLimitBytes: Long = 5L * 1024 * 1024 * 1024,
    val feedPreviewQuality: String = "low",
    val illustDetailQuality: String = "low",
    val mangaDetailQuality: String = "low",
    val fullscreenQuality: String = "high",
    val startupScreen: String = "home",
    val userProfileBottomSheetEnabled: Boolean = false,
    val shortsFeedEnabled: Boolean = false,
    val disableHorizontalSwipeInShortsFeed: Boolean = false,
    val verticalColumnCount: Int = 2,
    val horizontalColumnCount: Int = 4,
    val pixivNetworkMode: String = "standard",
    val pixivImageProxyBaseUrl: String = "",
    val mutedIllusts: List<Long> = emptyList(),
    val mutedUsers: List<Long> = emptyList(),
    val mutedTags: List<String> = emptyList(),
    val accounts: List<StoredAccount> = emptyList(),
    val activeAccountIndex: Int = -1,
    val privacyModeEnabled: Boolean = false,
    val privacyModeAutoLockTiming: String = "immediate",  // immediate|30s|1m|5m|10m|disabled
    val hideRecents: Boolean = true,
    val hideNotifications: Boolean = false,
    val dummyAppName: String = "電卓",
    val dummyIconVariant: String = "ic_launcher_dummy",
)
