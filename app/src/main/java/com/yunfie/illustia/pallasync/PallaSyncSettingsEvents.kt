package com.yunfie.illustia.pallasync

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SyncedCollectionsSnapshot
import com.yunfie.illustia.settings.syncedCollections
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal fun buildSettingsSyncEvents(
    previous: AppSettings,
    next: AppSettings,
): List<PallaSyncPendingEvent> = buildSettingsSyncEvents(previous.syncedCollections(), next.syncedCollections())

internal fun buildInitialSettingsSyncEvents(settings: AppSettings): List<PallaSyncPendingEvent> =
    buildInitialSettingsSyncEvents(settings.syncedCollections())

internal fun buildInitialSettingsSyncEvents(collections: SyncedCollectionsSnapshot): List<PallaSyncPendingEvent> =
    buildSettingsSyncEvents(SyncedCollectionsSnapshot(), collections)

internal fun buildSettingsSyncEvents(
    previous: SyncedCollectionsSnapshot,
    next: SyncedCollectionsSnapshot,
): List<PallaSyncPendingEvent> =
    buildList {
        addStringSetChanges(
            schema = FAVORITE_TAG_SCHEMA_V2,
            previous = previous.favoriteTags,
            next = next.favoriteTags,
            entityId = { it },
            body = { value -> buildJsonObject { put("tag", value) } },
        )

        if (previous.searchHistory != next.searchHistory) {
            val nextQueries = next.searchHistory.distinct()
            val nextQuerySet = nextQueries.toSet()
            previous.searchHistory
                .distinct()
                .filterNot(nextQuerySet::contains)
                .forEach { query ->
                    add(
                        PallaSyncPendingEvent(
                            schema = SEARCH_HISTORY_SCHEMA_V2,
                            entityId = query,
                            operation = SYNC_OPERATION_DELETE,
                            body = buildJsonObject { put("query", query) },
                        ),
                    )
                }
            // An upsert moves an item to the front. Send only the shortest prefix
            // that must move; a normal new/repeated search therefore emits one event.
            orderedMovedPrefix(previous.searchHistory, nextQueries) { it }
                .asReversed()
                .forEach { query ->
                    add(
                        PallaSyncPendingEvent(
                            schema = SEARCH_HISTORY_SCHEMA_V2,
                            entityId = query,
                            operation = SYNC_OPERATION_UPSERT,
                            body = buildJsonObject { put("query", query) },
                        ),
                    )
                }
        }

        addMuteChanges("tag", previous.mutedTags, next.mutedTags) { it }
        addMuteChanges("user", previous.mutedUsers, next.mutedUsers) { it.toString() }
        addMuteChanges("illust", previous.mutedIllusts, next.mutedIllusts) { it.toString() }

        if (previous.seenFeedIllusts != next.seenFeedIllusts) {
            val nextSeen = next.seenFeedIllusts.distinct()
            val nextSeenSet = nextSeen.toSet()
            previous.seenFeedIllusts
                .distinct()
                .filterNot(nextSeenSet::contains)
                .forEach { id -> add(seenIllustEvent(id, SYNC_OPERATION_DELETE)) }
            orderedMovedPrefix(previous.seenFeedIllusts, nextSeen) { it }
                .asReversed()
                .forEach { id -> add(seenIllustEvent(id, SYNC_OPERATION_UPSERT)) }
        }

        if (previous.viewHistory != next.viewHistory) {
            val nextItems = next.viewHistory.distinctBy(Illust::id)
            val nextIds = nextItems.mapTo(mutableSetOf(), Illust::id)
            previous.viewHistory
                .distinctBy(Illust::id)
                .filterNot { it.id in nextIds }
                .forEach { illust -> add(viewedIllustEvent(illust, SYNC_OPERATION_DELETE)) }
            orderedMovedPrefix(previous.viewHistory, nextItems, Illust::id)
                .asReversed()
                .forEach { illust -> add(viewedIllustEvent(illust, SYNC_OPERATION_UPSERT)) }
        }
    }

/**
 * Returns the smallest target prefix whose reverse upserts transform [previous]
 * into [next]. The untouched suffix must already have the same order and values.
 */
private fun <T, K> orderedMovedPrefix(
    previous: List<T>,
    next: List<T>,
    key: (T) -> K,
): List<T> {
    val previousDistinct = previous.distinctBy(key)
    val nextDistinct = next.distinctBy(key)
    val nextKeys = nextDistinct.mapTo(linkedSetOf(), key)
    val previousByKey = previousDistinct.associateBy(key)

    for (prefixSize in 0..nextDistinct.size) {
        val movedKeys = nextDistinct.take(prefixSize).mapTo(hashSetOf(), key)
        val untouchedPrevious =
            previousDistinct.filter { item ->
                val itemKey = key(item)
                itemKey in nextKeys && itemKey !in movedKeys
            }
        val targetSuffix = nextDistinct.drop(prefixSize)
        if (
            untouchedPrevious.map(key) == targetSuffix.map(key) &&
            targetSuffix.all { item -> previousByKey[key(item)] == item }
        ) {
            return nextDistinct.take(prefixSize)
        }
    }
    return nextDistinct
}

internal suspend fun PalleriaSyncManager.enqueueSettingsChanges(
    previous: AppSettings,
    next: AppSettings,
) {
    val events = buildSettingsSyncEvents(previous, next)
    if (events.isEmpty()) return
    enqueueDataEvents(events)
}

internal suspend fun PalleriaSyncManager.enqueueInitialSettings(settings: AppSettings) {
    val events = buildInitialSettingsSyncEvents(settings)
    if (events.isEmpty()) return
    enqueueDataEvents(events)
}

private fun MutableList<PallaSyncPendingEvent>.addStringSetChanges(
    schema: String,
    previous: List<String>,
    next: List<String>,
    entityId: (String) -> String,
    body: (String) -> kotlinx.serialization.json.JsonElement,
) {
    val previousValues = previous.distinct()
    val nextValues = next.distinct()
    val previousSet = previousValues.toSet()
    val nextSet = nextValues.toSet()
    previousValues.filterNot(nextSet::contains).forEach { value ->
        add(PallaSyncPendingEvent(schema, entityId(value), SYNC_OPERATION_DELETE, body(value)))
    }
    nextValues.filterNot(previousSet::contains).forEach { value ->
        add(PallaSyncPendingEvent(schema, entityId(value), SYNC_OPERATION_UPSERT, body(value)))
    }
}

private fun <T> MutableList<PallaSyncPendingEvent>.addMuteChanges(
    kind: String,
    previous: List<T>,
    next: List<T>,
    encode: (T) -> String,
) {
    val previousValues = previous.map(encode).distinct()
    val nextValues = next.map(encode).distinct()
    val previousSet = previousValues.toSet()
    val nextSet = nextValues.toSet()
    previousValues.filterNot(nextSet::contains).forEach { value ->
        add(muteEvent(kind, value, SYNC_OPERATION_DELETE))
    }
    nextValues.filterNot(previousSet::contains).forEach { value ->
        add(muteEvent(kind, value, SYNC_OPERATION_UPSERT))
    }
}

private fun seenIllustEvent(
    id: Long,
    operation: String,
): PallaSyncPendingEvent =
    PallaSyncPendingEvent(
        schema = VIEW_HISTORY_SCHEMA_V2,
        entityId = "seen:$id",
        operation = operation,
        body = buildJsonObject { put("id", id) },
    )

private fun muteEvent(
    kind: String,
    value: String,
    operation: String,
): PallaSyncPendingEvent =
    PallaSyncPendingEvent(
        schema = MUTE_SETTINGS_SCHEMA_V2,
        entityId = "$kind:$value",
        operation = operation,
        body =
            buildJsonObject {
                put("kind", kind)
                put("value", value)
            },
    )

private fun viewedIllustEvent(
    illust: Illust,
    operation: String,
): PallaSyncPendingEvent =
    PallaSyncPendingEvent(
        schema = VIEW_HISTORY_SCHEMA_V2,
        entityId = "viewed:${illust.id}",
        operation = operation,
        body =
            buildJsonObject {
                put("id", illust.id)
                put("title", illust.title)
                put("artistName", illust.artistName)
                put("imageUrl", illust.imageUrl)
                put("pageCount", illust.pageCount)
                put("type", illust.type)
                put("isBookmarked", illust.isBookmarked)
                put("xRestrict", illust.xRestrict)
                put("illustAiType", illust.illustAiType)
                put(
                    "tags",
                    kotlinx.serialization.json.buildJsonArray {
                        illust.tags.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    },
                )
            },
    )
