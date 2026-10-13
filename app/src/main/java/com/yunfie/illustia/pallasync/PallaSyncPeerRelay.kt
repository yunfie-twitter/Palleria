package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Exact REST controls; all requests use the shared policy/origin gate (§10.7/§11.7). */
internal class PallaSyncPeerRelay(
    private val store: PallaSyncLocalStore,
    private val remote: PallaSyncRemoteService,
    private val crypto: PallaSyncCryptoService,
) {
    fun request(
        url: HttpUrl,
        device: PallaSyncKeySnapshot,
        method: String,
        body: String = "",
        validate: (ByteArray) -> String,
    ): String {
        val token =
            checkNotNull(
                crypto.createCapabilityToken(
                    PallaSyncUrls.extractOrigin(url),
                    checkNotNull(device.chainId),
                    "device",
                    device.deviceId,
                    method,
                    url.encodedPath,
                    url.encodedQuery.orEmpty(),
                    body,
                    device.signingKeyBase64Url,
                ),
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $token")
                .apply { if (method == "GET") get() else post(body.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType())) }
                .build()
        return when (val result = remote.execute(request) { PallaSyncHttpResult.Success(validate(it.pallaSyncBytes())) }) {
            is PallaSyncHttpResult.Success -> result.value
            else -> throw PallaSyncInitialHistory.Failure(result)
        }
    }

    suspend fun retainReceipts(
        authority: JsonObject,
        response: JsonObject,
    ) {
        val pin = authority.getValue("pin").jsonObject
        for (value in response.getValue("upload_receipts").jsonArray) {
            val receipt = value.jsonObject
            store.protocolDao().retainEvidence(
                ProtectedEvidenceEntity(
                    pin.getValue("origin").jsonPrimitive.content,
                    receipt.getValue("relay_key_id").jsonPrimitive.content,
                    pin.getValue("chain_id").jsonPrimitive.content,
                    pin.getValue("log_id").jsonPrimitive.content,
                    "upload_receipt",
                    receipt.getValue("relay_seq").jsonPrimitive.long,
                    pallaSyncPeerHash(receipt),
                    receipt.toString(),
                ),
            )
        }
    }
}

internal fun pallaSyncPeerUrl(
    base: HttpUrl,
    chain: String,
    suffix: String,
): HttpUrl = base.newBuilder().encodedPath("/pallasync/v3/chains/$chain/$suffix").build()

internal fun pallaSyncPeerHash(value: JsonObject): String =
    checkNotNull(PallaSyncCore.objectHash(value.toString())) {
        "C_SCHEMA_VIOLATION"
    }
