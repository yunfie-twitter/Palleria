@file:JvmName("AndroidAppLockScreen")

package com.yunfie.illustia.ui.screens

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.ui.screens.AppLockScreen as SharedAppLockScreen

@Composable
fun AppLockScreen(
    biometricEnabled: Boolean,
    failCount: Int,
    cooldownUntil: Long,
    viewModel: IllustiaViewModel,
) {
    val context = LocalContext.current
    val actions =
        remember(viewModel, context) {
            object : AppLockUnlockActions {
                override suspend fun verifyPin(pin: String) = viewModel.verifyPin(pin)

                override fun resetLockFailCount() = viewModel.resetLockFailCount()

                override fun recordLockFailure() = viewModel.recordLockFailure()

                override fun confirmUnlock() = viewModel.confirmUnlock()

                override fun openRecoveryWebLogin() = viewModel.openRecoveryWebLogin()

                override fun resetAppFromLock() = viewModel.resetAppFromLock()

                override fun isBiometricAvailable(): Boolean {
                    val manager = BiometricManager.from(context)
                    return manager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                            BiometricManager.Authenticators.BIOMETRIC_WEAK,
                    ) == BiometricManager.BIOMETRIC_SUCCESS
                }

                override fun requestBiometricAuth(onSuccess: () -> Unit) {
                    val activity = context as? FragmentActivity ?: return
                    val executor = ContextCompat.getMainExecutor(context)
                    val prompt =
                        BiometricPrompt(
                            activity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                    onSuccess()
                                }
                            },
                        )
                    val promptInfo =
                        BiometricPrompt.PromptInfo
                            .Builder()
                            .setTitle(AppStrings.get(StringKey.AppLockBiometricTitle))
                            .setSubtitle(AppStrings.get(StringKey.AppLockBiometricSubtitle))
                            .setNegativeButtonText(AppStrings.get(StringKey.ActionCancel))
                            .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                    BiometricManager.Authenticators.BIOMETRIC_WEAK,
                            ).build()
                    prompt.authenticate(promptInfo)
                }

                override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
            }
        }
    SharedAppLockScreen(
        biometricEnabled = biometricEnabled,
        failCount = failCount,
        cooldownUntil = cooldownUntil,
        actions = actions,
    )
}
