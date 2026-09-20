package com.yunfie.illustia.pallasync

import android.content.Context
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import com.yunfie.illustia.settings.store.MAX_SEARCH_HISTORY
import com.yunfie.illustia.settings.store.MAX_VIEW_HISTORY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val MAX_SYNCED_SEEN_ILLUSTS = 2_000

/** Applies a relay page to the four Palleria-owned sync domains. */
class PallaSyncEventApplier internal constructor(
    private val collectionStore: SyncedCollectionsStore,
) {
    constructor(
        context: Context,
    ) : this(SettingsSyncedCollectionsStore(context.applicationContext))

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    internal suspend fun applyEvent(payloadJsonString: String): PallaSyncApplyResult = applyEvents(listOf(payloadJsonString)).single()

    /**
     * Folds a whole page in memory and persists it once. Invalid/unknown records
     * are returned as quarantine results; storage failures are deliberately thrown
     * so the coordinator does not advance its relay cursor.
     */
    internal suspend fun applyEvents(payloadJsonStrings: List<String>): List<PallaSyncApplyResult> =
        withContext(Dispatchers.IO) {
            if (payloadJsonStrings.isEmpty()) return@withContext emptyList()

            val parsedPayloads =
                kotlinx.coroutines.coroutineScope {
                    payloadJsonStrings
                        .map { encoded ->
                            async(Dispatchers.Default) {
                                runCatching { json.decodeFromString<DataPayload>(encoded) }
                            }
                        }.awaitAll()
                }

            val results = ArrayList<PallaSyncApplyResult>(payloadJsonStrings.size)
            collectionStore.update { original ->
                var current = original
                parsedPayloads.forEach { parsed ->
                    val outcome =
                        parsed
                            .mapCatching { payload ->
                                applyPayload(current, payload)
                            }.fold(
                                onSuccess = { it },
                                onFailure = { error ->
                                    FoldResult(
                                        current,
                                        PallaSyncApplyResult.Quarantined(
                                            error.message?.take(240) ?: error::class.java.simpleName,
                                        ),
                                    )
                                },
                            )
                    current = outcome.collections
                    results += outcome.result
                }
                current
            }
            results
        }

    private fun applyPayload(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult =
        when (payload.schema) {
            FAVORITE_TAG_SCHEMA_V2 -> applyFavoriteTag(current, payload)
            SEARCH_HISTORY_SCHEMA_V2 -> applySearchHistory(current, payload)
            MUTE_SETTINGS_SCHEMA_V2 -> applyMuteSetting(current, payload)
            VIEW_HISTORY_SCHEMA_V2 -> applyViewHistory(current, payload)
            FAVORITE_TAG_SCHEMA_V1 -> applyLegacyFavoriteTags(current, payload)
            SEARCH_HISTORY_SCHEMA_V1 -> applyLegacySearchHistory(current, payload)
            MUTE_SETTINGS_SCHEMA_V1 -> applyLegacyMuteSettings(current, payload)
            VIEW_HISTORY_SCHEMA_V1 -> applyLegacyViewHistory(current, payload)
            else -> current.quarantined("Unknown schema: ${payload.schema}")
        }

    private fun applyFavoriteTag(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult {
        val tag =
            payload.body.objectString("tag")
                ?: return current.quarantined("favorite_tag body is missing tag")
        if (tag.isBlank() || payload.entity_id != tag) {
            return current.quarantined("favorite_tag entity/body mismatch")
        }
        val tags =
            when (payload.operation) {
                SYNC_OPERATION_UPSERT -> {
                    if (tag in current.favoriteTags) {
                        current.favoriteTags
                    } else {
                        current.favoriteTags + tag
                    }
                }

                SYNC_OPERATION_DELETE -> {
                    current.favoriteTags.filterNot { it == tag }
                }

                else -> {
                    return current.invalidOperation(payload)
                }
            }
        return current.copy(favoriteTags = tags).applied()
    }

    private fun applySearchHistory(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult {
        val query =
            payload.body.objectString("query")
                ?: return current.quarantined("search_history body is missing query")
        if (query.isBlank() || payload.entity_id != query) {
            return current.quarantined("search_history entity/body mismatch")
        }
        val history =
            when (payload.operation) {
                SYNC_OPERATION_UPSERT -> {
                    (listOf(query) + current.searchHistory.filterNot { it == query })
                        .take(MAX_SEARCH_HISTORY)
                }

                SYNC_OPERATION_DELETE -> {
                    current.searchHistory.filterNot { it == query }
                }

                else -> {
                    return current.invalidOperation(payload)
                }
            }
        return current.copy(searchHistory = history).applied()
    }

    private fun applyMuteSetting(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult {
        val separator = payload.entity_id.indexOf(':')
        if (separator <= 0 || separator == payload.entity_id.lastIndex) {
            return current.quarantined("Invalid mute entity_id")
        }
        val kind = payload.entity_id.substring(0, separator)
        val value = payload.entity_id.substring(separator + 1)
        val bodyKind = payload.body.objectString("kind")
        val bodyValue = payload.body.objectString("value")
        if ((bodyKind != null && bodyKind != kind) || (bodyValue != null && bodyValue != value)) {
            return current.quarantined("mute_settings entity/body mismatch")
        }

        return when (kind) {
            "tag" -> {
                val values =
                    current.mutedTags.applyItemOperation(value, payload.operation)
                        ?: return current.invalidOperation(payload)
                current.copy(mutedTags = values).applied()
            }

            "user" -> {
                val id =
                    value.toLongOrNull()?.takeIf { it > 0L }
                        ?: return current.quarantined("Invalid muted user ID")
                val values =
                    current.mutedUsers.applyItemOperation(id, payload.operation)
                        ?: return current.invalidOperation(payload)
                current.copy(mutedUsers = values).applied()
            }

            "illust" -> {
                val id =
                    value.toLongOrNull()?.takeIf { it > 0L }
                        ?: return current.quarantined("Invalid muted illustration ID")
                val values =
                    current.mutedIllusts.applyItemOperation(id, payload.operation)
                        ?: return current.invalidOperation(payload)
                current.copy(mutedIllusts = values).applied()
            }

            else -> {
                current.quarantined("Unknown mute entity kind: $kind")
            }
        }
    }

    private fun applyViewHistory(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult {
        val separator = payload.entity_id.indexOf(':')
        if (separator <= 0 || separator == payload.entity_id.lastIndex) {
            return current.quarantined("Invalid view_history entity_id")
        }
        val kind = payload.entity_id.substring(0, separator)
        val id =
            payload.entity_id
                .substring(separator + 1)
                .toLongOrNull()
                ?.takeIf { it > 0L }
                ?: return current.quarantined("Invalid view_history illustration ID")

        return when (kind) {
            "seen" -> {
                val bodyId = payload.body.objectLong("id")
                if (bodyId != null && bodyId != id) {
                    return current.quarantined("seen entity/body mismatch")
                }
                val values =
                    when (payload.operation) {
                        SYNC_OPERATION_UPSERT -> {
                            (listOf(id) + current.seenFeedIllusts.filterNot { it == id })
                                .take(MAX_SYNCED_SEEN_ILLUSTS)
                        }

                        SYNC_OPERATION_DELETE -> {
                            current.seenFeedIllusts.filterNot { it == id }
                        }

                        else -> {
                            return current.invalidOperation(payload)
                        }
                    }
                current.copy(seenFeedIllusts = values).applied()
            }

            "viewed" -> {
                val values =
                    when (payload.operation) {
                        SYNC_OPERATION_UPSERT -> {
                            val illust =
                                payload.body.toHistoryIllust()
                                    ?: return current.quarantined("Invalid viewed illustration body")
                            if (illust.id != id) {
                                return current.quarantined("viewed entity/body mismatch")
                            }
                            (listOf(illust) + current.viewHistory.filterNot { it.id == id })
                                .take(MAX_VIEW_HISTORY)
                        }

                        SYNC_OPERATION_DELETE -> {
                            current.viewHistory.filterNot { it.id == id }
                        }

                        else -> {
                            return current.invalidOperation(payload)
                        }
                    }
                current.copy(viewHistory = values).applied()
            }

            else -> {
                current.quarantined("Unknown view_history entity kind: $kind")
            }
        }
    }

    private fun applyLegacyFavoriteTags(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult {
        val incoming =
            (payload.body as? JsonArray)
                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                ?: return current.quarantined("Invalid favorite_tag/1 snapshot")
        return current.copy(favoriteTags = (incoming + current.favoriteTags).distinct()).applied()
    }

    private fun applyLegacySearchHistory(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult {
        val incoming =
            (payload.body as? JsonArray)
                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                ?: return current.quarantined("Invalid search_history/1 snapshot")
        return current
            .copy(
                searchHistory = (incoming + current.searchHistory).distinct().take(MAX_SEARCH_HISTORY),
            ).applied()
    }

    private fun applyLegacyMuteSettings(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult {
        val body =
            payload.body as? JsonObject
                ?: return current.quarantined("Invalid mute_settings/1 snapshot")
        val tags = body.stringArray("mutedTags")
        val users = body.longArray("mutedUsers")
        val illusts = body.longArray("mutedIllusts")
        return current
            .copy(
                mutedTags = (tags + current.mutedTags).distinct(),
                mutedUsers = (users + current.mutedUsers).distinct(),
                mutedIllusts = (illusts + current.mutedIllusts).distinct(),
            ).applied()
    }

    private fun applyLegacyViewHistory(
        current: SyncedCollectionsSnapshot,
        payload: DataPayload,
    ): FoldResult {
        val body =
            payload.body as? JsonObject
                ?: return current.quarantined("Invalid view_history/1 snapshot")
        val seen = body.longArray("seenFeedIllusts")
        val viewed =
            body["viewedIllusts"]
                ?.jsonArray
                ?.mapNotNull(JsonElement::toHistoryIllust)
                .orEmpty()
        return current
            .copy(
                seenFeedIllusts =
                    (seen + current.seenFeedIllusts)
                        .distinct()
                        .take(MAX_SYNCED_SEEN_ILLUSTS),
                viewHistory =
                    (viewed + current.viewHistory)
                        .distinctBy(Illust::id)
                        .take(MAX_VIEW_HISTORY),
            ).applied()
    }

    private data class FoldResult(
        val collections: SyncedCollectionsSnapshot,
        val result: PallaSyncApplyResult,
    )

    private fun SyncedCollectionsSnapshot.applied(): FoldResult = FoldResult(this, PallaSyncApplyResult.Applied)

    private fun SyncedCollectionsSnapshot.quarantined(reason: String): FoldResult =
        FoldResult(this, PallaSyncApplyResult.Quarantined(reason))

    private fun SyncedCollectionsSnapshot.invalidOperation(payload: DataPayload): FoldResult =
        quarantined("Invalid ${payload.schema} operation: ${payload.operation}")
}

internal interface SyncedCollectionsStore {
    suspend fun update(transform: (SyncedCollectionsSnapshot) -> SyncedCollectionsSnapshot): SyncedCollectionsSnapshot
}

private class SettingsSyncedCollectionsStore(
    context: Context,
) : SyncedCollectionsStore {
    private val store = SettingsStore(context, IncomingSyncEventWriter)

    override suspend fun update(transform: (SyncedCollectionsSnapshot) -> SyncedCollectionsSnapshot): SyncedCollectionsSnapshot =
        store.updateSyncedCollections(transform)
}

/** Prevents incoming relay events from recursively constructing another sync coordinator. */
private object IncomingSyncEventWriter : PallaSyncEventWriter {
    override suspend fun enqueueDataEvents(events: List<PallaSyncPendingEvent>): Boolean = events.isEmpty()

    override suspend fun <T> enqueueDataEventsThen(
        events: List<PallaSyncPendingEvent>,
        afterEnqueue: suspend () -> T,
    ): T {
        require(events.isEmpty()) { "Incoming sync persistence must not enqueue outgoing events" }
        return afterEnqueue()
    }
}

private fun <T> List<T>.applyItemOperation(
    item: T,
    operation: String,
): List<T>? =
    when (operation) {
        SYNC_OPERATION_UPSERT -> if (item in this) this else this + item
        SYNC_OPERATION_DELETE -> filterNot { it == item }
        else -> null
    }

private fun JsonElement.objectString(key: String): String? =
    (this as? JsonObject)?.get(key)?.let { element ->
        runCatching { element.jsonPrimitive.content }.getOrNull()
    }

private fun JsonElement.objectLong(key: String): Long? = objectString(key)?.toLongOrNull()

private fun JsonObject.stringArray(key: String): List<String> =
    (this[key] as? JsonArray)
        ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
        .orEmpty()

private fun JsonObject.longArray(key: String): List<Long> = stringArray(key).mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0L } }

private fun JsonElement.toHistoryIllust(): Illust? {
    val item = this as? JsonObject ?: return null
    val id =
        item["id"]
            ?.let { runCatching { it.jsonPrimitive.content.toLong() }.getOrNull() }
            ?.takeIf { it > 0L } ?: return null
    val imageUrl = item["imageUrl"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty()
    return Illust(
        id = id,
        title = item["title"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty(),
        type =
            item["type"]
                ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
                ?.ifBlank { "illust" } ?: "illust",
        caption = "",
        artistId = 0L,
        artistName = item["artistName"]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }.orEmpty(),
        artistAvatarUrl = null,
        squareImageUrl = "",
        mediumImageUrl = imageUrl,
        imageUrl = imageUrl,
        originalImageUrl = null,
        mediumImagePages = emptyList(),
        imagePages = emptyList(),
        originalImagePages = emptyList(),
        tags = emptyList(),
        pageCount =
            item["pageCount"]
                ?.let {
                    runCatching { it.jsonPrimitive.content.toInt() }.getOrNull()
                }?.coerceAtLeast(1) ?: 1,
        isBookmarked =
            item["isBookmarked"]
                ?.let {
                    runCatching { it.jsonPrimitive.content.toBoolean() }.getOrNull()
                } ?: false,
    )
}
