package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.QuarantineEvidenceEntity
import com.yunfie.illustia.pallasync.data.SignedEventEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** §11.2: global plaintext progress walks every retained log entry, including controls. */
internal class PallaSyncPrefixMaterializer(
    private val store: PallaSyncLocalStore,
    private val keys: PallaSyncKeystore,
    private val fault: (Boundary) -> Unit = {},
) {
    enum class Boundary { EFFECTS_APPLIED, PROGRESS_COMMITTED }

    suspend fun apply(
        chain: String,
        device: PallaSyncKeySnapshot,
        checkpoint: PallaSyncGenesisCheckpoint,
    ) {
        val authority = checkpoint.authority ?: error("C_MISSING_DEPENDENCY")
        val pin = authority.getValue("pin").jsonObject.toString()
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        check(state.logId == checkpoint.logId && state.materializedSeq <= state.verifiedSeq && state.verifiedSeq <= checkpoint.headSeq) {
            "C_ROLLBACK_DETECTED"
        }
        // Verify the complete retained range against the protected authenticated cut
        // before any application state can be installed from a restored DB.
        val startHash =
            if (state.materializedSeq == 0L && state.verifiedSeq == 0L) {
                checkNotNull(checkpoint.dataZero) { "C_MISSING_DEPENDENCY" }
            } else if (state.materializedSeq == state.verifiedSeq) {
                checkNotNull(state.verifiedHash) { "C_MISSING_DEPENDENCY" }
            } else if (state.materializedSeq == 0L) {
                checkNotNull(checkpoint.dataZero) { "C_MISSING_DEPENDENCY" }
            } else {
                val row =
                    checkNotNull(store.protocolDao().logEntry(chain, checkpoint.logId, state.materializedSeq)) { "C_MISSING_DEPENDENCY" }
                Json
                    .parseToJsonElement(row.receiptJson)
                    .jsonObject
                    .getValue("resulting_head_hash")
                    .jsonPrimitive.content
            }
        verifyRange(state, checkpoint, pin, startHash)
        store.transaction {
            val current = checkNotNull(store.dao.getChainState(chain))
            store.dao.updateChainState(current.copy(downloadedSeq = maxOf(current.downloadedSeq, checkpoint.headSeq)))
        }
        materializeRange(state, device, checkpoint, authority, startHash)
        store.dao.markInitialPullCompleted(chain)
    }

    private suspend fun verifyRange(
        state: ChainStateEntity,
        checkpoint: PallaSyncGenesisCheckpoint,
        pin: String,
        startHash: String,
    ) {
        var after = state.materializedSeq
        var previous = startHash
        while (after < checkpoint.headSeq) {
            currentCoroutineContext().ensureActive()
            val row = next(state.chainId, checkpoint.logId, after, checkpoint.headSeq)
            previous = link(row, pin, after, previous)
            after = row.relaySeq
            if (after == state.verifiedSeq) check(previous == state.verifiedHash) { "C_LOG_HASH_MISMATCH" }
        }
        check(previous == checkpoint.headHash) { "C_LOG_HASH_MISMATCH" }
    }

    private suspend fun materializeRange(
        state: ChainStateEntity,
        device: PallaSyncKeySnapshot,
        checkpoint: PallaSyncGenesisCheckpoint,
        authority: JsonObject,
        startHash: String,
    ) {
        var after = state.materializedSeq
        var previous = startHash
        var contiguous = true
        var firstFailure: String? = null
        val pin = authority.getValue("pin").jsonObject.toString()
        while (after < checkpoint.headSeq) {
            currentCoroutineContext().ensureActive()
            val row = next(state.chainId, checkpoint.logId, after, checkpoint.headSeq)
            val nextHash = link(row, pin, after, previous)
            val entry = Json.parseToJsonElement(row.signedJson).jsonObject
            val prepared = runCatching { prepare(row, entry, authority, device) }
            prepared.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
            val code = commit(row, entry, prepared, nextHash, contiguous)
            if (firstFailure == null) firstFailure = code
            contiguous = contiguous && code == null
            fault(Boundary.PROGRESS_COMMITTED)
            previous = nextHash
            after = row.relaySeq
        }
        check(firstFailure == null) { checkNotNull(firstFailure) }
    }

    private suspend fun commit(
        row: LogEntryEntity,
        entry: JsonObject,
        prepared: Result<Prepared?>,
        nextHash: String,
        contiguous: Boolean,
    ): String? =
        store.transaction {
            val value = prepared.getOrNull()
            val failure = prepared.exceptionOrNull()
            // A runtime/storage/cancellation failure is not evidence of invalid signed content.
            var code = failure?.let { pendingInboxFailureCode(it) ?: throw it }
            if (value != null) {
                code = store.protocolDao().recordConflict(value.signed, value.generation, value.device, value.inner)
                if (code == null) store.protocolDao().applyVerifiedRecord(value.signed, value.generation, value.device, value.inner)
            }
            fault(Boundary.EFFECTS_APPLIED)
            PallaSyncLogRecordDisposition(store).retain(row, entry, code)
            if (code != null && validationState(code) == "quarantined") {
                store.protocolDao().quarantine(
                    QuarantineEvidenceEntity(row.chainId, row.entryHash, code, row.signedJson, "pending_resolution"),
                )
            }
            check(store.protocolDao().markLogEntry(row.chainId, row.logId, row.relaySeq, row.entryHash, validationState(code)) == 1) {
                "C_LOG_HASH_MISMATCH"
            }
            if (contiguous && code == null) {
                val current = checkNotNull(store.dao.getChainState(row.chainId))
                store.dao.updateChainState(
                    current.copy(
                        verifiedSeq = row.relaySeq,
                        materializedSeq = row.relaySeq,
                        verifiedHash = nextHash,
                        lastRelaySeq = row.relaySeq,
                    ),
                )
            }
            code
        }

    private suspend fun next(
        chain: String,
        log: String,
        after: Long,
        through: Long,
    ): LogEntryEntity =
        store.protocolDao().logPage(chain, log, after, through, 1).singleOrNull()?.also {
            check(it.relaySeq == after + 1) { "C_MISSING_DEPENDENCY" }
        } ?: error("C_MISSING_DEPENDENCY")

    private fun link(
        row: LogEntryEntity,
        pin: String,
        after: Long,
        previous: String,
    ): String =
        Json
            .parseToJsonElement(
                PallaSyncCore.validateRetainedLogLink(row.signedJson, row.receiptJson, pin, after, previous),
            ).jsonPrimitive.content

    private fun prepare(
        row: LogEntryEntity,
        entry: JsonObject,
        authority: JsonObject,
        device: PallaSyncKeySnapshot,
    ): Prepared? {
        if (entry.getValue("kind").jsonPrimitive.content != "record") return null
        val record = entry.getValue("object").jsonObject
        check(PallaSyncCore.validateRecordAuthority(authority.toString(), record.toString(), row.relaySeq)) { "C_ACCEPTANCE_UNPROVEN" }
        val epoch = record.getValue("epoch").jsonPrimitive.long
        val epochKeys = checkNotNull(keys.keysForEpoch(device, epoch)) { "C_MISSING_DEPENDENCY" }
        val payload = PallaSyncEpochSession.begin(epochKeys, authority.toString(), epoch).use { it.decryptRecord(record.toString()) }
        val inner = Json.decodeFromString<PallaSyncInnerRecord>(payload)
        val signed =
            SignedEventEntity(
                row.chainId,
                record.getValue("record_id").jsonPrimitive.content,
                row.logId,
                row.relaySeq,
                checkNotNull(PallaSyncCore.objectHash(record.toString())),
                record.toString(),
                row.receiptJson,
                "validated_object",
            )
        return Prepared(signed, record.getValue("generation").jsonPrimitive.long, record.getValue("device_id").jsonPrimitive.content, inner)
    }

    private data class Prepared(
        val signed: SignedEventEntity,
        val generation: Long,
        val device: String,
        val inner: PallaSyncInnerRecord,
    )
}
