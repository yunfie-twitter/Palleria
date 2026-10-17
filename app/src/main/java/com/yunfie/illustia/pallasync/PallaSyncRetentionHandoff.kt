package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.SnapshotPublicationEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** §13.5: a locally published, receipt-verified complete current-epoch state is the handoff proof. */
internal class PallaSyncRetentionHandoff(
    private val store: PallaSyncLocalStore,
    private val expireEpochSecrets: (String, Collection<Long>) -> Boolean = { _, _ -> false },
) {
    @Suppress("ReturnCount")
    suspend fun eligibleEpochs(
        chain: String,
        authority: JsonObject,
        nowMs: Long,
    ): List<Long> {
        val epochs = currentGenerationEpochs(authority)
        val policy =
            authority
                .getValue("parameters")
                .jsonObject
                .getValue("policy")
                .jsonObject
                .getValue("epoch_retention")
                .jsonPrimitive.content
        val strict = PallaSyncPeerEpochEligibility.eligibleEpochs(authority, nowMs, handoffVerified = true)
        if (policy == "retain") return strict
        if (policy == "immediate" && epochs.size == 1) return strict
        val withoutProof =
            if (policy == "immediate") {
                emptyList()
            } else {
                PallaSyncPeerEpochEligibility.eligibleEpochs(authority, nowMs)
            }
        if (policy == "windowed" && withoutProof == strict) return withoutProof
        val handoff = hasVerifiedCurrentEpochSnapshot(chain, authority)
        return PallaSyncPeerEpochEligibility
            .eligibleEpochs(authority, nowMs, handoffVerified = handoff)
            .also { result ->
                check(result.isNotEmpty() && result.last() == epochs.last()) { "C_MISSING_DEPENDENCY" }
            }
    }

    /** Erases expired cache keys only after checking current signed head, complete parts and acceptance log. */
    @Suppress("ReturnCount")
    suspend fun expireAfterHandoff(
        chain: String,
        authority: JsonObject,
        nowMs: Long,
    ): List<Long> {
        if (!hasVerifiedCurrentEpochSnapshot(chain, authority)) return emptyList()
        val eligible = PallaSyncPeerEpochEligibility.eligibleEpochs(authority, nowMs, handoffVerified = true).toSet()
        val expired = currentGenerationEpochs(authority).filterNot { it in eligible }
        if (expired.isEmpty()) return emptyList()
        check(hasVerifiedCurrentEpochSnapshot(chain, authority)) { "C_MISSING_DEPENDENCY" }
        check(expireEpochSecrets(chain, expired)) { "C_LOCAL_STORAGE_FULL" }
        return expired
    }

    /** Non-destructive proof lookup used by §16.7 pruning; the covered prefix may precede the current head. */
    internal suspend fun latestVerifiedPublishedCompaction(
        chain: String,
        authority: JsonObject,
    ): JsonObject? {
        val state = store.dao.getChainState(chain) ?: return null
        val epochs = currentGenerationEpochs(authority)
        val latest =
            store.snapshotDatabase().snapshotPublicationDao().latestPublishedForEpoch(
                chain,
                state.generation,
                epochs.lastOrNull() ?: return null,
            ) ?: return null
        return verifiedPublishedCompaction(chain, authority, latest.coveredThroughSeq)
    }

    internal suspend fun verifiedPublishedCompaction(
        chain: String,
        authority: JsonObject,
        coveredThroughSeq: Long,
    ): JsonObject? {
        val state = store.dao.getChainState(chain) ?: return null
        val generation = pallaSyncAuthorityGeneration(authority)
        val epochs = currentGenerationEpochs(authority)
        val currentEpoch = epochs.lastOrNull() ?: return null
        val epochRecord =
            authority
                .getValue("epochs")
                .jsonObject
                .getValue(currentEpoch.toString())
                .jsonObject
                .getValue("record")
                .jsonObject
        val epochHash = PallaSyncCore.objectHash(epochRecord.toString()) ?: return null
        val pin = authority.getValue("pin").jsonObject
        if (
            state.writeState != "active" || state.generation != generation || state.keyEpoch != currentEpoch ||
            state.epochHash != epochHash || state.logId != pin.getValue("log_id").jsonPrimitive.content ||
            state.verifiedSeq < coveredThroughSeq || state.verifiedSeq != state.materializedSeq || state.verifiedHash == null ||
            store.dao.pendingOutboxCount(chain) != 0L ||
            store.snapshotInstallDao().unresolvedQuarantineCount(chain) != 0L ||
            store.protocolDao().unresolvedAuthorGapCount(chain) != 0L
        ) {
            return null
        }
        val publication =
            store
                .snapshotDatabase()
                .snapshotPublicationDao()
                .latestPublishedForEpoch(chain, generation, currentEpoch) ?: return null
        if (publication.coveredThroughSeq != coveredThroughSeq ||
            !publicationCoversVerifiedPrefix(publication, epochHash, state)
        ) {
            return null
        }
        if (!verifyPublishedCompaction(publication, state.logId, state.verifiedSeq)) return null
        return publication.compactionJson?.let { Json.parseToJsonElement(it).jsonObject }
    }

    /** Resolve a signed transparency prune hash to its exact locally published snapshot proof. */
    internal suspend fun publishedCompactionForPruneEntry(
        chain: String,
        authority: JsonObject,
        entry: JsonObject,
    ): JsonObject? {
        if (entry["operation"]?.jsonPrimitive?.content != "prune") return null
        val state = store.dao.getChainState(chain) ?: return null
        val generation = pallaSyncAuthorityGeneration(authority)
        val logId =
            authority
                .getValue("pin")
                .jsonObject
                .getValue("log_id")
                .jsonPrimitive.content
        if (state.chainId != chain || state.generation != generation || state.logId != logId) return null
        val expectedHash = entry.getValue("object_hash").jsonPrimitive.content
        val dao = store.snapshotDatabase().snapshotPublicationDao()
        var afterCoverage = -1L
        var afterCompaction = ""
        var selected: JsonObject? = null
        while (true) {
            val page = dao.publishedPage(chain, generation, afterCoverage, afterCompaction, PUBLICATION_PAGE_SIZE)
            if (page.isEmpty()) break
            for (publication in page) {
                afterCoverage = publication.coveredThroughSeq
                afterCompaction = publication.compactionId
                if (publication.logId != logId || publication.coveredThroughSeq > state.verifiedSeq) continue
                val compaction = publication.compactionJson?.let { Json.parseToJsonElement(it).jsonObject } ?: continue
                val target =
                    buildJsonObject {
                        put("compaction_id", publication.compactionId)
                        put("through_seq", publication.coveredThroughSeq)
                    }
                if (PallaSyncCore.objectHash(target.toString()) != expectedHash) continue
                check(selected == null) { "C_TRANSPARENCY_INVALID" }
                val epoch =
                    authority
                        .getValue("epochs")
                        .jsonObject[publication.epoch.toString()]
                        ?.jsonObject
                        ?.getValue("record")
                        ?.jsonObject ?: continue
                val epochHash = PallaSyncCore.objectHash(epoch.toString()) ?: continue
                val header = Json.parseToJsonElement(publication.headerJson).jsonObject
                if (
                    publication.status != "published" || publication.chainId != chain ||
                    publication.generation != generation || publication.stateSha256 != compaction["state_sha256"]?.jsonPrimitive?.content ||
                    compaction["compaction_id"]?.jsonPrimitive?.content != publication.compactionId ||
                    compaction["chain_id"]?.jsonPrimitive?.content != chain ||
                    compaction["generation"]?.jsonPrimitive?.long != generation ||
                    compaction["log_id"]?.jsonPrimitive?.content != logId ||
                    compaction["epoch_hash"]?.jsonPrimitive?.content != epochHash ||
                    compaction["covered_through_seq"]?.jsonPrimitive?.long != publication.coveredThroughSeq ||
                    header["kind"]?.jsonPrimitive?.content != "header" ||
                    header["chain_id"]?.jsonPrimitive?.content != chain ||
                    header["generation"]?.jsonPrimitive?.long != generation ||
                    header["log_id"]?.jsonPrimitive?.content != logId ||
                    header["epoch_hash"]?.jsonPrimitive?.content != epochHash ||
                    header["covered_through_seq"]?.jsonPrimitive?.long != publication.coveredThroughSeq ||
                    header["covered_log_hash"] != compaction["covered_log_hash"]
                ) {
                    continue
                }
                if (!verifyPublishedCompaction(publication, logId, state.verifiedSeq)) continue
                selected = compaction
            }
            if (page.size < PUBLICATION_PAGE_SIZE) break
        }
        return selected
    }

    private fun publicationCoversVerifiedPrefix(
        publication: SnapshotPublicationEntity,
        epochHash: String,
        state: com.yunfie.illustia.pallasync.data.ChainStateEntity,
    ): Boolean {
        val header = Json.parseToJsonElement(publication.headerJson).jsonObject
        val compaction = publication.compactionJson?.let { Json.parseToJsonElement(it).jsonObject } ?: return false
        return publication.status == "published" && publication.chainId == state.chainId &&
            publication.generation == state.generation && publication.epoch == state.keyEpoch &&
            publication.logId == state.logId && publication.coveredThroughSeq <= state.verifiedSeq &&
            publication.stateSha256 == compaction.getValue("state_sha256").jsonPrimitive.content &&
            header.getValue("kind").jsonPrimitive.content == "header" &&
            header.getValue("chain_id").jsonPrimitive.content == state.chainId &&
            header.getValue("generation").jsonPrimitive.long == state.generation &&
            header.getValue("log_id").jsonPrimitive.content == state.logId &&
            header.getValue("epoch_hash").jsonPrimitive.content == epochHash &&
            header.getValue("covered_through_seq").jsonPrimitive.long == publication.coveredThroughSeq &&
            compaction.getValue("compaction_id").jsonPrimitive.content == publication.compactionId &&
            compaction.getValue("chain_id").jsonPrimitive.content == state.chainId &&
            compaction.getValue("generation").jsonPrimitive.long == state.generation &&
            compaction.getValue("log_id").jsonPrimitive.content == state.logId &&
            compaction.getValue("epoch_hash").jsonPrimitive.content == epochHash &&
            compaction.getValue("covered_through_seq").jsonPrimitive.long == publication.coveredThroughSeq &&
            compaction.getValue("covered_log_hash").jsonPrimitive.content ==
            header.getValue("covered_log_hash").jsonPrimitive.content
    }

    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "ReturnCount")
    private suspend fun hasVerifiedCurrentEpochSnapshot(
        chain: String,
        authority: JsonObject,
    ): Boolean {
        val state = store.dao.getChainState(chain) ?: return false
        return verifiedPublishedCompaction(chain, authority, state.verifiedSeq) != null
    }

    private fun currentGenerationEpochs(authority: JsonObject): List<Long> {
        val generation = pallaSyncAuthorityGeneration(authority)
        val values = authority.getValue("epochs").jsonObject
        val numbers = values.keys.map { it.toLongOrNull() ?: error("C_SCHEMA_VIOLATION") }.sorted()
        val currentGeneration =
            numbers.filter { epoch ->
                values
                    .getValue(epoch.toString())
                    .jsonObject
                    .getValue("record")
                    .jsonObject
                    .getValue("generation")
                    .jsonPrimitive.long ==
                    generation
            }
        check(currentGeneration.isNotEmpty() && currentGeneration.zipWithNext().all { (a, b) -> b == a + 1 }) {
            "C_MISSING_DEPENDENCY"
        }
        return currentGeneration
    }

    @Suppress("CyclomaticComplexMethod")
    private fun publicationMatchesHead(
        publication: SnapshotPublicationEntity,
        epochHash: String,
        state: com.yunfie.illustia.pallasync.data.ChainStateEntity,
    ): Boolean {
        val header = Json.parseToJsonElement(publication.headerJson).jsonObject
        val compaction = publication.compactionJson?.let { Json.parseToJsonElement(it).jsonObject } ?: return false
        return publication.status == "published" && publication.chainId == state.chainId &&
            publication.generation == state.generation && publication.epoch == state.keyEpoch &&
            publication.logId == state.logId && publication.coveredThroughSeq == state.verifiedSeq &&
            publication.stateSha256 == compaction.getValue("state_sha256").jsonPrimitive.content &&
            header.getValue("kind").jsonPrimitive.content == "header" &&
            header.getValue("chain_id").jsonPrimitive.content == state.chainId &&
            header.getValue("generation").jsonPrimitive.long == state.generation &&
            header.getValue("log_id").jsonPrimitive.content == state.logId &&
            header.getValue("epoch_hash").jsonPrimitive.content == epochHash &&
            header.getValue("covered_through_seq").jsonPrimitive.long == publication.coveredThroughSeq &&
            header.getValue("covered_log_hash").jsonPrimitive.content == state.verifiedHash &&
            compaction.getValue("compaction_id").jsonPrimitive.content == publication.compactionId &&
            compaction.getValue("chain_id").jsonPrimitive.content == state.chainId &&
            compaction.getValue("generation").jsonPrimitive.long == state.generation &&
            compaction.getValue("log_id").jsonPrimitive.content == state.logId &&
            compaction.getValue("epoch_hash").jsonPrimitive.content == epochHash &&
            compaction.getValue("covered_through_seq").jsonPrimitive.long == publication.coveredThroughSeq &&
            compaction.getValue("covered_log_hash").jsonPrimitive.content == header.getValue("covered_log_hash").jsonPrimitive.content
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun verifyPublishedCompaction(
        publication: SnapshotPublicationEntity,
        logId: String,
        verifiedSequence: Long,
    ): Boolean {
        val compaction = Json.parseToJsonElement(checkNotNull(publication.compactionJson)).jsonObject
        val entries =
            store
                .compactionCatalogueDao()
                .verifiedEntries(publication.chainId, logId, publication.compactionId)
        val accepted = entries.singleOrNull() ?: return false
        if (accepted.relaySeq > verifiedSequence) return false
        val acceptedObject = Json.parseToJsonElement(accepted.signedJson).jsonObject
        if (
            accepted.validationState != "authority_verified" ||
            acceptedObject.getValue("kind").jsonPrimitive.content != "compaction" ||
            PallaSyncCore.canonicalJson(acceptedObject.getValue("object").toString()) !=
            PallaSyncCore.canonicalJson(compaction.toString())
        ) {
            return false
        }
        val parts = store.snapshotDatabase().snapshotPublicationDao().parts(publication.chainId, publication.compactionId)
        val references = compaction.getValue("snapshot_parts").jsonArray
        if (parts.size != publication.partCount || references.size != publication.partCount) return false
        return parts.indices.all { index ->
            val part = parts[index]
            val reference = references[index].jsonObject
            val signedPart = Json.parseToJsonElement(part.signedJson).jsonObject
            val receipt = part.uploadReceiptJson?.let { Json.parseToJsonElement(it).jsonObject } ?: return@all false
            val calculatedHash = PallaSyncCore.objectHash(part.signedJson)
            part.partIndex == index && part.status == "receipted" && part.recordId ==
                signedPart
                    .getValue(
                        "record_id",
                    ).jsonPrimitive.content &&
                part.recordHash == calculatedHash && reference.getValue("record_id").jsonPrimitive.content == part.recordId &&
                reference.getValue("record_hash").jsonPrimitive.content == part.recordHash &&
                receipt.getValue("chain_id").jsonPrimitive.content == publication.chainId &&
                receipt.getValue("log_id").jsonPrimitive.content == logId &&
                receipt.getValue("object_hash").jsonPrimitive.content == part.recordHash &&
                receipt["relay_seq"] == JsonNull && receipt["entry_hash"] == JsonNull
        }
    }

    private companion object {
        const val PUBLICATION_PAGE_SIZE = 64
    }
}
