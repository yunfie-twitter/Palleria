package com.yunfie.illustia.pallasync

import androidx.annotation.Keep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Base64

/** Fresh target-generation keys are held only for the foreground reseed operation. */
@Keep
class PallaSyncPreparedReseed(
    private val material: Array<ByteArray>,
    publicJson: String,
) : AutoCloseable {
    data class PublicResult(
        val chainId: String,
        val generation: Long,
        val deviceId: String,
        val parametersHash: String,
        val epochHash: String,
        val epochCommitment: String,
        val parametersJson: String,
        val epochJson: String,
        val deviceRecordJson: String,
        val envelopesJson: List<String>,
    ) {
        val epoch: Long
            get() =
                Json
                    .parseToJsonElement(epochJson)
                    .jsonObject
                    .getValue("epoch")
                    .jsonPrimitive.long
    }

    val publicResult: PublicResult =
        Json.parseToJsonElement(publicJson).jsonObject.let { value ->
            PublicResult(
                chainId = value.getValue("chain_id").jsonPrimitive.content,
                generation = value.getValue("generation").jsonPrimitive.long,
                deviceId = value.getValue("device_id").jsonPrimitive.content,
                parametersHash = value.getValue("parameters_hash").jsonPrimitive.content,
                epochHash = value.getValue("epoch_hash").jsonPrimitive.content,
                epochCommitment = value.getValue("epoch_commitment").jsonPrimitive.content,
                parametersJson = value.getValue("parameters").toString(),
                epochJson = value.getValue("epoch").toString(),
                deviceRecordJson = value.getValue("device_record").toString(),
                envelopesJson = value.getValue("envelopes").jsonArray.map { it.toString() },
            )
        }
    private var closed = false

    init {
        check(material.size == MATERIAL_COUNT && material.all { it.size == KEY_BYTES }) { "C_SCHEMA_VIOLATION" }
    }

    /** The caller must save this complete bundle to OS-protected storage and verify readback. */
    internal fun keySnapshot(): PallaSyncKeySnapshot {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val record = Json.parseToJsonElement(publicResult.deviceRecordJson).jsonObject
        return PallaSyncKeySnapshot(
            chainId = publicResult.chainId,
            deviceId = publicResult.deviceId,
            encryptionKeyBase64Url = encode(RECORD_KEY),
            signingKeyBase64Url = encode(DEVICE_SIGNING),
            publicKeyBase64Url = record.getValue("device_public_key").jsonPrimitive.content,
            kexPrivateKeyBase64Url = encode(DEVICE_KEX_PRIVATE),
            kexPublicKeyBase64Url = record.getValue("device_kex_public_key").jsonPrimitive.content,
            deviceMetaKeyBase64Url = encode(META_KEY),
            collectionTagKeyBase64Url = encode(TAG_KEY),
            epochSecretBase64Url = encode(EPOCH_SECRET),
            epoch = publicResult.epoch,
        )
    }

    /** Save the candidate as a separate pending identity and require exact encrypted-store readback. */
    internal fun persistPendingKeys(keystore: PallaSyncKeystore): PallaSyncKeySnapshot {
        val snapshot = keySnapshot()
        check(keystore.savePendingChainKeys(snapshot)) { "C_LOCAL_STORAGE_FULL" }
        val readback = keystore.getPendingChainKeys()
        if (readback != snapshot) {
            keystore.clearPendingChainKeys()
            error("C_COMMITMENT_MISMATCH")
        }
        return readback
    }

    /** Open target-generation crypto using only this operation's fresh epoch secret. */
    internal fun openEpochSession(): PallaSyncEpochSession {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val epoch = Json.parseToJsonElement(publicResult.epochJson).jsonObject
        val parameters = Json.parseToJsonElement(publicResult.parametersJson).jsonObject
        val context =
            kotlinx.serialization.json.buildJsonObject {
                put("chain_id", publicResult.chainId)
                put("generation", publicResult.generation)
                put("epoch", publicResult.epoch)
                put("epoch_commitment", publicResult.epochCommitment)
                put("metadata_profile", parameters.getValue("policy").jsonObject.getValue("metadata_profile"))
            }
        check(epoch.getValue("epoch_commitment").jsonPrimitive.content == publicResult.epochCommitment) {
            "C_COMMITMENT_MISMATCH"
        }
        return PallaSyncEpochSession.begin(context, material[EPOCH_SECRET].copyOf())
    }

    internal fun signingSeedCopy(): ByteArray {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return material[DEVICE_SIGNING].copyOf()
    }

    override fun close() {
        material.forEach { it.fill(0) }
        closed = true
    }

    private fun encode(index: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(material[index])

    private companion object {
        const val RECORD_KEY = 0
        const val META_KEY = 1
        const val TAG_KEY = 2
        const val EPOCH_SECRET = 3
        const val DEVICE_SIGNING = 4
        const val DEVICE_KEX_PRIVATE = 5
        const val MATERIAL_COUNT = 6
        const val KEY_BYTES = 32
    }
}
