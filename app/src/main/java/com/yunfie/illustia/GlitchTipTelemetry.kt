package com.yunfie.illustia

import android.content.Context
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SpanStatus
import io.sentry.android.core.SentryAndroid

/** Controls the GlitchTip-compatible Sentry SDK without bypassing telemetry consent. */
object GlitchTipTelemetry {
    private val lock = Any()

    @Volatile
    private var enabled = false

    fun setEnabled(
        context: Context,
        shouldEnable: Boolean,
    ) {
        synchronized(lock) {
            if (enabled == shouldEnable) return

            if (shouldEnable) {
                SentryAndroid.init(context.applicationContext) { options ->
                    // GlitchTip does not support Sentry session tracking.
                    options.isEnableAutoSessionTracking = false
                    options.isSendDefaultPii = false
                    // Frame metrics and PerformanceV2 use Sentry-specific envelope
                    // types that GlitchTip does not yet process.
                    options.isEnableFramesTracking = false
                    @Suppress("DEPRECATION")
                    options.isEnablePerformanceV2 = false
                }
                enabled = Sentry.isEnabled()
            } else {
                Sentry.close()
                enabled = false
            }
        }
    }

    /**
     * Begins a performance transaction if telemetry is currently enabled.
     *
     * The transaction sample rate is governed by the manifest value
     * `io.sentry.traces.sample-rate` (currently 1 %). Returns `null` when
     * telemetry is disabled so callers can safely skip instrumentation without
     * any extra consent checks.
     *
     * The caller is responsible for calling [ITransaction.finish] (or
     * [ITransaction.finish] with a [SpanStatus]) when the measured operation
     * completes.
     */
    fun startTransaction(
        name: String,
        operation: String,
    ): ITransaction? {
        if (!enabled) return null
        return Sentry.startTransaction(name, operation)
    }
}
