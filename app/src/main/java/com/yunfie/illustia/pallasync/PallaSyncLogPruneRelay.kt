package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Explicit §10.12 prune request. This transport never infers authorization from snapshot publication. */
internal class PallaSyncLogPruneRelay(
    private val remote: PallaSyncRemoteService,
    private val base: HttpUrl,
    private val chainId: String,
    private val token: (HttpUrl, String, String, String, String, String) -> String?,
    private val verifyAndRetainPrune: suspend (compactionId: String, throughSeq: Long, response: JsonObject) -> Unit,
) {
    suspend fun prune(
        compactionId: String,
        throughSeq: Long,
        adminOperation: JsonObject,
    ): PallaSyncHttpResult<JsonObject> {
        val request =
            try {
                request(compactionId, throughSeq, adminOperation)
            } catch (error: IllegalStateException) {
                return PallaSyncHttpResult.ProtocolError(
                    error.message?.takeIf { it.startsWith("C_") } ?: "C_SCHEMA_VIOLATION",
                )
            }
        val checked =
            remote.execute(request, explicitRootAuthorization = true) { response ->
                validatePruneResponse(response.pallaSyncBytes(), compactionId, throughSeq)
            }
        return when (checked) {
            is PallaSyncHttpResult.Success -> {
                try {
                    verifyAndRetainPrune(compactionId, throughSeq, checked.value)
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

    private fun request(
        compactionId: String,
        throughSeq: Long,
        adminOperation: JsonObject,
    ): Request {
        check(throughSeq in 0..9_007_199_254_740_991L) { "C_SCHEMA_VIOLATION" }
        check(adminOperation["action"]?.jsonPrimitive?.content == "prune_log") { "C_RECOVERY_AUTH_REQUIRED" }
        check(adminOperation["chain_id"]?.jsonPrimitive?.content == chainId) { "C_RECOVERY_ROOT_MISMATCH" }
        val target =
            buildJsonObject {
                put("compaction_id", compactionId)
                put("through_seq", throughSeq)
            }
        check(adminOperation["target_hash"]?.jsonPrimitive?.content == PallaSyncCore.objectHash(target.toString())) {
            "C_RECOVERY_AUTH_REQUIRED"
        }
        val canonicalOperation =
            checkNotNull(PallaSyncCore.canonicalJson(adminOperation.toString())) { "C_SCHEMA_VIOLATION" }
        val body =
            checkNotNull(
                PallaSyncCore.canonicalJson(
                    buildJsonObject {
                        put("compaction_id", compactionId)
                        put("through_seq", throughSeq)
                        put("admin_operation", Json.parseToJsonElement(canonicalOperation))
                    }.toString(),
                ),
            ) { "C_SCHEMA_VIOLATION" }
        val url = PallaSyncUrls.logPrune(base, chainId)
        val authorization =
            checkNotNull(token(base, chainId, "POST", url.encodedPath, "", body)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return Request
            .Builder()
            .url(url)
            .header("Accept", PALLASYNC_MEDIA_TYPE)
            .header("Authorization", "PallaSync $authorization")
            .post(body.toByteArray(Charsets.UTF_8).toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
            .build()
    }

    companion object {
        internal fun validatePruneResponse(
            bytes: ByteArray,
            compactionId: String,
            throughSeq: Long,
        ): PallaSyncHttpResult.Success<JsonObject> {
            check(bytes.size in 1..4_096) { "C_LOCAL_LIMIT" }
            val text = PallaSyncDeletionEvidenceArchive.decodeUtf8(bytes)
            val value = PallaSyncDeletionEvidenceArchive.parseStrictObject(text)
            check(value.keys == setOf("min_available_seq", "compaction_id")) { "C_SCHEMA_VIOLATION" }
            check(value["compaction_id"]?.jsonPrimitive?.content == compactionId) { "C_ACCEPTANCE_UNPROVEN" }
            val sequence = value["min_available_seq"]?.jsonPrimitive ?: error("C_SCHEMA_VIOLATION")
            check(!sequence.isString) { "C_SCHEMA_VIOLATION" }
            val minimum = sequence.long
            check(minimum == throughSeq + 1L) { "C_ACCEPTANCE_UNPROVEN" }
            return PallaSyncHttpResult.Success(value)
        }
    }
}
