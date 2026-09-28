package com.yunfie.illustia.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.discord.DiscordRpcManager
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DiscordSettingsScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onOpenDiscordLogin: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val isSupported = DiscordRpcManager.isSupported(context)
    val diagnostics by DiscordRpcManager.diagnostics.collectAsStateWithLifecycle()

    var showTokenDialog by remember { mutableStateOf(false) }
    var tempToken by remember { mutableStateOf("") }

    var showAppIdDialog by remember { mutableStateOf(false) }
    var tempAppId by remember { mutableStateOf("") }

    if (showTokenDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.discord_token_dialog_title),
            summary = stringResource(R.string.discord_token_desc),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
            onDismissRequest = { showTokenDialog = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextField(
                    value = tempToken,
                    onValueChange = { tempToken = it },
                    label = stringResource(R.string.discord_token_hint),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { showTokenDialog = false },
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.action_cancel),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        onClick = {
                            viewModel.updateDiscordToken(tempToken.trim())
                            showTokenDialog = false
                            Toast.makeText(context, R.string.action_save, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        text = stringResource(R.string.action_confirm),
                    )
                }
            }
        }
    }

    if (showAppIdDialog) {
        OverlayDialog(
            show = true,
            title = stringResource(R.string.discord_application_id_dialog_title),
            summary = stringResource(R.string.discord_application_id_desc),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainerHighest,
            onDismissRequest = { showAppIdDialog = false },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                TextField(
                    value = tempAppId,
                    onValueChange = { tempAppId = it },
                    label = stringResource(R.string.discord_application_id),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            tempAppId = DiscordRpcManager.DEFAULT_APP_ID
                            viewModel.updateDiscordApplicationId(DiscordRpcManager.DEFAULT_APP_ID)
                            showAppIdDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.action_reset),
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = { showAppIdDialog = false },
                        modifier = Modifier.weight(1f),
                        text = stringResource(R.string.action_cancel),
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = {
                            val id = tempAppId.trim().ifBlank { DiscordRpcManager.DEFAULT_APP_ID }
                            viewModel.updateDiscordApplicationId(id)
                            showAppIdDialog = false
                            Toast.makeText(context, R.string.action_save, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        text = stringResource(R.string.action_confirm),
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.discord_settings_title),
                largeTitle = stringResource(R.string.discord_settings_title),
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
            if (!isSupported) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            CardDefaults.defaultColors(
                                color = MiuixTheme.colorScheme.errorContainer,
                            ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.discord_unsupported_version),
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer,
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.discord_warning_title),
                            style = MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.discord_warning_desc),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.discord_section_connection)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.discord_enable_rpc),
                            summary = stringResource(R.string.discord_enable_rpc_desc),
                            checked = isSupported && state.settings.discordRpcEnabled,
                            onCheckedChange = {
                                if (isSupported) {
                                    viewModel.updateDiscordRpcEnabled(it)
                                }
                            },
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(R.string.discord_login_with_discord),
                            summary = stringResource(R.string.discord_login_with_discord_desc),
                            onClick = onOpenDiscordLogin,
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(R.string.discord_login_manual_token),
                            summary =
                                if (state.settings.discordToken.isNotBlank()) {
                                    "••••••••••••••••"
                                } else {
                                    stringResource(R.string.discord_status_no_token)
                                },
                            onClick = {
                                tempToken = state.settings.discordToken
                                showTokenDialog = true
                            },
                        )
                        DividerLine()
                        SettingRow(stringResource(R.string.account_status)) {
                            val isConnected = isSupported && state.settings.discordRpcEnabled && state.settings.discordToken.isNotBlank()
                            val statusText =
                                when {
                                    !isSupported -> stringResource(R.string.discord_unsupported_version)
                                    state.settings.discordToken.isBlank() -> stringResource(R.string.discord_status_no_token)
                                    isConnected -> stringResource(R.string.discord_status_connected)
                                    else -> stringResource(R.string.discord_status_disconnected)
                                }
                            Text(
                                text = statusText,
                                color =
                                    if (isConnected) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.discord_section_display)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.discord_show_artwork_details),
                            summary = stringResource(R.string.discord_show_artwork_details_desc),
                            checked = state.settings.discordRpcShowArtworkDetails,
                            onCheckedChange = { viewModel.updateDiscordRpcShowArtworkDetails(it) },
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.discord_show_buttons),
                            summary = stringResource(R.string.discord_show_buttons_desc),
                            checked = state.settings.discordRpcShowButtons,
                            onCheckedChange = { viewModel.updateDiscordRpcShowButtons(it) },
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.discord_section_advanced)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(R.string.discord_application_id),
                            summary = state.settings.discordApplicationId.ifBlank { DiscordRpcManager.DEFAULT_APP_ID },
                            onClick = {
                                tempAppId = state.settings.discordApplicationId
                                showAppIdDialog = true
                            },
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.discord_section_log)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(R.string.discord_enable_log),
                            summary = stringResource(R.string.discord_enable_log_desc),
                            checked = state.settings.discordRpcShowLogs,
                            onCheckedChange = { viewModel.updateDiscordRpcShowLogs(it) },
                        )
                        if (state.settings.discordRpcShowLogs) {
                            DividerLine()
                            if (diagnostics.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.discord_log_empty),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            } else {
                                diagnostics.asReversed().forEach { entry ->
                                    Text(
                                        text = entry,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                            TextButton(
                                text = stringResource(R.string.discord_log_clear),
                                onClick = DiscordRpcManager::clearDiagnostics,
                            )
                        }
                    }
                }
            }
        }
    }
}
