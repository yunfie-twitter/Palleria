package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Dedicated §10.12 compaction publication; the receipt callback must prove transparency inclusion and retain it. */
internal class PallaSyncSnapshotCompactionRelay(
    private val remote: PallaSyncRemoteService,
    private val base: HttpUrl,
    private val chainId: String,
    private val pin: JsonObject,
    private val token: (HttpUrl, String, String, String, String, String) -> String?,
    private val verifyAndRetainReceipt: suspend (compaction: JsonObject, response: JsonObject) -> Unit,
) {
    suspend fun publish(compaction: JsonObject): PallaSyncHttpResult<JsonObject> {
        val request =
            try {
                request(compaction)
            } catch (error: IllegalStateException) {
                return PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_SNAPSHOT_INVALID")
            }
        val checked =
            remote.execute(request) { response ->
                PallaSyncHttpResult.Success(
                    PallaSyncSnapshotVerifier.validatePublishedCompaction(
                        response.pallaSyncBytes(),
                        compaction,
                        pin,
                    ),
                )
            }
        return retain(compaction, checked)
    }

    private fun request(compaction: JsonObject): Request {
        val canonicalCompaction =
            checkNotNull(PallaSyncCore.canonicalJson(compaction.toString())) { "C_SNAPSHOT_INVALID" }
        check(
            compaction["chain_id"]?.jsonPrimitive?.content == chainId &&
                pin["chain_id"]?.jsonPrimitive?.content == chainId,
        ) { "C_SNAPSHOT_INVALID" }
        val compactionValue = Json.parseToJsonElement(canonicalCompaction)
        val wrapper = buildJsonObject { put("compaction", compactionValue) }
        val body = checkNotNull(PallaSyncCore.canonicalJson(wrapper.toString())) { "C_SCHEMA_VIOLATION" }
        val url = PallaSyncUrls.snapshots(base, chainId)
        val authorization = checkNotNull(token(base, chainId, "POST", url.encodedPath, "", body)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return Request
            .Builder()
            .url(url)
            .header("Accept", PALLASYNC_MEDIA_TYPE)
            .header("Authorization", "PallaSync $authorization")
            .post(body.toByteArray(Charsets.UTF_8).toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
            .build()
    }

    private suspend fun retain(
        compaction: JsonObject,
        checked: PallaSyncHttpResult<JsonObject>,
    ): PallaSyncHttpResult<JsonObject> =
        when (checked) {
            is PallaSyncHttpResult.Success -> {
                try {
                    verifyAndRetainReceipt(compaction, checked.value)
                    checked
                } catch (error: IllegalStateException) {
                    PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_ACCEPTANCE_UNPROVEN")
                }
            }

            PallaSyncHttpResult.Gone -> {
                PallaSyncHttpResult.Gone
            }

            is PallaSyncHttpResult.Retryable -> {
                checked
            }

            is PallaSyncHttpResult.ProtocolError -> {
                checked
            }
        }
}
