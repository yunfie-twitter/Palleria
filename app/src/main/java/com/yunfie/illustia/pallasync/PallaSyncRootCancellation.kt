package com.yunfie.illustia.pallasync

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** One explicit operation owns cancellation of its native root; an old screen cannot cancel a newer operation. */
class PallaSyncRootCancellation {
    private val cancelled = AtomicBoolean(false)
    private val attached = AtomicBoolean(false)
    private val session = AtomicReference<PallaSyncRootSession?>(null)

    internal fun attach(root: PallaSyncRootSession) {
        check(attached.compareAndSet(false, true) && session.compareAndSet(null, root)) { "C_RECOVERY_AUTH_REQUIRED" }
        if (cancelled.get()) {
            root.close()
            error("C_RECOVERY_AUTH_REQUIRED")
        }
    }

    internal fun release(root: PallaSyncRootSession) {
        session.compareAndSet(root, null)
    }

    fun cancel() {
        cancelled.set(true)
        session.get()?.close()
    }
}
