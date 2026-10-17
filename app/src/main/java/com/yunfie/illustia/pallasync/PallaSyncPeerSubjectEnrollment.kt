package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity
import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

private const val MAX_PEER_ENVELOPES = 128

/** §18.1 subject-side foreground ceremony. This only prepares a pending device; it never activates it. */
@Suppress("TooGenericExceptionCaught", "TooManyFunctions") // Keep the ceremony boundary together and release native state on failure.
internal class PallaSyncPeerSubjectEnrollment(
    private val store: PallaSyncLocalStore,
    private val remote: PallaSyncRemoteService,
    private val policy: PallaSyncPolicyGate,
    private val keys: PallaSyncKeystore,
    private val crypto: PallaSyncCryptoService,
) {
    internal class Work(
        val offerJson: String,
        val historyJson: String,
        val authorityJson: String,
        val requestJson: String,
        val sas: String,
        val origin: String,
        val session: PallaSyncPeerSession,
    ) : AutoCloseable {
        var confirmed = false
            private set

        fun confirm(
            observed: String,
            nowMs: Long,
        ) {
            check(!confirmed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            session.confirm(observed, confirmed = true, nowMs = nowMs)
            confirmed = true
        }

        override fun close() = session.close()

        override fun toString(): String = "PallaSync foreground peer subject session"
    }

    internal class Candidate(
        val keys: PallaSyncKeySnapshot,
        val requestBody: String,
        val origin: String,
    ) {
        override fun toString(): String = "PallaSync pending peer enrollment"
    }

    fun pendingCandidate(configuredOrigin: String): Candidate? {
        val origin = PallaSyncPolicyGate.normalizeOrigin(configuredOrigin)
        return keys.getPendingChainKeys()?.let { pending ->
            pending.chainId?.let { chain ->
                keys
                    .pendingEnrollment(chain, origin)
                    ?.takeIf { keys.pendingPeerContext(chain, origin) != null }
                    ?.let { saved -> Candidate(pending, saved.first, origin) }
            }
        }
    }

    suspend fun begin(
        offerJson: String,
        authorityJson: String,
        configuredOrigin: String,
        nowMs: Long,
    ): Work {
        val offer = PallaSyncDeletionEvidenceArchive.parseStrictObject(offerJson)
        val origin = PallaSyncPolicyGate.normalizeOrigin(offer.getValue("relay_origin").jsonPrimitive.content)
        check(origin == PallaSyncPolicyGate.normalizeOrigin(configuredOrigin)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        policy.requireCurrent(origin, "authenticated_export")
        val transfer = validateSelectedHistory(authorityJson, origin)
        val verified = transfer.getValue("authority").jsonObject
        val session = PallaSyncPeerSession.subject(offerJson, verified.toString(), explicitlySelected = true, nowMs)
        try {
            val requestJson = checkNotNull(session.requestJson) { "C_MISSING_DEPENDENCY" }
            val chain = offer.getValue("chain_id").jsonPrimitive.content
            val url =
                origin
                    .toHttpUrl()
                    .newBuilder()
                    .encodedPath("/pallasync/v3/chains/$chain/enrollment-requests")
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("Accept", PALLASYNC_MEDIA_TYPE)
                    .post(requestJson.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
                    .build()
            val result =
                remote.execute(request) {
                    PallaSyncHttpResult.Success(
                        PallaSyncCore.validatePeerResponse(
                            it.pallaSyncBytes(),
                            requestJson,
                            "request",
                            0,
                        ),
                    )
                }
            if (result !is PallaSyncHttpResult.Success) throw PallaSyncInitialHistory.Failure(result)
            return Work(
                offerJson,
                Json.parseToJsonElement(authorityJson).toString(),
                verified.toString(),
                requestJson,
                session.sas(nowMs),
                origin,
                session,
            )
        } catch (failure: Throwable) {
            session.close()
            throw failure
        }
    }

    fun prepare(
        work: Work,
        certificateJson: String,
        envelopesJson: String,
        deviceName: String,
        nowMs: Long,
    ): Candidate {
        try {
            check(work.confirmed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val certificate = Json.parseToJsonElement(certificateJson).jsonObject
            val envelopes = Json.parseToJsonElement(envelopesJson).jsonArray.map { it.jsonObject }
            val authority = Json.parseToJsonElement(work.authorityJson).jsonObject
            check(envelopes.isNotEmpty() && envelopes.size <= MAX_PEER_ENVELOPES) { "C_SCHEMA_VIOLATION" }
            // The approver must prove local handoff before it can issue this policy-minimal set.
            val expectedEpochs = PallaSyncPeerEpochEligibility.eligibleEpochs(authority, nowMs, handoffVerified = true).sorted()
            check(envelopes.map { it.getValue("epoch").jsonPrimitive.long }.sorted() == expectedEpochs) {
                "C_MISSING_DEPENDENCY"
            }
            val offer = Json.parseToJsonElement(work.offerJson).jsonObject
            val currentEnvelope = selectCurrentEnvelope(offer, authority, envelopes)
            val (prepared, historicSecrets) =
                work.session.checked {
                    work.session.acceptCertificate(certificate.toString(), nowMs)
                    validateEnvelopes(work, certificate, envelopes, nowMs)
                    val historic = unwrapHistoricSecrets(work, certificate, envelopes, currentEnvelope, nowMs)
                    try {
                        work.session.prepareSubject(
                            work.authorityJson,
                            currentEnvelope.toString(),
                            buildJsonObject {
                                put("name", deviceName)
                                put("key_protection", "os-keystore")
                            }.toString(),
                            nowMs,
                        ) to historic
                    } catch (failure: Throwable) {
                        historic.forEach { (_, secret) -> secret.fill(0) }
                        throw failure
                    }
                }
            saveHistoricSecrets(checkNotNull(offer.getValue("chain_id").jsonPrimitive.content), historicSecrets)
            return saveCandidate(prepared, work, offer, authority, certificate, envelopes)
        } finally {
            work.close()
        }
    }

    suspend fun complete(
        acceptanceJson: String,
        base: okhttp3.HttpUrl,
    ): Pair<PallaSyncKeySnapshot, PallaSyncGenesisCheckpoint> {
        val origin = PallaSyncUrls.extractOrigin(base)
        val pending = checkNotNull(keys.getPendingChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val chain = checkNotNull(pending.chainId)
        val relay = PallaSyncPeerRelay(store, remote, crypto)
        val packageValue = Json.parseToJsonElement(acceptanceJson).jsonObject
        check(
            packageValue.keys == setOf("format", "acceptance", "history") &&
                packageValue.getValue("format").jsonPrimitive.content == "pallasync-peer-acceptance/3",
        ) { "C_SCHEMA_VIOLATION" }
        val (original, submitted, accepted) = validateAcceptance(packageValue.getValue("acceptance").toString(), pending, origin)
        val history = packageValue.getValue("history").jsonObject
        val finalAuthority = verifyTransferredHistory(history, origin, original.authority)
        proveAcceptanceEntries(accepted, submitted, history)
        val context = original.copy(history = history, authority = finalAuthority)
        retainVerifiedPrefix(context, origin, chain)
        relay.retainReceipts(finalAuthority, accepted)
        verifyAndAcknowledge(base, pending, origin, finalAuthority, context.envelopes)
        val checkpoint = verifyCurrentHistory(base, pending, context, finalAuthority)
        val device = checkNotNull(pending.deviceId)
        val acceptedDevice =
            checkpoint.authority
                .getValue("devices")
                .jsonObject
                .getValue(device)
                .jsonObject
        val submittedDevice = Json.parseToJsonElement(submitted).jsonObject.getValue("device_record")
        check(
            acceptedDevice.getValue("record") == submittedDevice && acceptedDevice.getValue("revoked_seq").jsonPrimitive.content == "null",
        ) {
            "C_ACCEPTANCE_UNPROVEN"
        }
        check(keys.getPendingChainKeys() == pending) { "C_LOCAL_STORAGE_FULL" }
        return pending to checkpoint
    }

    private data class SubjectContext(
        val offer: JsonObject,
        val history: JsonObject,
        val authority: JsonObject,
        val envelopes: List<JsonObject>,
    )

    private fun validateSelectedHistory(
        encoded: String,
        origin: String,
    ): JsonObject {
        val transfer = Json.parseToJsonElement(encoded).jsonObject
        check(
            transfer.keys == setOf("format", "parameters", "authority", "items") &&
                transfer.getValue("format").jsonPrimitive.content == "pallasync-peer-history/3",
        ) { "C_SCHEMA_VIOLATION" }
        val authority = transfer.getValue("authority").jsonObject
        val pin = authority.getValue("pin").jsonObject
        check(
            pin.getValue("origin").jsonPrimitive.content == origin &&
                pin.getValue("public_key").jsonPrimitive.content == policy.trustedRelayKey(origin),
        ) { "C_RELAY_KEY_MISMATCH" }
        val verified = verifyTransferredHistory(transfer, origin)
        check(pallaSyncPeerHash(verified) == pallaSyncPeerHash(authority)) { "C_LOG_HASH_MISMATCH" }
        return transfer
    }

    private fun loadContext(
        pending: PallaSyncKeySnapshot,
        origin: String,
    ): SubjectContext {
        val chain = checkNotNull(pending.chainId)
        val context =
            Json
                .parseToJsonElement(checkNotNull(keys.pendingPeerContext(chain, origin)) { "C_MISSING_DEPENDENCY" })
                .jsonObject
        val authority = context.getValue("authority").jsonObject
        val pin = authority.getValue("pin").jsonObject
        check(
            pin.getValue("chain_id").jsonPrimitive.content == chain &&
                pin.getValue("origin").jsonPrimitive.content == origin &&
                pin.getValue("public_key").jsonPrimitive.content == policy.trustedRelayKey(origin),
        ) { "C_RELAY_KEY_MISMATCH" }
        return SubjectContext(
            context.getValue("offer").jsonObject,
            context.getValue("history").jsonObject,
            authority,
            context.getValue("envelopes").jsonArray.map { it.jsonObject },
        )
    }

    private fun verifyTransferredHistory(
        transfer: JsonObject,
        origin: String,
        expectedPrefix: JsonObject? = null,
    ): JsonObject {
        val authority = transfer.getValue("authority").jsonObject
        val pin = authority.getValue("pin").jsonObject
        val identity =
            buildJsonObject {
                put("chain_id", pin.getValue("chain_id"))
                put("admin_public_key", authority.getValue("admin_public_key"))
            }.toString()
        var current =
            Json
                .parseToJsonElement(
                    PallaSyncCore.beginAuthorityHistory(
                        transfer.getValue("parameters").toString().toByteArray(Charsets.UTF_8),
                        identity,
                        origin,
                        policy.trustedRelayKey(origin),
                    ),
                ).jsonObject
        val items = transfer.getValue("items").jsonArray
        val finalSequence = authority.getValue("sequence").jsonPrimitive.long
        check(items.size.toLong() == finalSequence) { "C_MISSING_DEPENDENCY" }
        val prefixSequence = expectedPrefix?.getValue("sequence")?.jsonPrimitive?.long
        var prefixVerified = prefixSequence == null
        if (prefixSequence == 0L) {
            check(pallaSyncPeerHash(current) == pallaSyncPeerHash(expectedPrefix)) { "C_LOG_HASH_MISMATCH" }
            prefixVerified = true
        }
        items.forEachIndexed { index, value ->
            val item = value.jsonObject
            check(item.keys == setOf("entry", "receipt")) { "C_SCHEMA_VIOLATION" }
            val entry = item.getValue("entry").jsonObject
            check(entry.getValue("relay_seq").jsonPrimitive.long == index + 1L) { "C_MISSING_DEPENDENCY" }
            current =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.advanceAuthorityHistory(
                            current.toString(),
                            entry.toString(),
                            item.getValue("receipt").toString(),
                        ),
                    ).jsonObject
            if (prefixSequence == index + 1L) {
                check(pallaSyncPeerHash(current) == pallaSyncPeerHash(expectedPrefix)) { "C_LOG_HASH_MISMATCH" }
                prefixVerified = true
            }
        }
        check(prefixSequence == null || prefixSequence in 0..finalSequence) { "C_MISSING_DEPENDENCY" }
        check(prefixVerified) { "C_MISSING_DEPENDENCY" }
        check(pallaSyncPeerHash(current) == pallaSyncPeerHash(authority)) { "C_LOG_HASH_MISMATCH" }
        return current
    }

    private fun proveAcceptanceEntries(
        acceptance: JsonObject,
        submittedJson: String,
        history: JsonObject,
    ) {
        val submitted = Json.parseToJsonElement(submittedJson).jsonObject
        val objects =
            listOf("device" to submitted.getValue("device_record")) +
                submitted.getValue("envelopes").jsonArray.map { "envelope" to it }
        val receipts = acceptance.getValue("upload_receipts").jsonArray.map { it.jsonObject }
        check(receipts.size == objects.size && receipts.map { it.getValue("relay_seq") }.toSet().size == receipts.size) {
            "C_ACCEPTANCE_UNPROVEN"
        }
        val entries =
            history.getValue("items").jsonArray.associate { value ->
                val item = value.jsonObject
                item
                    .getValue("entry")
                    .jsonObject
                    .getValue("relay_seq")
                    .jsonPrimitive.long to item
            }
        for (receipt in receipts) {
            val objectMatches =
                objects.filter { (_, item) ->
                    PallaSyncCore.objectHash(item.toString()) ==
                        receipt.getValue("object_hash").jsonPrimitive.content
                }
            check(objectMatches.size == 1) { "C_ACCEPTANCE_UNPROVEN" }
            val (kind, value) = objectMatches.single()
            val item = checkNotNull(entries[receipt.getValue("relay_seq").jsonPrimitive.long]) { "C_ACCEPTANCE_UNPROVEN" }
            check(item.getValue("receipt") == receipt) { "C_ACCEPTANCE_UNPROVEN" }
            val entry = item.getValue("entry").jsonObject
            check(entry.getValue("kind").jsonPrimitive.content == kind && entry.getValue("object") == value) {
                "C_ACCEPTANCE_UNPROVEN"
            }
        }
    }

    private suspend fun retainVerifiedPrefix(
        context: SubjectContext,
        origin: String,
        chain: String,
    ) {
        val verified = verifyTransferredHistory(context.history, origin)
        check(pallaSyncPeerHash(verified) == pallaSyncPeerHash(context.authority)) { "C_LOG_HASH_MISMATCH" }
        val pin = verified.getValue("pin").jsonObject
        val generation = verified.getValue("generation").jsonPrimitive.long
        for (value in context.history.getValue("items").jsonArray) {
            val item = value.jsonObject
            val entry = item.getValue("entry").jsonObject
            val receipt = item.getValue("receipt").jsonObject
            val sequence = entry.getValue("relay_seq").jsonPrimitive.long
            store.transaction {
                store.protocolDao().retainEvidence(
                    ProtectedEvidenceEntity(
                        origin,
                        pin.getValue("relay_key_id").jsonPrimitive.content,
                        chain,
                        pin.getValue("log_id").jsonPrimitive.content,
                        "upload_receipt",
                        sequence,
                        pallaSyncPeerHash(receipt),
                        receipt.toString(),
                    ),
                )
                PallaSyncEnvelopeEvidence.retain(store, chain, entry)
                store.protocolDao().retainLogEntry(
                    LogEntryEntity(
                        chain,
                        pin.getValue("log_id").jsonPrimitive.content,
                        sequence,
                        pallaSyncPeerHash(entry),
                        entry.toString(),
                        receipt.toString(),
                        "authority_verified",
                    ),
                )
                store.protocolDao().retainAuthority(
                    AuthorityHistoryEntity(
                        chain,
                        "selected_history_entry",
                        pallaSyncPeerHash(entry),
                        generation,
                        entry.toString(),
                        "authority_verified",
                    ),
                )
            }
        }
    }

    private fun validateAcceptance(
        acceptanceJson: String,
        pending: PallaSyncKeySnapshot,
        origin: String,
    ): Triple<SubjectContext, String, JsonObject> {
        val context = loadContext(pending, origin)
        val submitted = checkNotNull(keys.pendingEnrollment(checkNotNull(pending.chainId), origin)).first
        val pin = context.authority.getValue("pin").jsonObject
        val accepted =
            Json
                .parseToJsonElement(
                    PallaSyncCore.validateEnrollmentAcceptance(
                        acceptanceJson.toByteArray(Charsets.UTF_8),
                        submitted,
                        pin.toString(),
                    ),
                ).jsonObject
        return Triple(context, submitted, accepted)
    }

    private fun verifyAndAcknowledge(
        base: okhttp3.HttpUrl,
        pending: PallaSyncKeySnapshot,
        origin: String,
        authority: JsonObject,
        envelopes: List<JsonObject>,
    ) {
        val relay = PallaSyncPeerRelay(store, remote, crypto)
        val chain = checkNotNull(pending.chainId)
        val device = checkNotNull(pending.deviceId)
        val epoch = pending.epoch
        val expected = envelopes.singleOrNull { it.getValue("epoch").jsonPrimitive.long == epoch } ?: error("C_MISSING_DEPENDENCY")
        val url =
            PallaSyncUrls
                .deviceEnvelopes(base, chain, device)
                .newBuilder()
                .addQueryParameter("epoch", epoch.toString())
                .addQueryParameter("limit", PALLASYNC_PAGE_SIZE.toString())
                .build()
        val readback =
            relay.request(
                url,
                pending,
                "GET",
            ) { PallaSyncCore.validateOwnEnvelopePage(it, expected.toString(), authority.toString()) }
        val privateKey = Base64.getUrlDecoder().decode(pending.kexPrivateKeyBase64Url)
        try {
            PallaSyncEpochSession.begin(pending, authority.toString(), epoch).use { session ->
                check(PallaSyncCore.verifyDeviceKeyDelivery(session.handle, readback, privateKey)) { "C_COMMITMENT_MISMATCH" }
            }
        } finally {
            privateKey.fill(0)
        }
        val ack = checkNotNull(keys.pendingEnrollment(chain, origin)).second
        val ackUrl = PallaSyncUrls.deviceKeysAck(base, chain, device)
        val record =
            authority
                .getValue("epochs")
                .jsonObject
                .getValue(epoch.toString())
                .jsonObject
                .getValue("record")
                .jsonObject
        relay.request(ackUrl, pending, "POST", ack) { PallaSyncCore.validateKeysAck(it, device, pallaSyncPeerHash(record)) }
    }

    private suspend fun verifyCurrentHistory(
        base: okhttp3.HttpUrl,
        pending: PallaSyncKeySnapshot,
        context: SubjectContext,
        acceptedAuthority: JsonObject,
    ): PallaSyncGenesisCheckpoint {
        val chain = checkNotNull(pending.chainId)
        val history =
            PallaSyncInitialHistory(
                store = store,
                remote = remote,
                policy = policy,
                root = null,
                base = base,
                peerChainId = chain,
                peerAdminPublicKey =
                    context.authority
                        .getValue("admin_public_key")
                        .jsonPrimitive.content,
                importedAuthority = context.authority,
                peerToken = { url, path, query -> capability(pending, url, path, query) },
                source = "authenticated_export",
            )
        val checkpoint = history.load(acceptedAuthority)
        check(
            checkpoint.epochHash ==
                context.offer
                    .getValue("epoch_hash")
                    .jsonPrimitive.content &&
                checkpoint.generation == pendingGeneration(context.authority),
        ) { "C_MISSING_DEPENDENCY" }
        return checkpoint
    }

    private fun capability(
        device: PallaSyncKeySnapshot,
        url: okhttp3.HttpUrl,
        path: String,
        query: String,
    ): String? =
        crypto.createCapabilityToken(
            aud = PallaSyncUrls.extractOrigin(url),
            chainId = checkNotNull(device.chainId),
            signerKind = "device",
            deviceId = device.deviceId,
            method = "GET",
            path = path,
            query = query,
            signingKeyBase64 = device.signingKeyBase64Url,
        )

    private fun pendingGeneration(authority: JsonObject): Long = authority.getValue("generation").jsonPrimitive.long

    private fun selectCurrentEnvelope(
        offer: JsonObject,
        authority: JsonObject,
        envelopes: List<JsonObject>,
    ): JsonObject =
        envelopes.singleOrNull { envelope ->
            val number = envelope.getValue("epoch").jsonPrimitive.long
            val record =
                authority
                    .getValue("epochs")
                    .jsonObject
                    .getValue(number.toString())
                    .jsonObject
                    .getValue("record")
                    .jsonObject
            pallaSyncPeerHash(record) == offer.getValue("epoch_hash").jsonPrimitive.content
        } ?: error("C_MISSING_DEPENDENCY")

    private fun validateEnvelopes(
        work: Work,
        certificate: JsonObject,
        envelopes: List<JsonObject>,
        nowMs: Long,
    ) = PallaSyncCore.validatePeerSubjectEnvelopes(
        work.session.handle,
        work.authorityJson,
        certificate.toString(),
        JsonArray(envelopes).toString(),
        nowMs,
    )

    private fun unwrapHistoricSecrets(
        work: Work,
        certificate: JsonObject,
        envelopes: List<JsonObject>,
        currentEnvelope: JsonObject,
        nowMs: Long,
    ): List<Pair<Long, ByteArray>> {
        val currentEpoch = currentEnvelope.getValue("epoch").jsonPrimitive.long
        val secrets = mutableListOf<Pair<Long, ByteArray>>()
        try {
            for (envelope in envelopes.filter { it.getValue("epoch").jsonPrimitive.long != currentEpoch }) {
                secrets +=
                    envelope.getValue("epoch").jsonPrimitive.long to
                    PallaSyncCore.unwrapPeerSubjectEnvelope(
                        work.session.handle,
                        work.authorityJson,
                        certificate.toString(),
                        envelope.toString(),
                        nowMs,
                    )
            }
            return secrets
        } catch (failure: Throwable) {
            secrets.forEach { (_, secret) -> secret.fill(0) }
            throw failure
        }
    }

    private fun saveHistoricSecrets(
        chain: String,
        secrets: List<Pair<Long, ByteArray>>,
    ) {
        try {
            for ((epoch, secret) in secrets) {
                check(keys.retainEpochSecret(chain, epoch, secret)) { "C_LOCAL_STORAGE_FULL" }
            }
        } finally {
            secrets.forEach { (_, secret) -> secret.fill(0) }
        }
    }

    private fun saveCandidate(
        prepared: PallaSyncPreparedKeys,
        work: Work,
        offer: JsonObject,
        authority: JsonObject,
        certificate: JsonObject,
        envelopes: List<JsonObject>,
    ): Candidate =
        prepared.use {
            val candidateKeys = it.snapshot()
            val body = it.publicResult.requestBody
            val peerContext =
                buildJsonObject {
                    put("offer", offer)
                    put("history", Json.parseToJsonElement(work.historyJson))
                    put("authority", authority)
                    put("certificate", certificate)
                    put("envelopes", JsonArray(envelopes))
                }.toString()
            check(
                candidateKeys.chainId ==
                    Json
                        .parseToJsonElement(work.offerJson)
                        .jsonObject
                        .getValue("chain_id")
                        .jsonPrimitive.content &&
                    candidateKeys.deviceId == it.publicResult.deviceId,
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            check(
                keys.savePendingEnrollment(
                    candidateKeys,
                    work.origin,
                    body,
                    checkNotNull(it.publicResult.keysAckBody),
                    peerContext,
                ),
            ) {
                "C_LOCAL_STORAGE_FULL"
            }
            check(
                keys.getPendingChainKeys() == candidateKeys &&
                    keys.pendingEnrollment(checkNotNull(candidateKeys.chainId), work.origin) == (body to it.publicResult.keysAckBody),
            ) { "C_LOCAL_STORAGE_FULL" }
            Candidate(candidateKeys, body, work.origin)
        }
}
