package com.yunfie.illustia.pallasync

import androidx.annotation.Keep

/** Snapshot-only native validation entry points, kept separate from normal InnerRecord APIs. */
@Keep
internal object PallaSyncSnapshotCore {
    /** §10.11: verify snapshot-part ciphertext hash and its null-sequence unchanged-head receipt. */
    external fun validateSnapshotPartResponse(
        body: ByteArray,
        expectedRecordId: String,
        pinJson: String,
    ): String
}
