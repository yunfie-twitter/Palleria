package com.yunfie.illustia.settings.store

import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.db.IllustiaDatabase
import com.yunfie.illustia.settings.db.SettingsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

internal suspend fun migrateSettingsIfNeeded(
    dataStore: DataStore<Preferences>,
    encryptedPreferences: SharedPreferences?,
    legacyPreferences: SharedPreferences,
    database: IllustiaDatabase,
    dao: SettingsDao,
) = withContext(Dispatchers.IO) {
    val current =
        dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }.first()
    if ((current[SETTINGS_VERSION] ?: 0) >= CURRENT_SETTINGS_VERSION) {
        if (encryptedPreferences != null) {
            migrateFallbackCredentials(encryptedPreferences, legacyPreferences)
        }
        return@withContext
    }

    val source =
        when {
            encryptedPreferences != null && encryptedPreferences.all.isNotEmpty() -> encryptedPreferences
            else -> legacyPreferences
        }
    val sensitivePreferences = encryptedPreferences ?: legacyPreferences
    val migrated =
        if (current[SETTINGS_VERSION] != null) {
            // Once migrated, DataStore and Room own these values; the secure store holds only credentials.
            readFromDataStore(current, readRoomSettingsData(dao), source)
        } else {
            readLegacyMigrationSettings(current, source)
        }
    writeRoomSettingsData(database, dao, migrated)
    dataStore.edit { preferences ->
        writeToDataStore(preferences, migrated)
        // Keep a retryable snapshot before changing the source credential format.
        preferences[SETTINGS_VERSION] = current[SETTINGS_VERSION] ?: 0
    }
    writeSensitiveSettings(sensitivePreferences, migrated, commit = true)
    val cleanedLegacyCredentials =
        legacyPreferences
            .edit()
            .apply {
                // The legacy store is also the active fallback when the keystore is unavailable.
                if (encryptedPreferences != null) {
                    remove(KEY_REFRESH_TOKEN)
                    remove(KEY_DISCORD_TOKEN)
                    remove(KEY_ACCOUNT_TOKENS)
                    remove(KEY_ACCOUNTS)
                }
            }.remove(KEY_ACTIVE_ACCOUNT_INDEX)
            .commit()
    if (!cleanedLegacyCredentials) throw IOException("Unable to remove migrated legacy credentials")
    dataStore.edit { preferences ->
        preferences[SETTINGS_VERSION] = CURRENT_SETTINGS_VERSION
    }
}

private fun readLegacyMigrationSettings(
    current: Preferences,
    source: SharedPreferences,
): AppSettings {
    val sharedSettings = readFromSharedPreferences(source)
    val dataStoreSettings = readCollectionsFromDataStore(current, source)
    return sharedSettings.copy(
        searchHistory = dataStoreSettings.searchHistory.ifEmpty { sharedSettings.searchHistory },
        favoriteTags = dataStoreSettings.favoriteTags.ifEmpty { sharedSettings.favoriteTags },
        viewHistory = dataStoreSettings.viewHistory.ifEmpty { sharedSettings.viewHistory },
        accounts = dataStoreSettings.accounts.ifEmpty { sharedSettings.accounts },
        onboardingSetupCompleted = source.all.isNotEmpty() || current.asMap().isNotEmpty(),
    )
}

private fun migrateFallbackCredentials(
    encryptedPreferences: SharedPreferences,
    legacyPreferences: SharedPreferences,
) {
    val credentialKeys = listOf(KEY_REFRESH_TOKEN, KEY_DISCORD_TOKEN, KEY_ACCOUNT_TOKENS, KEY_ACCOUNTS)
    if (credentialKeys.none(legacyPreferences::contains)) return

    val editor = encryptedPreferences.edit()
    listOf(KEY_REFRESH_TOKEN, KEY_DISCORD_TOKEN, KEY_ACCOUNT_TOKENS).forEach { key ->
        if (!encryptedPreferences.contains(key)) {
            val value =
                legacyPreferences.getString(key, null)
                    ?: if (key == KEY_ACCOUNT_TOKENS && legacyPreferences.contains(KEY_ACCOUNTS)) {
                        encodeAccountTokens(decodeAccounts(legacyPreferences.getString(KEY_ACCOUNTS, "").orEmpty()))
                    } else {
                        null
                    }
            value?.let { editor.putString(key, it) }
        }
    }
    if (!editor.commit()) throw IOException("Unable to persist fallback credentials")
    val cleanup = legacyPreferences.edit()
    credentialKeys.forEach(cleanup::remove)
    if (!cleanup.commit()) throw IOException("Unable to remove fallback credentials")
}
