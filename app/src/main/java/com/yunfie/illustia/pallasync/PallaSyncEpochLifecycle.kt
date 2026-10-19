package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.util.Base64

/** §13: explicit foreground operation, durable proposal, complete H/T evidence,
 * recipient delivery and OS readback before ack. Old keys and outbox remain intact.
 * Operations follow independently resumable protocol and OS-store boundaries.
 */
@Suppress("TooManyFunctions")
internal class PallaSyncEpochLifecycle(
    private val store: PallaSyncLocalStore,
    private val keys: PallaSyncKeystore,
    private val remote: PallaSyncRemoteService,
    private val policy: PallaSyncPolicyGate,
    private val crypto: PallaSyncCryptoService,
    private val journal: PallaSyncEpochJournal,
    private val author: PallaSyncLocalAuthor,
    private val fault: (Boundary) -> Unit = {},
) {
    enum class Boundary {
        PROPOSAL_STAGED,
        DISPATCH_STAGED,
        ACCEPTANCE_RETAINED,
        AUTHORITY_INSTALLED,
        NORMAL_KEYS_READBACK,
        ACK_OBSERVED,
        EPOCH_PUBLISHED,
    }

    enum class Outcome { UPDATED, CAS_LOST }

    var stage: String = "idle"
        private set

    suspend fun rotate(
        root: PallaSyncRootSession,
        base: HttpUrl,
        reason: String,
        revoked: List<String>,
        admin: Boolean,
    ): Outcome {
        val chain = root.identity.chainId
        val origin = PallaSyncUrls.extractOrigin(base)
        val before = refresh(root, base)
        val existing = journal.pending(chain)
        var pending = existing
        if (existing != null) {
            requireCandidate(existing, origin, activeKeys(chain))
            val known = before.authority!!.getValue("epochs").jsonObject[existing.proposal.epoch.toString()]
            if (known != null) return resolve(existing, before)
            val requested =
                existing.proposal.requestBody
                    .getValue("epoch")
                    .jsonObject
            check(
                requested.getValue("reason").jsonPrimitive.content == reason &&
                    requested.getValue("revoked_device_ids").jsonArray.map { it.jsonPrimitive.content } == revoked.sorted() &&
                    (requested.getValue("signer_kind").jsonPrimitive.content == "admin") == admin,
            ) {
                "C_MISSING_DEPENDENCY"
            }
        } else {
            val current = activeKeys(chain)
            stage = "proposal"
            crypto.prepareEpoch(root, checkNotNull(before.authority).toString(), current, reason, revoked, admin, origin).use {
                pending =
                    PallaSyncEpochJournal.Candidate(
                        origin,
                        checkNotNull(current.deviceId),
                        hash(checkNotNull(before.authority)),
                        it.publicResult,
                    )
                journal.stage(checkNotNull(pending), it.epochSecret())
            }
            fault(Boundary.PROPOSAL_STAGED)
        }
        currentCoroutineContext().ensureActive()
        stage = "proposal_dispatch"
        val candidate = journal.submitted(checkNotNull(pending))
        fault(Boundary.DISPATCH_STAGED)
        var reconciled: PallaSyncGenesisCheckpoint? = null
        try {
            send(root, base, candidate, before)
        } catch (failure: PallaSyncInitialHistory.Failure) {
            // A semantic hint does not authorize erasure. Fetch the signed winner first.
            val code = (failure.result as? PallaSyncHttpResult.ProtocolError)?.message
            if (code != "CAS_MISMATCH" && code != "EPOCH_STALE") throw failure
            reconciled = refresh(root, base)
        }
        val after = reconciled ?: refresh(root, base)
        return resolve(checkNotNull(journal.pending(chain)), after)
    }

    /** Root is explicitly supplied when D cannot read the complete controls (§7.7).
     * Loading an existing protected checkpoint does not choose a new source.
     */
    suspend fun refresh(
        root: PallaSyncRootSession,
        base: HttpUrl,
    ): PallaSyncGenesisCheckpoint {
        val chain = root.identity.chainId
        PallaSyncAuthorityCommit(store, keys).recover(chain)
        val old = protectedAuthority(chain)
        stage = "history_binding"
        val candidate = journal.pending(chain)
        var initial = old
        if (candidate != null && old.getValue("epochs").jsonObject[candidate.proposal.epoch.toString()] == null) {
            requireCandidate(candidate, PallaSyncUrls.extractOrigin(base), activeKeys(chain))
            val body = candidate.proposal.requestBody
            val adminOperation = body.getValue("admin_operation")
            if (adminOperation is JsonObject) {
                initial =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.bindAuthorityActionPayload(
                                old.toString(),
                                adminOperation.toString(),
                                buildJsonObject {
                                    put("epoch", body.getValue("epoch"))
                                    put("envelopes", body.getValue("envelopes"))
                                }.toString(),
                            ),
                        ).jsonObject
            }
        }
        val checkpoint =
            PallaSyncInitialHistory(store, remote, policy, root, base) {
                stage = it
            }.load(initial, checkNotNull(keys.transparencyPin(chain)))
        stage = "authority_install"
        installCut(chain, old, checkpoint)
        activateCurrent(base, activeKeys(chain), checkpoint)
        return checkpoint
    }

    /**
     * §19.3/§19.4: replace one device envelope only after explicit root authorization,
     * signed recovery-envelope validation, relay acceptance and signed-log replay.
     */
    suspend fun repairDeviceEnvelope(
        root: PallaSyncRootSession,
        base: HttpUrl,
        epoch: Long,
    ) {
        val chain = root.identity.chainId
        val device = checkNotNull(keys.boundDeviceId(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val oldAuthority = protectedAuthority(chain)
        val before =
            PallaSyncInitialHistory(store, remote, policy, root, base)
                .load(oldAuthority, checkNotNull(keys.transparencyPin(chain)))
        val beforeAuthority = checkNotNull(before.authority) { "C_MISSING_DEPENDENCY" }
        check(PallaSyncCore.validateCurrentKeyRecipient(beforeAuthority.toString(), device)) {
            "C_RECOVERY_AUTH_REQUIRED"
        }
        val oldDeviceKeys = activeKeys(chain)
        val origin = PallaSyncUrls.extractOrigin(base)
        val history = PallaSyncInitialHistory(store, remote, policy, root, base)
        val recoveryEnvelope =
            PallaSyncCore.validateRecoveryEnvelopeResponse(
                history.get(history.url("epochs/$epoch/recovery-envelope")),
                beforeAuthority.toString(),
                epoch,
            )
        val replacement =
            root.createReplacementDeviceEnvelope(
                recoveryEnvelope,
                device,
                oldDeviceKeys.kexPublicKeyBase64Url,
            )
        val replacementObject = Json.parseToJsonElement(replacement).jsonObject
        check(
            replacementObject.getValue("chain_id").jsonPrimitive.content == chain &&
                replacementObject.getValue("recipient_device_id").jsonPrimitive.content == device &&
                replacementObject.getValue("epoch").jsonPrimitive.long == epoch &&
                replacementObject.getValue("signer_kind").jsonPrimitive.content == "admin",
        ) { "C_SCHEMA_VIOLATION" }
        val body = buildJsonObject { put("envelopes", kotlinx.serialization.json.JsonArray(listOf(replacementObject))) }.toString()
        val url =
            base
                .newBuilder()
                .encodedPath("/pallasync/v3/chains/$chain/epochs/$epoch/envelopes")
                .build()
        val token =
            checkNotNull(root.token(origin, "POST", url.encodedPath, body = body.toByteArray())) {
                "C_RECOVERY_AUTH_REQUIRED"
            }
        val response =
            requireSuccess(
                remote.execute(request(url, token, body)) { result ->
                    val value = Json.parseToJsonElement(result.pallaSyncBytes().toString(Charsets.UTF_8)).jsonObject
                    check(value.keys == setOf("accepted", "rejected")) { "C_SCHEMA_VIOLATION" }
                    val accepted = value.getValue("accepted").jsonArray.map { it.jsonPrimitive.content }
                    val rejected = value.getValue("rejected").jsonArray
                    if (rejected.isNotEmpty()) {
                        val item = rejected.single().jsonObject
                        check(item.keys == setOf("envelope_id", "status", "code", "retryable")) { "C_SCHEMA_VIOLATION" }
                        error(item.getValue("code").jsonPrimitive.content)
                    }
                    PallaSyncHttpResult.Success(accepted)
                },
            )
        check(response == listOf(replacementObject.getValue("envelope_id").jsonPrimitive.content)) {
            "C_ACCEPTANCE_UNPROVEN"
        }

        val after =
            PallaSyncInitialHistory(store, remote, policy, root, base)
                .load(beforeAuthority, checkNotNull(keys.transparencyPin(chain)))
        installCut(chain, oldAuthority, after)
        val authority = checkNotNull(after.authority) { "C_MISSING_DEPENDENCY" }
        val replacementHash = checkNotNull(PallaSyncCore.objectHash(replacement)) { "C_SCHEMA_VIOLATION" }
        val evidence =
            checkNotNull(
                store.protocolDao().authorityObject(
                    chain,
                    PallaSyncEnvelopeEvidence.kind(device, epoch),
                    replacementHash,
                ),
            ) { "C_ACCEPTANCE_UNPROVEN" }
        check(evidence.validationState == "authority_verified") { "C_ACCEPTANCE_UNPROVEN" }
        val logEntry = Json.parseToJsonElement(evidence.signedJson).jsonObject
        val sequence = logEntry.getValue("relay_seq").jsonPrimitive.long
        val row = checkNotNull(store.protocolDao().logEntry(chain, after.logId, sequence)) { "C_ACCEPTANCE_UNPROVEN" }
        check(
            row.signedJson == evidence.signedJson && row.validationState in setOf("authority_verified", "materialized") &&
                logEntry.getValue("object") == replacementObject,
        ) { "C_LOG_HASH_MISMATCH" }

        val ownEnvelopeUrl =
            endpoint(base, chain, "devices/$device/envelopes")
                .newBuilder()
                .addQueryParameter("epoch", epoch.toString())
                .addQueryParameter("limit", PALLASYNC_PAGE_SIZE.toString())
                .build()
        val ownEnvelopeToken =
            checkNotNull(root.token(origin, "GET", ownEnvelopeUrl.encodedPath, ownEnvelopeUrl.encodedQuery.orEmpty())) {
                "C_RECOVERY_AUTH_REQUIRED"
            }
        requireSuccess(
            remote.execute(request(ownEnvelopeUrl, ownEnvelopeToken)) {
                PallaSyncHttpResult.Success(
                    PallaSyncCore.validateOwnEnvelopePage(it.pallaSyncBytes(), replacement, authority.toString()),
                )
            },
        )
        val privateKey = decode(oldDeviceKeys.kexPrivateKeyBase64Url)
        val recoveredSecret =
            try {
                PallaSyncCore.openOwnEpochEnvelope(
                    authority.toString(),
                    row.signedJson,
                    row.receiptJson,
                    device,
                    privateKey,
                )
            } finally {
                privateKey.fill(0)
            }
        val currentKeys = checkNotNull(keys.keysForEpoch(oldDeviceKeys, epoch)) { "C_MISSING_DEPENDENCY" }
        val encodedSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(recoveredSecret)
        try {
            val verifiedMaterial =
                PallaSyncEpochSession.materialize(currentKeys.copy(epochSecretBase64Url = encodedSecret), authority.toString())
            keys.replaceEpochMaterial(chain, epoch, verifiedMaterial, recoveredSecret)
        } finally {
            recoveredSecret.fill(0)
        }
    }

    private suspend fun installCut(
        chain: String,
        old: JsonObject,
        checkpoint: PallaSyncGenesisCheckpoint,
    ) {
        val authority = checkNotNull(checkpoint.authority)
        if (hash(authority) != hash(old)) PallaSyncAuthorityCommit(store, keys).install(chain, hash(old), authority)
        val anchor =
            buildJsonObject {
                put("transparency_id", checkpoint.transparencyId)
                put("index", checkpoint.transparencyIndex)
                put("hash", checkpoint.transparencyHash)
            }
        store.transaction {
            val state = checkNotNull(store.dao.getChainState(chain))
            check(state.transparencyId == checkpoint.transparencyId && state.transparencyIndex <= checkpoint.transparencyIndex) {
                "C_ROLLBACK_DETECTED"
            }
            if (state.transparencyIndex == checkpoint.transparencyIndex) {
                check(state.transparencyHash == checkpoint.transparencyHash) { "C_TRANSPARENCY_INVALID" }
            } else {
                val pin = authority.getValue("pin").jsonObject
                val next =
                    store
                        .protocolDao()
                        .evidenceAt(
                            pin.getValue("origin").jsonPrimitive.content,
                            relayKeyId(pin.getValue("public_key").jsonPrimitive.content),
                            chain,
                            checkpoint.logId,
                            "transparency",
                            state.transparencyIndex + 1,
                        ).singleOrNull() ?: error("C_MISSING_DEPENDENCY")
                check(
                    Json
                        .parseToJsonElement(next)
                        .jsonObject
                        .getValue("previous_transparency_hash")
                        .jsonPrimitive.content ==
                        state.transparencyHash,
                ) { "C_TRANSPARENCY_INVALID" }
            }
            store.dao.updateChainState(
                state.copy(transparencyIndex = checkpoint.transparencyIndex, transparencyHash = checkpoint.transparencyHash),
            )
        }
        check(keys.publishTransparencyPin(chain, anchor)) { "C_LOCAL_STORAGE_FULL" }
        fault(Boundary.AUTHORITY_INSTALLED)
    }

    internal suspend fun installAndActivateVerifiedCheckpoint(
        chain: String,
        old: JsonObject,
        checkpoint: PallaSyncGenesisCheckpoint,
        base: HttpUrl,
        current: PallaSyncKeySnapshot,
    ) {
        installCut(chain, old, checkpoint)
        activateCurrent(base, current, checkpoint)
    }

    private suspend fun activateCurrent(
        base: HttpUrl,
        previous: PallaSyncKeySnapshot,
        checkpoint: PallaSyncGenesisCheckpoint,
    ) {
        val chain = checkNotNull(previous.chainId)
        val id = checkNotNull(previous.deviceId)
        val authority = checkNotNull(checkpoint.authority)
        val admission =
            authority
                .getValue("devices")
                .jsonObject
                .getValue(id)
                .jsonObject
        if (admission.getValue("revoked_seq").jsonPrimitive.content != "null") {
            store.dao.updateChainState(checkNotNull(store.dao.getChainState(chain)).copy(writeState = "device_revoked"))
            error("C_RECOVERY_AUTH_REQUIRED")
        }
        val epoch = currentEpoch(authority)
        check(PallaSyncCore.validateCurrentKeyRecipient(authority.toString(), id)) { "C_MISSING_DEPENDENCY" }
        val number = epoch.getValue("epoch").jsonPrimitive.long
        val state = checkNotNull(store.dao.getChainState(chain))
        check(state.writeState != "integrity_paused") { "C_EPOCH_FORK" }
        if (state.keyEpoch == number && previous.epoch == number && state.writeState != "epoch_pending_keys") {
            stage = "materialize_current"
            PallaSyncPrefixMaterializer(store, keys).apply(chain, previous, checkpoint)
            remote.reconciled(base, chain)
            return
        }
        val retained = checkNotNull(keys.keysForEpoch(previous, state.keyEpoch)) { "C_MISSING_DEPENDENCY" }
        author.finishPending(chain, PallaSyncEpochSession.materialize(retained, authority.toString()))
        val next = deliver(base, previous, checkpoint, number)
        stage = "normal_keys_readback"
        val oldKeys = checkNotNull(keys.keysForEpoch(previous, state.keyEpoch)) { "C_MISSING_DEPENDENCY" }
        check(keys.retainEpochSecret(chain, oldKeys.epoch, decode(oldKeys.epochSecretBase64Url))) { "C_LOCAL_STORAGE_FULL" }
        check(keys.saveActiveChainKeys(next) && keys.getActiveChainKeys() == next) { "C_LOCAL_STORAGE_FULL" }
        fault(Boundary.NORMAL_KEYS_READBACK)
        PallaSyncPrefixMaterializer(store, keys).apply(chain, next, checkpoint)
        stage = "epoch_ack"
        remote.reconciled(base, chain)
        acknowledge(base, next, checkpoint, epoch)
        fault(Boundary.ACK_OBSERVED)
        store.transaction {
            val current = checkNotNull(store.dao.getChainState(chain))
            check(current.epochHash == checkpoint.epochHash && current.writeState != "integrity_paused") { "C_EPOCH_FORK" }
            store.dao.updateChainState(
                current.copy(
                    keyEpoch = number,
                    writeState =
                        if (store.dao.pendingOutboxCount(chain) ==
                            0L
                        ) {
                            "active"
                        } else {
                            "epoch_rebase_required"
                        },
                ),
            )
        }
        fault(Boundary.EPOCH_PUBLISHED)
    }

    private suspend fun deliver(
        base: HttpUrl,
        previous: PallaSyncKeySnapshot,
        checkpoint: PallaSyncGenesisCheckpoint,
        epoch: Long,
    ): PallaSyncKeySnapshot {
        val chain = checkNotNull(previous.chainId)
        val authority = checkNotNull(checkpoint.authority)
        stage = "device_key_delivery"
        val secret = openDeliveredSecret(base, previous, checkpoint, epoch)
        try {
            val pending = journal.pending(chain)
            if (pending?.proposal?.epoch == epoch && pending.proposal.epochHash == hash(expectedEpoch(authority, epoch))) {
                val proposed = journal.normalSecret(pending)
                try {
                    check(MessageDigest.isEqual(secret, proposed)) { "C_COMMITMENT_MISMATCH" }
                } finally {
                    proposed.fill(0)
                }
            }
            val signed = expectedEpoch(authority, epoch)
            val context =
                buildJsonObject {
                    put("chain_id", chain)
                    put("generation", signed.getValue("generation"))
                    put("epoch", epoch)
                    put("epoch_commitment", signed.getValue("epoch_commitment"))
                    put(
                        "metadata_profile",
                        authority
                            .getValue("parameters")
                            .jsonObject
                            .getValue("policy")
                            .jsonObject
                            .getValue("metadata_profile"),
                    )
                }
            val next = PallaSyncCore.deriveEpochMaterial(context.toString(), secret).use { it.snapshot(previous, epoch) }
            check(keys.retainEpochSecret(chain, epoch, secret.copyOf())) { "C_LOCAL_STORAGE_FULL" }
            if (pending?.proposal?.epoch == epoch && pending.proposal.epochHash == hash(signed)) {
                journal.delivered(if (pending.submitted) pending else journal.submitted(pending))
            }
            return next
        } finally {
            secret.fill(0)
        }
    }

    private suspend fun openDeliveredSecret(
        base: HttpUrl,
        previous: PallaSyncKeySnapshot,
        checkpoint: PallaSyncGenesisCheckpoint,
        epoch: Long,
    ): ByteArray {
        val chain = checkNotNull(previous.chainId)
        val device = checkNotNull(previous.deviceId)
        val authority = checkNotNull(checkpoint.authority)
        val indexed =
            store.protocolDao().firstAuthorityOfKind(chain, PallaSyncEnvelopeEvidence.kind(device, epoch)) ?: error("C_MISSING_DEPENDENCY")
        val entry = Json.parseToJsonElement(indexed.signedJson).jsonObject
        val row =
            checkNotNull(store.protocolDao().logEntry(chain, checkpoint.logId, entry.getValue("relay_seq").jsonPrimitive.long)) {
                "C_MISSING_DEPENDENCY"
            }
        check(row.signedJson == indexed.signedJson) { "C_LOG_HASH_MISMATCH" }
        val expected = entry.getValue("object")
        val url =
            endpoint(
                base,
                chain,
                "devices/$device/envelopes",
            ).newBuilder().addQueryParameter("epoch", epoch.toString()).addQueryParameter("limit", PALLASYNC_PAGE_SIZE.toString()).build()
        val token = token(url, previous, "GET", "")
        requireSuccess(
            remote.execute(request(url, token)) {
                PallaSyncHttpResult.Success(
                    PallaSyncCore.validateOwnEnvelopePage(it.pallaSyncBytes(), expected.toString(), authority.toString()),
                )
            },
        )
        val private = decode(previous.kexPrivateKeyBase64Url)
        return try {
            PallaSyncCore.openOwnEpochEnvelope(authority.toString(), row.signedJson, row.receiptJson, device, private)
        } finally {
            private.fill(0)
        }
    }

    private fun acknowledge(
        base: HttpUrl,
        next: PallaSyncKeySnapshot,
        checkpoint: PallaSyncGenesisCheckpoint,
        epoch: JsonObject,
    ) {
        val device = checkNotNull(next.deviceId)
        val url = PallaSyncUrls.deviceKeysAck(base, checkNotNull(next.chainId), device)
        val body =
            buildJsonObject {
                put("epoch_hash", checkpoint.epochHash)
                put("epoch_commitment", epoch.getValue("epoch_commitment"))
            }.toString()
        requireSuccess(
            remote.execute(request(url, token(url, next, "POST", body), body)) {
                PallaSyncHttpResult.Success(PallaSyncCore.validateKeysAck(it.pallaSyncBytes(), device, checkpoint.epochHash))
            },
        )
    }

    private suspend fun send(
        root: PallaSyncRootSession,
        base: HttpUrl,
        candidate: PallaSyncEpochJournal.Candidate,
        before: PallaSyncGenesisCheckpoint,
    ) {
        val body = candidate.proposal.requestBody.toString()
        val signerAdmin =
            candidate.proposal.requestBody
                .getValue("epoch")
                .jsonObject
                .getValue("signer_kind")
                .jsonPrimitive.content == "admin"
        val revocation = signerAdmin && root.operation == PallaSyncRootSession.Operation.REVOKE
        val target =
            if (revocation) {
                candidate.proposal.requestBody
                    .getValue(
                        "epoch",
                    ).jsonObject
                    .getValue("revoked_device_ids")
                    .jsonArray
                    .first()
                    .jsonPrimitive.content
            } else {
                null
            }
        val url = endpoint(base, candidate.proposal.chainId, if (target == null) "epochs" else "devices/$target/revoke")
        val token =
            if (signerAdmin) {
                checkNotNull(root.token(candidate.origin, "POST", url.encodedPath, body = body.toByteArray())) {
                    "C_RECOVERY_AUTH_REQUIRED"
                }
            } else {
                token(url, activeKeys(candidate.proposal.chainId), "POST", body)
            }
        val result =
            requireSuccess(
                remote.execute(request(url, token, body)) {
                    val bytes = it.pallaSyncBytes()
                    val checked =
                        if (target ==
                            null
                        ) {
                            PallaSyncCore.validateEpochAcceptance(bytes, body, before.authority!!.getValue("pin").toString())
                        } else {
                            PallaSyncCore.validateRevocationAcceptance(bytes, body, before.authority!!.getValue("pin").toString(), target)
                        }
                    PallaSyncHttpResult.Success(Json.parseToJsonElement(checked).jsonObject)
                },
            )
        retainAcceptance(candidate, before, result)
        fault(Boundary.ACCEPTANCE_RETAINED)
    }

    private suspend fun retainAcceptance(
        candidate: PallaSyncEpochJournal.Candidate,
        before: PallaSyncGenesisCheckpoint,
        result: JsonObject,
    ) {
        for (value in result.getValue("upload_receipts").jsonArray) {
            val receipt = value.jsonObject
            store.protocolDao().retainEvidence(
                ProtectedEvidenceEntity(
                    candidate.origin,
                    receipt.getValue("relay_key_id").jsonPrimitive.content,
                    candidate.proposal.chainId,
                    before.logId,
                    "upload_receipt",
                    receipt.getValue("relay_seq").jsonPrimitive.long,
                    hash(receipt),
                    receipt.toString(),
                ),
            )
        }
    }

    private suspend fun resolve(
        candidate: PallaSyncEpochJournal.Candidate,
        checkpoint: PallaSyncGenesisCheckpoint,
    ): Outcome {
        val authority = checkNotNull(checkpoint.authority)
        val known = expectedEpoch(authority, candidate.proposal.epoch)
        if (hash(known) != candidate.proposal.epochHash) {
            check(hash(authority) == keys.authorityHash(candidate.proposal.chainId)) { "C_ROLLBACK_DETECTED" }
            journal.discardCasLoser(checkNotNull(journal.pending(candidate.proposal.chainId)), authority)
            return Outcome.CAS_LOST
        }
        var pending = checkNotNull(journal.pending(candidate.proposal.chainId))
        if (!pending.delivered) {
            // A committed ancestor still needs recipient readback before releasing the proposal.
            val base = PallaSyncUrls.normalize(candidate.origin).let { (it as PallaSyncHttpResult.Success).value }
            deliver(base, activeKeys(candidate.proposal.chainId), checkpoint, candidate.proposal.epoch)
            pending = checkNotNull(journal.pending(candidate.proposal.chainId))
        }
        journal.finish(journal.acknowledged(pending))
        return Outcome.UPDATED
    }

    private suspend fun protectedAuthority(chain: String): JsonObject {
        val raw = checkNotNull(store.protocolDao().currentAuthority(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(PallaSyncCore.objectHash(raw) == keys.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
        return Json.parseToJsonElement(raw).jsonObject
    }

    private fun activeKeys(chain: String): PallaSyncKeySnapshot {
        val active = checkNotNull(keys.getActiveChainKeys()) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(active.chainId == chain && keys.boundDeviceId(chain) == active.deviceId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return active
    }

    private fun requireCandidate(
        candidate: PallaSyncEpochJournal.Candidate,
        origin: String,
        current: PallaSyncKeySnapshot,
    ) {
        check(candidate.origin == origin && candidate.deviceId == current.deviceId && candidate.proposal.chainId == current.chainId) {
            "C_BOOTSTRAP_TRUST_REQUIRED"
        }
    }

    private fun currentEpoch(authority: JsonObject) =
        authority
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

    private fun expectedEpoch(
        authority: JsonObject,
        epoch: Long,
    ) = checkNotNull(authority.getValue("epochs").jsonObject[epoch.toString()]) {
        "C_MISSING_DEPENDENCY"
    }.jsonObject.getValue("record").jsonObject

    private fun endpoint(
        base: HttpUrl,
        chain: String,
        resource: String,
    ) = base
        .newBuilder()
        .encodedPath("/pallasync/v3/chains/$chain/$resource")
        .query(null)
        .build()

    private fun hash(value: JsonObject) = checkNotNull(PallaSyncCore.objectHash(value.toString())) { "C_SCHEMA_VIOLATION" }

    private fun decode(value: String) = Base64.getUrlDecoder().decode(value)

    private fun relayKeyId(publicKey: String) =
        Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(decode(publicKey)))

    private fun token(
        url: HttpUrl,
        current: PallaSyncKeySnapshot,
        method: String,
        body: String,
    ) = checkNotNull(
        crypto.createCapabilityToken(
            PallaSyncUrls.extractOrigin(url),
            checkNotNull(current.chainId),
            "device",
            current.deviceId,
            method,
            url.encodedPath,
            url.encodedQuery.orEmpty(),
            body,
            current.signingKeyBase64Url,
        ),
    ) {
        "C_BOOTSTRAP_TRUST_REQUIRED"
    }

    private fun request(
        url: HttpUrl,
        token: String,
        body: String? = null,
    ): Request =
        Request
            .Builder()
            .url(url)
            .header("Accept", PALLASYNC_MEDIA_TYPE)
            .header("Authorization", "PallaSync $token")
            .apply {
                if (body ==
                    null
                ) {
                    get()
                } else {
                    post(body.toRequestBody(PALLASYNC_MEDIA_TYPE.toMediaType()))
                }
            }.build()

    private fun <T> requireSuccess(result: PallaSyncHttpResult<T>): T =
        when (result) {
            is PallaSyncHttpResult.Success -> result.value
            else -> throw PallaSyncInitialHistory.Failure(result)
        }
}
