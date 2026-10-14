package com.yunfie.illustia.pallasync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** §§10.7/18.1: only an active protected identity creates/offers/approves. Every
 * chain request passes the common privacy gate. The foreground caller owns the SAS session.
 */
internal class PallaSyncPeerApprover(
    private val store: PallaSyncLocalStore,
    private val policy: PallaSyncPolicyGate,
    private val keys: PallaSyncKeystore,
    private val journal: PallaSyncPeerJournal,
    private val relay: PallaSyncPeerRelay,
) {
    suspend fun offer(
        base: HttpUrl,
        explicitlyRequested: Boolean,
    ): PallaSyncPeerJournal.Transcript {
        check(explicitlyRequested) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val device = checkNotNull(keys.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val authority = authority(base, checkNotNull(device.chainId))
        val now = policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base))
        val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val body =
            buildJsonObject {
                put("request_id", UUID.randomUUID().toString())
                put("offer_nonce", Base64.getUrlEncoder().withoutPadding().encodeToString(random))
                put("expires_at_ms", now + OFFER_LIFETIME_MS)
            }
        val qr =
            Json
                .parseToJsonElement(
                    PallaSyncCore.createPeerOffer(authority.toString(), checkNotNull(device.deviceId), body.toString(), now),
                ).jsonObject
        val candidate = journal.create(qr, pallaSyncPeerHash(authority))
        return retryOffer(base, candidate)
    }

    suspend fun retryOffer(
        base: HttpUrl,
        candidate: PallaSyncPeerJournal.Transcript,
    ): PallaSyncPeerJournal.Transcript {
        check(journal.read(candidate.requestId) == candidate && candidate.stage == PallaSyncPeerJournal.Stage.CREATED) {
            "C_BOOTSTRAP_TRUST_REQUIRED"
        }
        val qr = candidate.offer
        val chain = qr.getValue("chain_id").jsonPrimitive.content
        val authority = authority(base, chain)
        val device = checkNotNull(keys.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val body =
            buildJsonObject {
                put("request_id", qr.getValue("request_id"))
                put("offer_nonce", qr.getValue("offer_nonce"))
                put("expires_at_ms", qr.getValue("expires_at_ms"))
            }
        val currentOffer =
            Json
                .parseToJsonElement(
                    PallaSyncCore.createPeerOffer(
                        authority.toString(),
                        checkNotNull(device.deviceId),
                        body.toString(),
                        policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
                    ),
                ).jsonObject
        check(currentOffer == qr) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val url = pallaSyncPeerUrl(base, checkNotNull(device.chainId), "enrollment-offers")
        relay.request(
            url,
            device,
            "POST",
            body.toString(),
        ) { bytes -> PallaSyncCore.validatePeerResponse(bytes, qr.toString(), "offer", 0) }
        return journal.update(candidate, candidate.copy(stage = PallaSyncPeerJournal.Stage.OFFERED))
    }

    suspend fun begin(
        base: HttpUrl,
        saved: PallaSyncPeerJournal.Transcript,
    ): PallaSyncPeerSession {
        check(journal.read(saved.requestId) == saved && saved.stage == PallaSyncPeerJournal.Stage.OFFERED) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val chain =
            saved.offer
                .getValue("chain_id")
                .jsonPrimitive.content
        val authority = authority(base, chain)
        val device = checkNotNull(keys.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val signing = Base64.getUrlDecoder().decode(device.signingKeyBase64Url)
        return try {
            PallaSyncPeerSession.approver(
                saved.offer.toString(),
                authority.toString(),
                signing,
                Base64.getUrlDecoder().decode(device.kexPrivateKeyBase64Url),
                policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
            )
        } finally {
            signing.fill(0)
        }
    }

    suspend fun pendingRequest(
        base: HttpUrl,
        saved: PallaSyncPeerJournal.Transcript,
    ): JsonObject? {
        val chain =
            saved.offer
                .getValue("chain_id")
                .jsonPrimitive.content
        val authority = authority(base, chain)
        val device = checkNotNull(keys.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val binding =
            buildJsonObject {
                put("authority", authority)
                put("offer", saved.offer)
            }
        var cursor: String? = null
        val seen = mutableSetOf<String>()
        do {
            currentCoroutineContext().ensureActive()
            val url =
                pallaSyncPeerUrl(base, chain, "enrollment-requests")
                    .newBuilder()
                    .addQueryParameter("limit", PALLASYNC_PAGE_SIZE.toString())
                    .apply { cursor?.let { addQueryParameter("cursor", it) } }
                    .build()
            val page =
                Json
                    .parseToJsonElement(
                        relay.request(url, device, "GET") {
                            PallaSyncCore.validatePeerResponse(it, binding.toString(), "requests", PALLASYNC_PAGE_SIZE.toLong())
                        },
                    ).jsonObject
            val matching =
                page.getValue("items").jsonArray.singleOrNull {
                    it.jsonObject
                        .getValue("request")
                        .jsonObject
                        .getValue("request_id") ==
                        saved.offer.getValue("request_id")
                }
            if (matching != null) return matching.jsonObject
            cursor =
                page
                    .getValue("next_cursor")
                    .jsonPrimitive
                    .takeIf { it.isString }
                    ?.content
            check(cursor == null || seen.add(checkNotNull(cursor))) { "C_SCHEMA_VIOLATION" }
        } while (cursor != null)
        return null
    }

    fun bind(
        saved: PallaSyncPeerJournal.Transcript,
        session: PallaSyncPeerSession,
        item: JsonObject,
        nowMs: Long,
    ): Pair<PallaSyncPeerJournal.Transcript, String> =
        session.checked {
            check(
                saved.stage == PallaSyncPeerJournal.Stage.OFFERED && item.getValue("status").jsonPrimitive.content == "awaiting_approval",
            ) {
                "C_BOOTSTRAP_TRUST_REQUIRED"
            }
            val request = item.getValue("request").jsonObject
            val sas = session.bindRequest(request.toString(), nowMs)
            val bound = journal.update(saved, saved.copy(stage = PallaSyncPeerJournal.Stage.BOUND, request = request))
            bound to sas
        }

    suspend fun confirmedTransfer(
        base: HttpUrl,
        saved: PallaSyncPeerJournal.Transcript,
        session: PallaSyncPeerSession,
        observedSas: String,
        confirmed: Boolean,
    ): PallaSyncPeerJournal.Transcript =
        session.checked {
            check(saved.stage == PallaSyncPeerJournal.Stage.BOUND) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val chain =
                saved.offer
                    .getValue("chain_id")
                    .jsonPrimitive.content
            val authority = authority(base, chain)
            val now = policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base))
            session.confirm(observedSas, confirmed, now)
            val certificate = Json.parseToJsonElement(session.certificate(now)).jsonObject
            val device = checkNotNull(keys.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val eligibleEpochs =
                PallaSyncRetentionHandoff(store, keys::expireRetainedEpochSecrets).eligibleEpochs(
                    chain,
                    authority,
                    now,
                )
            val envelopes =
                eligibleEpochs.map { epoch ->
                    val epochKeys = checkNotNull(keys.keysForEpoch(device, epoch)) { "C_MISSING_DEPENDENCY" }
                    val secret = Base64.getUrlDecoder().decode(epochKeys.epochSecretBase64Url)
                    Json.parseToJsonElement(session.envelope(authority.toString(), epoch, secret, now)).jsonObject
                }
            val currentEpochHash =
                saved.offer
                    .getValue("epoch_hash")
                    .jsonPrimitive.content
            val currentEnvelope =
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
                    pallaSyncPeerHash(record) == currentEpochHash
                } ?: error("C_MISSING_DEPENDENCY")
            journal.update(
                saved,
                saved.copy(
                    stage = PallaSyncPeerJournal.Stage.TRANSFERRED,
                    certificate = certificate,
                    envelope = currentEnvelope,
                    envelopes = envelopes,
                ),
            )
        }

    suspend fun approve(
        base: HttpUrl,
        saved: PallaSyncPeerJournal.Transcript,
        session: PallaSyncPeerSession,
        body: String,
    ): PallaSyncPeerJournal.Transcript =
        session.checked {
            check(saved.stage == PallaSyncPeerJournal.Stage.TRANSFERRED) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val authority =
                authority(
                    base,
                    saved.offer
                        .getValue("chain_id")
                        .jsonPrimitive.content,
                )
            val subjectBody = Json.parseToJsonElement(body).jsonObject
            check(subjectBody.keys == setOf("device_record", "envelopes", "admin_operation")) { "C_SCHEMA_VIOLATION" }
            check(subjectBody.getValue("envelopes") == JsonArray(listOf(checkNotNull(saved.envelope)))) { "C_DEVICE_EQUIVOCATION" }
            val completeBody =
                buildJsonObject {
                    put("device_record", subjectBody.getValue("device_record"))
                    put("envelopes", JsonArray(checkNotNull(saved.envelopes)))
                    put("admin_operation", subjectBody.getValue("admin_operation"))
                }.toString()
            val validated =
                session.approval(
                    authority.toString(),
                    completeBody,
                    policy.retry.correctedTime(PallaSyncUrls.extractOrigin(base)),
                )
            val dispatched = journal.update(saved, saved.copy(stage = PallaSyncPeerJournal.Stage.SUBMITTED, approvalBody = validated))
            retryApproval(base, dispatched)
        }

    /** Exact already-confirmed public payload retries; no new certificate/nonce/device is generated. */
    suspend fun retryApproval(
        base: HttpUrl,
        saved: PallaSyncPeerJournal.Transcript,
    ): PallaSyncPeerJournal.Transcript {
        check(saved.stage == PallaSyncPeerJournal.Stage.SUBMITTED && journal.read(saved.requestId) == saved) { "C_ACCEPTANCE_UNPROVEN" }
        val chain =
            saved.offer
                .getValue("chain_id")
                .jsonPrimitive.content
        val authority = authority(base, chain)
        val device = checkNotNull(keys.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val body = checkNotNull(saved.approvalBody) { "C_MISSING_DEPENDENCY" }
        val response =
            Json
                .parseToJsonElement(
                    relay.request(pallaSyncPeerUrl(base, chain, "enrollment-requests/${saved.requestId}/approve"), device, "POST", body) {
                        PallaSyncCore.validateEnrollmentAcceptance(it, body, authority.getValue("pin").toString())
                    },
                ).jsonObject
        relay.retainReceipts(authority, response)
        return journal.update(saved, saved.copy(stage = PallaSyncPeerJournal.Stage.ACCEPTED, acceptance = response))
    }

    suspend fun reject(
        base: HttpUrl,
        saved: PallaSyncPeerJournal.Transcript,
        session: PallaSyncPeerSession,
    ): PallaSyncPeerJournal.Transcript {
        session.close()
        check(
            saved.stage in
                setOf(PallaSyncPeerJournal.Stage.BOUND, PallaSyncPeerJournal.Stage.TRANSFERRED, PallaSyncPeerJournal.Stage.REJECTED),
        ) { "C_ACCEPTANCE_UNPROVEN" }
        val request = checkNotNull(saved.request) { "C_MISSING_DEPENDENCY" }
        val body = buildJsonObject { put("request_hash", pallaSyncPeerHash(request)) }.toString()
        val chain =
            saved.offer
                .getValue("chain_id")
                .jsonPrimitive.content
        authority(base, chain)
        val device = checkNotNull(keys.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val consumed = journal.update(saved, saved.copy(stage = PallaSyncPeerJournal.Stage.REJECTED))
        relay.request(pallaSyncPeerUrl(base, chain, "enrollment-requests/${saved.requestId}/reject"), device, "POST", body) {
            PallaSyncCore.validatePeerResponse(it, saved.offer.toString(), "reject", 0)
        }
        return consumed
    }

    fun cancel(
        saved: PallaSyncPeerJournal.Transcript,
        session: PallaSyncPeerSession?,
    ) {
        session?.close()
        check(saved.stage !in setOf(PallaSyncPeerJournal.Stage.SUBMITTED, PallaSyncPeerJournal.Stage.ACCEPTED)) { "C_ACCEPTANCE_UNPROVEN" }
        journal.update(saved, saved.copy(stage = PallaSyncPeerJournal.Stage.CONSUMED))
    }

    private suspend fun authority(
        base: HttpUrl,
        chain: String,
    ): JsonObject {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(state.writeState == "active" && keys.getActiveChainKeys()?.chainId == chain) { "C_WRITE_PAUSED" }
        val authority = Json.parseToJsonElement(checkNotNull(store.protocolDao().currentAuthority(chain))).jsonObject
        check(pallaSyncPeerHash(authority) == keys.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
        check(
            authority
                .getValue("pin")
                .jsonObject
                .getValue("origin")
                .jsonPrimitive.content == PallaSyncUrls.extractOrigin(base),
        ) {
            "C_BOOTSTRAP_TRUST_REQUIRED"
        }
        policy.bindChainOrigin(chain, PallaSyncUrls.extractOrigin(base))
        check(PallaSyncCore.validateCurrentKeyRecipient(authority.toString(), checkNotNull(keys.getActiveChainKeys()?.deviceId))) {
            "C_BOOTSTRAP_TRUST_REQUIRED"
        }
        return authority
    }

    private companion object {
        const val OFFER_LIFETIME_MS = 300_000L
    }
}
