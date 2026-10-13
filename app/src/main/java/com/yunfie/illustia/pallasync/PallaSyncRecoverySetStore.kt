package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** §§18.6–18.9: public provisioning/availability evidence, outside sync/export/backup.
 * No RMS, piece, credential, RSWK, blob/package ciphertext or resumable secret session is stored.
 * Public inventory and lifecycle transitions share one CAS/commit lock and protected store.
 */
@Suppress("TooManyFunctions")
internal class PallaSyncRecoverySetStore(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(protectedPrefs(context))

    val auditQueue = PallaSyncRecoveryAuditQueue(prefs)

    @Serializable
    data class HolderState(
        val shareId: String,
        val instanceId: String,
        val recordHash: String? = null,
        val available: Boolean = false,
        val lost: Boolean = false,
    )

    @Serializable
    data class Entry(
        val manifestJson: String,
        val statusesJson: String,
        val holders: List<HolderState>,
        val interrupted: Boolean = false,
        val compromised: Boolean = false,
        val conflictingStatuses: String? = null,
        val additionalConflictingStatuses: List<String> = emptyList(),
        val replacesSetId: String? = null,
        val replacementPending: Boolean = false,
    ) {
        val forkEvidence: List<String> get() = (listOfNotNull(conflictingStatuses) + additionalConflictingStatuses).distinct()
        val manifest: JsonObject get() = Json.parseToJsonElement(manifestJson).jsonObject
        val statuses get() = Json.parseToJsonElement(statusesJson).jsonArray
        val id: String get() = manifest.getValue("recovery_set_id").jsonPrimitive.content
        val admin: String get() = manifest.getValue("admin_public_key").jsonPrimitive.content
        val head: String get() = checkNotNull(PallaSyncCore.objectHash(statuses.last().toString())) { "C_RECOVERY_SHARE_INVALID" }
        val state: String get() =
            statuses
                .last()
                .jsonObject
                .getValue("state")
                .jsonPrimitive.content
        val threshold: Int get() =
            manifest
                .getValue("threshold")
                .jsonPrimitive.content
                .toInt()
        val total: Int get() = holders.size
        val usable: Int get() = holders.count { it.available && !it.lost && it.recordHash != null }
        val ordinaryRecoveryAvailable: Boolean get() =
            state == "active" && !compromised && conflictingStatuses == null &&
                usable >= threshold

        override fun toString(): String = "PallaSync public recovery set evidence"
    }

    fun read(setId: String): Entry? = synchronized(lock) { prefs.getString(PREFIX + setId, null)?.let { Json.decodeFromString<Entry>(it) } }

    fun selected(): Entry? = synchronized(lock) { prefs.getString(SELECTED, null)?.let(::read) }

    /** Retain visibility of predecessors during an interrupted replacement. */
    fun all(): List<Entry> =
        synchronized(lock) {
            prefs.all.keys.filter { it.startsWith(PREFIX) }.sorted().map { key ->
                val entry = checkNotNull(read(key.removePrefix(PREFIX)))
                PallaSyncCore.validateRecoverySetState(entry.manifestJson, entry.statusesJson, entry.admin, entry.head)
                entry.forkEvidence.forEach {
                    PallaSyncCore.validateRecoverySetState(entry.manifestJson, it, entry.admin, null)
                }
                entry
            }
        }

    /** Authenticated public holder evidence is not a fresh credential/readback availability check. */
    fun observeHolder(holder: PallaSyncRecoverySlot.Entry): Entry =
        synchronized(lock) {
            val packageJson = PallaSyncCore.validateRecoveryPackage(holder.packageJson, holder.admin, holder.statusHash)
            val packageObject = Json.parseToJsonElement(packageJson).jsonObject
            val manifestJson = packageObject.getValue("manifest").toString()
            val statusesJson = packageObject.getValue("status_chain").toString()
            val known = read(holder.setId)
            if (known != null) {
                check(known.manifestJson == manifestJson) { "C_RECOVERY_SET_MISMATCH" }
                val incoming = Json.parseToJsonElement(statusesJson).jsonArray
                if (incoming.size <= known.statuses.size && incoming == JsonArray(known.statuses.take(incoming.size))) {
                    return@synchronized observeSafety(known, holder)
                }
                return@synchronized observeSafety(observeStatus(known, statusesJson), holder)
            }
            val holders =
                packageObject.getValue("manifest").jsonObject.getValue("holders").jsonArray.map { node ->
                    val value = node.jsonObject
                    HolderState(
                        value.getValue("recovery_share_id").jsonPrimitive.content,
                        value.getValue("holder_instance_id").jsonPrimitive.content,
                    )
                }
            observeSafety(commit(Entry(manifestJson, statusesJson, holders)), holder)
        }

    /** A holder's retained fork remains a suspension even when its ordinary chain is already known. */
    private fun observeSafety(
        expected: Entry,
        holder: PallaSyncRecoverySlot.Entry,
    ): Entry {
        val marked = if (holder.compromised && !expected.compromised) compromised(expected) else expected
        return holder.forkEvidence.fold(marked) { current, evidence -> observeStatus(current, evidence) }
    }

    private fun observeStatus(
        expected: Entry,
        statusesJson: String,
    ): Entry {
        val checked = PallaSyncCore.validateRecoverySetState(expected.manifestJson, statusesJson, expected.admin, null)
        return try {
            updateStatus(expected, checked, requireLocalActivation = false)
        } catch (failure: IllegalStateException) {
            // A valid fork is displayable only after its exact signed evidence was durably quarantined.
            val saved = read(expected.id)
            if (saved == null || checked !in saved.forkEvidence) throw failure
            saved
        }
    }

    fun create(
        manifestJson: String,
        statusesJson: String,
        trustedAdmin: String,
        previous: Entry? = null,
    ): Entry =
        synchronized(lock) {
            val statuses = PallaSyncCore.validateRecoverySetState(manifestJson, statusesJson, trustedAdmin, null)
            val manifest = Json.parseToJsonElement(manifestJson).jsonObject
            val holders =
                manifest.getValue("holders").jsonArray.map { node ->
                    val holder = node.jsonObject
                    HolderState(
                        holder.getValue("recovery_share_id").jsonPrimitive.content,
                        holder.getValue("holder_instance_id").jsonPrimitive.content,
                    )
                }
            previous?.let {
                requireCurrent(it)
                check(
                    !it.replacementPending && it.conflictingStatuses == null && it.forkEvidence.isEmpty() &&
                        it.admin == trustedAdmin && it.manifest.getValue("chain_id") == manifest.getValue("chain_id") && it.id !=
                        manifest.getValue("recovery_set_id").jsonPrimitive.content,
                ) {
                    "C_RECOVERY_SET_MISMATCH"
                }
            }
            val entry =
                Entry(
                    manifest.toString(),
                    statuses,
                    holders,
                    replacesSetId = previous?.id,
                    replacementPending = previous != null,
                )
            check(read(entry.id) == null && entry.state == "prepared") { "C_RECOVERY_SHARE_INVALID" }
            commit(entry, true)
        }

    /** Both signed heads remain pinned under the same commit lock; no secret dealer state is resumed. */
    fun finishReplacement(
        expected: Entry,
        root: PallaSyncRootSession,
        nowMs: Long,
    ): Entry =
        synchronized(lock) {
            requireCurrent(expected)
            check(
                expected.replacementPending && expected.state == "active" && expected.conflictingStatuses == null && !expected.compromised,
            ) {
                "C_RECOVERY_ROTATION_INCOMPLETE"
            }
            val previous = checkNotNull(read(checkNotNull(expected.replacesSetId))) { "C_MISSING_DEPENDENCY" }
            validateReplacementTarget(previous, expected)
            validateReplacementRoot(root, expected)
            PallaSyncCore.bindRecoveryReplacement(root.handle, expected.manifestJson, expected.statusesJson, expected.head)
            retireReplacedSet(previous, expected, root, nowMs)
            commit(expected.copy(replacementPending = false))
        }

    private fun validateReplacementTarget(
        previous: Entry,
        expected: Entry,
    ) {
        check(
            !previous.replacementPending && previous.conflictingStatuses == null && previous.forkEvidence.isEmpty() &&
                previous.admin == expected.admin &&
                previous.manifest.getValue("chain_id") == expected.manifest.getValue("chain_id"),
        ) { "C_RECOVERY_SET_MISMATCH" }
        when (previous.state) {
            "active", "prepared", "compromised" -> {
                Unit
            }

            "retired" -> {
                check(
                    previous.statuses
                        .last()
                        .jsonObject
                        .getValue("replacement_set_id")
                        .jsonPrimitive.content == expected.id,
                ) {
                    "C_RECOVERY_SET_MISMATCH"
                }
            }

            else -> {
                error("C_RECOVERY_ROTATION_INCOMPLETE")
            }
        }
    }

    private fun validateReplacementRoot(
        root: PallaSyncRootSession,
        expected: Entry,
    ) {
        check(
            root.operation == PallaSyncRootSession.Operation.RECOVERY_ROTATE && root.identity.adminPublicKey == expected.admin &&
                root.identity.chainId ==
                expected.manifest
                    .getValue("chain_id")
                    .jsonPrimitive.content,
        ) { "C_RECOVERY_ROOT_MISMATCH" }
    }

    private fun retireReplacedSet(
        previous: Entry,
        expected: Entry,
        root: PallaSyncRootSession,
        nowMs: Long,
    ) {
        if (previous.state == "compromised" || previous.state == "retired") return
        signStatus(previous) {
            PallaSyncCore.advanceRecoveryStatus(
                root.handle,
                root.operation.wire,
                previous.manifestJson,
                previous.statusesJson,
                previous.head,
                "retired",
                expected.id,
                nowMs,
            )
        }
    }

    /** This caller must already have validated a signed live persisted/readback packet through the native dealer. */
    fun readback(
        expected: Entry,
        shareId: String,
        recordHash: String,
    ): Entry =
        synchronized(lock) {
            requireCurrent(expected)
            check(expected.state == "prepared" && !expected.interrupted && !expected.compromised) { "C_RECOVERY_ROTATION_INCOMPLETE" }
            val selected = expected.holders.singleOrNull { it.shareId == shareId }
            check(selected != null && (selected.recordHash == null || selected.recordHash == recordHash)) { "C_RECOVERY_SHARE_INVALID" }
            commit(
                expected.copy(
                    holders =
                        expected.holders.map {
                            if (it.shareId ==
                                shareId
                            ) {
                                it.copy(recordHash = recordHash, available = true)
                            } else {
                                it
                            }
                        },
                ),
            )
        }

    fun updateStatus(
        expected: Entry,
        statusesJson: String,
        requireLocalActivation: Boolean = true,
    ): Entry =
        synchronized(lock) {
            requireCurrent(expected)
            val canonical = PallaSyncCore.validateRecoverySetState(expected.manifestJson, statusesJson, expected.admin, null)
            val statuses = Json.parseToJsonElement(canonical).jsonArray
            val shared = minOf(statuses.size, expected.statuses.size)
            if (statuses.take(shared) != expected.statuses.take(shared)) {
                PallaSyncCore.cancelRecoverySetSessions(expected.id)
                val evidence = (expected.forkEvidence + canonical).distinct()
                commit(expected.copy(conflictingStatuses = evidence.first(), additionalConflictingStatuses = evidence.drop(1)))
                error("C_RECOVERY_SHARE_INVALID")
            }
            PallaSyncCore.validateRecoverySetState(expected.manifestJson, canonical, expected.admin, expected.head)
            if (requireLocalActivation && statuses.size > expected.statuses.size && statuses
                    .last()
                    .jsonObject
                    .getValue("state")
                    .jsonPrimitive.content == "active"
            ) {
                check(
                    expected.holders.all { it.recordHash != null && it.available && !it.lost } && !expected.interrupted &&
                        !expected.compromised,
                ) {
                    "C_RECOVERY_ROTATION_INCOMPLETE"
                }
            }
            commit(expected.copy(statusesJson = canonical))
        }

    fun signStatus(
        expected: Entry,
        signer: () -> String,
    ): Entry =
        synchronized(lock) {
            requireCurrent(expected)
            updateStatus(expected, signer())
        }

    fun interrupted(expected: Entry): Entry =
        synchronized(lock) {
            requireCurrent(expected)
            commit(expected.copy(interrupted = true))
        }

    fun lost(
        expected: Entry,
        shareId: String,
    ): Entry =
        synchronized(lock) {
            requireCurrent(expected)
            check(expected.holders.any { it.shareId == shareId }) { "C_RECOVERY_SET_MISMATCH" }
            PallaSyncCore.cancelRecoverySetSessions(expected.id)
            commit(
                expected.copy(
                    holders =
                        expected.holders.map {
                            if (it.shareId ==
                                shareId
                            ) {
                                it.copy(available = false, lost = true)
                            } else {
                                it
                            }
                        },
                ),
            )
        }

    /** §§18.6–18.8: a replaced local package cannot remain counted in its previous set's H. */
    fun observeReplacedHolder(
        instanceId: String,
        currentSetId: String?,
        previous: PallaSyncRecoverySlot.PublicEvidence,
    ): Entry? =
        synchronized(lock) {
            val id =
                Json
                    .parseToJsonElement(previous.manifestJson)
                    .jsonObject
                    .getValue("recovery_set_id")
                    .jsonPrimitive.content
            val entry = read(id) ?: return@synchronized null
            if (id == currentSetId) return@synchronized entry
            check(entry.manifestJson == previous.manifestJson) { "C_RECOVERY_SET_MISMATCH" }
            PallaSyncCore.validateRecoverySetState(previous.manifestJson, previous.statusesJson, entry.admin, previous.statusHash)
            val holder = entry.holders.singleOrNull { it.instanceId == instanceId } ?: return@synchronized entry
            if (!holder.available || holder.lost) return@synchronized entry
            check(holder.recordHash == previous.recordHash) { "C_RECOVERY_SHARE_INVALID" }
            lost(entry, holder.shareId)
        }

    fun compromised(
        expected: Entry,
        beforeCommit: () -> Unit = {},
    ): Entry =
        synchronized(lock) {
            requireCurrent(expected)
            PallaSyncCore.cancelRecoverySetSessions(expected.id)
            beforeCommit()
            commit(expected.copy(compromised = true))
        }

    private fun requireCurrent(expected: Entry) {
        check(read(expected.id) == expected) { "C_RECOVERY_SHARE_INVALID" }
    }

    private fun commit(
        entry: Entry,
        select: Boolean = false,
    ): Entry {
        val editor = prefs.edit().putString(PREFIX + entry.id, Json.encodeToString(entry))
        if (select) editor.putString(SELECTED, entry.id)
        check(editor.commit() && read(entry.id) == entry) { "C_LOCAL_STORAGE_FULL" }
        return entry
    }

    companion object {
        private val lock = Any()
        private const val PREFIX = "public-set."
        private const val SELECTED = "selected-set"

        private fun protectedPrefs(context: Context): SharedPreferences {
            check(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP == 0) { "C_RECOVERY_STORAGE_UNSAFE" }
            return EncryptedSharedPreferences.create(
                context,
                "pallasync_recovery_sets",
                PallaSyncPlatformKeys.master(context),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
