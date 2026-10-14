package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Merge independently verified local witness archives without changing their receipt binding. */
internal object PallaSyncDeletionEvidenceMerge {
    fun merge(archives: List<JsonObject>): JsonObject? {
        if (archives.isEmpty()) return null
        val receipt = archives.first().getValue("delete_receipt")
        val sourceBundle = archives.first().getValue("evidence_bundle").jsonObject
        val witnessesByDevice = sortedMapOf<String, JsonObject>()
        archives.forEach { archive ->
            check(archive.keys == setOf("delete_receipt", "evidence_bundle")) { "C_SCHEMA_VIOLATION" }
            check(archive.getValue("delete_receipt") == receipt) { "C_DELETION_UNVERIFIED" }
            archive
                .getValue("evidence_bundle")
                .jsonObject
                .getValue("witnesses")
                .jsonArray
                .forEach { value ->
                    val witness = value.jsonObject
                    val device = witness.getValue("witness_device_id").jsonPrimitive.content
                    val previous = witnessesByDevice.putIfAbsent(device, witness)
                    check(previous == null || previous == witness) { "C_DEVICE_EQUIVOCATION" }
                }
        }
        val bundle =
            buildJsonObject {
                put("type", sourceBundle.getValue("type"))
                put("version", sourceBundle.getValue("version"))
                put("chain_id", sourceBundle.getValue("chain_id"))
                put("deletion_id", sourceBundle.getValue("deletion_id"))
                put("delete_receipt_hash", sourceBundle.getValue("delete_receipt_hash"))
                put("witnesses", JsonArray(witnessesByDevice.values.toList()))
            }
        return buildJsonObject {
            put("delete_receipt", receipt)
            put("evidence_bundle", bundle)
        }
    }
}
