package com.yunfie.illustia.pallasync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

internal const val FAVORITE_TAG_SCHEMA_V1 = "palleria.favorite_tag/1"
internal const val FAVORITE_TAG_SCHEMA_V2 = "palleria.favorite_tag/2"
internal const val SEARCH_HISTORY_SCHEMA_V1 = "palleria.search_history/1"
internal const val SEARCH_HISTORY_SCHEMA_V2 = "palleria.search_history/2"
internal const val MUTE_SETTINGS_SCHEMA_V1 = "palleria.mute_settings/1"
internal const val MUTE_SETTINGS_SCHEMA_V2 = "palleria.mute_settings/2"
internal const val VIEW_HISTORY_SCHEMA_V1 = "palleria.view_history/1"
internal const val VIEW_HISTORY_SCHEMA_V2 = "palleria.view_history/2"

internal const val SYNC_OPERATION_UPSERT = "upsert"
internal const val SYNC_OPERATION_DELETE = "delete"

@Serializable
data class DataPayload(
    val schema: String,
    val entity_id: String,
    val operation: String,
    val context: Map<String, Long>,
    val lamport: Long,
    val created_at_ms: Long,
    val body: JsonElement,
)

@Serializable
data class FavoriteTagBody(
    val tag: String,
)

@Serializable
data class SearchHistoryBody(
    val query: String,
)

@Serializable
data class MuteSettingBody(
    val kind: String,
    val value: String,
)

@Serializable
data class SeenIllustBody(
    val id: Long,
)

@Serializable
data class ViewedIllustBody(
    val id: Long,
    val title: String,
    val artistName: String,
    val imageUrl: String,
    val pageCount: Int,
    val type: String,
    val isBookmarked: Boolean = false,
    val xRestrict: Int = 0,
    val tags: List<String> = emptyList(),
    val illustAiType: Int = 0,
)

internal data class PallaSyncPendingEvent(
    val schema: String,
    val entityId: String,
    val operation: String,
    val body: JsonElement,
)

internal sealed interface PallaSyncApplyResult {
    data object Applied : PallaSyncApplyResult

    data class Quarantined(
        val reason: String,
    ) : PallaSyncApplyResult
}
