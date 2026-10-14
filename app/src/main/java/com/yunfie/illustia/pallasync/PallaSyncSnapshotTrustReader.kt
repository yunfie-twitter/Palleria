package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.LogEntryEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

internal data class PallaSyncTrustedSnapshotCandidate(
    val chainId: String,
    val logId: String,
    val generation: Long,
    val compactionId: String,
    val compactionHash: String,
    val coveredThroughSeq: Long,
    val coveredLogHash: String,
    val stateSha256: String,
    val epochHash: String,
    val authorDeviceId: String,
    val authorPublicKey: String,
    val partCount: Int,
)

/** Reads only compactions already authenticated into the current authority log. */
internal class PallaSyncSnapshotTrustReader(
    private val store: PallaSyncLocalStore,
    private val chain: String,
) {
    suspend fun candidates(
        authority: JsonObject,
        deviceKeys: PallaSyncKeySnapshot,
        keystore: PallaSyncKeystore,
    ): List<PallaSyncTrustedSnapshotCandidate> {
        val logId =
            authority
                .getValue("pin")
                .jsonObject
                .getValue("log_id")
                .jsonPrimitive.content
        val generation = authority.getValue("generation").jsonPrimitive.long
        val headSeq = authority.getValue("sequence").jsonPrimitive.long
        val currentEpoch =
            authority
                .getValue("epochs")
                .jsonObject
                .values
                .maxBy {
                    it.jsonObject
                        .getValue("record")
                        .jsonObject
                        .getValue("epoch")
                        .jsonPrimitive.long
                }.jsonObject
                .getValue("record")
        val epochHash = checkNotNull(PallaSyncCore.objectHash(currentEpoch.toString())) { "C_SCHEMA_VIOLATION" }
        return store
            .compactionCatalogueDao()
            .verifiedSnapshotsForEpoch(chain, logId, generation, epochHash)
            .mapNotNull { row -> candidateForRow(row, authority, logId, generation, headSeq, epochHash, deviceKeys, keystore) }
    }

    private suspend fun candidateForRow(
        row: LogEntryEntity,
        authority: JsonObject,
        logId: String,
        generation: Long,
        headSeq: Long,
        epochHash: String,
        deviceKeys: PallaSyncKeySnapshot,
        keystore: PallaSyncKeystore,
    ): PallaSyncTrustedSnapshotCandidate? {
        val entry = Json.parseToJsonElement(row.signedJson).jsonObject
        val compaction = entry.getValue("object").jsonObject
        val compactionId = compaction.getValue("compaction_id").jsonPrimitive.content
        val coverage = compaction.getValue("covered_through_seq").jsonPrimitive.long
        if (coverage > headSeq || !hasEpochKey(authority, epochHash, deviceKeys, keystore)) return null
        validateCandidateRow(entry, compaction, logId, generation, epochHash)
        requireCatalogueMatch(compaction, logId, compactionId)
        val authorId = compaction.getValue("device_id").jsonPrimitive.content
        val author = authorizedAuthor(authority, authorId, generation)
        return PallaSyncTrustedSnapshotCandidate(
            chainId = chain,
            logId = logId,
            generation = generation,
            compactionId = compactionId,
            compactionHash = checkNotNull(PallaSyncCore.objectHash(compaction.toString())),
            coveredThroughSeq = coverage,
            coveredLogHash = compaction.getValue("covered_log_hash").jsonPrimitive.content,
            stateSha256 = compaction.getValue("state_sha256").jsonPrimitive.content,
            epochHash = epochHash,
            authorDeviceId = authorId,
            authorPublicKey = author.getValue("device_public_key").jsonPrimitive.content,
            partCount = compaction.getValue("snapshot_parts").jsonArray.size,
        )
    }

    private fun validateCandidateRow(
        entry: JsonObject,
        compaction: JsonObject,
        logId: String,
        generation: Long,
        epochHash: String,
    ) {
        check(
            entry.getValue("kind").jsonPrimitive.content == "compaction" &&
                compaction.getValue("chain_id").jsonPrimitive.content == chain &&
                compaction.getValue("log_id").jsonPrimitive.content == logId &&
                compaction.getValue("generation").jsonPrimitive.long == generation &&
                compaction.getValue("epoch_hash").jsonPrimitive.content == epochHash,
        ) { "C_LOG_HASH_MISMATCH" }
    }

    private suspend fun requireCatalogueMatch(
        compaction: JsonObject,
        logId: String,
        compactionId: String,
    ) {
        val accepted = store.compactionCatalogueDao().verifiedEntries(chain, logId, compactionId)
        check(accepted.size == 1) { "C_ACCEPTANCE_UNPROVEN" }
        val acceptedObject = Json.parseToJsonElement(accepted.single().signedJson).jsonObject.getValue("object")
        check(
            PallaSyncCore.canonicalJson(acceptedObject.toString()) ==
                PallaSyncCore.canonicalJson(compaction.toString()),
        ) { "C_LOG_HASH_MISMATCH" }
    }

    private fun authorizedAuthor(
        authority: JsonObject,
        authorId: String,
        generation: Long,
    ): JsonObject {
        val author =
            authority
                .getValue("devices")
                .jsonObject[authorId]
                ?.jsonObject
                ?.getValue("record")
                ?.jsonObject ?: error("C_MISSING_DEPENDENCY")
        check(author.getValue("generation").jsonPrimitive.long == generation) { "C_ACCEPTANCE_UNPROVEN" }
        return author
    }

    private fun hasEpochKey(
        authority: JsonObject,
        epochHash: String,
        deviceKeys: PallaSyncKeySnapshot,
        keystore: PallaSyncKeystore,
    ): Boolean {
        val epochNumber =
            authority
                .getValue("epochs")
                .jsonObject
                .entries
                .singleOrNull { (_, entry) ->
                    PallaSyncCore.objectHash(entry.jsonObject.getValue("record").toString()) == epochHash
                }?.key
                ?.toLongOrNull() ?: error("C_MISSING_DEPENDENCY")
        return keystore.keysForEpoch(deviceKeys, epochNumber) != null
    }
}

internal object PallaSyncSnapshotTrustVerifier {
    fun requireExactChoice(
        policy: PallaSyncPolicyGate,
        chain: String,
        origin: String,
        logId: String,
        compaction: JsonObject,
        authorId: String,
        authorPublicKey: String,
        partCount: Int,
    ) {
        val choice = checkNotNull(policy.trustedSnapshotChoice(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(
            choice.relayOrigin == origin && choice.chainId == chain && choice.logId == logId &&
                choice.compactionId == compaction.getValue("compaction_id").jsonPrimitive.content &&
                choice.compactionHash == PallaSyncCore.objectHash(compaction.toString()) &&
                choice.coveredThroughSeq == compaction.getValue("covered_through_seq").jsonPrimitive.long &&
                choice.coveredLogHash == compaction.getValue("covered_log_hash").jsonPrimitive.content &&
                choice.stateSha256 == compaction.getValue("state_sha256").jsonPrimitive.content &&
                choice.epochHash == compaction.getValue("epoch_hash").jsonPrimitive.content &&
                choice.authorDeviceId == authorId && choice.authorPublicKey == authorPublicKey && choice.partCount == partCount,
        ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        policy.requireCurrent(origin, "trusted_snapshot")
    }
}
