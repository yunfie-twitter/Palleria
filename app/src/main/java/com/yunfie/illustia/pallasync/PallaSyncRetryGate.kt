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
        val blockedRequestChains: Map<String, String> = emptyMap(),
        val capacityIssues: Map<String, CapacityIssue> = emptyMap(),
        val relayShuttingDown: Boolean = false,
        val shutdownAtMs: Long? = null,
        val migrationOrigin: String? = null,
        val noticeId: String? = null,
    )

    @Serializable
    data class CapacityIssue(
        val code: String,
        val usedBytes: Long? = null,
        val maxBytes: Long? = null,
        val observedAtMs: Long,
    )

    data class RelayShutdownNotice(
        val shutdownAtMs: Long?,
        val migrationOrigin: String?,
        val writesStopped: Boolean,
        val noticeId: String? = null,
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

    fun capacityIssue(
        origin: String,
        chain: String,
    ): CapacityIssue? =
        synchronized(lock) {
            load(origin).capacityIssues[chain]
        }

    /** The first stable catalogue code blocking this chain's exact saved requests. */
    fun blockedCode(
        origin: String,
        chain: String,
    ): String? = blockedCodes(origin, chain).firstOrNull()

    fun blockedCodes(
        origin: String,
        chain: String,
    ): List<String> =
        synchronized(lock) {
            val state = load(origin)
            state.blockedRequests
                .toSortedMap()
                .entries
                .mapNotNull { (key, code) -> code.takeIf { state.blockedRequestChains[key] == chain } }
                .distinct()
                .sorted()
        }

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
                    // A catalogue 4xx is a precondition/schema outcome, not a transport
                    // retry. Keep the exact request body and stop its periodic replay until
                    // the matching recovery flow resolves it or the caller builds a new
                    // request. A bare 410 is classified as Gone and never reaches here.
                    if (result.statusCode in CLIENT_ACTION_STATUSES) {
                        val key = fingerprint(request)
                        val chain = chainId(request)
                        val capacityIssue =
                            if (result.statusCode == 507 &&
                                problem?.code in CAPACITY_CODES &&
                                chain != null
                            ) {
                                CapacityIssue(
                                    code = checkNotNull(problem).code,
                                    usedBytes = problem.usedBytes,
                                    maxBytes = problem.maxBytes,
                                    observedAtMs = wall(),
                                )
                            } else {
                                null
                            }
                        state.copy(
                            blockedRequests = state.blockedRequests + (key to (problem?.code ?: "C_MISSING_DEPENDENCY")),
                            blockedRequestChains = chain?.let { state.blockedRequestChains + (key to it) } ?: state.blockedRequestChains,
                            capacityIssues =
                                if (chain != null && capacityIssue != null) {
                                    state.capacityIssues + (chain to capacityIssue)
                                } else {
                                    state.capacityIssues
                                },
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
                RelayShutdownNotice(state.shutdownAtMs, state.migrationOrigin, state.relayShuttingDown, state.noticeId)
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
                    shutdownAtMs = if (state.noticeId == null) shutdownAtMs else state.shutdownAtMs,
                    migrationOrigin =
                        if (state.noticeId == null) {
                            migrationOrigin?.let(::validatedMigrationOrigin)
                        } else {
                            state.migrationOrigin
                        },
                ),
            )
        }
    }

    fun observeRelayNotice(
        origin: String,
        shutdownAtMs: Long?,
        migrationOrigin: String?,
        noticeId: String,
    ) = synchronized(lock) {
        val state = load(origin)
        save(
            origin,
            state.copy(
                shutdownAtMs = shutdownAtMs,
                migrationOrigin = migrationOrigin?.let(::validatedMigrationOrigin),
                noticeId = noticeId,
            ),
        )
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
        chain: String,
        code: String,
    ): Boolean =
        synchronized(lock) {
            if (!canRetryAfterResolution(code)) return@synchronized false
            val state = load(origin)
            val resolved =
                state.blockedRequests
                    .filter { (key, blockedCode) -> blockedCode == code && state.blockedRequestChains[key] == chain }
                    .keys
            if (resolved.isEmpty()) return@synchronized false
            save(
                origin,
                state.copy(
                    blockedRequests = state.blockedRequests - resolved,
                    blockedRequestChains = state.blockedRequestChains - resolved,
                ),
            )
            true
        }

    /** §17.8: a capacity error is unblocked only after the user confirms the prerequisite changed. */
    fun resolveCapacity(
        origin: String,
        chain: String,
    ): Boolean =
        synchronized(lock) {
            val state = load(origin)
            val issue = state.capacityIssues[chain] ?: return@synchronized false
            if (issue.code !in CAPACITY_CODES) return@synchronized false
            val resolved =
                state.blockedRequests
                    .filter { (key, code) -> code == issue.code && state.blockedRequestChains[key] == chain }
                    .keys
            if (resolved.isEmpty()) return@synchronized false
            save(
                origin,
                state.copy(
                    blockedRequests = state.blockedRequests - resolved,
                    blockedRequestChains = state.blockedRequestChains - resolved,
                    capacityIssues = state.capacityIssues - chain,
                ),
            )
            true
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

    private fun chainId(request: Request): String? = PallaSyncRemoteService.chainIdForAuthorization(request.url.pathSegments)

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
        private val CLIENT_ACTION_STATUSES = (400..499).toSet() + 507
        private val CAPACITY_CODES = setOf("QUOTA_EXCEEDED", "STORAGE_UNAVAILABLE")
        private val RESOLUTION_RETRYABLE_CODES =
            setOf(
                "TOKEN_REQUIRED",
                "TOKEN_NOT_YET_VALID",
                "TOKEN_EXPIRED",
                "TOKEN_REPLAYED",
                "ADMIN_REQUIRED",
                "DEVICE_PENDING_KEYS",
                "DEVICE_UNKNOWN",
                "ENROLLMENT_NOT_ALLOWED",
                "ROTATION_NOT_ALLOWED",
                "CHAIN_NOT_FOUND",
                "DEVICE_NOT_FOUND",
                "EPOCH_NOT_FOUND",
                "OFFER_NOT_FOUND",
                "ENVELOPE_NOT_FOUND",
                "SNAPSHOT_PART_NOT_FOUND",
                "SNAPSHOT_NOT_FOUND",
                "REQUEST_TIMEOUT",
                "EPOCH_STALE",
                "CAS_MISMATCH",
                "GENERATION_MISMATCH",
                "MEMBER_SET_MISMATCH",
                "RECOVERY_ENVELOPE_REQUIRED",
                "HISTORY_KEY_EXPIRED",
                "CURSOR_EXPIRED",
                "LOG_ID_MISMATCH",
                "LOG_PRUNED",
                "SNAPSHOT_INCOMPLETE",
                "COMPACTION_UNSAFE",
                "REPAIR_PROOF_REQUIRED",
                "PAYLOAD_TOO_LARGE",
                "DEVICE_LIMIT_REACHED",
                "RATE_LIMITED",
                "INTERNAL_ERROR",
                "BAD_GATEWAY",
                "RELAY_UNAVAILABLE",
                "RELAY_SHUTTING_DOWN",
                "GATEWAY_TIMEOUT",
                "QUOTA_EXCEEDED",
                "STORAGE_UNAVAILABLE",
                "RECEIPT_NOT_FOUND",
                "TRANSPARENCY_ID_MISMATCH",
                "TRANSPARENCY_RANGE_UNAVAILABLE",
            )

        internal fun canRetryAfterResolution(code: String): Boolean = code in RESOLUTION_RETRYABLE_CODES

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
