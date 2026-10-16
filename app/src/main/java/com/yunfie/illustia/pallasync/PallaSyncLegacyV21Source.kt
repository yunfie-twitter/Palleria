package com.yunfie.illustia.pallasync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Read-only v2.1 source adapter for the explicit §9 migration flow. */
internal class PallaSyncLegacyV21Source(
    private val remote: PallaSyncRemoteService,
) {
    @Serializable
    internal data class EntityCandidate(
        val collection: String,
        @SerialName("legacy_entity_id") val legacyEntityId: String,
        @SerialName("entity_id") val entityId: String,
        val operation: String,
        val body: kotlinx.serialization.json.JsonElement,
        @SerialName("legacy_lamport") val legacyLamport: Long,
        @SerialName("legacy_created_at_ms") val legacyCreatedAtMs: Long,
        @SerialName("normalization_changed") val normalizationChanged: Boolean,
        @SerialName("normalization_collision") val normalizationCollision: Boolean,
        @SerialName("legacy_snapshot") val legacySnapshot: Boolean = false,
    )

    internal data class Parameters(
        val chainId: String,
        val chainSalt: String,
        val adminPublicKey: String,
        val creatorDeviceId: String,
        val creatorPublicKey: String,
        val signatureVerified: Boolean = false,
    )

    internal data class Device(
        val deviceId: String,
        val publicKey: String,
        val active: Boolean,
        val sourceObjectHash: String,
    )

    internal data class SignedRecord(
        val sequence: Long,
        val json: String,
        val recordId: String,
        val deviceId: String,
        val collectionName: String? = null,
        val lamport: Long? = null,
    )

    internal data class RecordPage(
        val afterSequence: Long,
        val throughSequence: Long,
        val hasMore: Boolean,
        val records: List<SignedRecord>,
    )

    /** Local proof of a fully read, signature-verified immutable legacy source (§9.1/§9.3). */
    internal data class FrozenExportProof(
        val sourceChain: String,
        val highWater: Long,
        val recordCount: Long,
        val exportHash: String,
        val verifiedDeviceRegistryHash: String,
    )

    /** Deterministic local transcript for the ordinary v2.1 read API (§9.3). */
    internal class ExportTranscript(
        chainId: String,
        verifiedDeviceRegistryHash: String? = null,
    ) {
        private val digest = MessageDigest.getInstance("SHA-256")
        var highWater: Long = 0
            private set
        var recordCount: Long = 0
            private set

        init {
            digest.update("PALLASYNC-LEGACY-EXPORT-TRANSCRIPT-v1\u0000".toByteArray(Charsets.UTF_8))
            digest.update(decode(chainId))
            verifiedDeviceRegistryHash?.let { digest.update(decode(it)) }
        }

        fun append(record: SignedRecord) {
            check(record.sequence == highWater + 1) { "C_ROLLBACK_DETECTED" }
            val objectHash = decode(checkNotNull(PallaSyncCore.objectHash(record.json)))
            try {
                digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(record.sequence).array())
                digest.update(objectHash)
            } finally {
                objectHash.fill(0)
            }
            highWater = record.sequence
            recordCount++
        }

        fun finish(): String {
            digest.update(
                ByteBuffer
                    .allocate(2 * Long.SIZE_BYTES)
                    .putLong(highWater)
                    .putLong(recordCount)
                    .array(),
            )
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
        }
    }

    internal fun getParameters(
        base: HttpUrl,
        chainId: String,
        seedPhrase: String,
        passphrase: String,
    ): PallaSyncHttpResult<Parameters> {
        require(decode(chainId).size == 32) { "C_SCHEMA_VIOLATION" }
        val request = request(base, "/pallasync/v2/chains/$chainId/parameters", null)
        return remote.execute(request) { response ->
            val value = Json.parseToJsonElement(response.pallaSyncText()).jsonObject
            val parameters = parseParameters(value, chainId)
            val identityMatches =
                withLegacySecretInputs(seedPhrase, passphrase) { mnemonicBytes, passphraseBytes ->
                    PallaSyncCore.validateLegacyV21Identity(
                        mnemonicBytes,
                        passphraseBytes,
                        parameters.chainId,
                        parameters.chainSalt,
                        parameters.adminPublicKey,
                    )
                }
            check(identityMatches) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            PallaSyncHttpResult.Success(parameters)
        }
    }

    /**
     * Read the registry using the old enrolled device key, then require that exact active key to
     * appear as a self-signed registry row before allowing it to authorize record pages.
     */
    internal fun getDevices(
        base: HttpUrl,
        parameters: Parameters,
        deviceId: String,
        deviceSeed: String,
        devicePublicKey: String,
        nowMs: Long,
    ): PallaSyncHttpResult<List<Device>> {
        val path = "/pallasync/v2/chains/${parameters.chainId}/devices"
        val token =
            withLegacySecretInputs(deviceSeed, "") { seedBytes, _ ->
                PallaSyncCore.legacyV21GetToken(seedBytes, parameters.chainId, deviceId, path, "", nowMs)
            }
        val request = request(base, path, token)
        return remote.execute(request) { response ->
            val root = Json.parseToJsonElement(response.pallaSyncText())
            val rows =
                when (root) {
                    is JsonArray -> {
                        root
                    }

                    is JsonObject -> {
                        check(root.keys == setOf("devices", "next_cursor") && root["next_cursor"] == JsonNull) {
                            "C_SCHEMA_VIOLATION"
                        }
                        root["devices"]?.jsonArray ?: error("C_SCHEMA_VIOLATION")
                    }

                    else -> {
                        error("C_SCHEMA_VIOLATION")
                    }
                }
            val devices =
                rows.map { element ->
                    val row = element.jsonObject
                    val raw = row.toString()
                    check(PallaSyncCore.verifyLegacyDeviceRecordV21(raw)) { "C_SIGNATURE_INVALID" }
                    val parsed = parseDevice(row, parameters.chainId)
                    parsed
                }
            check(devices.map(Device::deviceId).distinct().size == devices.size) { "C_DEVICE_EQUIVOCATION" }
            val signer = devices.singleOrNull { it.deviceId == deviceId }
            check(
                signer != null && signer.active && signer.publicKey == devicePublicKey,
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            PallaSyncHttpResult.Success(devices)
        }
    }

    /** A page is complete only when the relay explicitly says there is no next cursor. */
    internal fun getRecordPage(
        base: HttpUrl,
        chainId: String,
        afterSequence: Long,
        devices: List<Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
        nowMs: Long,
    ): PallaSyncHttpResult<RecordPage> {
        require(afterSequence in 0..PallaSyncConflictResolver.MAX_COUNTER) { "C_SCHEMA_VIOLATION" }
        val query = "after_seq=$afterSequence&limit=200"
        val path = "/pallasync/v2/chains/$chainId/records"
        val token =
            withLegacySecretInputs(signerDeviceSeed, "") { seedBytes, _ ->
                PallaSyncCore.legacyV21GetToken(seedBytes, chainId, signerDeviceId, path, query, nowMs)
            }
        val request = request(base, "$path?$query", token)
        return remote.execute(request) { response ->
            val nextSequence = response.header("PallaSync-Next-Seq") ?: error("C_SCHEMA_VIOLATION")
            val hasMore = response.header("PallaSync-Has-More") ?: error("C_SCHEMA_VIOLATION")
            val page =
                decodePage(
                    response.pallaSyncText(),
                    afterSequence,
                    nextSequence,
                    hasMore,
                )
            val byDevice = devices.associateBy(Device::deviceId)
            for (record in page.records) {
                check(Json.parseToJsonElement(record.json).jsonObject.string("chain_id") == chainId) {
                    "C_BOOTSTRAP_TRUST_REQUIRED"
                }
                val device = byDevice[record.deviceId] ?: error("C_MISSING_DEPENDENCY")
                check(PallaSyncCore.verifyLegacySyncRecordV21(record.json, device.publicKey)) { "C_SIGNATURE_INVALID" }
            }
            PallaSyncHttpResult.Success(page)
        }
    }

    /** Native signature/AEAD validation and schema conversion; plaintext buffers are cleared after decode. */
    internal fun convertRecord(
        session: Long,
        record: SignedRecord,
        device: Device,
    ): List<EntityCandidate> {
        val converted = PallaSyncCore.convertLegacyRecord(session, record.json, record.deviceId, device.publicKey)
        return try {
            Json.decodeFromString(String(converted, Charsets.UTF_8))
        } finally {
            converted.fill(0)
        }
    }

    companion object {
        private const val MEDIA_TYPE = "application/vnd.palleria.sync.v2+json"

        /**
         * Reads the complete source twice around an independently re-read signed device registry.
         * Ordinary v2.1 reads cannot prove writers are stopped; explicit freeze confirmation is mandatory.
         */
        internal suspend fun inspectFrozenExport(
            chainId: String,
            initialDevices: List<Device>,
            freezeConfirmed: Boolean,
            fetchDevices: suspend () -> PallaSyncHttpResult<List<Device>>,
            fetchPage: suspend (Long) -> PallaSyncHttpResult<RecordPage>,
        ): PallaSyncHttpResult<FrozenExportProof> {
            if (!freezeConfirmed) return PallaSyncHttpResult.ProtocolError("C_MISSING_DEPENDENCY")
            return try {
                val initialRegistryHash = registryHash(initialDevices)
                val transcript = ExportTranscript(chainId, initialRegistryHash)
                var cursor = 0L
                var hasMore = true
                while (hasMore) {
                    currentCoroutineContext().ensureActive()
                    when (val result = fetchPage(cursor)) {
                        is PallaSyncHttpResult.Success -> {
                            val page = result.value
                            validateFrozenPage(cursor, page)
                            for (record in page.records) {
                                currentCoroutineContext().ensureActive()
                                transcript.append(record)
                                cursor = record.sequence
                            }
                            check(page.throughSequence == cursor) { "C_ROLLBACK_DETECTED" }
                            hasMore = page.hasMore
                        }

                        is PallaSyncHttpResult.ProtocolError -> {
                            return result
                        }

                        is PallaSyncHttpResult.Retryable -> {
                            return result
                        }

                        PallaSyncHttpResult.Gone -> {
                            return PallaSyncHttpResult.Gone
                        }
                    }
                }
                when (val after = fetchDevices()) {
                    is PallaSyncHttpResult.Success -> {
                        check(initialRegistryHash == registryHash(after.value)) { "C_DEVICE_EQUIVOCATION" }
                    }

                    is PallaSyncHttpResult.ProtocolError -> {
                        return after
                    }

                    is PallaSyncHttpResult.Retryable -> {
                        return after
                    }

                    PallaSyncHttpResult.Gone -> {
                        return PallaSyncHttpResult.Gone
                    }
                }
                PallaSyncHttpResult.Success(
                    FrozenExportProof(
                        sourceChain = chainId,
                        highWater = cursor,
                        recordCount = transcript.recordCount,
                        exportHash = transcript.finish(),
                        verifiedDeviceRegistryHash = initialRegistryHash,
                    ),
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: IllegalStateException) {
                PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_SCHEMA_VIOLATION")
            } catch (_: IllegalArgumentException) {
                PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            }
        }

        internal fun parseParameters(
            value: JsonObject,
            requestedChainId: String,
        ): Parameters {
            val required =
                setOf(
                    "protocol_version",
                    "chain_id",
                    "chain_salt",
                    "created_at_ms",
                    "creator_device_id",
                    "creator_public_key",
                    "admin_public_key",
                    "signature",
                )
            check(value.keys == required && value.string("protocol_version") == "2.1") { "C_SCHEMA_VIOLATION" }
            val chainId = value.string("chain_id")
            val salt = value.string("chain_salt")
            val admin = value.string("admin_public_key")
            val creatorDevice = value.string("creator_device_id")
            val creatorPublic = value.string("creator_public_key")
            val signature = value.string("signature")
            require(chainId == requestedChainId && decode(chainId).size == 32) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            require(decode(salt).size == 32 && decode(admin).size == 32 && decode(creatorPublic).size == 32) {
                "C_SCHEMA_VIOLATION"
            }
            require(decode(signature).size == 64) { "C_SCHEMA_VIOLATION" }
            require(value["created_at_ms"]?.jsonPrimitive?.long?.let { it >= 0 } == true) { "C_SCHEMA_VIOLATION" }
            require(UUID.fromString(creatorDevice).toString() == creatorDevice) { "C_SCHEMA_VIOLATION" }
            // v2.1 §4.1 defines no ChainParameters signature context; root-derived pins are checked separately.
            return Parameters(chainId, salt, admin, creatorDevice, creatorPublic)
        }

        internal fun parseDevice(
            value: JsonObject,
            chainId: String,
        ): Device {
            val required =
                setOf(
                    "protocol_version",
                    "chain_id",
                    "device_id",
                    "device_public_key",
                    "encrypted_device_name",
                    "device_name_nonce",
                    "status",
                    "created_at_ms",
                    "updated_at_ms",
                    "signature",
                )
            check(value.keys == required && value.string("protocol_version") == "2.1" && value.string("chain_id") == chainId) {
                "C_SCHEMA_VIOLATION"
            }
            val id = value.string("device_id")
            require(UUID.fromString(id).toString() == id) { "C_SCHEMA_VIOLATION" }
            val createdAt = value["created_at_ms"]?.jsonPrimitive?.long ?: error("C_SCHEMA_VIOLATION")
            val updatedAt = value["updated_at_ms"]?.jsonPrimitive?.long ?: error("C_SCHEMA_VIOLATION")
            require(createdAt >= 0 && updatedAt >= createdAt) {
                "C_SCHEMA_VIOLATION"
            }
            val status = value.string("status")
            require(status == "active" || status == "revoked") { "C_SCHEMA_VIOLATION" }
            val sourceHash = checkNotNull(PallaSyncCore.objectHash(value.toString())) { "C_SCHEMA_VIOLATION" }
            return Device(id, value.string("device_public_key"), status == "active", sourceHash)
        }

        internal fun validateFrozenPage(
            expectedAfter: Long,
            page: RecordPage,
        ) {
            check(page.afterSequence == expectedAfter) { "C_ROLLBACK_DETECTED" }
            check(!page.hasMore || page.records.isNotEmpty()) { "C_ROLLBACK_DETECTED" }
            if (page.records.isEmpty()) {
                check(page.throughSequence == expectedAfter && !page.hasMore) { "C_ROLLBACK_DETECTED" }
            } else {
                check(page.records.first().sequence > expectedAfter) { "C_ROLLBACK_DETECTED" }
                check(page.records.last().sequence == page.throughSequence) { "C_ROLLBACK_DETECTED" }
            }
        }

        internal fun registryHash(devices: List<Device>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("PALLASYNC-LEGACY-DEVICE-REGISTRY-v1\u0000".toByteArray(Charsets.UTF_8))
            for (device in devices.sortedBy(Device::deviceId)) {
                digest.update(device.deviceId.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                val hash = decode(device.sourceObjectHash)
                try {
                    digest.update(hash)
                } finally {
                    hash.fill(0)
                }
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
        }

        internal fun decodePage(
            raw: String,
            afterSequence: Long,
            nextSequenceHeader: String? = null,
            hasMoreHeader: String? = null,
        ): RecordPage {
            require(afterSequence in 0..PallaSyncConflictResolver.MAX_COUNTER) { "C_SCHEMA_VIOLATION" }
            val root = Json.parseToJsonElement(raw)
            val pageObject = root as? JsonObject ?: error("C_SCHEMA_VIOLATION")
            check(pageObject.keys == setOf("records", "next_cursor", "server_time_ms")) { "C_SCHEMA_VIOLATION" }
            require(
                pageObject["server_time_ms"]
                    ?.jsonPrimitive
                    ?.long
                    ?.let { it in 0..PallaSyncConflictResolver.MAX_COUNTER } == true,
            ) {
                "C_SCHEMA_VIOLATION"
            }
            val rows = pageObject["records"]!!.jsonArray
            val nextCursor = pageObject["next_cursor"]!!
            val result = ArrayList<SignedRecord>(rows.size)
            var cursor = afterSequence
            for (rowElement in rows) {
                val row = rowElement.jsonObject
                val sequence = row.sequence()
                require(sequence > cursor && sequence <= PallaSyncConflictResolver.MAX_COUNTER) { "C_ROLLBACK_DETECTED" }
                require(row.string("protocol_version") == "2.1") { "C_SCHEMA_VIOLATION" }
                val recordId = row.string("record_id")
                val deviceId = row.string("device_id")
                val parsedRecordId = UUID.fromString(recordId)
                require(
                    parsedRecordId.version() == 7 && parsedRecordId.toString() == recordId &&
                        UUID.fromString(deviceId).toString() == deviceId,
                ) {
                    "C_SCHEMA_VIOLATION"
                }
                val encoded = row.toString()
                result +=
                    SignedRecord(
                        sequence,
                        encoded,
                        recordId,
                        deviceId,
                        row.string("collection_name"),
                        row["lamport"]?.jsonPrimitive?.longOrNull?.also {
                            require(it in 0..PallaSyncConflictResolver.MAX_COUNTER) { "C_SCHEMA_VIOLATION" }
                        } ?: error("C_SCHEMA_VIOLATION"),
                    )
                cursor = sequence
            }
            val more =
                run {
                    val next = nextCursor as? JsonPrimitive ?: error("C_SCHEMA_VIOLATION")
                    if (next == JsonNull) {
                        false
                    } else {
                        require(next.isString && next.content == cursor.toString() && cursor > afterSequence) {
                            "C_ROLLBACK_DETECTED"
                        }
                        true
                    }
                }
            check(!more || result.isNotEmpty()) { "C_ROLLBACK_DETECTED" }
            if (nextSequenceHeader != null || hasMoreHeader != null) {
                val headerSequence = nextSequenceHeader?.toLongOrNull() ?: error("C_SCHEMA_VIOLATION")
                require(headerSequence.toString() == nextSequenceHeader && headerSequence in 0..PallaSyncConflictResolver.MAX_COUNTER) {
                    "C_SCHEMA_VIOLATION"
                }
                val headerMore =
                    when (hasMoreHeader) {
                        "true" -> true
                        "false" -> false
                        else -> error("C_SCHEMA_VIOLATION")
                    }
                check(headerSequence == cursor && headerMore == more) { "C_ROLLBACK_DETECTED" }
            }
            return RecordPage(afterSequence, cursor, more, result)
        }

        private fun JsonObject.sequence(): Long {
            val server = this["server_sequence"]?.jsonPrimitive?.longOrNull
            val relay = this["relay_seq"]?.jsonPrimitive?.longOrNull
            require(server != null || relay != null) { "C_SCHEMA_VIOLATION" }
            require(server == null || relay == null || server == relay) { "C_ROLLBACK_DETECTED" }
            return server ?: checkNotNull(relay)
        }

        private fun JsonObject.string(key: String): String =
            this[key]?.jsonPrimitive?.takeIf(JsonPrimitive::isString)?.content ?: error("C_SCHEMA_VIOLATION")

        private fun decode(value: String): ByteArray =
            runCatching { Base64.getUrlDecoder().decode(value) }
                .getOrElse { error("C_SCHEMA_VIOLATION") }
                .also { decoded ->
                    require(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value) { "C_SCHEMA_VIOLATION" }
                }

        private fun request(
            base: HttpUrl,
            pathAndQuery: String,
            token: String?,
        ): Request {
            val requestUrl = base.newBuilder().encodedPath(pathAndQuery.substringBefore('?'))
            pathAndQuery.substringAfter('?', "").takeIf(String::isNotEmpty)?.let(requestUrl::encodedQuery)
            val builder =
                Request
                    .Builder()
                    .url(requestUrl.build())
                    .header("Accept", MEDIA_TYPE)
                    .get()
            token?.let { builder.header("Authorization", "PallaSync $it") }
            return builder.build()
        }
    }
}
