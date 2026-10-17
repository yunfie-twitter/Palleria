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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
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

        /** Reproduce the relay's deterministic staged-control append order for §17.5 receipts. */
        internal fun expectedReseedCommitEntries(
            history: History,
            commitBody: String,
        ): JsonArray {
            val body =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(commitBody)
                    .jsonObject
            check(body.keys == setOf("parameters", "epoch", "envelopes", "compaction", "admin_operation", "device_records")) {
                "C_SCHEMA_VIOLATION"
            }
            val entries = mutableListOf<JsonObject>()

            fun add(
                kind: String,
                values: Iterable<kotlinx.serialization.json.JsonElement>,
            ) {
                values.forEach { value ->
                    entries +=
                        buildJsonObject {
                            put("kind", kind)
                            put("object", value)
                        }
                }
            }
            add(
                "parameters",
                history.parameters.sortedBy {
                    it.jsonObject
                        .getValue("generation")
                        .jsonPrimitive.long
                },
            )
            add(
                "device",
                history.devices.sortedBy {
                    it.jsonObject
                        .getValue("device_id")
                        .jsonPrimitive.content
                },
            )
            add(
                "envelope",
                history.envelopes.sortedBy {
                    it.jsonObject
                        .getValue("envelope_id")
                        .jsonPrimitive.content
                },
            )
            add(
                "epoch",
                history.epochs.sortedBy {
                    it.jsonObject
                        .getValue("epoch")
                        .jsonPrimitive.long
                },
            )
            entries +=
                buildJsonObject {
                    put("kind", "parameters")
                    put("object", body.getValue("parameters"))
                }
            add(
                "device",
                body.getValue("device_records").jsonArray.sortedBy {
                    it.jsonObject
                        .getValue("device_id")
                        .jsonPrimitive.content
                },
            )
            add(
                "envelope",
                body.getValue("envelopes").jsonArray.sortedWith(
                    compareBy<kotlinx.serialization.json.JsonElement> {
                        it.jsonObject
                            .getValue("recipient_kind")
                            .jsonPrimitive.content
                    }.thenBy {
                        it.jsonObject["recipient_device_id"]
                            ?.jsonPrimitive
                            ?.content
                            .orEmpty()
                    },
                ),
            )
            entries +=
                buildJsonObject {
                    put("kind", "admin")
                    put("object", body.getValue("admin_operation"))
                }
            entries +=
                buildJsonObject {
                    put("kind", "epoch")
                    put("object", body.getValue("epoch"))
                }
            entries +=
                buildJsonObject {
                    put("kind", "compaction")
                    put("object", body.getValue("compaction"))
                }
            return JsonArray(entries)
        }

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
            val parameterHead = history.parameters.last()
            val epochHead = history.epochs.last()
            val fixedHead =
                history.copy(
                    parameters = JsonArray(listOf(parameterHead)),
                    epochs = JsonArray(listOf(epochHead)),
                    devices = JsonArray(emptyList()),
                    envelopes = JsonArray(emptyList()),
                    compactions = JsonArray(emptyList()),
                )
            check(
                canonicalBody(chainId, fixedHead).toByteArray(Charsets.UTF_8).size <= maxBytes,
            ) { "C_LOCAL_LIMIT" }
            val chunks = mutableListOf<History>()
            var parameters = mutableListOf<kotlinx.serialization.json.JsonElement>()
            var epochs = mutableListOf<kotlinx.serialization.json.JsonElement>()
            var devices = mutableListOf<kotlinx.serialization.json.JsonElement>()
            var envelopes = mutableListOf<kotlinx.serialization.json.JsonElement>()
            var compactions = mutableListOf<kotlinx.serialization.json.JsonElement>()

            fun materialize() =
                history.copy(
                    parameters = JsonArray((parameters + parameterHead).distinct()),
                    epochs = JsonArray((epochs + epochHead).distinct()),
                    devices = JsonArray(devices.toList()),
                    envelopes = JsonArray(envelopes.toList()),
                    compactions = JsonArray(compactions.toList()),
                )

            fun hasPayload() =
                parameters.isNotEmpty() ||
                    epochs.isNotEmpty() ||
                    devices.isNotEmpty() ||
                    envelopes.isNotEmpty() ||
                    compactions.isNotEmpty()

            fun flush() {
                if (!hasPayload()) return
                val chunk = materialize()
                check(canonicalBody(chainId, chunk).toByteArray(Charsets.UTF_8).size <= maxBytes) { "C_LOCAL_LIMIT" }
                chunks += chunk
                parameters = mutableListOf()
                epochs = mutableListOf()
                devices = mutableListOf()
                envelopes = mutableListOf()
                compactions = mutableListOf()
            }

            fun append(
                value: kotlinx.serialization.json.JsonElement,
                kind: Int,
            ) {
                fun destination() =
                    when (kind) {
                        0 -> parameters
                        1 -> epochs
                        2 -> devices
                        3 -> envelopes
                        else -> compactions
                    }
                var target = destination()
                target.add(value)
                val fits =
                    canonicalBody(chainId, materialize()).toByteArray(Charsets.UTF_8).size <= maxBytes
                if (!fits) {
                    target.removeAt(target.lastIndex)
                    check(hasPayload()) { "C_LOCAL_LIMIT" }
                    flush()
                    target = destination()
                    target.add(value)
                    check(
                        canonicalBody(chainId, materialize()).toByteArray(Charsets.UTF_8).size <= maxBytes,
                    ) { "C_LOCAL_LIMIT" }
                }
            }

            history.parameters.dropLast(1).forEach { append(it, 0) }
            history.epochs.dropLast(1).forEach { append(it, 1) }
            history.devices.forEach { append(it, 2) }
            history.envelopes.forEach { append(it, 3) }
            history.compactions.forEach { append(it, 4) }
            flush()
            if (chunks.isEmpty()) {
                val chunk = materialize()
                check(canonicalBody(chainId, chunk).toByteArray(Charsets.UTF_8).size <= maxBytes) { "C_LOCAL_LIMIT" }
                chunks += chunk
            }
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
