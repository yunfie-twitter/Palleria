package com.yunfie.illustia.pallasync

import java.util.concurrent.atomic.AtomicLong

/** Public completion observation for UI/test synchronization; cannot authenticate or unwrap a key. */
internal object PallaSyncRecoveryAuthEvents {
    private val completed = AtomicLong()
    val successfulPrompts: Long get() = completed.get()

    fun succeeded() {
        completed.incrementAndGet()
    }
}
