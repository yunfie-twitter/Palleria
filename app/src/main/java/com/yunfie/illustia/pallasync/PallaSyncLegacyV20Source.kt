package com.yunfie.illustia.pallasync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Strict structural reader for the unpaged v2.0 `/records` export used only by §9 migration. */
internal object PallaSyncLegacyV20Source {
    private const val MEDIA_TYPE = "application/vnd.palleria.sync.v2+json"

    internal data class Record(
        val sequence: Long,
        val json: String,
        val recordId: String,
        val deviceId: String,
        val createdAtMs: Long,
    )

    internal data class FrozenExportProof(
        val sourceChain: String,
        val highWater: Long,
        val exportHash: String,
        val quarantinedCount: Long,
    )

    /** Incremental equivalent of v2.0's (incoming + existing).distinct() favorite reducer. */
    internal class FavoriteMerge {
        private val oldestFirst = linkedSetOf<String>()

        fun accept(incomingMostRecentFirst: List<String>) {
            incomingMostRecentFirst.asReversed().forEach { tag ->
                oldestFirst.remove(tag)
                oldestFirst.add(tag)
            }
        }

        fun mostRecentFirst(): List<String> = oldestFirst.toList().asReversed()
    }

    /** §6.3: v2.0 exposes the complete array at since_ms=0 and sorts it oldest to newest. */
    internal fun fetchRecords(
        remote: PallaSyncRemoteService,
        base: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<List<Record>> {
        requireCanonicalBase64Url(chainId, exactBytes = 32)
        val path = "/pallasync/v2/chains/$chainId/records"
        val url =
            base
                .newBuilder()
                .encodedPath(path)
                .query("since_ms=0")
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", MEDIA_TYPE)
                .get()
                .build()
        return remote.execute(request) { response ->
            PallaSyncHttpResult.Success(decodeRecords(response.pallaSyncText(), chainId))
        }
    }

    /** The v2.0 endpoint returns the complete array ordered by created_at_ms ascending (§6.3). */
    internal fun decodeRecords(
        raw: String,
        expectedChainId: String,
    ): List<Record> {
        requireCanonicalBase64Url(expectedChainId, exactBytes = 32)
        check(PallaSyncCore.canonicalJson(raw) != null) { "C_SCHEMA_VIOLATION" }
        val root = Json.parseToJsonElement(raw) as? JsonArray ?: error("C_SCHEMA_VIOLATION")
        val seen = HashSet<String>(root.size)
        var previousCreatedAt = -1L
        return root.mapIndexed { index, item ->
            val value = item as? JsonObject ?: error("C_SCHEMA_VIOLATION")
            check(value.keys.containsAll(SOURCE_INDEX_FIELDS)) { "C_SCHEMA_VIOLATION" }
            val relaySequence = value.optionalSafeSequence("relay_seq")
            val serverSequence = value.optionalSafeSequence("server_sequence")
            check(relaySequence == null || serverSequence == null || relaySequence == serverSequence) {
                "C_ROLLBACK_DETECTED"
            }
            check(value.string("protocol_version") == "2.0") { "C_SCHEMA_VIOLATION" }
            check(value.string("chain_id") == expectedChainId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val recordId = value.string("record_id")
            val deviceId = value.string("device_id")
            requireUuidV7(recordId)
            requireUuidV7(deviceId)
            check(seen.add(recordId)) { "C_RECORD_ID_CONFLICT" }
            val createdAt = value.integer("created_at_ms")
            check(createdAt >= previousCreatedAt) { "C_ROLLBACK_DETECTED" }
            previousCreatedAt = createdAt
            // Non-index fields are deliberately left for the legacy native verifier. Invalid
            // signature, ciphertext, action, schema, or extra members become per-record quarantine
            // decisions during the authenticated scan instead of discarding the entire export.
            Record(index + 1L, value.toString(), recordId, deviceId, createdAt)
        }
    }

    /** Hashes the complete ordered array; the ordinal is local to this complete v2.0 response. */
    internal fun exportHash(
        chainId: String,
        records: List<Record>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("PALLASYNC-LEGACY-V20-EXPORT-v1\u0000".toByteArray(Charsets.UTF_8))
        val chainBytes = decodeCanonicalBase64Url(chainId, CHAIN_ID_BYTES)
        try {
            digest.update(chainBytes)
        } finally {
            chainBytes.fill(0)
        }
        var expectedSequence = 1L
        for (record in records) {
            check(record.sequence == expectedSequence) { "C_ROLLBACK_DETECTED" }
            val objectHash = PallaSyncCore.objectHash(record.json) ?: error("C_SCHEMA_VIOLATION")
            val decodedHash = decodeCanonicalBase64Url(objectHash, HASH_BYTES)
            try {
                digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(record.sequence).array())
                digest.update(decodedHash)
            } finally {
                decodedHash.fill(0)
            }
            expectedSequence++
        }
        digest.update(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(records.size.toLong()).array())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
    }

    /** A frozen-source assertion is accepted only when two complete reads are byte-object equivalent. */
    internal suspend fun inspectFrozenExport(
        chainId: String,
        writersStopped: Boolean,
        authenticateRecord: suspend (Record) -> String?,
        fetchRecords: suspend () -> List<Record>,
    ): PallaSyncHttpResult<FrozenExportProof> {
        if (!writersStopped) return PallaSyncHttpResult.ProtocolError("C_MISSING_DEPENDENCY")
        return try {
            suspend fun verifiedFingerprint(): Triple<Long, String, Long> {
                val records = fetchRecords()
                var quarantined = 0L
                for (record in records) {
                    currentCoroutineContext().ensureActive()
                    val failureCode = authenticateRecord(record)
                    if (failureCode != null) {
                        check(failureCode in QUARANTINE_CODES) { "C_SCHEMA_VIOLATION" }
                        quarantined++
                    }
                }
                return Triple(records.size.toLong(), exportHash(chainId, records), quarantined)
            }
            val first = verifiedFingerprint()
            val second = verifiedFingerprint()
            check(first == second) { "C_RECORD_ID_CONFLICT" }
            PallaSyncHttpResult.Success(FrozenExportProof(chainId, second.first, second.second, second.third))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: IllegalStateException) {
            PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_SCHEMA_VIOLATION")
        } catch (_: IllegalArgumentException) {
            PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
        }
    }

    private fun JsonObject.string(key: String): String =
        this[key]?.jsonPrimitive?.takeIf(JsonPrimitive::isString)?.content ?: error("C_SCHEMA_VIOLATION")

    private fun JsonObject.integer(key: String): Long =
        this[key]?.jsonPrimitive?.takeIf { !it.isString }?.long ?: error("C_SCHEMA_VIOLATION")

    private fun JsonObject.optionalSafeSequence(key: String): Long? =
        this[key]?.let { sequence ->
            val parsed = sequence.jsonPrimitive.takeIf { !it.isString }?.long ?: error("C_SCHEMA_VIOLATION")
            check(parsed in 0..MAX_SAFE_INTEGER) { "C_SCHEMA_VIOLATION" }
            parsed
        }

    private fun requireUuidV7(value: String) {
        val uuid = runCatching { UUID.fromString(value) }.getOrNull()
        check(uuid != null && uuid.version() == UUID_V7_VERSION && uuid.toString() == value) { "C_SCHEMA_VIOLATION" }
    }

    private fun requireCanonicalBase64Url(
        value: String,
        minimumBytes: Int = 0,
        exactBytes: Int? = null,
    ) {
        val decoded =
            runCatching { Base64.getUrlDecoder().decode(value) }
                .getOrElse { error("C_SCHEMA_VIOLATION") }
        try {
            check(Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value) { "C_SCHEMA_VIOLATION" }
            check(decoded.size >= minimumBytes && (exactBytes == null || decoded.size == exactBytes)) {
                "C_SCHEMA_VIOLATION"
            }
        } finally {
            decoded.fill(0)
        }
    }

    private fun decodeCanonicalBase64Url(
        value: String,
        exactBytes: Int,
    ): ByteArray {
        val decoded =
            runCatching { Base64.getUrlDecoder().decode(value) }
                .getOrElse { error("C_SCHEMA_VIOLATION") }
        check(decoded.size == exactBytes && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value) {
            "C_SCHEMA_VIOLATION"
        }
        return decoded
    }

    private val SOURCE_INDEX_FIELDS =
        setOf(
            "protocol_version",
            "chain_id",
            "record_id",
            "device_id",
            "created_at_ms",
        )

    private val RELAY_SEQUENCE_FIELDS = setOf("relay_seq", "server_sequence")
    private const val CHAIN_ID_BYTES = 32
    private const val HASH_BYTES = 32
    private const val UUID_V7_VERSION = 7
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

    private val QUARANTINE_CODES =
        setOf(
            "C_AEAD_FAILURE",
            "C_SIGNATURE_INVALID",
            "C_SCHEMA_VIOLATION",
            "C_UNSUPPORTED_SCHEMA",
        )
}
