package com.yunfie.illustia.pallasync

import android.content.SharedPreferences
import android.os.SystemClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okio.HashingSink
import okio.blackholeSink
import okio.buffer
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/** §§6.3/17.8. Public retry state is protected and backup-excluded with policy state. */
@Suppress("TooManyFunctions")
internal class PallaSyncRetryGate(
    private val prefs: SharedPreferences,
    private val boot: () -> Long,
    private val wall: () -> Long = System::currentTimeMillis,
    private val elapsed: () -> Long = SystemClock::elapsedRealtime,
    private val random: (Long) -> Long = { Random.nextLong(it + 1) },
) {
    @Serializable
    internal data class State(
        val attempt: Int = 0,
        val nextWall: Long = 0,
        val nextElapsed: Long = 0,
        val sampledElapsed: Long = 0,
        val bootId: Long = -1,
        val waitMs: Long = 0,
        val offsetMs: Long = 0,
        val clockFailures: Int = 0,
        val uncertainScopes: Set<String> = emptySet(),
        val blockedRequests: Map<String, String> = emptyMap(),
        val relayShuttingDown: Boolean = false,
        val shutdownAtMs: Long? = null,
        val migrationOrigin: String? = null,
    )

    data class RelayShutdownNotice(
        val shutdownAtMs: Long?,
        val migrationOrigin: String?,
        val writesStopped: Boolean,
    )

    fun remaining(origin: String): Long =
        synchronized(lock) {
            val state = load(origin)
            if (state.nextWall == 0L) return@synchronized 0L
            if (state.bootId == boot() && elapsed() >= state.sampledElapsed) {
                val wait = (state.nextElapsed - elapsed()).coerceAtLeast(0)
                if (wait == 0L) save(origin, state.copy(nextWall = 0, nextElapsed = 0, waitMs = 0))
                wait
            } else {
                // A new boot/uncertain monotonic clock never bypasses the previous minimum.
                val wait = maxOf((state.nextWall - wall()).coerceAtLeast(0), state.waitMs)
                save(origin, deadline(state, wait))
                wait
            }
        }

    fun correctedTime(origin: String): Long =
        synchronized(lock) {
            val time = Math.addExact(wall(), load(origin).offsetMs)
            check(time in 0..MAX_SAFE_INTEGER - TOKEN_TTL_MS) { "C_SCHEMA_VIOLATION" }
            time
        }

    fun hasDeferredRetry(origin: String): Boolean = synchronized(lock) { load(origin).attempt > 0 }

    fun before(request: Request): PallaSyncHttpResult<Nothing>? {
        val origin = PallaSyncUrls.extractOrigin(request.url)
        synchronized(lock) {
            val state = load(origin)
            if (state.relayShuttingDown && request.method != "GET") {
                return PallaSyncHttpResult.ProtocolError("RELAY_SHUTTING_DOWN", 503)
            }
        }
        val wait = remaining(origin)
        if (wait > 0) return PallaSyncHttpResult.Retryable("PallaSync retry is deferred", wait)
        return synchronized(lock) {
            val state = load(origin)
            val key = fingerprint(request)
            val blocked = state.blockedRequests[key]
            when {
                blocked != null -> {
                    PallaSyncHttpResult.ProtocolError(blocked)
                }

                request.method != "GET" && scope(request) in state.uncertainScopes -> {
                    PallaSyncHttpResult.ProtocolError("C_ACCEPTANCE_UNPROVEN")
                }

                else -> {
                    null
                }
            }
        }
    }

    fun observe(
        request: Request,
        result: PallaSyncHttpResult<*>,
        problem: PallaSyncProblemHint? = null,
        date: String? = null,
    ) = synchronized(lock) {
        val origin = PallaSyncUrls.extractOrigin(request.url)
        var state = load(origin)
        if (problem?.code == "RELAY_SHUTTING_DOWN" && result is PallaSyncHttpResult.ProtocolError) {
            val candidate = problem.migrationOrigin?.let(::validatedMigrationOrigin)
            state =
                state.copy(
                    attempt = 0,
                    nextWall = 0,
                    nextElapsed = 0,
                    waitMs = 0,
                    relayShuttingDown = true,
                    shutdownAtMs = problem.shutdownAtMs ?: state.shutdownAtMs,
                    migrationOrigin = candidate ?: state.migrationOrigin,
                )
            save(origin, state)
            return@synchronized
        }
        val clockFailure = problem?.code in setOf("TOKEN_EXPIRED", "TOKEN_NOT_YET_VALID")
        val failures = if (clockFailure) (state.clockFailures + 1).coerceAtMost(2) else 0
        var offset = state.offsetMs
        if (failures >= 2) {
            val serverTime = date?.let(::dateMillis) ?: problem?.serverTimeMs
            if (serverTime != null) offset = Math.subtractExact(serverTime, wall())
        }
        state = state.copy(clockFailures = failures, offsetMs = offset)
        state =
            when (result) {
                is PallaSyncHttpResult.Retryable -> {
                    val base = minOf(MAX_BASE_MS, START_BASE_MS shl state.attempt.coerceAtMost(MAX_EXPONENT))
                    val wait = maxOf(random(base), result.retryAfterMs)
                    val scopes =
                        if (request.method != "GET" &&
                            result.reconciliationRequired
                        ) {
                            state.uncertainScopes + scope(request)
                        } else {
                            state.uncertainScopes
                        }
                    deadline(state.copy(attempt = (state.attempt + 1).coerceAtMost(MAX_EXPONENT), uncertainScopes = scopes), wait)
                }

                is PallaSyncHttpResult.ProtocolError -> {
                    if (result.statusCode in LIFECYCLE_STATUSES) {
                        state.copy(
                            blockedRequests = state.blockedRequests + (fingerprint(request) to (problem?.code ?: "C_MISSING_DEPENDENCY")),
                        )
                    } else {
                        state
                    }
                }

                else -> {
                    state
                }
            }
        save(origin, state)
    }

    fun relayShutdownNotice(origin: String): RelayShutdownNotice? =
        synchronized(lock) {
            val state = load(origin)
            if (!state.relayShuttingDown && state.shutdownAtMs == null && state.migrationOrigin == null) {
                null
            } else {
                RelayShutdownNotice(state.shutdownAtMs, state.migrationOrigin, state.relayShuttingDown)
            }
        }

    fun observeHealth(
        origin: String,
        shutdownAtMs: Long?,
        migrationOrigin: String?,
    ) = synchronized(lock) {
        val state = load(origin)
        if (!state.relayShuttingDown) {
            save(
                origin,
                state.copy(
                    shutdownAtMs = shutdownAtMs,
                    migrationOrigin = migrationOrigin?.let(::validatedMigrationOrigin),
                ),
            )
        }
    }

    fun needsReconciliation(
        origin: String,
        chain: String,
    ): Boolean = synchronized(lock) { "/pallasync/v3/chains/$chain" in load(origin).uncertainScopes }

    /** Only a complete authenticated H/T comparison calls this, never health/policy/error hints. */
    fun reconciled(
        origin: String,
        chain: String,
    ) = synchronized(lock) {
        val state = load(origin)
        save(origin, state.copy(uncertainScopes = state.uncertainScopes - "/pallasync/v3/chains/$chain"))
    }

    fun progress(origin: String) =
        synchronized(lock) {
            val state = load(origin)
            save(origin, state.copy(attempt = 0, nextWall = 0, nextElapsed = 0, waitMs = 0))
        }

    /** Explicit lifecycle/capacity handlers invoke this after satisfying the named prerequisite. */
    fun resolved(
        origin: String,
        code: String,
    ) = synchronized(lock) {
        val state = load(origin)
        save(origin, state.copy(blockedRequests = state.blockedRequests.filterValues { it != code }))
    }

    private fun deadline(
        state: State,
        wait: Long,
    ): State =
        state.copy(
            nextWall = Math.addExact(wall(), wait),
            nextElapsed = Math.addExact(elapsed(), wait),
            sampledElapsed = elapsed(),
            bootId = boot(),
            waitMs = wait,
        )

    @Suppress("SwallowedException")
    private fun load(origin: String): State =
        try {
            prefs.getString("retry:$origin", null)?.let { Json.decodeFromString<State>(it) } ?: State()
        } catch (failure: IllegalArgumentException) {
            error("C_LOCAL_STORAGE_FULL")
        }

    private fun save(
        origin: String,
        state: State,
    ) {
        check(prefs.edit().putString("retry:$origin", Json.encodeToString(state)).commit()) { "C_LOCAL_STORAGE_FULL" }
    }

    private fun scope(request: Request): String =
        request.url.encodedPath
            .split('/')
            .take(CHAIN_SCOPE_SEGMENTS)
            .joinToString("/")

    private fun fingerprint(request: Request): String {
        val digest = HashingSink.sha256(blackholeSink())
        digest.buffer().use { sink ->
            sink.writeUtf8(request.method)
            sink.writeUtf8("\n")
            sink.writeUtf8(request.url.encodedPath)
            sink.writeUtf8("?")
            sink.writeUtf8(request.url.encodedQuery.orEmpty())
            sink.writeUtf8("\n")
            request.body?.writeTo(sink)
        }
        return digest.hash.hex()
    }

    private fun validatedMigrationOrigin(value: String): String? =
        when (val normalized = PallaSyncUrls.normalize(value)) {
            is PallaSyncHttpResult.Success -> {
                PallaSyncUrls.extractOrigin(normalized.value).takeIf { it == value }
            }

            else -> {
                null
            }
        }

    companion object {
        private val lock = Any()
        private const val START_BASE_MS = 1000L
        private const val MAX_BASE_MS = 60_000L
        private const val MAX_EXPONENT = 16
        private const val TOKEN_TTL_MS = 120_000L
        private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        private const val CHAIN_SCOPE_SEGMENTS = 5
        private const val MILLIS_PER_SECOND = 1000L
        private const val INVALID_HEADER_WAIT_MS = 3_600_000L
        private const val RATE_MAX_SECONDS = 3600L
        private val LIFECYCLE_STATUSES = setOf(403, 409, 507)

        internal fun retryAfter(
            value: String?,
            rateLimited: Boolean = false,
            nowMs: Long = System.currentTimeMillis(),
        ): Long {
            // §17.8 defines integer seconds. Invalid rate headers retain a
            // conservative one-hour wait instead of permitting an immediate retry.
            val seconds = value?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toLongOrNull()
            return when {
                value == null -> {
                    if (rateLimited) INVALID_HEADER_WAIT_MS else 0
                }

                rateLimited -> {
                    if (seconds != null &&
                        seconds in 1..RATE_MAX_SECONDS
                    ) {
                        seconds * MILLIS_PER_SECOND
                    } else {
                        INVALID_HEADER_WAIT_MS
                    }
                }

                seconds != null && seconds <= Long.MAX_VALUE / MILLIS_PER_SECOND -> {
                    seconds * MILLIS_PER_SECOND
                }

                else -> {
                    dateMillis(value)?.let { (it - nowMs).coerceAtLeast(0) } ?: INVALID_HEADER_WAIT_MS
                }
            }
        }

        private fun dateMillis(value: String): Long? {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            format.isLenient = false
            val position = ParsePosition(0)
            val date = format.parse(value, position)
            return date?.time?.takeIf { position.index == value.length }
        }
    }
}
