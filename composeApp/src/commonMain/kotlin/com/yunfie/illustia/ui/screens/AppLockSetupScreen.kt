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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.app_lock_attempts_remaining
import com.yunfie.illustia.resources.app_lock_biometric
import com.yunfie.illustia.resources.app_lock_biometric_desc
import com.yunfie.illustia.resources.app_lock_change_pin
import com.yunfie.illustia.resources.app_lock_cooldown
import com.yunfie.illustia.resources.app_lock_disable
import com.yunfie.illustia.resources.app_lock_disabled
import com.yunfie.illustia.resources.app_lock_enable
import com.yunfie.illustia.resources.app_lock_enabled
import com.yunfie.illustia.resources.app_lock_incorrect
import com.yunfie.illustia.resources.app_lock_section_options
import com.yunfie.illustia.resources.app_lock_section_pin
import com.yunfie.illustia.resources.app_lock_settings_title
import com.yunfie.illustia.resources.app_lock_timing_return
import com.yunfie.illustia.resources.app_lock_timing_return_desc
import com.yunfie.illustia.resources.app_lock_verify_change_timing
import com.yunfie.illustia.resources.app_lock_verify_disable
import com.yunfie.illustia.resources.data_section_cleanup
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.AppHapticEffect
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.rememberHapticFeedbackAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Remove
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed class PinVerifyAction {
    data object Disable : PinVerifyAction()

    data class ChangeTiming(
        val newValue: String,
    ) : PinVerifyAction()
}

@Composable
fun AppLockSetupScreen(
    settings: AppSettings,
    actions: AppLockActions,
    onBack: () -> Unit,
    requestBiometricAuth: suspend (onSuccess: () -> Unit, onError: () -> Unit) -> Unit,
    elapsedRealtimeMillis: () -> Long,
) {
    BackNavigationHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val performHaptic = rememberHapticFeedbackAction()
    val coroutineScope = rememberCoroutineScope()

    var pendingAction by remember { mutableStateOf<PinVerifyAction?>(null) }
    var verifyPin by remember { mutableStateOf("") }
    var verifyError by remember { mutableStateOf(false) }
    var verifyShake by remember { mutableStateOf(false) }
    var requestBiometric by remember { mutableStateOf(false) }
    var cooldownRemaining by remember { mutableStateOf(0L) }
    val isCooldownActive = cooldownRemaining > 0L

    // Cooldown countdown timer — reads from shared ViewModel state
    LaunchedEffect(settings.appLockCooldownUntil) {
        val until = settings.appLockCooldownUntil
        while (true) {
            val remaining = ((until - elapsedRealtimeMillis()) / 1000L).coerceAtLeast(0L)
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
            requestBiometricAuth(
                {
                    actions.updateBiometricEnabled(true)
                    performHaptic(AppHapticEffect.Success)
                },
                { requestBiometric = false },
            )
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
        performHaptic(AppHapticEffect.Click)
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
            coroutineScope.launch {
                if (actions.verifyPin(newPin)) {
                    performHaptic(AppHapticEffect.Success)
                    actions.resetLockFailCount()
                    when (val action = pendingAction) {
                        is PinVerifyAction.Disable -> {
                            actions.disableAppLock()
                        }

                        is PinVerifyAction.ChangeTiming -> {
                            actions.updateAppLockTiming(action.newValue)
                        }

                        null -> {}
                    }
                    dismissPinOverlay()
                } else {
                    performHaptic(AppHapticEffect.Error)
                    verifyError = true
                    verifyShake = true
                    actions.recordLockFailure()
                    verifyPin = ""
                }
            }
        }
    }

    fun onVerifyDelete() {
        performHaptic(AppHapticEffect.Click)
        if (verifyError) {
            verifyError = false
            verifyPin = ""
            return
        }
        if (verifyPin.isNotEmpty()) verifyPin = verifyPin.dropLast(1)
    }

    val verifyTitle =
        when (pendingAction) {
            is PinVerifyAction.Disable -> stringResource(Res.string.app_lock_verify_disable)
            is PinVerifyAction.ChangeTiming -> stringResource(Res.string.app_lock_verify_change_timing)
            null -> ""
        }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.app_lock_settings_title),
                largeTitle = stringResource(Res.string.app_lock_settings_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .background(MiuixTheme.colorScheme.surface),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = scaffoldPadding.calculateTopPadding() + 16.dp,
                        bottom = 96.dp,
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Section(stringResource(Res.string.app_lock_enable)) {
                        ElevatedPanel {
                            SettingSwitchRow(
                                title = stringResource(Res.string.app_lock_enable),
                                checked = settings.appLockEnabled,
                                onCheckedChange = { enabled ->
                                    if (!enabled) {
                                        showPinOverlay(PinVerifyAction.Disable)
                                    } else {
                                        actions.openAppLockPinEntry()
                                    }
                                },
                                summary =
                                    if (settings.appLockEnabled) {
                                        stringResource(Res.string.app_lock_enabled)
                                    } else {
                                        stringResource(Res.string.app_lock_disabled)
                                    },
                            )
                        }
                    }
                }

                if (settings.appLockEnabled) {
                    item {
                        Section(stringResource(Res.string.app_lock_section_pin)) {
                            ElevatedPanel {
                                SettingLinkRow(
                                    title = stringResource(Res.string.app_lock_change_pin),
                                    onClick = { actions.openAppLockPinEntry() },
                                )
                            }
                        }
                    }

                    item {
                        Section(stringResource(Res.string.app_lock_section_options)) {
                            ElevatedPanel {
                                SettingSwitchRow(
                                    title = stringResource(Res.string.app_lock_timing_return),
                                    checked = settings.appLockTiming == "return",
                                    onCheckedChange = { enabled ->
                                        showPinOverlay(
                                            PinVerifyAction.ChangeTiming(if (enabled) "return" else "launch"),
                                        )
                                    },
                                    summary = stringResource(Res.string.app_lock_timing_return_desc),
                                )
                                DividerLine()
                                SettingSwitchRow(
                                    title = stringResource(Res.string.app_lock_biometric),
                                    checked = settings.biometricEnabled,
                                    onCheckedChange = { enable ->
                                        if (enable) {
                                            requestBiometric = true
                                        } else {
                                            actions.updateBiometricEnabled(false)
                                        }
                                    },
                                    summary = stringResource(Res.string.app_lock_biometric_desc),
                                )
                            }
                        }
                    }

                    item {
                        Section(stringResource(Res.string.data_section_cleanup)) {
                            ElevatedPanel {
                                SettingLinkRow(
                                    title = stringResource(Res.string.app_lock_disable),
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
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surface)
                            .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier =
                            Modifier
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
                                    modifier =
                                        Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    verifyError -> MiuixTheme.colorScheme.error
                                                    index < verifyPin.length -> MiuixTheme.colorScheme.primary
                                                    else -> MiuixTheme.colorScheme.outline.copy(alpha = 0.3f)
                                                },
                                            ),
                                )
                            }
                        }

                        if (isCooldownActive) {
                            Text(
                                text = stringResource(Res.string.app_lock_cooldown, cooldownRemaining.toFloat()),
                                color = MiuixTheme.colorScheme.error,
                                style = MiuixTheme.textStyles.footnote1,
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else if (verifyError) {
                            val attemptsRemaining = (12 - settings.appLockFailCount).coerceAtLeast(0)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(Res.string.app_lock_incorrect),
                                    color = MiuixTheme.colorScheme.error,
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                                if (settings.appLockFailCount in 3..11) {
                                    Text(
                                        text = stringResource(Res.string.app_lock_attempts_remaining, attemptsRemaining),
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
