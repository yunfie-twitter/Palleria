package com.yunfie.illustia.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.yunfie.illustia.settings.db.IllustiaDatabase
import com.yunfie.illustia.settings.db.SettingsDao
import com.yunfie.illustia.settings.db.SavedIllustEntity
import com.yunfie.illustia.settings.db.SavedIllustPageEntity
import com.yunfie.illustia.settings.db.SavedIllustWithPages
import com.yunfie.illustia.settings.store.DATASTORE_NAME
import com.yunfie.illustia.settings.store.LEGACY_PREFS_NAME
import com.yunfie.illustia.settings.store.PRIVACY_MODE_ENABLED
import com.yunfie.illustia.settings.store.KEY_APP_LANGUAGE
import com.yunfie.illustia.settings.store.clearPinHash as clearPinHashImpl
import com.yunfie.illustia.settings.store.clearSensitiveSettings as clearSensitiveSettingsImpl
import com.yunfie.illustia.settings.store.clearUnlockCodeHash as clearUnlockCodeHashImpl
import com.yunfie.illustia.settings.store.hasPinSet as hasPinSetImpl
import com.yunfie.illustia.settings.store.hasUnlockCodeSet as hasUnlockCodeSetImpl
import com.yunfie.illustia.settings.store.isValidUnlockCode as isValidUnlockCodeImpl
import com.yunfie.illustia.settings.store.migrateSettingsIfNeeded as migrateSettingsIfNeededImpl
import com.yunfie.illustia.settings.store.readAppSettings as readAppSettingsImpl
import com.yunfie.illustia.settings.store.savedIllustStorageBytes as savedIllustStorageBytesImpl
import com.yunfie.illustia.settings.store.savePinHash as savePinHashImpl
import com.yunfie.illustia.settings.store.saveUnlockCodeHash as saveUnlockCodeHashImpl
import com.yunfie.illustia.settings.store.verifyPinHash as verifyPinHashImpl
import com.yunfie.illustia.settings.store.verifyUnlockCodeHash as verifyUnlockCodeHashImpl
import com.yunfie.illustia.settings.store.writeAppSettings as writeAppSettingsImpl
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPreferences = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val encryptedPreferences = Companion.createEncryptedPreferences(appContext)
    private val sensitivePreferences = encryptedPreferences ?: legacyPreferences
    private val dataStore = Companion.dataStoreFor(appContext)
    private val database = IllustiaDatabase.getInstance(appContext)
    private val dao = database.settingsDao()

    init {
        migrateIfNeeded()
    }

    suspend fun read(): AppSettings {
        return readAppSettingsImpl(dataStore, sensitivePreferences, dao)
    }

    suspend fun write(settings: AppSettings) {
        writeAppSettingsImpl(dataStore, sensitivePreferences, database, dao, settings)
        // These non-sensitive values are needed before the asynchronous authoritative
        // settings load completes. Keep a lightweight startup mirror off the DataStore path.
        legacyPreferences.edit()
            .putInt(KEY_IMAGE_CACHE_SIZE_MB, settings.imageCacheSizeMb)
            .putString(KEY_APP_LANGUAGE, settings.appLanguage)
            .apply()
    }

    suspend fun clearSensitive() {
        clearSensitiveSettingsImpl(dataStore, sensitivePreferences, legacyPreferences, database, dao)
    }

    suspend fun getSavedIllusts() = withContext(Dispatchers.IO) {
        dao.getSavedIllusts()
    }

    suspend fun getSavedIllust(illustId: Long): SavedIllustWithPages? = withContext(Dispatchers.IO) {
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

    fun savePinHash(pin: String) {
        savePinHashImpl(sensitivePreferences, pin)
    }

    fun verifyPin(pin: String): Boolean {
        return verifyPinHashImpl(sensitivePreferences, pin)
    }

    fun hasPinSet(): Boolean {
        return hasPinSetImpl(sensitivePreferences)
    }

    fun clearPinHash() {
        clearPinHashImpl(sensitivePreferences)
    }

    fun saveUnlockCodeHash(code: String) {
        saveUnlockCodeHashImpl(sensitivePreferences, code)
    }

    fun verifyUnlockCode(code: String): Boolean {
        return verifyUnlockCodeHashImpl(sensitivePreferences, code)
    }

    fun hasUnlockCodeSet(): Boolean {
        return hasUnlockCodeSetImpl(sensitivePreferences)
    }

    fun clearUnlockCodeHash() {
        clearUnlockCodeHashImpl(sensitivePreferences)
    }

    fun isValidUnlockCode(code: String): Boolean {
        return isValidUnlockCodeImpl(code)
    }

    suspend fun getSavedIllustStorageBytes(): Long {
        return withContext(Dispatchers.IO) {
            savedIllustStorageBytesImpl(savedIllustDir())
        }
    }

    fun savedIllustDir(): File {
        return File(appContext.filesDir, "saved_illusts")
    }

    private fun migrateIfNeeded() {
        if (migrationCompleted) return
        synchronized(migrationLock) {
            if (migrationCompleted) return
            runBlocking(Dispatchers.IO) {
                migrateSettingsIfNeededImpl(dataStore, encryptedPreferences, legacyPreferences, database, dao)
            }
            migrationCompleted = true
        }
    }

    companion object {
        @Volatile
        private var sharedDataStore: DataStore<Preferences>? = null
        @Volatile
        private var sharedEncryptedPreferences: SharedPreferences? = null
        @Volatile
        private var encryptedPreferencesInitialized = false
        @Volatile
        private var migrationCompleted = false
        private val encryptedPreferencesLock = Any()
        private val migrationLock = Any()

        fun dataStoreFor(context: Context): DataStore<Preferences> {
            return sharedDataStore ?: synchronized(this) {
                sharedDataStore ?: PreferenceDataStoreFactory.create(
                    scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO),
                    produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
                ).also { sharedDataStore = it }
            }
        }

        fun createEncryptedPreferences(context: Context): SharedPreferences? {
            if (encryptedPreferencesInitialized) return sharedEncryptedPreferences
            return synchronized(encryptedPreferencesLock) {
                if (!encryptedPreferencesInitialized) {
                    sharedEncryptedPreferences = runCatching {
                        val appContext = context.applicationContext
                        val masterKey = MasterKey.Builder(appContext)
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
            return appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_APP_LANGUAGE, "system")
            ?: "system"
        }

        fun isPrivacyModeEnabledSync(context: Context): Boolean {
            val appContext = context.applicationContext
            return runCatching {
                runBlocking(Dispatchers.IO) {
                    dataStoreFor(appContext).data
                        .catch { error ->
                            if (error is java.io.IOException) emit(emptyPreferences()) else throw error
                        }
                        .first()[PRIVACY_MODE_ENABLED]
                }
            }.getOrNull() ?: false
        }

        fun readImageCacheSizeMbSync(context: Context): Int {
            return context.applicationContext
                .getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_IMAGE_CACHE_SIZE_MB, DEFAULT_IMAGE_CACHE_SIZE_MB)
                .coerceIn(MIN_IMAGE_CACHE_SIZE_MB, MAX_IMAGE_CACHE_SIZE_MB)
        }

        private const val KEY_IMAGE_CACHE_SIZE_MB = "startup_image_cache_size_mb"
        private const val DEFAULT_IMAGE_CACHE_SIZE_MB = 300
        private const val MIN_IMAGE_CACHE_SIZE_MB = 100
        private const val MAX_IMAGE_CACHE_SIZE_MB = 1000
    }
}
