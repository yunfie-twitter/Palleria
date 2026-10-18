package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ChainStateEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Local §17.6 report for a verified post-cut source suffix; it is never sent to the target relay. */
internal object PallaSyncReseedSourceConflict {
    data class Summary(
        val sourceOrigin: String,
        val firstSequence: Long,
        val lastSequence: Long,
        val entryCount: Long,
    )

    fun summary(report: String): Summary? {
        val value =
            kotlinx.serialization.json.Json
                .parseToJsonElement(report)
                .jsonObject
        if (value.getValue("unimported_entry_count").jsonPrimitive.long == 0L) return null
        return Summary(
            sourceOrigin = value.getValue("source_origin").jsonPrimitive.content,
            firstSequence = value.getValue("unimported_first_seq").jsonPrimitive.long,
            lastSequence = value.getValue("unimported_last_seq").jsonPrimitive.long,
            entryCount = value.getValue("unimported_entry_count").jsonPrimitive.long,
        )
    }

    fun report(
        cut: ChainStateEntity,
        checkpoint: PallaSyncGenesisCheckpoint,
        sourceOrigin: String,
        verifiedEntryCount: Long,
    ): JsonObject {
        check(checkpoint.logId == cut.logId && checkpoint.generation == cut.generation) { "C_ROLLBACK_DETECTED" }
        val cutSequence = cut.materializedSeq
        check(checkpoint.headSeq >= cutSequence) { "C_ROLLBACK_DETECTED" }
        if (checkpoint.headSeq == cutSequence) {
            check(
                checkpoint.headHash == cut.verifiedHash && checkpoint.parametersHash == cut.parametersHash &&
                    checkpoint.epochHash == cut.epochHash,
            ) { "C_EPOCH_FORK" }
            check(verifiedEntryCount == 0L) { "C_TRANSPARENCY_INVALID" }
        } else {
            val suffixCount = checkpoint.headSeq - cutSequence
            check(verifiedEntryCount == suffixCount) { "C_TRANSPARENCY_INVALID" }
        }
        val suffixCount = checkpoint.headSeq - cutSequence
        return buildJsonObject {
            put("source_origin", sourceOrigin)
            put("source_log_id", checkNotNull(cut.logId))
            put("source_generation", cut.generation)
            put("source_authority_hash", checkNotNull(PallaSyncCore.objectHash(checkpoint.authority.toString())))
            put("source_cut_seq", cutSequence)
            put("source_cut_hash", checkNotNull(cut.verifiedHash))
            put("source_cut_parameters_hash", checkNotNull(cut.parametersHash))
            put("source_cut_epoch_hash", checkNotNull(cut.epochHash))
            put("observed_head_seq", checkpoint.headSeq)
            put("observed_head_hash", checkpoint.headHash)
            put("observed_parameters_hash", checkpoint.parametersHash)
            put("observed_epoch_hash", checkpoint.epochHash)
            put("unimported_entry_count", suffixCount)
            put("unimported_first_seq", cutSequence + 1)
            put("unimported_last_seq", checkpoint.headSeq)
        }
    }
}
