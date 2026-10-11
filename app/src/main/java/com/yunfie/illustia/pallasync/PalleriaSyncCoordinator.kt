package com.yunfie.illustia.pallasync

import android.content.Context
import android.os.Build
import com.yunfie.illustia.GlitchTipTelemetry
import com.yunfie.illustia.GlitchTipTelemetry.span
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
import kotlinx.serialization.json.JsonPrimitive
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
        val baseUrl =
            when (val normalized = PallaSyncUrls.normalize(serverUrl ?: getServerUrl())) {
                is PallaSyncHttpResult.Success -> normalized.value
                is PallaSyncHttpResult.ProtocolError -> return null.also { log(normalized.message) }
                else -> return null
            }
        val aud = PallaSyncUrls.extractOrigin(baseUrl)

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
                    if (contentType != JSON_MEDIA_TYPE.toString() && contentType != PALLASYNC_MEDIA_TYPE) {
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
                    if (healthBody.status != "ok" ||
                        (
                            healthBody.protocolVersion != PALLASYNC_PROTOCOL_VERSION &&
                                healthBody.protocolVersion != PALLASYNC_LEGACY_PROTOCOL_VERSION
                        )
                    ) {
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

        val chainId: String
        val deviceId: String
        val candidateKeys: PallaSyncKeySnapshot

        if (isGenesis) {
            val genesisBundleJson =
                runCatching {
                    crypto.createGenesisBundle(
                        seedPhrase = seedPhrase,
                        passphrase = "",
                        deviceName = deviceName,
                        keyProtection = "os-keystore",
                        aud = aud,
                    ) ?: error("Native genesis bundle creation failed")
                }.getOrElse {
                    log("Could not create PallaSync genesis bundle: ${it.message}")
                    return null
                }
            val bundle =
                runCatching { json.decodeFromString<PallaSyncGenesisBundle>(genesisBundleJson) }
                    .getOrElse {
                        log("Could not decode genesis bundle: ${it.message}")
                        return null
                    }
            chainId = bundle.chainId
            deviceId = bundle.deviceId

            val createChainRequest =
                Request
                    .Builder()
                    .url(PallaSyncUrls.chains(baseUrl))
                    .header("Accept", JSON_MEDIA_TYPE.toString())
                    .header("Authorization", "PallaSync ${bundle.adminCapabilityToken}")
                    .post(bundle.genesisRequestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            when (val created = remote.executeUnit(createChainRequest)) {
                is PallaSyncHttpResult.Success -> Unit
                is PallaSyncHttpResult.Retryable -> return null.also { log(created.message) }
                is PallaSyncHttpResult.ProtocolError -> return null.also { log(created.message) }
                PallaSyncHttpResult.Gone -> return null.also { log("The PallaSync service is unavailable") }
            }

            candidateKeys =
                PallaSyncKeySnapshot(
                    chainId = bundle.chainId,
                    seedPhrase = seedPhrase,
                    encryptionKeyBase64Url = bundle.recordKey,
                    signingKeyBase64Url = bundle.deviceSigningKey,
                    publicKeyBase64Url = bundle.devicePublicKey,
                    kexPrivateKeyBase64Url = bundle.deviceKexPrivateKey,
                    kexPublicKeyBase64Url = bundle.deviceKexPublicKey,
                    deviceMetaKeyBase64Url = bundle.deviceMetaKey,
                )
        } else {
            val rootKeysJson =
                crypto.deriveRootKeys(seedPhrase, "")
                    ?: return null.also { log("Native root key derivation failed") }
            val rootKeys =
                runCatching { json.parseToJsonElement(rootKeysJson).jsonObject }
                    .getOrNull() ?: return null.also { log("Could not parse derived root keys") }
            val derivedChainId =
                rootKeys["chain_id"]?.jsonPrimitive?.content
                    ?: return null.also { log("Derived keys did not contain a chain ID") }
            chainId = derivedChainId

            val paramsPath = "/pallasync/v3/chains/$chainId/parameters"
            val adminTokenForParams =
                crypto.createCapabilityToken(
                    chainId = chainId,
                    deviceId = null,
                    method = "GET",
                    path = paramsPath,
                    query = "",
                    bodyJson = "",
                    signingKeyBase64 = rootKeys["admin_private_key"]?.jsonPrimitive?.content ?: "",
                    ttlMs = 120_000L,
                    aud = aud,
                    signerKind = "admin",
                ) ?: return null.also { log("Could not generate admin capability token for parameters") }

            val paramsRequest =
                Request
                    .Builder()
                    .url(PallaSyncUrls.parameters(baseUrl, chainId))
                    .header("Accept", JSON_MEDIA_TYPE.toString())
                    .header("Authorization", "PallaSync $adminTokenForParams")
                    .get()
                    .build()
            val paramsObj =
                when (
                    val res =
                        remote.execute(paramsRequest) { resp ->
                            val body = resp.body?.string() ?: return@execute PallaSyncHttpResult.ProtocolError("Empty parameters response")
                            val parsed =
                                runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
                                    ?: return@execute PallaSyncHttpResult.ProtocolError("Invalid parameters response")
                            PallaSyncHttpResult.Success(parsed)
                        }
                ) {
                    is PallaSyncHttpResult.Success -> res.value
                    PallaSyncHttpResult.Gone -> return null.also { log("Chain deleted") }
                    is PallaSyncHttpResult.Retryable -> return null.also { log(res.message) }
                    is PallaSyncHttpResult.ProtocolError -> return null.also { log(res.message) }
                }
            val parametersHash = paramsObj["parameters_hash"]?.jsonPrimitive?.content.orEmpty()
            val epochHash = paramsObj["epoch_hash"]?.jsonPrimitive?.content.orEmpty()
            val generation = paramsObj["generation"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

            val recoveryPath = "/pallasync/v3/chains/$chainId/epochs/0/recovery-envelope"
            val adminTokenForRecovery =
                crypto.createCapabilityToken(
                    chainId = chainId,
                    deviceId = null,
                    method = "GET",
                    path = recoveryPath,
                    query = "",
                    bodyJson = "",
                    signingKeyBase64 = rootKeys["admin_private_key"]?.jsonPrimitive?.content ?: "",
                    ttlMs = 120_000L,
                    aud = aud,
                    signerKind = "admin",
                ) ?: return null.also { log("Could not generate admin token for recovery envelope") }

            val recoveryUrl =
                baseUrl
                    .newBuilder()
                    .addPathSegment("pallasync")
                    .addPathSegment("v3")
                    .addPathSegment("chains")
                    .addPathSegment(chainId)
                    .addPathSegment("epochs")
                    .addPathSegment("0")
                    .addPathSegment("recovery-envelope")
                    .build()
            val recoveryRequest =
                Request
                    .Builder()
                    .url(recoveryUrl)
                    .header("Accept", JSON_MEDIA_TYPE.toString())
                    .header("Authorization", "PallaSync $adminTokenForRecovery")
                    .get()
                    .build()
            val recoveryEnvelopeJson =
                when (
                    val res =
                        remote.execute(recoveryRequest) { resp ->
                            val body =
                                resp.body?.string() ?: return@execute PallaSyncHttpResult.ProtocolError("Empty recovery envelope response")
                            val parsed = runCatching { json.parseToJsonElement(body).jsonObject["envelope"] }.getOrNull()
                            PallaSyncHttpResult.Success(parsed?.toString() ?: body)
                        }
                ) {
                    is PallaSyncHttpResult.Success -> res.value
                    PallaSyncHttpResult.Gone -> return null.also { log("Chain deleted") }
                    is PallaSyncHttpResult.Retryable -> return null.also { log(res.message) }
                    is PallaSyncHttpResult.ProtocolError -> return null.also { log(res.message) }
                }

            val enrollmentBundleJson =
                runCatching {
                    crypto.createMnemonicEnrollmentBundle(
                        seedPhrase = seedPhrase,
                        passphrase = "",
                        deviceName = deviceName,
                        keyProtection = "os-keystore",
                        generation = generation,
                        epoch = 0,
                        recoveryEnvelopeJson = recoveryEnvelopeJson,
                        expectedParametersHash = parametersHash,
                        expectedEpochHash = epochHash,
                        aud = aud,
                    ) ?: error("Native mnemonic enrollment bundle creation failed")
                }.getOrElse {
                    log("Could not create mnemonic enrollment bundle: ${it.message}")
                    return null
                }
            val bundle =
                runCatching { json.decodeFromString<PallaSyncMnemonicEnrollmentBundle>(enrollmentBundleJson) }
                    .getOrElse {
                        log("Could not decode enrollment bundle: ${it.message}")
                        return null
                    }
            deviceId = bundle.deviceId

            val enrollRequest =
                Request
                    .Builder()
                    .url(PallaSyncUrls.enrollDevice(baseUrl, chainId))
                    .header("Accept", JSON_MEDIA_TYPE.toString())
                    .header("Authorization", "PallaSync ${bundle.adminCapabilityToken}")
                    .post(bundle.enrollRequestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            when (val enrolled = remote.executeUnit(enrollRequest)) {
                is PallaSyncHttpResult.Success -> Unit
                is PallaSyncHttpResult.Retryable -> return null.also { log(enrolled.message) }
                is PallaSyncHttpResult.ProtocolError -> return null.also { log(enrolled.message) }
                PallaSyncHttpResult.Gone -> return null.also { log("Chain deleted") }
            }

            val ackRequest =
                Request
                    .Builder()
                    .url(PallaSyncUrls.deviceKeysAck(baseUrl, chainId, deviceId))
                    .header("Accept", JSON_MEDIA_TYPE.toString())
                    .header("Authorization", "PallaSync ${bundle.deviceKeysAckToken}")
                    .post(bundle.keysAckRequestBodyJson.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
            when (val acked = remote.executeUnit(ackRequest)) {
                is PallaSyncHttpResult.Success -> Unit
                is PallaSyncHttpResult.Retryable -> return null.also { log(acked.message) }
                is PallaSyncHttpResult.ProtocolError -> return null.also { log(acked.message) }
                PallaSyncHttpResult.Gone -> return null.also { log("Chain deleted") }
            }

            candidateKeys =
                PallaSyncKeySnapshot(
                    chainId = bundle.chainId,
                    seedPhrase = seedPhrase,
                    encryptionKeyBase64Url = bundle.recordKey,
                    signingKeyBase64Url = bundle.deviceSigningKey,
                    publicKeyBase64Url = bundle.devicePublicKey,
                    kexPrivateKeyBase64Url = bundle.deviceKexPrivateKey,
                    kexPublicKeyBase64Url = bundle.deviceKexPublicKey,
                    deviceMetaKeyBase64Url = bundle.deviceMetaKey,
                )
        }

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
            val operation =
                PallaSyncOperation(
                    entityId = event.entityId,
                    operation = event.operation,
                    lamport = lamport,
                    createdAtMs = System.currentTimeMillis(),
                    context = event.context,
                    body = (event.body as? JsonObject) ?: JsonObject(emptyMap()),
                )
            val innerRecord =
                PallaSyncInnerRecord(
                    collection = event.schema,
                    deviceSeq = lamport,
                    prevRecordHash = null,
                    operations = listOf(operation),
                )
            val recordJson =
                crypto.createSyncRecord(
                    chainId = state.chainId,
                    generation = 0,
                    recordId =
                        com.yunfie.illustia.pallasync.util.UuidV7
                            .generateString(),
                    deviceId = deviceId,
                    epoch = 0,
                    collectionTag = null,
                    innerRecordJson = json.encodeToString(innerRecord),
                    recordKeyBase64 = keys.encryptionKeyBase64Url,
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

    private suspend fun synchronizeOnceLocked(baseUrlOverride: HttpUrl? = null): SyncCycleOutcome =
        GlitchTipTelemetry.traceAsync("pallasync.sync", "sync.operation") { tx ->
            val dao = db.pallaSyncDao()
            val chainState =
                activeChainStateLocked() ?: run {
                    keystore.clearPendingChainKeys()
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

            // Device validation/self-heal must happen before old queued records are
            // uploaded, otherwise a missing relay-side device can block forever on 400.
            val devices = tx.span("sync.fetch_devices") { fetchDevicesLocked(baseUrl, chainState.chainId) }
            when (devices) {
                is PallaSyncHttpResult.Success -> Unit
                PallaSyncHttpResult.Gone -> return@traceAsync goneLocked()
                is PallaSyncHttpResult.Retryable -> return@traceAsync SyncCycleOutcome.Retryable.also { log(devices.message) }
                is PallaSyncHttpResult.ProtocolError -> return@traceAsync SyncCycleOutcome.ProtocolError.also { log(devices.message) }
            }
            val pushed = tx.span("sync.process_outbox") { processOutboxLocked(baseUrl, chainState.chainId) }
            when (pushed) {
                is PallaSyncHttpResult.Success -> Unit
                PallaSyncHttpResult.Gone -> return@traceAsync goneLocked()
                is PallaSyncHttpResult.Retryable -> return@traceAsync SyncCycleOutcome.Retryable.also { log(pushed.message) }
                is PallaSyncHttpResult.ProtocolError -> return@traceAsync SyncCycleOutcome.ProtocolError.also { log(pushed.message) }
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
            SyncCycleOutcome.Success
        }

    private fun makeCapabilityToken(
        baseUrl: HttpUrl,
        chainId: String,
        method: String,
        path: String,
        query: String = "",
        bodyJson: String = "",
        signerKind: String = "device",
    ): String? {
        val keys = keysForChainLocked(chainId)
        val deviceId = keystore.getDeviceId()
        if (keys == null || (signerKind == "device" && deviceId == null)) {
            return null
        }
        val aud = PallaSyncUrls.extractOrigin(baseUrl)
        return crypto.createCapabilityToken(
            chainId = chainId,
            deviceId = if (signerKind == "device") deviceId else null,
            method = method,
            path = path,
            query = query,
            bodyJson = bodyJson,
            signingKeyBase64 = keys.signingKeyBase64Url,
            ttlMs = 120_000L,
            aud = aud,
            signerKind = signerKind,
        )
    }

    private suspend fun processOutboxLocked(
        baseUrl: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<Unit> {
        val dao = db.pallaSyncDao()
        dao.deleteAcceptedEvents()
        val events = dao.getQueuedEvents().filter { it.chainId == chainId }
        if (events.isEmpty()) return PallaSyncHttpResult.Success(Unit)
        val batchRecordsJson = events.joinToString(separator = ",", prefix = "[", postfix = "]") { it.eventJson }
        val batchBody = "{\"records\":$batchRecordsJson}"
        val path = "/pallasync/v3/chains/$chainId/records"
        val batchToken = makeCapabilityToken(baseUrl, chainId, "POST", path, "", batchBody)
        val batchRequestBuilder =
            Request
                .Builder()
                .url(PallaSyncUrls.recordsEndpoint(baseUrl, chainId))
                .header("Accept", JSON_MEDIA_TYPE.toString())
        if (batchToken != null) {
            batchRequestBuilder.header("Authorization", "PallaSync $batchToken")
        }
        val batchRequest = batchRequestBuilder.post(batchBody.toRequestBody(JSON_MEDIA_TYPE)).build()

        return when (val result = remote.executeUnit(batchRequest)) {
            is PallaSyncHttpResult.Success -> {
                events.forEach { dao.deleteOutboxEvent(it.id) }
                log("Uploaded and removed ${events.size} accepted sync record(s) in a single batch")
                PallaSyncHttpResult.Success(Unit)
            }

            is PallaSyncHttpResult.ProtocolError -> {
                if (result.statusCode == 400) {
                    log("Batch upload failed, falling back to sequential upload to isolate the bad record")
                    uploadSequentialOutboxEvents(baseUrl, chainId, events)
                } else {
                    result
                }
            }

            else -> {
                result
            }
        }
    }

    private suspend fun uploadSequentialOutboxEvents(
        baseUrl: HttpUrl,
        chainId: String,
        events: List<OutboxEntity>,
    ): PallaSyncHttpResult<Unit> {
        val dao = db.pallaSyncDao()
        val path = "/pallasync/v3/chains/$chainId/records"
        var accepted = 0
        var errorResult: PallaSyncHttpResult<Unit>? = null
        var index = 0

        while (index < events.size && errorResult == null) {
            val event = events[index++]
            val singleBody = "{\"records\":[${event.eventJson}]}"
            val singleToken = makeCapabilityToken(baseUrl, chainId, "POST", path, "", singleBody)
            val singleRequestBuilder =
                Request
                    .Builder()
                    .url(PallaSyncUrls.recordsEndpoint(baseUrl, chainId))
                    .header("Accept", JSON_MEDIA_TYPE.toString())
            if (singleToken != null) {
                singleRequestBuilder.header("Authorization", "PallaSync $singleToken")
            }
            val request = singleRequestBuilder.post(singleBody.toRequestBody(JSON_MEDIA_TYPE)).build()
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
                        errorResult = result
                    }
                }

                else -> {
                    errorResult = result
                }
            }
        }
        if (accepted > 0) log("Uploaded and removed $accepted accepted sync record(s)")
        return errorResult ?: PallaSyncHttpResult.Success(Unit)
    }

    private suspend fun fetchDevicesLocked(
        baseUrl: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<Unit> {
        val path = "/pallasync/v3/chains/$chainId/devices"
        val token = makeCapabilityToken(baseUrl, chainId, "GET", path, "", "")
        val requestBuilder =
            Request
                .Builder()
                .url(PallaSyncUrls.devices(baseUrl, chainId))
                .header("Accept", JSON_MEDIA_TYPE.toString())
        if (token != null) {
            requestBuilder.header("Authorization", "PallaSync $token")
        }
        val request = requestBuilder.get().build()
        val responseResult =
            remote.execute(request) { response ->
                val body =
                    response.body?.string()
                        ?: return@execute PallaSyncHttpResult.ProtocolError("Device response body was empty")
                parseDeviceIdsResponseBody(json, body)
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
            if (device.chainId != chainId ||
                (
                    device.protocolVersion != PALLASYNC_PROTOCOL_VERSION &&
                        device.protocolVersion != PALLASYNC_LEGACY_PROTOCOL_VERSION
                )
            ) {
                log("Ignored a device record for the wrong chain or protocol")
                return@forEach
            }
            activeDeviceIds += device.deviceId
            if (!runCatching { crypto.verifyDeviceRecord(rawDevice) }.getOrDefault(false)) {
                log("Ignored an invalid device signature for ${device.deviceId.take(8)}")
                if (device.deviceId == myDeviceId) shouldSelfHeal = true
                dao.insertDevice(
                    PallaSyncDeviceEntity(
                        deviceId = device.deviceId,
                        chainId = chainId,
                        deviceName = "Device ${device.deviceId.take(4)}",
                        publicKey = device.devicePublicKey,
                        joinedAtMs = device.createdAtMs,
                    ),
                )
                return@forEach
            }

            val decryptedName =
                runCatching {
                    crypto
                        .decryptDeviceRecord(
                            recordJson = rawDevice,
                            deviceMetaKeyBase64 = keys.deviceMetaKeyBase64Url,
                        )?.let { json.decodeFromString<PallaSyncDeviceMetaPlaintext>(it).name }
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
            val body =
                response.body?.string()
                    ?: return@execute PallaSyncHttpResult.ProtocolError("Relay page body was empty")

            parseRecordsResponseBody(json, body, afterSeq)
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
            if (wire.chainId != chainId ||
                (
                    wire.protocolVersion != PALLASYNC_PROTOCOL_VERSION &&
                        wire.protocolVersion != PALLASYNC_LEGACY_PROTOCOL_VERSION
                )
            ) {
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
            val innerRecord = runCatching { json.decodeFromString<PallaSyncInnerRecord>(payloadJson) }.getOrNull()
            val maxLamport =
                if (innerRecord != null) {
                    innerRecord.operations.maxOfOrNull { it.lamport } ?: 0L
                } else {
                    val payload = runCatching { json.decodeFromString<DataPayload>(payloadJson) }.getOrNull()
                    if (payload == null || payload.lamport < 0L) {
                        quarantine("Decrypted payload metadata was invalid")
                        return@forEach
                    }
                    payload.lamport
                }
            decryptedCandidates +=
                DecryptedCandidate(
                    recordId = recordId,
                    relaySeq = relaySeq,
                    lamport = maxLamport,
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
                        val isCompatibleProtocol =
                            record.protocolVersion == PALLASYNC_PROTOCOL_VERSION ||
                                record.protocolVersion == PALLASYNC_LEGACY_PROTOCOL_VERSION
                        if (record.chainId != state.chainId || !isCompatibleProtocol || record.deviceId != deviceId) {
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
                        val innerRecord = runCatching { json.decodeFromString<PallaSyncInnerRecord>(decrypted) }.getOrNull()
                        if (innerRecord != null) {
                            if (innerRecord.collection in setOf(VIEW_HISTORY_SCHEMA_V1, VIEW_HISTORY_SCHEMA_V2, VIEW_HISTORY_SCHEMA_V3)) {
                                innerRecord.operations.forEach { op ->
                                    val payload =
                                        DataPayload(
                                            schema = innerRecord.collection,
                                            entity_id = op.entityId,
                                            operation = op.operation,
                                            context = emptyMap(),
                                            lamport = op.lamport,
                                            created_at_ms = op.createdAtMs,
                                            body = op.body,
                                        )
                                    applyViewHistoryPayload(history, payload)
                                }
                            }
                        } else {
                            val payload = runCatching { json.decodeFromString<DataPayload>(decrypted) }.getOrNull() ?: return@forEach
                            if (payload.schema in setOf(VIEW_HISTORY_SCHEMA_V1, VIEW_HISTORY_SCHEMA_V2, VIEW_HISTORY_SCHEMA_V3)) {
                                applyViewHistoryPayload(history, payload)
                            }
                        }
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

            VIEW_HISTORY_SCHEMA_V2, VIEW_HISTORY_SCHEMA_V3 -> {
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
                val operation =
                    PallaSyncOperation(
                        entityId = event.entityId,
                        operation = event.operation,
                        lamport = lamport,
                        createdAtMs = System.currentTimeMillis(),
                        context = event.context,
                        body = (event.body as? JsonObject) ?: JsonObject(emptyMap()),
                    )
                val innerRecord =
                    PallaSyncInnerRecord(
                        collection = event.schema,
                        deviceSeq = lamport,
                        prevRecordHash = null,
                        operations = listOf(operation),
                    )
                val recordJson =
                    crypto.createSyncRecord(
                        chainId = state.chainId,
                        generation = 0,
                        recordId =
                            com.yunfie.illustia.pallasync.util.UuidV7
                                .generateString(),
                        deviceId = deviceId,
                        epoch = 0,
                        collectionTag = null,
                        innerRecordJson = json.encodeToString(innerRecord),
                        recordKeyBase64 = keys.encryptionKeyBase64Url,
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
            val derived = crypto.deriveRootKeys(snapshot.seedPhrase, "") ?: return@runCatching null
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
            tags = tags,
            pageCount = pageCount,
            isBookmarked = isBookmarked,
            xRestrict = xRestrict,
            illustAiType = illustAiType,
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
