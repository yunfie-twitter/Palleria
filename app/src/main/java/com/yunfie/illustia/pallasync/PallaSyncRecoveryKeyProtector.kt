package com.yunfie.illustia.pallasync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.biometric.BiometricManager
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** §5.4/§7.11.3: OS-only wrapper, separate from wire AEAD and ordinary sync keys. */
internal class PallaSyncRecoveryKeyProtector(
    private val context: Context,
) {
    data class Prepared(
        val alias: String,
        val protection: String,
    )

    fun available(): Boolean = BiometricManager.from(context).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS

    fun prepare(): Prepared {
        check(available()) { "C_RECOVERY_STORAGE_UNSAFE" }
        val alias = PREFIX + UUID.randomUUID()
        val strong =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        // Documented StrongBox unavailability permits an OS Keystore fallback, with the same per-use authentication.
        @Suppress("SwallowedException")
        fun generate() =
            try {
                generate(alias, strong)
            } catch (unavailable: StrongBoxUnavailableException) {
                generate(alias, false)
            }
        // Any failure after generation must remove this operation's otherwise orphaned key.
        @Suppress("TooGenericExceptionCaught")
        try {
            val key = generate()
            val info = SecretKeyFactory.getInstance(key.algorithm, PROVIDER).getKeySpec(key, KeyInfo::class.java) as KeyInfo
            check(info.isUserAuthenticationRequired && info.userAuthenticationValidityDurationSeconds <= 0) {
                "C_RECOVERY_STORAGE_UNSAFE"
            }
            return Prepared(alias, if (info.isInsideSecureHardware) "hardware" else "os-keystore")
        } catch (failure: Throwable) {
            @Suppress("TooGenericExceptionCaught")
            try {
                delete(alias)
            } catch (cleanup: Throwable) {
                failure.addSuppressed(cleanup)
            }
            throw failure
        }
    }

    fun encrypt(alias: String): Cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key(alias)) }

    fun decrypt(entry: PallaSyncRecoverySlot.Entry): Cipher =
        Cipher.getInstance(TRANSFORMATION).apply {
            val iv = Base64.getUrlDecoder().decode(entry.keyIv)
            check(iv.size == IV_BYTES) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }
            init(Cipher.DECRYPT_MODE, key(entry.keyAlias), GCMParameterSpec(TAG_BITS, iv))
        }

    fun delete(alias: String) {
        requireOwnedAlias(alias)
        val store = store()
        store.deleteEntry(alias)
        check(!store.containsAlias(alias)) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }
    }

    private fun key(alias: String): SecretKey {
        requireOwnedAlias(alias)
        return checkNotNull(store().getKey(alias, null) as? SecretKey) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }
    }

    private fun generate(
        alias: String,
        strong: Boolean,
    ): SecretKey {
        val builder =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(KEY_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setIsStrongBoxBacked(strong)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply { init(builder.build()) }.generateKey()
    }

    companion object {
        private const val PROVIDER = "AndroidKeyStore"
        private const val PREFIX = "pallasync.recovery.wrap."
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_BITS = 256
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val UUID_VERSION = 4
        private const val UUID_VARIANT = 2

        fun authenticators(): Int =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BiometricManager.Authenticators.DEVICE_CREDENTIAL else 0

        private fun requireOwnedAlias(alias: String) {
            check(alias.startsWith(PREFIX)) { "C_RECOVERY_WRAP_KEY_UNAVAILABLE" }
            val id = UUID.fromString(alias.removePrefix(PREFIX))
            check(id.version() == UUID_VERSION && id.variant() == UUID_VARIANT && PREFIX + id == alias) {
                "C_RECOVERY_WRAP_KEY_UNAVAILABLE"
            }
        }

        private fun store(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
    }
}
