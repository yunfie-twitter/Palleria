package com.yunfie.illustia.pallasync

import android.content.Context
import com.yunfie.illustia.pallasync.data.PallaSyncDao
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase

internal class PallaSyncLocalStore(
    context: Context,
) {
    private val database by lazy { PallaSyncDatabase.getDatabase(context.applicationContext) }

    val dao: PallaSyncDao
        get() = database.pallaSyncDao()

    fun pallaSyncDao(): PallaSyncDao = dao
}

internal class PallaSyncCryptoService {
    fun generateSeedPhrase(): String = PallaSyncCore.generateSeedPhrase()

    fun deriveKeys(seedPhrase: String): String? = PallaSyncCore.deriveKeys(seedPhrase)

    fun createSyncRecord(
        chainId: String,
        recordId: String,
        collectionName: String,
        action: String,
        payloadJson: String,
        deviceId: String,
        encryptionKeyBase64: String,
        signingKeyBase64: String,
        lamport: Long = 0L,
    ): String? =
        PallaSyncCore.createSyncRecord(
            chainId,
            recordId,
            collectionName,
            action,
            payloadJson,
            deviceId,
            encryptionKeyBase64,
            signingKeyBase64,
            lamport,
        )

    fun verifySyncRecord(
        recordJson: String,
        devicePublicKeyBase64: String,
    ): Boolean = PallaSyncCore.verifySyncRecord(recordJson, devicePublicKeyBase64)

    fun decryptSyncRecord(
        recordJson: String,
        encryptionKeyBase64: String,
    ): String? = PallaSyncCore.decryptSyncRecord(recordJson, encryptionKeyBase64)

    fun createDeviceRecord(
        chainId: String,
        deviceId: String,
        deviceName: String,
        encryptionKeyBase64: String,
        signingKeyBase64: String,
    ): String? =
        PallaSyncCore.createDeviceRecord(
            chainId,
            deviceId,
            deviceName,
            encryptionKeyBase64,
            signingKeyBase64,
        )

    fun verifyDeviceRecord(recordJson: String): Boolean = PallaSyncCore.verifyDeviceRecord(recordJson)

    fun decryptDeviceRecord(
        encryptedDeviceName: String,
        deviceId: String,
        encryptionKeyBase64: String,
    ): String? = PallaSyncCore.decryptDeviceRecord(encryptedDeviceName, deviceId, encryptionKeyBase64)

    fun createCapabilityToken(
        chainId: String,
        deviceId: String,
        method: String,
        path: String,
        query: String = "",
        bodyJson: String = "",
        signingKeyBase64: String,
        ttlMs: Long = 300_000L,
    ): String? =
        PallaSyncCore.createCapabilityToken(
            chainId,
            deviceId,
            method,
            path,
            query,
            bodyJson,
            signingKeyBase64,
            ttlMs,
        )
}

internal class PallaSyncRecordProcessor(
    context: Context,
) {
    private val applier = PallaSyncEventApplier(context.applicationContext)

    suspend fun applyEvents(payloads: List<String>): List<PallaSyncApplyResult> = applier.applyEvents(payloads)
}
