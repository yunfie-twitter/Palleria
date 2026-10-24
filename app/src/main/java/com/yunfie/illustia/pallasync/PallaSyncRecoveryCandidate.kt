package com.yunfie.illustia.pallasync

import androidx.annotation.Keep

/** Typed native ownership. The descriptor contains public keys; secret session keys stay native. */
@Keep
class PallaSyncRecoveryCandidate(
    val handle: Long,
    val descriptorJson: String,
) : AutoCloseable {
    override fun close() = PallaSyncCore.endRecoveryTransfer(handle)
}
