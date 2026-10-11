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

@Suppress("TooManyFunctions")
internal class PallaSyncCryptoService {
    fun generateSeedPhrase(): String = PallaSyncCore.generateSeedPhrase()

    fun generateDeviceKeys(): String = PallaSyncCore.generateDeviceKeys()

    fun deriveRootKeys(
        seedPhrase: String,
        passphrase: String = "",
    ): String? = PallaSyncCore.deriveRootKeys(seedPhrase, passphrase)

    fun deriveEpochKeys(
        epochSecretBase64: String,
        chainId: String,
        epoch: Long,
    ): String? = PallaSyncCore.deriveEpochKeys(epochSecretBase64, chainId, epoch)

    fun createSyncRecord(
        chainId: String,
        generation: Int,
        recordId: String,
        deviceId: String,
        epoch: Int,
        collectionTag: String?,
        innerRecordJson: String,
        recordKeyBase64: String,
        signingKeyBase64: String,
    ): String? =
        PallaSyncCore.createSyncRecord(
            chainId,
            generation,
            recordId,
            deviceId,
            epoch,
            collectionTag,
            innerRecordJson,
            recordKeyBase64,
            signingKeyBase64,
        )

    fun verifySyncRecord(
        recordJson: String,
        devicePublicKeyBase64: String,
    ): Boolean = PallaSyncCore.verifySyncRecord(recordJson, devicePublicKeyBase64)

    fun decryptSyncRecord(
        recordJson: String,
        recordKeyBase64: String,
    ): String? = PallaSyncCore.decryptSyncRecord(recordJson, recordKeyBase64)

    fun createDeviceRecord(
        chainId: String,
        generation: Int,
        deviceId: String,
        deviceSigningKeyBase64: String,
        deviceKexPublicKeyBase64: String,
        deviceName: String,
        keyProtection: String,
        metaEpoch: Int,
        deviceMetaKeyBase64: String,
        enrollmentCertificateJson: String,
    ): String? =
        PallaSyncCore.createDeviceRecord(
            chainId,
            generation,
            deviceId,
            deviceSigningKeyBase64,
            deviceKexPublicKeyBase64,
            deviceName,
            keyProtection,
            metaEpoch,
            deviceMetaKeyBase64,
            enrollmentCertificateJson,
        )

    fun verifyDeviceRecord(recordJson: String): Boolean = PallaSyncCore.verifyDeviceRecord(recordJson)

    fun decryptDeviceRecord(
        recordJson: String,
        deviceMetaKeyBase64: String,
    ): String? = PallaSyncCore.decryptDeviceRecord(recordJson, deviceMetaKeyBase64)

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
        PallaSyncCore.createCapabilityToken(
            aud,
            chainId,
            signerKind,
            deviceId,
            method,
            path,
            query,
            bodyJson,
            signingKeyBase64,
            ttlMs,
        )

    fun createGenesisBundle(
        seedPhrase: String,
        passphrase: String = "",
        deviceName: String,
        keyProtection: String = "os-keystore",
        aud: String,
    ): String? =
        PallaSyncCore.createGenesisBundle(
            seedPhrase,
            passphrase,
            deviceName,
            keyProtection,
            aud,
        )

    fun createMnemonicEnrollmentBundle(
        seedPhrase: String,
        passphrase: String = "",
        deviceName: String,
        keyProtection: String = "os-keystore",
        generation: Int,
        epoch: Int,
        recoveryEnvelopeJson: String,
        expectedParametersHash: String,
        expectedEpochHash: String,
        aud: String,
    ): String? =
        PallaSyncCore.createMnemonicEnrollmentBundle(
            seedPhrase,
            passphrase,
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

internal class PallaSyncRecordProcessor(
    context: Context,
) {
    private val applier = PallaSyncEventApplier(context.applicationContext)

    suspend fun applyEvents(payloads: List<String>): List<PallaSyncApplyResult> = applier.applyEvents(payloads)
}
