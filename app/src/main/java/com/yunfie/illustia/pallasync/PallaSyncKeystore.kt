package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Snapshot of local device keys and active epoch keys.
 * Note: Per PallaSync Protocol 3.0 §5.5 (C-02), the root mnemonic, admin key,
 * and root_seed MUST NOT be persisted to non-volatile storage.
 */
data class PallaSyncKeySnapshot(
    val chainId: String? = null,
    val seedPhrase: String = "", // Memory-only; never persisted to disk
    val encryptionKeyBase64Url: String, // record_key
    val signingKeyBase64Url: String, // device_signing_key (Ed25519)
    val publicKeyBase64Url: String, // device_public_key (Ed25519)
    val kexPrivateKeyBase64Url: String = "", // device_kex_key (X25519)
    val kexPublicKeyBase64Url: String = "", // device_kex_public_key (X25519)
    val deviceMetaKeyBase64Url: String = "", // device_meta_key
)

class PallaSyncKeystore(
    context: Context,
) {
    private val masterKey =
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private val sharedPrefs =
        EncryptedSharedPreferences.create(
            context,
            "pallasync_keystore",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun saveDevicePrivateKey(seedBase64Url: String) {
        sharedPrefs.edit().putString("device_private_seed", seedBase64Url).apply()
    }

    fun getDevicePrivateKey(): String? = sharedPrefs.getString("device_private_seed", null)

    fun saveDeviceSignPublicKey(keyBase64Url: String) {
        sharedPrefs.edit().putString("device_sign_public", keyBase64Url).apply()
    }

    fun getDeviceSignPublicKey(): String? = sharedPrefs.getString("device_sign_public", null)

    fun saveDeviceHpkePrivateKey(keyBase64Url: String) {
        sharedPrefs.edit().putString("device_hpke_private", keyBase64Url).apply()
    }

    fun getDeviceHpkePrivateKey(): String? = sharedPrefs.getString("device_hpke_private", null)

    fun saveDeviceHpkePublicKey(keyBase64Url: String) {
        sharedPrefs.edit().putString("device_hpke_public", keyBase64Url).apply()
    }

    fun getDeviceHpkePublicKey(): String? = sharedPrefs.getString("device_hpke_public", null)

    fun saveDeviceCertificate(certificateJson: String) {
        sharedPrefs.edit().putString("device_certificate", certificateJson).apply()
    }

    fun getDeviceCertificate(): String? = sharedPrefs.getString("device_certificate", null)

    fun saveDeviceId(deviceId: String) {
        sharedPrefs.edit().putString("device_id", deviceId).apply()
    }

    /** Returns or generates a canonical UUIDv4 device ID (§7.4, §3.4). */
    fun getDeviceId(): String {
        var id = sharedPrefs.getString("device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString().lowercase()
            saveDeviceId(id)
        }
        return id
    }

    fun saveEpochKey(epochKeyBase64Url: String) {
        sharedPrefs.edit().putString("epoch_key", epochKeyBase64Url).apply()
    }

    fun getEpochKey(): String? = sharedPrefs.getString("epoch_key", null)

    fun saveDeviceMetaKey(deviceMetaKeyBase64Url: String) {
        sharedPrefs.edit().putString("device_meta_key", deviceMetaKeyBase64Url).apply()
    }

    fun getDeviceMetaKey(): String? = sharedPrefs.getString("device_meta_key", null)

    /**
     * Commits all active-chain key material in one encrypted preference edit.
     * Mnemonic is never persisted per §5.5.
     */
    fun saveActiveChainKeys(snapshot: PallaSyncKeySnapshot): Boolean {
        require(snapshot.encryptionKeyBase64Url.isNotBlank()) { "encryption key is blank" }
        require(snapshot.signingKeyBase64Url.isNotBlank()) { "signing key is blank" }
        require(snapshot.publicKeyBase64Url.isNotBlank()) { "public key is blank" }

        val editor =
            sharedPrefs
                .edit()
                .remove("seed_phrase") // Explicitly purge any legacy stored mnemonic
                .putString("epoch_key", snapshot.encryptionKeyBase64Url)
                .putString("device_private_seed", snapshot.signingKeyBase64Url)
                .putString("device_sign_public", snapshot.publicKeyBase64Url)
        if (snapshot.kexPrivateKeyBase64Url.isNotBlank()) {
            editor.putString("device_hpke_private", snapshot.kexPrivateKeyBase64Url)
        }
        if (snapshot.kexPublicKeyBase64Url.isNotBlank()) {
            editor.putString("device_hpke_public", snapshot.kexPublicKeyBase64Url)
        }
        if (snapshot.deviceMetaKeyBase64Url.isNotBlank()) {
            editor.putString("device_meta_key", snapshot.deviceMetaKeyBase64Url)
        }
        if (snapshot.chainId.isNullOrBlank()) {
            editor.remove("active_chain_id")
        } else {
            editor.putString("active_chain_id", snapshot.chainId)
        }
        return editor.commit()
    }

    fun getActiveChainKeys(): PallaSyncKeySnapshot? {
        val encryptionKey = getEpochKey()?.takeIf { it.isNotBlank() } ?: return null
        val signingKey = getDevicePrivateKey()?.takeIf { it.isNotBlank() } ?: return null
        val publicKey = getDeviceSignPublicKey()?.takeIf { it.isNotBlank() } ?: return null
        return PallaSyncKeySnapshot(
            chainId = sharedPrefs.getString("active_chain_id", null),
            seedPhrase = "",
            encryptionKeyBase64Url = encryptionKey,
            signingKeyBase64Url = signingKey,
            publicKeyBase64Url = publicKey,
            kexPrivateKeyBase64Url = getDeviceHpkePrivateKey().orEmpty(),
            kexPublicKeyBase64Url = getDeviceHpkePublicKey().orEmpty(),
            deviceMetaKeyBase64Url = getDeviceMetaKey().orEmpty(),
        )
    }

    /** Stages a candidate without overwriting the currently active chain keys. */
    fun savePendingChainKeys(snapshot: PallaSyncKeySnapshot): Boolean {
        require(!snapshot.chainId.isNullOrBlank()) { "pending chain ID is blank" }
        return sharedPrefs
            .edit()
            .putString("pending_chain_id", snapshot.chainId)
            .putString("pending_epoch_key", snapshot.encryptionKeyBase64Url)
            .putString("pending_signing_key", snapshot.signingKeyBase64Url)
            .putString("pending_public_key", snapshot.publicKeyBase64Url)
            .putString("pending_kex_private", snapshot.kexPrivateKeyBase64Url)
            .putString("pending_kex_public", snapshot.kexPublicKeyBase64Url)
            .putString("pending_meta_key", snapshot.deviceMetaKeyBase64Url)
            .commit()
    }

    fun getPendingChainKeys(): PallaSyncKeySnapshot? {
        val chainId =
            sharedPrefs.getString("pending_chain_id", null)?.takeIf { it.isNotBlank() }
                ?: return null
        return PallaSyncKeySnapshot(
            chainId = chainId,
            seedPhrase = "",
            encryptionKeyBase64Url =
                sharedPrefs
                    .getString("pending_epoch_key", null)
                    ?.takeIf { it.isNotBlank() } ?: return null,
            signingKeyBase64Url =
                sharedPrefs
                    .getString("pending_signing_key", null)
                    ?.takeIf { it.isNotBlank() } ?: return null,
            publicKeyBase64Url =
                sharedPrefs
                    .getString("pending_public_key", null)
                    ?.takeIf { it.isNotBlank() } ?: return null,
            kexPrivateKeyBase64Url = sharedPrefs.getString("pending_kex_private", "").orEmpty(),
            kexPublicKeyBase64Url = sharedPrefs.getString("pending_kex_public", "").orEmpty(),
            deviceMetaKeyBase64Url = sharedPrefs.getString("pending_meta_key", "").orEmpty(),
        )
    }

    /** Promotes a previously durable candidate after Room activation succeeds. */
    fun promotePendingChainKeys(): Boolean {
        val pending = getPendingChainKeys() ?: return false
        return sharedPrefs
            .edit()
            .putString("active_chain_id", pending.chainId)
            .putString("epoch_key", pending.encryptionKeyBase64Url)
            .putString("device_private_seed", pending.signingKeyBase64Url)
            .putString("device_sign_public", pending.publicKeyBase64Url)
            .putString("device_hpke_private", pending.kexPrivateKeyBase64Url)
            .putString("device_hpke_public", pending.kexPublicKeyBase64Url)
            .putString("device_meta_key", pending.deviceMetaKeyBase64Url)
            .remove("pending_chain_id")
            .remove("pending_seed_phrase")
            .remove("pending_epoch_key")
            .remove("pending_signing_key")
            .remove("pending_public_key")
            .remove("pending_kex_private")
            .remove("pending_kex_public")
            .remove("pending_meta_key")
            .commit()
    }

    fun clearPendingChainKeys(): Boolean =
        sharedPrefs
            .edit()
            .remove("pending_chain_id")
            .remove("pending_seed_phrase")
            .remove("pending_epoch_key")
            .remove("pending_signing_key")
            .remove("pending_public_key")
            .remove("pending_kex_private")
            .remove("pending_kex_public")
            .remove("pending_meta_key")
            .commit()

    fun clearActiveChainKeys(): Boolean =
        sharedPrefs
            .edit()
            .remove("seed_phrase")
            .remove("epoch_key")
            .remove("device_private_seed")
            .remove("device_sign_public")
            .remove("device_hpke_private")
            .remove("device_hpke_public")
            .remove("device_meta_key")
            .remove("active_chain_id")
            .commit()

    fun clearAllKeys() {
        val deviceId = sharedPrefs.getString("device_id", null)
        val editor = sharedPrefs.edit().clear()
        if (deviceId != null) {
            editor.putString("device_id", deviceId)
        }
        editor.commit()
    }
}
