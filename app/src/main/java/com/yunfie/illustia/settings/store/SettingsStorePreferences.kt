package com.yunfie.illustia.settings.store

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

internal fun readFromDataStore(
    preferences: Preferences,
    roomData: RoomSettingsData,
    sensitivePreferences: SharedPreferences,
): AppSettings {
    val tokenByUserId = decodeAccountTokens(sensitivePreferences.getString(KEY_ACCOUNT_TOKENS, "").orEmpty())
    val fallbackAccounts = decodeAccounts(sensitivePreferences.getString(KEY_ACCOUNTS, "").orEmpty())
    val fallbackTokenByUserId = fallbackAccounts.associate { it.userId to it.refreshToken }
    val accounts = if (roomData.accounts.isNotEmpty()) {
        roomData.accounts.mapNotNull { account ->
            val token = tokenByUserId[account.userId] ?: fallbackTokenByUserId[account.userId]
            if (token.isNullOrBlank()) return@mapNotNull null
            StoredAccount(
                name = account.name.orEmpty(),
                account = account.account.orEmpty(),
                profileImageUrl = account.profileImageUrl,
                refreshToken = token,
                userId = account.userId,
            )
        }
    } else {
        fallbackAccounts
    }
    return AppSettings(
        refreshToken = sensitivePreferences.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
        bookmarkUserId = preferences[BOOKMARK_USER_ID].takeIf { it != null && it > 0L },
        appLanguage = preferences[APP_LANGUAGE] ?: "system",
        appFont = preferences[APP_FONT] ?: "system",
        themeMode = preferences[THEME_MODE] ?: "system",
        useDynamicColor = preferences[USE_DYNAMIC_COLOR] ?: true,
        seedColor = preferences[SEED_COLOR] ?: 0xFF42A5F5L,
        onboardingSetupCompleted = preferences[ONBOARDING_SETUP_COMPLETED] ?: false,
        allowR18 = preferences[ALLOW_R18] ?: false,
        highQualityImages = preferences[HIGH_QUALITY_IMAGES] ?: true,
        bookmarkRestrict = enumValueOrDefault(preferences[BOOKMARK_RESTRICT], com.yunfie.illustia.models.Restrict.Public),
        searchSort = enumValueOrDefault(preferences[SEARCH_SORT], com.yunfie.illustia.models.SearchSort.DateDesc),
        searchTarget = enumValueOrDefault(preferences[SEARCH_TARGET], com.yunfie.illustia.models.SearchTarget.PartialTags),
        searchDuration = enumValueOrDefault(preferences[SEARCH_DURATION], com.yunfie.illustia.models.SearchDuration.All),
        searchBookmarkFilter = enumValueOrDefault(preferences[SEARCH_BOOKMARK_FILTER], com.yunfie.illustia.models.SearchBookmarkFilter.None),
        searchUsersEnabled = preferences[SEARCH_USERS_ENABLED] ?: true,
        searchHistory = roomData.searchHistory.map { it.query }.ifEmpty {
            decodeStringList(preferences[SEARCH_HISTORY_JSON])
        }.take(MAX_SEARCH_HISTORY),
        favoriteTags = roomData.favoriteTags.map { it.tag }.ifEmpty {
            decodeStringList(preferences[FAVORITE_TAGS_JSON])
        },
        saveViewHistory = preferences[SAVE_VIEW_HISTORY] ?: true,
        saveSearchHistory = preferences[SAVE_SEARCH_HISTORY] ?: true,
        appLockEnabled = preferences[APP_LOCK_ENABLED] ?: false,
        appLockTiming = preferences[APP_LOCK_TIMING] ?: "launch",
        biometricEnabled = preferences[BIOMETRIC_ENABLED] ?: false,
        appLockFailCount = preferences[APP_LOCK_FAIL_COUNT] ?: 0,
        appLockCooldownUntil = preferences[APP_LOCK_COOLDOWN_UNTIL] ?: 0L,
        viewHistory = roomData.viewHistory.map(::illustFromEntity).ifEmpty {
            decodeHistoryIllusts(preferences[VIEW_HISTORY_JSON])
        }.take(MAX_VIEW_HISTORY),
        smoothTransitions = preferences[SMOOTH_TRANSITIONS] ?: true,
        hapticMode = preferences[HAPTIC_MODE] ?: "rich",
        prefetchImages = preferences[PREFETCH_IMAGES] ?: false,
        autoLoadMore = preferences[AUTO_LOAD_MORE] ?: false,
        notchOptimization = preferences[NOTCH_OPTIMIZATION] ?: true,
        confirmOnLongPressSave = preferences[CONFIRM_ON_LONG_PRESS_SAVE] ?: true,
        doubleBackToExit = preferences[DOUBLE_BACK_TO_EXIT] ?: false,
        swipeToSwitchWorks = preferences[SWIPE_TO_SWITCH_WORKS] ?: true,
        secureWindow = preferences[SECURE_WINDOW] ?: false,
        amoledMode = preferences[AMOLED_MODE] ?: false,
        skipConfirmOnDetailSave = preferences[SKIP_CONFIRM_ON_DETAIL_SAVE] ?: false,
        showAiBadge = preferences[SHOW_AI_BADGE] ?: true,
        followOnLike = preferences[FOLLOW_ON_LIKE] ?: false,
        privateBookmarkDefault = preferences[PRIVATE_BOOKMARK_DEFAULT] ?: false,
        autoDownloadOnBookmark = preferences[AUTO_DOWNLOAD_ON_BOOKMARK] ?: false,
        autoBookmarkOnDownload = preferences[AUTO_BOOKMARK_ON_DOWNLOAD] ?: false,
        downloadFolderByArtist = preferences[DOWNLOAD_FOLDER_BY_ARTIST] ?: true,
        downloadFolderByWork = preferences[DOWNLOAD_FOLDER_BY_WORK] ?: true,
        autoTagOnBookmark = preferences[AUTO_TAG_ON_BOOKMARK] ?: false,
        simultaneousDownloads = preferences[SIMULTANEOUS_DOWNLOADS] ?: 2,
        offlineWifiOnly = preferences[OFFLINE_WIFI_ONLY] ?: true,
        offlineStorageLimitBytes = preferences[OFFLINE_STORAGE_LIMIT_BYTES] ?: DEFAULT_OFFLINE_STORAGE_LIMIT_BYTES,
        feedPreviewQuality = preferences[FEED_PREVIEW_QUALITY] ?: "low",
        illustDetailQuality = preferences[ILLUST_DETAIL_QUALITY] ?: "low",
        mangaDetailQuality = preferences[MANGA_DETAIL_QUALITY] ?: "low",
        fullscreenQuality = preferences[FULLSCREEN_QUALITY] ?: "high",
        startupScreen = preferences[STARTUP_SCREEN] ?: "home",
        userProfileBottomSheetEnabled = preferences[USER_PROFILE_BOTTOM_SHEET_ENABLED] ?: false,
        shortsFeedEnabled = preferences[SHORTS_FEED_ENABLED] ?: false,
        disableHorizontalSwipeInShortsFeed = preferences[DISABLE_HORIZONTAL_SWIPE_IN_SHORTS_FEED] ?: false,
        verticalColumnCount = preferences[VERTICAL_COLUMN_COUNT] ?: 2,
        horizontalColumnCount = preferences[HORIZONTAL_COLUMN_COUNT] ?: 4,
        pixivNetworkMode = preferences[PIXIV_NETWORK_MODE] ?: "standard",
        pixivImageProxyBaseUrl = preferences[PIXIV_IMAGE_PROXY_BASE_URL].orEmpty(),
        mutedIllusts = decodeLongList(preferences[MUTED_ILLUSTS_JSON]),
        mutedUsers = decodeLongList(preferences[MUTED_USERS_JSON]),
        mutedTags = decodeStringList(preferences[MUTED_TAGS_JSON]),
        accounts = accounts,
        activeAccountIndex = preferences[ACTIVE_ACCOUNT_INDEX] ?: -1,
        privacyModeEnabled = preferences[PRIVACY_MODE_ENABLED] ?: false,
        privacyModeAutoLockTiming = preferences[PRIVACY_MODE_AUTO_LOCK_TIMING] ?: "immediate",
        hideRecents = preferences[HIDE_RECENTS] ?: true,
        hideNotifications = preferences[HIDE_NOTIFICATIONS] ?: false,
        dummyAppName = preferences[DUMMY_APP_NAME] ?: "電卓",
        dummyIconVariant = preferences[DUMMY_ICON_VARIANT] ?: "ic_launcher_dummy",
    )
}

internal suspend fun readDataStorePreferences(dataStore: DataStore<Preferences>): Preferences = withContext(Dispatchers.IO) {
    dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .first()
}

internal suspend fun writeDataStorePreferences(
    dataStore: DataStore<Preferences>,
    settings: AppSettings,
) {
    dataStore.edit { preferences ->
        writeToDataStore(preferences, settings)
    }
}

internal fun readFromSharedPreferences(preferences: SharedPreferences): AppSettings {
    return AppSettings(
        refreshToken = preferences.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
        bookmarkUserId = preferences.getLong(KEY_BOOKMARK_USER_ID, 0L).takeIf { it > 0L },
        appLanguage = preferences.getString(KEY_APP_LANGUAGE, "system") ?: "system",
        appFont = preferences.getString(KEY_APP_FONT, "system") ?: "system",
        themeMode = preferences.getString(KEY_THEME_MODE, "system") ?: "system",
        useDynamicColor = preferences.getBoolean(KEY_USE_DYNAMIC_COLOR, true),
        seedColor = preferences.getLong(KEY_SEED_COLOR, 0xFF42A5F5L),
        onboardingSetupCompleted = preferences.getBoolean(KEY_ONBOARDING_SETUP_COMPLETED, false),
        allowR18 = preferences.getBoolean(KEY_ALLOW_R18, false),
        highQualityImages = preferences.getBoolean(KEY_HIGH_QUALITY, true),
        bookmarkRestrict = enumValueOrDefault(preferences.getString(KEY_BOOKMARK_RESTRICT, null), com.yunfie.illustia.models.Restrict.Public),
        searchSort = enumValueOrDefault(preferences.getString(KEY_SEARCH_SORT, null), com.yunfie.illustia.models.SearchSort.DateDesc),
        searchTarget = enumValueOrDefault(preferences.getString(KEY_SEARCH_TARGET, null), com.yunfie.illustia.models.SearchTarget.PartialTags),
        searchDuration = enumValueOrDefault(preferences.getString(KEY_SEARCH_DURATION, null), com.yunfie.illustia.models.SearchDuration.All),
        searchBookmarkFilter = enumValueOrDefault(preferences.getString(KEY_SEARCH_BOOKMARK_FILTER, null), com.yunfie.illustia.models.SearchBookmarkFilter.None),
        searchUsersEnabled = preferences.getBoolean(KEY_SEARCH_USERS_ENABLED, true),
        searchHistory = decodeLegacyStringList(preferences.getString(KEY_SEARCH_HISTORY, "")).take(MAX_SEARCH_HISTORY),
        favoriteTags = decodeLegacyStringList(preferences.getString(KEY_FAVORITE_TAGS, "")),
        saveViewHistory = preferences.getBoolean("saveViewHistory", true),
        saveSearchHistory = preferences.getBoolean("saveSearchHistory", true),
        appLockEnabled = preferences.getBoolean("appLockEnabled", false),
        appLockTiming = preferences.getString("appLockTiming", "launch") ?: "launch",
        biometricEnabled = preferences.getBoolean("biometricEnabled", false),
        appLockFailCount = 0,
        appLockCooldownUntil = 0L,
        viewHistory = decodeHistoryIllusts(preferences.getString(KEY_VIEW_HISTORY, "")).take(MAX_VIEW_HISTORY),
        smoothTransitions = preferences.getBoolean(KEY_SMOOTH_TRANSITIONS, true),
        hapticMode = preferences.getString(KEY_HAPTIC_MODE, "rich") ?: "rich",
        prefetchImages = preferences.getBoolean(KEY_PREFETCH_IMAGES, false),
        autoLoadMore = preferences.getBoolean("autoLoadMore", false),
        notchOptimization = preferences.getBoolean("notchOptimization", true),
        confirmOnLongPressSave = preferences.getBoolean("confirmOnLongPressSave", true),
        doubleBackToExit = preferences.getBoolean("doubleBackToExit", false),
        swipeToSwitchWorks = preferences.getBoolean("swipeToSwitchWorks", true),
        secureWindow = preferences.getBoolean("secureWindow", false),
        amoledMode = preferences.getBoolean("amoledMode", false),
        skipConfirmOnDetailSave = preferences.getBoolean("skipConfirmOnDetailSave", false),
        showAiBadge = preferences.getBoolean("showAiBadge", true),
        followOnLike = preferences.getBoolean("followOnLike", false),
        privateBookmarkDefault = preferences.getBoolean("privateBookmarkDefault", false),
        autoDownloadOnBookmark = preferences.getBoolean("autoDownloadOnBookmark", false),
        autoBookmarkOnDownload = preferences.getBoolean("autoBookmarkOnDownload", false),
        downloadFolderByArtist = preferences.getBoolean("downloadFolderByArtist", true),
        downloadFolderByWork = preferences.getBoolean("downloadFolderByWork", true),
        autoTagOnBookmark = preferences.getBoolean("autoTagOnBookmark", false),
        simultaneousDownloads = preferences.getInt("simultaneousDownloads", 2),
        offlineWifiOnly = preferences.getBoolean("offlineWifiOnly", true),
        offlineStorageLimitBytes = preferences.getLong("offlineStorageLimitBytes", DEFAULT_OFFLINE_STORAGE_LIMIT_BYTES),
        feedPreviewQuality = preferences.getString("feedPreviewQuality", "low") ?: "low",
        illustDetailQuality = preferences.getString("illustDetailQuality", "low") ?: "low",
        mangaDetailQuality = preferences.getString("mangaDetailQuality", "low") ?: "low",
        fullscreenQuality = preferences.getString("fullscreenQuality", "high") ?: "high",
        startupScreen = preferences.getString("startupScreen", "home") ?: "home",
        userProfileBottomSheetEnabled = preferences.getBoolean("userProfileBottomSheetEnabled", false),
        shortsFeedEnabled = preferences.getBoolean("shortsFeedEnabled", false),
        disableHorizontalSwipeInShortsFeed = preferences.getBoolean("disableHorizontalSwipeInShortsFeed", false),
        verticalColumnCount = preferences.getInt("verticalColumnCount", 2),
        horizontalColumnCount = preferences.getInt("horizontalColumnCount", 4),
        pixivNetworkMode = preferences.getString(KEY_PIXIV_NETWORK_MODE, "standard") ?: "standard",
        pixivImageProxyBaseUrl = preferences.getString(KEY_PIXIV_IMAGE_PROXY_BASE_URL, "").orEmpty(),
        mutedIllusts = preferences.getString("mutedIllusts", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() },
        mutedUsers = preferences.getString("mutedUsers", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() },
        mutedTags = preferences.getString("mutedTags", "").orEmpty().split(",").filter { it.isNotBlank() },
        accounts = decodeAccounts(preferences.getString(KEY_ACCOUNTS, "").orEmpty()),
        activeAccountIndex = preferences.getInt(KEY_ACTIVE_ACCOUNT_INDEX, -1),
        privacyModeEnabled = false,
        privacyModeAutoLockTiming = "immediate",
        hideRecents = true,
        hideNotifications = false,
        dummyAppName = "電卓",
        dummyIconVariant = "ic_launcher_dummy",
    )
}

internal fun readCollectionsFromDataStore(
    preferences: Preferences,
    sensitivePreferences: SharedPreferences,
): AppSettings {
    return AppSettings(
        refreshToken = sensitivePreferences.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
        searchHistory = decodeStringList(preferences[SEARCH_HISTORY_JSON]).take(MAX_SEARCH_HISTORY),
        favoriteTags = decodeStringList(preferences[FAVORITE_TAGS_JSON]),
        viewHistory = decodeHistoryIllusts(preferences[VIEW_HISTORY_JSON]).take(MAX_VIEW_HISTORY),
        accounts = decodeAccounts(sensitivePreferences.getString(KEY_ACCOUNTS, "").orEmpty()),
    )
}

internal fun writeToDataStore(preferences: MutablePreferences, settings: AppSettings) {
    preferences[SETTINGS_VERSION] = CURRENT_SETTINGS_VERSION
    settings.bookmarkUserId?.let { preferences[BOOKMARK_USER_ID] = it } ?: preferences.remove(BOOKMARK_USER_ID)
    preferences[APP_LANGUAGE] = settings.appLanguage
    preferences[APP_FONT] = settings.appFont
    preferences[THEME_MODE] = settings.themeMode
    preferences[USE_DYNAMIC_COLOR] = settings.useDynamicColor
    preferences[SEED_COLOR] = settings.seedColor
    preferences[ONBOARDING_SETUP_COMPLETED] = settings.onboardingSetupCompleted
    preferences[ALLOW_R18] = settings.allowR18
    preferences[HIGH_QUALITY_IMAGES] = settings.highQualityImages
    preferences[BOOKMARK_RESTRICT] = settings.bookmarkRestrict.name
    preferences[SEARCH_SORT] = settings.searchSort.name
    preferences[SEARCH_TARGET] = settings.searchTarget.name
    preferences[SEARCH_DURATION] = settings.searchDuration.name
    preferences[SEARCH_BOOKMARK_FILTER] = settings.searchBookmarkFilter.name
    preferences[SEARCH_USERS_ENABLED] = settings.searchUsersEnabled
    preferences[SAVE_VIEW_HISTORY] = settings.saveViewHistory
    preferences[SAVE_SEARCH_HISTORY] = settings.saveSearchHistory
    preferences[APP_LOCK_ENABLED] = settings.appLockEnabled
    preferences[APP_LOCK_TIMING] = settings.appLockTiming
    preferences[BIOMETRIC_ENABLED] = settings.biometricEnabled
    preferences[APP_LOCK_FAIL_COUNT] = settings.appLockFailCount
    preferences[APP_LOCK_COOLDOWN_UNTIL] = settings.appLockCooldownUntil
    preferences.remove(SEARCH_HISTORY_JSON)
    preferences.remove(FAVORITE_TAGS_JSON)
    preferences.remove(VIEW_HISTORY_JSON)
    preferences[SMOOTH_TRANSITIONS] = settings.smoothTransitions
    preferences[HAPTIC_MODE] = settings.hapticMode
    preferences[PREFETCH_IMAGES] = settings.prefetchImages
    preferences[AUTO_LOAD_MORE] = settings.autoLoadMore
    preferences[NOTCH_OPTIMIZATION] = settings.notchOptimization
    preferences[CONFIRM_ON_LONG_PRESS_SAVE] = settings.confirmOnLongPressSave
    preferences[DOUBLE_BACK_TO_EXIT] = settings.doubleBackToExit
    preferences[SWIPE_TO_SWITCH_WORKS] = settings.swipeToSwitchWorks
    preferences[SECURE_WINDOW] = settings.secureWindow
    preferences[AMOLED_MODE] = settings.amoledMode
    preferences[SKIP_CONFIRM_ON_DETAIL_SAVE] = settings.skipConfirmOnDetailSave
    preferences[SHOW_AI_BADGE] = settings.showAiBadge
    preferences[FOLLOW_ON_LIKE] = settings.followOnLike
    preferences[PRIVATE_BOOKMARK_DEFAULT] = settings.privateBookmarkDefault
    preferences[AUTO_DOWNLOAD_ON_BOOKMARK] = settings.autoDownloadOnBookmark
    preferences[AUTO_BOOKMARK_ON_DOWNLOAD] = settings.autoBookmarkOnDownload
    preferences[DOWNLOAD_FOLDER_BY_ARTIST] = settings.downloadFolderByArtist
    preferences[DOWNLOAD_FOLDER_BY_WORK] = settings.downloadFolderByWork
    preferences[AUTO_TAG_ON_BOOKMARK] = settings.autoTagOnBookmark
    preferences[SIMULTANEOUS_DOWNLOADS] = settings.simultaneousDownloads
    preferences[OFFLINE_WIFI_ONLY] = settings.offlineWifiOnly
    preferences[OFFLINE_STORAGE_LIMIT_BYTES] = settings.offlineStorageLimitBytes
    preferences[FEED_PREVIEW_QUALITY] = settings.feedPreviewQuality
    preferences[ILLUST_DETAIL_QUALITY] = settings.illustDetailQuality
    preferences[MANGA_DETAIL_QUALITY] = settings.mangaDetailQuality
    preferences[FULLSCREEN_QUALITY] = settings.fullscreenQuality
    preferences[STARTUP_SCREEN] = settings.startupScreen
    preferences[USER_PROFILE_BOTTOM_SHEET_ENABLED] = settings.userProfileBottomSheetEnabled
    preferences[SHORTS_FEED_ENABLED] = settings.shortsFeedEnabled
    preferences[DISABLE_HORIZONTAL_SWIPE_IN_SHORTS_FEED] = settings.disableHorizontalSwipeInShortsFeed
    preferences[VERTICAL_COLUMN_COUNT] = settings.verticalColumnCount
    preferences[HORIZONTAL_COLUMN_COUNT] = settings.horizontalColumnCount
    preferences[PIXIV_NETWORK_MODE] = settings.pixivNetworkMode
    preferences[PIXIV_IMAGE_PROXY_BASE_URL] = settings.pixivImageProxyBaseUrl
    preferences[MUTED_ILLUSTS_JSON] = encodeLongList(settings.mutedIllusts)
    preferences[MUTED_USERS_JSON] = encodeLongList(settings.mutedUsers)
    preferences[MUTED_TAGS_JSON] = encodeStringList(settings.mutedTags)
    preferences[ACTIVE_ACCOUNT_INDEX] = settings.activeAccountIndex
    preferences[PRIVACY_MODE_ENABLED] = settings.privacyModeEnabled
    preferences[PRIVACY_MODE_AUTO_LOCK_TIMING] = settings.privacyModeAutoLockTiming
    preferences[HIDE_RECENTS] = settings.hideRecents
    preferences[HIDE_NOTIFICATIONS] = settings.hideNotifications
    preferences[DUMMY_APP_NAME] = settings.dummyAppName
    preferences[DUMMY_ICON_VARIANT] = settings.dummyIconVariant
}

internal fun writeSensitiveSettings(
    sensitivePreferences: SharedPreferences,
    settings: AppSettings,
) {
    sensitivePreferences.edit()
        .putString(KEY_REFRESH_TOKEN, settings.refreshToken)
        .putString(KEY_ACCOUNT_TOKENS, encodeAccountTokens(settings.accounts))
        .remove(KEY_ACCOUNTS)
        .apply()
}
