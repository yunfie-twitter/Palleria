package com.yunfie.illustia.pallasync

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.yunfie.illustia.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** §7.11.5/§18.7: fresh OS authentication bound to this exact crypto operation, never an app-lock boolean. */
internal suspend fun authenticateRecoveryCipher(
    activity: FragmentActivity,
    cipher: Cipher,
    title: String = activity.getString(R.string.pallasync_unlock_recovery_share),
    subtitle: String = activity.getString(R.string.pallasync_recovery_auth_purpose),
): Cipher =
    withContext(Dispatchers.Main.immediate) {
        check(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { "C_RECOVERY_AUTH_REQUIRED" }
        suspendCancellableCoroutine { continuation ->
            val callback =
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (!continuation.isActive) return
                        val authenticated = result.cryptoObject?.cipher
                        if (authenticated === cipher) {
                            PallaSyncRecoveryAuthEvents.succeeded()
                            continuation.resume(cipher)
                        } else {
                            continuation.resumeWithException(IllegalStateException("C_RECOVERY_AUTH_REQUIRED"))
                        }
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (continuation.isActive) continuation.resumeWithException(IllegalStateException("C_RECOVERY_AUTH_REQUIRED"))
                    }
                }
            val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
            val authenticators = PallaSyncRecoveryKeyProtector.authenticators()
            val info =
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setConfirmationRequired(true)
                    .setAllowedAuthenticators(authenticators)
            if (authenticators and androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0) {
                info.setNegativeButtonText(activity.getString(R.string.action_cancel))
            }
            continuation.invokeOnCancellation { ContextCompat.getMainExecutor(activity).execute { prompt.cancelAuthentication() } }
            prompt.authenticate(info.build(), BiometricPrompt.CryptoObject(cipher))
        }
    }

/** Require fresh OS authentication bound to the exact receipt before creating a local intent witness. */
internal suspend fun <T> authenticateDeletionWitness(
    activity: FragmentActivity,
    context: android.content.Context,
    receiptHash: String,
    title: String,
    subtitle: String,
    operation: suspend () -> T,
): T {
    val protector = PallaSyncRecoveryKeyProtector(context)
    val prepared = protector.prepare()
    val binding = receiptHash.toByteArray(Charsets.US_ASCII)
    try {
        val cipher = protector.encrypt(prepared.alias)
        val authenticated = authenticateRecoveryCipher(activity, cipher, title, subtitle)
        authenticated.updateAAD(binding)
        val proof = authenticated.doFinal(binding)
        proof.fill(0)
        return operation()
    } finally {
        binding.fill(0)
        protector.delete(prepared.alias)
    }
}
