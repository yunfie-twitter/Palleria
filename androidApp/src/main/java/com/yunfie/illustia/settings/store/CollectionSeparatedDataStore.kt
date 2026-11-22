package com.yunfie.illustia.settings.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Full settings view; startup reads only the primary store, without opening collections. */
internal class CollectionSeparatedDataStore(
    private val primary: DataStore<Preferences>,
    private val collections: DataStore<Preferences>,
) : DataStore<Preferences> {
    private val mutex = Mutex()
    private var migrated = false

    override val data: Flow<Preferences> =
        flow {
            mutex.withLock { migrateLocked() }
            emitAll(combine(primary.data, collections.data, ::merge))
        }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            migrateLocked()
            val updated = transform(merge(primary.data.first(), collections.data.first()))
            collections.edit { target ->
                COLLECTION_KEYS.forEach { key ->
                    updated[key]?.let { target[key] = it } ?: target.remove(key)
                }
            }
            primary.updateData {
                updated
                    .toMutablePreferences()
                    .apply {
                        COLLECTION_KEYS.forEach { remove(it) }
                    }.toPreferences()
            }
            updated
        }

    private suspend fun migrateLocked() {
        if (migrated) return
        val source = primary.data.first()
        if (COLLECTION_KEYS.any { source[it] != null }) {
            // The source remains authoritative until its cleanup succeeds. If either
            // write fails, no normal writes are allowed and the next read retries.
            collections.edit { target ->
                COLLECTION_KEYS.forEach { key -> source[key]?.let { target[key] = it } }
            }
            primary.edit { target -> COLLECTION_KEYS.forEach { target.remove(it) } }
        }
        migrated = true
    }

    private fun merge(
        settings: Preferences,
        bulk: Preferences,
    ): Preferences =
        settings
            .toMutablePreferences()
            .apply {
                COLLECTION_KEYS.forEach { key ->
                    bulk[key]?.let { this[key] = it } ?: remove(key)
                }
            }.toPreferences()

    private companion object {
        val COLLECTION_KEYS = listOf(MUTED_ILLUSTS_JSON, MUTED_USERS_JSON, MUTED_TAGS_JSON, SEEN_FEED_ILLUSTS_JSON)
    }
}
