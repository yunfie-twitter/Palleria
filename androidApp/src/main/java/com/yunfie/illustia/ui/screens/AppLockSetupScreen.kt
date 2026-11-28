@file:JvmName("AndroidAppLockSetupScreen")

package com.yunfie.illustia.ui.screens

import android.os.SystemClock
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.yunfie.illustia.ui.screens.AppLockSetupScreen as SharedAppLockSetupScreen

@Composable
fun AppLockSetupScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val actions =
        remember(viewModel) {
            object : AppLockActions {
                override suspend fun verifyPin(pin: String) = viewModel.verifyPin(pin)

                override fun resetLockFailCount() = viewModel.resetLockFailCount()

                override fun recordLockFailure() = viewModel.recordLockFailure()

                override fun disableAppLock() = viewModel.disableAppLock()

                override fun updateBiometricEnabled(enabled: Boolean) = viewModel.updateBiometricEnabled(enabled)

                override fun updateAppLockTiming(value: String) = viewModel.updateAppLockTiming(value)

                override fun openAppLockPinEntry() = viewModel.openAppLockPinEntry()
            }
        }
    SharedAppLockSetupScreen(
        settings = settings,
        actions = actions,
        onBack = onBack,
        requestBiometricAuth = { onSuccess, onError ->
            val timeout = 3000L
            var waited = 0L
            while (
                lifecycleOwner.lifecycle.currentState != Lifecycle.State.RESUMED &&
                waited < timeout
            ) {
                delay(100)
                waited += 100
            }
            withContext(Dispatchers.Main) {
                val activity = context as? FragmentActivity ?: return@withContext
                val executor = ContextCompat.getMainExecutor(context)
                val prompt =
                    BiometricPrompt(
                        activity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                onSuccess()
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence,
                            ) {
                                onError()
                            }
                        },
                    )
                val promptInfo =
                    BiometricPrompt.PromptInfo
                        .Builder()
                        .setTitle(AppStrings.get(StringKey.AppLockBiometricVerifyTitle))
                        .setSubtitle(AppStrings.get(StringKey.AppLockBiometricVerifySubtitle))
                        .setNegativeButtonText(AppStrings.get(StringKey.ActionCancel))
                        .setAllowedAuthenticators(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.BIOMETRIC_WEAK,
                        ).build()
                prompt.authenticate(promptInfo)
            }
        },
        elapsedRealtimeMillis = { SystemClock.elapsedRealtime() },
    )
}
