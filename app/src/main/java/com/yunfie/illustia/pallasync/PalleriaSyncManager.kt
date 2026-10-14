package com.yunfie.illustia.pallasync

import android.content.Context
import android.os.Build
import com.yunfie.illustia.IllustiaApplication
import com.yunfie.illustia.models.Illust
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient

/**
 * Stable public PallaSync API.
 *
 * Legacy callers may continue constructing this type. Stateful work is routed to the single
 * application coordinator; tests and non-application contexts receive an isolated coordinator.
 */
class PalleriaSyncManager(
    client: OkHttpClient = OkHttpClient(),
    context: Context,
    coordinatorScope: CoroutineScope? = null,
) {
    private val appContext = context.applicationContext
    private val coordinator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (coordinatorScope == null) {
            (appContext as? IllustiaApplication)?.pallaSyncCoordinator
                ?: PalleriaSyncCoordinator(client, appContext)
        } else {
            PalleriaSyncCoordinator(client, appContext, coordinatorScope)
        }
    }
    internal val eventWriter: PallaSyncEventWriter =
        object : PallaSyncEventWriter {
            override suspend fun enqueueDataEvents(events: List<PallaSyncPendingEvent>): Boolean = coordinator.enqueueDataEvents(events)

            override suspend fun <T> enqueueDataEventsThen(
                events: List<PallaSyncPendingEvent>,
                afterEnqueue: suspend () -> T,
            ): T = coordinator.enqueueDataEventsThen(events, afterEnqueue)
        }

    companion object {
        val syncLogs = PalleriaSyncCoordinator.syncLogs

        fun log(message: String) = PalleriaSyncCoordinator.log(message)
    }

    fun startBackgroundSync() = coordinator.startBackgroundSync()

    fun stopBackgroundSync() = coordinator.stopBackgroundSync()

    suspend fun syncNow(): Boolean = coordinator.syncNow()

    internal suspend fun readFeedMembership(items: List<Illust>): PallaSyncFeedMembership? = coordinator.readFeedMembership(items)

    suspend fun getServerUrl(): String = coordinator.getServerUrl()

    fun normalizeServerUrl(rawUrl: String): String? = coordinator.normalizeServerUrl(rawUrl)

    fun getPallaSyncKeystore(): PallaSyncKeystore = coordinator.getPallaSyncKeystore()

    suspend fun recoverInterruptedActivation(): Boolean = coordinator.recoverInterruptedActivation()

    suspend fun generateGenesisMnemonic(): String = coordinator.generateGenesisMnemonic()

    suspend fun hasLocalChain(): Boolean = coordinator.hasLocalChain()

    suspend fun getPendingLocalEditCount(): Long = coordinator.getPendingLocalEditCount()

    internal suspend fun recoveryChainContext(): PallaSyncRecoveryChainContext? = coordinator.recoveryChainContext()

    internal suspend fun rotateRecoveredEpoch(
        completion: PallaSyncRecoveryCompletion,
        cancellation: PallaSyncRootCancellation,
    ): Boolean = coordinator.rotateRecoveredEpoch(completion, cancellation)

    internal suspend fun enqueueRecoveryAudit(
        chainId: String,
        event: PallaSyncPendingEvent,
    ): Boolean = coordinator.enqueueRecoveryAudit(chainId, event)

    suspend fun resumeBoundChain(): Boolean = coordinator.resumeBoundChain()

    suspend fun getEpochStatus(): PallaSyncEpochStatus? = coordinator.getEpochStatus()

    internal suspend fun createPeerDeviceOffer(): PallaSyncPeerJournal.Transcript = coordinator.createPeerDeviceOffer()

    suspend fun rotateEpoch(
        mnemonic: String,
        reason: String = "manual",
        revoked: List<String> = emptyList(),
        admin: Boolean = false,
        passphrase: String = "",
        cancellation: PallaSyncRootCancellation? = null,
    ): Boolean = coordinator.rotateEpoch(mnemonic, reason, revoked, admin, passphrase, cancellation)

    suspend fun refreshEpochs(
        mnemonic: String,
        passphrase: String = "",
        cancellation: PallaSyncRootCancellation? = null,
    ): Boolean = coordinator.refreshEpochs(mnemonic, passphrase, cancellation)

    suspend fun createGenesis(
        seedPhrase: String,
        serverUrl: String? = null,
        passphrase: String = "",
        cancellation: PallaSyncRootCancellation? = null,
    ): Boolean = coordinator.createGenesis(seedPhrase, serverUrl, passphrase, cancellation)

    suspend fun joinChain(
        seedPhrase: String,
        deviceName: String = Build.MODEL,
        serverUrl: String? = null,
        passphrase: String = "",
        cancellation: PallaSyncRootCancellation? = null,
    ): Boolean = coordinator.joinChain(seedPhrase, deviceName, serverUrl, passphrase, cancellation)

    suspend fun enqueueDataEvent(
        schema: String,
        entityId: String,
        operation: String,
        body: JsonElement,
    ): Boolean = coordinator.enqueueDataEvent(schema, entityId, operation, body)

    internal suspend fun enqueueDataEvents(events: List<PallaSyncPendingEvent>): Boolean = coordinator.enqueueDataEvents(events)

    suspend fun deleteChain(callApi: Boolean = true): Boolean = coordinator.deleteChain(callApi)

    internal suspend fun requestChainDeletion(
        mnemonic: String,
        passphrase: String = "",
    ): PallaSyncDeletionRequestResult = coordinator.requestChainDeletion(mnemonic, passphrase)

    internal suspend fun pendingDeletionProof(): String? = coordinator.pendingDeletionProof()

    internal suspend fun pendingDeletionEvidenceArchive(): String? = coordinator.pendingDeletionEvidenceArchive()

    internal suspend fun completeDeletionAfterExport(
        proof: String,
        explicitlyConfirmedOfflineCopy: Boolean,
    ): Boolean = coordinator.completeDeletionAfterExport(proof, explicitlyConfirmedOfflineCopy)

    suspend fun fetchDevices(
        serverUrl: String,
        chainId: String,
    ): Boolean = coordinator.fetchDevices(serverUrl, chainId)

    suspend fun processOutbox(serverUrl: String): Boolean = coordinator.processOutbox(serverUrl)

    suspend fun pushRecords(
        serverUrl: String,
        chainId: String,
        recordsJsonArray: String,
    ): Boolean = coordinator.pushRecords(serverUrl, chainId, recordsJsonArray)

    suspend fun getDeviceViewHistory(deviceId: String): List<Illust> = coordinator.getDeviceViewHistory(deviceId)

    suspend fun getDeviceHistoryPage(
        deviceId: String,
        cursor: com.yunfie.illustia.pallasync.data.HistoryCursor? = null,
    ): PallaSyncHistoryPage = coordinator.getDeviceHistoryPage(deviceId, cursor)
}
