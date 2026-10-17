package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity
import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import com.yunfie.illustia.pallasync.data.SnapshotPartEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.Request
import java.util.UUID

private const val SNAPSHOT_RECORD_UUID_VERSION = 4
private const val SNAPSHOT_RECORD_UUID_VARIANT = 2

/** §11.6: replay the explicitly selected relay in bounded pages, without materializing plaintext. */
@Suppress("TooManyFunctions", "LargeClass") // Selected-source history validation shares a single pinned relay context.
internal class PallaSyncInitialHistory(
    private val store: PallaSyncLocalStore,
    private val remote: PallaSyncRemoteService,
    private val policy: PallaSyncPolicyGate,
    private val root: PallaSyncRootSession?,
    private val base: HttpUrl,
    private val peerChainId: String? = null,
    private val peerAdminPublicKey: String? = null,
    private val importedAuthority: JsonObject? = null,
    private val peerToken: ((HttpUrl, String, String) -> String?)? = null,
    private val source: String? = "relay_full_replay",
    private val progress: (String) -> Unit = {},
) {
    private val origin = PallaSyncUrls.extractOrigin(base)
    private val chain = root?.identity?.chainId ?: checkNotNull(peerChainId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
    private val adminPublicKey = root?.identity?.adminPublicKey ?: checkNotNull(peerAdminPublicKey) { "C_BOOTSTRAP_TRUST_REQUIRED" }
    private val prefix = "/pallasync/v3/chains/$chain"
    private val json = Json

    suspend fun load(
        previous: JsonObject? = null,
        knownAnchor: JsonObject? = null,
    ): PallaSyncGenesisCheckpoint {
        progress("history_policy")
        if (previous == null) {
            policy.requireCurrent(origin, source)
        } else {
            policy.requireCurrent(origin, source)
            val pin = previous.getValue("pin").jsonObject
            check(
                pin.getValue("chain_id").jsonPrimitive.content == chain &&
                    previous.getValue("admin_public_key").jsonPrimitive.content == adminPublicKey &&
                    pin.getValue("origin").jsonPrimitive.content == origin &&
                    pin.getValue("public_key").jsonPrimitive.content == policy.trustedRelayKey(origin),
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        }
        val initial = previous ?: importedAuthority ?: initialAuthority()
        progress("history_log")
        val authority = replay(initial)
        val pin = authority.getValue("pin").jsonObject
        progress("history_cut")
        val cut =
            json
                .parseToJsonElement(
                    PallaSyncCore.validateInitialHistoryCut(authority.toString(), get(url("transparency/head"))),
                ).jsonObject
        val head = cut.getValue("head").jsonObject
        verifySnapshotCatalogue(
            pin,
            cut.getValue("generation").jsonPrimitive.long,
            authority.getValue("sequence").jsonPrimitive.long,
            head.getValue("min_available_seq").jsonPrimitive.long,
        )
        progress("history_transparency")
        val anchor = transparency(pin, cut, knownAnchor)
        return PallaSyncGenesisCheckpoint(
            logId = pin.getValue("log_id").jsonPrimitive.content,
            headSeq = authority.getValue("sequence").jsonPrimitive.long,
            headHash = authority.getValue("head_hash").jsonPrimitive.content,
            parametersHash = head.getValue("parameters_hash").jsonPrimitive.content,
            epochHash = head.getValue("epoch_hash").jsonPrimitive.content,
            transparencyId = anchor.getValue("transparency_id").jsonPrimitive.content,
            transparencyIndex = anchor.getValue("index").jsonPrimitive.long,
            transparencyHash = anchor.getValue("hash").jsonPrimitive.content,
            authority = authority,
            generation = cut.getValue("generation").jsonPrimitive.long,
            dataZero = cut.getValue("data_zero").jsonPrimitive.content,
        )
    }

    /** §10.11: fetch one immutable part and verify its null-sequence receipt under the selected relay pin. */
    suspend fun fetchSnapshotPart(
        recordId: String,
        pinJson: String,
    ): JsonObject {
        val uuid = runCatching { UUID.fromString(recordId) }.getOrNull()
        check(
            uuid != null &&
                uuid.version() == SNAPSHOT_RECORD_UUID_VERSION &&
                uuid.variant() == SNAPSHOT_RECORD_UUID_VARIANT &&
                uuid.toString() == recordId,
        ) { "C_SCHEMA_VIOLATION" }
        val pin = Json.parseToJsonElement(pinJson).jsonObject
        check(pin.getValue("chain_id").jsonPrimitive.content == chain) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val response = get(url("snapshots/parts/$recordId"))
        return json
            .parseToJsonElement(
                PallaSyncSnapshotCore.validateSnapshotPartResponse(
                    response,
                    recordId,
                    pinJson,
                ),
            ).jsonObject
    }

    /** §10.11 + §16.6: persist only the receipt-checked, compaction-listed opaque record. */
    suspend fun fetchAndStageSnapshotPart(
        partIndex: Int,
        pinJson: String,
        staging: PallaSyncSnapshotStaging,
    ): SnapshotPartEntity {
        val recordId = staging.expectedRecordId(partIndex)
        val response = fetchSnapshotPart(recordId, pinJson)
        return staging.retainDownloaded(partIndex, response.toString())
    }

    /** Stream the complete listed part sequence, revalidating durable receipts and fetching gaps. */
    suspend fun fetchAndStageSnapshotParts(
        pinJson: String,
        staging: PallaSyncSnapshotStaging,
        consumePart: suspend (Int, SnapshotPartEntity) -> Unit,
    ) {
        for (partIndex in 0 until staging.partCount) {
            currentCoroutineContext().ensureActive()
            val part =
                staging.verifiedDownloaded(partIndex, pinJson)
                    ?: fetchAndStageSnapshotPart(partIndex, pinJson, staging)
            consumePart(partIndex, part)
        }
    }

    /**
     * Verify every staged part against the replay-authenticated compaction and registry, then
     * stream its canonical entries into the native bounded commitment verifier. Installation is
     * normally allowed only when local replay matches the checkpoint and state. The explicit
     * trust path supports incomplete bootstrap and does not authorize pruning.
     */
    suspend fun verifySnapshotParts(
        staging: PallaSyncSnapshotStaging,
        pinJson: String,
        authority: JsonObject,
        deviceKeys: PallaSyncKeySnapshot,
        keystore: PallaSyncKeystore,
        explicitlyTrustedBootstrap: Boolean = false,
    ) {
        val compaction = staging.compactionRecord()
        val input = snapshotVerificationInput(compaction, pinJson, authority, deviceKeys, keystore)
        if (explicitlyTrustedBootstrap) {
            PallaSyncSnapshotTrustVerifier.requireExactChoice(
                policy,
                chain,
                origin,
                input.logId,
                input.compaction,
                input.authorId,
                input.authorPublicKey,
                staging.partCount,
            )
        }
        val stateStager = PallaSyncSnapshotStateStager(store.snapshotInstallDao(), chain, staging, compaction)
        stateStager.begin()
        PallaSyncEpochSession.begin(input.epochKeys, authority.toString(), input.epochNumber).use { epochSession ->
            val first = stagedPart(0, staging, pinJson)
            val firstRecord = Json.parseToJsonElement(first.signedJson).jsonObject
            check(firstRecord.getValue("device_id").jsonPrimitive.content == input.authorId) { "C_ACCEPTANCE_UNPROVEN" }
            val firstPlaintext = epochSession.decryptSnapshotPart(first.signedJson, input.authorPublicKey)
            val firstPart = Json.parseToJsonElement(firstPlaintext).jsonObject
            val header =
                firstPart
                    .getValue("entries")
                    .jsonArray
                    .firstOrNull()
                    ?.jsonObject ?: error("C_SNAPSHOT_INVALID")
            validateSnapshotHeader(header, input)
            val retainPart: suspend (Int, SnapshotPartEntity, JsonObject) -> Unit = { index, part, frame ->
                stateStager.retainVerifiedPart(index, part, frame)
            }
            verifyRemainingSnapshotParts(
                staging,
                pinJson,
                input,
                first,
                firstPlaintext,
                firstPart,
                header,
                epochSession,
                retainPart,
            )
        }
        stateStager.complete()
        installVerifiedSnapshot(compaction, input, explicitlyTrustedBootstrap)
    }

    private suspend fun installVerifiedSnapshot(
        compaction: JsonObject,
        input: SnapshotVerificationInput,
        explicitlyTrustedBootstrap: Boolean,
    ) {
        val coveredSeq = compaction.getValue("covered_through_seq").jsonPrimitive.long
        val coveredHash = compaction.getValue("covered_log_hash").jsonPrimitive.content
        val exactReplayRevision =
            PallaSyncSnapshotSemanticComparator.compareWithExactLocalReplay(
                store,
                chain,
                input.compactionId,
                input.logId,
                input.expectedGeneration,
                input.epochHash,
                coveredSeq,
                coveredHash,
            )
        if (exactReplayRevision != null) {
            val state = checkNotNull(store.dao.getChainState(chain)) { "C_MISSING_DEPENDENCY" }
            store.snapshotInstallDao().installVerifiedState(
                chain,
                input.compactionId,
                exactReplayRevision,
                explicitlyTrustedBootstrap = explicitlyTrustedBootstrap && !state.initialPullCompleted,
            )
        } else if (explicitlyTrustedBootstrap) {
            store.snapshotInstallDao().installVerifiedState(
                chain,
                input.compactionId,
                checkNotNull(store.dao.getChainState(chain)).indexRevision,
                explicitlyTrustedBootstrap = true,
            )
        }
    }

    private suspend fun snapshotVerificationInput(
        compaction: JsonObject,
        pinJson: String,
        authority: JsonObject,
        deviceKeys: PallaSyncKeySnapshot,
        keystore: PallaSyncKeystore,
    ): SnapshotVerificationInput {
        val compactionId = compaction.getValue("compaction_id").jsonPrimitive.content
        val logId = compaction.getValue("log_id").jsonPrimitive.content
        validateCompactionSource(compaction, compactionId, logId, pinJson, authority)
        val epochHash = compaction.getValue("epoch_hash").jsonPrimitive.content
        val (epochNumber, epochKeys) = snapshotEpochKeys(authority, epochHash, deviceKeys, keystore)
        val authorId = compaction.getValue("device_id").jsonPrimitive.content
        val author = snapshotAuthor(authority, authorId, compaction.getValue("generation").jsonPrimitive.long)
        return SnapshotVerificationInput(
            compaction,
            compactionId,
            logId,
            epochHash,
            epochNumber,
            epochKeys,
            authorId,
            author.getValue("device_public_key").jsonPrimitive.content,
            compaction.getValue("covered_through_seq").jsonPrimitive.long,
            compaction.getValue("generation").jsonPrimitive.long,
        )
    }

    private suspend fun validateCompactionSource(
        compaction: JsonObject,
        compactionId: String,
        logId: String,
        pinJson: String,
        authority: JsonObject,
    ) {
        check(
            compaction.getValue("chain_id").jsonPrimitive.content == chain &&
                authority
                    .getValue("pin")
                    .jsonObject
                    .getValue("chain_id")
                    .jsonPrimitive.content == chain &&
                authority
                    .getValue("pin")
                    .jsonObject
                    .getValue("log_id")
                    .jsonPrimitive.content == logId &&
                Json
                    .parseToJsonElement(pinJson)
                    .jsonObject
                    .getValue("log_id")
                    .jsonPrimitive.content == logId,
        ) { "C_BOOTSTRAP_TRUST_REQUIRED" }

        // A caller cannot elevate an arbitrary cached CompactionRecord into a trust anchor.
        val accepted = store.compactionCatalogueDao().verifiedEntries(chain, logId, compactionId)
        check(accepted.size == 1) { "C_ACCEPTANCE_UNPROVEN" }
        val acceptedEntry = Json.parseToJsonElement(accepted.single().signedJson).jsonObject
        check(
            acceptedEntry.getValue("kind").jsonPrimitive.content == "compaction" &&
                PallaSyncCore.canonicalJson(acceptedEntry.getValue("object").toString()) ==
                PallaSyncCore.canonicalJson(compaction.toString()),
        ) { "C_LOG_HASH_MISMATCH" }
    }

    private fun snapshotEpochKeys(
        authority: JsonObject,
        epochHash: String,
        deviceKeys: PallaSyncKeySnapshot,
        keystore: PallaSyncKeystore,
    ): Pair<Long, PallaSyncKeySnapshot> {
        val epochEntry =
            authority
                .getValue("epochs")
                .jsonObject
                .entries
                .singleOrNull { (_, value) ->
                    val record = value.jsonObject.getValue("record").jsonObject
                    PallaSyncCore.objectHash(record.toString()) == epochHash
                } ?: error("C_MISSING_DEPENDENCY")
        val epochNumber = epochEntry.key.toLongOrNull() ?: error("C_SCHEMA_VIOLATION")
        val epochKeys = checkNotNull(keystore.keysForEpoch(deviceKeys, epochNumber)) { "C_MISSING_DEPENDENCY" }
        return epochNumber to epochKeys
    }

    private fun snapshotAuthor(
        authority: JsonObject,
        authorId: String,
        generation: Long,
    ): JsonObject {
        val author =
            authority
                .getValue("devices")
                .jsonObject[authorId]
                ?.jsonObject
                ?.getValue("record")
                ?.jsonObject ?: error("C_MISSING_DEPENDENCY")
        check(author.getValue("generation").jsonPrimitive.long == generation) { "C_ACCEPTANCE_UNPROVEN" }
        return author
    }

    private fun validateSnapshotHeader(
        header: JsonObject,
        input: SnapshotVerificationInput,
    ) {
        check(
            header.getValue("kind").jsonPrimitive.content == "header" &&
                header.getValue("chain_id").jsonPrimitive.content == chain &&
                header.getValue("generation").jsonPrimitive.long == input.expectedGeneration &&
                header.getValue("log_id").jsonPrimitive.content == input.logId &&
                header.getValue("covered_through_seq").jsonPrimitive.long == input.expectedCoveredSeq &&
                header.getValue("covered_log_hash").jsonPrimitive.content ==
                input.compaction
                    .getValue("covered_log_hash")
                    .jsonPrimitive.content &&
                header.getValue("epoch_hash").jsonPrimitive.content == input.epochHash,
        ) { "C_SNAPSHOT_INVALID" }
    }

    private suspend fun verifyRemainingSnapshotParts(
        staging: PallaSyncSnapshotStaging,
        pinJson: String,
        input: SnapshotVerificationInput,
        firstRecord: SnapshotPartEntity,
        firstPlaintext: String,
        firstPart: JsonObject,
        header: JsonObject,
        epochSession: PallaSyncEpochSession,
        retainPart: suspend (Int, SnapshotPartEntity, JsonObject) -> Unit,
    ) {
        PallaSyncSnapshotVerifier
            .beginFromEpochSession(
                epochSession,
                header.toString(),
                input.compactionId,
                staging.partCount,
            ).use { verifier ->
                verifier.acceptDecryptedAuthenticatedPart(firstPlaintext)
                retainPart(0, firstRecord, firstPart)
                for (partIndex in 1 until staging.partCount) {
                    currentCoroutineContext().ensureActive()
                    val part = stagedPart(partIndex, staging, pinJson)
                    val record = Json.parseToJsonElement(part.signedJson).jsonObject
                    check(record.getValue("device_id").jsonPrimitive.content == input.authorId) { "C_ACCEPTANCE_UNPROVEN" }
                    val plaintext = verifier.acceptSignedRecord(part.signedJson, input.authorPublicKey, epochSession)
                    retainPart(partIndex, part, Json.parseToJsonElement(plaintext).jsonObject)
                }
                verifier.finish(
                    input.compaction
                        .getValue("state_sha256")
                        .jsonPrimitive.content,
                )
            }
    }

    private suspend fun stagedPart(
        index: Int,
        staging: PallaSyncSnapshotStaging,
        pinJson: String,
    ) = staging.verifiedDownloaded(index, pinJson) ?: fetchAndStageSnapshotPart(index, pinJson, staging)

    private data class SnapshotVerificationInput(
        val compaction: JsonObject,
        val compactionId: String,
        val logId: String,
        val epochHash: String,
        val epochNumber: Long,
        val epochKeys: PallaSyncKeySnapshot,
        val authorId: String,
        val authorPublicKey: String,
        val expectedCoveredSeq: Long,
        val expectedGeneration: Long,
    )

    /** §10.12: accept catalogue entries only when they exactly match a receipt-verified replayed log entry. */
    private suspend fun verifySnapshotCatalogue(
        pin: JsonObject,
        generation: Long,
        headSeq: Long,
        minimumSequence: Long,
    ) {
        val pinJson = pin.toString()
        val logId = pin.getValue("log_id").jsonPrimitive.content
        var cursor: String? = null
        var previousId = ""
        var matched = 0
        val seenCursors = mutableSetOf<String>()
        do {
            currentCoroutineContext().ensureActive()
            val requestUrl =
                url("snapshots")
                    .newBuilder()
                    .addQueryParameter("limit", PALLASYNC_PAGE_SIZE.toString())
                    .apply { cursor?.let { addQueryParameter("cursor", it) } }
                    .build()
            val page =
                json
                    .parseToJsonElement(
                        PallaSyncSnapshotCore.validateCompactionPage(
                            get(requestUrl),
                            pinJson,
                            generation,
                            headSeq,
                            PALLASYNC_PAGE_SIZE,
                        ),
                    ).jsonObject
            for (value in page.getValue("items").jsonArray) {
                currentCoroutineContext().ensureActive()
                val compaction = value.jsonObject
                val id = compaction.getValue("compaction_id").jsonPrimitive.content
                check(id > previousId) { "C_SCHEMA_VIOLATION" }
                previousId = id
                // The relay may retain catalogue objects from older log incarnations. They
                // are never used as authority or bootstrap candidates for this selected pin.
                if (compaction.getValue("log_id").jsonPrimitive.content != logId) continue
                val candidates = store.compactionCatalogueDao().verifiedEntries(chain, logId, id)
                if (
                    candidates.isEmpty() &&
                    compaction.getValue("covered_through_seq").jsonPrimitive.long < minimumSequence - 1L
                ) {
                    continue
                }
                check(candidates.size == 1) { "C_ACCEPTANCE_UNPROVEN" }
                val entry = json.parseToJsonElement(candidates.single().signedJson).jsonObject
                check(
                    entry.getValue("kind").jsonPrimitive.content == "compaction" &&
                        PallaSyncCore.canonicalJson(entry.getValue("object").toString()) ==
                        PallaSyncCore.canonicalJson(compaction.toString()),
                ) { "C_LOG_HASH_MISMATCH" }
                if (candidates.single().relaySeq in minimumSequence..headSeq) matched++
            }
            cursor =
                page
                    .getValue("next_cursor")
                    .jsonPrimitive
                    .takeIf { it.isString }
                    ?.content
            check(cursor == null || seenCursors.add(checkNotNull(cursor))) { "C_SCHEMA_VIOLATION" }
        } while (cursor != null)
        check(
            store
                .compactionCatalogueDao()
                .verifiedCountInRetainedSuffix(chain, logId, minimumSequence, headSeq) == matched,
        ) { "C_MISSING_DEPENDENCY" }
    }

    private fun initialAuthority(): JsonObject {
        val session = checkNotNull(root) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val identity = session.identity
        val publicIdentity =
            buildJsonObject {
                put("chain_id", identity.chainId)
                put("admin_public_key", identity.adminPublicKey)
            }
        return json
            .parseToJsonElement(
                PallaSyncCore.beginAuthorityHistory(
                    get(url("parameters")),
                    publicIdentity.toString(),
                    origin,
                    policy.trustedRelayKey(origin),
                ),
            ).jsonObject
    }

    private suspend fun replay(initial: JsonObject): JsonObject {
        var authority = initial
        val pin = initial.getValue("pin").jsonObject
        // Current generation is independently signed in the parameters advertisement, but
        // historical generation switches still require the verified snapshot installer.
        val generation =
            initial["generation"]?.jsonPrimitive?.long
                ?: initial["parameters"]?.let { if (it is JsonObject) it.getValue("generation").jsonPrimitive.long else null }
                ?: 0L
        var cursor: String? = null
        val seen = mutableSetOf<String>()
        do {
            currentCoroutineContext().ensureActive()
            val after = authority.getValue("sequence").jsonPrimitive.long
            val requestUrl =
                url("log")
                    .newBuilder()
                    .addQueryParameter("limit", PALLASYNC_PAGE_SIZE.toString())
                    .apply {
                        if (cursor == null) addQueryParameter("after_seq", after.toString()) else addQueryParameter("cursor", cursor)
                    }.build()
            val page =
                json
                    .parseToJsonElement(
                        PallaSyncCore.validateLogPage(get(requestUrl), pin.toString(), generation, after, PALLASYNC_PAGE_SIZE),
                    ).jsonObject
            for (value in page.getValue("items").jsonArray) {
                val entry = value.jsonObject
                val receipt = receipt(pin, entry)
                authority =
                    json
                        .parseToJsonElement(
                            PallaSyncCore.advanceAuthorityHistory(authority.toString(), entry.toString(), receipt.toString()),
                        ).jsonObject
                // Public/ciphertext staging survives cancellation; it cannot replace an active checkpoint.
                retainEntry(pin, entry, receipt, generation)
            }
            cursor =
                page
                    .getValue("next_cursor")
                    .jsonPrimitive
                    .takeIf { it.isString }
                    ?.content
            check(cursor == null || seen.add(checkNotNull(cursor))) { "C_SCHEMA_VIOLATION" }
        } while (cursor != null)
        return authority
    }

    private suspend fun retainEntry(
        pin: JsonObject,
        entry: JsonObject,
        receipt: JsonObject,
        generation: Long,
    ) {
        store.transaction {
            retain(pin, receipt, "upload_receipt", entry.getValue("relay_seq").jsonPrimitive.long)
            PallaSyncEnvelopeEvidence.retain(store, chain, entry)
            store.protocolDao().retainLogEntry(
                com.yunfie.illustia.pallasync.data.LogEntryEntity(
                    chain,
                    pin.getValue("log_id").jsonPrimitive.content,
                    entry.getValue("relay_seq").jsonPrimitive.long,
                    hash(entry),
                    entry.toString(),
                    receipt.toString(),
                    "authority_verified",
                ),
            )
            store.protocolDao().retainAuthority(
                AuthorityHistoryEntity(
                    chain,
                    "selected_history_entry",
                    hash(entry),
                    generation,
                    entry.toString(),
                    "authority_verified",
                ),
            )
        }
    }

    private fun receipt(
        pin: JsonObject,
        entry: JsonObject,
    ): JsonObject {
        val seq = entry.getValue("relay_seq").jsonPrimitive.long
        val requestUrl = url("receipts/$seq").newBuilder().addQueryParameter("log_id", pin.getValue("log_id").jsonPrimitive.content).build()
        return json.parseToJsonElement(PallaSyncCore.validateReceiptResponse(get(requestUrl), entry.toString(), pin.toString())).jsonObject
    }

    private suspend fun transparency(
        pin: JsonObject,
        cut: JsonObject,
        knownAnchor: JsonObject?,
    ): JsonObject {
        val head = cut.getValue("head").jsonObject
        var anchor = knownAnchor ?: cut.getValue("zero_anchor").jsonObject
        val id = anchor.getValue("transparency_id").jsonPrimitive.content
        val through = head.getValue("entry_index").jsonPrimitive.long
        val start = anchor.getValue("index").jsonPrimitive.long
        check(anchor.getValue("transparency_id") == head.getValue("transparency_id") && start <= through) { "C_ROLLBACK_DETECTED" }
        var cursor: String? = null
        val seen = mutableSetOf<String>()
        do {
            currentCoroutineContext().ensureActive()
            val checked =
                json
                    .parseToJsonElement(
                        PallaSyncCore.validateTransparencyPage(
                            get(PallaSyncUrls.transparencyEntries(base, chain, id, start, through, cursor)),
                            pin.toString(),
                            anchor.toString(),
                            head.toString(),
                            PALLASYNC_PAGE_SIZE,
                        ),
                    ).jsonObject
            val page = checked.getValue("response").jsonObject
            val next = checked.getValue("next_anchor").jsonObject
            store.transaction {
                for (value in page.getValue("items").jsonArray) {
                    val entry = value.jsonObject
                    verifyIncludedReceipt(pin, entry, next)
                    retain(pin, entry, "transparency", entry.getValue("entry_index").jsonPrimitive.long)
                }
            }
            anchor = next
            cursor =
                page
                    .getValue("next_cursor")
                    .jsonPrimitive
                    .takeIf { it.isString }
                    ?.content
            check(cursor == null || seen.add(checkNotNull(cursor))) { "C_TRANSPARENCY_INVALID" }
        } while (cursor != null)
        retain(pin, head, "transparency_head", through)
        return anchor
    }

    private suspend fun verifyIncludedReceipt(
        pin: JsonObject,
        entry: JsonObject,
        anchor: JsonObject,
    ) {
        val seq =
            entry
                .getValue("relay_seq")
                .jsonPrimitive
                .takeIf { !it.isString && it.content != "null" }
                ?.long
        if (entry.getValue("operation").jsonPrimitive.content == "accept" && seq != null) {
            val receipts =
                store.protocolDao().evidenceAt(
                    origin,
                    entry.getValue("relay_key_id").jsonPrimitive.content,
                    chain,
                    pin.getValue("log_id").jsonPrimitive.content,
                    "upload_receipt",
                    seq,
                )
            check(receipts.size == 1) { "C_ACCEPTANCE_UNPROVEN" }
            PallaSyncCore.validateReceiptInclusion(receipts.single(), entry.toString(), pin.toString(), anchor.toString())
        }
    }

    private suspend fun retain(
        pin: JsonObject,
        value: JsonObject,
        kind: String,
        sequence: Long,
    ) {
        store.protocolDao().retainEvidence(
            ProtectedEvidenceEntity(
                origin,
                value.getValue("relay_key_id").jsonPrimitive.content,
                chain,
                pin.getValue("log_id").jsonPrimitive.content,
                kind,
                sequence,
                hash(value),
                value.toString(),
            ),
        )
    }

    private fun hash(value: JsonObject): String = checkNotNull(PallaSyncCore.objectHash(value.toString())) { "C_SCHEMA_VIOLATION" }

    fun get(requestUrl: HttpUrl): ByteArray {
        val token =
            checkNotNull(
                peerToken?.invoke(requestUrl, requestUrl.encodedPath, requestUrl.encodedQuery.orEmpty())
                    ?: root?.token(origin, "GET", requestUrl.encodedPath, requestUrl.encodedQuery.orEmpty()),
            ) { "C_RECOVERY_AUTH_REQUIRED" }
        val request =
            Request
                .Builder()
                .url(
                    requestUrl,
                ).header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $token")
                .get()
                .build()
        return when (val response = remote.execute(request) { PallaSyncHttpResult.Success(it.pallaSyncBytes()) }) {
            is PallaSyncHttpResult.Success -> response.value
            else -> throw Failure(response)
        }
    }

    fun url(resource: String): HttpUrl = base.newBuilder().encodedPath("$prefix/$resource").build()

    class Failure(
        val result: PallaSyncHttpResult<*>,
    ) : RuntimeException()
}
