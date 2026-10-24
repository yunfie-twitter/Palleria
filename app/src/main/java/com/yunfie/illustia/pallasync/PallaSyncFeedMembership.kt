package com.yunfie.illustia.pallasync

import com.yunfie.illustia.models.Illust

/** §16.5: an absent row is unknown until materialization covers the selected prefix. */
internal data class PallaSyncFeedMembership(
    val seen: Set<Long>,
    val muted: Set<Long>,
    val complete: Boolean,
) {
    fun priority(id: Long): Int =
        when {
            id in seen -> 2
            complete -> 0
            else -> 1
        }
}

internal class PallaSyncMembershipReader(
    private val store: PallaSyncLocalStore,
) {
    suspend fun read(items: List<Illust>): PallaSyncFeedMembership? =
        store.transaction {
            val state = store.dao.getActiveChainState() ?: return@transaction null
            val dao = store.protocolDao()

            suspend fun lookup(
                collection: String,
                ids: Iterable<String>,
            ): Set<String> =
                ids
                    .distinct()
                    .chunked(PALLASYNC_PAGE_SIZE)
                    .flatMap { dao.liveEntityIds(state.chainId, collection, it) }
                    .toSet()
            dao.historyProjection(state.chainId, VIEW_HISTORY_SCHEMA_V3, "seen")
            val seen =
                items
                    .map { "seen:${it.id}" }
                    .distinct()
                    .chunked(PALLASYNC_PAGE_SIZE)
                    .flatMap { dao.retainedHistoryIds(state.chainId, VIEW_HISTORY_SCHEMA_V3, "seen", it) }
                    .map { it.substringAfter(':').toLong() }
                    .toSet()
            val muteKeys =
                items.flatMap { item ->
                    listOf("illust:${item.id}", "user:${item.artistId}") + item.tags.map { "tag:$it" }
                }
            val muted = lookup(MUTE_SETTINGS_SCHEMA_V3, muteKeys)
            PallaSyncFeedMembership(
                seen = seen,
                muted =
                    items
                        .filter { item ->
                            "illust:${item.id}" in muted || "user:${item.artistId}" in muted || item.tags.any { "tag:$it" in muted }
                        }.map(Illust::id)
                        .toSet(),
                complete = state.initialPullCompleted && state.materializedSeq == state.downloadedSeq,
            )
        }
}
