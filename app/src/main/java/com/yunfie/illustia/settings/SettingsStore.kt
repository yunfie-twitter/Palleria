package com.yunfie.illustia.settings

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yunfie.illustia.data.computeAccountKey
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.platform.PlatformCapabilities
import com.yunfie.illustia.settings.db.IllustiaDatabase
import com.yunfie.illustia.settings.db.SavedIllustEntity
import com.yunfie.illustia.settings.db.SavedIllustPageEntity
import com.yunfie.illustia.settings.db.SavedIllustWithPages
import com.yunfie.illustia.settings.db.SettingsDao
import com.yunfie.illustia.settings.store.AUTO_DOWNLOAD_UPDATES
import com.yunfie.illustia.settings.store.AUTO_LOAD_MORE
import com.yunfie.illustia.settings.store.AUTO_LOAD_MORE_SPEC_MIGRATED
import com.yunfie.illustia.settings.store.BOOKMARK_USER_ID
import com.yunfie.illustia.settings.store.CHECK_UPDATES_IN_BACKGROUND
import com.yunfie.illustia.settings.store.CURRENT_SETTINGS_VERSION
import com.yunfie.illustia.settings.store.CollectionSeparatedDataStore
import com.yunfie.illustia.settings.store.DATASTORE_NAME
import com.yunfie.illustia.settings.store.KEY_ACCOUNTS
import com.yunfie.illustia.settings.store.KEY_ACCOUNT_TOKENS
import com.yunfie.illustia.settings.store.KEY_APP_LANGUAGE
import com.yunfie.illustia.settings.store.KEY_REFRESH_TOKEN
import com.yunfie.illustia.settings.store.KEY_STARTUP_ACCOUNT_HASH
import com.yunfie.illustia.settings.store.KEY_STARTUP_HAS_PIN
import com.yunfie.illustia.settings.store.KEY_STARTUP_IS_LOGGED_IN
import com.yunfie.illustia.settings.store.KEY_WIDE_COLOR_GAMUT
import com.yunfie.illustia.settings.store.LEGACY_PREFS_NAME
import com.yunfie.illustia.settings.store.MUTED_ILLUSTS_JSON
import com.yunfie.illustia.settings.store.MUTED_TAGS_JSON
import com.yunfie.illustia.settings.store.MUTED_USERS_JSON
import com.yunfie.illustia.settings.store.NOTIFY_NEW_VERSION
import com.yunfie.illustia.settings.store.SECURE_PREFS_NAME
import com.yunfie.illustia.settings.store.SEND_TELEMETRY
import com.yunfie.illustia.settings.store.SETTINGS_VERSION
import com.yunfie.illustia.settings.store.STARTUP_LOGGED_IN_TOKEN
import com.yunfie.illustia.settings.store.decodeAccountTokens
import com.yunfie.illustia.settings.store.decodeAccounts
import com.yunfie.illustia.settings.store.decodeLongList
import com.yunfie.illustia.settings.store.decodeStringList
import com.yunfie.illustia.settings.store.illustFromEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import com.yunfie.illustia.settings.store.clearPinHash as clearPinHashImpl
import com.yunfie.illustia.settings.store.clearSensitiveSettings as clearSensitiveSettingsImpl
import com.yunfie.illustia.settings.store.clearUnlockCodeHash as clearUnlockCodeHashImpl
import com.yunfie.illustia.settings.store.hasPinSet as hasPinSetImpl
import com.yunfie.illustia.settings.store.hasUnlockCodeSet as hasUnlockCodeSetImpl
import com.yunfie.illustia.settings.store.isValidUnlockCode as isValidUnlockCodeImpl
import com.yunfie.illustia.settings.store.migrateSettingsIfNeeded as migrateSettingsIfNeededImpl
import com.yunfie.illustia.settings.store.readAppSettings as readAppSettingsImpl
import com.yunfie.illustia.settings.store.readStartupAppSettings as readStartupAppSettingsImpl
import com.yunfie.illustia.settings.store.savePinHash as savePinHashImpl
import com.yunfie.illustia.settings.store.saveUnlockCodeHash as saveUnlockCodeHashImpl
import com.yunfie.illustia.settings.store.savedIllustStorageBytes as savedIllustStorageBytesImpl
import com.yunfie.illustia.settings.store.verifyPinHash as verifyPinHashImpl
import com.yunfie.illustia.settings.store.verifyUnlockCodeHash as verifyUnlockCodeHashImpl
import com.yunfie.illustia.settings.store.writeAppSettings as writeAppSettingsImpl

internal data class StartupMaintenanceSettings(
    val sendTelemetry: Boolean,
    val checkUpdatesInBackground: Boolean,
    val notifyNewVersion: Boolean,
    val autoDownloadUpdates: Boolean,
)

internal fun hasNewCredentialValues(
    current: AppSettings,
    proposed: AppSettings,
): Boolean {
    val currentTokens = current.accounts.associate { it.userId to it.refreshToken }
    val hasRefreshTokenUpdate =
        proposed.refreshToken.isNotBlank() &&
            proposed.refreshToken != STARTUP_LOGGED_IN_TOKEN &&
            proposed.refreshToken != current.refreshToken
    val hasDiscordTokenUpdate = proposed.discordToken.isNotBlank() && proposed.discordToken != current.discordToken
    val hasAccountTokenUpdate =
        proposed.accounts.any { account ->
            account.refreshToken.isNotBlank() && currentTokens[account.userId] != account.refreshToken
        }
    return hasRefreshTokenUpdate || hasDiscordTokenUpdate || hasAccountTokenUpdate
}

class SettingsStore internal constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val legacyPreferences by lazy { appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE) }
    private val encryptedPreferences by lazy { Companion.createEncryptedPreferences(appContext) }
    private val sensitivePreferences by lazy { encryptedPreferences ?: legacyPreferences }
    private val dataStore by lazy { Companion.dataStoreFor(appContext) }
    private val database by lazy { IllustiaDatabase.getInstance(appContext) }
    private val dao by lazy { database.settingsDao() }

    init {
        // Migration will be executed on first suspend read/write off main thread
    }

    private val startupCacheMutex = Mutex()

    @Volatile
    private var cachedStartupSettings: AppSettings? = null

    suspend fun read(viewHistoryLimit: Int? = null): AppSettings {
        ensureMigrated()
        return readAppSettingsImpl(dataStore, sensitivePreferences, dao, viewHistoryLimit)
    }

    /** Credentials only: no Room tables or history decoding on the request path. */
    internal suspend fun readAuth(): AuthSettings =
        withContext(Dispatchers.IO) {
            val primary = startupDataStoreFor(appContext).data.first()
            if ((primary[SETTINGS_VERSION] ?: 0) <
                CURRENT_SETTINGS_VERSION
            ) {
                ensureMigrated()
            }
            if (legacyPreferences.contains(KEY_REFRESH_TOKEN)) {
                encryptedPreferences?.let { secure ->
                    persistenceMutex.withLock {
                        com.yunfie.illustia.settings.store
                            .migrateFallbackCredentials(secure, legacyPreferences)
                    }
                }
            }
            val token = sensitivePreferences.getString(KEY_REFRESH_TOKEN, "").orEmpty()
            AuthSettings(token, readStartup().pixivNetworkMode, encryptedPreferences?.let { readPersistedSession(it, token) })
        }

    internal suspend fun persistAuth(session: com.yunfie.illustia.models.PixivSession) =
        withContext(Dispatchers.IO) {
            persistenceMutex.withLock {
                val securePreferences =
                    encryptedPreferences
                        ?: error("Secure credential storage is unavailable; login credentials were not saved.")
                val editor = securePreferences.edit().putString(KEY_REFRESH_TOKEN, session.refreshToken)
                val tokens = org.json.JSONObject()
                com.yunfie.illustia.settings.store
                    .decodeAccountTokens(
                        securePreferences.getString(KEY_ACCOUNT_TOKENS, "").orEmpty(),
                    ).forEach { (userId, token) -> tokens.put(userId.toString(), token) }
                session.userId?.let { tokens.put(it.toString(), session.refreshToken) }
                editor.putString(KEY_ACCOUNT_TOKENS, tokens.toString())
                editor.putString(KEY_CACHED_SESSION, encodePersistedSession(session))
                check(editor.commit()) { "Could not persist OAuth credentials" }
                startupDataStoreFor(appContext).edit { preferences ->
                    session.userId?.let { preferences[BOOKMARK_USER_ID] = it }
                }
                legacyPreferences
                    .edit()
                    .putBoolean(KEY_STARTUP_IS_LOGGED_IN, true)
                    .putString(KEY_STARTUP_ACCOUNT_HASH, computeAccountKey(session.refreshToken))
                    .apply()
                cachedStartupSettings = null
            }
        }

    internal suspend fun readFeedSettings(): AppSettings =
        withContext(Dispatchers.IO) {
            // Read current filters, without opening Room or decoding account/history collections.
            val preferences = dataStore.data.first()
            readStartup().copy(
                mutedIllusts =
                    decodeLongList(
                        preferences[MUTED_ILLUSTS_JSON],
                    ),
                mutedUsers =
                    decodeLongList(
                        preferences[MUTED_USERS_JSON],
                    ),
                mutedTags =
                    decodeStringList(
                        preferences[MUTED_TAGS_JSON],
                    ),
            )
        }

    internal fun homeCacheDirectory(): File = File(appContext.cacheDir, "home-feed")

    suspend fun readStartup(): AppSettings =
        withContext(Dispatchers.IO) {
            cachedStartupSettings?.let { return@withContext it }
            startupCacheMutex.withLock {
                cachedStartupSettings?.let { return@withLock it }
                val isLoggedIn = isStartupLoggedIn()
                val result = readStartupAppSettingsImpl(startupDataStoreFor(appContext), isLoggedIn = isLoggedIn)
                cachedStartupSettings = result
                result
            }
        }

    internal suspend fun readStartupMaintenanceSettings(): StartupMaintenanceSettings {
        ensureMigrated()
        return withContext(Dispatchers.IO) {
            val preferences = startupDataStoreFor(appContext).data.first()
            StartupMaintenanceSettings(
                sendTelemetry = preferences[SEND_TELEMETRY] ?: false,
                checkUpdatesInBackground = preferences[CHECK_UPDATES_IN_BACKGROUND] ?: true,
                notifyNewVersion = preferences[NOTIFY_NEW_VERSION] ?: true,
                autoDownloadUpdates = preferences[AUTO_DOWNLOAD_UPDATES] ?: false,
            )
        }
    }

    internal suspend fun readAccountsForStartupMaintenance(): List<StoredAccount> {
        ensureMigrated()
        return withContext(Dispatchers.IO) {
            val tokensByUserId =
                sensitivePreferences.getString(KEY_ACCOUNT_TOKENS, null)?.let(::decodeAccountTokens).orEmpty()
            val fallbackAccounts =
                sensitivePreferences.getString(KEY_ACCOUNTS, null)?.let(::decodeAccounts).orEmpty()
            val fallbackTokens = fallbackAccounts.associate { it.userId to it.refreshToken }
            val roomAccounts = dao.getAccounts()
            if (roomAccounts.isEmpty()) {
                fallbackAccounts
            } else {
                roomAccounts.mapNotNull { account ->
                    val token = tokensByUserId[account.userId] ?: fallbackTokens[account.userId]
                    if (token.isNullOrBlank()) return@mapNotNull null
                    StoredAccount(
                        name = account.name.orEmpty(),
                        account = account.account.orEmpty(),
                        profileImageUrl = account.profileImageUrl,
                        refreshToken = token,
                        userId = account.userId,
                    )
                }
            }
        }
    }

    private fun isStartupLoggedIn(): Boolean {
        if (legacyPreferences.contains(KEY_STARTUP_IS_LOGGED_IN)) {
            return legacyPreferences.getBoolean(KEY_STARTUP_IS_LOGGED_IN, false)
        }
        val loggedIn = sensitivePreferences.getString(KEY_REFRESH_TOKEN, "").orEmpty().isNotBlank()
        legacyPreferences.edit().putBoolean(KEY_STARTUP_IS_LOGGED_IN, loggedIn).apply()
        return loggedIn
    }

    internal fun readStartupAccountHash(): String {
        val cached = legacyPreferences.getString(KEY_STARTUP_ACCOUNT_HASH, "").orEmpty()
        if (cached.isNotBlank()) return cached
        val token = sensitivePreferences.getString(KEY_REFRESH_TOKEN, "").orEmpty()
        return when {
            token.isNotBlank() && token != STARTUP_LOGGED_IN_TOKEN -> {
                val hash = computeAccountKey(token)
                legacyPreferences.edit().putString(KEY_STARTUP_ACCOUNT_HASH, hash).apply()
                hash
            }

            else -> {
                ""
            }
        }
    }

    suspend fun readStartupWithRecentHistory(limit: Int = STARTUP_VIEW_HISTORY_LIMIT): AppSettings {
        ensureMigrated()
        return readAppSettingsImpl(dataStore, sensitivePreferences, dao, limit)
    }

    suspend fun readFullViewHistory(): List<Illust> {
        ensureMigrated()
        return withContext(Dispatchers.IO) {
            dao.getViewHistory().map(::illustFromEntity)
        }
    }

    suspend fun write(
        settings: AppSettings,
        baseSettings: AppSettings? = null,
    ): AppSettings {
        ensureMigrated()
        val base = baseSettings ?: persistenceMutex.withLock { read() }
        val saved =
            persistenceMutex.withLock {
                val persisted = read()
                val rebased = resolveRebasedSettings(settings, base, persisted)
                check(encryptedPreferences != null || !hasNewCredentialValues(base, settings)) {
                    "Secure credential storage is unavailable; updated credentials were not saved."
                }
                writeAppSettingsImpl(dataStore, encryptedPreferences, database, dao, rebased, baseSettings = base)
                writeStartupMirror(rebased, persisted)
                rebased
            }
        cachedStartupSettings = null
        return saved
    }

    private fun resolveRebasedSettings(
        settings: AppSettings,
        base: AppSettings,
        persisted: AppSettings,
    ): AppSettings {
        val resolvedRefreshToken =
            if (settings.refreshToken == STARTUP_LOGGED_IN_TOKEN || settings.refreshToken == base.refreshToken) {
                persisted.refreshToken
            } else {
                settings.refreshToken
            }
        val resolvedDiscordToken =
            if (settings.discordToken.isBlank() && persisted.discordToken.isNotBlank()) {
                persisted.discordToken
            } else {
                settings.discordToken
            }
        val resolvedAccounts =
            if (settings.accounts == base.accounts || (settings.accounts.isEmpty() && persisted.accounts.isNotEmpty())) {
                persisted.accounts
            } else {
                settings.accounts.map { account ->
                    val previous = base.accounts.firstOrNull { it.userId == account.userId }
                    val current = persisted.accounts.firstOrNull { it.userId == account.userId }
                    if (account.refreshToken == previous?.refreshToken && current != null) {
                        account.copy(refreshToken = current.refreshToken)
                    } else {
                        account
                    }
                }
            }
        return settings
            .copy(
                refreshToken = resolvedRefreshToken,
                bookmarkUserId = if (settings.bookmarkUserId == base.bookmarkUserId) persisted.bookmarkUserId else settings.bookmarkUserId,
                discordToken = resolvedDiscordToken,
                accounts = resolvedAccounts,
            )
    }

    private fun writeStartupMirror(
        rebased: AppSettings,
        persisted: AppSettings,
    ) {
        val isLoggedIn =
            (rebased.refreshToken.isNotBlank() && rebased.refreshToken != STARTUP_LOGGED_IN_TOKEN) ||
                persisted.refreshToken.isNotBlank()
        val editor =
            legacyPreferences
                .edit()
                .putInt(KEY_IMAGE_CACHE_SIZE_MB, rebased.imageCacheSizeMb)
                .putString(KEY_APP_LANGUAGE, rebased.appLanguage)
                .putBoolean(KEY_STARTUP_PRIVACY_MODE, rebased.privacyModeEnabled)
                .putBoolean(KEY_STARTUP_IS_LOGGED_IN, isLoggedIn)
                .putBoolean(KEY_STARTUP_HAS_PIN, rebased.appLockEnabled && hasPinSet())
                .putBoolean(KEY_WIDE_COLOR_GAMUT, rebased.wideColorGamutEnabled)
        val token =
            when {
                rebased.refreshToken.isNotBlank() && rebased.refreshToken != STARTUP_LOGGED_IN_TOKEN -> rebased.refreshToken
                persisted.refreshToken.isNotBlank() && persisted.refreshToken != STARTUP_LOGGED_IN_TOKEN -> persisted.refreshToken
                else -> null
            }
        if (token != null) {
            editor.putString(KEY_STARTUP_ACCOUNT_HASH, computeAccountKey(token))
        } else if (!isLoggedIn) {
            editor.remove(KEY_STARTUP_ACCOUNT_HASH)
        }
        editor.apply()
    }

    suspend fun clearSensitive() =
        withContext(Dispatchers.IO) {
            encryptedPreferences?.edit()?.remove(KEY_CACHED_SESSION)?.commit()
            clearSensitiveSettingsImpl(dataStore, sensitivePreferences, legacyPreferences, database, dao)
            cachedStartupSettings = null
        }

    suspend fun getSavedIllusts() =
        withContext(Dispatchers.IO) {
            dao.getSavedIllusts()
        }

    suspend fun getSavedIllust(illustId: Long): SavedIllustWithPages? =
        withContext(Dispatchers.IO) {
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

    suspend fun deleteSavedIllust(illustId: Long) =
        withContext(Dispatchers.IO) {
            database.runInTransaction {
                dao.deleteSavedIllustPages(illustId)
                dao.deleteSavedIllust(illustId)
            }
        }

    fun savePinHash(pin: String) {
        savePinHashImpl(sensitivePreferences, pin)
        legacyPreferences.edit().putBoolean(KEY_STARTUP_HAS_PIN, true).apply()
    }

    suspend fun verifyPin(pin: String): Boolean = verifyPinHashImpl(sensitivePreferences, pin)

    fun hasPinSet(): Boolean {
        if (legacyPreferences.contains(KEY_STARTUP_HAS_PIN)) {
            return legacyPreferences.getBoolean(KEY_STARTUP_HAS_PIN, false)
        }
        val hasPin = hasPinSetImpl(sensitivePreferences)
        legacyPreferences.edit().putBoolean(KEY_STARTUP_HAS_PIN, hasPin).apply()
        return hasPin
    }

    fun clearPinHash() {
        clearPinHashImpl(sensitivePreferences)
        legacyPreferences.edit().putBoolean(KEY_STARTUP_HAS_PIN, false).apply()
    }

    fun saveUnlockCodeHash(code: String) {
        saveUnlockCodeHashImpl(sensitivePreferences, code)
    }

    suspend fun verifyUnlockCode(code: String): Boolean = verifyUnlockCodeHashImpl(sensitivePreferences, code)

    fun hasUnlockCodeSet(): Boolean = hasUnlockCodeSetImpl(sensitivePreferences)

    fun clearUnlockCodeHash() {
        clearUnlockCodeHashImpl(sensitivePreferences)
    }

    fun isValidUnlockCode(code: String): Boolean = isValidUnlockCodeImpl(code)

    suspend fun getSavedIllustStorageBytes(): Long =
        withContext(Dispatchers.IO) {
            savedIllustStorageBytesImpl(savedIllustDir())
        }

    fun savedIllustDir(): File = File(appContext.filesDir, "saved_illusts")

    internal suspend fun ensureMigrated() {
        if (migrationCompleted) return
        migrationMutex.withLock {
            if (migrationCompleted) return@withLock
            withContext(Dispatchers.IO) {
                migrateSettingsIfNeededImpl(
                    dataStore = dataStore,
                    encryptedPreferences = encryptedPreferences,
                    legacyPreferences = legacyPreferences,
                    databaseProvider = { database },
                    daoProvider = { dao },
                )
                val current = dataStore.data.first()
                if (current[AUTO_LOAD_MORE_SPEC_MIGRATED] != true) {
                    val isNormalOrHigher = !PlatformCapabilities.isLowSpecDevice(appContext)
                    dataStore.edit { prefs ->
                        if (isNormalOrHigher) {
                            prefs[AUTO_LOAD_MORE] = true
                        }
                        prefs[AUTO_LOAD_MORE_SPEC_MIGRATED] = true
                    }
                }
            }
            migrationCompleted = true
        }
    }

    companion object {
        private val persistenceMutex = Mutex()

        @Volatile
        private var sharedDataStore: DataStore<Preferences>? = null

        @Volatile
        private var sharedStartupDataStore: DataStore<Preferences>? = null

        @Volatile
        private var sharedEncryptedPreferences: SharedPreferences? = null

        @Volatile
        private var encryptedPreferencesInitialized = false

        @Volatile
        private var migrationCompleted = false
        private val encryptedPreferencesLock = Any()
        private val migrationMutex = Mutex()

        fun dataStoreFor(context: Context): DataStore<Preferences> =
            sharedDataStore ?: synchronized(this) {
                sharedDataStore ?: CollectionSeparatedDataStore(
                    startupDataStoreFor(context),
                    createPreferencesStore(context, "illustia_collections"),
                ).also { sharedDataStore = it }
            }

        private fun startupDataStoreFor(context: Context): DataStore<Preferences> =
            sharedStartupDataStore ?: synchronized(this) {
                sharedStartupDataStore ?: createPreferencesStore(context, DATASTORE_NAME)
                    .also { sharedStartupDataStore = it }
            }

        private fun createPreferencesStore(
            context: Context,
            name: String,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO),
                produceFile = { context.preferencesDataStoreFile(name) },
            )

        fun createEncryptedPreferences(context: Context): SharedPreferences? {
            if (encryptedPreferencesInitialized) return sharedEncryptedPreferences
            return synchronized(encryptedPreferencesLock) {
                if (!encryptedPreferencesInitialized) {
                    sharedEncryptedPreferences =
                        runCatching {
                            val appContext = context.applicationContext
                            val masterKey =
                                MasterKey
                                    .Builder(appContext)
                                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                                    .build()
                            EncryptedSharedPreferences.create(
                                appContext,
                                SECURE_PREFS_NAME,
                                masterKey,
                                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                            )
                        }.getOrNull()
                    encryptedPreferencesInitialized = sharedEncryptedPreferences != null
                }
                sharedEncryptedPreferences
            }
        }

        @Volatile
        private var cachedPrivacyMode: Boolean? = null

        @Volatile
        private var cachedAppLanguage: String? = null

        fun readStoredAppLanguage(context: Context): String {
            cachedAppLanguage?.let { return it }
            val appContext = context.applicationContext
            val lang =
                appContext
                    .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_APP_LANGUAGE, "system")
                    ?: "system"
            cachedAppLanguage = lang
            return lang
        }

        fun isPrivacyModeEnabledSync(context: Context): Boolean {
            cachedPrivacyMode?.let { return it }
            val appContext = context.applicationContext
            val startupPreferences =
                appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val enabled =
                if (startupPreferences.contains(KEY_STARTUP_PRIVACY_MODE)) {
                    startupPreferences.getBoolean(KEY_STARTUP_PRIVACY_MODE, false)
                } else {
                    // One-time compatibility path for installs created before the startup mirror.
                    val legacyEnabled =
                        runCatching {
                            appContext.packageManager.getComponentEnabledSetting(
                                ComponentName(appContext, DUMMY_LAUNCHER_ALIAS),
                            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        }.getOrDefault(false)
                    startupPreferences
                        .edit()
                        .putBoolean(KEY_STARTUP_PRIVACY_MODE, legacyEnabled)
                        .apply()
                    legacyEnabled
                }
            cachedPrivacyMode = enabled
            return enabled
        }

        fun updatePrivacyModeCache(enabled: Boolean) {
            cachedPrivacyMode = enabled
        }

        fun updateAppLanguageCache(language: String) {
            cachedAppLanguage = language
        }

        private const val KEY_IMAGE_CACHE_SIZE_MB = "startup_image_cache_size_mb"
        private const val KEY_STARTUP_PRIVACY_MODE = "startup_privacy_mode_enabled"
        private const val DUMMY_LAUNCHER_ALIAS = "com.yunfie.illustia.MainActivityDummy"
        const val STARTUP_VIEW_HISTORY_LIMIT = 16
    }
}
