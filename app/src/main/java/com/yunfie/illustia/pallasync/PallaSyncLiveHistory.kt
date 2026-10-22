package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.LogEntryEntity
import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl
import okhttp3.Request

/** §§7.7/11.2/17.9. Device reads never skip an inaccessible recovery entry. */
internal class PallaSyncLiveHistory(
    private val store: PallaSyncLocalStore,
    private val remote: PallaSyncRemoteService,
    private val keys: PallaSyncKeystore,
    private val probe: PallaSyncConsistencyProbe,
    private val token: (HttpUrl, String, String, String, String, String) -> String?,
) {
    suspend fun pull(
        base: HttpUrl,
        chain: String,
        device: PallaSyncKeySnapshot,
    ): PallaSyncHttpResult<Unit> =
        try {
            pullChecked(base, chain, device)
            PallaSyncHttpResult.Success(Unit)
        } catch (failure: ReadFailure) {
            failure.result
        } catch (failure: IllegalStateException) {
            val code = failure.message?.takeIf { it.matches(Regex("C_[A-Z_]+")) } ?: "C_SCHEMA_VIOLATION"
            if (code in setOf("C_LOG_HASH_MISMATCH", "C_ROLLBACK_DETECTED", "C_EPOCH_FORK")) store.dao.pauseIntegrityWrites(chain)
            PallaSyncHttpResult.ProtocolError(code)
        }

    private suspend fun pullChecked(
        base: HttpUrl,
        chain: String,
        device: PallaSyncKeySnapshot,
    ) {
        val commits = PallaSyncAuthorityCommit(store, keys)
        commits.recover(chain)
        val old = checkNotNull(store.protocolDao().currentAuthority(chain)) { "C_MISSING_DEPENDENCY" }
        val oldHash = hash(Json.parseToJsonElement(old).jsonObject)
        check(oldHash == keys.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
        val before = Json.parseToJsonElement(old).jsonObject
        val authority = replay(base, chain, before)
        if (authority == before &&
            checkNotNull(store.dao.getChainState(chain)).materializedSeq >= authority.getValue("sequence").jsonPrimitive.long &&
            !remote.needsReconciliation(base, chain)
        ) {
            return
        }
        requireValue(probe.run(base, chain))
        val cut =
            Json
                .parseToJsonElement(
                    PallaSyncCore.validateInitialHistoryCut(
                        authority.toString(),
                        get(PallaSyncUrls.transparencyHead(base, chain), chain),
                    ),
                ).jsonObject
        val head = cut.getValue("head").jsonObject
        val anchor = checkNotNull(keys.transparencyPin(chain)) { "C_MISSING_DEPENDENCY" }
        check(anchor.getValue("index") == head.getValue("entry_index") && anchor.getValue("hash") == head.getValue("transparency_hash")) {
            "C_MISSING_DEPENDENCY"
        }
        verifyReceipts(chain, before, authority, anchor)
        if (authority != before) commits.install(chain, oldHash, authority)
        val checkpoint =
            PallaSyncGenesisCheckpoint(
                logId =
                    authority
                        .getValue("pin")
                        .jsonObject
                        .getValue("log_id")
                        .jsonPrimitive.content,
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
        PallaSyncPrefixMaterializer(store, keys).apply(chain, device, checkpoint)
        remote.reconciled(base, chain)
    }

    private suspend fun replay(
        base: HttpUrl,
        chain: String,
        initial: JsonObject,
    ): JsonObject {
        var authority = initial
        val pin = initial.getValue("pin").jsonObject
        val generation =
            initial
                .getValue("parameters")
                .jsonObject
                .getValue("generation")
                .jsonPrimitive.long
        var cursor: String? = null
        val seen = mutableSetOf<String>()
        var expiredCursorRequest: Request? = null
        var lastExpiredSequence: Long? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val after = authority.getValue("sequence").jsonPrimitive.long
            val url =
                endpoint(base, chain, "log")
                    .newBuilder()
                    .addQueryParameter("limit", PALLASYNC_PAGE_SIZE.toString())
                    .apply {
                        if (cursor ==
                            null
                        ) {
                            addQueryParameter("after_seq", after.toString())
                        } else {
                            addQueryParameter("cursor", cursor)
                        }
                    }.build()
            val pageBytes =
                try {
                    get(url, chain)
                } catch (failure: ReadFailure) {
                    val error = failure.result as? PallaSyncHttpResult.ProtocolError
                    if (cursor == null || error?.message != "CURSOR_EXPIRED") throw failure
                    if (lastExpiredSequence == after) throw failure
                    lastExpiredSequence = after
                    expiredCursorRequest =
                        Request
                            .Builder()
                            .url(url)
                            .get()
                            .build()
                    cursor = null
                    seen.clear()
                    continue
                }
            val page =
                Json
                    .parseToJsonElement(
                        PallaSyncCore.validateLogPage(
                            pageBytes,
                            pin.toString(),
                            generation,
                            after,
                            PALLASYNC_PAGE_SIZE,
                        ),
                    ).jsonObject
            expiredCursorRequest?.let { remote.cursorRestarted(base, chain, it) }
            expiredCursorRequest = null
            for (value in page.getValue("items").jsonArray) {
                val entry = value.jsonObject
                val receipt = receipt(base, chain, pin, entry)
                authority =
                    Json
                        .parseToJsonElement(
                            PallaSyncCore.advanceAuthorityHistory(authority.toString(), entry.toString(), receipt.toString()),
                        ).jsonObject
                retain(chain, pin, entry, receipt)
            }
            cursor =
                page
                    .getValue("next_cursor")
                    .jsonPrimitive
                    .takeIf { it.isString }
                    ?.content
            check(cursor == null || seen.add(checkNotNull(cursor))) { "C_SCHEMA_VIOLATION" }
            if (cursor == null) break
        }
        return authority
    }

    private fun receipt(
        base: HttpUrl,
        chain: String,
        pin: JsonObject,
        entry: JsonObject,
    ): JsonObject {
        val seq = entry.getValue("relay_seq").jsonPrimitive.long
        val url =
            endpoint(base, chain, "receipts/$seq")
                .newBuilder()
                .addQueryParameter("log_id", pin.getValue("log_id").jsonPrimitive.content)
                .build()
        return Json.parseToJsonElement(PallaSyncCore.validateReceiptResponse(get(url, chain), entry.toString(), pin.toString())).jsonObject
    }

    private suspend fun retain(
        chain: String,
        pin: JsonObject,
        entry: JsonObject,
        receipt: JsonObject,
    ) = store.transaction {
        val sequence = entry.getValue("relay_seq").jsonPrimitive.long
        val log = pin.getValue("log_id").jsonPrimitive.content
        store.protocolDao().retainLogEntry(
            LogEntryEntity(chain, log, sequence, hash(entry), entry.toString(), receipt.toString(), "authority_verified"),
        )
        PallaSyncEnvelopeEvidence.retain(store, chain, entry)
        store.protocolDao().retainEvidence(
            ProtectedEvidenceEntity(
                pin.getValue("origin").jsonPrimitive.content,
                receipt.getValue("relay_key_id").jsonPrimitive.content,
                chain,
                log,
                "upload_receipt",
                sequence,
                hash(receipt),
                receipt.toString(),
            ),
        )
    }

    private suspend fun verifyReceipts(
        chain: String,
        before: JsonObject,
        after: JsonObject,
        anchor: JsonObject,
    ) {
        val pin = after.getValue("pin").jsonObject
        val log = pin.getValue("log_id").jsonPrimitive.content
        var sequence = before.getValue("sequence").jsonPrimitive.long
        val through = after.getValue("sequence").jsonPrimitive.long
        while (sequence < through) {
            val row = checkNotNull(store.protocolDao().logEntry(chain, log, ++sequence)) { "C_MISSING_DEPENDENCY" }
            val receipt = Json.parseToJsonElement(row.receiptJson).jsonObject
            val proof =
                store
                    .protocolDao()
                    .evidenceAt(
                        pin.getValue("origin").jsonPrimitive.content,
                        receipt.getValue("relay_key_id").jsonPrimitive.content,
                        chain,
                        log,
                        "transparency",
                        receipt.getValue("transparency_index").jsonPrimitive.long,
                    ).singleOrNull() ?: error("C_MISSING_DEPENDENCY")
            check(
                PallaSyncCore.validateReceiptInclusion(receipt.toString(), proof, pin.toString(), anchor.toString()),
            ) { "C_TRANSPARENCY_INVALID" }
        }
    }

    private fun endpoint(
        base: HttpUrl,
        chain: String,
        tail: String,
    ): HttpUrl =
        base
            .newBuilder()
            .encodedPath("/pallasync/v3/chains/$chain/$tail")
            .query(null)
            .build()

    private fun get(
        url: HttpUrl,
        chain: String,
    ): ByteArray =
        requireValue(
            remote.execute(
                Request
                    .Builder()
                    .url(url)
                    .header(
                        "Accept",
                        PALLASYNC_MEDIA_TYPE,
                    ).header(
                        "Authorization",
                        "PallaSync ${checkNotNull(token(url, chain, "GET", url.encodedPath, url.encodedQuery.orEmpty(), ""))}",
                    ).get()
                    .build(),
            ) { PallaSyncHttpResult.Success(it.pallaSyncBytes()) },
        )

    private fun hash(value: JsonObject): String = checkNotNull(PallaSyncCore.objectHash(value.toString())) { "C_SCHEMA_VIOLATION" }

    private fun <T> requireValue(result: PallaSyncHttpResult<T>): T =
        when (result) {
            is PallaSyncHttpResult.Success -> result.value
            is PallaSyncHttpResult.ProtocolError -> throw ReadFailure(result)
            is PallaSyncHttpResult.Retryable -> throw ReadFailure(result)
            PallaSyncHttpResult.Gone -> throw ReadFailure(PallaSyncHttpResult.Gone)
        }

    private class ReadFailure(
        val result: PallaSyncHttpResult<Nothing>,
    ) : RuntimeException()
}
