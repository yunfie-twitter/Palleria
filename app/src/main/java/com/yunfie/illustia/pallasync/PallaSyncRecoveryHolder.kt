package com.yunfie.illustia.pallasync

import androidx.fragment.app.FragmentActivity
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/** §18.6/§18.7: only OS-backed wrapping enters the single holder slot; temporary pieces remain native. */
internal class PallaSyncRecoveryHolder(
    private val slot: PallaSyncRecoverySlot,
    private val protector: PallaSyncRecoveryKeyProtector,
) {
    init {
        finishKeyErasure()
    }

    private fun finishKeyErasure() {
        slot.pendingKeyErasure().forEach { alias ->
            check(slot.read()?.keyAlias != alias) { "C_RECOVERY_SHARE_INVALID" }
            protector.delete(alias)
            slot.keyErased(alias)
        }
    }

    internal class Admission(
        val key: PallaSyncRecoveryKeyProtector.Prepared,
        val previous: PallaSyncRecoverySlot.Entry?,
        val deviceId: String,
        val replacementConfirmed: Boolean,
        private val slot: PallaSyncRecoverySlot,
        private val protector: PallaSyncRecoveryKeyProtector,
    ) : AutoCloseable {
        val holderInstanceId: String get() = slot.instanceId
        internal var acknowledgedPacket: String? = null
        internal var persistedEntry: PallaSyncRecoverySlot.Entry? = null

        override fun close() {
            if (slot.read()?.keyAlias != key.alias) protector.delete(key.alias)
        }
    }

    fun prepare(
        deviceId: String,
        replacementConfirmed: Boolean,
    ): Admission {
        val previous = slot.read()
        check(previous == null || replacementConfirmed) { "C_RECOVERY_SHARE_ALREADY_USED" }
        return Admission(protector.prepare(), previous, deviceId, replacementConfirmed, slot, protector)
    }

    fun receive(
        admission: Admission,
        pair: PallaSyncRecoveryCandidate,
        packetJson: String,
        nowMs: Long,
    ): String =
        checked(pair) {
            check(slot.read() == admission.previous) { "C_RECOVERY_SHARE_ALREADY_USED" }
            PallaSyncCore.receiveRecoveryHolder(
                pair.handle,
                packetJson,
                slot.instanceId,
                admission.deviceId,
                admission.key.protection,
                true,
                admission.replacementConfirmed,
                admission.previous != null,
                nowMs,
            )
        }

    suspend fun persist(
        admission: Admission,
        pair: PallaSyncRecoveryCandidate,
        statusesJson: String,
        trustedAdmin: String,
        activity: FragmentActivity,
    ): String =
        checked(pair) {
            admission.acknowledgedPacket?.let { packet ->
                val saved = checkNotNull(admission.persistedEntry) { "C_RECOVERY_SHARE_INVALID" }
                check(slot.read() == saved && !saved.compromised && saved.conflictingStatusChain == null) { "C_RECOVERY_SHARE_INVALID" }
                val sequence =
                    kotlinx.serialization.json.Json
                        .parseToJsonElement(
                            packet,
                        ).jsonObject
                        .getValue("message_seq")
                        .jsonPrimitive.content
                        .toLong()
                return@checked PallaSyncCore.retryRecoveryTransfer(pair.handle, sequence).also {
                    check(it == packet) { "C_RECOVERY_SHARE_INVALID" }
                }
            }
            val packageJson = PallaSyncCore.recoveryHolderPackage(pair.handle, statusesJson, null)
            val cipher = authenticateRecoveryCipher(activity, protector.encrypt(admission.key.alias))
            val rswk = PallaSyncCore.recoveryHolderWrapKey(pair.handle)
            val encrypted =
                try {
                    cipher.doFinal(rswk)
                } finally {
                    rswk.fill(0)
                }
            val next = PallaSyncRecoverySlot.Entry(packageJson, admission.key.alias, encoded(cipher.iv), encoded(encrypted))
            val saved = slot.replace(admission.previous, next, trustedAdmin, admission.replacementConfirmed)
            // A new OS read and a new authenticated decrypt precede the native readback acknowledgement.
            val reopened = checkNotNull(slot.read()) { "C_RECOVERY_STORAGE_UNSAFE" }
            check(reopened == saved) { "C_RECOVERY_STORAGE_UNSAFE" }
            val readback = unwrap(reopened, activity)
            val ack =
                try {
                    PallaSyncCore.recoveryHolderPersisted(pair.handle, reopened.packageJson, readback)
                } finally {
                    readback.fill(0)
                }
            finishKeyErasure()
            admission.persistedEntry = saved
            admission.acknowledgedPacket = ack
            ack
        }

    suspend fun release(
        pair: PallaSyncRecoveryCandidate,
        activity: FragmentActivity,
        explicitlyConsented: Boolean,
        emergencyNewRoot: Boolean,
        expectedPackageJson: String? = null,
    ): String =
        checked(pair) {
            check(explicitlyConsented) { "C_RECOVERY_AUTH_REQUIRED" }
            val stored = checkNotNull(slot.read()) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }
            check(expectedPackageJson == null || stored.packageJson == expectedPackageJson) { "C_RECOVERY_SHARE_INVALID" }
            check(stored.conflictingStatusChain == null) { "C_RECOVERY_SHARE_INVALID" }
            val key = unwrap(stored, activity)
            try {
                check(slot.read() == stored) { "C_RECOVERY_SHARE_INVALID" }
                PallaSyncCore.releaseRecoveryShare(pair.handle, stored.packageJson, key, true, true, stored.compromised, emergencyNewRoot)
            } finally {
                key.fill(0)
            }
        }

    // Cancellation and JVM failures must erase this collector's temporary pieces before propagating.
    @Suppress("TooGenericExceptionCaught")
    suspend fun collectOwn(
        collector: Long,
        activity: FragmentActivity,
        explicitlyConsented: Boolean,
    ): String {
        try {
            check(explicitlyConsented) { "C_RECOVERY_AUTH_REQUIRED" }
            val stored = checkNotNull(slot.read()) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }
            check(stored.conflictingStatusChain == null) { "C_RECOVERY_SHARE_INVALID" }
            val key = unwrap(stored, activity)
            return try {
                check(slot.read() == stored) { "C_RECOVERY_SHARE_INVALID" }
                PallaSyncCore.collectOwnRecoveryShare(collector, stored.packageJson, key, slot.instanceId, true, true, stored.compromised)
            } finally {
                key.fill(0)
            }
        } catch (failure: Throwable) {
            PallaSyncCore.endRecoveryCollection(collector)
            throw failure
        }
    }

    fun erase(
        explicitlyConfirmed: Boolean,
        stored: PallaSyncRecoverySlot.Entry = checkNotNull(slot.read()) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" },
    ) {
        slot.erase(stored, explicitlyConfirmed)
        finishKeyErasure()
    }

    private suspend fun unwrap(
        entry: PallaSyncRecoverySlot.Entry,
        activity: FragmentActivity,
    ): ByteArray = authenticateRecoveryCipher(activity, protector.decrypt(entry)).doFinal(Base64.getUrlDecoder().decode(entry.encryptedKey))

    // Catch cancellation and JVM allocation failures to release the operation's native secret buffers, then rethrow.
    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> checked(
        pair: PallaSyncRecoveryCandidate,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (failure: Throwable) {
            if (failure !is IllegalStateException || failure.message != "C_MISSING_DEPENDENCY") pair.close()
            throw failure
        }
}

private fun encoded(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
