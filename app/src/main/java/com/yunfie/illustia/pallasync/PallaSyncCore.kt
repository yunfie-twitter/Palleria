package com.yunfie.illustia.pallasync

import androidx.annotation.Keep

@Keep
object PallaSyncCore {
    init {
        System.loadLibrary("pallasync_core")
    }

    external fun generateSeedPhrase(): String

    // Returns JSON: {"chain_id": "...", "encryption_key": "...", "signing_key": "...", "public_key": "..."}
    external fun deriveKeys(seedPhrase: String): String?

    // Returns JSON representation of SyncRecord
    external fun createSyncRecord(
        chainId: String,
        recordId: String,
        collectionName: String,
        action: String,
        payloadJson: String,
        deviceId: String,
        encryptionKeyBase64: String,
        signingKeyBase64: String,
        lamport: Long = 0L,
    ): String?

    /** Verifies the unsigned-record JCS Ed25519 signature. */
    external fun verifySyncRecord(
        recordJson: String,
        devicePublicKeyBase64: String,
    ): Boolean

    // Returns decrypted payload string
    external fun decryptSyncRecord(
        recordJson: String,
        encryptionKeyBase64: String,
    ): String?

    // Returns JSON representation of DeviceRecord
    external fun createDeviceRecord(
        chainId: String,
        deviceId: String,
        deviceName: String,
        encryptionKeyBase64: String,
        signingKeyBase64: String,
    ): String?

    /** Verifies a device record against its embedded public key. */
    external fun verifyDeviceRecord(recordJson: String): Boolean

    // Decrypts encrypted_device_name
    external fun decryptDeviceRecord(
        encryptedDeviceName: String,
        deviceId: String,
        encryptionKeyBase64: String,
    ): String?

    // Creates Base64URL-encoded capability token for Authorization header
    external fun createCapabilityToken(
        chainId: String,
        deviceId: String,
        method: String,
        path: String,
        query: String,
        bodyJson: String,
        signingKeyBase64: String,
        ttlMs: Long,
    ): String?
}
