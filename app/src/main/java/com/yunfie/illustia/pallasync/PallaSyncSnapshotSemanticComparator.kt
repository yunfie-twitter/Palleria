package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.EntityRegisterEntity
import com.yunfie.illustia.pallasync.data.ScopeRegisterEntity
import com.yunfie.illustia.pallasync.data.SnapshotInstallDao
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Compares the complete verified snapshot stream with an independently verified local replay (§16.6). */
internal object PallaSyncSnapshotSemanticComparator {
    private const val PAGE_SIZE = 200

    suspend fun compareWithExactLocalReplay(
        store: PallaSyncLocalStore,
        chainId: String,
        compactionId: String,
        logId: String,
        generation: Long,
        epochHash: String,
        coveredSeq: Long,
        coveredHash: String,
        onMismatch: (String) -> Unit = {},
    ): Long? {
        val local = store.dao.getChainState(chainId)
        val matches =
            local != null &&
                listOf(
                    local.logId == logId,
                    local.generation == generation,
                    local.epochHash == epochHash,
                    local.verifiedSeq >= coveredSeq,
                    local.materializedSeq == local.verifiedSeq,
                ).all { it }
        if (!matches) return null
        if (!verifyControlOnlySuffix(store, chainId, logId, coveredSeq, coveredHash, local.verifiedSeq, local.verifiedHash ?: return null)) {
            return null
        }
        val reader = PallaSyncSnapshotStateReader(store.snapshotDatabase())
        compare(
            store.snapshotInstallDao(),
            chainId,
            compactionId,
            onMismatch = onMismatch,
            allowVerifiedControlSuffixHeaderDifference = coveredSeq < local.verifiedSeq,
        ) { emit -> reader.stream(chainId) { entry -> emit(entry.toString()) } }
        return local.indexRevision
    }

    /** A later verified state is an exact replay of this prefix only when the suffix changes no data. */
    private suspend fun verifyControlOnlySuffix(
        store: PallaSyncLocalStore,
        chainId: String,
        logId: String,
        coveredSeq: Long,
        coveredHash: String,
        throughSeq: Long,
        expectedHeadHash: String,
    ): Boolean {
        if (coveredSeq > throughSeq) return false
        if (coveredSeq == throughSeq) return coveredHash == expectedHeadHash
        val authorityText = store.protocolDao().currentAuthority(chainId) ?: return false
        val pin = Json.parseToJsonElement(authorityText).jsonObject.getValue("pin").toString()
        var sequence = coveredSeq
        var previousHash = coveredHash
        while (sequence < throughSeq) {
            val page = store.protocolDao().logPage(chainId, logId, sequence, throughSeq, PAGE_SIZE)
            if (page.isEmpty()) return false
            for (row in page) {
                if (row.relaySeq != sequence + 1L || row.validationState !in setOf("authority_verified", "materialized")) {
                    return false
                }
                val entry = Json.parseToJsonElement(row.signedJson).jsonObject
                if (entry.getValue("kind").jsonPrimitive.content == "record") return false
                previousHash =
                    runCatching {
                        Json.parseToJsonElement(
                            PallaSyncCore.validateRetainedLogLink(row.signedJson, row.receiptJson, pin, sequence, previousHash),
                        ).jsonPrimitive.content
                    }.getOrNull() ?: return false
                sequence = row.relaySeq
            }
        }
        return sequence == throughSeq && previousHash == expectedHeadHash
    }

    /** Compare a fully receipt-confirmed new-root target to its verified source logical state (§13.8). */
    suspend fun compareNewRootMaterializedState(
        store: PallaSyncLocalStore,
        sourceChain: String,
        targetChain: String,
        checkpoint: NewRootMigrationCheckpoint,
        receipts: NewRootMigrationReceiptStatus,
    ): Boolean {
        check(
            checkpoint.complete && receipts.complete && receipts.staged == checkpoint.stagedCount &&
                receipts.receiptVerified == checkpoint.stagedCount && checkpoint.anchor.chainId == sourceChain &&
                sourceChain != targetChain,
        ) { "C_ACCEPTANCE_UNPROVEN" }
        val database = store.snapshotDatabase()
        val chainDao = database.pallaSyncDao()
        val sourceBefore = checkNotNull(chainDao.getChainState(sourceChain)) { "C_MISSING_DEPENDENCY" }
        val targetBefore = checkNotNull(chainDao.getChainState(targetChain)) { "C_MISSING_DEPENDENCY" }
        check(sourceBefore.logicalAnchor(sourceChain) == checkpoint.anchor) { "C_ROLLBACK_DETECTED" }
        check(
            targetBefore.generation == checkpoint.targetGeneration && targetBefore.initialPullCompleted &&
                targetBefore.verifiedSeq == targetBefore.materializedSeq && targetBefore.verifiedHash != null,
        ) { "C_ACCEPTANCE_UNPROVEN" }
        requireNoNewRootMigrationGaps(store, sourceChain)
        requireNoNewRootMigrationGaps(store, targetChain)
        check(chainDao.pendingOutboxCount(sourceChain) == 0L && chainDao.pendingOutboxCount(targetChain) == 0L) {
            "C_ACCEPTANCE_UNPROVEN"
        }

        val stateDao = database.snapshotStateDao()
        val entitiesMatch = compareEntities(stateDao, sourceChain, targetChain)
        val registersMatch =
            compareRegisters(stateDao, sourceChain, targetChain, "clear") &&
                compareRegisters(stateDao, sourceChain, targetChain, "retention")
        val sourceAfter = checkNotNull(chainDao.getChainState(sourceChain)) { "C_ROLLBACK_DETECTED" }
        val targetAfter = checkNotNull(chainDao.getChainState(targetChain)) { "C_ROLLBACK_DETECTED" }
        check(
            sourceAfter.logicalAnchor(sourceChain) == checkpoint.anchor &&
                targetAfter.logicalAnchor(targetChain) == targetBefore.logicalAnchor(targetChain),
        ) { "C_ROLLBACK_DETECTED" }
        requireNoNewRootMigrationGaps(store, sourceChain)
        requireNoNewRootMigrationGaps(store, targetChain)
        check(chainDao.pendingOutboxCount(sourceChain) == 0L && chainDao.pendingOutboxCount(targetChain) == 0L) {
            "C_ACCEPTANCE_UNPROVEN"
        }
        return entitiesMatch && registersMatch
    }

    private suspend fun compareEntities(
        dao: com.yunfie.illustia.pallasync.data.SnapshotStateDao,
        source: String,
        target: String,
    ): Boolean {
        var sourceCollection = ""
        var sourceEntity = ""
        var targetCollection = ""
        var targetEntity = ""
        while (true) {
            val sourcePage = dao.entityPage(source, sourceCollection, sourceEntity, PAGE_SIZE)
            val targetPage = dao.entityPage(target, targetCollection, targetEntity, PAGE_SIZE)
            if (sourcePage.size != targetPage.size) return false
            if (sourcePage.indices.any { !sameEntityState(sourcePage[it], targetPage[it]) }) return false
            val sourceTail = sourcePage.lastOrNull()
            val targetTail = targetPage.lastOrNull()
            if (sourceTail == null || targetTail == null) return sourceTail == null && targetTail == null
            sourceCollection = sourceTail.collection
            sourceEntity = sourceTail.entityId
            targetCollection = targetTail.collection
            targetEntity = targetTail.entityId
            if (sourcePage.size < PAGE_SIZE) return true
        }
    }

    private suspend fun compareRegisters(
        dao: com.yunfie.illustia.pallasync.data.SnapshotStateDao,
        source: String,
        target: String,
        kind: String,
    ): Boolean {
        var sourceCollection = ""
        var sourceScope = ""
        var targetCollection = ""
        var targetScope = ""
        while (true) {
            val sourcePage = dao.registerPage(source, kind, sourceCollection, sourceScope, PAGE_SIZE)
            val targetPage = dao.registerPage(target, kind, targetCollection, targetScope, PAGE_SIZE)
            if (sourcePage.size != targetPage.size) return false
            if (sourcePage.indices.any { !sameRegisterState(sourcePage[it], targetPage[it]) }) return false
            val sourceTail = sourcePage.lastOrNull()
            val targetTail = targetPage.lastOrNull()
            if (sourceTail == null || targetTail == null) return sourceTail == null && targetTail == null
            sourceCollection = sourceTail.collection
            sourceScope = sourceTail.scope
            targetCollection = targetTail.collection
            targetScope = targetTail.scope
            if (sourcePage.size < PAGE_SIZE) return true
        }
    }

    private fun sameEntityState(
        source: EntityRegisterEntity,
        target: EntityRegisterEntity,
    ): Boolean =
        source.collection == target.collection && source.entityId == target.entityId &&
            source.operation == target.operation && source.scope == target.scope &&
            source.orderKey == target.orderKey && canonical(source.bodyJson) == canonical(target.bodyJson) &&
            canonical(source.contextJson) == canonical(target.contextJson)

    private fun sameRegisterState(
        source: ScopeRegisterEntity,
        target: ScopeRegisterEntity,
    ): Boolean =
        source.collection == target.collection && source.scope == target.scope &&
            source.registerKind == target.registerKind && canonical(source.bodyJson) == canonical(target.bodyJson)

    private fun canonical(json: String): String = checkNotNull(PallaSyncCore.canonicalJson(json)) { "C_SNAPSHOT_INVALID" }

    private suspend fun requireNoNewRootMigrationGaps(
        store: PallaSyncLocalStore,
        chain: String,
    ) {
        check(
            store.protocolDao().unresolvedQuarantineCount(chain) == 0L &&
                store.snapshotDatabase().snapshotStateDao().unresolvedSnapshotAuthorGapCount(chain) == 0L &&
                store.snapshotDatabase().snapshotStateDao().unresolvedAuthorGapCount(chain) == 0L,
        ) { "C_SNAPSHOT_INVALID" }
    }

    private fun com.yunfie.illustia.pallasync.data.ChainStateEntity.logicalAnchor(chainId: String) =
        NewRootMigrationSourceAnchor(
            chainId,
            generation,
            checkNotNull(logId) { "C_SNAPSHOT_INVALID" },
            checkNotNull(epochHash) { "C_SNAPSHOT_INVALID" },
            verifiedSeq,
            checkNotNull(verifiedHash) { "C_SNAPSHOT_INVALID" },
            indexRevision,
        )

    suspend fun compare(
        dao: SnapshotInstallDao,
        chainId: String,
        compactionId: String,
        onMismatch: (String) -> Unit = {},
        allowVerifiedControlSuffixHeaderDifference: Boolean = false,
        emitLocalState: suspend (suspend (String) -> Unit) -> Unit,
    ) {
        check(dao.install(chainId, compactionId)?.status in setOf("verified", "installed")) { "C_SNAPSHOT_INVALID" }
        var part = -1
        var entry = -1
        var page = emptyList<com.yunfie.illustia.pallasync.data.SnapshotInstallEntryEntity>()
        var pageIndex = 0

        suspend fun nextSnapshotEntry(): String? {
            if (pageIndex == page.size) {
                page = dao.entryPage(chainId, compactionId, part, entry, PAGE_SIZE)
                pageIndex = 0
            }
            val row = page.getOrNull(pageIndex++) ?: return null
            part = row.partIndex
            entry = row.entryIndex
            return row.entryJson
        }

        emitLocalState { localEntry ->
            val canonical = checkNotNull(PallaSyncCore.canonicalJson(localEntry)) { "C_SNAPSHOT_INVALID" }
            val archived = nextSnapshotEntry()
            val equivalentHeader =
                allowVerifiedControlSuffixHeaderDifference && part == 0 && entry == 0 && archived != null &&
                    sameSnapshotHeaderExceptCoverage(archived, canonical)
            if (archived != canonical && !equivalentHeader) {
                onMismatch("Snapshot/local semantic entry differs at part=$part entry=$entry")
                error("C_SNAPSHOT_INVALID")
            }
        }
        check(nextSnapshotEntry() == null) { "C_SNAPSHOT_INVALID" }
    }

    private fun sameSnapshotHeaderExceptCoverage(
        archived: String,
        local: String,
    ): Boolean =
        runCatching {
            val archivedHeader = Json.parseToJsonElement(archived).jsonObject
            val localHeader = Json.parseToJsonElement(local).jsonObject
            if (archivedHeader["kind"]?.jsonPrimitive?.content != "header" ||
                localHeader["kind"]?.jsonPrimitive?.content != "header"
            ) {
                false
            } else {
                val archivedLogical = archivedHeader.toMutableMap().apply {
                    remove("covered_through_seq")
                    remove("covered_log_hash")
                }
                val localLogical = localHeader.toMutableMap().apply {
                    remove("covered_through_seq")
                    remove("covered_log_hash")
                }
                JsonObject(archivedLogical) == JsonObject(localLogical)
            }
        }.getOrDefault(false)
}
