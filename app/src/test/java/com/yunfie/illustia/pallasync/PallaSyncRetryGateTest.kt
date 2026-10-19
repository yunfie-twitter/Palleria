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

    private fun request(
        body: String = "{}",
        requestOrigin: String = origin,
    ) = Request
        .Builder()
        .url("$requestOrigin/pallasync/v3/chains/$chain/records")
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
        gate.resolved(origin, chain, "QUOTA_EXCEEDED") shouldBe true
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
