package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** §10.13: retransmit only retained record/receipt pairs under an explicit admin root session. */
internal class PallaSyncRecordRepairRelay(
    private val remote: PallaSyncRemoteService,
) {
    companion object {
        private const val UUID_VERSION_4 = 4
        private const val UUID_VARIANT_RFC = 2

        internal fun canonicalRequestBody(
            sourceLogId: String,
            records: List<SourceRecord>,
        ): String {
            val parsedSourceLogId = runCatching { java.util.UUID.fromString(sourceLogId) }.getOrNull()
            check(
                parsedSourceLogId?.version() == UUID_VERSION_4 &&
                    parsedSourceLogId.variant() == UUID_VARIANT_RFC &&
                    parsedSourceLogId.toString() == sourceLogId,
            ) { "C_SCHEMA_VIOLATION" }
            check(records.size in 1..PALLASYNC_HARD_LIMITS.maxRecordsPerUpload) { "C_SCHEMA_VIOLATION" }
            val bodyObject =
                buildJsonObject {
                    put("source_log_id", sourceLogId)
                    put("records", JsonArray(records.map(SourceRecord::record)))
                    put("receipts", JsonArray(records.map(SourceRecord::entry)))
                }
            return checkNotNull(PallaSyncCore.canonicalJson(bodyObject.toString())) { "C_SCHEMA_VIOLATION" }
        }
    }

    data class SourceRecord(
        val entry: JsonObject,
        val record: JsonObject,
    )

    fun requestBody(
        sourceLogId: String,
        records: List<SourceRecord>,
    ): String = canonicalRequestBody(sourceLogId, records)

    fun fits(
        sourceLogId: String,
        records: List<SourceRecord>,
    ): Boolean =
        records.size <= PALLASYNC_HARD_LIMITS.maxRecordsPerUpload &&
            runCatching { requestBody(sourceLogId, records).toByteArray(Charsets.UTF_8).size <= PALLASYNC_HARD_LIMITS.maxRequestBytes }
                .getOrDefault(false)

    fun submit(
        root: PallaSyncRootSession,
        base: HttpUrl,
        sourceLogId: String,
        pinJson: String,
        records: List<SourceRecord>,
    ): PallaSyncHttpResult<PallaSyncPostRecordsResponse> {
        check(root.operation == PallaSyncRootSession.Operation.RESEED) { "C_RECOVERY_AUTH_REQUIRED" }
        check(records.size in 1..PALLASYNC_HARD_LIMITS.maxRecordsPerUpload) { "C_SCHEMA_VIOLATION" }
        val ids = HashSet<String>()
        val sequences = HashSet<Long>()
        records.forEach { source ->
            check(
                source.entry
                    .getValue("kind")
                    .jsonPrimitive.content == "record",
            ) { "C_SCHEMA_VIOLATION" }
            check(source.entry.getValue("object").jsonObject == source.record) { "C_ACCEPTANCE_UNPROVEN" }
            check(
                ids.add(
                    source.record
                        .getValue("record_id")
                        .jsonPrimitive.content,
                ),
            ) { "C_RECORD_ID_CONFLICT" }
            check(
                sequences.add(
                    source.entry
                        .getValue("relay_seq")
                        .jsonPrimitive.content
                        .toLong(),
                ),
            ) { "C_ACCEPTANCE_UNPROVEN" }
        }
        val body = requestBody(sourceLogId, records)
        check(body.toByteArray(Charsets.UTF_8).size <= PALLASYNC_HARD_LIMITS.maxRequestBytes) { "C_LOCAL_LIMIT" }
        val url = PallaSyncUrls.recordRepair(base, root.identity.chainId)
        val origin = PallaSyncUrls.extractOrigin(base)
        val token =
            checkNotNull(
                root.token(
                    origin,
                    "POST",
                    url.encodedPath,
                    body = body.toByteArray(Charsets.UTF_8),
                ),
            ) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
        val request = createRequest(url, token, body)
        return remote.execute(request, explicitRootAuthorization = true) { response ->
            parseRepairResponse(response.pallaSyncBytes(), pinJson, records)
        }
    }

    private fun createRequest(
        url: HttpUrl,
        token: String,
        body: String,
    ): Request =
        Request
            .Builder()
            .url(url)
            .header("Accept", PALLASYNC_MEDIA_TYPE)
            .header("Authorization", "PallaSync $token")
            .post(body.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
            .build()

    private fun parseRepairResponse(
        bytes: ByteArray,
        pinJson: String,
        records: List<SourceRecord>,
    ): PallaSyncHttpResult<PallaSyncPostRecordsResponse> {
        val verified =
            PallaSyncCore.validateRecordRepair(
                bytes,
                JsonArray(records.map(SourceRecord::record)).toString(),
                pinJson,
            )
        val submitted =
            records.associate {
                it.record
                    .getValue("record_id")
                    .jsonPrimitive.content to it.record
            }
        return parseRecordUploadResponseBody(Json, verified, submitted)
    }
}
