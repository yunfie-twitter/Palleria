package com.yunfie.illustia.settings

import com.yunfie.illustia.models.SearchBookmarkFilter
import com.yunfie.illustia.models.SearchDuration
import com.yunfie.illustia.models.SearchSort
import com.yunfie.illustia.models.SearchTarget
import com.yunfie.illustia.models.SearchWorkType

internal data class SearchSettings(
    val sort: SearchSort,
    val target: SearchTarget,
    val workType: SearchWorkType,
    val duration: SearchDuration,
    val bookmarkFilter: SearchBookmarkFilter,
    val usersEnabled: Boolean,
    val allowR18: Boolean,
    val allowR18G: Boolean,
)

internal data class SecuritySettings(
    val appLockEnabled: Boolean,
    val appLockTiming: String,
    val biometricEnabled: Boolean,
    val privacyModeEnabled: Boolean,
    val privacyModeAutoLockTiming: String,
    val secureWindow: Boolean,
)

internal data class ImageSettings(
    val feedQuality: String,
    val detailQuality: String,
    val mangaQuality: String,
    val fullscreenQuality: String,
    val proxyBaseUrl: String,
)

internal data class OfflineSettings(
    val wifiOnly: Boolean,
    val storageLimitBytes: Long,
    val simultaneousDownloads: Int,
    val smartCacheEnabled: Boolean,
    val smartCacheItemCount: Int,
)

internal data class WallpaperSettings(
    val enabled: Boolean,
    val source: String,
    val sourceFolder: String,
    val changeMode: String,
    val intervalMinutes: Int,
    val order: String,
    val scaleMode: String,
    val background: String,
    val crossfade: Boolean,
    val excludeSensitive: Boolean,
)

internal data class SyncSettings(
    val enabled: Boolean,
    val serverUrl: String,
)

internal fun AppSettings.searchProjection() =
    SearchSettings(
        searchSort,
        searchTarget,
        searchWorkType,
        searchDuration,
        searchBookmarkFilter,
        searchUsersEnabled,
        allowR18,
        allowR18G,
    )

internal fun AppSettings.securityProjection() =
    SecuritySettings(
        appLockEnabled,
        appLockTiming,
        biometricEnabled,
        privacyModeEnabled,
        privacyModeAutoLockTiming,
        secureWindow,
    )

internal fun AppSettings.imageProjection() =
    ImageSettings(
        feedPreviewQuality,
        illustDetailQuality,
        mangaDetailQuality,
        fullscreenQuality,
        pixivImageProxyBaseUrl,
    )

internal fun AppSettings.offlineProjection() =
    OfflineSettings(
        offlineWifiOnly,
        offlineStorageLimitBytes,
        simultaneousDownloads,
        smartCacheEnabled,
        smartCacheItemCount,
    )

internal fun AppSettings.wallpaperProjection() =
    WallpaperSettings(
        wallpaperPlaylistEnabled,
        liveWallpaperSource,
        liveWallpaperSourceFolder,
        liveWallpaperChangeMode,
        liveWallpaperIntervalMinutes,
        liveWallpaperOrder,
        liveWallpaperScaleMode,
        liveWallpaperBackground,
        liveWallpaperCrossfade,
        liveWallpaperExcludeSensitive,
    )

internal fun AppSettings.syncProjection() = SyncSettings(pallaSyncEnabled, pallaSyncServerUrl)
