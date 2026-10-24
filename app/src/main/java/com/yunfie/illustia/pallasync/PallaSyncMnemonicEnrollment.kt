package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Base64

/** Root participation never substitutes for verifying the selected authority history (§10.4/§18.4). */
internal class PallaSyncMnemonicEnrollment(
    private val store: PallaSyncLocalStore,
    private val remote: PallaSyncRemoteService,
    private val policy: PallaSyncPolicyGate,
    private val keys: PallaSyncKeystore,
    private val crypto: PallaSyncCryptoService,
) {
    data class Candidate(
        val keys: PallaSyncKeySnapshot,
        val body: String,
        val ack: String,
    )

    suspend fun prepare(
        root: PallaSyncRootSession,
        name: String,
        base: HttpUrl,
    ): Pair<PallaSyncKeySnapshot, PallaSyncGenesisCheckpoint> {
        val history = PallaSyncInitialHistory(store, remote, policy, root, base)
        val before = history.load()
        val candidate = candidate(root, name, base, history, before)
        enroll(root, base, candidate, before)
        val after = history.load(before.authority)
        retainHistoryKeys(root, history, candidate, after)
        val id = checkNotNull(candidate.keys.deviceId)
        val admission =
            after.authority
                .getValue("devices")
                .jsonObject
                .getValue(id)
                .jsonObject
        val submitted = Json.parseToJsonElement(candidate.body).jsonObject.getValue("device_record")
        check(admission.getValue("record") == submitted && admission.getValue("revoked_seq").jsonPrimitive.content == "null") {
            "C_ACCEPTANCE_UNPROVEN"
        }
        check(after.epochHash == before.epochHash && after.parametersHash == before.parametersHash) { "C_MISSING_DEPENDENCY" }
        // The OS store's complete key bundle was committed and read back before acknowledgement.
        check(keys.getPendingChainKeys() == candidate.keys) { "C_LOCAL_STORAGE_FULL" }
        currentCoroutineContext().ensureActive()
        check(
            root.identity.chainId ==
                after.authority
                    .getValue("pin")
                    .jsonObject
                    .getValue("chain_id")
                    .jsonPrimitive.content,
        ) {
            "C_RECOVERY_ROOT_MISMATCH"
        }
        verifyDelivery(base, candidate, after)
        acknowledge(base, candidate, after)
        return candidate.keys to after
    }

    private suspend fun candidate(
        root: PallaSyncRootSession,
        name: String,
        base: HttpUrl,
        history: PallaSyncInitialHistory,
        checkpoint: PallaSyncGenesisCheckpoint,
    ): Candidate {
        val origin = PallaSyncUrls.extractOrigin(base)
        val chain = root.identity.chainId
        val stored = keys.pendingEnrollment(chain, origin)
        if (stored != null) return Candidate(checkNotNull(keys.getPendingChainKeys()), stored.first, stored.second)
        val current =
            checkpoint.authority
                .getValue("epochs")
                .jsonObject.values
                .maxBy {
                    it.jsonObject
                        .getValue("record")
                        .jsonObject
                        .getValue("epoch")
                        .jsonPrimitive.long
                }.jsonObject
                .getValue("record")
                .jsonObject
        val epoch = current.getValue("epoch").jsonPrimitive.long
        val eligibleEpochs =
            PallaSyncRetentionHandoff(store, keys::expireRetainedEpochSecrets).eligibleEpochs(
                chain,
                checkpoint.authority,
                policy.retry.correctedTime(origin),
            )
        check(eligibleEpochs.lastOrNull() == epoch) { "C_MISSING_DEPENDENCY" }
        val envelopes =
            eligibleEpochs.map { eligibleEpoch ->
                PallaSyncCore.validateRecoveryEnvelopeResponse(
                    history.get(history.url("epochs/$eligibleEpoch/recovery-envelope")),
                    checkpoint.authority.toString(),
                    eligibleEpoch,
                )
            }
        val prepared =
            crypto.createMnemonicEnrollmentBundle(
                root,
                name,
                generation = checkpoint.generation,
                epoch = epoch,
                recoveryEnvelopesJson = JsonArray(envelopes.map(Json::parseToJsonElement)).toString(),
                expectedParametersHash = checkpoint.parametersHash,
                expectedEpochHash = checkpoint.epochHash,
                aud = origin,
            )
        return prepared.use {
            val candidate = Candidate(it.snapshot(), it.publicResult.requestBody, checkNotNull(it.publicResult.keysAckBody))
            check(keys.savePendingEnrollment(candidate.keys, origin, candidate.body, candidate.ack)) { "C_LOCAL_STORAGE_FULL" }
            candidate
        }
    }

    private fun retainHistoryKeys(
        root: PallaSyncRootSession,
        history: PallaSyncInitialHistory,
        candidate: Candidate,
        checkpoint: PallaSyncGenesisCheckpoint,
    ) {
        val chain = checkNotNull(candidate.keys.chainId)
        val submitted = Json.parseToJsonElement(candidate.body).jsonObject
        val envelopeItems = submitted.getValue("envelopes").jsonArray
        val epochs = mutableListOf<Long>()
        for (envelope in envelopeItems) {
            val epoch = envelope.jsonObject.getValue("epoch")
            epochs += epoch.jsonPrimitive.long
        }
        epochs.sort()
        for (epoch in epochs) {
            if (keys.keysForEpoch(candidate.keys, epoch) != null) continue
            val response = history.get(history.url("epochs/$epoch/recovery-envelope"))
            val secret =
                PallaSyncCore.recoverEpochSecret(
                    root.handle,
                    root.operation.wire,
                    checkpoint.authority.toString(),
                    response,
                    epoch,
                )
            check(keys.retainEpochSecret(chain, epoch, secret)) { "C_LOCAL_STORAGE_FULL" }
        }
    }

    private suspend fun enroll(
        root: PallaSyncRootSession,
        base: HttpUrl,
        candidate: Candidate,
        checkpoint: PallaSyncGenesisCheckpoint,
    ) {
        val url = PallaSyncUrls.enrollDevice(base, checkNotNull(candidate.keys.chainId))
        val origin = PallaSyncUrls.extractOrigin(base)
        val token =
            checkNotNull(root.token(origin, "POST", url.encodedPath, body = candidate.body.toByteArray())) { "C_RECOVERY_AUTH_REQUIRED" }
        val request = request(url, token, candidate.body)
        val response =
            remote.execute(request, explicitRootAuthorization = true) {
                PallaSyncHttpResult.Success(
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.validateEnrollmentAcceptance(
                                it.pallaSyncBytes(),
                                candidate.body,
                                checkpoint.authority.getValue("pin").toString(),
                            ),
                        ).jsonObject,
                )
            }
        if (response !is PallaSyncHttpResult.Success) throw PallaSyncInitialHistory.Failure(response)
        // Retain original acceptance even when a later history/probe request cannot complete.
        for (value in response.value.getValue("upload_receipts").jsonArray) {
            val receipt = value.jsonObject
            store.protocolDao().retainEvidence(
                ProtectedEvidenceEntity(
                    origin,
                    receipt.getValue("relay_key_id").jsonPrimitive.content,
                    checkNotNull(candidate.keys.chainId),
                    checkpoint.logId,
                    "upload_receipt",
                    receipt.getValue("relay_seq").jsonPrimitive.long,
                    checkNotNull(PallaSyncCore.objectHash(receipt.toString())),
                    receipt.toString(),
                ),
            )
        }
    }

    private fun acknowledge(
        base: HttpUrl,
        candidate: Candidate,
        checkpoint: PallaSyncGenesisCheckpoint,
    ) {
        val device = checkNotNull(candidate.keys.deviceId)
        val url = PallaSyncUrls.deviceKeysAck(base, checkNotNull(candidate.keys.chainId), device)
        val token =
            checkNotNull(
                crypto.createCapabilityToken(
                    PallaSyncUrls.extractOrigin(base),
                    checkNotNull(candidate.keys.chainId),
                    "device",
                    device,
                    "POST",
                    url.encodedPath,
                    bodyJson = candidate.ack,
                    signingKeyBase64 = candidate.keys.signingKeyBase64Url,
                ),
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val response =
            remote.execute(request(url, token, candidate.ack)) {
                PallaSyncHttpResult.Success(PallaSyncCore.validateKeysAck(it.pallaSyncBytes(), device, checkpoint.epochHash))
            }
        if (response !is PallaSyncHttpResult.Success) throw PallaSyncInitialHistory.Failure(response)
    }

    private fun verifyDelivery(
        base: HttpUrl,
        candidate: Candidate,
        checkpoint: PallaSyncGenesisCheckpoint,
    ) {
        val chain = checkNotNull(candidate.keys.chainId)
        val device = checkNotNull(candidate.keys.deviceId)
        val pageLimit = minOf(PALLASYNC_PAGE_SIZE, policy.pageLimit(PallaSyncUrls.extractOrigin(base)))
        val url =
            base
                .newBuilder()
                .encodedPath("/pallasync/v3/chains/$chain/devices/$device/envelopes")
                .addQueryParameter("epoch", candidate.keys.epoch.toString())
                .addQueryParameter("limit", pageLimit.toString())
                .build()
        val token =
            checkNotNull(
                crypto.createCapabilityToken(
                    PallaSyncUrls.extractOrigin(base),
                    chain,
                    "device",
                    device,
                    "GET",
                    url.encodedPath,
                    query = url.encodedQuery.orEmpty(),
                    signingKeyBase64 = candidate.keys.signingKeyBase64Url,
                ),
            ) {
                "C_BOOTSTRAP_TRUST_REQUIRED"
            }
        val expected =
            Json.parseToJsonElement(candidate.body).jsonObject.getValue("envelopes").jsonArray.single {
                it.jsonObject
                    .getValue("epoch")
                    .jsonPrimitive.long == candidate.keys.epoch
            }
        val request =
            Request
                .Builder()
                .url(
                    url,
                ).header("Accept", PALLASYNC_MEDIA_TYPE)
                .header("Authorization", "PallaSync $token")
                .get()
                .build()
        val response =
            remote.execute(request) {
                PallaSyncHttpResult.Success(
                    PallaSyncCore.validateOwnEnvelopePage(it.pallaSyncBytes(), expected.toString(), checkpoint.authority.toString()),
                )
            }
        if (response !is PallaSyncHttpResult.Success) throw PallaSyncInitialHistory.Failure(response)
        PallaSyncEpochSession.begin(candidate.keys, checkpoint.authority.toString(), candidate.keys.epoch).use { epoch ->
            val private = Base64.getUrlDecoder().decode(candidate.keys.kexPrivateKeyBase64Url)
            try {
                check(PallaSyncCore.verifyDeviceKeyDelivery(epoch.handle, response.value, private)) { "C_COMMITMENT_MISMATCH" }
            } finally {
                private.fill(0)
            }
        }
    }

    private fun request(
        url: HttpUrl,
        token: String,
        body: String,
    ): Request =
        Request
            .Builder()
            .url(url)
            .header("Accept", PALLASYNC_MEDIA_TYPE)
            .header("Authorization", "PallaSync $token")
            .post(body.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
            .build()
}
