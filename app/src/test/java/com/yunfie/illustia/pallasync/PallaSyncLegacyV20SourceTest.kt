package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Base64

class PallaSyncLegacyV20SourceTest {
    private val chainId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    private val deviceId = "01890f3e-7c00-7000-8000-000000000002"
    private val firstId = "01890f3e-7c00-7000-8000-000000000001"
    private val secondId = "01890f3e-7c00-7000-8000-000000000003"
    private val ciphertext = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(16) { 1 })
    private val signature = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(64) { 2 })

    @Test
    fun `v20 complete array requires strict legacy objects and ascending source order`() {
        val decoded = PallaSyncLegacyV20Source.decodeRecords("[${record(firstId, 10)},${record(secondId, 11)}]", chainId)

        decoded.map { it.sequence } shouldBe listOf(1L, 2L)
        decoded.map { it.recordId } shouldBe listOf(firstId, secondId)
        decoded.map { it.createdAtMs } shouldBe listOf(10L, 11L)
        val relayed = record(firstId, 10).dropLast(1) + ",\"relay_seq\":4,\"server_sequence\":4}"
        PallaSyncLegacyV20Source.decodeRecords("[$relayed]", chainId).single().sequence shouldBe 1L
    }

    @Test
    fun `v20 source rejects duplicates wrong chain bad order and unknown members`() {
        shouldThrow<IllegalStateException> {
            PallaSyncLegacyV20Source.decodeRecords("[${record(firstId, 10)},${record(firstId, 11)}]", chainId)
        }
        shouldThrow<IllegalStateException> {
            val wrongChain = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
            val source = record(firstId, 10, chain = wrongChain)
            PallaSyncLegacyV20Source.decodeRecords("[$source]", chainId)
        }
        shouldThrow<IllegalStateException> {
            PallaSyncLegacyV20Source.decodeRecords("[${record(firstId, 11)},${record(secondId, 10)}]", chainId)
        }
        shouldThrow<IllegalStateException> {
            val unknown = record(firstId, 10).dropLast(1) + ",\"unknown\":true}"
            PallaSyncLegacyV20Source.decodeRecords("[$unknown]", chainId)
        }
        shouldThrow<IllegalStateException> {
            val duplicateMember = record(firstId, 10).dropLast(1) + ",\"action\":\"delete\"}"
            PallaSyncLegacyV20Source.decodeRecords("[$duplicateMember]", chainId)
        }
        shouldThrow<IllegalStateException> {
            val invalidSequence = record(firstId, 10).dropLast(1) + ",\"relay_seq\":-1}"
            PallaSyncLegacyV20Source.decodeRecords("[$invalidSequence]", chainId)
        }
        shouldThrow<IllegalStateException> {
            val conflictingSequence = record(firstId, 10).dropLast(1) + ",\"relay_seq\":4,\"server_sequence\":5}"
            PallaSyncLegacyV20Source.decodeRecords("[$conflictingSequence]", chainId)
        }
        shouldThrow<IllegalStateException> {
            PallaSyncLegacyV20Source.decodeRecords("[]", "not-a-chain-id")
        }
    }

    @Test
    fun `v20 export transcript binds chain ordered record hashes and rejects sequence gaps`() {
        val first = PallaSyncLegacyV20Source.decodeRecords("[${record(firstId, 10)},${record(secondId, 11)}]", chainId)
        val reordered = PallaSyncLegacyV20Source.decodeRecords("[${record(secondId, 10)},${record(firstId, 11)}]", chainId)
        val hash = PallaSyncLegacyV20Source.exportHash(chainId, first)

        hash shouldBe PallaSyncLegacyV20Source.exportHash(chainId, first)
        (hash != PallaSyncLegacyV20Source.exportHash(chainId, reordered)) shouldBe true
        shouldThrow<IllegalStateException> {
            PallaSyncLegacyV20Source.exportHash(chainId, listOf(first.first().copy(sequence = 2)))
        }
    }

    @Test
    fun `v20 frozen scan requires stopped writers repeats full transcript and reports quarantines`() =
        runTest {
            val records = PallaSyncLegacyV20Source.decodeRecords("[${record(firstId, 10)}]", chainId)
            var reads = 0
            val result =
                PallaSyncLegacyV20Source.inspectFrozenExport(
                    chainId,
                    writersStopped = true,
                    authenticateRecord = { "C_SIGNATURE_INVALID" },
                    fetchRecords = {
                        reads++
                        records
                    },
                )

            reads shouldBe 2
            val proof = (result as PallaSyncHttpResult.Success).value
            proof.highWater shouldBe 1L
            proof.quarantinedCount shouldBe 1L

            var fetchedWithoutFreeze = false
            val refused =
                PallaSyncLegacyV20Source.inspectFrozenExport(
                    chainId,
                    writersStopped = false,
                    authenticateRecord = { null },
                    fetchRecords = {
                        fetchedWithoutFreeze = true
                        records
                    },
                )
            (refused as PallaSyncHttpResult.ProtocolError).message shouldBe "C_MISSING_DEPENDENCY"
            fetchedWithoutFreeze shouldBe false
        }

    @Test
    fun `v20 frozen scan rejects changed second export and unrecognized verifier failures`() =
        runTest {
            val first = PallaSyncLegacyV20Source.decodeRecords("[${record(firstId, 10)}]", chainId)
            val changed = PallaSyncLegacyV20Source.decodeRecords("[${record(secondId, 10)}]", chainId)
            var reads = 0
            val changedResult =
                PallaSyncLegacyV20Source.inspectFrozenExport(
                    chainId,
                    writersStopped = true,
                    authenticateRecord = { null },
                    fetchRecords = {
                        if (reads++ == 0) first else changed
                    },
                )
            (changedResult as PallaSyncHttpResult.ProtocolError).message shouldBe "C_RECORD_ID_CONFLICT"

            val invalidResult =
                PallaSyncLegacyV20Source.inspectFrozenExport(
                    chainId,
                    writersStopped = true,
                    authenticateRecord = { "C_MISSING_DEPENDENCY" },
                    fetchRecords = { first },
                )
            (invalidResult as PallaSyncHttpResult.ProtocolError).message shouldBe "C_SCHEMA_VIOLATION"
        }

    private fun record(
        recordId: String,
        createdAt: Long,
        chain: String = chainId,
    ) =
        """{"protocol_version":"2.0","chain_id":"$chain","record_id":"$recordId","collection_name":"palleria.favorite_tag/1","action":"upsert","encrypted_payload":"$ciphertext","device_id":"$deviceId","created_at_ms":$createdAt,"signature":"$signature"}"""
}
