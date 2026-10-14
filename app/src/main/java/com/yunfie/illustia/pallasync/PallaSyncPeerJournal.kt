package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** §§11.1/18.1: public offer consumption and exact approval retry survive interruption.
 * A stored confirmation is audit state; it cannot authorize a new native SAS session.
 */
internal class PallaSyncPeerJournal(
    private val prefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        EncryptedSharedPreferences.create(
            context,
            "pallasync_peer_journal",
            PallaSyncPlatformKeys.master(context),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        ),
    )

    @Serializable
    enum class Stage { CREATED, OFFERED, BOUND, TRANSFERRED, SUBMITTED, ACCEPTED, REJECTED, CONSUMED }

    @Serializable
    data class Transcript(
        val offer: JsonObject,
        val authorityHash: String,
        val stage: Stage = Stage.CREATED,
        val request: JsonObject? = null,
        val certificate: JsonObject? = null,
        val envelope: JsonObject? = null,
        val envelopes: List<JsonObject>? = null,
        val approvalBody: String? = null,
        val acceptance: JsonObject? = null,
    ) {
        val requestId: String get() = offer.getValue("request_id").jsonPrimitive.content
    }

    fun read(id: String): Transcript? = prefs.getString("offer.$id", null)?.let { Json.decodeFromString<Transcript>(it) }

    fun create(
        offer: JsonObject,
        authorityHash: String,
    ): Transcript {
        val value = Transcript(offer, authorityHash)
        check(read(value.requestId) == null) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return commit(value)
    }

    fun update(
        previous: Transcript,
        next: Transcript,
    ): Transcript {
        check(read(previous.requestId) == previous) { "C_ROLLBACK_DETECTED" }
        check(next.offer == previous.offer && next.authorityHash == previous.authorityHash) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(previous.request == null || previous.request == next.request) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(previous.certificate == null || previous.certificate == next.certificate) { "C_DEVICE_EQUIVOCATION" }
        check(previous.envelope == null || previous.envelope == next.envelope) { "C_DEVICE_EQUIVOCATION" }
        check(previous.envelopes == null || previous.envelopes == next.envelopes) { "C_DEVICE_EQUIVOCATION" }
        check(previous.approvalBody == null || previous.approvalBody == next.approvalBody) { "C_DEVICE_EQUIVOCATION" }
        check(previous.acceptance == null || previous.acceptance == next.acceptance) { "C_ACCEPTANCE_UNPROVEN" }
        check(allowed(previous.stage, next.stage)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return commit(next)
    }

    private fun allowed(
        previous: Stage,
        next: Stage,
    ): Boolean =
        previous == next ||
            when (previous) {
                Stage.CREATED -> next in setOf(Stage.OFFERED, Stage.CONSUMED)
                Stage.OFFERED -> next in setOf(Stage.BOUND, Stage.CONSUMED)
                Stage.BOUND -> next in setOf(Stage.TRANSFERRED, Stage.REJECTED, Stage.CONSUMED)
                Stage.TRANSFERRED -> next in setOf(Stage.SUBMITTED, Stage.REJECTED, Stage.CONSUMED)
                Stage.SUBMITTED -> next == Stage.ACCEPTED
                Stage.ACCEPTED, Stage.REJECTED, Stage.CONSUMED -> false
            }

    private fun commit(value: Transcript): Transcript {
        val bound = value.stage in setOf(Stage.BOUND, Stage.TRANSFERRED, Stage.SUBMITTED, Stage.ACCEPTED, Stage.REJECTED)
        val transferred = value.stage in setOf(Stage.TRANSFERRED, Stage.SUBMITTED, Stage.ACCEPTED)
        check(!bound || value.request != null) { "C_MISSING_DEPENDENCY" }
        check(!transferred || (value.certificate != null && value.envelope != null && value.envelopes?.contains(value.envelope) == true)) {
            "C_MISSING_DEPENDENCY"
        }
        check(value.stage !in setOf(Stage.SUBMITTED, Stage.ACCEPTED) || value.approvalBody != null) { "C_MISSING_DEPENDENCY" }
        check(value.stage != Stage.ACCEPTED || value.acceptance != null) { "C_ACCEPTANCE_UNPROVEN" }
        check(prefs.edit().putString("offer.${value.requestId}", Json.encodeToString(value)).commit() && read(value.requestId) == value) {
            "C_LOCAL_STORAGE_FULL"
        }
        return value
    }
}
