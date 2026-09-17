package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.overlayActionButtonColors
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ── Auto-lock timing options ───────────────────────────────────────────────────

private val AUTO_LOCK_VALUES = listOf("immediate", "30s", "1m", "5m", "10m", "disabled")

@Composable
private fun autoLockLabel(value: String): String =
    when (value) {
        "immediate" -> stringResource(R.string.privacy_auto_lock_immediate)
        "30s" -> stringResource(R.string.privacy_auto_lock_30s)
        "1m" -> stringResource(R.string.privacy_auto_lock_1m)
        "5m" -> stringResource(R.string.privacy_auto_lock_5m)
        "10m" -> stringResource(R.string.privacy_auto_lock_10m)
        "disabled" -> stringResource(R.string.privacy_auto_lock_disabled)
        else -> value
    }

// ── Dummy icon variant options ─────────────────────────────────────────────────

private val DUMMY_ICON_VALUES = listOf("ic_launcher_dummy")

@Composable
private fun dummyIconLabel(value: String): String =
    when (value) {
        "ic_launcher_dummy" -> stringResource(R.string.privacy_dummy_icon_calculator)
        else -> value
    }

// ── Screen ─────────────────────────────────────────────────────────────────────

/**
 * Privacy Mode settings screen.
 *
 * Requirements: 11.1, 11.2 (Task 8.1)
 * ViewModel wiring: 5.3, 5.4, 5.7, 11.4 (Task 8.2)
 */
@Composable
fun PrivacyModeSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val defaultDummyAppName = stringResource(R.string.app_name_dummy)

    // ── Enable privacy mode dialog state ──────────────────────────────────────
    var showEnableCodeDialog by remember { mutableStateOf(false) }
    var isResettingCode by remember { mutableStateOf(false) }
    var enableCodeInput by remember { mutableStateOf("") }
    var enableCodeConfirm by remember { mutableStateOf("") }
    var enableCodeError by remember { mutableStateOf<String?>(null) }
    val enableCodeMismatchText = stringResource(R.string.privacy_code_mismatch)
    val enableCodeInvalidText = stringResource(R.string.privacy_code_invalid)
    val enableCodeIncorrectText = stringResource(R.string.privacy_code_incorrect)

    // ── Change unlock code dialog state ───────────────────────────────────────
    var showChangeCodeDialog by remember { mutableStateOf(false) }
    var currentCode by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var changeCodeError by remember { mutableStateOf<String?>(null) }

    // ── Dummy app name edit dialog state ──────────────────────────────────────
    var showDummyNameDialog by remember { mutableStateOf(false) }
    var dummyNameDraft by remember { mutableStateOf("") }
    var dummyNameError by remember { mutableStateOf<String?>(null) }
    val changeCodeErrorText = stringResource(R.string.privacy_change_code_error)
    val dummyNameErrorText = stringResource(R.string.privacy_dummy_name_error)

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.privacy_mode_title),
                largeTitle = stringResource(R.string.privacy_mode_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
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
            // ── Section 1: Enable / Disable ───────────────────────────────────
            item {
                Section(stringResource(R.string.privacy_mode_title)) {
                    ElevatedPanel {
                        // 1. Privacy Mode ON/OFF toggle (Req 11.2, 1.1–1.3)
                        SettingSwitchRow(
                            title = stringResource(R.string.privacy_mode_title),
                            checked = state.settings.privacyModeEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    enableCodeInput = ""
                                    enableCodeConfirm = ""
                                    enableCodeError = null
                                    isResettingCode = false
                                    showEnableCodeDialog = true
                                } else {
                                    viewModel.disablePrivacyMode()
                                }
                            },
                            summary =
                                if (state.settings.privacyModeEnabled) {
                                    stringResource(R.string.privacy_mode_enabled_desc)
                                } else {
                                    stringResource(R.string.privacy_mode_disabled_desc)
                                },
                        )
                    }
                }
            }

            // ── Section 2: Unlock code ─────────────────────────────────────────
            item {
                Section(stringResource(R.string.privacy_unlock_code_section)) {
                    ElevatedPanel {
                        // 2. Change unlock code (Req 5.3, 5.4, 5.7)
                        SettingLinkRow(
                            title = stringResource(R.string.privacy_change_code),
                            summary = stringResource(R.string.privacy_change_code_desc),
                            onClick = {
                                currentCode = ""
                                newCode = ""
                                changeCodeError = null
                                showChangeCodeDialog = true
                            },
                        )
                    }
                }
            }

            // ── Section 3: Lock behaviour ──────────────────────────────────────
            item {
                Section(stringResource(R.string.privacy_lock_behavior)) {
                    ElevatedPanel {
                        // 3. Auto-lock timing selection (Req 6.4–6.6)
                        SettingDropdownRow(
                            title = stringResource(R.string.privacy_auto_lock),
                            summary = stringResource(R.string.privacy_auto_lock_desc),
                            values = AUTO_LOCK_VALUES,
                            selected = state.settings.privacyModeAutoLockTiming,
                            label = { autoLockLabel(it) },
                            onSelect = { viewModel.updatePrivacyModeAutoLockTiming(it) },
                        )
                    }
                }
            }

            // ── Section 4: Privacy ─────────────────────────────────────────────
            item {
                Section(stringResource(R.string.privacy_section)) {
                    ElevatedPanel {
                        // 4. Hide Recents toggle (Req 7.1–7.4)
                        SettingSwitchRow(
                            title = stringResource(R.string.privacy_hide_recents),
                            checked = state.settings.hideRecents,
                            onCheckedChange = { viewModel.updateHideRecents(it) },
                            summary = stringResource(R.string.privacy_hide_recents_desc),
                        )
                        DividerLine()
                        // 5. Hide Notifications toggle (Req 9.1–9.4)
                        SettingSwitchRow(
                            title = stringResource(R.string.privacy_hide_notifications),
                            checked = state.settings.hideNotifications,
                            onCheckedChange = { viewModel.updateHideNotifications(it) },
                            summary = stringResource(R.string.privacy_hide_notifications_desc),
                        )
                    }
                }
            }

            // ── Section 5: Disguise ────────────────────────────────────────────
            item {
                Section(stringResource(R.string.privacy_disguise_section)) {
                    ElevatedPanel {
                        // 6. Dummy app name text input (Req 10.1, 10.3)
                        SettingLinkRow(
                            title = stringResource(R.string.privacy_dummy_app_name),
                            summary = state.settings.dummyAppName.ifBlank { defaultDummyAppName },
                            onClick = {
                                dummyNameDraft = state.settings.dummyAppName.ifBlank { defaultDummyAppName }
                                dummyNameError = null
                                showDummyNameDialog = true
                            },
                        )
                        DividerLine()
                        // 7. Dummy icon variant selector (Req 10.2, 10.4)
                        SettingDropdownRow(
                            title = stringResource(R.string.privacy_dummy_icon),
                            summary = stringResource(R.string.privacy_dummy_icon_desc),
                            values = DUMMY_ICON_VALUES,
                            selected = state.settings.dummyIconVariant,
                            label = { dummyIconLabel(it) },
                            onSelect = { viewModel.updateDummyIconVariant(it) },
                        )
                    }
                }
            }
        }
    }

    // ── Enable Privacy Mode Dialog ────────────────────────────────────────────
    if (showEnableCodeDialog) {
        val requiresNewCode = !viewModel.hasUnlockCodeSet() || isResettingCode
        OverlayDialog(
            show = true,
            title = stringResource(R.string.privacy_enable_dialog_title),
            onDismissRequest = {
                showEnableCodeDialog = false
                isResettingCode = false
                enableCodeInput = ""
                enableCodeConfirm = ""
                enableCodeError = null
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text =
                        stringResource(
                            if (requiresNewCode) {
                                R.string.privacy_enable_set_code_desc
                            } else {
                                R.string.privacy_enable_enter_code_desc
                            },
                        ),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )

                if (requiresNewCode) {
                    TextField(
                        value = enableCodeInput,
                        onValueChange = {
                            enableCodeInput = it
                            enableCodeError = null
                        },
                        label = stringResource(R.string.privacy_new_code),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Next,
                            ),
                    )

                    TextField(
                        value = enableCodeConfirm,
                        onValueChange = {
                            enableCodeConfirm = it
                            enableCodeError = null
                        },
                        label = stringResource(R.string.privacy_code_confirm),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                    )
                } else {
                    TextField(
                        value = enableCodeInput,
                        onValueChange = {
                            enableCodeInput = it
                            enableCodeError = null
                        },
                        label = stringResource(R.string.privacy_unlock_code),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done,
                            ),
                    )

                    Text(
                        text = stringResource(R.string.privacy_reset_code),
                        color = MiuixTheme.colorScheme.primary,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        modifier =
                            Modifier.clickable {
                                isResettingCode = true
                                enableCodeInput = ""
                                enableCodeConfirm = ""
                                enableCodeError = null
                            },
                    )
                }

                enableCodeError?.let { errorText ->
                    Text(
                        text = errorText,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            showEnableCodeDialog = false
                            isResettingCode = false
                            enableCodeInput = ""
                            enableCodeConfirm = ""
                            enableCodeError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            if (requiresNewCode) {
                                if (enableCodeInput != enableCodeConfirm) {
                                    enableCodeError = enableCodeMismatchText
                                } else if (!viewModel.isValidUnlockCode(enableCodeInput)) {
                                    enableCodeError = enableCodeInvalidText
                                } else {
                                    val success = viewModel.enablePrivacyModeWithCode(enableCodeInput)
                                    if (success) {
                                        showEnableCodeDialog = false
                                        isResettingCode = false
                                        enableCodeInput = ""
                                        enableCodeConfirm = ""
                                        enableCodeError = null
                                    } else {
                                        enableCodeError = enableCodeInvalidText
                                    }
                                }
                            } else {
                                if (viewModel.verifyCurrentUnlockCode(enableCodeInput)) {
                                    viewModel.enablePrivacyMode()
                                    showEnableCodeDialog = false
                                    isResettingCode = false
                                    enableCodeInput = ""
                                    enableCodeConfirm = ""
                                    enableCodeError = null
                                } else {
                                    enableCodeError = enableCodeIncorrectText
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_enable), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ── Change Unlock Code Dialog ─────────────────────────────────────────────
    // Requirements: 5.3, 5.4, 5.7
    if (showChangeCodeDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.privacy_change_code),
            onDismissRequest = {
                showChangeCodeDialog = false
                currentCode = ""
                newCode = ""
                changeCodeError = null
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Current code field
                TextField(
                    value = currentCode,
                    onValueChange = {
                        currentCode = it
                        changeCodeError = null
                    },
                    label = stringResource(R.string.privacy_current_code),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next,
                        ),
                )

                // New code field
                TextField(
                    value = newCode,
                    onValueChange = {
                        newCode = it
                        changeCodeError = null
                    },
                    label = stringResource(R.string.privacy_new_code),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                )

                // Error message (Req 5.7)
                changeCodeError?.let { errorText ->
                    Text(
                        text = errorText,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            showChangeCodeDialog = false
                            currentCode = ""
                            newCode = ""
                            changeCodeError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            // Validate and apply (Req 5.3, 5.4)
                            val success = viewModel.changeUnlockCode(currentCode, newCode)
                            if (success) {
                                showChangeCodeDialog = false
                                currentCode = ""
                                newCode = ""
                                changeCodeError = null
                            } else {
                                // Show error — either wrong current code or invalid new code (Req 5.7)
                                changeCodeError = changeCodeErrorText
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_change), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ── Dummy App Name Dialog ─────────────────────────────────────────────────
    // Requirements: 10.1, 10.3
    if (showDummyNameDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.privacy_change_dummy_name),
            onDismissRequest = {
                showDummyNameDialog = false
                dummyNameDraft = ""
                dummyNameError = null
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = dummyNameDraft,
                    onValueChange = {
                        // Limit to 30 chars (Req 10.1)
                        if (it.length <= 30) {
                            dummyNameDraft = it
                            dummyNameError = null
                        }
                    },
                    label = stringResource(R.string.privacy_dummy_name_field),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                )

                // Error message
                dummyNameError?.let { errorText ->
                    Text(
                        text = errorText,
                        color = MiuixTheme.colorScheme.error,
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            showDummyNameDialog = false
                            dummyNameDraft = ""
                            dummyNameError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(
                        onClick = {
                            val trimmed = dummyNameDraft.trim()
                            if (trimmed.isBlank()) {
                                dummyNameError = dummyNameErrorText
                            } else {
                                viewModel.updateDummyAppName(trimmed)
                                showDummyNameDialog = false
                                dummyNameDraft = ""
                                dummyNameError = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = overlayActionButtonColors(),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
