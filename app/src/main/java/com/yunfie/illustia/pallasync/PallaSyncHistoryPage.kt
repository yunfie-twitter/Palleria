package com.yunfie.illustia.pallasync

import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.pallasync.data.HistoryCursor

/** Local display result, never a relay/wire model (§16.5). */
data class PallaSyncHistoryPage(
    val items: List<Illust>,
    val next: HistoryCursor?,
    val partial: Boolean,
)

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
) {
    val partial: Boolean
        get() =
            !initialPullCompleted || verifiedSeq < downloadedSeq || materializedSeq < verifiedSeq ||
                verifiedTransparencyIndex < observedTransparencyTargetIndex || transparencyObservationConflict
}
