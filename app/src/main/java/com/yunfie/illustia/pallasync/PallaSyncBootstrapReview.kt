package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** §11.6: authenticated source details shown before a first-history checkpoint is installed. */
internal data class PallaSyncBootstrapReview(
    val relayOrigin: String,
    val chainId: String,
    val adminPublicKey: String,
    val logId: String,
    val generation: Long,
    val headSeq: Long,
    val headHash: String,
    val parametersHash: String,
    val epochHash: String,
    val transparencyId: String,
    val transparencyIndex: Long,
    val transparencyHash: String,
    val dataZero: String?,
) {
    companion object {
        fun fromVerifiedCheckpoint(
            relayOrigin: String,
            identity: PallaSyncRootSession.Identity,
            checkpoint: PallaSyncGenesisCheckpoint,
        ): PallaSyncBootstrapReview {
            val authority = checkpoint.authority
            val pin = authority.getValue("pin").jsonObject
            check(
                pin.getValue("origin").jsonPrimitive.content == relayOrigin &&
                    pin.getValue("log_id").jsonPrimitive.content == checkpoint.logId &&
                    pin.getValue("chain_id").jsonPrimitive.content == identity.chainId &&
                    authority.getValue("admin_public_key").jsonPrimitive.content == identity.adminPublicKey &&
                    pallaSyncAuthorityGeneration(authority) == checkpoint.generation &&
                    authority.getValue("sequence").jsonPrimitive.long == checkpoint.headSeq &&
                    authority.getValue("head_hash").jsonPrimitive.content == checkpoint.headHash,
            ) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            return PallaSyncBootstrapReview(
                relayOrigin = relayOrigin,
                chainId = identity.chainId,
                adminPublicKey = identity.adminPublicKey,
                logId = checkpoint.logId,
                generation = checkpoint.generation,
                headSeq = checkpoint.headSeq,
                headHash = checkpoint.headHash,
                parametersHash = checkpoint.parametersHash,
                epochHash = checkpoint.epochHash,
                transparencyId = checkpoint.transparencyId,
                transparencyIndex = checkpoint.transparencyIndex,
                transparencyHash = checkpoint.transparencyHash,
                dataZero = checkpoint.dataZero,
            )
        }
    }
}
