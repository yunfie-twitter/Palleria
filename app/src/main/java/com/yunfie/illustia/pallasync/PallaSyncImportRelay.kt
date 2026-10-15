package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ProtocolDao
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
    }

    fun stage(
        root: PallaSyncRootSession,
        target: HttpUrl,
        history: History,
    ): PallaSyncHttpResult<PallaSyncImportStageResponse.Staged> {
        check(root.operation == PallaSyncRootSession.Operation.RESEED) { "C_RECOVERY_AUTH_REQUIRED" }
        val chainId = root.identity.chainId
        val body = canonicalBody(chainId, history)
        val url = PallaSyncUrls.importChain(target)
        val origin = PallaSyncUrls.extractOrigin(target)
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
        return remote.execute(request) { response ->
            check(response.code == HTTP_ACCEPTED) { "C_SCHEMA_VIOLATION" }
            PallaSyncHttpResult.Success(PallaSyncImportStageResponse.validate(response.pallaSyncBytes(), chainId))
        }
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
