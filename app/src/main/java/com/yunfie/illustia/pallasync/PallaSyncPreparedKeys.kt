package com.yunfie.illustia.pallasync

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** Typed JNI ownership. Only public signed requests are JSON; private material is mutable and wiped. */
@Keep
class PallaSyncPreparedKeys(
    private val material: Array<ByteArray>,
    publicJson: String,
) : AutoCloseable {
    val publicResult: PublicResult = Json.decodeFromString(publicJson)

    init {
        check(material.size == MATERIAL_COUNT && material.all { it.size == KEY_BYTES }) { "C_SCHEMA_VIOLATION" }
    }

    @Serializable
    data class PublicResult(
        @SerialName("chain_id") val chainId: String,
        @SerialName("device_id") val deviceId: String,
        @SerialName("device_public_key") val devicePublicKey: String,
        @SerialName("device_kex_public_key") val deviceKexPublicKey: String,
        val epoch: Long,
        @SerialName("epoch_commitment") val epochCommitment: String,
        @SerialName("request_body_json") val requestBody: String,
        @SerialName("keys_ack_body_json") val keysAckBody: String?,
    )

    // Adapter for the existing encrypted preference store. No secret enters a JSON serializer.
    fun snapshot(): PallaSyncKeySnapshot =
        PallaSyncKeySnapshot(
            chainId = publicResult.chainId,
            deviceId = publicResult.deviceId,
            encryptionKeyBase64Url = encode(RECORD_KEY),
            signingKeyBase64Url = encode(SIGNING_SEED),
            publicKeyBase64Url = publicResult.devicePublicKey,
            kexPrivateKeyBase64Url = encode(KEX_PRIVATE),
            kexPublicKeyBase64Url = publicResult.deviceKexPublicKey,
            deviceMetaKeyBase64Url = encode(META_KEY),
            collectionTagKeyBase64Url = encode(TAG_KEY),
            epochSecretBase64Url = encode(EPOCH_SECRET),
            epoch = publicResult.epoch,
        )

    private var closed = false

    override fun close() {
        material.forEach { it.fill(0) }
        closed = true
    }

    private fun encode(index: Int): String {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(material[index])
    }

    private companion object {
        const val RECORD_KEY = 0
        const val SIGNING_SEED = 1
        const val KEX_PRIVATE = 2
        const val META_KEY = 3
        const val TAG_KEY = 4
        const val EPOCH_SECRET = 5
        const val MATERIAL_COUNT = 6
        const val KEY_BYTES = 32
    }
}
