package com.yunfie.illustia.ui.screens

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import kotlinx.coroutines.delay
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.performAppHapticFeedback
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AppLockScreen(
    biometricEnabled: Boolean,
    failCount: Int,
    cooldownUntil: Long,
    viewModel: IllustiaViewModel,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val hapticMode = LocalAppHapticMode.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var shake by remember { mutableStateOf(false) }
    var unlocking by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableStateOf(0L) }
    var errorFlash by remember { mutableFloatStateOf(0f) }
    var showRecoverySheet by remember { mutableStateOf(false) }

    // Block all back navigation while locked.
    BackHandler(enabled = true) {}
    PredictiveBackHandler(enabled = true) {}

    val biometricAvailable = remember {
        val manager = BiometricManager.from(context)
        manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val showBiometric = biometricEnabled && biometricAvailable
    val isCooldownActive = cooldownRemaining > 0L

    // Cooldown countdown timer
    LaunchedEffect(cooldownUntil) {
        while (true) {
            val remaining = ((cooldownUntil - android.os.SystemClock.elapsedRealtime()) / 1000L).coerceAtLeast(0L)
            cooldownRemaining = remaining
            if (remaining <= 0L) break
            delay(250)
        }
    }

    fun vibrateUnlock() {
        performAppHapticFeedback(context, haptic, hapticMode)
    }

    fun vibrateError() {
        performAppHapticFeedback(context, haptic, hapticMode)
    }

    fun triggerUnlockAnimation() {
        unlocking = true
        vibrateUnlock()
    }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                viewModel.resetLockFailCount()
                triggerUnlockAnimation()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
        })
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.app_lock_biometric_title))
            .setSubtitle(context.getString(R.string.app_lock_biometric_subtitle))
            .setNegativeButtonText(context.getString(R.string.action_cancel))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(showBiometric) {
        if (showBiometric) {
            delay(300)
            triggerBiometric()
        }
    }

    LaunchedEffect(shake) {
        if (shake) {
            delay(500)
            shake = false
            error = false
        }
    }

    LaunchedEffect(errorFlash) {
        if (errorFlash > 0f) {
            delay(600)
            errorFlash = 0f
        }
    }

    LaunchedEffect(unlocking) {
        if (unlocking) {
            delay(500)
            viewModel.confirmUnlock()
        }
    }

    fun onDigitPressed(digit: Char) {
        performAppHapticFeedback(context, haptic, hapticMode)
        if (unlocking || isCooldownActive) return
        if (error) {
            pin = digit.toString()
            error = false
            return
        }
        if (pin.length >= 6) return
        val newPin = pin + digit
        pin = newPin
        if (newPin.length == 6) {
            if (viewModel.verifyPin(newPin)) {
                viewModel.resetLockFailCount()
                triggerUnlockAnimation()
            } else {
                error = true
                shake = true
                errorFlash = 1f
                vibrateError()
                viewModel.recordLockFailure()
                pin = ""
            }
        }
    }

    fun onDeletePressed() {
        performAppHapticFeedback(context, haptic, hapticMode)
        if (unlocking || isCooldownActive) return
        if (error) {
            error = false
            pin = ""
            return
        }
        if (pin.isNotEmpty()) {
            pin = pin.dropLast(1)
        }
    }

    val unlockAlpha by animateFloatAsState(
        targetValue = if (unlocking) 0f else 1f,
        animationSpec = tween(durationMillis = 400),
    )
    val unlockScale by animateFloatAsState(
        targetValue = if (unlocking) 1.1f else 1f,
        animationSpec = tween(durationMillis = 400),
    )
    val flashAlpha by animateFloatAsState(
        targetValue = errorFlash,
        animationSpec = tween(durationMillis = 600),
    )

    val shakeTranslateX = if (shake) 16.dp else 0.dp
    val attemptsRemaining = (12 - failCount).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        // Red flash overlay on error
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.error.copy(alpha = flashAlpha * 0.15f))
                .clickable(enabled = false) {},
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .alpha(unlockAlpha)
                .scale(unlockScale),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Icon(
                imageVector = if (unlocking) MiuixIcons.Unlock else MiuixIcons.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
            )

            Text(
                text = stringResource(R.string.app_lock_title),
                color = MiuixTheme.colorScheme.onSurface,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
            )

            // PIN dots with shake
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = if (shake) shakeTranslateX else 0.dp),
            ) {
                repeat(6) { index ->
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    unlocking -> MiuixTheme.colorScheme.primary
                                    error -> MiuixTheme.colorScheme.error
                                    index < pin.length -> MiuixTheme.colorScheme.primary
                                    else -> MiuixTheme.colorScheme.outline.copy(alpha = 0.3f)
                                }
                            ),
                    )
                }
            }

            // Status messages
            when {
                isCooldownActive -> {
                    Text(
                        text = stringResource(R.string.app_lock_cooldown, cooldownRemaining.toFloat()),
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.app_lock_incorrect),
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                        if (failCount in 3..11) {
                            Text(
                                text = stringResource(R.string.app_lock_attempts_remaining, attemptsRemaining),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                    }
                }
                else -> {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Forgot PIN? link
            if (failCount >= 3) {
                Text(
                    text = stringResource(R.string.app_lock_forgot_pin),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier
                        .clickable { showRecoverySheet = true }
                        .padding(vertical = 8.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(32.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            NumberPad(
                onDigit = ::onDigitPressed,
                onDelete = ::onDeletePressed,
                onBiometric = if (showBiometric && !unlocking && !isCooldownActive) ::triggerBiometric else null,
                enabled = !isCooldownActive && !unlocking,
            )
        }
    }

    if (showRecoverySheet) {
        OverlayBottomSheet(
            show = true,
            modifier = Modifier.scrollEndHaptic(),
            onDismissRequest = { showRecoverySheet = false },
            title = stringResource(R.string.app_lock_recovery_title),
            backgroundColor = LocalBottomSheetBackgroundColor.current,
            insideMargin = BottomSheetInsideMargin,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_lock_recovery_summary),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = {
                        showRecoverySheet = false
                        viewModel.openRecoveryWebLogin()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(stringResource(R.string.app_lock_recovery_verify))
                }
                Button(
                    onClick = {
                        showRecoverySheet = false
                        viewModel.resetAppLockData()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(stringResource(R.string.app_lock_recovery_reset), color = MiuixTheme.colorScheme.error)
                }
            }
        }
    }
}
