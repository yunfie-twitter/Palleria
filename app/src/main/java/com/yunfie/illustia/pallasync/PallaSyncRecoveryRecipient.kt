package com.yunfie.illustia.pallasync

import androidx.fragment.app.FragmentActivity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** §§7.11.3–7.11.5/18.6: the receiving holder/issuer owns its keys and single native pair.
 * Public binding and encrypted package values may cross JNI; raw pieces and RSWK never become JSON.
 */
@Suppress("TooManyFunctions")
internal class PallaSyncRecoveryRecipient private constructor(
    private val pair: PallaSyncRecoveryCandidate,
    private val statusesJson: String,
    private val admin: String,
    private val instanceId: String,
    private val holder: PallaSyncRecoveryHolder?,
    private val admission: PallaSyncRecoveryHolder.Admission?,
    val visual: String,
) : AutoCloseable {
    enum class Stage { SAS, RECEIVING, READY_TO_PERSIST, PERSISTING, PERSISTED, CLOSED }

    @Volatile
    var stage = Stage.SAS
        private set
    val descriptor: String get() = pair.descriptorJson
    val holderInstanceId: String get() = instanceId
    private var offlinePackage: String? = null
    private var acknowledgement: String? = null
    private val received = PallaSyncRecoveryReplayCache<String?>()

    fun confirm(bothEndpointsConfirmed: Boolean) =
        checked {
            check(stage == Stage.SAS) { "C_RECOVERY_AUTH_REQUIRED" }
            PallaSyncCore.confirmRecoveryTransfer(pair.handle, visual, bothEndpointsConfirmed)
            stage = Stage.RECEIVING
        }

    /** Returns original wrap_proposal ciphertext, or null after verifying the unchanged signed record. */
    @Synchronized
    fun receive(
        packet: String,
        nowMs: Long,
    ): String? =
        checked {
            received.lookup(packet)?.let { return@checked it.value }
            check(stage == Stage.RECEIVING) { "C_RECOVERY_AUTH_REQUIRED" }
            val response =
                if (admission != null) {
                    checkNotNull(holder).receive(admission, pair, packet, nowMs)
                } else {
                    PallaSyncCore.receiveRecoveryHolder(
                        pair.handle,
                        packet,
                        instanceId,
                        null,
                        "passphrase-wrapped",
                        true,
                        false,
                        false,
                        nowMs,
                    )
                }
            val ready =
                Json
                    .parseToJsonElement(response)
                    .jsonObject["ready_to_persist"]
                    ?.jsonPrimitive
                    ?.content == "true"
            synchronized(this) {
                check(stage == Stage.RECEIVING) { "C_RECOVERY_AUTH_REQUIRED" }
                if (ready) stage = Stage.READY_TO_PERSIST
                val reply = if (ready) null else response
                received.remember(packet, reply)
                reply
            }
        }

    /** Takes mutable credential ownership; only its protected envelope appears in the exported package. */
    fun prepareOfflinePackage(credential: ByteArray): String =
        try {
            checked {
                check(stage == Stage.READY_TO_PERSIST && holder == null) { "C_RECOVERY_AUTH_REQUIRED" }
                val existing = offlinePackage
                if (existing == null) {
                    PallaSyncCore.recoveryHolderPackage(pair.handle, statusesJson, credential).also { offlinePackage = it }
                } else {
                    // An explicit credential retry must unlock the original package, never silently rewrap it.
                    val key = PallaSyncCore.unlockOfflineRecoveryKey(existing, credential)
                    key.fill(0)
                    existing
                }
            }
        } finally {
            credential.fill(0)
        }

    /** Read from the designated medium after writing it; the dealer never obtains this wrapping key. */
    fun acknowledgeOfflineReadback(
        readback: String,
        credential: ByteArray,
    ): String =
        try {
            checked {
                check(holder == null && stage == Stage.READY_TO_PERSIST) { "C_RECOVERY_AUTH_REQUIRED" }
                val canonical = PallaSyncCore.validateRecoveryPackage(readback, admin, null)
                check(canonical == offlinePackage) { "C_RECOVERY_SHARE_INVALID" }
                val key = PallaSyncCore.unlockOfflineRecoveryKey(canonical, credential)
                val packet =
                    try {
                        PallaSyncCore.recoveryHolderPersisted(pair.handle, canonical, key)
                    } finally {
                        key.fill(0)
                    }
                completed(packet)
            }
        } finally {
            credential.fill(0)
        }

    /** OS wrapping, atomic protected persistence, new OS authentication/readback, then original ACK. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun persistDevice(activity: FragmentActivity): String {
        try {
            synchronized(this) {
                check(stage == Stage.READY_TO_PERSIST && holder != null && admission != null) { "C_RECOVERY_AUTH_REQUIRED" }
                stage = Stage.PERSISTING
            }
            return completed(checkNotNull(holder).persist(checkNotNull(admission), pair, statusesJson, admin, activity))
        } catch (failure: Throwable) {
            try {
                close()
            } catch (cleanup: Throwable) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    /** An IO retry copies these exact encrypted bytes, after all secret-bearing session state has ended. */
    fun persistedAcknowledgement(): String {
        check(stage == Stage.PERSISTED) { "C_RECOVERY_AUTH_REQUIRED" }
        return checkNotNull(acknowledgement)
    }

    @Synchronized
    private fun completed(packet: String): String {
        check(stage in setOf(Stage.READY_TO_PERSIST, Stage.PERSISTING)) { "C_RECOVERY_AUTH_REQUIRED" }
        acknowledgement = packet
        offlinePackage = null
        pair.close()
        admission?.close()
        stage = Stage.PERSISTED
        return packet
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> checked(action: () -> T): T =
        try {
            check(stage != Stage.CLOSED) { "C_RECOVERY_SESSION_EXPIRED" }
            action()
        } catch (failure: Throwable) {
            if (failure !is IllegalStateException || failure.message != "C_MISSING_DEPENDENCY") {
                try {
                    close()
                } catch (cleanup: Throwable) {
                    failure.addSuppressed(cleanup)
                }
            }
            throw failure
        }

    @Synchronized
    override fun close() {
        if (stage != Stage.CLOSED) {
            stage = Stage.CLOSED
            offlinePackage = null
            acknowledgement = null
            received.clear()
            try {
                pair.close()
            } finally {
                admission?.close()
            }
        }
    }

    companion object {
        /** Device preparation precedes accepting a share; this factory takes admission ownership on every exit. */
        @Suppress("LongParameterList", "TooGenericExceptionCaught")
        fun begin(
            chain: PallaSyncRecoveryChainContext,
            manifestJson: String,
            statusesJson: String,
            peerDescriptor: String,
            instanceId: String,
            holder: PallaSyncRecoveryHolder? = null,
            admission: PallaSyncRecoveryHolder.Admission? = null,
        ): PallaSyncRecoveryRecipient {
            var pair: PallaSyncRecoveryCandidate? = null
            try {
                check((holder == null) == (admission == null)) { "C_RECOVERY_STORAGE_UNSAFE" }
                val statuses = PallaSyncCore.validateRecoverySetState(manifestJson, statusesJson, chain.adminPublicKey, null)
                check(
                    Json
                        .parseToJsonElement(statuses)
                        .jsonArray
                        .last()
                        .jsonObject
                        .getValue("state")
                        .jsonPrimitive.content == "prepared",
                ) {
                    "C_RECOVERY_ROTATION_INCOMPLETE"
                }
                val manifest = Json.parseToJsonElement(manifestJson).jsonObject
                check(manifest.getValue("chain_id").jsonPrimitive.content == chain.chainId) { "C_RECOVERY_SET_MISMATCH" }
                check(
                    manifest
                        .getValue("generation")
                        .jsonPrimitive.content
                        .toLongOrNull() == chain.generation,
                ) {
                    "C_RECOVERY_SET_MISMATCH"
                }
                validateHolderBinding(manifestJson, chain, instanceId, admission)
                val peer = Json.parseToJsonElement(peerDescriptor).jsonObject
                check(
                    peer.getValue("side").jsonPrimitive.content == "initiator" &&
                        peer.getValue("role").jsonPrimitive.content == "dealer" &&
                        peer.getValue("purpose").jsonPrimitive.content in setOf("create", "rotate"),
                ) { "C_RECOVERY_SHARE_INVALID" }
                pair =
                    PallaSyncCore.beginRecoveryTransfer(
                        manifestJson,
                        chain.adminPublicKey,
                        peer.getValue("ceremony_id").jsonPrimitive.content,
                        peer.getValue("session_id").jsonPrimitive.content,
                        "responder",
                        if (holder == null) "reader" else "holder",
                        peer.getValue("purpose").jsonPrimitive.content,
                    )
                val visual = PallaSyncCore.bindRecoveryTransfer(pair.handle, peerDescriptor)
                return PallaSyncRecoveryRecipient(pair, statuses, chain.adminPublicKey, instanceId, holder, admission, visual)
            } catch (failure: Throwable) {
                try {
                    pair?.close()
                } finally {
                    admission?.close()
                }
                throw failure
            }
        }

        private fun validateHolderBinding(
            manifestJson: String,
            chain: PallaSyncRecoveryChainContext,
            instanceId: String,
            admission: PallaSyncRecoveryHolder.Admission?,
        ) {
            check(admission == null || (admission.deviceId == chain.deviceId && admission.holderInstanceId == instanceId)) {
                "C_RECOVERY_SET_MISMATCH"
            }
            val kind = if (admission == null) "offline" else "device"
            check(
                Json.parseToJsonElement(manifestJson).jsonObject.getValue("holders").jsonArray.any { node ->
                    val binding = node.jsonObject
                    binding.getValue("holder_instance_id").jsonPrimitive.content == instanceId &&
                        binding.getValue("holder_kind").jsonPrimitive.content == kind &&
                        (admission == null || binding.getValue("holder_device_id").jsonPrimitive.content == chain.deviceId)
                },
            ) { "C_RECOVERY_SET_MISMATCH" }
        }
    }
}
