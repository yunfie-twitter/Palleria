package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** §§5.5/11.4/18.7: scoped collection, exact replay evidence, then one named native root operation. */
@Suppress("TooManyFunctions")
internal class PallaSyncRecoveryCollectorOwner(
    val entry: PallaSyncRecoverySetStore.Entry,
    val operation: PallaSyncRootSession.Operation,
    private val auditQueue: PallaSyncRecoveryAuditQueue? = null,
) : AutoCloseable {
    enum class Stage { NEW, READY, SAS, RECEIVING, RECOVERED, CLOSED }

    @Volatile var stage = Stage.NEW
        private set
    private var collection: PallaSyncRecoveryCollection? = null
    private var pair: PallaSyncRecoveryCandidate? = null
    private var completion: PallaSyncRecoveryCompletion? = null
    private var expectedRole: String? = null
    private val replay = PallaSyncRecoveryReplayCache<Int>()
    private val ceremonyId = UUID.randomUUID().toString()
    private var auditStarted = false
    val auditStorageFailed: Boolean get() = auditQueue?.storageFailed == true
    var distinctShares = 0
        private set
    var descriptor: String? = null
        private set
    var visual: String? = null
        private set

    @Synchronized
    fun begin(
        chain: PallaSyncRecoveryChainContext,
        explicitlyConfirmed: Boolean,
    ) = checked {
        check(stage == Stage.NEW) { "C_RECOVERY_AUTH_REQUIRED" }
        check(entry.conflictingStatuses == null) { "C_RECOVERY_SHARE_INVALID" }
        check(!entry.compromised) { "C_RECOVERY_SHARE_COMPROMISED" }
        check(
            entry.admin == chain.adminPublicKey && entry.manifest
                .getValue("chain_id")
                .jsonPrimitive.content == chain.chainId,
        ) {
            "C_RECOVERY_SET_MISMATCH"
        }
        collection =
            PallaSyncRecoveryCollection.recover(
                entry.manifestJson,
                entry.statusesJson,
                entry.admin,
                entry.head,
                ceremonyId,
                operation,
                explicitlyConfirmed,
                false,
                false,
            )
        stage = Stage.READY
        auditStarted = true
        auditQueue?.record(entry, "recovery_ceremony_started", "started")
    }

    @Synchronized
    fun beginPair(offlineReader: Boolean): String =
        checked {
            check(stage == Stage.READY) { "C_RECOVERY_AUTH_REQUIRED" }
            checkNotNull(collection).userInteraction()
            expectedRole = if (offlineReader) "reader" else "holder"
            val candidate =
                PallaSyncCore.beginRecoveryTransfer(
                    entry.manifestJson,
                    entry.admin,
                    ceremonyId,
                    UUID.randomUUID().toString(),
                    "initiator",
                    "collector",
                    if (operation == PallaSyncRootSession.Operation.RECOVERY_DISABLE) "disable" else "recover",
                )
            pair = candidate
            descriptor = candidate.descriptorJson
            visual = null
            stage = Stage.SAS
            candidate.descriptorJson
        }

    @Synchronized
    fun bind(peer: String): String =
        checked {
            check(stage == Stage.SAS) { "C_RECOVERY_AUTH_REQUIRED" }
            val sas = PallaSyncCore.bindRecoveryTransfer(checkNotNull(pair).handle, peer)
            check(
                Json
                    .parseToJsonElement(peer)
                    .jsonObject
                    .getValue("role")
                    .jsonPrimitive.content == expectedRole,
            ) {
                "C_RECOVERY_SHARE_INVALID"
            }
            visual = sas
            sas
        }

    @Synchronized
    fun confirm(bothEndpointsConfirmed: Boolean) =
        checked {
            check(stage == Stage.SAS && visual != null) { "C_RECOVERY_AUTH_REQUIRED" }
            PallaSyncCore.confirmRecoveryTransfer(checkNotNull(pair).handle, checkNotNull(visual), bothEndpointsConfirmed)
            stage = Stage.RECEIVING
        }

    @Synchronized
    fun receive(packet: String): Int =
        checked {
            replay.lookup(packet)?.let { return@checked it.value }
            check(stage == Stage.RECEIVING) { "C_RECOVERY_AUTH_REQUIRED" }
            val verified = checkNotNull(collection).receive(checkNotNull(pair), packet)
            distinctShares =
                Json
                    .parseToJsonElement(verified)
                    .jsonObject
                    .getValue("distinct_shares")
                    .jsonPrimitive.content
                    .toInt()
            replay.remember(packet, distinctShares)
            pair?.close()
            pair = null
            descriptor = null
            visual = null
            stage = Stage.READY
            distinctShares
        }

    @Synchronized
    fun finish(explicitlyConfirmed: Boolean) =
        checked {
            check(stage == Stage.READY && distinctShares >= entry.threshold) { "C_RECOVERY_THRESHOLD_NOT_MET" }
            completion = checkNotNull(collection).finish(explicitlyConfirmed)
            collection = null
            stage = Stage.RECOVERED
        }

    @Synchronized
    fun recovered(): PallaSyncRecoveryCompletion {
        check(stage == Stage.RECOVERED) { "C_RECOVERY_AUTH_REQUIRED" }
        return checkNotNull(completion)
    }

    @Synchronized
    fun operationFinished(success: Boolean) {
        check(stage == Stage.RECOVERED) { "C_RECOVERY_AUTH_REQUIRED" }
        finishAudit(success)
    }

    private fun finishAudit(success: Boolean) {
        if (auditStarted) {
            auditStarted = false
            auditQueue?.record(
                entry,
                if (success) "recovery_ceremony_succeeded" else "recovery_ceremony_failed",
                if (success) "succeeded" else "failed",
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> checked(action: () -> T): T =
        try {
            check(stage != Stage.CLOSED) { "C_RECOVERY_SESSION_EXPIRED" }
            action()
        } catch (failure: Throwable) {
            if (failure !is IllegalStateException || failure.message != "C_MISSING_DEPENDENCY") close()
            throw failure
        }

    @Synchronized
    override fun close() {
        stage = Stage.CLOSED
        val resources = listOfNotNull<AutoCloseable>(pair, collection, completion)
        pair = null
        collection = null
        completion = null
        replay.clear()
        try {
            closeRecoveryResources(resources)
        } finally {
            finishAudit(false)
        }
    }
}
