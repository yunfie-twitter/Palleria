package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Index authenticated recipient evidence without scanning the application history. */
internal object PallaSyncEnvelopeEvidence {
    fun kind(
        device: String,
        epoch: Long,
    ) = "device_envelope:$device:$epoch"

    suspend fun retain(
        store: PallaSyncLocalStore,
        chain: String,
        entry: JsonObject,
    ) {
        if (entry.getValue("kind").jsonPrimitive.content != "envelope") return
        val envelope = entry.getValue("object").jsonObject
        if (envelope.getValue("recipient_kind").jsonPrimitive.content != "device") return
        val device = envelope.getValue("recipient_device_id").jsonPrimitive.content
        val epoch = envelope.getValue("epoch").jsonPrimitive.long
        val kind = kind(device, epoch)
        val hash = checkNotNull(PallaSyncCore.objectHash(envelope.toString()))
        val existing = store.protocolDao().authorityObject(chain, kind, hash)
        if (existing != null) {
            val oldEntry =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(existing.signedJson)
                    .jsonObject
            check(
                oldEntry.getValue("kind").jsonPrimitive.content == "envelope" &&
                    oldEntry.getValue("object") == envelope,
            ) { "C_LOG_HASH_MISMATCH" }
            return
        }
        store.protocolDao().retainAuthority(
            AuthorityHistoryEntity(
                chain,
                kind,
                hash,
                envelope.getValue("generation").jsonPrimitive.long,
                entry.toString(),
                "authority_verified",
            ),
        )
    }
}
