package com.yunfie.illustia.pallasync

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.ByteString.Companion.decodeBase64
import java.util.UUID

internal const val PALLASYNC_PROTOCOL_VERSION = "3.0"
internal const val PALLASYNC_LEGACY_PROTOCOL_VERSION = "2.1"
internal const val PALLASYNC_PROTOCOL_IDENTIFIER = "pallasync/3"
internal const val PALLASYNC_MEDIA_TYPE = "application/vnd.palleria.sync.v3+json"
internal const val PALLASYNC_EXPORT_MEDIA_TYPE = "application/vnd.palleria.sync.export.v3+jsonl"
internal const val PALLASYNC_PAGE_SIZE = 200
private const val PALLASYNC_EXPORT_MAX_PAGE_SIZE = 1_000
internal val PALLASYNC_AUTOMATIC_RETRY_STATUSES = setOf(408, 429, 500, 502, 503, 504)
private const val HTTP_UNAUTHORIZED = 401
private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
private const val ED25519_PUBLIC_KEY_BYTES = 32
internal val PALLASYNC_HARD_LIMITS = PallaSyncLimits(8_388_608, 64, 1_000, 128)

@Serializable
internal data class PallaSyncHealth(
    val protocol: String,
    @SerialName("relay_origin") val relayOrigin: String,
    @SerialName("server_time_ms") val serverTimeMs: Long,
    @SerialName("durability_tier") val durabilityTier: String,
    val limits: PallaSyncLimits,
    @SerialName("shutdown_at_ms") val shutdownAtMs: Long?,
    @SerialName("migration_origin") val migrationOrigin: String?,
    @SerialName("relay_public_key") val relayPublicKey: String,
    @SerialName("relay_key_id") val relayKeyId: String,
)

@Serializable
internal data class PallaSyncLimits(
    @SerialName("max_request_bytes") val maxRequestBytes: Int,
    @SerialName("max_records_per_upload") val maxRecordsPerUpload: Int,
    @SerialName("page_limit") val pageLimit: Int,
    @SerialName("max_devices") val maxDevices: Int,
)

internal fun parseHealthResponseBody(
    body: String,
    selectedOrigin: String,
): PallaSyncHttpResult<PallaSyncHealth> {
    val health =
        runCatching { Json.decodeFromString<PallaSyncHealth>(body) }.getOrNull()
            ?: return PallaSyncHttpResult.ProtocolError("PallaSync health response was invalid")
    return if (validHealthIdentity(health, selectedOrigin) && validHealthTiming(health) && validHealthConfiguration(health)) {
        PallaSyncHttpResult.Success(health)
    } else {
        PallaSyncHttpResult.ProtocolError("PallaSync health response did not match the selected v3 relay")
    }
}

private fun validHealthIdentity(
    health: PallaSyncHealth,
    selectedOrigin: String,
): Boolean =
    health.protocol == PALLASYNC_PROTOCOL_IDENTIFIER && health.relayOrigin == selectedOrigin &&
        validRelayKeyFingerprint(health.relayPublicKey, health.relayKeyId)

private fun validRelayKeyFingerprint(
    publicKey: String,
    keyId: String,
): Boolean {
    val key = publicKey.decodeBase64() ?: return false
    return key.size == ED25519_PUBLIC_KEY_BYTES && key.base64Url().trimEnd('=') == publicKey &&
        key.sha256().base64Url().trimEnd('=') == keyId
}

private fun validHealthTiming(health: PallaSyncHealth): Boolean =
    health.serverTimeMs in 0..MAX_SAFE_INTEGER && health.shutdownAtMs?.let { it in 0..MAX_SAFE_INTEGER } != false

private fun validHealthConfiguration(health: PallaSyncHealth): Boolean {
    val tier = health.durabilityTier in setOf("single-durable", "replicated-durable", "backup-durable")
    val limits =
        listOf(
            health.limits.maxRequestBytes to PALLASYNC_HARD_LIMITS.maxRequestBytes,
            health.limits.maxRecordsPerUpload to PALLASYNC_HARD_LIMITS.maxRecordsPerUpload,
            health.limits.pageLimit to PALLASYNC_HARD_LIMITS.pageLimit,
            health.limits.maxDevices to PALLASYNC_HARD_LIMITS.maxDevices,
        ).all { (value, maximum) -> value in 1..maximum }
    val migration =
        health.migrationOrigin?.let {
            val normalized = PallaSyncUrls.normalize(it)
            normalized is PallaSyncHttpResult.Success && PallaSyncUrls.extractOrigin(normalized.value) == it
        } != false
    return tier && limits && migration
}

/** §3.4 and §7.1 require UUIDv4, including encrypted records generated locally. */
internal fun newPallaSyncRecordId(): String = UUID.randomUUID().toString()

internal fun recordUploadBatchSize(
    records: List<String>,
    limits: PallaSyncLimits,
): Int {
    var bytes = "{\"records\":[]}".toByteArray(Charsets.UTF_8).size
    var count = 0
    for (record in records.take(limits.maxRecordsPerUpload)) {
        val additional = record.toByteArray(Charsets.UTF_8).size + if (count == 0) 0 else 1
        if (bytes + additional > limits.maxRequestBytes) break
        bytes += additional
        count++
    }
    return count
}

@Serializable
internal data class PallaSyncWireRecord(
    val version: String,
    @SerialName("chain_id") val chainId: String,
    val generation: Long,
    @SerialName("record_id") val recordId: String,
    @SerialName("device_id") val deviceId: String,
    val epoch: Long,
    @SerialName("collection_tag") val collectionTag: String?,
    @SerialName("payload_nonce") val payloadNonce: String,
    @SerialName("encrypted_payload") val encryptedPayload: String,
    val signature: String,
    val ext: JsonObject? = null,
    @kotlinx.serialization.Transient val relaySeq: Long? = null,
) {
    val protocolVersion: String get() = version
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PallaSyncInnerRecord(
    val collection: String,
    @SerialName("device_seq") val deviceSeq: Long,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    @SerialName("prev_record_hash") val prevRecordHash: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val operations: List<PallaSyncOperation> = emptyList(),
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PallaSyncOperation(
    @SerialName("entity_id") val entityId: String,
    val operation: String, // "upsert", "delete", "clear"
    val lamport: Long,
    @SerialName("created_at_ms") val createdAtMs: Long,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val context: JsonObject = JsonObject(emptyMap()),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val body: JsonObject = JsonObject(emptyMap()),
)

@Serializable
internal data class PallaSyncWireDevice(
    val version: String = PALLASYNC_PROTOCOL_VERSION,
    @SerialName("chain_id") val chainId: String,
    val generation: Long = 0L,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_public_key") val devicePublicKey: String,
    @SerialName("device_kex_public_key") val deviceKexPublicKey: String = "",
    @SerialName("meta_epoch") val metaEpoch: Long = 0L,
    @SerialName("meta_nonce") val metaNonce: String = "",
    @SerialName("encrypted_device_meta") val encryptedDeviceMeta: String = "",
    val enrollment: PallaSyncEnrollmentCertificate? = null,
    val status: String = "active",
    val signature: String = "",
) {
    val protocolVersion: String get() = version
    val encryptedDeviceName: String get() = encryptedDeviceMeta
    val createdAtMs: Long get() = enrollment?.approvedAtMs ?: 0L
}

@Serializable
internal data class PallaSyncDeviceMetaPlaintext(
    val name: String,
    @SerialName("key_protection") val keyProtection: String = "os-keystore",
)

@Serializable
internal data class PallaSyncEnrollmentCertificate(
    @SerialName("chain_id") val chainId: String,
    val generation: Long = 0L,
    @SerialName("certificate_id") val certificateId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_public_key") val devicePublicKey: String,
    @SerialName("device_kex_public_key") val deviceKexPublicKey: String,
    @SerialName("signer_kind") val signerKind: String,
    @SerialName("signer_device_id") val signerDeviceId: String? = null,
    @SerialName("request_hash") val requestHash: String? = null,
    @SerialName("approved_at_ms") val approvedAtMs: Long,
    val signature: String,
)

@Serializable
internal data class PallaSyncChainParameters(
    val version: String = PALLASYNC_PROTOCOL_VERSION,
    @SerialName("chain_id") val chainId: String,
    @SerialName("admin_public_key") val adminPublicKey: String,
    val generation: Long = 0L,
    @SerialName("previous_parameters_hash") val previousParametersHash: String? = null,
    val policy: PallaSyncChainPolicy = PallaSyncChainPolicy(),
    val signature: String = "",
)

@Serializable
internal data class PallaSyncChainPolicy(
    @SerialName("metadata_profile") val metadataProfile: String = "private",
    @SerialName("allow_device_chain_delete") val allowDeviceChainDelete: Boolean = false,
    @SerialName("allow_peer_enrollment") val allowPeerEnrollment: Boolean = true,
    @SerialName("allow_device_rotation") val allowDeviceRotation: Boolean = true,
    @SerialName("epoch_retention") val epochRetention: String = "retain",
    @SerialName("epoch_window_ms") val epochWindowMs: Long = 2592000000L,
    @SerialName("scheduled_rotation_ms") val scheduledRotationMs: Long = 0L,
)

@Serializable
internal data class PallaSyncEpochRecord(
    @SerialName("chain_id") val chainId: String,
    val generation: Long = 0L,
    val epoch: Long = 0L,
    @SerialName("previous_epoch_hash") val previousEpochHash: String? = null,
    @SerialName("epoch_commitment") val epochCommitment: String,
    val reason: String,
    @SerialName("signer_kind") val signerKind: String,
    @SerialName("signer_device_id") val signerDeviceId: String? = null,
    val members: List<String> = emptyList(),
    @SerialName("revoked_device_ids") val revokedDeviceIds: List<String> = emptyList(),
    @SerialName("recovery_envelope_hash") val recoveryEnvelopeHash: String,
    val signature: String = "",
)

@Serializable
internal data class PallaSyncEpochKeyEnvelope(
    @SerialName("envelope_id") val envelopeId: String,
    @SerialName("chain_id") val chainId: String,
    val generation: Long = 0L,
    val epoch: Long = 0L,
    @SerialName("previous_epoch_hash") val previousEpochHash: String? = null,
    @SerialName("epoch_commitment") val epochCommitment: String,
    @SerialName("recipient_kind") val recipientKind: String,
    @SerialName("recipient_device_id") val recipientDeviceId: String? = null,
    @SerialName("recipient_key_hash") val recipientKeyHash: String? = null,
    @SerialName("signer_kind") val signerKind: String,
    @SerialName("signer_device_id") val signerDeviceId: String? = null,
    val enc: String? = null,
    val nonce: String? = null,
    val ciphertext: String,
    val signature: String = "",
)

@Serializable
internal data class PallaSyncGenesisRequestBody(
    val parameters: PallaSyncChainParameters,
    @SerialName("device_record") val deviceRecord: PallaSyncWireDevice,
    val epoch: PallaSyncEpochRecord,
    val envelopes: List<PallaSyncEpochKeyEnvelope>,
)

@Serializable
internal data class PallaSyncRegistryEntry(
    @SerialName("device_record") val deviceRecord: JsonObject,
    val status: String,
    @SerialName("enrolled_seq") val enrolledSeq: Long,
    @SerialName("revoked_seq") val revokedSeq: Long?,
)

@Serializable
internal data class PallaSyncFetchDevicesResponse(
    val items: List<PallaSyncRegistryEntry>,
    @SerialName("next_cursor") val nextCursor: String?,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("log_id") val logId: String,
    val generation: Long,
    @SerialName("head_seq") val headSeq: Long,
)

internal fun parseDevicesResponseBody(
    json: Json,
    body: String,
): PallaSyncHttpResult<PallaSyncFetchDevicesResponse> {
    val page =
        runCatching { json.decodeFromString<PallaSyncFetchDevicesResponse>(body) }.getOrNull()
            ?: return PallaSyncHttpResult.ProtocolError("Device response was not a v3 registry page")
    return if (page.hasMore != (page.nextCursor != null) ||
        page.items.any { it.status !in setOf("active", "pending_keys", "revoked") || it.enrolledSeq < 1 }
    ) {
        PallaSyncHttpResult.ProtocolError("Device response contained invalid registry state")
    } else {
        PallaSyncHttpResult.Success(page)
    }
}

@Serializable
internal data class PallaSyncFetchRecordsResponse(
    val items: List<PallaSyncLogEntry>,
    @SerialName("next_cursor") val nextCursor: String?,
    @SerialName("has_more") val hasMore: Boolean,
    @SerialName("scan_through_seq") val scanThroughSeq: Long,
    @SerialName("log_id") val logId: String,
    val generation: Long,
    @SerialName("head_seq") val headSeq: Long,
    @SerialName("head_hash") val headHash: String,
) {
    fun validRecordPage(after: Long): Boolean =
        items.all { it.kind == "record" } && hasMore == (nextCursor != null) && scanThroughSeq in after..headSeq
}

@Serializable
internal data class PallaSyncLogEntry(
    @SerialName("relay_seq") val relaySeq: Long,
    @SerialName("received_at_ms") val receivedAtMs: Long,
    val kind: String, // "record", "epoch", "parameters", "device", etc.
    @SerialName("object") val payloadObject: JsonObject,
    @SerialName("committed_at_ms") val committedAtMs: Long? = null,
)

@Serializable
internal data class PallaSyncPostRecordsResponse(
    val accepted: List<PallaSyncAcceptedRecord>,
    val rejected: List<PallaSyncRejectedRecord>,
    @SerialName("upload_receipts") val uploadReceipts: List<JsonObject>,
    val generation: Long,
    @SerialName("log_id") val logId: String,
    @SerialName("head_seq") val headSeq: Long,
    @SerialName("head_hash") val headHash: String,
    @SerialName("repair_required") val repairRequired: Boolean = false,
)

@Serializable
internal data class PallaSyncGenesisCheckpoint(
    @SerialName("log_id") val logId: String,
    @SerialName("head_seq") val headSeq: Long,
    @SerialName("head_hash") val headHash: String,
    @SerialName("parameters_hash") val parametersHash: String,
    @SerialName("epoch_hash") val epochHash: String,
    @SerialName("transparency_id") val transparencyId: String,
    @SerialName("transparency_index") val transparencyIndex: Long,
    @SerialName("transparency_hash") val transparencyHash: String,
    val authority: JsonObject,
    val genesis: JsonObject? = null,
    val receipts: List<JsonObject> = emptyList(),
    val generation: Long = 0,
    @SerialName("data_zero") val dataZero: String? = null,
)

@Serializable
internal data class PallaSyncAcceptedRecord(
    @SerialName("record_id") val recordId: String,
    val duplicate: Boolean,
    val receipt: PallaSyncLogEntry,
)

@Serializable
internal data class PallaSyncRejectedRecord(
    @SerialName("record_id") val recordId: String,
    val status: Int,
    val code: String,
    val retryable: Boolean,
)

/** §10.6: HTTP 200 is a per-item result; missing IDs never imply acceptance. */
internal fun parseRecordUploadResponseBody(
    json: Json,
    body: String,
    submitted: Map<String, JsonObject>,
): PallaSyncHttpResult<PallaSyncPostRecordsResponse> {
    val result =
        runCatching { json.decodeFromString<PallaSyncPostRecordsResponse>(body) }.getOrNull()
            ?: return PallaSyncHttpResult.ProtocolError("Record upload response was invalid")
    val ids = result.accepted.map { it.recordId } + result.rejected.map { it.recordId }
    val coverageMatches = ids.size == submitted.size && ids.toSet() == submitted.keys
    return if (!coverageMatches ||
        result.accepted.any { it.receipt.kind != "record" || it.receipt.payloadObject != submitted[it.recordId] }
    ) {
        PallaSyncHttpResult.ProtocolError("Record upload response did not account for every submitted record")
    } else {
        PallaSyncHttpResult.Success(result)
    }
}

internal data class PallaSyncRecordsPage(
    val records: List<PallaSyncPageRecord>,
    val nextSeq: Long,
    val hasMore: Boolean,
    val nextCursor: String? = null,
    val logId: String,
    val generation: Long,
    val headSeq: Long,
    val headHash: String,
)

internal data class PallaSyncPageRecord(
    val wireRecord: PallaSyncWireRecord?,
    val rawJson: String,
    val parseError: String? = null,
)

internal sealed interface PallaSyncHttpResult<out T> {
    data class Success<T>(
        val value: T,
    ) : PallaSyncHttpResult<T>

    data object Gone : PallaSyncHttpResult<Nothing>

    data class Retryable(
        val message: String,
        val retryAfterMs: Long = 0,
        val statusCode: Int? = null,
        val reconciliationRequired: Boolean = false,
    ) : PallaSyncHttpResult<Nothing>

    data class ProtocolError(
        val message: String,
        val statusCode: Int? = null,
    ) : PallaSyncHttpResult<Nothing>
}

internal fun classifyPallaSyncHttpStatus(
    statusCode: Int,
    problem: PallaSyncProblemHint? = null,
    request: okhttp3.Request? = null,
): PallaSyncHttpResult<Nothing>? =
    when {
        statusCode in 200..299 -> {
            null
        }

        statusCode == 410 -> {
            PallaSyncHttpResult.Gone
        }

        statusCode == 503 && problem?.code == "RELAY_SHUTTING_DOWN" -> {
            PallaSyncHttpResult.ProtocolError(problem.code, statusCode)
        }

        // §19.2: a missing receipt is pending acceptance evidence, not proof of rejection.
        // Keep the exact request/evidence and retry it only after the normal backoff so the
        // caller can reconcile the accepted object and receipt again.
        statusCode == 404 && problem?.code == "RECEIPT_NOT_FOUND" && problem.retryable && request.isReceiptLookup() -> {
            PallaSyncHttpResult.Retryable(
                problem.code,
                retryAfterMs = problem.retryAfterMs ?: 0,
                statusCode = statusCode,
            )
        }

        statusCode in PALLASYNC_AUTOMATIC_RETRY_STATUSES ||
            (
                statusCode == HTTP_UNAUTHORIZED &&
                    problem?.code in
                    setOf(
                        "TOKEN_REQUIRED",
                        "TOKEN_EXPIRED",
                        "TOKEN_NOT_YET_VALID",
                        "TOKEN_REPLAYED",
                    )
            ) -> {
            PallaSyncHttpResult.Retryable(
                problem?.code ?: "PallaSync server returned HTTP $statusCode",
                retryAfterMs = problem?.retryAfterMs ?: 0,
                statusCode = statusCode,
                reconciliationRequired = statusCode == 500,
            )
        }

        else -> {
            PallaSyncHttpResult.ProtocolError(
                problem?.code ?: "PallaSync server returned HTTP $statusCode",
                statusCode,
            )
        }
    }

private fun okhttp3.Request?.isReceiptLookup(): Boolean {
    if (this == null || method != "GET") return false
    val segments = url.pathSegments
    return segments.size == 6 &&
        segments.take(3) == listOf("pallasync", "v3", "chains") &&
        segments[4] == "receipts" &&
        segments[5].toLongOrNull()?.let { it > 0L } == true &&
        url.queryParameterNames == setOf("log_id") &&
        url.queryParameter("log_id") != null
}

internal fun parseRecordsResponseBody(
    json: Json,
    body: String,
    afterSeq: Long,
): PallaSyncHttpResult<PallaSyncRecordsPage> {
    val fetchResp =
        runCatching { json.decodeFromString<PallaSyncFetchRecordsResponse>(body) }.getOrNull()
    if (fetchResp == null || !fetchResp.validRecordPage(afterSeq)) {
        return PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
    }
    val parsedRecords =
        fetchResp.items
            .map { entry ->
                val objStr = entry.payloadObject.toString()
                val wire =
                    runCatching {
                        val decoded = json.decodeFromString<PallaSyncWireRecord>(objStr)
                        decoded.copy(relaySeq = entry.relaySeq)
                    }.getOrNull()
                PallaSyncPageRecord(wire, objStr)
            }
    return PallaSyncHttpResult.Success(
        PallaSyncRecordsPage(
            records = parsedRecords,
            nextSeq = fetchResp.scanThroughSeq,
            hasMore = fetchResp.hasMore,
            nextCursor = fetchResp.nextCursor,
            logId = fetchResp.logId,
            generation = fetchResp.generation,
            headSeq = fetchResp.headSeq,
            headHash = fetchResp.headHash,
        ),
    )
}

@Suppress("TooManyFunctions")
internal object PallaSyncUrls {
    fun normalize(rawUrl: String): PallaSyncHttpResult<HttpUrl> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return PallaSyncHttpResult.ProtocolError("PallaSync server URL is empty")
        }
        if ('?' in trimmed || '#' in trimmed) {
            return PallaSyncHttpResult.ProtocolError("PallaSync server URL must not contain a query or fragment")
        }

        val parsed =
            trimmed.trimEnd('/').toHttpUrlOrNull()
                ?: return PallaSyncHttpResult.ProtocolError("Invalid PallaSync server URL")
        if (parsed.scheme != "https") {
            return PallaSyncHttpResult.ProtocolError("PallaSync server URL must use HTTPS")
        }
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return PallaSyncHttpResult.ProtocolError("PallaSync server URL must not contain credentials")
        }
        if (parsed.encodedPath != "/") {
            return PallaSyncHttpResult.ProtocolError("PallaSync server URL must be an origin without an API path")
        }
        return PallaSyncHttpResult.Success(parsed)
    }

    fun extractOrigin(baseUrl: HttpUrl): String {
        val scheme = baseUrl.scheme.lowercase()
        val host = baseUrl.host.lowercase()
        val port = baseUrl.port
        val defaultPort = if (scheme == "https") 443 else 80
        return if (port == defaultPort) {
            "$scheme://$host"
        } else {
            "$scheme://$host:$port"
        }
    }

    fun health(baseUrl: HttpUrl): HttpUrl = endpoint(baseUrl, "health")

    fun chains(baseUrl: HttpUrl): HttpUrl = endpoint(baseUrl, "chains")

    /** §10.3: administrative restore is a top-level A-scope route, not chain/{id}/import. */
    fun importChain(baseUrl: HttpUrl): HttpUrl = endpoint(baseUrl, "chains", "import")

    fun parameters(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "parameters")

    fun devices(
        baseUrl: HttpUrl,
        chainId: String,
        cursor: String? = null,
        limit: Int = PALLASYNC_PAGE_SIZE,
    ): HttpUrl =
        endpoint(baseUrl, "chains", chainId, "devices")
            .newBuilder()
            .addQueryParameter("limit", limit.toString())
            .apply { if (cursor != null) addQueryParameter("cursor", cursor) }
            .build()

    fun enrollDevice(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "devices", "enroll")

    fun records(
        baseUrl: HttpUrl,
        chainId: String,
        cursor: String?,
        afterSeq: Long,
        limit: Int,
    ): HttpUrl {
        val builder =
            recordsEndpoint(baseUrl, chainId)
                .newBuilder()
                .addQueryParameter("limit", limit.coerceIn(1, 500).toString())
        if (!cursor.isNullOrBlank()) {
            builder.addQueryParameter("cursor", cursor)
        } else {
            builder.addQueryParameter("after_seq", afterSeq.toString())
        }
        return builder.build()
    }

    fun recordsEndpoint(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "records")

    fun recordRepair(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "records", "repair")

    fun snapshotParts(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "snapshots", "parts")

    fun snapshots(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "snapshots")

    fun export(
        baseUrl: HttpUrl,
        chainId: String,
        cursor: String?,
        limit: Int = 200,
        includeRecovery: Boolean = false,
    ): HttpUrl =
        endpoint(baseUrl, "chains", chainId, "export")
            .newBuilder()
            .addQueryParameter("limit", limit.coerceIn(1, PALLASYNC_EXPORT_MAX_PAGE_SIZE).toString())
            .addQueryParameter("include_recovery", includeRecovery.toString())
            .apply { if (cursor != null) addQueryParameter("cursor", cursor) }
            .build()

    fun relayNotice(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "relay-notice")

    fun chain(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId)

    fun deletion(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "deletion")

    fun transparencyHead(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "transparency", "head")

    fun logHead(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "log", "head")

    fun logPrune(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "log", "prune")

    fun transparencyEntries(
        baseUrl: HttpUrl,
        chainId: String,
        id: String,
        after: Long,
        through: Long,
        cursor: String?,
        limit: Int = PALLASYNC_PAGE_SIZE,
    ): HttpUrl =
        endpoint(baseUrl, "chains", chainId, "transparency", "entries")
            .newBuilder()
            .addQueryParameter("transparency_id", id)
            .addQueryParameter("after_index", after.toString())
            .addQueryParameter("through_index", through.toString())
            .addQueryParameter("limit", limit.toString())
            .apply { if (cursor != null) addQueryParameter("cursor", cursor) }
            .build()

    fun deviceEnvelopes(
        baseUrl: HttpUrl,
        chainId: String,
        deviceId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "devices", deviceId, "envelopes")

    fun deviceKeysAck(
        baseUrl: HttpUrl,
        chainId: String,
        deviceId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "devices", deviceId, "keys", "ack")

    private fun endpoint(
        baseUrl: HttpUrl,
        vararg pathSegments: String,
    ): HttpUrl {
        val builder = baseUrl.newBuilder()
        builder.addPathSegment("pallasync")
        builder.addPathSegment("v3")
        pathSegments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }
}
