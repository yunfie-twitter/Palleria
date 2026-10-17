package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.LogEntryEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class PallaSyncImportRelayTest {
    @Test
    fun `stops later chunk after cancellation`() {
        runBlocking {
            var requests = 0
            var cancelled = false
            val task =
                launch {
                    val failure =
                        runCatching {
                            forEachImportChunk(listOf(1, 2)) {
                                requests += 1
                                if (it == 1) currentCoroutineContext().cancel()
                                true
                            }
                        }
                    cancelled = failure.exceptionOrNull() is CancellationException
                }
            task.join()
            cancelled shouldBe true
            requests shouldBe 1
        }
    }

    @Test
    fun `builds exact canonical import body from source chain history`() {
        val history = history()
        val body = PallaSyncImportRelay.canonicalBody(CHAIN_ID, history)
        Json.parseToJsonElement(body).jsonObject.keys shouldBe
            setOf("parameters_history", "epoch_history", "device_history", "envelopes", "compactions")
        PallaSyncCore.canonicalJson(body) shouldBe body
    }

    @Test
    fun `rejects objects from another chain and an empty history`() {
        val wrongChain = history().copy(parameters = Json.parseToJsonElement("""[{"chain_id":"other"}]""").let { it as JsonArray })
        shouldThrow<IllegalStateException> {
            PallaSyncImportRelay.canonicalBody(CHAIN_ID, wrongChain)
        }.message shouldBe "C_RECOVERY_ROOT_MISMATCH"

        shouldThrow<IllegalStateException> {
            PallaSyncImportRelay.canonicalBody(
                CHAIN_ID,
                PallaSyncImportRelay.History(
                    JsonArray(emptyList()),
                    JsonArray(emptyList()),
                    JsonArray(emptyList()),
                    JsonArray(emptyList()),
                    JsonArray(emptyList()),
                ),
            )
        }.message shouldBe "C_MISSING_DEPENDENCY"
    }

    @Test
    fun `rejects non-object history members with a protocol error`() {
        val malformed = history().copy(parameters = Json.parseToJsonElement("[null]") as JsonArray)
        shouldThrow<IllegalStateException> {
            PallaSyncImportRelay.canonicalBody(CHAIN_ID, malformed)
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }

    @Test
    fun `import URL is the global administrative route`() {
        val base = (PallaSyncUrls.normalize("https://relay.example.com") as PallaSyncHttpResult.Success).value
        PallaSyncUrls.importChain(base).encodedPath shouldBe "/pallasync/v3/chains/import"
    }

    @Test
    fun `reseed receipt list follows staged controls and atomic commit append order`() {
        val staged =
            PallaSyncImportRelay.History(
                parameters = Json.parseToJsonElement("""[{"generation":2},{"generation":1}]""") as JsonArray,
                epochs = Json.parseToJsonElement("""[{"epoch":2},{"epoch":1}]""") as JsonArray,
                devices = Json.parseToJsonElement("""[{"device_id":"b"},{"device_id":"a"}]""") as JsonArray,
                envelopes = Json.parseToJsonElement("""[{"envelope_id":"b"},{"envelope_id":"a"}]""") as JsonArray,
                compactions = JsonArray(emptyList()),
            )
        val body =
            """{"parameters":{"generation":3},"epoch":{"epoch":3},"envelopes":[{"recipient_kind":"recovery"},{"recipient_kind":"device"}],"compaction":{"compaction_id":"c"},"admin_operation":{"operation_id":"op"},"device_records":[{"device_id":"fresh"}]}"""
        val entries = PallaSyncImportRelay.expectedReseedCommitEntries(staged, body)
        entries.map {
            it.jsonObject
                .getValue("kind")
                .jsonPrimitive.content
        } shouldBe
            listOf(
                "parameters",
                "parameters",
                "device",
                "device",
                "envelope",
                "envelope",
                "epoch",
                "epoch",
                "parameters",
                "device",
                "envelope",
                "envelope",
                "admin",
                "epoch",
                "compaction",
            )
        entries[0]
            .jsonObject
            .getValue("object")
            .jsonObject
            .getValue("generation")
            .jsonPrimitive.content shouldBe "1"
        entries[1]
            .jsonObject
            .getValue("object")
            .jsonObject
            .getValue("generation")
            .jsonPrimitive.content shouldBe "2"
    }

    @Test
    fun `history builder includes verified controls after materialization`() {
        val rows =
            listOf(
                row(1, "parameters", "{\"chain_id\":\"$CHAIN_ID\",\"generation\":0}").copy(validationState = "materialized"),
                row(2, "record", "{\"chain_id\":\"$CHAIN_ID\",\"generation\":0}"),
                row(3, "epoch", "{\"chain_id\":\"$CHAIN_ID\",\"generation\":0,\"epoch\":0}"),
            )
        val rebuilt = PallaSyncImportHistoryBuilder.fromVerifiedRows(CHAIN_ID, rows)
        rebuilt.parameters.size shouldBe 1
        rebuilt.epochs.size shouldBe 1
        rebuilt.devices.size shouldBe 0
        rebuilt.envelopes.size shouldBe 0
        rebuilt.compactions.size shouldBe 0
    }

    @Test
    fun `history builder refuses unverified or foreign chain rows`() {
        val unverified = row(1, "parameters", "{\"chain_id\":\"$CHAIN_ID\"}").copy(validationState = "downloaded")
        shouldThrow<IllegalStateException> {
            PallaSyncImportHistoryBuilder.fromVerifiedRows(CHAIN_ID, listOf(unverified))
        }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"

        val foreign = row(1, "device", "{\"chain_id\":\"foreign\"}")
        shouldThrow<IllegalStateException> {
            PallaSyncImportHistoryBuilder.fromVerifiedRows(CHAIN_ID, listOf(foreign))
        }.message shouldBe "C_RECOVERY_ROOT_MISMATCH"
    }

    @Test
    fun `history builder refuses persisted entry hash mismatch`() {
        val tampered = row(1, "parameters", "{\"chain_id\":\"$CHAIN_ID\"}").copy(entryHash = "stale-hash")
        shouldThrow<IllegalStateException> {
            PallaSyncImportHistoryBuilder.fromVerifiedRows(CHAIN_ID, listOf(tampered))
        }.message shouldBe "C_LOG_HASH_MISMATCH"
    }

    @Test
    fun `import chunks keep authority heads fixed and split oversized histories`() {
        val history =
            history().copy(
                parameters = payloads("parameters", count = 5),
                epochs = payloads("epoch", count = 4),
                devices = payloads("device", count = 5),
                envelopes = payloads("envelope", count = 4),
                compactions = payloads("compaction", count = 3),
            )
        val maxBytes = 1_800
        val chunks = PallaSyncImportRelay.chunkHistories(CHAIN_ID, history, maxBytes)
        (chunks.size > 1) shouldBe true
        chunks.forEach { chunk ->
            chunk.parameters.last() shouldBe history.parameters.last()
            chunk.epochs.last() shouldBe history.epochs.last()
            (PallaSyncImportRelay.canonicalBody(CHAIN_ID, chunk).toByteArray(Charsets.UTF_8).size <= maxBytes) shouldBe true
        }
        (chunks.flatMap { it.parameters.dropLast(1) } + history.parameters.last()) shouldBe history.parameters
        (chunks.flatMap { it.epochs.dropLast(1) } + history.epochs.last()) shouldBe history.epochs
        chunks.flatMap { it.devices } shouldBe history.devices
        chunks.flatMap { it.envelopes } shouldBe history.envelopes
        chunks.flatMap { it.compactions } shouldBe history.compactions
    }

    @Test
    fun `import chunker rejects one object larger than the signed body limit`() {
        val history = history().copy(envelopes = payloads("envelope", count = 1, payloadBytes = 512))
        shouldThrow<IllegalStateException> {
            PallaSyncImportRelay.chunkHistories(CHAIN_ID, history, maxBytes = 500)
        }.message shouldBe "C_LOCAL_LIMIT"
    }

    private fun history(): PallaSyncImportRelay.History =
        PallaSyncImportRelay.History(
            parameters = Json.parseToJsonElement("""[{"chain_id":"$CHAIN_ID"}]""") as JsonArray,
            epochs = Json.parseToJsonElement("""[{"chain_id":"$CHAIN_ID"}]""") as JsonArray,
            devices = JsonArray(emptyList()),
            envelopes = JsonArray(emptyList()),
            compactions = JsonArray(emptyList()),
        )

    private fun payloads(
        kind: String,
        count: Int,
        payloadBytes: Int = 256,
    ): JsonArray =
        JsonArray(
            (0 until count).map { index ->
                Json.parseToJsonElement(
                    """{"chain_id":"$CHAIN_ID","kind":"$kind","generation":0,"epoch":$index,"payload":"${"x".repeat(payloadBytes)}"}""",
                )
            },
        )

    private fun row(
        sequence: Long,
        kind: String,
        value: String,
    ): LogEntryEntity {
        val signedJson = """{"kind":"$kind","object":$value}"""
        return LogEntryEntity(
            CHAIN_ID,
            "00000000-0000-4000-8000-000000000001",
            sequence,
            checkNotNull(PallaSyncCore.objectHash(signedJson)),
            signedJson,
            "{}",
            "authority_verified",
        )
    }

    private companion object {
        const val CHAIN_ID = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
    }
}
