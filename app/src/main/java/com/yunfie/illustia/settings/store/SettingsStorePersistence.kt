package com.yunfie.illustia.settings.store

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import com.yunfie.illustia.settings.db.IllustiaDatabase
import com.yunfie.illustia.settings.db.SettingsDao

internal suspend fun readAppSettings(
    dataStore: DataStore<Preferences>,
    sensitivePreferences: SharedPreferences,
    dao: SettingsDao,
    viewHistoryLimit: Int? = null,
): AppSettings {
    val preferences = readDataStorePreferences(dataStore)
    val roomData = readRoomSettingsData(dao, viewHistoryLimit)
    return readFromDataStore(preferences, roomData, sensitivePreferences)
}

/**
 * Reads only the settings required to build the first app frame.
 * Room-backed collections are hydrated separately after startup.
 */
internal suspend fun readStartupAppSettings(
    dataStore: DataStore<Preferences>,
    sensitivePreferences: SharedPreferences,
): AppSettings {
    val preferences = readDataStorePreferences(dataStore)
    return readFromDataStore(preferences, RoomSettingsData(), sensitivePreferences)
}

internal suspend fun writeAppSettings(
    dataStore: DataStore<Preferences>,
    sensitivePreferences: SharedPreferences,
    database: IllustiaDatabase,
    dao: SettingsDao,
    settings: AppSettings,
    baseSettings: AppSettings? = null,
) {
    writeDataStorePreferences(dataStore, settings)
    writeSensitiveSettings(sensitivePreferences, settings)
    writeRoomSettingsData(database, dao, settings, baseSettings)
}

internal suspend fun writeSyncedCollections(
    dataStore: DataStore<Preferences>,
    database: IllustiaDatabase,
    dao: SettingsDao,
    synced: SyncedCollectionsSnapshot,
) {
    dataStore.edit { preferences ->
        preferences[MUTED_ILLUSTS_JSON] = encodeLongList(synced.mutedIllusts)
        preferences[MUTED_USERS_JSON] = encodeLongList(synced.mutedUsers)
        preferences[MUTED_TAGS_JSON] = encodeStringList(synced.mutedTags)
        preferences[SEEN_FEED_ILLUSTS_JSON] = encodeLongList(synced.seenFeedIllusts)
    }
    writeSyncedRoomSettingsData(database, dao, synced)
}

internal suspend fun clearSensitiveSettings(
    dataStore: DataStore<Preferences>,
    sensitivePreferences: SharedPreferences,
    legacyPreferences: SharedPreferences,
    database: IllustiaDatabase,
    dao: SettingsDao,
) {
    sensitivePreferences
        .edit()
        .remove(KEY_REFRESH_TOKEN)
        .remove(KEY_ACCOUNTS)
        .remove(KEY_ACCOUNT_TOKENS)
        .apply()
    legacyPreferences
        .edit()
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
