package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Dedicated §10.11 snapshot-part upload path. The required callback must verify transparency
 * inclusion and durably retain every validated receipt before this operation reports success.
 */
internal class PallaSyncSnapshotPartRelay(
    private val remote: PallaSyncRemoteService,
    private val base: HttpUrl,
    private val chainId: String,
    private val pin: JsonObject,
    private val limits: PallaSyncLimits,
    private val token: (HttpUrl, String, String, String, String, String) -> String?,
    private val verifyAndRetainReceipts: suspend (records: List<JsonObject>, response: JsonObject) -> Unit,
) {
    suspend fun upload(records: List<JsonObject>): PallaSyncHttpResult<JsonObject> {
        val prepared =
            try {
                prepare(records)
            } catch (error: IllegalStateException) {
                return PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_SCHEMA_VIOLATION")
            }
        val checked = execute(prepared)
        return retain(prepared.records, checked)
    }

    private fun prepare(records: List<JsonObject>): Prepared {
        check(records.isNotEmpty() && records.size <= minOf(MAX_SNAPSHOT_UPLOAD_RECORDS, limits.maxRecordsPerUpload)) {
            "C_LOCAL_LIMIT"
        }
        val canonicalRecords = records.map { record -> canonicalObject(record) }
        check(canonicalRecords.map { it["record_id"] }.toSet().size == canonicalRecords.size) { "C_DEVICE_EQUIVOCATION" }
        val body =
            checkNotNull(
                PallaSyncCore.canonicalJson(
                    buildJsonObject { put("records", JsonArray(canonicalRecords)) }.toString(),
                ),
            ) { "C_SCHEMA_VIOLATION" }
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val submittedBytes = canonicalRecords.map { it.toString() }
        check(bodyBytes.size <= limits.maxRequestBytes && recordUploadBatchSize(submittedBytes, limits) == canonicalRecords.size) {
            "C_LOCAL_LIMIT"
        }

        val url = PallaSyncUrls.snapshotParts(base, chainId)
        val path = url.encodedPath
        val authorization = checkNotNull(token(base, chainId, "POST", path, "", body)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $authorization")
                .post(body.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
                .build()
        return Prepared(canonicalRecords, request)
    }

    private fun canonicalObject(record: JsonObject): JsonObject =
        PallaSyncCore
            .canonicalJson(record.toString())
            ?.let {
                kotlinx.serialization.json.Json
                    .parseToJsonElement(it) as? JsonObject
            }
            ?: error("C_SCHEMA_VIOLATION")

    private fun execute(prepared: Prepared): PallaSyncHttpResult<JsonObject> =
        remote.execute(prepared.request) { response ->
            PallaSyncHttpResult.Success(
                PallaSyncSnapshotVerifier.validateUploadedParts(
                    response.pallaSyncBytes(),
                    JsonArray(prepared.records),
                    pin,
                ),
            )
        }

    private suspend fun retain(
        records: List<JsonObject>,
        checked: PallaSyncHttpResult<JsonObject>,
    ): PallaSyncHttpResult<JsonObject> =
        when (checked) {
            is PallaSyncHttpResult.Success -> {
                try {
                    verifyAndRetainReceipts(records, checked.value)
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

    private data class Prepared(
        val records: List<JsonObject>,
        val request: Request,
    )

    private companion object {
        const val MAX_SNAPSHOT_UPLOAD_RECORDS = 64
    }
}
