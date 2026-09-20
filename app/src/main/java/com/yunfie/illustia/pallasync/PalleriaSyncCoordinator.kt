package com.yunfie.illustia.pallasync

import android.content.Context
import android.os.Build
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.OutboxEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDeviceEntity
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import com.yunfie.illustia.settings.SettingsStore
import com.yunfie.illustia.settings.store.PALLA_SYNC_SERVER_URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Application-scoped implementation of PallaSync state transitions and polling. */
internal class PalleriaSyncCoordinator(
    client: OkHttpClient = OkHttpClient(),
    context: Context,
    private val coordinatorScope: CoroutineScope? = null,
) : PallaSyncEventWriter {
    private val appContext = context.applicationContext
    private val scope = coordinatorScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val jobLock = Any()
    private val remote = PallaSyncRemoteService(client)
    private val localStore = PallaSyncLocalStore(appContext)
    private val db = localStore
    private val crypto = PallaSyncCryptoService()
    private val keystore by lazy { PallaSyncKeystore(appContext) }
    private val recordProcessor = PallaSyncRecordProcessor(appContext)
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    @Volatile
    private var backgroundSyncJob: Job? = null

    companion object {
        private const val DEFAULT_SERVER_URL = "https://api.yunfi.f5.si"
        private const val NORMAL_POLL_DELAY_MS = 5_000L
        private const val MAX_RETRY_DELAY_MS = 5 * 60_000L
        private const val PROTOCOL_ERROR_DELAY_MS = 30_000L
        private val JSON_MEDIA_TYPE = "application/vnd.palleria.sync.v2+json".toMediaType()

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
            backgroundSyncJob =
                scope.launch {
                    var retryDelayMs = NORMAL_POLL_DELAY_MS
                    while (currentCoroutineContext().isActive) {
                        val outcome =
                            runCatching {
                                operationMutex.withLock { synchronizeOnceLocked() }
                            }.getOrElse { expectedFailure ->
                                val error = expectedFailure
                                log("Sync cycle failed without advancing the relay cursor: ${error.message}")
                                SyncCycleOutcome.Retryable
                            }

                        val waitMs =
                            when (outcome) {
                                SyncCycleOutcome.Success,
                                SyncCycleOutcome.Idle,
                                -> {
                                    retryDelayMs = NORMAL_POLL_DELAY_MS
                                    NORMAL_POLL_DELAY_MS
                                }

                                SyncCycleOutcome.Retryable -> {
                                    val current = retryDelayMs
                                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
                                    current
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

    fun getPallaSyncKeystore(): PallaSyncKeystore = keystore

    /** Repairs a process death between staged-key and Room activation commits. */
    suspend fun recoverInterruptedActivation(): Boolean {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state =
                    activeChainStateLocked() ?: run {
                        keystore.clearPendingChainKeys()
                        return@withLock false
                    }
                if (keysForChainLocked(state.chainId) == null) return@withLock false
                SettingsStore(appContext).setPallaSyncEnabledFromCoordinator(true)
                true
            }
        }
    }

    suspend fun initializeGenesis(serverUrl: String? = null): String {
        val seedPhrase = withContext(Dispatchers.Default) { crypto.generateSeedPhrase() }
        val joined =
            joinChainInternal(
                seedPhrase = seedPhrase,
                deviceName = Build.MODEL,
                isGenesis = true,
                serverUrl = serverUrl,
            )
        return if (joined) seedPhrase else ""
    }

    suspend fun joinChain(
        seedPhrase: String,
        deviceName: String = Build.MODEL,
        serverUrl: String? = null,
    ): Boolean = joinChainInternal(seedPhrase, deviceName, isGenesis = false, serverUrl = serverUrl)

    private suspend fun joinChainInternal(
        seedPhrase: String,
        deviceName: String,
        isGenesis: Boolean,
        serverUrl: String?,
    ): Boolean {
        var activatedChainId: String? = null
        val joined =
            runCatching {
                withContext(Dispatchers.IO) {
                    operationMutex.withLock {
                        val activated =
                            joinChainLocked(seedPhrase.trim(), deviceName, isGenesis, serverUrl)
                                ?: return@withLock false
                        val chainId = activated.first
                        val activatedBaseUrl = activated.second
                        activatedChainId = chainId
                        SettingsStore(appContext).apply {
                            setPallaSyncServerUrlFromCoordinator(activatedBaseUrl.toString().trimEnd('/'))
                            setPallaSyncEnabledFromCoordinator(true)
                        }
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
                log("Initial PallaSync cycle failed; background retry will continue: ${error.message}")
                activatedChainId != null
            }
        if (joined) {
            startBackgroundSync()
        }
        return joined
    }

    private suspend fun joinChainLocked(
        seedPhrase: String,
        deviceName: String,
        isGenesis: Boolean,
        serverUrl: String?,
    ): Pair<String, HttpUrl>? {
        val keysObject =
            runCatching {
                val derived =
                    crypto.deriveKeys(seedPhrase)
                        ?: error("Native key derivation failed")
                json.parseToJsonElement(derived).jsonObject
            }.getOrElse {
                log("Could not derive PallaSync keys from the seed phrase")
                return null
            }
        val chainId =
            keysObject.string("chain_id") ?: return null.also {
                log("Derived PallaSync keys did not contain a chain ID")
            }
        val encryptionKey =
            keysObject.string("encryption_key") ?: return null.also {
                log("Derived PallaSync keys did not contain an encryption key")
            }
        val signingKey =
            keysObject.string("signing_key") ?: return null.also {
                log("Derived PallaSync keys did not contain a signing key")
            }
        val publicKey =
            keysObject.string("public_key") ?: return null.also {
                log("Derived PallaSync keys did not contain a public key")
            }
        val baseUrl =
            when (val normalized = PallaSyncUrls.normalize(serverUrl ?: getServerUrl())) {
                is PallaSyncHttpResult.Success -> normalized.value
                is PallaSyncHttpResult.ProtocolError -> return null.also { log(normalized.message) }
                else -> return null
            }

        val deviceId =
            keystore.getDeviceId()
                ?: com.yunfie.illustia.pallasync.util.UuidV7
                    .generateString()
        val deviceRecord =
            runCatching {
                crypto.createDeviceRecord(
                    chainId = chainId,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    encryptionKeyBase64 = encryptionKey,
                    signingKeyBase64 = signingKey,
                ) ?: error("Native device record creation failed")
            }.getOrElse {
                log("Could not create this device's signed join record: ${it.message}")
                return null
            }

        val healthRequest =
            Request
                .Builder()
                .url(PallaSyncUrls.health(baseUrl))
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .get()
                .build()
        when (
            val health =
                remote.execute(healthRequest) { response ->
                    val contentType = response.header("Content-Type")?.substringBefore(';')?.trim()
                    if (contentType != JSON_MEDIA_TYPE.toString()) {
                        return@execute PallaSyncHttpResult.ProtocolError(
                            "PallaSync health response used an unexpected media type",
                        )
                    }
                    val body =
                        response.body?.string()
                            ?: return@execute PallaSyncHttpResult.ProtocolError("PallaSync health response was empty")
                    val healthBody =
                        runCatching { json.decodeFromString<PallaSyncHealth>(body) }.getOrNull()
                            ?: return@execute PallaSyncHttpResult.ProtocolError("PallaSync health response was invalid")
                    if (healthBody.status != "ok" || healthBody.protocolVersion != PALLASYNC_PROTOCOL_VERSION) {
                        return@execute PallaSyncHttpResult.ProtocolError(
                            "PallaSync health response advertised an incompatible protocol",
                        )
                    }
                    PallaSyncHttpResult.Success(Unit)
                }
        ) {
            is PallaSyncHttpResult.Success -> Unit
            PallaSyncHttpResult.Gone -> return null.also { log("The PallaSync service is unavailable") }
            is PallaSyncHttpResult.Retryable -> return null.also { log(health.message) }
            is PallaSyncHttpResult.ProtocolError -> return null.also { log(health.message) }
        }
        val joinRequest =
            Request
                .Builder()
                .url(PallaSyncUrls.devices(baseUrl, chainId))
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .post(deviceRecord.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        when (val posted = remote.executeUnit(joinRequest)) {
            is PallaSyncHttpResult.Success -> Unit

            PallaSyncHttpResult.Gone -> return null.also {
                log("The requested PallaSync chain has been deleted")
            }

            is PallaSyncHttpResult.Retryable -> return null.also { log(posted.message) }

            is PallaSyncHttpResult.ProtocolError -> return null.also { log(posted.message) }
        }

        val candidateKeys =
            PallaSyncKeySnapshot(
                chainId = chainId,
                seedPhrase = seedPhrase,
                encryptionKeyBase64Url = encryptionKey,
                signingKeyBase64Url = signingKey,
                publicKeyBase64Url = publicKey,
            )
        val initialState =
            ChainStateEntity(
                chainId = chainId,
                lamport = 0,
                keyEpoch = 1,
                chainVectorJson = "{}",
                lastRelaySeq = 0,
                initialPullCompleted = isGenesis,
            )
        val localCollections =
            try {
                SettingsStore(appContext).readSyncedCollections()
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                log("Could not snapshot local sync items before activating the chain: ${error.message}")
                return null
            }
        val initialSettingsEvents = buildInitialSettingsSyncEvents(localCollections)
        val (activatedState, initialOutbox) =
            runCatching {
                createInitialOutbox(
                    state = initialState,
                    keys = candidateKeys,
                    deviceId = deviceId,
                    events = initialSettingsEvents,
                    status = if (isGenesis) "queued" else "pending_initial_merge",
                )
            }.getOrElse { expectedFailure ->
                val error = expectedFailure
                log("Could not prepare initial sync items; the current chain was retained: ${error.message}")
                return null
            }
        if (!keystore.savePendingChainKeys(candidateKeys)) {
            log("The new PallaSync keys could not be stored; the current chain was retained")
            return null
        }

        try {
            db.pallaSyncDao().activateChain(
                activatedState,
                initialEvents = initialOutbox,
            )
        } catch (expectedFailure: Exception) {
            val error = expectedFailure
            keystore.clearPendingChainKeys()
            log("The new chain could not be activated; the previous chain was retained: ${error.message}")
            return null
        }

        if (!keystore.promotePendingChainKeys()) {
            log("The staged chain keys will be promoted on the next sync cycle")
        }

        stopBackgroundSync()
        keystore.saveDeviceId(deviceId)
        log("Successfully joined chain: ${chainId.take(8)}…")
        return chainId to baseUrl
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
            operationMutex.withLock { enqueueDataEventsLocked(events) }
        }
    }

    override suspend fun <T> enqueueDataEventsThen(
        events: List<PallaSyncPendingEvent>,
        afterEnqueue: suspend () -> T,
    ): T =
        withContext(Dispatchers.IO) {
            operationMutex.withLock {
                check(enqueueDataEventsLocked(events)) {
                    "No matching active PallaSync chain/key exists"
                }
                afterEnqueue()
            }
        }

    private suspend fun enqueueDataEventsLocked(events: List<PallaSyncPendingEvent>): Boolean {
        val dao = db.pallaSyncDao()
        var state = activeChainStateLocked() ?: return false
        val keys = keysForChainLocked(state.chainId) ?: return false
        val deviceId = keystore.getDeviceId() ?: return false
        val outbox = mutableListOf<OutboxEntity>()
        val status = if (state.initialPullCompleted) "queued" else "pending_initial_merge"

        events.forEach { event ->
            val lamport = state.lamport + 1
            val payload =
                DataPayload(
                    schema = event.schema,
                    entity_id = event.entityId,
                    operation = event.operation,
                    context = emptyMap(),
                    lamport = lamport,
                    created_at_ms = System.currentTimeMillis(),
                    body = event.body,
                )
            val recordJson =
                crypto.createSyncRecord(
                    chainId = state.chainId,
                    recordId =
                        com.yunfie.illustia.pallasync.util.UuidV7
                            .generateString(),
                    collectionName = event.schema,
                    action = event.operation,
                    payloadJson = json.encodeToString(payload),
                    deviceId = deviceId,
                    encryptionKeyBase64 = keys.encryptionKeyBase64Url,
                    signingKeyBase64 = keys.signingKeyBase64Url,
                ) ?: error("Native sync record creation failed")
            state = state.copy(lamport = lamport)
            outbox +=
                OutboxEntity(
                    chainId = state.chainId,
                    deviceSeq = lamport,
                    status = status,
                    eventJson = recordJson,
                )
        }
        dao.updateChainStateAndInsertOutboxEvents(state, outbox)
        return true
    }

    /** Explicit delete succeeds only on 2xx/410; transient/protocol errors retain local keys. */
    suspend fun deleteChain(callApi: Boolean = true): Boolean {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val activeChain = db.pallaSyncDao().getAllChainStates().singleOrNull()
                if (activeChain == null) return@withLock true
                if (!callApi) {
                    clearLocalChainLocked()
                    return@withLock true
                }

                val baseUrl =
                    when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        is PallaSyncHttpResult.ProtocolError -> return@withLock false.also { log(normalized.message) }
                        else -> return@withLock false
                    }
                val request =
                    Request
                        .Builder()
                        .url(PallaSyncUrls.chain(baseUrl, activeChain.chainId))
                        .header("Accept", JSON_MEDIA_TYPE.toString())
                        .delete()
                        .build()
                when (val result = remote.executeUnit(request)) {
                    is PallaSyncHttpResult.Success,
                    PallaSyncHttpResult.Gone,
                    -> {
                        clearLocalChainLocked()
                        log("PallaSync chain deleted")
                        true
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
                                clearLocalChainLocked()
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
                val state = db.pallaSyncDao().getAllChainStates().singleOrNull() ?: return@withLock true
                val baseUrl =
                    when (val normalized = PallaSyncUrls.normalize(serverUrl)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> return@withLock false
                    }
                when (val result = processOutboxLocked(baseUrl, state.chainId)) {
                    is PallaSyncHttpResult.Success -> true
                    PallaSyncHttpResult.Gone -> false.also { clearLocalChainLocked() }
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
                val baseUrl =
                    when (val normalized = PallaSyncUrls.normalize(serverUrl)) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> return@withLock false
                    }
                val request =
                    Request
                        .Builder()
                        .url(PallaSyncUrls.recordsEndpoint(baseUrl, chainId))
                        .header("Accept", JSON_MEDIA_TYPE.toString())
                        .post(recordsJsonArray.toRequestBody(JSON_MEDIA_TYPE))
                        .build()
                when (remote.executeUnit(request)) {
                    is PallaSyncHttpResult.Success -> {
                        true
                    }

                    PallaSyncHttpResult.Gone -> {
                        false.also {
                            if (db.pallaSyncDao().getActiveChainState()?.chainId == chainId) {
                                clearLocalChainLocked()
                            }
                        }
                    }

                    else -> {
                        false
                    }
                }
            }
        }
    }

    private suspend fun synchronizeOnceLocked(baseUrlOverride: HttpUrl? = null): SyncCycleOutcome {
        val dao = db.pallaSyncDao()
        val chainState =
            activeChainStateLocked() ?: run {
                keystore.clearPendingChainKeys()
                disableOrphanedEnabledFlag()
                return SyncCycleOutcome.Idle
            }
        val baseUrl =
            baseUrlOverride ?: when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                is PallaSyncHttpResult.Success -> {
                    normalized.value
                }

                is PallaSyncHttpResult.ProtocolError -> {
                    log(normalized.message)
                    return SyncCycleOutcome.ProtocolError
                }

                else -> {
                    return SyncCycleOutcome.ProtocolError
                }
            }
        if (keysForChainLocked(chainState.chainId) == null) {
            log("Active PallaSync keys do not match the Room chain state")
            return SyncCycleOutcome.ProtocolError
        }

        // Device validation/self-heal must happen before old queued records are
        // uploaded, otherwise a missing relay-side device can block forever on 400.
        when (val devices = fetchDevicesLocked(baseUrl, chainState.chainId)) {
            is PallaSyncHttpResult.Success -> Unit
            PallaSyncHttpResult.Gone -> return goneLocked()
            is PallaSyncHttpResult.Retryable -> return SyncCycleOutcome.Retryable.also { log(devices.message) }
            is PallaSyncHttpResult.ProtocolError -> return SyncCycleOutcome.ProtocolError.also { log(devices.message) }
        }
        when (val pushed = processOutboxLocked(baseUrl, chainState.chainId)) {
            is PallaSyncHttpResult.Success -> Unit
            PallaSyncHttpResult.Gone -> return goneLocked()
            is PallaSyncHttpResult.Retryable -> return SyncCycleOutcome.Retryable.also { log(pushed.message) }
            is PallaSyncHttpResult.ProtocolError -> return SyncCycleOutcome.ProtocolError.also { log(pushed.message) }
        }
        when (val pulled = pullRecordPagesLocked(baseUrl, chainState.chainId)) {
            is PallaSyncHttpResult.Success -> Unit
            PallaSyncHttpResult.Gone -> return goneLocked()
            is PallaSyncHttpResult.Retryable -> return SyncCycleOutcome.Retryable.also { log(pulled.message) }
            is PallaSyncHttpResult.ProtocolError -> return SyncCycleOutcome.ProtocolError.also { log(pulled.message) }
        }

        val activatedInitialEvents =
            if (chainState.initialPullCompleted) {
                0
            } else {
                dao.completeInitialPullAndQueueEvents(chainState.chainId)
            }
        if (!chainState.initialPullCompleted) {
            log("Queued $activatedInitialEvents local item(s) after the first successful pull")
            when (val pushed = processOutboxLocked(baseUrl, chainState.chainId)) {
                is PallaSyncHttpResult.Success -> Unit
                PallaSyncHttpResult.Gone -> return goneLocked()
                is PallaSyncHttpResult.Retryable -> return SyncCycleOutcome.Retryable.also { log(pushed.message) }
                is PallaSyncHttpResult.ProtocolError -> return SyncCycleOutcome.ProtocolError.also { log(pushed.message) }
            }
            // Apply the just-merged local items immediately so the UI does not
            // temporarily show only the remote pre-join snapshot.
            when (val pulled = pullRecordPagesLocked(baseUrl, chainState.chainId)) {
                is PallaSyncHttpResult.Success -> Unit
                PallaSyncHttpResult.Gone -> return goneLocked()
                is PallaSyncHttpResult.Retryable -> return SyncCycleOutcome.Retryable.also { log(pulled.message) }
                is PallaSyncHttpResult.ProtocolError -> return SyncCycleOutcome.ProtocolError.also { log(pulled.message) }
            }
        }
        return SyncCycleOutcome.Success
    }

    private suspend fun processOutboxLocked(
        baseUrl: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<Unit> {
        val dao = db.pallaSyncDao()
        dao.deleteAcceptedEvents()
        val events = dao.getQueuedEvents().filter { it.chainId == chainId }
        if (events.isEmpty()) return PallaSyncHttpResult.Success(Unit)
        val batchJson = events.joinToString(separator = ",", prefix = "[", postfix = "]") { it.eventJson }
        val batchRequest =
            Request
                .Builder()
                .url(PallaSyncUrls.recordsEndpoint(baseUrl, chainId))
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .post(batchJson.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        when (val result = remote.executeUnit(batchRequest)) {
            is PallaSyncHttpResult.Success -> {
                events.forEach { dao.deleteOutboxEvent(it.id) }
                log("Uploaded and removed ${events.size} accepted sync record(s) in a single batch")
                return PallaSyncHttpResult.Success(Unit)
            }

            is PallaSyncHttpResult.ProtocolError -> {
                if (result.statusCode != 400) {
                    return result
                }
                log("Batch upload failed, falling back to sequential upload to isolate the bad record")
            }

            else -> {
                return result
            }
        }

        var accepted = 0
        for (event in events) {
            val request =
                Request
                    .Builder()
                    .url(PallaSyncUrls.recordsEndpoint(baseUrl, chainId))
                    .header("Accept", JSON_MEDIA_TYPE.toString())
                    .post("[${event.eventJson}]".toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            when (val result = remote.executeUnit(request)) {
                is PallaSyncHttpResult.Success -> {
                    dao.deleteOutboxEvent(event.id)
                    accepted += 1
                }

                is PallaSyncHttpResult.ProtocolError -> {
                    if (result.statusCode == 400) {
                        dao.updateOutboxEvent(event.copy(status = "rejected"))
                        log("Quarantined an unrecoverable local sync record: ${result.message}")
                    } else {
                        return result
                    }
                }

                else -> {
                    return result
                }
            }
        }
        if (accepted > 0) log("Uploaded and removed $accepted accepted sync record(s)")
        return PallaSyncHttpResult.Success(Unit)
    }

    private suspend fun fetchDevicesLocked(
        baseUrl: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<Unit> {
        val request =
            Request
                .Builder()
                .url(PallaSyncUrls.devices(baseUrl, chainId))
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .get()
                .build()
        val responseResult =
            remote.execute(request) { response ->
                val body =
                    response.body?.string()
                        ?: return@execute PallaSyncHttpResult.ProtocolError("Device response body was empty")
                val array =
                    runCatching { json.parseToJsonElement(body).jsonArray }.getOrElse {
                        return@execute PallaSyncHttpResult.ProtocolError("Device response was not valid JSON")
                    }
                PallaSyncHttpResult.Success(array.map { it.toString() })
            }
        val rawDevices =
            when (responseResult) {
                is PallaSyncHttpResult.Success -> responseResult.value
                PallaSyncHttpResult.Gone -> return PallaSyncHttpResult.Gone
                is PallaSyncHttpResult.Retryable -> return responseResult
                is PallaSyncHttpResult.ProtocolError -> return responseResult
            }

        val dao = db.pallaSyncDao()
        val keys =
            keysForChainLocked(chainId)
                ?: return PallaSyncHttpResult.ProtocolError("Active PallaSync keys are missing")
        val myDeviceId = keystore.getDeviceId()
        val activeDeviceIds = mutableSetOf<String>()
        var shouldSelfHeal = false

        rawDevices.forEach { rawDevice ->
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
            activeDeviceIds += device.deviceId
            if (device.devicePublicKey != keys.publicKeyBase64Url) {
                log("Ignored a device record outside the active chain key")
                if (device.deviceId == myDeviceId) shouldSelfHeal = true
                return@forEach
            }
            if (!runCatching { crypto.verifyDeviceRecord(rawDevice) }.getOrDefault(false)) {
                log("Ignored an invalid device signature for ${device.deviceId.take(8)}")
                if (device.deviceId == myDeviceId) shouldSelfHeal = true
                // Older relay UPSERTs mixed a new signature with the previous timestamp. Keep
                // only the already-trusted chain public key so valid historical sync records can
                // still be checked while each affected device re-registers itself.
                dao.insertDevice(
                    PallaSyncDeviceEntity(
                        deviceId = device.deviceId,
                        chainId = chainId,
                        deviceName = "Device ${device.deviceId.take(4)}",
                        publicKey = keys.publicKeyBase64Url,
                        joinedAtMs = device.createdAtMs,
                    ),
                )
                return@forEach
            }

            val decryptedName =
                runCatching {
                    crypto.decryptDeviceRecord(
                        encryptedDeviceName = device.encryptedDeviceName,
                        deviceId = device.deviceId,
                        encryptionKeyBase64 = keys.encryptionKeyBase64Url,
                    )
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
        dao
            .getDevicesInChain(chainId)
            .filterNot { it.deviceId in activeDeviceIds }
            .forEach { dao.deleteDevice(it.deviceId) }

        if (shouldSelfHeal && myDeviceId != null) {
            val record =
                runCatching {
                    crypto.createDeviceRecord(
                        chainId = chainId,
                        deviceId = myDeviceId,
                        deviceName = Build.MODEL,
                        encryptionKeyBase64 = keys.encryptionKeyBase64Url,
                        signingKeyBase64 = keys.signingKeyBase64Url,
                    ) ?: error("Native device record creation failed")
                }.getOrElse {
                    return PallaSyncHttpResult.ProtocolError("Could not create a self-healing device record")
                }
            val healRequest =
                Request
                    .Builder()
                    .url(PallaSyncUrls.devices(baseUrl, chainId))
                    .header("Accept", JSON_MEDIA_TYPE.toString())
                    .post(record.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            when (val healed = remote.executeUnit(healRequest)) {
                is PallaSyncHttpResult.Success -> {
                    dao.insertDevice(
                        PallaSyncDeviceEntity(
                            deviceId = myDeviceId,
                            chainId = chainId,
                            deviceName = Build.MODEL,
                            publicKey = keys.publicKeyBase64Url,
                            joinedAtMs = System.currentTimeMillis(),
                        ),
                    )
                    log("Re-registered this device's signed record")
                }

                else -> {
                    return healed
                }
            }
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
        val request =
            Request
                .Builder()
                .url(PallaSyncUrls.records(baseUrl, chainId, afterSeq, PALLASYNC_PAGE_SIZE))
                .header("Accept", JSON_MEDIA_TYPE.toString())
                .get()
                .build()
        return remote.execute(request) { response ->
            val nextSeq =
                response.header(PALLASYNC_NEXT_SEQ_HEADER)?.toLongOrNull()
                    ?: return@execute PallaSyncHttpResult.ProtocolError("Relay page did not include a valid next cursor")
            val hasMore =
                when (response.header(PALLASYNC_HAS_MORE_HEADER)?.lowercase(Locale.ROOT)) {
                    "true" -> true
                    "false" -> false
                    else -> return@execute PallaSyncHttpResult.ProtocolError("Relay page did not include a valid has-more header")
                }
            val body =
                response.body?.string()
                    ?: return@execute PallaSyncHttpResult.ProtocolError("Relay page body was empty")
            val array =
                runCatching { json.parseToJsonElement(body).jsonArray }.getOrElse {
                    return@execute PallaSyncHttpResult.ProtocolError("Relay page was not valid JSON")
                }
            val records =
                array.map { element ->
                    val raw = element.toString()
                    runCatching { json.decodeFromString<PallaSyncWireRecord>(raw) }
                        .fold(
                            onSuccess = { PallaSyncPageRecord(it, raw) },
                            onFailure = { PallaSyncPageRecord(null, raw, it.message ?: "malformed record") },
                        )
                }
            PallaSyncHttpResult.Success(PallaSyncRecordsPage(records, nextSeq, hasMore))
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

        page.records.forEach { pageRecord ->
            val wire = pageRecord.wireRecord
            val recordId = wire?.recordId ?: malformedRecordId(pageRecord.rawJson)
            if (!pageRecordIds.add(recordId)) return@forEach
            if (dao.hasInboxRecord(chainId, recordId)) return@forEach
            val relaySeq = wire?.relaySeq ?: page.nextSeq

            fun quarantine(reason: String) {
                inboxRecords +=
                    inboxRecord(
                        chainId = chainId,
                        recordId = recordId,
                        relaySeq = relaySeq,
                        rawJson = pageRecord.rawJson,
                        status = "quarantined",
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
            val device = dao.getDeviceSync(wire.deviceId)
            if (device == null || device.chainId != chainId || device.publicKey.isBlank()) {
                quarantine("Record signer is not a valid device in the active chain")
                return@forEach
            }
            val signatureValid =
                runCatching {
                    crypto.verifySyncRecord(pageRecord.rawJson, device.publicKey)
                }.getOrDefault(false)
            if (!signatureValid) {
                quarantine("Record signature is invalid")
                return@forEach
            }
            val payloadJson =
                runCatching {
                    crypto.decryptSyncRecord(pageRecord.rawJson, keys.encryptionKeyBase64Url)
                }.getOrNull()
            if (payloadJson.isNullOrBlank()) {
                quarantine("Record payload could not be decrypted")
                return@forEach
            }
            val payload = runCatching { json.decodeFromString<DataPayload>(payloadJson) }.getOrNull()
            if (payload == null || payload.schema != wire.collectionName || payload.operation != wire.action) {
                quarantine("Decrypted payload metadata did not match its signed envelope")
                return@forEach
            }
            if (payload.lamport < 0L) {
                quarantine("Decrypted payload contained a negative Lamport clock")
                return@forEach
            }
            decryptedCandidates +=
                DecryptedCandidate(
                    recordId = recordId,
                    relaySeq = relaySeq,
                    lamport = payload.lamport,
                    rawRecordJson = pageRecord.rawJson,
                    payloadJson = payloadJson,
                )
        }

        val applyResults =
            try {
                recordProcessor.applyEvents(decryptedCandidates.map(DecryptedCandidate::payloadJson))
            } catch (expectedFailure: Exception) {
                val error = expectedFailure
                return PallaSyncHttpResult.Retryable(
                    "Local sync apply failed; relay cursor was retained: ${error.message}",
                )
            }
        if (applyResults.size != decryptedCandidates.size) {
            return PallaSyncHttpResult.ProtocolError("Sync applier returned an invalid result count")
        }
        decryptedCandidates.zip(applyResults).forEach { (candidate, result) ->
            inboxRecords +=
                when (result) {
                    PallaSyncApplyResult.Applied -> {
                        inboxRecord(
                            chainId,
                            candidate.recordId,
                            candidate.relaySeq,
                            candidate.rawRecordJson,
                            status = "applied",
                        )
                    }

                    is PallaSyncApplyResult.Quarantined -> {
                        inboxRecord(
                            chainId,
                            candidate.recordId,
                            candidate.relaySeq,
                            candidate.rawRecordJson,
                            status = "quarantined",
                            reason = result.reason,
                        )
                    }
                }
        }

        try {
            val maxLamport = decryptedCandidates.maxOfOrNull(DecryptedCandidate::lamport) ?: 0L
            dao.commitInboxPage(chainId, page.nextSeq, maxLamport, inboxRecords)
        } catch (expectedFailure: Exception) {
            val error = expectedFailure
            return PallaSyncHttpResult.Retryable(
                "Could not commit the inbox page; relay cursor was retained: ${error.message}",
            )
        }
        return PallaSyncHttpResult.Success(Unit)
    }

    suspend fun getDeviceViewHistory(deviceId: String): List<Illust> {
        return withContext(Dispatchers.IO) {
            operationMutex.withLock {
                val state =
                    db.pallaSyncDao().getAllChainStates().singleOrNull()
                        ?: return@withLock emptyList()
                val keys = keysForChainLocked(state.chainId) ?: return@withLock emptyList()
                val baseUrl =
                    when (val normalized = PallaSyncUrls.normalize(getServerUrl())) {
                        is PallaSyncHttpResult.Success -> normalized.value
                        else -> return@withLock emptyList()
                    }
                when (fetchDevicesLocked(baseUrl, state.chainId)) {
                    is PallaSyncHttpResult.Success -> {
                        Unit
                    }

                    PallaSyncHttpResult.Gone -> {
                        clearLocalChainLocked()
                        return@withLock emptyList()
                    }

                    else -> {
                        return@withLock emptyList()
                    }
                }
                val signer =
                    db
                        .pallaSyncDao()
                        .getDeviceSync(deviceId)
                        ?.takeIf { it.chainId == state.chainId }
                        ?: return@withLock emptyList()

                val history = linkedMapOf<Long, Illust>()
                var afterSeq = 0L
                do {
                    val pageResult = fetchRecordsPage(baseUrl, state.chainId, afterSeq)
                    if (pageResult == PallaSyncHttpResult.Gone) {
                        clearLocalChainLocked()
                        return@withLock emptyList()
                    }
                    if (pageResult !is PallaSyncHttpResult.Success) break
                    val page = pageResult.value
                    if (
                        page.nextSeq < afterSeq ||
                        (page.nextSeq == afterSeq && (page.hasMore || page.records.isNotEmpty()))
                    ) {
                        log("Stopped device-history paging on a non-advancing relay cursor")
                        break
                    }
                    page.records.forEach { pageRecord ->
                        val record = pageRecord.wireRecord ?: return@forEach
                        if (record.chainId != state.chainId ||
                            record.protocolVersion != PALLASYNC_PROTOCOL_VERSION ||
                            record.deviceId != deviceId ||
                            record.collectionName !in setOf(VIEW_HISTORY_SCHEMA_V1, VIEW_HISTORY_SCHEMA_V2)
                        ) {
                            return@forEach
                        }
                        if (!runCatching {
                                crypto.verifySyncRecord(pageRecord.rawJson, signer.publicKey)
                            }.getOrDefault(false)
                        ) {
                            return@forEach
                        }
                        val decrypted =
                            runCatching {
                                crypto.decryptSyncRecord(pageRecord.rawJson, keys.encryptionKeyBase64Url)
                            }.getOrNull() ?: return@forEach
                        val payload =
                            runCatching { json.decodeFromString<DataPayload>(decrypted) }.getOrNull()
                                ?: return@forEach
                        if (payload.schema != record.collectionName || payload.operation != record.action) {
                            return@forEach
                        }
                        applyViewHistoryPayload(history, payload)
                    }
                    afterSeq = page.nextSeq
                } while (page.hasMore)
                history.values.toList().asReversed()
            }
        }
    }

    private fun applyViewHistoryPayload(
        history: LinkedHashMap<Long, Illust>,
        payload: DataPayload,
    ) {
        when (payload.schema) {
            VIEW_HISTORY_SCHEMA_V1 -> {
                val viewed = (payload.body as? JsonObject)?.get("viewedIllusts") as? JsonArray ?: return
                viewed.asReversed().forEach { element ->
                    legacyIllust(element)?.let { illust ->
                        history.remove(illust.id)
                        history[illust.id] = illust
                    }
                }
            }

            VIEW_HISTORY_SCHEMA_V2 -> {
                if (!payload.entity_id.startsWith("viewed:")) return
                val id = payload.entity_id.removePrefix("viewed:").toLongOrNull() ?: return
                if (payload.operation == SYNC_OPERATION_DELETE) {
                    history.remove(id)
                    return
                }
                if (payload.operation != SYNC_OPERATION_UPSERT) return
                val body =
                    runCatching { json.decodeFromJsonElement<ViewedIllustBody>(payload.body) }.getOrNull()
                        ?: return
                if (body.id != id) return
                history.remove(body.id)
                history[body.id] = body.toIllust()
            }
        }
    }

    private suspend fun goneLocked(): SyncCycleOutcome {
        clearLocalChainLocked()
        log("The active PallaSync chain is gone; local chain material was cleared")
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
        // Cancel only after durable cleanup. A 410 may be handled by the poll job itself.
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

    private fun createInitialOutbox(
        state: ChainStateEntity,
        keys: PallaSyncKeySnapshot,
        deviceId: String,
        events: List<PallaSyncPendingEvent>,
        status: String,
    ): Pair<ChainStateEntity, List<OutboxEntity>> {
        var nextState = state
        val outbox =
            events.map { event ->
                val lamport = nextState.lamport + 1
                val payload =
                    DataPayload(
                        schema = event.schema,
                        entity_id = event.entityId,
                        operation = event.operation,
                        context = emptyMap(),
                        lamport = lamport,
                        created_at_ms = System.currentTimeMillis(),
                        body = event.body,
                    )
                val recordJson =
                    crypto.createSyncRecord(
                        chainId = state.chainId,
                        recordId =
                            com.yunfie.illustia.pallasync.util.UuidV7
                                .generateString(),
                        collectionName = event.schema,
                        action = event.operation,
                        payloadJson = json.encodeToString(payload),
                        deviceId = deviceId,
                        encryptionKeyBase64 = keys.encryptionKeyBase64Url,
                        signingKeyBase64 = keys.signingKeyBase64Url,
                    ) ?: error("Native sync record creation failed")
                nextState = nextState.copy(lamport = lamport)
                OutboxEntity(
                    chainId = state.chainId,
                    deviceSeq = lamport,
                    status = status,
                    eventJson = recordJson,
                )
            }
        return nextState to outbox
    }

    /**
     * Resolves the only key snapshot that is allowed to sign/decrypt [chainId].
     * A staged join survives process death: it is promoted when Room already
     * points at the candidate, or discarded when Room still points at the old chain.
     */
    private suspend fun activeChainStateLocked(): ChainStateEntity? {
        val dao = db.pallaSyncDao()
        val states = dao.getAllChainStates()
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
        val pending = keystore.getPendingChainKeys()
        if (pending?.chainId == chainId) {
            if (keystore.promotePendingChainKeys()) {
                return keystore.getActiveChainKeys()
            }
            return pending
        }

        val active = keystore.getActiveChainKeys() ?: return null
        val resolvedChainId = resolvedSnapshotChainId(active)
        if (resolvedChainId != chainId) return null

        if (pending != null) keystore.clearPendingChainKeys()
        if (active.chainId == null) {
            val upgraded = active.copy(chainId = resolvedChainId)
            if (keystore.saveActiveChainKeys(upgraded)) return upgraded
        }
        return active.copy(chainId = resolvedChainId)
    }

    private fun resolvedSnapshotChainId(snapshot: PallaSyncKeySnapshot): String? {
        return snapshot.chainId ?: runCatching {
            val derived = crypto.deriveKeys(snapshot.seedPhrase) ?: return@runCatching null
            json.parseToJsonElement(derived).jsonObject.string("chain_id")
        }.getOrNull()
    }

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

    private fun legacyIllust(element: JsonElement): Illust? {
        val item = element as? JsonObject ?: return null
        val id = item["id"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        return Illust(
            id = id,
            title = item.string("title").orEmpty(),
            type = item.string("type") ?: "illust",
            caption = "",
            artistId = 0,
            artistName = item.string("artistName").orEmpty(),
            artistAvatarUrl = null,
            squareImageUrl = item.string("imageUrl").orEmpty(),
            mediumImageUrl = item.string("imageUrl").orEmpty(),
            imageUrl = item.string("imageUrl").orEmpty(),
            originalImageUrl = null,
            tags = emptyList(),
            pageCount = item["pageCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1,
            isBookmarked = false,
        )
    }

    private fun ViewedIllustBody.toIllust() =
        Illust(
            id = id,
            title = title,
            type = type,
            caption = "",
            artistId = 0,
            artistName = artistName,
            artistAvatarUrl = null,
            squareImageUrl = imageUrl,
            mediumImageUrl = imageUrl,
            imageUrl = imageUrl,
            originalImageUrl = null,
            tags = emptyList(),
            pageCount = pageCount,
            isBookmarked = isBookmarked,
        )

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
