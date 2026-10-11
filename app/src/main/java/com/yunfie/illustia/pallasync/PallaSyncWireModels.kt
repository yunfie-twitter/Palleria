package com.yunfie.illustia.pallasync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val PALLASYNC_PROTOCOL_VERSION = "3.0"
internal const val PALLASYNC_LEGACY_PROTOCOL_VERSION = "2.1"
internal const val PALLASYNC_PROTOCOL_IDENTIFIER = "pallasync/3"
internal const val PALLASYNC_MEDIA_TYPE = "application/vnd.palleria.sync.v3+json"
internal const val PALLASYNC_PAGE_SIZE = 200
internal const val PALLASYNC_NEXT_SEQ_HEADER = "X-PallaSync-Next-Seq"
internal const val PALLASYNC_HAS_MORE_HEADER = "X-PallaSync-Has-More"

@Serializable
internal data class PallaSyncHealth(
    val status: String = "ok",
    val protocol: String = PALLASYNC_PROTOCOL_IDENTIFIER,
    @SerialName("protocol_version") val protocolVersion: String = PALLASYNC_PROTOCOL_VERSION,
    @SerialName("relay_origin") val relayOrigin: String = "",
    @SerialName("server_time_ms") val serverTimeMs: Long = 0L,
    @SerialName("relay_public_key") val relayPublicKey: String = "",
    @SerialName("relay_key_id") val relayKeyId: String = "",
)

@Serializable
internal data class PallaSyncWireRecord(
    val version: String = PALLASYNC_PROTOCOL_VERSION,
    @SerialName("chain_id") val chainId: String,
    val generation: Long = 0L,
    @SerialName("record_id") val recordId: String,
    @SerialName("device_id") val deviceId: String,
    val epoch: Long = 0L,
    @SerialName("collection_tag") val collectionTag: String? = null,
    @SerialName("payload_nonce") val payloadNonce: String = "",
    @SerialName("encrypted_payload") val encryptedPayload: String = "",
    val signature: String = "",
    @SerialName("relay_seq") val relaySeq: Long? = null,
) {
    val protocolVersion: String get() = version
}

@Serializable
internal data class PallaSyncInnerRecord(
    val collection: String,
    @SerialName("device_seq") val deviceSeq: Long,
    @SerialName("prev_record_hash") val prevRecordHash: String? = null,
    val operations: List<PallaSyncOperation> = emptyList(),
)

@Serializable
internal data class PallaSyncOperation(
    @SerialName("entity_id") val entityId: String,
    val operation: String, // "upsert", "delete", "clear"
    val lamport: Long,
    @SerialName("created_at_ms") val createdAtMs: Long,
    val context: JsonObject = JsonObject(emptyMap()),
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
internal data class PallaSyncGenesisBundle(
    @SerialName("chain_id") val chainId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_signing_key") val deviceSigningKey: String,
    @SerialName("device_public_key") val devicePublicKey: String,
    @SerialName("device_kex_private_key") val deviceKexPrivateKey: String,
    @SerialName("device_kex_public_key") val deviceKexPublicKey: String,
    val epoch: Long = 0L,
    @SerialName("epoch_secret") val epochSecret: String,
    @SerialName("record_key") val recordKey: String,
    @SerialName("device_meta_key") val deviceMetaKey: String,
    @SerialName("collection_tag_key") val collectionTagKey: String,
    @SerialName("epoch_commitment") val epochCommitment: String,
    @SerialName("genesis_request_body_json") val genesisRequestBodyJson: String,
    @SerialName("admin_capability_token") val adminCapabilityToken: String,
)

@Serializable
internal data class PallaSyncMnemonicEnrollmentBundle(
    @SerialName("chain_id") val chainId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_signing_key") val deviceSigningKey: String,
    @SerialName("device_public_key") val devicePublicKey: String,
    @SerialName("device_kex_private_key") val deviceKexPrivateKey: String,
    @SerialName("device_kex_public_key") val deviceKexPublicKey: String,
    val epoch: Long = 0L,
    @SerialName("epoch_secret") val epochSecret: String,
    @SerialName("record_key") val recordKey: String,
    @SerialName("device_meta_key") val deviceMetaKey: String,
    @SerialName("collection_tag_key") val collectionTagKey: String,
    @SerialName("epoch_commitment") val epochCommitment: String,
    @SerialName("enroll_request_body_json") val enrollRequestBodyJson: String,
    @SerialName("admin_capability_token") val adminCapabilityToken: String,
    @SerialName("keys_ack_request_body_json") val keysAckRequestBodyJson: String,
    @SerialName("device_keys_ack_token") val deviceKeysAckToken: String,
)

@Serializable
internal data class PallaSyncFetchDevicesResponse(
    val devices: List<PallaSyncWireDevice> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Suppress("ReturnCount")
internal fun parseDeviceIdsResponseBody(
    json: Json,
    body: String,
): PallaSyncHttpResult<List<String>> {
    val element =
        runCatching { json.parseToJsonElement(body) }.getOrNull()
            ?: return PallaSyncHttpResult.ProtocolError("Device response was not valid JSON")

    val array =
        when (element) {
            is JsonObject -> {
                if (!element.containsKey("devices")) {
                    return PallaSyncHttpResult.ProtocolError("Device response devices field was missing")
                }
                element["devices"] as? JsonArray
                    ?: return PallaSyncHttpResult.ProtocolError("Device response was not valid JSON")
            }

            is JsonArray -> {
                element
            }

            else -> {
                return PallaSyncHttpResult.ProtocolError("Device response was not valid JSON")
            }
        }

    return PallaSyncHttpResult.Success(array.map { it.toString() })
}

@Serializable
internal data class PallaSyncFetchRecordsResponse(
    val items: List<PallaSyncLogEntry> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("scan_through_seq") val scanThroughSeq: Long? = null,
    @SerialName("head_seq") val headSeq: Long? = null,
)

@Serializable
internal data class PallaSyncLogEntry(
    @SerialName("relay_seq") val relaySeq: Long,
    @SerialName("received_at_ms") val receivedAtMs: Long,
    val kind: String, // "record", "epoch", "parameters", "device", etc.
    @SerialName("object") val payloadObject: JsonObject,
)

@Serializable
internal data class PallaSyncPostRecordsResponse(
    val accepted: List<PallaSyncAcceptedRecord> = emptyList(),
    val rejected: List<PallaSyncRejectedRecord> = emptyList(),
    @SerialName("log_id") val logId: String? = null,
    @SerialName("head_seq") val headSeq: Long? = null,
    @SerialName("head_hash") val headHash: String? = null,
)

@Serializable
internal data class PallaSyncAcceptedRecord(
    @SerialName("record_id") val recordId: String,
    val duplicate: Boolean = false,
)

@Serializable
internal data class PallaSyncRejectedRecord(
    @SerialName("record_id") val recordId: String,
    val status: Int = 400,
    val code: String = "",
    val retryable: Boolean = false,
)

internal data class PallaSyncRecordsPage(
    val records: List<PallaSyncPageRecord>,
    val nextSeq: Long,
    val hasMore: Boolean,
    val nextCursor: String? = null,
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
    ) : PallaSyncHttpResult<Nothing>

    data class ProtocolError(
        val message: String,
        val statusCode: Int? = null,
    ) : PallaSyncHttpResult<Nothing>
}

internal fun classifyPallaSyncHttpStatus(
    statusCode: Int,
    errorBody: String? = null,
): PallaSyncHttpResult<Unit>? =
    when {
        statusCode in 200..299 -> {
            null
        }

        statusCode == 410 -> {
            PallaSyncHttpResult.Gone
        }

        statusCode == 429 || statusCode >= 500 -> {
            val details = errorBody?.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""
            PallaSyncHttpResult.Retryable(
                "PallaSync server returned HTTP $statusCode$details",
            )
        }

        else -> {
            val details = errorBody?.takeIf(String::isNotBlank)?.let { ": $it" } ?: ""
            PallaSyncHttpResult.ProtocolError(
                "PallaSync server returned HTTP $statusCode$details",
                statusCode,
            )
        }
    }

internal fun parseRecordsResponseBody(
    json: Json,
    body: String,
    afterSeq: Long,
): PallaSyncHttpResult<PallaSyncRecordsPage> {
    val fetchResp = runCatching { json.decodeFromString<PallaSyncFetchRecordsResponse>(body) }.getOrNull()
    if (fetchResp != null) {
        val parsedRecords =
            fetchResp.items
                .filter { it.kind == "record" }
                .map { entry ->
                    val objStr = entry.payloadObject.toString()
                    val wire =
                        runCatching {
                            val decoded = json.decodeFromString<PallaSyncWireRecord>(objStr)
                            decoded.copy(relaySeq = entry.relaySeq)
                        }.getOrNull()
                    PallaSyncPageRecord(wire, objStr)
                }
        val nextSeq = fetchResp.scanThroughSeq ?: fetchResp.headSeq ?: (afterSeq + parsedRecords.size)
        return PallaSyncHttpResult.Success(
            PallaSyncRecordsPage(
                records = parsedRecords,
                nextSeq = nextSeq,
                hasMore = fetchResp.hasMore,
                nextCursor = fetchResp.nextCursor,
            ),
        )
    }

    // Direct items / records array fallback
    val parsedArray = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull()
    return if (parsedArray != null) {
        val parsedRecords =
            parsedArray.map { element ->
                val raw = element.toString()
                runCatching { json.decodeFromString<PallaSyncWireRecord>(raw) }
                    .fold(
                        onSuccess = { PallaSyncPageRecord(it, raw) },
                        onFailure = { PallaSyncPageRecord(null, raw, it.message ?: "malformed record") },
                    )
            }
        PallaSyncHttpResult.Success(PallaSyncRecordsPage(parsedRecords, afterSeq + parsedRecords.size, false))
    } else {
        PallaSyncHttpResult.ProtocolError("Relay page was not valid JSON")
    }
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
        if (parsed.scheme != "https" && parsed.scheme != "http") {
            return PallaSyncHttpResult.ProtocolError("PallaSync server URL must use HTTP or HTTPS")
        }
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return PallaSyncHttpResult.ProtocolError("PallaSync server URL must not contain credentials")
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

    fun parameters(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "parameters")

    fun devices(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId, "devices")

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

    fun chain(
        baseUrl: HttpUrl,
        chainId: String,
    ): HttpUrl = endpoint(baseUrl, "chains", chainId)

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
