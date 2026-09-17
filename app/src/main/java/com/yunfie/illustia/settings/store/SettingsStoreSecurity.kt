package com.yunfie.illustia.settings.store

import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal fun savePinHash(
    prefs: SharedPreferences,
    pin: String,
) {
    val salt = generateSalt()
    val hash = pbkdf2(pin, salt)
    prefs
        .edit()
        .putString(KEY_PIN_HASH, hash)
        .putString(KEY_PIN_SALT, salt)
        .apply()
}

internal suspend fun verifyPinHash(
    prefs: SharedPreferences,
    pin: String,
): Boolean =
    withContext(Dispatchers.Default) {
        val storedHash = prefs.getString(KEY_PIN_HASH, null) ?: return@withContext false
        val salt = prefs.getString(KEY_PIN_SALT, null)
        if (salt == null) {
            val legacyMatch = constantTimeEquals(sha256(pin).toByteArray(), storedHash.toByteArray())
            if (legacyMatch) {
                savePinHash(prefs, pin)
            }
            return@withContext legacyMatch
        }
        val computed = pbkdf2(pin, salt)
        constantTimeEquals(computed.toByteArray(), storedHash.toByteArray())
    }

internal fun hasPinSet(prefs: SharedPreferences): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

internal fun clearPinHash(prefs: SharedPreferences) {
    prefs
        .edit()
        .remove(KEY_PIN_HASH)
        .remove(KEY_PIN_SALT)
        .apply()
}

internal fun saveUnlockCodeHash(
    prefs: SharedPreferences,
    code: String,
) {
    val salt = generateSalt()
    val hash = pbkdf2(code, salt)
    prefs
        .edit()
        .putString(KEY_UNLOCK_CODE_HASH, hash)
        .putString(KEY_UNLOCK_CODE_SALT, salt)
        .apply()
}

internal suspend fun verifyUnlockCodeHash(
    prefs: SharedPreferences,
    code: String,
): Boolean =
    withContext(Dispatchers.Default) {
        try {
            val storedHash = prefs.getString(KEY_UNLOCK_CODE_HASH, null) ?: return@withContext false
            val salt = prefs.getString(KEY_UNLOCK_CODE_SALT, null) ?: return@withContext false
            val computed = pbkdf2(code, salt)
            constantTimeEquals(computed.toByteArray(), storedHash.toByteArray())
        } catch (expectedFailure: GeneralSecurityException) {
            Log.e("SettingsStore", "verifyUnlockCode error", expectedFailure)
            false
        }
    }

internal fun hasUnlockCodeSet(prefs: SharedPreferences): Boolean = prefs.getString(KEY_UNLOCK_CODE_HASH, null) != null

internal fun clearUnlockCodeHash(prefs: SharedPreferences) {
    prefs
        .edit()
        .remove(KEY_UNLOCK_CODE_HASH)
        .remove(KEY_UNLOCK_CODE_SALT)
        .apply()
}

internal fun isValidUnlockCode(code: String): Boolean {
    if (code.length !in 4..20) return false
    val allowedChars =
        setOf(
            '0',
            '1',
            '2',
            '3',
            '4',
            '5',
            '6',
            '7',
            '8',
            '9',
            '+',
            '-',
            '*',
            '×',
            '/',
            '÷',
            '.',
            '=',
        )
    return code.all { it in allowedChars }
}

private fun generateSalt(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun pbkdf2(
    value: String,
    salt: String,
): String {
    val spec =
        PBEKeySpec(
            value.toCharArray(),
            salt.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERATIONS,
            PBKDF2_KEY_LENGTH,
        )
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val hash = factory.generateSecret(spec).encoded
    return hash.joinToString("") { "%02x".format(it) }
}

private fun sha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hashBytes.joinToString("") { "%02x".format(it) }
}

private fun constantTimeEquals(
    a: ByteArray,
    b: ByteArray,
): Boolean = MessageDigest.isEqual(a, b)
