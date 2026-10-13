package com.yunfie.illustia.pallasync

/** §§7.11.5/18.6: publishes session ownership before a cancellable dispatcher returns it to the UI. */
internal class PallaSyncRecoveryRecipientOwner : AutoCloseable {
    private var recipient: PallaSyncRecoveryRecipient? = null
    private var closed = false
    var protection: String? = null
        private set

    @Synchronized
    @Suppress("LongParameterList")
    fun begin(
        chain: PallaSyncRecoveryChainContext,
        manifest: String,
        statuses: String,
        peer: String,
        instance: String,
        holder: PallaSyncRecoveryHolder? = null,
        replacementConfirmed: Boolean = false,
    ) {
        check(!closed && recipient == null) { "C_RECOVERY_SESSION_EXPIRED" }
        val admission = holder?.prepare(chain.deviceId, replacementConfirmed)
        recipient = PallaSyncRecoveryRecipient.begin(chain, manifest, statuses, peer, instance, holder, admission)
        protection = admission?.key?.protection ?: "passphrase-wrapped"
    }

    @Synchronized
    fun current(): PallaSyncRecoveryRecipient {
        check(!closed) { "C_RECOVERY_SESSION_EXPIRED" }
        return checkNotNull(recipient) { "C_RECOVERY_AUTH_REQUIRED" }
    }

    @Synchronized
    override fun close() {
        closed = true
        recipient?.close()
        recipient = null
    }
}
