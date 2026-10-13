package com.yunfie.illustia.pallasync

/** §§5.5/18.6: a UI scope owns preparation, including cancellation during derivation/publication.
 * Construction performs no setup. Close does not delete existing protected holder packages.
 */
internal class PallaSyncRecoverySetupOwner(
    private val store: PallaSyncRecoverySetStore,
    private val previous: PallaSyncRecoverySetStore.Entry? = null,
) : AutoCloseable {
    private val cancellation = PallaSyncRootCancellation()
    private val lock = Any()
    private var started = false
    private var closed = false
    private var setup: PallaSyncRecoverySetup? = null
    val auditStorageFailed: Boolean get() = store.auditQueue.storageFailed

    /** Run off the UI thread. Ownership is installed before a coroutine dispatcher can discard its result. */
    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    fun prepare(
        chain: PallaSyncRecoveryChainContext,
        configuration: PallaSyncRecoveryConfiguration,
        mnemonic: String,
        passphrase: String,
        nowMs: Long,
    ) {
        synchronized(lock) {
            check(!closed && !started) { "C_RECOVERY_AUTH_REQUIRED" }
            started = true
        }
        try {
            val holders = configuration.holdersJson()
            val root =
                PallaSyncRootSession.beginText(
                    mnemonic,
                    passphrase,
                    if (previous ==
                        null
                    ) {
                        PallaSyncRootSession.Operation.RECOVERY_CREATE
                    } else {
                        PallaSyncRootSession.Operation.RECOVERY_ROTATE
                    },
                    chain.chainId,
                    chain.adminPublicKey,
                )
            val prepared =
                try {
                    cancellation.attach(root)
                    PallaSyncRecoverySetup.create(root, store, chain.generation, configuration.threshold.toLong(), holders, nowMs, previous)
                } catch (failure: Throwable) {
                    root.close()
                    throw failure
                }
            synchronized(lock) {
                if (closed) {
                    prepared.close()
                    error("C_RECOVERY_SESSION_EXPIRED")
                }
                setup = prepared
            }
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    /** Serialize operations against disposal. No private root/RSWK/piece is exposed by this owner. */
    fun <T> usePrepared(action: (PallaSyncRecoverySetup) -> T): T =
        synchronized(lock) {
            check(!closed) { "C_RECOVERY_SESSION_EXPIRED" }
            action(checkNotNull(setup) { "C_MISSING_DEPENDENCY" })
        }

    override fun close() {
        cancellation.cancel()
        synchronized(lock) {
            if (!closed) {
                closed = true
                val previous = setup
                setup = null
                previous?.close()
            }
        }
    }
}
