package com.yunfie.illustia.pallasync

/** §18.1: only the foreground caller owns the native session. No automatic SAS confirmation. */
internal class PallaSyncPeerSession private constructor(
    val handle: Long,
    val requestJson: String? = null,
) : AutoCloseable {
    fun sas(nowMs: Long): String = PallaSyncCore.peerSas(handle, nowMs)

    fun bindRequest(
        requestJson: String,
        nowMs: Long,
    ): String = PallaSyncCore.bindPeerRequest(handle, requestJson, nowMs)

    fun confirm(
        observed: String,
        confirmed: Boolean,
        nowMs: Long,
    ) = PallaSyncCore.confirmPeerSas(handle, observed, confirmed, nowMs)

    fun certificate(nowMs: Long): String = PallaSyncCore.peerCertificate(handle, nowMs)

    fun acceptCertificate(
        certificateJson: String,
        nowMs: Long,
    ) = PallaSyncCore.acceptPeerCertificate(handle, certificateJson, nowMs)

    fun approval(
        authorityJson: String,
        bodyJson: String,
        nowMs: Long,
    ): String = PallaSyncCore.validatePeerApproval(handle, authorityJson, bodyJson, nowMs)

    override fun close() = PallaSyncCore.endPeerSession(handle)

    // Release native ownership on JVM parse, persistence or cancellation failure, then rethrow unchanged.
    @Suppress("TooGenericExceptionCaught")
    internal inline fun <T> checked(action: () -> T): T =
        try {
            action()
        } catch (failure: Throwable) {
            close()
            throw failure
        }

    fun envelope(
        authorityJson: String,
        epoch: Long,
        secret: ByteArray,
        nowMs: Long,
    ): String =
        try {
            PallaSyncCore.preparePeerEnvelope(handle, authorityJson, epoch, secret, nowMs)
        } finally {
            secret.fill(0)
        }

    fun prepareSubject(
        authorityJson: String,
        envelopeJson: String,
        metadataJson: String,
        nowMs: Long,
    ): PallaSyncPreparedKeys = PallaSyncCore.preparePeerSubject(handle, authorityJson, envelopeJson, metadataJson, nowMs)

    companion object {
        @Suppress("TooGenericExceptionCaught") // A failed JVM wrapper allocation must release its already allocated native handle.
        fun subject(
            offerJson: String,
            authorityJson: String,
            explicitlySelected: Boolean,
            nowMs: Long,
        ): PallaSyncPeerSession {
            val candidate = PallaSyncCore.beginPeerSubject(offerJson, authorityJson, explicitlySelected, nowMs)
            return try {
                PallaSyncPeerSession(candidate.handle, candidate.requestJson)
            } catch (error: Throwable) {
                candidate.close()
                throw error
            }
        }

        /** Takes ownership of temporary mutable OS-unwrapped device-key buffers. */
        @Suppress("TooGenericExceptionCaught") // Preserve throwable semantics after releasing native/key-buffer ownership.
        fun approver(
            offerJson: String,
            authorityJson: String,
            signingSeed: ByteArray,
            kexSecret: ByteArray,
            nowMs: Long,
        ): PallaSyncPeerSession {
            var handle = 0L
            return try {
                handle = PallaSyncCore.beginPeerApprover(offerJson, authorityJson, signingSeed, kexSecret, nowMs)
                PallaSyncPeerSession(handle)
            } catch (error: Throwable) {
                if (handle > 0) PallaSyncCore.endPeerSession(handle)
                throw error
            } finally {
                signingSeed.fill(0)
                kexSecret.fill(0)
            }
        }
    }
}
