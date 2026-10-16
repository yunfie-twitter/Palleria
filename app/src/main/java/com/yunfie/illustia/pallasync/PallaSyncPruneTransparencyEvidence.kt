package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Binds an authenticated §17.10 prune entry to its exact §10.12 request and signed compaction. */
internal object PallaSyncPruneTransparencyEvidence {
    fun validate(
        entry: JsonObject,
        compaction: JsonObject,
        chainId: String,
        logId: String,
        expectedHeadHash: String,
        newMinimumSequence: Long,
    ) {
        check(newMinimumSequence > 1) { "C_TRANSPARENCY_INVALID" }
        val throughSeq = newMinimumSequence - 1
        check(
            compaction["chain_id"]?.jsonPrimitive?.content == chainId &&
                compaction["log_id"]?.jsonPrimitive?.content == logId &&
                compaction["covered_through_seq"]?.jsonPrimitive?.long == throughSeq,
        ) { "C_SNAPSHOT_INVALID" }
        val payload =
            buildJsonObject {
                put("compaction_id", compaction.getValue("compaction_id").jsonPrimitive.content)
                put("through_seq", throughSeq)
            }
        check(
            entry["chain_id"]?.jsonPrimitive?.content == chainId &&
                entry["log_id"]?.jsonPrimitive?.content == logId &&
                entry["operation"]?.jsonPrimitive?.content == "prune" &&
                entry["object_hash"]?.jsonPrimitive?.content == PallaSyncCore.objectHash(payload.toString()) &&
                entry["resulting_head_hash"]?.jsonPrimitive?.content == expectedHeadHash &&
                entry["relay_seq"]?.let { it == kotlinx.serialization.json.JsonNull } == true &&
                entry["code"]?.let { it == kotlinx.serialization.json.JsonNull } == true,
        ) { "C_TRANSPARENCY_INVALID" }
    }
}
