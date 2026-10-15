package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.AuthorSequenceEntity
import com.yunfie.illustia.pallasync.data.EntityRegisterEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.ProjectionStateEntity
import com.yunfie.illustia.pallasync.data.ScopeRegisterEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.nio.ByteBuffer

/** Streams the canonical §16.6 logical snapshot entries from the authoritative Room tables. */
@Suppress("TooManyFunctions")
internal class PallaSyncSnapshotStateReader(
    private val database: PallaSyncDatabase,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    init {
        require(pageSize in 1..MAX_PAGE_SIZE)
    }

    suspend fun stream(
        chainId: String,
        emit: suspend (JsonObject) -> Unit,
    ) = streamInternal(chainId, null, false, emit)

    suspend fun stream(
        chainId: String,
        reseedHeader: JsonObject?,
        preservePendingOutbox: Boolean,
        emit: suspend (JsonObject) -> Unit,
    ) = streamInternal(chainId, reseedHeader, preservePendingOutbox, emit)

    private suspend fun streamInternal(
        chainId: String,
        reseedHeader: JsonObject?,
        preservePendingOutbox: Boolean,
        emit: suspend (JsonObject) -> Unit,
    ) {
        val dao = database.protocolDao()
        val chainDao = database.pallaSyncDao()
        val before = checkNotNull(chainDao.getChainState(chainId)) { "C_SNAPSHOT_INVALID" }
        require(before.initialPullCompleted && before.logId != null && before.epochHash != null) { "C_SNAPSHOT_INVALID" }
        require(before.verifiedSeq == before.materializedSeq && before.verifiedHash != null) { "C_SNAPSHOT_INVALID" }
        require(
            before.generation in 0..MAX_GENERATION &&
                before.lamport in 0..PallaSyncConflictResolver.MAX_COUNTER &&
                before.verifiedSeq >= 0,
        ) { "C_SNAPSHOT_INVALID" }
        val pendingOutbox = chainDao.pendingOutboxCount(chainId)
        require(
            if (reseedHeader == null) {
                pendingOutbox == 0L && !preservePendingOutbox
            } else {
                preservePendingOutbox
            },
        ) { "C_SNAPSHOT_INVALID" }
        require(dao.unresolvedQuarantineCount(chainId) == 0L) { "C_SNAPSHOT_INVALID" }
        val revision = before.indexRevision
        val covered = before.verifiedSeq

        val sourceHeader =
            buildJsonObject {
                put("kind", "header")
                put("chain_id", chainId)
                put("generation", before.generation)
                put("log_id", before.logId)
                put("covered_through_seq", covered)
                put("covered_log_hash", before.verifiedHash)
                put("epoch_hash", before.epochHash)
                put("lamport_max", before.lamport)
            }
        val header = reseedHeader?.also { validateReseedHeader(chainId, before, it) } ?: sourceHeader
        emit(header)

        streamEntities(chainId, emit)
        streamRegisters(chainId, "clear", emit)
        streamRegisters(chainId, "retention", emit)
        streamAuthors(chainId, emit)
        streamAuthorGaps(chainId, emit)
        streamProjections(chainId, emit)

        val after = checkNotNull(chainDao.getChainState(chainId)) { "C_SNAPSHOT_INVALID" }
        require(
            after.indexRevision == revision && after.verifiedSeq == covered && after.materializedSeq == covered &&
                after.verifiedHash == before.verifiedHash && after.logId == before.logId &&
                chainDao.pendingOutboxCount(chainId) == pendingOutbox && dao.unresolvedQuarantineCount(chainId) == 0L,
        ) { "C_SNAPSHOT_INVALID" }
    }

    private fun validateReseedHeader(
        chainId: String,
        source: com.yunfie.illustia.pallasync.data.ChainStateEntity,
        header: JsonObject,
    ) {
        require(header.keys == RESEED_HEADER_FIELDS) { "C_SNAPSHOT_INVALID" }
        val logId = header["log_id"]?.jsonPrimitive?.content
        require(source.generation < MAX_GENERATION) { "C_SNAPSHOT_INVALID" }
        require(header["kind"]?.jsonPrimitive?.content == "header") { "C_SNAPSHOT_INVALID" }
        require(header["chain_id"]?.jsonPrimitive?.content == chainId) { "C_SNAPSHOT_INVALID" }
        require(header["generation"]?.jsonPrimitive?.long == source.generation + 1L) { "C_SNAPSHOT_INVALID" }
        require(logId?.let(::isUuid4) == true) { "C_SNAPSHOT_INVALID" }
        require(header["covered_through_seq"]?.jsonPrimitive?.long == 0L) { "C_SNAPSHOT_INVALID" }
        require(header["covered_log_hash"]?.jsonPrimitive?.content == dataZero(chainId, checkNotNull(logId))) {
            "C_SNAPSHOT_INVALID"
        }
        require(header["epoch_hash"]?.jsonPrimitive?.content?.let(::isHash32) == true) { "C_SNAPSHOT_INVALID" }
        require(header["lamport_max"]?.jsonPrimitive?.long == source.lamport) { "C_SNAPSHOT_INVALID" }
    }

    private fun dataZero(
        chainId: String,
        logId: String,
    ): String {
        val chain =
            java.util.Base64
                .getUrlDecoder()
                .decode(chainId)
        require(chain.size == 32 && isUuid4(logId)) { "C_SNAPSHOT_INVALID" }
        val id = java.util.UUID.fromString(logId)
        val uuidBytes =
            ByteBuffer
                .allocate(16)
                .putLong(id.mostSignificantBits)
                .putLong(id.leastSignificantBits)
                .array()
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update("PALLASYNC-LOG-v3\u0000".toByteArray(Charsets.US_ASCII))
        digest.update(chain)
        digest.update(uuidBytes)
        return java.util.Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(digest.digest())
    }

    private fun isHash32(value: String): Boolean =
        runCatching {
            val decoded =
                java.util.Base64
                    .getUrlDecoder()
                    .decode(value)
            decoded.size == 32 && java.util.Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(decoded) == value
        }.getOrDefault(false)

    private fun isUuid4(value: String): Boolean =
        runCatching { java.util.UUID.fromString(value) }.getOrNull()?.let {
            it.toString() == value && it.version() == UUID_VERSION && it.variant() == UUID_VARIANT
        } == true

    private suspend fun streamEntities(
        chain: String,
        emit: suspend (JsonObject) -> Unit,
    ) {
        var collection = ""
        var entityId = ""
        while (true) {
            val rows = database.snapshotStateDao().entityPage(chain, collection, entityId, pageSize)
            if (rows.isEmpty()) return
            rows.forEach { row -> emit(row.snapshotEntry()) }
            collection = rows.last().collection
            entityId = rows.last().entityId
        }
    }

    private suspend fun streamRegisters(
        chain: String,
        kind: String,
        emit: suspend (JsonObject) -> Unit,
    ) {
        var collection = ""
        var scope = ""
        while (true) {
            val rows = database.snapshotStateDao().registerPage(chain, kind, collection, scope, pageSize)
            if (rows.isEmpty()) return
            rows.forEach { row -> emit(row.snapshotEntry(kind)) }
            collection = rows.last().collection
            scope = rows.last().scope
        }
    }

    private suspend fun streamAuthors(
        chain: String,
        emit: suspend (JsonObject) -> Unit,
    ) {
        var group: Pair<Long, String>? = null
        var latest: com.yunfie.illustia.pallasync.data.SnapshotAuthorEvidenceRow? = null
        scanAuthorEvidence(chain) { row ->
            val key = row.generation to row.deviceId
            if (group != key) {
                latest?.let { emit(it.snapshotEntry()) }
                group = key
            }
            val previous = latest
            if (previous?.sameAuthorPosition(row) == true) {
                check(previous.recordHash == row.recordHash) { "C_DEVICE_EQUIVOCATION" }
            }
            if (previous == null || !previous.sameAuthorGroup(row) || row.deviceSeq > previous.deviceSeq) {
                latest = row
            }
        }
        latest?.let { emit(it.snapshotEntry()) }
    }

    // This is a bounded merge of two separately paged canonical streams.
    @Suppress("CyclomaticComplexMethod")
    private suspend fun streamAuthorGaps(
        chain: String,
        emit: suspend (JsonObject) -> Unit,
    ) {
        var storedGeneration = -1L
        var storedDevice = ""
        var storedFirst = 0L
        var storedPage = emptyList<com.yunfie.illustia.pallasync.data.SnapshotAuthorGapEntity>()
        var storedIndex = 0

        suspend fun nextStoredGap(): AuthorGapValue? {
            if (storedIndex == storedPage.size) {
                storedPage = database.snapshotStateDao().authorGapPage(chain, storedGeneration, storedDevice, storedFirst, pageSize)
                storedIndex = 0
            }
            val row = storedPage.getOrNull(storedIndex++) ?: return null
            storedGeneration = row.generation
            storedDevice = row.deviceId
            storedFirst = row.first
            return AuthorGapValue(row.generation, row.deviceId, row.first, row.last)
        }

        var authorGeneration = -1L
        var authorDevice = ""
        var authorSequence = -1L
        var authorPage = emptyList<AuthorSequenceEntity>()
        var authorIndex = 0
        var currentGroup: Pair<Long, String>? = null
        var previousSequence = 0L

        suspend fun nextAuthorRow(): AuthorSequenceEntity? {
            if (authorIndex == authorPage.size) {
                authorPage = database.snapshotStateDao().authorPage(chain, authorGeneration, authorDevice, authorSequence, pageSize)
                authorIndex = 0
            }
            val row = authorPage.getOrNull(authorIndex++) ?: return null
            authorGeneration = row.generation
            authorDevice = row.deviceId
            authorSequence = row.deviceSeq
            return row
        }

        suspend fun nextSuffixGap(): AuthorGapValue? {
            while (true) {
                val row = nextAuthorRow() ?: return null
                val key = row.generation to row.deviceId
                if (currentGroup != key) {
                    currentGroup = key
                    previousSequence = database.snapshotStateDao().authorHead(chain, row.generation, row.deviceId)?.deviceSeq ?: 0
                }
                if (row.deviceSeq <= previousSequence) continue
                val firstMissing = previousSequence + 1
                previousSequence = row.deviceSeq
                if (firstMissing < row.deviceSeq) return AuthorGapValue(row.generation, row.deviceId, firstMissing, row.deviceSeq - 1)
            }
        }

        var stored = nextStoredGap()
        var suffix = nextSuffixGap()
        while (stored != null || suffix != null) {
            if (stored != null && (suffix == null || stored.compareTo(suffix) <= 0)) {
                emit(authorGap(stored.generation to stored.deviceId, stored.first, stored.last))
                stored = nextStoredGap()
            } else {
                val row = checkNotNull(suffix)
                emit(authorGap(row.generation to row.deviceId, row.first, row.last))
                suffix = nextSuffixGap()
            }
        }
    }

    private suspend fun scanAuthorEvidence(
        chain: String,
        consume: suspend (com.yunfie.illustia.pallasync.data.SnapshotAuthorEvidenceRow) -> Unit,
    ) {
        var generation = -1L
        var device = ""
        var sequence = -1L
        while (true) {
            val rows = database.snapshotStateDao().authorEvidencePage(chain, generation, device, sequence, pageSize)
            if (rows.isEmpty()) return
            rows.forEach { consume(it) }
            rows.last().also {
                generation = it.generation
                device = it.deviceId
                sequence = it.deviceSeq
            }
        }
    }

    private suspend fun streamProjections(
        chain: String,
        emit: suspend (JsonObject) -> Unit,
    ) {
        var collection = ""
        var scope = ""
        while (true) {
            val rows = database.snapshotStateDao().projectionPage(chain, collection, scope, pageSize)
            if (rows.isEmpty()) return
            rows.forEach { row -> emit(row.snapshotEntry()) }
            collection = rows.last().collection
            scope = rows.last().scope
        }
    }

    private fun EntityRegisterEntity.snapshotEntry(): JsonObject {
        require(operation == "upsert" || operation == "delete") { "C_SNAPSHOT_INVALID" }
        require(winner.opRank == if (operation == "delete") 1 else 0) { "C_SNAPSHOT_INVALID" }
        val body = parseObject(bodyJson)
        val context = parseObject(contextJson)
        if (operation == "delete") require(body.isEmpty() && context.isEmpty()) { "C_SNAPSHOT_INVALID" }
        return buildJsonObject {
            put("kind", "entity")
            put("collection", collection)
            put("entity_id", entityId)
            put("key", winner.snapshotKey())
            put("operation", operation)
            put("body", body)
            put("context", context)
        }
    }

    private fun ScopeRegisterEntity.snapshotEntry(kind: String): JsonObject {
        require(registerKind == kind) { "C_SNAPSHOT_INVALID" }
        val name = if (kind == "clear") "clear" else "retention"
        val body = parseObject(bodyJson)
        require(winner.opRank == if (kind == "clear") 1 else 0) { "C_SNAPSHOT_INVALID" }
        return buildJsonObject {
            put("kind", name)
            put("collection", collection)
            put("scope", scope)
            put("key", winner.snapshotKey())
            if (kind == "retention") {
                require(body["scope"]?.jsonPrimitive?.content == scope) { "C_SNAPSHOT_INVALID" }
                val limit = body["limit"]
                if (limit == null ||
                    limit == kotlinx.serialization.json.JsonNull
                ) {
                    put("limit", kotlinx.serialization.json.JsonNull)
                } else {
                    put("limit", limit.jsonPrimitive.long)
                }
                put(
                    "stop_on_safety_limit",
                    body["stop_on_safety_limit"]?.jsonPrimitive?.content?.toBooleanStrict() ?: error("C_SNAPSHOT_INVALID"),
                )
            }
        }
    }

    private fun com.yunfie.illustia.pallasync.data.SnapshotAuthorEvidenceRow.snapshotEntry(): JsonObject =
        buildJsonObject {
            put("kind", "author")
            put("generation", generation)
            put("device_id", deviceId)
            put("device_seq", deviceSeq)
            put("record_hash", recordHash)
        }

    private fun com.yunfie.illustia.pallasync.data.SnapshotAuthorEvidenceRow.sameAuthorPosition(
        other: com.yunfie.illustia.pallasync.data.SnapshotAuthorEvidenceRow,
    ): Boolean = sameAuthorGroup(other) && deviceSeq == other.deviceSeq

    private fun com.yunfie.illustia.pallasync.data.SnapshotAuthorEvidenceRow.sameAuthorGroup(
        other: com.yunfie.illustia.pallasync.data.SnapshotAuthorEvidenceRow,
    ): Boolean = generation == other.generation && deviceId == other.deviceId

    private fun ProjectionStateEntity.snapshotEntry(): JsonObject {
        require(projectionLimit > 0 && (evictionKey == null) == (evictionEntity == null)) { "C_SNAPSHOT_INVALID" }
        val floor =
            if (evictionKey == null) {
                kotlinx.serialization.json.JsonNull
            } else {
                buildJsonObject {
                    put("key", decodeKey(evictionKey))
                    put("entity_id", checkNotNull(evictionEntity))
                }
            }
        return buildJsonObject {
            put("kind", "projection")
            put("collection", collection)
            put("scope", scope)
            put("limit", projectionLimit)
            put("eviction_floor", floor)
        }
    }

    private fun WinnerKeyHolder.snapshotKey(): JsonArray =
        JsonArray(
            listOf(
                JsonPrimitive(lamport),
                JsonPrimitive(opRank),
                JsonPrimitive(deviceId),
                JsonPrimitive(recordId),
                JsonPrimitive(opIndex),
            ),
        )

    private fun com.yunfie.illustia.pallasync.data.WinnerColumns.snapshotKey() =
        WinnerKeyHolder(lamport, opRank, uuid(deviceId), uuid(recordId), opIndex).snapshotKey()

    private fun authorGap(
        key: Pair<Long, String>,
        first: Long,
        last: Long,
    ): JsonObject =
        buildJsonObject {
            require(first > 0 && last >= first) { "C_SNAPSHOT_INVALID" }
            put("kind", "author_gap")
            put("generation", key.first)
            put("device_id", key.second)
            put("first", first)
            put("last", last)
        }

    private fun parseObject(json: String): JsonObject =
        runCatching {
            Json.parseToJsonElement(json).jsonObject
        }.getOrElse { error("C_SNAPSHOT_INVALID") }

    private fun decodeKey(bytes: ByteArray): JsonArray {
        require(bytes.size == ENCODED_KEY_BYTES) { "C_SNAPSHOT_INVALID" }
        val buffer = ByteBuffer.wrap(bytes)
        val lamport = buffer.long
        val rank = buffer.get().toInt() and 0xff
        val device = ByteArray(UUID_BYTES).also(buffer::get)
        val record = ByteArray(UUID_BYTES).also(buffer::get)
        val index = buffer.int
        require(
            lamport in 0..PallaSyncConflictResolver.MAX_COUNTER &&
                rank in 0..MAX_OP_RANK && index in 0..MAX_OP_INDEX,
        ) { "C_SNAPSHOT_INVALID" }
        return WinnerKeyHolder(lamport, rank, uuid(device), uuid(record), index).snapshotKey()
    }

    private fun uuid(bytes: ByteArray): String {
        require(bytes.size == UUID_BYTES) { "C_SNAPSHOT_INVALID" }
        val buffer = ByteBuffer.wrap(bytes)
        val value = java.util.UUID(buffer.long, buffer.long)
        require(value.version() == UUID_VERSION && value.variant() == UUID_VARIANT) { "C_SNAPSHOT_INVALID" }
        return value.toString()
    }

    private data class WinnerKeyHolder(
        val lamport: Long,
        val opRank: Int,
        val deviceId: String,
        val recordId: String,
        val opIndex: Int,
    )

    private data class AuthorGapValue(
        val generation: Long,
        val deviceId: String,
        val first: Long,
        val last: Long,
    ) : Comparable<AuthorGapValue> {
        override fun compareTo(other: AuthorGapValue): Int =
            compareValuesBy(this, other, AuthorGapValue::generation, AuthorGapValue::deviceId, AuthorGapValue::first)
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 200
        const val MAX_PAGE_SIZE = 500
        const val UUID_BYTES = 16
        const val ENCODED_KEY_BYTES = 45
        const val MAX_OP_RANK = 1
        const val MAX_OP_INDEX = 1023
        const val MAX_GENERATION = 4_294_967_295L
        const val UUID_VERSION = 4
        const val UUID_VARIANT = 2
        val RESEED_HEADER_FIELDS =
            setOf("kind", "chain_id", "generation", "log_id", "covered_through_seq", "covered_log_hash", "epoch_hash", "lamport_max")
    }
}
