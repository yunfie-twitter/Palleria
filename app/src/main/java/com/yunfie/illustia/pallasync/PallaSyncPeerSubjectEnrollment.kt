package com.yunfie.illustia.pallasync

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

private const val MAX_PEER_ENVELOPES = 128

/** §18.1 subject-side foreground ceremony. This only prepares a pending device; it never activates it. */
@Suppress("TooGenericExceptionCaught") // Release the native session if parsing, transport, or persistence throws.
internal class PallaSyncPeerSubjectEnrollment(
    private val remote: PallaSyncRemoteService,
    private val policy: PallaSyncPolicyGate,
    private val keys: PallaSyncKeystore,
) {
    internal class Work(
        val offerJson: String,
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

    fun begin(
        offerJson: String,
        authorityJson: String,
        configuredOrigin: String,
        nowMs: Long,
    ): Work {
        val offer = Json.parseToJsonElement(offerJson).jsonObject
        val origin = PallaSyncPolicyGate.normalizeOrigin(offer.getValue("relay_origin").jsonPrimitive.content)
        check(origin == PallaSyncPolicyGate.normalizeOrigin(configuredOrigin)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        policy.requireCurrent(origin, "authenticated_export")
        val imported = Json.parseToJsonElement(authorityJson).jsonObject
        val pin = imported.getValue("pin").jsonObject
        check(
            pin.getValue("origin").jsonPrimitive.content == origin &&
                pin.getValue("public_key").jsonPrimitive.content == policy.trustedRelayKey(origin),
        ) { "C_RELAY_KEY_MISMATCH" }
        val session = PallaSyncPeerSession.subject(offerJson, authorityJson, explicitlySelected = true, nowMs)
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
            return Work(offerJson, authorityJson, requestJson, session.sas(nowMs), origin, session)
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
            val expectedEpochs = PallaSyncPeerEpochEligibility.eligibleEpochs(authority, nowMs).sorted()
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
            return saveCandidate(prepared, work)
        } finally {
            work.close()
        }
    }

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
    ): Candidate =
        prepared.use {
            val candidateKeys = it.snapshot()
            val body = it.publicResult.requestBody
            check(
                candidateKeys.chainId ==
                    Json
                        .parseToJsonElement(work.offerJson)
                        .jsonObject
                        .getValue("chain_id")
                        .jsonPrimitive.content &&
                    candidateKeys.deviceId == it.publicResult.deviceId,
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            check(keys.savePendingEnrollment(candidateKeys, work.origin, body, checkNotNull(it.publicResult.keysAckBody))) {
                "C_LOCAL_STORAGE_FULL"
            }
            check(
                keys.getPendingChainKeys() == candidateKeys &&
                    keys.pendingEnrollment(checkNotNull(candidateKeys.chainId), work.origin) == (body to it.publicResult.keysAckBody),
            ) { "C_LOCAL_STORAGE_FULL" }
            Candidate(candidateKeys, body, work.origin)
        }
}
