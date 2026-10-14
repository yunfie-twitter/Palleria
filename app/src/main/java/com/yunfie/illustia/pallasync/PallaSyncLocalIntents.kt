package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.MigrationJournalEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Base64

/** §§11.1/16.4. Unsigned application intent, separate from immutable signed outbox objects.
 * Uses the existing application journal table, never a relay object or recovery store.
 */
internal class PallaSyncLocalIntents(
    private val store: PallaSyncLocalStore,
    private val author: PallaSyncLocalAuthor,
    private val order: suspend (String, List<PallaSyncPendingEvent>) -> List<PallaSyncPendingEvent>,
) {
    @Serializable
    private data class LegacyManifest(
        val sourceChain: String,
        val sourceProtocol: String,
        val sourceHighWater: Long,
        val exportHash: String,
        val targetChain: String,
        val targetGeneration: Long,
        val stagedThrough: Long = 0,
        val sourceRecordsVisited: Long = 0,
        val candidateCount: Long = 0,
        val quarantinedCount: Long = 0,
        val normalizationCount: Long = 0,
        val collisionCount: Long = 0,
        val sourceComplete: Boolean = false,
    )

    internal data class LegacyEventMapping(
        val sourceSequence: Long,
        val sourceRecordId: String,
        val candidateIndex: Int,
        val sourceObjectHash: String,
        val event: PallaSyncPendingEvent,
    )

    internal data class LegacyBatchReport(
        val sourceRecordsVisited: Long,
        val quarantinedCount: Long,
        val normalizationCount: Long,
        val collisionCount: Long,
        val complete: Boolean,
    )

    internal data class LegacyMigrationProgress(
        val sourceChain: String,
        val targetChain: String,
        val sourceHighWater: Long,
        val stagedThrough: Long,
        val sourceComplete: Boolean,
        val candidates: Long,
        val pending: Long,
        val constructed: Long,
        val quarantined: Long,
    )

    companion object {
        private const val PAGE_SIZE = 200

        fun journalId(chain: String): String = "local-intents:$chain"

        // Exact local serialization identity, not a protocol object hash or signed wire value.
        private fun sourceHash(event: PallaSyncPendingEvent): String {
            val bytes = Json.encodeToString(event).toByteArray(Charsets.UTF_8)
            return try {
                PallaSyncCore.requireNormalDataBoundary(bytes)
                Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
            } finally {
                bytes.fill(0)
            }
        }
    }

    suspend fun stage(
        chain: String,
        events: List<PallaSyncPendingEvent>,
    ) = store.transaction {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        val dao = store.protocolDao()
        val id = journalId(chain)
        if (dao.migrationChunk(id, 0) == null) {
            dao.putMigrationChunk(MigrationJournalEntity(id, 0, id, chain, chain, "local_intent_cursor", "0"))
        }
        var index = checkNotNull(dao.lastMigrationIndex(id))
        for (event in events) {
            currentCoroutineContext().ensureActive()
            check(index < PallaSyncConflictResolver.MAX_COUNTER) { "C_COUNTER_EXHAUSTED" }
            val hash = sourceHash(event)
            val work = PallaSyncReplayWork(event, targetGeneration = state.generation)
            dao.putMigrationChunk(
                MigrationJournalEntity(id, ++index, hash, event.schema, chain, "pending", Json.encodeToString(work)),
            )
        }
    }

    /**
     * Start a migration journal only after the caller has selected and frozen an immutable
     * source/export (§9.1). The hash commits to the exact exported bytes, never plaintext keys.
     */
    suspend fun beginLegacyMigration(
        migrationId: String,
        sourceChain: String,
        sourceProtocol: String,
        sourceHighWater: Long,
        exportHash: String,
        targetChain: String,
        freezeConfirmed: Boolean,
    ) = store.transaction {
        require(migrationId.startsWith("legacy:") && migrationId.length <= 200) { "C_SCHEMA_VIOLATION" }
        require(sourceProtocol in setOf("2.0", "2.1") && sourceHighWater in 0..PallaSyncConflictResolver.MAX_COUNTER) {
            "C_SCHEMA_VIOLATION"
        }
        require(freezeConfirmed) { "C_MISSING_DEPENDENCY" }
        require(decodeHash(exportHash)) { "C_SCHEMA_VIOLATION" }
        val state = checkNotNull(store.dao.getChainState(targetChain)) { "C_MISSING_DEPENDENCY" }
        val dao = store.protocolDao()
        val row = dao.migrationChunk(migrationId, 0)
        val expected =
            LegacyManifest(sourceChain, sourceProtocol, sourceHighWater, exportHash, targetChain, state.generation)
        if (row == null) {
            dao.putMigrationChunk(
                MigrationJournalEntity(
                    migrationId,
                    0,
                    exportHash,
                    sourceChain,
                    targetChain,
                    "legacy_staging",
                    Json.encodeToString(expected),
                ),
            )
        } else {
            val saved = Json.decodeFromString<LegacyManifest>(row.reportJson)
            check(
                row.sourceHash == exportHash && row.sourceIdentity == sourceChain && row.targetChain == targetChain &&
                    saved.sourceChain == expected.sourceChain && saved.sourceProtocol == expected.sourceProtocol &&
                    saved.sourceHighWater == expected.sourceHighWater && saved.exportHash == expected.exportHash &&
                    saved.targetGeneration == state.generation,
            ) { "C_RECORD_ID_CONFLICT" }
        }
    }

    /** Atomically journal one verified export page and its cursor before any destination authoring (§9.3). */
    suspend fun stageLegacyBatch(
        migrationId: String,
        expectedAfterSequence: Long,
        throughSequence: Long,
        mappings: List<LegacyEventMapping>,
        report: LegacyBatchReport,
    ) = store.transaction {
        val dao = store.protocolDao()
        val row = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_MISSING_DEPENDENCY" }
        check(row.state == "legacy_staging") { "C_ROLLBACK_DETECTED" }
        val manifest = Json.decodeFromString<LegacyManifest>(row.reportJson)
        check(
            row.sourceHash == manifest.exportHash && row.sourceIdentity == manifest.sourceChain &&
                row.targetChain == manifest.targetChain,
        ) { "C_ROLLBACK_DETECTED" }
        check(expectedAfterSequence == manifest.stagedThrough && throughSequence in expectedAfterSequence..manifest.sourceHighWater) {
            "C_ROLLBACK_DETECTED"
        }
        val batchCounts =
            listOf(
                report.sourceRecordsVisited,
                report.quarantinedCount,
                report.normalizationCount,
                report.collisionCount,
            )
        check(batchCounts.all { it >= 0 }) {
            "C_SCHEMA_VIOLATION"
        }
        check(
            report.sourceRecordsVisited == throughSequence - expectedAfterSequence &&
                report.quarantinedCount <= report.sourceRecordsVisited,
        ) { "C_ROLLBACK_DETECTED" }
        check(!report.complete || throughSequence == manifest.sourceHighWater) { "C_ROLLBACK_DETECTED" }
        val state = checkNotNull(store.dao.getChainState(manifest.targetChain)) { "C_MISSING_DEPENDENCY" }
        check(state.generation == manifest.targetGeneration) { "C_MISSING_DEPENDENCY" }
        check(mappings.all { it.sourceSequence > expectedAfterSequence && it.sourceSequence <= throughSequence }) {
            "C_SCHEMA_VIOLATION"
        }
        check(mappings.map { "${it.sourceSequence}:${it.sourceRecordId}:${it.candidateIndex}" }.distinct().size == mappings.size) {
            "C_RECORD_ID_CONFLICT"
        }
        var index = checkNotNull(dao.lastMigrationIndex(migrationId))
        for (mapping in mappings) {
            currentCoroutineContext().ensureActive()
            check(mapping.sourceRecordId.length <= 64 && mapping.candidateIndex >= 0 && decodeHash(mapping.sourceObjectHash)) {
                "C_SCHEMA_VIOLATION"
            }
            check(mapping.event.createdAtMs == null && mapping.event.favoriteOrder == null) { "C_SCHEMA_VIOLATION" }
            check(index < PallaSyncConflictResolver.MAX_COUNTER) { "C_COUNTER_EXHAUSTED" }
            val eventHash = sourceHash(mapping.event)
            val work = PallaSyncReplayWork(mapping.event, targetGeneration = manifest.targetGeneration)
            dao.putMigrationChunk(
                MigrationJournalEntity(
                    migrationId,
                    ++index,
                    eventHash,
                    "legacy:${mapping.sourceSequence}:${mapping.sourceRecordId}:${mapping.candidateIndex}:${mapping.sourceObjectHash}",
                    manifest.targetChain,
                    "pending",
                    Json.encodeToString(work),
                ),
            )
        }
        val updated =
            manifest.copy(
                stagedThrough = throughSequence,
                sourceRecordsVisited = manifest.sourceRecordsVisited + report.sourceRecordsVisited,
                candidateCount = manifest.candidateCount + mappings.size,
                quarantinedCount = manifest.quarantinedCount + report.quarantinedCount,
                normalizationCount = manifest.normalizationCount + report.normalizationCount,
                collisionCount = manifest.collisionCount + report.collisionCount,
                sourceComplete = report.complete,
            )
        val nextState = if (report.complete) "legacy_staged" else "legacy_staging"
        check(
            dao.updateMigrationManifest(
                migrationId,
                manifest.exportHash,
                "legacy_staging",
                nextState,
                Json.encodeToString(updated),
            ) == 1,
        ) { "C_LOCAL_STORAGE_FULL" }
    }

    /** Re-author pending migration candidates from the saved source-to-record mapping. */
    suspend fun resumeLegacyMigration(
        migrationId: String,
        keys: PallaSyncKeySnapshot,
    ) {
        val dao = store.protocolDao()
        val manifestRow = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_MISSING_DEPENDENCY" }
        check(manifestRow.state in setOf("legacy_staged", "legacy_uploading")) { "C_MISSING_DEPENDENCY" }
        val manifest = Json.decodeFromString<LegacyManifest>(manifestRow.reportJson)
        check(manifest.sourceComplete && manifestRow.targetChain != null) { "C_MISSING_DEPENDENCY" }
        val state = checkNotNull(store.dao.getChainState(manifest.targetChain)) { "C_MISSING_DEPENDENCY" }
        check(state.generation == manifest.targetGeneration) { "C_MISSING_DEPENDENCY" }
        if (state.writeState != "active") return
        author.append(manifest.targetChain, keys, emptyList())
        for (row in dao.pendingMigrationPage(migrationId, 0, PAGE_SIZE)) {
            currentCoroutineContext().ensureActive()
            construct(row, keys)
        }
        if (manifestRow.state == "legacy_staged") {
            check(
                dao.updateMigrationManifest(
                    migrationId,
                    manifest.exportHash,
                    "legacy_staged",
                    "legacy_uploading",
                    Json.encodeToString(manifest),
                ) == 1,
            ) { "C_LOCAL_STORAGE_FULL" }
        }
    }

    suspend fun legacyMigrationProgress(migrationId: String): LegacyMigrationProgress? {
        val dao = store.protocolDao()
        val row = dao.migrationChunk(migrationId, 0) ?: return null
        if (!row.state.startsWith("legacy_")) return null
        val manifest = Json.decodeFromString<LegacyManifest>(row.reportJson)
        return LegacyMigrationProgress(
            manifest.sourceChain,
            manifest.targetChain,
            manifest.sourceHighWater,
            manifest.stagedThrough,
            manifest.sourceComplete,
            manifest.candidateCount,
            dao.migrationStateCount(migrationId, "pending"),
            dao.migrationStateCount(migrationId, "constructed"),
            manifest.quarantinedCount,
        )
    }

    private fun decodeHash(value: String): Boolean {
        val bytes = runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull() ?: return false
        return bytes.size == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) == value
    }

    /** §§8.6/11.4: uncertain queue-to-Room handoff must not allocate another operation for the same occurrence. */
    suspend fun stageRecoveryAudit(
        chain: String,
        event: PallaSyncPendingEvent,
    ) = store.transaction {
        PallaSyncRecoveryAuditQueue.validate(event)
        val marker = "recovery-audit:$chain:${event.entityId}"
        val hash = sourceHash(event)
        val existing = store.protocolDao().migrationChunk(marker, 0)
        if (existing != null) {
            check(existing.sourceHash == hash && existing.targetChain == chain) { "C_RECORD_ID_CONFLICT" }
        } else {
            stage(chain, listOf(event))
            store.protocolDao().putMigrationChunk(
                MigrationJournalEntity(
                    marker,
                    0,
                    hash,
                    event.entityId,
                    chain,
                    "recovery_audit_queued",
                    "{}",
                ),
            )
        }
    }

    /** One bounded rotating scan: a paused creation cannot starve a later delete/clear (§16.4). */
    suspend fun resume(
        chain: String,
        keys: PallaSyncKeySnapshot,
    ) {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        if (state.writeState != "active") return
        author.append(chain, keys, emptyList())
        val dao = store.protocolDao()
        val id = journalId(chain)
        val cursor = dao.migrationChunk(id, 0) ?: return
        val after = cursor.reportJson.toLongOrNull() ?: error("C_ROLLBACK_DETECTED")
        check(after >= 0 && after <= (dao.lastMigrationIndex(id) ?: 0)) { "C_ROLLBACK_DETECTED" }
        var page = dao.pendingMigrationPage(id, after, PAGE_SIZE)
        if (page.isEmpty() && after > 0) page = dao.pendingMigrationPage(id, 0, PAGE_SIZE)
        for (row in page) {
            currentCoroutineContext().ensureActive()
            construct(row, keys)
        }
        val next = if (page.size == PAGE_SIZE) page.last().chunkIndex else 0
        check(dao.updateMigrationReport(id, 0, id, "local_intent_cursor", next.toString()) == 1) { "C_LOCAL_STORAGE_FULL" }
    }

    private suspend fun construct(
        row: MigrationJournalEntity,
        keys: PallaSyncKeySnapshot,
    ) {
        val chain = checkNotNull(row.targetChain) { "C_ROLLBACK_DETECTED" }
        var work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
        val original = work.sourceEvent ?: work.event
        check(sourceHash(original) == row.sourceHash) { "C_ROLLBACK_DETECTED" }
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        if (state.generation != work.targetGeneration) {
            update(row, work.copy(blockedCode = "C_MISSING_DEPENDENCY"))
            return
        }
        try {
            if (!work.localPrepared) {
                val ordered = order(chain, listOf(original)).single()
                work = work.copy(event = ordered, sourceEvent = original.takeIf { it != ordered }, localPrepared = true)
                update(row, work)
            }
            author.append(
                chain,
                keys,
                listOf(work.event),
                replay = PallaSyncAuthorJournal.ReplayReference(row.migrationId, row.chunkIndex, row.sourceHash),
            )
        } catch (error: IllegalStateException) {
            val code = error.message
            if (code !in
                setOf("C_WRITE_PAUSED_BY_POLICY", "C_LOCAL_LIMIT", "C_ORDER_KEY_EXHAUSTED", "C_COUNTER_EXHAUSTED", "C_SCHEMA_VIOLATION")
            ) {
                throw error
            }
            update(row, work.copy(blockedCode = code))
        }
    }

    private suspend fun update(
        row: MigrationJournalEntity,
        work: PallaSyncReplayWork,
    ) {
        check(
            store.protocolDao().updateMigrationReport(
                row.migrationId,
                row.chunkIndex,
                row.sourceHash,
                "pending",
                Json.encodeToString(work),
            ) == 1,
        ) { "C_LOCAL_STORAGE_FULL" }
    }
}
