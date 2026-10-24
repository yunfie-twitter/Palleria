package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.ProtocolDao
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Builds §10.3 control history only from complete, already verified local log incarnations. */
internal class PallaSyncImportHistoryBuilder(
    private val dao: ProtocolDao,
) {
    suspend fun build(chainId: String): PallaSyncImportRelay.History {
        val controls = mutableListOf<VerifiedControl>()
        val logIds = dao.importSourceLogIds(chainId)
        check(logIds.isNotEmpty()) { "C_MISSING_DEPENDENCY" }
        for (logId in logIds) {
            currentCoroutineContext().ensureActive()
            val highWater = dao.importSourceHighWater(chainId, logId) ?: error("C_MISSING_DEPENDENCY")
            check(highWater > 0) { "C_MISSING_DEPENDENCY" }
            var after = 0L
            while (after < highWater) {
                currentCoroutineContext().ensureActive()
                val page = dao.logPage(chainId, logId, after, highWater, PALLASYNC_PAGE_SIZE)
                check(page.isNotEmpty()) { "C_MISSING_DEPENDENCY" }
                for (row in page) {
                    check(row.logId == logId && row.chainId == chainId && row.relaySeq == after + 1L) {
                        "C_MISSING_DEPENDENCY"
                    }
                    check(row.validationState in VERIFIED_STATES) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                    parseVerifiedControl(chainId, row)?.let(controls::add)
                    after = row.relaySeq
                }
            }
            check(after == highWater) { "C_MISSING_DEPENDENCY" }
        }
        return fromControls(chainId, controls)
    }

    companion object {
        internal fun fromVerifiedRows(
            chainId: String,
            rows: List<LogEntryEntity>,
        ): PallaSyncImportRelay.History = fromControls(chainId, rows.mapNotNull { parseVerifiedControl(chainId, it) })

        private fun fromControls(
            chainId: String,
            objects: List<VerifiedControl>,
        ): PallaSyncImportRelay.History {
            fun ordered(kind: String): JsonArray =
                JsonArray(
                    objects
                        .filter { it.kind == kind }
                        .sortedWith(compareBy<VerifiedControl>({ it.generation }, { it.epoch }, { it.logId }, { it.sequence }))
                        .map { it.value },
                )

            val history =
                PallaSyncImportRelay.History(
                    parameters = ordered("parameters"),
                    epochs = ordered("epoch"),
                    devices = ordered("device"),
                    envelopes = ordered("envelope"),
                    compactions = ordered("compaction"),
                )
            PallaSyncImportRelay.canonicalBody(chainId, history)
            return history
        }

        private fun parseVerifiedControl(
            chainId: String,
            row: LogEntryEntity,
        ): VerifiedControl? {
            check(row.chainId == chainId && row.validationState in VERIFIED_STATES) {
                "C_BOOTSTRAP_TRUST_REQUIRED"
            }
            val entry = Json.parseToJsonElement(row.signedJson).jsonObject
            check(PallaSyncCore.objectHash(entry.toString()) == row.entryHash) { "C_LOG_HASH_MISMATCH" }
            val kind = entry.getValue("kind").jsonPrimitive.content
            if (kind !in IMPORT_KINDS) return null
            val value = entry.getValue("object") as? JsonObject ?: error("C_SCHEMA_VIOLATION")
            val objectChain = value["chain_id"] as? JsonPrimitive
            check(objectChain?.isString == true && objectChain.content == chainId) {
                "C_RECOVERY_ROOT_MISMATCH"
            }
            return VerifiedControl(kind, row.logId, row.relaySeq, value)
        }

        private val IMPORT_KINDS = setOf("parameters", "epoch", "device", "envelope", "compaction")
        private val VERIFIED_STATES = setOf("authority_verified", "materialized")

        private data class VerifiedControl(
            val kind: String,
            val logId: String,
            val sequence: Long,
            val value: JsonObject,
        ) {
            val generation: Long = value["generation"]?.jsonPrimitive?.longOrNull ?: 0L
            val epoch: Long = value["epoch"]?.jsonPrimitive?.longOrNull ?: 0L
        }
    }
}
