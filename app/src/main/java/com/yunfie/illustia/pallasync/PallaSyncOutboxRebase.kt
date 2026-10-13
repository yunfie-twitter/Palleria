package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.MigrationJournalEntity
import com.yunfie.illustia.pallasync.data.OutboxEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** §11.1: resolve acceptance at a protected complete cut before replacing an old-epoch intent.
 * Signed sources and author sequence evidence remain immutable. Work-to-record mapping commits
 * with the new signed record, so restarting cannot manufacture another copy of the same operation.
 */
internal class PallaSyncOutboxRebase(
    private val store: PallaSyncLocalStore,
    private val author: PallaSyncLocalAuthor,
    private val authorityHash: (String) -> String?,
    private val epochKeys: (PallaSyncKeySnapshot, Long) -> PallaSyncKeySnapshot?,
    private val transparencyPin: (String) -> JsonObject?,
    private val fault: (Boundary) -> Unit = {},
) {
    constructor(
        store: PallaSyncLocalStore,
        author: PallaSyncLocalAuthor,
        keys: PallaSyncKeystore,
        fault: (Boundary) -> Unit = {},
    ) : this(
        store,
        author,
        keys::authorityHash,
        keys::keysForEpoch,
        keys::transparencyPin,
        fault,
    )

    enum class Boundary { SOURCE_STAGED, DESTINATION_COMMITTED, SOURCE_RETIRED }

    suspend fun resume(
        chain: String,
        currentKeys: PallaSyncKeySnapshot,
    ) {
        val initial = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        check(initial.writeState in setOf("active", "epoch_rebase_required")) { "C_MISSING_DEPENDENCY" }
        val raw = checkNotNull(store.protocolDao().currentAuthority(chain)) { "C_MISSING_DEPENDENCY" }
        check(PallaSyncCore.objectHash(raw) == authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
        val authority = Json.parseToJsonElement(raw).jsonObject
        requireCut(initial, authority, currentKeys)
        // Complete a Room-committed replay before inspecting its destination mapping.
        author.finishPending(chain, currentKeys)
        val through = store.dao.lastPendingOutboxId(chain) ?: return finish(chain, initial)
        var after = 0L
        while (after < through) {
            currentCoroutineContext().ensureActive()
            val page = store.dao.pendingOutboxPage(chain, after, PALLASYNC_PAGE_SIZE).takeWhile { it.id <= through }
            if (page.isEmpty()) break
            for (source in page) {
                currentCoroutineContext().ensureActive()
                rebase(source, currentKeys, authority)
                after = source.id
            }
        }
        finish(chain, initial)
    }

    private fun requireCut(
        state: ChainStateEntity,
        authority: JsonObject,
        keys: PallaSyncKeySnapshot,
    ) {
        val pin = authority.getValue("pin").jsonObject
        val epoch =
            authority
                .getValue("epochs")
                .jsonObject
                .getValue(state.keyEpoch.toString())
                .jsonObject
                .getValue("record")
        check(
            state.chainId == keys.chainId && state.keyEpoch == keys.epoch &&
                state.generation == generation(authority) &&
                state.logId == pin.getValue("log_id").jsonPrimitive.content &&
                state.epochHash == PallaSyncCore.objectHash(epoch.toString()) &&
                state.downloadedSeq == state.verifiedSeq && state.verifiedSeq == state.materializedSeq &&
                state.verifiedSeq == authority.getValue("sequence").jsonPrimitive.long &&
                state.verifiedHash == authority.getValue("head_hash").jsonPrimitive.content,
        ) { "C_ACCEPTANCE_UNPROVEN" }
        check(PallaSyncCore.validateCurrentKeyRecipient(authority.toString(), checkNotNull(keys.deviceId))) { "C_MISSING_DEPENDENCY" }
    }

    private suspend fun rebase(
        source: OutboxEntity,
        keys: PallaSyncKeySnapshot,
        authority: JsonObject,
    ) {
        val wire = Json.decodeFromString<PallaSyncWireRecord>(source.eventJson)
        val hash = checkNotNull(source.objectHash) { "C_ACCEPTANCE_UNPROVEN" }
        check(
            wire.chainId == source.chainId && wire.recordId == source.recordId &&
                wire.deviceId == keys.deviceId && wire.generation == generation(authority) &&
                PallaSyncCore.objectHash(source.eventJson) == hash &&
                PallaSyncCore.verifySyncRecord(source.eventJson, keys.publicKeyBase64Url),
        ) { "C_RECORD_ID_CONFLICT" }
        val saved = checkNotNull(store.protocolDao().event(source.chainId, wire.recordId)) { "C_ACCEPTANCE_UNPROVEN" }
        check(saved.objectHash == hash && PallaSyncCore.objectHash(saved.signedJson) == hash) { "C_RECORD_ID_CONFLICT" }
        if (saved.relaySeq != null) {
            accepted(source, wire, authority, checkNotNull(saved.receiptJson), saved.relaySeq)
            return
        }
        // A held original signed receipt is evidence even if the materializer has not bound it yet.
        check(source.receiptJson == null && store.protocolDao().recordAcceptance(source.chainId, hash) == null) { "C_ACCEPTANCE_UNPROVEN" }
        check(wire.epoch <= keys.epoch) { "C_EPOCH_FORK" }
        if (wire.epoch == keys.epoch) return
        check(authority.getValue("epochs").jsonObject.containsKey((wire.epoch + 1).toString())) { "C_ACCEPTANCE_UNPROVEN" }
        val retained = checkNotNull(epochKeys(keys, wire.epoch)) { "C_MISSING_DEPENDENCY" }
        val inner =
            PallaSyncEpochSession.begin(retained, authority.toString(), wire.epoch).use {
                Json.decodeFromString<PallaSyncInnerRecord>(it.decryptRecord(source.eventJson))
            }
        check(inner.deviceSeq == source.deviceSeq) { "C_DEVICE_EQUIVOCATION" }
        val id = "epoch-rebase:$hash"
        stage(source, inner, id)
        fault(Boundary.SOURCE_STAGED)
        val work = store.protocolDao().migrationChunks(id, MAX_OPERATIONS)
        check(work.size == inner.operations.size) { "C_ROLLBACK_DETECTED" }
        for (row in work) {
            currentCoroutineContext().ensureActive()
            val value = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
            if (value.destinationRecordId == null) {
                author.append(
                    source.chainId,
                    keys,
                    listOf(value.event),
                    replay = PallaSyncAuthorJournal.ReplayReference(id, row.chunkIndex, hash),
                )
            }
            val completed = checkNotNull(store.protocolDao().migrationChunk(id, row.chunkIndex)) { "C_MISSING_DEPENDENCY" }
            val record =
                checkNotNull(
                    Json.decodeFromString<PallaSyncReplayWork>(completed.reportJson).destinationRecordId,
                ) { "C_MISSING_DEPENDENCY" }
            check(completed.state == "constructed" && store.protocolDao().event(source.chainId, record) != null) { "C_ROLLBACK_DETECTED" }
            fault(Boundary.DESTINATION_COMMITTED)
        }
        store.transaction {
            check(store.dao.outboxEvent(source.id) == source) { "C_RECORD_ID_CONFLICT" }
            check(store.protocolDao().retainAbandonedEvent(source.chainId, wire.recordId, hash) == 1) { "C_ACCEPTANCE_UNPROVEN" }
            check(store.dao.removeReceiptConfirmedOutbox(source.id, source.chainId, wire.recordId, hash) == 1) { "C_ACCEPTANCE_UNPROVEN" }
        }
        fault(Boundary.SOURCE_RETIRED)
    }

    private suspend fun stage(
        source: OutboxEntity,
        inner: PallaSyncInnerRecord,
        id: String,
    ) = store.transaction {
        inner.operations.forEachIndexed { index, op ->
            val event = PallaSyncPendingEvent(inner.collection, op.entityId, op.operation, op.body, op.context)
            val value = PallaSyncReplayWork(event, source.id)
            val existing = store.protocolDao().migrationChunk(id, index.toLong())
            if (existing == null) {
                store.protocolDao().putMigrationChunk(
                    MigrationJournalEntity(
                        id,
                        index.toLong(),
                        checkNotNull(source.objectHash),
                        checkNotNull(source.recordId),
                        source.chainId,
                        "pending",
                        Json.encodeToString(value),
                    ),
                )
            } else {
                check(
                    existing.sourceHash == source.objectHash && existing.sourceIdentity == source.recordId &&
                        existing.targetChain == source.chainId,
                ) { "C_ROLLBACK_DETECTED" }
                check(
                    Json.decodeFromString<PallaSyncReplayWork>(existing.reportJson).copy(destinationRecordId = null) == value,
                ) { "C_ROLLBACK_DETECTED" }
            }
        }
    }

    private suspend fun accepted(
        source: OutboxEntity,
        wire: PallaSyncWireRecord,
        authority: JsonObject,
        receipt: String,
        sequence: Long,
    ) {
        val pin = authority.getValue("pin").jsonObject
        val log = pin.getValue("log_id").jsonPrimitive.content
        val row = checkNotNull(store.protocolDao().logEntry(source.chainId, log, sequence)) { "C_ACCEPTANCE_UNPROVEN" }
        val entry = Json.parseToJsonElement(row.signedJson).jsonObject
        check(
            PallaSyncCore.objectHash(entry.getValue("object").toString()) == source.objectHash && row.receiptJson == receipt &&
                PallaSyncCore.validateRecordAuthority(authority.toString(), source.eventJson, sequence),
        ) { "C_ACCEPTANCE_UNPROVEN" }
        PallaSyncCore.validateReceiptResponse(
            "{\"entry\":${row.signedJson},\"receipt\":$receipt}".toByteArray(),
            row.signedJson,
            pin.toString(),
        )
        val original = Json.parseToJsonElement(receipt).jsonObject
        val inclusion =
            store
                .protocolDao()
                .evidenceAt(
                    pin.getValue("origin").jsonPrimitive.content,
                    original.getValue("relay_key_id").jsonPrimitive.content,
                    source.chainId,
                    log,
                    "transparency",
                    original.getValue("transparency_index").jsonPrimitive.long,
                ).singleOrNull() ?: error("C_ACCEPTANCE_UNPROVEN")
        check(
            PallaSyncCore.validateReceiptInclusion(
                receipt,
                inclusion,
                pin.toString(),
                checkNotNull(transparencyPin(source.chainId)).toString(),
            ),
        ) {
            "C_TRANSPARENCY_INVALID"
        }
        store.transaction {
            check(store.dao.outboxEvent(source.id) == source) { "C_RECORD_ID_CONFLICT" }
            check(store.dao.removeReceiptConfirmedOutbox(source.id, source.chainId, wire.recordId, checkNotNull(source.objectHash)) == 1) {
                "C_ACCEPTANCE_UNPROVEN"
            }
        }
    }

    private suspend fun finish(
        chain: String,
        initial: ChainStateEntity,
    ) = store.transaction {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        check(
            state.keyEpoch == initial.keyEpoch && state.epochHash == initial.epochHash &&
                state.writeState in setOf("active", "epoch_rebase_required"),
        ) { "C_EPOCH_FORK" }
        store.dao.updateChainState(state.copy(writeState = "active"))
    }

    private fun generation(authority: JsonObject) =
        authority
            .getValue("parameters")
            .jsonObject
            .getValue("generation")
            .jsonPrimitive.long

    private companion object {
        const val MAX_OPERATIONS = 1024
    }
}
