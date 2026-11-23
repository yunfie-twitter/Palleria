package com.yunfie.illustia.platform

import com.yunfie.illustia.GlitchTipTelemetry

/** [Telemetry] の Android 実装。Sentry/GlitchTip SDK へ委譲する。 */
object AndroidTelemetry : Telemetry {
    override fun recordException(
        throwable: Throwable,
        tag: String?,
        extras: Map<String, Any?>?,
    ) = GlitchTipTelemetry.recordException(throwable, tag = tag, extras = extras)

    override fun <T> trace(
        name: String,
        operation: String,
        block: () -> T,
    ): T = GlitchTipTelemetry.trace(name, operation) { block() }

    override suspend fun <T> traceAsync(
        name: String,
        operation: String,
        block: suspend () -> T,
    ): T = GlitchTipTelemetry.traceAsync(name, operation) { block() }
}
