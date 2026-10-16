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
    /** Marks a v2.1 /2 event whose legacy state is replayed in verified relay-sequence order. */
    val legacyEventSequenceOrdered: Boolean = false,
    /** v2.0 favorite snapshots merge by distinct membership; first occurrence fixes list order. */
    val legacyFavoriteSnapshotUnique: Boolean = false,
)
