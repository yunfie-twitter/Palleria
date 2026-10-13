package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.AuthorityHistoryEntity
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
import okhttp3.Request

/** §11.6: replay the explicitly selected relay in bounded pages, without materializing plaintext. */
internal class PallaSyncInitialHistory(
    private val store: PallaSyncLocalStore,
    private val remote: PallaSyncRemoteService,
    private val policy: PallaSyncPolicyGate,
    private val root: PallaSyncRootSession,
    private val base: HttpUrl,
    private val progress: (String) -> Unit = {},
) {
    private val origin = PallaSyncUrls.extractOrigin(base)
    private val chain = root.identity.chainId
    private val prefix = "/pallasync/v3/chains/$chain"
    private val json = Json

    suspend fun load(
        previous: JsonObject? = null,
        knownAnchor: JsonObject? = null,
    ): PallaSyncGenesisCheckpoint {
        progress("history_policy")
        if (previous == null) {
            policy.requireCurrent(origin, "relay_full_replay")
        } else {
            policy.requireCurrent(origin)
            val pin = previous.getValue("pin").jsonObject
            check(
                pin.getValue("chain_id").jsonPrimitive.content == chain &&
                    previous.getValue("admin_public_key").jsonPrimitive.content == root.identity.adminPublicKey &&
                    pin.getValue("origin").jsonPrimitive.content == origin &&
                    pin.getValue("public_key").jsonPrimitive.content == policy.trustedRelayKey(origin),
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        }
        val initial = previous ?: initialAuthority()
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

    private fun initialAuthority(): JsonObject {
        val identity = root.identity
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
            initial["parameters"]?.let { if (it is JsonObject) it.getValue("generation").jsonPrimitive.long else null }
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
                root.token(origin, "GET", requestUrl.encodedPath, requestUrl.encodedQuery.orEmpty()),
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
