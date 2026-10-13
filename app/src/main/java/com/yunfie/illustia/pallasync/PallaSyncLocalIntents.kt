package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.MigrationJournalEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
