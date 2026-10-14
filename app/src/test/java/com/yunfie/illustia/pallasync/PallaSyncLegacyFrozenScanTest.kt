package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.Base64

class PallaSyncLegacyFrozenScanTest {
    @Test
    fun `frozen source proof records final high-water and stable empty registry`() {
        runBlocking {
            val chainId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            val record = PallaSyncLegacyV21Source.SignedRecord(1, """{"value":1}""", "record-1", "device-1")
            val result =
                PallaSyncLegacyV21Source.inspectFrozenExport(
                    chainId = chainId,
                    initialDevices = emptyList(),
                    freezeConfirmed = true,
                    fetchDevices = { PallaSyncHttpResult.Success(emptyList()) },
                    fetchPage = { after ->
                        PallaSyncHttpResult.Success(
                            PallaSyncLegacyV21Source.RecordPage(
                                afterSequence = after,
                                throughSequence = 1,
                                hasMore = false,
                                records = listOf(record),
                            ),
                        )
                    },
                )

            val proof = (result as PallaSyncHttpResult.Success).value
            proof.sourceChain shouldBe chainId
            proof.highWater shouldBe 1L
            proof.recordCount shouldBe 1L
            proof.exportHash.length shouldBe 43
        }
    }

    @Test
    fun `frozen source proof requires freeze and propagates sequence gaps`() {
        runBlocking {
            val chainId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            val unconfirmed =
                PallaSyncLegacyV21Source.inspectFrozenExport(
                    chainId,
                    emptyList(),
                    freezeConfirmed = false,
                    fetchDevices = { error("unconfirmed source must not be read") },
                    fetchPage = { error("unconfirmed source must not be read") },
                )
            (unconfirmed as PallaSyncHttpResult.ProtocolError).message shouldBe "C_MISSING_DEPENDENCY"

            val gap =
                PallaSyncLegacyV21Source.inspectFrozenExport(
                    chainId,
                    emptyList(),
                    freezeConfirmed = true,
                    fetchDevices = { PallaSyncHttpResult.Success(emptyList()) },
                    fetchPage = { after ->
                        PallaSyncHttpResult.Success(
                            PallaSyncLegacyV21Source.RecordPage(
                                afterSequence = after,
                                throughSequence = 2,
                                hasMore = false,
                                records = listOf(PallaSyncLegacyV21Source.SignedRecord(2, "{}", "record-2", "device-1")),
                            ),
                        )
                    },
                )
            (gap as PallaSyncHttpResult.ProtocolError).message shouldBe "C_ROLLBACK_DETECTED"
        }
    }

    @Test
    fun `frozen source proof rejects a registry that changes during scan`() {
        runBlocking {
            val registryBefore =
                PallaSyncLegacyV21Source.Device(
                    "device-1",
                    "public-key",
                    true,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 1 }),
                )
            val registryAfter =
                registryBefore.copy(
                    sourceObjectHash = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { 2 }),
                )
            val result =
                PallaSyncLegacyV21Source.inspectFrozenExport(
                    "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    listOf(registryBefore),
                    freezeConfirmed = true,
                    fetchDevices = { PallaSyncHttpResult.Success(listOf(registryAfter)) },
                    fetchPage = { after ->
                        PallaSyncHttpResult.Success(PallaSyncLegacyV21Source.RecordPage(after, after, false, emptyList()))
                    },
                )
            (result as PallaSyncHttpResult.ProtocolError).message shouldBe "C_DEVICE_EQUIVOCATION"
        }
    }
}
