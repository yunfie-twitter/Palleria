package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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

private fun zeroHash(
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
