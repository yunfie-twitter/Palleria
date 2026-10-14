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
        require(chainDao.pendingOutboxCount(chainId) == 0L) { "C_SNAPSHOT_INVALID" }
        require(dao.unresolvedQuarantineCount(chainId) == 0L) { "C_SNAPSHOT_INVALID" }
        val revision = before.indexRevision
        val covered = before.verifiedSeq

        emit(
            buildJsonObject {
                put("kind", "header")
                put("chain_id", chainId)
                put("generation", before.generation)
                put("log_id", before.logId)
                put("covered_through_seq", covered)
                put("covered_log_hash", before.verifiedHash)
                put("epoch_hash", before.epochHash)
                put("lamport_max", before.lamport)
            },
        )

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
                chainDao.pendingOutboxCount(chainId) == 0L && dao.unresolvedQuarantineCount(chainId) == 0L,
        ) { "C_SNAPSHOT_INVALID" }
    }

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
        var latest: AuthorSequenceEntity? = null
        scanAuthors(chain) { row ->
            val key = row.generation to row.deviceId
            if (group != key) {
                latest?.let { emit(it.snapshotEntry()) }
                group = key
            }
            latest = row
        }
        latest?.let { emit(it.snapshotEntry()) }
    }

    private suspend fun streamAuthorGaps(
        chain: String,
        emit: suspend (JsonObject) -> Unit,
    ) {
        var group: Pair<Long, String>? = null
        var previousSequence = 0L
        scanAuthors(chain) { row ->
            val key = row.generation to row.deviceId
            if (group != key) {
                group = key
                previousSequence = 0
            }
            if (row.deviceSeq > previousSequence + 1) {
                emit(authorGap(key, previousSequence + 1, row.deviceSeq - 1))
            }
            previousSequence = row.deviceSeq
        }
    }

    private suspend fun scanAuthors(
        chain: String,
        consume: suspend (AuthorSequenceEntity) -> Unit,
    ) {
        var generation = -1L
        var device = ""
        var sequence = -1L
        while (true) {
            val rows = database.snapshotStateDao().authorPage(chain, generation, device, sequence, pageSize)
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

    private fun AuthorSequenceEntity.snapshotEntry(): JsonObject =
        buildJsonObject {
            put("kind", "author")
            put("generation", generation)
            put("device_id", deviceId)
            put("device_seq", deviceSeq)
            put("record_hash", recordHash)
        }

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
    }
}
