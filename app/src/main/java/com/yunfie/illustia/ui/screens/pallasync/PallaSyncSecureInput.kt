package com.yunfie.illustia.ui.screens.pallasync

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_CONTEXT_WRAPPERS = 32

/** Compose/theme contexts may wrap the window-owning activity. */
internal fun Context.pallaSyncInputActivity(): Activity? =
    generateSequence(this) { current ->
        (current as? ContextWrapper)?.baseContext?.takeUnless { it === current }
    }.take(MAX_CONTEXT_WRAPPERS).filterIsInstance<Activity>().firstOrNull()

/** Each input owns a lease; disposing an older input cannot unprotect another open input. */
internal object PallaSyncSecureInput {
    private data class Protection(
        val originallySecure: Boolean,
        var leases: Int,
    )

    private val windows = WeakHashMap<Window, Protection>()

    fun protect(activity: Activity): AutoCloseable {
        val window = activity.window
        synchronized(windows) {
            val protection =
                windows.getOrPut(window) {
                    Protection(window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0, 0)
                }
            protection.leases++
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        val closed = AtomicBoolean(false)
        return AutoCloseable {
            if (closed.compareAndSet(false, true)) {
                synchronized(windows) {
                    val protection = checkNotNull(windows[window])
                    protection.leases--
                    if (protection.leases == 0) {
                        windows.remove(window)
                        if (!protection.originallySecure) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
    }
}
