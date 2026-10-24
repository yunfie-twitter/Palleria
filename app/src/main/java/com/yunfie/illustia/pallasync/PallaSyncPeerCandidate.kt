package com.yunfie.illustia.pallasync

import androidx.annotation.Keep

/** §18.1: a typed native handle and public self-signed request; no private JSON result. */
@Keep
class PallaSyncPeerCandidate(
    val handle: Long,
    val requestJson: String,
) : AutoCloseable {
    override fun close() = PallaSyncCore.endPeerSession(handle)
}
