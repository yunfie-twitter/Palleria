package com.yunfie.illustia.settings.store

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.db.IllustiaDatabase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class SettingsMigrationCredentialsTest {
    @Test
    fun `migration preserves active credentials when encrypted storage is unavailable`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val legacy = context.getSharedPreferences("migration-fallback", Context.MODE_PRIVATE)
            legacy
                .edit()
                .clear()
                .putString(KEY_REFRESH_TOKEN, "pixiv-token")
                .putString(KEY_DISCORD_TOKEN, "discord-token")
                .commit()
            val database = Room.inMemoryDatabaseBuilder(context, IllustiaDatabase::class.java).build()
            try {
                migrateSettingsIfNeeded(MemoryPreferences(), null, legacy, database, database.settingsDao())

                legacy.getString(KEY_REFRESH_TOKEN, null) shouldBe "pixiv-token"
                legacy.getString(KEY_DISCORD_TOKEN, null) shouldBe "discord-token"
            } finally {
                database.close()
                legacy.edit().clear().commit()
            }
        }

    @Test
    fun `migration transfers both credentials and removes the legacy copies`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val legacy = context.getSharedPreferences("migration-legacy", Context.MODE_PRIVATE)
            val secure = context.getSharedPreferences("migration-secure", Context.MODE_PRIVATE)
            legacy
                .edit()
                .clear()
                .putString(KEY_REFRESH_TOKEN, "pixiv-token")
                .putString(KEY_DISCORD_TOKEN, "discord-token")
                .commit()
            secure.edit().clear().commit()
            val database = Room.inMemoryDatabaseBuilder(context, IllustiaDatabase::class.java).build()
            try {
                migrateSettingsIfNeeded(MemoryPreferences(), secure, legacy, database, database.settingsDao())

                secure.getString(KEY_REFRESH_TOKEN, null) shouldBe "pixiv-token"
                secure.getString(KEY_DISCORD_TOKEN, null) shouldBe "discord-token"
                legacy.contains(KEY_REFRESH_TOKEN) shouldBe false
                legacy.contains(KEY_DISCORD_TOKEN) shouldBe false
            } finally {
                database.close()
                legacy.edit().clear().commit()
                secure.edit().clear().commit()
            }
        }

    @Test
    fun `version upgrade preserves DataStore settings Room collections and account credentials`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val legacy = context.getSharedPreferences("upgrade-legacy", Context.MODE_PRIVATE)
            val secure = context.getSharedPreferences("upgrade-secure", Context.MODE_PRIVATE)
            legacy.edit().clear().commit()
            secure.edit().clear().commit()
            val database = Room.inMemoryDatabaseBuilder(context, IllustiaDatabase::class.java).build()
            val dataStore = MemoryPreferences()
            val settings =
                AppSettings(
                    refreshToken = "pixiv-token",
                    discordToken = "discord-token",
                    themeMode = "dark",
                    allowR18 = false,
                    privacyModeEnabled = true,
                    favoriteTags = listOf("favorite"),
                    searchHistory = listOf("query"),
                    accounts = listOf(StoredAccount("Name", "account", null, "account-token", 123L)),
                    activeAccountIndex = 0,
                )
            try {
                dataStore.edit {
                    writeToDataStore(it, settings)
                    it[SETTINGS_VERSION] = CURRENT_SETTINGS_VERSION - 1
                }
                writeRoomSettingsData(database, database.settingsDao(), settings)
                writeSensitiveSettings(secure, settings)

                migrateSettingsIfNeeded(dataStore, secure, legacy, database, database.settingsDao())

                val restored = readFromDataStore(dataStore.data.value, readRoomSettingsData(database.settingsDao()), secure)
                restored.themeMode shouldBe settings.themeMode
                restored.allowR18 shouldBe false
                restored.privacyModeEnabled shouldBe true
                restored.favoriteTags shouldBe settings.favoriteTags
                restored.searchHistory shouldBe settings.searchHistory
                restored.accounts shouldBe settings.accounts
                restored.activeAccountIndex shouldBe 0
                restored.refreshToken shouldBe settings.refreshToken
                restored.discordToken shouldBe settings.discordToken
                dataStore.data.value[SETTINGS_VERSION] shouldBe CURRENT_SETTINGS_VERSION
            } finally {
                database.close()
                legacy.edit().clear().commit()
                secure.edit().clear().commit()
            }
        }

    @Test
    fun `interrupted migration retries from its persisted snapshot without losing accounts`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val legacy = context.getSharedPreferences("retry-legacy", Context.MODE_PRIVATE)
            val secure = context.getSharedPreferences("retry-secure", Context.MODE_PRIVATE)
            legacy
                .edit()
                .clear()
                .putString(KEY_REFRESH_TOKEN, "pixiv-token")
                .putString(KEY_THEME_MODE, "dark")
                .putString(
                    KEY_ACCOUNTS,
                    """[{"name":"Name","account":"account","refreshToken":"account-token","userId":123}]""",
                ).commit()
            secure.edit().clear().commit()
            val database = Room.inMemoryDatabaseBuilder(context, IllustiaDatabase::class.java).build()
            val dataStore = MemoryPreferences(failOnWrite = 2)
            try {
                shouldThrow<IOException> {
                    migrateSettingsIfNeeded(dataStore, secure, legacy, database, database.settingsDao())
                }
                dataStore.data.value[SETTINGS_VERSION] shouldBe 0

                migrateSettingsIfNeeded(dataStore, secure, legacy, database, database.settingsDao())

                val restored = readFromDataStore(dataStore.data.value, readRoomSettingsData(database.settingsDao()), secure)
                restored.themeMode shouldBe "dark"
                restored.refreshToken shouldBe "pixiv-token"
                restored.accounts.single().refreshToken shouldBe "account-token"
                restored.accounts.single().userId shouldBe 123L
                dataStore.data.value[SETTINGS_VERSION] shouldBe CURRENT_SETTINGS_VERSION
            } finally {
                database.close()
                legacy.edit().clear().commit()
                secure.edit().clear().commit()
            }
        }

    @Test
    fun `failed legacy cleanup keeps migration retryable`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val legacy = context.getSharedPreferences("cleanup-legacy", Context.MODE_PRIVATE)
            val secure = context.getSharedPreferences("cleanup-secure", Context.MODE_PRIVATE)
            legacy
                .edit()
                .clear()
                .putString(KEY_REFRESH_TOKEN, "pixiv-token")
                .commit()
            secure.edit().clear().commit()
            var failCleanup = true
            val failingLegacy =
                object : SharedPreferences by legacy {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = legacy.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun remove(key: String?): SharedPreferences.Editor = apply { editor.remove(key) }

                            override fun commit(): Boolean = if (failCleanup) false else editor.commit()
                        }
                    }
                }
            val database = Room.inMemoryDatabaseBuilder(context, IllustiaDatabase::class.java).build()
            val dataStore = MemoryPreferences()
            try {
                shouldThrow<IOException> {
                    migrateSettingsIfNeeded(dataStore, secure, failingLegacy, database, database.settingsDao())
                }
                dataStore.data.value[SETTINGS_VERSION] shouldBe 0
                legacy.contains(KEY_REFRESH_TOKEN) shouldBe true
                secure.getString(KEY_REFRESH_TOKEN, null) shouldBe "pixiv-token"

                failCleanup = false
                migrateSettingsIfNeeded(dataStore, secure, failingLegacy, database, database.settingsDao())

                dataStore.data.value[SETTINGS_VERSION] shouldBe CURRENT_SETTINGS_VERSION
                legacy.contains(KEY_REFRESH_TOKEN) shouldBe false
            } finally {
                database.close()
                legacy.edit().clear().commit()
                secure.edit().clear().commit()
            }
        }

    @Test
    fun `fallback credentials transfer when encrypted storage recovers after schema migration`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val legacy = context.getSharedPreferences("recovered-legacy", Context.MODE_PRIVATE)
            val secure = context.getSharedPreferences("recovered-secure", Context.MODE_PRIVATE)
            legacy
                .edit()
                .clear()
                .putString(KEY_REFRESH_TOKEN, "pixiv-token")
                .putString(KEY_DISCORD_TOKEN, "discord-token")
                .putString(
                    KEY_ACCOUNTS,
                    """[{"name":"Name","account":"account","refreshToken":"account-token","userId":123}]""",
                ).commit()
            secure.edit().clear().commit()
            val database = Room.inMemoryDatabaseBuilder(context, IllustiaDatabase::class.java).build()
            val dataStore = MemoryPreferences()
            try {
                migrateSettingsIfNeeded(dataStore, null, legacy, database, database.settingsDao())
                dataStore.data.value[SETTINGS_VERSION] shouldBe CURRENT_SETTINGS_VERSION

                migrateSettingsIfNeeded(dataStore, secure, legacy, database, database.settingsDao())

                val restored = readFromDataStore(dataStore.data.value, readRoomSettingsData(database.settingsDao()), secure)
                restored.refreshToken shouldBe "pixiv-token"
                restored.discordToken shouldBe "discord-token"
                restored.accounts.single().refreshToken shouldBe "account-token"
                legacy.contains(KEY_REFRESH_TOKEN) shouldBe false
                legacy.contains(KEY_DISCORD_TOKEN) shouldBe false
                legacy.contains(KEY_ACCOUNT_TOKENS) shouldBe false
            } finally {
                database.close()
                legacy.edit().clear().commit()
                secure.edit().clear().commit()
            }
        }

    @Test
    fun `fallback cleanup preserves newer encrypted credentials and an explicit logout`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val legacy = context.getSharedPreferences("newer-legacy", Context.MODE_PRIVATE)
            val secure = context.getSharedPreferences("newer-secure", Context.MODE_PRIVATE)
            legacy
                .edit()
                .clear()
                .putString(KEY_REFRESH_TOKEN, "old-token")
                .putString(KEY_DISCORD_TOKEN, "old-discord")
                .commit()
            secure
                .edit()
                .clear()
                .putString(KEY_REFRESH_TOKEN, "new-token")
                .putString(KEY_DISCORD_TOKEN, "")
                .commit()
            val database = Room.inMemoryDatabaseBuilder(context, IllustiaDatabase::class.java).build()
            val dataStore = MemoryPreferences()
            dataStore.edit { it[SETTINGS_VERSION] = CURRENT_SETTINGS_VERSION }
            try {
                migrateSettingsIfNeeded(dataStore, secure, legacy, database, database.settingsDao())

                secure.getString(KEY_REFRESH_TOKEN, null) shouldBe "new-token"
                secure.getString(KEY_DISCORD_TOKEN, null) shouldBe ""
                legacy.contains(KEY_REFRESH_TOKEN) shouldBe false
                legacy.contains(KEY_DISCORD_TOKEN) shouldBe false
            } finally {
                database.close()
                legacy.edit().clear().commit()
                secure.edit().clear().commit()
            }
        }

    private class MemoryPreferences(
        private val failOnWrite: Int? = null,
    ) : DataStore<Preferences> {
        override val data = MutableStateFlow(emptyPreferences())
        private var writes = 0

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (++writes == failOnWrite) throw IOException("Simulated persistence failure")
            return transform(data.value).also { data.value = it }
        }
    }
}
