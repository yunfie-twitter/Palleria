package com.yunfie.illustia.pallasync

import kotlinx.serialization.Serializable

/** Local intent/source mapping; never a wire object or a recovery artifact. */
@Serializable
internal data class PallaSyncReplayWork(
    val event: PallaSyncPendingEvent,
    val sourceOutboxId: Long? = null,
    val destinationRecordId: String? = null,
    val sourceEvent: PallaSyncPendingEvent? = null,
    val targetGeneration: Long? = null,
    val localPrepared: Boolean = false,
    val blockedCode: String? = null,
    /** Marks state whose legacy last-writer winner and initialization order follow verified source sequence. */
    val legacyEventSequenceOrdered: Boolean = false,
    /** v2.0 favorite snapshots merge by distinct membership; first occurrence fixes list order. */
    val legacyFavoriteSnapshotUnique: Boolean = false,
)
