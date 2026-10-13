package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.withTransaction
import com.yunfie.illustia.pallasync.data.PallaSyncDao
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal class PallaSyncLocalStore(
    context: Context,
    suppliedDatabase: PallaSyncDatabase? = null,
) {
    private val database by lazy { suppliedDatabase ?: PallaSyncDatabase.getDatabase(context.applicationContext) }

    val dao: PallaSyncDao
        get() = database.pallaSyncDao()

    fun pallaSyncDao(): PallaSyncDao = dao

    fun protocolDao() = database.protocolDao()

    suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }
}

@Suppress("TooManyFunctions")
internal class PallaSyncCryptoService(
    private val policy: PallaSyncPolicyGate,
) {
    private val tokenTtlMs = 120_000L

    fun objectHash(valueJson: String): String? = PallaSyncCore.objectHash(valueJson)

    fun generateSeedPhrase(): String = PallaSyncCore.generateSeedPhrase()

    fun verifySyncRecord(
        recordJson: String,
        devicePublicKeyBase64: String,
    ): Boolean = PallaSyncCore.verifySyncRecord(recordJson, devicePublicKeyBase64)

    fun verifyDeviceRecord(recordJson: String): Boolean = PallaSyncCore.verifyDeviceRecord(recordJson)

    fun createCapabilityToken(
        aud: String,
        chainId: String,
        signerKind: String,
        deviceId: String?,
        method: String,
        path: String,
        query: String = "",
        bodyJson: String = "",
        signingKeyBase64: String,
        ttlMs: Long = 120_000L,
    ): String? =
        run {
            policy.requireCurrent(aud)
            check(signerKind == "device" && deviceId != null) { "C_RECOVERY_AUTH_REQUIRED" }
            check(ttlMs == tokenTtlMs) { "C_SCHEMA_VIOLATION" }
            val binding =
                buildJsonObject {
                    put("aud", aud)
                    put("chain_id", chainId)
                    put("device_id", deviceId)
                    put("method", method)
                    put("path", path)
                    put("query", query)
                }.toString()
            PallaSyncDeviceSession.beginToken(signingKeyBase64).use {
                PallaSyncCore.deviceCapabilityToken(it.handle, binding, bodyJson.toByteArray(), policy.retry.correctedTime(aud))
            }
        }

    fun createGenesisBundle(
        rootSession: PallaSyncRootSession,
        deviceName: String,
        keyProtection: String = "os-keystore",
        aud: String,
    ): PallaSyncPreparedKeys =
        run {
            policy.requireCurrent(aud)
            PallaSyncCore.createGenesisBundle(
                rootSession.handle,
                deviceName,
                keyProtection,
                aud,
            )
        }

    fun createMnemonicEnrollmentBundle(
        rootSession: PallaSyncRootSession,
        deviceName: String,
        keyProtection: String = "os-keystore",
        generation: Long,
        epoch: Long,
        recoveryEnvelopeJson: String,
        expectedParametersHash: String,
        expectedEpochHash: String,
        aud: String,
    ): PallaSyncPreparedKeys =
        run {
            policy.requireCurrent(aud)
            PallaSyncCore.createMnemonicEnrollmentBundle(
                rootSession.handle,
                deviceName,
                keyProtection,
                generation,
                epoch,
                recoveryEnvelopeJson,
                expectedParametersHash,
                expectedEpochHash,
                aud,
            )
        }

    fun prepareEpoch(
        root: PallaSyncRootSession,
        authority: String,
        previous: PallaSyncKeySnapshot,
        reason: String,
        revoked: List<String>,
        admin: Boolean,
        aud: String,
    ): PallaSyncPreparedEpoch {
        policy.requireCurrent(aud)
        val input =
            buildJsonObject {
                put("reason", reason)
                put("signer_kind", if (admin) "admin" else "device")
                put("signer_device_id", if (admin) JsonNull else JsonPrimitive(checkNotNull(previous.deviceId)))
                put("revoked_device_ids", JsonArray(revoked.sorted().map(::JsonPrimitive)))
                put("created_at_ms", policy.retry.correctedTime(aud))
            }
        val seed =
            if (admin) {
                byteArrayOf()
            } else {
                java.util.Base64
                    .getUrlDecoder()
                    .decode(previous.signingKeyBase64Url)
            }
        return try {
            PallaSyncCore.prepareEpochBundle(root.handle, root.operation.wire, authority, input.toString(), seed)
        } finally {
            seed.fill(0)
        }
    }
}

internal class PallaSyncRecordProcessor(
    context: Context,
) {
    private val applier = PallaSyncEventApplier(context.applicationContext)

    suspend fun applyEvents(payloads: List<String>): List<PallaSyncApplyResult> = applier.applyEvents(payloads)
}
