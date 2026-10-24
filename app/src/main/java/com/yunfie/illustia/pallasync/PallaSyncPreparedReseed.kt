package com.yunfie.illustia.pallasync

import androidx.annotation.Keep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Base64

private const val RESEED_RECORD_KEY = 0
private const val RESEED_META_KEY = 1
private const val RESEED_TAG_KEY = 2
private const val RESEED_EPOCH_SECRET = 3
private const val RESEED_DEVICE_SIGNING = 4
private const val RESEED_DEVICE_KEX_PRIVATE = 5
private const val RESEED_MATERIAL_COUNT = 6
private const val RESEED_KEY_BYTES = 32

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
        check(material.size == RESEED_MATERIAL_COUNT && material.all { it.size == RESEED_KEY_BYTES }) { "C_SCHEMA_VIOLATION" }
    }

    /** The caller must save this complete bundle to OS-protected storage and verify readback. */
    internal fun keySnapshot(): PallaSyncKeySnapshot {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val record = Json.parseToJsonElement(publicResult.deviceRecordJson).jsonObject
        return PallaSyncKeySnapshot(
            chainId = publicResult.chainId,
            deviceId = publicResult.deviceId,
            encryptionKeyBase64Url = encode(RESEED_RECORD_KEY),
            signingKeyBase64Url = encode(RESEED_DEVICE_SIGNING),
            publicKeyBase64Url = record.getValue("device_public_key").jsonPrimitive.content,
            kexPrivateKeyBase64Url = encode(RESEED_DEVICE_KEX_PRIVATE),
            kexPublicKeyBase64Url = record.getValue("device_kex_public_key").jsonPrimitive.content,
            deviceMetaKeyBase64Url = encode(RESEED_META_KEY),
            collectionTagKeyBase64Url = encode(RESEED_TAG_KEY),
            epochSecretBase64Url = encode(RESEED_EPOCH_SECRET),
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
        return PallaSyncEpochSession.begin(context, material[RESEED_EPOCH_SECRET].copyOf())
    }

    internal fun signingSeedCopy(): ByteArray {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return material[RESEED_DEVICE_SIGNING].copyOf()
    }

    /** Public-only journal payload; the matching key material stays in OS-protected storage. */
    internal fun publicBundleJson(): String {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val value = publicResult
        return checkNotNull(
            PallaSyncCore.canonicalJson(
                buildJsonObject {
                    put("chain_id", value.chainId)
                    put("generation", value.generation)
                    put("device_id", value.deviceId)
                    put("parameters_hash", value.parametersHash)
                    put("epoch_hash", value.epochHash)
                    put("epoch_commitment", value.epochCommitment)
                    put("parameters", Json.parseToJsonElement(value.parametersJson))
                    put("epoch", Json.parseToJsonElement(value.epochJson))
                    put("device_record", Json.parseToJsonElement(value.deviceRecordJson))
                    put("envelopes", JsonArray(value.envelopesJson.map(Json::parseToJsonElement)))
                }.toString(),
            ),
        ) { "C_SCHEMA_VIOLATION" }
    }

    override fun close() {
        material.forEach { it.fill(0) }
        closed = true
    }

    private fun encode(index: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(material[index])
}

/** Reconstitute only from a bundle journal and a complete OS-keystore readback. */
internal fun resumePallaSyncPreparedReseed(
    publicBundleJson: String,
    savedKeys: PallaSyncKeySnapshot,
): PallaSyncPreparedReseed {
    val canonical = checkNotNull(PallaSyncCore.canonicalJson(publicBundleJson)) { "C_SCHEMA_VIOLATION" }
    check(canonical == publicBundleJson) { "C_SCHEMA_VIOLATION" }
    val bundle = Json.parseToJsonElement(canonical).jsonObject
    check(
        bundle.keys ==
            setOf(
                "chain_id",
                "generation",
                "device_id",
                "parameters_hash",
                "epoch_hash",
                "epoch_commitment",
                "parameters",
                "epoch",
                "device_record",
                "envelopes",
            ),
    ) { "C_SCHEMA_VIOLATION" }
    val parameters = bundle.getValue("parameters").jsonObject
    val record = bundle.getValue("device_record").jsonObject
    val epoch = bundle.getValue("epoch").jsonObject
    check(
        bundle.getValue("chain_id").jsonPrimitive.content == savedKeys.chainId &&
            bundle.getValue("device_id").jsonPrimitive.content == savedKeys.deviceId &&
            bundle.getValue("generation").jsonPrimitive.long > 0 &&
            parameters.getValue("chain_id").jsonPrimitive.content == savedKeys.chainId &&
            parameters.getValue("generation").jsonPrimitive.long == bundle.getValue("generation").jsonPrimitive.long &&
            PallaSyncCore.objectHash(parameters.toString()) == bundle.getValue("parameters_hash").jsonPrimitive.content &&
            epoch.getValue("chain_id").jsonPrimitive.content == savedKeys.chainId &&
            epoch.getValue("generation").jsonPrimitive.long == parameters.getValue("generation").jsonPrimitive.long &&
            epoch.getValue("epoch").jsonPrimitive.long == savedKeys.epoch &&
            epoch.getValue("epoch_commitment").jsonPrimitive.content == bundle.getValue("epoch_commitment").jsonPrimitive.content &&
            PallaSyncCore.objectHash(epoch.toString()) == bundle.getValue("epoch_hash").jsonPrimitive.content &&
            record.getValue("device_id").jsonPrimitive.content == savedKeys.deviceId &&
            record.getValue("chain_id").jsonPrimitive.content == savedKeys.chainId &&
            record.getValue("generation").jsonPrimitive.long == parameters.getValue("generation").jsonPrimitive.long &&
            record.getValue("device_public_key").jsonPrimitive.content == savedKeys.publicKeyBase64Url &&
            record.getValue("device_kex_public_key").jsonPrimitive.content == savedKeys.kexPublicKeyBase64Url &&
            epoch.getValue("members").jsonArray.any { it.jsonPrimitive.content == savedKeys.deviceId } &&
            PallaSyncCore.verifyDeviceRecord(record.toString()),
    ) { "C_COMMITMENT_MISMATCH" }
    val decoded = mutableListOf<ByteArray>()
    return try {
        decoded += decodeReseedKey(savedKeys.encryptionKeyBase64Url)
        decoded += decodeReseedKey(savedKeys.deviceMetaKeyBase64Url)
        decoded += decodeReseedKey(savedKeys.collectionTagKeyBase64Url)
        decoded += decodeReseedKey(savedKeys.epochSecretBase64Url)
        decoded += decodeReseedKey(savedKeys.signingKeyBase64Url)
        decoded += decodeReseedKey(savedKeys.kexPrivateKeyBase64Url)
        val material = decoded.toTypedArray()
        PallaSyncPreparedReseed(material, canonical)
    } catch (failure: Throwable) {
        decoded.forEach { it.fill(0) }
        throw failure
    }
}

private fun decodeReseedKey(value: String): ByteArray {
    val decoded = runCatching { Base64.getUrlDecoder().decode(value) }.getOrElse { error("C_SCHEMA_VIOLATION") }
    check(decoded.size == RESEED_KEY_BYTES && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value) {
        decoded.fill(0)
        "C_SCHEMA_VIOLATION"
    }
    return decoded
}
