package com.yunfie.illustia.pallasync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** §5.5. A foreground operation owns the native input; no private root is a JVM value. */
internal class PallaSyncRootSession private constructor(
    val handle: Long,
    val operation: Operation,
    private val requestGuard: ((String) -> Unit)?,
    private val clock: (String) -> Long,
) : AutoCloseable {
    enum class Operation(
        val wire: String,
    ) {
        GENESIS("genesis"),
        ENROLLMENT("enrollment"),
        ROTATE("rotate"),
        REVOKE("revoke"),
        RESEED("reseed"),
        DELETE("delete"),
        PRUNE("prune"),
        RECOVERY_CREATE("recovery_create"),
        RECOVERY_ROTATE("recovery_rotate"),
        RECOVERY_DISABLE("recovery_disable"),
        NEW_ROOT_MIGRATION("new_root_migration"),
    }

    @Serializable
    data class Identity(
        @SerialName("chain_id") val chainId: String,
        @SerialName("admin_public_key") val adminPublicKey: String,
    )

    val identity: Identity get() = Json.decodeFromString(PallaSyncCore.rootIdentity(handle))

    fun token(
        aud: String,
        method: String,
        path: String,
        query: String = "",
        body: ByteArray = byteArrayOf(),
    ): String? =
        run {
            checkNotNull(requestGuard) { "C_RELAY_POLICY_CONSENT_REQUIRED" }(aud)
            PallaSyncCore.rootCapabilityToken(handle, operation.wire, aud, method, path, query, body, clock(aud))
        }

    fun userInteraction() = PallaSyncCore.touchRootSession(handle)

    /** The admin signature binds every exact object committed by §17.5 PUT /parameters. */
    fun createReseedAdminOperation(
        currentParametersJson: String,
        currentEpochJson: String,
        parametersJson: String,
        epochJson: String,
        envelopesJson: String,
        compactionJson: String,
        deviceRecordsJson: String,
        createdAtMs: Long,
    ): String {
        check(operation == Operation.RESEED) { "C_RECOVERY_AUTH_REQUIRED" }
        return PallaSyncCore.createReseedAdminOperation(
            handle,
            currentParametersJson,
            currentEpochJson,
            parametersJson,
            epochJson,
            envelopesJson,
            compactionJson,
            deviceRecordsJson,
            createdAtMs,
        )
    }

    override fun close() = PallaSyncCore.endRootSession(handle)

    companion object {
        internal fun adopt(
            handle: Long,
            operation: Operation,
            requestGuard: ((String) -> Unit)?,
            clock: (String) -> Long,
        ): PallaSyncRootSession = PallaSyncRootSession(handle, operation, requestGuard, clock)

        /** §3/§5.5: reject malformed UTF-16 instead of silently replacing a root input during UTF-8 encoding. */
        fun beginText(
            mnemonic: String,
            passphrase: String,
            operation: Operation,
            expectedChain: String? = null,
            expectedAdmin: String? = null,
            requestGuard: ((String) -> Unit)? = null,
            clock: (String) -> Long = { System.currentTimeMillis() },
        ): PallaSyncRootSession {
            val phrase = strictRootUtf8(mnemonic)
            return try {
                begin(phrase, strictRootUtf8(passphrase), operation, expectedChain, expectedAdmin, requestGuard, clock)
            } finally {
                phrase.fill(0)
            }
        }

        /** Takes ownership of mutable input buffers and overwrites them on every exit. */
        fun begin(
            mnemonic: ByteArray,
            passphrase: ByteArray,
            operation: Operation,
            expectedChain: String? = null,
            expectedAdmin: String? = null,
            requestGuard: ((String) -> Unit)? = null,
            clock: (String) -> Long = { System.currentTimeMillis() },
        ): PallaSyncRootSession =
            try {
                val handle = PallaSyncCore.beginRootSession(mnemonic, passphrase, operation.wire, expectedChain, expectedAdmin)
                check(handle > 0) { "C_RECOVERY_AUTH_REQUIRED" }
                PallaSyncRootSession(handle, operation, requestGuard, clock)
            } finally {
                mnemonic.fill(0)
                passphrase.fill(0)
            }
    }
}

internal fun strictRootUtf8(value: String): ByteArray {
    var index = 0
    while (index < value.length) {
        val character = value[index++]
        when {
            character.isHighSurrogate() -> {
                check(index < value.length && value[index].isLowSurrogate()) { "C_RECOVERY_PARAMETER_INVALID" }
                index++
            }

            character.isLowSurrogate() -> {
                error("C_RECOVERY_PARAMETER_INVALID")
            }
        }
    }
    return value.toByteArray(Charsets.UTF_8)
}
