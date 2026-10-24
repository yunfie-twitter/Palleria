package com.yunfie.illustia.pallasync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Verify and checkpoint a normal suffix, including authorized pruning transitions (§§17.9–17.10). */
internal class PallaSyncTransparencySuffix(
    private val keys: PallaSyncKeystore,
    private val state: PallaSyncProbeState,
    private val transport: PallaSyncProbeTransport,
    private val resolvePruneCompaction: suspend (chainId: String, entry: JsonObject) -> JsonObject?,
    private val afterPageCheckpoint: suspend () -> Unit,
) {
    suspend fun verify(
        context: PallaSyncProbeState.Context,
        head: JsonObject,
        trustedHead: JsonObject?,
        previousMinimum: Long,
        expectedMinimum: Long,
    ) {
        val saved = keys.transparencyProgress(context.chain)?.takeIf { it.getValue("target_head") == head }
        val range = PallaSyncTransparencyRange(head, anchor(context, trustedHead, saved), state.pageLimit(context))
        val progress = Progress(saved, trustedHead, previousMinimum, expectedMinimum)
        while (range.hasRemaining) {
            currentCoroutineContext().ensureActive()
            val checked = transport.page(context, range)
            val response = checked.getValue("response").jsonObject
            val next = checked.getValue("next_anchor").jsonObject
            progress.accept(context, response, resolvePruneCompaction)
            state.persistPage(context, response, next)
            state.publishAnchor(context, next)
            range.advance(response, next)
            check(keys.retainTransparencyProgress(context.chain, progress.checkpoint(range))) { "C_LOCAL_STORAGE_FULL" }
            afterPageCheckpoint()
        }
        range.requireComplete()
        progress.requireComplete(range)
    }

    private fun anchor(
        context: PallaSyncProbeState.Context,
        trusted: JsonObject?,
        saved: JsonObject?,
    ): JsonObject =
        saved?.get("anchor")?.jsonObject ?: trusted?.let {
            buildJsonObject {
                put("transparency_id", it.getValue("transparency_id"))
                put("index", it.getValue("entry_index").jsonPrimitive.long)
                put("hash", it.getValue("transparency_hash"))
            }
        } ?: context.anchor

    private class Progress(
        saved: JsonObject?,
        trusted: JsonObject?,
        private val previousMinimum: Long,
        private val expectedMinimum: Long,
    ) {
        private var dataHeadHash: String? =
            if (saved == null) {
                trusted?.get("head_hash")?.jsonPrimitive?.content
            } else {
                saved["observed_data_head_hash"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
            }
        private var minimum = saved?.get("observed_minimum")?.jsonPrimitive?.long ?: previousMinimum
        private val minimumAlreadyAdvanced = minimum > previousMinimum
        private var matchedPruneEntry = false

        suspend fun accept(
            context: PallaSyncProbeState.Context,
            page: JsonObject,
            resolve: suspend (String, JsonObject) -> JsonObject?,
        ) {
            page.getValue("items").jsonArray.forEach { value ->
                val entry = value.jsonObject
                dataHeadHash = PallaSyncPruneTransparencyEvidence.advanceDataHead(entry, dataHeadHash)
                if (entry.getValue("operation").jsonPrimitive.content == "prune") {
                    val compaction = checkNotNull(resolve(context.chain, entry)) { "C_MISSING_DEPENDENCY" }
                    minimum =
                        PallaSyncPruneTransparencyEvidence.advanceMinimum(
                            entry,
                            compaction,
                            context.chain,
                            context.logId,
                            checkNotNull(dataHeadHash) { "C_MISSING_DEPENDENCY" },
                            minimum,
                        )
                    matchedPruneEntry = true
                }
            }
        }

        fun checkpoint(range: PallaSyncTransparencyRange): JsonObject =
            buildJsonObject {
                put("target_head", range.head)
                put("anchor", range.anchor)
                put("observed_data_head_hash", dataHeadHash)
                put("observed_minimum", minimum)
            }

        fun requireComplete(range: PallaSyncTransparencyRange) {
            check(minimum == expectedMinimum) { "C_TRANSPARENCY_INVALID" }
            check(((minimumAlreadyAdvanced || matchedPruneEntry) == (expectedMinimum > previousMinimum))) { "C_TRANSPARENCY_INVALID" }
            dataHeadHash?.let {
                check(
                    it ==
                        range.head
                            .getValue("head_hash")
                            .jsonPrimitive.content,
                ) { "C_TRANSPARENCY_INVALID" }
            }
        }
    }
}
