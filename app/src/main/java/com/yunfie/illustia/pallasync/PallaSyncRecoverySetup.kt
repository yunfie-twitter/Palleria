package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** §18.6: scoped dealer orchestration. Restart retains public evidence, never RMS/session keys. */
@Suppress("TooManyFunctions") // Native dealer stages share one ownership/lifetime scope.
internal class PallaSyncRecoverySetup private constructor(
    private val root: PallaSyncRootSession,
    private val store: PallaSyncRecoverySetStore,
    val prepared: JsonObject,
    initial: PallaSyncRecoverySetStore.Entry,
) : AutoCloseable {
    class Transfer internal constructor(
        val shareIndex: Int,
        val holderRole: String,
        val pair: PallaSyncRecoveryCandidate,
    ) : AutoCloseable {
        internal var distributionPacket: String? = null

        override fun close() = pair.close()
    }

    val ceremonyId: String = UUID.randomUUID().toString()
    var evidence: PallaSyncRecoverySetStore.Entry = initial
        private set
    private val transfers = mutableListOf<Transfer>()
    private val collections = mutableListOf<PallaSyncRecoveryCollection>()
    private var closed = false
    private var thresholdAuditStarted = false
    val manifestJson: String get() = evidence.manifestJson

    fun userInteraction() {
        checkOpen()
        root.userInteraction()
    }

    fun beginTransfer(shareIndex: Int): Transfer =
        checked {
            val holder =
                prepared
                    .getValue("manifest")
                    .jsonObject
                    .getValue("holders")
                    .jsonArray
                    .getOrNull(shareIndex - 1)
                    ?.jsonObject
            check(holder != null) { "C_RECOVERY_PARAMETER_INVALID" }
            val role = if (holder.getValue("holder_kind").jsonPrimitive.content == "offline") "reader" else "holder"
            val pair =
                PallaSyncCore.beginRecoveryTransfer(
                    manifestJson,
                    root.identity.adminPublicKey,
                    ceremonyId,
                    UUID.randomUUID().toString(),
                    "initiator",
                    "dealer",
                    purpose(),
                )
            Transfer(shareIndex, role, pair).also(transfers::add)
        }

    fun bind(
        transfer: Transfer,
        peerDescriptorJson: String,
    ): String =
        checked {
            requireTransfer(transfer)
            val visual = PallaSyncCore.bindRecoveryTransfer(transfer.pair.handle, peerDescriptorJson)
            val role =
                Json
                    .parseToJsonElement(peerDescriptorJson)
                    .jsonObject
                    .getValue("role")
                    .jsonPrimitive.content
            check(role == transfer.holderRole) { "C_RECOVERY_SHARE_INVALID" }
            visual
        }

    fun confirm(
        transfer: Transfer,
        visual: String,
        bothEndpointsConfirmed: Boolean,
    ) = checked {
        requireTransfer(transfer)
        PallaSyncCore.confirmRecoveryTransfer(transfer.pair.handle, visual, bothEndpointsConfirmed)
    }

    fun distribute(transfer: Transfer): String =
        checked {
            requireTransfer(transfer)
            transfer.distributionPacket?.let { packet ->
                val sequence =
                    Json
                        .parseToJsonElement(packet)
                        .jsonObject
                        .getValue("message_seq")
                        .jsonPrimitive.content
                        .toLong()
                return@checked PallaSyncCore.retryRecoveryTransfer(transfer.pair.handle, sequence)
            }
            PallaSyncCore
                .distributeRecoveryShare(root.handle, root.operation.wire, transfer.pair.handle, transfer.shareIndex.toLong())
                .also { transfer.distributionPacket = it }
        }

    /** Returns an encrypted signed_share packet or the exact public persisted payload (§7.11.5). */
    fun receive(
        transfer: Transfer,
        packetJson: String,
    ): String =
        checked {
            requireTransfer(transfer)
            val reply = PallaSyncCore.receiveRecoveryDealer(root.handle, root.operation.wire, transfer.pair.handle, packetJson)
            val result = Json.parseToJsonElement(reply).jsonObject
            if (result["verified"]?.jsonPrimitive?.content == "true") {
                val holder =
                    prepared
                        .getValue("manifest")
                        .jsonObject
                        .getValue("holders")
                        .jsonArray[transfer.shareIndex - 1]
                        .jsonObject
                val shareId = result.getValue("recovery_share_id").jsonPrimitive.content
                check(shareId == holder.getValue("recovery_share_id").jsonPrimitive.content) { "C_RECOVERY_SHARE_INVALID" }
                val hash = result.getValue("record_hash").jsonPrimitive.content
                if (evidence.state == "prepared") {
                    val firstReadback = evidence.holders.single { it.shareId == shareId }.recordHash == null
                    evidence = store.readback(evidence, shareId, hash)
                    if (firstReadback) {
                        store.auditQueue.record(
                            evidence,
                            "recovery_share_holder_added",
                            "succeeded",
                            holder.getValue("holder_kind").jsonPrimitive.content,
                        )
                    }
                } else {
                    check(evidence.holders.single { it.shareId == shareId }.recordHash == hash) { "C_RECOVERY_SHARE_INVALID" }
                }
            }
            reply
        }

    fun beginThresholdTest(explicitlyConfirmed: Boolean): PallaSyncRecoveryCollection =
        checked {
            check(evidence.holders.all { it.recordHash != null && it.available && !it.lost }) { "C_RECOVERY_ROTATION_INCOMPLETE" }
            PallaSyncRecoveryCollection
                .setupTest(manifestJson, evidence.statusesJson, root, evidence.head, ceremonyId, explicitlyConfirmed)
                .also {
                    collections.add(it)
                    thresholdAuditStarted = true
                    store.auditQueue.record(evidence, "recovery_ceremony_started", "started")
                }
        }

    fun activate(nowMs: Long): String =
        checked {
            evidence = store.signStatus(evidence) { PallaSyncCore.activateRecoverySet(root.handle, root.operation.wire, nowMs) }
            finishThresholdAudit(true)
            store.auditQueue.record(
                evidence,
                if (root.operation ==
                    PallaSyncRootSession.Operation.RECOVERY_ROTATE
                ) {
                    "recovery_share_set_rotated"
                } else {
                    "recovery_share_set_created"
                },
                "succeeded",
            )
            evidence.statusesJson
        }

    fun finishThresholdAudit(success: Boolean) {
        if (thresholdAuditStarted) {
            thresholdAuditStarted = false
            store.auditQueue.record(
                evidence,
                if (success) "recovery_ceremony_succeeded" else "recovery_ceremony_failed",
                if (success) "succeeded" else "failed",
            )
        }
    }

    fun retire(
        previous: PallaSyncRecoverySetStore.Entry,
        nowMs: Long,
    ): String =
        checked {
            check(
                root.operation == PallaSyncRootSession.Operation.RECOVERY_ROTATE && evidence.state == "active",
            ) { "C_RECOVERY_ROTATION_INCOMPLETE" }
            store
                .signStatus(previous) {
                    PallaSyncCore.advanceRecoveryStatus(
                        root.handle,
                        root.operation.wire,
                        previous.manifestJson,
                        previous.statusesJson,
                        previous.head,
                        "retired",
                        evidence.id,
                        nowMs,
                    )
                }.statusesJson
        }

    private fun purpose(): String = if (root.operation == PallaSyncRootSession.Operation.RECOVERY_ROTATE) "rotate" else "create"

    private fun checkOpen() {
        check(!closed) { "C_RECOVERY_SESSION_EXPIRED" }
    }

    private fun requireTransfer(transfer: Transfer) {
        check(transfers.any { it === transfer }) { "C_RECOVERY_SHARE_INVALID" }
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> checked(action: () -> T): T {
        checkOpen()
        try {
            return action()
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
    }

    override fun close() {
        if (!closed) {
            closed = true
            try {
                closeRecoveryResources(
                    buildList<AutoCloseable> {
                        addAll(collections)
                        addAll(transfers)
                        add(root)
                    },
                )
            } finally {
                finishThresholdAudit(false)
                if (evidence.state == "prepared") evidence = store.interrupted(evidence)
            }
        }
    }

    companion object {
        @Suppress("LongParameterList", "TooGenericExceptionCaught")
        fun create(
            ownedRoot: PallaSyncRootSession,
            store: PallaSyncRecoverySetStore,
            generation: Long,
            threshold: Long,
            holdersJson: String,
            nowMs: Long,
        ): PallaSyncRecoverySetup {
            try {
                val prepared =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.prepareRecoverySet(
                                ownedRoot.handle,
                                ownedRoot.operation.wire,
                                generation,
                                threshold,
                                holdersJson,
                                nowMs,
                            ),
                        ).jsonObject
                val entry =
                    store.create(
                        prepared.getValue("manifest").toString(),
                        prepared.getValue("status_chain").toString(),
                        ownedRoot.identity.adminPublicKey,
                    )
                return PallaSyncRecoverySetup(ownedRoot, store, prepared, entry)
            } catch (failure: Throwable) {
                ownedRoot.close()
                throw failure
            }
        }
    }
}
