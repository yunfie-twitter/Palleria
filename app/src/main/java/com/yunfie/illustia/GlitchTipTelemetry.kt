package com.yunfie.illustia

import android.content.Context
import io.sentry.Breadcrumb
import io.sentry.ISpan
import io.sentry.ITransaction
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SpanStatus
import io.sentry.android.core.SentryAndroid

/** Controls the GlitchTip-compatible Sentry SDK without bypassing telemetry consent. */
@Suppress("TooGenericExceptionCaught")
object GlitchTipTelemetry {
    private val lock = Any()

    @Volatile
    private var enabled = false

    fun isTelemetryEnabled(): Boolean = enabled

    fun setEnabled(
        context: Context,
        shouldEnable: Boolean,
    ) {
        synchronized(lock) {
            if (enabled == shouldEnable) return

            if (shouldEnable) {
                SentryAndroid.init(context.applicationContext) { options ->
                    options.tracesSampleRate = 1.0
                    options.isAttachStacktrace = true
                    options.isAttachThreads = true
                    options.isPrintUncaughtStackTrace = true
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
     * Records a non-fatal exception to GlitchTip if telemetry is enabled.
     * Ignores coroutine cancellation failures.
     */
    fun recordException(
        throwable: Throwable,
        tag: String? = null,
        extras: Map<String, Any?>? = null,
    ) {
        if (!enabled || throwable.isCancellationFailure()) return
        runCatching {
            Sentry.captureException(throwable) { scope ->
                if (!tag.isNullOrBlank()) {
                    scope.setTag("module", tag)
                }
                extras?.forEach { (key, value) ->
                    if (value != null) {
                        scope.setExtra(key, value.toString())
                    }
                }
            }
        }
    }

    /** Records an informational or diagnostic message to GlitchTip. */
    fun recordMessage(
        message: String,
        level: SentryLevel = SentryLevel.INFO,
        tag: String? = null,
    ) {
        if (!enabled || message.isBlank()) return
        runCatching {
            Sentry.captureMessage(message, level) { scope ->
                if (!tag.isNullOrBlank()) {
                    scope.setTag("module", tag)
                }
            }
        }
    }

    /** Adds a breadcrumb to the current Sentry scope. */
    fun addBreadcrumb(
        message: String,
        category: String? = null,
        data: Map<String, Any?>? = null,
    ) {
        if (!enabled || message.isBlank()) return
        runCatching {
            val breadcrumb =
                Breadcrumb().apply {
                    this.message = message
                    category?.let { this.category = it }
                    data?.forEach { (k, v) ->
                        if (v != null) setData(k, v)
                    }
                }
            Sentry.addBreadcrumb(breadcrumb)
        }
    }

    /** Flushes any queued events to the server within the given timeout. */
    fun flush(timeoutMillis: Long = 2000L) {
        if (!enabled) return
        runCatching {
            Sentry.flush(timeoutMillis)
        }
    }

    /**
     * Begins a performance transaction if telemetry is currently enabled.
     * Returns `null` when telemetry is disabled so callers can safely skip instrumentation.
     */
    fun startTransaction(
        name: String,
        operation: String,
    ): ITransaction? {
        if (!enabled) return null
        return Sentry.startTransaction(name, operation)
    }

    /** Executes [block] inside a performance transaction, finishing with [SpanStatus.OK] or recording failure. */
    inline fun <T> trace(
        name: String,
        operation: String,
        block: (ITransaction?) -> T,
    ): T {
        val tx = startTransaction(name, operation)
        return try {
            val result = block(tx)
            tx?.finish(SpanStatus.OK)
            result
        } catch (t: Throwable) {
            if (!t.isCancellationFailure()) {
                tx?.finish(SpanStatus.INTERNAL_ERROR)
                recordException(t, tag = "trace_$operation")
            } else {
                tx?.finish(SpanStatus.CANCELLED)
            }
            throw t
        }
    }

    /** Executes a suspending [block] inside a performance transaction. */
    suspend inline fun <T> traceAsync(
        name: String,
        operation: String,
        crossinline block: suspend (ITransaction?) -> T,
    ): T {
        val tx = startTransaction(name, operation)
        return try {
            val result = block(tx)
            tx?.finish(SpanStatus.OK)
            result
        } catch (t: Throwable) {
            if (!t.isCancellationFailure()) {
                tx?.finish(SpanStatus.INTERNAL_ERROR)
                recordException(t, tag = "trace_$operation")
            } else {
                tx?.finish(SpanStatus.CANCELLED)
            }
            throw t
        }
    }

    /** Creates and manages a child span within an existing transaction. */
    inline fun <T> ITransaction?.span(
        operation: String,
        description: String? = null,
        block: (ISpan?) -> T,
    ): T {
        val child = this?.startChild(operation, description)
        return try {
            val result = block(child)
            child?.finish(SpanStatus.OK)
            result
        } catch (t: Throwable) {
            child?.finish(SpanStatus.INTERNAL_ERROR)
            throw t
        }
    }

    /** Creates and manages a child span within an existing span. */
    inline fun <T> ISpan?.span(
        operation: String,
        description: String? = null,
        block: (ISpan?) -> T,
    ): T {
        val child = this?.startChild(operation, description)
        return try {
            val result = block(child)
            child?.finish(SpanStatus.OK)
            result
        } catch (t: Throwable) {
            child?.finish(SpanStatus.INTERNAL_ERROR)
            throw t
        }
    }
}
