package com.yunfie.illustia.pallasync

import androidx.annotation.Keep

/** §7.11: a recovered root stays native. Close unclaimed ownership, including UI cancellation. */
@Keep
class PallaSyncRecoveryCompletion(
    private val handle: Long,
    val identityJson: String?,
    val thresholdTestPassed: Boolean,
) : AutoCloseable {
    private var claimed = false

    @Synchronized
    internal fun takeRoot(
        operation: PallaSyncRootSession.Operation,
        requestGuard: ((String) -> Unit)?,
        clock: (String) -> Long,
    ): PallaSyncRootSession {
        check(!claimed && handle > 0 && !thresholdTestPassed && identityJson != null) { "C_RECOVERY_AUTH_REQUIRED" }
        return PallaSyncRootSession.adopt(handle, operation, requestGuard, clock).also { claimed = true }
    }

    @Synchronized
    override fun close() {
        if (!claimed && handle > 0) PallaSyncCore.endRootSession(handle)
        claimed = true
    }
}
