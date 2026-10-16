package com.yunfie.illustia.pallasync

import android.content.Context
import android.os.Build
import com.yunfie.illustia.IllustiaApplication
import com.yunfie.illustia.models.Illust
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

    internal suspend fun publishSnapshot(): Boolean = coordinator.publishSnapshot()

    internal suspend fun prunePublishedLog(
        mnemonic: String,
        passphrase: String,
    ): Boolean = coordinator.prunePublishedLog(mnemonic, passphrase)

    internal suspend fun exportRelayArchive(output: java.io.OutputStream) = coordinator.exportRelayArchive(output)

    internal suspend fun repairRetainedRelayHistory(
        mnemonic: String,
        passphrase: String,
    ): PallaSyncRecordRepairSummary = coordinator.repairRetainedRelayHistory(mnemonic, passphrase)

    internal suspend fun stageReseedHistory(
        targetRelayUrl: String,
        mnemonic: String,
        passphrase: String,
    ): PallaSyncHttpResult<PallaSyncImportStageResponse.Staged> = coordinator.stageReseedHistory(targetRelayUrl, mnemonic, passphrase)

    internal suspend fun commitStagedReseedHistory(
        targetRelayUrl: String,
        mnemonic: String,
        passphrase: String,
    ): PallaSyncHttpResult<JsonObject> = coordinator.commitStagedReseedHistory(targetRelayUrl, mnemonic, passphrase)

    internal suspend fun pendingReseedCutoverTarget(): String? = coordinator.pendingReseedCutoverTarget()

    internal suspend fun pendingReseedCommitTarget(): String? = coordinator.pendingReseedCommitTarget()

    internal suspend fun pendingReseedSourceConflict(): PallaSyncReseedSourceConflict.Summary? = coordinator.pendingReseedSourceConflict()

    internal suspend fun resumeStagedReseedCutover(): Boolean = coordinator.resumeStagedReseedCutover()

    internal suspend fun readFeedMembership(items: List<Illust>): PallaSyncFeedMembership? = coordinator.readFeedMembership(items)

    suspend fun getServerUrl(): String = coordinator.getServerUrl()

    fun normalizeServerUrl(rawUrl: String): String? = coordinator.normalizeServerUrl(rawUrl)

    fun getPallaSyncKeystore(): PallaSyncKeystore = coordinator.getPallaSyncKeystore()

    suspend fun recoverInterruptedActivation(): Boolean = coordinator.recoverInterruptedActivation()

    suspend fun generateGenesisMnemonic(): String = coordinator.generateGenesisMnemonic()

    suspend fun hasLocalChain(): Boolean = coordinator.hasLocalChain()

    suspend fun getPendingLocalEditCount(): Long = coordinator.getPendingLocalEditCount()

    internal suspend fun recoveryChainContext(chainId: String? = null): PallaSyncRecoveryChainContext? =
        coordinator.recoveryChainContext(chainId)

    internal suspend fun rotateRecoveredEpoch(
        completion: PallaSyncRecoveryCompletion,
        cancellation: PallaSyncRootCancellation,
    ): Boolean = coordinator.rotateRecoveredEpoch(completion, cancellation)

    internal suspend fun advanceNewRootMigrationPage(
        sourceChain: String,
        targetChain: String,
    ): NewRootMigrationCheckpoint = coordinator.advanceNewRootMigrationPage(sourceChain, targetChain)

    internal suspend fun newRootMigrationProgress(targetChain: String): NewRootMigrationProgress? =
        coordinator.newRootMigrationProgress(targetChain)

    internal suspend fun activeNewRootMigrationTarget(): String? = coordinator.activeNewRootMigrationTarget()

    internal suspend fun newRootMigrationSources(): List<String> = coordinator.newRootMigrationSources()

    internal suspend fun verifyNewRootMigrationState(
        sourceChain: String,
        targetChain: String,
    ): NewRootMigrationProgress = coordinator.verifyNewRootMigrationState(sourceChain, targetChain)

    internal suspend fun requestNewRootSourceDeletion(
        sourceChain: String,
        targetChain: String,
        mnemonic: String,
        passphrase: String = "",
    ): PallaSyncDeletionRequestResult =
        coordinator.requestNewRootSourceDeletion(
            sourceChain,
            targetChain,
            mnemonic,
            passphrase,
        )

    internal suspend fun requestNewRootSourceDeletionFromRecovery(
        sourceChain: String,
        targetChain: String,
        completion: PallaSyncRecoveryCompletion,
        cancellation: PallaSyncRootCancellation,
    ): PallaSyncDeletionRequestResult =
        coordinator.requestNewRootSourceDeletionFromRecovery(sourceChain, targetChain, completion, cancellation)

    internal suspend fun pendingNewRootSourceDeletionProof(
        sourceChain: String,
        targetChain: String,
    ): String? = coordinator.pendingNewRootSourceDeletionProof(sourceChain, targetChain)

    internal suspend fun completeNewRootSourceDeletionAfterExport(
        sourceChain: String,
        targetChain: String,
        exportedProof: String,
        explicitlyConfirmedOfflineCopy: Boolean,
    ): Boolean =
        coordinator.completeNewRootSourceDeletionAfterExport(
            sourceChain,
            targetChain,
            exportedProof,
            explicitlyConfirmedOfflineCopy,
        )

    internal suspend fun enqueueRecoveryAudit(
        chainId: String,
        event: PallaSyncPendingEvent,
    ): Boolean = coordinator.enqueueRecoveryAudit(chainId, event)

    suspend fun resumeBoundChain(): Boolean = coordinator.resumeBoundChain()

    suspend fun getEpochStatus(): PallaSyncEpochStatus? = coordinator.getEpochStatus()

    internal suspend fun createPeerDeviceOffer(): PallaSyncPeerJournal.Transcript = coordinator.createPeerDeviceOffer()

    internal suspend fun peerAuthorityForOffer(transcript: PallaSyncPeerJournal.Transcript): String =
        coordinator.peerAuthorityForOffer(transcript)

    internal suspend fun pollPeerEnrollmentRequest(transcript: PallaSyncPeerJournal.Transcript): kotlinx.serialization.json.JsonObject? =
        coordinator.pollPeerEnrollmentRequest(transcript)

    internal suspend fun beginPeerEnrollmentApproval(
        transcript: PallaSyncPeerJournal.Transcript,
        item: kotlinx.serialization.json.JsonObject,
    ): PallaSyncPeerApprovalWork = coordinator.beginPeerEnrollmentApproval(transcript, item)

    internal suspend fun confirmPeerEnrollmentSas(work: PallaSyncPeerApprovalWork): PallaSyncPeerJournal.Transcript =
        coordinator.confirmPeerEnrollmentSas(work)

    internal suspend fun cancelPeerEnrollmentApproval(work: PallaSyncPeerApprovalWork) = coordinator.cancelPeerEnrollmentApproval(work)

    internal suspend fun approvePeerEnrollment(
        work: PallaSyncPeerApprovalWork,
        submittedBody: String,
    ): PallaSyncPeerJournal.Transcript = coordinator.approvePeerEnrollment(work, submittedBody)

    internal suspend fun retryPeerEnrollmentApproval(transcript: PallaSyncPeerJournal.Transcript): PallaSyncPeerJournal.Transcript =
        coordinator.retryPeerEnrollmentApproval(transcript)

    internal suspend fun peerAcceptanceTransfer(transcript: PallaSyncPeerJournal.Transcript): String =
        coordinator.peerAcceptanceTransfer(transcript)

    internal suspend fun beginPeerSubjectEnrollment(
        offerJson: String,
        authorityJson: String,
        configuredOrigin: String,
    ): PallaSyncPeerSubjectEnrollment.Work = coordinator.beginPeerSubjectEnrollment(offerJson, authorityJson, configuredOrigin)

    internal suspend fun confirmPeerSubjectSas(
        work: PallaSyncPeerSubjectEnrollment.Work,
        observed: String,
    ) = coordinator.confirmPeerSubjectSas(work, observed)

    internal suspend fun preparePeerSubjectEnrollment(
        work: PallaSyncPeerSubjectEnrollment.Work,
        certificateJson: String,
        envelopeJson: String,
        deviceName: String,
    ): PallaSyncPeerSubjectEnrollment.Candidate = coordinator.preparePeerSubjectEnrollment(work, certificateJson, envelopeJson, deviceName)

    internal suspend fun completePeerSubjectEnrollment(acceptanceJson: String): Pair<String, okhttp3.HttpUrl> =
        coordinator.completePeerSubjectEnrollment(acceptanceJson)

    internal suspend fun pendingPeerSubjectEnrollment(origin: String): PallaSyncPeerSubjectEnrollment.Candidate? =
        coordinator.pendingPeerSubjectEnrollment(origin)

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

    internal suspend fun refreshDeletedChainEvidence(
        chainId: String,
        mnemonic: String,
        passphrase: String = "",
    ): String? = coordinator.refreshDeletedChainEvidence(chainId, mnemonic, passphrase)

    internal suspend fun retainedDeletedChainTargets() = coordinator.retainedDeletedChainTargets()

    internal suspend fun previewDeletionIntentWitness(archive: String): PallaSyncDeletionWitnessPreview? =
        coordinator.previewDeletionIntentWitness(archive)

    internal suspend fun addDeletionIntentWitness(
        archive: String,
        explicitlyConfirmed: Boolean,
    ): String? = coordinator.addDeletionIntentWitness(archive, explicitlyConfirmed)

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

    internal suspend fun getClientStatus(): PallaSyncClientStatus? = coordinator.getClientStatus()

    internal suspend fun getUnresolvedQuarantinePage(
        afterHash: String = "",
        limit: Int = 50,
    ): PallaSyncQuarantinePage = coordinator.getUnresolvedQuarantinePage(afterHash, limit)

    internal suspend fun retryMissingDependencyQuarantine(objectHash: String): Boolean =
        coordinator.retryMissingDependencyQuarantine(objectHash)

    internal suspend fun trustedSnapshotCandidates(): List<PallaSyncTrustedSnapshotCandidate> = coordinator.trustedSnapshotCandidates()

    internal suspend fun trustAndInstallSnapshot(
        compactionId: String,
        compactionHash: String,
        explicitlyConfirmed: Boolean,
    ): Boolean = coordinator.trustAndInstallSnapshot(compactionId, compactionHash, explicitlyConfirmed)

    internal suspend fun fetchLegacyV21Parameters(
        sourceUrl: String,
        chainId: String,
        seedPhrase: String,
        passphrase: String,
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.Parameters> =
        coordinator.fetchLegacyV21Parameters(sourceUrl, chainId, seedPhrase, passphrase)

    internal suspend fun fetchLegacyV21Devices(
        sourceUrl: String,
        parameters: PallaSyncLegacyV21Source.Parameters,
        deviceId: String,
        deviceSeed: String,
        devicePublicKey: String,
    ): PallaSyncHttpResult<List<PallaSyncLegacyV21Source.Device>> =
        coordinator.fetchLegacyV21Devices(sourceUrl, parameters, deviceId, deviceSeed, devicePublicKey)

    internal suspend fun fetchLegacyV21RecordPage(
        sourceUrl: String,
        chainId: String,
        afterSequence: Long,
        devices: List<PallaSyncLegacyV21Source.Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.RecordPage> =
        coordinator.fetchLegacyV21RecordPage(
            sourceUrl,
            chainId,
            afterSequence,
            devices,
            signerDeviceId,
            signerDeviceSeed,
        )

    internal suspend fun inspectFrozenLegacyV21Export(
        sourceUrl: String,
        parameters: PallaSyncLegacyV21Source.Parameters,
        devices: List<PallaSyncLegacyV21Source.Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
        signerDevicePublicKey: String,
        freezeConfirmed: Boolean,
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.FrozenExportProof> =
        coordinator.inspectFrozenLegacyV21Export(
            sourceUrl,
            parameters,
            devices,
            signerDeviceId,
            signerDeviceSeed,
            signerDevicePublicKey,
            freezeConfirmed,
        )

    internal suspend fun fetchLegacyV20Records(
        sourceUrl: String,
        chainId: String,
    ): PallaSyncHttpResult<List<PallaSyncLegacyV20Source.Record>> = coordinator.fetchLegacyV20Records(sourceUrl, chainId)

    internal suspend fun beginLegacyV21Migration(
        migrationId: String,
        sourceUrl: String,
        seedPhrase: String,
        passphrase: String,
        parameters: PallaSyncLegacyV21Source.Parameters,
        devices: List<PallaSyncLegacyV21Source.Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
        signerDevicePublicKey: String,
        targetChain: String,
        freezeConfirmed: Boolean,
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.FrozenExportProof> =
        coordinator.beginLegacyV21Migration(
            migrationId,
            sourceUrl,
            seedPhrase,
            passphrase,
            parameters,
            devices,
            signerDeviceId,
            signerDeviceSeed,
            signerDevicePublicKey,
            targetChain,
            freezeConfirmed,
        )

    internal suspend fun beginLegacyV20Migration(
        migrationId: String,
        sourceUrl: String,
        seedPhrase: String,
        passphrase: String,
        sourceChain: String,
        targetChain: String,
        freezeConfirmed: Boolean,
    ): PallaSyncHttpResult<PallaSyncLegacyV20Source.FrozenExportProof> =
        coordinator.beginLegacyV20Migration(
            migrationId,
            sourceUrl,
            seedPhrase,
            passphrase,
            sourceChain,
            targetChain,
            freezeConfirmed,
        )

    internal suspend fun revalidateLegacyV21MigrationSource(
        migrationId: String,
        sourceUrl: String,
        seedPhrase: String,
        passphrase: String,
        parameters: PallaSyncLegacyV21Source.Parameters,
        devices: List<PallaSyncLegacyV21Source.Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
        signerDevicePublicKey: String,
        targetChain: String,
        freezeConfirmed: Boolean,
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.FrozenExportProof> =
        coordinator.revalidateLegacyV21MigrationSource(
            migrationId,
            sourceUrl,
            seedPhrase,
            passphrase,
            parameters,
            devices,
            signerDeviceId,
            signerDeviceSeed,
            signerDevicePublicKey,
            targetChain,
            freezeConfirmed,
        )

    internal suspend fun legacyMigrationProgress(migrationId: String): PallaSyncLocalIntents.LegacyMigrationProgress? =
        coordinator.legacyMigrationProgress(migrationId)

    internal suspend fun verifyLegacyMigrationSnapshot(migrationId: String): PallaSyncLocalIntents.LegacyMigrationProgress =
        coordinator.verifyLegacyMigrationSnapshot(migrationId)

    internal suspend fun legacyMigrationProgressPage(
        afterMigrationId: String = "",
        limit: Int = 20,
    ): PallaSyncLocalIntents.LegacyProgressPage = coordinator.legacyMigrationProgressPage(afterMigrationId, limit)

    internal suspend fun legacyMigrationDecisionPage(
        migrationId: String,
        afterIndex: Long,
        limit: Int = 200,
    ): PallaSyncLocalIntents.LegacyDecisionPage = coordinator.legacyMigrationDecisionPage(migrationId, afterIndex, limit)

    internal suspend fun legacyMigrationSourceEvidencePage(
        migrationId: String,
        afterSequence: Long,
        limit: Int = 200,
    ): PallaSyncLocalIntents.LegacySourceEvidencePage = coordinator.legacyMigrationSourceEvidencePage(migrationId, afterSequence, limit)

    internal suspend fun legacyMigrationCandidatePage(
        migrationId: String,
        afterIndex: Long,
        limit: Int = 200,
    ): PallaSyncLocalIntents.LegacyCandidatePage = coordinator.legacyMigrationCandidatePage(migrationId, afterIndex, limit)

    internal suspend fun chooseLegacyMigrationCandidate(
        migrationId: String,
        groupKey: String,
        entryIndex: Long,
    ) = coordinator.chooseLegacyMigrationCandidate(migrationId, groupKey, entryIndex)

    internal suspend fun finalizeLegacyMigrationChoices(migrationId: String) = coordinator.finalizeLegacyMigrationChoices(migrationId)

    internal suspend fun resumeLegacyV21Migration(
        migrationId: String,
        sourceUrl: String,
        seedPhrase: String,
        passphrase: String,
        parameters: PallaSyncLegacyV21Source.Parameters,
        devices: List<PallaSyncLegacyV21Source.Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
        signerDevicePublicKey: String,
        targetChain: String,
        freezeConfirmed: Boolean,
    ): PallaSyncHttpResult<Unit> =
        coordinator.resumeLegacyV21Migration(
            migrationId,
            sourceUrl,
            seedPhrase,
            passphrase,
            parameters,
            devices,
            signerDeviceId,
            signerDeviceSeed,
            signerDevicePublicKey,
            targetChain,
            freezeConfirmed,
        )
}
