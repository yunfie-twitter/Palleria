package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** §11.1/§12.1. One encrypted construction journal and a protected author tail per immutable identity.
 * This store contains application frames and public signed records, never root or recovery material.
 */
internal class PallaSyncAuthorJournal(
    private val storage: Storage,
) {
    constructor(context: Context) : this(EncryptedStorage(context))

    internal interface Storage {
        fun read(key: String): String?

        fun commit(
            values: Map<String, String>,
            removed: Set<String> = emptySet(),
        ): Boolean

        fun keys(): Set<String> = emptySet()
    }

    private class EncryptedStorage(
        context: Context,
    ) : Storage {
        private val preferences =
            EncryptedSharedPreferences.create(
                context,
                "pallasync_author_journal",
                PallaSyncPlatformKeys.master(context),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

        override fun read(key: String): String? = preferences.getString(key, null)

        override fun keys(): Set<String> = preferences.all.keys

        override fun commit(
            values: Map<String, String>,
            removed: Set<String>,
        ): Boolean {
            val editor = preferences.edit()
            values.forEach { (key, value) -> editor.putString(key, value) }
            removed.forEach(editor::remove)
            return editor.commit()
        }
    }

    @Serializable
    data class RecordContext(
        @SerialName("chain_id") val chainId: String,
        val generation: Long,
        @SerialName("record_id") val recordId: String,
        @SerialName("device_id") val deviceId: String,
        val epoch: Long,
        @SerialName("collection_tag") val collectionTag: String? = null,
    )

    @Serializable
    data class ReplayReference(
        val migrationId: String,
        val chunkIndex: Long,
        val sourceHash: String,
    )

    @Serializable
    data class Intent(
        val context: RecordContext,
        val inner: PallaSyncInnerRecord,
        val frame: String,
        val nonce: String,
        val status: String,
        val signed: String? = null,
        val initialIndex: Int? = null,
        val replay: ReplayReference? = null,
    ) {
        override fun toString(): String = "PallaSync author intent"
    }

    @Serializable
    private data class Tail(
        val sequence: Long,
        val hash: String?,
        val lamport: Long,
    )

    @Serializable
    data class InitialPlan(
        val events: List<PallaSyncPendingEvent>,
        val nextIndex: Int = 0,
    )

    fun initialPlan(
        state: ChainStateEntity,
        device: String,
        events: List<PallaSyncPendingEvent>,
    ): InitialPlan {
        val key = "${prefix(state, device)}.initial"
        val saved = storage.read(key)?.let { Json.decodeFromString<InitialPlan>(it) }
        val plan = saved ?: InitialPlan(events)
        check(plan.nextIndex in 0..plan.events.size) { "C_ROLLBACK_DETECTED" }
        if (saved == null) {
            check(storage.commit(mapOf(key to Json.encodeToString(plan))) && storage.read(key) == Json.encodeToString(plan)) {
                "C_LOCAL_STORAGE_FULL"
            }
        }
        return plan
    }

    fun initialProgress(
        state: ChainStateEntity,
        device: String,
    ): Int =
        Json
            .decodeFromString<InitialPlan>(
                checkNotNull(storage.read("${prefix(state, device)}.initial")) { "C_MISSING_DEPENDENCY" },
            ).nextIndex

    fun pending(
        state: ChainStateEntity,
        device: String,
    ): Intent? = storage.read("${prefix(state, device)}.intent")?.let { Json.decodeFromString<Intent>(it) }

    fun requireTail(
        state: ChainStateEntity,
        device: String,
    ) {
        val raw = storage.read("${prefix(state, device)}.tail")
        val tail = raw?.let { Json.decodeFromString<Tail>(it) }
        check(
            if (tail == null) {
                state.authorSeq == 0L && state.authorTailHash == null
            } else {
                tail.sequence == state.authorSeq && tail.hash == state.authorTailHash && tail.lamport <= state.lamport
            },
        ) {
            "C_ROLLBACK_DETECTED"
        }
    }

    /** A Room commit may precede publication of its protected tail, but only for this exact intent. */
    fun requireResume(
        state: ChainStateEntity,
        device: String,
        intent: Intent,
    ) {
        val tail =
            storage.read("${prefix(state, device)}.tail")?.let { Json.decodeFromString<Tail>(it) }
                ?: Tail(0, null, 0)
        check(
            intent.inner.deviceSeq == tail.sequence + 1 && intent.inner.prevRecordHash == tail.hash &&
                intent.inner.operations.all { it.lamport > tail.lamport } && state.lamport >= tail.lamport,
        ) {
            "C_ROLLBACK_DETECTED"
        }
        val before = state.authorSeq == tail.sequence && state.authorTailHash == tail.hash
        val after =
            intent.signed?.let { signed ->
                state.authorSeq == intent.inner.deviceSeq && state.authorTailHash == PallaSyncCore.objectHash(signed)
            } == true
        check(before || after) { "C_ROLLBACK_DETECTED" }
    }

    fun stage(
        state: ChainStateEntity,
        device: String,
        intent: Intent,
    ) {
        check(
            intent.context.chainId == state.chainId && intent.context.deviceId == device && intent.context.generation == state.generation,
        ) {
            "C_SCHEMA_VIOLATION"
        }
        val previous = pending(state, device)
        intent.initialIndex?.let { check(initialProgress(state, device) == it) { "C_ROLLBACK_DETECTED" } }
        check(previous == null || previous.copy(signed = null) == intent.copy(signed = null)) { "C_DEVICE_EQUIVOCATION" }
        check(previous?.signed == null || previous.signed == intent.signed) { "C_RECORD_ID_CONFLICT" }
        check(
            storage.commit(mapOf("${prefix(state, device)}.intent" to Json.encodeToString(intent))),
        ) { "C_LOCAL_STORAGE_FULL" }
        check(pending(state, device) == intent) { "C_LOCAL_STORAGE_FULL" }
    }

    fun complete(
        state: ChainStateEntity,
        device: String,
        intent: Intent,
        hash: String,
    ) {
        check(pending(state, device) == intent) { "C_DEVICE_EQUIVOCATION" }
        val tail = Tail(intent.inner.deviceSeq, hash, intent.inner.operations.maxOf { it.lamport })
        val key = prefix(state, device)
        val values = mutableMapOf("$key.tail" to Json.encodeToString(tail))
        intent.initialIndex?.let { index ->
            val plan = Json.decodeFromString<InitialPlan>(checkNotNull(storage.read("$key.initial")))
            check(plan.nextIndex == index) { "C_ROLLBACK_DETECTED" }
            values["$key.initial"] = Json.encodeToString(plan.copy(nextIndex = index + 1))
        }
        check(storage.commit(values, setOf("$key.intent"))) { "C_LOCAL_STORAGE_FULL" }
        requireTail(state.copy(authorSeq = tail.sequence, authorTailHash = hash), device)
        check(pending(state, device) == null) { "C_LOCAL_STORAGE_FULL" }
    }

    private fun prefix(
        state: ChainStateEntity,
        device: String,
    ): String = "${state.chainId}.$device.${state.generation}"

    fun eraseChain(chainId: String) {
        val owned = storage.keys().filter { it.startsWith("$chainId.") }.toSet()
        check(owned.isEmpty() || storage.commit(emptyMap(), owned)) { "C_LOCAL_STORAGE_FULL" }
        check(storage.keys().none { it.startsWith("$chainId.") }) { "C_LOCAL_STORAGE_FULL" }
    }
}
