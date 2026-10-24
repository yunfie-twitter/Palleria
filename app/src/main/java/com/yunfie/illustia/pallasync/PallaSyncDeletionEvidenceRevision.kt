package com.yunfie.illustia.pallasync

/** A locally retained, cryptographically revalidated bundle bound to one immutable receipt revision. */
internal data class PallaSyncDeletionEvidenceRevision(
    val transparencyIndex: Long,
    val completedAtMs: Long?,
    val archiveJson: String,
)
