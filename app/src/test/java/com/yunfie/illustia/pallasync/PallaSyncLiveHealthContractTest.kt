package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Validates the payload the deployed relay actually serves against the client's native health
 * validator. A failure here means no client can complete the first PallaSync request regardless of
 * anything else the app does, so the shape is pinned as a literal capture rather than a round trip.
 */
class PallaSyncLiveHealthContractTest {
    // Captured verbatim from GET https://sync.yunfi.f5.si/pallasync/v3/health.
    private val liveHealth =
        """
        {"durability_tier":"single-durable","limits":{"max_devices":128,"max_records_per_upload":64,"max_request_bytes":8388608,"page_limit":1000},"migration_origin":null,"protocol":"pallasync/3","relay_key_id":"MDQPt95TwBxI_o5JMzArlIafDjzb3e4OgfNXB6vtL-E","relay_origin":"https://sync.yunfi.f5.si","relay_public_key":"UyrQD-wOLh23aJH5dAU8YrML1Q9U6nZjBinODD75TcQ","server_time_ms":1793005520441,"shutdown_at_ms":null}
        """.trimIndent()

    @Test
    fun `native validator accepts the deployed relay health payload`() {
        val validated =
            PallaSyncCore.validateHealth(
                liveHealth.toByteArray(),
                "https://sync.yunfi.f5.si",
            )
        // The validator returns the body unchanged; the parsed limits are what the client budgets
        // uploads and page sizes against.
        validated.isNotBlank() shouldBe true
        val health = Json.decodeFromString<PallaSyncHealth>(validated)
        health.protocol shouldBe "pallasync/3"
        health.relayOrigin shouldBe "https://sync.yunfi.f5.si"
        health.limits.maxDevices shouldBe 128
        health.limits.maxRecordsPerUpload shouldBe 64
        health.limits.maxRequestBytes shouldBe 8_388_608
        health.limits.pageLimit shouldBe 1_000
    }

    @Test
    fun `native validator rejects a mismatched origin`() {
        val failure =
            runCatching {
                PallaSyncCore.validateHealth(
                    liveHealth.toByteArray(),
                    "https://elsewhere.example",
                )
            }
        failure.isFailure shouldBe true
    }
}
