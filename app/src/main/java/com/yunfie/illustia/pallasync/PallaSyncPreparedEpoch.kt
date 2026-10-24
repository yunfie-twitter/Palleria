package com.yunfie.illustia.pallasync

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.Base64

/** Normal epoch keys have mutable JNI ownership. Root and recovery material never enter this object. */
@Keep
class PallaSyncPreparedEpoch(
    private val material: Array<ByteArray>,
    publicJson: String,
) : AutoCloseable {
    @Serializable
    data class PublicResult(
        @SerialName("chain_id") val chainId: String,
        val generation: Long,
        val epoch: Long,
        @SerialName("epoch_hash") val epochHash: String,
        @SerialName("epoch_commitment") val epochCommitment: String,
        @SerialName("request_body") val requestBody: JsonObject,
    )

    val publicResult: PublicResult = Json.decodeFromString(publicJson)
    private var closed = false

    init {
        check(material.size == MATERIAL_COUNT && material.all { it.size == KEY_BYTES }) { "C_SCHEMA_VIOLATION" }
    }

    internal fun snapshot(previous: PallaSyncKeySnapshot): PallaSyncKeySnapshot {
        check(!closed && previous.chainId == publicResult.chainId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return previous.copy(
            epoch = publicResult.epoch,
            encryptionKeyBase64Url = encode(RECORD_KEY),
            deviceMetaKeyBase64Url = encode(META_KEY),
            collectionTagKeyBase64Url = encode(TAG_KEY),
            epochSecretBase64Url = encode(EPOCH_SECRET),
        )
    }

    /** Caller owns this normal secret copy and must wipe it after OS-protected readback. */
    internal fun epochSecret(): ByteArray {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return material[EPOCH_SECRET].copyOf()
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
        const val MATERIAL_COUNT = 4
        const val KEY_BYTES = 32
    }
}
