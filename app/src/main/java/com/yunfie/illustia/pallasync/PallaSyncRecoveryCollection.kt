package com.yunfie.illustia.pallasync

/** §§5.5/11.4: a foreground ceremony owns a native collector; no piece/RMS is a JVM value. */
internal class PallaSyncRecoveryCollection private constructor(
    private val nativeHandle: Long,
    val ceremonyId: String,
) : AutoCloseable {
    private var closed = false
    val handle: Long get() {
        check(!closed) { "C_RECOVERY_SESSION_EXPIRED" }
        return nativeHandle
    }

    fun userInteraction() = PallaSyncCore.touchRecoveryCollection(handle)

    @Suppress("TooGenericExceptionCaught")
    fun receive(
        pair: PallaSyncRecoveryCandidate,
        packetJson: String,
    ): String =
        try {
            PallaSyncCore.receiveRecoveryCollector(handle, pair.handle, packetJson)
        } catch (failure: Throwable) {
            if (failure !is IllegalStateException || failure.message != "C_MISSING_DEPENDENCY") {
                close()
                pair.close()
            }
            throw failure
        }

    fun finish(explicitlyConfirmed: Boolean): PallaSyncRecoveryCompletion =
        try {
            PallaSyncCore.finishRecoveryCollection(handle, explicitlyConfirmed)
        } finally {
            close()
        }

    override fun close() {
        if (!closed) {
            closed = true
            PallaSyncCore.endRecoveryCollection(nativeHandle)
        }
    }

    companion object {
        fun setupTest(
            manifestJson: String,
            statusesJson: String,
            root: PallaSyncRootSession,
            knownHead: String?,
            ceremonyId: String,
            explicitlyConfirmed: Boolean,
        ): PallaSyncRecoveryCollection {
            check(
                root.operation == PallaSyncRootSession.Operation.RECOVERY_CREATE ||
                    root.operation == PallaSyncRootSession.Operation.RECOVERY_ROTATE,
            ) {
                "C_RECOVERY_AUTH_REQUIRED"
            }
            return begin(
                manifestJson,
                statusesJson,
                root.identity.adminPublicKey,
                knownHead,
                ceremonyId,
                root.operation,
                root.handle,
                explicitlyConfirmed,
                false,
                false,
            )
        }

        @Suppress("LongParameterList")
        fun recover(
            manifestJson: String,
            statusesJson: String,
            trustedAdmin: String,
            knownHead: String?,
            ceremonyId: String,
            operation: PallaSyncRootSession.Operation,
            explicitlyConfirmed: Boolean,
            compromised: Boolean,
            emergencyNewRoot: Boolean,
        ): PallaSyncRecoveryCollection =
            begin(
                manifestJson,
                statusesJson,
                trustedAdmin,
                knownHead,
                ceremonyId,
                operation,
                0,
                explicitlyConfirmed,
                compromised,
                emergencyNewRoot,
            )

        @Suppress("LongParameterList")
        private fun begin(
            manifestJson: String,
            statusesJson: String,
            trustedAdmin: String,
            knownHead: String?,
            ceremonyId: String,
            operation: PallaSyncRootSession.Operation,
            setupRoot: Long,
            explicitlyConfirmed: Boolean,
            compromised: Boolean,
            emergencyNewRoot: Boolean,
        ): PallaSyncRecoveryCollection {
            val native =
                PallaSyncCore.beginRecoveryCollection(
                    manifestJson,
                    statusesJson,
                    trustedAdmin,
                    knownHead,
                    ceremonyId,
                    operation.wire,
                    setupRoot,
                    explicitlyConfirmed,
                    compromised,
                    emergencyNewRoot,
                )
            @Suppress("TooGenericExceptionCaught")
            try {
                return PallaSyncRecoveryCollection(native, ceremonyId)
            } catch (failure: Throwable) {
                PallaSyncCore.endRecoveryCollection(native)
                throw failure
            }
        }
    }
}
