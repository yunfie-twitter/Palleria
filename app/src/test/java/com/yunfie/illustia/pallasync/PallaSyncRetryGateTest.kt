package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PallaSyncRetryGateTest {
    private val origin = "https://relay.example.com"
    private val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("retry-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
    private var boot = 1L
    private var wall = 1_791_683_000_000L
    private var elapsed = 100L

    private fun gate(random: (Long) -> Long = { it }) = PallaSyncRetryGate(prefs, { boot }, { wall }, { elapsed }, random)

    private fun freshGate() =
        PallaSyncRetryGate(
            context.getSharedPreferences("retry-root-${UUID.randomUUID()}", Context.MODE_PRIVATE),
            { boot },
            { wall },
            { elapsed },
        )

    private fun request(
        body: String = "{}",
        requestOrigin: String = origin,
        authorization: String? = null,
    ) = Request
        .Builder()
        .url("$requestOrigin/pallasync/v3/chains/$chain/records")
        .apply { authorization?.let { header("Authorization", it) } }
        .post(body.toRequestBody())
        .build()

    @Test
    fun `jitter uses the exact capped exponential range and progress resets the attempt`() {
        val bounds = mutableListOf<Long>()
        val gate =
            gate {
                bounds.add(it)
                it
            }
        repeat(10) {
            gate.observe(request(), PallaSyncHttpResult.Retryable("transport"))
            val wait = gate.remaining(origin)
            elapsed += wait
            wall += wait
            gate.remaining(origin) shouldBe 0
        }
        bounds shouldBe listOf(1000, 2000, 4000, 8000, 16000, 32000, 60000, 60000, 60000, 60000).map(Int::toLong)
        gate.progress(origin)
        gate.observe(request(), PallaSyncHttpResult.Retryable("transport"))
        bounds.last() shouldBe 1000
    }

    @Test
    fun `minimum wait survives process restart wall clock changes and a new boot`() {
        gate { 0 }.observe(request(), PallaSyncHttpResult.Retryable("RATE_LIMITED", retryAfterMs = 120_000, statusCode = 429))
        wall += 31_536_000_000L
        gate().remaining(origin) shouldBe 120_000
        elapsed += 20_000
        gate().remaining(origin) shouldBe 100_000
        boot++
        elapsed = 0
        val resumed = gate()
        resumed.remaining(origin) shouldBe 120_000
        elapsed = 120_000
        resumed.remaining(origin) shouldBe 0
        boot++
        elapsed = 0
        gate().remaining(origin) shouldBe 0
    }

    @Test
    fun `two clock failures correct time without bypassing a restart barrier`() {
        val gate = gate { 0 }
        val hint = PallaSyncProblemHint(401, "TOKEN_NOT_YET_VALID", true, serverTimeMs = 1_791_684_000_000, retryAfterMs = 120_000)
        val failure = checkNotNull(classifyPallaSyncHttpStatus(401, hint))
        gate.observe(request(), failure, hint)
        gate.correctedTime(origin) shouldBe wall
        elapsed += 120_000
        wall += 120_000
        gate.observe(request(), failure, hint)
        gate.correctedTime(origin) shouldBe hint.serverTimeMs
        gate.remaining(origin) shouldBe 120_000
        (gate.before(request()) is PallaSyncHttpResult.Retryable) shouldBe true
    }

    @Test
    fun `uncertain writes require the same chain authenticated reconciliation before an exact retry`() {
        val gate = gate { 0 }
        gate.observe(request(), PallaSyncHttpResult.Retryable("INTERNAL_ERROR", statusCode = 500, reconciliationRequired = true))
        gate.hasDeferredRetry(origin) shouldBe true
        gate.progress(origin)
        gate.before(request()) shouldBe PallaSyncHttpResult.ProtocolError("C_ACCEPTANCE_UNPROVEN")
        gate.before(Request.Builder().url("$origin/pallasync/v3/chains/$chain/log/head").build()) shouldBe null
        gate.reconciled(origin, "another-chain")
        gate.before(request()) shouldBe PallaSyncHttpResult.ProtocolError("C_ACCEPTANCE_UNPROVEN")
        gate.reconciled(origin, chain)
        gate.before(request()) shouldBe null
    }

    @Test
    fun `semantic and quota errors preserve a durable block instead of an immediate replay`() {
        val gate = gate()
        val hint = PallaSyncProblemHint(507, "QUOTA_EXCEEDED", true)
        val failure = checkNotNull(classifyPallaSyncHttpStatus(507, hint))
        failure shouldBe PallaSyncHttpResult.ProtocolError("QUOTA_EXCEEDED", 507)
        gate.observe(request(), failure, hint)
        gate().before(request()) shouldBe PallaSyncHttpResult.ProtocolError("QUOTA_EXCEEDED")
        gate.resolved(origin, chain, "EPOCH_STALE") shouldBe false
        gate.before(request()) shouldBe PallaSyncHttpResult.ProtocolError("QUOTA_EXCEEDED")
        gate.resolveCapacity(origin, chain) shouldBe true
        gate.before(request()) shouldBe null
        PallaSyncRetryGate.retryAfter("12") shouldBe 12_000
        PallaSyncRetryGate.retryAfter("invalid") shouldBe 3_600_000
        PallaSyncRetryGate.retryAfter(null, rateLimited = true) shouldBe 3_600_000
        PallaSyncRetryGate.retryAfter("0", rateLimited = true) shouldBe 3_600_000
        PallaSyncRetryGate.retryAfter("3601", rateLimited = true) shouldBe 3_600_000
        PallaSyncRetryGate.retryAfter("Thu, 01 Jan 1970 00:01:00 GMT", nowMs = 20_000) shouldBe 40_000
    }

    @Test
    fun `catalogue 404 and 413 errors pause only the exact request until recovery`() {
        val gate = gate()
        val source = request("{\"epoch\":7}")
        val missingEpoch = PallaSyncProblemHint(404, "EPOCH_NOT_FOUND", true)
        val failure = checkNotNull(classifyPallaSyncHttpStatus(404, missingEpoch))
        failure shouldBe PallaSyncHttpResult.ProtocolError("EPOCH_NOT_FOUND", 404)
        gate.observe(source, failure, missingEpoch)

        gate.blockedCode(origin, chain) shouldBe "EPOCH_NOT_FOUND"
        gate.before(source) shouldBe PallaSyncHttpResult.ProtocolError("EPOCH_NOT_FOUND")
        gate.resolved(origin, chain, "SNAPSHOT_NOT_FOUND") shouldBe false
        gate.before(source) shouldBe PallaSyncHttpResult.ProtocolError("EPOCH_NOT_FOUND")
        gate.resolved(origin, chain, "EPOCH_NOT_FOUND") shouldBe true
        gate.before(source) shouldBe null

        val oversized = request("{\"records\":[1,2,3]}")
        val tooLarge = PallaSyncProblemHint(413, "PAYLOAD_TOO_LARGE", true)
        gate.observe(oversized, checkNotNull(classifyPallaSyncHttpStatus(413, tooLarge)), tooLarge)
        gate.before(oversized) shouldBe PallaSyncHttpResult.ProtocolError("PAYLOAD_TOO_LARGE")
        // Rebuilding a compliant smaller batch has a different exact request fingerprint.
        gate.before(request("{\"records\":[1]}")) shouldBe null
    }

    @Test
    fun `missing receipt retries after backoff without discarding or blocking the exact evidence request`() {
        val gate = gate { it }
        val request =
            Request
                .Builder()
                .url("$origin/pallasync/v3/chains/$chain/receipts/7?log_id=11111111-1111-4111-8111-111111111111")
                .get()
                .build()
        val hint = PallaSyncProblemHint(404, "RECEIPT_NOT_FOUND", true)
        val retry = checkNotNull(classifyPallaSyncHttpStatus(404, hint, request))

        gate.observe(request, retry, hint)
        gate.blockedCode(origin, chain) shouldBe null
        gate.before(request) shouldBe PallaSyncHttpResult.Retryable("PallaSync retry is deferred", 1000)
        elapsed += 1000
        wall += 1000
        gate.before(request) shouldBe null
    }

    @Test
    fun `local parse failures do not become server request blockers`() {
        val gate = gate()
        gate.observe(request(), PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION"))
        gate.blockedCode(origin, chain) shouldBe null
        gate.before(request()) shouldBe null
    }

    @Test
    fun `507 capacity hints are diagnostic only and explicit resolution retries the retained request`() {
        val gate = gate()
        val hint = PallaSyncProblemHint(507, "QUOTA_EXCEEDED", true, usedBytes = 900, maxBytes = 1000)
        val failure = checkNotNull(classifyPallaSyncHttpStatus(507, hint))
        failure shouldBe PallaSyncHttpResult.ProtocolError("QUOTA_EXCEEDED", 507)
        gate.observe(request(), failure, hint)

        gate.capacityIssue(origin, chain) shouldBe PallaSyncRetryGate.CapacityIssue("QUOTA_EXCEEDED", 900, 1000, wall)
        gate.before(request()) shouldBe PallaSyncHttpResult.ProtocolError("QUOTA_EXCEEDED")
        gate.resolveCapacity(origin, "another-chain") shouldBe false
        gate.before(request()) shouldBe PallaSyncHttpResult.ProtocolError("QUOTA_EXCEEDED")

        gate.resolveCapacity(origin, chain) shouldBe true
        gate.capacityIssue(origin, chain) shouldBe null
        gate.before(request()) shouldBe null
    }

    @Test
    fun `lifecycle resolution clears only matching error requests for the selected chain`() {
        val gate = gate()
        val anotherChain = "AbCdEf0123456789012345678901234567890123456"
        val first = request()
        val second =
            Request
                .Builder()
                .url("$origin/pallasync/v3/chains/$anotherChain/records")
                .post("{}".toRequestBody())
                .build()
        val pendingKeys = PallaSyncHttpResult.ProtocolError("DEVICE_PENDING_KEYS", 403)
        gate.observe(first, pendingKeys, PallaSyncProblemHint(403, "DEVICE_PENDING_KEYS", true))
        gate.observe(second, pendingKeys, PallaSyncProblemHint(403, "DEVICE_PENDING_KEYS", true))

        gate.resolved(origin, chain, "DEVICE_PENDING_KEYS") shouldBe true
        gate.before(first) shouldBe null
        gate.before(second) shouldBe PallaSyncHttpResult.ProtocolError("DEVICE_PENDING_KEYS")
        gate.resolved(origin, anotherChain, "EPOCH_STALE") shouldBe false
        gate.resolved(origin, anotherChain, "DEVICE_PENDING_KEYS") shouldBe true
        gate.before(second) shouldBe null
    }

    @Test
    fun `explicit root authorization releases only exact admin authorization blockers`() {
        val first = request("{\"epoch\":7}", authorization = "PallaSync expired-device-token")
        val sibling = request("{\"epoch\":8}", authorization = "PallaSync expired-device-token")

        for (code in listOf("ADMIN_REQUIRED", "ROTATION_NOT_ALLOWED")) {
            val gate = freshGate()
            val querySibling =
                Request
                    .Builder()
                    .url("$origin/pallasync/v3/chains/$chain/records?cursor=next")
                    .header("Authorization", "PallaSync expired-device-token")
                    .post("{\"epoch\":7}".toRequestBody())
                    .build()
            gate.observe(first, PallaSyncHttpResult.ProtocolError(code, 403), PallaSyncProblemHint(403, code, true))
            gate.observe(sibling, PallaSyncHttpResult.ProtocolError(code, 403), PallaSyncProblemHint(403, code, true))
            gate.observe(querySibling, PallaSyncHttpResult.ProtocolError(code, 403), PallaSyncProblemHint(403, code, true))
            gate.before(first) shouldBe PallaSyncHttpResult.ProtocolError(code)

            val refreshedRootRequest = request("{\"epoch\":7}", authorization = "PallaSync fresh-root-token")
            gate.retryWithExplicitRootAuthorization(request("{\"epoch\":7}")) shouldBe false
            gate.retryWithExplicitRootAuthorization(refreshedRootRequest) shouldBe true
            gate.before(refreshedRootRequest) shouldBe null
            gate.before(sibling) shouldBe PallaSyncHttpResult.ProtocolError(code)
            gate.before(querySibling) shouldBe PallaSyncHttpResult.ProtocolError(code)
        }

        val terminalGate = freshGate()
        val terminal = request("{\"epoch\":9}", authorization = "PallaSync expired-device-token")
        terminalGate.observe(
            terminal,
            PallaSyncHttpResult.ProtocolError("DEVICE_REVOKED", 403),
            PallaSyncProblemHint(403, "DEVICE_REVOKED", false),
        )
        terminalGate.retryWithExplicitRootAuthorization(
            request("{\"epoch\":9}", authorization = "PallaSync fresh-root-token"),
        ) shouldBe false
        terminalGate.before(terminal) shouldBe PallaSyncHttpResult.ProtocolError("DEVICE_REVOKED")
    }

    @Test
    fun `origin status exposes only unscoped blocked control requests`() {
        val gate = gate()
        val chainRequest = request()
        val importRequest =
            Request
                .Builder()
                .url("$origin/pallasync/v3/chains/import")
                .post("{}".toRequestBody())
                .build()
        val tokenRequired = PallaSyncHttpResult.ProtocolError("TOKEN_REQUIRED", 401)
        val hint = PallaSyncProblemHint(401, "TOKEN_REQUIRED", true)

        gate.observe(chainRequest, tokenRequired, hint)
        gate.observe(importRequest, tokenRequired, hint)

        gate.blockedCodes(origin, chain) shouldBe listOf("TOKEN_REQUIRED")
        gate.unscopedBlockedCodes(origin) shouldBe listOf("TOKEN_REQUIRED")
        gate.unscopedBlockedCodes("https://other.example.com") shouldBe emptyList()
        gate.resolved(origin, chain, "TOKEN_REQUIRED") shouldBe true
        gate.blockedCodes(origin, chain) shouldBe emptyList()
        gate.unscopedBlockedCodes(origin) shouldBe listOf("TOKEN_REQUIRED")
    }

    @Test
    fun `unvalidated client error is retained as a local schema failure without a fabricated server code`() {
        val gate = gate()
        val request = request()
        val invalidProblem = checkNotNull(classifyPallaSyncHttpStatus(403))

        invalidProblem shouldBe PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION", 403)
        gate.observe(request, invalidProblem)

        gate.blockedCodes(origin, chain) shouldBe listOf("C_SCHEMA_VIOLATION")
        gate.blockedCodes(origin, chain).contains("C_MISSING_DEPENDENCY") shouldBe false
        gate.before(request) shouldBe PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
    }

    @Test
    fun `device limit blocker is released only by explicit chain scoped lifecycle resolution`() {
        val gate = gate()
        val anotherChain = "AbCdEf0123456789012345678901234567890123456"
        val first = request()
        val second =
            Request
                .Builder()
                .url("$origin/pallasync/v3/chains/$anotherChain/records")
                .post("{}".toRequestBody())
                .build()
        val limit = PallaSyncProblemHint(413, "DEVICE_LIMIT_REACHED", true)
        gate.observe(first, PallaSyncHttpResult.ProtocolError("DEVICE_LIMIT_REACHED", 413), limit)
        gate.observe(second, PallaSyncHttpResult.ProtocolError("DEVICE_LIMIT_REACHED", 413), limit)

        PallaSyncRetryGate.canRetryAfterResolution("DEVICE_LIMIT_REACHED") shouldBe false
        gate.resolved(origin, chain, "DEVICE_LIMIT_REACHED") shouldBe true
        gate.before(first) shouldBe null
        gate.before(second) shouldBe PallaSyncHttpResult.ProtocolError("DEVICE_LIMIT_REACHED")
        gate.resolved(origin, anotherChain, "DEVICE_LIMIT_REACHED") shouldBe true
        gate.before(second) shouldBe null
    }

    @Test
    fun `blocked request diagnostics list distinct codes for only the selected chain`() {
        val gate = gate()
        val anotherChain = "AbCdEf0123456789012345678901234567890123456"
        val first = request("{\"epoch\":1}")
        val second = request("{\"epoch\":2}")
        val other =
            Request
                .Builder()
                .url("$origin/pallasync/v3/chains/$anotherChain/records")
                .post("{}".toRequestBody())
                .build()

        fun block(
            request: Request,
            code: String,
        ) {
            val hint = PallaSyncProblemHint(409, code, true)
            gate.observe(request, checkNotNull(classifyPallaSyncHttpStatus(409, hint)), hint)
        }

        block(first, "EPOCH_STALE")
        block(second, "EPOCH_NOT_FOUND")
        block(other, "DEVICE_PENDING_KEYS")

        gate.blockedCodes(origin, chain) shouldBe listOf("EPOCH_NOT_FOUND", "EPOCH_STALE")
        gate.blockedCodes(origin, anotherChain) shouldBe listOf("DEVICE_PENDING_KEYS")
        gate.resolved(origin, chain, "EPOCH_STALE") shouldBe false
        gate.blockedCodes(origin, chain) shouldBe listOf("EPOCH_NOT_FOUND", "EPOCH_STALE")
        gate.blockedCodes(origin, anotherChain) shouldBe listOf("DEVICE_PENDING_KEYS")
    }

    @Test
    fun `non-retryable catalogue blocks remain visible but cannot be manually released`() {
        val gate = gate()
        val revoked = PallaSyncProblemHint(403, "DEVICE_REVOKED", false)
        val failure = checkNotNull(classifyPallaSyncHttpStatus(403, revoked))
        gate.observe(request(), failure, revoked)

        gate.blockedCodes(origin, chain) shouldBe listOf("DEVICE_REVOKED")
        PallaSyncRetryGate.canRetryAfterResolution("DEVICE_REVOKED") shouldBe false
        PallaSyncRetryGate.canRetryAfterResolution("DEVICE_PENDING_KEYS") shouldBe false
        gate.resolved(origin, chain, "DEVICE_REVOKED") shouldBe false
        gate.before(request()) shouldBe PallaSyncHttpResult.ProtocolError("DEVICE_REVOKED")
    }

    @Test
    fun `stale signed proposals stay blocked until their dedicated rebuild workflow`() {
        val gate = gate()
        val staleCodes =
            listOf(
                "EPOCH_STALE",
                "CAS_MISMATCH",
                "GENERATION_MISMATCH",
                "MEMBER_SET_MISMATCH",
                "CURSOR_EXPIRED",
                "LOG_ID_MISMATCH",
                "LOG_PRUNED",
                "SNAPSHOT_INCOMPLETE",
                "COMPACTION_UNSAFE",
                "REPAIR_PROOF_REQUIRED",
                "PAYLOAD_TOO_LARGE",
                "TRANSPARENCY_ID_MISMATCH",
                "TRANSPARENCY_RANGE_UNAVAILABLE",
            )

        staleCodes.forEach { code ->
            val source = request("{\"proposal\":\"immutable\"}")
            val hint = PallaSyncProblemHint(409, code, true)
            gate.observe(source, PallaSyncHttpResult.ProtocolError(code, 409), hint)

            PallaSyncRetryGate.canRetryAfterResolution(code) shouldBe false
            gate.resolved(origin, chain, code) shouldBe false
            gate.before(source) shouldBe PallaSyncHttpResult.ProtocolError(code)
        }
    }

    @Test
    fun `cursor expiry releases only the exact blocked cursor after restart success`() {
        val gate = gate()
        val expired =
            Request
                .Builder()
                .url("$origin/pallasync/v3/chains/$chain/log?cursor=opaque-next&limit=200")
                .get()
                .build()
        val problem = PallaSyncProblemHint(409, "CURSOR_EXPIRED", true)
        gate.observe(expired, PallaSyncHttpResult.ProtocolError("CURSOR_EXPIRED", 409), problem)
        gate.before(expired) shouldBe PallaSyncHttpResult.ProtocolError("CURSOR_EXPIRED")

        gate.cursorRestarted(origin, "other-chain", expired) shouldBe false
        gate.before(expired) shouldBe PallaSyncHttpResult.ProtocolError("CURSOR_EXPIRED")
        val restarted =
            Request
                .Builder()
                .url("$origin/pallasync/v3/chains/$chain/log?after_seq=200&limit=200")
                .get()
                .build()
        gate.before(restarted) shouldBe null

        gate.cursorRestarted(origin, chain, expired) shouldBe true
        gate.before(expired) shouldBe null
        gate.cursorRestarted(origin, chain, expired) shouldBe false
    }

    @Test
    fun `oversized outbox batches shrink durably and clear only replaced request blockers`() {
        val gate = gate()
        val oversized = request("{\"records\":[1,2,3,4]}")
        val unrelated = request("{\"records\":[5,6,7,8]}")
        val problem = PallaSyncProblemHint(413, "PAYLOAD_TOO_LARGE", true)
        val failure = PallaSyncHttpResult.ProtocolError("PAYLOAD_TOO_LARGE", 413)
        gate.observe(oversized, failure, problem)
        gate.observe(unrelated, failure, problem)

        gate.reduceRecordBatchLimit(origin, chain, 4, oversized) shouldBe 2
        gate.recordBatchLimit(origin, chain, 10) shouldBe 2
        gate().recordBatchLimit(origin, chain, 10) shouldBe 2
        gate.before(oversized) shouldBe PallaSyncHttpResult.ProtocolError("PAYLOAD_TOO_LARGE")
        gate.recordBatchesRecovered(origin, chain) shouldBe true
        gate.before(oversized) shouldBe null
        gate.before(unrelated) shouldBe PallaSyncHttpResult.ProtocolError("PAYLOAD_TOO_LARGE")
    }

    @Test
    fun `relay shutdown suspends writes and persists destination only as an explicit suggestion`() {
        val gate = gate { 0 }
        val hint =
            PallaSyncProblemHint(
                503,
                "RELAY_SHUTTING_DOWN",
                true,
                shutdownAtMs = 1_791_685_000_000,
                migrationOrigin = "https://target.example",
            )
        val failure = checkNotNull(classifyPallaSyncHttpStatus(503, hint))
        failure shouldBe PallaSyncHttpResult.ProtocolError("RELAY_SHUTTING_DOWN", 503)
        gate.observe(request(), failure, hint)

        gate.before(request()) shouldBe PallaSyncHttpResult.ProtocolError("RELAY_SHUTTING_DOWN", 503)
        gate.before(Request.Builder().url("$origin/pallasync/v3/chains/$chain/log/head").build()) shouldBe null
        gate().relayShutdownNotice(origin) shouldBe
            PallaSyncRetryGate.RelayShutdownNotice(1_791_685_000_000, "https://target.example", writesStopped = true)

        val unsafe = gate()
        unsafe.observe(
            request(),
            failure,
            hint.copy(migrationOrigin = "https://target.example/path"),
        )
        unsafe.relayShutdownNotice(origin) shouldBe
            PallaSyncRetryGate.RelayShutdownNotice(
                1_791_685_000_000,
                "https://target.example",
                writesStopped = true,
            )

        val previouslyAdvertisedOrigin = "https://previously-advertised.example"
        val previous = gate()
        previous.observeHealth(previouslyAdvertisedOrigin, 1_791_685_000_000, "https://target.example")
        previous.observe(
            request(requestOrigin = previouslyAdvertisedOrigin),
            failure,
            hint.copy(shutdownAtMs = null, migrationOrigin = null),
        )
        previous.relayShutdownNotice(previouslyAdvertisedOrigin) shouldBe
            PallaSyncRetryGate.RelayShutdownNotice(1_791_685_000_000, "https://target.example", writesStopped = true)
    }

    @Test
    fun `planned shutdown health is persisted as a suggestion without stopping writes`() {
        val gate = gate()
        gate.observeHealth(origin, 1_791_685_000_000, "https://target.example")
        gate.relayShutdownNotice(origin) shouldBe
            PallaSyncRetryGate.RelayShutdownNotice(1_791_685_000_000, "https://target.example", writesStopped = false)
        gate.before(request()) shouldBe null

        gate.observeHealth(origin, 1_791_685_000_000, "https://target.example/path")
        gate.relayShutdownNotice(origin) shouldBe
            PallaSyncRetryGate.RelayShutdownNotice(1_791_685_000_000, null, writesStopped = false)
    }

    @Test
    fun `authenticated relay notice stores its public notice id`() {
        val gate = gate()
        gate.observeRelayNotice(
            origin,
            1_791_685_000_000,
            "https://target.example",
            "f4e3d90e-2461-4c7c-9f2f-89452a28657c",
        )
        gate.observeHealth(origin, null, null)
        gate.relayShutdownNotice(origin) shouldBe
            PallaSyncRetryGate.RelayShutdownNotice(
                1_791_685_000_000,
                "https://target.example",
                writesStopped = false,
                noticeId = "f4e3d90e-2461-4c7c-9f2f-89452a28657c",
            )
    }
}
