package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/** Room-backed §17.5 snapshot work stays outside the JNI-owned secret holder. */
internal suspend fun PallaSyncPreparedReseed.produceSnapshot(
    database: PallaSyncDatabase,
    stagedLogId: String,
    compactionId: String,
    journal: PallaSyncSnapshotPublicationJournal,
    publishPart: suspend (recordId: String, signedJson: String, recordHash: String) -> Unit,
): PallaSyncProducedSnapshot {
    val result = publicResult
    val chain = result.chainId
    val source = checkNotNull(database.pallaSyncDao().getChainState(chain)) { "C_MISSING_DEPENDENCY" }
    check(source.generation + 1L == result.generation && source.lamport >= 0) { "C_SNAPSHOT_INVALID" }
    check(isUuid4(stagedLogId) && journal.matches(chain, compactionId, result.epoch)) { "C_SNAPSHOT_INVALID" }
    val header =
        buildJsonObject {
            put("kind", "header")
            put("chain_id", chain)
            put("generation", result.generation)
            put("log_id", stagedLogId)
            put("covered_through_seq", 0)
            put("covered_log_hash", zeroHash(chain, stagedLogId))
            put("epoch_hash", result.epochHash)
            put("lamport_max", source.lamport)
        }
    val publicKey =
        Json
            .parseToJsonElement(result.deviceRecordJson)
            .jsonObject
            .getValue("device_public_key")
            .jsonPrimitive.content
    return openEpochSession().use { epochSession ->
        PallaSyncSnapshotProducer(database).produce(
            chainId = chain,
            compactionId = compactionId,
            deviceId = result.deviceId,
            epochSession = epochSession,
            signingSeed = ::signingSeedCopy,
            devicePublicKeyBase64Url = publicKey,
            journal = journal,
            reseedHeader = header,
            preservePendingOutbox = true,
            publishPart = publishPart,
        )
    }
}

/** Assemble the exact §10.3/§17.5 PUT payload after all snapshot parts are accepted. */
internal fun PallaSyncRootSession.createReseedCommitBody(
    prepared: PallaSyncPreparedReseed,
    currentParametersJson: String,
    currentEpochJson: String,
    snapshot: PallaSyncProducedSnapshot,
    createdAtMs: Long,
): String {
    check(operation == PallaSyncRootSession.Operation.RESEED) { "C_RECOVERY_AUTH_REQUIRED" }
    val result = prepared.publicResult
    check(identity.chainId == result.chainId && snapshot.header["generation"]?.jsonPrimitive?.content == result.generation.toString()) {
        "C_RECOVERY_ROOT_MISMATCH"
    }
    val keySnapshot = prepared.keySnapshot()
    check(snapshot.parts.isNotEmpty() && snapshot.parts.all { it.partIndex in snapshot.parts.indices }) { "C_SNAPSHOT_INVALID" }
    val unsignedCompaction =
        buildJsonObject {
            put("compaction_id", snapshot.compactionId)
            put("chain_id", result.chainId)
            put("generation", result.generation)
            put("device_id", result.deviceId)
            put("log_id", snapshot.header.getValue("log_id"))
            put("covered_through_seq", snapshot.header.getValue("covered_through_seq"))
            put("covered_log_hash", snapshot.header.getValue("covered_log_hash"))
            put("epoch_hash", result.epochHash)
            put(
                "snapshot_parts",
                JsonArray(
                    snapshot.parts.map { part ->
                        buildJsonObject {
                            put("record_id", part.recordId)
                            put("record_hash", part.recordHash)
                        }
                    },
                ),
            )
            put("state_sha256", snapshot.stateCommitment)
        }
    val compaction =
        PallaSyncDeviceSession.beginToken(keySnapshot.signingKeyBase64Url).use { device ->
            Json
                .parseToJsonElement(
                    device.signCompaction(unsignedCompaction.toString(), result.deviceId, keySnapshot.publicKeyBase64Url),
                ).jsonObject
        }
    val deviceRecords = JsonArray(listOf(Json.parseToJsonElement(result.deviceRecordJson)))
    val envelopes = JsonArray(result.envelopesJson.map { Json.parseToJsonElement(it) })
    val adminOperation =
        Json.parseToJsonElement(
            createReseedAdminOperation(
                currentParametersJson,
                currentEpochJson,
                result.parametersJson,
                result.epochJson,
                envelopes.toString(),
                compaction.toString(),
                deviceRecords.toString(),
                createdAtMs,
            ),
        )
    val body =
        buildJsonObject {
            put("parameters", Json.parseToJsonElement(result.parametersJson))
            put("epoch", Json.parseToJsonElement(result.epochJson))
            put("envelopes", envelopes)
            put("compaction", compaction)
            put("admin_operation", adminOperation)
            put("device_records", deviceRecords)
        }
    val canonical = checkNotNull(PallaSyncCore.canonicalJson(body.toString())) { "C_SCHEMA_VIOLATION" }
    check(canonical.toByteArray(Charsets.UTF_8).size <= PALLASYNC_HARD_LIMITS.maxRequestBytes) { "C_LOCAL_LIMIT" }
    return canonical
}

internal fun zeroHash(
    chainId: String,
    logId: String,
): String {
    val chain = Base64.getUrlDecoder().decode(chainId)
    check(chain.size == 32 && isUuid4(logId)) { "C_SNAPSHOT_INVALID" }
    val id = UUID.fromString(logId)
    val uuidBytes =
        ByteBuffer
            .allocate(16)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .array()
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("PALLASYNC-LOG-v3\u0000".toByteArray(Charsets.US_ASCII))
    digest.update(chain)
    digest.update(uuidBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest())
}

private fun isUuid4(value: String): Boolean =
    runCatching { UUID.fromString(value) }.getOrNull()?.let { it.toString() == value && it.version() == 4 && it.variant() == 2 } == true

/** A-scope generation commit; only a caller that proves the receipt may return success. */
internal class PallaSyncReseedCommitRelay(
    private val remote: PallaSyncRemoteService,
) {
    suspend fun commit(
        root: PallaSyncRootSession,
        target: HttpUrl,
        canonicalBody: String,
        currentParametersJson: String,
        currentEpochJson: String,
        expectedEntriesJson: String,
        targetPinJson: String,
        stagedSnapshotReceiptsJson: String,
        persistVerifiedTransparencyPage: suspend (
            items: List<JsonObject>,
            anchor: JsonObject,
            observedHead: JsonObject,
            expectedReceiptIndices: Set<Long>,
        ) -> Unit,
        confirmStagedSnapshotReceipts: suspend () -> Unit,
        transparencyResume: PallaSyncReseedTransparencyVerifier.Resume? = null,
    ): PallaSyncHttpResult<JsonObject> {
        check(root.operation == PallaSyncRootSession.Operation.RESEED) { "C_RECOVERY_AUTH_REQUIRED" }
        val bodyBytes = canonicalBody.toByteArray(Charsets.UTF_8)
        check(bodyBytes.size <= PALLASYNC_HARD_LIMITS.maxRequestBytes) { "C_LOCAL_LIMIT" }
        val body = Json.parseToJsonElement(canonicalBody).jsonObject
        check(
            body.keys == setOf("parameters", "epoch", "envelopes", "compaction", "admin_operation", "device_records") &&
                checkNotNull(PallaSyncCore.canonicalJson(canonicalBody)) == canonicalBody &&
                body
                    .getValue("parameters")
                    .jsonObject
                    .getValue("chain_id")
                    .jsonPrimitive.content == root.identity.chainId,
        ) { "C_SCHEMA_VIOLATION" }
        currentCoroutineContext().ensureActive()
        val url = PallaSyncUrls.parameters(target, root.identity.chainId)
        val origin = PallaSyncUrls.extractOrigin(target)
        val token =
            checkNotNull(root.token(origin, "PUT", url.encodedPath, body = bodyBytes)) {
                "C_RELAY_POLICY_CONSENT_REQUIRED"
            }
        val request =
            Request
                .Builder()
                .url(url)
                .header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $token")
                .put(canonicalBody.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
                .build()
        val committed =
            runInterruptible(Dispatchers.IO) {
                remote.execute(request, migrationDestination = true, explicitRootAuthorization = true) { response ->
                    check(response.code == HTTP_OK) { "C_SCHEMA_VIOLATION" }
                    val checkpoint =
                        PallaSyncCore.validateReseedCommitAcceptance(
                            response.pallaSyncBytes(),
                            buildJsonObject {
                                put("parameters", body.getValue("parameters"))
                                put("epoch", body.getValue("epoch"))
                                put("envelopes", body.getValue("envelopes"))
                                put("compaction", body.getValue("compaction"))
                                put("device_records", body.getValue("device_records"))
                            }.toString(),
                            body.getValue("admin_operation").toString(),
                            currentParametersJson,
                            currentEpochJson,
                            expectedEntriesJson,
                            targetPinJson,
                        )
                    PallaSyncHttpResult.Success(Json.parseToJsonElement(checkpoint).jsonObject)
                }
            }
        val checkpoint = (committed as? PallaSyncHttpResult.Success)?.value ?: return committed.failure()
        val snapshotReceipts = Json.parseToJsonElement(stagedSnapshotReceiptsJson) as? JsonArray ?: error("C_SCHEMA_VIOLATION")
        val commitReceipts = checkpoint.getValue("receipts").jsonArray
        val allReceipts = JsonArray(snapshotReceipts + commitReceipts)
        val targetPin = Json.parseToJsonElement(targetPinJson).jsonObject
        val verifiedTransparency =
            when (
                val result =
                    PallaSyncReseedTransparencyVerifier(remote).verifySuffix(
                        root,
                        target,
                        targetPin,
                        allReceipts,
                        persistVerifiedTransparencyPage,
                        transparencyResume,
                    )
            ) {
                is PallaSyncHttpResult.Success -> result.value
                else -> return result.failure()
            }
        val head = verifiedTransparency.getValue("head").jsonObject
        check(
            head.getValue("head_seq") == checkpoint.getValue("head_seq") &&
                head.getValue("head_hash") == checkpoint.getValue("head_hash") &&
                head.getValue("parameters_hash") == checkpoint.getValue("parameters_hash") &&
                head.getValue("epoch_hash") == checkpoint.getValue("epoch_hash"),
        ) { "C_LOG_HASH_MISMATCH" }
        confirmStagedSnapshotReceipts()
        return PallaSyncHttpResult.Success(
            buildJsonObject {
                checkpoint.forEach { (name, value) -> put(name, value) }
                put("transparency_anchor", verifiedTransparency.getValue("anchor"))
            },
        )
    }

    private companion object {
        const val HTTP_OK = 200

        fun PallaSyncHttpResult<*>.failure(): PallaSyncHttpResult<JsonObject> =
            when (this) {
                PallaSyncHttpResult.Gone -> PallaSyncHttpResult.Gone
                is PallaSyncHttpResult.ProtocolError -> this
                is PallaSyncHttpResult.Retryable -> this
                is PallaSyncHttpResult.Success -> error("C_SCHEMA_VIOLATION")
            }
    }
}
