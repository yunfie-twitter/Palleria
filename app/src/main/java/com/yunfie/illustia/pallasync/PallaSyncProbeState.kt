package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.ProtectedEvidenceEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.HttpUrl

/** Public evidence/checkpoint persistence. Room always commits before a protected anchor (§17.9). */
internal class PallaSyncProbeState(
    private val store: PallaSyncLocalStore,
    private val keys: PallaSyncKeystore,
    private val policy: PallaSyncPolicyGate,
) {
    data class Context(
        val base: HttpUrl,
        val state: ChainStateEntity,
        val origin: String,
        val pin: String,
        val checkpoint: String,
        val anchor: JsonObject,
        val migrationDestination: Boolean,
    ) {
        val chain: String get() = state.chainId
        val logId: String get() = checkNotNull(state.logId)
    }

    suspend fun context(
        base: HttpUrl,
        chain: String,
        migrationDestination: Boolean,
    ): Context {
        val state = checkNotNull(store.dao.getChainState(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        checkNotNull(state.logId) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val authority = checkNotNull(store.protocolDao().currentAuthority(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(PallaSyncCore.objectHash(authority) == keys.authorityHash(chain)) { "C_ROLLBACK_DETECTED" }
        val anchor = checkNotNull(keys.transparencyPin(chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val index = anchor.getValue("index").jsonPrimitive.long
        check(state.transparencyIndex >= index) { "C_ROLLBACK_DETECTED" }
        if (state.transparencyIndex == index) {
            check(state.transparencyHash == anchor.getValue("hash").jsonPrimitive.content) { "C_TRANSPARENCY_INVALID" }
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
        return Context(base, state, origin, pin, checkpoint, anchor, migrationDestination)
    }

    fun pageLimit(context: Context): Int = minOf(PALLASYNC_PAGE_SIZE, policy.pageLimit(context.origin))

    suspend fun contains(
        context: Context,
        head: JsonObject,
        index: Long,
    ): Boolean =
        store
            .protocolDao()
            .evidenceAt(
                context.origin,
                head.getValue("relay_key_id").jsonPrimitive.content,
                context.chain,
                context.logId,
                "transparency",
                index,
            ).isNotEmpty()

    suspend fun retain(
        context: Context,
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

    suspend fun persistPage(
        context: Context,
        page: JsonObject,
        next: JsonObject,
    ) {
        store.transaction {
            val current = checkNotNull(store.dao.getChainState(context.chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            val index = next.getValue("index").jsonPrimitive.long
            val hash = next.getValue("hash").jsonPrimitive.content
            check(current.logId == context.state.logId && current.transparencyId == context.state.transparencyId) { "C_ROLLBACK_DETECTED" }
            if (index == current.transparencyIndex) check(hash == current.transparencyHash) { "C_TRANSPARENCY_INVALID" }
            // Room can be ahead of the protected pin after a crash. Replayed pages must match its boundary.
            val items = page.getValue("items").jsonArray
            val boundary =
                items.firstOrNull {
                    it.jsonObject
                        .getValue("entry_index")
                        .jsonPrimitive.long == current.transparencyIndex + 1
                }
            if (boundary != null) {
                check(
                    boundary.jsonObject
                        .getValue("previous_transparency_hash")
                        .jsonPrimitive.content == current.transparencyHash,
                ) {
                    "C_TRANSPARENCY_INVALID"
                }
            }
            items.forEach { value ->
                val entry = value.jsonObject
                retain(context, entry, "transparency", entry.getValue("entry_index").jsonPrimitive.long)
            }
            if (index > current.transparencyIndex) {
                store.dao.updateChainState(current.copy(transparencyIndex = index, transparencyHash = hash))
            }
        }
    }

    fun publishAnchor(
        context: Context,
        next: JsonObject,
    ) {
        val saved = checkNotNull(keys.transparencyPin(context.chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        val nextIndex = next.getValue("index").jsonPrimitive.long
        val savedIndex = saved.getValue("index").jsonPrimitive.long
        if (nextIndex > savedIndex) {
            check(keys.publishTransparencyPin(context.chain, next)) { "C_LOCAL_STORAGE_FULL" }
        } else if (nextIndex == savedIndex) {
            check(next == saved) { "C_TRANSPARENCY_INVALID" }
        }
    }

    fun sealHead(
        context: Context,
        head: JsonObject,
    ) {
        val pin = checkNotNull(keys.transparencyPin(context.chain)) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        check(
            pin.getValue("index").jsonPrimitive.long == head.getValue("entry_index").jsonPrimitive.long &&
                pin.getValue("hash").jsonPrimitive.content == head.getValue("transparency_hash").jsonPrimitive.content,
        ) { "C_TRANSPARENCY_INVALID" }
        check(keys.retainVerifiedTransparencyHead(context.chain, head)) { "C_LOCAL_STORAGE_FULL" }
    }
}
