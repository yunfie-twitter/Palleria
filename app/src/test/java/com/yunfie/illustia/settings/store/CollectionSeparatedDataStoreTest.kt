package com.yunfie.illustia.settings.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.IOException

class CollectionSeparatedDataStoreTest {
    @Test
    fun `migration separates every bulk key while retaining startup settings`() =
        runBlocking<Unit> {
            val primary =
                MemoryStore(
                    preferencesOf(
                        APP_LANGUAGE to "ja",
                        MUTED_ILLUSTS_JSON to "[1]",
                        MUTED_USERS_JSON to "[2]",
                        MUTED_TAGS_JSON to "[\"tag\"]",
                        SEEN_FEED_ILLUSTS_JSON to "[3]",
                    ),
                )
            val bulk = MemoryStore()
            val full = CollectionSeparatedDataStore(primary, bulk)

            full.data.first()[MUTED_TAGS_JSON] shouldBe "[\"tag\"]"
            primary.data.value.asMap() shouldBe preferencesOf(APP_LANGUAGE to "ja").asMap()
            bulk.data.value[MUTED_ILLUSTS_JSON] shouldBe "[1]"
            bulk.data.value[MUTED_USERS_JSON] shouldBe "[2]"
            bulk.data.value[SEEN_FEED_ILLUSTS_JSON] shouldBe "[3]"
            full.edit { it[APP_LANGUAGE] = "en" }
            bulk.data.value[MUTED_TAGS_JSON] shouldBe "[\"tag\"]"
            primary.data.value[APP_LANGUAGE] shouldBe "en"
        }

    @Test
    fun `failed collection write leaves source intact and can retry`() =
        runBlocking<Unit> {
            val primary = MemoryStore(preferencesOf(MUTED_ILLUSTS_JSON to "[1]"))
            val bulk = MemoryStore().apply { failNextWrite = true }
            val full = CollectionSeparatedDataStore(primary, bulk)

            shouldThrow<IOException> { full.data.first() }
            primary.data.value[MUTED_ILLUSTS_JSON] shouldBe "[1]"
            full.data.first()[MUTED_ILLUSTS_JSON] shouldBe "[1]"
            primary.data.value[MUTED_ILLUSTS_JSON] shouldBe null
        }

    @Test
    fun `failed cleanup retries from authoritative source after restart`() =
        runBlocking<Unit> {
            val primary = MemoryStore(preferencesOf(MUTED_ILLUSTS_JSON to "[1]"))
            val bulk = MemoryStore(preferencesOf(MUTED_ILLUSTS_JSON to "[0]"))
            primary.failNextWrite = true
            shouldThrow<IOException> { CollectionSeparatedDataStore(primary, bulk).data.first() }
            primary.data.value[MUTED_ILLUSTS_JSON] shouldBe "[1]"
            bulk.data.value[MUTED_ILLUSTS_JSON] shouldBe "[1]"

            // Simulate the authoritative source changing before a retry.
            primary.edit { it[MUTED_ILLUSTS_JSON] = "[2]" }
            val restarted = CollectionSeparatedDataStore(primary, bulk)
            restarted.data.first()[MUTED_ILLUSTS_JSON] shouldBe "[2]"
            primary.data.value[MUTED_ILLUSTS_JSON] shouldBe null
            restarted.edit { it[MUTED_ILLUSTS_JSON] = "[]" }
            CollectionSeparatedDataStore(primary, bulk).data.first()[MUTED_ILLUSTS_JSON] shouldBe "[]"
        }

    @Test
    fun `startup store reads do not read or write deferred collections`() =
        runBlocking<Unit> {
            val primary = MemoryStore(preferencesOf(APP_LANGUAGE to "en"))
            val unavailableBulk =
                object : DataStore<Preferences> {
                    override val data: kotlinx.coroutines.flow.Flow<Preferences>
                        get() = error("Startup must not open collections")

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        error("Startup must not write collections")
                }
            CollectionSeparatedDataStore(primary, unavailableBulk)
            readStartupAppSettings(primary).appLanguage shouldBe "en"
        }

    private class MemoryStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        override val data = MutableStateFlow(initial)
        var failNextWrite = false

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (failNextWrite) {
                failNextWrite = false
                throw IOException("Simulated interrupted persistence")
            }
            return transform(data.value).also { data.value = it }
        }
    }
}
