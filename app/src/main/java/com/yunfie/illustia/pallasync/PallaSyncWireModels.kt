package com.yunfie.illustia.pallasync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val PALLASYNC_PROTOCOL_VERSION = "2.1"
internal const val PALLASYNC_LEGACY_PROTOCOL_VERSION = "2.0"
internal const val PALLASYNC_PAGE_SIZE = 200
internal const val PALLASYNC_NEXT_SEQ_HEADER = "PallaSync-Next-Seq"
internal const val PALLASYNC_HAS_MORE_HEADER = "PallaSync-Has-More"

@Serializable
internal data class PallaSyncHealth(
    val status: String,
    @SerialName("protocol_version") val protocolVersion: String,
)

@Serializable
internal data class PallaSyncWireRecord(
    @SerialName("protocol_version") val protocolVersion: String,
    @SerialName("chain_id") val chainId: String,
    @SerialName("record_id") val recordId: String,
    val epoch: Long = 0L,
    @SerialName("collection_name") val collectionName: String,
    val action: String,
    @SerialName("encrypted_payload") val encryptedPayload: String,
    @SerialName("payload_nonce") val payloadNonce: String = "",
    @SerialName("device_id") val deviceId: String,
    val lamport: Long = 0L,
    @SerialName("created_at_ms") val createdAtMs: Long,
    val signature: String,
    @SerialName("relay_seq") val relaySeq: Long? = null,
    @SerialName("server_sequence") val serverSequence: Long? = null,
)

@Serializable
internal data class PallaSyncWireDevice(
    @SerialName("protocol_version") val protocolVersion: String,
    @SerialName("chain_id") val chainId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("encrypted_device_name") val encryptedDeviceName: String,
    @SerialName("device_name_nonce") val deviceNameNonce: String = "",
    @SerialName("device_public_key") val devicePublicKey: String,
    val status: String = "active",
    @SerialName("created_at_ms") val createdAtMs: Long,
    @SerialName("updated_at_ms") val updatedAtMs: Long = createdAtMs,
    val signature: String,
)

@Serializable
internal data class PallaSyncFetchRecordsResponse(
    val records: List<PallaSyncWireRecord> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
    @SerialName("server_time_ms") val serverTimeMs: Long? = null,
)

@Serializable
internal data class PallaSyncPostRecordsResponse(
    @SerialName("accepted_record_ids") val acceptedRecordIds: List<String> = emptyList(),
    @SerialName("duplicate_record_ids") val duplicateRecordIds: List<String> = emptyList(),
    val rejected: List<PallaSyncRejectedRecord> = emptyList(),
)

@Serializable
internal data class PallaSyncRejectedRecord(
    @SerialName("record_id") val recordId: String,
    val reason: String = "",
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

    private fun endpoint(
        baseUrl: HttpUrl,
        vararg pathSegments: String,
    ): HttpUrl {
        val builder = baseUrl.newBuilder()
        builder.addPathSegment("pallasync")
        builder.addPathSegment("v2")
        pathSegments.forEach { builder.addPathSegment(it) }
        return builder.build()
    }
}
