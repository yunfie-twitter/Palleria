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
import com.yunfie.illustia.IllustiaApplication
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.pallasync.PallaSyncEventWriter
import com.yunfie.illustia.pallasync.PalleriaSyncCoordinator
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.pallasync.buildSettingsSyncEvents
import com.yunfie.illustia.settings.db.IllustiaDatabase
import com.yunfie.illustia.settings.db.SavedIllustEntity
import com.yunfie.illustia.settings.db.SavedIllustPageEntity
import com.yunfie.illustia.settings.db.SavedIllustWithPages
import com.yunfie.illustia.settings.db.SettingsDao
import com.yunfie.illustia.settings.store.DATASTORE_NAME
import com.yunfie.illustia.settings.store.KEY_APP_LANGUAGE
import com.yunfie.illustia.settings.store.LEGACY_PREFS_NAME
import com.yunfie.illustia.settings.store.PALLA_SYNC_ENABLED
import com.yunfie.illustia.settings.store.PALLA_SYNC_SERVER_URL
import com.yunfie.illustia.settings.store.illustFromEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong
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
import com.yunfie.illustia.settings.store.writeSyncedCollections as writeSyncedCollectionsImpl

internal data class SettingsSyncUpdate(
    val revision: Long,
    val collections: SyncedCollectionsSnapshot,
)

internal data class PallaSyncEnabledUpdate(
    val revision: Long,
    val enabled: Boolean,
)

private fun defaultSyncEventWriter(context: Context): PallaSyncEventWriter {
    val appContext = context.applicationContext
    return (appContext as? IllustiaApplication)?.pallaSyncCoordinator
        ?: PalleriaSyncCoordinator(context = appContext)
}

class SettingsStore internal constructor(
    context: Context,
    private val syncEventWriter: PallaSyncEventWriter,
) {
    constructor(context: Context) : this(context, defaultSyncEventWriter(context))

    private val appContext = context.applicationContext
    private val legacyPreferences = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val encryptedPreferences = Companion.createEncryptedPreferences(appContext)
    private val sensitivePreferences = encryptedPreferences ?: legacyPreferences
    private val dataStore = Companion.dataStoreFor(appContext)
    private val database = IllustiaDatabase.getInstance(appContext)
    private val dao = database.settingsDao()

    init {
        // Migration will be executed on first suspend read/write off main thread
    }

    suspend fun read(viewHistoryLimit: Int? = null): AppSettings {
        ensureMigrated()
        return readAppSettingsImpl(dataStore, sensitivePreferences, dao, viewHistoryLimit)
    }

    suspend fun readStartup(): AppSettings {
        ensureMigrated()
        return readStartupAppSettingsImpl(dataStore, sensitivePreferences)
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
        val events =
            if (base.pallaSyncEnabled && settings.pallaSyncEnabled) {
                buildSettingsSyncEvents(base, settings)
            } else {
                emptyList()
            }

        suspend fun persistRebased(): AppSettings =
            persistenceMutex.withLock {
                val persisted = read()
                val rebasedCollections =
                    rebaseSyncedCollections(
                        base = base.syncedCollections(),
                        intended = settings.syncedCollections(),
                        persisted = persisted.syncedCollections(),
                    )
                // A queued unrelated settings write must not re-enable a chain that
                // the coordinator disabled after an authoritative 410 response.
                val enabled =
                    if (base.pallaSyncEnabled == settings.pallaSyncEnabled) {
                        persisted.pallaSyncEnabled
                    } else {
                        settings.pallaSyncEnabled
                    }
                val serverUrl =
                    if (base.pallaSyncServerUrl == settings.pallaSyncServerUrl) {
                        persisted.pallaSyncServerUrl
                    } else {
                        settings.pallaSyncServerUrl
                    }
                val rebased =
                    settings
                        .copy(
                            pallaSyncEnabled = enabled,
                            pallaSyncServerUrl = serverUrl,
                        ).withSyncedCollections(rebasedCollections)
                writeAppSettingsImpl(dataStore, sensitivePreferences, database, dao, rebased, baseSettings = base)
                // These non-sensitive values are needed before the asynchronous authoritative
                // settings load completes. Keep a lightweight startup mirror off the DataStore path.
                legacyPreferences
                    .edit()
                    .putInt(KEY_IMAGE_CACHE_SIZE_MB, rebased.imageCacheSizeMb)
                    .putString(KEY_APP_LANGUAGE, rebased.appLanguage)
                    .putBoolean(KEY_STARTUP_PRIVACY_MODE, rebased.privacyModeEnabled)
                    .apply()

                rebased
            }

        return try {
            // The coordinator owns operationMutex first, then this callback takes
            // persistenceMutex. Incoming page apply uses the same lock order.
            persistAfterSyncEnqueue(events, syncEventWriter) { persistRebased() }
        } catch (error: CancellationException) {
            throw error
        } catch (expectedFailure: Exception) {
            PalleriaSyncManager.log("Failed to durably enqueue local settings changes: ${expectedFailure.message}")
            throw expectedFailure
        }
    }

    suspend fun writeFromSync(settings: AppSettings) {
        writeSyncedCollections(settings.syncedCollections())
    }

    internal suspend fun writeSyncedCollections(collections: SyncedCollectionsSnapshot) {
        updateSyncedCollections { collections }
    }

    internal suspend fun readSyncedCollections(): SyncedCollectionsSnapshot = persistenceMutex.withLock { read().syncedCollections() }

    internal suspend fun updateSyncedCollections(
        transform: (SyncedCollectionsSnapshot) -> SyncedCollectionsSnapshot,
    ): SyncedCollectionsSnapshot {
        persistenceMutex.withLock {
            val current = read().syncedCollections()
            val updated = transform(current)
            if (updated != current) {
                writeSyncedCollectionsImpl(dataStore, database, dao, updated)
                publishSyncUpdate(updated)
            }
            return updated
        }
    }

    internal suspend fun setPallaSyncEnabledFromCoordinator(enabled: Boolean) {
        persistenceMutex.withLock {
            dataStore.edit { preferences -> preferences[PALLA_SYNC_ENABLED] = enabled }
            _pallaSyncEnabledUpdates.value =
                PallaSyncEnabledUpdate(
                    revision = pallaSyncStateRevision.incrementAndGet(),
                    enabled = enabled,
                )
        }
    }

    internal suspend fun setPallaSyncServerUrlFromCoordinator(serverUrl: String) {
        persistenceMutex.withLock {
            dataStore.edit { preferences -> preferences[PALLA_SYNC_SERVER_URL] = serverUrl }
        }
    }

    suspend fun clearSensitive() =
        withContext(Dispatchers.IO) {
            clearSensitiveSettingsImpl(dataStore, sensitivePreferences, legacyPreferences, database, dao)
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
    }

    suspend fun verifyPin(pin: String): Boolean = verifyPinHashImpl(sensitivePreferences, pin)

    fun hasPinSet(): Boolean = hasPinSetImpl(sensitivePreferences)

    fun clearPinHash() {
        clearPinHashImpl(sensitivePreferences)
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

    private suspend fun ensureMigrated() {
        if (migrationCompleted) return
        migrationMutex.withLock {
            if (migrationCompleted) return@withLock
            withContext(Dispatchers.IO) {
                migrateSettingsIfNeededImpl(dataStore, encryptedPreferences, legacyPreferences, database, dao)
            }
            migrationCompleted = true
        }
    }

    companion object {
        private val syncRevision = AtomicLong(0L)
        private val _syncUpdates = MutableStateFlow<SettingsSyncUpdate?>(null)
        internal val syncUpdates: StateFlow<SettingsSyncUpdate?> = _syncUpdates.asStateFlow()
        private val pallaSyncStateRevision = AtomicLong(0L)
        private val persistenceMutex = Mutex()
        private val _pallaSyncEnabledUpdates = MutableStateFlow<PallaSyncEnabledUpdate?>(null)
        internal val pallaSyncEnabledUpdates: StateFlow<PallaSyncEnabledUpdate?> =
            _pallaSyncEnabledUpdates.asStateFlow()

        private fun publishSyncUpdate(collections: SyncedCollectionsSnapshot) {
            _syncUpdates.value =
                SettingsSyncUpdate(
                    revision = syncRevision.incrementAndGet(),
                    collections = collections,
                )
        }

        @Volatile
        private var sharedDataStore: DataStore<Preferences>? = null

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
                sharedDataStore ?: PreferenceDataStoreFactory
                    .create(
                        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO),
                        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
                    ).also { sharedDataStore = it }
            }

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
                                com.yunfie.illustia.settings.store.SECURE_PREFS_NAME,
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

        fun readStoredAppLanguage(context: Context): String {
            val appContext = context.applicationContext
            return appContext
                .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_APP_LANGUAGE, "system")
                ?: "system"
        }

        fun isPrivacyModeEnabledSync(context: Context): Boolean {
            val appContext = context.applicationContext
            val startupPreferences =
                appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            if (startupPreferences.contains(KEY_STARTUP_PRIVACY_MODE)) {
                return startupPreferences.getBoolean(KEY_STARTUP_PRIVACY_MODE, false)
            }

            // One-time compatibility path for installs created before the startup mirror.
            // The dummy launcher alias was already the persisted privacy-mode indicator.
            val enabled =
                runCatching {
                    appContext.packageManager.getComponentEnabledSetting(
                        ComponentName(appContext, DUMMY_LAUNCHER_ALIAS),
                    ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                }.getOrDefault(false)
            startupPreferences
                .edit()
                .putBoolean(KEY_STARTUP_PRIVACY_MODE, enabled)
                .apply()
            return enabled
        }

        fun readImageCacheSizeMbSync(context: Context): Int =
            context.applicationContext
                .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_IMAGE_CACHE_SIZE_MB, DEFAULT_IMAGE_CACHE_SIZE_MB)
                .coerceIn(MIN_IMAGE_CACHE_SIZE_MB, MAX_IMAGE_CACHE_SIZE_MB)

        private const val KEY_IMAGE_CACHE_SIZE_MB = "startup_image_cache_size_mb"
        private const val KEY_STARTUP_PRIVACY_MODE = "startup_privacy_mode_enabled"
        private const val DUMMY_LAUNCHER_ALIAS = "com.yunfie.illustia.MainActivityDummy"
        private const val DEFAULT_IMAGE_CACHE_SIZE_MB = 300
        private const val MIN_IMAGE_CACHE_SIZE_MB = 100
        private const val MAX_IMAGE_CACHE_SIZE_MB = 1000
        const val STARTUP_VIEW_HISTORY_LIMIT = 16
    }
}
