package com.yunfie.illustia.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed class PinVerifyAction {
    data object Disable : PinVerifyAction()
    data class ChangeTiming(val newValue: String) : PinVerifyAction()
}

@Composable
fun AppLockSetupScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var pendingAction by remember { mutableStateOf<PinVerifyAction?>(null) }
    var verifyPin by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf(false) }
    var verifyShake by remember { mutableStateOf(false) }
    var requestBiometric by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableStateOf(0L) }
    val isCooldownActive = cooldownRemaining > 0L

    // Cooldown countdown timer — reads from shared ViewModel state
    LaunchedEffect(state.settings.appLockCooldownUntil) {
        val until = state.settings.appLockCooldownUntil
        while (true) {
            val remaining = ((until - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
            cooldownRemaining = remaining
            if (remaining <= 0L) break
            delay(250)
        }
    }

    LaunchedEffect(verifyShake) {
        if (verifyShake) {
            delay(400)
            verifyShake = false
            verifyError = false
        }
    }

    LaunchedEffect(requestBiometric) {
        if (requestBiometric) {
            delay(300)
            // Wait until the activity lifecycle is RESUMED so BiometricPrompt can display
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
                val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        viewModel.updateBiometricEnabled(true)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        requestBiometric = false
                    }
                })
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(context.getString(R.string.app_lock_biometric_verify_title))
                    .setSubtitle(context.getString(R.string.app_lock_biometric_verify_subtitle))
                    .setNegativeButtonText(context.getString(R.string.action_cancel))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    .build()
                prompt.authenticate(promptInfo)
            }
            requestBiometric = false
        }
    }

    fun showPinOverlay(action: PinVerifyAction) {
        pendingAction = action
        verifyPin = ""
        verifyError = false
    }

    fun dismissPinOverlay() {
        pendingAction = null
        verifyPin = ""
        verifyError = false
    }

    fun onVerifyDigit(digit: Char) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (isCooldownActive) return
        if (verifyError) {
            verifyPin = digit.toString()
            verifyError = false
            return
        }
        if (verifyPin.length >= 6) return
        val newPin = verifyPin + digit
        verifyPin = newPin
        if (newPin.length == 6) {
            if (viewModel.verifyPin(newPin)) {
                viewModel.resetLockFailCount()
                when (val action = pendingAction) {
                    is PinVerifyAction.Disable -> viewModel.disableAppLock()
                    is PinVerifyAction.ChangeTiming -> viewModel.updateAppLockTiming(action.newValue)
                    null -> {}
                }
                dismissPinOverlay()
            } else {
                verifyError = true
                verifyShake = true
                viewModel.recordLockFailure()
                verifyPin = ""
            }
        }
    }

    fun onVerifyDelete() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (verifyError) {
            verifyError = false
            verifyPin = ""
            return
        }
        if (verifyPin.isNotEmpty()) verifyPin = verifyPin.dropLast(1)
    }

    val verifyTitle = when (pendingAction) {
        is PinVerifyAction.Disable -> stringResource(R.string.app_lock_verify_disable)
        is PinVerifyAction.ChangeTiming -> stringResource(R.string.app_lock_verify_change_timing)
        null -> ""
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_lock_settings_title),
                largeTitle = stringResource(R.string.app_lock_settings_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 16.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Section(stringResource(R.string.app_lock_enable)) {
                        ElevatedPanel {
                            SettingSwitchRow(
                                title = stringResource(R.string.app_lock_enable),
                                checked = state.settings.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        showPinOverlay(PinVerifyAction.Disable)
                                    } else {
                                        viewModel.openAppLockPinEntry()
                                    }
                                },
                                summary = if (state.settings.appLockEnabled)
                                    stringResource(R.string.app_lock_enabled)
                                else
                                    stringResource(R.string.app_lock_disabled),
                            )
                        }
                    }
                }

                if (state.settings.appLockEnabled) {
                    item {
                        Section(stringResource(R.string.app_lock_section_pin)) {
                            ElevatedPanel {
                                SettingLinkRow(
                                    title = stringResource(R.string.app_lock_change_pin),
                                    onClick = { viewModel.openAppLockPinEntry() },
                                )
                            }
                        }
                    }

                    item {
                        Section(stringResource(R.string.app_lock_section_options)) {
                            ElevatedPanel {
                                SettingSwitchRow(
                                    title = stringResource(R.string.app_lock_timing_return),
                                    checked = state.settings.appLockTiming == "return",
                                    onCheckedChange = { enabled ->
                                        showPinOverlay(
                                            PinVerifyAction.ChangeTiming(if (enabled) "return" else "launch")
                                        )
                                    },
                                    summary = stringResource(R.string.app_lock_timing_return_desc),
                                )
                                DividerLine()
                                SettingSwitchRow(
                                    title = stringResource(R.string.app_lock_biometric),
                                    checked = state.settings.biometricEnabled,
                                    onCheckedChange = { enable ->
                                        if (enable) {
                                            requestBiometric = true
                                        } else {
                                            viewModel.updateBiometricEnabled(false)
                                        }
                                    },
                                    summary = stringResource(R.string.app_lock_biometric_desc),
                                )
                            }
                        }
                    }

                    item {
                        Section(stringResource(R.string.data_section_cleanup)) {
                            ElevatedPanel {
                                SettingLinkRow(
                                    title = stringResource(R.string.app_lock_disable),
                                    onClick = { showPinOverlay(PinVerifyAction.Disable) },
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = pendingAction != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.surface)
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = verifyTitle,
                            color = MiuixTheme.colorScheme.onSurface,
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
                        )

                        val translateX = if (verifyShake) 12.dp else 0.dp
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(horizontal = if (verifyShake) translateX else 0.dp),
                        ) {
                            repeat(6) { index ->
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                verifyError -> MiuixTheme.colorScheme.error
                                                index < verifyPin.length -> MiuixTheme.colorScheme.primary
                                                else -> MiuixTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            }
                                        ),
                                )
                            }
                        }

                        if (isCooldownActive) {
                            Text(
                                text = stringResource(R.string.app_lock_cooldown, cooldownRemaining.toFloat()),
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else if (verifyError) {
                            val attemptsRemaining = (12 - state.settings.appLockFailCount).coerceAtLeast(0)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.app_lock_incorrect),
                                    color = MiuixTheme.colorScheme.error,
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                                if (state.settings.appLockFailCount in 3..11) {
                                    Text(
                                        text = stringResource(R.string.app_lock_attempts_remaining, attemptsRemaining),
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        style = MiuixTheme.textStyles.footnote1,
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        VerifyNumberPad(
                            onDigit = ::onVerifyDigit,
                            onDelete = ::onVerifyDelete,
                            onCancel = ::dismissPinOverlay,
                            enabled = !isCooldownActive,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifyNumberPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean,
) {
    val rows = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
    )
    val btnAlpha = if (enabled) 1f else 0.4f

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { digit ->
                    VerifyPadButton(
                        label = digit.toString(),
                        onClick = { onDigit(digit) },
                        enabled = enabled,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .alpha(btnAlpha)
                    .clickable(enabled = enabled) { onCancel() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MiuixTheme.colorScheme.primary,
                    style = MiuixTheme.textStyles.body1,
                )
            }
            VerifyPadButton(label = "0", onClick = { onDigit('0') }, enabled = enabled)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .alpha(btnAlpha)
                    .clickable(enabled = enabled) { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MiuixIcons.Remove,
                    contentDescription = stringResource(R.string.action_delete),
                    modifier = Modifier.size(28.dp),
                    tint = MiuixTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun VerifyPadButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                if (enabled) MiuixTheme.colorScheme.surfaceContainer
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Medium,
        )
    }
}
