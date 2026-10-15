package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.LogEntryEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class PallaSyncImportRelayTest {
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
    fun `history builder includes only verified control objects`() {
        val rows =
            listOf(
                row(1, "parameters", "{\"chain_id\":\"$CHAIN_ID\",\"generation\":0}"),
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

    private fun history(): PallaSyncImportRelay.History =
        PallaSyncImportRelay.History(
            parameters = Json.parseToJsonElement("""[{"chain_id":"$CHAIN_ID"}]""") as JsonArray,
            epochs = Json.parseToJsonElement("""[{"chain_id":"$CHAIN_ID"}]""") as JsonArray,
            devices = JsonArray(emptyList()),
            envelopes = JsonArray(emptyList()),
            compactions = JsonArray(emptyList()),
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
