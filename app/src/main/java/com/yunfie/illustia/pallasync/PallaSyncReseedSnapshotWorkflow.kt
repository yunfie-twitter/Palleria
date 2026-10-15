package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import kotlinx.serialization.json.Json
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
