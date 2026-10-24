package com.yunfie.illustia.pallasync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Restore locally missing proofs only through an already fully verified signed head (§17.9). */
internal class PallaSyncHistoricalTransparency(
    private val keys: PallaSyncKeystore,
    private val state: PallaSyncProbeState,
    private val transport: PallaSyncProbeTransport,
    private val afterPagePersisted: () -> Unit,
    private val afterPageCheckpoint: suspend () -> Unit,
) {
    suspend fun recover(
        context: PallaSyncProbeState.Context,
        targetIndex: Long,
    ): Boolean {
        require(targetIndex > 0) { "C_SCHEMA_VIOLATION" }
        val saved = keys.transparencyRecoveryProgress(context.chain)
        val head = saved?.get("target_head")?.jsonObject ?: keys.verifiedTransparencyHead(context.chain) ?: return false
        val headIndex = head.getValue("entry_index").jsonPrimitive.long
        val currentIndex =
            context.anchor
                .getValue("index")
                .jsonPrimitive.long
        return if (targetIndex > headIndex || headIndex > currentIndex) {
            false
        } else {
            requireKnownHead(context, head, currentIndex)
            val anchor = anchor(context, head, saved)
            if (anchor.getValue("index").jsonPrimitive.long == headIndex) {
                // Completed local readback needs no policy/network access.
                finish(context, head, anchor, targetIndex)
            } else {
                val range = PallaSyncTransparencyRange(head, anchor, state.pageLimit(context))
                if (replay(context, range, saved == null)) finish(context, head, range.anchor, targetIndex) else false
            }
        }
    }

    private fun requireKnownHead(
        context: PallaSyncProbeState.Context,
        head: JsonObject,
        currentIndex: Long,
    ) {
        check(
            head.getValue("transparency_id") == context.anchor.getValue("transparency_id") &&
                (
                    head.getValue("entry_index").jsonPrimitive.long != currentIndex ||
                        head.getValue("transparency_hash") == context.anchor.getValue("hash")
                ),
        ) { "C_TRANSPARENCY_INVALID" }
    }

    private fun anchor(
        context: PallaSyncProbeState.Context,
        head: JsonObject,
        saved: JsonObject?,
    ): JsonObject {
        val anchor =
            saved?.get("anchor")?.jsonObject ?: buildJsonObject {
                put("transparency_id", head.getValue("transparency_id"))
                put("index", 0)
                put(
                    "hash",
                    pallaSyncTransparencyZero(
                        context.chain,
                        head.getValue("transparency_id").jsonPrimitive.content,
                        head.getValue("relay_key_id").jsonPrimitive.content,
                    ),
                )
            }
        check(
            anchor.getValue("transparency_id") == head.getValue("transparency_id") &&
                anchor.getValue("index").jsonPrimitive.long <= head.getValue("entry_index").jsonPrimitive.long,
        ) { "C_TRANSPARENCY_INVALID" }
        return anchor
    }

    private suspend fun finish(
        context: PallaSyncProbeState.Context,
        head: JsonObject,
        anchor: JsonObject,
        targetIndex: Long,
    ): Boolean {
        check(
            anchor.getValue("index").jsonPrimitive.long == head.getValue("entry_index").jsonPrimitive.long &&
                anchor.getValue("hash") == head.getValue("transparency_hash"),
        ) { "C_TRANSPARENCY_INVALID" }
        val found = state.contains(context, head, targetIndex)
        check(keys.clearTransparencyRecoveryProgress(context.chain, head)) { "C_LOCAL_STORAGE_FULL" }
        return found
    }

    private suspend fun replay(
        context: PallaSyncProbeState.Context,
        range: PallaSyncTransparencyRange,
        start: Boolean,
    ): Boolean =
        try {
            if (start) checkpoint(context, range)
            while (range.hasRemaining) {
                currentCoroutineContext().ensureActive()
                val checked = transport.page(context, range)
                val response = checked.getValue("response").jsonObject
                val next = checked.getValue("next_anchor").jsonObject
                state.persistPage(context, response, next)
                range.advance(response, next)
                afterPagePersisted()
                checkpoint(context, range)
                afterPageCheckpoint()
            }
            true
        } catch (_: PallaSyncProbeTransport.Failure) {
            // Missing/temporarily unavailable evidence leaves the exact durable range pending.
            false
        }

    private fun checkpoint(
        context: PallaSyncProbeState.Context,
        range: PallaSyncTransparencyRange,
    ) {
        val progress =
            buildJsonObject {
                put("target_head", range.head)
                put("anchor", range.anchor)
            }
        check(keys.retainTransparencyRecoveryProgress(context.chain, progress)) { "C_LOCAL_STORAGE_FULL" }
    }
}
