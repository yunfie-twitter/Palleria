package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

class PallaSyncPruneTransparencyEvidenceTest {
    @Test
    fun `prune evidence binds exact covered prefix and unchanged data head`() {
        val compaction = compaction(42)
        val payload = """{"compaction_id":"compaction","through_seq":42}"""
        val entry =
            buildJsonObject {
                put("chain_id", CHAIN)
                put("log_id", LOG)
                put("operation", "prune")
                put("object_hash", checkNotNull(PallaSyncCore.objectHash(payload)))
                put("resulting_head_hash", HEAD)
                put("relay_seq", kotlinx.serialization.json.JsonNull)
                put("code", kotlinx.serialization.json.JsonNull)
            }

        PallaSyncPruneTransparencyEvidence.validate(entry, compaction, CHAIN, LOG, HEAD, 43)
    }

    @Test
    fun `prune evidence rejects altered request hash head or coverage`() {
        val compaction = compaction(42)
        val entry = validEntry()
        listOf(
            { PallaSyncPruneTransparencyEvidence.validate(entry, compaction, CHAIN, LOG, "other", 43) },
            { PallaSyncPruneTransparencyEvidence.validate(entry, compaction, CHAIN, LOG, HEAD, 44) },
            {
                val changed = JsonObject(entry + ("object_hash" to JsonPrimitive("wrong")))
                PallaSyncPruneTransparencyEvidence.validate(changed, compaction, CHAIN, LOG, HEAD, 43)
            },
        ).forEach { verify -> shouldThrow<IllegalStateException> { verify() } }
    }

    private fun validEntry() =
        buildJsonObject {
            put("chain_id", CHAIN)
            put("log_id", LOG)
            put("operation", "prune")
            put(
                "object_hash",
                checkNotNull(
                    PallaSyncCore.objectHash(
                        """{"compaction_id":"compaction","through_seq":42}""",
                    ),
                ),
            )
            put("resulting_head_hash", HEAD)
            put("relay_seq", kotlinx.serialization.json.JsonNull)
            put("code", kotlinx.serialization.json.JsonNull)
        }

    private fun compaction(through: Long) =
        buildJsonObject {
            put("compaction_id", "compaction")
            put("chain_id", CHAIN)
            put("log_id", LOG)
            put("covered_through_seq", through)
        }

    private companion object {
        const val CHAIN = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val LOG = "11111111-1111-4111-8111-111111111111"
        const val HEAD = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
    }
}
