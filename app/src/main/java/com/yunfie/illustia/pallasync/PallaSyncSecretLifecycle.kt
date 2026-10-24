package com.yunfie.illustia.pallasync

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/** Observe process foreground without loading JNI for users who do not use synchronization. */
internal object PallaSyncSecretLifecycle {
    private var installed = false
    private var foreground = false
    private var nativeLoaded = false

    @Volatile var foregroundRevision = 0L
        private set

    @Synchronized
    fun install() {
        if (installed) return
        installed = true
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) = update(true)

                override fun onStop(owner: LifecycleOwner) = update(false)
            },
        )
    }

    @Synchronized
    private fun update(value: Boolean) {
        if (value && !foreground) foregroundRevision++
        foreground = value
        if (nativeLoaded) PallaSyncCore.setSecretForeground(value)
    }

    @Synchronized
    fun nativeReady() {
        nativeLoaded = true
        if (installed) PallaSyncCore.setSecretForeground(foreground)
    }
}
