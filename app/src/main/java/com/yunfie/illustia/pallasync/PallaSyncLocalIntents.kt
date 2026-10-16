package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.LegacyMigrationCandidateEntity
import com.yunfie.illustia.pallasync.data.LegacyMigrationChoiceEntity
import com.yunfie.illustia.pallasync.data.MigrationJournalEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
        val conversionVersion: String = "legacy-v1",
        val stagedThrough: Long = 0,
        val sourceRecordsVisited: Long = 0,
        val candidateCount: Long = 0,
        val decisionCount: Long = 0,
        val quarantinedCount: Long = 0,
        val normalizationCount: Long = 0,
        val collisionCount: Long = 0,
        val schemaCounts: Map<String, Long> = emptyMap(),
        val sourceComplete: Boolean = false,
        val legacyFavoriteOrder: List<String>? = null,
    )

    internal data class LegacyEventMapping(
        val sourceSequence: Long,
        val sourceRecordId: String,
        val candidateIndex: Int,
        val sourceObjectHash: String,
        val event: PallaSyncPendingEvent,
        val normalizationCollision: Boolean = false,
        val legacyEventSequenceOrdered: Boolean = false,
        val legacyFavoriteSnapshotUnique: Boolean = false,
        val legacyEntityId: String? = null,
    )

    internal data class LegacyBatchReport(
        val sourceRecordsVisited: Long,
        val quarantinedCount: Long,
        val normalizationCount: Long,
        val collisionCount: Long,
        val complete: Boolean,
    )

    @Serializable
    internal data class LegacySourceDecision(
        val sourceSequence: Long,
        val sourceRecordId: String,
        val sourceObjectHash: String,
        val disposition: String,
        val reasonCode: String? = null,
        val normalizationChanged: Boolean = false,
        val normalizationCollision: Boolean = false,
    )

    internal data class LegacyMigrationProgress(
        val migrationId: String,
        val sourceChain: String,
        val sourceProtocol: String,
        val exportHash: String,
        val targetChain: String,
        val sourceHighWater: Long,
        val stagedThrough: Long,
        val sourceComplete: Boolean,
        val candidates: Long,
        val decisions: Long,
        val pending: Long,
        val constructed: Long,
        val quarantined: Long,
        val schemaCounts: Map<String, Long>,
        val accepted: Long = 0,
        val excluded: Long = 0,
        val state: String = "",
    )

    internal data class LegacyDecisionPage(
        val items: List<LegacySourceDecision>,
        val nextIndex: Long?,
    )

    @Serializable
    internal data class LegacySourceEvidence(
        val sourceSequence: Long,
        val sourceRecordId: String,
        val sourceObjectHash: String,
        val signedRecordJson: String,
    )

    internal data class LegacySourceEvidencePage(
        val items: List<LegacySourceEvidence>,
        val nextSequence: Long?,
    )

    @Serializable
    internal data class LegacyCandidate(
        val migrationEntryIndex: Long,
        val groupKey: String = "",
        val sourceSequence: Long,
        val sourceRecordId: String,
        val candidateIndex: Int,
        val sourceObjectHash: String,
        val event: PallaSyncPendingEvent,
        val legacyEntityId: String? = null,
        val requiresChoice: Boolean = false,
        val selectedByUser: Boolean = false,
        val excluded: Boolean = false,
    )

    internal data class LegacyCandidatePage(
        val items: List<LegacyCandidate>,
        val nextIndex: Long?,
    )

    internal data class LegacyCandidateGroup(
        val groupKey: String,
        val schema: String,
        val entityId: String,
        val candidateCount: Long,
        val selectedEntryIndex: Long?,
        val userConfirmed: Boolean,
    )

    internal data class LegacyCandidateGroupPage(
        val items: List<LegacyCandidateGroup>,
        val nextGroupKey: String?,
    )

    internal data class LegacyCandidateGroupDetail(
        val group: LegacyCandidateGroup,
        val candidates: List<LegacyCandidate>,
    )

    internal data class LegacyProgressPage(
        val items: List<LegacyMigrationProgress>,
        val nextMigrationId: String?,
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

        private fun legacyGroupKey(
            schema: String,
            entityId: String,
            discriminator: String? = null,
        ): String {
            val bytes = "$schema\u0000$entityId\u0000${discriminator.orEmpty()}".toByteArray(Charsets.UTF_8)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes))
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
                    saved.targetGeneration == state.generation && saved.conversionVersion == expected.conversionVersion,
            ) { "C_RECORD_ID_CONFLICT" }
        }
    }

    /** Atomically journal one verified export page and its cursor before any destination authoring (§9.3). */
    suspend fun stageLegacyBatch(
        migrationId: String,
        expectedAfterSequence: Long,
        throughSequence: Long,
        mappings: List<LegacyEventMapping>,
        decisions: List<LegacySourceDecision>,
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
        check(mappings.all { it.event.schema.length in 1..80 }) { "C_SCHEMA_VIOLATION" }
        check(mappings.map { it.event.schema }.distinct().size <= 64) { "C_LOCAL_LIMIT" }
        check(
            report.sourceRecordsVisited == throughSequence - expectedAfterSequence &&
                report.quarantinedCount <= report.sourceRecordsVisited &&
                decisions.size.toLong() == report.sourceRecordsVisited,
        ) { "C_ROLLBACK_DETECTED" }
        check(!report.complete) { "C_ROLLBACK_DETECTED" }
        val state = checkNotNull(store.dao.getChainState(manifest.targetChain)) { "C_MISSING_DEPENDENCY" }
        check(state.generation == manifest.targetGeneration) { "C_MISSING_DEPENDENCY" }
        check(mappings.all { it.sourceSequence > expectedAfterSequence && it.sourceSequence <= throughSequence }) {
            "C_SCHEMA_VIOLATION"
        }
        check(mappings.map { "${it.sourceSequence}:${it.sourceRecordId}:${it.candidateIndex}" }.distinct().size == mappings.size) {
            "C_RECORD_ID_CONFLICT"
        }
        check(decisions.map(LegacySourceDecision::sourceSequence).distinct().size == decisions.size) { "C_RECORD_ID_CONFLICT" }
        check(decisions.all { it.sourceSequence > expectedAfterSequence && it.sourceSequence <= throughSequence }) {
            "C_SCHEMA_VIOLATION"
        }
        val mappingsPerSource = mappings.groupBy(LegacyEventMapping::sourceSequence)
        check(
            decisions.all { decision ->
                val sourceMappings = mappingsPerSource[decision.sourceSequence].orEmpty()
                val mappingsMatchDecision =
                    sourceMappings.all {
                        it.sourceRecordId == decision.sourceRecordId && it.sourceObjectHash == decision.sourceObjectHash
                    }
                val idValid =
                    runCatching {
                        val id = java.util.UUID.fromString(decision.sourceRecordId)
                        id.version() == 7 && id.toString() == decision.sourceRecordId
                    }.getOrDefault(false)
                val outcomeValid =
                    when (decision.disposition) {
                        "converted" -> {
                            decision.reasonCode == null && mappingsMatchDecision
                        }

                        "quarantined", "excluded" -> {
                            decision.reasonCode?.matches(Regex("C_[A-Z_]+")) == true &&
                                sourceMappings.isEmpty()
                        }

                        else -> {
                            false
                        }
                    }
                idValid && decodeHash(decision.sourceObjectHash) && outcomeValid
            },
        ) { "C_SCHEMA_VIOLATION" }
        check(decisions.count { it.disposition == "quarantined" }.toLong() == report.quarantinedCount) {
            "C_ROLLBACK_DETECTED"
        }
        check(decisions.count(LegacySourceDecision::normalizationChanged).toLong() == report.normalizationCount) {
            "C_ROLLBACK_DETECTED"
        }
        check(decisions.count(LegacySourceDecision::normalizationCollision).toLong() == report.collisionCount) {
            "C_ROLLBACK_DETECTED"
        }
        var index = checkNotNull(dao.lastMigrationIndex(migrationId))
        for (mapping in mappings) {
            currentCoroutineContext().ensureActive()
            check(mapping.sourceRecordId.length <= 64 && mapping.candidateIndex >= 0 && decodeHash(mapping.sourceObjectHash)) {
                "C_SCHEMA_VIOLATION"
            }
            check(mapping.event.createdAtMs == null && mapping.event.favoriteOrder == null) { "C_SCHEMA_VIOLATION" }
            check(index < PallaSyncConflictResolver.MAX_COUNTER) { "C_COUNTER_EXHAUSTED" }
            val entryIndex = ++index
            val eventHash = sourceHash(mapping.event)
            val work =
                PallaSyncReplayWork(
                    mapping.event,
                    targetGeneration = manifest.targetGeneration,
                    legacyEventSequenceOrdered = mapping.legacyEventSequenceOrdered,
                    legacyFavoriteSnapshotUnique = mapping.legacyFavoriteSnapshotUnique,
                    legacyEntityId = mapping.legacyEntityId,
                )
            dao.putMigrationChunk(
                MigrationJournalEntity(
                    migrationId,
                    entryIndex,
                    eventHash,
                    "legacy:${mapping.sourceSequence}:${mapping.sourceRecordId}:${mapping.candidateIndex}:${mapping.sourceObjectHash}",
                    manifest.targetChain,
                    "pending",
                    Json.encodeToString(work),
                ),
            )
            dao.putLegacyMigrationCandidate(
                LegacyMigrationCandidateEntity(
                    migrationId = migrationId,
                    entryIndex = entryIndex,
                    groupKey =
                        legacyGroupKey(
                            mapping.event.schema,
                            mapping.event.entityId,
                            when {
                                mapping.legacyEventSequenceOrdered -> "legacy-v21-sequence"
                                mapping.normalizationCollision -> "collision:${mapping.sourceSequence}"
                                mapping.legacyFavoriteSnapshotUnique -> "legacy-v20-favorite-distinct"
                                else -> "entry:$entryIndex"
                            },
                        ),
                    sourceSequence = mapping.sourceSequence,
                    candidateIndex = mapping.candidateIndex,
                    sourceRecordId = mapping.sourceRecordId,
                    sourceObjectHash = mapping.sourceObjectHash,
                    schema = mapping.event.schema,
                    entityId = mapping.event.entityId,
                    operation = mapping.event.operation,
                    requiresChoice = mapping.normalizationCollision,
                ),
            )
        }
        for (decision in decisions) {
            currentCoroutineContext().ensureActive()
            check(index < PallaSyncConflictResolver.MAX_COUNTER) { "C_COUNTER_EXHAUSTED" }
            val decisionJson = Json.encodeToString(decision)
            dao.putMigrationChunk(
                MigrationJournalEntity(
                    migrationId,
                    ++index,
                    decision.sourceObjectHash,
                    "legacy-decision:${decision.sourceSequence}:${decision.sourceRecordId}",
                    manifest.targetChain,
                    "legacy_decision_${decision.disposition}",
                    decisionJson,
                ),
            )
        }
        val updated =
            manifest.copy(
                stagedThrough = throughSequence,
                sourceRecordsVisited = manifest.sourceRecordsVisited + report.sourceRecordsVisited,
                candidateCount = manifest.candidateCount + mappings.size,
                decisionCount = manifest.decisionCount + decisions.size,
                quarantinedCount = manifest.quarantinedCount + report.quarantinedCount,
                normalizationCount = manifest.normalizationCount + report.normalizationCount,
                collisionCount = manifest.collisionCount + report.collisionCount,
                schemaCounts =
                    manifest.schemaCounts +
                        mappings
                            .groupingBy { it.event.schema }
                            .eachCount()
                            .mapValues { (schema, count) -> manifest.schemaCounts.getOrDefault(schema, 0) + count },
                sourceComplete = false,
            )
        check(
            dao.updateMigrationManifest(
                migrationId,
                manifest.exportHash,
                "legacy_staging",
                "legacy_staging",
                Json.encodeToString(updated),
            ) == 1,
        ) { "C_LOCAL_STORAGE_FULL" }
    }

    /** Finalizes source conversion only after the second full transcript matches the frozen proof (§9.3). */
    suspend fun completeLegacySourceScan(
        migrationId: String,
        expectedHighWater: Long,
        expectedRecordCount: Long,
        expectedExportHash: String,
        legacyFavoriteOrder: List<String>? = null,
    ) = store.transaction {
        val dao = store.protocolDao()
        val row = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_MISSING_DEPENDENCY" }
        val manifest = Json.decodeFromString<LegacyManifest>(row.reportJson)
        check(
            manifest.sourceHighWater == expectedHighWater && manifest.exportHash == expectedExportHash &&
                manifest.stagedThrough == expectedHighWater && manifest.sourceRecordsVisited == expectedRecordCount &&
                manifest.decisionCount == expectedRecordCount &&
                dao.legacySourceEvidenceCount(sourceEvidenceJournalId(migrationId)) == expectedRecordCount,
        ) { "C_ROLLBACK_DETECTED" }
        if (manifest.sourceComplete) {
            check(
                row.state == "legacy_candidates_staged" &&
                    (legacyFavoriteOrder == null || manifest.legacyFavoriteOrder == legacyFavoriteOrder),
            ) { "C_ROLLBACK_DETECTED" }
            return@transaction
        }
        check(row.state == "legacy_staging") { "C_ROLLBACK_DETECTED" }
        reconcileLegacyFavoriteNormalizationCollisions(migrationId)
        val completed =
            manifest.copy(
                sourceComplete = true,
                legacyFavoriteOrder = legacyFavoriteOrder ?: manifest.legacyFavoriteOrder,
            )
        check(
            dao.updateMigrationManifest(
                migrationId,
                manifest.exportHash,
                "legacy_staging",
                "legacy_candidates_staged",
                Json.encodeToString(completed),
            ) == 1,
        ) { "C_LOCAL_STORAGE_FULL" }
    }

    /** Retains the exact signed ciphertext source object, separate from destination intents and reports (§9.2). */
    suspend fun archiveLegacySourceRecord(
        migrationId: String,
        sequence: Long,
        recordId: String,
        objectHash: String,
        signedRecordJson: String,
    ) = store.transaction {
        val dao = store.protocolDao()
        val manifestRow = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_MISSING_DEPENDENCY" }
        val manifest = Json.decodeFromString<LegacyManifest>(manifestRow.reportJson)
        val bytes = signedRecordJson.toByteArray(Charsets.UTF_8)
        try {
            check(sequence in 1..manifest.sourceHighWater && decodeHash(objectHash) && bytes.size <= 8_388_608) {
                "C_SCHEMA_VIOLATION"
            }
            check(PallaSyncCore.objectHash(signedRecordJson) == objectHash) { "C_RECORD_ID_CONFLICT" }
        } finally {
            bytes.fill(0)
        }
        val archiveId = sourceEvidenceJournalId(migrationId)
        val existing = dao.migrationChunk(archiveId, sequence)
        val evidence = LegacySourceEvidence(sequence, recordId, objectHash, signedRecordJson)
        if (existing != null) {
            check(
                existing.state == "legacy_source_evidence" && existing.sourceHash == objectHash &&
                    existing.sourceIdentity == "legacy-source:$sequence:$recordId" &&
                    existing.reportJson == Json.encodeToString(evidence),
            ) { "C_RECORD_ID_CONFLICT" }
        } else {
            dao.putMigrationChunk(
                MigrationJournalEntity(
                    archiveId,
                    sequence,
                    objectHash,
                    "legacy-source:$sequence:$recordId",
                    manifest.targetChain,
                    "legacy_source_evidence",
                    Json.encodeToString(evidence),
                ),
            )
        }
    }

    /** Re-author pending migration candidates from the saved source-to-record mapping. */
    suspend fun resumeLegacyMigration(
        migrationId: String,
        keys: PallaSyncKeySnapshot,
    ) {
        val dao = store.protocolDao()
        val manifestRow = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_MISSING_DEPENDENCY" }
        if (manifestRow.state == "legacy_receipts_complete") return
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
        settleLegacyMigrationReceipts(migrationId)
    }

    suspend fun legacyMigrationProgress(migrationId: String): LegacyMigrationProgress? {
        val dao = store.protocolDao()
        val row = dao.migrationChunk(migrationId, 0) ?: return null
        if (!row.state.startsWith("legacy_")) return null
        val manifest = Json.decodeFromString<LegacyManifest>(row.reportJson)
        return LegacyMigrationProgress(
            migrationId,
            manifest.sourceChain,
            manifest.sourceProtocol,
            manifest.exportHash,
            manifest.targetChain,
            manifest.sourceHighWater,
            manifest.stagedThrough,
            manifest.sourceComplete,
            manifest.candidateCount,
            manifest.decisionCount,
            dao.migrationStateCount(migrationId, "pending"),
            dao.migrationStateCount(migrationId, "constructed"),
            manifest.quarantinedCount,
            manifest.schemaCounts,
            dao.migrationStateCount(migrationId, "receipt_verified"),
            dao.migrationStateCount(migrationId, "legacy_candidate_excluded"),
            row.state,
        )
    }

    /** Called only inside the same transaction that retains a verified signed upload receipt (§9.3). */
    suspend fun confirmLegacyMigrationReceipt(
        chain: String,
        recordId: String,
        objectHash: String,
        receiptHash: String,
    ) {
        check(decodeHash(objectHash) && decodeHash(receiptHash)) { "C_SCHEMA_VIOLATION" }
        val dao = store.protocolDao()
        val matches = dao.legacyMigrationReceiptCandidates(chain, recordId)
        check(matches.size <= 1) { "C_RECORD_ID_CONFLICT" }
        val candidate = matches.singleOrNull() ?: return
        check(candidate.destinationObjectHash == objectHash) { "C_RECORD_ID_CONFLICT" }
        if (candidate.state == "constructed") {
            check(
                dao.confirmLegacyMigrationReceipt(
                    candidate.migrationId,
                    candidate.chunkIndex,
                    chain,
                    recordId,
                    objectHash,
                    receiptHash,
                ) == 1,
            ) { "C_ACCEPTANCE_UNPROVEN" }
        } else {
            check(candidate.state == "receipt_verified" && candidate.verifiedReceiptHash == receiptHash) {
                "C_RECORD_ID_CONFLICT"
            }
        }
        settleLegacyMigrationReceipts(candidate.migrationId)
    }

    private suspend fun settleLegacyMigrationReceipts(migrationId: String) {
        val dao = store.protocolDao()
        val root = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_ROLLBACK_DETECTED" }
        if (root.state == "legacy_receipts_complete") return
        check(root.state in setOf("legacy_uploading", "legacy_staged")) { "C_ROLLBACK_DETECTED" }
        val manifest = Json.decodeFromString<LegacyManifest>(root.reportJson)
        val excluded = dao.migrationStateCount(migrationId, "legacy_candidate_excluded")
        val accepted = dao.migrationStateCount(migrationId, "receipt_verified")
        if (accepted == manifest.candidateCount - excluded &&
            dao.pendingMigrationCount(migrationId) == 0L && dao.migrationStateCount(migrationId, "constructed") == 0L
        ) {
            check(
                dao.updateMigrationManifest(
                    migrationId,
                    root.sourceHash,
                    root.state,
                    "legacy_receipts_complete",
                    root.reportJson,
                ) == 1,
            ) { "C_LOCAL_STORAGE_FULL" }
        }
    }

    suspend fun legacyMigrationProgressPage(
        afterMigrationId: String = "",
        limit: Int = 20,
    ): LegacyProgressPage {
        require(limit in 1..20 && afterMigrationId.length <= 200) { "C_SCHEMA_VIOLATION" }
        val rows = store.protocolDao().legacyMigrationManifestPage(afterMigrationId, limit + 1)
        val pageRows = rows.take(limit)
        val items = pageRows.mapNotNull { row -> legacyMigrationProgress(row.migrationId) }
        return LegacyProgressPage(items, pageRows.lastOrNull()?.migrationId.takeIf { rows.size > limit })
    }

    /** Bounded keyset read for the migration report; decision rows never enter sync/outbox paths. */
    suspend fun legacyMigrationDecisionPage(
        migrationId: String,
        afterIndex: Long,
        limit: Int = PAGE_SIZE,
    ): LegacyDecisionPage {
        require(afterIndex >= 0 && limit in 1..PAGE_SIZE) { "C_SCHEMA_VIOLATION" }
        val rows = store.protocolDao().legacyDecisionPage(migrationId, afterIndex, limit)
        val items =
            rows.map { row ->
                val decision = Json.decodeFromString<LegacySourceDecision>(row.reportJson)
                check(
                    row.sourceHash == decision.sourceObjectHash &&
                        row.sourceIdentity == "legacy-decision:${decision.sourceSequence}:${decision.sourceRecordId}" &&
                        row.state == "legacy_decision_${decision.disposition}",
                ) { "C_ROLLBACK_DETECTED" }
                decision
            }
        return LegacyDecisionPage(items, rows.lastOrNull()?.chunkIndex)
    }

    suspend fun legacyMigrationSourceEvidencePage(
        migrationId: String,
        afterSequence: Long,
        limit: Int = PAGE_SIZE,
    ): LegacySourceEvidencePage {
        require(afterSequence >= 0 && limit in 1..PAGE_SIZE) { "C_SCHEMA_VIOLATION" }
        val archiveId = sourceEvidenceJournalId(migrationId)
        val rows = store.protocolDao().legacySourceEvidencePage(archiveId, afterSequence, limit + 1)
        val pageRows = rows.take(limit)
        val items =
            pageRows.map { row ->
                val evidence = Json.decodeFromString<LegacySourceEvidence>(row.reportJson)
                check(
                    row.chunkIndex == evidence.sourceSequence && row.sourceHash == evidence.sourceObjectHash &&
                        row.sourceIdentity == "legacy-source:${evidence.sourceSequence}:${evidence.sourceRecordId}" &&
                        row.state == "legacy_source_evidence" &&
                        PallaSyncCore.objectHash(evidence.signedRecordJson) == evidence.sourceObjectHash,
                ) { "C_ROLLBACK_DETECTED" }
                evidence
            }
        return LegacySourceEvidencePage(items, pageRows.lastOrNull()?.chunkIndex.takeIf { rows.size > limit })
    }

    suspend fun legacyMigrationCandidatePage(
        migrationId: String,
        afterIndex: Long,
        limit: Int = PAGE_SIZE,
    ): LegacyCandidatePage {
        require(afterIndex >= 0 && limit in 1..PAGE_SIZE) { "C_SCHEMA_VIOLATION" }
        val dao = store.protocolDao()
        val manifestRow = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_MISSING_DEPENDENCY" }
        val manifest = Json.decodeFromString<LegacyManifest>(manifestRow.reportJson)
        val rows = dao.legacyCandidatePage(migrationId, afterIndex, limit + 1)
        val pageRows = rows.take(limit)
        val items =
            pageRows.map { row ->
                val identity = row.sourceIdentity.split(':')
                check(identity.size == 5 && identity[0] == "legacy") { "C_ROLLBACK_DETECTED" }
                val sequence = identity[1].toLongOrNull() ?: error("C_ROLLBACK_DETECTED")
                val recordId = identity[2]
                val candidateIndex = identity[3].toIntOrNull() ?: error("C_ROLLBACK_DETECTED")
                val objectHash = identity[4]
                check(
                    sequence in 1..manifest.sourceHighWater && candidateIndex >= 0 &&
                        runCatching {
                            java.util.UUID
                                .fromString(recordId)
                                .let { it.version() == 7 && it.toString() == recordId }
                        }.getOrDefault(false) &&
                        decodeHash(objectHash) && row.targetChain == manifest.targetChain &&
                        row.state in setOf("pending", "constructed", "receipt_verified", "legacy_candidate_excluded"),
                ) { "C_ROLLBACK_DETECTED" }
                val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
                val original = work.sourceEvent ?: work.event
                val destinationBindingValid =
                    if (row.state in setOf("constructed", "receipt_verified")) {
                        row.destinationRecordId == work.destinationRecordId &&
                            decodeHash(row.destinationObjectHash.orEmpty())
                    } else {
                        true
                    }
                check(
                    sourceHash(original) == row.sourceHash && work.targetGeneration == manifest.targetGeneration &&
                        original.favoriteOrder == null && original.createdAtMs == null &&
                        destinationBindingValid &&
                        (row.state != "receipt_verified" || decodeHash(row.verifiedReceiptHash.orEmpty())),
                ) { "C_ROLLBACK_DETECTED" }
                val metadata = checkNotNull(dao.legacyMigrationCandidateByEntry(migrationId, row.chunkIndex)) { "C_ROLLBACK_DETECTED" }
                val groupKey = metadata.groupKey
                check(
                    groupKey ==
                        legacyGroupKey(
                            original.schema,
                            original.entityId,
                            legacyCandidateDiscriminator(work, metadata, row.chunkIndex),
                        ),
                ) { "C_ROLLBACK_DETECTED" }
                val choice = dao.legacyMigrationCandidateChoice(migrationId, groupKey)
                LegacyCandidate(
                    row.chunkIndex,
                    groupKey,
                    sequence,
                    recordId,
                    candidateIndex,
                    objectHash,
                    original,
                    legacyEntityId = work.legacyEntityId,
                    requiresChoice = metadata.requiresChoice && !work.legacyEventSequenceOrdered,
                    selectedByUser = choice?.let { it.userConfirmed && it.selectedEntryIndex == row.chunkIndex } == true,
                    excluded = row.state == "legacy_candidate_excluded",
                )
            }
        return LegacyCandidatePage(items, pageRows.lastOrNull()?.chunkIndex.takeIf { rows.size > limit })
    }

    /** Bounded view of collision groups; persisted choices are local review metadata (§9.2). */
    suspend fun legacyMigrationCandidateGroupPage(
        migrationId: String,
        afterGroupKey: String = "",
        limit: Int = 50,
    ): LegacyCandidateGroupPage {
        require(limit in 1..50 && afterGroupKey.length <= 64) { "C_SCHEMA_VIOLATION" }
        val dao = store.protocolDao()
        val keys = dao.legacyMigrationCandidateGroupPage(migrationId, afterGroupKey, limit + 1)
        val pageKeys = keys.take(limit)
        val items = pageKeys.map { key -> legacyCandidateGroup(migrationId, key) }
        return LegacyCandidateGroupPage(items, pageKeys.lastOrNull().takeIf { keys.size > limit })
    }

    suspend fun legacyMigrationCandidateGroup(
        migrationId: String,
        groupKey: String,
    ): LegacyCandidateGroupDetail {
        require(groupKey.length == 43) { "C_SCHEMA_VIOLATION" }
        val dao = store.protocolDao()
        val group = legacyCandidateGroup(migrationId, groupKey)
        val candidateRows = dao.legacyMigrationCandidatesInGroup(migrationId, groupKey, 0, 201)
        check(candidateRows.size.toLong() == group.candidateCount && candidateRows.size <= 200) { "C_LOCAL_LIMIT" }
        val candidates =
            candidateRows.map { candidate ->
                val row = checkNotNull(dao.migrationChunk(migrationId, candidate.entryIndex)) { "C_ROLLBACK_DETECTED" }
                check(row.state in setOf("pending", "legacy_candidate_excluded")) { "C_ROLLBACK_DETECTED" }
                val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
                check(
                    row.sourceHash ==
                        sourceHash(
                            work.event,
                        ) && candidate.groupKey ==
                        legacyGroupKey(
                            work.event.schema,
                            work.event.entityId,
                            legacyCandidateDiscriminator(work, candidate, row.chunkIndex),
                        ) &&
                        candidate.schema == work.event.schema && candidate.entityId == work.event.entityId &&
                        candidate.operation == work.event.operation,
                ) { "C_ROLLBACK_DETECTED" }
                val identity = row.sourceIdentity.split(':')
                check(
                    identity.size == 5 && identity[1].toLongOrNull() == candidate.sourceSequence &&
                        identity[2] == candidate.sourceRecordId && identity[3].toIntOrNull() == candidate.candidateIndex &&
                        identity[4] == candidate.sourceObjectHash,
                ) { "C_ROLLBACK_DETECTED" }
                LegacyCandidate(
                    candidate.entryIndex,
                    candidate.groupKey,
                    candidate.sourceSequence,
                    candidate.sourceRecordId,
                    candidate.candidateIndex,
                    candidate.sourceObjectHash,
                    work.event,
                    legacyEntityId = work.legacyEntityId,
                    requiresChoice = candidate.requiresChoice && !work.legacyEventSequenceOrdered,
                    selectedByUser =
                        dao.legacyMigrationCandidateChoice(migrationId, candidate.groupKey)?.let {
                            it.userConfirmed &&
                                it.selectedEntryIndex == candidate.entryIndex
                        } ==
                            true,
                )
            }
        return LegacyCandidateGroupDetail(group, candidates)
    }

    /** Save only an explicit choice from a verified candidate set; no candidate is authored yet. */
    suspend fun chooseLegacyMigrationCandidate(
        migrationId: String,
        groupKey: String,
        entryIndex: Long,
    ) = store.transaction {
        val dao = store.protocolDao()
        val manifest = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_MISSING_DEPENDENCY" }
        check(manifest.state == "legacy_candidates_staged") { "C_MISSING_DEPENDENCY" }
        val metadata = checkNotNull(dao.legacyMigrationCandidate(migrationId, groupKey, entryIndex)) { "C_SCHEMA_VIOLATION" }
        val row = checkNotNull(dao.migrationChunk(migrationId, entryIndex)) { "C_ROLLBACK_DETECTED" }
        val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
        check(
            row.state == "pending" && row.targetChain == checkNotNull(manifest.targetChain) &&
                row.sourceHash == sourceHash(work.event) && metadata.groupKey ==
                legacyGroupKey(
                    work.event.schema,
                    work.event.entityId,
                    legacyCandidateDiscriminator(work, metadata, entryIndex),
                ),
        ) { "C_ROLLBACK_DETECTED" }
        check(!work.legacyEventSequenceOrdered && metadata.requiresChoice) { "C_MISSING_DEPENDENCY" }
        dao.putLegacyMigrationCandidateChoice(
            LegacyMigrationChoiceEntity(migrationId, groupKey, entryIndex, userConfirmed = true),
        )
    }

    /** Do not author a partially reviewed migration. Ambiguous groups require an explicit choice. */
    suspend fun finalizeLegacyMigrationChoices(migrationId: String) =
        store.transaction {
            val dao = store.protocolDao()
            val root = checkNotNull(dao.migrationChunk(migrationId, 0)) { "C_MISSING_DEPENDENCY" }
            check(root.state == "legacy_candidates_staged") { "C_MISSING_DEPENDENCY" }
            val manifest = Json.decodeFromString<LegacyManifest>(root.reportJson)
            check(
                manifest.sourceComplete && dao.legacySourceEvidenceCount(sourceEvidenceJournalId(migrationId)) == manifest.sourceHighWater,
            ) {
                "C_ROLLBACK_DETECTED"
            }
            var after = ""
            while (true) {
                currentCoroutineContext().ensureActive()
                val keys = dao.legacyMigrationCandidateGroupPage(migrationId, after, 50)
                if (keys.isEmpty()) break
                for (key in keys) {
                    currentCoroutineContext().ensureActive()
                    val count = dao.legacyMigrationCandidateGroupCount(migrationId, key)
                    check(count > 0) { "C_ROLLBACK_DETECTED" }
                    val choice = dao.legacyMigrationCandidateChoice(migrationId, key)
                    val selected = resolveLegacyGroupSelection(migrationId, key, count, choice)
                    val selectedRow = checkNotNull(dao.migrationChunk(migrationId, selected)) { "C_ROLLBACK_DETECTED" }
                    check(selectedRow.state == "pending" && selectedRow.targetChain == manifest.targetChain) { "C_ROLLBACK_DETECTED" }
                    val selectedWork = Json.decodeFromString<PallaSyncReplayWork>(selectedRow.reportJson)
                    val selectedMetadata =
                        checkNotNull(dao.legacyMigrationCandidateByEntry(migrationId, selected)) {
                            "C_ROLLBACK_DETECTED"
                        }
                    check(
                        sourceHash(selectedWork.event) == selectedRow.sourceHash &&
                            key ==
                            legacyGroupKey(
                                selectedWork.event.schema,
                                selectedWork.event.entityId,
                                legacyCandidateDiscriminator(selectedWork, selectedMetadata, selected),
                            ),
                    ) {
                        "C_ROLLBACK_DETECTED"
                    }
                    dao.putLegacyMigrationCandidateChoice(
                        LegacyMigrationChoiceEntity(migrationId, key, selected, userConfirmed = choice?.userConfirmed == true),
                    )
                    var afterEntry = 0L
                    while (true) {
                        val members = dao.legacyMigrationCandidatesInGroup(migrationId, key, afterEntry, PAGE_SIZE)
                        if (members.isEmpty()) break
                        for (loser in members.filter { it.entryIndex != selected }) {
                            val loserRow = checkNotNull(dao.migrationChunk(migrationId, loser.entryIndex)) { "C_ROLLBACK_DETECTED" }
                            check(
                                loserRow.state == "pending" &&
                                    dao.excludeLegacyMigrationCandidate(migrationId, loser.entryIndex, loserRow.sourceHash) == 1,
                            ) { "C_ROLLBACK_DETECTED" }
                        }
                        afterEntry = members.last().entryIndex
                        if (members.size < PAGE_SIZE) break
                    }
                }
                after = keys.last()
                if (keys.size < 50) break
            }
            check(
                dao.migrationStateCount(migrationId, "pending") ==
                    manifest.candidateCount - dao.migrationStateCount(migrationId, "legacy_candidate_excluded"),
            ) {
                "C_ROLLBACK_DETECTED"
            }
            prepareLegacyV20FavoriteOrder(migrationId, manifest)
            check(
                dao.updateMigrationManifest(
                    migrationId,
                    root.sourceHash,
                    "legacy_candidates_staged",
                    "legacy_staged",
                    root.reportJson,
                ) == 1,
            ) {
                "C_LOCAL_STORAGE_FULL"
            }
        }

    /** Persist exact v2.0 incoming-first distinct merge order before any migrated favorite is authored (§9.2, v2 §5.2). */
    private suspend fun prepareLegacyV20FavoriteOrder(
        migrationId: String,
        manifest: LegacyManifest,
    ) {
        val order = manifest.legacyFavoriteOrder ?: return
        if (manifest.sourceProtocol != "2.0") return
        check(order.distinct().size == order.size) { "C_ROLLBACK_DETECTED" }
        val dao = store.protocolDao()
        val selected =
            dao.selectedLegacyMigrationFavorites(
                migrationId,
                FAVORITE_TAG_SCHEMA_V3,
                order.size,
            )
        check(selected.size == order.size && selected.map { it.entityId }.toSet() == order.toSet()) { "C_ROLLBACK_DETECTED" }
        val candidateByTag = selected.associateBy(LegacyMigrationCandidateEntity::entityId)
        val orderKeys = PallaSyncFractionalOrder.place(order, order.toSet(), emptyMap())
        for (tag in order) {
            currentCoroutineContext().ensureActive()
            val candidate = checkNotNull(candidateByTag[tag]) { "C_ROLLBACK_DETECTED" }
            val row = checkNotNull(dao.migrationChunk(migrationId, candidate.entryIndex)) { "C_ROLLBACK_DETECTED" }
            check(row.state == "pending" && row.targetChain == manifest.targetChain) { "C_ROLLBACK_DETECTED" }
            val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
            val original = work.sourceEvent ?: work.event
            check(
                work.legacyFavoriteSnapshotUnique && original.schema == FAVORITE_TAG_SCHEMA_V3 &&
                    original.entityId == tag && original.operation == "upsert" &&
                    sourceHash(original) == row.sourceHash,
            ) { "C_ROLLBACK_DETECTED" }
            val prepared =
                work.copy(
                    event = original.copy(context = buildJsonObject { put("order_key", orderKeys.getValue(tag)) }),
                    sourceEvent = original,
                    localPrepared = true,
                )
            check(
                dao.updateLegacyMigrationCandidate(
                    migrationId,
                    candidate.entryIndex,
                    row.sourceHash,
                    Json.encodeToString(prepared),
                ) == 1,
            ) { "C_LOCAL_STORAGE_FULL" }
        }
    }

    private suspend fun legacyCandidateGroup(
        migrationId: String,
        groupKey: String,
    ): LegacyCandidateGroup {
        val dao = store.protocolDao()
        val first =
            checkNotNull(dao.legacyMigrationCandidatesInGroup(migrationId, groupKey, 0, 1).firstOrNull()) {
                "C_MISSING_DEPENDENCY"
            }
        check(
            groupKey ==
                legacyGroupKey(
                    first.schema,
                    first.entityId,
                    run {
                        val row = checkNotNull(dao.migrationChunk(migrationId, first.entryIndex)) { "C_ROLLBACK_DETECTED" }
                        val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
                        legacyCandidateDiscriminator(work, first, first.entryIndex)
                    },
                ),
        ) { "C_ROLLBACK_DETECTED" }
        val choice = dao.legacyMigrationCandidateChoice(migrationId, groupKey)
        val count = dao.legacyMigrationCandidateGroupCount(migrationId, groupKey)
        check(count > 0 && (choice == null || choice.selectedEntryIndex >= 0)) { "C_ROLLBACK_DETECTED" }
        return LegacyCandidateGroup(
            groupKey,
            first.schema,
            first.entityId,
            count,
            choice?.selectedEntryIndex,
            choice?.userConfirmed == true,
        )
    }

    private fun legacyCandidateDiscriminator(
        work: PallaSyncReplayWork,
        metadata: LegacyMigrationCandidateEntity,
        entryIndex: Long,
    ): String =
        when {
            work.legacyEventSequenceOrdered -> "legacy-v21-sequence"
            metadata.requiresChoice && work.legacyFavoriteSnapshotUnique -> "collision"
            metadata.requiresChoice -> "collision:${metadata.sourceSequence}"
            work.legacyFavoriteSnapshotUnique -> "legacy-v20-favorite-distinct"
            else -> "entry:$entryIndex"
        }

    /**
     * Frozen v2.0 favorite snapshots can contain distinct raw strings that NFC maps to
     * one v3 entity across separate source records. Reconcile them after the complete
     * verified transcript is staged so no collision alternative is silently discarded.
     */
    private suspend fun reconcileLegacyFavoriteNormalizationCollisions(migrationId: String) {
        val dao = store.protocolDao()
        val firstRawIdentifier = mutableMapOf<Pair<String, String>, String>()
        val collisions = mutableSetOf<Pair<String, String>>()
        var after = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = dao.legacyCandidatePage(migrationId, after, PAGE_SIZE)
            if (rows.isEmpty()) break
            for (row in rows) {
                val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
                if (work.legacyFavoriteSnapshotUnique) {
                    val originalId = work.legacyEntityId ?: continue
                    val metadata =
                        checkNotNull(dao.legacyMigrationCandidateByEntry(migrationId, row.chunkIndex)) {
                            "C_ROLLBACK_DETECTED"
                        }
                    check(metadata.entityId == work.event.entityId && metadata.schema == work.event.schema) {
                        "C_ROLLBACK_DETECTED"
                    }
                    val entityKey = metadata.schema to metadata.entityId
                    val firstRaw = firstRawIdentifier.putIfAbsent(entityKey, originalId)
                    if (firstRaw != null && firstRaw != originalId) collisions += entityKey
                }
                after = row.chunkIndex
            }
            if (rows.size < PAGE_SIZE) break
        }
        after = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = dao.legacyCandidatePage(migrationId, after, PAGE_SIZE)
            if (rows.isEmpty()) break
            for (row in rows) {
                val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
                if (work.legacyFavoriteSnapshotUnique) {
                    val metadata =
                        checkNotNull(dao.legacyMigrationCandidateByEntry(migrationId, row.chunkIndex)) {
                            "C_ROLLBACK_DETECTED"
                        }
                    if ((metadata.schema to metadata.entityId) in collisions) {
                        val expectedGroup = metadata.groupKey
                        val collisionGroup = legacyGroupKey(metadata.schema, metadata.entityId, "collision")
                        if (expectedGroup != collisionGroup || !metadata.requiresChoice) {
                            check(
                                dao.markLegacyMigrationNormalizationCollision(
                                    migrationId,
                                    metadata.entryIndex,
                                    expectedGroup,
                                    collisionGroup,
                                    expectedRequiresChoice = metadata.requiresChoice,
                                ) == 1,
                            ) { "C_ROLLBACK_DETECTED" }
                        }
                    }
                }
                after = row.chunkIndex
            }
            if (rows.size < PAGE_SIZE) break
        }
    }

    private suspend fun resolveLegacyGroupSelection(
        migrationId: String,
        groupKey: String,
        count: Long,
        choice: LegacyMigrationChoiceEntity?,
    ): Long {
        val dao = store.protocolDao()
        var after = 0L
        var sequenceOrdered = false
        var favoriteDistinct = false
        var unsequenced = false
        var selectedSequence = Long.MIN_VALUE
        var selectedCandidateIndex = Int.MIN_VALUE
        var selectedEntry = Long.MIN_VALUE
        var favoriteSequence = Long.MIN_VALUE
        var favoriteCandidateIndex = Int.MIN_VALUE
        var favoriteEntry = Long.MIN_VALUE
        var userChoiceRequired = false
        while (true) {
            currentCoroutineContext().ensureActive()
            val rows = dao.legacyMigrationCandidatesInGroup(migrationId, groupKey, after, PAGE_SIZE)
            if (rows.isEmpty()) break
            for (candidate in rows) {
                val metadata =
                    checkNotNull(dao.legacyMigrationCandidateByEntry(migrationId, candidate.entryIndex)) {
                        "C_ROLLBACK_DETECTED"
                    }
                userChoiceRequired = userChoiceRequired || metadata.requiresChoice
                val row = checkNotNull(dao.migrationChunk(migrationId, candidate.entryIndex)) { "C_ROLLBACK_DETECTED" }
                val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
                if (!work.legacyEventSequenceOrdered) {
                    if (work.legacyFavoriteSnapshotUnique) {
                        favoriteDistinct = true
                        val later =
                            when {
                                metadata.sourceSequence > favoriteSequence -> true
                                metadata.sourceSequence < favoriteSequence -> false
                                metadata.candidateIndex > favoriteCandidateIndex -> true
                                metadata.candidateIndex < favoriteCandidateIndex -> false
                                else -> candidate.entryIndex > favoriteEntry
                            }
                        if (later) {
                            favoriteSequence = metadata.sourceSequence
                            favoriteCandidateIndex = metadata.candidateIndex
                            favoriteEntry = candidate.entryIndex
                        }
                    } else {
                        unsequenced = true
                    }
                    continue
                }
                sequenceOrdered = true
                if (metadata.sourceSequence > selectedSequence ||
                    (metadata.sourceSequence == selectedSequence && metadata.candidateIndex > selectedCandidateIndex) ||
                    (
                        metadata.sourceSequence == selectedSequence && metadata.candidateIndex == selectedCandidateIndex &&
                            candidate.entryIndex > selectedEntry
                    )
                ) {
                    selectedSequence = metadata.sourceSequence
                    selectedCandidateIndex = metadata.candidateIndex
                    selectedEntry = candidate.entryIndex
                }
            }
            after = rows.last().entryIndex
            if (rows.size < PAGE_SIZE) break
        }
        check(!(sequenceOrdered && (unsequenced || favoriteDistinct)) && !(favoriteDistinct && unsequenced)) {
            "C_ROLLBACK_DETECTED"
        }
        val selected =
            if (sequenceOrdered) {
                check(selectedEntry >= 0 && choice?.userConfirmed != true) { "C_ROLLBACK_DETECTED" }
                selectedEntry
            } else if (userChoiceRequired) {
                check(
                    choice != null && choice.userConfirmed &&
                        dao.legacyMigrationCandidate(migrationId, groupKey, choice.selectedEntryIndex) != null,
                ) { "C_MISSING_DEPENDENCY" }
                choice.selectedEntryIndex
            } else if (favoriteDistinct) {
                check(favoriteEntry >= 0 && choice?.userConfirmed != true) { "C_ROLLBACK_DETECTED" }
                favoriteEntry
            } else {
                when {
                    count == 1L -> dao.legacyMigrationCandidatesInGroup(migrationId, groupKey, 0, 1).single().entryIndex

                    choice?.userConfirmed == true &&
                        dao.legacyMigrationCandidate(migrationId, groupKey, choice.selectedEntryIndex) != null -> choice.selectedEntryIndex

                    else -> error("C_MISSING_DEPENDENCY")
                }
            }
        check(dao.legacyMigrationCandidate(migrationId, groupKey, selected) != null) { "C_ROLLBACK_DETECTED" }
        return selected
    }

    private fun sourceEvidenceJournalId(migrationId: String): String = "$migrationId:source"

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
                val orderingIntent =
                    if (
                        row.migrationId.startsWith("legacy:") && original.schema == FAVORITE_TAG_SCHEMA_V3 &&
                        original.operation == "upsert"
                    ) {
                        original.copy(favoriteOrder = listOf(original.entityId))
                    } else {
                        original
                    }
                val ordered = order(chain, listOf(orderingIntent)).single()
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
