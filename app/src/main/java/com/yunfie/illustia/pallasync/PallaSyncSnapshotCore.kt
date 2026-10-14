package com.yunfie.illustia.pallasync

import androidx.annotation.Keep

/** Snapshot-only native validation entry points, kept separate from normal InnerRecord APIs. */
@Keep
internal object PallaSyncSnapshotCore {
    init {
        PallaSyncCore.ensureNativeLibraryLoaded()
    }

    /** Begin bounded streaming validation for independently authenticated, decrypted parts. */
    external fun beginVerification(
        recordKey: ByteArray,
        headerJson: String,
        compactionId: String,
        partCount: Int,
    ): Long

    /** Start a producer commitment stream with the epoch session's private record key. */
    external fun beginFromEpochSession(
        epochHandle: Long,
        headerJson: String,
        compactionId: String,
        partCount: Int,
    ): Long

    external fun acceptVerifiedPart(
        handle: Long,
        partJson: String,
    ): Boolean

    external fun finishVerification(
        handle: Long,
        expectedCommitment: String,
    ): Boolean

    /** Finish a complete producer stream and return its commitment, consuming the handle. */
    external fun finishCommitment(handle: Long): String

    external fun abortVerification(handle: Long)

    /** §10.11: verify snapshot-part ciphertext hash and its null-sequence unchanged-head receipt. */
    external fun validateSnapshotPartResponse(
        body: ByteArray,
        expectedRecordId: String,
        pinJson: String,
    ): String

    /** §10.12: validate the strict, bounded CompactionRecord catalogue response. */
    external fun validateCompactionPage(
        body: ByteArray,
        pinJson: String,
        generation: Long,
        headSeq: Long,
        limit: Int,
    ): String

    /** Create a signed, encrypted §16.6 part using epoch keys held by the native session. */
    external fun createSnapshotRecord(
        handle: Long,
        recordId: String,
        deviceId: String,
        partJson: String,
        signingSeed: ByteArray,
    ): String
}
