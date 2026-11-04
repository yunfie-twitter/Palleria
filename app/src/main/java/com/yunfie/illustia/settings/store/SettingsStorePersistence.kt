package com.yunfie.illustia.settings.store

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.yunfie.illustia.settings.AppSettings
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
 * Keystore-backed secrets and Room-backed collections are hydrated separately after startup.
 */
internal suspend fun readStartupAppSettings(
    dataStore: DataStore<Preferences>,
    isLoggedIn: Boolean = false,
): AppSettings {
    val preferences = readDataStorePreferences(dataStore)
    return readFromDataStore(
        preferences = preferences,
        roomData = RoomSettingsData(),
        sensitivePreferences = null,
        isStartup = true,
        isStartupLoggedIn = isLoggedIn,
    )
}

internal suspend fun writeAppSettings(
    dataStore: DataStore<Preferences>,
    sensitivePreferences: SharedPreferences?,
    database: IllustiaDatabase,
    dao: SettingsDao,
    settings: AppSettings,
    baseSettings: AppSettings? = null,
) {
    writeDataStorePreferences(dataStore, settings)
    sensitivePreferences?.let { writeSensitiveSettings(it, settings) }
    writeRoomSettingsData(database, dao, settings, baseSettings)
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
        .putBoolean(KEY_STARTUP_IS_LOGGED_IN, false)
        .putBoolean(KEY_STARTUP_HAS_PIN, false)
        .remove(KEY_STARTUP_ACCOUNT_HASH)
        .apply()
    database.runInTransaction {
        dao.clearAccounts()
    }
    dataStore.edit { preferences ->
        preferences[ACTIVE_ACCOUNT_INDEX] = -1
    }
}
