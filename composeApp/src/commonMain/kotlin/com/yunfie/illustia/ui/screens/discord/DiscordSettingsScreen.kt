package com.yunfie.illustia.ui.screens.discord

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.account_status
import com.yunfie.illustia.resources.action_cancel
import com.yunfie.illustia.resources.action_confirm
import com.yunfie.illustia.resources.action_reset
import com.yunfie.illustia.resources.discord_application_id
import com.yunfie.illustia.resources.discord_application_id_desc
import com.yunfie.illustia.resources.discord_application_id_dialog_title
import com.yunfie.illustia.resources.discord_enable_log
import com.yunfie.illustia.resources.discord_enable_log_desc
import com.yunfie.illustia.resources.discord_enable_rpc
import com.yunfie.illustia.resources.discord_enable_rpc_desc
import com.yunfie.illustia.resources.discord_log_clear
import com.yunfie.illustia.resources.discord_log_empty
import com.yunfie.illustia.resources.discord_login_manual_token
import com.yunfie.illustia.resources.discord_login_with_discord
import com.yunfie.illustia.resources.discord_login_with_discord_desc
import com.yunfie.illustia.resources.discord_section_advanced
import com.yunfie.illustia.resources.discord_section_connection
import com.yunfie.illustia.resources.discord_section_display
import com.yunfie.illustia.resources.discord_section_log
import com.yunfie.illustia.resources.discord_settings_title
import com.yunfie.illustia.resources.discord_show_artwork_details
import com.yunfie.illustia.resources.discord_show_artwork_details_desc
import com.yunfie.illustia.resources.discord_show_buttons
import com.yunfie.illustia.resources.discord_show_buttons_desc
import com.yunfie.illustia.resources.discord_status_connected
import com.yunfie.illustia.resources.discord_status_disconnected
import com.yunfie.illustia.resources.discord_status_no_token
import com.yunfie.illustia.resources.discord_token_desc
import com.yunfie.illustia.resources.discord_token_dialog_title
import com.yunfie.illustia.resources.discord_token_hide
import com.yunfie.illustia.resources.discord_token_hint
import com.yunfie.illustia.resources.discord_token_show
import com.yunfie.illustia.resources.discord_unsupported_version
import com.yunfie.illustia.resources.discord_warning_desc
import com.yunfie.illustia.resources.discord_warning_title
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.AppDialog
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.jetbrains.compose.resources.stringResource as resString

@Composable
fun DiscordSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    platform: DiscordRpcPlatform,
    onBack: () -> Unit,
    onOpenDiscordLogin: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    BackNavigationHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val isSupported = platform.isSupported()
    val diagnostics by platform.diagnostics.collectAsState()

    var showTokenDialog by remember { mutableStateOf(false) }
    var tempToken by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }

    var showAppIdDialog by remember { mutableStateOf(false) }
    var tempAppId by remember { mutableStateOf("") }

    if (showTokenDialog) {
        AppDialog(
            show = true,
            title = stringResource(Res.string.discord_token_dialog_title),
            summary = stringResource(Res.string.discord_token_desc),
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
                    label = stringResource(Res.string.discord_token_hint),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (tokenVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { tokenVisible = !tokenVisible }) {
                            Icon(
                                imageVector = if (tokenVisible) MiuixIcons.Hide else MiuixIcons.Show,
                                contentDescription =
                                    stringResource(
                                        if (tokenVisible) Res.string.discord_token_hide else Res.string.discord_token_show,
                                    ),
                            )
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { showTokenDialog = false },
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.action_cancel),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        onClick = {
                            viewModel.updateDiscordToken(tempToken.trim())
                            showTokenDialog = false
                            onShowMessage(AppStrings.get(StringKey.ActionSave))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        text = stringResource(Res.string.action_confirm),
                    )
                }
            }
        }
    }

    if (showAppIdDialog) {
        AppDialog(
            show = true,
            title = stringResource(Res.string.discord_application_id_dialog_title),
            summary = stringResource(Res.string.discord_application_id_desc),
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
                    label = stringResource(Res.string.discord_application_id),
                    useLabelAsPlaceholder = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = {
                            tempAppId = platform.defaultApplicationId
                            viewModel.updateDiscordApplicationId(platform.defaultApplicationId)
                            showAppIdDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        text = resString(Res.string.action_reset),
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = { showAppIdDialog = false },
                        modifier = Modifier.weight(1f),
                        text = stringResource(Res.string.action_cancel),
                    )
                    Spacer(Modifier.width(12.dp))
                    TextButton(
                        onClick = {
                            val id = tempAppId.trim().ifBlank { platform.defaultApplicationId }
                            viewModel.updateDiscordApplicationId(id)
                            showAppIdDialog = false
                            onShowMessage(AppStrings.get(StringKey.ActionSave))
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        text = stringResource(Res.string.action_confirm),
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.discord_settings_title),
                largeTitle = stringResource(Res.string.discord_settings_title),
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
                                text = stringResource(Res.string.discord_unsupported_version),
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
                            text = stringResource(Res.string.discord_warning_title),
                            style = MiuixTheme.textStyles.headline1,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(Res.string.discord_warning_desc),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.discord_section_connection)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.discord_enable_rpc),
                            summary = stringResource(Res.string.discord_enable_rpc_desc),
                            checked = isSupported && settings.discordRpcEnabled,
                            onCheckedChange = {
                                if (isSupported) {
                                    viewModel.updateDiscordRpcEnabled(it)
                                }
                            },
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(Res.string.discord_login_with_discord),
                            summary = stringResource(Res.string.discord_login_with_discord_desc),
                            onClick = onOpenDiscordLogin,
                        )
                        DividerLine()
                        SettingLinkRow(
                            title = stringResource(Res.string.discord_login_manual_token),
                            summary =
                                if (settings.discordToken.isNotBlank()) {
                                    "••••••••••••••••"
                                } else {
                                    stringResource(Res.string.discord_status_no_token)
                                },
                            onClick = {
                                tempToken = settings.discordToken
                                tokenVisible = false
                                showTokenDialog = true
                            },
                        )
                        DividerLine()
                        SettingRow(stringResource(Res.string.account_status)) {
                            val isConnected = isSupported && settings.discordRpcEnabled && settings.discordToken.isNotBlank()
                            val statusText =
                                when {
                                    !isSupported -> stringResource(Res.string.discord_unsupported_version)
                                    settings.discordToken.isBlank() -> stringResource(Res.string.discord_status_no_token)
                                    isConnected -> stringResource(Res.string.discord_status_connected)
                                    else -> stringResource(Res.string.discord_status_disconnected)
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
                Section(stringResource(Res.string.discord_section_display)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.discord_show_artwork_details),
                            summary = stringResource(Res.string.discord_show_artwork_details_desc),
                            checked = settings.discordRpcShowArtworkDetails,
                            onCheckedChange = { viewModel.updateDiscordRpcShowArtworkDetails(it) },
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.discord_show_buttons),
                            summary = stringResource(Res.string.discord_show_buttons_desc),
                            checked = settings.discordRpcShowButtons,
                            onCheckedChange = { viewModel.updateDiscordRpcShowButtons(it) },
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.discord_section_advanced)) {
                    ElevatedPanel {
                        SettingLinkRow(
                            title = stringResource(Res.string.discord_application_id),
                            summary = settings.discordApplicationId.ifBlank { platform.defaultApplicationId },
                            onClick = {
                                tempAppId = settings.discordApplicationId
                                showAppIdDialog = true
                            },
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.discord_section_log)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.discord_enable_log),
                            summary = stringResource(Res.string.discord_enable_log_desc),
                            checked = settings.discordRpcShowLogs,
                            onCheckedChange = { viewModel.updateDiscordRpcShowLogs(it) },
                        )
                        if (settings.discordRpcShowLogs) {
                            DividerLine()
                            if (diagnostics.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.discord_log_empty),
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
                                text = stringResource(Res.string.discord_log_clear),
                                onClick = platform::clearDiagnostics,
                            )
                        }
                    }
                }
            }
        }
    }
}
