package com.yunfie.illustia.pallasync

import androidx.fragment.app.FragmentActivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** §§7.11.4–7.11.5/18.7: an independent reader/holder releases only into its confirmed live pair. */
internal class PallaSyncRecoveryReleaseOwner : AutoCloseable {
    enum class Stage { EMPTY, SAS, READY, RELEASING, RELEASED, CLOSED }

    @Volatile var stage = Stage.EMPTY
        private set
    private var pair: PallaSyncRecoveryCandidate? = null
    private var packageJson: String? = null
    private var packet: String? = null
    var visual: String? = null
        private set
    var descriptor: String? = null
        private set
    var holderInstanceId: String? = null
        private set
    var protection: String? = null
        private set

    @Synchronized
    @Suppress("LongParameterList", "TooGenericExceptionCaught")
    fun begin(
        chain: PallaSyncRecoveryChainContext,
        artifact: String,
        peerDescriptor: String,
        offlineReader: Boolean,
        known: PallaSyncRecoverySetStore.Entry?,
        readerAlsoHoldsAnotherPiece: Boolean,
        readerRiskConfirmed: Boolean,
    ) {
        check(stage == Stage.EMPTY) { "C_RECOVERY_SESSION_EXPIRED" }
        try {
            check(!readerAlsoHoldsAnotherPiece || readerRiskConfirmed) { "C_RECOVERY_AUTH_REQUIRED" }
            val peer = Json.parseToJsonElement(peerDescriptor).jsonObject
            check(
                peer.getValue("side").jsonPrimitive.content == "initiator" &&
                    peer.getValue("role").jsonPrimitive.content == "collector",
            ) { "C_RECOVERY_SHARE_INVALID" }
            val purpose = peer.getValue("purpose").jsonPrimitive.content
            val canonical = validateArtifact(chain, artifact, known, offlineReader, purpose)
            val public = Json.parseToJsonElement(canonical).jsonObject
            val record = public.getValue("record").jsonObject
            holderInstanceId = record.getValue("holder_instance_id").jsonPrimitive.content
            protection = record.getValue("share_wrap_key_protection").jsonPrimitive.content
            val candidate =
                PallaSyncCore.beginRecoveryTransfer(
                    public.getValue("manifest").toString(),
                    chain.adminPublicKey,
                    peer.getValue("ceremony_id").jsonPrimitive.content,
                    peer.getValue("session_id").jsonPrimitive.content,
                    "responder",
                    if (offlineReader) "reader" else "holder",
                    purpose,
                )
            pair = candidate
            visual = PallaSyncCore.bindRecoveryTransfer(candidate.handle, peerDescriptor)
            descriptor = candidate.descriptorJson
            packageJson = canonical
            stage = Stage.SAS
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    private fun validateArtifact(
        chain: PallaSyncRecoveryChainContext,
        artifact: String,
        known: PallaSyncRecoverySetStore.Entry?,
        offlineReader: Boolean,
        purpose: String,
    ): String {
        check(known?.conflictingStatuses == null) { "C_RECOVERY_SHARE_INVALID" }
        check(known?.compromised != true) { "C_RECOVERY_SHARE_COMPROMISED" }
        val canonical = PallaSyncCore.validateRecoveryPackage(artifact, chain.adminPublicKey, known?.head)
        val public = Json.parseToJsonElement(canonical).jsonObject
        val record = public.getValue("record").jsonObject
        // A later chain generation does not regenerate or invalidate an existing active recovery set.
        // Native package/descriptor validation binds this pair to the selected manifest's original generation.
        check(record.getValue("chain_id").jsonPrimitive.content == chain.chainId) { "C_RECOVERY_SET_MISMATCH" }
        check(record.getValue("holder_kind").jsonPrimitive.content == (if (offlineReader) "offline" else "device")) {
            "C_RECOVERY_SET_MISMATCH"
        }
        val state =
            public
                .getValue("status_chain")
                .jsonArray
                .last()
                .jsonObject
                .getValue("state")
                .jsonPrimitive.content
        check(state != "retired") { "C_RECOVERY_SET_RETIRED" }
        check(state != "compromised") { "C_RECOVERY_SHARE_COMPROMISED" }
        check(state == "active" || (state == "prepared" && purpose in setOf("create", "rotate"))) {
            "C_RECOVERY_ROTATION_INCOMPLETE"
        }
        return canonical
    }

    @Synchronized
    fun confirm(bothEndpointsConfirmed: Boolean) {
        check(stage == Stage.SAS) { "C_RECOVERY_AUTH_REQUIRED" }
        try {
            PallaSyncCore.confirmRecoveryTransfer(checkNotNull(pair).handle, checkNotNull(visual), bothEndpointsConfirmed)
            stage = Stage.READY
        } finally {
            if (stage != Stage.READY) close()
        }
    }

    /** Every release owns a fresh mutable unlock input; the collector never gets RSWK/credential JSON. */
    fun releaseOffline(
        credential: ByteArray,
        explicitlyConsented: Boolean,
    ): String {
        try {
            val candidate = reserve()
            check(explicitlyConsented) { "C_RECOVERY_AUTH_REQUIRED" }
            val artifact = checkNotNull(packageJson) { "C_RECOVERY_SHARE_INVALID" }
            val key = PallaSyncCore.unlockOfflineRecoveryKey(artifact, credential)
            val encrypted =
                try {
                    PallaSyncCore.releaseRecoveryShare(candidate.handle, artifact, key, true, true, false, false)
                } finally {
                    key.fill(0)
                }
            return completed(encrypted)
        } finally {
            credential.fill(0)
            if (stage != Stage.RELEASED) close()
        }
    }

    suspend fun releaseDevice(
        holder: PallaSyncRecoveryHolder,
        activity: FragmentActivity,
        explicitlyConsented: Boolean,
    ): String {
        try {
            val candidate = reserve()
            return completed(
                holder.release(
                    candidate,
                    activity,
                    explicitlyConsented,
                    false,
                    checkNotNull(packageJson) { "C_RECOVERY_SHARE_INVALID" },
                ),
            )
        } finally {
            if (stage != Stage.RELEASED) close()
        }
    }

    @Synchronized
    private fun reserve(): PallaSyncRecoveryCandidate {
        check(stage == Stage.READY) { "C_RECOVERY_AUTH_REQUIRED" }
        stage = Stage.RELEASING
        return checkNotNull(pair)
    }

    @Synchronized
    private fun completed(encrypted: String): String {
        check(stage == Stage.RELEASING) { "C_RECOVERY_SESSION_EXPIRED" }
        packet = encrypted
        pair?.close()
        pair = null
        packageJson = null
        stage = Stage.RELEASED
        return encrypted
    }

    @Synchronized
    fun originalPacket(): String {
        check(stage == Stage.RELEASED) { "C_RECOVERY_AUTH_REQUIRED" }
        return checkNotNull(packet)
    }

    @Synchronized
    override fun close() {
        stage = Stage.CLOSED
        pair?.close()
        pair = null
        packageJson = null
        packet = null
    }
}
