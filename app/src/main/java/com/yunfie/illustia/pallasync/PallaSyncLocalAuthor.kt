package com.yunfie.illustia.pallasync

import android.content.Context
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.OutboxEntity
import com.yunfie.illustia.pallasync.data.SignedEventEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.security.SecureRandom
import java.util.Base64

/** Serial authoring (§11.1): save the predecessor before constructing the next record. */
internal class PallaSyncLocalAuthor(
    private val store: PallaSyncLocalStore,
    private val journal: PallaSyncAuthorJournal,
    private val epochTag: suspend (ChainStateEntity, PallaSyncKeySnapshot, String) -> String? = { state, keys, collection ->
        val authority = checkNotNull(store.protocolDao().currentAuthority(state.chainId)) { "C_MISSING_DEPENDENCY" }
        PallaSyncEpochSession.begin(keys, authority, state.keyEpoch).use { it.tag(collection) }
    },
    private val fault: (Boundary) -> Unit = {},
) {
    constructor(context: Context, store: PallaSyncLocalStore) : this(store, PallaSyncAuthorJournal(context))

    enum class Boundary { FRAME_JOURNALED, RECORD_JOURNALED, ROOM_COMMITTED, TAIL_PUBLISHED }

    private val random = SecureRandom()
    private val json = Json { encodeDefaults = true }

    suspend fun initialize(
        chain: String,
        keys: PallaSyncKeySnapshot,
        events: List<PallaSyncPendingEvent>,
    ) {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        val device = checkNotNull(keys.deviceId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val plan = journal.initialPlan(state, device, events)
        append(chain, keys, plan.events.drop(plan.nextIndex), plan.nextIndex)
        check(journal.initialProgress(state, device) == plan.events.size) { "C_MISSING_DEPENDENCY" }
    }

    suspend fun append(
        chain: String,
        keys: PallaSyncKeySnapshot,
        events: List<PallaSyncPendingEvent>,
        initialIndex: Int? = null,
        replay: PallaSyncAuthorJournal.ReplayReference? = null,
    ) {
        check(replay == null || (events.size == 1 && initialIndex == null)) { "C_SCHEMA_VIOLATION" }
        val device = checkNotNull(keys.deviceId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        PallaSyncDeviceSession.begin(keys).use { session ->
            val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
            requireWritable(state, initialIndex, replay)
            journal.pending(state, device)?.let {
                journal.requireResume(state, device, it)
                persist(state, session, it)
            }
            journal.requireTail(checkNotNull(store.dao.getChainState(chain)), device)
            if (replay != null && replayAlreadyConstructed(state, replay, events.single())) return@use
            events.forEachIndexed { offset, event ->
                currentCoroutineContext().ensureActive()
                val current = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
                val index = initialIndex?.plus(offset)
                if (index != null && index < journal.initialProgress(current, device)) return@forEachIndexed
                requireWritable(current, initialIndex, replay)
                journal.requireTail(current, device)
                val intent = prepare(current, device, event, keys).copy(initialIndex = index, replay = replay)
                journal.stage(current, device, intent)
                fault(Boundary.FRAME_JOURNALED)
                persist(current, session, intent)
            }
        }
    }

    private suspend fun replayAlreadyConstructed(
        state: ChainStateEntity,
        reference: PallaSyncAuthorJournal.ReplayReference,
        event: PallaSyncPendingEvent,
    ): Boolean {
        val row = checkNotNull(store.protocolDao().migrationChunk(reference.migrationId, reference.chunkIndex)) { "C_MISSING_DEPENDENCY" }
        val work = Json.decodeFromString<PallaSyncReplayWork>(row.reportJson)
        check(row.targetChain == state.chainId && row.sourceHash == reference.sourceHash && work.event == event) { "C_RECORD_ID_CONFLICT" }
        val destination = work.destinationRecordId
        if (destination == null) {
            check(row.state == "pending") { "C_ROLLBACK_DETECTED" }
            return false
        }
        check(row.state == "constructed" && store.protocolDao().event(state.chainId, destination) != null) { "C_ROLLBACK_DETECTED" }
        return true
    }

    private fun requireWritable(
        state: ChainStateEntity,
        initialIndex: Int?,
        replay: PallaSyncAuthorJournal.ReplayReference?,
    ) {
        val allowed =
            if (initialIndex !=
                null
            ) {
                state.writeState == "activation_preparing"
            } else {
                state.writeState == "active" ||
                    (replay != null && state.writeState == "epoch_rebase_required")
            }
        check(allowed) { "C_MISSING_DEPENDENCY" }
    }

    /** Finish only an already journaled construction under its retained epoch.
     * It cannot allocate an additional operation while the current writer is paused.
     */
    suspend fun finishPending(
        chain: String,
        keys: PallaSyncKeySnapshot,
    ) {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
        check(state.writeState in setOf("active", "epoch_pending_keys", "epoch_rebase_required")) { "C_MISSING_DEPENDENCY" }
        val device = checkNotNull(keys.deviceId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val saved = journal.pending(state, device)
        if (saved == null) {
            journal.requireTail(state, device)
            return
        }
        check(saved.context.epoch == keys.epoch && keys.epoch == state.keyEpoch) { "C_MISSING_DEPENDENCY" }
        journal.requireResume(state, device, saved)
        PallaSyncDeviceSession.begin(keys).use { persist(state, it, saved) }
        journal.requireTail(checkNotNull(store.dao.getChainState(chain)), device)
    }

    private suspend fun prepare(
        state: ChainStateEntity,
        device: String,
        event: PallaSyncPendingEvent,
        keys: PallaSyncKeySnapshot,
    ): PallaSyncAuthorJournal.Intent {
        check(state.authorSeq < PallaSyncConflictResolver.MAX_COUNTER && state.lamport < PallaSyncConflictResolver.MAX_COUNTER) {
            "C_COUNTER_EXHAUSTED"
        }
        val inner =
            PallaSyncInnerRecord(
                event.schema,
                state.authorSeq + 1,
                state.authorTailHash,
                listOf(
                    PallaSyncOperation(
                        event.entityId,
                        event.operation,
                        state.lamport + 1,
                        System.currentTimeMillis(),
                        event.context,
                        event.body as? JsonObject ?: error("C_SCHEMA_VIOLATION"),
                    ),
                ),
            )
        val input = json.encodeToString(inner).toByteArray()
        val frame =
            try {
                PallaSyncCore.prepareRecordFrame(input)
            } finally {
                input.fill(0)
            }
        return try {
            store.protocolDao().requireLocalHistoryWrite(state.chainId, event.schema, inner.operations.single())
            val nonce = ByteArray(24).also(random::nextBytes)
            val context =
                PallaSyncAuthorJournal.RecordContext(
                    state.chainId,
                    state.generation,
                    newPallaSyncRecordId(),
                    device,
                    state.keyEpoch,
                    epochTag(state, keys, event.schema),
                )
            PallaSyncAuthorJournal.Intent(
                context,
                inner,
                encode(frame),
                encode(nonce),
                if (state.initialPullCompleted) "queued" else "pending_initial_merge",
            )
        } finally {
            frame.fill(0)
        }
    }

    private suspend fun persist(
        state: ChainStateEntity,
        session: PallaSyncDeviceSession,
        initial: PallaSyncAuthorJournal.Intent,
    ) {
        val context = initial.context
        check(context.epoch == state.keyEpoch && context.generation == state.generation && context.chainId == state.chainId) {
            "C_MISSING_DEPENDENCY"
        }
        val frame = Base64.getUrlDecoder().decode(initial.frame)
        val record =
            try {
                PallaSyncCore.createPreparedRecord(
                    session.handle,
                    json.encodeToString(context),
                    frame,
                    Base64.getUrlDecoder().decode(initial.nonce),
                )
            } finally {
                frame.fill(0)
            }
        check(initial.signed == null || initial.signed == record) { "C_RECORD_ID_CONFLICT" }
        val intent = initial.copy(signed = record)
        journal.stage(state, context.deviceId, intent)
        fault(Boundary.RECORD_JOURNALED)
        val hash = checkNotNull(PallaSyncCore.objectHash(record)) { "C_SCHEMA_VIOLATION" }
        store.transaction { commit(state, intent, hash) }
        fault(Boundary.ROOM_COMMITTED)
        val committed = checkNotNull(store.dao.getChainState(state.chainId)) { "C_MISSING_DEPENDENCY" }
        journal.complete(committed, context.deviceId, intent, hash)
        fault(Boundary.TAIL_PUBLISHED)
    }

    private suspend fun commit(
        state: ChainStateEntity,
        intent: PallaSyncAuthorJournal.Intent,
        hash: String,
    ) {
        val context = intent.context
        val current = checkNotNull(store.dao.getChainState(state.chainId)) { "C_MISSING_DEPENDENCY" }
        if (current.authorSeq == intent.inner.deviceSeq) {
            check(current.authorTailHash == hash && store.protocolDao().event(state.chainId, context.recordId)?.objectHash == hash) {
                "C_DEVICE_EQUIVOCATION"
            }
        } else {
            check(current.authorSeq + 1 == intent.inner.deviceSeq && current.authorTailHash == intent.inner.prevRecordHash) {
                "C_DEVICE_EQUIVOCATION"
            }
            store.protocolDao().applyVerifiedRecord(
                SignedEventEntity(
                    state.chainId,
                    context.recordId,
                    checkNotNull(current.logId) { "C_BOOTSTRAP_TRUST_REQUIRED" },
                    null,
                    hash,
                    checkNotNull(intent.signed),
                    null,
                    "local_provisional",
                ),
                context.generation,
                context.deviceId,
                intent.inner,
            )
            store.dao.insertOutboxEvent(
                OutboxEntity(
                    chainId = state.chainId,
                    deviceSeq = intent.inner.deviceSeq,
                    eventJson = checkNotNull(intent.signed),
                    status = intent.status,
                    recordId = context.recordId,
                    objectHash = hash,
                ),
            )
            check(
                store.dao.commitAuthorTail(
                    state.chainId,
                    current.authorSeq,
                    intent.inner.deviceSeq,
                    hash,
                    intent.inner.operations.maxOf { it.lamport },
                ) ==
                    1,
            ) {
                "C_DEVICE_EQUIVOCATION"
            }
        }
        intent.replay?.let {
            store.protocolDao().commitReplay(it.migrationId, it.chunkIndex, it.sourceHash, state.chainId, context.recordId)
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
