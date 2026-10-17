package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** §§7.11.3/18.8: one protected slot for this physical app storage domain, across all chains.
 * A second install/OS backup is never counted as an independent physical holder.
 */
@Suppress("TooManyFunctions")
internal class PallaSyncRecoverySlot(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(protectedPrefs(context))

    @Serializable
    data class Entry(
        val packageJson: String,
        val keyAlias: String,
        val keyIv: String,
        val encryptedKey: String,
        val compromised: Boolean = false,
        val conflictingStatusChain: String? = null,
        val additionalConflictingStatusChains: List<String> = emptyList(),
    ) {
        val forkEvidence: List<String> get() = (listOfNotNull(conflictingStatusChain) + additionalConflictingStatusChains).distinct()
        val packageObject: JsonObject get() = Json.parseToJsonElement(packageJson).jsonObject
        val admin: String get() =
            packageObject
                .getValue("manifest")
                .jsonObject
                .getValue("admin_public_key")
                .jsonPrimitive.content
        val record: JsonObject get() = packageObject.getValue("record").jsonObject
        val setId: String get() = record.getValue("recovery_set_id").jsonPrimitive.content
        val statusHash: String get() =
            checkNotNull(
                PallaSyncCore.objectHash(
                    packageObject
                        .getValue("status_chain")
                        .jsonArray
                        .last()
                        .toString(),
                ),
            ) { "C_RECOVERY_SHARE_INVALID" }

        override fun toString(): String = "PallaSync protected recovery holder"
    }

    val instanceId: String
        get() =
            synchronized(lock) {
                prefs.getString(INSTANCE, null) ?: UUID.randomUUID().toString().also { id ->
                    check(prefs.edit().putString(INSTANCE, id).commit() && prefs.getString(INSTANCE, null) == id) {
                        "C_RECOVERY_STORAGE_UNSAFE"
                    }
                }
            }

    fun read(): Entry? = synchronized(lock) { prefs.getString(SLOT, null)?.let { Json.decodeFromString<Entry>(it) } }

    fun pendingKeyErasure(): Set<String> = synchronized(lock) { prefs.getStringSet(ERASE_KEYS, emptySet())!!.toSet() }

    fun keyErased(alias: String) =
        synchronized(lock) {
            check(read()?.keyAlias != alias) { "C_RECOVERY_SHARE_INVALID" }
            val pending = pendingKeyErasure() - alias
            check(prefs.edit().putStringSet(ERASE_KEYS, pending).commit() && pendingKeyErasure() == pending) { "C_LOCAL_STORAGE_FULL" }
        }

    /** Public hashes and signed ancestry survive replacement/erase; no package ciphertext or key wrapper. */
    @Serializable
    data class PublicEvidence(
        val manifestJson: String,
        val statusesJson: String,
        val recordHash: String,
        val blobHash: String,
        val statusHash: String,
        val compromised: Boolean,
        val conflictingStatusChain: String?,
        val additionalConflictingStatusChains: List<String> = emptyList(),
    ) {
        val forkEvidence: List<String> get() = (listOfNotNull(conflictingStatusChain) + additionalConflictingStatusChains).distinct()
    }

    fun publicEvidence(setId: String): PublicEvidence? =
        synchronized(lock) {
            val id = UUID.fromString(setId)
            check(id.version() == UUID_VERSION && id.variant() == UUID_VARIANT && id.toString() == setId) { "C_RECOVERY_SET_MISMATCH" }
            prefs.getString(EVIDENCE + setId, null)?.let { Json.decodeFromString<PublicEvidence>(it) }
        }

    /** CAS prevents concurrent ceremonies from replacing a holder that the user did not select. */
    fun replace(
        expected: Entry?,
        next: Entry,
        trustedAdmin: String,
        replacementConfirmed: Boolean,
    ): Entry =
        synchronized(lock) {
            check(read() == expected) { "C_RECOVERY_SHARE_ALREADY_USED" }
            check(expected == null || replacementConfirmed) { "C_RECOVERY_SHARE_ALREADY_USED" }
            check(next.keyAlias !in pendingKeyErasure()) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }
            val canonical = PallaSyncCore.validateRecoveryPackage(next.packageJson, trustedAdmin, null)
            val candidate = next.copy(packageJson = canonical)
            val known = publicEvidence(candidate.setId)
            if (known != null) {
                check(candidate.packageObject.getValue("manifest").toString() == known.manifestJson) { "C_RECOVERY_SET_MISMATCH" }
                check(PallaSyncCore.objectHash(candidate.record.toString()) == known.recordHash) { "C_RECOVERY_SET_MISMATCH" }
                PallaSyncCore.validateRecoveryPackage(candidate.packageJson, trustedAdmin, known.statusHash)
            }
            check(
                candidate.record
                    .getValue("holder_kind")
                    .jsonPrimitive.content == "device",
            ) { "C_RECOVERY_STORAGE_UNSAFE" }
            check(
                candidate.record
                    .getValue("holder_instance_id")
                    .jsonPrimitive.content == instanceId,
            ) { "C_RECOVERY_SET_MISMATCH" }
            check(
                candidate.packageObject
                    .getValue("offline_key_envelope")
                    .jsonPrimitive.content == "null",
            ) {
                "C_RECOVERY_STORAGE_UNSAFE"
            }
            val guarded =
                if (expected?.setId == candidate.setId) {
                    candidate.copy(
                        compromised = expected.compromised || candidate.compromised,
                        conflictingStatusChain = expected.conflictingStatusChain ?: candidate.conflictingStatusChain,
                        additionalConflictingStatusChains = (expected.forkEvidence + candidate.forkEvidence).distinct().drop(1),
                    )
                } else {
                    candidate
                }
            write(
                guarded.copy(
                    compromised = guarded.compromised || known?.compromised == true,
                    conflictingStatusChain = guarded.conflictingStatusChain ?: known?.conflictingStatusChain,
                    additionalConflictingStatusChains = (guarded.forkEvidence + known?.forkEvidence.orEmpty()).distinct().drop(1),
                ),
            )
        }

    fun updateStatus(
        expected: Entry,
        packageJson: String,
    ): Entry =
        synchronized(lock) {
            check(read() == expected) { "C_RECOVERY_SHARE_INVALID" }
            val candidate = expected.copy(packageJson = PallaSyncCore.validateRecoveryPackage(packageJson, expected.admin, null))
            val old = expected.packageObject
            val next = candidate.packageObject
            check(old.filterKeys { it != "status_chain" } == next.filterKeys { it != "status_chain" }) { "C_RECOVERY_SET_MISMATCH" }
            val oldStatuses = old.getValue("status_chain").jsonArray
            val nextStatuses = next.getValue("status_chain").jsonArray
            val sharedLength = minOf(oldStatuses.size, nextStatuses.size)
            if (oldStatuses.take(sharedLength) != nextStatuses.take(sharedLength)) {
                PallaSyncCore.cancelRecoverySetSessions(expected.setId)
                val evidence = (expected.forkEvidence + nextStatuses.toString()).distinct()
                write(expected.copy(conflictingStatusChain = evidence.first(), additionalConflictingStatusChains = evidence.drop(1)))
                error("C_RECOVERY_SHARE_INVALID")
            }
            PallaSyncCore.validateRecoveryPackage(candidate.packageJson, expected.admin, expected.statusHash)
            write(candidate)
        }

    fun markCompromised(expected: Entry): Entry =
        synchronized(lock) {
            check(read() == expected) { "C_RECOVERY_SHARE_INVALID" }
            PallaSyncCore.cancelRecoverySetSessions(expected.setId)
            write(expected.copy(compromised = true))
        }

    fun erase(
        expected: Entry,
        explicitlyConfirmed: Boolean,
    ) = synchronized(lock) {
        check(explicitlyConfirmed) { "C_RECOVERY_AUTH_REQUIRED" }
        check(read() == expected) { "C_RECOVERY_SHARE_INVALID" }
        PallaSyncCore.cancelRecoverySetSessions(expected.setId)
        check(
            prefs
                .edit()
                .putString(EVIDENCE + expected.setId, Json.encodeToString(evidence(expected)))
                .putStringSet(ERASE_KEYS, pendingKeyErasure() + expected.keyAlias)
                .remove(SLOT)
                .commit() &&
                read() == null,
        ) { "C_LOCAL_STORAGE_FULL" }
        // Keep the physical instance ID; an erase does not create another independent holder.
    }

    /** §17.11: remove a protected share only when its signed manifest names the deleted chain. */
    fun eraseForVerifiedChainDeletion(chainId: String): Pair<String, String>? =
        synchronized(lock) {
            val current = read() ?: return@synchronized null
            val manifestChain =
                current.packageObject
                    .getValue("manifest")
                    .jsonObject
                    .getValue("chain_id")
                    .jsonPrimitive.content
            if (manifestChain != chainId) return@synchronized null
            erase(current, explicitlyConfirmed = true)
            current.keyAlias to current.setId
        }

    fun discardDeletedChainPublicEvidence(setId: String) =
        synchronized(lock) {
            check(read()?.setId != setId) { "C_RECOVERY_SHARE_INVALID" }
            check(prefs.edit().remove(EVIDENCE + setId).commit() && prefs.getString(EVIDENCE + setId, null) == null) {
                "C_LOCAL_STORAGE_FULL"
            }
        }

    private fun write(entry: Entry): Entry {
        val editor = prefs.edit()
        read()?.takeIf { it.keyAlias != entry.keyAlias }?.let { previous ->
            editor.putStringSet(ERASE_KEYS, pendingKeyErasure() + previous.keyAlias)
        }
        read()?.takeIf { it.setId != entry.setId }?.let { previous ->
            editor.putString(EVIDENCE + previous.setId, Json.encodeToString(evidence(previous)))
        }
        editor.putString(EVIDENCE + entry.setId, Json.encodeToString(evidence(entry)))
        check(editor.putString(SLOT, Json.encodeToString(entry)).commit() && read() == entry) { "C_LOCAL_STORAGE_FULL" }
        return entry
    }

    private fun evidence(entry: Entry): PublicEvidence =
        PublicEvidence(
            entry.packageObject.getValue("manifest").toString(),
            entry.packageObject.getValue("status_chain").toString(),
            checkNotNull(PallaSyncCore.objectHash(entry.record.toString())),
            entry.record
                .getValue("recovery_blob_hash")
                .jsonPrimitive.content,
            entry.statusHash,
            entry.compromised,
            entry.conflictingStatusChain,
            entry.additionalConflictingStatusChains,
        )

    companion object {
        private val lock = Any()
        private const val INSTANCE = "physical-holder-instance"
        private const val SLOT = "single-protected-package"
        private const val EVIDENCE = "public-set-evidence."
        private const val ERASE_KEYS = "pending-wrap-key-erasure"
        private const val UUID_VERSION = 4
        private const val UUID_VARIANT = 2

        private fun protectedPrefs(context: Context): SharedPreferences {
            check((context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0) { "C_RECOVERY_STORAGE_UNSAFE" }
            return EncryptedSharedPreferences.create(
                context,
                "pallasync_recovery_holder",
                PallaSyncPlatformKeys.master(context),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}
