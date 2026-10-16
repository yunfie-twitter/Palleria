package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ChainStateEntity
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

/** §§11.5/17.9–17.10. Bounded signed ranges; T advancement never advances the data-log cursor. */
internal class PallaSyncConsistencyProbe(
    private val store: PallaSyncLocalStore,
    private val remote: PallaSyncRemoteService,
    private val policy: PallaSyncPolicyGate,
    private val keys: PallaSyncKeystore,
    private val token: (HttpUrl, String, String, String, String, String) -> String?,
    private val resolvePruneCompaction: suspend (chainId: String, entry: JsonObject) -> JsonObject?,
) {
    private val json = Json

    suspend fun run(
        base: HttpUrl,
        chainId: String,
    ): PallaSyncHttpResult<Unit> {
        val result =
            try {
                compare(context(base, chainId))
            } catch (failure: ProbeFailure) {
                failure.result
            } catch (error: IllegalStateException) {
                PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_SCHEMA_VIOLATION")
            }
        if (result is PallaSyncHttpResult.ProtocolError && result.message in CONTRADICTIONS) {
            store.dao.pauseIntegrityWrites(chainId)
        }
        return result
    }

    private suspend fun context(
        base: HttpUrl,
        chain: String,
    ): ProbeContext {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        checkNotNull(state.logId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val authority = checkNotNull(store.protocolDao().currentAuthority(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(PallaSyncCore.objectHash(authority) == keys.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
        val anchor = checkNotNull(keys.transparencyPin(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val index = anchor.getValue("index").jsonPrimitive.long
        check(state.transparencyIndex >= index) { "C_ROLLBACK_DETECTED" }
        if (state.transparencyIndex == index) {
            check(state.transparencyHash == anchor.getValue("hash").jsonPrimitive.content) {
                "C_TRANSPARENCY_INVALID"
            }
        }
        val origin = PallaSyncUrls.extractOrigin(base)
        val protectedPin =
            Json
                .parseToJsonElement(authority)
                .jsonObject
                .getValue("pin")
                .jsonObject
        check(
            protectedPin.getValue("origin").jsonPrimitive.content == origin &&
                protectedPin.getValue("public_key").jsonPrimitive.content == policy.trustedRelayKey(origin),
        ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val pin =
            buildJsonObject {
                put("origin", origin)
                put("chain_id", chain)
                put("public_key", policy.trustedRelayKey(origin))
                put("log_id", state.logId)
            }.toString()
        val checkpoint =
            buildJsonObject {
                put("anchor", anchor)
                put("verified_seq", state.verifiedSeq)
                put("verified_hash", checkNotNull(state.verifiedHash) { "C_BOOTSTRAP_TRUST_REQUIRED" })
                put("parameters_hash", checkNotNull(state.parametersHash) { "C_BOOTSTRAP_TRUST_REQUIRED" })
                put("epoch_hash", checkNotNull(state.epochHash) { "C_BOOTSTRAP_TRUST_REQUIRED" })
            }.toString()
        return ProbeContext(base, state, origin, pin, checkpoint, anchor)
    }

    private suspend fun compare(context: ProbeContext): PallaSyncHttpResult<Unit> {
        val log = readLogHead(context).requireValue()
        val response =
            remote
                .execute(request(PallaSyncUrls.transparencyHead(context.base, context.chain), context.chain)) {
                    val bytes = it.pallaSyncBytes()
                    val head = json.parseToJsonElement(PallaSyncCore.observeTransparencyHead(bytes, context.pin)).jsonObject
                    PallaSyncHttpResult.Success(ObservedHead(bytes, head))
                }.requireValue()
        // Store every authenticated observation before the rollback/fork comparison can fail.
        retain(
            context,
            response.head,
            "transparency_head",
            response.head
                .getValue("entry_index")
                .jsonPrimitive.long,
        )
        PallaSyncCore.validateTransparencyHead(response.bytes, context.pin, context.checkpoint)
        requireMatchingView(context, log, response.head)
        val target =
            pendingTarget(context) ?: response.also {
                retain(
                    context,
                    it.head,
                    "transparency_target",
                    it.head
                        .getValue("entry_index")
                        .jsonPrimitive.long,
                )
            }
        val trustedHead = keys.verifiedTransparencyHead(context.chain)
        val minimum =
            target.head
                .getValue("min_available_seq")
                .jsonPrimitive.long
        val previousMinimum = trustedHead?.get("min_available_seq")?.jsonPrimitive?.long ?: 1L
        check(minimum >= previousMinimum) { "C_ROLLBACK_DETECTED" }
        if (minimum > previousMinimum) {
            checkNotNull(trustedHead) { "C_MISSING_DEPENDENCY" }
            check(
                trustedHead.getValue("log_id") == target.head.getValue("log_id") &&
                    trustedHead.getValue("transparency_id") == target.head.getValue("transparency_id"),
            ) { "C_TRANSPARENCY_INVALID" }
        }
        val result = stream(context, target.head, trustedHead, previousMinimum, minimum)
        if (result is PallaSyncHttpResult.Success) {
            val pin = checkNotNull(keys.transparencyPin(context.chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            check(
                pin.getValue("index").jsonPrimitive.long ==
                    target.head
                        .getValue("entry_index")
                        .jsonPrimitive.long &&
                    pin.getValue("hash").jsonPrimitive.content ==
                    target.head
                        .getValue("transparency_hash")
                        .jsonPrimitive.content,
            ) { "C_TRANSPARENCY_INVALID" }
            check(keys.retainVerifiedTransparencyHead(context.chain, target.head)) { "C_LOCAL_STORAGE_FULL" }
        }
        return result
    }

    /** A validated target survives cancellation; never replace an unfinished target mid-suffix. */
    private suspend fun pendingTarget(context: ProbeContext): ObservedHead? {
        val saved = store.protocolDao().latestEvidence(context.chain, context.logId, "transparency_target")
        val head = saved?.let { json.parseToJsonElement(it).jsonObject } ?: return null
        val targetIndex = head.getValue("entry_index").jsonPrimitive.long
        return if (!PallaSyncTransparencyProgress.shouldResumeSavedTarget(targetIndex, context.state.transparencyIndex)) {
            null
        } else {
            check(
                head.getValue("transparency_id").jsonPrimitive.content ==
                    context.anchor
                        .getValue("transparency_id")
                        .jsonPrimitive.content &&
                    head.getValue("log_id").jsonPrimitive.content == context.logId,
            ) { "C_TRANSPARENCY_INVALID" }
            val bytes = head.toString().toByteArray(Charsets.UTF_8)
            PallaSyncCore.validateTransparencyHead(bytes, context.pin, context.checkpoint)
            ObservedHead(bytes, head)
        }
    }

    private fun requireMatchingView(
        context: ProbeContext,
        log: JsonObject,
        head: JsonObject,
    ) {
        if (!sameView(context, log, head)) {
            // Concurrent acceptance can advance the first view while the signed view is being read.
            check(sameView(context, readLogHead(context).requireValue(), head)) { "C_MISSING_DEPENDENCY" }
        }
    }

    private fun sameView(
        context: ProbeContext,
        log: JsonObject,
        head: JsonObject,
    ): Boolean = PallaSyncCore.compareLogHeads(log.toString(), head.toString(), context.pin, context.state.generation)

    private fun readLogHead(context: ProbeContext): PallaSyncHttpResult<JsonObject> =
        remote.execute(request(PallaSyncUrls.logHead(context.base, context.chain), context.chain)) {
            PallaSyncHttpResult.Success(json.parseToJsonElement(PallaSyncCore.validateLogHead(it.pallaSyncBytes())).jsonObject)
        }

    private suspend fun stream(
        context: ProbeContext,
        head: JsonObject,
        trustedHead: JsonObject?,
        previousMinimum: Long,
        expectedMinimum: Long,
    ): PallaSyncHttpResult<Unit> {
        var anchor =
            trustedHead?.let { verified ->
                buildJsonObject {
                    put("transparency_id", verified.getValue("transparency_id"))
                    put("index", verified.getValue("entry_index").jsonPrimitive.long)
                    put("hash", verified.getValue("transparency_hash"))
                }
            } ?: context.anchor
        var observedDataHeadHash = trustedHead?.get("head_hash")?.jsonPrimitive?.content
        var observedMinimum = previousMinimum
        var matchingPruneEntries = 0
        val start = anchor.getValue("index").jsonPrimitive.long
        val through = head.getValue("entry_index").jsonPrimitive.long
        val id = anchor.getValue("transparency_id").jsonPrimitive.content
        var cursor: String? = null
        val seenCursors = mutableSetOf<String>()
        do {
            currentCoroutineContext().ensureActive()
            val url = PallaSyncUrls.transparencyEntries(context.base, context.chain, id, start, through, cursor)
            val checked =
                remote.execute(request(url, context.chain)) { response ->
                    PallaSyncHttpResult.Success(
                        json
                            .parseToJsonElement(
                                PallaSyncCore.validateTransparencyPage(
                                    response.pallaSyncBytes(),
                                    context.pin,
                                    anchor.toString(),
                                    head.toString(),
                                    PALLASYNC_PAGE_SIZE,
                                ),
                            ).jsonObject,
                    )
                }
            if (checked !is PallaSyncHttpResult.Success) return checked.failure()
            val next = checked.value.getValue("next_anchor").jsonObject
            val page = checked.value.getValue("response").jsonObject
            page.getValue("items").jsonArray.forEach { item ->
                val entry = item.jsonObject
                val operation = entry.getValue("operation").jsonPrimitive.content
                observedDataHeadHash = PallaSyncPruneTransparencyEvidence.advanceDataHead(entry, observedDataHeadHash)
                if (operation == "prune") {
                    val compaction = checkNotNull(resolvePruneCompaction(context.chain, entry)) { "C_MISSING_DEPENDENCY" }
                    observedMinimum =
                        PallaSyncPruneTransparencyEvidence.advanceMinimum(
                            entry,
                            compaction,
                            context.chain,
                            context.logId,
                            checkNotNull(observedDataHeadHash) { "C_MISSING_DEPENDENCY" },
                            observedMinimum,
                        )
                    matchingPruneEntries++
                }
            }
            persistPage(context, page, next)
            val savedPin = checkNotNull(keys.transparencyPin(context.chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val nextIndex = next.getValue("index").jsonPrimitive.long
            val savedIndex = savedPin.getValue("index").jsonPrimitive.long
            if (nextIndex > savedIndex) {
                check(keys.publishTransparencyPin(context.chain, next)) { "C_LOCAL_STORAGE_FULL" }
            } else if (nextIndex == savedIndex) {
                check(next == savedPin) { "C_TRANSPARENCY_INVALID" }
            }
            anchor = next
            cursor =
                page
                    .getValue("next_cursor")
                    .jsonPrimitive
                    .takeIf { it.isString }
                    ?.content
            check(cursor == null || seenCursors.add(checkNotNull(cursor))) { "C_TRANSPARENCY_INVALID" }
        } while (cursor != null)
        check(observedMinimum == expectedMinimum) { "C_TRANSPARENCY_INVALID" }
        check((matchingPruneEntries > 0) == (expectedMinimum > previousMinimum)) { "C_TRANSPARENCY_INVALID" }
        observedDataHeadHash?.let {
            check(it == head.getValue("head_hash").jsonPrimitive.content) { "C_TRANSPARENCY_INVALID" }
        }
        check(
            anchor.getValue("index").jsonPrimitive.long == head.getValue("entry_index").jsonPrimitive.long &&
                anchor.getValue("hash").jsonPrimitive.content == head.getValue("transparency_hash").jsonPrimitive.content,
        ) { "C_TRANSPARENCY_INVALID" }
        return PallaSyncHttpResult.Success(Unit)
    }

    private suspend fun persistPage(
        context: ProbeContext,
        page: JsonObject,
        next: JsonObject,
    ) {
        store.transaction {
            val current = checkNotNull(store.dao.getChainState(context.chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val index = next.getValue("index").jsonPrimitive.long
            val hash = next.getValue("hash").jsonPrimitive.content
            check(current.logId == context.state.logId && current.transparencyId == context.state.transparencyId) {
                "C_ROLLBACK_DETECTED"
            }
            if (index == current.transparencyIndex) check(hash == current.transparencyHash) { "C_TRANSPARENCY_INVALID" }
            // A crash between Room and protected-pin commits may require replay. Compare at the saved boundary.
            val boundary =
                page.getValue("items").jsonArray.map { it.jsonObject }.firstOrNull {
                    it.getValue("entry_index").jsonPrimitive.long == current.transparencyIndex + 1
                }
            if (boundary != null) {
                check(boundary.getValue("previous_transparency_hash").jsonPrimitive.content == current.transparencyHash) {
                    "C_TRANSPARENCY_INVALID"
                }
            }
            page.getValue("items").jsonArray.forEach { entry ->
                retain(
                    context,
                    entry.jsonObject,
                    "transparency",
                    entry.jsonObject
                        .getValue("entry_index")
                        .jsonPrimitive.long,
                )
            }
            if (index >
                current.transparencyIndex
            ) {
                store.dao.updateChainState(current.copy(transparencyIndex = index, transparencyHash = hash))
            }
        }
    }

    private suspend fun retain(
        context: ProbeContext,
        value: JsonObject,
        kind: String,
        sequence: Long,
    ) {
        store.protocolDao().retainEvidence(
            ProtectedEvidenceEntity(
                context.origin,
                value.getValue("relay_key_id").jsonPrimitive.content,
                context.chain,
                value.getValue("log_id").jsonPrimitive.content,
                kind,
                sequence,
                checkNotNull(PallaSyncCore.objectHash(value.toString())),
                value.toString(),
            ),
        )
    }

    private fun request(
        url: HttpUrl,
        chain: String,
    ): Request {
        val authorization =
            checkNotNull(token(url, chain, "GET", url.encodedPath, url.encodedQuery.orEmpty(), "")) {
                "C_BOOTSTRAP_TRUST_REQUIRED"
            }
        return Request
            .Builder()
            .url(url)
            .header("Accept", PALLASYNC_MEDIA_TYPE)
            .header("Authorization", "PallaSync $authorization")
            .get()
            .build()
    }

    private class ProbeFailure(
        val result: PallaSyncHttpResult<Nothing>,
    ) : RuntimeException()

    private data class ProbeContext(
        val base: HttpUrl,
        val state: ChainStateEntity,
        val origin: String,
        val pin: String,
        val checkpoint: String,
        val anchor: JsonObject,
    ) {
        val chain: String get() = state.chainId
        val logId: String get() = checkNotNull(state.logId)
    }

    private data class ObservedHead(
        val bytes: ByteArray,
        val head: JsonObject,
    )

    private companion object {
        val CONTRADICTIONS = setOf("C_ROLLBACK_DETECTED", "C_LOG_HASH_MISMATCH", "C_RELAY_KEY_MISMATCH", "C_TRANSPARENCY_INVALID")

        fun PallaSyncHttpResult<*>.failure(): PallaSyncHttpResult<Nothing> =
            when (this) {
                PallaSyncHttpResult.Gone -> PallaSyncHttpResult.Gone
                is PallaSyncHttpResult.ProtocolError -> this
                is PallaSyncHttpResult.Retryable -> this
                is PallaSyncHttpResult.Success -> error("C_SCHEMA_VIOLATION")
            }

        fun <T> PallaSyncHttpResult<T>.requireValue(): T =
            when (this) {
                is PallaSyncHttpResult.Success -> value
                else -> throw ProbeFailure(failure())
            }
    }
}
