package com.yunfie.illustia.pallasync

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl

/** §§11.5/17.9–17.10. Verify the signed view before promoting its bounded suffix. */
internal class PallaSyncConsistencyProbe(
    private val store: PallaSyncLocalStore,
    remote: PallaSyncRemoteService,
    policy: PallaSyncPolicyGate,
    private val keys: PallaSyncKeystore,
    token: (HttpUrl, String, String, String, String, String) -> String?,
    resolvePruneCompaction: suspend (chainId: String, entry: JsonObject) -> JsonObject?,
    private val onSuccessfulComparison: (chainId: String) -> Unit = {},
    afterHistoricalPagePersisted: () -> Unit = {},
    afterHistoricalPageCheckpoint: suspend () -> Unit = {},
    afterProbePageCheckpoint: suspend () -> Unit = {},
) {
    private val state = PallaSyncProbeState(store, keys, policy)
    private val transport = PallaSyncProbeTransport(remote, token)
    private val historical =
        PallaSyncHistoricalTransparency(keys, state, transport, afterHistoricalPagePersisted, afterHistoricalPageCheckpoint)
    private val suffix = PallaSyncTransparencySuffix(keys, state, transport, resolvePruneCompaction, afterProbePageCheckpoint)

    suspend fun run(
        base: HttpUrl,
        chainId: String,
        migrationDestination: Boolean = false,
    ): PallaSyncHttpResult<Unit> {
        val result =
            try {
                compare(state.context(base, chainId, migrationDestination))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: PallaSyncProbeTransport.Failure) {
                failure.result
            } catch (error: IllegalStateException) {
                PallaSyncHttpResult.ProtocolError(error.message?.takeIf { it.startsWith("C_") } ?: "C_SCHEMA_VIOLATION")
            }
        if (result is PallaSyncHttpResult.ProtocolError && result.message in CONTRADICTIONS) store.dao.pauseIntegrityWrites(chainId)
        if (result is PallaSyncHttpResult.Success) onSuccessfulComparison(chainId)
        return result
    }

    suspend fun recoverHistoricalEvidence(
        base: HttpUrl,
        chainId: String,
        targetIndex: Long,
    ): Boolean {
        require(targetIndex > 0) { "C_SCHEMA_VIOLATION" }
        return historical.recover(state.context(base, chainId, migrationDestination = false), targetIndex)
    }

    private suspend fun compare(context: PallaSyncProbeState.Context): PallaSyncHttpResult<Unit> {
        val log = transport.readLogHead(context).requireProbeValue()
        val target = validatedTarget(context, log)
        val trustedHead = keys.verifiedTransparencyHead(context.chain)
        val minimum =
            target.head
                .getValue("min_available_seq")
                .jsonPrimitive.long
        val previousMinimum = trustedHead?.get("min_available_seq")?.jsonPrimitive?.long ?: 1L
        requirePruningHistory(trustedHead, target.head, previousMinimum, minimum)
        suffix.verify(context, target.head, trustedHead, previousMinimum, minimum)
        state.sealHead(context, target.head)
        return PallaSyncHttpResult.Success(Unit)
    }

    private suspend fun validatedTarget(
        context: PallaSyncProbeState.Context,
        log: JsonObject,
    ): PallaSyncProbeTransport.ObservedHead {
        val response = transport.observeHead(context)
        // Retain the authenticated observation before the full signature/fork comparison can fail.
        state.retain(
            context,
            response.head,
            "transparency_head",
            response.head
                .getValue("entry_index")
                .jsonPrimitive.long,
        )
        PallaSyncCore.validateTransparencyHead(response.bytes, context.pin, context.checkpoint)
        requireMatchingView(context, log, response.head)
        return pendingTarget(context) ?: response.also {
            state.retain(
                context,
                it.head,
                "transparency_target",
                it.head
                    .getValue("entry_index")
                    .jsonPrimitive.long,
            )
        }
    }

    /** A validated target survives cancellation; it is not replaced partway through its suffix. */
    private suspend fun pendingTarget(context: PallaSyncProbeState.Context): PallaSyncProbeTransport.ObservedHead? {
        val saved = store.protocolDao().latestEvidence(context.chain, context.logId, "transparency_target")
        val head = saved?.let { Json.parseToJsonElement(it).jsonObject } ?: return null
        val targetIndex = head.getValue("entry_index").jsonPrimitive.long
        val completeIndex =
            keys
                .verifiedTransparencyHead(context.chain)
                ?.get("entry_index")
                ?.jsonPrimitive
                ?.long ?: 0L
        return if (!PallaSyncTransparencyProgress.shouldResumeSavedTarget(targetIndex, completeIndex)) {
            null
        } else {
            check(
                head
                    .getValue(
                        "transparency_id",
                    ).jsonPrimitive.content ==
                    context.anchor
                        .getValue("transparency_id")
                        .jsonPrimitive.content &&
                    head.getValue("log_id").jsonPrimitive.content == context.logId,
            ) { "C_TRANSPARENCY_INVALID" }
            // Evidence stores the signed object; the strict §10.17 response validator expects its wrapper.
            val bytes = buildJsonObject { put("head", head) }.toString().toByteArray(Charsets.UTF_8)
            PallaSyncCore.validateTransparencyHead(bytes, context.pin, context.checkpoint)
            PallaSyncProbeTransport.ObservedHead(bytes, head)
        }
    }

    private fun requireMatchingView(
        context: PallaSyncProbeState.Context,
        log: JsonObject,
        head: JsonObject,
    ) {
        if (!sameView(context, log, head)) {
            // Repeat once to distinguish concurrent acceptance from a contradictory signed view.
            check(sameView(context, transport.readLogHead(context).requireProbeValue(), head)) { "C_TRANSPARENCY_INVALID" }
        }
    }

    private fun sameView(
        context: PallaSyncProbeState.Context,
        log: JsonObject,
        head: JsonObject,
    ): Boolean = PallaSyncCore.compareLogHeads(log.toString(), head.toString(), context.pin, context.state.generation)

    private fun requirePruningHistory(
        trusted: JsonObject?,
        target: JsonObject,
        previousMinimum: Long,
        minimum: Long,
    ) {
        check(minimum >= previousMinimum) { "C_ROLLBACK_DETECTED" }
        if (minimum > previousMinimum) {
            checkNotNull(trusted) { "C_MISSING_DEPENDENCY" }
            check(
                trusted.getValue("log_id") == target.getValue("log_id") &&
                    trusted.getValue("transparency_id") == target.getValue("transparency_id"),
            ) {
                "C_TRANSPARENCY_INVALID"
            }
        }
    }

    private companion object {
        val CONTRADICTIONS = setOf("C_ROLLBACK_DETECTED", "C_LOG_HASH_MISMATCH", "C_RELAY_KEY_MISMATCH", "C_TRANSPARENCY_INVALID")
    }
}
