package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Explicit local UI intent. Cache eviction/paging never constructs one (§8.2/§8.4/§16). */
sealed interface PallaSyncHistoryEdit {
    data object ClearSearch : PallaSyncHistoryEdit

    data object ClearViewed : PallaSyncHistoryEdit

    data object ClearSeen : PallaSyncHistoryEdit

    data class RemoveQuery(
        val query: String,
    ) : PallaSyncHistoryEdit

    data class RemoveViewed(
        val ids: List<Long>,
    ) : PallaSyncHistoryEdit

    data class RemoveSeen(
        val ids: List<Long>,
    ) : PallaSyncHistoryEdit
}

internal fun List<PallaSyncHistoryEdit>.events(): List<PallaSyncPendingEvent> =
    flatMap { edit ->
        when (edit) {
            PallaSyncHistoryEdit.ClearSearch -> listOf(historyClear(SEARCH_HISTORY_SCHEMA_V3, "all"))
            PallaSyncHistoryEdit.ClearViewed -> listOf(historyClear(VIEW_HISTORY_SCHEMA_V3, "viewed"))
            PallaSyncHistoryEdit.ClearSeen -> listOf(historyClear(VIEW_HISTORY_SCHEMA_V3, "seen"))
            is PallaSyncHistoryEdit.RemoveQuery -> listOf(historyDelete(SEARCH_HISTORY_SCHEMA_V3, edit.query))
            is PallaSyncHistoryEdit.RemoveViewed -> edit.ids.distinct().map { historyDelete(VIEW_HISTORY_SCHEMA_V3, "viewed:$it") }
            is PallaSyncHistoryEdit.RemoveSeen -> edit.ids.distinct().map { historyDelete(VIEW_HISTORY_SCHEMA_V3, "seen:$it") }
        }
    }

private fun historyClear(
    collection: String,
    scope: String,
) = PallaSyncPendingEvent(
    collection,
    "*",
    "clear",
    buildJsonObject {},
    buildJsonObject { put("scope", scope) },
)

private fun historyDelete(
    collection: String,
    id: String,
) = PallaSyncPendingEvent(collection, id, "delete", buildJsonObject {})
