package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** §§7.11.5/18.6: one foreground dealer, a confirmed pair at a time and an explicit threshold test.
 * All stages share ownership of the native preparation; exposed values are public or HPKE ciphertext.
 */
@Suppress("TooManyFunctions")
internal class PallaSyncRecoveryDealerWorkflow(
    private val owner: PallaSyncRecoverySetupOwner,
) : AutoCloseable {
    enum class Stage { READY, SAS, DISTRIBUTING, COLLECTING, ACTIVE, CLOSED }

    var stage = Stage.READY
        private set
    var descriptor: String? = null
        private set
    var visual: String? = null
        private set
    var distinctShares = 0
        private set
    var thresholdTestPassed = false
        private set
    private var transfer: PallaSyncRecoverySetup.Transfer? = null
    private var collection: PallaSyncRecoveryCollection? = null
    private var receiver: PallaSyncRecoveryCandidate? = null
    private var expectedRole: String? = null
    private var completedEvidence: PallaSyncRecoverySetStore.Entry? = null
    val evidence get() = completedEvidence ?: owner.usePrepared { it.evidence }
    val testing get() = collection != null

    fun beginDistribution(index: Int): String =
        checked {
            check(stage == Stage.READY && collection == null) { "C_RECOVERY_AUTH_REQUIRED" }
            owner.usePrepared { setup ->
                val next = setup.beginTransfer(index)
                transfer = next
                expectedRole = next.holderRole
                descriptor = next.pair.descriptorJson
                stage = Stage.SAS
                checkNotNull(descriptor)
            }
        }

    fun bind(peerDescriptor: String): String =
        checked {
            check(stage == Stage.SAS) { "C_RECOVERY_AUTH_REQUIRED" }
            val sas =
                owner.usePrepared { setup ->
                    transfer?.let { setup.bind(it, peerDescriptor) }
                        ?: PallaSyncCore.bindRecoveryTransfer(checkNotNull(receiver).handle, peerDescriptor)
                }
            check(
                Json
                    .parseToJsonElement(peerDescriptor)
                    .jsonObject
                    .getValue("role")
                    .jsonPrimitive.content == expectedRole,
            ) {
                "C_RECOVERY_SHARE_INVALID"
            }
            visual = sas
            sas
        }

    /** A user must explicitly compare the displayed purpose/recipient/SAS on BOTH endpoints. */
    fun confirm(bothEndpointsConfirmed: Boolean): String? =
        checked {
            check(stage == Stage.SAS && visual != null) { "C_RECOVERY_AUTH_REQUIRED" }
            owner.usePrepared { setup ->
                val current = transfer
                if (current != null) {
                    setup.confirm(current, checkNotNull(visual), bothEndpointsConfirmed)
                    setup.distribute(current).also { stage = Stage.DISTRIBUTING }
                } else {
                    PallaSyncCore.confirmRecoveryTransfer(checkNotNull(receiver).handle, checkNotNull(visual), bothEndpointsConfirmed)
                    stage = Stage.COLLECTING
                    null
                }
            }
        }

    /** Returns only the original encrypted signed_share reply, or null after verified readback/collection. */
    fun receive(packet: String): String? =
        checked {
            owner.usePrepared { setup ->
                if (stage == Stage.DISTRIBUTING) {
                    val response = setup.receive(checkNotNull(transfer), packet)
                    if (Json
                            .parseToJsonElement(response)
                            .jsonObject["verified"]
                            ?.jsonPrimitive
                            ?.content == "true"
                    ) {
                        transfer?.close()
                        transfer = null
                        clearPair()
                        null
                    } else {
                        response
                    }
                } else {
                    check(stage == Stage.COLLECTING) { "C_RECOVERY_AUTH_REQUIRED" }
                    val result = checkNotNull(collection).receive(checkNotNull(receiver), packet)
                    distinctShares =
                        Json
                            .parseToJsonElement(result)
                            .jsonObject
                            .getValue("distinct_shares")
                            .jsonPrimitive.content
                            .toInt()
                    receiver?.close()
                    receiver = null
                    clearPair()
                    null
                }
            }
        }

    fun beginThresholdTest(explicitlyConfirmed: Boolean) =
        checked {
            check(stage == Stage.READY && collection == null && !thresholdTestPassed) { "C_RECOVERY_AUTH_REQUIRED" }
            collection = owner.usePrepared { it.beginThresholdTest(explicitlyConfirmed) }
        }

    fun beginCollectionPair(offlineReader: Boolean): String =
        checked {
            check(stage == Stage.READY && collection != null) { "C_RECOVERY_AUTH_REQUIRED" }
            expectedRole = if (offlineReader) "reader" else "holder"
            owner.usePrepared { setup ->
                receiver =
                    PallaSyncCore.beginRecoveryTransfer(
                        setup.manifestJson,
                        setup.evidence.admin,
                        checkNotNull(collection).ceremonyId,
                        UUID.randomUUID().toString(),
                        "initiator",
                        "collector",
                        "create",
                    )
            }
            descriptor = checkNotNull(receiver).descriptorJson
            stage = Stage.SAS
            checkNotNull(descriptor)
        }

    fun finishThresholdTest(explicitlyConfirmed: Boolean) =
        checked {
            check(stage == Stage.READY && distinctShares >= evidence.threshold) { "C_RECOVERY_THRESHOLD_NOT_MET" }
            checkNotNull(collection).finish(explicitlyConfirmed).use { result ->
                check(result.thresholdTestPassed) { "C_RECOVERY_BLOB_INVALID" }
            }
            collection = null
            thresholdTestPassed = true
        }

    fun activate(nowMs: Long): String =
        checked {
            check(stage == Stage.READY && thresholdTestPassed) { "C_RECOVERY_ROTATION_INCOMPLETE" }
            val statuses =
                owner.usePrepared {
                    it.activate(nowMs).also { _ -> completedEvidence = it.evidence }
                }
            // Success ends the secret-bearing ceremony; only the signed public state stays visible.
            owner.close()
            stage = Stage.ACTIVE
            statuses
        }

    private fun clearPair() {
        descriptor = null
        visual = null
        expectedRole = null
        stage = Stage.READY
    }

    @Suppress("TooGenericExceptionCaught")
    private inline fun <T> checked(action: () -> T): T =
        try {
            check(stage != Stage.CLOSED) { "C_RECOVERY_SESSION_EXPIRED" }
            owner.usePrepared { it.userInteraction() }
            collection?.userInteraction()
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

    override fun close() {
        if (stage != Stage.CLOSED) {
            stage = Stage.CLOSED
            descriptor = null
            visual = null
            try {
                receiver?.close()
                collection?.close()
            } finally {
                owner.close()
            }
        }
    }
}
