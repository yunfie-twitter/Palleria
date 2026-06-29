package com.yunfie.illustia.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yunfie.illustia.data.Illust
import com.yunfie.illustia.data.Restrict
import com.yunfie.illustia.data.SearchBookmarkFilter
import com.yunfie.illustia.data.SearchDuration
import com.yunfie.illustia.data.SearchSort
import com.yunfie.illustia.data.SearchTarget
import com.yunfie.illustia.data.StoredAccount
import com.yunfie.illustia.settings.db.AccountEntity
import com.yunfie.illustia.settings.db.FavoriteTagEntity
import com.yunfie.illustia.settings.db.IllustiaDatabase
import com.yunfie.illustia.settings.db.SearchHistoryEntity
import com.yunfie.illustia.settings.db.SettingsDao
import com.yunfie.illustia.settings.db.SavedIllustEntity
import com.yunfie.illustia.settings.db.SavedIllustPageEntity
import com.yunfie.illustia.settings.db.ViewHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

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
    val feedPreviewQuality: String = "low",
    val illustDetailQuality: String = "low",
    val mangaDetailQuality: String = "low",
    val fullscreenQuality: String = "high",
    val viewerThumbnailsInToolbar: Boolean = false,
    val startupScreen: String = "home",
    val offlineWifiOnly: Boolean = true,
    val offlineStorageLimitBytes: Long = 5L * 1024 * 1024 * 1024,
    val userProfileBottomSheetEnabled: Boolean = false,
    val verticalColumnCount: Int = 2,
    val horizontalColumnCount: Int = 4,
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

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPreferences = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val encryptedPreferences = createEncryptedPreferences(appContext)
    private val sensitivePreferences = encryptedPreferences ?: legacyPreferences
    private val dataStore = dataStoreFor(appContext)
    private val database = IllustiaDatabase.getInstance(appContext)
    private val dao = database.settingsDao()

    init {
        migrateIfNeeded()
    }

    suspend fun read(): AppSettings = withContext(Dispatchers.IO) {
        coroutineScope {
            val preferences = async {
                dataStore.data
                    .catch { error ->
                        if (error is IOException) emit(emptyPreferences()) else throw error
                    }
                    .first()
            }
            val roomData = async {
                readRoomData()
            }
            readFromDataStore(preferences.await(), roomData.await())
        }
    }

    suspend fun write(settings: AppSettings) {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                writeToDataStore(preferences, settings)
            }
            writeSensitive(settings)
            writeRoomData(settings)
        }
    }

    suspend fun clearSensitive() {
        withContext(Dispatchers.IO) {
            sensitivePreferences.edit()
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_ACCOUNTS)
                .remove(KEY_ACCOUNT_TOKENS)
                .apply()
            legacyPreferences.edit()
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_ACCOUNTS)
                .remove(KEY_ACCOUNT_TOKENS)
                .apply()
            database.runInTransaction {
                dao.clearAccounts()
            }
            dataStore.edit { preferences ->
                preferences[ACTIVE_ACCOUNT_INDEX] = -1
            }
        }
    }

    fun savePinHash(pin: String) {
        val salt = generateSalt()
        val hash = pbkdf2(pin, salt)
        sensitivePreferences.edit()
            .putString(KEY_PIN_HASH, hash)
            .putString(KEY_PIN_SALT, salt)
            .apply()
    }

    fun verifyPin(pin: String): Boolean {
        val storedHash = sensitivePreferences.getString(KEY_PIN_HASH, null) ?: return false
        val salt = sensitivePreferences.getString(KEY_PIN_SALT, null)
        if (salt == null) {
            // Legacy SHA-256 hash (no salt) — verify and migrate on success
            val legacyMatch = constantTimeEquals(sha256(pin).toByteArray(), storedHash.toByteArray())
            if (legacyMatch) {
                savePinHash(pin)
            }
            return legacyMatch
        }
        val computed = pbkdf2(pin, salt)
        return constantTimeEquals(computed.toByteArray(), storedHash.toByteArray())
    }

    fun hasPinSet(): Boolean {
        return sensitivePreferences.getString(KEY_PIN_HASH, null) != null
    }

    fun clearPinHash() {
        sensitivePreferences.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_PIN_SALT)
            .apply()
    }

    // --- Unlock Code (Privacy Mode) ---

    /**
     * 解除コードを PBKDF2WithHmacSHA256 + 32byte ランダムソルト + 100,000 iterations で
     * ハッシュ化し、EncryptedSharedPreferences に保存する。
     */
    fun saveUnlockCodeHash(code: String) {
        val salt = generateSalt()
        val hash = pbkdf2(code, salt)
        sensitivePreferences.edit()
            .putString(KEY_UNLOCK_CODE_HASH, hash)
            .putString(KEY_UNLOCK_CODE_SALT, salt)
            .apply()
    }

    /**
     * 指定した解除コードが保存済みハッシュと一致するか定数時間比較で検証する。
     * 暗号エラーが発生した場合は false を返す（フェイルセーフ）。
     * @return 一致すれば true
     */
    fun verifyUnlockCode(code: String): Boolean {
        return try {
            val storedHash = sensitivePreferences.getString(KEY_UNLOCK_CODE_HASH, null) ?: return false
            val salt = sensitivePreferences.getString(KEY_UNLOCK_CODE_SALT, null) ?: return false
            val computed = pbkdf2(code, salt)
            constantTimeEquals(computed.toByteArray(), storedHash.toByteArray())
        } catch (e: Exception) {
            android.util.Log.e("SettingsStore", "verifyUnlockCode error", e)
            false
        }
    }

    /**
     * 解除コードが設定済みかどうかを返す。
     */
    fun hasUnlockCodeSet(): Boolean {
        return sensitivePreferences.getString(KEY_UNLOCK_CODE_HASH, null) != null
    }

    /**
     * 解除コードハッシュを削除する（プライバシーモード無効化時に呼ぶ）。
     */
    fun clearUnlockCodeHash() {
        sensitivePreferences.edit()
            .remove(KEY_UNLOCK_CODE_HASH)
            .remove(KEY_UNLOCK_CODE_SALT)
            .apply()
    }

    /**
     * 解除コードのバリデーション。
     * 4〜20 文字で、数字（0-9）および電卓記号（+, -, *, ×, /, ÷, ., =）のみ許容。
     * @return 有効なら true
     */
    fun isValidUnlockCode(code: String): Boolean {
        if (code.length !in 4..20) return false
        val allowedChars = setOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            '+', '-', '*', '×', '/', '÷', '.', '=')
        return code.all { it in allowedChars }
    }

    suspend fun getSavedIllusts(): List<SavedIllustEntity> = withContext(Dispatchers.IO) {
        dao.getSavedIllusts()
    }

    suspend fun getSavedIllust(illustId: Long): com.yunfie.illustia.settings.db.SavedIllustWithPages? = withContext(Dispatchers.IO) {
        dao.getSavedIllust(illustId)
    }

    suspend fun saveSavedIllust(
        illust: SavedIllustEntity,
        pages: List<SavedIllustPageEntity>,
    ) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            dao.deleteSavedIllustPages(illust.illustId)
            dao.deleteSavedIllust(illust.illustId)
            dao.upsertSavedIllust(illust)
            dao.upsertSavedIllustPages(pages)
        }
    }

    suspend fun deleteSavedIllust(illustId: Long) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            dao.deleteSavedIllustPages(illustId)
            dao.deleteSavedIllust(illustId)
        }
    }

    suspend fun getSavedIllustStorageBytes(): Long = withContext(Dispatchers.IO) {
        val directory = savedIllustDir()
        if (!directory.exists()) return@withContext 0L
        directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun savedIllustDir(): java.io.File {
        return java.io.File(appContext.filesDir, "saved_illusts")
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2(pin: String, salt: String): String {
        val spec = javax.crypto.spec.PBEKeySpec(
            pin.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH,
        )
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        return java.security.MessageDigest.isEqual(a, b)
    }

    private fun readFromDataStore(preferences: Preferences, roomData: RoomSettingsData): AppSettings {
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
            seedColor = preferences[SEED_COLOR] ?: DEFAULT_SEED_COLOR,
            onboardingSetupCompleted = preferences[ONBOARDING_SETUP_COMPLETED] ?: false,
            allowR18 = preferences[ALLOW_R18] ?: false,
            highQualityImages = preferences[HIGH_QUALITY_IMAGES] ?: true,
            bookmarkRestrict = enumValueOrDefault(preferences[BOOKMARK_RESTRICT], Restrict.Public),
            searchSort = enumValueOrDefault(preferences[SEARCH_SORT], SearchSort.DateDesc),
            searchTarget = enumValueOrDefault(preferences[SEARCH_TARGET], SearchTarget.PartialTags),
            searchDuration = enumValueOrDefault(preferences[SEARCH_DURATION], SearchDuration.All),
            searchBookmarkFilter = enumValueOrDefault(preferences[SEARCH_BOOKMARK_FILTER], SearchBookmarkFilter.None),
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
            feedPreviewQuality = preferences[FEED_PREVIEW_QUALITY] ?: "low",
            illustDetailQuality = preferences[ILLUST_DETAIL_QUALITY] ?: "low",
            mangaDetailQuality = preferences[MANGA_DETAIL_QUALITY] ?: "low",
            fullscreenQuality = preferences[FULLSCREEN_QUALITY] ?: "high",
            viewerThumbnailsInToolbar = preferences[VIEWER_THUMBNAILS_IN_TOOLBAR] ?: false,
            startupScreen = preferences[STARTUP_SCREEN] ?: "home",
            offlineWifiOnly = preferences[OFFLINE_WIFI_ONLY] ?: true,
            offlineStorageLimitBytes = preferences[OFFLINE_STORAGE_LIMIT_BYTES] ?: DEFAULT_OFFLINE_STORAGE_LIMIT_BYTES,
            userProfileBottomSheetEnabled = preferences[USER_PROFILE_BOTTOM_SHEET_ENABLED] ?: false,
            verticalColumnCount = preferences[VERTICAL_COLUMN_COUNT] ?: 2,
            horizontalColumnCount = preferences[HORIZONTAL_COLUMN_COUNT] ?: 4,
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

    private fun writeToDataStore(preferences: MutablePreferences, settings: AppSettings) {
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
        preferences[FEED_PREVIEW_QUALITY] = settings.feedPreviewQuality
        preferences[ILLUST_DETAIL_QUALITY] = settings.illustDetailQuality
        preferences[MANGA_DETAIL_QUALITY] = settings.mangaDetailQuality
        preferences[FULLSCREEN_QUALITY] = settings.fullscreenQuality
        preferences[VIEWER_THUMBNAILS_IN_TOOLBAR] = settings.viewerThumbnailsInToolbar
        preferences[STARTUP_SCREEN] = settings.startupScreen
        preferences[OFFLINE_WIFI_ONLY] = settings.offlineWifiOnly
        preferences[OFFLINE_STORAGE_LIMIT_BYTES] = settings.offlineStorageLimitBytes
        preferences[USER_PROFILE_BOTTOM_SHEET_ENABLED] = settings.userProfileBottomSheetEnabled
        preferences[VERTICAL_COLUMN_COUNT] = settings.verticalColumnCount
        preferences[HORIZONTAL_COLUMN_COUNT] = settings.horizontalColumnCount
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

    private fun writeSensitive(settings: AppSettings) {
        sensitivePreferences.edit()
            .putString(KEY_REFRESH_TOKEN, settings.refreshToken)
            .putString(KEY_ACCOUNT_TOKENS, encodeAccountTokens(settings.accounts))
            .remove(KEY_ACCOUNTS)
            .apply()
    }

    private fun readRoomData(): RoomSettingsData {
        return RoomSettingsData(
            searchHistory = dao.getSearchHistory(),
            favoriteTags = dao.getFavoriteTags(),
            viewHistory = dao.getViewHistory(),
            accounts = dao.getAccounts(),
        )
    }

    private fun writeRoomData(settings: AppSettings) {
        database.runInTransaction {
            dao.clearSearchHistory()
            dao.insertSearchHistory(
                settings.searchHistory.take(MAX_SEARCH_HISTORY).mapIndexed { index, query ->
                    SearchHistoryEntity(query, index)
                },
            )

            dao.clearFavoriteTags()
            dao.insertFavoriteTags(
                settings.favoriteTags.mapIndexed { index, tag ->
                    FavoriteTagEntity(tag, index)
                },
            )

            dao.clearViewHistory()
            dao.insertViewHistory(
                settings.viewHistory.take(MAX_VIEW_HISTORY).mapIndexed { index, illust ->
                    ViewHistoryEntity(
                        illust.id,
                        illust.title,
                        illust.artistName,
                        illust.imageUrl,
                        illust.pageCount,
                        illust.type,
                        index,
                    )
                },
            )

            dao.clearAccounts()
            dao.insertAccounts(
                settings.accounts.mapIndexed { index, account ->
                    AccountEntity(
                        account.userId,
                        account.name,
                        account.account,
                        account.profileImageUrl,
                        index,
                    )
                },
            )
        }
    }

    private fun migrateIfNeeded() {
        runBlocking(Dispatchers.IO) {
            val current = dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .first()
            if ((current[SETTINGS_VERSION] ?: 0) >= CURRENT_SETTINGS_VERSION) return@runBlocking

            val source = when {
                encryptedPreferences != null && encryptedPreferences.all.isNotEmpty() -> encryptedPreferences
                else -> legacyPreferences
            }
            val migratedFromSharedPreferences = readFromSharedPreferences(source)
            val migratedFromDataStore = readCollectionsFromDataStore(current)
            val hadExistingData = source.all.isNotEmpty() || current.asMap().isNotEmpty()
            val migrated = migratedFromSharedPreferences.copy(
                searchHistory = migratedFromDataStore.searchHistory.ifEmpty { migratedFromSharedPreferences.searchHistory },
                favoriteTags = migratedFromDataStore.favoriteTags.ifEmpty { migratedFromSharedPreferences.favoriteTags },
                viewHistory = migratedFromDataStore.viewHistory.ifEmpty { migratedFromSharedPreferences.viewHistory },
                accounts = migratedFromDataStore.accounts.ifEmpty { migratedFromSharedPreferences.accounts },
                onboardingSetupCompleted = hadExistingData,
            )
            dataStore.edit { preferences ->
                writeToDataStore(preferences, migrated)
            }
            writeSensitive(migrated)
            writeRoomData(migrated)
            legacyPreferences.edit()
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_ACCOUNTS)
                .remove(KEY_ACTIVE_ACCOUNT_INDEX)
                .apply()
        }
    }

    private data class RoomSettingsData(
        val searchHistory: List<SearchHistoryEntity> = emptyList(),
        val favoriteTags: List<FavoriteTagEntity> = emptyList(),
        val viewHistory: List<ViewHistoryEntity> = emptyList(),
        val accounts: List<AccountEntity> = emptyList(),
    )

    private fun readFromSharedPreferences(preferences: SharedPreferences): AppSettings {
        return AppSettings(
            refreshToken = preferences.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            bookmarkUserId = preferences.getLong(KEY_BOOKMARK_USER_ID, 0L).takeIf { it > 0L },
            appLanguage = preferences.getString(KEY_APP_LANGUAGE, "system") ?: "system",
            appFont = preferences.getString(KEY_APP_FONT, "system") ?: "system",
            themeMode = preferences.getString(KEY_THEME_MODE, "system") ?: "system",
            useDynamicColor = preferences.getBoolean(KEY_USE_DYNAMIC_COLOR, true),
            seedColor = preferences.getLong(KEY_SEED_COLOR, DEFAULT_SEED_COLOR),
            onboardingSetupCompleted = preferences.getBoolean(KEY_ONBOARDING_SETUP_COMPLETED, false),
            allowR18 = preferences.getBoolean(KEY_ALLOW_R18, false),
            highQualityImages = preferences.getBoolean(KEY_HIGH_QUALITY, true),
            bookmarkRestrict = enumValueOrDefault(preferences.getString(KEY_BOOKMARK_RESTRICT, null), Restrict.Public),
            searchSort = enumValueOrDefault(preferences.getString(KEY_SEARCH_SORT, null), SearchSort.DateDesc),
            searchTarget = enumValueOrDefault(preferences.getString(KEY_SEARCH_TARGET, null), SearchTarget.PartialTags),
            searchDuration = enumValueOrDefault(preferences.getString(KEY_SEARCH_DURATION, null), SearchDuration.All),
            searchBookmarkFilter = enumValueOrDefault(preferences.getString(KEY_SEARCH_BOOKMARK_FILTER, null), SearchBookmarkFilter.None),
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
            feedPreviewQuality = preferences.getString("feedPreviewQuality", "low") ?: "low",
            illustDetailQuality = preferences.getString("illustDetailQuality", "low") ?: "low",
            mangaDetailQuality = preferences.getString("mangaDetailQuality", "low") ?: "low",
            fullscreenQuality = preferences.getString("fullscreenQuality", "high") ?: "high",
            viewerThumbnailsInToolbar = preferences.getBoolean("viewerThumbnailsInToolbar", false),
            startupScreen = preferences.getString("startupScreen", "home") ?: "home",
            userProfileBottomSheetEnabled = preferences.getBoolean("userProfileBottomSheetEnabled", false),
            verticalColumnCount = preferences.getInt("verticalColumnCount", 2),
            horizontalColumnCount = preferences.getInt("horizontalColumnCount", 4),
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

    private fun readCollectionsFromDataStore(preferences: Preferences): AppSettings {
        return AppSettings(
            refreshToken = sensitivePreferences.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            searchHistory = decodeStringList(preferences[SEARCH_HISTORY_JSON]).take(MAX_SEARCH_HISTORY),
            favoriteTags = decodeStringList(preferences[FAVORITE_TAGS_JSON]),
            viewHistory = decodeHistoryIllusts(preferences[VIEW_HISTORY_JSON]).take(MAX_VIEW_HISTORY),
            accounts = decodeAccounts(sensitivePreferences.getString(KEY_ACCOUNTS, "").orEmpty()),
        )
    }

    private fun illustFromEntity(entity: ViewHistoryEntity): Illust {
        return Illust(
            id = entity.id,
            title = entity.title,
            type = entity.type.ifBlank { "illust" },
            caption = "",
            artistId = 0L,
            artistName = entity.artistName,
            artistAvatarUrl = null,
            squareImageUrl = "",
            mediumImageUrl = entity.imageUrl,
            imageUrl = entity.imageUrl,
            originalImageUrl = null,
            mediumImagePages = emptyList(),
            imagePages = emptyList(),
            originalImagePages = emptyList(),
            tags = emptyList(),
            pageCount = entity.pageCount,
            isBookmarked = false,
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
        return runCatching {
            value?.let { enumValueOf<T>(it) }
        }.getOrNull() ?: default
    }

    private fun encodeStringList(values: List<String>): String {
        return JSONArray().apply {
            values.forEach { put(it) }
        }.toString()
    }

    private fun decodeStringList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            List(array.length()) { index -> array.optString(index) }
                .filter { it.isNotBlank() }
        }.getOrElse {
            decodeLegacyStringList(value)
        }
    }

    private fun decodeLegacyStringList(value: String?): List<String> {
        return value.orEmpty()
            .split(HISTORY_SEPARATOR)
            .filter { it.isNotBlank() }
    }

    private fun encodeLongList(values: List<Long>): String {
        return JSONArray().apply {
            values.forEach { put(it) }
        }.toString()
    }

    private fun decodeLongList(value: String?): List<Long> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            List(array.length()) { index -> array.optLong(index, 0L) }
                .filter { it > 0L }
        }.getOrElse {
            value.split(",").mapNotNull { it.toLongOrNull() }
        }
    }

    private fun decodeHistoryIllusts(value: String?): List<Illust> {
        if (value.isNullOrBlank()) return emptyList()
        return if (value.trimStart().startsWith("[")) {
            runCatching {
                val array = JSONArray(value)
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    historyIllustFromJson(item)
                }.filterNotNull()
            }.getOrDefault(emptyList())
        } else {
            value.split(HISTORY_SEPARATOR)
                .filter { it.isNotBlank() }
                .mapNotNull(::decodeLegacyHistoryIllust)
        }
    }

    private fun historyIllustFromJson(item: JSONObject): Illust? {
        val id = item.optLong("id", 0L).takeIf { it > 0L } ?: return null
        val imageUrl = item.optString("imageUrl")
        return Illust(
            id = id,
            title = item.optString("title"),
            type = item.optString("type").ifBlank { "illust" },
            caption = "",
            artistId = 0L,
            artistName = item.optString("artistName"),
            artistAvatarUrl = null,
            squareImageUrl = "",
            mediumImageUrl = imageUrl,
            imageUrl = imageUrl,
            originalImageUrl = null,
            mediumImagePages = emptyList(),
            imagePages = emptyList(),
            originalImagePages = emptyList(),
            tags = emptyList(),
            pageCount = item.optInt("pageCount", 1),
            isBookmarked = false,
        )
    }

    private fun decodeLegacyHistoryIllust(value: String): Illust? {
        val parts = value.split(FIELD_SEPARATOR)
        if (parts.size < 6) return null
        val imageUrl = parts[3].decodeBase64Field()
        return Illust(
            id = parts[0].toLongOrNull() ?: return null,
            title = parts[1].decodeBase64Field(),
            type = parts[5].decodeBase64Field().ifBlank { "illust" },
            caption = "",
            artistId = 0L,
            artistName = parts[2].decodeBase64Field(),
            artistAvatarUrl = null,
            squareImageUrl = "",
            mediumImageUrl = imageUrl,
            imageUrl = imageUrl,
            originalImageUrl = null,
            mediumImagePages = emptyList(),
            imagePages = emptyList(),
            originalImagePages = emptyList(),
            tags = emptyList(),
            pageCount = parts[4].toIntOrNull() ?: 1,
            isBookmarked = false,
        )
    }

    private fun encodeAccountTokens(accounts: List<StoredAccount>): String {
        return JSONObject().apply {
            accounts.forEach { account ->
                put(account.userId.toString(), account.refreshToken)
            }
        }.toString()
    }

    private fun decodeAccountTokens(value: String): Map<Long, String> {
        if (value.isBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(value)
            json.keys().asSequence().mapNotNull { key ->
                val userId = key.toLongOrNull() ?: return@mapNotNull null
                val token = json.optString(key)
                if (token.isBlank()) null else userId to token
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun decodeAccounts(value: String): List<StoredAccount> {
        if (value.isBlank()) return emptyList()
        return if (value.trimStart().startsWith("[")) {
            runCatching {
                val array = JSONArray(value)
                List(array.length()) { index ->
                    val item = array.optJSONObject(index) ?: return@List null
                    StoredAccount(
                        name = item.optString("name"),
                        account = item.optString("account"),
                        profileImageUrl = item.optString("profileImageUrl").takeIf { it.isNotBlank() && it != "null" },
                        refreshToken = item.optString("refreshToken"),
                        userId = item.optLong("userId", 0L).takeIf { it > 0L } ?: return@List null,
                    )
                }.filterNotNull()
            }.getOrDefault(emptyList())
        } else {
            value.split(HISTORY_SEPARATOR).mapNotNull { entry ->
                val parts = entry.split(FIELD_SEPARATOR)
                if (parts.size < 5) return@mapNotNull null
                StoredAccount(
                    name = parts[0].decodeBase64Field(),
                    account = parts[1].decodeBase64Field(),
                    profileImageUrl = parts[2].decodeBase64Field().takeIf { it.isNotBlank() },
                    refreshToken = parts[3].decodeBase64Field(),
                    userId = parts[4].toLongOrNull() ?: return@mapNotNull null,
                )
            }
        }
    }

    private fun String.decodeBase64Field(): String {
        return runCatching {
            String(android.util.Base64.decode(this, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP))
        }.getOrDefault("")
    }

    companion object {
        private const val LEGACY_PREFS_NAME = "illustia"
        private const val SECURE_PREFS_NAME = "illustia_secure"
        private const val DATASTORE_NAME = "illustia_settings.preferences_pb"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
        private const val KEY_ACCOUNTS = "accounts"
        private const val KEY_ACCOUNT_TOKENS = "accountTokens"
        private const val KEY_ACTIVE_ACCOUNT_INDEX = "activeAccountIndex"
        private const val KEY_PIN_HASH = "pinHash"
        private const val KEY_PIN_SALT = "pinSalt"
        private const val KEY_UNLOCK_CODE_HASH = "unlockCodeHash"
        private const val KEY_UNLOCK_CODE_SALT = "unlockCodeSalt"
        private const val PBKDF2_ITERATIONS = 100_000
        private const val PBKDF2_KEY_LENGTH = 256
        private const val KEY_BOOKMARK_USER_ID = "bookmarkUserId"
        private const val KEY_ALLOW_R18 = "allowR18"
        private const val KEY_HIGH_QUALITY = "highQualityImages"
        private const val KEY_BOOKMARK_RESTRICT = "bookmarkRestrict"
        private const val KEY_SEARCH_SORT = "searchSort"
        private const val KEY_SEARCH_TARGET = "searchTarget"
        private const val KEY_SEARCH_DURATION = "searchDuration"
        private const val KEY_SEARCH_BOOKMARK_FILTER = "searchBookmarkFilter"
        private const val KEY_SEARCH_USERS_ENABLED = "searchUsersEnabled"
        private const val KEY_SEARCH_HISTORY = "searchHistory"
        private const val KEY_FAVORITE_TAGS = "favoriteTags"
        private const val KEY_VIEW_HISTORY = "viewHistory"
        private const val KEY_APP_LANGUAGE = "appLanguage"
        private const val KEY_APP_FONT = "appFont"
        private const val KEY_THEME_MODE = "themeMode"
        private const val KEY_USE_DYNAMIC_COLOR = "useDynamicColor"
        private const val KEY_SEED_COLOR = "seedColor"
        private const val KEY_ONBOARDING_SETUP_COMPLETED = "onboardingSetupCompleted"
        private const val KEY_SMOOTH_TRANSITIONS = "smoothTransitions"
        private const val KEY_HAPTIC_MODE = "hapticMode"
        private const val KEY_PREFETCH_IMAGES = "prefetchImages"
        private const val KEY_PIXIV_IMAGE_PROXY_BASE_URL = "pixivImageProxyBaseUrl"
        private const val KEY_OFFLINE_WIFI_ONLY = "offlineWifiOnly"
        private const val KEY_OFFLINE_STORAGE_LIMIT_BYTES = "offlineStorageLimitBytes"
        private const val CURRENT_SETTINGS_VERSION = 7
        private const val HISTORY_SEPARATOR = '\u001F'
        private const val FIELD_SEPARATOR = '\u001E'
        private const val MAX_SEARCH_HISTORY = 6
        private const val MAX_VIEW_HISTORY = 48
        private const val DEFAULT_OFFLINE_STORAGE_LIMIT_BYTES = 5L * 1024 * 1024 * 1024

        private val SETTINGS_VERSION = intPreferencesKey("settings_version")
        private val BOOKMARK_USER_ID = longPreferencesKey(KEY_BOOKMARK_USER_ID)
        private val APP_LANGUAGE = stringPreferencesKey(KEY_APP_LANGUAGE)
        private val APP_FONT = stringPreferencesKey(KEY_APP_FONT)
        private val THEME_MODE = stringPreferencesKey(KEY_THEME_MODE)
        private val USE_DYNAMIC_COLOR = booleanPreferencesKey(KEY_USE_DYNAMIC_COLOR)
        private val SEED_COLOR = longPreferencesKey(KEY_SEED_COLOR)
        private val ONBOARDING_SETUP_COMPLETED = booleanPreferencesKey(KEY_ONBOARDING_SETUP_COMPLETED)
        private val ALLOW_R18 = booleanPreferencesKey(KEY_ALLOW_R18)
        private val HIGH_QUALITY_IMAGES = booleanPreferencesKey(KEY_HIGH_QUALITY)
        private val BOOKMARK_RESTRICT = stringPreferencesKey(KEY_BOOKMARK_RESTRICT)
        private val SEARCH_SORT = stringPreferencesKey(KEY_SEARCH_SORT)
        private val SEARCH_TARGET = stringPreferencesKey(KEY_SEARCH_TARGET)
        private val SEARCH_DURATION = stringPreferencesKey(KEY_SEARCH_DURATION)
        private val SEARCH_BOOKMARK_FILTER = stringPreferencesKey(KEY_SEARCH_BOOKMARK_FILTER)
        private val SEARCH_USERS_ENABLED = booleanPreferencesKey(KEY_SEARCH_USERS_ENABLED)
        private val SEARCH_HISTORY_JSON = stringPreferencesKey(KEY_SEARCH_HISTORY)
        private val FAVORITE_TAGS_JSON = stringPreferencesKey(KEY_FAVORITE_TAGS)
        private val VIEW_HISTORY_JSON = stringPreferencesKey(KEY_VIEW_HISTORY)
        private val SAVE_VIEW_HISTORY = booleanPreferencesKey("saveViewHistory")
        private val SAVE_SEARCH_HISTORY = booleanPreferencesKey("saveSearchHistory")
        private val APP_LOCK_ENABLED = booleanPreferencesKey("appLockEnabled")
        private val APP_LOCK_TIMING = stringPreferencesKey("appLockTiming")
        private val BIOMETRIC_ENABLED = booleanPreferencesKey("biometricEnabled")
        private val APP_LOCK_FAIL_COUNT = intPreferencesKey("appLockFailCount")
        private val APP_LOCK_COOLDOWN_UNTIL = longPreferencesKey("appLockCooldownUntil")
        private val SMOOTH_TRANSITIONS = booleanPreferencesKey(KEY_SMOOTH_TRANSITIONS)
        private val HAPTIC_MODE = stringPreferencesKey(KEY_HAPTIC_MODE)
        private val PREFETCH_IMAGES = booleanPreferencesKey(KEY_PREFETCH_IMAGES)
        private val NOTCH_OPTIMIZATION = booleanPreferencesKey("notchOptimization")
        private val CONFIRM_ON_LONG_PRESS_SAVE = booleanPreferencesKey("confirmOnLongPressSave")
        private val DOUBLE_BACK_TO_EXIT = booleanPreferencesKey("doubleBackToExit")
        private val SWIPE_TO_SWITCH_WORKS = booleanPreferencesKey("swipeToSwitchWorks")
        private val SECURE_WINDOW = booleanPreferencesKey("secureWindow")
        private val AMOLED_MODE = booleanPreferencesKey("amoledMode")
        private val SKIP_CONFIRM_ON_DETAIL_SAVE = booleanPreferencesKey("skipConfirmOnDetailSave")
        private val SHOW_AI_BADGE = booleanPreferencesKey("showAiBadge")
        private val FOLLOW_ON_LIKE = booleanPreferencesKey("followOnLike")
        private val PRIVATE_BOOKMARK_DEFAULT = booleanPreferencesKey("privateBookmarkDefault")
        private val AUTO_DOWNLOAD_ON_BOOKMARK = booleanPreferencesKey("autoDownloadOnBookmark")
        private val AUTO_BOOKMARK_ON_DOWNLOAD = booleanPreferencesKey("autoBookmarkOnDownload")
        private val DOWNLOAD_FOLDER_BY_ARTIST = booleanPreferencesKey("downloadFolderByArtist")
        private val DOWNLOAD_FOLDER_BY_WORK = booleanPreferencesKey("downloadFolderByWork")
        private val AUTO_TAG_ON_BOOKMARK = booleanPreferencesKey("autoTagOnBookmark")
        private val SIMULTANEOUS_DOWNLOADS = intPreferencesKey("simultaneousDownloads")
        private val FEED_PREVIEW_QUALITY = stringPreferencesKey("feedPreviewQuality")
        private val ILLUST_DETAIL_QUALITY = stringPreferencesKey("illustDetailQuality")
        private val MANGA_DETAIL_QUALITY = stringPreferencesKey("mangaDetailQuality")
        private val FULLSCREEN_QUALITY = stringPreferencesKey("fullscreenQuality")
        private val VIEWER_THUMBNAILS_IN_TOOLBAR = booleanPreferencesKey("viewerThumbnailsInToolbar")
        private val STARTUP_SCREEN = stringPreferencesKey("startupScreen")
        private val OFFLINE_WIFI_ONLY = booleanPreferencesKey(KEY_OFFLINE_WIFI_ONLY)
        private val OFFLINE_STORAGE_LIMIT_BYTES = longPreferencesKey(KEY_OFFLINE_STORAGE_LIMIT_BYTES)
        private val USER_PROFILE_BOTTOM_SHEET_ENABLED = booleanPreferencesKey("userProfileBottomSheetEnabled")
        private val VERTICAL_COLUMN_COUNT = intPreferencesKey("verticalColumnCount")
        private val HORIZONTAL_COLUMN_COUNT = intPreferencesKey("horizontalColumnCount")
        private val PIXIV_IMAGE_PROXY_BASE_URL = stringPreferencesKey(KEY_PIXIV_IMAGE_PROXY_BASE_URL)
        private val MUTED_ILLUSTS_JSON = stringPreferencesKey("mutedIllusts")
        private val MUTED_USERS_JSON = stringPreferencesKey("mutedUsers")
        private val MUTED_TAGS_JSON = stringPreferencesKey("mutedTags")
        private val ACTIVE_ACCOUNT_INDEX = intPreferencesKey(KEY_ACTIVE_ACCOUNT_INDEX)
        private val PRIVACY_MODE_ENABLED = booleanPreferencesKey("privacyModeEnabled")
        private val PRIVACY_MODE_AUTO_LOCK_TIMING = stringPreferencesKey("privacyModeAutoLockTiming")
        private val HIDE_RECENTS = booleanPreferencesKey("hideRecents")
        private val HIDE_NOTIFICATIONS = booleanPreferencesKey("hideNotifications")
        private val DUMMY_APP_NAME = stringPreferencesKey("dummyAppName")
        private val DUMMY_ICON_VARIANT = stringPreferencesKey("dummyIconVariant")

        @Volatile
        private var sharedDataStore: DataStore<Preferences>? = null

        private fun dataStoreFor(context: Context): DataStore<Preferences> {
            return sharedDataStore ?: synchronized(this) {
                sharedDataStore ?: PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                    produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
                ).also { sharedDataStore = it }
            }
        }

        private fun createEncryptedPreferences(context: Context): SharedPreferences? {
            return runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.getOrNull()
        }

        fun readStoredAppLanguage(context: Context): String {
            val appContext = context.applicationContext
            return runBlocking(Dispatchers.IO) {
                dataStoreFor(appContext).data
                    .catch { error ->
                        if (error is IOException) emit(emptyPreferences()) else throw error
                    }
                    .first()[APP_LANGUAGE]
            } ?: appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_APP_LANGUAGE, "system")
            ?: "system"
        }

        /**
         * プライバシーモードの ON/OFF を同期的に読み取る。
         * スプラッシュ画面を表示する前にテーマを切り替える用途。
         */
        fun isPrivacyModeEnabledSync(context: Context): Boolean {
            val appContext = context.applicationContext
            return runCatching {
                runBlocking(Dispatchers.IO) {
                    dataStoreFor(appContext).data
                        .catch { error ->
                            if (error is IOException) emit(emptyPreferences()) else throw error
                        }
                        .first()[PRIVACY_MODE_ENABLED]
                }
            }.getOrNull() ?: false
        }
    }
}
