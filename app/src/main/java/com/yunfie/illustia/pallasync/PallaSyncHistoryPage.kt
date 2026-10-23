package com.yunfie.illustia.pallasync

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.pallasync.data.HistoryCursor
import com.yunfie.illustia.pallasync.data.QuarantineSummaryRow

/** Local display result, never a relay/wire model (§16.5). */
data class PallaSyncHistoryPage(
    val items: List<Illust>,
    val next: HistoryCursor?,
    val partial: Boolean,
)

data class PallaSyncQuarantinePage(
    val items: List<QuarantineSummaryRow>,
    val nextHash: String?,
)

/** §19.4 permits explicit exclusion only for terminally invalid originals, never as proof of resolution. */
internal object PallaSyncQuarantineDisposition {
    private val independentCheckpointReviewReasons =
        setOf(
            "C_RECEIPT_INVALID",
            "C_TRANSPARENCY_INVALID",
        )

    val trustedBranchReviewReasons =
        setOf(
            "C_EPOCH_FORK",
            "C_ROLLBACK_DETECTED",
            "C_DEVICE_EQUIVOCATION",
        )

    private val explicitlyExcludableReasons =
        setOf(
            "C_SCHEMA_VIOLATION",
            "C_SIGNATURE_INVALID",
            "C_AAD_MISMATCH",
            "C_AEAD_FAILURE",
            "C_COLLECTION_TAG_MISMATCH",
        )

    fun canExplicitlyExclude(reason: String): Boolean = reason in explicitlyExcludableReasons

    fun requiresTrustedBranchReview(reason: String): Boolean = reason in trustedBranchReviewReasons

    fun requiresIndependentCheckpointReview(reason: String): Boolean = reason in independentCheckpointReviewReasons
}

/** Unsigned relay error context shown only as a diagnostic hint; it never authorizes a retry. */
data class PallaSyncCapacityIssue(
    val code: String,
    val usedBytes: Long?,
    val maxBytes: Long?,
    val observedAtMs: Long,
)

/** Last locally observed, independently authenticated device checkpoint comparison (§11.5). */
data class PallaSyncIndependentCheckpointComparison(
    val checkedAtMs: Long,
    val generation: Long,
    val peerDeviceId: String,
    val dataSequence: Long,
    val transparencyIndex: Long,
)

internal object PallaSyncIndependentCheckpointPolicy {
    fun isEnrolledPeerComparison(
        localDeviceId: String?,
        checkpointDeviceId: String,
    ): Boolean = !localDeviceId.isNullOrBlank() && checkpointDeviceId.isNotBlank() && checkpointDeviceId != localDeviceId
}

/** Local bounded status summary; sequence positions are independent verified pipeline marks. */
data class PallaSyncClientStatus(
    val downloadedSeq: Long,
    val verifiedSeq: Long,
    val materializedSeq: Long,
    val verifiedTransparencyIndex: Long,
    val observedTransparencyTargetIndex: Long,
    val transparencyObservationConflict: Boolean,
    val pendingOutbox: Long,
    val authorGaps: Long,
    val unresolvedQuarantine: Long,
    val writeState: String,
    val initialPullCompleted: Boolean,
    val searchCount: Long,
    val searchCeiling: Long,
    val viewedCount: Long,
    val viewedCeiling: Long,
    val seenCount: Long,
    val seenCeiling: Long,
    val searchProjectionCount: Long = minOf(searchCount, searchCeiling),
    val viewedProjectionCount: Long = minOf(viewedCount, viewedCeiling),
    val seenProjectionCount: Long = minOf(seenCount, seenCeiling),
    val availableStorageBytes: Long? = null,
    val retryAfterMs: Long = 0,
    val capacityIssue: PallaSyncCapacityIssue? = null,
    val blockedRequestCode: String? = null,
    val blockedRequestCodes: List<String> = emptyList(),
    val originBlockedRequestCodes: List<String> = emptyList(),
    val favoriteOrderReindexRequired: Boolean = false,
    val favoriteCount: Long = 0,
    val favoriteProjectionCount: Long = minOf(favoriteCount, PALLASYNC_PAGE_SIZE.toLong()),
    val muteCount: Long = 0,
    val muteProjectionCount: Long = minOf(muteCount, PALLASYNC_PAGE_SIZE.toLong()),
    val lastIndependentCheckpointComparison: PallaSyncIndependentCheckpointComparison? = null,
    val lastConsistencyProbeAtMs: Long? = null,
    val consistencyProbeIntervalMs: Long = PallaSyncPolicyGate.DEFAULT_CONSISTENCY_PROBE_INTERVAL_MS,
    val consistencyProbeDeferred: Boolean = false,
    val consistencyProbeDelayMs: Long? = null,
    val pendingRecordDispositions: Long = 0,
) {
    val searchColdCount: Long get() = (searchCount - searchProjectionCount).coerceAtLeast(0)
    val viewedColdCount: Long get() = (viewedCount - viewedProjectionCount).coerceAtLeast(0)
    val seenColdCount: Long get() = (seenCount - seenProjectionCount).coerceAtLeast(0)
    val favoriteColdCount: Long get() = (favoriteCount - favoriteProjectionCount).coerceAtLeast(0)
    val muteColdCount: Long get() = (muteCount - muteProjectionCount).coerceAtLeast(0)
    val safetyLimitReached: Boolean
        get() = searchCount >= searchCeiling || viewedCount >= viewedCeiling || seenCount >= seenCeiling

    val partial: Boolean
        get() =
            !initialPullCompleted || verifiedSeq < downloadedSeq || materializedSeq < verifiedSeq ||
                verifiedTransparencyIndex < observedTransparencyTargetIndex || transparencyObservationConflict
}
