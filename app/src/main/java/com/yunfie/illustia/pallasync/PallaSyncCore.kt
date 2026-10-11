package com.yunfie.illustia.pallasync

import androidx.annotation.Keep

@Keep
@Suppress("TooManyFunctions")
object PallaSyncCore {
    init {
        System.loadLibrary("pallasync_core")
    }

    /** Generates a 24-word BIP39 English seed phrase. */
    external fun generateSeedPhrase(): String

    /**
     * Generates a fresh device keypair (Ed25519 signing key and X25519 KEX key).
     * Returns JSON: {"signing_key": "...", "public_key": "...", "kex_private_key": "...", "kex_public_key": "..."}
     */
    external fun generateDeviceKeys(): String

    /**
     * Derives root keys and deterministic chain ID from mnemonic and optional passphrase (§5.1, §5.2).
     * Returns JSON: {"chain_id": "...", "chain_salt": "...", "admin_public_key": "...", "admin_signing_key": "...", "recovery_kek": "..."}
     */
    external fun deriveRootKeys(
        seedPhrase: String,
        passphrase: String = "",
    ): String?

    /**
     * Derives epoch keys from an epoch secret (§5.3).
     * Returns JSON: {"record_key": "...", "device_meta_key": "...", "collection_tag_key": "...", "epoch_commitment": "..."}
     */
    external fun deriveEpochKeys(
        epochSecretBase64: String,
        chainId: String,
        epoch: Long,
    ): String?

    /**
     * Encrypts and frames an InnerRecord into a padded SyncRecord signed envelope (§7.1, §7.2, §7.3, §14.3).
     * Returns JSON representation of SyncRecord.
     */
    external fun createSyncRecord(
        chainId: String,
        generation: Int,
        recordId: String,
        deviceId: String,
        epoch: Int,
        collectionTag: String?,
        innerRecordJson: String,
        recordKeyBase64: String,
        signingKeyBase64: String,
    ): String?

    /** Verifies the SyncRecord Ed25519 signature (§7.1, §4.1). */
    external fun verifySyncRecord(
        recordJson: String,
        devicePublicKeyBase64: String,
    ): Boolean

    /**
     * Decrypts a SyncRecord and returns the InnerRecord JSON representation.
     */
    external fun decryptSyncRecord(
        recordJson: String,
        recordKeyBase64: String,
    ): String?

    /**
     * Creates a signed DeviceRecord with padded and encrypted metadata (§7.4).
     * Returns JSON representation of DeviceRecord.
     */
    external fun createDeviceRecord(
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
    ): String?

    /** Verifies a device record against its embedded public key. */
    external fun verifyDeviceRecord(recordJson: String): Boolean

    /** Decrypts encrypted_device_meta and returns DeviceMetaPlaintext JSON. */
    external fun decryptDeviceRecord(
        recordJson: String,
        deviceMetaKeyBase64: String,
    ): String?

    /**
     * Creates a Base64URL-encoded capability token for Authorization header (§6).
     */
    external fun createCapabilityToken(
        aud: String,
        chainId: String,
        signerKind: String,
        deviceId: String?,
        method: String,
        path: String,
        query: String,
        bodyJson: String,
        signingKeyBase64: String,
        ttlMs: Long,
    ): String?

    /**
     * Unwraps an HPKE device EpochKeyEnvelope using the device's X25519 private key (§7.7).
     * Returns raw 32-byte epoch_secret encoded in Base64URL.
     */
    external fun unwrapDeviceEpochEnvelope(
        envelopeJson: String,
        deviceKexPrivateKeyBase64: String,
        chainId: String,
    ): String?

    /**
     * Wraps an epoch secret into an HPKE device EpochKeyEnvelope (§7.7).
     * Returns JSON representation of EpochKeyEnvelope.
     */
    external fun wrapDeviceEpochEnvelope(
        envelopeId: String,
        chainId: String,
        generation: Int,
        epoch: Int,
        previousEpochHash: String?,
        epochCommitment: String,
        recipientDeviceId: String,
        recipientKexPublicKeyBase64: String,
        signerKind: String,
        signerDeviceId: String?,
        signingKeyBase64: String,
        epochSecretBase64: String,
    ): String?

    /**
     * Wraps an epoch secret into an XChaCha20-Poly1305 recovery EpochKeyEnvelope (§7.7).
     * Returns JSON representation of EpochKeyEnvelope.
     */
    external fun wrapRecoveryEpochEnvelope(
        envelopeId: String,
        chainId: String,
        generation: Int,
        epoch: Int,
        previousEpochHash: String?,
        epochCommitment: String,
        signerKind: String,
        signerDeviceId: String?,
        signingKeyBase64: String,
        recoveryKekBase64: String,
        epochSecretBase64: String,
    ): String?

    /**
     * Unwraps an XChaCha20-Poly1305 recovery EpochKeyEnvelope using recovery_kek (§7.7).
     * Returns raw 32-byte epoch_secret encoded in Base64URL.
     */
    external fun unwrapRecoveryEpochEnvelope(
        envelopeJson: String,
        recoveryKekBase64: String,
        chainId: String,
    ): String?

    /**
     * Creates a complete Genesis Bundle (§10.2).
     * Returns JSON with keys, parameters, device_record, epoch, envelopes, and admin_capability_token.
     */
    external fun createGenesisBundle(
        seedPhrase: String,
        passphrase: String = "",
        deviceName: String,
        keyProtection: String = "os-keystore",
        aud: String,
    ): String?

    /**
     * Creates a complete Mnemonic Enrollment Bundle (§10.4, §10.8).
     * Returns JSON with keys, enroll_request_body_json, admin_capability_token, keys_ack_request_body_json, device_keys_ack_token.
     */
    external fun createMnemonicEnrollmentBundle(
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
    ): String?
}
