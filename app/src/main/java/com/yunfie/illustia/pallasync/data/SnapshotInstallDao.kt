package com.yunfie.illustia.pallasync.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yunfie.illustia.pallasync.PallaSyncConflictResolver
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.nio.ByteBuffer
import java.util.UUID

/** Isolated plaintext staging; read access opens only after the whole snapshot verifies (§16.6). */
@Suppress("TooManyFunctions")
@Dao
interface SnapshotInstallDao {
    @Query("SELECT * FROM pallasync_chain_state WHERE chainId=:chain")
    suspend fun chainState(chain: String): ChainStateEntity?

    @Query("SELECT COUNT(*) FROM pallasync_outbox WHERE chain_id=:chain AND status!='accepted'")
    suspend fun pendingOutboxCount(chain: String): Long

    @Query("SELECT COUNT(*) FROM ps_quarantine WHERE chain_id=:chain AND resolution_state!='resolved'")
    suspend fun unresolvedQuarantineCount(chain: String): Long

    @Query("DELETE FROM ps_entities WHERE chain_id=:chain")
    suspend fun deleteEntities(chain: String)

    @Query("DELETE FROM ps_registers WHERE chain_id=:chain")
    suspend fun deleteRegisters(chain: String)

    @Query("DELETE FROM ps_authors WHERE chain_id=:chain")
    suspend fun deleteAuthors(chain: String)

    @Query("DELETE FROM ps_snapshot_author_heads WHERE chain_id=:chain")
    suspend fun deleteAuthorHeads(chain: String)

    @Query("DELETE FROM ps_snapshot_author_gaps WHERE chain_id=:chain")
    suspend fun deleteAuthorGaps(chain: String)

    @Query("DELETE FROM ps_projections WHERE chain_id=:chain")
    suspend fun deleteProjections(chain: String)

    @Query("DELETE FROM ps_history WHERE chain_id=:chain")
    suspend fun deleteHistory(chain: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntityRows(rows: List<EntityRegisterEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRegisterRows(rows: List<ScopeRegisterEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuthorHeadRows(rows: List<SnapshotAuthorHeadEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuthorGapRows(rows: List<SnapshotAuthorGapEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProjectionRows(rows: List<ProjectionStateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistoryRows(rows: List<HistoryIndexEntity>)

    @Query(
        "UPDATE pallasync_chain_state SET lamport=MAX(lamport,:lamport),downloaded_seq=MAX(downloaded_seq,:covered),verified_seq=:covered,materialized_seq=:covered,verified_hash=:coveredHash,last_relay_seq=CASE WHEN :trustedBootstrap THEN :covered ELSE last_relay_seq END,index_revision=index_revision+1 WHERE chainId=:chain AND index_revision=:revision AND log_id=:logId AND generation=:generation AND epoch_hash=:epochHash",
    )
    suspend fun advanceInstalledSnapshot(
        chain: String,
        revision: Long,
        logId: String,
        generation: Long,
        epochHash: String,
        covered: Long,
        coveredHash: String,
        lamport: Long,
        trustedBootstrap: Boolean,
    ): Int

    @Query("UPDATE ps_snapshot_installs SET status='installed' WHERE chain_id=:chain AND compaction_id=:compaction AND status='verified'")
    suspend fun markInstalled(
        chain: String,
        compaction: String,
    ): Int

    @Query("SELECT * FROM ps_snapshot_installs WHERE chain_id=:chain AND compaction_id=:compaction")
    suspend fun install(
        chain: String,
        compaction: String,
    ): SnapshotInstallEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInstall(install: SnapshotInstallEntity)

    @Query("SELECT * FROM ps_snapshot_install_parts WHERE chain_id=:chain AND compaction_id=:compaction ORDER BY part_index")
    suspend fun parts(
        chain: String,
        compaction: String,
    ): List<SnapshotInstallPartEntity>

    @Query(
        "SELECT e.* FROM ps_snapshot_install_entries e INNER JOIN ps_snapshot_installs i ON i.chain_id=e.chain_id AND i.compaction_id=e.compaction_id WHERE e.chain_id=:chain AND e.compaction_id=:compaction AND e.part_index=:part AND i.status='verified' ORDER BY e.entry_index",
    )
    suspend fun verifiedPartEntries(
        chain: String,
        compaction: String,
        part: Int,
    ): List<SnapshotInstallEntryEntity>

    @Query(
        "SELECT * FROM ps_snapshot_install_entries WHERE chain_id=:chain AND compaction_id=:compaction AND part_index=:part ORDER BY entry_index",
    )
    suspend fun stagedPartEntriesForRetry(
        chain: String,
        compaction: String,
        part: Int,
    ): List<SnapshotInstallEntryEntity>

    @Query(
        "SELECT e.* FROM ps_snapshot_install_entries e INNER JOIN ps_snapshot_installs i ON i.chain_id=e.chain_id AND i.compaction_id=e.compaction_id WHERE e.chain_id=:chain AND e.compaction_id=:compaction AND i.status='verified' AND (e.part_index>:afterPart OR (e.part_index=:afterPart AND e.entry_index>:afterEntry)) ORDER BY e.part_index,e.entry_index LIMIT :limit",
    )
    suspend fun entryPage(
        chain: String,
        compaction: String,
        afterPart: Int,
        afterEntry: Int,
        limit: Int,
    ): List<SnapshotInstallEntryEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPart(part: SnapshotInstallPartEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: SnapshotInstallEntryEntity)

    @Query(
        "UPDATE ps_snapshot_installs SET status='verified' WHERE chain_id=:chain AND compaction_id=:compaction AND status IN ('staging','verified')",
    )
    suspend fun setVerified(
        chain: String,
        compaction: String,
    ): Int

    @Transaction
    suspend fun begin(install: SnapshotInstallEntity) {
        check(install.status == "staging" && install.partCount in 1..MAX_SNAPSHOT_PARTS) { "C_SNAPSHOT_INVALID" }
        val old = install(install.chainId, install.compactionId)
        if (old == null) {
            insertInstall(install)
        } else {
            check(old.status in setOf("staging", "verified")) { "C_SNAPSHOT_INVALID" }
            check(old.copy(status = "staging") == install) { "C_SNAPSHOT_INVALID" }
        }
        check(install(install.chainId, install.compactionId)?.copy(status = "staging") == install) { "C_LOCAL_STORAGE_FULL" }
    }

    /** Retry is idempotent only when the exact same authenticated part entries are replayed. */
    @Transaction
    suspend fun retainVerifiedPart(
        part: SnapshotInstallPartEntity,
        rows: List<SnapshotInstallEntryEntity>,
    ) {
        val manifest = checkNotNull(install(part.chainId, part.compactionId)) { "C_SNAPSHOT_INVALID" }
        check(manifest.status in setOf("staging", "verified") && part.partIndex in 0 until manifest.partCount) {
            "C_SNAPSHOT_INVALID"
        }
        check(
            rows.isNotEmpty() && rows.size == part.entryCount &&
                rows.indices.all { index ->
                    rows[index].chainId == part.chainId &&
                        rows[index].compactionId == part.compactionId &&
                        rows[index].partIndex == part.partIndex &&
                        rows[index].entryIndex == index
                },
        ) { "C_SNAPSHOT_INVALID" }
        val existing = parts(part.chainId, part.compactionId).singleOrNull { it.partIndex == part.partIndex }
        if (existing == null) {
            check(manifest.status == "staging") { "C_SNAPSHOT_INVALID" }
            insertPart(part)
            for (row in rows) insertEntry(row)
        } else {
            check(existing == part && stagedPartEntriesForRetry(part.chainId, part.compactionId, part.partIndex) == rows) {
                "C_SNAPSHOT_INVALID"
            }
        }
    }

    @Transaction
    suspend fun markVerified(
        chain: String,
        compaction: String,
    ) {
        val manifest = checkNotNull(install(chain, compaction)) { "C_SNAPSHOT_INVALID" }
        val parts = parts(chain, compaction)
        check(parts.size == manifest.partCount && parts.indices.all { parts[it].partIndex == it }) { "C_SNAPSHOT_INVALID" }
        check(setVerified(chain, compaction) == 1) { "C_LOCAL_STORAGE_FULL" }
        check(install(chain, compaction)?.status == "verified") { "C_LOCAL_STORAGE_FULL" }
    }

    // Atomically replace only the materialized prefix after authenticated source selection (§16.6).
    // Keep orchestration under one Room transaction so cancellation or any failed insert rolls back all tables.
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    @Transaction
    suspend fun installVerifiedState(
        chain: String,
        compaction: String,
        expectedIndexRevision: Long,
        explicitlyTrustedBootstrap: Boolean = false,
    ) {
        val manifest = checkNotNull(install(chain, compaction)) { "C_SNAPSHOT_INVALID" }
        if (manifest.status == "installed") return
        check(manifest.status == "verified" && pendingOutboxCount(chain) == 0L) { "C_SNAPSHOT_INVALID" }
        check(unresolvedQuarantineCount(chain) == 0L) { "C_SNAPSHOT_INVALID" }
        val state = checkNotNull(chainState(chain)) { "C_SNAPSHOT_INVALID" }
        check(
            headerField(chain, compaction, "chain_id") == chain &&
                headerField(chain, compaction, "generation").toLongOrNull() == manifest.generation &&
                headerField(chain, compaction, "covered_through_seq").toLongOrNull() == manifest.coveredThroughSeq &&
                headerField(chain, compaction, "covered_log_hash") == manifest.coveredLogHash &&
                headerField(chain, compaction, "lamport_max").toLongOrNull() in 0..PallaSyncConflictResolver.MAX_COUNTER,
        ) { "C_SNAPSHOT_INVALID" }
        val validBootstrap =
            explicitlyTrustedBootstrap && !state.initialPullCompleted &&
                state.lastRelaySeq <= manifest.coveredThroughSeq
        check(
            state.indexRevision == expectedIndexRevision && (state.initialPullCompleted || validBootstrap) &&
                state.logId != null && state.logId == headerField(chain, compaction, "log_id") &&
                state.generation == manifest.generation && state.epochHash == headerField(chain, compaction, "epoch_hash") &&
                manifest.coveredThroughSeq >= state.verifiedSeq &&
                (manifest.coveredThroughSeq != state.verifiedSeq || manifest.coveredLogHash == state.verifiedHash),
        ) { "C_MISSING_DEPENDENCY" }
        val entries = entryPage(chain, compaction, -1, -1, INSTALL_PAGE_SIZE)
        check(entries.firstOrNull()?.let { snapshotObject(it.entryJson)["kind"]?.jsonPrimitive?.content == "header" } == true) {
            "C_SNAPSHOT_INVALID"
        }
        deleteHistory(chain)
        deleteEntities(chain)
        deleteRegisters(chain)
        deleteAuthors(chain)
        deleteAuthorHeads(chain)
        deleteAuthorGaps(chain)
        deleteProjections(chain)

        var part = -1
        var entry = -1
        while (true) {
            val page = entryPage(chain, compaction, part, entry, INSTALL_PAGE_SIZE)
            if (page.isEmpty()) break
            val entities = mutableListOf<EntityRegisterEntity>()
            val registers = mutableListOf<ScopeRegisterEntity>()
            val heads = mutableListOf<SnapshotAuthorHeadEntity>()
            val gaps = mutableListOf<SnapshotAuthorGapEntity>()
            val projections = mutableListOf<ProjectionStateEntity>()
            val history = mutableListOf<HistoryIndexEntity>()
            page.drop(if (part == -1 && entry == -1) 1 else 0).forEach { row ->
                val value = snapshotObject(row.entryJson)
                when (value.getValue("kind").jsonPrimitive.content) {
                    "header" -> {
                        check(row.partIndex == 0 && row.entryIndex == 0) { "C_SNAPSHOT_INVALID" }
                    }

                    "entity" -> {
                        val collection = value.getValue("collection").jsonPrimitive.content
                        val entityId = value.getValue("entity_id").jsonPrimitive.content
                        val operation = value.getValue("operation").jsonPrimitive.content
                        check(operation == "upsert" || operation == "delete") { "C_SNAPSHOT_INVALID" }
                        val key = snapshotWinner(value.getValue("key").jsonArray)
                        val scope = if (collection == "palleria.view_history/3") entityId.substringBefore(':') else "all"
                        val body = value.getValue("body").jsonObject.toString()
                        val context = value.getValue("context").jsonObject.toString()
                        entities +=
                            EntityRegisterEntity(
                                chain,
                                collection,
                                entityId,
                                key,
                                operation,
                                scope,
                                body,
                                context,
                                value["context"]
                                    ?.jsonObject
                                    ?.get("order_key")
                                    ?.jsonPrimitive
                                    ?.contentOrNull,
                            )
                        if (operation == "upsert") {
                            history += HistoryIndexEntity(chain, collection, scope, PallaSyncConflictResolver.encoded(key), entityId, key)
                        }
                    }

                    "clear", "retention" -> {
                        val kind = value.getValue("kind").jsonPrimitive.content
                        val collection = value.getValue("collection").jsonPrimitive.content
                        val scope = value.getValue("scope").jsonPrimitive.content
                        val key = snapshotWinner(value.getValue("key").jsonArray)
                        val body =
                            if (kind == "clear") {
                                "{}"
                            } else {
                                JsonObject(
                                    linkedMapOf(
                                        "scope" to kotlinx.serialization.json.JsonPrimitive(scope),
                                        "limit" to value.getValue("limit"),
                                        "stop_on_safety_limit" to value.getValue("stop_on_safety_limit"),
                                    ),
                                ).toString()
                            }
                        check(key.opRank == if (kind == "clear") 1 else 0) { "C_SNAPSHOT_INVALID" }
                        registers += ScopeRegisterEntity(chain, collection, scope, kind, key, body)
                    }

                    "author" -> {
                        val generation = value.getValue("generation").jsonPrimitive.long
                        check(generation == manifest.generation) { "C_SNAPSHOT_INVALID" }
                        heads +=
                            SnapshotAuthorHeadEntity(
                                chain,
                                generation,
                                value.getValue("device_id").jsonPrimitive.content,
                                value.getValue("device_seq").jsonPrimitive.long,
                                value.getValue("record_hash").jsonPrimitive.content,
                                compaction,
                            )
                    }

                    "author_gap" -> {
                        val generation = value.getValue("generation").jsonPrimitive.long
                        check(generation == manifest.generation) { "C_SNAPSHOT_INVALID" }
                        gaps +=
                            SnapshotAuthorGapEntity(
                                chain,
                                generation,
                                value.getValue("device_id").jsonPrimitive.content,
                                value.getValue("first").jsonPrimitive.long,
                                value.getValue("last").jsonPrimitive.long,
                                compaction,
                            )
                    }

                    "projection" -> {
                        val collection = value.getValue("collection").jsonPrimitive.content
                        val scope = value.getValue("scope").jsonPrimitive.content
                        val limit = value.getValue("limit").jsonPrimitive.long
                        val floor = value.getValue("eviction_floor")
                        val key = if (floor == JsonNull) null else snapshotWinner(floor.jsonObject.getValue("key").jsonArray)
                        val floorEntity =
                            if (floor == JsonNull) {
                                null
                            } else {
                                floor.jsonObject
                                    .getValue("entity_id")
                                    .jsonPrimitive.content
                            }
                        projections +=
                            ProjectionStateEntity(
                                chain,
                                collection,
                                scope,
                                liveProjectionCount(chain, collection, scope),
                                limit,
                                retentionLimit(chain, collection, scope),
                                key?.let(PallaSyncConflictResolver::encoded),
                                floorEntity,
                                expectedIndexRevision + 1,
                            )
                    }

                    else -> {
                        error("C_SNAPSHOT_INVALID")
                    }
                }
            }
            insertInstallRows(entities, registers, heads, gaps, projections, history)
            part = page.last().partIndex
            entry = page.last().entryIndex
        }
        check(
            advanceInstalledSnapshot(
                chain,
                expectedIndexRevision,
                checkNotNull(state.logId),
                manifest.generation,
                checkNotNull(state.epochHash),
                manifest.coveredThroughSeq,
                manifest.coveredLogHash,
                headerField(chain, compaction, "lamport_max").toLong(),
                validBootstrap,
            ) == 1,
        ) { "C_MISSING_DEPENDENCY" }
        check(markInstalled(chain, compaction) == 1) { "C_LOCAL_STORAGE_FULL" }
    }

    private suspend fun insertInstallRows(
        entities: List<EntityRegisterEntity>,
        registers: List<ScopeRegisterEntity>,
        heads: List<SnapshotAuthorHeadEntity>,
        gaps: List<SnapshotAuthorGapEntity>,
        projections: List<ProjectionStateEntity>,
        history: List<HistoryIndexEntity>,
    ) {
        if (entities.isNotEmpty()) insertEntityRows(entities)
        if (registers.isNotEmpty()) insertRegisterRows(registers)
        if (heads.isNotEmpty()) insertAuthorHeadRows(heads)
        if (gaps.isNotEmpty()) insertAuthorGapRows(gaps)
        if (projections.isNotEmpty()) insertProjectionRows(projections)
        if (history.isNotEmpty()) insertHistoryRows(history)
    }

    @Query(
        "SELECT COUNT(*) FROM ps_entities e JOIN ps_history h ON e.chain_id=h.chain_id AND e.collection=h.collection AND e.entity_id=h.entity_id WHERE e.chain_id=:chain AND e.collection=:collection AND h.scope=:scope AND e.operation='upsert' AND NOT EXISTS (SELECT 1 FROM ps_registers c WHERE c.chain_id=h.chain_id AND c.collection=h.collection AND c.register_kind='clear' AND (c.scope='all' OR c.scope=h.scope) AND (c.lamport>h.lamport OR (c.lamport=h.lamport AND (c.op_rank>h.op_rank OR (c.op_rank=h.op_rank AND (c.device_id>h.device_id OR (c.device_id=h.device_id AND (c.record_id>h.record_id OR (c.record_id=h.record_id AND c.op_index>=h.op_index)))))))))",
    )
    suspend fun liveProjectionCount(
        chain: String,
        collection: String,
        scope: String,
    ): Long

    @Query(
        "SELECT json_extract(body_json,'$.limit') FROM ps_registers WHERE chain_id=:chain AND collection=:collection AND scope=:scope AND register_kind='retention'",
    )
    suspend fun retentionLimit(
        chain: String,
        collection: String,
        scope: String,
    ): Long?

    private suspend fun headerField(
        chain: String,
        compaction: String,
        field: String,
    ): String {
        val header = entryPage(chain, compaction, -1, -1, 1).singleOrNull()?.let { snapshotObject(it.entryJson) }
        return checkNotNull(header?.get(field)?.jsonPrimitive?.content) { "C_SNAPSHOT_INVALID" }
    }

    private fun snapshotObject(value: String): JsonObject =
        runCatching { Json.parseToJsonElement(value).jsonObject }.getOrElse { error("C_SNAPSHOT_INVALID") }

    private fun snapshotWinner(value: JsonArray): WinnerColumns {
        check(value.size == SNAPSHOT_KEY_SIZE) { "C_SNAPSHOT_INVALID" }
        val lamport = value[0].jsonPrimitive.long
        val rank = value[1].jsonPrimitive.content.toIntOrNull() ?: error("C_SNAPSHOT_INVALID")
        val device = uuidBytes(value[2].jsonPrimitive.content)
        val record = uuidBytes(value[3].jsonPrimitive.content)
        val index = value[4].jsonPrimitive.content.toIntOrNull() ?: error("C_SNAPSHOT_INVALID")
        check(
            lamport in 0..PallaSyncConflictResolver.MAX_COUNTER && rank in 0..MAX_OP_RANK &&
                index in 0..MAX_OP_INDEX,
        ) { "C_SNAPSHOT_INVALID" }
        return WinnerColumns(lamport, rank, device, record, index)
    }

    private fun uuidBytes(value: String): ByteArray {
        val id = UUID.fromString(value)
        check(id.toString() == value && id.version() == UUID_VERSION && id.variant() == UUID_VARIANT) { "C_SNAPSHOT_INVALID" }
        return ByteBuffer
            .allocate(UUID_BYTES)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .array()
    }

    private companion object {
        const val MAX_SNAPSHOT_PARTS = 65_536
        const val INSTALL_PAGE_SIZE = 200
        const val SNAPSHOT_KEY_SIZE = 5
        const val MAX_OP_RANK = 1
        const val MAX_OP_INDEX = 1023
        const val UUID_VERSION = 4
        const val UUID_VARIANT = 2
        const val UUID_BYTES = 16
    }
}
