package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ProtocolDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** §10.3/§17.5: stage only previously verified signed source history under explicit A authority. */
internal class PallaSyncImportRelay(
    private val remote: PallaSyncRemoteService,
) {
    data class History(
        val parameters: JsonArray,
        val epochs: JsonArray,
        val devices: JsonArray,
        val envelopes: JsonArray,
        val compactions: JsonArray,
    )

    companion object {
        private const val HTTP_ACCEPTED = 202

        internal fun canonicalBody(
            chainId: String,
            history: History,
        ): String {
            val arrays =
                listOf(history.parameters, history.epochs, history.devices, history.envelopes, history.compactions)
            check(arrays.any(JsonArray::isNotEmpty)) { "C_MISSING_DEPENDENCY" }
            arrays.flatten().forEach { value ->
                check(value is JsonObject) { "C_SCHEMA_VIOLATION" }
                val objectChain = value["chain_id"] as? JsonPrimitive
                check(objectChain?.isString == true) { "C_SCHEMA_VIOLATION" }
                check(objectChain.content == chainId) { "C_RECOVERY_ROOT_MISMATCH" }
            }
            val body =
                buildJsonObject {
                    put("parameters_history", history.parameters)
                    put("epoch_history", history.epochs)
                    put("device_history", history.devices)
                    put("envelopes", history.envelopes)
                    put("compactions", history.compactions)
                }
            val text = checkNotNull(PallaSyncCore.canonicalJson(body.toString())) { "C_SCHEMA_VIOLATION" }
            check(text.toByteArray(Charsets.UTF_8).size <= PALLASYNC_HARD_LIMITS.maxRequestBytes) { "C_LOCAL_LIMIT" }
            return text
        }

        internal fun chunkHistories(
            chainId: String,
            history: History,
            maxBytes: Int = PALLASYNC_HARD_LIMITS.maxRequestBytes,
        ): List<History> {
            check(history.parameters.isNotEmpty() && history.epochs.isNotEmpty()) { "C_MISSING_DEPENDENCY" }
            check(maxBytes > 0) { "C_LOCAL_LIMIT" }
            val common =
                history.copy(
                    devices = JsonArray(emptyList()),
                    envelopes = JsonArray(emptyList()),
                    compactions = JsonArray(emptyList()),
                )
            val baseBytes = canonicalBody(chainId, common).toByteArray(Charsets.UTF_8).size
            check(baseBytes <= maxBytes) { "C_LOCAL_LIMIT" }
            val chunks = mutableListOf<History>()
            val devices = mutableListOf<kotlinx.serialization.json.JsonElement>()
            val envelopes = mutableListOf<kotlinx.serialization.json.JsonElement>()
            val compactions = mutableListOf<kotlinx.serialization.json.JsonElement>()
            var usedBytes = baseBytes

            fun flush() {
                if (devices.isEmpty() && envelopes.isEmpty() && compactions.isEmpty()) return
                val chunk =
                    common.copy(
                        devices = JsonArray(devices.toList()),
                        envelopes = JsonArray(envelopes.toList()),
                        compactions = JsonArray(compactions.toList()),
                    )
                check(canonicalBody(chainId, chunk).toByteArray(Charsets.UTF_8).size <= maxBytes) { "C_LOCAL_LIMIT" }
                chunks += chunk
                devices.clear()
                envelopes.clear()
                compactions.clear()
                usedBytes = baseBytes
            }

            fun append(
                value: kotlinx.serialization.json.JsonElement,
                destination: MutableList<kotlinx.serialization.json.JsonElement>,
            ) {
                val canonical = checkNotNull(PallaSyncCore.canonicalJson(value.toString())) { "C_SCHEMA_VIOLATION" }
                val itemBytes = canonical.toByteArray(Charsets.UTF_8).size
                val separatorBytes = if (destination.isEmpty()) 0 else 1
                if (usedBytes + itemBytes + separatorBytes > maxBytes) {
                    flush()
                    check(baseBytes + itemBytes <= maxBytes) { "C_LOCAL_LIMIT" }
                }
                destination += value
                usedBytes += itemBytes + if (destination.size == 1) 0 else 1
            }
            history.devices.forEach { append(it, devices) }
            history.envelopes.forEach { append(it, envelopes) }
            history.compactions.forEach { append(it, compactions) }
            flush()
            if (chunks.isEmpty()) chunks += common
            return chunks
        }
    }

    suspend fun stage(
        root: PallaSyncRootSession,
        target: HttpUrl,
        history: History,
    ): PallaSyncHttpResult<PallaSyncImportStageResponse.Staged> {
        check(root.operation == PallaSyncRootSession.Operation.RESEED) { "C_RECOVERY_AUTH_REQUIRED" }
        val chainId = root.identity.chainId
        val url = PallaSyncUrls.importChain(target)
        val origin = PallaSyncUrls.extractOrigin(target)
        var staged: PallaSyncImportStageResponse.Staged? = null
        var terminal: PallaSyncHttpResult<PallaSyncImportStageResponse.Staged>? = null
        forEachImportChunk(chunkHistories(chainId, history)) { chunk ->
            val body = canonicalBody(chainId, chunk)
            val token =
                checkNotNull(root.token(origin, "POST", url.encodedPath, body = body.toByteArray(Charsets.UTF_8))) {
                    "C_RELAY_POLICY_CONSENT_REQUIRED"
                }
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", PALLASYNC_MEDIA_TYPE)
                    .header("Authorization", "PallaSync $token")
                    .post(body.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
                    .build()
            val result =
                runInterruptible(Dispatchers.IO) {
                    remote.execute(request) { response ->
                        check(response.code == HTTP_ACCEPTED) { "C_SCHEMA_VIOLATION" }
                        PallaSyncHttpResult.Success(PallaSyncImportStageResponse.validate(response.pallaSyncBytes(), chainId))
                    }
                }
            val accepted = (result as? PallaSyncHttpResult.Success)?.value
            if (accepted == null) {
                terminal = result
                false
            } else {
                if (staged == null) {
                    staged = accepted
                } else {
                    check(staged == accepted) { "C_IMPORT_CONFLICT" }
                }
                true
            }
        }
        return terminal ?: PallaSyncHttpResult.Success(checkNotNull(staged) { "C_MISSING_DEPENDENCY" })
    }

    suspend fun stageFromVerifiedStore(
        root: PallaSyncRootSession,
        target: HttpUrl,
        dao: ProtocolDao,
    ): PallaSyncHttpResult<PallaSyncImportStageResponse.Staged> {
        check(root.operation == PallaSyncRootSession.Operation.RESEED) { "C_RECOVERY_AUTH_REQUIRED" }
        val history = PallaSyncImportHistoryBuilder(dao).build(root.identity.chainId)
        return stage(root, target, history)
    }
}

/** Treat an in-flight POST as an uncertain outcome on cancellation; never start a later chunk. */
internal suspend fun <T> forEachImportChunk(
    chunks: List<T>,
    action: suspend (T) -> Boolean,
) {
    for (chunk in chunks) {
        currentCoroutineContext().ensureActive()
        val hasNext = action(chunk)
        currentCoroutineContext().ensureActive()
        if (!hasNext) return
    }
}
