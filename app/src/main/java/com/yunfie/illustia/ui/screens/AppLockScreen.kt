package com.yunfie.illustia.ui.screens

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.BottomSheetInsideMargin
import com.yunfie.illustia.ui.components.LocalAppHapticMode
import com.yunfie.illustia.ui.components.LocalBottomSheetBackgroundColor
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import com.yunfie.illustia.ui.components.performAppHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Block all back navigation while locked.
    BackHandler(enabled = true) {}
    PredictiveBackHandler(enabled = true) {}

    val biometricAvailable =
        remember {
            val manager = BiometricManager.from(context)
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
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
        performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.Success)
    }

    fun vibrateError() {
        performAppHapticFeedback(context, haptic, hapticMode, AppHapticEffect.Error)
    }

    fun triggerUnlockAnimation() {
        unlocking = true
        vibrateUnlock()
    }

    fun triggerBiometric() {
        val activity = context as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt =
            BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        viewModel.resetLockFailCount()
                        triggerUnlockAnimation()
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {}
                },
            )
        val promptInfo =
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(context.getString(R.string.app_lock_biometric_title))
                .setSubtitle(context.getString(R.string.app_lock_biometric_subtitle))
                .setNegativeButtonText(context.getString(R.string.action_cancel))
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK,
                ).build()
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
            coroutineScope.launch {
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

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.surface)
                .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        // Red flash overlay on error
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.error.copy(alpha = flashAlpha * 0.15f))
                    .clickable(enabled = false) {},
        )

        val useWideLayout = maxWidth >= 600.dp && maxWidth > maxHeight
        val unlockModifier = Modifier.alpha(unlockAlpha).scale(unlockScale)
        val biometricAction = if (showBiometric && !unlocking && !isCooldownActive) ::triggerBiometric else null

        if (useWideLayout) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 32.dp, vertical = 16.dp)
                        .then(unlockModifier),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppLockStatusPane(
                    pin = pin,
                    error = error,
                    unlocking = unlocking,
                    isCooldownActive = isCooldownActive,
                    cooldownRemaining = cooldownRemaining,
                    failCount = failCount,
                    shake = shake,
                    onShowRecovery = { showRecoverySheet = true },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                NumberPad(
                    onDigit = ::onDigitPressed,
                    onDelete = ::onDeletePressed,
                    onBiometric = biometricAction,
                    enabled = !isCooldownActive && !unlocking,
                    compact = true,
                    modifier = Modifier.weight(1f).widthIn(max = 360.dp),
                )
            }
        } else {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .widthIn(max = 480.dp)
                        .padding(horizontal = 32.dp)
                        .then(unlockModifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppLockStatusPane(
                    pin = pin,
                    error = error,
                    unlocking = unlocking,
                    isCooldownActive = isCooldownActive,
                    cooldownRemaining = cooldownRemaining,
                    failCount = failCount,
                    shake = shake,
                    onShowRecovery = { showRecoverySheet = true },
                )
                NumberPad(
                    onDigit = ::onDigitPressed,
                    onDelete = ::onDeletePressed,
                    onBiometric = biometricAction,
                    enabled = !isCooldownActive && !unlocking,
                )
            }
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_lock_recovery_summary, failCount),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Button(
                    onClick = {
                        showRecoverySheet = false
                        viewModel.openRecoveryWebLogin()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(
                        stringResource(R.string.app_lock_recovery_verify),
                        color = MiuixTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Button(
                    onClick = {
                        showRecoverySheet = false
                        showResetConfirmDialog = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = overlayActionButtonColors(),
                ) {
                    Text(
                        stringResource(R.string.app_lock_recovery_reset),
                        color = MiuixTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }

    if (showResetConfirmDialog) {
        MiuixConfirmDialog(
            show = true,
            title = stringResource(R.string.app_lock_recovery_reset_confirm_title),
            summary = stringResource(R.string.app_lock_recovery_reset_confirm_summary),
            confirmText = stringResource(R.string.app_lock_recovery_reset),
            destructive = true,
            onConfirm = {
                showResetConfirmDialog = false
                viewModel.resetAppFromLock()
            },
            onDismiss = { showResetConfirmDialog = false },
        )
    }
}

@Composable
private fun AppLockStatusPane(
    pin: String,
    error: Boolean,
    unlocking: Boolean,
    isCooldownActive: Boolean,
    cooldownRemaining: Long,
    failCount: Int,
    shake: Boolean,
    onShowRecovery: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val attemptsRemaining = (12 - failCount).coerceAtLeast(0)
    val itemSpacing = if (compact) 12.dp else 24.dp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
    ) {
        if (!compact) Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = if (unlocking) MiuixIcons.Unlock else MiuixIcons.Lock,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 40.dp else 48.dp),
            tint = if (error) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.app_lock_title),
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
            modifier = Modifier.padding(horizontal = if (shake) 16.dp else 0.dp),
        ) {
            repeat(6) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(if (compact) 13.dp else 16.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    unlocking -> MiuixTheme.colorScheme.primary
                                    error -> MiuixTheme.colorScheme.error
                                    index < pin.length -> MiuixTheme.colorScheme.primary
                                    else -> MiuixTheme.colorScheme.outline.copy(alpha = 0.3f)
                                },
                            ),
                )
            }
        }
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
                Spacer(modifier = Modifier.height(if (compact) 4.dp else 16.dp))
            }
        }
        if (failCount >= 3) {
            Text(
                text = stringResource(R.string.app_lock_forgot_pin),
                color = MiuixTheme.colorScheme.primary,
                style = MiuixTheme.textStyles.footnote1,
                modifier = Modifier.clickable(onClick = onShowRecovery).padding(vertical = 8.dp),
            )
        } else if (!compact) {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
