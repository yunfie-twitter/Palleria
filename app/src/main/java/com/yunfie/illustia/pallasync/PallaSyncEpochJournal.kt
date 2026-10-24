package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/** §13.4: persist an exact proposal before dispatch; an uncertain outcome retains
 * its normal secret. Root and recovery input are never stored in this journal.
 * Separate guarded transitions keep dispatch/readback/ack boundaries explicit.
 */
@Suppress("TooManyFunctions")
internal class PallaSyncEpochJournal(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        EncryptedSharedPreferences.create(
            context,
            "pallasync_epoch_journal",
            PallaSyncPlatformKeys.master(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ),
    )

    @Serializable
    data class Candidate(
        val origin: String,
        val deviceId: String,
        val baseAuthorityHash: String,
        val proposal: PallaSyncPreparedEpoch.PublicResult,
        val submitted: Boolean = false,
        val delivered: Boolean = false,
        val acknowledged: Boolean = false,
    )

    fun pending(chain: String): Candidate? = prefs.getString(publicKey(chain), null)?.let { Json.decodeFromString<Candidate>(it) }

    /** Owns the mutable normal epoch secret, including failed commits/readback. */
    fun stage(
        candidate: Candidate,
        secret: ByteArray,
    ) {
        try {
            check(
                secret.size == KEY_BYTES && !candidate.submitted && !candidate.delivered && !candidate.acknowledged,
            ) { "C_SCHEMA_VIOLATION" }
            val chain = candidate.proposal.chainId
            val existing = pending(chain)
            check(existing == null || existing == candidate) { "C_MISSING_DEPENDENCY" }
            val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(secret)
            val previous = prefs.getString(secretKey(chain), null)
            check(previous == null || previous == encoded) { "C_COMMITMENT_MISMATCH" }
            check(
                prefs
                    .edit()
                    .putString(publicKey(chain), Json.encodeToString(candidate))
                    .putString(secretKey(chain), encoded)
                    .commit() && pending(chain) == candidate,
            ) { "C_LOCAL_STORAGE_FULL" }
            val readback = normalSecret(candidate)
            try {
                check(java.security.MessageDigest.isEqual(secret, readback)) { "C_COMMITMENT_MISMATCH" }
            } finally {
                readback.fill(0)
            }
        } finally {
            secret.fill(0)
        }
    }

    fun normalSecret(candidate: Candidate): ByteArray {
        check(pending(candidate.proposal.chainId) == candidate) { "C_ROLLBACK_DETECTED" }
        val encoded = checkNotNull(prefs.getString(secretKey(candidate.proposal.chainId), null)) { "C_MISSING_DEPENDENCY" }
        val bytes = Base64.getUrlDecoder().decode(encoded)
        if (bytes.size != KEY_BYTES) {
            bytes.fill(0)
            error("C_COMMITMENT_MISMATCH")
        }
        return bytes
    }

    fun submitted(candidate: Candidate): Candidate = update(candidate, candidate.copy(submitted = true))

    fun delivered(candidate: Candidate): Candidate {
        check(candidate.submitted) { "C_ACCEPTANCE_UNPROVEN" }
        return update(candidate, candidate.copy(delivered = true))
    }

    fun acknowledged(candidate: Candidate): Candidate {
        check(candidate.delivered) { "C_ACCEPTANCE_UNPROVEN" }
        return update(candidate, candidate.copy(acknowledged = true))
    }

    fun cancelBeforeDispatch(candidate: Candidate) {
        check(!candidate.submitted) { "C_ACCEPTANCE_UNPROVEN" }
        remove(candidate)
    }

    /** Caller must supply the authenticated protected checkpoint, never an error hint. */
    fun discardCasLoser(
        candidate: Candidate,
        authority: JsonObject,
    ) {
        val chain = candidate.proposal.chainId
        check(
            authority
                .getValue("pin")
                .jsonObject
                .getValue("chain_id")
                .jsonPrimitive.content == chain,
        ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val winner = checkNotNull(authority.getValue("epochs").jsonObject[candidate.proposal.epoch.toString()]) { "C_MISSING_DEPENDENCY" }
        val hash = checkNotNull(PallaSyncCore.objectHash(winner.jsonObject.getValue("record").toString())) { "C_SCHEMA_VIOLATION" }
        check(hash != candidate.proposal.epochHash) { "C_ACCEPTANCE_UNPROVEN" }
        remove(candidate)
    }

    fun finish(candidate: Candidate) {
        check(candidate.acknowledged) { "C_ACCEPTANCE_UNPROVEN" }
        remove(candidate)
    }

    private fun update(
        previous: Candidate,
        next: Candidate,
    ): Candidate {
        val chain = previous.proposal.chainId
        check(pending(chain) == previous) { "C_ROLLBACK_DETECTED" }
        check(
            prefs.edit().putString(publicKey(chain), Json.encodeToString(next)).commit() && pending(chain) == next,
        ) { "C_LOCAL_STORAGE_FULL" }
        return next
    }

    private fun remove(candidate: Candidate) {
        val chain = candidate.proposal.chainId
        check(pending(chain) == candidate) { "C_ROLLBACK_DETECTED" }
        check(
            prefs
                .edit()
                .remove(publicKey(chain))
                .remove(secretKey(chain))
                .commit() &&
                pending(chain) == null && !prefs.contains(secretKey(chain)),
        ) { "C_LOCAL_STORAGE_FULL" }
    }

    private fun publicKey(chain: String) = "$chain.proposal"

    private fun secretKey(chain: String) = "$chain.normal_secret"

    private companion object {
        const val KEY_BYTES = 32
    }
}
