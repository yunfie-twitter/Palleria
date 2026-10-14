package com.yunfie.illustia.pallasync

/** Foreground-only owner for one SAS-bound approver session (§18.1). */
internal data class PallaSyncPeerApprovalWork(
    var transcript: PallaSyncPeerJournal.Transcript,
    val session: PallaSyncPeerSession,
    val sas: String,
) : AutoCloseable {
    override fun close() = session.close()
}
