package com.yunfie.illustia.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.about_section_links
import com.yunfie.illustia.resources.update_auto_check_startup
import com.yunfie.illustia.resources.update_auto_check_startup_desc
import com.yunfie.illustia.resources.update_auto_download
import com.yunfie.illustia.resources.update_auto_download_desc
import com.yunfie.illustia.resources.update_auto_download_wifi_only
import com.yunfie.illustia.resources.update_auto_download_wifi_only_desc
import com.yunfie.illustia.resources.update_check_background
import com.yunfie.illustia.resources.update_check_background_desc
import com.yunfie.illustia.resources.update_include_prerelease
import com.yunfie.illustia.resources.update_include_prerelease_desc
import com.yunfie.illustia.resources.update_install_method
import com.yunfie.illustia.resources.update_method_shizuku_desc
import com.yunfie.illustia.resources.update_method_standard_desc
import com.yunfie.illustia.resources.update_notify_new_version
import com.yunfie.illustia.resources.update_notify_new_version_desc
import com.yunfie.illustia.resources.update_section_check_and_download
import com.yunfie.illustia.resources.update_section_install
import com.yunfie.illustia.resources.update_settings_title
import com.yunfie.illustia.resources.update_shizuku_available
import com.yunfie.illustia.resources.update_shizuku_grant
import com.yunfie.illustia.resources.update_shizuku_not_running
import com.yunfie.illustia.resources.update_shizuku_open
import com.yunfie.illustia.resources.update_shizuku_permission_required
import com.yunfie.illustia.resources.update_view_on_github
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.ui.components.BackNavigationHandler
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.ui.components.isRunningOnDesktop
import com.yunfie.illustia.ui.components.rememberUrlOpener
import com.yunfie.illustia.ui.screens.ShizukuInstallState
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateSettingsScreen(
    settings: AppSettings,
    shizukuState: ShizukuInstallState,
    actions: UpdateSettingsActions,
    onBack: () -> Unit,
) {
    BackNavigationHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val openUrl = rememberUrlOpener()
    val isShizukuAvailable = shizukuState.available
    val isShizukuGranted = shizukuState.permissionGranted

    val isDesktop = isRunningOnDesktop()
    val installMethods = remember(actions, isDesktop) { actions.installMethodOptions() }
    val selectedInstallIndex =
        if (!isDesktop && settings.updateInstallMethod == "shizuku") 1 else 0

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(Res.string.update_settings_title),
                largeTitle = stringResource(Res.string.update_settings_title),
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
            // インストール設定セクション
            item {
                Section(stringResource(Res.string.update_section_install)) {
                    ElevatedPanel {
                        if (!isDesktop) {
                            OverlayDropdownPreference(
                                title = stringResource(Res.string.update_install_method),
                                summary =
                                    when (selectedInstallIndex) {
                                        1 -> stringResource(Res.string.update_method_shizuku_desc)
                                        else -> stringResource(Res.string.update_method_standard_desc)
                                    },
                                items = installMethods,
                                selectedIndex = selectedInstallIndex,
                                onSelectedIndexChange = { index ->
                                    actions.updateInstallMethod(if (index == 1) "shizuku" else "standard_apk")
                                },
                            )
                        }

                        if (!isDesktop && selectedInstallIndex == 1) {
                            DividerLine()
                            SettingRow(
                                title = "Shizuku",
                                summary =
                                    when {
                                        !isShizukuAvailable -> stringResource(Res.string.update_shizuku_not_running)
                                        !isShizukuGranted -> stringResource(Res.string.update_shizuku_permission_required)
                                        else -> stringResource(Res.string.update_shizuku_available)
                                    },
                            ) {
                                if (!isShizukuAvailable) {
                                    Button(
                                        onClick = { actions.openShizukuApp() },
                                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text(stringResource(Res.string.update_shizuku_open))
                                    }
                                } else if (!isShizukuGranted) {
                                    Button(
                                        onClick = { actions.requestShizukuPermission() },
                                        colors = ButtonDefaults.buttonColorsPrimary(),
                                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text(stringResource(Res.string.update_shizuku_grant), color = MiuixTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Section(stringResource(Res.string.update_section_check_and_download)) {
                    ElevatedPanel {
                        SettingSwitchRow(
                            title = stringResource(Res.string.update_include_prerelease),
                            summary = stringResource(Res.string.update_include_prerelease_desc),
                            checked = settings.includePrereleaseUpdates,
                            onCheckedChange = actions::updateIncludePrereleaseUpdates,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.update_notify_new_version),
                            summary = stringResource(Res.string.update_notify_new_version_desc),
                            checked = settings.notifyNewVersion,
                            onCheckedChange = actions::updateNotifyNewVersion,
                        )
                        if (settings.notifyNewVersion) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.update_check_background),
                                summary = stringResource(Res.string.update_check_background_desc),
                                checked = settings.checkUpdatesInBackground,
                                onCheckedChange = actions::updateCheckUpdatesInBackground,
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.update_auto_download),
                            summary = stringResource(Res.string.update_auto_download_desc),
                            checked = settings.autoDownloadUpdates,
                            onCheckedChange = actions::updateAutoDownloadUpdates,
                        )
                        if (settings.autoDownloadUpdates) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.update_auto_download_wifi_only),
                                summary = stringResource(Res.string.update_auto_download_wifi_only_desc),
                                checked = settings.autoDownloadWifiOnly,
                                onCheckedChange = actions::updateAutoDownloadWifiOnly,
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.update_auto_check_startup),
                            summary = stringResource(Res.string.update_auto_check_startup_desc),
                            checked = settings.autoCheckUpdateOnStartup,
                            onCheckedChange = actions::updateAutoCheckUpdateOnStartup,
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.about_section_links)) {
                    ElevatedPanel {
                        SettingLinkRow(stringResource(Res.string.update_view_on_github)) {
                            openUrl("https://github.com/yunfie-twitter/Palleria/releases")
                        }
                    }
                }
            }
        }
    }
}
