package com.yunfie.illustia.pallasync

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** §§18.7–18.9: local safety markers retain public evidence; retirement uses fresh, explicit root input. */
internal class PallaSyncRecoveryManagement(
    context: Context,
    private val manager: PalleriaSyncManager,
) {
    private val store = PallaSyncRecoverySetStore(context)
    private val slot = PallaSyncRecoverySlot(context)
    private val protector = PallaSyncRecoveryKeyProtector(context)

    suspend fun heldSetId(): String? = withContext(Dispatchers.IO) { slot.read()?.setId }

    suspend fun inventory(): List<PallaSyncRecoverySetStore.Entry> =
        withContext(Dispatchers.IO) {
            if (slot.pendingKeyErasure().isNotEmpty()) PallaSyncRecoveryHolder(slot, protector)
            val chain = checkNotNull(manager.recoveryChainContext()) { "C_MISSING_DEPENDENCY" }
            val held = slot.read()
            store.all().forEach { entry ->
                slot.publicEvidence(entry.id)?.let { evidence ->
                    store.observeReplacedHolder(slot.instanceId, held?.setId, evidence)
                }
            }
            slot
                .read()
                ?.takeIf {
                    it.admin == chain.adminPublicKey && it.record
                        .getValue("chain_id")
                        .jsonPrimitive.content == chain.chainId
                }?.let(store::observeHolder)
            store
                .all()
                .filter {
                    it.admin == chain.adminPublicKey && it.manifest
                        .getValue("chain_id")
                        .jsonPrimitive.content == chain.chainId
                }.also { entries ->
                    entries
                        .filter {
                            it.conflictingStatuses != null || it.state in
                                setOf(
                                    "retired",
                                    "compromised",
                                )
                        }.forEach(::updateOwnStatus)
                }
        }

    suspend fun markLost(
        expected: PallaSyncRecoverySetStore.Entry,
        shareId: String,
    ) = withContext(Dispatchers.IO) {
        val changed = store.lost(expected, shareId)
        audit(changed, shareId)
    }

    suspend fun retire(
        expected: PallaSyncRecoverySetStore.Entry,
        mnemonic: String,
        passphrase: String,
        cancellation: PallaSyncRootCancellation,
    ): PallaSyncRecoverySetStore.Entry =
        withContext(Dispatchers.IO) {
            val chain = checkNotNull(manager.recoveryChainContext()) { "C_MISSING_DEPENDENCY" }
            check(
                expected.admin == chain.adminPublicKey && expected.manifest
                    .getValue("chain_id")
                    .jsonPrimitive.content == chain.chainId,
            ) {
                "C_RECOVERY_SET_MISMATCH"
            }
            val root =
                PallaSyncRootSession.beginText(
                    mnemonic,
                    passphrase,
                    PallaSyncRootSession.Operation.RECOVERY_DISABLE,
                    chain.chainId,
                    chain.adminPublicKey,
                )
            try {
                cancellation.attach(root)
                val retired = signRecoveryRetirement(root, store, expected)
                updateOwnStatus(retired)
                retired
            } finally {
                root.close()
                cancellation.release(root)
            }
        }

    suspend fun importStatuses(
        expected: PallaSyncRecoverySetStore.Entry,
        statusesJson: String,
    ): PallaSyncRecoverySetStore.Entry =
        withContext(Dispatchers.IO) {
            val updated =
                try {
                    store.updateStatus(expected, statusesJson, requireLocalActivation = false)
                } catch (failure: IllegalStateException) {
                    store.read(expected.id)?.takeIf { it.conflictingStatuses != null }?.let(::updateOwnStatus)
                    throw failure
                }
            updateOwnStatus(updated)
            updated
        }

    /** §18.7: consumes one explicitly collected root; retirement retains the public evidence. */
    suspend fun retireRecovered(
        expected: PallaSyncRecoverySetStore.Entry,
        completion: PallaSyncRecoveryCompletion,
        cancellation: PallaSyncRootCancellation,
    ): PallaSyncRecoverySetStore.Entry =
        withContext(Dispatchers.IO) {
            val root = completion.takeRoot(PallaSyncRootSession.Operation.RECOVERY_DISABLE, null) { System.currentTimeMillis() }
            try {
                cancellation.attach(root)
                val chain = checkNotNull(manager.recoveryChainContext()) { "C_MISSING_DEPENDENCY" }
                check(
                    root.identity.chainId == chain.chainId && root.identity.adminPublicKey == chain.adminPublicKey &&
                        expected.admin == chain.adminPublicKey && expected.manifest
                            .getValue("chain_id")
                            .jsonPrimitive.content == chain.chainId,
                ) {
                    "C_RECOVERY_ROOT_MISMATCH"
                }
                signRecoveryRetirement(root, store, expected).also(::updateOwnStatus)
            } finally {
                root.close()
                cancellation.release(root)
                completion.close()
            }
        }

    private fun updateOwnStatus(entry: PallaSyncRecoverySetStore.Entry) {
        if (entry.conflictingStatuses != null ||
            entry.state in setOf("retired", "compromised")
        ) {
            PallaSyncCore.cancelRecoverySetSessions(entry.id)
        }
        slot.read()?.takeIf { it.setId == entry.id }?.let { own ->
            val statuses = entry.conflictingStatuses ?: entry.statusesJson
            val next = JsonObject(own.packageObject + ("status_chain" to Json.parseToJsonElement(statuses)))
            try {
                slot.updateStatus(own, next.toString())
            } catch (failure: IllegalStateException) {
                // A verified fork is an expected rejection only after its exact evidence is durable in the holder.
                if (entry.conflictingStatuses == null || slot.read()?.conflictingStatusChain != statuses) throw failure
            }
        }
    }

    suspend fun markCompromised(expected: PallaSyncRecoverySetStore.Entry) =
        withContext(Dispatchers.IO) {
            // Stop native ceremonies before either durable marker; neither marker is an admin signature.
            val changed =
                store.compromised(expected) {
                    slot.read()?.takeIf { it.setId == expected.id }?.let(slot::markCompromised)
                }
            audit(changed, null)
        }

    suspend fun eraseOwn(
        expected: PallaSyncRecoverySetStore.Entry,
        explicitlyConfirmed: Boolean,
    ) = withContext(Dispatchers.IO) {
        check(explicitlyConfirmed) { "C_RECOVERY_AUTH_REQUIRED" }
        check(store.read(expected.id) == expected) { "C_RECOVERY_SHARE_INVALID" }
        val own = checkNotNull(slot.read()?.takeIf { it.setId == expected.id }) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }
        // Commit the loss before erasure: a storage failure may conservatively undercount, never falsely promise capacity.
        val changed =
            store.lost(
                expected,
                own.record
                    .getValue("recovery_share_id")
                    .jsonPrimitive.content,
            )
        PallaSyncRecoveryHolder(slot, protector).erase(true, own)
        audit(
            changed,
            own.record
                .getValue("recovery_share_id")
                .jsonPrimitive.content,
        )
    }

    // Optional audit failure cannot undo or prevent the local safety action. No diagnostic receives the failure.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun audit(
        entry: PallaSyncRecoverySetStore.Entry,
        lostShareId: String?,
    ) {
        try {
            manager.enqueueRecoveryAudit(
                entry.manifest
                    .getValue("chain_id")
                    .jsonPrimitive.content,
                recoverySafetyAudit(entry, lostShareId),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Local public flags remain durable. Audit delivery is optional (§18.7).
        }
    }
}
