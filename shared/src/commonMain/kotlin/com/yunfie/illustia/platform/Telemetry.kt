package com.yunfie.illustia.platform

/**
 * ViewModel 層が必要とするテレメトリ境界。
 *
 * Sentry / GlitchTip SDK への直接依存をここに閉じ込める。ブロックには計測用の
 * トランザクションを渡さない（ViewModel 層はどれも参照していないため）。
 */
interface Telemetry {
    fun recordException(
        throwable: Throwable,
        tag: String? = null,
        extras: Map<String, Any?>? = null,
    )

    fun <T> trace(
        name: String,
        operation: String,
        block: () -> T,
    ): T

    suspend fun <T> traceAsync(
        name: String,
        operation: String,
        block: suspend () -> T,
    ): T
}
