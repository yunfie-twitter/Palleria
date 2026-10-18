package com.yunfie.illustia.pallasync

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Build
import com.yunfie.illustia.GlitchTipTelemetry
import com.yunfie.illustia.GlitchTipTelemetry.span
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.OutboxEntity
import com.yunfie.illustia.pallasync.data.PALLASYNC_SEARCH_SAFETY_LIMIT
import com.yunfie.illustia.pallasync.data.PALLASYNC_SEEN_SAFETY_LIMIT
import com.yunfie.illustia.pallasync.data.PALLASYNC_VIEWED_SAFETY_LIMIT
import com.yunfie.illustia.pallasync.data.PallaSyncDeviceEntity
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import com.yunfie.illustia.pallasync.data.QuarantineEvidenceEntity
import com.yunfie.illustia.pallasync.data.ReseedJournalEntity
import com.yunfie.illustia.pallasync.data.SignedEventEntity
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.store.PALLA_SYNC_SERVER_URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val MAX_PROTOCOL_COUNTER = 4_294_967_295L
private const val MAX_EXPORT_CHUNK_BYTES = 8_388_608
private const val MAX_DELETION_WITNESSES = 128
private const val MAX_SNAPSHOT_UPLOAD_RECORDS = 64
private val LEGACY_QUARANTINE_CODES = setOf("C_AEAD_FAILURE", "C_SIGNATURE_INVALID", "C_SCHEMA_VIOLATION", "C_UNSUPPORTED_SCHEMA")

internal data class PallaSyncRecordRepairSummary(
    val submitted: Int,
    val accepted: Int,
    val rejectedCodes: List<String>,
    val repairRequired: Boolean,
)

private data class DeletionContext(
    val state: ChainStateEntity,
    val parametersHash: String,
    val epochHash: String,
    val authorityText: String,
    val admin: String,
    val base: HttpUrl,
    val origin: String,
    val logId: String,
    val relayKeyId: String,
)

private data class RepairBatchResult(
    val submitted: Int,
    val accepted: Int,
    val rejectedCodes: List<String>,
    val repairRequired: Boolean,
) {
    operator fun plus(other: RepairBatchResult) =
        RepairBatchResult(
            submitted + other.submitted,
            accepted + other.accepted,
            rejectedCodes + other.rejectedCodes,
            repairRequired || other.repairRequired,
        )
}

private data class SnapshotPublicationContext(
    val state: ChainStateEntity,
    val authority: JsonObject,
    val authorityText: String,
    val keys: PallaSyncKeySnapshot,
    val base: HttpUrl,
    val origin: String,
    val pin: JsonObject,
    val compactionId: String,
    val journal: PallaSyncSnapshotPublicationJournal,
)

private data class TrustedSnapshotBootstrapContext(
    val keys: PallaSyncKeySnapshot,
    val checkpoint: PallaSyncGenesisCheckpoint,
    val history: PallaSyncInitialHistory,
    val candidate: PallaSyncTrustedSnapshotCandidate,
    val compaction: JsonObject,
    val epoch: Long,
)

/** Application-scoped implementation of PallaSync state transitions and polling. */
internal enum class PallaSyncDeletionRequestResult {
    PROOF_READY_FOR_EXPORT,
    PENDING_EVIDENCE,
    FAILED,
}

internal data class PallaSyncDeletionWitnessPreview(
    val chainId: String,
    val deletionId: String,
    val receiptHash: String,
    val assurance: String,
    val requestedAtMs: String,
    val existingWitnessCount: Int,
)

internal class PalleriaSyncCoordinator(
    client: OkHttpClient = OkHttpClient(),
    context: Context,
    private val coordinatorScope: CoroutineScope? = null,
    suppliedPolicy: PallaSyncPolicyGate? = null,
    suppliedStore: PallaSyncLocalStore? = null,
    private val rebaseFault: (PallaSyncOutboxRebase.Boundary) -> Unit = {},
) : PallaSyncEventWriter {
    private val appContext = context.applicationContext
    private val scope = coordinatorScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val jobLock = Any()

    private val policy by lazy { suppliedPolicy ?: PallaSyncPolicyGate(appContext) }
    private val remote by lazy { PallaSyncRemoteService(client, policy) }
    private val localStore = suppliedStore ?: PallaSyncLocalStore(appContext)
    private val db = localStore
    private val crypto by lazy { PallaSyncCryptoService(policy) }
    private val keystore by lazy { PallaSyncKeystore(appContext) }
    private val recoveryAudits by lazy { PallaSyncRecoverySetStore(appContext).auditQueue }
    private val consistencyProbe by lazy {
        PallaSyncConsistencyProbe(db, remote, policy, keystore, ::makeCapabilityToken, ::resolvePruneCompaction)
    }
    private val projection = PallaSyncProjection(appContext)
    private val localAuthor by lazy { PallaSyncLocalAuthor(appContext, db) }
    private val localIntents by lazy { PallaSyncLocalIntents(db, localAuthor, ::orderLocalEvents) }
    private val membership by lazy { PallaSyncMembershipReader(db) }
    private val liveHistory by lazy { PallaSyncLiveHistory(db, remote, keystore, consistencyProbe, ::makeCapabilityToken) }
    private val legacyV21Source by lazy { PallaSyncLegacyV21Source(remote) }
    private val epochLifecycle by lazy {
        PallaSyncEpochLifecycle(db, keystore, remote, policy, crypto, PallaSyncEpochJournal(appContext), localAuthor)
    }
    private val outboxRebase by lazy { PallaSyncOutboxRebase(db, localAuthor, keystore, rebaseFault) }
    private val epochStatus by lazy { PallaSyncEpochStatusReader(db, keystore, policy.retry::correctedTime) }
    private val peerJournal by lazy { PallaSyncPeerJournal(appContext) }
    private val peerApprover by lazy {
        PallaSyncPeerApprover(db, policy, keystore, peerJournal, PallaSyncPeerRelay(db, remote, crypto))
    }
    private val peerSubject by lazy { PallaSyncPeerSubjectEnrollment(db, remote, policy, keystore, crypto) }
    private val json =
        Json {
            ignoreUnknownKeys = true
        }
    private var relayLimits = PALLASYNC_HARD_LIMITS
    private var nextProbeAt = 0L
    private var foregroundRevision = -1L

    @Volatile
    private var backgroundSyncJob: Job? = null

    suspend fun readFeedMembership(items: List<Illust>): PallaSyncFeedMembership? = membership.read(items)

    internal suspend fun recoveryChainContext(): PallaSyncRecoveryChainContext? = recoveryChainContext(chainId = null)

    internal suspend fun recoveryChainContext(chainId: String?): PallaSyncRecoveryChainContext? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state =
                    if (chainId == null) {
                        db.dao.getActiveChainState()
                    } else {
                        db.dao.getChainState(chainId)
                    } ?: return@withLock null
                requireProtectedAuthority(state.chainId)
                val authority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
                val parameters = authority.getValue("parameters").jsonObject
                PallaSyncRecoveryChainContext(
                    state.chainId,
                    parameters.getValue("admin_public_key").jsonPrimitive.content,
                    parameters
                        .getValue("generation")
                        .jsonPrimitive.content
                        .toLong(),
                    checkNotNull(keystore.boundDeviceId(state.chainId)) { "C_MISSING_DEPENDENCY" },
                )
            }
        }

    companion object {
        private const val DEFAULT_SERVER_URL = "https://api.yunfi.f5.si"
        private const val NORMAL_POLL_DELAY_MS = 5_000L
        private const val PROTOCOL_ERROR_DELAY_MS = 30_000L
        private const val PROBE_INTERVAL_MS = 15 * 60_000L
        private const val PROBE_JITTER_MS = PROBE_INTERVAL_MS / 5
        private const val RESEED_CUTOVER_JOURNAL_PHASE = 6
        private const val RESEED_COMPLETE_JOURNAL_PHASE = 7
        private val JSON_MEDIA_TYPE = PALLASYNC_MEDIA_TYPE.toMediaType()

        val syncLogs = MutableStateFlow<List<String>>(emptyList())

        fun log(message: String) {
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            syncLogs.update { current ->
                (listOf("[$time] $message") + current).take(100)
            }
        }
    }

    fun startBackgroundSync() {
        synchronized(jobLock) {
            if (backgroundSyncJob?.isActive == true) {
                return
            }
            nextProbeAt = 0L
            backgroundSyncJob =
                scope.launch {
                    while (currentCoroutineContext().isActive) {
                        val outcome =
                            runCatching {
                                operationMutex.withLock { synchronizeOnceLocked() }
                            }.getOrElse { expectedFailure ->
                                val error = expectedFailure
                                log(
                                    error.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) }
                                        ?: "PallaSync cycle failed; retained state requires another check",
                                )
                                if (error.message ==
                                    "C_RELAY_POLICY_UNAVAILABLE"
                                ) {
                                    SyncCycleOutcome.Retryable
                                } else {
                                    SyncCycleOutcome.ProtocolError
                                }
                            }

                        val waitMs =
                            when (outcome) {
                                SyncCycleOutcome.Success,
                                SyncCycleOutcome.Idle,
                                -> {
                                    NORMAL_POLL_DELAY_MS
                                }

                                SyncCycleOutcome.Retryable -> {
                                    val base = PallaSyncUrls.normalize(getServerUrl())
                                    if (base is PallaSyncHttpResult.Success) {
                                        policy.retry.remaining(PallaSyncUrls.extractOrigin(base.value)).coerceAtLeast(1)
                                    } else {
                                        NORMAL_POLL_DELAY_MS
                                    }
                                }

                                SyncCycleOutcome.ProtocolError -> {
                                    PROTOCOL_ERROR_DELAY_MS
                                }

                                SyncCycleOutcome.Gone -> {
                                    break
                                }
                            }
                        delay(waitMs)
                    }
                }
        }
    }

    fun stopBackgroundSync() {
        synchronized(jobLock) {
            backgroundSyncJob?.cancel()
            backgroundSyncJob = null
        }
    }

    /** Runs one complete push/devices/paged-pull cycle immediately. */
    suspend fun syncNow(): Boolean {
        val outcome =
            runCatching {
                withContext(Dispatchers.IO) {
                    operationMutex.withLock { synchronizeOnceLocked() }
                }
            }.getOrElse { expectedFailure ->
                val error = expectedFailure
                log("Manual sync failed without clearing local chain data: ${error.message}")
                return false
            }
        return outcome == SyncCycleOutcome.Success || outcome == SyncCycleOutcome.Idle
    }

    /** Explicit §10.11–§10.12 publication; it never prunes the event log. */
    suspend fun publishSnapshot(): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val publication = prepareSnapshotPublication()
                    val produced = produceSnapshotParts(publication)
                    publishSnapshotCompaction(publication, produced)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IllegalStateException) {
                    val code = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_SNAPSHOT_INVALID"
                    log("Snapshot publication paused with exact local parts retained: $code")
                    false
                } catch (failure: IllegalArgumentException) {
                    log("Snapshot publication paused with exact local parts retained: ${failure.message}")
                    false
                }
            }
        }

    /** Explicit §10.12/§16.7 pruning, bound to a locally verified complete publication. */
    suspend fun prunePublishedLog(
        mnemonic: String,
        passphrase: String,
    ): Boolean =
        withEpochRoot(mnemonic, passphrase, null, PallaSyncRootSession.Operation.PRUNE) { root, base ->
            val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            check(state.writeState == "active" && state.initialPullCompleted) { "C_WRITE_PAUSED" }
            val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_MISSING_DEPENDENCY" }
            val authority = Json.parseToJsonElement(authorityText).jsonObject
            val origin = PallaSyncUrls.extractOrigin(base)
            policy.requireCurrent(origin)
            requireSnapshotResult(consistencyProbe.run(base, state.chainId))
            val stable = checkNotNull(db.pallaSyncDao().getChainState(state.chainId)) { "C_MISSING_DEPENDENCY" }
            check(
                stable.logId == state.logId && stable.generation == state.generation &&
                    stable.verifiedSeq == state.verifiedSeq && stable.verifiedHash == state.verifiedHash,
            ) { "C_MISSING_DEPENDENCY" }
            val compaction =
                checkNotNull(
                    PallaSyncRetentionHandoff(db).latestVerifiedPublishedCompaction(state.chainId, authority),
                ) { "C_MISSING_DEPENDENCY" }
            val coveredThrough = compaction.getValue("covered_through_seq").jsonPrimitive.long
            check(coveredThrough <= stable.verifiedSeq) { "C_MISSING_DEPENDENCY" }
            val compactionId = compaction.getValue("compaction_id").jsonPrimitive.content
            val logId = checkNotNull(stable.logId)
            val relayKeyId =
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(policy.trustedRelayKey(origin))),
                )
            val target =
                buildJsonObject {
                    put("compaction_id", compactionId)
                    put("through_seq", coveredThrough)
                }
            val saved =
                db
                    .protocolDao()
                    .evidenceAt(origin, relayKeyId, state.chainId, logId, "prune_request", coveredThrough)
                    .firstOrNull { value ->
                        runCatching {
                            Json.parseToJsonElement(value).jsonObject.getValue("target") == target
                        }.getOrDefault(false)
                    }
            val adminOperation =
                if (saved != null) {
                    Json
                        .parseToJsonElement(saved)
                        .jsonObject
                        .getValue("admin_operation")
                        .jsonObject
                } else {
                    val operation =
                        Json
                            .parseToJsonElement(
                                root.createPruneAdminOperation(
                                    authority.getValue("parameters").toString(),
                                    authority
                                        .getValue("epochs")
                                        .jsonObject
                                        .getValue(stable.keyEpoch.toString())
                                        .jsonObject
                                        .getValue("record")
                                        .toString(),
                                    compaction.toString(),
                                    coveredThrough,
                                    policy.retry.correctedTime(origin),
                                ),
                            ).jsonObject
                    val requestText =
                        buildJsonObject {
                            put("target", target)
                            put("admin_operation", operation)
                        }.toString()
                    val evidence =
                        ProtectedEvidenceEntity(
                            origin,
                            relayKeyId,
                            state.chainId,
                            logId,
                            "prune_request",
                            coveredThrough,
                            checkNotNull(PallaSyncCore.objectHash(requestText)),
                            requestText,
                        )
                    db.protocolDao().retainEvidence(evidence)
                    check(
                        db
                            .protocolDao()
                            .evidenceAt(
                                origin,
                                relayKeyId,
                                state.chainId,
                                logId,
                                "prune_request",
                                coveredThrough,
                            ).contains(requestText),
                    ) { "C_LOCAL_STORAGE_FULL" }
                    operation
                }
            val relay =
                PallaSyncLogPruneRelay(
                    remote,
                    base,
                    state.chainId,
                    { url, _, method, path, query, body ->
                        root.token(PallaSyncUrls.extractOrigin(url), method, path, query, body.toByteArray(Charsets.UTF_8))
                    },
                ) { _, _, response ->
                    check(response.getValue("min_available_seq").jsonPrimitive.long == coveredThrough + 1L) {
                        "C_ACCEPTANCE_UNPROVEN"
                    }
                    requireSnapshotResult(consistencyProbe.run(base, state.chainId))
                }
            relay.prune(compactionId, coveredThrough, adminOperation) is PallaSyncHttpResult.Success
        }

    private suspend fun prepareSnapshotPublication(): SnapshotPublicationContext {
        val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        requireProtectedAuthority(state.chainId)
        check(state.writeState == "active") { "C_WRITE_PAUSED" }
        val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_MISSING_DEPENDENCY" }
        val authority = Json.parseToJsonElement(authorityText).jsonObject
        val keys = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        validateSnapshotPublisher(state, authority, keys)
        val base =
            when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                is PallaSyncHttpResult.Success -> normalized.value
                else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
            }
        val origin = PallaSyncUrls.extractOrigin(base)
        check(
            authority
                .getValue("pin")
                .jsonObject
                .getValue("origin")
                .jsonPrimitive.content == origin,
        ) {
            "C_BOOTSTRAP_TRUST_REQUIRED"
        }
        policy.requireCurrent(origin)
        requireSnapshotResult(refreshRelayHealth(base))
        requireSnapshotResult(consistencyProbe.run(base, state.chainId))
        val pin =
            buildJsonObject {
                put("origin", origin)
                put("chain_id", state.chainId)
                put("public_key", policy.trustedRelayKey(origin))
                put("log_id", checkNotNull(state.logId))
            }
        val resumable = db.snapshotDatabase().snapshotPublicationDao().resumablePublications(state.chainId)
        check(resumable.size <= 1) { "C_LOCAL_LIMIT" }
        val compactionId = resumable.singleOrNull()?.compactionId ?: UUID.randomUUID().toString()
        return SnapshotPublicationContext(
            state,
            authority,
            authorityText,
            keys,
            base,
            origin,
            pin,
            compactionId,
            PallaSyncSnapshotPublicationJournal(db.snapshotDatabase(), state.chainId, compactionId, state.keyEpoch),
        )
    }

    private fun validateSnapshotPublisher(
        state: ChainStateEntity,
        authority: JsonObject,
        keys: PallaSyncKeySnapshot,
    ) {
        check(keys.chainId == state.chainId && keys.epoch == state.keyEpoch) { "C_EPOCH_FORK" }
        val deviceId = checkNotNull(keys.deviceId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val epoch =
            authority
                .getValue("epochs")
                .jsonObject
                .getValue(state.keyEpoch.toString())
                .jsonObject
                .getValue("record")
                .jsonObject
        val admission = checkNotNull(authority.getValue("devices").jsonObject[deviceId]?.jsonObject) { "C_MISSING_DEPENDENCY" }
        val record = admission.getValue("record").jsonObject
        check(
            epoch.getValue("members").jsonArray.any { it.jsonPrimitive.content == deviceId } &&
                epoch.getValue("generation").jsonPrimitive.long == state.generation &&
                admission.getValue("revoked_seq").jsonPrimitive.content == "null" &&
                record.getValue("generation").jsonPrimitive.long == state.generation &&
                record.getValue("device_public_key").jsonPrimitive.content == keys.publicKeyBase64Url,
        ) { "C_DEVICE_REVOKED" }
    }

    private suspend fun produceSnapshotParts(context: SnapshotPublicationContext): PallaSyncProducedSnapshot {
        val state = context.state
        val keys = context.keys
        val journal = context.journal
        val pending = ArrayList<JsonObject>()
        var transparencyCheckedForSavedParts = false
        var nextPartIndex = 0
        PallaSyncEpochSession.begin(keysForChainLocked(state.chainId) ?: keys, context.authorityText, state.keyEpoch).use { epochSession ->
            val relay =
                PallaSyncSnapshotPartRelay(
                    remote,
                    context.base,
                    state.chainId,
                    context.pin,
                    relayLimits,
                    ::makeCapabilityToken,
                ) { records, response ->
                    requireSnapshotResult(consistencyProbe.run(context.base, state.chainId))
                    journal.verifyAndRetainUploadReceipts(records, response) { receipt ->
                        verifySnapshotReceiptInclusion(state.chainId, context.authority, receipt)
                    }
                }

            suspend fun flushParts() {
                if (pending.isEmpty()) return
                val batch = pending.toList()
                requireSnapshotResult(relay.upload(batch))
                pending.clear()
            }
            val produced =
                PallaSyncSnapshotProducer(db.snapshotDatabase()).produce(
                    chainId = state.chainId,
                    compactionId = context.compactionId,
                    deviceId = checkNotNull(keys.deviceId),
                    epochSession = epochSession,
                    signingSeed = { Base64.getUrlDecoder().decode(keys.signingKeyBase64Url) },
                    devicePublicKeyBase64Url = keys.publicKeyBase64Url,
                    journal = journal,
                ) { _, recordJson, _ ->
                    val partIndex = nextPartIndex++
                    val record = Json.parseToJsonElement(recordJson).jsonObject
                    val savedReceipt = journal.prepared(partIndex)?.uploadReceiptJson
                    if (savedReceipt != null) {
                        flushParts()
                        if (!transparencyCheckedForSavedParts) {
                            requireSnapshotResult(consistencyProbe.run(context.base, state.chainId))
                            transparencyCheckedForSavedParts = true
                        }
                        verifySnapshotReceiptInclusion(
                            state.chainId,
                            context.authority,
                            Json.parseToJsonElement(savedReceipt).jsonObject,
                        )
                    } else {
                        pending += record
                        check(recordUploadBatchSize(pending.map { it.toString() }, relayLimits) == pending.size) {
                            "C_LOCAL_LIMIT"
                        }
                        if (pending.size >= minOf(MAX_SNAPSHOT_UPLOAD_RECORDS, relayLimits.maxRecordsPerUpload)) flushParts()
                    }
                }
            flushParts()
            return produced
        }
    }

    private suspend fun publishSnapshotCompaction(
        context: SnapshotPublicationContext,
        produced: PallaSyncProducedSnapshot,
    ): Boolean {
        val keys = context.keys
        val unsigned = snapshotCompactionUnsigned(context, produced)
        val signed =
            PallaSyncDeviceSession.beginToken(keys.signingKeyBase64Url).use { deviceSession ->
                Json
                    .parseToJsonElement(
                        deviceSession.signCompaction(unsigned.toString(), checkNotNull(keys.deviceId), keys.publicKeyBase64Url),
                    ).jsonObject
            }
        val signedText = checkNotNull(PallaSyncCore.canonicalJson(signed.toString())) { "C_SNAPSHOT_INVALID" }
        context.journal.finalize(produced.stateCommitment, signedText)
        val relay =
            PallaSyncSnapshotCompactionRelay(
                remote,
                context.base,
                context.state.chainId,
                context.pin,
                ::makeCapabilityToken,
            ) { compaction, response ->
                acceptPublishedSnapshot(context, compaction, response, signedText)
            }
        requireSnapshotResult(relay.publish(signed))
        expireHistoricalEpochKeys(context.state.chainId, context.origin)
        return true
    }

    private fun snapshotCompactionUnsigned(
        context: SnapshotPublicationContext,
        produced: PallaSyncProducedSnapshot,
    ): JsonObject {
        val parts =
            JsonArray(
                produced.parts.map { part ->
                    buildJsonObject {
                        put("record_id", part.recordId)
                        put("record_hash", part.recordHash)
                    }
                },
            )
        return buildJsonObject {
            put("compaction_id", context.compactionId)
            put("chain_id", context.state.chainId)
            put("generation", produced.header.getValue("generation"))
            put("device_id", checkNotNull(context.keys.deviceId))
            put("log_id", produced.header.getValue("log_id"))
            put("covered_through_seq", produced.header.getValue("covered_through_seq"))
            put("covered_log_hash", produced.header.getValue("covered_log_hash"))
            put("epoch_hash", produced.header.getValue("epoch_hash"))
            put("snapshot_parts", parts)
            put("state_sha256", produced.stateCommitment)
        }
    }

    private suspend fun acceptPublishedSnapshot(
        context: SnapshotPublicationContext,
        compaction: JsonObject,
        response: JsonObject,
        signedText: String,
    ) {
        val chain = context.state.chainId
        requireSnapshotResult(liveHistory.pull(context.base, chain, context.keys))
        val receipt =
            response
                .getValue("upload_receipts")
                .jsonArray
                .single()
                .jsonObject
        verifySnapshotReceiptInclusion(chain, context.authority, receipt)
        val entry =
            checkNotNull(
                db.protocolDao().logEntry(
                    chain,
                    checkNotNull(context.state.logId),
                    receipt.getValue("relay_seq").jsonPrimitive.long,
                ),
            ) { "C_ACCEPTANCE_UNPROVEN" }
        check(
            PallaSyncCore.canonicalJson(entry.signedJson) ==
                PallaSyncCore.canonicalJson(response.getValue("receipt").toString()),
        ) { "C_ACCEPTANCE_UNPROVEN" }
        check(Json.parseToJsonElement(entry.signedJson).jsonObject.getValue("object") == compaction) { "C_ACCEPTANCE_UNPROVEN" }
        check(context.journal.markPublished(signedText) == 1) { "C_LOCAL_STORAGE_FULL" }
    }

    private fun requireSnapshotResult(result: PallaSyncHttpResult<*>) {
        when (result) {
            is PallaSyncHttpResult.Success -> Unit
            is PallaSyncHttpResult.ProtocolError -> error(result.message)
            is PallaSyncHttpResult.Retryable -> error(result.message)
            PallaSyncHttpResult.Gone -> error("C_DELETION_UNVERIFIED")
        }
    }

    private suspend fun verifySnapshotReceiptInclusion(
        chainId: String,
        authority: JsonObject,
        receipt: JsonObject,
    ) {
        val state = checkNotNull(db.dao.getChainState(chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val pinAuthority = authority.getValue("pin").jsonObject
        val origin = pinAuthority.getValue("origin").jsonPrimitive.content
        val logId = checkNotNull(state.logId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val index = receipt.getValue("transparency_index").jsonPrimitive.long
        check(
            state.transparencyId == receipt.getValue("transparency_id").jsonPrimitive.content &&
                state.transparencyIndex >= index && state.transparencyHash != null,
        ) { "C_ACCEPTANCE_UNPROVEN" }
        val proof =
            db
                .protocolDao()
                .evidenceAt(
                    origin,
                    receipt.getValue("relay_key_id").jsonPrimitive.content,
                    chainId,
                    logId,
                    "transparency",
                    index,
                ).singleOrNull() ?: error("C_MISSING_DEPENDENCY")
        val pin =
            buildJsonObject {
                put("origin", origin)
                put("chain_id", chainId)
                put("public_key", policy.trustedRelayKey(origin))
                put("log_id", logId)
            }
        val anchor =
            buildJsonObject {
                put("transparency_id", checkNotNull(state.transparencyId))
                put("index", state.transparencyIndex)
                put("hash", checkNotNull(state.transparencyHash))
            }
        check(PallaSyncCore.validateReceiptInclusion(receipt.toString(), proof, pin.toString(), anchor.toString())) {
            "C_TRANSPARENCY_INVALID"
        }
    }

    /** §17.4/§17.10: stream a pinned device-authorized export without recovery envelopes. */
    suspend fun exportRelayArchive(output: java.io.OutputStream): PallaSyncExportArchive.ExpectedSource =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                requireProtectedAuthority(state.chainId)
                val authority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
                val pin = authority.getValue("pin").jsonObject
                val origin = pin.getValue("origin").jsonPrimitive.content
                val baseResult = PallaSyncUrls.normalize(getServerUrl())
                check(baseResult is PallaSyncHttpResult.Success && PallaSyncUrls.extractOrigin(baseResult.value) == origin) {
                    "C_BOOTSTRAP_TRUST_REQUIRED"
                }
                policy.requireCurrent(origin)
                val device = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(device.chainId == state.chainId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val relay = PallaSyncPeerRelay(db, remote, crypto)
                val expectedSource =
                    PallaSyncExportArchive.ExpectedSource(
                        chainId = pin.getValue("chain_id").jsonPrimitive.content,
                        generation = state.generation,
                        logId = pin.getValue("log_id").jsonPrimitive.content,
                        relayPublicKey = policy.trustedRelayKey(origin),
                        relayKeyId = pin.getValue("relay_key_id").jsonPrimitive.content,
                        relayPinJson = pin.toString(),
                    )
                PallaSyncExportArchive.create(output, expectedSource).use { archive ->
                    var cursor: String? = null
                    do {
                        currentCoroutineContext().ensureActive()
                        val requestedCursor = cursor
                        val url = PallaSyncUrls.export(baseResult.value, state.chainId, requestedCursor)
                        val chunk =
                            relay.requestBytes(url, device, "GET", PALLASYNC_EXPORT_MEDIA_TYPE) { bytes ->
                                check(bytes.size <= MAX_EXPORT_CHUNK_BYTES) { "C_LOCAL_LIMIT" }
                                bytes
                            }
                        val page = archive.acceptChunk(chunk, requestedCursor)
                        cursor = page.cursor
                        if (!page.hasMore) break
                    } while (true)
                    archive.finish()
                }
                expectedSource
            }
        }

    /** §17.4: explicit mnemonic-holder export may include recovery envelope ciphertext. */
    suspend fun exportRelayArchiveWithRecovery(
        output: java.io.OutputStream,
        mnemonic: String,
        passphrase: String,
    ): PallaSyncExportArchive.ExpectedSource =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                requireProtectedAuthority(state.chainId)
                val authority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
                val pin = authority.getValue("pin").jsonObject
                val origin = pin.getValue("origin").jsonPrimitive.content
                val base =
                    when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                check(PallaSyncUrls.extractOrigin(base) == origin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val root =
                    PallaSyncRootSession.beginText(
                        mnemonic,
                        passphrase,
                        PallaSyncRootSession.Operation.EXPORT,
                        state.chainId,
                        authority.getValue("admin_public_key").jsonPrimitive.content,
                        policy::requireCurrent,
                        policy.retry::correctedTime,
                    )
                root.use { session ->
                    val expectedSource =
                        PallaSyncExportArchive.ExpectedSource(
                            chainId = pin.getValue("chain_id").jsonPrimitive.content,
                            generation = state.generation,
                            logId = pin.getValue("log_id").jsonPrimitive.content,
                            relayPublicKey = policy.trustedRelayKey(origin),
                            relayKeyId = pin.getValue("relay_key_id").jsonPrimitive.content,
                            includeRecovery = true,
                            relayPinJson = pin.toString(),
                            identityJson =
                                buildJsonObject {
                                    put("chain_id", state.chainId)
                                    put("admin_public_key", authority.getValue("admin_public_key").jsonPrimitive.content)
                                }.toString(),
                        )
                    PallaSyncExportArchive.create(output, expectedSource).use { archive ->
                        var cursor: String? = null
                        do {
                            currentCoroutineContext().ensureActive()
                            val url = PallaSyncUrls.export(base, state.chainId, cursor, includeRecovery = true)
                            val token =
                                checkNotNull(
                                    session.token(origin, "GET", url.encodedPath, url.encodedQuery.orEmpty()),
                                ) {
                                    "C_RECOVERY_AUTH_REQUIRED"
                                }
                            val request =
                                Request
                                    .Builder()
                                    .url(url)
                                    .header("Accept", PALLASYNC_EXPORT_MEDIA_TYPE)
                                    .header("Authorization", "PallaSync $token")
                                    .get()
                                    .build()
                            val result =
                                remote.execute(request) { response ->
                                    PallaSyncHttpResult.Success(
                                        response.pallaSyncBytes().also {
                                            check(it.size <= MAX_EXPORT_CHUNK_BYTES) { "C_LOCAL_LIMIT" }
                                        },
                                    )
                                }
                            val bytes =
                                when (result) {
                                    is PallaSyncHttpResult.Success -> result.value
                                    else -> throw PallaSyncInitialHistory.Failure(result)
                                }
                            val page = archive.acceptChunk(bytes, cursor)
                            cursor = page.cursor
                            if (!page.hasMore) break
                        } while (true)
                        archive.finish()
                    }
                    expectedSource
                }
            }
        }

    /** Verify an archive readback against the protected source through signed log entries and record AEAD. */
    suspend fun recoveryArchiveExpectedSource(): PallaSyncExportArchive.ExpectedSource =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(state.generation == 0L) { "C_SNAPSHOT_INVALID" }
                requireProtectedAuthority(state.chainId)
                val authority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
                val pin = authority.getValue("pin").jsonObject
                val origin = pin.getValue("origin").jsonPrimitive.content
                val selectedOrigin =
                    when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                        is PallaSyncHttpResult.Success -> PallaSyncUrls.extractOrigin(normalized.value)
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                check(selectedOrigin == origin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                policy.requireCurrent(origin)
                PallaSyncExportArchive.ExpectedSource(
                    chainId = state.chainId,
                    generation = state.generation,
                    logId = pin.getValue("log_id").jsonPrimitive.content,
                    relayPublicKey = policy.trustedRelayKey(origin),
                    relayKeyId = pin.getValue("relay_key_id").jsonPrimitive.content,
                    includeRecovery = true,
                    relayPinJson = pin.toString(),
                    identityJson =
                        buildJsonObject {
                            put("chain_id", state.chainId)
                            put("admin_public_key", authority.getValue("admin_public_key").jsonPrimitive.content)
                        }.toString(),
                )
            }
        }

    suspend fun verifyRelayArchive(
        input: java.io.InputStream,
        expectedSource: PallaSyncExportArchive.ExpectedSource,
    ): PallaSyncExportArchiveVerifier.Header =
        withContext(Dispatchers.IO) {
            operationMutex.withLock { verifyRelayArchiveLocked(input, expectedSource) }
        }

    suspend fun restoreRelayArchive(
        input: java.io.InputStream,
        expectedSource: PallaSyncExportArchive.ExpectedSource,
    ) = withContext(Dispatchers.IO) {
        operationMutex.withLock {
            val snapshot = verifyRelayArchiveLocked(input, expectedSource, retainEvidence = true)
            val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val device = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val cut = Json.parseToJsonElement(checkNotNull(snapshot.historyCutJson)).jsonObject
            val head = cut.getValue("head").jsonObject
            val checkpoint =
                PallaSyncGenesisCheckpoint(
                    logId = snapshot.logId,
                    headSeq = snapshot.headSeq,
                    headHash = snapshot.headHash,
                    parametersHash = head.getValue("parameters_hash").jsonPrimitive.content,
                    epochHash = head.getValue("epoch_hash").jsonPrimitive.content,
                    transparencyId = head.getValue("transparency_id").jsonPrimitive.content,
                    transparencyIndex = head.getValue("entry_index").jsonPrimitive.long,
                    transparencyHash = head.getValue("transparency_hash").jsonPrimitive.content,
                    authority = Json.parseToJsonElement(checkNotNull(snapshot.authorityJson)).jsonObject,
                    generation = snapshot.generation,
                    dataZero = cut.getValue("data_zero").jsonPrimitive.content,
                )
            PallaSyncPrefixMaterializer(db, keystore).apply(state.chainId, device, checkpoint)
            projection.publish(state.chainId, db)
        }
    }

    private suspend fun verifyRelayArchiveLocked(
        input: java.io.InputStream,
        expectedSource: PallaSyncExportArchive.ExpectedSource,
        retainEvidence: Boolean = false,
    ): PallaSyncExportArchiveVerifier.Header {
        check(expectedSource.includeRecovery && expectedSource.identityJson != null) { "C_RECOVERY_AUTH_REQUIRED" }
        val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        requireProtectedAuthority(state.chainId)
        val authority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
        val pin = authority.getValue("pin").jsonObject
        val identity =
            buildJsonObject {
                put("chain_id", state.chainId)
                put("admin_public_key", authority.getValue("admin_public_key").jsonPrimitive.content)
            }.toString()
        check(
            Json.parseToJsonElement(checkNotNull(expectedSource.identityJson)).jsonObject ==
                Json.parseToJsonElement(identity).jsonObject &&
                Json.parseToJsonElement(checkNotNull(expectedSource.relayPinJson)).jsonObject == pin &&
                expectedSource.chainId == state.chainId && expectedSource.generation == state.generation &&
                expectedSource.logId == pin.getValue("log_id").jsonPrimitive.content,
        ) { "C_ROLLBACK_DETECTED" }
        val device = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val snapshot =
            PallaSyncExportArchiveVerifier.verifySuspending(
                input,
                expectedSource,
                PallaSyncArchiveRecordVerifier { recordAuthority, recordJson, _ ->
                    val record = Json.parseToJsonElement(recordJson).jsonObject
                    val epoch = record.getValue("epoch").jsonPrimitive.long
                    val epochKeys = checkNotNull(keystore.keysForEpoch(device, epoch)) { "C_MISSING_DEPENDENCY" }
                    val plaintext =
                        PallaSyncEpochSession.begin(epochKeys, recordAuthority, epoch).use { session ->
                            session.decryptRecord(recordJson)
                        }
                    check(PallaSyncCore.canonicalJson(plaintext) == plaintext) { "C_SCHEMA_VIOLATION" }
                    kotlinx.serialization.json.Json
                        .decodeFromString<PallaSyncInnerRecord>(plaintext)
                },
                PallaSyncArchiveSnapshotVerifier(::verifyExportSnapshotParts),
                logEntrySink =
                    if (retainEvidence) {
                        PallaSyncArchiveLogEntrySink { _, entryJson, receiptJson, sequence ->
                            val entry = Json.parseToJsonElement(entryJson).jsonObject
                            val receipt = Json.parseToJsonElement(receiptJson).jsonObject
                            val logId = pin.getValue("log_id").jsonPrimitive.content
                            val entryHash = checkNotNull(PallaSyncCore.objectHash(entryJson)) { "C_SCHEMA_VIOLATION" }
                            val receiptHash = checkNotNull(PallaSyncCore.objectHash(receiptJson)) { "C_SCHEMA_VIOLATION" }
                            db.transaction {
                                db.protocolDao().retainEvidence(
                                    ProtectedEvidenceEntity(
                                        pin.getValue("origin").jsonPrimitive.content,
                                        receipt.getValue("relay_key_id").jsonPrimitive.content,
                                        state.chainId,
                                        logId,
                                        "upload_receipt",
                                        sequence,
                                        receiptHash,
                                        receiptJson,
                                    ),
                                )
                                PallaSyncEnvelopeEvidence.retain(db, state.chainId, entry)
                                db.protocolDao().retainLogEntry(
                                    LogEntryEntity(
                                        state.chainId,
                                        logId,
                                        sequence,
                                        entryHash,
                                        entryJson,
                                        receiptJson,
                                        "authority_verified",
                                    ),
                                )
                                db.protocolDao().retainAuthority(
                                    com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity(
                                        state.chainId,
                                        "selected_history_entry",
                                        entryHash,
                                        state.generation,
                                        entryJson,
                                        "authority_verified",
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    },
                beforeRetain =
                    if (retainEvidence) {
                        { verified ->
                            val current = checkNotNull(db.dao.getChainState(state.chainId)) { "C_MISSING_DEPENDENCY" }
                            val verifiedAuthority = Json.parseToJsonElement(checkNotNull(verified.authorityJson)).jsonObject
                            val verifiedHead = Json.parseToJsonElement(checkNotNull(verified.transparencyHeadJson)).jsonObject
                            check(
                                verified.complete && verified.includeRecovery && verified.generation == 0L &&
                                    verified.chainId == state.chainId && verified.logId == state.logId &&
                                    PallaSyncCore.objectHash(verifiedAuthority.toString()) ==
                                    PallaSyncCore.objectHash(checkNotNull(db.protocolDao().currentAuthority(state.chainId))) &&
                                    current == state && current.writeState == "active" &&
                                    current.materializedSeq <= current.verifiedSeq && current.verifiedSeq <= verified.headSeq &&
                                    (current.verifiedSeq != verified.headSeq || current.verifiedHash == verified.headHash) &&
                                    current.transparencyId == verifiedHead.getValue("transparency_id").jsonPrimitive.content &&
                                    current.transparencyIndex == verifiedHead.getValue("entry_index").jsonPrimitive.long &&
                                    current.transparencyHash == verifiedHead.getValue("transparency_hash").jsonPrimitive.content &&
                                    db.dao.pendingOutboxCount(state.chainId) == 0L &&
                                    db.protocolDao().unresolvedQuarantineCount(state.chainId) == 0L,
                            ) { "C_ROLLBACK_DETECTED" }
                        }
                    } else {
                        null
                    },
            )
        check(snapshot.includeRecovery && snapshot.complete) { "C_SCHEMA_VIOLATION" }
        return snapshot
    }

    private suspend fun verifyExportSnapshotParts(
        authorityJson: String,
        compactionJson: String,
        parts: PallaSyncArchiveSnapshotPartSource,
    ) {
        val authority = Json.parseToJsonElement(authorityJson).jsonObject
        val compaction = Json.parseToJsonElement(compactionJson).jsonObject
        val chain = compaction.getValue("chain_id").jsonPrimitive.content
        val generation = compaction.getValue("generation").jsonPrimitive.long
        val logId = compaction.getValue("log_id").jsonPrimitive.content
        check(
            chain ==
                authority
                    .getValue("parameters")
                    .jsonObject
                    .getValue("chain_id")
                    .jsonPrimitive.content &&
                generation ==
                authority
                    .getValue("parameters")
                    .jsonObject
                    .getValue("generation")
                    .jsonPrimitive.long &&
                logId ==
                authority
                    .getValue("pin")
                    .jsonObject
                    .getValue("log_id")
                    .jsonPrimitive.content,
        ) { "C_SNAPSHOT_INVALID" }
        val epochHash = compaction.getValue("epoch_hash").jsonPrimitive.content
        val epoch =
            authority
                .getValue("epochs")
                .jsonObject
                .entries
                .singleOrNull { (_, value) ->
                    PallaSyncCore.objectHash(value.jsonObject.getValue("record").toString()) == epochHash
                }?.key
                ?.toLongOrNull() ?: error("C_MISSING_DEPENDENCY")
        val device = checkNotNull(keysForChainLocked(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val epochKeys = checkNotNull(keystore.keysForEpoch(device, epoch)) { "C_MISSING_DEPENDENCY" }
        val authorId = compaction.getValue("device_id").jsonPrimitive.content
        val author =
            authority
                .getValue("devices")
                .jsonObject[authorId]
                ?.jsonObject
                ?.getValue("record")
                ?.jsonObject ?: error("C_MISSING_DEPENDENCY")
        check(author.getValue("generation").jsonPrimitive.long == generation) { "C_ACCEPTANCE_UNPROVEN" }
        val authorKey = author.getValue("device_public_key").jsonPrimitive.content
        val references = compaction.getValue("snapshot_parts").jsonArray
        val snapshotVerifier =
            PallaSyncEpochSession.begin(epochKeys, authorityJson, epoch).use { session ->
                var verifier: PallaSyncSnapshotVerifier? = null
                var expectedIndex = 0
                parts.forEachPart { partIndex, recordJson ->
                    check(partIndex == expectedIndex && partIndex < references.size) { "C_SNAPSHOT_INVALID" }
                    val record = Json.parseToJsonElement(recordJson).jsonObject
                    check(record.getValue("device_id").jsonPrimitive.content == authorId) { "C_ACCEPTANCE_UNPROVEN" }
                    val plaintext = session.decryptSnapshotPart(recordJson, authorKey)
                    val frame = Json.parseToJsonElement(plaintext).jsonObject
                    if (partIndex == 0) {
                        val header =
                            frame
                                .getValue("entries")
                                .jsonArray
                                .firstOrNull()
                                ?.jsonObject ?: error("C_SNAPSHOT_INVALID")
                        check(
                            header.getValue("chain_id").jsonPrimitive.content == chain &&
                                header.getValue("generation").jsonPrimitive.long == generation &&
                                header.getValue("log_id").jsonPrimitive.content == logId &&
                                header.getValue("covered_through_seq").jsonPrimitive.long ==
                                compaction.getValue("covered_through_seq").jsonPrimitive.long &&
                                header.getValue("covered_log_hash").jsonPrimitive.content ==
                                compaction.getValue("covered_log_hash").jsonPrimitive.content &&
                                header.getValue("epoch_hash").jsonPrimitive.content == epochHash,
                        ) { "C_SNAPSHOT_INVALID" }
                        verifier =
                            PallaSyncSnapshotVerifier.beginFromEpochSession(
                                session,
                                header.toString(),
                                compaction.getValue("compaction_id").jsonPrimitive.content,
                                references.size,
                            )
                    }
                    checkNotNull(verifier).acceptDecryptedAuthenticatedPart(plaintext)
                    expectedIndex++
                }
                check(expectedIndex == references.size) { "C_MISSING_DEPENDENCY" }
                checkNotNull(verifier).also {
                    it.finish(compaction.getValue("state_sha256").jsonPrimitive.content)
                }
            }
        snapshotVerifier.close()
    }

    /** §7.12.4/§17.10: sign a protected public checkpoint at the local verified data prefix. */
    internal suspend fun createDeviceCheckpoint(): String =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                requireProtectedAuthority(state.chainId)
                check(state.logId != null && state.verifiedHash != null) { "C_MISSING_DEPENDENCY" }
                val keys = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_MISSING_DEPENDENCY" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val pin = authority.getValue("pin").jsonObject
                val parameters = authority.getValue("parameters").jsonObject
                val epoch =
                    authority
                        .getValue("epochs")
                        .jsonObject
                        .getValue(state.keyEpoch.toString())
                        .jsonObject
                        .getValue("record")
                        .jsonObject
                val deviceId = checkNotNull(keys.deviceId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                validateSnapshotPublisher(state, authority, keys)
                check(
                    crypto.objectHash(parameters.toString()) == state.parametersHash &&
                        crypto.objectHash(epoch.toString()) == state.epochHash &&
                        pin.getValue("log_id").jsonPrimitive.content == state.logId &&
                        pin.getValue("chain_id").jsonPrimitive.content == state.chainId,
                ) { "C_ROLLBACK_DETECTED" }
                val transparencyId = checkNotNull(state.transparencyId) { "C_MISSING_DEPENDENCY" }
                val transparencyHash = checkNotNull(state.transparencyHash) { "C_MISSING_DEPENDENCY" }
                val template =
                    buildJsonObject {
                        put("type", "PALLASYNC-CHECKPOINT-v3")
                        put("version", "3.0")
                        put("checkpoint_id", UUID.randomUUID().toString())
                        put("chain_id", state.chainId)
                        put("generation", state.generation)
                        put("device_id", deviceId)
                        put("relay_origin", pin.getValue("origin").jsonPrimitive.content)
                        put("relay_key_id", pin.getValue("relay_key_id").jsonPrimitive.content)
                        put("log_id", checkNotNull(state.logId))
                        put("seq", state.verifiedSeq)
                        put("head_hash", checkNotNull(state.verifiedHash))
                        put("parameters_hash", checkNotNull(state.parametersHash))
                        put("epoch_hash", checkNotNull(state.epochHash))
                        put("transparency_id", transparencyId)
                        put("transparency_index", state.transparencyIndex)
                        put("transparency_hash", transparencyHash)
                        put("created_at_ms", policy.retry.correctedTime(pin.getValue("origin").jsonPrimitive.content))
                        put("signature", "A".repeat(86))
                    }
                val signed =
                    PallaSyncDeviceSession.begin(keys).use {
                        it.signCheckpoint(template.toString(), deviceId, keys.publicKeyBase64Url)
                    }
                val canonical = checkNotNull(PallaSyncCore.canonicalJson(signed)) { "C_SCHEMA_VIOLATION" }
                PallaSyncCheckpointFile.encode(canonical)
                canonical
            }
        }

    /** Imports only a canonical checkpoint signed by a chain device already certified in local authority. */
    internal suspend fun importDeviceCheckpoint(checkpointJson: String): String =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                PallaSyncCheckpointFile.encode(checkpointJson)
                val value = Json.parseToJsonElement(checkpointJson).jsonObject
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                requireProtectedAuthority(state.chainId)
                val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_MISSING_DEPENDENCY" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val pin = authority.getValue("pin").jsonObject
                val chain = value.getValue("chain_id").jsonPrimitive.content
                val origin = value.getValue("relay_origin").jsonPrimitive.content
                val relayKeyId = value.getValue("relay_key_id").jsonPrimitive.content
                val logId = value.getValue("log_id").jsonPrimitive.content
                check(
                    chain == state.chainId && chain == pin.getValue("chain_id").jsonPrimitive.content &&
                        origin == pin.getValue("origin").jsonPrimitive.content &&
                        relayKeyId == pin.getValue("relay_key_id").jsonPrimitive.content &&
                        logId == pin.getValue("log_id").jsonPrimitive.content,
                ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val deviceId = value.getValue("device_id").jsonPrimitive.content
                val generation = value.getValue("generation").jsonPrimitive.long
                val seq = value.getValue("seq").jsonPrimitive.long
                check(generation == state.generation && seq <= state.verifiedSeq) { "C_MISSING_DEPENDENCY" }
                val admission = checkNotNull(authority.getValue("devices").jsonObject[deviceId]?.jsonObject) { "C_MISSING_DEPENDENCY" }
                val deviceRecord = admission.getValue("record").jsonObject
                val certifiedKey = deviceRecord.getValue("device_public_key").jsonPrimitive.content
                check(deviceRecord.getValue("generation").jsonPrimitive.long == generation) { "C_MISSING_DEPENDENCY" }
                val enrolledSeq = admission.getValue("enrolled_seq").jsonPrimitive.long
                val revokedSeq = admission.getValue("revoked_seq")
                check(enrolledSeq <= seq && (revokedSeq is kotlinx.serialization.json.JsonNull || revokedSeq.jsonPrimitive.long > seq)) {
                    "C_DEVICE_REVOKED"
                }
                val epochKnown =
                    authority.getValue("epochs").jsonObject.values.any { accepted ->
                        val record = accepted.jsonObject.getValue("record").jsonObject
                        record.getValue("generation").jsonPrimitive.long == generation &&
                            crypto.objectHash(record.toString()) == value.getValue("epoch_hash").jsonPrimitive.content &&
                            record.getValue("members").jsonArray.any { it.jsonPrimitive.content == deviceId }
                    }
                check(epochKnown) { "C_MISSING_DEPENDENCY" }
                val params = authority.getValue("parameters")
                check(crypto.objectHash(params.toString()) == value.getValue("parameters_hash").jsonPrimitive.content) {
                    "C_MISSING_DEPENDENCY"
                }
                check(PallaSyncCore.verifyDeviceCheckpoint(checkpointJson, certifiedKey)) { "C_SIGNATURE_INVALID" }
                val objectHash = checkNotNull(crypto.objectHash(checkpointJson)) { "C_SCHEMA_VIOLATION" }
                db.protocolDao().retainEvidence(
                    ProtectedEvidenceEntity(
                        origin = origin,
                        relayKeyId = relayKeyId,
                        chainId = chain,
                        logId = logId,
                        kind = "device_checkpoint",
                        sequence = seq,
                        objectHash = objectHash,
                        signedJson = checkpointJson,
                    ),
                )

                val expectedHead =
                    if (seq == state.verifiedSeq) {
                        checkNotNull(state.verifiedHash) { "C_MISSING_DEPENDENCY" }
                    } else {
                        val history =
                            Json
                                .parseToJsonElement(
                                    PallaSyncCore.beginAuthorityHistory(
                                        params.toString().toByteArray(Charsets.UTF_8),
                                        buildJsonObject {
                                            put("chain_id", chain)
                                            put("admin_public_key", authority.getValue("admin_public_key"))
                                        }.toString(),
                                        origin,
                                        policy.trustedRelayKey(origin),
                                    ),
                                ).jsonObject
                        var verified = history
                        var after = 0L
                        while (after < seq) {
                            val page = db.protocolDao().logPage(chain, logId, after, seq, PALLASYNC_PAGE_SIZE)
                            check(page.isNotEmpty() && page.first().relaySeq == after + 1L) { "C_MISSING_DEPENDENCY" }
                            for (row in page) {
                                check(row.validationState == "authority_verified" && row.relaySeq == after + 1L) {
                                    "C_MISSING_DEPENDENCY"
                                }
                                verified =
                                    Json
                                        .parseToJsonElement(
                                            PallaSyncCore.advanceAuthorityHistory(
                                                verified.toString(),
                                                row.signedJson,
                                                row.receiptJson,
                                            ),
                                        ).jsonObject
                                after = row.relaySeq
                            }
                        }
                        verified.getValue("head_hash").jsonPrimitive.content
                    }
                if (expectedHead != value.getValue("head_hash").jsonPrimitive.content) {
                    db.pallaSyncDao().updateChainState(state.copy(writeState = "integrity_paused"))
                    error("C_DEVICE_EQUIVOCATION")
                }
                val peerTransparencyIndex = value.getValue("transparency_index").jsonPrimitive.long
                if (peerTransparencyIndex == state.transparencyIndex &&
                    (
                        value.getValue("transparency_id").jsonPrimitive.content != state.transparencyId ||
                            value.getValue("transparency_hash").jsonPrimitive.content != state.transparencyHash
                    )
                ) {
                    db.pallaSyncDao().updateChainState(state.copy(writeState = "integrity_paused"))
                    error("C_DEVICE_EQUIVOCATION")
                }
                val localTransparencyMatches =
                    verifyRetainedTransparencyCheckpoint(
                        origin = origin,
                        relayKeyId = relayKeyId,
                        chain = chain,
                        logId = logId,
                        transparencyId = value.getValue("transparency_id").jsonPrimitive.content,
                        targetIndex = peerTransparencyIndex,
                        targetHash = value.getValue("transparency_hash").jsonPrimitive.content,
                        expectedTransparencyId = state.transparencyId,
                        certifiedRelayKey = policy.trustedRelayKey(origin),
                    )
                if (localTransparencyMatches) "verified_data_and_transparency_prefix" else "verified_data_prefix_transparency_unmatched"
            }
        }

    private suspend fun verifyRetainedTransparencyCheckpoint(
        origin: String,
        relayKeyId: String,
        chain: String,
        logId: String,
        transparencyId: String,
        targetIndex: Long,
        targetHash: String,
        expectedTransparencyId: String?,
        certifiedRelayKey: String,
    ): Boolean {
        if (transparencyId != expectedTransparencyId) return false
        val pin =
            buildJsonObject {
                put("origin", origin)
                put("chain_id", chain)
                put("public_key", certifiedRelayKey)
                put("log_id", logId)
            }.toString()
        var anchor = ""
        var verifiedIndex = 0L
        do {
            val entries =
                if (verifiedIndex == targetIndex) {
                    emptyList()
                } else {
                    val protocolDao = db.protocolDao()
                    if (protocolDao.transparencyEvidenceConflict(origin, relayKeyId, chain, verifiedIndex, targetIndex) != null) {
                        val state = checkNotNull(db.pallaSyncDao().getChainState(chain)) { "C_MISSING_DEPENDENCY" }
                        db.pallaSyncDao().updateChainState(state.copy(writeState = "integrity_paused"))
                        error("C_DEVICE_EQUIVOCATION")
                    }
                    protocolDao
                        .transparencyEvidencePage(origin, relayKeyId, chain, verifiedIndex, targetIndex, PALLASYNC_PAGE_SIZE)
                        .also { page ->
                            if (
                                page.isEmpty() ||
                                page.withIndex().any { (index, row) -> row.sequence != verifiedIndex + index + 1L }
                            ) {
                                return false
                            }
                        }.map { row ->
                            try {
                                Json.parseToJsonElement(row.signedJson)
                            } catch (_: kotlinx.serialization.SerializationException) {
                                val state = checkNotNull(db.pallaSyncDao().getChainState(chain)) { "C_MISSING_DEPENDENCY" }
                                db.pallaSyncDao().updateChainState(state.copy(writeState = "integrity_paused"))
                                error("C_DEVICE_EQUIVOCATION")
                            }
                        }
                }
            currentCoroutineContext().ensureActive()
            val resultText =
                try {
                    PallaSyncCore.verifyTransparencyCheckpointPrefix(
                        JsonArray(entries).toString(),
                        transparencyId,
                        targetIndex,
                        targetHash,
                        pin,
                        anchor,
                    )
                } catch (_: IllegalStateException) {
                    val state = checkNotNull(db.pallaSyncDao().getChainState(chain)) { "C_MISSING_DEPENDENCY" }
                    db.pallaSyncDao().updateChainState(state.copy(writeState = "integrity_paused"))
                    error("C_DEVICE_EQUIVOCATION")
                }
            val next =
                Json
                    .parseToJsonElement(resultText)
                    .jsonObject
                    .getValue("next_anchor")
                    .jsonObject
            val nextIndex = next.getValue("index").jsonPrimitive.long
            if (nextIndex < verifiedIndex || nextIndex > targetIndex) {
                val state = checkNotNull(db.pallaSyncDao().getChainState(chain)) { "C_MISSING_DEPENDENCY" }
                db.pallaSyncDao().updateChainState(state.copy(writeState = "integrity_paused"))
                error("C_DEVICE_EQUIVOCATION")
            }
            if (nextIndex == verifiedIndex && verifiedIndex < targetIndex) return false
            anchor = next.toString()
            verifiedIndex = nextIndex
        } while (verifiedIndex < targetIndex)
        val finalAnchor = Json.parseToJsonElement(anchor).jsonObject
        return finalAnchor.getValue("transparency_id").jsonPrimitive.content == transparencyId &&
            finalAnchor.getValue("index").jsonPrimitive.long == targetIndex &&
            finalAnchor.getValue("hash").jsonPrimitive.content == targetHash
    }

    /** §10.13: retransmit only current-generation records with retained verified source LogEntries. */
    internal suspend fun repairRetainedRelayHistory(
        mnemonic: String,
        passphrase: String,
    ): PallaSyncRecordRepairSummary =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                requireProtectedAuthority(state.chainId)
                val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val pinObject = authority.getValue("pin").jsonObject
                val origin = pinObject.getValue("origin").jsonPrimitive.content
                val base =
                    when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                check(PallaSyncUrls.extractOrigin(base) == origin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                policy.requireCurrent(origin)
                val root =
                    PallaSyncRootSession.beginText(
                        mnemonic,
                        passphrase,
                        PallaSyncRootSession.Operation.RESEED,
                        state.chainId,
                        authority.getValue("admin_public_key").jsonPrimitive.content,
                        policy::requireCurrent,
                        policy.retry::correctedTime,
                    )
                try {
                    val targetLogId = pinObject.getValue("log_id").jsonPrimitive.content
                    check(state.logId == targetLogId) { "C_LOG_ID_MISMATCH" }
                    val targetPin =
                        buildJsonObject {
                            put("origin", origin)
                            put("chain_id", state.chainId)
                            put("public_key", pinObject.getValue("public_key"))
                            put("log_id", targetLogId)
                        }.toString()
                    performRetainedHistoryRepair(root, base, origin, state, targetPin)
                } finally {
                    root.close()
                }
            }
        }

    /** §10.3/§17.5: stage a complete retained source history on an explicitly selected relay. */
    internal suspend fun stageReseedHistory(
        targetRelayUrl: String,
        mnemonic: String,
        passphrase: String,
    ): PallaSyncHttpResult<PallaSyncImportStageResponse.Staged> =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                requireProtectedAuthority(state.chainId)
                val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val adminPublicKey = authority.getValue("admin_public_key").jsonPrimitive.content
                val target =
                    when (val normalized = PallaSyncUrls.normalize(targetRelayUrl)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                val origin = PallaSyncUrls.extractOrigin(target)
                policy.requireCurrent(origin)
                requireSnapshotResult(refreshRelayHealth(target))
                val targetPublicKey = policy.trustedRelayKey(origin)
                val source =
                    when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                val sourceOrigin = PallaSyncUrls.extractOrigin(source)
                val sourcePin = authority.getValue("pin").jsonObject
                check(
                    sourceOrigin == sourcePin.getValue("origin").jsonPrimitive.content &&
                        sourcePin.getValue("public_key").jsonPrimitive.content == policy.trustedRelayKey(sourceOrigin) &&
                        state.logId == sourcePin.getValue("log_id").jsonPrimitive.content,
                ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                policy.requireCurrent(sourceOrigin)
                requireSnapshotResult(refreshRelayHealth(source))
                requireSnapshotResult(consistencyProbe.run(source, state.chainId))
                val stableSource = checkNotNull(db.pallaSyncDao().getChainState(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(
                    stableSource.logId == state.logId && stableSource.generation == state.generation &&
                        stableSource.initialPullCompleted &&
                        stableSource.downloadedSeq == stableSource.verifiedSeq &&
                        stableSource.verifiedSeq == stableSource.materializedSeq &&
                        stableSource.lastRelaySeq == stableSource.materializedSeq && stableSource.verifiedHash != null &&
                        db.protocolDao().unresolvedQuarantineCount(state.chainId) == 0L,
                ) { "C_MISSING_DEPENDENCY" }
                val reseedDao = db.snapshotDatabase().reseedJournalDao()
                val existingReseed = reseedDao.get(state.chainId)
                check(
                    existingReseed == null ||
                        (
                            existingReseed.sourceLogId == stableSource.logId &&
                                existingReseed.sourceGeneration == stableSource.generation && existingReseed.targetOrigin == origin
                        ),
                ) { "C_MISSING_DEPENDENCY" }
                val root =
                    PallaSyncRootSession.beginText(
                        mnemonic,
                        passphrase,
                        PallaSyncRootSession.Operation.RESEED,
                        state.chainId,
                        adminPublicKey,
                        policy::requireCurrent,
                        policy.retry::correctedTime,
                    )
                try {
                    requireReseedSourceHead(root, source, sourceOrigin, stableSource)
                    val history = PallaSyncImportHistoryBuilder(db.protocolDao()).build(state.chainId)
                    val staged = PallaSyncImportRelay(remote).stage(root, target, history)
                    if (staged is PallaSyncHttpResult.Success) {
                        check(existingReseed?.stagedLogId == null || existingReseed.stagedLogId == staged.value.logId) {
                            "C_IMPORT_CONFLICT"
                        }
                        val targetPin =
                            buildJsonObject {
                                put("origin", origin)
                                put("chain_id", state.chainId)
                                put("public_key", targetPublicKey)
                                put("log_id", staged.value.logId)
                            }
                        val targetPinJson = checkNotNull(PallaSyncCore.canonicalJson(targetPin.toString())) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                        val journal =
                            existingReseed?.copy(
                                stagedLogId = staged.value.logId,
                                compactionId = existingReseed.compactionId ?: UUID.randomUUID().toString(),
                                targetPinJson = targetPinJson,
                                phase = maxOf(1, existingReseed.phase),
                                updatedAtMs = policy.retry.correctedTime(origin),
                            ) ?: ReseedJournalEntity(
                                chainId = state.chainId,
                                sourceLogId = checkNotNull(stableSource.logId),
                                sourceGeneration = stableSource.generation,
                                targetOrigin = origin,
                                stagedLogId = staged.value.logId,
                                compactionId = UUID.randomUUID().toString(),
                                targetPinJson = targetPinJson,
                                phase = 1,
                                updatedAtMs = policy.retry.correctedTime(origin),
                            )
                        reseedDao.saveMonotone(journal)
                        prepareReseedSnapshot(
                            root = root,
                            target = target,
                            authority = authority,
                            source = stableSource,
                            history = history,
                            journal = journal,
                        )
                    }
                    staged
                } finally {
                    root.close()
                }
            }
        }

    /** Stage generation+1 ciphertext under A; receipts stay untrusted until the commit suffix is proven. */
    private suspend fun prepareReseedSnapshot(
        root: PallaSyncRootSession,
        target: HttpUrl,
        authority: JsonObject,
        source: ChainStateEntity,
        history: PallaSyncImportRelay.History,
        journal: ReseedJournalEntity,
    ) {
        val stagedLogId = checkNotNull(journal.stagedLogId) { "C_MISSING_DEPENDENCY" }
        val compactionId = checkNotNull(journal.compactionId) { "C_MISSING_DEPENDENCY" }
        val origin = PallaSyncUrls.extractOrigin(target)
        check(origin == journal.targetOrigin) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
        policy.requireCurrent(origin)
        val preparedBundle: String
        val prepared: PallaSyncPreparedReseed
        val pending = keystore.pendingReseed()
        if (journal.preparedBundleJson != null) {
            val candidate = checkNotNull(pending) { "C_MISSING_DEPENDENCY" }
            check(candidate.targetOrigin == origin && candidate.publicBundleJson == journal.preparedBundleJson) {
                "C_ROLLBACK_DETECTED"
            }
            preparedBundle = journal.preparedBundleJson
            prepared = resumePallaSyncPreparedReseed(preparedBundle, candidate.keys)
        } else if (pending != null) {
            check(pending.keys.chainId == source.chainId && pending.targetOrigin == origin) { "C_IMPORT_CONFLICT" }
            preparedBundle = pending.publicBundleJson
            prepared = resumePallaSyncPreparedReseed(preparedBundle, pending.keys)
        } else {
            prepared =
                root.prepareReseed(
                    checkNotNull(db.protocolDao().currentAuthority(source.chainId)),
                    "Palleria reseed",
                    policy.retry.correctedTime(origin),
                )
            try {
                preparedBundle = prepared.publicBundleJson()
                check(keystore.savePendingReseed(prepared.keySnapshot(), preparedBundle, origin)) { "C_LOCAL_STORAGE_FULL" }
            } catch (failure: Throwable) {
                prepared.close()
                throw failure
            }
        }
        prepared.use {
            val reseedDao = db.snapshotDatabase().reseedJournalDao()
            val checkpointed =
                journal.copy(
                    preparedBundleJson = preparedBundle,
                    phase = maxOf(2, journal.phase),
                    updatedAtMs = policy.retry.correctedTime(origin),
                )
            reseedDao.saveMonotone(checkpointed)
            val result = prepared.publicResult
            val snapshotJournal =
                PallaSyncSnapshotPublicationJournal(
                    db.snapshotDatabase(),
                    source.chainId,
                    compactionId,
                    result.epoch,
                )
            val targetPin = Json.parseToJsonElement(journal.targetPinJson).jsonObject
            val pendingParts = ArrayList<JsonObject>()
            val uploadRelay =
                PallaSyncSnapshotPartRelay(
                    remote,
                    target,
                    source.chainId,
                    targetPin,
                    relayLimits,
                    { base, _, method, path, query, body ->
                        root.token(PallaSyncUrls.extractOrigin(base), method, path, query, body.toByteArray())
                    },
                ) { records, response -> snapshotJournal.retainStagedUploadReceipts(records, response) }

            suspend fun flushStagedParts() {
                if (pendingParts.isEmpty()) return
                requireSnapshotResult(uploadRelay.upload(pendingParts.toList()))
                pendingParts.clear()
            }

            var partIndex = 0
            val snapshot =
                prepared.produceSnapshot(
                    db.snapshotDatabase(),
                    stagedLogId,
                    compactionId,
                    snapshotJournal,
                ) { _, recordJson, _ ->
                    val part = snapshotJournal.prepared(partIndex++) ?: error("C_MISSING_DEPENDENCY")
                    if (part.uploadReceiptJson != null) {
                        flushStagedParts()
                    } else {
                        pendingParts += Json.parseToJsonElement(recordJson).jsonObject
                        check(recordUploadBatchSize(pendingParts.map { it.toString() }, relayLimits) == pendingParts.size) {
                            "C_LOCAL_LIMIT"
                        }
                        if (pendingParts.size >= minOf(MAX_SNAPSHOT_UPLOAD_RECORDS, relayLimits.maxRecordsPerUpload)) {
                            flushStagedParts()
                        }
                    }
                }
            flushStagedParts()

            val currentParametersJson = authority.getValue("parameters").toString()
            val currentEpochJson =
                authority
                    .getValue("epochs")
                    .jsonObject
                    .values
                    .maxBy {
                        it.jsonObject
                            .getValue("record")
                            .jsonObject
                            .getValue("epoch")
                            .jsonPrimitive.long
                    }.jsonObject
                    .getValue("record")
                    .toString()
            val body =
                journal.commitBodyJson
                    ?: root.createReseedCommitBody(
                        prepared,
                        currentParametersJson,
                        currentEpochJson,
                        snapshot,
                        policy.retry.correctedTime(origin),
                    )
            val commit = Json.parseToJsonElement(body).jsonObject
            val compaction = commit.getValue("compaction").jsonObject
            check(
                snapshot.parts.isNotEmpty() &&
                    compaction.getValue("compaction_id").jsonPrimitive.content == snapshot.compactionId &&
                    compaction.getValue("log_id").jsonPrimitive.content == stagedLogId &&
                    compaction.getValue("state_sha256").jsonPrimitive.content == snapshot.stateCommitment &&
                    compaction.getValue("snapshot_parts").jsonArray.map { value ->
                        value.jsonObject
                            .getValue("record_id")
                            .jsonPrimitive.content to
                            value.jsonObject
                                .getValue("record_hash")
                                .jsonPrimitive.content
                    } == snapshot.parts.map { it.recordId to it.recordHash },
            ) {
                "C_SNAPSHOT_INVALID"
            }
            val expected = PallaSyncImportRelay.expectedReseedCommitEntries(history, body).toString()
            check(journal.expectedEntriesJson == null || journal.expectedEntriesJson == expected) { "C_IMPORT_CONFLICT" }
            reseedDao.saveMonotone(
                checkpointed.copy(
                    commitBodyJson = body,
                    expectedEntriesJson = expected,
                    phase = maxOf(3, checkpointed.phase),
                    updatedAtMs = policy.retry.correctedTime(origin),
                ),
            )
        }
    }

    /** §17.5: explicitly commit a previously staged generation, then prove its complete transparency suffix. */
    internal suspend fun commitStagedReseedHistory(
        targetRelayUrl: String,
        mnemonic: String,
        passphrase: String,
    ): PallaSyncHttpResult<JsonObject> =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val target =
                    when (val normalized = PallaSyncUrls.normalize(targetRelayUrl)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                val origin = PallaSyncUrls.extractOrigin(target)
                policy.requireCurrent(origin)
                requireSnapshotResult(refreshRelayHealth(target))
                val reseedDao = db.snapshotDatabase().reseedJournalDao()
                val savedJournal =
                    checkNotNull(reseedDao.get(state.chainId)) {
                        "C_MISSING_DEPENDENCY"
                    }
                check(savedJournal.targetOrigin == origin) { "C_IMPORT_CONFLICT" }
                if (savedJournal.phase >= 5 && savedJournal.commitResultJson != null) {
                    finishReseedCutover(state.chainId, savedJournal)
                    return@withLock PallaSyncHttpResult.Success(Json.parseToJsonElement(savedJournal.commitResultJson).jsonObject)
                }
                check(savedJournal.sourceLogId == state.logId && savedJournal.sourceGeneration == state.generation) {
                    "C_IMPORT_CONFLICT"
                }
                requireProtectedAuthority(state.chainId)
                val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                check(
                    savedJournal.phase >= 3 && savedJournal.commitBodyJson != null && savedJournal.expectedEntriesJson != null,
                ) { "C_MISSING_DEPENDENCY" }
                val targetPinText = savedJournal.targetPinJson
                val targetPinObject = Json.parseToJsonElement(targetPinText).jsonObject
                check(
                    targetPinObject.getValue("origin").jsonPrimitive.content == origin &&
                        targetPinObject.getValue("chain_id").jsonPrimitive.content == state.chainId &&
                        targetPinObject.getValue("public_key").jsonPrimitive.content == policy.trustedRelayKey(origin),
                ) { "C_RELAY_KEY_MISMATCH" }
                val source =
                    when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                val sourceOrigin = PallaSyncUrls.extractOrigin(source)
                policy.requireCurrent(sourceOrigin)
                requireSnapshotResult(refreshRelayHealth(source))
                requireSnapshotResult(consistencyProbe.run(source, state.chainId))
                val stable = checkNotNull(db.pallaSyncDao().getChainState(state.chainId)) { "C_MISSING_DEPENDENCY" }
                check(
                    stable.logId == state.logId && stable.generation == state.generation && stable.initialPullCompleted &&
                        stable.downloadedSeq == stable.verifiedSeq && stable.verifiedSeq == stable.materializedSeq &&
                        stable.lastRelaySeq == stable.materializedSeq && stable.verifiedHash != null &&
                        db.protocolDao().unresolvedQuarantineCount(state.chainId) == 0L,
                ) { "C_MISSING_DEPENDENCY" }
                val root =
                    PallaSyncRootSession.beginText(
                        mnemonic,
                        passphrase,
                        PallaSyncRootSession.Operation.RESEED,
                        state.chainId,
                        authority.getValue("admin_public_key").jsonPrimitive.content,
                        policy::requireCurrent,
                        policy.retry::correctedTime,
                    )
                try {
                    requireReseedSourceHead(root, source, sourceOrigin, stable)
                    val history = PallaSyncImportHistoryBuilder(db.protocolDao()).build(state.chainId)
                    val journalDao = reseedDao
                    val journal = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                    prepareReseedSnapshot(root, target, authority, stable, history, journal)
                    val preparedJournal = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                    val compactionId = checkNotNull(preparedJournal.compactionId) { "C_MISSING_DEPENDENCY" }
                    val parts = db.snapshotDatabase().snapshotPublicationDao().parts(state.chainId, compactionId)
                    check(parts.isNotEmpty() && parts.all { it.status in setOf("staged", "receipted") && it.uploadReceiptJson != null }) {
                        "C_ACCEPTANCE_UNPROVEN"
                    }
                    val stagedReceipts = JsonArray(parts.map { Json.parseToJsonElement(checkNotNull(it.uploadReceiptJson)) })
                    val targetPin = Json.parseToJsonElement(targetPinText).jsonObject
                    val currentParameters = authority.getValue("parameters").toString()
                    val currentEpoch =
                        authority
                            .getValue("epochs")
                            .jsonObject.values
                            .maxBy {
                                it.jsonObject
                                    .getValue("record")
                                    .jsonObject
                                    .getValue("epoch")
                                    .jsonPrimitive.long
                            }.jsonObject
                            .getValue("record")
                            .toString()
                    val resumed =
                        if (preparedJournal.transparencyHeadJson != null && preparedJournal.transparencyAnchorJson != null) {
                            PallaSyncReseedTransparencyVerifier.Resume(
                                Json.parseToJsonElement(preparedJournal.transparencyHeadJson).jsonObject,
                                Json.parseToJsonElement(preparedJournal.transparencyAnchorJson).jsonObject,
                                Json
                                    .parseToJsonElement(
                                        preparedJournal.verifiedReceiptIndicesJson,
                                    ).jsonArray
                                    .map { it.jsonPrimitive.long }
                                    .toSet(),
                            )
                        } else {
                            null
                        }
                    val workflow = PallaSyncReseedCommitRelay(remote)
                    db.transaction {
                        val current = checkNotNull(db.pallaSyncDao().getChainState(state.chainId)) { "C_MISSING_DEPENDENCY" }
                        check(
                            current.logId == stable.logId && current.generation == stable.generation &&
                                current.writeState in setOf("active", "epoch_rebase_required"),
                        ) { "C_MISSING_DEPENDENCY" }
                        db.pallaSyncDao().updateChainState(current.copy(writeState = "epoch_rebase_required"))
                    }
                    val result =
                        preparedJournal.commitResultJson?.let { saved ->
                            PallaSyncHttpResult.Success(Json.parseToJsonElement(saved).jsonObject)
                        } ?: workflow.commit(
                            root = root,
                            target = target,
                            canonicalBody = checkNotNull(preparedJournal.commitBodyJson),
                            currentParametersJson = currentParameters,
                            currentEpochJson = currentEpoch,
                            expectedEntriesJson = checkNotNull(preparedJournal.expectedEntriesJson),
                            targetPinJson = targetPinText,
                            stagedSnapshotReceiptsJson = stagedReceipts.toString(),
                            persistVerifiedTransparencyPage = { items, anchor, observedHead, expectedReceiptIndices ->
                                val receiptIndices =
                                    expectedReceiptIndices
                                        .filter { it <= anchor.getValue("index").jsonPrimitive.long }
                                        .sorted()
                                db.transaction {
                                    items.forEach { item ->
                                        db.protocolDao().retainEvidence(
                                            ProtectedEvidenceEntity(
                                                origin,
                                                item.getValue("relay_key_id").jsonPrimitive.content,
                                                state.chainId,
                                                targetPin.getValue("log_id").jsonPrimitive.content,
                                                "transparency",
                                                item.getValue("entry_index").jsonPrimitive.long,
                                                checkNotNull(PallaSyncCore.objectHash(item.toString())),
                                                item.toString(),
                                            ),
                                        )
                                    }
                                    val current = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                                    journalDao.saveMonotone(
                                        current.copy(
                                            transparencyHeadJson = observedHead.toString(),
                                            transparencyAnchorJson = anchor.toString(),
                                            verifiedReceiptIndicesJson = JsonArray(receiptIndices.map(::JsonPrimitive)).toString(),
                                            phase = maxOf(3, current.phase),
                                            updatedAtMs = policy.retry.correctedTime(origin),
                                        ),
                                    )
                                }
                            },
                            confirmStagedSnapshotReceipts = {
                                val status = db.snapshotDatabase().snapshotPublicationDao().parts(state.chainId, compactionId)
                                if (status.all { it.status == "staged" }) {
                                    PallaSyncSnapshotPublicationJournal(
                                        db.snapshotDatabase(),
                                        state.chainId,
                                        compactionId,
                                        checkNotNull(keystore.pendingReseed()?.keys?.epoch) { "C_MISSING_DEPENDENCY" },
                                    ).confirmStagedTransparencySuffix()
                                } else {
                                    check(status.isNotEmpty() && status.all { it.status == "receipted" }) { "C_ACCEPTANCE_UNPROVEN" }
                                }
                            },
                            transparencyResume = resumed,
                        )
                    if (result is PallaSyncHttpResult.Success) {
                        val pending = checkNotNull(keystore.pendingReseed()) { "C_MISSING_DEPENDENCY" }
                        check(pending.targetOrigin == origin && pending.keys.chainId == state.chainId) { "C_ROLLBACK_DETECTED" }
                        val bundle = Json.parseToJsonElement(preparedJournal.preparedBundleJson ?: error("C_MISSING_DEPENDENCY")).jsonObject
                        val expectedEntries = Json.parseToJsonElement(checkNotNull(preparedJournal.expectedEntriesJson)).jsonArray
                        val targetAuthority =
                            verifyReseedTargetAuthority(
                                root,
                                targetPin,
                                authority,
                                expectedEntries,
                                result.value,
                            )
                        val verifiedCommitJournal = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                        journalDao.saveMonotone(
                            verifiedCommitJournal.copy(
                                commitResultJson = result.value.toString(),
                                phase = maxOf(4, verifiedCommitJournal.phase),
                                updatedAtMs = policy.retry.correctedTime(origin),
                            ),
                        )
                        val newEpoch = bundle.getValue("epoch").jsonObject
                        val newDeviceId = pending.keys.deviceId ?: error("C_BOOTSTRAP_TRUST_REQUIRED")
                        val ackUrl = PallaSyncUrls.deviceKeysAck(target, state.chainId, newDeviceId)
                        val ackBody =
                            checkNotNull(
                                PallaSyncCore.canonicalJson(
                                    buildJsonObject {
                                        put("epoch_hash", bundle.getValue("epoch_hash"))
                                        put("epoch_commitment", newEpoch.getValue("epoch_commitment"))
                                    }.toString(),
                                ),
                            ) { "C_SCHEMA_VIOLATION" }
                        val ackToken =
                            checkNotNull(
                                crypto.createCapabilityToken(
                                    chainId = state.chainId,
                                    deviceId = newDeviceId,
                                    method = "POST",
                                    path = ackUrl.encodedPath,
                                    bodyJson = ackBody,
                                    signingKeyBase64 = pending.keys.signingKeyBase64Url,
                                    ttlMs = 120_000L,
                                    aud = origin,
                                    signerKind = "device",
                                ),
                            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                        val ack =
                            remote.execute(
                                Request
                                    .Builder()
                                    .url(ackUrl)
                                    .header("Accept", PALLASYNC_MEDIA_TYPE)
                                    .header("Authorization", "PallaSync $ackToken")
                                    .post(ackBody.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
                                    .build(),
                                migrationDestination = true,
                            ) { response ->
                                PallaSyncHttpResult.Success(
                                    Json
                                        .parseToJsonElement(
                                            PallaSyncCore.validateKeysAck(
                                                response.pallaSyncBytes(),
                                                newDeviceId,
                                                bundle.getValue("epoch_hash").jsonPrimitive.content,
                                            ),
                                        ).jsonObject,
                                )
                            }
                        if (ack !is PallaSyncHttpResult.Success) {
                            return@withLock when (ack) {
                                PallaSyncHttpResult.Gone -> PallaSyncHttpResult.Gone
                                is PallaSyncHttpResult.ProtocolError -> ack
                                is PallaSyncHttpResult.Retryable -> ack
                                is PallaSyncHttpResult.Success -> error("C_SCHEMA_VIOLATION")
                            }
                        }
                        // §10.8: only a validated acknowledgement for this chain completes the
                        // prerequisite behind exact DEVICE_PENDING_KEYS retries.
                        policy.retry.resolved(origin, state.chainId, "DEVICE_PENDING_KEYS")
                        // Capture after target key acknowledgement, immediately before local cutover.
                        // This includes the longest network step after the earlier post-commit capture.
                        val sourceConflict =
                            captureReseedSourceConflict(
                                PallaSyncInitialHistory(db, remote, policy, root, source),
                                sourceOrigin,
                                stable,
                                authority,
                            )
                        val currentJournal = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                        journalDao.saveMonotone(
                            currentJournal.copy(
                                sourceConflictReportJson = sourceConflict.toString(),
                                updatedAtMs = policy.retry.correctedTime(sourceOrigin),
                            ),
                        )
                        val finalJournal = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                        db.transaction {
                            val targetLogId = targetPin.getValue("log_id").jsonPrimitive.content
                            result.value.getValue("receipts").jsonArray.forEachIndexed { index, receiptElement ->
                                val receipt = receiptElement.jsonObject
                                val sequence = receipt.getValue("relay_seq").jsonPrimitive.long
                                db.protocolDao().retainEvidence(
                                    ProtectedEvidenceEntity(
                                        origin,
                                        receipt.getValue("relay_key_id").jsonPrimitive.content,
                                        state.chainId,
                                        targetLogId,
                                        "upload_receipt",
                                        sequence,
                                        checkNotNull(PallaSyncCore.objectHash(receipt.toString())),
                                        receipt.toString(),
                                    ),
                                )
                            }
                            db.protocolDao().retainAuthority(
                                com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity(
                                    state.chainId,
                                    "checkpoint",
                                    checkNotNull(PallaSyncCore.objectHash(targetAuthority.toString())),
                                    bundle.getValue("generation").jsonPrimitive.long,
                                    targetAuthority.toString(),
                                    "reseed_candidate",
                                ),
                            )
                            val currentJournal = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                            journalDao.saveMonotone(
                                currentJournal.copy(
                                    commitResultJson = result.value.toString(),
                                    phase = maxOf(5, currentJournal.phase),
                                    updatedAtMs = policy.retry.correctedTime(origin),
                                ),
                            )
                        }
                    }
                    if (result is PallaSyncHttpResult.Success) {
                        finishReseedCutover(state.chainId, checkNotNull(reseedDao.get(state.chainId)))
                    }
                    result
                } finally {
                    root.close()
                }
            }
        }

    internal suspend fun pendingReseedCutoverTarget(): String? =
        withContext(Dispatchers.IO) {
            val chain = db.pallaSyncDao().getActiveChainState()?.chainId ?: return@withContext null
            db
                .snapshotDatabase()
                .reseedJournalDao()
                .get(chain)
                ?.takeIf { it.phase in 5..6 }
                ?.targetOrigin
        }

    internal suspend fun pendingReseedCommitTarget(): String? =
        withContext(Dispatchers.IO) {
            val chain = db.pallaSyncDao().getActiveChainState()?.chainId ?: return@withContext null
            db
                .snapshotDatabase()
                .reseedJournalDao()
                .get(chain)
                ?.takeIf { it.phase in 3..4 }
                ?.targetOrigin
        }

    internal suspend fun pendingReseedSourceConflict(): PallaSyncReseedSourceConflict.Summary? =
        withContext(Dispatchers.IO) {
            val chain = db.pallaSyncDao().getActiveChainState()?.chainId ?: return@withContext null
            val report =
                db
                    .snapshotDatabase()
                    .reseedJournalDao()
                    .get(chain)
                    ?.sourceConflictReportJson
                    ?.let { Json.parseToJsonElement(it).jsonObject }
                    ?: return@withContext null
            PallaSyncReseedSourceConflict.summary(report.toString())
        }

    internal suspend fun canAuditReseedSource(): Boolean =
        withContext(Dispatchers.IO) {
            val state = db.pallaSyncDao().getActiveChainState() ?: return@withContext false
            db
                .snapshotDatabase()
                .reseedJournalDao()
                .get(state.chainId)
                ?.phase == RESEED_COMPLETE_JOURNAL_PHASE && state.writeState == "active"
        }

    /** §17.6: explicitly replay the retired source to report any late old-generation suffix. */
    internal suspend fun auditReseedSource(): PallaSyncReseedSourceConflict.Summary? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_MISSING_DEPENDENCY" }
                val journalDao = db.snapshotDatabase().reseedJournalDao()
                val journal = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                check(journal.phase == RESEED_COMPLETE_JOURNAL_PHASE && state.writeState == "active") {
                    "C_MISSING_DEPENDENCY"
                }
                val baselineText = checkNotNull(journal.sourceConflictReportJson) { "C_MISSING_DEPENDENCY" }
                val baseline = Json.parseToJsonElement(baselineText).jsonObject
                val sourceEntity =
                    checkNotNull(db.protocolDao().archivedAuthority(state.chainId, journal.sourceGeneration)) {
                        "C_MISSING_DEPENDENCY"
                    }
                val sourceAuthority = Json.parseToJsonElement(sourceEntity.signedJson).jsonObject
                val sourceAuthorityHash = checkNotNull(PallaSyncCore.objectHash(sourceEntity.signedJson))
                val sourcePin = sourceAuthority.getValue("pin").jsonObject
                val sourceOrigin = sourcePin.getValue("origin").jsonPrimitive.content
                check(
                    sourceEntity.generation == journal.sourceGeneration &&
                        sourcePin.getValue("chain_id").jsonPrimitive.content == state.chainId &&
                        sourcePin.getValue("log_id").jsonPrimitive.content == journal.sourceLogId &&
                        baseline.getValue("source_origin").jsonPrimitive.content == sourceOrigin &&
                        baseline.getValue("source_log_id").jsonPrimitive.content == journal.sourceLogId &&
                        baseline.getValue("source_generation").jsonPrimitive.long == journal.sourceGeneration,
                ) { "C_ROLLBACK_DETECTED" }
                val source =
                    when (val normalized = PallaSyncUrls.normalize(sourceOrigin)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                check(PallaSyncUrls.extractOrigin(source) == sourceOrigin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                policy.requireCurrent(sourceOrigin)
                requireSnapshotResult(refreshRelayHealth(source))
                check(policy.trustedRelayKey(sourceOrigin) == sourcePin.getValue("public_key").jsonPrimitive.content) {
                    "C_RELAY_KEY_MISMATCH"
                }
                val archivedKeys = checkNotNull(keystore.getArchivedChainKeys(state.chainId)) { "C_MISSING_DEPENDENCY" }
                check(
                    archivedKeys.deviceId != null && archivedKeys.chainId == state.chainId &&
                        PallaSyncCore.validateCurrentKeyRecipient(sourceAuthority.toString(), checkNotNull(archivedKeys.deviceId)),
                ) { "C_MISSING_DEPENDENCY" }
                val sourceAnchor =
                    checkNotNull(keystore.reseedSourceTransparencyPin(state.chainId, sourceAuthorityHash)) {
                        "C_MISSING_DEPENDENCY"
                    }
                val sourceCut =
                    state.copy(
                        generation = journal.sourceGeneration,
                        logId = journal.sourceLogId,
                        materializedSeq = baseline.getValue("source_cut_seq").jsonPrimitive.long,
                        verifiedHash = baseline.getValue("source_cut_hash").jsonPrimitive.content,
                        parametersHash = baseline.getValue("source_cut_parameters_hash").jsonPrimitive.content,
                        epochHash = baseline.getValue("source_cut_epoch_hash").jsonPrimitive.content,
                    )
                val history =
                    PallaSyncInitialHistory(
                        store = db,
                        remote = remote,
                        policy = policy,
                        root = null,
                        base = source,
                        peerChainId = state.chainId,
                        peerAdminPublicKey = sourceAuthority.getValue("admin_public_key").jsonPrimitive.content,
                        importedAuthority = sourceAuthority,
                        peerToken = { url, path, query ->
                            crypto.createCapabilityToken(
                                chainId = state.chainId,
                                deviceId = checkNotNull(archivedKeys.deviceId),
                                method = "GET",
                                path = path,
                                query = query,
                                signingKeyBase64 = archivedKeys.signingKeyBase64Url,
                                ttlMs = 120_000L,
                                aud = PallaSyncUrls.extractOrigin(url),
                                signerKind = "device",
                            )
                        },
                        source = null,
                    )
                val report = captureReseedSourceConflict(history, sourceOrigin, sourceCut, sourceAuthority, sourceAnchor)
                val latest = checkNotNull(journalDao.get(state.chainId)) { "C_MISSING_DEPENDENCY" }
                journalDao.saveMonotone(
                    latest.copy(
                        sourceConflictReportJson = report.toString(),
                        updatedAtMs = policy.retry.correctedTime(sourceOrigin),
                    ),
                )
                PallaSyncReseedSourceConflict.summary(report.toString())
            }
        }

    internal suspend fun resumeStagedReseedCutover(): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val chain = db.pallaSyncDao().getActiveChainState()?.chainId ?: return@withLock false
                val journal = db.snapshotDatabase().reseedJournalDao().get(chain) ?: return@withLock false
                if (journal.phase !in 5..6) return@withLock false
                val target =
                    when (val normalized = PallaSyncUrls.normalize(journal.targetOrigin)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> return@withLock false
                    }
                check(PallaSyncUrls.extractOrigin(target) == journal.targetOrigin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                policy.requireCurrent(journal.targetOrigin)
                requireSnapshotResult(refreshRelayHealth(target))
                finishReseedCutover(chain, journal)
                db
                    .snapshotDatabase()
                    .reseedJournalDao()
                    .get(chain)
                    ?.phase == 7
            }
        }

    /** Completes the journaled Room, Keystore, outbox, and selected-origin handoff for §17.5. */
    private suspend fun finishReseedCutover(
        chain: String,
        journal: ReseedJournalEntity,
    ) {
        check(journal.phase >= 5 && journal.commitResultJson != null && journal.preparedBundleJson != null) {
            "C_MISSING_DEPENDENCY"
        }
        val prepared = Json.parseToJsonElement(journal.preparedBundleJson).jsonObject
        val targetGeneration = prepared.getValue("generation").jsonPrimitive.long
        val anchor = Json.parseToJsonElement(checkNotNull(journal.transparencyAnchorJson)).jsonObject
        val protocolDao = db.protocolDao()
        val chainDao = db.pallaSyncDao()
        val currentText = protocolDao.currentAuthority(chain)
        val current = currentText?.let { Json.parseToJsonElement(it).jsonObject }
        if (journal.phase >= 7) {
            val state = checkNotNull(chainDao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
            val activeKeys = checkNotNull(keystore.getActiveChainKeys()) { "C_MISSING_DEPENDENCY" }
            val sourceAuthority =
                Json
                    .parseToJsonElement(
                        checkNotNull(protocolDao.archivedAuthority(chain, journal.sourceGeneration)).signedJson,
                    ).jsonObject
            val targetAuthority = Json.parseToJsonElement(checkNotNull(currentText)).jsonObject
            val replayed =
                verifyReseedAuthorityFromSavedEvidence(
                    sourceAuthority,
                    Json.parseToJsonElement(journal.targetPinJson).jsonObject,
                    Json.parseToJsonElement(checkNotNull(journal.expectedEntriesJson)).jsonArray,
                    Json.parseToJsonElement(checkNotNull(journal.commitResultJson)).jsonObject,
                )
            check(
                state.generation == targetGeneration && state.writeState == "active" &&
                    PallaSyncCore.objectHash(checkNotNull(currentText)) == keystore.authorityHash(chain) &&
                    PallaSyncCore.objectHash(replayed.toString()) == PallaSyncCore.objectHash(targetAuthority.toString()) &&
                    activeKeys.chainId == chain && activeKeys.deviceId == prepared.getValue("device_id").jsonPrimitive.content &&
                    activeKeys.epoch ==
                    prepared
                        .getValue("epoch")
                        .jsonObject
                        .getValue("epoch")
                        .jsonPrimitive.long,
            ) { "C_ROLLBACK_DETECTED" }
            return
        }
        val sourceEntity =
            if (current
                    ?.get("parameters")
                    ?.jsonObject
                    ?.get("generation")
                    ?.jsonPrimitive
                    ?.long == journal.sourceGeneration
            ) {
                val hash = checkNotNull(PallaSyncCore.objectHash(checkNotNull(currentText)))
                check(keystore.authorityHash(chain) == hash) { "C_ROLLBACK_DETECTED" }
                checkNotNull(protocolDao.authorityObject(chain, "checkpoint", hash)) { "C_MISSING_DEPENDENCY" }
            } else {
                checkNotNull(protocolDao.archivedAuthority(chain, journal.sourceGeneration)) { "C_MISSING_DEPENDENCY" }
            }
        val sourceAuthority = Json.parseToJsonElement(sourceEntity.signedJson).jsonObject
        val sourceHash = checkNotNull(PallaSyncCore.objectHash(sourceEntity.signedJson))
        check(sourceEntity.generation == journal.sourceGeneration) { "C_ROLLBACK_DETECTED" }
        val targetEntity =
            if (current
                    ?.get("parameters")
                    ?.jsonObject
                    ?.get("generation")
                    ?.jsonPrimitive
                    ?.long == targetGeneration
            ) {
                checkNotNull(
                    protocolDao.authorityObject(chain, "checkpoint", checkNotNull(PallaSyncCore.objectHash(checkNotNull(currentText)))),
                )
            } else {
                checkNotNull(protocolDao.reseedCandidateAuthority(chain, targetGeneration)) { "C_MISSING_DEPENDENCY" }
            }
        val targetAuthority = Json.parseToJsonElement(targetEntity.signedJson).jsonObject
        val targetHash = checkNotNull(PallaSyncCore.objectHash(targetEntity.signedJson))
        val targetPin = targetAuthority.getValue("pin").jsonObject
        val replayedAuthority =
            verifyReseedAuthorityFromSavedEvidence(
                sourceAuthority,
                Json.parseToJsonElement(journal.targetPinJson).jsonObject,
                Json.parseToJsonElement(checkNotNull(journal.expectedEntriesJson)).jsonArray,
                Json.parseToJsonElement(checkNotNull(journal.commitResultJson)).jsonObject,
            )
        check(
            targetEntity.validationState in setOf("reseed_candidate", "current_checkpoint") &&
                targetEntity.generation == targetGeneration && targetPin.getValue("origin").jsonPrimitive.content == journal.targetOrigin &&
                PallaSyncCore.objectHash(replayedAuthority.toString()) == targetHash &&
                targetPin
                    .getValue("log_id")
                    .jsonPrimitive.content
                    .isNotBlank(),
        ) { "C_ROLLBACK_DETECTED" }
        val targetEpoch =
            prepared
                .getValue("epoch")
                .jsonObject
                .getValue("epoch")
                .jsonPrimitive.long
        val parameters = targetAuthority.getValue("parameters").jsonObject
        val epochRecord =
            targetAuthority
                .getValue("epochs")
                .jsonObject
                .getValue(targetEpoch.toString())
                .jsonObject
                .getValue("record")
        val preparedDeviceId = prepared.getValue("device_id").jsonPrimitive.content
        val deviceRecord = prepared.getValue("device_record").jsonObject
        val bundledDeviceId = deviceRecord.getValue("device_id").jsonPrimitive.content
        val parametersGeneration = parameters.getValue("generation").jsonPrimitive.long
        val epochRecordNumber =
            epochRecord.jsonObject
                .getValue("epoch")
                .jsonPrimitive.long
        check(
            parametersGeneration == targetGeneration &&
                epochRecordNumber == targetEpoch &&
                preparedDeviceId == bundledDeviceId,
        ) { "C_ROLLBACK_DETECTED" }
        val sequence = targetAuthority.getValue("sequence").jsonPrimitive.long
        val headHash = targetAuthority.getValue("head_hash").jsonPrimitive.content
        if (currentText != targetEntity.signedJson) {
            db.transaction {
                val state = checkNotNull(chainDao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
                check(state.generation == journal.sourceGeneration && state.logId == journal.sourceLogId) { "C_ROLLBACK_DETECTED" }
                protocolDao.installReseedAuthority(chain, targetHash, targetEntity.signedJson)
                chainDao.updateChainState(
                    state.copy(
                        keyEpoch = targetEpoch,
                        latestControlSeq = sequence,
                        prevControlHash = headHash,
                        lastRelaySeq = sequence,
                        generation = targetGeneration,
                        authorSeq = 0,
                        authorTailHash = null,
                        logId = targetPin.getValue("log_id").jsonPrimitive.content,
                        parametersHash = checkNotNull(PallaSyncCore.objectHash(parameters.toString())),
                        epochHash = checkNotNull(PallaSyncCore.objectHash(epochRecord.toString())),
                        downloadedSeq = sequence,
                        verifiedSeq = sequence,
                        materializedSeq = sequence,
                        verifiedHash = headHash,
                        transparencyId = anchor.getValue("transparency_id").jsonPrimitive.content,
                        transparencyIndex = anchor.getValue("index").jsonPrimitive.long,
                        transparencyHash = anchor.getValue("hash").jsonPrimitive.content,
                        writeState = "epoch_rebase_required",
                    ),
                )
                val currentJournal = checkNotNull(db.snapshotDatabase().reseedJournalDao().get(chain))
                db.snapshotDatabase().reseedJournalDao().saveMonotone(
                    currentJournal.copy(
                        phase = maxOf(RESEED_CUTOVER_JOURNAL_PHASE, currentJournal.phase),
                        updatedAtMs = policy.retry.correctedTime(journal.targetOrigin),
                    ),
                )
            }
        }
        rebaseFault(PallaSyncOutboxRebase.Boundary.RESEED_ROOM_COMMITTED)
        val targetDevice =
            checkNotNull(keystore.pendingReseed()?.keys?.deviceId ?: keystore.getActiveChainKeys()?.deviceId) {
                "C_MISSING_DEPENDENCY"
            }
        keystore.pendingReseed()?.let { pending ->
            check(
                pending.keys.chainId == chain && pending.keys.deviceId == targetDevice &&
                    pending.targetOrigin == journal.targetOrigin && pending.publicBundleJson == journal.preparedBundleJson,
            ) { "C_ROLLBACK_DETECTED" }
        }
        val activeDevice = keystore.getActiveChainKeys()?.deviceId
        val sourceDevice =
            if (activeDevice == targetDevice) {
                checkNotNull(keystore.getArchivedChainKeys(chain)?.deviceId) { "C_MISSING_DEPENDENCY" }
            } else {
                checkNotNull(activeDevice) { "C_MISSING_DEPENDENCY" }
            }
        if (keystore.authorityHash(chain) == sourceHash) {
            check(keystore.promotePendingReseed(chain, sourceDevice, sourceHash, targetHash, anchor)) { "C_LOCAL_STORAGE_FULL" }
        } else {
            check(keystore.authorityHash(chain) == targetHash && keystore.boundDeviceId(chain) != sourceDevice) {
                "C_ROLLBACK_DETECTED"
            }
        }
        rebaseFault(PallaSyncOutboxRebase.Boundary.RESEED_KEYSTORE_PROMOTED)
        val sourceKeys = checkNotNull(keystore.getArchivedChainKeys(chain)) { "C_MISSING_DEPENDENCY" }
        check(sourceKeys.deviceId == sourceDevice) { "C_ROLLBACK_DETECTED" }
        val targetKeys = checkNotNull(keystore.getActiveChainKeys()) { "C_MISSING_DEPENDENCY" }
        check(
            targetKeys.chainId == chain && targetKeys.deviceId == prepared.getValue("device_id").jsonPrimitive.content &&
                targetKeys.epoch == targetEpoch,
        ) { "C_ROLLBACK_DETECTED" }
        outboxRebase.resumeReseed(chain, targetKeys, sourceKeys, sourceAuthority)
        rebaseFault(PallaSyncOutboxRebase.Boundary.RESEED_OUTBOX_REBASED)
        SettingsStore(appContext).setPallaSyncServerUrlFromCoordinator(journal.targetOrigin)
        rebaseFault(PallaSyncOutboxRebase.Boundary.RESEED_ORIGIN_SELECTED)
        db.transaction {
            val currentState = checkNotNull(chainDao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
            var after = 0L
            val through = chainDao.lastPendingOutboxId(chain) ?: 0L
            while (after < through) {
                val page = chainDao.pendingOutboxPage(chain, after, PALLASYNC_PAGE_SIZE).takeWhile { it.id <= through }
                if (page.isEmpty()) break
                page.forEach { outbox ->
                    val wire = Json.decodeFromString<PallaSyncWireRecord>(outbox.eventJson)
                    check(
                        wire.chainId == chain && wire.deviceId == targetKeys.deviceId && wire.generation == targetGeneration &&
                            wire.recordId == outbox.recordId && PallaSyncCore.objectHash(outbox.eventJson) == outbox.objectHash &&
                            PallaSyncCore.verifySyncRecord(outbox.eventJson, targetKeys.publicKeyBase64Url),
                    ) { "C_ACCEPTANCE_UNPROVEN" }
                    after = outbox.id
                }
            }
            check(
                currentState.generation == targetGeneration && currentState.logId == targetPin.getValue("log_id").jsonPrimitive.content &&
                    currentState.writeState == "epoch_rebase_required",
            ) { "C_ACCEPTANCE_UNPROVEN" }
            chainDao.updateChainState(currentState.copy(writeState = "active"))
            val currentJournal = checkNotNull(db.snapshotDatabase().reseedJournalDao().get(chain))
            db.snapshotDatabase().reseedJournalDao().saveMonotone(
                currentJournal.copy(
                    phase = maxOf(RESEED_COMPLETE_JOURNAL_PHASE, currentJournal.phase),
                    updatedAtMs = policy.retry.correctedTime(journal.targetOrigin),
                ),
            )
        }
        rebaseFault(PallaSyncOutboxRebase.Boundary.RESEED_COMPLETE_COMMITTED)
    }

    /** Reconstructs the cutover checkpoint after verifying the target's exact receipts. */
    private fun verifyReseedAuthorityFromSavedEvidence(
        sourceAuthority: JsonObject,
        targetPin: JsonObject,
        expectedEntries: JsonArray,
        checkpoint: JsonObject,
    ): JsonObject {
        val operation =
            expectedEntries
                .single {
                    it.jsonObject
                        .getValue("kind")
                        .jsonPrimitive.content == "admin"
                }.jsonObject
                .getValue("object")
                .jsonObject
        val entries = expectedEntries.map { it.jsonObject }

        fun unique(
            kind: String,
            predicate: (JsonObject) -> Boolean = { true },
        ): JsonObject =
            entries
                .single { it.getValue("kind").jsonPrimitive.content == kind && predicate(it.getValue("object").jsonObject) }
                .getValue("object")
                .jsonObject
        val parameters =
            unique("parameters") {
                it.getValue("generation").jsonPrimitive.long >
                    sourceAuthority
                        .getValue("parameters")
                        .jsonObject
                        .getValue("generation")
                        .jsonPrimitive.long
            }
        val targetGeneration = parameters.getValue("generation").jsonPrimitive.long
        val epoch =
            unique("epoch") {
                it.getValue("reason").jsonPrimitive.content == "reseed" &&
                    it.getValue("generation").jsonPrimitive.long == targetGeneration
            }
        val compaction =
            unique("compaction") {
                it.getValue("log_id").jsonPrimitive.content == targetPin.getValue("log_id").jsonPrimitive.content
            }
        val deviceRecords =
            JsonArray(
                entries
                    .filter { it.getValue("kind").jsonPrimitive.content == "device" }
                    .map { it.getValue("object") }
                    .filter {
                        it.jsonObject
                            .getValue("generation")
                            .jsonPrimitive.long ==
                            parameters.getValue("generation").jsonPrimitive.long
                    },
            )
        val envelopes =
            JsonArray(
                entries
                    .filter { it.getValue("kind").jsonPrimitive.content == "envelope" }
                    .map { it.getValue("object") }
                    .filter {
                        it.jsonObject
                            .getValue("generation")
                            .jsonPrimitive.long ==
                            parameters.getValue("generation").jsonPrimitive.long
                    },
            )
        val payload =
            buildJsonObject {
                put("parameters", parameters)
                put("epoch", epoch)
                put("envelopes", envelopes)
                put("compaction", compaction)
                put("device_records", deviceRecords)
            }
        val receipts = checkpoint.getValue("receipts").jsonArray
        val response =
            buildJsonObject {
                put("parameters_hash", checkpoint.getValue("parameters_hash"))
                put("epoch_hash", checkpoint.getValue("epoch_hash"))
                put("log_id", checkpoint.getValue("log_id"))
                put("head_seq", checkpoint.getValue("head_seq"))
                put("head_hash", checkpoint.getValue("head_hash"))
                put("upload_receipts", JsonArray(receipts))
            }
        checkNotNull(
            PallaSyncCore.validateReseedCommitAcceptance(
                response.toString().toByteArray(Charsets.UTF_8),
                payload.toString(),
                operation.toString(),
                sourceAuthority.getValue("parameters").toString(),
                sourceAuthority
                    .getValue("epochs")
                    .jsonObject.values
                    .maxBy {
                        it.jsonObject
                            .getValue("record")
                            .jsonObject
                            .getValue("epoch")
                            .jsonPrimitive.long
                    }.jsonObject
                    .getValue("record")
                    .toString(),
                expectedEntries.toString(),
                targetPin.toString(),
            ),
        )
        return buildReseedAuthorityCheckpoint(sourceAuthority, targetPin, parameters, epoch, operation, expectedEntries, checkpoint)
    }

    /** Import logs group historical controls by kind; replaying them as an ordinary genesis prefix is invalid. */
    private fun verifyReseedTargetAuthority(
        root: PallaSyncRootSession,
        targetPin: JsonObject,
        sourceAuthority: JsonObject,
        expectedEntries: JsonArray,
        checkpoint: JsonObject,
    ): JsonObject {
        check(root.identity.chainId == targetPin.getValue("chain_id").jsonPrimitive.content) { "C_RECOVERY_ROOT_MISMATCH" }
        return verifyReseedAuthorityFromSavedEvidence(sourceAuthority, targetPin, expectedEntries, checkpoint)
    }

    private fun buildReseedAuthorityCheckpoint(
        sourceAuthority: JsonObject,
        targetPin: JsonObject,
        parameters: JsonObject,
        epoch: JsonObject,
        operation: JsonObject,
        expectedEntries: JsonArray,
        checkpoint: JsonObject,
    ): JsonObject {
        val entries = expectedEntries.map { it.jsonObject }
        val receipts = checkpoint.getValue("receipts").jsonArray
        check(receipts.size == entries.size) { "C_ACCEPTANCE_UNPROVEN" }
        val seqByHash = mutableMapOf<String, Long>()
        entries.forEachIndexed { index, entry ->
            val objectText = entry.getValue("object").toString()
            val hash = checkNotNull(PallaSyncCore.objectHash(objectText)) { "C_SCHEMA_VIOLATION" }
            val receipt = receipts[index].jsonObject
            check(receipt.getValue("object_hash").jsonPrimitive.content == hash) { "C_ACCEPTANCE_UNPROVEN" }
            seqByHash[hash] = receipt.getValue("relay_seq").jsonPrimitive.long
        }
        val importedDevices = sourceAuthority.getValue("devices").jsonObject.toMutableMap()
        sourceAuthority.getValue("devices").jsonObject.forEach { (id, admitted) ->
            val value = admitted.jsonObject
            val record = value.getValue("record").jsonObject
            val hash = checkNotNull(PallaSyncCore.objectHash(record.toString())) { "C_SCHEMA_VIOLATION" }
            val sequence = seqByHash[hash] ?: error("C_ACCEPTANCE_UNPROVEN")
            importedDevices[id] =
                buildJsonObject {
                    put("record", record)
                    put("enrolled_seq", sequence)
                    put("revoked_seq", value.getValue("revoked_seq"))
                }
        }
        val importedEpochs = sourceAuthority.getValue("epochs").jsonObject.toMutableMap()
        sourceAuthority.getValue("epochs").jsonObject.forEach { (number, accepted) ->
            val value = accepted.jsonObject
            val record = value.getValue("record").jsonObject
            val hash = checkNotNull(PallaSyncCore.objectHash(record.toString())) { "C_SCHEMA_VIOLATION" }
            val sequence = seqByHash[hash] ?: error("C_ACCEPTANCE_UNPROVEN")
            val receipt =
                receipts
                    .single {
                        it.jsonObject
                            .getValue("relay_seq")
                            .jsonPrimitive.long == sequence
                    }.jsonObject
            importedEpochs[number] =
                buildJsonObject {
                    put("record", record)
                    put("relay_seq", sequence)
                    put("committed_at_ms", receipt.getValue("received_at_ms"))
                }
            record.getValue("revoked_device_ids").jsonArray.forEach { revoked ->
                val admitted = importedDevices.getValue(revoked.jsonPrimitive.content).jsonObject
                importedDevices[revoked.jsonPrimitive.content] =
                    buildJsonObject {
                        put("record", admitted.getValue("record"))
                        put("enrolled_seq", admitted.getValue("enrolled_seq"))
                        put("revoked_seq", sequence)
                    }
            }
        }
        val newEpochHash = checkNotNull(PallaSyncCore.objectHash(epoch.toString())) { "C_SCHEMA_VIOLATION" }
        val newEpochSequence = seqByHash[newEpochHash] ?: error("C_ACCEPTANCE_UNPROVEN")
        val newEpochReceipt =
            receipts
                .single {
                    it.jsonObject
                        .getValue("relay_seq")
                        .jsonPrimitive.long == newEpochSequence
                }.jsonObject
        epoch.getValue("revoked_device_ids").jsonArray.forEach { revoked ->
            val id = revoked.jsonPrimitive.content
            val admitted = importedDevices[id]?.jsonObject ?: error("C_ACCEPTANCE_UNPROVEN")
            importedDevices[id] =
                buildJsonObject {
                    put("record", admitted.getValue("record"))
                    put("enrolled_seq", admitted.getValue("enrolled_seq"))
                    put("revoked_seq", newEpochSequence)
                }
        }
        importedEpochs[epoch.getValue("epoch").jsonPrimitive.content] =
            buildJsonObject {
                put("record", epoch)
                put("relay_seq", newEpochSequence)
                put("committed_at_ms", newEpochReceipt.getValue("received_at_ms"))
            }
        entries
            .filter { it.getValue("kind").jsonPrimitive.content == "device" }
            .map { it.getValue("object").jsonObject }
            .filter { it.getValue("generation").jsonPrimitive.long == parameters.getValue("generation").jsonPrimitive.long }
            .forEach { record ->
                val id = record.getValue("device_id").jsonPrimitive.content
                val sequence = seqByHash[checkNotNull(PallaSyncCore.objectHash(record.toString()))] ?: error("C_ACCEPTANCE_UNPROVEN")
                importedDevices[id] =
                    buildJsonObject {
                        put("record", record)
                        put("enrolled_seq", sequence)
                        put("revoked_seq", JsonNull)
                    }
            }
        val operationId = operation.getValue("operation_id").jsonPrimitive.content
        val operationHash = checkNotNull(PallaSyncCore.objectHash(operation.toString())) { "C_SCHEMA_VIOLATION" }
        return buildJsonObject {
            put("pin", targetPin)
            put("admin_public_key", sourceAuthority.getValue("admin_public_key"))
            put("sequence", checkpoint.getValue("head_seq"))
            put("head_hash", checkpoint.getValue("head_hash"))
            put("parameters", parameters)
            put("devices", JsonObject(importedDevices))
            put("epochs", JsonObject(importedEpochs))
            put("staged_envelopes", JsonObject(emptyMap()))
            put("control_batch", JsonArray(emptyList()))
            put("pending_admin", JsonNull)
            put("admin_operations", buildJsonObject { put(operationId, operationHash) })
            put("action_payloads", JsonObject(emptyMap()))
        }
    }

    /** An unsigned head can only veto stale source state; the local verified checkpoint remains authoritative. */
    private suspend fun requireReseedSourceHead(
        root: PallaSyncRootSession,
        source: HttpUrl,
        origin: String,
        state: ChainStateEntity,
    ) {
        val url = PallaSyncUrls.logHead(source, state.chainId)
        val token =
            checkNotNull(root.token(origin, "GET", url.encodedPath, query = url.encodedQuery.orEmpty())) {
                "C_RELAY_POLICY_CONSENT_REQUIRED"
            }
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $token")
                .get()
                .build()
        val head =
            when (
                val result =
                    remote.execute(request) { response ->
                        PallaSyncHttpResult.Success(
                            Json.parseToJsonElement(PallaSyncCore.validateLogHead(response.pallaSyncBytes())).jsonObject,
                        )
                    }
            ) {
                is PallaSyncHttpResult.Success -> result.value
                is PallaSyncHttpResult.ProtocolError -> error(result.message)
                is PallaSyncHttpResult.Retryable -> error(result.message)
                PallaSyncHttpResult.Gone -> error("C_DELETION_UNVERIFIED")
            }
        check(
            head.getValue("log_id").jsonPrimitive.content == state.logId &&
                head.getValue("generation").jsonPrimitive.long == state.generation &&
                head.getValue("head_seq").jsonPrimitive.long == state.materializedSeq &&
                head.getValue("head_hash").jsonPrimitive.content == state.verifiedHash &&
                head.getValue("parameters_hash").jsonPrimitive.content == state.parametersHash &&
                head.getValue("epoch_hash").jsonPrimitive.content == state.epochHash &&
                head.getValue("min_available_seq").jsonPrimitive.long == 1L,
        ) { "C_MISSING_DEPENDENCY" }
    }

    /** §17.6: retain a fully verified source-only suffix so post-commit races are reported, not replayed. */
    private suspend fun captureReseedSourceConflict(
        history: PallaSyncInitialHistory,
        sourceOrigin: String,
        cut: ChainStateEntity,
        sourceAuthority: JsonObject,
        knownAnchor: JsonObject? = keystore.transparencyPin(cut.chainId),
    ): JsonObject {
        val checkpoint = history.load(sourceAuthority, knownAnchor)
        val cutSequence = cut.materializedSeq
        check(checkpoint.logId == cut.logId && checkpoint.generation == cut.generation && checkpoint.headSeq >= cutSequence) {
            "C_ROLLBACK_DETECTED"
        }
        var after = cutSequence
        var count = 0L
        while (after < checkpoint.headSeq) {
            currentCoroutineContext().ensureActive()
            val page = db.protocolDao().logPage(cut.chainId, checkNotNull(cut.logId), after, checkpoint.headSeq, PALLASYNC_PAGE_SIZE)
            check(page.isNotEmpty()) { "C_MISSING_DEPENDENCY" }
            page.forEach { entry ->
                check(entry.validationState == "authority_verified" && entry.relaySeq == after + 1) { "C_TRANSPARENCY_INVALID" }
                after = entry.relaySeq
                count++
            }
        }
        check(after == checkpoint.headSeq) { "C_TRANSPARENCY_INVALID" }
        return PallaSyncReseedSourceConflict.report(cut, checkpoint, sourceOrigin, count)
    }

    private suspend fun performRetainedHistoryRepair(
        root: PallaSyncRootSession,
        base: HttpUrl,
        origin: String,
        state: ChainStateEntity,
        targetPin: String,
    ): PallaSyncRecordRepairSummary {
        val dao = db.protocolDao()
        var totals = RepairBatchResult(0, 0, emptyList(), false)
        val sources = mutableListOf<Pair<String, Long>>()
        for (sourceLogId in dao.repairSourceLogIds(state.chainId)) {
            dao.repairSourceHighWater(state.chainId, sourceLogId)?.let { through -> sources.add(sourceLogId to through) }
        }
        for ((sourceLogId, through) in sources) {
            totals += repairSourceLog(root, base, origin, state, targetPin, sourceLogId, through)
        }
        check(totals.submitted > 0) { "C_MISSING_DEPENDENCY" }
        return PallaSyncRecordRepairSummary(totals.submitted, totals.accepted, totals.rejectedCodes, totals.repairRequired)
    }

    private suspend fun repairSourceLog(
        root: PallaSyncRootSession,
        base: HttpUrl,
        origin: String,
        state: ChainStateEntity,
        targetPin: String,
        sourceLogId: String,
        through: Long,
    ): RepairBatchResult {
        val dao = db.protocolDao()
        val repair = PallaSyncRecordRepairRelay(remote)
        var totals = RepairBatchResult(0, 0, emptyList(), false)
        var after = 0L
        var pending = mutableListOf<PallaSyncRecordRepairRelay.SourceRecord>()
        while (after < through) {
            currentCoroutineContext().ensureActive()
            val page = dao.logPage(state.chainId, sourceLogId, after, through, PALLASYNC_PAGE_SIZE)
            check(page.isNotEmpty()) { "C_MISSING_DEPENDENCY" }
            for (row in page) {
                val candidate = repairCandidate(row, state) ?: continue
                if (pending.isNotEmpty() && !repair.fits(sourceLogId, pending + candidate)) {
                    totals += flushRepairBatch(root, base, origin, state, targetPin, sourceLogId, pending)
                    pending = mutableListOf()
                }
                check(repair.fits(sourceLogId, listOf(candidate))) { "C_LOCAL_LIMIT" }
                pending.add(candidate)
                if (pending.size == PALLASYNC_HARD_LIMITS.maxRecordsPerUpload) {
                    totals += flushRepairBatch(root, base, origin, state, targetPin, sourceLogId, pending)
                    pending = mutableListOf()
                }
            }
            after = page.last().relaySeq
        }
        if (pending.isNotEmpty()) {
            totals += flushRepairBatch(root, base, origin, state, targetPin, sourceLogId, pending)
        }
        return totals
    }

    private fun repairCandidate(
        row: LogEntryEntity,
        state: ChainStateEntity,
    ): PallaSyncRecordRepairRelay.SourceRecord? {
        check(row.validationState == "authority_verified") { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val entry = Json.parseToJsonElement(row.signedJson).jsonObject
        return if (entry.getValue("kind").jsonPrimitive.content == "record") {
            val record = entry.getValue("object").jsonObject
            if (record.getValue("generation").jsonPrimitive.long == state.generation) {
                PallaSyncRecordRepairRelay.SourceRecord(entry, record)
            } else {
                null
            }
        } else {
            null
        }
    }

    private suspend fun flushRepairBatch(
        root: PallaSyncRootSession,
        base: HttpUrl,
        origin: String,
        state: ChainStateEntity,
        targetPin: String,
        sourceLogId: String,
        batch: List<PallaSyncRecordRepairRelay.SourceRecord>,
    ): RepairBatchResult {
        currentCoroutineContext().ensureActive()
        return submitRepairBatch(root, base, sourceLogId, targetPin, origin, state, batch)
    }

    private suspend fun submitRepairBatch(
        root: PallaSyncRootSession,
        base: HttpUrl,
        sourceLogId: String,
        targetPin: String,
        origin: String,
        state: ChainStateEntity,
        batch: List<PallaSyncRecordRepairRelay.SourceRecord>,
    ): RepairBatchResult {
        val repair = PallaSyncRecordRepairRelay(remote)
        val response = repair.submit(root, base, sourceLogId, targetPin, batch)
        val outcome =
            when (response) {
                is PallaSyncHttpResult.Success -> response.value
                is PallaSyncHttpResult.ProtocolError -> error(response.message)
                is PallaSyncHttpResult.Retryable -> error(response.message)
                PallaSyncHttpResult.Gone -> error("C_DELETION_UNVERIFIED")
            }
        retainUploadEvidence(outcome, state.chainId, origin)
        val probe = consistencyProbe.run(base, state.chainId)
        check(probe is PallaSyncHttpResult.Success) {
            when (probe) {
                is PallaSyncHttpResult.ProtocolError -> probe.message
                is PallaSyncHttpResult.Retryable -> probe.message
                PallaSyncHttpResult.Gone -> "C_DELETION_UNVERIFIED"
                is PallaSyncHttpResult.Success -> "C_SCHEMA_VIOLATION"
            }
        }
        verifyUploadInclusions(outcome, state.chainId, origin)
        retainRepairedBatch(state, outcome, batch)
        return RepairBatchResult(batch.size, outcome.accepted.size, outcome.rejected.map { it.code }, outcome.repairRequired)
    }

    private suspend fun retainRepairedBatch(
        state: ChainStateEntity,
        outcome: PallaSyncPostRecordsResponse,
        batch: List<PallaSyncRecordRepairRelay.SourceRecord>,
    ) {
        val dao = db.protocolDao()
        val byId =
            batch.associateBy {
                it.record
                    .getValue("record_id")
                    .jsonPrimitive.content
            }
        val receiptsByHash = outcome.uploadReceipts.associateBy { it.getValue("object_hash").jsonPrimitive.content }
        db.transaction {
            outcome.accepted.forEach { accepted ->
                val source = checkNotNull(byId[accepted.recordId]) { "C_ACCEPTANCE_UNPROVEN" }
                val objectHash = checkNotNull(crypto.objectHash(source.record.toString())) { "C_SCHEMA_VIOLATION" }
                val receipt = checkNotNull(receiptsByHash[objectHash]) { "C_ACCEPTANCE_UNPROVEN" }
                val logEntry = Json.encodeToString(accepted.receipt)
                val seq = accepted.receipt.relaySeq
                dao.retainLogEntry(
                    LogEntryEntity(
                        state.chainId,
                        outcome.logId,
                        seq,
                        checkNotNull(crypto.objectHash(logEntry)),
                        logEntry,
                        receipt.toString(),
                        "authority_verified",
                    ),
                )
                dao.retainRepairedEvent(
                    SignedEventEntity(
                        state.chainId,
                        accepted.recordId,
                        outcome.logId,
                        seq,
                        objectHash,
                        source.record.toString(),
                        logEntry,
                        "materialized",
                    ),
                )
            }
        }
    }

    suspend fun getServerUrl(): String =
        runCatching {
            withContext(Dispatchers.IO) {
                SettingsStore.dataStoreFor(appContext).data.first()[PALLA_SYNC_SERVER_URL]
            }
        }.getOrNull() ?: DEFAULT_SERVER_URL

    /** Returns a canonical, slash-free server base URL or null when the value is invalid. */
    fun normalizeServerUrl(rawUrl: String): String? =
        when (val normalized = PallaSyncUrls.normalize(rawUrl)) {
            is PallaSyncHttpResult.Success -> normalized.value.toString().trimEnd('/')
            else -> null
        }

    internal fun relayShutdownNotice(rawOrigin: String): PallaSyncRetryGate.RelayShutdownNotice? {
        val normalized = normalizeServerUrl(rawOrigin) ?: return null
        return policy.retry.relayShutdownNotice(normalized)
    }

    /** §10.14: fetch and strictly validate the authenticated notice for the active chain. */
    internal suspend fun fetchRelayShutdownNotice(): PallaSyncRetryGate.RelayShutdownNotice =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                requireProtectedAuthority(state.chainId)
                val authority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
                val pin = authority.getValue("pin").jsonObject
                val origin = pin.getValue("origin").jsonPrimitive.content
                val base =
                    when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                    }
                check(PallaSyncUrls.extractOrigin(base) == origin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                policy.requireChainOrigin(state.chainId, origin)
                policy.requireCurrent(origin)
                val device = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val relay = PallaSyncPeerRelay(db, remote, crypto)
                val bytes =
                    relay.requestBytes(
                        PallaSyncUrls.relayNotice(base, state.chainId),
                        device,
                        "GET",
                        PALLASYNC_MEDIA_TYPE,
                    ) { response ->
                        PallaSyncCore.validateRelayNotice(response).toByteArray(Charsets.UTF_8)
                    }
                val notice = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                policy.retry.observeRelayNotice(
                    origin,
                    notice["shutdown_at_ms"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.long,
                    notice["migration_origin"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content,
                    notice.getValue("notice_id").jsonPrimitive.content,
                )
                checkNotNull(policy.retry.relayShutdownNotice(origin))
            }
        }

    /** Read-only v2.0 source endpoint; callers must still authenticate records and prove a frozen transcript. */
    internal suspend fun fetchLegacyV20Records(
        sourceUrl: String,
        chainId: String,
    ): PallaSyncHttpResult<List<PallaSyncLegacyV20Source.Record>> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeServerUrl(sourceUrl) ?: return@withContext PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            PallaSyncLegacyV20Source.fetchRecords(remote, normalized.toHttpUrl(), chainId)
        }

    internal suspend fun fetchLegacyV21Parameters(
        sourceUrl: String,
        chainId: String,
        seedPhrase: String,
        passphrase: String,
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.Parameters> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeServerUrl(sourceUrl) ?: return@withContext PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            legacyV21Source.getParameters(normalized.toHttpUrl(), chainId, seedPhrase, passphrase)
        }

    internal suspend fun fetchLegacyV21Devices(
        sourceUrl: String,
        parameters: PallaSyncLegacyV21Source.Parameters,
        deviceId: String,
        deviceSeed: String,
        devicePublicKey: String,
    ): PallaSyncHttpResult<List<PallaSyncLegacyV21Source.Device>> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeServerUrl(sourceUrl) ?: return@withContext PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            val base = normalized.toHttpUrl()
            legacyV21Source.getDevices(
                base,
                parameters,
                deviceId,
                deviceSeed,
                devicePublicKey,
                policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
            )
        }

    internal suspend fun fetchLegacyV21RecordPage(
        sourceUrl: String,
        chainId: String,
        afterSequence: Long,
        devices: List<PallaSyncLegacyV21Source.Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.RecordPage> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeServerUrl(sourceUrl) ?: return@withContext PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            val base = normalized.toHttpUrl()
            legacyV21Source.getRecordPage(
                base,
                chainId,
                afterSequence,
                devices,
                signerDeviceId,
                signerDeviceSeed,
                policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
            )
        }

    /**
     * Performs §9.1's explicit frozen-source completeness pass before a journal can be started.
     * The only available v2.1 API is ordinary reads, so freezeConfirmed represents the user's
     * confirmation that every old writer was stopped or the source is an immutable export.
     */
    internal suspend fun inspectFrozenLegacyV21Export(
        sourceUrl: String,
        parameters: PallaSyncLegacyV21Source.Parameters,
        devices: List<PallaSyncLegacyV21Source.Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
        signerDevicePublicKey: String,
        freezeConfirmed: Boolean,
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.FrozenExportProof> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeServerUrl(sourceUrl) ?: return@withContext PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            val base = normalized.toHttpUrl()
            PallaSyncLegacyV21Source.inspectFrozenExport(
                chainId = parameters.chainId,
                initialDevices = devices,
                freezeConfirmed = freezeConfirmed,
                fetchDevices = {
                    legacyV21Source.getDevices(
                        base,
                        parameters,
                        signerDeviceId,
                        signerDeviceSeed,
                        signerDevicePublicKey,
                        policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
                    )
                },
                fetchPage = { cursor ->
                    legacyV21Source.getRecordPage(
                        base,
                        parameters.chainId,
                        cursor,
                        devices,
                        signerDeviceId,
                        signerDeviceSeed,
                        policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
                    )
                },
            )
        }

    /** Persists the migration source marker only after the complete frozen-source scan succeeds. */
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
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.FrozenExportProof> {
        val proof =
            inspectFrozenLegacyV21Export(
                sourceUrl,
                parameters,
                devices,
                signerDeviceId,
                signerDeviceSeed,
                signerDevicePublicKey,
                freezeConfirmed,
            )
        if (proof !is PallaSyncHttpResult.Success) return proof
        return try {
            localIntents.beginLegacyMigration(
                migrationId = migrationId,
                sourceChain = proof.value.sourceChain,
                sourceProtocol = "2.1",
                sourceHighWater = proof.value.highWater,
                exportHash = proof.value.exportHash,
                targetChain = targetChain,
                freezeConfirmed = freezeConfirmed,
            )
            val staged =
                stageLegacyV21Candidates(
                    migrationId,
                    sourceUrl,
                    parameters,
                    devices,
                    signerDeviceId,
                    signerDeviceSeed,
                    signerDevicePublicKey,
                    proof.value,
                    seedPhrase,
                    passphrase,
                )
            when (staged) {
                is PallaSyncHttpResult.Success -> Unit
                is PallaSyncHttpResult.ProtocolError -> return staged
                is PallaSyncHttpResult.Retryable -> return staged
                PallaSyncHttpResult.Gone -> return PallaSyncHttpResult.Gone
            }
            proof
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IllegalStateException) {
            PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_MISSING_DEPENDENCY")
        } catch (_: IllegalArgumentException) {
            PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
        }
    }

    /** Converts an explicitly frozen v2.0 export into the same resumable journal used by v2.1 (§9.1–§9.3). */
    internal suspend fun beginLegacyV20Migration(
        migrationId: String,
        sourceUrl: String,
        seedPhrase: String,
        passphrase: String,
        sourceChain: String,
        targetChain: String,
        freezeConfirmed: Boolean,
    ): PallaSyncHttpResult<PallaSyncLegacyV20Source.FrozenExportProof> {
        return withContext(Dispatchers.IO) {
            if (!freezeConfirmed) return@withContext PallaSyncHttpResult.ProtocolError("C_MISSING_DEPENDENCY")
            val normalized = normalizeServerUrl(sourceUrl) ?: return@withContext PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            val existingProgress = localIntents.legacyMigrationProgress(migrationId)
            check(existingProgress == null || existingProgress.targetChain == targetChain) { "C_RECORD_ID_CONFLICT" }
            val target = targetChain
            var session = 0L
            try {
                session =
                    withLegacySecretInputs(seedPhrase, passphrase) { mnemonicBytes, passphraseBytes ->
                        PallaSyncCore.beginLegacyV20MigrationSession(mnemonicBytes, passphraseBytes, sourceChain)
                    }

                suspend fun read(): List<PallaSyncLegacyV20Source.Record> =
                    when (val result = fetchLegacyV20Records(normalized, sourceChain)) {
                        is PallaSyncHttpResult.Success -> result.value
                        is PallaSyncHttpResult.ProtocolError -> error(result.message)
                        is PallaSyncHttpResult.Retryable -> error(result.message)
                        PallaSyncHttpResult.Gone -> error("C_MISSING_DEPENDENCY")
                    }

                suspend fun authenticate(record: PallaSyncLegacyV20Source.Record): String? =
                    try {
                        val plaintext = PallaSyncCore.convertLegacyRecord(session, record.json, record.deviceId, "")
                        plaintext.fill(0)
                        null
                    } catch (error: IllegalStateException) {
                        error.message?.takeIf { it in LEGACY_QUARANTINE_CODES } ?: throw error
                    }
                val proofResult =
                    PallaSyncLegacyV20Source.inspectFrozenExport(
                        sourceChain,
                        writersStopped = true,
                        authenticateRecord = ::authenticate,
                        fetchRecords = ::read,
                    )
                if (proofResult !is PallaSyncHttpResult.Success) return@withContext proofResult
                val proof = proofResult.value
                localIntents.beginLegacyMigration(
                    migrationId,
                    proof.sourceChain,
                    "2.0",
                    proof.highWater,
                    proof.exportHash,
                    target,
                    freezeConfirmed = true,
                )
                val stagedProgress = localIntents.legacyMigrationProgress(migrationId) ?: error("C_MISSING_DEPENDENCY")
                check(
                    stagedProgress.sourceProtocol == "2.0" && stagedProgress.sourceChain == sourceChain &&
                        stagedProgress.targetChain == target && stagedProgress.sourceHighWater == proof.highWater &&
                        stagedProgress.exportHash == proof.exportHash,
                ) { "C_RECORD_ID_CONFLICT" }
                val records = read()
                check(
                    PallaSyncLegacyV20Source.exportHash(sourceChain, records) == proof.exportHash &&
                        records.size.toLong() == proof.highWater,
                ) {
                    "C_RECORD_ID_CONFLICT"
                }
                val legacyFavoriteMerge = PallaSyncLegacyV20Source.FavoriteMerge()
                for (record in records) {
                    currentCoroutineContext().ensureActive()
                    val candidates =
                        try {
                            val bytes = PallaSyncCore.convertLegacyRecord(session, record.json, record.deviceId, "")
                            try {
                                Json.decodeFromString<List<PallaSyncLegacyV21Source.EntityCandidate>>(String(bytes, Charsets.UTF_8))
                            } finally {
                                bytes.fill(0)
                            }
                        } catch (error: IllegalStateException) {
                            if (error.message?.takeIf { it in LEGACY_QUARANTINE_CODES } == null) throw error
                            continue
                        } catch (_: IllegalArgumentException) {
                            continue
                        }
                    val incoming =
                        candidates
                            .filter { it.legacySnapshot && it.collection == "palleria.favorite_tag/3" && it.operation == "upsert" }
                            .map(PallaSyncLegacyV21Source.EntityCandidate::entityId)
                    legacyFavoriteMerge.accept(incoming)
                }
                var after = stagedProgress.stagedThrough
                for (record in records) {
                    currentCoroutineContext().ensureActive()
                    val objectHash = checkNotNull(PallaSyncCore.objectHash(record.json)) { "C_SCHEMA_VIOLATION" }
                    localIntents.archiveLegacySourceRecord(migrationId, record.sequence, record.recordId, objectHash, record.json)
                    if (record.sequence <= after) continue
                    check(record.sequence == after + 1) { "C_ROLLBACK_DETECTED" }
                    val candidates =
                        try {
                            val bytes = PallaSyncCore.convertLegacyRecord(session, record.json, record.deviceId, "")
                            try {
                                Json.decodeFromString<List<PallaSyncLegacyV21Source.EntityCandidate>>(String(bytes, Charsets.UTF_8))
                            } finally {
                                bytes.fill(0)
                            }
                        } catch (error: IllegalStateException) {
                            val code = error.message?.takeIf { it in LEGACY_QUARANTINE_CODES } ?: throw error
                            stageLegacyQuarantine(migrationId, after, record.asSignedRecord(), objectHash, code)
                            after = record.sequence
                            continue
                        } catch (_: IllegalArgumentException) {
                            stageLegacyQuarantine(migrationId, after, record.asSignedRecord(), objectHash, "C_SCHEMA_VIOLATION")
                            after = record.sequence
                            continue
                        }
                    val mappings =
                        candidates.mapIndexed { index, candidate ->
                            PallaSyncLocalIntents.LegacyEventMapping(
                                sourceSequence = record.sequence,
                                sourceRecordId = record.recordId,
                                candidateIndex = index,
                                sourceObjectHash = objectHash,
                                event =
                                    PallaSyncPendingEvent(
                                        candidate.collection,
                                        candidate.entityId,
                                        candidate.operation,
                                        candidate.body,
                                    ),
                                normalizationCollision = candidate.normalizationCollision,
                                legacyEventSequenceOrdered =
                                    candidate.legacySnapshot &&
                                        candidate.collection in
                                        setOf("palleria.search_history/3", "palleria.view_history/3"),
                                legacyFavoriteSnapshotUnique =
                                    candidate.legacySnapshot && candidate.collection == "palleria.favorite_tag/3",
                                legacyEntityId = candidate.legacyEntityId,
                            )
                        }
                    val decision =
                        PallaSyncLocalIntents.LegacySourceDecision(
                            sourceSequence = record.sequence,
                            sourceRecordId = record.recordId,
                            sourceObjectHash = objectHash,
                            disposition = "converted",
                            normalizationChanged = candidates.any { it.normalizationChanged },
                            normalizationCollision = candidates.any { it.normalizationCollision },
                        )
                    localIntents.stageLegacyBatch(
                        migrationId,
                        expectedAfterSequence = after,
                        throughSequence = record.sequence,
                        mappings = mappings,
                        decisions = listOf(decision),
                        report =
                            PallaSyncLocalIntents.LegacyBatchReport(
                                sourceRecordsVisited = 1,
                                quarantinedCount = 0,
                                normalizationCount = if (decision.normalizationChanged) 1 else 0,
                                collisionCount = if (decision.normalizationCollision) 1 else 0,
                                complete = false,
                            ),
                    )
                    after = record.sequence
                }
                if (proof.highWater == 0L && stagedProgress.decisions == 0L) {
                    localIntents.stageLegacyBatch(
                        migrationId,
                        0,
                        0,
                        emptyList(),
                        emptyList(),
                        PallaSyncLocalIntents.LegacyBatchReport(0, 0, 0, 0, complete = false),
                    )
                }
                check(after == proof.highWater) { "C_ROLLBACK_DETECTED" }
                val finalProgress = localIntents.legacyMigrationProgress(migrationId) ?: error("C_MISSING_DEPENDENCY")
                check(finalProgress.quarantined == proof.quarantinedCount) { "C_RECORD_ID_CONFLICT" }
                localIntents.completeLegacySourceScan(
                    migrationId,
                    proof.highWater,
                    proof.highWater,
                    proof.exportHash,
                    legacyFavoriteMerge.mostRecentFirst(),
                )
                PallaSyncHttpResult.Success(proof)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalStateException) {
                PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_MISSING_DEPENDENCY")
            } catch (_: IllegalArgumentException) {
                PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            } finally {
                if (session != 0L) PallaSyncCore.endLegacyMigrationSession(session)
            }
        }
    }

    private fun PallaSyncLegacyV20Source.Record.asSignedRecord() = PallaSyncLegacyV21Source.SignedRecord(sequence, json, recordId, deviceId)

    /** Decrypts and journals one bounded source record at a time, then seals only a matching full transcript (§9.2–§9.3). */
    private suspend fun stageLegacyV21Candidates(
        migrationId: String,
        sourceUrl: String,
        parameters: PallaSyncLegacyV21Source.Parameters,
        devices: List<PallaSyncLegacyV21Source.Device>,
        signerDeviceId: String,
        signerDeviceSeed: String,
        signerDevicePublicKey: String,
        frozenProof: PallaSyncLegacyV21Source.FrozenExportProof,
        seedPhrase: String,
        passphrase: String,
    ): PallaSyncHttpResult<Unit> =
        withContext(Dispatchers.IO) {
            val normalized = normalizeServerUrl(sourceUrl) ?: return@withContext PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            val base = normalized.toHttpUrl()
            val registryHash = PallaSyncLegacyV21Source.registryHash(devices)
            if (registryHash != frozenProof.verifiedDeviceRegistryHash) {
                return@withContext PallaSyncHttpResult.ProtocolError("C_DEVICE_EQUIVOCATION")
            }
            val progress =
                localIntents.legacyMigrationProgress(migrationId)
                    ?: return@withContext PallaSyncHttpResult.ProtocolError("C_MISSING_DEPENDENCY")
            if (progress.sourceChain != frozenProof.sourceChain || progress.exportHash != frozenProof.exportHash ||
                progress.sourceHighWater != frozenProof.highWater
            ) {
                return@withContext PallaSyncHttpResult.ProtocolError("C_RECORD_ID_CONFLICT")
            }
            val transcript = PallaSyncLegacyV21Source.ExportTranscript(parameters.chainId, registryHash)
            var cursor = 0L
            var stagedThrough = progress.stagedThrough
            var session = 0L
            try {
                var hasMore = true
                while (hasMore) {
                    currentCoroutineContext().ensureActive()
                    when (
                        val result =
                            legacyV21Source.getRecordPage(
                                base,
                                parameters.chainId,
                                cursor,
                                devices,
                                signerDeviceId,
                                signerDeviceSeed,
                                policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
                            )
                    ) {
                        is PallaSyncHttpResult.Success -> {
                            val page = result.value
                            PallaSyncLegacyV21Source.validateFrozenPage(cursor, page)
                            for (record in page.records) {
                                currentCoroutineContext().ensureActive()
                                transcript.append(record)
                                cursor = record.sequence
                                val objectHash = checkNotNull(PallaSyncCore.objectHash(record.json)) { "C_SCHEMA_VIOLATION" }
                                localIntents.archiveLegacySourceRecord(
                                    migrationId,
                                    record.sequence,
                                    record.recordId,
                                    objectHash,
                                    record.json,
                                )
                                if (record.sequence <= stagedThrough) continue
                                check(record.sequence == stagedThrough + 1) { "C_ROLLBACK_DETECTED" }
                                if (session == 0L) {
                                    session =
                                        withLegacySecretInputs(seedPhrase, passphrase) { mnemonicBytes, passphraseBytes ->
                                            PallaSyncCore.beginLegacyMigrationSession(
                                                mnemonicBytes,
                                                passphraseBytes,
                                                parameters.chainSalt,
                                                parameters.chainId,
                                                parameters.adminPublicKey,
                                            )
                                        }
                                }
                                val device = devices.singleOrNull { it.deviceId == record.deviceId } ?: error("C_MISSING_DEPENDENCY")
                                val candidates =
                                    try {
                                        legacyV21Source.convertRecord(session, record, device)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: IllegalStateException) {
                                        val code =
                                            error.message?.takeIf { it in LEGACY_QUARANTINE_CODES }
                                                ?: throw error
                                        stageLegacyQuarantine(migrationId, stagedThrough, record, objectHash, code)
                                        stagedThrough = record.sequence
                                        continue
                                    } catch (_: IllegalArgumentException) {
                                        stageLegacyQuarantine(migrationId, stagedThrough, record, objectHash, "C_SCHEMA_VIOLATION")
                                        stagedThrough = record.sequence
                                        continue
                                    }
                                val mappings =
                                    candidates.mapIndexed { index, candidate ->
                                        PallaSyncLocalIntents.LegacyEventMapping(
                                            sourceSequence = record.sequence,
                                            sourceRecordId = record.recordId,
                                            candidateIndex = index,
                                            sourceObjectHash = objectHash,
                                            event =
                                                PallaSyncPendingEvent(
                                                    schema = candidate.collection,
                                                    entityId = candidate.entityId,
                                                    operation = candidate.operation,
                                                    body = candidate.body,
                                                ),
                                            normalizationCollision = candidate.normalizationCollision,
                                            legacyEventSequenceOrdered = record.collectionName?.endsWith("/2") == true,
                                            legacyFavoriteSnapshotUnique =
                                                record.collectionName == "palleria.favorite_tag/1" &&
                                                    candidate.legacySnapshot && candidate.collection == "palleria.favorite_tag/3",
                                            legacyEntityId = candidate.legacyEntityId,
                                        )
                                    }
                                val decision =
                                    PallaSyncLocalIntents.LegacySourceDecision(
                                        sourceSequence = record.sequence,
                                        sourceRecordId = record.recordId,
                                        sourceObjectHash = objectHash,
                                        disposition = "converted",
                                        normalizationChanged = candidates.any { it.normalizationChanged },
                                        normalizationCollision = candidates.any { it.normalizationCollision },
                                    )
                                localIntents.stageLegacyBatch(
                                    migrationId,
                                    expectedAfterSequence = stagedThrough,
                                    throughSequence = record.sequence,
                                    mappings = mappings,
                                    decisions = listOf(decision),
                                    report =
                                        PallaSyncLocalIntents.LegacyBatchReport(
                                            sourceRecordsVisited = 1,
                                            quarantinedCount = 0,
                                            normalizationCount = if (decision.normalizationChanged) 1 else 0,
                                            collisionCount = if (decision.normalizationCollision) 1 else 0,
                                            complete = false,
                                        ),
                                )
                                stagedThrough = record.sequence
                            }
                            check(page.throughSequence == cursor) { "C_ROLLBACK_DETECTED" }
                            hasMore = page.hasMore
                        }

                        is PallaSyncHttpResult.ProtocolError -> {
                            return@withContext result
                        }

                        is PallaSyncHttpResult.Retryable -> {
                            return@withContext result
                        }

                        PallaSyncHttpResult.Gone -> {
                            return@withContext PallaSyncHttpResult.Gone
                        }
                    }
                }
                if (frozenProof.highWater == 0L && stagedThrough == 0L && progress.decisions == 0L) {
                    localIntents.stageLegacyBatch(
                        migrationId,
                        expectedAfterSequence = 0,
                        throughSequence = 0,
                        mappings = emptyList(),
                        decisions = emptyList(),
                        report = PallaSyncLocalIntents.LegacyBatchReport(0, 0, 0, 0, complete = false),
                    )
                }
                check(
                    cursor == frozenProof.highWater && transcript.recordCount == frozenProof.recordCount &&
                        transcript.finish() == frozenProof.exportHash,
                ) { "C_RECORD_ID_CONFLICT" }
                when (
                    val after =
                        legacyV21Source.getDevices(
                            base,
                            parameters,
                            signerDeviceId,
                            signerDeviceSeed,
                            signerDevicePublicKey,
                            policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
                        )
                ) {
                    is PallaSyncHttpResult.Success -> {
                        check(PallaSyncLegacyV21Source.registryHash(after.value) == registryHash) { "C_DEVICE_EQUIVOCATION" }
                    }

                    is PallaSyncHttpResult.ProtocolError -> {
                        return@withContext after
                    }

                    is PallaSyncHttpResult.Retryable -> {
                        return@withContext after
                    }

                    PallaSyncHttpResult.Gone -> {
                        return@withContext PallaSyncHttpResult.Gone
                    }
                }
                localIntents.completeLegacySourceScan(
                    migrationId,
                    expectedHighWater = frozenProof.highWater,
                    expectedRecordCount = frozenProof.recordCount,
                    expectedExportHash = frozenProof.exportHash,
                )
                PallaSyncHttpResult.Success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: IllegalStateException) {
                PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_MISSING_DEPENDENCY")
            } catch (_: IllegalArgumentException) {
                PallaSyncHttpResult.ProtocolError("C_SCHEMA_VIOLATION")
            } finally {
                if (session != 0L) PallaSyncCore.endLegacyMigrationSession(session)
            }
        }

    private suspend fun stageLegacyQuarantine(
        migrationId: String,
        after: Long,
        record: PallaSyncLegacyV21Source.SignedRecord,
        objectHash: String,
        code: String,
    ) {
        localIntents.stageLegacyBatch(
            migrationId,
            expectedAfterSequence = after,
            throughSequence = record.sequence,
            mappings = emptyList(),
            decisions =
                listOf(
                    PallaSyncLocalIntents.LegacySourceDecision(
                        sourceSequence = record.sequence,
                        sourceRecordId = record.recordId,
                        sourceObjectHash = objectHash,
                        disposition = "quarantined",
                        reasonCode = code,
                    ),
                ),
            report = PallaSyncLocalIntents.LegacyBatchReport(1, 1, 0, 0, complete = false),
        )
    }

    /** Re-scan and compare the exact source proof before any resumed migration work (§9.3). */
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
    ): PallaSyncHttpResult<PallaSyncLegacyV21Source.FrozenExportProof> {
        if (localIntents.legacyMigrationProgress(migrationId) == null) {
            return PallaSyncHttpResult.ProtocolError("C_MISSING_DEPENDENCY")
        }
        return beginLegacyV21Migration(
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

    internal suspend fun legacyMigrationProgress(migrationId: String): PallaSyncLocalIntents.LegacyMigrationProgress? =
        withContext(Dispatchers.IO) { localIntents.legacyMigrationProgress(migrationId) }

    internal suspend fun verifyLegacyMigrationSnapshot(migrationId: String): PallaSyncLocalIntents.LegacyMigrationProgress {
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_MISSING_DEPENDENCY" }
                val keys = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                localIntents.completeLegacyMigrationWithVerifiedSnapshot(migrationId, keys)
            }
        }
        syncNow()
        return checkNotNull(localIntents.legacyMigrationProgress(migrationId)) { "C_MISSING_DEPENDENCY" }
    }

    internal suspend fun legacyMigrationProgressPage(
        afterMigrationId: String = "",
        limit: Int = 20,
    ): PallaSyncLocalIntents.LegacyProgressPage =
        withContext(Dispatchers.IO) { localIntents.legacyMigrationProgressPage(afterMigrationId, limit) }

    internal suspend fun legacyMigrationDecisionPage(
        migrationId: String,
        afterIndex: Long,
        limit: Int = 200,
    ): PallaSyncLocalIntents.LegacyDecisionPage =
        withContext(Dispatchers.IO) { localIntents.legacyMigrationDecisionPage(migrationId, afterIndex, limit) }

    internal suspend fun legacyMigrationSourceEvidencePage(
        migrationId: String,
        afterSequence: Long,
        limit: Int = 200,
    ): PallaSyncLocalIntents.LegacySourceEvidencePage =
        withContext(Dispatchers.IO) { localIntents.legacyMigrationSourceEvidencePage(migrationId, afterSequence, limit) }

    internal suspend fun legacyMigrationCandidatePage(
        migrationId: String,
        afterIndex: Long,
        limit: Int = 200,
    ): PallaSyncLocalIntents.LegacyCandidatePage =
        withContext(Dispatchers.IO) { localIntents.legacyMigrationCandidatePage(migrationId, afterIndex, limit) }

    internal suspend fun chooseLegacyMigrationCandidate(
        migrationId: String,
        groupKey: String,
        entryIndex: Long,
    ) = withContext(Dispatchers.IO) { localIntents.chooseLegacyMigrationCandidate(migrationId, groupKey, entryIndex) }

    internal suspend fun finalizeLegacyMigrationChoices(migrationId: String) =
        withContext(Dispatchers.IO) { localIntents.finalizeLegacyMigrationChoices(migrationId) }

    /** Revalidates the source proof before authoring any remaining migration candidates (§9.3). */
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
    ): PallaSyncHttpResult<Unit> {
        val verified =
            revalidateLegacyV21MigrationSource(
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
        if (verified !is PallaSyncHttpResult.Success) {
            return when (verified) {
                is PallaSyncHttpResult.ProtocolError -> verified
                is PallaSyncHttpResult.Retryable -> verified
                PallaSyncHttpResult.Gone -> PallaSyncHttpResult.Gone
                is PallaSyncHttpResult.Success -> error("unreachable")
            }
        }
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val state = checkNotNull(activeChainStateLocked()) { "C_MISSING_DEPENDENCY" }
                    check(state.chainId == targetChain) { "C_MISSING_DEPENDENCY" }
                    val keys = checkNotNull(keysForChainLocked(targetChain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                    localIntents.resumeLegacyMigration(migrationId, keys)
                    PallaSyncHttpResult.Success(Unit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: IllegalStateException) {
                    PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_MISSING_DEPENDENCY")
                }
            }
        }
    }

    fun getPallaSyncKeystore(): PallaSyncKeystore = keystore

    /** Repairs a process death between staged-key and Room activation commits. */
    suspend fun recoverInterruptedActivation(): Boolean {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val pending = keystore.pendingActivation() ?: return@withLock false
                val state =
                    activeChainStateLocked() ?: run {
                        return@withLock false
                    }
                if (state.chainId != pending || state.writeState == "activation_preparing") return@withLock false
                if (keysForChainLocked(state.chainId) == null) return@withLock false
                SettingsStore(appContext).setPallaSyncEnabledFromCoordinator(true)
                keystore.completeActivation(pending)
                true
            }
        }
    }

    suspend fun generateGenesisMnemonic(): String = withContext(Dispatchers.Default) { crypto.generateSeedPhrase() }

    suspend fun hasLocalChain(): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock { db.dao.getActiveChainStates().isNotEmpty() }
        }

    suspend fun getPendingLocalEditCount(): Long =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val chain = activeChainStateLocked()?.chainId
                if (chain == null) 0 else db.protocolDao().pendingMigrationCount(PallaSyncLocalIntents.journalId(chain))
            }
        }

    /** §§11.1/11.7: explicit resume preserves the existing identity, archive and queued work. */
    suspend fun resumeBoundChain(startWorker: Boolean = true): Boolean {
        val enabled =
            withContext(Dispatchers.IO) {
                operationMutex.withLock {
                    val state = activeChainStateLocked() ?: return@withLock false
                    try {
                        val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId))
                        val authority = Json.parseToJsonElement(authorityText).jsonObject
                        val logId =
                            state.logId
                                ?: authority
                                    .getValue("pin")
                                    .jsonObject
                                    .getValue("log_id")
                                    .jsonPrimitive.content
                        check(db.protocolDao().latestEvidence(state.chainId, logId, "delete_receipt") == null) { "C_DELETION_UNVERIFIED" }
                        requireProtectedAuthority(state.chainId)
                        val pinnedOrigin =
                            authority
                                .getValue("pin")
                                .jsonObject
                                .getValue("origin")
                                .jsonPrimitive.content
                        val selected = normalizeServerUrl(getServerUrl()) ?: return@withLock false
                        check(selected == pinnedOrigin) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                        policy.requireCurrent(pinnedOrigin)
                        check(keysForChainLocked(state.chainId) != null) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                        SettingsStore(appContext).setPallaSyncEnabledFromCoordinator(true)
                        resumeLocalIntentsLocked()
                        true
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: IllegalStateException) {
                        val code = error.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_MISSING_DEPENDENCY"
                        log("$code: resumption paused with the existing chain retained")
                        false
                    } catch (_: IOException) {
                        log("C_RELAY_POLICY_UNAVAILABLE: resumption paused with the existing chain retained")
                        false
                    } catch (_: SQLiteException) {
                        log("C_LOCAL_STORAGE_FULL: resumption paused with the existing chain retained")
                        false
                    }
                }
            }
        if (enabled && startWorker) startBackgroundSync()
        return enabled
    }

    suspend fun getEpochStatus(): PallaSyncEpochStatus? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val chain = activeChainStateLocked()?.chainId ?: return@withLock null
                try {
                    epochStatus.read(chain)
                } catch (failure: IllegalStateException) {
                    val code = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_MISSING_DEPENDENCY"
                    log("Epoch status unavailable: $code")
                    null
                }
            }
        }

    /** §18.1: create a fresh user-requested QR offer from the currently pinned active device. */
    internal suspend fun createPeerDeviceOffer(): PallaSyncPeerJournal.Transcript =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                refreshPeerHistory(base, activeChainStateLocked()?.chainId ?: error("C_BOOTSTRAP_TRUST_REQUIRED"))
                peerApprover.offer(base, explicitlyRequested = true)
            }
        }

    /** Exports a bounded, fully verified public history prefix for the physically selected subject. */
    internal suspend fun peerAuthorityForOffer(transcript: PallaSyncPeerJournal.Transcript): String =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val chain =
                    transcript.offer
                        .getValue("chain_id")
                        .jsonPrimitive.content
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                refreshPeerHistory(base, chain)
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(state.chainId == chain && transcript.stage == PallaSyncPeerJournal.Stage.OFFERED) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val authority = checkNotNull(db.protocolDao().currentAuthority(chain)) { "C_MISSING_DEPENDENCY" }
                check(
                    crypto.objectHash(authority) == transcript.authorityHash && keystore.authorityHash(chain) == transcript.authorityHash,
                ) {
                    "C_ROLLBACK_DETECTED"
                }
                exportPeerHistory(chain, state, authority, transcript.authorityHash, base)
            }
        }

    private suspend fun exportPeerHistory(
        chain: String,
        state: ChainStateEntity,
        authority: String,
        expectedHash: String?,
        base: HttpUrl,
    ): String {
        val parsedAuthority = Json.parseToJsonElement(authority).jsonObject
        val pin = parsedAuthority.getValue("pin").jsonObject
        val origin = pin.getValue("origin").jsonPrimitive.content
        val active = checkNotNull(keystore.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val parameters =
            PallaSyncPeerRelay(db, remote, crypto).request(PallaSyncUrls.parameters(base, chain), active, "GET") { bytes ->
                bytes.toString(Charsets.UTF_8).also { check(it.isNotBlank()) { "C_SCHEMA_VIOLATION" } }
            }
        val identity =
            kotlinx.serialization.json
                .buildJsonObject {
                    put("chain_id", chain)
                    put("admin_public_key", parsedAuthority.getValue("admin_public_key"))
                }.toString()
        var verified =
            Json
                .parseToJsonElement(
                    PallaSyncCore.beginAuthorityHistory(
                        parameters.toByteArray(Charsets.UTF_8),
                        identity,
                        origin,
                        policy.trustedRelayKey(origin),
                    ),
                ).jsonObject
        val through = parsedAuthority.getValue("sequence").jsonPrimitive.long
        val logId = checkNotNull(state.logId) { "C_MISSING_DEPENDENCY" }
        check(logId == pin.getValue("log_id").jsonPrimitive.content) { "C_LOG_ID_MISMATCH" }
        check(
            state.verifiedSeq == through && state.verifiedHash == parsedAuthority.getValue("head_hash").jsonPrimitive.content,
        ) { "C_MISSING_DEPENDENCY" }
        val entries = ArrayList<String>()
        var after = 0L
        while (after < through) {
            val page = db.protocolDao().logPage(chain, logId, after, through, PALLASYNC_PAGE_SIZE)
            check(page.isNotEmpty() && page.first().relaySeq == after + 1L) { "C_MISSING_DEPENDENCY" }
            for (row in page) {
                check(row.validationState == "authority_verified" && row.relaySeq == after + 1L) { "C_MISSING_DEPENDENCY" }
                val entry = Json.parseToJsonElement(row.signedJson).jsonObject
                val receipt = Json.parseToJsonElement(row.receiptJson).jsonObject
                verified =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.advanceAuthorityHistory(verified.toString(), entry.toString(), receipt.toString()),
                        ).jsonObject
                val item = "{\"entry\":$entry,\"receipt\":$receipt}"
                entries += item
                after = row.relaySeq
            }
        }
        check(expectedHash == null || crypto.objectHash(verified.toString()) == expectedHash) { "C_ROLLBACK_DETECTED" }
        return PallaSyncPeerTransferFile.encodeHistory(parameters, authority, entries)
    }

    private suspend fun refreshPeerHistory(
        base: HttpUrl,
        chain: String,
    ) {
        val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(state.chainId == chain) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val currentKeys = checkNotNull(keysForChainLocked(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        when (val result = liveHistory.pull(base, chain, currentKeys)) {
            is PallaSyncHttpResult.Success -> Unit
            is PallaSyncHttpResult.ProtocolError -> error(result.message)
            is PallaSyncHttpResult.Retryable -> error(result.message)
            PallaSyncHttpResult.Gone -> error("C_DELETION_UNVERIFIED")
        }
    }

    internal suspend fun peerAcceptanceTransfer(transcript: PallaSyncPeerJournal.Transcript): String =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                check(transcript.stage == PallaSyncPeerJournal.Stage.ACCEPTED && peerJournal.read(transcript.requestId) == transcript) {
                    "C_ACCEPTANCE_UNPROVEN"
                }
                val chain =
                    transcript.offer
                        .getValue("chain_id")
                        .jsonPrimitive.content
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(state.chainId == chain) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val keys = checkNotNull(keystore.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                when (
                    val pulled =
                        liveHistory.pull(
                            checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                                "C_BOOTSTRAP_TRUST_REQUIRED"
                            },
                            chain,
                            keys,
                        )
                ) {
                    is PallaSyncHttpResult.Success -> Unit
                    is PallaSyncHttpResult.ProtocolError -> error(pulled.message)
                    is PallaSyncHttpResult.Retryable -> error(pulled.message)
                    PallaSyncHttpResult.Gone -> error("C_DELETION_UNVERIFIED")
                }
                val authority = checkNotNull(db.protocolDao().currentAuthority(chain)) { "C_MISSING_DEPENDENCY" }
                val authorityHash = checkNotNull(crypto.objectHash(authority)) { "C_SCHEMA_VIOLATION" }
                check(authorityHash == keystore.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
                val latest = checkNotNull(db.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                val history = exportPeerHistory(chain, latest, authority, null, base)
                buildJsonObject {
                    put("format", "pallasync-peer-acceptance/3")
                    put("acceptance", checkNotNull(transcript.acceptance))
                    put("history", Json.parseToJsonElement(history))
                }.toString()
            }
        }

    internal suspend fun pollPeerEnrollmentRequest(transcript: PallaSyncPeerJournal.Transcript): JsonObject? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                refreshPeerHistory(
                    base,
                    transcript.offer
                        .getValue("chain_id")
                        .jsonPrimitive.content,
                )
                peerApprover.pendingRequest(base, transcript)
            }
        }

    @Suppress("TooGenericExceptionCaught") // A native SAS handle must be released for every throwable, including cancellation.
    internal suspend fun beginPeerEnrollmentApproval(
        transcript: PallaSyncPeerJournal.Transcript,
        item: JsonObject,
    ): PallaSyncPeerApprovalWork =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                refreshPeerHistory(
                    base,
                    transcript.offer
                        .getValue("chain_id")
                        .jsonPrimitive.content,
                )
                val session = peerApprover.begin(base, transcript)
                try {
                    val (bound, sas) =
                        peerApprover.bind(
                            transcript,
                            session,
                            item,
                            policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
                        )
                    PallaSyncPeerApprovalWork(bound, session, sas)
                } catch (failure: Throwable) {
                    session.close()
                    throw failure
                }
            }
        }

    internal suspend fun confirmPeerEnrollmentSas(work: PallaSyncPeerApprovalWork): PallaSyncPeerJournal.Transcript =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                refreshPeerHistory(
                    base,
                    work.transcript.offer
                        .getValue("chain_id")
                        .jsonPrimitive.content,
                )
                val next = peerApprover.confirmedTransfer(base, work.transcript, work.session, work.sas, confirmed = true)
                work.transcript = next
                next
            }
        }

    internal suspend fun cancelPeerEnrollmentApproval(work: PallaSyncPeerApprovalWork) =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    peerApprover.cancel(work.transcript, work.session)
                } finally {
                    work.close()
                }
            }
        }

    @Suppress("TooGenericExceptionCaught") // Refresh the durable exact-submission stage even when transport completion is uncertain.
    internal suspend fun approvePeerEnrollment(
        work: PallaSyncPeerApprovalWork,
        submittedBody: String,
    ): PallaSyncPeerJournal.Transcript =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                try {
                    refreshPeerHistory(
                        base,
                        work.transcript.offer
                            .getValue("chain_id")
                            .jsonPrimitive.content,
                    )
                    val next = peerApprover.approve(base, work.transcript, work.session, submittedBody)
                    work.transcript = next
                    next
                } catch (failure: Throwable) {
                    peerJournal.read(work.transcript.requestId)?.let { work.transcript = it }
                    throw failure
                } finally {
                    work.close()
                }
            }
        }

    internal suspend fun retryPeerEnrollmentApproval(transcript: PallaSyncPeerJournal.Transcript): PallaSyncPeerJournal.Transcript =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                peerApprover.retryApproval(base, transcript)
            }
        }

    internal suspend fun beginPeerSubjectEnrollment(
        offerJson: String,
        authorityJson: String,
        configuredOrigin: String,
    ): PallaSyncPeerSubjectEnrollment.Work =
        withContext(Dispatchers.IO) {
            val base =
                checkNotNull((PallaSyncUrls.normalize(configuredOrigin) as? PallaSyncHttpResult.Success)?.value) {
                    "C_BOOTSTRAP_TRUST_REQUIRED"
                }
            peerSubject.begin(
                offerJson,
                authorityJson,
                PallaSyncUrls.extractOrigin(base),
                policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
            )
        }

    internal suspend fun confirmPeerSubjectSas(
        work: PallaSyncPeerSubjectEnrollment.Work,
        observed: String,
    ) = withContext(Dispatchers.IO) {
        work.confirm(observed, policy.retry.correctedTime(work.origin))
    }

    internal suspend fun preparePeerSubjectEnrollment(
        work: PallaSyncPeerSubjectEnrollment.Work,
        certificateJson: String,
        envelopeJson: String,
        deviceName: String,
    ): PallaSyncPeerSubjectEnrollment.Candidate =
        withContext(Dispatchers.IO) {
            peerSubject.prepare(work, certificateJson, envelopeJson, deviceName, policy.retry.correctedTime(work.origin))
        }

    internal suspend fun completePeerSubjectEnrollment(acceptanceJson: String): Pair<String, HttpUrl> =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val base =
                    checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                        "C_BOOTSTRAP_TRUST_REQUIRED"
                    }
                val origin = PallaSyncUrls.extractOrigin(base)
                val (candidate, checkpoint) = peerSubject.complete(acceptanceJson, base)
                val chain = checkNotNull(candidate.chainId)
                val prepared = PreparedJoin(candidate, checkpoint)
                stagePreparedActivation(prepared, false, origin)
                PallaSyncPrefixMaterializer(db, keystore).apply(chain, candidate, checkpoint)
                val collections = SettingsStore(appContext).readSyncedCollections()
                localAuthor.initialize(chain, candidate, orderInitialEvents(chain, buildInitialSettingsSyncEvents(collections)))
                currentCoroutineContext().ensureActive()
                keystore.stageActivation(chain)
                db.transaction {
                    val active = checkNotNull(db.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
                    db.dao.archiveOtherChainStates(chain)
                    db.dao.updateChainState(active.copy(writeState = "active"))
                }
                check(keystore.promotePendingChainKeys()) { "C_LOCAL_STORAGE_FULL" }
                stopBackgroundSync()
                chain to base
            }
        }

    internal suspend fun pendingPeerSubjectEnrollment(origin: String): PallaSyncPeerSubjectEnrollment.Candidate? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock { peerSubject.pendingCandidate(origin) }
        }

    suspend fun rotateEpoch(
        mnemonic: String,
        reason: String = "manual",
        revoked: List<String> = emptyList(),
        admin: Boolean = false,
        passphrase: String = "",
        cancellation: PallaSyncRootCancellation? = null,
    ): Boolean =
        withEpochRoot(
            mnemonic,
            passphrase,
            cancellation,
            if (admin &&
                revoked.isNotEmpty()
            ) {
                PallaSyncRootSession.Operation.REVOKE
            } else {
                PallaSyncRootSession.Operation.ROTATE
            },
        ) { root, base ->
            epochLifecycle.rotate(root, base, reason, revoked, admin) == PallaSyncEpochLifecycle.Outcome.UPDATED
        }

    suspend fun refreshEpochs(
        mnemonic: String,
        passphrase: String = "",
        cancellation: PallaSyncRootCancellation? = null,
    ): Boolean =
        withEpochRoot(mnemonic, passphrase, cancellation, PallaSyncRootSession.Operation.ROTATE) { root, base ->
            epochLifecycle.refresh(root, base)
            true
        }

    /** §18.5: acknowledge a fresh identity, rebase unaccepted intents, and revoke the old identity. */
    suspend fun replaceCurrentDevice(
        mnemonic: String,
        passphrase: String = "",
        deviceName: String = Build.MODEL,
        cancellation: PallaSyncRootCancellation? = null,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                    val replacementOldId = keystore.pendingReplacementOldDevice(state.chainId)
                    val resumableWriteState =
                        replacementOldId != null && state.writeState in setOf("active", "epoch_rebase_required")
                    check(
                        (state.writeState == "active" || resumableWriteState) &&
                            (replacementOldId == null || keystore.getActiveChainKeys()?.chainId == state.chainId),
                    ) {
                        "C_WRITE_PAUSED_BY_POLICY"
                    }
                    val originBase =
                        checkNotNull((PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success)?.value) {
                            "C_BOOTSTRAP_TRUST_REQUIRED"
                        }
                    val replacement =
                        if (replacementOldId == null) {
                            prepareCurrentDeviceReplacement(state, originBase, mnemonic, passphrase, deviceName, cancellation)
                        } else {
                            resumeCurrentDeviceReplacement(state, replacementOldId)
                        }
                    completeCurrentDeviceReplacement(state, originBase, replacement, mnemonic, passphrase, cancellation)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: PallaSyncInitialHistory.Failure) {
                    val code = (failure.result as? PallaSyncHttpResult.ProtocolError)?.message ?: "C_MISSING_DEPENDENCY"
                    log("Device replacement paused: $code")
                    false
                } catch (failure: IllegalStateException) {
                    log(
                        "Device replacement paused [${epochLifecycle.stage}]: " +
                            (failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_SCHEMA_VIOLATION"),
                    )
                    false
                }
            }
        }

    private data class DeviceReplacement(
        val oldId: String,
        val oldKeys: PallaSyncKeySnapshot,
        val oldAuthority: JsonObject,
        val newKeys: PallaSyncKeySnapshot,
    )

    private suspend fun prepareCurrentDeviceReplacement(
        state: ChainStateEntity,
        base: HttpUrl,
        mnemonic: String,
        passphrase: String,
        deviceName: String,
        cancellation: PallaSyncRootCancellation?,
    ): DeviceReplacement {
        val oldKeys = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val oldId = checkNotNull(oldKeys.deviceId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val oldText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_MISSING_DEPENDENCY" }
        check(PallaSyncCore.objectHash(oldText) == keystore.authorityHash(state.chainId)) { "C_ROLLBACK_DETECTED" }
        val oldAuthority = Json.parseToJsonElement(oldText).jsonObject
        check(keystore.boundDeviceId(state.chainId) == oldId && PallaSyncCore.validateCurrentKeyRecipient(oldText, oldId)) {
            "C_BOOTSTRAP_TRUST_REQUIRED"
        }
        localAuthor.finishPending(state.chainId, oldKeys)
        val oldHash = checkNotNull(PallaSyncCore.objectHash(oldText)) { "C_SCHEMA_VIOLATION" }
        val oldAnchor =
            buildJsonObject {
                put("transparency_id", state.transparencyId)
                put("index", state.transparencyIndex)
                put("hash", state.transparencyHash)
            }
        check(keystore.retainSourceTransparencyPin(state.chainId, oldHash, oldAnchor)) { "C_LOCAL_STORAGE_FULL" }
        val root =
            PallaSyncRootSession.beginText(
                mnemonic,
                passphrase,
                PallaSyncRootSession.Operation.ENROLLMENT,
                state.chainId,
                oldAuthority.getValue("admin_public_key").jsonPrimitive.content,
                policy::requireCurrent,
                policy.retry::correctedTime,
            )
        val enrollment =
            useRoot(root, cancellation) {
                PallaSyncMnemonicEnrollment(db, remote, policy, keystore, crypto).prepare(it, deviceName, base)
            }
        val newKeys = enrollment.first
        check(newKeys.deviceId != oldId && newKeys.chainId == state.chainId) { "C_DEVICE_EQUIVOCATION" }
        epochLifecycle.installAndActivateVerifiedCheckpoint(state.chainId, oldAuthority, enrollment.second, base, oldKeys)
        check(
            keystore.promotePendingReplacementKeys(state.chainId, oldId, checkNotNull(newKeys.deviceId), oldHash),
        ) { "C_LOCAL_STORAGE_FULL" }
        localAuthor.prepareFreshDeviceWriter(state.chainId, newKeys)
        return DeviceReplacement(oldId, oldKeys, oldAuthority, newKeys)
    }

    private suspend fun resumeCurrentDeviceReplacement(
        state: ChainStateEntity,
        oldId: String,
    ): DeviceReplacement {
        val newKeys = checkNotNull(keystore.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(newKeys.deviceId != oldId && keystore.boundDeviceId(state.chainId) == newKeys.deviceId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val oldKeys = checkNotNull(keystore.getArchivedChainKeys(state.chainId)) { "C_MISSING_DEPENDENCY" }
        check(oldKeys.deviceId == oldId) { "C_DEVICE_EQUIVOCATION" }
        val sourceHash = checkNotNull(keystore.pendingReplacementSourceAuthorityHash(state.chainId)) { "C_MISSING_DEPENDENCY" }
        val archived = checkNotNull(db.protocolDao().authorityObject(state.chainId, "checkpoint", sourceHash)) { "C_MISSING_DEPENDENCY" }
        val oldAuthority = Json.parseToJsonElement(archived.signedJson).jsonObject
        check(PallaSyncCore.validateCurrentKeyRecipient(oldAuthority.toString(), oldId)) { "C_ROLLBACK_DETECTED" }
        localAuthor.prepareFreshDeviceWriter(state.chainId, newKeys)
        return DeviceReplacement(oldId, oldKeys, oldAuthority, newKeys)
    }

    private suspend fun completeCurrentDeviceReplacement(
        state: ChainStateEntity,
        base: HttpUrl,
        replacement: DeviceReplacement,
        mnemonic: String,
        passphrase: String,
        cancellation: PallaSyncRootCancellation?,
    ): Boolean {
        val (oldId, oldKeys, oldAuthority, newKeys) = replacement
        check(keystore.boundDeviceId(state.chainId) == newKeys.deviceId && keystore.getActiveChainKeys() == newKeys) {
            "C_BOOTSTRAP_TRUST_REQUIRED"
        }
        val currentAuthority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
        val revoked =
            currentAuthority
                .getValue(
                    "devices",
                ).jsonObject
                .getValue(oldId)
                .jsonObject
                .getValue("revoked_seq")
                .jsonPrimitive.content
        if (revoked == "null") {
            val root =
                PallaSyncRootSession.beginText(
                    mnemonic,
                    passphrase,
                    PallaSyncRootSession.Operation.ROTATE,
                    state.chainId,
                    oldAuthority.getValue("admin_public_key").jsonPrimitive.content,
                    policy::requireCurrent,
                    policy.retry::correctedTime,
                )
            val completed =
                useRoot(root, cancellation) {
                    epochLifecycle.rotate(it, base, "key_rotation", listOf(oldId), admin = false) ==
                        PallaSyncEpochLifecycle.Outcome.UPDATED
                }
            if (!completed) {
                log("Device replacement rotation awaits signed head reconciliation: ${epochLifecycle.stage}")
                return false
            }
        }
        // The signed next epoch closes late acceptance of the old identity's uncertain submissions.
        val currentKeys = checkNotNull(keystore.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(currentKeys.deviceId == newKeys.deviceId && currentKeys.epoch > oldKeys.epoch) { "C_MISSING_DEPENDENCY" }
        outboxRebase.resumeReseed(state.chainId, currentKeys, oldKeys, oldAuthority)
        check(keystore.finishReplacement(state.chainId)) { "C_LOCAL_STORAGE_FULL" }
        return true
    }

    /**
     * Advance one bounded page of the explicitly selected §13.8 source into the active new-root
     * target. The migration journal is the restart boundary; the caller must run normal sync to
     * upload the authored target objects and collect signed receipts before verification.
     */
    internal suspend fun advanceNewRootMigrationPage(
        sourceChain: String,
        targetChain: String,
    ): NewRootMigrationCheckpoint =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                check(sourceChain != targetChain) { "C_SCHEMA_VIOLATION" }
                val active = checkNotNull(activeChainStateLocked()) { "C_MISSING_DEPENDENCY" }
                check(active.chainId == targetChain && active.writeState == "active") { "C_MISSING_DEPENDENCY" }
                checkNotNull(keysForChainLocked(targetChain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(SettingsStore(appContext).read().pallaSyncEnabled) { "C_WRITE_PAUSED_BY_POLICY" }
                requireProtectedAuthority(targetChain)
                val checkpoint = localIntents.newRootMigrationCheckpoint(targetChain)
                if (checkpoint?.complete == true) return@withLock checkpoint
                val page =
                    PallaSyncSnapshotStateReader(db.snapshotDatabase()).newRootMigrationPage(
                        sourceChain,
                        checkpoint?.anchor,
                        checkpoint?.cursor ?: NewRootMigrationCursor(),
                    )
                check(page.anchor.chainId == sourceChain) { "C_ROLLBACK_DETECTED" }
                val staged = localIntents.stageNewRootMigrationPage(targetChain, page)
                resumeLocalIntentsLocked()
                staged
            }
        }

    /** Return source-scan and signed-receipt progress without inferring logical completion. */
    internal suspend fun newRootMigrationProgress(targetChain: String): NewRootMigrationProgress? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val checkpoint = localIntents.newRootMigrationCheckpoint(targetChain) ?: return@withLock null
                val receipts = checkNotNull(localIntents.newRootMigrationReceiptStatus(targetChain))
                NewRootMigrationProgress(checkpoint, receipts, null)
            }
        }

    internal suspend fun activeNewRootMigrationTarget(): String? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = activeChainStateLocked() ?: return@withLock null
                state.chainId.takeIf { state.writeState == "active" && keysForChainLocked(it) != null }
            }
        }

    /** Local chain identities for an explicit §13.8 source selection, independent of recovery-set setup. */
    internal suspend fun newRootMigrationSources(): List<String> =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                db.pallaSyncDao().getNewRootMigrationSourceIds()
            }
        }

    /** Compare source and target only after every migration intent has a retained signed receipt. */
    internal suspend fun verifyNewRootMigrationState(
        sourceChain: String,
        targetChain: String,
    ): NewRootMigrationProgress =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                check(sourceChain != targetChain) { "C_SCHEMA_VIOLATION" }
                verifyNewRootMigrationStateLocked(sourceChain, targetChain)
            }
        }

    private suspend fun verifyNewRootMigrationStateLocked(
        sourceChain: String,
        targetChain: String,
    ): NewRootMigrationProgress {
        check(sourceChain != targetChain) { "C_SCHEMA_VIOLATION" }
        val target = checkNotNull(activeChainStateLocked()) { "C_MISSING_DEPENDENCY" }
        check(target.chainId == targetChain && target.writeState == "active") { "C_MISSING_DEPENDENCY" }
        checkNotNull(keysForChainLocked(targetChain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        requireProtectedAuthority(sourceChain)
        requireProtectedAuthority(targetChain)
        val checkpoint = checkNotNull(localIntents.newRootMigrationCheckpoint(targetChain)) { "C_MISSING_DEPENDENCY" }
        check(checkpoint.anchor.chainId == sourceChain) { "C_RECORD_ID_CONFLICT" }
        val receipts = checkNotNull(localIntents.newRootMigrationReceiptStatus(targetChain)) { "C_MISSING_DEPENDENCY" }
        check(receipts.complete) { "C_ACCEPTANCE_UNPROVEN" }
        val matches =
            PallaSyncSnapshotSemanticComparator.compareNewRootMaterializedState(
                db,
                sourceChain,
                targetChain,
                checkpoint,
                receipts,
            )
        return NewRootMigrationProgress(checkpoint, receipts, matches)
    }

    /** §13.8: retire the compromised source only after a fresh target is proven equivalent. */
    internal suspend fun requestNewRootSourceDeletion(
        sourceChain: String,
        targetChain: String,
        mnemonic: String,
        passphrase: String = "",
    ): PallaSyncDeletionRequestResult =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val migration = verifyNewRootMigrationStateLocked(sourceChain, targetChain)
                    check(migration.logicalStateMatches == true) { "C_ACCEPTANCE_UNPROVEN" }
                    val context = createDeletionContext(sourceChain)
                    PallaSyncRootSession
                        .beginText(
                            mnemonic,
                            passphrase,
                            PallaSyncRootSession.Operation.DELETE,
                            expectedChain = sourceChain,
                            expectedAdmin = context.admin,
                            requestGuard = policy::requireCurrent,
                            clock = policy.retry::correctedTime,
                        ).use { root -> submitDeletionAndErase(root, context) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IllegalStateException) {
                    deletionFailure(failure)
                } catch (failure: IllegalArgumentException) {
                    deletionFailure(failure)
                } catch (failure: SQLiteException) {
                    deletionFailure(failure)
                }
            }
        }

    /** §5.6/§13.8: consume shares only inside Rust, then authorize old-chain DELETE with the recovered root. */
    internal suspend fun requestNewRootSourceDeletionFromRecovery(
        sourceChain: String,
        targetChain: String,
        completion: PallaSyncRecoveryCompletion,
        cancellation: PallaSyncRootCancellation,
    ): PallaSyncDeletionRequestResult =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                var root: PallaSyncRootSession? = null
                try {
                    val migration = verifyNewRootMigrationStateLocked(sourceChain, targetChain)
                    check(migration.logicalStateMatches == true) { "C_ACCEPTANCE_UNPROVEN" }
                    val context = createDeletionContext(sourceChain)
                    root =
                        completion.takeRoot(
                            PallaSyncRootSession.Operation.DELETE,
                            policy::requireCurrent,
                            policy.retry::correctedTime,
                        )
                    check(root.identity.chainId == sourceChain && root.identity.adminPublicKey == context.admin) {
                        "C_RECOVERY_ROOT_MISMATCH"
                    }
                    cancellation.attach(root)
                    submitDeletionAndErase(root, context)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IllegalStateException) {
                    deletionFailure(failure)
                } catch (failure: IllegalArgumentException) {
                    deletionFailure(failure)
                } catch (failure: SQLiteException) {
                    deletionFailure(failure)
                } finally {
                    root?.let {
                        it.close()
                        cancellation.release(it)
                    }
                    completion.close()
                }
            }
        }

    internal suspend fun pendingNewRootSourceDeletionProof(
        sourceChain: String,
        targetChain: String,
    ): String? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val migration = verifyNewRootMigrationStateLocked(sourceChain, targetChain)
                if (migration.logicalStateMatches != true) return@withLock null
                val context = createDeletionContext(sourceChain)
                latestDeletionProof(sourceChain, context.logId)
            }
        }

    internal suspend fun completeNewRootSourceDeletionAfterExport(
        sourceChain: String,
        targetChain: String,
        exportedProof: String,
        explicitlyConfirmedOfflineCopy: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (!explicitlyConfirmedOfflineCopy) return@withLock false
                try {
                    val migration = verifyNewRootMigrationStateLocked(sourceChain, targetChain)
                    check(migration.logicalStateMatches == true) { "C_ACCEPTANCE_UNPROVEN" }
                    val context = createDeletionContext(sourceChain)
                    check(
                        db.deletionEvidenceDao().latestReceiptRevision(sourceChain, context.logId) == exportedProof,
                    ) { "C_DELETION_UNVERIFIED" }
                    val request =
                        checkNotNull(db.protocolDao().latestEvidence(sourceChain, context.logId, "delete_request")) {
                            "C_DELETION_UNVERIFIED"
                        }
                    val evidence = validateDeletionProof(context, request, exportedProof.toByteArray(Charsets.UTF_8))
                    persistAndEraseMigratedSource(context, evidence, targetChain)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    deletionFailure(failure)
                    false
                }
            }
        }

    private suspend fun persistAndEraseMigratedSource(
        context: DeletionContext,
        evidence: ProtectedEvidenceEntity,
        targetChain: String,
    ) {
        check(activeChainStateLocked()?.chainId == targetChain && targetChain != context.state.chainId) { "C_ROLLBACK_DETECTED" }
        db.transaction { db.protocolDao().retainDeletionProof(evidence) }
        eraseChainRecoveryMaterial(context.state.chainId)
        db.transaction {
            db.protocolDao().eraseChainState(context.state.chainId)
            db.pallaSyncDao().clearVerifiedDeletedChain(context.state.chainId)
        }
        check(keystore.clearVerifiedDeletedChain(context.state.chainId)) { "C_LOCAL_STORAGE_FULL" }
        check(activeChainStateLocked()?.chainId == targetChain) { "C_ROLLBACK_DETECTED" }
        projection.publish(targetChain, db)
        log("Verified new-root migration and signed source deletion proof; compromised source chain erased")
    }

    /** §§11.4/13: consume a collected root for this named operation, with the same policy and authority guards. */
    internal suspend fun rotateRecoveredEpoch(
        completion: PallaSyncRecoveryCompletion,
        cancellation: PallaSyncRootCancellation,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val root =
                    completion.takeRoot(
                        PallaSyncRootSession.Operation.ROTATE,
                        policy::requireCurrent,
                        policy.retry::correctedTime,
                    )
                try {
                    cancellation.attach(root)
                    val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                    requireProtectedAuthority(state.chainId)
                    val authority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
                    check(
                        root.identity.chainId == state.chainId &&
                            root.identity.adminPublicKey == authority.getValue("admin_public_key").jsonPrimitive.content,
                    ) {
                        "C_RECOVERY_ROOT_MISMATCH"
                    }
                    val base =
                        when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                            is PallaSyncHttpResult.Success -> normalized.value
                            else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                        }
                    val changed =
                        useRoot(root) {
                            epochLifecycle.rotate(it, base, "manual", emptyList(), true) == PallaSyncEpochLifecycle.Outcome.UPDATED
                        }
                    outboxRebase.resume(state.chainId, checkNotNull(keysForChainLocked(state.chainId)))
                    changed
                } finally {
                    root.close()
                    cancellation.release(root)
                    completion.close()
                }
            }
        }

    private suspend fun withEpochRoot(
        mnemonic: String,
        passphrase: String,
        cancellation: PallaSyncRootCancellation?,
        operation: PallaSyncRootSession.Operation,
        action: suspend (PallaSyncRootSession, HttpUrl) -> Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                    requireProtectedAuthority(state.chainId)
                    val authority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId))).jsonObject
                    val base =
                        when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                            is PallaSyncHttpResult.Success -> normalized.value
                            else -> error("C_BOOTSTRAP_TRUST_REQUIRED")
                        }
                    val root =
                        PallaSyncRootSession
                            .beginText(
                                mnemonic,
                                passphrase,
                                operation,
                                state.chainId,
                                authority.getValue("admin_public_key").jsonPrimitive.content,
                                policy::requireCurrent,
                                policy.retry::correctedTime,
                            )
                    val completed = useRoot(root, cancellation) { action(it, base) }
                    // Large rebase work requires normal epoch keys only; end root first.
                    outboxRebase.resume(state.chainId, checkNotNull(keysForChainLocked(state.chainId)))
                    completed
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: PallaSyncInitialHistory.Failure) {
                    val code =
                        when (val result = failure.result) {
                            is PallaSyncHttpResult.ProtocolError -> result.message
                            is PallaSyncHttpResult.Retryable -> result.message
                            else -> "C_MISSING_DEPENDENCY"
                        }
                    log("Epoch operation paused [${epochLifecycle.stage}]: $code")
                    false
                } catch (failure: IllegalStateException) {
                    val code = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_SCHEMA_VIOLATION"
                    log("Epoch operation paused [${epochLifecycle.stage}]: $code")
                    false
                }
            }
        }

    private suspend fun <T> useRoot(
        root: PallaSyncRootSession,
        cancellation: PallaSyncRootCancellation? = null,
        action: suspend (PallaSyncRootSession) -> T,
    ): T =
        root.use {
            try {
                cancellation?.attach(it)
                currentCoroutineContext().ensureActive()
                action(it)
            } finally {
                cancellation?.release(it)
            }
        }

    suspend fun createGenesis(
        seedPhrase: String,
        serverUrl: String? = null,
        passphrase: String = "",
        cancellation: PallaSyncRootCancellation? = null,
    ): Boolean = joinChainInternal(seedPhrase, Build.MODEL, isGenesis = true, serverUrl, passphrase, cancellation)

    suspend fun joinChain(
        seedPhrase: String,
        deviceName: String = Build.MODEL,
        serverUrl: String? = null,
        passphrase: String = "",
        cancellation: PallaSyncRootCancellation? = null,
        bootstrapReview: suspend (PallaSyncBootstrapReview) -> Boolean,
    ): Boolean = joinChainInternal(seedPhrase, deviceName, isGenesis = false, serverUrl, passphrase, cancellation, bootstrapReview)

    private suspend fun joinChainInternal(
        seedPhrase: String,
        deviceName: String,
        isGenesis: Boolean,
        serverUrl: String?,
        passphrase: String,
        cancellation: PallaSyncRootCancellation?,
        bootstrapReview: suspend (PallaSyncBootstrapReview) -> Boolean = { false },
    ): Boolean {
        var activatedChainId: String? = null
        val joined =
            runCatching {
                withContext(Dispatchers.IO) {
                    operationMutex.withLock {
                        val activated =
                            joinChainLocked(
                                seedPhrase.trim(),
                                passphrase,
                                deviceName,
                                isGenesis,
                                serverUrl,
                                cancellation,
                                bootstrapReview,
                            )
                                ?: return@withLock false
                        val chainId = activated.first
                        val activatedBaseUrl = activated.second
                        activatedChainId = chainId
                        SettingsStore(appContext).apply {
                            setPallaSyncServerUrlFromCoordinator(activatedBaseUrl.toString().trimEnd('/'))
                            setPallaSyncEnabledFromCoordinator(true)
                        }
                        if (keystore.pendingActivation() == chainId) keystore.completeActivation(chainId)
                        when (synchronizeOnceLocked(activatedBaseUrl)) {
                            SyncCycleOutcome.Gone -> {
                                false
                            }

                            else -> {
                                db.pallaSyncDao().getActiveChainState()?.chainId == chainId &&
                                    keysForChainLocked(chainId) != null
                            }
                        }
                    }
                }
            }.getOrElse { expectedFailure ->
                val error = expectedFailure
                if (error is CancellationException) throw error
                val code = error.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_SCHEMA_VIOLATION"
                val debugFrames =
                    if (appContext.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                        error.stackTrace.take(4).joinToString(" <- ") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                    } else {
                        ""
                    }
                log("Initial PallaSync cycle paused: $code $debugFrames")
                activatedChainId != null
            }
        if (joined) {
            startBackgroundSync()
        }
        return joined
    }

    private suspend fun joinChainLocked(
        seedPhrase: String,
        passphrase: String,
        deviceName: String,
        isGenesis: Boolean,
        serverUrl: String?,
        cancellation: PallaSyncRootCancellation?,
        bootstrapReview: suspend (PallaSyncBootstrapReview) -> Boolean,
    ): Pair<String, HttpUrl>? =
        PallaSyncRootSession
            .beginText(
                seedPhrase,
                passphrase,
                if (isGenesis) PallaSyncRootSession.Operation.GENESIS else PallaSyncRootSession.Operation.ENROLLMENT,
                requestGuard = policy::requireCurrent,
                clock = policy.retry::correctedTime,
            ).let { root ->
                useRoot(root, cancellation) {
                    joinChainWithSessionLocked(it, deviceName, isGenesis, serverUrl, bootstrapReview)
                }
            }

    private suspend fun joinChainWithSessionLocked(
        rootSession: PallaSyncRootSession,
        deviceName: String,
        isGenesis: Boolean,
        serverUrl: String?,
        bootstrapReview: suspend (PallaSyncBootstrapReview) -> Boolean,
    ): Pair<String, HttpUrl>? {
        val base = selectedJoinBase(serverUrl)
        val origin = PallaSyncUrls.extractOrigin(base)
        val chain = rootSession.identity.chainId
        // A completed activation must never generate a replacement identity or genesis request.
        val committed = db.dao.getChainState(chain)
        if (committed != null && committed.writeState != "activation_preparing" && committed.writeState != "archived") {
            requireProtectedAuthority(chain)
            val boundAuthority = Json.parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(chain))).jsonObject
            check(
                boundAuthority
                    .getValue("pin")
                    .jsonObject
                    .getValue("origin")
                    .jsonPrimitive.content == origin,
            ) {
                "C_BOOTSTRAP_TRUST_REQUIRED"
            }
            check(keysForChainLocked(chain) != null) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            return chain to base
        }
        val prepared =
            if (isGenesis) {
                prepareGenesis(rootSession, deviceName, base, origin)
            } else {
                prepareMnemonicEnrollment(rootSession, deviceName, base, origin)
            }
        checkNotNull(prepared) { "C_MISSING_DEPENDENCY" }
        if (!isGenesis) {
            val review =
                PallaSyncBootstrapReview.fromVerifiedCheckpoint(
                    origin,
                    rootSession.identity,
                    checkNotNull(prepared.checkpoint) { "C_MISSING_DEPENDENCY" },
                )
            check(bootstrapReview(review)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        }
        check(keystore.savePendingChainKeys(prepared.keys) && keystore.getPendingChainKeys() == prepared.keys) { "C_LOCAL_STORAGE_FULL" }
        stagePreparedActivation(prepared, isGenesis, origin)
        val collections = SettingsStore(appContext).readSyncedCollections()
        if (!isGenesis) {
            PallaSyncPrefixMaterializer(db, keystore).apply(chain, prepared.keys, checkNotNull(prepared.checkpoint))
        }
        localAuthor.initialize(chain, prepared.keys, orderInitialEvents(chain, buildInitialSettingsSyncEvents(collections)))
        check(rootSession.identity.chainId == chain) { "C_RECOVERY_ROOT_MISMATCH" }
        currentCoroutineContext().ensureActive()
        keystore.stageActivation(chain)
        db.transaction {
            val complete = checkNotNull(db.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
            db.dao.archiveOtherChainStates(chain)
            db.dao.updateChainState(complete.copy(writeState = "active"))
        }
        check(keystore.promotePendingChainKeys()) { "C_LOCAL_STORAGE_FULL" }
        stopBackgroundSync()
        log("Successfully joined chain: ${chain.take(8)}…")
        return chain to base
    }

    private suspend fun selectedJoinBase(serverUrl: String?): HttpUrl {
        val normalized = PallaSyncUrls.normalize(serverUrl ?: getServerUrl())
        check(normalized is PallaSyncHttpResult.Success) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val health = refreshRelayHealth(normalized.value)
        if (health !is PallaSyncHttpResult.Success) throw PallaSyncInitialHistory.Failure(health)
        return normalized.value
    }

    private suspend fun requireProtectedAuthority(chain: String) {
        val saved = checkNotNull(db.protocolDao().currentAuthority(chain)) { "C_MISSING_DEPENDENCY" }
        check(
            crypto.objectHash(saved) == keystore.authorityHash(chain) &&
                keystore.authorityHash(chain) != null,
        ) { "C_ROLLBACK_DETECTED" }
        val origin =
            Json
                .parseToJsonElement(saved)
                .jsonObject
                .getValue("pin")
                .jsonObject
                .getValue("origin")
                .jsonPrimitive.content
        policy.bindChainOrigin(chain, origin)
    }

    private suspend fun stagePreparedActivation(
        prepared: PreparedJoin,
        genesis: Boolean,
        origin: String,
    ) {
        val chain = checkNotNull(prepared.keys.chainId)
        check(!db.deletionEvidenceDao().hasDeletionReceipt(chain)) { "C_DELETION_UNVERIFIED" }
        val existing = db.dao.getChainState(chain)
        if (existing == null) {
            pinPreparedCheckpoint(prepared)
            commitPreparedActivation(prepared, initialChainState(prepared, genesis).copy(writeState = "activation_preparing"), origin)
        } else {
            check(existing.writeState == "activation_preparing" && keystore.boundDeviceId(chain) == prepared.keys.deviceId) {
                "C_MISSING_DEPENDENCY"
            }
            requireProtectedAuthority(chain)
        }
    }

    private fun initialChainState(
        prepared: PreparedJoin,
        genesis: Boolean,
    ): ChainStateEntity {
        val pending =
            ChainStateEntity(
                chainId = checkNotNull(prepared.keys.chainId),
                lamport = 0,
                keyEpoch = prepared.keys.epoch,
                chainVectorJson = "{}",
                initialPullCompleted = genesis,
                writeState = "pending_verification",
            )
        val checkpoint = prepared.checkpoint ?: return pending
        return pending.copy(
            generation = checkpoint.generation,
            lastRelaySeq = if (genesis) checkpoint.headSeq else 0,
            downloadedSeq = if (genesis) checkpoint.headSeq else 0,
            logId = checkpoint.logId,
            parametersHash = checkpoint.parametersHash,
            epochHash = checkpoint.epochHash,
            verifiedSeq = if (genesis) checkpoint.headSeq else 0,
            materializedSeq = if (genesis) checkpoint.headSeq else 0,
            verifiedHash = if (genesis) checkpoint.headHash else checkNotNull(checkpoint.dataZero),
            transparencyId = checkpoint.transparencyId,
            transparencyIndex = checkpoint.transparencyIndex,
            transparencyHash = checkpoint.transparencyHash,
            writeState = "active",
        )
    }

    private fun pinPreparedCheckpoint(prepared: PreparedJoin) {
        val chainId = checkNotNull(prepared.keys.chainId)
        val deviceId = checkNotNull(prepared.keys.deviceId)
        val genesisCheckpoint = prepared.checkpoint
        genesisCheckpoint?.authority?.toString()?.let { authority ->
            check(keystore.pinInitialAuthority(chainId, crypto.objectHash(authority) ?: error("C_SCHEMA_VIOLATION"))) {
                "C_LOCAL_STORAGE_FULL"
            }
            policy.bindChainOrigin(
                chainId,
                Json
                    .parseToJsonElement(authority)
                    .jsonObject
                    .getValue("pin")
                    .jsonObject
                    .getValue("origin")
                    .jsonPrimitive.content,
            )
        }
        genesisCheckpoint?.let { checkpoint ->
            check(keystore.pinInitialDeviceId(chainId, deviceId)) { "C_LOCAL_STORAGE_FULL" }
            val anchor =
                buildJsonObject {
                    put("transparency_id", checkpoint.transparencyId)
                    put("index", checkpoint.transparencyIndex)
                    put("hash", checkpoint.transparencyHash)
                }
            check(keystore.pinInitialTransparency(chainId, anchor)) { "C_LOCAL_STORAGE_FULL" }
        }
    }

    private suspend fun commitPreparedActivation(
        prepared: PreparedJoin,
        activatedState: ChainStateEntity,
        aud: String,
    ) {
        val chainId = activatedState.chainId
        val genesisCheckpoint = prepared.checkpoint
        db.transaction {
            check(db.dao.getChainState(chainId) == null) { "C_MISSING_DEPENDENCY" }
            db.dao.updateChainState(activatedState)
            genesisCheckpoint?.let { retainGenesisEvidence(chainId, aud, it) }
            genesisCheckpoint?.authority?.toString()?.let { authority ->
                db.protocolDao().commitAuthority(
                    com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity(
                        chainId,
                        "checkpoint",
                        crypto.objectHash(authority) ?: error("C_SCHEMA_VIOLATION"),
                        activatedState.generation,
                        authority,
                        "current_checkpoint",
                    ),
                )
            }
        }
    }

    /** Original control objects and acceptance claims commit alongside the initial H5 state. */
    private suspend fun retainGenesisEvidence(
        chain: String,
        origin: String,
        checkpoint: PallaSyncGenesisCheckpoint,
    ) {
        val body = checkpoint.genesis ?: return
        val envelopes = body.getValue("envelopes").jsonArray.map { it.jsonObject }
        val objects =
            listOf(
                "parameters" to body.getValue("parameters"),
                "device" to body.getValue("device_record"),
                "envelope" to envelopes.single { it.getValue("recipient_kind").jsonPrimitive.content == "device" },
                "envelope" to envelopes.single { it.getValue("recipient_kind").jsonPrimitive.content == "recovery" },
                "epoch" to body.getValue("epoch"),
            )
        check(objects.size == checkpoint.receipts.size) { "C_ACCEPTANCE_UNPROVEN" }
        objects.zip(checkpoint.receipts).forEach { (objectAndKind, receipt) ->
            val (kind, value) = objectAndKind
            val entry =
                buildJsonObject {
                    put("relay_seq", receipt.getValue("relay_seq"))
                    put("received_at_ms", receipt.getValue("received_at_ms"))
                    put("kind", kind)
                    put("object", value)
                    if (kind == "epoch") put("committed_at_ms", receipt.getValue("received_at_ms"))
                }
            check(crypto.objectHash(entry.toString()) == receipt.getValue("entry_hash").jsonPrimitive.content) { "C_LOG_HASH_MISMATCH" }
            retainActivationLog(chain, checkpoint.logId, entry, receipt)
            db.protocolDao().retainAuthority(
                com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity(
                    chain,
                    "genesis_entry",
                    checkNotNull(crypto.objectHash(entry.toString())),
                    0,
                    entry.toString(),
                    "verified",
                ),
            )
            db.protocolDao().retainEvidence(
                ProtectedEvidenceEntity(
                    origin,
                    receipt.getValue("relay_key_id").jsonPrimitive.content,
                    chain,
                    checkpoint.logId,
                    "upload_receipt",
                    receipt
                        .getValue("relay_seq")
                        .jsonPrimitive.content
                        .toLong(),
                    checkNotNull(crypto.objectHash(receipt.toString())),
                    receipt.toString(),
                ),
            )
        }
    }

    private suspend fun retainActivationLog(
        chain: String,
        log: String,
        entry: JsonObject,
        receipt: JsonObject,
    ) {
        db.protocolDao().retainLogEntry(
            com.yunfie.illustia.pallasync.data.LogEntryEntity(
                chain,
                log,
                receipt.getValue("relay_seq").jsonPrimitive.long,
                checkNotNull(crypto.objectHash(entry.toString())),
                entry.toString(),
                receipt.toString(),
                "authority_verified",
            ),
        )
    }

    private data class PreparedJoin(
        val keys: PallaSyncKeySnapshot,
        val checkpoint: PallaSyncGenesisCheckpoint?,
    )

    private suspend fun prepareGenesis(
        root: PallaSyncRootSession,
        name: String,
        base: HttpUrl,
        origin: String,
    ): PreparedJoin? {
        val (body, keys) = prepareGenesisCandidate(root, name, origin)
        // An exact durable retry gets a fresh audience/body-bound token; tokens are never journaled.
        val token =
            checkNotNull(root.token(origin, "POST", "/pallasync/v3/chains", body = body.toByteArray())) { "C_RECOVERY_AUTH_REQUIRED" }
        val request =
            Request
                .Builder()
                .url(PallaSyncUrls.chains(base))
                .header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $token")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        val response =
            remote.execute(request) { result ->
                val liveIdentity = root.identity
                val publicIdentity =
                    buildJsonObject {
                        put("chain_id", liveIdentity.chainId)
                        put("admin_public_key", liveIdentity.adminPublicKey)
                    }.toString()
                val checkpoint =
                    PallaSyncCore.validateGenesisAcceptance(
                        result.pallaSyncBytes(),
                        body,
                        publicIdentity,
                        origin,
                        policy.trustedRelayKey(origin),
                    )
                PallaSyncHttpResult.Success(json.decodeFromString<PallaSyncGenesisCheckpoint>(checkpoint))
            }
        return when (response) {
            is PallaSyncHttpResult.Success -> PreparedJoin(keys, response.value)
            else -> null.also { log("Genesis acceptance is unresolved; the exact request and candidate keys were retained") }
        }
    }

    private fun prepareGenesisCandidate(
        root: PallaSyncRootSession,
        name: String,
        origin: String,
    ): Pair<String, PallaSyncKeySnapshot> {
        val identity = root.identity
        val stored = keystore.pendingGenesisRequest(identity.chainId, origin)
        val body: String
        val keys: PallaSyncKeySnapshot
        if (stored != null) {
            body = stored
            keys = checkNotNull(keystore.getPendingChainKeys()) { "C_MISSING_DEPENDENCY" }
            val request = json.parseToJsonElement(body).jsonObject
            check(
                request
                    .getValue("parameters")
                    .jsonObject
                    .getValue("admin_public_key")
                    .jsonPrimitive.content == identity.adminPublicKey &&
                    request
                        .getValue("device_record")
                        .jsonObject
                        .getValue("device_id")
                        .jsonPrimitive.content == keys.deviceId,
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        } else {
            val prepared = crypto.createGenesisBundle(root, name, aud = origin).use { it.publicResult to it.snapshot() }
            body = prepared.first.requestBody
            keys = prepared.second
            check(keystore.savePendingChainKeys(keys, body, origin)) { "C_LOCAL_STORAGE_FULL" }
        }
        check(keystore.getPendingChainKeys() == keys && keystore.pendingGenesisRequest(identity.chainId, origin) == body) {
            "C_LOCAL_STORAGE_FULL"
        }
        return body to keys
    }

    private suspend fun prepareMnemonicEnrollment(
        rootSession: PallaSyncRootSession,
        deviceName: String,
        baseUrl: HttpUrl,
        aud: String,
    ): PreparedJoin? =
        try {
            check(aud == PallaSyncUrls.extractOrigin(baseUrl)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val (keys, checkpoint) =
                PallaSyncMnemonicEnrollment(db, remote, policy, keystore, crypto)
                    .prepare(rootSession, deviceName, baseUrl)
            PreparedJoin(keys, checkpoint)
        } catch (failure: PallaSyncInitialHistory.Failure) {
            log("Enrollment is unresolved (${failure.result::class.simpleName}); candidate keys and verified evidence were retained")
            null
        }

    suspend fun enqueueDataEvent(
        schema: String,
        entityId: String,
        operation: String,
        body: JsonElement,
    ): Boolean = enqueueDataEvents(listOf(PallaSyncPendingEvent(schema, entityId, operation, body)))

    override suspend fun enqueueDataEvents(events: List<PallaSyncPendingEvent>): Boolean {
        if (events.isEmpty()) return true
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val retained = enqueueDataEventsLocked(events)
                if (retained) resumeLocalIntentsLocked()
                retained
            }
        }
    }

    /** Optional recovery audit must not be published into a concurrently selected different chain. */
    internal suspend fun enqueueRecoveryAudit(
        chainId: String,
        event: PallaSyncPendingEvent,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (activeChainStateLocked()?.chainId != chainId) return@withLock false
                check(event.schema == "pallasync.audit/3") { "C_RECOVERY_SHARE_INVALID" }
                localIntents.stageRecoveryAudit(chainId, event)
                true
            }
        }

    override suspend fun <T> enqueueDataEventsThen(
        events: List<PallaSyncPendingEvent>,
        afterEnqueue: suspend () -> T,
    ): T =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                // An app without a bound chain edits locally. A paused/disabled bound chain journals first.
                if (activeChainStateLocked() != null) check(enqueueDataEventsLocked(events)) { "C_LOCAL_STORAGE_FULL" }
                val saved = afterEnqueue()
                resumeLocalIntentsLocked()
                saved
            }
        }

    private suspend fun orderInitialEvents(
        chain: String,
        events: List<PallaSyncPendingEvent>,
    ): List<PallaSyncPendingEvent> {
        var tail = db.protocolDao().lastOrderedEntity(chain, FAVORITE_TAG_SCHEMA_V3)?.orderKey
        return events.map { event ->
            if (event.schema != FAVORITE_TAG_SCHEMA_V3 || event.operation != "upsert") return@map event
            val live = db.protocolDao().liveEntityIds(chain, FAVORITE_TAG_SCHEMA_V3, listOf(event.entityId)).isNotEmpty()
            val observed = if (live) db.protocolDao().entity(chain, FAVORITE_TAG_SCHEMA_V3, event.entityId)?.orderKey else null
            val order = observed ?: PallaSyncFractionalOrder.between(tail, null).also { tail = it }
            event.copy(context = buildJsonObject { put("order_key", order) }, favoriteOrder = null)
        }
    }

    private suspend fun orderLocalEvents(
        chain: String,
        events: List<PallaSyncPendingEvent>,
    ): List<PallaSyncPendingEvent> {
        val order = events.mapNotNull { it.favoriteOrder }.firstOrNull()
        val retained = mutableMapOf<String, String>()
        order?.forEach { tag ->
            db
                .protocolDao()
                .entity(chain, FAVORITE_TAG_SCHEMA_V3, tag)
                ?.orderKey
                ?.let { retained[tag] = it }
        }
        val tail = db.protocolDao().lastOrderedEntity(chain, FAVORITE_TAG_SCHEMA_V3)?.orderKey
        return prepareFavoriteOrder(events, retained, tail)
    }

    private suspend fun enqueueDataEventsLocked(events: List<PallaSyncPendingEvent>): Boolean {
        val state = activeChainStateLocked() ?: return false
        localIntents.stage(state.chainId, events)
        return true
    }

    private suspend fun resumeLocalIntentsLocked() {
        val state = activeChainStateLocked() ?: return
        drainRecoveryAuditsLocked(state.chainId)
        if (state.writeState == "active" && SettingsStore(appContext).read().pallaSyncEnabled) {
            try {
                requireProtectedAuthority(state.chainId)
                val keys = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                localIntents.resume(state.chainId, keys)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IllegalStateException) {
                val code = error.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_MISSING_DEPENDENCY"
                log("$code: unsigned local intent was retained")
            } catch (_: SQLiteException) {
                log("C_LOCAL_STORAGE_FULL: unsigned local intent was retained")
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun drainRecoveryAuditsLocked(chain: String) {
        try {
            recoveryAudits.drain(chain) { event ->
                localIntents.stageRecoveryAudit(chain, event)
                true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val code = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_LOCAL_STORAGE_FULL"
            log("$code: recovery audit metadata retained")
        }
    }

    /** §17.11: HTTP status alone never grants local erasure. */
    suspend fun deleteChain(callApi: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val activeChain = db.pallaSyncDao().getActiveChainStates().singleOrNull()
                if (activeChain == null) return@withLock true
                if (!callApi) {
                    clearLocalChainLocked()
                    return@withLock true
                }
                log("C_DELETION_UNVERIFIED: chain deletion requires the explicit root-authorized flow")
                false
            }
        }
    }

    /** §10.15: submit a root-authorized deletion request bound to the verified CAS heads. */
    suspend fun requestChainDeletion(
        mnemonic: String,
        passphrase: String = "",
    ): PallaSyncDeletionRequestResult =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val context = deletionContext() ?: return@withLock PallaSyncDeletionRequestResult.FAILED
                try {
                    PallaSyncRootSession
                        .beginText(
                            mnemonic,
                            passphrase,
                            PallaSyncRootSession.Operation.DELETE,
                            expectedChain = context.state.chainId,
                            expectedAdmin = context.admin,
                            requestGuard = policy::requireCurrent,
                            clock = policy.retry::correctedTime,
                        ).use { root ->
                            submitDeletionAndErase(root, context)
                        }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IllegalStateException) {
                    deletionFailure(failure)
                } catch (failure: IllegalArgumentException) {
                    deletionFailure(failure)
                } catch (failure: SQLiteException) {
                    deletionFailure(failure)
                }
            }
        }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun deletionContext(chainId: String? = null): DeletionContext? =
        try {
            createDeletionContext(chainId)
        } catch (_: Exception) {
            log("C_DELETION_UNVERIFIED: local chain authority or relay context is unavailable")
            null
        }

    private suspend fun createDeletionContext(chainId: String? = null): DeletionContext {
        val state =
            if (chainId == null) {
                checkNotNull(activeChainStateLocked()) { "C_DELETION_UNVERIFIED" }
            } else {
                checkNotNull(db.dao.getChainState(chainId)) { "C_DELETION_UNVERIFIED" }
            }
        check(state.generation in 0..MAX_PROTOCOL_COUNTER) { "C_DELETION_UNVERIFIED" }
        val parametersHash = checkNotNull(state.parametersHash) { "C_DELETION_UNVERIFIED" }
        val epochHash = checkNotNull(state.epochHash) { "C_DELETION_UNVERIFIED" }
        val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_DELETION_UNVERIFIED" }
        val authority = Json.parseToJsonElement(authorityText).jsonObject
        val pin = checkNotNull(authority["pin"]?.jsonObject) { "C_DELETION_UNVERIFIED" }
        val admin = checkNotNull(authority["admin_public_key"]?.jsonPrimitive?.content) { "C_DELETION_UNVERIFIED" }
        val normalized = PallaSyncUrls.normalize(getServerUrl()) as? PallaSyncHttpResult.Success
        val base = checkNotNull(normalized?.value) { "C_DELETION_UNVERIFIED" }
        val origin = PallaSyncUrls.extractOrigin(base)
        val logId = state.logId ?: checkNotNull(pin["log_id"]?.jsonPrimitive?.content) { "C_DELETION_UNVERIFIED" }
        val publicKey = checkNotNull(pin["public_key"]?.jsonPrimitive?.content) { "C_DELETION_UNVERIFIED" }
        val relayKeyId =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(Base64.getUrlDecoder().decode(publicKey)),
            )
        return DeletionContext(state, parametersHash, epochHash, authorityText, admin, base, origin, logId, relayKeyId)
    }

    private suspend fun submitDeletionAndErase(
        root: PallaSyncRootSession,
        context: DeletionContext,
    ): PallaSyncDeletionRequestResult {
        // A first DELETE is destructive: establish a current consistency checkpoint first.
        // If the outcome of a previous DELETE was unknown, reuse its exact signed request and
        // query the deletion proof route without probing routes that may now return 410.
        if (db.protocolDao().latestEvidence(context.state.chainId, context.logId, "delete_request") == null) {
            requireSnapshotResult(consistencyProbe.run(context.base, context.state.chainId))
        }
        val body = prepareDeletionRequest(root, context)
        log("Signed deletion request prepared")
        val chainUrl = PallaSyncUrls.chain(context.base, context.state.chainId)
        val deletionUrl = chainUrl.newBuilder().addPathSegment("deletion").build()
        val deleteToken =
            checkNotNull(
                root.token(context.origin, "DELETE", chainUrl.encodedPath, body = body.toByteArray()),
            ) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
        val deleteRequest =
            Request
                .Builder()
                .url(chainUrl)
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .header("Authorization", "PallaSync $deleteToken")
                .delete(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        val deleteResult = remote.executeUnit(deleteRequest)
        log("Deletion request response received; fetching signed proof")
        if (deleteResult !is PallaSyncHttpResult.Success) log("Deletion response did not confirm erasure; checking signed proof")
        val getToken =
            try {
                checkNotNull(root.token(context.origin, "GET", deletionUrl.encodedPath)) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
            } catch (failure: IllegalStateException) {
                if (failure.message != "C_RELAY_POLICY_UNAVAILABLE") throw failure
                return PallaSyncDeletionRequestResult.PENDING_EVIDENCE.also {
                    log("C_DELETION_UNVERIFIED: relay policy refresh is deferred after an unknown DELETE result; local data retained")
                }
            }
        val getRequest =
            Request
                .Builder()
                .url(deletionUrl)
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .header("Authorization", "PallaSync $getToken")
                .get()
                .build()
        val proofBytes =
            when (val result = remote.execute(getRequest) { PallaSyncHttpResult.Success(it.pallaSyncBytes()) }) {
                is PallaSyncHttpResult.Success -> result.value

                else -> return PallaSyncDeletionRequestResult.PENDING_EVIDENCE.also {
                    log("C_DELETION_UNVERIFIED: signed deletion proof is not available; local data retained")
                }
            }
        log("Signed deletion proof fetched; validating pinned authority and receipt")
        val evidence = validateDeletionProof(context, body, proofBytes)
        log("Signed deletion proof validated")
        db.protocolDao().retainDeletionProof(evidence)
        if (activeChainStateLocked()?.chainId == context.state.chainId) {
            SettingsStore(appContext).setPallaSyncEnabledFromCoordinator(false)
            stopBackgroundSync()
        }
        return PallaSyncDeletionRequestResult.PROOF_READY_FOR_EXPORT
    }

    private suspend fun prepareDeletionRequest(
        root: PallaSyncRootSession,
        context: DeletionContext,
    ): String {
        val previous = db.protocolDao().latestEvidence(context.state.chainId, context.logId, "delete_request")
        val reusable =
            previous?.takeIf { saved ->
                runCatching {
                    val value = Json.parseToJsonElement(saved).jsonObject
                    value["expected_parameters_hash"]?.jsonPrimitive?.content == context.parametersHash &&
                        value["expected_epoch_hash"]?.jsonPrimitive?.content == context.epochHash
                }.getOrDefault(false)
            }
        val body =
            reusable ?: run {
                check(previous == null) { "C_DELETION_UNVERIFIED" }
                PallaSyncCore.createDeleteRequest(
                    root.handle,
                    context.state.generation.toInt(),
                    UUID.randomUUID().toString(),
                    context.parametersHash,
                    context.epochHash,
                    policy.retry.correctedTime(context.origin),
                )
            }
        if (previous == null) {
            val hash = checkNotNull(PallaSyncCore.objectHash(body)) { "C_DELETION_UNVERIFIED" }
            val requestEvidence =
                ProtectedEvidenceEntity(
                    context.origin,
                    context.relayKeyId,
                    context.state.chainId,
                    context.logId,
                    "delete_request",
                    0,
                    hash,
                    body,
                )
            db.protocolDao().retainEvidence(requestEvidence)
            check(
                db
                    .protocolDao()
                    .evidenceAt(
                        context.origin,
                        context.relayKeyId,
                        context.state.chainId,
                        context.logId,
                        "delete_request",
                        0,
                    ).contains(body),
            ) {
                "C_LOCAL_STORAGE_FULL"
            }
        }
        return body
    }

    private suspend fun validateDeletionReceiptHistory(
        context: DeletionContext,
        receipt: JsonObject,
        pinJson: String,
        sequence: Long,
    ) {
        val deletionId = receipt.getValue("deletion_id").jsonPrimitive.content
        val previousReceipt =
            db
                .deletionEvidenceDao()
                .latestReceiptAtSequence(
                    context.origin,
                    context.relayKeyId,
                    context.state.chainId,
                    context.logId,
                    sequence,
                )?.let { saved ->
                    runCatching {
                        Json
                            .parseToJsonElement(saved)
                            .jsonObject
                            .getValue("delete_receipt")
                            .jsonObject
                    }.getOrNull()?.takeIf { it.getValue("deletion_id").jsonPrimitive.content == deletionId }
                }
        previousReceipt?.let { previous ->
            PallaSyncCore.validateDeletionReceiptRevision(previous.toString(), receipt.toString(), pinJson)
        }
    }

    private suspend fun validateDeletionProof(
        context: DeletionContext,
        body: String,
        bytes: ByteArray,
    ): ProtectedEvidenceEntity {
        val pinJson =
            buildJsonObject {
                put("origin", context.origin)
                put("chain_id", context.state.chainId)
                put("public_key", policy.trustedRelayKey(context.origin))
                put("log_id", context.logId)
            }.toString()
        PallaSyncCore.validateDeletionProof(bytes, pinJson, context.authorityText, body)
        val proofText = bytes.toString(Charsets.UTF_8)
        val receipt =
            Json
                .parseToJsonElement(proofText)
                .jsonObject
                .getValue("delete_receipt")
                .jsonObject
        check(receipt.getValue("relay_key_id").jsonPrimitive.content == context.relayKeyId) { "C_RELAY_KEY_MISMATCH" }
        val sequence =
            receipt
                .getValue("transparency_index")
                .jsonPrimitive.content
                .toLong()
        validateDeletionReceiptHistory(context, receipt, pinJson, sequence)
        val hash = checkNotNull(PallaSyncCore.objectHash(proofText)) { "C_DELETION_UNVERIFIED" }
        return ProtectedEvidenceEntity(
            context.origin,
            context.relayKeyId,
            context.state.chainId,
            context.logId,
            "delete_receipt",
            sequence,
            hash,
            proofText,
        )
    }

    private suspend fun persistAndErase(
        context: DeletionContext,
        evidence: ProtectedEvidenceEntity,
    ) {
        val request =
            checkNotNull(
                db.protocolDao().latestEvidence(context.state.chainId, context.logId, "delete_request"),
            ) {
                "C_DELETION_UNVERIFIED"
            }
        val authorityHash = checkNotNull(PallaSyncCore.objectHash(context.authorityText)) { "C_DELETION_UNVERIFIED" }
        val authorityEvidence =
            ProtectedEvidenceEntity(
                context.origin,
                context.relayKeyId,
                context.state.chainId,
                context.logId,
                "deletion_authority",
                0,
                authorityHash,
                context.authorityText,
            )
        db.transaction {
            db.protocolDao().retainDeletionProof(evidence)
            db.protocolDao().retainEvidence(authorityEvidence)
            check(
                db
                    .protocolDao()
                    .evidenceAt(
                        context.origin,
                        context.relayKeyId,
                        context.state.chainId,
                        context.logId,
                        "deletion_authority",
                        0,
                    ).contains(context.authorityText),
            ) { "C_LOCAL_STORAGE_FULL" }
            check(
                db
                    .protocolDao()
                    .evidenceAt(
                        context.origin,
                        context.relayKeyId,
                        context.state.chainId,
                        context.logId,
                        "delete_request",
                        0,
                    ).contains(request),
            ) { "C_DELETION_UNVERIFIED" }
        }
        eraseChainRecoveryMaterial(context.state.chainId)
        db.transaction {
            db.protocolDao().eraseChainState(context.state.chainId)
            db.pallaSyncDao().clearVerifiedDeletedChain(context.state.chainId)
        }
        check(keystore.clearVerifiedDeletedChain(context.state.chainId)) { "C_LOCAL_STORAGE_FULL" }
        SettingsStore(appContext).setPallaSyncEnabledFromCoordinator(false)
        stopBackgroundSync()
        projection.clearAfterVerifiedDeletion()
        log("Signed deletion proof verified and retained; chain-local keys and state erased")
    }

    suspend fun pendingDeletionProof(): String? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = activeChainStateLocked() ?: return@withLock null
                val authorityText = db.protocolDao().currentAuthority(state.chainId) ?: return@withLock null
                val authority = runCatching { Json.parseToJsonElement(authorityText).jsonObject }.getOrNull() ?: return@withLock null
                val logId =
                    state.logId ?: authority["pin"]
                        ?.jsonObject
                        ?.get("log_id")
                        ?.jsonPrimitive
                        ?.content ?: return@withLock null
                latestDeletionProof(state.chainId, logId)
            }
        }

    private suspend fun latestDeletionProof(
        chainId: String,
        logId: String,
    ): String? = db.deletionEvidenceDao().latestReceiptRevision(chainId, logId)

    suspend fun retainedDeletedChainTargets(): List<ProtectedEvidenceEntity> =
        withContext(Dispatchers.IO) { db.deletionEvidenceDao().retainedDeletionAuthorities() }

    /** Re-export every retained completion revision only after rechecking its signed proof and exact bundle binding. */
    suspend fun retainedDeletedChainEvidenceRevisions(chainId: String): List<PallaSyncDeletionEvidenceRevision> =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val authorityEvidence =
                    db.deletionEvidenceDao().retainedDeletionAuthorities().singleOrNull { it.chainId == chainId }
                        ?: return@withLock emptyList()
                check(PallaSyncCore.objectHash(authorityEvidence.signedJson) == authorityEvidence.objectHash) {
                    "C_DELETION_UNVERIFIED"
                }
                val authorityText = authorityEvidence.signedJson
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val pin = authority.getValue("pin").jsonObject
                check(
                    authorityEvidence.origin == pin.getValue("origin").jsonPrimitive.content &&
                        authorityEvidence.logId == pin.getValue("log_id").jsonPrimitive.content,
                ) { "C_RELAY_KEY_MISMATCH" }
                val request =
                    db
                        .protocolDao()
                        .evidenceAt(
                            authorityEvidence.origin,
                            authorityEvidence.relayKeyId,
                            chainId,
                            authorityEvidence.logId,
                            "delete_request",
                            0,
                        ).singleOrNull() ?: error("C_DELETION_UNVERIFIED")
                val pinJson = pin.toString()
                val archiveRows =
                    db
                        .protocolDao()
                        .deletionEvidenceArchiveHistory(chainId, authorityEvidence.logId)
                val verifiedRevisions =
                    archiveRows.map { row ->
                        check(
                            row.origin == authorityEvidence.origin && row.relayKeyId == authorityEvidence.relayKeyId &&
                                row.chainId == chainId && row.logId == authorityEvidence.logId,
                        ) { "C_RELAY_KEY_MISMATCH" }
                        check(row.signedJson.toByteArray(Charsets.UTF_8).size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) {
                            "C_LOCAL_LIMIT"
                        }
                        val archive = PallaSyncDeletionEvidenceArchive.parseStrictObject(row.signedJson)
                        check(archive.keys == setOf("delete_receipt", "evidence_bundle")) { "C_SCHEMA_VIOLATION" }
                        val receipt = archive.getValue("delete_receipt").jsonObject
                        val bundle = archive.getValue("evidence_bundle").jsonObject
                        val index =
                            receipt
                                .getValue("transparency_index")
                                .jsonPrimitive
                                .content
                                .toLong()
                        check(
                            index == row.sequence && receipt.getValue("chain_id").jsonPrimitive.content == chainId,
                        ) {
                            "C_DELETION_UNVERIFIED"
                        }
                        val proofCandidates =
                            db
                                .protocolDao()
                                .deletionProofsAtSequence(
                                    authorityEvidence.origin,
                                    authorityEvidence.relayKeyId,
                                    chainId,
                                    authorityEvidence.logId,
                                    "delete_receipt",
                                    index,
                                )
                        val proofMatches =
                            proofCandidates.any { proofText ->
                                val verified =
                                    PallaSyncCore.validateDeletionProof(
                                        proofText.toByteArray(Charsets.UTF_8),
                                        pinJson,
                                        authorityText,
                                        request,
                                    )
                                Json
                                    .parseToJsonElement(verified)
                                    .jsonObject
                                    .getValue("delete_receipt")
                                    .jsonObject == receipt
                            }
                        check(proofMatches) { "C_DELETION_UNVERIFIED" }
                        PallaSyncCore.validateDeletionEvidenceBundle(receipt.toString(), bundle.toString(), authorityText)
                        checkNotNull(PallaSyncCore.objectHash(receipt.toString())) to
                            PallaSyncDeletionEvidenceRevision(
                                transparencyIndex = index,
                                completedAtMs = receipt["completed_at_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
                                archiveJson = row.signedJson,
                            )
                    }
                verifiedRevisions
                    .groupBy { it.first }
                    .values
                    .map { sameReceipt ->
                        val merged =
                            PallaSyncDeletionEvidenceMerge.merge(
                                sameReceipt.map { revision ->
                                    PallaSyncDeletionEvidenceArchive.parseStrictObject(revision.second.archiveJson)
                                },
                            ) ?: error("C_DELETION_UNVERIFIED")
                        val first = sameReceipt.first().second
                        first.copy(archiveJson = merged.toString())
                    }.sortedWith(compareBy({ it.transparencyIndex }, { it.completedAtMs ?: Long.MIN_VALUE }))
            }
        }

    /** §17.11: use fresh mnemonic input to refresh only public deletion evidence after local erasure. */
    suspend fun refreshDeletedChainEvidence(
        chainId: String,
        mnemonic: String,
        passphrase: String,
    ): String? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val authorityEvidence =
                    db
                        .deletionEvidenceDao()
                        .retainedDeletionAuthorities()
                        .singleOrNull { it.chainId == chainId }
                        ?: return@withLock null
                val authorityText = authorityEvidence.signedJson
                check(PallaSyncCore.objectHash(authorityText) == authorityEvidence.objectHash) { "C_DELETION_UNVERIFIED" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val pin = authority.getValue("pin").jsonObject
                val origin = pin.getValue("origin").jsonPrimitive.content
                check(authorityEvidence.origin == origin && authorityEvidence.logId == pin.getValue("log_id").jsonPrimitive.content) {
                    "C_RELAY_KEY_MISMATCH"
                }
                val base =
                    when (val result = PallaSyncUrls.normalize(origin)) {
                        is PallaSyncHttpResult.Success -> result.value
                        else -> error("C_DELETION_UNVERIFIED")
                    }
                val request =
                    db
                        .protocolDao()
                        .evidenceAt(
                            authorityEvidence.origin,
                            authorityEvidence.relayKeyId,
                            chainId,
                            authorityEvidence.logId,
                            "delete_request",
                            0,
                        ).singleOrNull()
                        ?: error("C_DELETION_UNVERIFIED")
                val previousProofText =
                    checkNotNull(latestDeletionProof(chainId, authorityEvidence.logId)) { "C_DELETION_UNVERIFIED" }
                val previousProof = Json.parseToJsonElement(previousProofText).jsonObject
                val previousReceipt = previousProof.getValue("delete_receipt").jsonObject
                val root =
                    PallaSyncRootSession.beginText(
                        mnemonic,
                        passphrase,
                        PallaSyncRootSession.Operation.DELETE,
                        expectedChain = chainId,
                        expectedAdmin = authority.getValue("admin_public_key").jsonPrimitive.content,
                        requestGuard = policy::requireCurrent,
                        clock = policy.retry::correctedTime,
                    )
                root.use { session ->
                    check(session.identity.chainId == chainId) { "C_CHAIN_ID_MISMATCH" }
                    val url = PallaSyncUrls.deletion(base, chainId)
                    val token =
                        checkNotNull(session.token(origin, "GET", url.encodedPath)) { "C_RELAY_POLICY_CONSENT_REQUIRED" }
                    val response =
                        remote.execute(
                            Request
                                .Builder()
                                .url(url)
                                .header("Accept", JSON_MEDIA_TYPE.toString())
                                .header("Authorization", "PallaSync $token")
                                .get()
                                .build(),
                        ) { PallaSyncHttpResult.Success(it.pallaSyncBytes()) }
                    val proofBytes =
                        when (response) {
                            is PallaSyncHttpResult.Success -> response.value
                            else -> error("C_DELETION_UNVERIFIED")
                        }
                    val validatedText =
                        PallaSyncCore.validateDeletionProof(
                            proofBytes,
                            pin.toString(),
                            authorityText,
                            request,
                        )
                    val proof = Json.parseToJsonElement(validatedText).jsonObject
                    val receipt = proof.getValue("delete_receipt").jsonObject
                    PallaSyncCore.validateDeletionReceiptRevision(previousReceipt.toString(), receipt.toString(), pin.toString())
                    val receiptIndexValue = receipt.getValue("transparency_index").jsonPrimitive
                    val receiptIndex = receiptIndexValue.content.toLong()
                    val proofHash = checkNotNull(PallaSyncCore.objectHash(validatedText)) { "C_DELETION_UNVERIFIED" }
                    val proofEntity =
                        ProtectedEvidenceEntity(
                            origin,
                            authorityEvidence.relayKeyId,
                            chainId,
                            authorityEvidence.logId,
                            "delete_receipt",
                            receiptIndex,
                            proofHash,
                            validatedText,
                        )
                    db.protocolDao().retainDeletionProof(proofEntity)
                    val existingArchives =
                        db
                            .protocolDao()
                            .deletionEvidenceArchives(
                                origin,
                                authorityEvidence.relayKeyId,
                                chainId,
                                authorityEvidence.logId,
                                receiptIndex,
                            ).map { archiveText ->
                                val archive = PallaSyncDeletionEvidenceArchive.parseStrictObject(archiveText)
                                check(archive.keys == setOf("delete_receipt", "evidence_bundle")) { "C_DELETION_UNVERIFIED" }
                                val archivedReceipt = archive.getValue("delete_receipt").jsonObject
                                val archivedBundle = archive.getValue("evidence_bundle").jsonObject
                                check(
                                    archivedReceipt.getValue("chain_id") == receipt.getValue("chain_id") &&
                                        archivedReceipt.getValue("deletion_id") == receipt.getValue("deletion_id") &&
                                        archivedReceipt.getValue("transparency_id") == receipt.getValue("transparency_id") &&
                                        archivedReceipt.getValue("transparency_index") == receipt.getValue("transparency_index"),
                                ) { "C_DELETION_UNVERIFIED" }
                                PallaSyncCore.validateDeletionEvidenceBundle(
                                    archivedReceipt.toString(),
                                    archivedBundle.toString(),
                                    authorityText,
                                )
                                archive
                            }.filter { it.getValue("delete_receipt").jsonObject == receipt }
                    val archiveText =
                        if (existingArchives.isEmpty()) {
                            buildDeletionEvidenceArchive(receipt, authorityText)
                        } else {
                            val merged = checkNotNull(PallaSyncDeletionEvidenceMerge.merge(existingArchives)) { "C_DELETION_UNVERIFIED" }
                            val bundle = merged.getValue("evidence_bundle").jsonObject
                            PallaSyncCore.validateDeletionEvidenceBundle(receipt.toString(), bundle.toString(), authorityText)
                            merged.toString()
                        }
                    check(archiveText.toByteArray(Charsets.UTF_8).size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) {
                        "C_LOCAL_LIMIT"
                    }
                    val bundle = PallaSyncDeletionEvidenceArchive.parseStrictObject(archiveText).getValue("evidence_bundle").jsonObject
                    db.protocolDao().retainEvidence(
                        ProtectedEvidenceEntity(
                            origin,
                            authorityEvidence.relayKeyId,
                            chainId,
                            authorityEvidence.logId,
                            "deletion_evidence_bundle",
                            receiptIndex,
                            checkNotNull(PallaSyncCore.objectHash(bundle.toString())) { "C_DELETION_UNVERIFIED" },
                            archiveText,
                        ),
                    )
                    check(
                        db
                            .protocolDao()
                            .deletionEvidenceArchives(
                                origin,
                                authorityEvidence.relayKeyId,
                                chainId,
                                authorityEvidence.logId,
                                receiptIndex,
                            ).contains(archiveText),
                    ) { "C_LOCAL_STORAGE_FULL" }
                    return@withLock archiveText
                }
            }
        }

    private fun buildDeletionEvidenceArchive(
        receipt: JsonObject,
        authorityText: String,
    ): String {
        val bundle =
            buildJsonObject {
                put("type", "PALLASYNC-DELETION-EVIDENCE-v3")
                put("version", "3.0")
                put("chain_id", receipt.getValue("chain_id"))
                put("deletion_id", receipt.getValue("deletion_id"))
                put("delete_receipt_hash", checkNotNull(PallaSyncCore.objectHash(receipt.toString())))
                put("witnesses", JsonArray(emptyList()))
            }
        PallaSyncCore.validateDeletionEvidenceBundle(receipt.toString(), bundle.toString(), authorityText)
        val archive =
            buildJsonObject {
                put("delete_receipt", receipt)
                put("evidence_bundle", bundle)
            }.toString()
        check(archive.toByteArray(Charsets.UTF_8).size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) { "C_LOCAL_LIMIT" }
        return archive
    }

    /** Build and retain the §7.12.5 archive wrapper separately from the immutable receipt. */
    @Suppress("LongMethod") // Keep receipt binding, archive size enforcement, and durable evidence retention atomic.
    suspend fun pendingDeletionEvidenceArchive(): String? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val context = deletionContext() ?: return@withLock null
                val proofText = latestDeletionProof(context.state.chainId, context.logId) ?: return@withLock null
                val proof = Json.parseToJsonElement(proofText).jsonObject
                val receipt = proof.getValue("delete_receipt").jsonObject
                val index =
                    receipt
                        .getValue("transparency_index")
                        .jsonPrimitive.content
                        .toLong()
                val retained = retainedDeletionEvidenceArchive(context, receipt, index)
                if (retained != null) {
                    val archive = retained.toString()
                    check(archive.toByteArray(Charsets.UTF_8).size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) { "C_LOCAL_LIMIT" }
                    val bundle = retained.getValue("evidence_bundle").jsonObject
                    val hash = checkNotNull(PallaSyncCore.objectHash(bundle.toString())) { "C_DELETION_UNVERIFIED" }
                    db.protocolDao().retainEvidence(
                        ProtectedEvidenceEntity(
                            context.origin,
                            context.relayKeyId,
                            context.state.chainId,
                            context.logId,
                            "deletion_evidence_bundle",
                            index,
                            hash,
                            archive,
                        ),
                    )
                    check(
                        db
                            .protocolDao()
                            .deletionEvidenceArchives(
                                context.origin,
                                context.relayKeyId,
                                context.state.chainId,
                                context.logId,
                                index,
                            ).contains(archive),
                    ) { "C_LOCAL_STORAGE_FULL" }
                    return@withLock archive
                }
                val bundle =
                    buildJsonObject {
                        put("type", "PALLASYNC-DELETION-EVIDENCE-v3")
                        put("version", "3.0")
                        put("chain_id", receipt.getValue("chain_id"))
                        put("deletion_id", receipt.getValue("deletion_id"))
                        put("delete_receipt_hash", checkNotNull(PallaSyncCore.objectHash(receipt.toString())))
                        put("witnesses", JsonArray(emptyList()))
                    }
                PallaSyncCore.validateDeletionEvidenceBundle(receipt.toString(), bundle.toString(), context.authorityText)
                val archive =
                    buildJsonObject {
                        put("delete_receipt", receipt)
                        put("evidence_bundle", bundle)
                    }.toString()
                check(archive.toByteArray(Charsets.UTF_8).size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) { "C_LOCAL_LIMIT" }
                val hash = checkNotNull(PallaSyncCore.objectHash(bundle.toString())) { "C_DELETION_UNVERIFIED" }
                db.protocolDao().retainEvidence(
                    ProtectedEvidenceEntity(
                        context.origin,
                        context.relayKeyId,
                        context.state.chainId,
                        context.logId,
                        "deletion_evidence_bundle",
                        index,
                        hash,
                        archive,
                    ),
                )
                check(
                    db
                        .protocolDao()
                        .deletionEvidenceArchives(
                            context.origin,
                            context.relayKeyId,
                            context.state.chainId,
                            context.logId,
                            index,
                        ).contains(archive),
                ) { "C_LOCAL_STORAGE_FULL" }
                archive
            }
        }

    private suspend fun retainedDeletionEvidenceArchive(
        context: DeletionContext,
        receipt: JsonObject,
        index: Long,
    ): JsonObject? {
        val archives =
            db
                .protocolDao()
                .deletionEvidenceArchives(
                    context.origin,
                    context.relayKeyId,
                    context.state.chainId,
                    context.logId,
                    index,
                ).map { value ->
                    val archive = Json.parseToJsonElement(value).jsonObject
                    check(archive.keys == setOf("delete_receipt", "evidence_bundle")) { "C_DELETION_UNVERIFIED" }
                    val archivedReceipt = archive.getValue("delete_receipt").jsonObject
                    check(
                        archivedReceipt.getValue("chain_id") == receipt.getValue("chain_id") &&
                            archivedReceipt.getValue("deletion_id") == receipt.getValue("deletion_id") &&
                            archivedReceipt.getValue("transparency_id") == receipt.getValue("transparency_id") &&
                            archivedReceipt.getValue("transparency_index") == receipt.getValue("transparency_index"),
                    ) { "C_DELETION_UNVERIFIED" }
                    PallaSyncCore.validateDeletionEvidenceBundle(
                        archivedReceipt.toString(),
                        archive.getValue("evidence_bundle").toString(),
                        context.authorityText,
                    )
                    archive
                }.filter { it.getValue("delete_receipt").jsonObject == receipt }
        val merged = PallaSyncDeletionEvidenceMerge.merge(archives) ?: return null
        val mergedBundle = merged.getValue("evidence_bundle").jsonObject
        PallaSyncCore.validateDeletionEvidenceBundle(receipt.toString(), mergedBundle.toString(), context.authorityText)
        return merged
    }

    /** Local file ceremony: one active enrolled device signs one exact receipt after foreground confirmation. */
    @Suppress("LongMethod") // Keep signing, bundle validation, and protected persistence in one serialized operation.
    suspend fun addDeletionIntentWitness(
        archiveText: String,
        explicitlyConfirmed: Boolean,
    ): String? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (!explicitlyConfirmed) return@withLock null
                check(archiveText.toByteArray(Charsets.UTF_8).size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) { "C_LOCAL_LIMIT" }
                val archive = PallaSyncDeletionEvidenceArchive.parseStrictObject(archiveText)
                check(archive.keys == setOf("delete_receipt", "evidence_bundle")) { "C_SCHEMA_VIOLATION" }
                val receipt = archive.getValue("delete_receipt").jsonObject
                val oldBundle = archive.getValue("evidence_bundle").jsonObject
                check(oldBundle.getValue("chain_id").jsonPrimitive.content == receipt.getValue("chain_id").jsonPrimitive.content) {
                    "C_DELETION_UNVERIFIED"
                }
                val chain = receipt.getValue("chain_id").jsonPrimitive.content
                val state = checkNotNull(db.dao.getChainState(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(state.writeState == "active") { "C_WRITE_PAUSED" }
                val authorityText = checkNotNull(db.protocolDao().currentAuthority(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(PallaSyncCore.objectHash(authorityText) == keystore.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val keys = checkNotNull(keystore.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(keys.chainId == chain && keys.deviceId != null) { "C_DEVICE_KEY_MISMATCH" }
                validateDeletionWitnessReceiptRevision(chain, receipt, authority)
                PallaSyncCore.validateDeletionEvidenceBundle(receipt.toString(), oldBundle.toString(), authorityText)
                val witnessSeed = Base64.getUrlDecoder().decode(keys.signingKeyBase64Url)
                val witnessText =
                    try {
                        PallaSyncCore.createDeletionWitness(
                            receipt.toString(),
                            authorityText,
                            checkNotNull(keys.deviceId),
                            witnessSeed,
                            policy.retry.correctedTime(
                                authority
                                    .getValue("pin")
                                    .jsonObject
                                    .getValue("origin")
                                    .jsonPrimitive.content,
                            ),
                        )
                    } finally {
                        witnessSeed.fill(0)
                    }
                val witness = Json.parseToJsonElement(witnessText).jsonObject
                val witnessId = witness.getValue("witness_device_id").jsonPrimitive.content
                val witnesses =
                    oldBundle
                        .getValue("witnesses")
                        .jsonArray
                        .map { it.jsonObject }
                        .toMutableList()
                check(witnesses.none { it.getValue("witness_device_id").jsonPrimitive.content == witnessId }) { "C_DEVICE_EQUIVOCATION" }
                check(witnesses.size < MAX_DELETION_WITNESSES) { "C_SCHEMA_VIOLATION" }
                witnesses.add(witness)
                val nextBundle =
                    buildJsonObject {
                        put("type", oldBundle.getValue("type"))
                        put("version", oldBundle.getValue("version"))
                        put("chain_id", oldBundle.getValue("chain_id"))
                        put("deletion_id", oldBundle.getValue("deletion_id"))
                        put("delete_receipt_hash", oldBundle.getValue("delete_receipt_hash"))
                        put("witnesses", JsonArray(witnesses.sortedBy { it.getValue("witness_device_id").jsonPrimitive.content }))
                    }
                PallaSyncCore.validateDeletionEvidenceBundle(receipt.toString(), nextBundle.toString(), authorityText)
                val result =
                    buildJsonObject {
                        put("delete_receipt", receipt)
                        put("evidence_bundle", nextBundle)
                    }.toString()
                check(result.toByteArray(Charsets.UTF_8).size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) { "C_LOCAL_LIMIT" }
                val pin = authority.getValue("pin").jsonObject
                val hash = checkNotNull(PallaSyncCore.objectHash(nextBundle.toString())) { "C_DELETION_UNVERIFIED" }
                val index =
                    receipt
                        .getValue("transparency_index")
                        .jsonPrimitive.content
                        .toLong()
                db.protocolDao().retainEvidence(
                    ProtectedEvidenceEntity(
                        pin.getValue("origin").jsonPrimitive.content,
                        receipt.getValue("relay_key_id").jsonPrimitive.content,
                        chain,
                        pin.getValue("log_id").jsonPrimitive.content,
                        "deletion_evidence_bundle",
                        index,
                        hash,
                        result,
                    ),
                )
                check(
                    db
                        .protocolDao()
                        .deletionEvidenceArchives(
                            pin.getValue("origin").jsonPrimitive.content,
                            receipt.getValue("relay_key_id").jsonPrimitive.content,
                            chain,
                            pin.getValue("log_id").jsonPrimitive.content,
                            index,
                        ).contains(result),
                ) { "C_LOCAL_STORAGE_FULL" }
                result
            }
        }

    /** Validate the exact imported receipt and witnesses before the confirmation UI is shown. */
    suspend fun previewDeletionIntentWitness(archiveText: String): PallaSyncDeletionWitnessPreview? =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                check(archiveText.toByteArray(Charsets.UTF_8).size <= PallaSyncDeletionEvidenceArchive.MAX_BYTES) { "C_LOCAL_LIMIT" }
                val archive = PallaSyncDeletionEvidenceArchive.parseStrictObject(archiveText)
                check(archive.keys == setOf("delete_receipt", "evidence_bundle")) { "C_SCHEMA_VIOLATION" }
                val receipt = archive.getValue("delete_receipt").jsonObject
                val bundle = archive.getValue("evidence_bundle").jsonObject
                val chain = receipt.getValue("chain_id").jsonPrimitive.content
                check(bundle.getValue("chain_id").jsonPrimitive.content == chain) { "C_DELETION_UNVERIFIED" }
                val state = checkNotNull(db.dao.getChainState(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(state.writeState == "active") { "C_WRITE_PAUSED" }
                val authorityText = checkNotNull(db.protocolDao().currentAuthority(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(PallaSyncCore.objectHash(authorityText) == keystore.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                validateDeletionWitnessReceiptRevision(chain, receipt, authority)
                PallaSyncCore.validateDeletionEvidenceBundle(receipt.toString(), bundle.toString(), authorityText)
                PallaSyncDeletionWitnessPreview(
                    chainId = chain,
                    deletionId = receipt.getValue("deletion_id").jsonPrimitive.content,
                    receiptHash = checkNotNull(PallaSyncCore.objectHash(receipt.toString())) { "C_SCHEMA_VIOLATION" },
                    assurance = receipt.getValue("assurance").jsonPrimitive.content,
                    requestedAtMs = receipt.getValue("requested_at_ms").jsonPrimitive.content,
                    existingWitnessCount = bundle.getValue("witnesses").jsonArray.size,
                )
            }
        }

    /** Keep optional witnesses bound to the latest locally verified monotone receipt revision (§7.12.3/§17.11). */
    private suspend fun validateDeletionWitnessReceiptRevision(
        chainId: String,
        receipt: JsonObject,
        authority: JsonObject,
    ) {
        val pin = authority.getValue("pin").jsonObject
        check(receipt.getValue("chain_id").jsonPrimitive.content == chainId) { "C_DELETION_UNVERIFIED" }
        check(receipt.getValue("log_id").jsonPrimitive.content == pin.getValue("log_id").jsonPrimitive.content) {
            "C_DELETION_UNVERIFIED"
        }
        val latestProof =
            db
                .deletionEvidenceDao()
                .latestReceiptRevision(chainId, pin.getValue("log_id").jsonPrimitive.content)
                ?: return
        val latestReceipt =
            Json
                .parseToJsonElement(latestProof)
                .jsonObject
                .getValue("delete_receipt")
                .jsonObject
        check(latestReceipt.getValue("deletion_id") == receipt.getValue("deletion_id")) { "C_DELETION_UNVERIFIED" }
        if (latestReceipt != receipt) {
            PallaSyncCore.validateDeletionReceiptRevision(latestReceipt.toString(), receipt.toString(), pin.toString())
        }
    }

    suspend fun completeDeletionAfterExport(
        exportedProof: String,
        explicitlyConfirmedOfflineCopy: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (!explicitlyConfirmedOfflineCopy) return@withLock false
                try {
                    val context = deletionContext() ?: return@withLock false
                    val savedProof = db.protocolDao().latestEvidence(context.state.chainId, context.logId, "delete_receipt")
                    check(savedProof == exportedProof) { "C_DELETION_UNVERIFIED" }
                    val request =
                        checkNotNull(db.protocolDao().latestEvidence(context.state.chainId, context.logId, "delete_request")) {
                            "C_DELETION_UNVERIFIED"
                        }
                    val evidence = validateDeletionProof(context, request, exportedProof.toByteArray(Charsets.UTF_8))
                    persistAndErase(context, evidence)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: IllegalStateException) {
                    deletionFailure(failure)
                    false
                } catch (failure: IllegalArgumentException) {
                    deletionFailure(failure)
                    false
                } catch (failure: SQLiteException) {
                    deletionFailure(failure)
                    false
                }
            }
        }

    private fun eraseChainRecoveryMaterial(chain: String) {
        val slot = PallaSyncRecoverySlot(appContext)
        slot.eraseForVerifiedChainDeletion(chain)?.let { (alias, setId) ->
            PallaSyncRecoveryKeyProtector(appContext).delete(alias)
            slot.keyErased(alias)
            slot.discardDeletedChainPublicEvidence(setId)
        }
        PallaSyncRecoverySetStore(appContext).eraseChain(chain)
        recoveryAudits.eraseChain(chain)
        PallaSyncAuthorJournal(appContext).eraseChain(chain)
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun deletionFailure(failure: Exception): PallaSyncDeletionRequestResult {
        val code =
            failure.message?.let { Regex("C_[A-Z_]+").find(it)?.value }
                ?: "C_DELETION_UNVERIFIED"
        log("$code: deletion could not be verified or erased; recoverable local data retained")
        return PallaSyncDeletionRequestResult.FAILED
    }

    suspend fun fetchDevices(
        serverUrl: String,
        chainId: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val baseUrl =
                    when (val normalized = PallaSyncUrls.normalize(serverUrl)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        is PallaSyncHttpResult.ProtocolError -> return@withLock false.also { log(normalized.message) }
                        else -> return@withLock false
                    }
                when (val result = fetchDevicesLocked(baseUrl, chainId)) {
                    is PallaSyncHttpResult.Success -> {
                        true
                    }

                    PallaSyncHttpResult.Gone -> {
                        false.also {
                            if (db.pallaSyncDao().getActiveChainState()?.chainId == chainId) {
                                goneLocked()
                            }
                        }
                    }

                    is PallaSyncHttpResult.Retryable -> {
                        false.also { log(result.message) }
                    }

                    is PallaSyncHttpResult.ProtocolError -> {
                        false.also { log(result.message) }
                    }
                }
            }
        }
    }

    /** Compatibility entry point; accepted rows are now deleted instead of retained forever. */
    suspend fun processOutbox(serverUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = db.pallaSyncDao().getActiveChainStates().singleOrNull() ?: return@withLock true
                val baseUrl =
                    when (val normalized = PallaSyncUrls.normalize(serverUrl)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> return@withLock false
                    }
                when (val result = processOutboxLocked(baseUrl, state.chainId)) {
                    is PallaSyncHttpResult.Success -> true
                    PallaSyncHttpResult.Gone -> false.also { goneLocked() }
                    is PallaSyncHttpResult.Retryable -> false.also { log(result.message) }
                    is PallaSyncHttpResult.ProtocolError -> false.also { log(result.message) }
                }
            }
        }
    }

    suspend fun pushRecords(
        serverUrl: String,
        chainId: String,
        recordsJsonArray: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (db.dao.getChainState(chainId)?.writeState != "active") return@withLock false
                val baseUrl =
                    when (val normalized = PallaSyncUrls.normalize(serverUrl)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> return@withLock false
                    }
                when (val health = refreshRelayHealth(baseUrl)) {
                    is PallaSyncHttpResult.Success -> Unit
                    else -> return@withLock false
                }
                val records =
                    runCatching { json.parseToJsonElement(recordsJsonArray).jsonArray }.getOrNull()
                        ?: return@withLock false
                val objects = records.map { it.jsonObject }
                val submitted = objects.associateBy { it["record_id"]!!.jsonPrimitive.content }
                if (!validSubmittedBatch(objects, submitted)) return@withLock false
                val body = "{\"records\":$records}"
                val path = "/pallasync/v3/chains/$chainId/records"
                val token =
                    makeCapabilityToken(baseUrl, chainId, "POST", path, "", body)
                        ?: return@withLock false
                val request =
                    Request
                        .Builder()
                        .url(PallaSyncUrls.recordsEndpoint(baseUrl, chainId))
                        .header("Accept", JSON_MEDIA_TYPE.toString())
                        .header("Authorization", "PallaSync $token")
                        .post(body.toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                when (val result = executeRecordUpload(request, submitted)) {
                    is PallaSyncHttpResult.Success -> {
                        val proof = proveUploadOutcome(baseUrl, chainId, result.value)
                        proof is PallaSyncHttpResult.Success && result.value.rejected.isEmpty()
                    }

                    PallaSyncHttpResult.Gone -> {
                        unverifiedUploadDeletion(chainId)
                    }

                    else -> {
                        false
                    }
                }
            }
        }
    }

    private suspend fun unverifiedUploadDeletion(chainId: String): Boolean {
        if (db.dao.getActiveChainState()?.chainId == chainId) goneLocked()
        return false
    }

    private suspend fun synchronizeOnceLocked(baseUrlOverride: HttpUrl? = null): SyncCycleOutcome =
        GlitchTipTelemetry.traceAsync("pallasync.sync", "sync.operation") { tx ->
            val dao = db.pallaSyncDao()
            val chainState =
                activeChainStateLocked() ?: run {
                    // An accepted or uncertain remote candidate survives a missing Room commit.
                    disableOrphanedEnabledFlag()
                    return@traceAsync SyncCycleOutcome.Idle
                }
            val baseUrl =
                baseUrlOverride ?: when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                    is PallaSyncHttpResult.Success -> {
                        normalized.value
                    }

                    is PallaSyncHttpResult.ProtocolError -> {
                        log(normalized.message)
                        return@traceAsync SyncCycleOutcome.ProtocolError
                    }

                    else -> {
                        return@traceAsync SyncCycleOutcome.ProtocolError
                    }
                }
            if (keysForChainLocked(chainState.chainId) == null) {
                log("Active PallaSync keys do not match the Room chain state")
                return@traceAsync SyncCycleOutcome.ProtocolError
            }
            if (policy.retry.remaining(PallaSyncUrls.extractOrigin(baseUrl)) > 0) return@traceAsync SyncCycleOutcome.Retryable

            PallaSyncAuthorityCommit(db, keystore).recover(chainState.chainId)
            if (db.dao.getChainState(chainState.chainId)?.writeState == "epoch_rebase_required") {
                outboxRebase.resume(chainState.chainId, checkNotNull(keysForChainLocked(chainState.chainId)))
            }
            if (chainState.writeState == "active") {
                localAuthor.append(chainState.chainId, checkNotNull(keysForChainLocked(chainState.chainId)), emptyList())
            }

            if (android.os.SystemClock.elapsedRealtime() >= nextProbeAt ||
                foregroundRevision != PallaSyncSecretLifecycle.foregroundRevision
            ) {
                when (val checked = consistencyProbe.run(baseUrl, chainState.chainId)) {
                    is PallaSyncHttpResult.Success -> {
                        nextProbeAt =
                            android.os.SystemClock.elapsedRealtime() + PROBE_INTERVAL_MS +
                            kotlin.random.Random.nextLong(
                                PROBE_JITTER_MS + 1,
                            )
                        foregroundRevision = PallaSyncSecretLifecycle.foregroundRevision
                    }

                    PallaSyncHttpResult.Gone -> {
                        return@traceAsync goneLocked()
                    }

                    is PallaSyncHttpResult.Retryable -> {
                        return@traceAsync SyncCycleOutcome.Retryable.also { log(checked.message) }
                    }

                    is PallaSyncHttpResult.ProtocolError -> {
                        return@traceAsync SyncCycleOutcome.ProtocolError.also { log(checked.message) }
                    }
                }
            }

            // Device validation/self-heal must happen before old queued records are
            // uploaded, otherwise a missing relay-side device can block forever on 400.
            val devices = tx.span("sync.fetch_devices") { fetchDevicesLocked(baseUrl, chainState.chainId) }
            when (devices) {
                is PallaSyncHttpResult.Success -> Unit
                PallaSyncHttpResult.Gone -> return@traceAsync goneLocked()
                is PallaSyncHttpResult.Retryable -> return@traceAsync SyncCycleOutcome.Retryable.also { log(devices.message) }
                is PallaSyncHttpResult.ProtocolError -> return@traceAsync SyncCycleOutcome.ProtocolError.also { log(devices.message) }
            }
            resumeLocalIntentsLocked()
            val pushed = tx.span("sync.process_outbox") { processOutboxLocked(baseUrl, chainState.chainId) }
            when (pushed) {
                is PallaSyncHttpResult.Success -> Unit
                PallaSyncHttpResult.Gone -> return@traceAsync goneLocked()
                is PallaSyncHttpResult.Retryable -> return@traceAsync SyncCycleOutcome.Retryable.also { log(pushed.message) }
                is PallaSyncHttpResult.ProtocolError -> return@traceAsync SyncCycleOutcome.ProtocolError.also { log(pushed.message) }
            }
            val fullPrefix = liveHistory.pull(baseUrl, chainState.chainId, checkNotNull(keysForChainLocked(chainState.chainId)))
            when (fullPrefix) {
                is PallaSyncHttpResult.Success -> {
                    epochStatus.read(chainState.chainId)
                    projection.publish(chainState.chainId, db)
                }

                PallaSyncHttpResult.Gone -> {
                    return@traceAsync goneLocked()
                }

                is PallaSyncHttpResult.Retryable -> {
                    return@traceAsync SyncCycleOutcome.Retryable.also { log(fullPrefix.message) }
                }

                is PallaSyncHttpResult.ProtocolError -> {
                    return@traceAsync SyncCycleOutcome.ProtocolError.also { log(fullPrefix.message) }
                }
            }
            val pulled = tx.span("sync.pull_records") { pullRecordPagesLocked(baseUrl, chainState.chainId) }
            when (pulled) {
                is PallaSyncHttpResult.Success -> Unit
                PallaSyncHttpResult.Gone -> return@traceAsync goneLocked()
                is PallaSyncHttpResult.Retryable -> return@traceAsync SyncCycleOutcome.Retryable.also { log(pulled.message) }
                is PallaSyncHttpResult.ProtocolError -> return@traceAsync SyncCycleOutcome.ProtocolError.also { log(pulled.message) }
            }

            val activatedInitialEvents =
                if (chainState.initialPullCompleted) {
                    0
                } else {
                    dao.completeInitialPullAndQueueEvents(chainState.chainId)
                }
            if (!chainState.initialPullCompleted) {
                log("Queued $activatedInitialEvents local item(s) after the first successful pull")
                when (val pushedAfterInit = processOutboxLocked(baseUrl, chainState.chainId)) {
                    is PallaSyncHttpResult.Success -> Unit

                    PallaSyncHttpResult.Gone -> return@traceAsync goneLocked()

                    is PallaSyncHttpResult.Retryable -> return@traceAsync SyncCycleOutcome.Retryable.also { log(pushedAfterInit.message) }

                    is PallaSyncHttpResult.ProtocolError -> return@traceAsync SyncCycleOutcome.ProtocolError.also {
                        log(
                            pushedAfterInit.message,
                        )
                    }
                }
                // Apply the just-merged local items immediately so the UI does not
                // temporarily show only the remote pre-join snapshot.
                when (val pulledAfterInit = pullRecordPagesLocked(baseUrl, chainState.chainId)) {
                    is PallaSyncHttpResult.Success -> Unit

                    PallaSyncHttpResult.Gone -> return@traceAsync goneLocked()

                    is PallaSyncHttpResult.Retryable -> return@traceAsync SyncCycleOutcome.Retryable.also { log(pulledAfterInit.message) }

                    is PallaSyncHttpResult.ProtocolError -> return@traceAsync SyncCycleOutcome.ProtocolError.also {
                        log(
                            pulledAfterInit.message,
                        )
                    }
                }
            }
            policy.retry.progress(PallaSyncUrls.extractOrigin(baseUrl))
            expireHistoricalEpochKeys(chainState.chainId, PallaSyncUrls.extractOrigin(baseUrl))
            SyncCycleOutcome.Success
        }

    private suspend fun expireHistoricalEpochKeys(
        chain: String,
        origin: String,
    ) {
        val authorityText = db.protocolDao().currentAuthority(chain) ?: return
        val authority = Json.parseToJsonElement(authorityText).jsonObject
        val expired =
            PallaSyncRetentionHandoff(db, keystore::expireRetainedEpochSecrets).expireAfterHandoff(
                chain,
                authority,
                policy.retry.correctedTime(origin),
            )
        if (expired.isNotEmpty()) {
            log(
                "Expired cached keys for historical epochs ${expired.joinToString()}; the verified current-epoch snapshot preserves materialized state, while raw old-epoch archive replay is no longer available",
            )
        }
    }

    private fun refreshRelayHealth(baseUrl: HttpUrl): PallaSyncHttpResult<Unit> {
        val request =
            Request
                .Builder()
                .url(PallaSyncUrls.health(baseUrl))
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .get()
                .build()
        return when (
            val result =
                remote.execute(request) { response ->
                    if (response.header("Content-Type")?.substringBefore(';')?.trim() != PALLASYNC_MEDIA_TYPE) {
                        return@execute PallaSyncHttpResult.ProtocolError("PallaSync health response used an unexpected media type")
                    }
                    val body = PallaSyncCore.validateHealth(response.pallaSyncBytes(), PallaSyncUrls.extractOrigin(baseUrl))
                    parseHealthResponseBody(body, PallaSyncUrls.extractOrigin(baseUrl))
                }
        ) {
            is PallaSyncHttpResult.Success -> {
                relayLimits = result.value.limits
                policy.retry.observeHealth(
                    PallaSyncUrls.extractOrigin(baseUrl),
                    result.value.shutdownAtMs,
                    result.value.migrationOrigin,
                )
                PallaSyncHttpResult.Success(Unit)
            }

            PallaSyncHttpResult.Gone -> {
                PallaSyncHttpResult.ProtocolError("Relay health unavailable")
            }

            is PallaSyncHttpResult.Retryable -> {
                result
            }

            is PallaSyncHttpResult.ProtocolError -> {
                result
            }
        }
    }

    private suspend fun resolvePruneCompaction(
        chainId: String,
        entry: JsonObject,
    ): JsonObject? {
        val authorityText = db.protocolDao().currentAuthority(chainId) ?: return null
        val authority = Json.parseToJsonElement(authorityText).jsonObject
        return PallaSyncRetentionHandoff(db)
            .publishedCompactionForPruneEntry(chainId, authority, entry)
    }

    private fun makeCapabilityToken(
        baseUrl: HttpUrl,
        chainId: String,
        method: String,
        path: String,
        query: String = "",
        bodyJson: String = "",
    ): String? {
        val keys = keysForChainLocked(chainId)
        val deviceId = keys?.deviceId ?: return null
        val aud = PallaSyncUrls.extractOrigin(baseUrl)
        return crypto.createCapabilityToken(
            chainId = chainId,
            deviceId = deviceId,
            method = method,
            path = path,
            query = query,
            bodyJson = bodyJson,
            signingKeyBase64 = keys.signingKeyBase64Url,
            ttlMs = 120_000L,
            aud = aud,
            signerKind = "device",
        )
    }

    private suspend fun executeRecordUpload(
        request: Request,
        submitted: Map<String, JsonObject>,
    ): PallaSyncHttpResult<PallaSyncPostRecordsResponse> {
        val chainId =
            submitted.values
                .firstOrNull()
                ?.get("chain_id")
                ?.jsonPrimitive
                ?.content
        val state =
            chainId?.let { db.pallaSyncDao().getChainState(it) }
        val logId = state?.logId
        if (chainId == null || logId == null) {
            return PallaSyncHttpResult.ProtocolError(
                if (chainId == null) "C_SCHEMA_VIOLATION" else "C_BOOTSTRAP_TRUST_REQUIRED",
            )
        }
        val origin = PallaSyncUrls.extractOrigin(request.url)
        val pin =
            buildJsonObject {
                put("origin", origin)
                put("chain_id", chainId)
                put("public_key", policy.trustedRelayKey(origin))
                put("log_id", logId)
            }.toString()
        return remote.execute(request) { response ->
            val body =
                response.pallaSyncBytes()
            val verified = PallaSyncCore.validateRecordUpload(body, JsonArray(submitted.values.toList()).toString(), pin)
            parseRecordUploadResponseBody(json, verified, submitted)
        }
    }

    private suspend fun processOutboxLocked(
        baseUrl: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<Unit> {
        PallaSyncAuthorityCommit(db, keystore).recover(chainId)
        if (db.dao.getChainState(chainId)?.writeState != "active") return PallaSyncHttpResult.ProtocolError("C_MISSING_DEPENDENCY")
        val currentKeys = keysForChainLocked(chainId) ?: return PallaSyncHttpResult.ProtocolError("C_BOOTSTRAP_TRUST_REQUIRED")
        val verified = liveHistory.pull(baseUrl, chainId, currentKeys)
        if (verified !is PallaSyncHttpResult.Success) return verified
        if (db.dao.getChainState(chainId)?.writeState != "active") return PallaSyncHttpResult.ProtocolError("C_MISSING_DEPENDENCY")
        when (val health = refreshRelayHealth(baseUrl)) {
            is PallaSyncHttpResult.Success -> Unit
            else -> return health
        }
        // §11.1: one bounded pass. A later cycle resumes remaining immutable objects.
        val events = db.dao.queuedBatch(chainId, PALLASYNC_PAGE_SIZE)
        var offset = 0
        while (offset < events.size) {
            val count = recordUploadBatchSize(events.drop(offset).map { it.eventJson }, relayLimits)
            if (count == 0) return PallaSyncHttpResult.ProtocolError("C_LOCAL_LIMIT")
            val result = uploadOutboxBatch(baseUrl, chainId, events.drop(offset).take(count))
            if (result !is PallaSyncHttpResult.Success) return result
            offset += count
        }
        return PallaSyncHttpResult.Success(Unit)
    }

    private suspend fun uploadOutboxBatch(
        base: HttpUrl,
        chain: String,
        events: List<OutboxEntity>,
    ): PallaSyncHttpResult<Unit> {
        val objects = events.map { json.parseToJsonElement(it.eventJson).jsonObject }
        val submitted = objects.associateBy { it.getValue("record_id").jsonPrimitive.content }
        check(validSubmittedBatch(objects, submitted)) { "C_SCHEMA_VIOLATION" }
        val body = "{\"records\":${JsonArray(objects)}}"
        val url = PallaSyncUrls.recordsEndpoint(base, chain)
        val token = checkNotNull(makeCapabilityToken(base, chain, "POST", url.encodedPath, "", body)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $token")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        return when (val result = executeRecordUpload(request, submitted)) {
            is PallaSyncHttpResult.Success -> {
                val proof = proveUploadOutcome(base, chain, result.value)
                if (proof is PallaSyncHttpResult.Success) {
                    val byId = events.zip(objects).associate { (event, obj) -> obj.getValue("record_id").jsonPrimitive.content to event }
                    val reconciled = reconcileUploadOutcome(result.value, byId, PallaSyncUrls.extractOrigin(base))
                    if (reconciled is PallaSyncHttpResult.Retryable) remote.defer(request, reconciled)
                    reconciled
                } else {
                    proof
                }
            }

            PallaSyncHttpResult.Gone -> {
                PallaSyncHttpResult.Gone
            }

            is PallaSyncHttpResult.Retryable -> {
                result.also { nextProbeAt = 0L }
            }

            is PallaSyncHttpResult.ProtocolError -> {
                result
            }
        }
    }

    private suspend fun proveUploadOutcome(
        base: HttpUrl,
        chain: String,
        outcome: PallaSyncPostRecordsResponse,
    ): PallaSyncHttpResult<Unit> {
        retainUploadEvidence(outcome, chain, PallaSyncUrls.extractOrigin(base))
        val proof = consistencyProbe.run(base, chain)
        if (proof !is PallaSyncHttpResult.Success) return proof
        return try {
            verifyUploadInclusions(outcome, chain, PallaSyncUrls.extractOrigin(base))
            PallaSyncHttpResult.Success(Unit)
        } catch (error: IllegalStateException) {
            val code = error.message?.takeIf { it.startsWith("C_") } ?: "C_ACCEPTANCE_UNPROVEN"
            if (code == "C_TRANSPARENCY_INVALID") db.dao.pauseIntegrityWrites(chain)
            PallaSyncHttpResult.ProtocolError(code)
        }
    }

    private fun validSubmittedBatch(
        objects: List<JsonObject>,
        submitted: Map<String, JsonObject>,
    ): Boolean =
        objects.isNotEmpty() && submitted.size == objects.size &&
            recordUploadBatchSize(objects.map { it.toString() }, relayLimits) == objects.size

    private suspend fun reconcileUploadOutcome(
        outcome: PallaSyncPostRecordsResponse,
        eventsById: Map<String, OutboxEntity>,
        origin: String,
    ): PallaSyncHttpResult<Unit> {
        val dao = db.pallaSyncDao()
        val receipts = outcome.uploadReceipts.associateBy { it.getValue("object_hash").jsonPrimitive.content }
        verifyUploadInclusions(outcome, eventsById.values.first().chainId, origin)
        db.transaction {
            outcome.accepted.forEach { accepted ->
                val submitted = eventsById.getValue(accepted.recordId)
                val hash = submitted.objectHash ?: error("C_ACCEPTANCE_UNPROVEN")
                check(submitted.recordId == accepted.recordId && crypto.objectHash(submitted.eventJson) == hash) {
                    "C_ACCEPTANCE_UNPROVEN"
                }
                val receipt = receipts.getValue(hash)
                val receiptJson = receipt.toString()
                val evidence =
                    ProtectedEvidenceEntity(
                        origin = origin,
                        relayKeyId = receipt.getValue("relay_key_id").jsonPrimitive.content,
                        chainId = submitted.chainId,
                        logId = outcome.logId,
                        kind = "upload_receipt",
                        sequence = accepted.receipt.relaySeq,
                        objectHash = crypto.objectHash(receiptJson) ?: error("C_SCHEMA_VIOLATION"),
                        signedJson = receiptJson,
                    )
                db.protocolDao().retainEvidence(evidence)
                localIntents.confirmMigrationReceipt(
                    submitted.chainId,
                    accepted.recordId,
                    hash,
                    checkNotNull(crypto.objectHash(receiptJson)) { "C_SCHEMA_VIOLATION" },
                )
                val present = dao.outboxEvent(submitted.id)
                if (present != null) {
                    check(present == submitted) { "C_RECORD_ID_CONFLICT" }
                    check(dao.removeReceiptConfirmedOutbox(submitted.id, submitted.chainId, accepted.recordId, hash) == 1) {
                        "C_ACCEPTANCE_UNPROVEN"
                    }
                }
            }
        }
        val deferred = outcome.rejected.filter { it.status !in PALLASYNC_AUTOMATIC_RETRY_STATUSES }
        deferred.forEach {
            dao.updateOutboxEvent(eventsById.getValue(it.recordId).copy(status = "awaiting_reconciliation:${it.code}"))
        }
        if (outcome.accepted.isNotEmpty()) log("Uploaded ${outcome.accepted.size} accepted sync record(s)")
        val error = outcome.rejected.firstOrNull { it !in deferred } ?: outcome.rejected.firstOrNull()
        return if (error == null) {
            PallaSyncHttpResult.Success(Unit)
        } else {
            checkNotNull(classifyPallaSyncHttpStatus(error.status, PallaSyncProblemHint(error.status, error.code, error.retryable)))
        }
    }

    private suspend fun retainUploadEvidence(
        outcome: PallaSyncPostRecordsResponse,
        chainId: String,
        origin: String,
    ) {
        outcome.uploadReceipts.forEach { receipt ->
            db.protocolDao().retainEvidence(
                ProtectedEvidenceEntity(
                    origin,
                    receipt.getValue("relay_key_id").jsonPrimitive.content,
                    chainId,
                    outcome.logId,
                    "upload_receipt",
                    receipt
                        .getValue("relay_seq")
                        .jsonPrimitive.content
                        .toLong(),
                    checkNotNull(crypto.objectHash(receipt.toString())),
                    receipt.toString(),
                ),
            )
            db.protocolDao().retainEvidence(
                ProtectedEvidenceEntity(
                    origin,
                    receipt.getValue("relay_key_id").jsonPrimitive.content,
                    chainId,
                    outcome.logId,
                    "record_acceptance",
                    receipt.getValue("relay_seq").jsonPrimitive.long,
                    receipt.getValue("object_hash").jsonPrimitive.content,
                    receipt.toString(),
                ),
            )
        }
    }

    private suspend fun verifyUploadInclusions(
        outcome: PallaSyncPostRecordsResponse,
        chainId: String,
        origin: String,
    ) {
        val anchor = checkNotNull(keystore.transparencyPin(chainId)) { "C_ACCEPTANCE_UNPROVEN" }
        val pin =
            buildJsonObject {
                put("origin", origin)
                put("chain_id", chainId)
                put("public_key", policy.trustedRelayKey(origin))
                put("log_id", outcome.logId)
            }.toString()
        outcome.uploadReceipts.forEach { receipt ->
            val items =
                db.protocolDao().evidenceAt(
                    origin,
                    receipt.getValue("relay_key_id").jsonPrimitive.content,
                    chainId,
                    outcome.logId,
                    "transparency",
                    receipt
                        .getValue("transparency_index")
                        .jsonPrimitive.content
                        .toLong(),
                )
            check(items.size == 1) { "C_ACCEPTANCE_UNPROVEN" }
            check(
                PallaSyncCore.validateReceiptInclusion(receipt.toString(), items.single(), pin, anchor.toString()),
            ) { "C_TRANSPARENCY_INVALID" }
        }
    }

    private suspend fun fetchDevicesLocked(
        baseUrl: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<Unit> {
        val path = "/pallasync/v3/chains/$chainId/devices"
        val registry = mutableListOf<PallaSyncRegistryEntry>()
        var cursor: String? = null
        val seenCursors = mutableSetOf<String>()
        var expectedLogId: String? = null
        do {
            val url = PallaSyncUrls.devices(baseUrl, chainId, cursor, minOf(PALLASYNC_PAGE_SIZE, relayLimits.pageLimit))
            val token =
                makeCapabilityToken(baseUrl, chainId, "GET", path, url.encodedQuery.orEmpty(), "")
                    ?: return PallaSyncHttpResult.ProtocolError("Active PallaSync keys are missing")
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", JSON_MEDIA_TYPE.toString())
                    .header("Authorization", "PallaSync $token")
                    .get()
                    .build()
            val page =
                when (
                    val result =
                        remote.execute(request) { response ->
                            val body =
                                response.pallaSyncText()
                            parseDevicesResponseBody(json, body)
                        }
                ) {
                    is PallaSyncHttpResult.Success -> result.value
                    PallaSyncHttpResult.Gone -> return PallaSyncHttpResult.Gone
                    is PallaSyncHttpResult.Retryable -> return result
                    is PallaSyncHttpResult.ProtocolError -> return result
                }
            if (expectedLogId != null && page.logId != expectedLogId) {
                return PallaSyncHttpResult.ProtocolError("Device registry changed log while paging")
            }
            expectedLogId = page.logId
            registry += page.items
            cursor = page.nextCursor
            if (cursor != null && !seenCursors.add(cursor)) {
                return PallaSyncHttpResult.ProtocolError("Device registry repeated its cursor")
            }
        } while (cursor != null)

        val dao = db.pallaSyncDao()
        val keys =
            keysForChainLocked(chainId)
                ?: return PallaSyncHttpResult.ProtocolError("Active PallaSync keys are missing")
        val myDeviceId = keys.deviceId
        val authority =
            db.protocolDao().currentAuthority(chainId)
                ?: return PallaSyncHttpResult.ProtocolError("C_BOOTSTRAP_TRUST_REQUIRED")
        if (keystore.authorityHash(chainId) != crypto.objectHash(authority)) {
            return PallaSyncHttpResult.ProtocolError("C_ROLLBACK_DETECTED")
        }
        val activeDeviceIds = mutableSetOf<String>()
        var shouldSelfHeal = false

        registry.forEach { entry ->
            val rawDevice = entry.deviceRecord.toString()
            val verified =
                runCatching { json.parseToJsonElement(PallaSyncCore.validateKnownDevice(authority, rawDevice)).jsonObject }
                    .getOrElse {
                        val code = it.message?.takeIf { message -> message.matches(Regex("C_[A-Z_]+")) } ?: "C_SCHEMA_VIOLATION"
                        crypto.objectHash(rawDevice)?.let { hash ->
                            db.protocolDao().quarantine(QuarantineEvidenceEntity(chainId, hash, code, rawDevice, "unresolved"))
                        }
                        return@forEach
                    }
            val device =
                runCatching { json.decodeFromString<PallaSyncWireDevice>(rawDevice) }
                    .getOrElse {
                        log("Ignored a malformed device record")
                        return@forEach
                    }
            if (device.chainId != chainId || device.protocolVersion != PALLASYNC_PROTOCOL_VERSION) {
                log("Ignored a device record for the wrong chain or protocol")
                return@forEach
            }
            if (verified["revoked"]?.jsonPrimitive?.content == "true") {
                if (device.deviceId == myDeviceId) return PallaSyncHttpResult.ProtocolError("The active device is revoked; sync paused")
                return@forEach
            }
            if (!runCatching { crypto.verifyDeviceRecord(rawDevice) }.getOrDefault(false)) {
                log("C_SIGNATURE_INVALID")
                if (device.deviceId == myDeviceId) shouldSelfHeal = true
                crypto.objectHash(rawDevice)?.let { hash ->
                    db.protocolDao().quarantine(QuarantineEvidenceEntity(chainId, hash, "C_SIGNATURE_INVALID", rawDevice, "unresolved"))
                }
                return@forEach
            }
            activeDeviceIds += device.deviceId

            val decryptedName =
                runCatching {
                    PallaSyncEpochSession
                        .begin(
                            checkNotNull(keystore.keysForEpoch(keys, device.metaEpoch)) {
                                "C_MISSING_DEPENDENCY"
                            },
                            authority,
                            device.metaEpoch,
                        ).use {
                            json.decodeFromString<PallaSyncDeviceMetaPlaintext>(it.decryptDevice(rawDevice)).name
                        }
                }.getOrNull()
            if (decryptedName.isNullOrBlank() && device.deviceId == myDeviceId) {
                shouldSelfHeal = true
            }
            dao.insertDevice(
                PallaSyncDeviceEntity(
                    deviceId = device.deviceId,
                    chainId = chainId,
                    deviceName = decryptedName ?: "Device ${device.deviceId.take(4)}",
                    publicKey = device.devicePublicKey,
                    joinedAtMs = device.createdAtMs,
                ),
            )
        }

        if (myDeviceId != null && myDeviceId !in activeDeviceIds) shouldSelfHeal = true
        // §18.4: a mutable registry omission cannot erase historical verification keys.

        if (shouldSelfHeal && myDeviceId != null) {
            log("Device registration mismatch detected for this device ($myDeviceId)")
        }
        return PallaSyncHttpResult.Success(Unit)
    }

    private suspend fun pullRecordPagesLocked(
        baseUrl: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<Unit> {
        var afterSeq = db.pallaSyncDao().getChainState(chainId)?.lastRelaySeq ?: 0L
        do {
            val page =
                when (val pageResult = fetchRecordsPage(baseUrl, chainId, afterSeq)) {
                    is PallaSyncHttpResult.Success -> pageResult.value
                    PallaSyncHttpResult.Gone -> return PallaSyncHttpResult.Gone
                    is PallaSyncHttpResult.Retryable -> return pageResult
                    is PallaSyncHttpResult.ProtocolError -> return pageResult
                }
            if (
                page.nextSeq < afterSeq ||
                (page.nextSeq == afterSeq && (page.hasMore || page.records.isNotEmpty()))
            ) {
                return PallaSyncHttpResult.ProtocolError("Relay returned a non-advancing page cursor")
            }

            val pageApplied = applyRecordPageLocked(chainId, page)
            if (pageApplied !is PallaSyncHttpResult.Success) return pageApplied
            afterSeq = page.nextSeq
        } while (page.hasMore)
        return PallaSyncHttpResult.Success(Unit)
    }

    private suspend fun fetchRecordsPage(
        baseUrl: HttpUrl,
        chainId: String,
        afterSeq: Long,
    ): PallaSyncHttpResult<PallaSyncRecordsPage> {
        val state =
            db.pallaSyncDao().getChainState(chainId)
        val logId = state?.logId
        if (state == null || logId == null) return PallaSyncHttpResult.ProtocolError("C_BOOTSTRAP_TRUST_REQUIRED")
        val origin = PallaSyncUrls.extractOrigin(baseUrl)
        val pin =
            buildJsonObject {
                put("origin", origin)
                put("chain_id", chainId)
                put("public_key", policy.trustedRelayKey(origin))
                put("log_id", logId)
            }.toString()
        val path = "/pallasync/v3/chains/$chainId/records"
        val query = "after_seq=$afterSeq&limit=$PALLASYNC_PAGE_SIZE"
        val token = makeCapabilityToken(baseUrl, chainId, "GET", path, query, "")
        val requestBuilder =
            Request
                .Builder()
                .url(PallaSyncUrls.records(baseUrl, chainId, null, afterSeq, PALLASYNC_PAGE_SIZE))
                .header("Accept", JSON_MEDIA_TYPE.toString())
        if (token != null) {
            requestBuilder.header("Authorization", "PallaSync $token")
        }
        val request = requestBuilder.get().build()
        return remote.execute(request) { response ->
            val validated =
                PallaSyncCore.validateRecordsPage(
                    response.pallaSyncBytes(),
                    pin,
                    state.generation,
                    afterSeq,
                    PALLASYNC_PAGE_SIZE,
                )
            parseRecordsResponseBody(json, validated, afterSeq)
        }
    }

    private suspend fun applyRecordPageLocked(
        chainId: String,
        page: PallaSyncRecordsPage,
    ): PallaSyncHttpResult<Unit> {
        val dao = db.pallaSyncDao()
        val keys =
            keysForChainLocked(chainId)
                ?: return PallaSyncHttpResult.ProtocolError("Active PallaSync keys are missing")
        val inboxRecords = mutableListOf<PallaSyncInboxEntity>()
        val decryptedCandidates = mutableListOf<DecryptedCandidate>()
        val pageRecordIds = mutableSetOf<String>()
        val authority =
            db.protocolDao().currentAuthority(chainId)
                ?: return PallaSyncHttpResult.ProtocolError("C_BOOTSTRAP_TRUST_REQUIRED")
        if (keystore.authorityHash(chainId) != crypto.objectHash(authority)) return PallaSyncHttpResult.ProtocolError("C_ROLLBACK_DETECTED")

        page.records.forEach { pageRecord ->
            val wire = pageRecord.wireRecord
            val recordId = wire?.recordId ?: malformedRecordId(pageRecord.rawJson)
            if (!pageRecordIds.add(recordId)) return@forEach
            val previous = dao.getInboxRecord(chainId, recordId)
            if (previous?.status == "applied" &&
                crypto.objectHash(previous.rawRecordJson) == crypto.objectHash(pageRecord.rawJson)
            ) {
                return@forEach
            }
            val relaySeq = wire?.relaySeq ?: page.nextSeq

            fun quarantine(reason: String) {
                inboxRecords +=
                    inboxRecord(
                        chainId = chainId,
                        recordId = recordId,
                        relaySeq = relaySeq,
                        rawJson = pageRecord.rawJson,
                        status = validationState(reason),
                        reason = reason,
                    )
            }

            if (wire == null) {
                quarantine(pageRecord.parseError ?: "Malformed sync record")
                return@forEach
            }
            if (wire.chainId != chainId || wire.protocolVersion != PALLASYNC_PROTOCOL_VERSION) {
                quarantine("Record chain or protocol did not match the active chain")
                return@forEach
            }
            val eligibility = runCatching { PallaSyncCore.validateRecordAuthority(authority, pageRecord.rawJson, relaySeq) }
            if (eligibility.isFailure || eligibility.getOrDefault(false) != true) {
                quarantine(eligibility.exceptionOrNull()?.message ?: "C_ACCEPTANCE_UNPROVEN")
                return@forEach
            }
            // Native authority validation already checks the retained certificate's
            // signing key at this log position. Mutable device display status must
            // not erase historical verification eligibility after revocation (§18.4).
            val payloadResult =
                runCatching {
                    val epochKeys = checkNotNull(keystore.keysForEpoch(keys, wire.epoch)) { "C_MISSING_DEPENDENCY" }
                    PallaSyncEpochSession.begin(epochKeys, authority, wire.epoch).use { it.decryptRecord(pageRecord.rawJson) }
                }
            val payloadJson = payloadResult.getOrNull()
            if (payloadJson.isNullOrBlank()) {
                val failureCode = payloadResult.exceptionOrNull()?.message?.takeIf { it.startsWith("C_") }
                if (recordPageMustRemainUncommitted(failureCode)) {
                    // The row has not been materialized and cannot be replayed from this page
                    // after its relay cursor advances. Keep the whole page uncommitted so the
                    // user can supply the retained epoch key and retry from the same boundary.
                    return PallaSyncHttpResult.Retryable(
                        "C_MISSING_DEPENDENCY: epoch key unavailable; record page and relay cursor were retained",
                    )
                }
                quarantine(failureCode ?: "C_SCHEMA_VIOLATION")
                return@forEach
            }
            val innerRecord =
                runCatching { json.decodeFromString<PallaSyncInnerRecord>(payloadJson) }.getOrNull()
                    ?: run {
                        quarantine("C_SCHEMA_VIOLATION")
                        return@forEach
                    }
            val maxLamport = innerRecord.operations.maxOf { it.lamport }
            decryptedCandidates +=
                DecryptedCandidate(
                    recordId = recordId,
                    relaySeq = relaySeq,
                    lamport = maxLamport,
                    rawRecordJson = pageRecord.rawJson,
                    payloadJson = payloadJson,
                )
        }

        try {
            db.transaction {
                decryptedCandidates.forEach { candidate -> inboxRecords += materializeCandidate(chainId, page.logId, candidate) }
                inboxRecords.filter { it.status == "quarantined" }.forEach { retainRecordQuarantine(it) }
                // applyVerifiedRecord observed only accepted operations' clocks in this transaction.
                dao.commitInboxPage(chainId, page.nextSeq, 0L, inboxRecords)
            }
            projection.publish(chainId, db)
        } catch (expectedFailure: Exception) {
            val error = expectedFailure
            return PallaSyncHttpResult.Retryable(
                "Could not commit the inbox page; relay cursor was retained: ${error.message}",
            )
        }
        return PallaSyncHttpResult.Success(Unit)
    }

    private suspend fun materializeCandidate(
        chain: String,
        log: String,
        candidate: DecryptedCandidate,
    ): PallaSyncInboxEntity {
        val wire = json.decodeFromString<PallaSyncWireRecord>(candidate.rawRecordJson)
        val inner = json.decodeFromString<PallaSyncInnerRecord>(candidate.payloadJson)
        val event =
            SignedEventEntity(
                chain,
                candidate.recordId,
                log,
                candidate.relaySeq,
                checkNotNull(crypto.objectHash(candidate.rawRecordJson)),
                candidate.rawRecordJson,
                null,
                "validated_object",
            )
        val conflict = db.protocolDao().recordConflict(event, wire.generation, wire.deviceId, inner)
        if (conflict == null) {
            db.protocolDao().applyVerifiedRecord(event, wire.generation, wire.deviceId, inner)
            db.dao.resolveInboxRecord(chain, candidate.recordId, candidate.rawRecordJson, candidate.relaySeq)
        }
        return inboxRecord(
            chain,
            candidate.recordId,
            candidate.relaySeq,
            candidate.rawRecordJson,
            status = if (conflict == null) "applied" else "quarantined",
            reason = conflict,
        )
    }

    private suspend fun retainRecordQuarantine(record: PallaSyncInboxEntity) {
        db.protocolDao().quarantine(
            QuarantineEvidenceEntity(
                record.chainId,
                checkNotNull(crypto.objectHash(record.rawRecordJson)),
                record.quarantineReason ?: "C_SCHEMA_VIOLATION",
                record.rawRecordJson,
                "unresolved",
            ),
        )
        if (record.quarantineReason in setOf("C_DEVICE_EQUIVOCATION", "C_RECORD_ID_CONFLICT")) db.dao.pauseIntegrityWrites(record.chainId)
    }

    suspend fun getDeviceViewHistory(deviceId: String): List<Illust> = getDeviceHistoryPage(deviceId, null).items

    suspend fun getDeviceHistoryPage(
        deviceId: String,
        cursor: com.yunfie.illustia.pallasync.data.HistoryCursor?,
    ): PallaSyncHistoryPage =
        withContext(Dispatchers.IO) {
            db.transaction {
                val state = checkNotNull(db.dao.getActiveChainState()) { "C_MISSING_DEPENDENCY" }
                val deviceBytes = PallaSyncConflictResolver.key(0, "upsert", deviceId, deviceId, 0).deviceId
                val page =
                    db.protocolDao().historySlice(
                        state.chainId,
                        VIEW_HISTORY_SCHEMA_V3,
                        "viewed",
                        cursor,
                        PALLASYNC_PAGE_SIZE,
                        deviceBytes,
                    )
                val items =
                    page.rows.map { row ->
                        val entity = checkNotNull(db.protocolDao().entity(state.chainId, VIEW_HISTORY_SCHEMA_V3, row.entityId))
                        json.decodeFromString<ViewedIllustBody>(entity.bodyJson).toIllust()
                    }
                PallaSyncHistoryPage(items, page.next, !state.initialPullCompleted || state.materializedSeq < state.downloadedSeq)
            }
        }

    suspend fun getClientStatus(): PallaSyncClientStatus? =
        withContext(Dispatchers.IO) {
            val state = db.dao.getActiveChainState() ?: return@withContext null
            val protocol = db.protocolDao()
            val transparencyProgress =
                PallaSyncTransparencyProgress.fromSavedHead(
                    transparencyId = state.transparencyId,
                    logId = state.logId,
                    verifiedIndex = state.transparencyIndex,
                    savedHeadJson = state.logId?.let { protocol.latestEvidence(state.chainId, it, "transparency_head") },
                )
            PallaSyncClientStatus(
                downloadedSeq = state.downloadedSeq,
                verifiedSeq = state.verifiedSeq,
                materializedSeq = state.materializedSeq,
                verifiedTransparencyIndex = transparencyProgress.verifiedIndex,
                observedTransparencyTargetIndex = transparencyProgress.observedTargetIndex,
                transparencyObservationConflict = transparencyProgress.observationConflict,
                pendingOutbox = db.dao.pendingOutboxCount(state.chainId),
                authorGaps = protocol.unresolvedAuthorGapCount(state.chainId),
                unresolvedQuarantine = protocol.unresolvedQuarantineCount(state.chainId),
                writeState = state.writeState,
                initialPullCompleted = state.initialPullCompleted,
                searchCount = protocol.liveScopeCount(state.chainId, "palleria.search_history/3", "all"),
                searchCeiling = PALLASYNC_SEARCH_SAFETY_LIMIT,
                viewedCount = protocol.liveScopeCount(state.chainId, "palleria.view_history/3", "viewed"),
                viewedCeiling = PALLASYNC_VIEWED_SAFETY_LIMIT,
                seenCount = protocol.liveScopeCount(state.chainId, "palleria.view_history/3", "seen"),
                seenCeiling = PALLASYNC_SEEN_SAFETY_LIMIT,
                retryAfterMs = policy.retry.remaining(policy.boundChainOrigin(state.chainId).orEmpty()),
                capacityIssue =
                    policy
                        .boundChainOrigin(state.chainId)
                        ?.let { origin -> policy.retry.capacityIssue(origin, state.chainId) }
                        ?.let {
                            PallaSyncCapacityIssue(it.code, it.usedBytes, it.maxBytes, it.observedAtMs)
                        },
            )
        }

    /** Clears only this chain's saved 507 request blockers after explicit user confirmation. */
    suspend fun confirmRelayCapacityRecovered(): Boolean =
        withContext(Dispatchers.IO) {
            val state = db.dao.getActiveChainState() ?: return@withContext false
            val origin = policy.boundChainOrigin(state.chainId) ?: return@withContext false
            policy.retry.resolveCapacity(origin, state.chainId)
        }.also { resolved ->
            if (resolved) syncNow()
        }

    suspend fun getUnresolvedQuarantinePage(
        afterHash: String = "",
        limit: Int = 50,
    ): PallaSyncQuarantinePage =
        withContext(Dispatchers.IO) {
            require(limit in 1..50 && afterHash.length <= 128) { "C_SCHEMA_VIOLATION" }
            val state = db.dao.getActiveChainState() ?: return@withContext PallaSyncQuarantinePage(emptyList(), null)
            val rows = db.protocolDao().unresolvedQuarantinePage(state.chainId, afterHash, limit + 1)
            val page = rows.take(limit)
            PallaSyncQuarantinePage(page, page.lastOrNull()?.objectHash.takeIf { rows.size > limit })
        }

    /** §19.3/§19.4: fetch missing authority/acceptance evidence, then replay only the immutable original. */
    suspend fun retryQuarantinedRecordEvidence(objectHash: String): Boolean {
        require(objectHash.matches(Regex("[A-Za-z0-9_-]{43}"))) { "C_SCHEMA_VIOLATION" }
        // A quarantined chain can make ordinary sync report partial completion even though it
        // successfully refreshed the proof needed for this exact item. Never use that summary
        // boolean as authority; the immutable object, receipt, and inclusion proof below decide.
        syncNow()
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                try {
                    val state = checkNotNull(activeChainStateLocked()) { "C_MISSING_DEPENDENCY" }
                    val protocol = db.protocolDao()
                    val evidence = checkNotNull(protocol.quarantineEvidence(state.chainId, objectHash)) { "C_MISSING_DEPENDENCY" }
                    check(
                        evidence.reason in setOf("C_MISSING_DEPENDENCY", "C_ACCEPTANCE_UNPROVEN") &&
                            evidence.resolutionState in setOf("unresolved", "pending_resolution") &&
                            crypto.objectHash(evidence.evidenceJson) == objectHash,
                    ) { "C_SCHEMA_VIOLATION" }
                    val wire = json.decodeFromString<PallaSyncWireRecord>(evidence.evidenceJson)
                    check(wire.chainId == state.chainId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                    val inbox = checkNotNull(db.dao.getInboxRecord(state.chainId, wire.recordId)) { "C_MISSING_DEPENDENCY" }
                    check(
                        inbox.status == "quarantined" && inbox.rawRecordJson == evidence.evidenceJson && inbox.relaySeq != null,
                    ) { "C_ROLLBACK_DETECTED" }
                    val relaySequence = checkNotNull(inbox.relaySeq) { "C_MISSING_DEPENDENCY" }
                    val authorityText = checkNotNull(protocol.currentAuthority(state.chainId)) { "C_MISSING_DEPENDENCY" }
                    check(keystore.authorityHash(state.chainId) == crypto.objectHash(authorityText)) { "C_ROLLBACK_DETECTED" }
                    check(PallaSyncCore.validateRecordAuthority(authorityText, evidence.evidenceJson, relaySequence)) {
                        "C_MISSING_DEPENDENCY"
                    }
                    verifyQuarantinedRecordAcceptance(protocol, state.chainId, relaySequence, evidence.evidenceJson, authorityText)
                    val keys = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                    val epochKeys = checkNotNull(keystore.keysForEpoch(keys, wire.epoch)) { "C_MISSING_DEPENDENCY" }
                    val payload =
                        PallaSyncEpochSession.begin(epochKeys, authorityText, wire.epoch).use {
                            it.decryptRecord(evidence.evidenceJson)
                        }
                    val inner = json.decodeFromString<PallaSyncInnerRecord>(payload)
                    check(inner.operations.isNotEmpty()) { "C_SCHEMA_VIOLATION" }
                    db.transaction {
                        val current = checkNotNull(protocol.quarantineEvidence(state.chainId, objectHash)) { "C_MISSING_DEPENDENCY" }
                        check(current == evidence) { "C_ROLLBACK_DETECTED" }
                        val applied =
                            materializeCandidate(
                                state.chainId,
                                checkNotNull(state.logId) { "C_MISSING_DEPENDENCY" },
                                DecryptedCandidate(
                                    wire.recordId,
                                    relaySequence,
                                    inner.operations.maxOf { it.lamport },
                                    evidence.evidenceJson,
                                    payload,
                                ),
                            )
                        check(applied.status == "applied") { applied.quarantineReason ?: "C_SCHEMA_VIOLATION" }
                        check(db.dao.getInboxRecord(state.chainId, wire.recordId)?.status == "applied") { "C_ROLLBACK_DETECTED" }
                        check(protocol.resolveQuarantineEvidence(state.chainId, objectHash, evidence.reason) == 1) {
                            "C_ROLLBACK_DETECTED"
                        }
                    }
                    projection.publish(state.chainId, db)
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val code = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_MISSING_DEPENDENCY"
                    log("Quarantine retry retained original evidence: $code")
                    false
                }
            }
        }
    }

    private suspend fun verifyQuarantinedRecordAcceptance(
        protocol: com.yunfie.illustia.pallasync.data.ProtocolDao,
        chainId: String,
        relaySequence: Long,
        recordJson: String,
        authorityJson: String,
    ) {
        val authority = Json.parseToJsonElement(authorityJson).jsonObject
        val pin = authority.getValue("pin").jsonObject
        val logId = pin.getValue("log_id").jsonPrimitive.content
        val row = checkNotNull(protocol.logEntry(chainId, logId, relaySequence)) { "C_MISSING_DEPENDENCY" }
        check(row.validationState in setOf("authority_verified", "materialized")) { "C_ACCEPTANCE_UNPROVEN" }
        check(crypto.objectHash(row.signedJson) == row.entryHash) { "C_ACCEPTANCE_UNPROVEN" }
        val entry = Json.parseToJsonElement(row.signedJson).jsonObject
        val record = Json.parseToJsonElement(recordJson)
        check(
            entry.getValue("relay_seq").jsonPrimitive.long == relaySequence &&
                entry.getValue("kind").jsonPrimitive.content == "record" && entry.getValue("object") == record,
        ) { "C_ACCEPTANCE_UNPROVEN" }
        val receipt = Json.parseToJsonElement(row.receiptJson).jsonObject
        check(
            receipt.getValue("relay_seq").jsonPrimitive.long == relaySequence &&
                receipt.getValue("object_hash").jsonPrimitive.content == crypto.objectHash(recordJson),
        ) { "C_ACCEPTANCE_UNPROVEN" }
        val receiptKeyId = receipt.getValue("relay_key_id").jsonPrimitive.content
        val transparencyIndex = receipt.getValue("transparency_index").jsonPrimitive.long
        val proof =
            protocol
                .evidenceAt(
                    pin.getValue("origin").jsonPrimitive.content,
                    receiptKeyId,
                    chainId,
                    logId,
                    "transparency",
                    transparencyIndex,
                ).singleOrNull() ?: error("C_MISSING_DEPENDENCY")
        val anchor = checkNotNull(keystore.transparencyPin(chainId)) { "C_MISSING_DEPENDENCY" }
        check(PallaSyncCore.validateReceiptInclusion(receipt.toString(), proof, pin.toString(), anchor.toString())) {
            "C_ACCEPTANCE_UNPROVEN"
        }
    }

    suspend fun trustedSnapshotCandidates(): List<PallaSyncTrustedSnapshotCandidate> =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(!state.initialPullCompleted) { "C_MISSING_DEPENDENCY" }
                val keys = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                requireProtectedAuthority(state.chainId)
                val authorityText = checkNotNull(db.protocolDao().currentAuthority(state.chainId)) { "C_MISSING_DEPENDENCY" }
                val authority = Json.parseToJsonElement(authorityText).jsonObject
                val base = selectedRelayBase()
                val origin = PallaSyncUrls.extractOrigin(base)
                policy.requireChainOrigin(state.chainId, origin)
                val history = selectedSourceHistory(state, base, authority, source = null)
                val checkpoint = history.load(authority, keystore.transparencyPin(state.chainId))
                check(PallaSyncCore.objectHash(checkNotNull(checkpoint.authority).toString()) == keystore.authorityHash(state.chainId)) {
                    "C_MISSING_DEPENDENCY"
                }
                PallaSyncSnapshotTrustReader(db, state.chainId).candidates(checkpoint.authority, keys, keystore)
            }
        }

    suspend fun trustAndInstallSnapshot(
        compactionId: String,
        compactionHash: String,
        explicitlyConfirmed: Boolean,
    ): Boolean =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                if (!explicitlyConfirmed) return@withLock false
                val state = checkNotNull(activeChainStateLocked()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                check(!state.initialPullCompleted && state.writeState == "active") { "C_MISSING_DEPENDENCY" }
                check(state.lastRelaySeq <= state.verifiedSeq) { "C_MISSING_DEPENDENCY" }
                check(db.snapshotInstallDao().pendingOutboxCount(state.chainId) == 0L) { "C_MISSING_DEPENDENCY" }
                check(db.snapshotInstallDao().unresolvedQuarantineCount(state.chainId) == 0L) { "C_MISSING_DEPENDENCY" }
                val keys = checkNotNull(keysForChainLocked(state.chainId)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                val base = selectedRelayBase()
                val context = resolveTrustedSnapshotContext(state, keys, base, compactionId, compactionHash)
                val staging =
                    PallaSyncSnapshotStaging(
                        db.snapshotDatabase().snapshotDao(),
                        state.chainId,
                        context.candidate.compactionId,
                        context.candidate.generation,
                        context.epoch,
                        context.candidate.coveredThroughSeq,
                        context.compaction.toString(),
                    )
                val pinJson =
                    context.checkpoint.authority
                        .getValue("pin")
                        .jsonObject
                        .toString()
                context.history.fetchAndStageSnapshotParts(pinJson, staging) { _, _ -> }
                context.history.verifySnapshotParts(
                    staging,
                    pinJson,
                    context.checkpoint.authority,
                    context.keys,
                    keystore,
                    explicitlyTrustedBootstrap = true,
                )
                check(db.snapshotInstallDao().install(state.chainId, context.candidate.compactionId)?.status == "installed") {
                    "C_MISSING_DEPENDENCY"
                }
                log(
                    "Explicitly trusted snapshot installed at covered sequence ${context.candidate.coveredThroughSeq}; suffix replay is pending",
                )
                true
            }
        }

    private suspend fun resolveTrustedSnapshotContext(
        state: ChainStateEntity,
        keys: PallaSyncKeySnapshot,
        base: HttpUrl,
        compactionId: String,
        compactionHash: String,
    ): TrustedSnapshotBootstrapContext {
        requireProtectedAuthority(state.chainId)
        val authority =
            Json
                .parseToJsonElement(checkNotNull(db.protocolDao().currentAuthority(state.chainId)))
                .jsonObject
        val origin = PallaSyncUrls.extractOrigin(base)
        policy.requireChainOrigin(state.chainId, origin)
        val discovery = selectedSourceHistory(state, base, authority, source = null)
        val discoveryCheckpoint = discovery.load(authority, keystore.transparencyPin(state.chainId))
        check(PallaSyncCore.objectHash(discoveryCheckpoint.authority.toString()) == keystore.authorityHash(state.chainId)) {
            "C_MISSING_DEPENDENCY"
        }
        val selected =
            PallaSyncSnapshotTrustReader(db, state.chainId)
                .candidates(discoveryCheckpoint.authority, keys, keystore)
                .singleOrNull { it.compactionId == compactionId && it.compactionHash == compactionHash }
                ?: error("C_BOOTSTRAP_TRUST_REQUIRED")
        check(selected.coveredThroughSeq >= state.verifiedSeq && selected.coveredThroughSeq >= state.lastRelaySeq) {
            "C_MISSING_DEPENDENCY"
        }
        policy.confirmTrustedSnapshot(selected.toPolicyChoice(origin), explicitlyConfirmed = true)
        val history = selectedSourceHistory(state, base, authority, source = "trusted_snapshot")
        val checkpoint = history.load(authority, keystore.transparencyPin(state.chainId))
        check(PallaSyncCore.objectHash(checkpoint.authority.toString()) == keystore.authorityHash(state.chainId)) {
            "C_MISSING_DEPENDENCY"
        }
        check(
            checkpoint.generation == selected.generation && checkpoint.logId == selected.logId &&
                checkpoint.epochHash == selected.epochHash,
        ) {
            "C_MISSING_DEPENDENCY"
        }
        val compaction =
            db
                .compactionCatalogueDao()
                .verifiedEntries(state.chainId, selected.logId, selected.compactionId)
                .singleOrNull()
                ?.let {
                    Json
                        .parseToJsonElement(it.signedJson)
                        .jsonObject
                        .getValue("object")
                        .jsonObject
                }
                ?: error("C_BOOTSTRAP_TRUST_REQUIRED")
        check(PallaSyncCore.objectHash(compaction.toString()) == selected.compactionHash) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val epoch =
            checkpoint.authority
                .getValue("epochs")
                .jsonObject
                .entries
                .single { (_, entry) -> PallaSyncCore.objectHash(entry.jsonObject.getValue("record").toString()) == selected.epochHash }
                .key
                .toLongOrNull() ?: error("C_SCHEMA_VIOLATION")
        return TrustedSnapshotBootstrapContext(keys, checkpoint, history, selected, compaction, epoch)
    }

    private fun PallaSyncTrustedSnapshotCandidate.toPolicyChoice(origin: String) =
        PallaSyncPolicyGate.TrustedSnapshotChoice(
            relayOrigin = origin,
            chainId = chainId,
            logId = logId,
            compactionId = compactionId,
            compactionHash = compactionHash,
            coveredThroughSeq = coveredThroughSeq,
            coveredLogHash = coveredLogHash,
            stateSha256 = stateSha256,
            epochHash = epochHash,
            authorDeviceId = authorDeviceId,
            authorPublicKey = authorPublicKey,
            partCount = partCount,
        )

    private suspend fun selectedRelayBase(): HttpUrl =
        when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
            is PallaSyncHttpResult.Success -> normalized.value
            is PallaSyncHttpResult.ProtocolError -> error(normalized.message)
            else -> error("C_RELAY_UNAVAILABLE")
        }

    private fun selectedSourceHistory(
        state: ChainStateEntity,
        base: HttpUrl,
        authority: JsonObject,
        source: String?,
    ) = PallaSyncInitialHistory(
        store = db,
        remote = remote,
        policy = policy,
        root = null,
        base = base,
        peerChainId = state.chainId,
        peerAdminPublicKey = authority.getValue("admin_public_key").jsonPrimitive.content,
        importedAuthority = authority,
        peerToken = { url, path, query -> makeCapabilityToken(url, state.chainId, "GET", path, query) },
        source = source,
    )

    private suspend fun goneLocked(): SyncCycleOutcome {
        stopBackgroundSync()
        log("C_DELETION_UNVERIFIED: signed deletion authority is required; keys, state and outbox were retained")
        return SyncCycleOutcome.Gone
    }

    private suspend fun clearLocalChainLocked() {
        val dao = db.pallaSyncDao()
        dao.clearActiveChainData()
        keystore.clearAllKeys()
        runCatching {
            SettingsStore(appContext).setPallaSyncEnabledFromCoordinator(false)
        }.onFailure { expectedFailure ->
            val error = expectedFailure
            log("Chain data was cleared, but the enabled flag could not be updated: ${error.message}")
        }
        // Cancel after the explicitly requested local cleanup is durable.
        stopBackgroundSync()
    }

    private suspend fun disableOrphanedEnabledFlag() {
        runCatching {
            val settingsStore = SettingsStore(appContext)
            val settings = settingsStore.read()
            if (settings.pallaSyncEnabled) {
                settingsStore.setPallaSyncEnabledFromCoordinator(false)
                log("Disabled PallaSync because no active local chain exists")
                stopBackgroundSync()
            }
        }.onFailure { expectedFailure ->
            val error = expectedFailure
            log("Could not repair the orphaned PallaSync enabled flag: ${error.message}")
        }
    }

    private fun prepareFavoriteOrder(
        events: List<PallaSyncPendingEvent>,
        retained: Map<String, String>,
        appendAfter: String? = null,
    ): List<PallaSyncPendingEvent> {
        val order = events.mapNotNull { it.favoriteOrder }.firstOrNull() ?: return events
        check(events.mapNotNull { it.favoriteOrder }.all { it == order }) { "C_SCHEMA_VIOLATION" }
        val changed =
            events.filter { it.schema == FAVORITE_TAG_SCHEMA_V3 && it.operation == "upsert" }.mapTo(
                mutableSetOf(),
            ) { it.entityId }
        val keys = PallaSyncFractionalOrder.place(order, changed, retained, appendAfter)
        return events.map { event ->
            if (event.schema == FAVORITE_TAG_SCHEMA_V3 && event.operation == "upsert") {
                event.copy(
                    context =
                        kotlinx.serialization.json.buildJsonObject {
                            put("order_key", kotlinx.serialization.json.JsonPrimitive(keys.getValue(event.entityId)))
                        },
                    favoriteOrder = null,
                )
            } else {
                event
            }
        }
    }

    /**
     * Resolves the only key snapshot that is allowed to sign/decrypt [chainId].
     * A staged join survives process death: it is promoted when Room already
     * points at the candidate, and retained for reconciliation when Room still points at the old chain.
     */
    private suspend fun activeChainStateLocked(): ChainStateEntity? {
        val dao = db.pallaSyncDao()
        val states = dao.getActiveChainStates()
        if (states.size <= 1) return states.singleOrNull()

        val pendingChainId = keystore.getPendingChainKeys()?.chainId
        val activeChainId = keystore.getActiveChainKeys()?.let(::resolvedSnapshotChainId)
        val retained =
            pendingChainId
                ?.let { id -> states.singleOrNull { it.chainId == id } }
                ?: activeChainId?.let { id -> states.singleOrNull { it.chainId == id } }
                ?: return null
        dao.retainOnlyChain(retained.chainId)
        log("Repaired multiple local PallaSync chain states using the matching key material")
        return retained
    }

    private fun keysForChainLocked(chainId: String): PallaSyncKeySnapshot? {
        val identity = keystore.boundDeviceId(chainId) ?: return null
        val pending = keystore.getPendingChainKeys()
        if (pending?.chainId == chainId && pending.deviceId == identity) {
            if (keystore.promotePendingChainKeys()) {
                return keystore.getActiveChainKeys()
            }
            return pending
        }

        val active = keystore.getActiveChainKeys() ?: return null
        val resolvedChainId = resolvedSnapshotChainId(active)
        if (resolvedChainId != chainId || active.deviceId != identity) return null

        // An uncertain remote join retains its candidate even when another chain is active.
        if (active.chainId == null) {
            val upgraded = active.copy(chainId = resolvedChainId)
            if (keystore.saveActiveChainKeys(upgraded)) return upgraded
        }
        return active.copy(chainId = resolvedChainId)
    }

    private fun resolvedSnapshotChainId(snapshot: PallaSyncKeySnapshot): String? = snapshot.chainId

    private fun inboxRecord(
        chainId: String,
        recordId: String,
        relaySeq: Long,
        rawJson: String,
        status: String,
        reason: String? = null,
    ) = PallaSyncInboxEntity(
        chainId = chainId,
        recordId = recordId,
        relaySeq = relaySeq,
        status = status,
        rawRecordJson = rawJson,
        quarantineReason = reason,
        receivedAtMs = System.currentTimeMillis(),
    )

    private fun malformedRecordId(rawJson: String): String {
        val reportedId =
            runCatching {
                json
                    .parseToJsonElement(rawJson)
                    .jsonObject["record_id"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()
        return reportedId?.takeIf(String::isNotBlank)
            ?: UUID.nameUUIDFromBytes(rawJson.toByteArray(Charsets.UTF_8)).toString()
    }

    private fun ViewedIllustBody.toIllust() = illust()

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)

    private data class DecryptedCandidate(
        val recordId: String,
        val relaySeq: Long,
        val lamport: Long,
        val rawRecordJson: String,
        val payloadJson: String,
    )

    private enum class SyncCycleOutcome {
        Success,
        Idle,
        Retryable,
        ProtocolError,
        Gone,
    }
}
