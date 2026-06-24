package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingDropdownRow
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
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
private fun autoLockLabel(value: String): String = when (value) {
    "immediate" -> "即時"
    "30s"       -> "30秒"
    "1m"        -> "1分"
    "5m"        -> "5分"
    "10m"       -> "10分"
    "disabled"  -> "無効"
    else        -> value
}

// ── Dummy icon variant options ─────────────────────────────────────────────────

private val DUMMY_ICON_VALUES = listOf("ic_launcher_dummy")

@Composable
private fun dummyIconLabel(value: String): String = when (value) {
    "ic_launcher_dummy" -> "電卓（デフォルト）"
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

    // ── Change unlock code dialog state ───────────────────────────────────────
    var showChangeCodeDialog by remember { mutableStateOf(false) }
    var currentCode by remember { mutableStateOf("") }
    var newCode by remember { mutableStateOf("") }
    var changeCodeError by remember { mutableStateOf<String?>(null) }

    // ── Dummy app name edit dialog state ──────────────────────────────────────
    var showDummyNameDialog by remember { mutableStateOf(false) }
    var dummyNameDraft by remember { mutableStateOf("") }
    var dummyNameError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = "プライバシーモード",
                largeTitle = "プライバシーモード",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
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

            // ── Section 1: Enable / Disable ───────────────────────────────────
            item {
                Section("プライバシーモード") {
                    ElevatedPanel {
                        // 1. Privacy Mode ON/OFF toggle (Req 11.2, 1.1–1.3)
                        SettingSwitchRow(
                            title = "プライバシーモード",
                            checked = state.settings.privacyModeEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    viewModel.enablePrivacyMode()
                                } else {
                                    viewModel.disablePrivacyMode()
                                }
                            },
                            summary = if (state.settings.privacyModeEnabled)
                                "有効 — 起動時に電卓を表示します"
                            else
                                "無効 — 通常の起動画面を表示します",
                        )
                    }
                }
            }

            // ── Section 2: Unlock code ─────────────────────────────────────────
            item {
                Section("解除コード") {
                    ElevatedPanel {
                        // 2. Change unlock code (Req 5.3, 5.4, 5.7)
                        SettingLinkRow(
                            title = "解除コードを変更",
                            summary = "現在のコードを確認してから新しいコードを設定します",
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
                Section("ロック動作") {
                    ElevatedPanel {
                        // 3. Auto-lock timing selection (Req 6.4–6.6)
                        SettingDropdownRow(
                            title = "自動ロック",
                            summary = "バックグラウンド移行時のロックタイミング",
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
                Section("プライバシー") {
                    ElevatedPanel {
                        // 4. Hide Recents toggle (Req 7.1–7.4)
                        SettingSwitchRow(
                            title = "最近使ったアプリ画面を隠す",
                            checked = state.settings.hideRecents,
                            onCheckedChange = { viewModel.updateHideRecents(it) },
                            summary = "タスク切り替え画面に電卓のサムネイルを表示します",
                        )
                        DividerLine()
                        // 5. Hide Notifications toggle (Req 9.1–9.4)
                        SettingSwitchRow(
                            title = "通知内容を隠す",
                            checked = state.settings.hideNotifications,
                            onCheckedChange = { viewModel.updateHideNotifications(it) },
                            summary = "通知のタイトル・本文を汎用テキストに置き換えます",
                        )
                    }
                }
            }

            // ── Section 5: Disguise ────────────────────────────────────────────
            item {
                Section("ダミーアプリ設定") {
                    ElevatedPanel {
                        // 6. Dummy app name text input (Req 10.1, 10.3)
                        SettingLinkRow(
                            title = "ダミーアプリ名",
                            summary = state.settings.dummyAppName.ifBlank { "（未設定）" },
                            onClick = {
                                dummyNameDraft = state.settings.dummyAppName
                                dummyNameError = null
                                showDummyNameDialog = true
                            },
                        )
                        DividerLine()
                        // 7. Dummy icon variant selector (Req 10.2, 10.4)
                        SettingDropdownRow(
                            title = "ダミーアイコン",
                            summary = "ランチャーに表示するアイコンを選択します",
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

    // ── Change Unlock Code Dialog ─────────────────────────────────────────────
    // Requirements: 5.3, 5.4, 5.7
    if (showChangeCodeDialog) {
        OverlayDialog(
            show = true,
            title = "解除コードを変更",
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
                    label = "現在の解除コード",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
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
                    label = "新しい解除コード（4〜20文字）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done,
                    ),
                )

                // Error message (Req 5.7)
                if (changeCodeError != null) {
                    Text(
                        text = changeCodeError!!,
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
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text("キャンセル")
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
                                changeCodeError = "コードの変更に失敗しました。現在のコードが正しいこと、" +
                                    "新しいコードが4〜20文字の有効な形式であることを確認してください。"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text("変更する", fontWeight = FontWeight.Bold)
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
            title = "ダミーアプリ名を変更",
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
                    label = "アプリ名（1〜30文字）",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done,
                    ),
                )

                // Error message
                if (dummyNameError != null) {
                    Text(
                        text = dummyNameError!!,
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
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text("キャンセル")
                    }
                    Button(
                        onClick = {
                            val trimmed = dummyNameDraft.trim()
                            if (trimmed.isBlank()) {
                                dummyNameError = "アプリ名を空にすることはできません。"
                            } else {
                                viewModel.updateDummyAppName(trimmed)
                                showDummyNameDialog = false
                                dummyNameDraft = ""
                                dummyNameError = null
                            }
                        },
                        modifier = Modifier.weight(1f),
                        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    ) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
