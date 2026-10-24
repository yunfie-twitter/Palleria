package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Base64

/** §§5.3/11.2. Bind every decrypt/tag operation to the authenticated epoch and policy. */
internal class PallaSyncEpochSession private constructor(
    val handle: Long,
) : AutoCloseable {
    fun tag(collection: String): String? = PallaSyncCore.epochCollectionTag(handle, collection)

    fun decryptRecord(record: String): String = PallaSyncCore.decryptEpochRecord(handle, record)

    /** Author key must be selected from this chain's authenticated registry history. */
    fun decryptSnapshotPart(
        record: String,
        authorPublicKeyBase64Url: String,
    ): String = PallaSyncCore.decryptEpochSnapshotPart(handle, record, authorPublicKeyBase64Url)

    /** The caller transfers ownership of the short-lived seed; it is wiped even on JNI failure. */
    fun createSnapshotRecord(
        recordId: String,
        deviceId: String,
        partJson: String,
        signingSeed: ByteArray,
    ): String =
        try {
            PallaSyncSnapshotCore.createSnapshotRecord(handle, recordId, deviceId, partJson, signingSeed)
        } finally {
            signingSeed.fill(0)
        }

    fun decryptDevice(record: String): String = PallaSyncCore.decryptEpochDevice(handle, record)

    override fun close() = PallaSyncCore.endEpochSession(handle)

    companion object {
        fun begin(
            keys: PallaSyncKeySnapshot,
            authority: String,
            epoch: Long,
        ): PallaSyncEpochSession {
            val context = context(keys, authority, epoch)
            return begin(context, Base64.getUrlDecoder().decode(keys.epochSecretBase64Url))
        }

        /** Retained SE must rederive all keys, not mix a new record key with an old epoch. */
        fun materialize(
            keys: PallaSyncKeySnapshot,
            authority: String,
        ): PallaSyncKeySnapshot {
            val secret = Base64.getUrlDecoder().decode(keys.epochSecretBase64Url)
            return try {
                PallaSyncCore.deriveEpochMaterial(context(keys, authority, keys.epoch).toString(), secret).use {
                    it.snapshot(keys, keys.epoch)
                }
            } finally {
                secret.fill(0)
            }
        }

        private fun context(
            keys: PallaSyncKeySnapshot,
            authority: String,
            epoch: Long,
        ): JsonObject {
            check(epoch == keys.epoch && keys.epochSecretBase64Url.isNotEmpty()) { "C_MISSING_DEPENDENCY" }
            val known = Json.parseToJsonElement(authority).jsonObject
            val signed =
                known
                    .getValue("epochs")
                    .jsonObject
                    .getValue(epoch.toString())
                    .jsonObject
                    .getValue("record")
                    .jsonObject
            check(
                signed.getValue("chain_id").jsonPrimitive.content == keys.chainId && signed.getValue("epoch").jsonPrimitive.long == epoch,
            ) {
                "C_MISSING_DEPENDENCY"
            }
            val policy =
                known
                    .getValue("parameters")
                    .jsonObject
                    .getValue("policy")
                    .jsonObject
            return buildJsonObject {
                put("chain_id", signed.getValue("chain_id"))
                put("generation", signed.getValue("generation"))
                put("epoch", signed.getValue("epoch"))
                put("epoch_commitment", signed.getValue("epoch_commitment"))
                put("metadata_profile", policy.getValue("metadata_profile"))
            }
        }

        /** Owns and wipes the mutable secret even if context validation or JNI construction fails. */
        fun begin(
            context: JsonObject,
            secret: ByteArray,
        ): PallaSyncEpochSession =
            try {
                val handle = PallaSyncCore.beginEpochSession(context.toString(), secret)
                check(handle > 0) { "C_MISSING_DEPENDENCY" }
                PallaSyncEpochSession(handle)
            } finally {
                secret.fill(0)
            }
    }
}
