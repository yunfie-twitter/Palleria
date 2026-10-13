package com.yunfie.illustia.pallasync

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** §§8.6/11.4: only sanitized public audit metadata waits for an approved writer. Never store artifacts or secret sessions. */
internal class PallaSyncRecoveryAuditQueue(
    private val prefs: SharedPreferences,
) {
    @Serializable
    data class Pending(
        val chainId: String,
        val event: PallaSyncPendingEvent,
    )

    /** Failure is deliberately observable without making audit a prerequisite for zeroization/emergency action. */
    @Volatile var storageFailed = false
        private set

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun record(
        entry: PallaSyncRecoverySetStore.Entry,
        event: String,
        result: String,
        holderKind: String? = null,
    ): Boolean =
        try {
            enqueue(
                Pending(
                    entry.manifest
                        .getValue("chain_id")
                        .jsonPrimitive.content,
                    recoveryAudit(entry, event, result, holderKind),
                ),
            )
            true
        } catch (_: Exception) {
            storageFailed = true
            false
        }

    fun enqueue(pending: Pending) =
        synchronized(lock) {
            validate(pending.event)
            val bytes =
                java.util.Base64
                    .getUrlDecoder()
                    .decode(pending.chainId)
            check(
                bytes.size == CHAIN_DIGEST_BYTES && java.util.Base64
                    .getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(bytes) == pending.chainId,
            ) {
                "C_SCHEMA_VIOLATION"
            }
            val key = PREFIX + pending.event.entityId
            val encoded = Json.encodeToString(pending)
            val existing = prefs.getString(key, null)
            check(existing == null || existing == encoded) { "C_RECORD_ID_CONFLICT" }
            check(prefs.edit().putString(key, encoded).commit() && prefs.getString(key, null) == encoded) { "C_LOCAL_STORAGE_FULL" }
        }

    fun pending(
        chainId: String,
        limit: Int = PAGE_SIZE,
    ): List<Pending> =
        synchronized(lock) {
            check(limit in 1..PAGE_SIZE) { "C_SCHEMA_VIOLATION" }
            prefs.all.keys
                .filter { it.startsWith(PREFIX) }
                .sorted()
                .asSequence()
                .map { key ->
                    val encoded = checkNotNull(prefs.getString(key, null))
                    checkNotNull(PallaSyncCore.objectHash(encoded)) { "C_SCHEMA_VIOLATION" }
                    Json.decodeFromString<Pending>(encoded).also { pending ->
                        check(key == PREFIX + pending.event.entityId) { "C_RECORD_ID_CONFLICT" }
                        validate(pending.event)
                    }
                }.filter { it.chainId == chainId }
                .take(limit)
                .toList()
        }

    fun eraseChain(chainId: String) =
        synchronized(lock) {
            val owned =
                prefs.all.keys.filter { key ->
                    key.startsWith(PREFIX) &&
                        runCatching {
                            Json
                                .decodeFromString<Pending>(
                                    checkNotNull(prefs.getString(key, null)),
                                ).chainId == chainId
                        }.getOrDefault(false)
                }
            check(prefs.edit().also { editor -> owned.forEach(editor::remove) }.commit()) { "C_LOCAL_STORAGE_FULL" }
            check(owned.none(prefs::contains)) { "C_LOCAL_STORAGE_FULL" }
        }

    /** Remove only after an idempotent Room handoff commits, including after uncertain prior publication. */
    suspend fun drain(
        chainId: String,
        publish: suspend (PallaSyncPendingEvent) -> Boolean,
    ) {
        for (pending in pending(chainId)) {
            if (!publish(pending.event)) return
            synchronized(lock) {
                val key = PREFIX + pending.event.entityId
                check(prefs.getString(key, null) == Json.encodeToString(pending)) { "C_RECORD_ID_CONFLICT" }
                check(prefs.edit().remove(key).commit() && !prefs.contains(key)) { "C_LOCAL_STORAGE_FULL" }
            }
        }
    }

    companion object {
        private val lock = Any()
        private const val PREFIX = "pending-recovery-audit."
        private const val PAGE_SIZE = 200
        private const val CHAIN_DIGEST_BYTES = 32

        internal fun validate(event: PallaSyncPendingEvent) {
            check(
                event.schema == "pallasync.audit/3" && event.operation == "upsert" &&
                    event.context.isEmpty() && event.favoriteOrder == null,
            ) {
                "C_SCHEMA_VIOLATION"
            }
            check(
                event.body.jsonObject
                    .getValue("event")
                    .jsonPrimitive.content in recoveryAuditEvents,
            ) { "C_SCHEMA_VIOLATION" }
            val inner =
                PallaSyncInnerRecord(
                    event.schema,
                    1,
                    null,
                    listOf(
                        PallaSyncOperation(
                            event.entityId,
                            event.operation,
                            1,
                            checkNotNull(event.createdAtMs) { "C_SCHEMA_VIOLATION" },
                            event.context,
                            event.body.jsonObject,
                        ),
                    ),
                )
            val bytes = Json.encodeToString(inner).toByteArray(Charsets.UTF_8)
            try {
                PallaSyncCore.prepareRecordFrame(bytes).fill(0)
            } finally {
                bytes.fill(0)
            }
        }
    }
}
