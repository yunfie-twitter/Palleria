package com.yunfie.illustia.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
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
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.updater.UpdateInstallMethod
import org.jetbrains.compose.resources.stringResource
import rikka.shizuku.Shizuku
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
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    var isShizukuAvailable by remember { mutableStateOf(viewModel.appUpdaterRepository.isShizukuAvailable()) }
    var isShizukuGranted by remember { mutableStateOf(viewModel.appUpdaterRepository.isShizukuPermissionGranted()) }

    LifecycleResumeEffect(Unit) {
        isShizukuAvailable = viewModel.appUpdaterRepository.isShizukuAvailable()
        isShizukuGranted = viewModel.appUpdaterRepository.isShizukuPermissionGranted()
        onPauseOrDispose { }
    }

    DisposableEffect(Unit) {
        val permissionListener =
            Shizuku.OnRequestPermissionResultListener { _, grantResult ->
                isShizukuGranted = grantResult == PackageManager.PERMISSION_GRANTED
                isShizukuAvailable = viewModel.appUpdaterRepository.isShizukuAvailable()
            }
        val binderReceivedListener =
            Shizuku.OnBinderReceivedListener {
                isShizukuAvailable = true
                isShizukuGranted = viewModel.appUpdaterRepository.isShizukuPermissionGranted()
            }
        val binderDeadListener =
            Shizuku.OnBinderDeadListener {
                isShizukuAvailable = false
                isShizukuGranted = false
            }

        runCatching { Shizuku.addRequestPermissionResultListener(permissionListener) }
        runCatching { Shizuku.addBinderReceivedListenerSticky(binderReceivedListener) }
        runCatching { Shizuku.addBinderDeadListener(binderDeadListener) }

        onDispose {
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionListener) }
            runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
        }
    }

    val isDesktop =
        remember(context) {
            com.yunfie.illustia.platform.DesktopEnvironment
                .isDesktop(context)
        }
    val installMethods =
        remember(isDesktop) {
            if (isDesktop) {
                listOf(AppStrings.get(StringKey.UpdateMethodStandard))
            } else {
                listOf(
                    AppStrings.get(StringKey.UpdateMethodStandard),
                    AppStrings.get(StringKey.UpdateMethodShizuku),
                )
            }
        }
    val selectedInstallIndex =
        if (!isDesktop && settings.updateInstallMethod == UpdateInstallMethod.SHIZUKU.value) 1 else 0

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
                                    val method =
                                        if (index == 1) UpdateInstallMethod.SHIZUKU else UpdateInstallMethod.STANDARD_APK
                                    viewModel.updateUpdateInstallMethod(method)
                                    isShizukuAvailable = viewModel.appUpdaterRepository.isShizukuAvailable()
                                    isShizukuGranted = viewModel.appUpdaterRepository.isShizukuPermissionGranted()
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
                                        onClick = {
                                            runCatching {
                                                val intent =
                                                    context.packageManager
                                                        .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                                if (intent != null) {
                                                    context.startActivity(intent)
                                                } else {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            Uri.parse("market://details?id=moe.shizuku.privileged.api"),
                                                        ),
                                                    )
                                                }
                                            }
                                        },
                                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    ) {
                                        Text(stringResource(Res.string.update_shizuku_open))
                                    }
                                } else if (!isShizukuGranted) {
                                    Button(
                                        onClick = {
                                            isShizukuAvailable = viewModel.appUpdaterRepository.isShizukuAvailable()
                                            isShizukuGranted = viewModel.appUpdaterRepository.isShizukuPermissionGranted()
                                            viewModel.appUpdaterRepository.requestShizukuPermission()
                                        },
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
                            onCheckedChange = viewModel::updateIncludePrereleaseUpdates,
                        )
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.update_notify_new_version),
                            summary = stringResource(Res.string.update_notify_new_version_desc),
                            checked = settings.notifyNewVersion,
                            onCheckedChange = viewModel::updateNotifyNewVersion,
                        )
                        if (settings.notifyNewVersion) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.update_check_background),
                                summary = stringResource(Res.string.update_check_background_desc),
                                checked = settings.checkUpdatesInBackground,
                                onCheckedChange = viewModel::updateCheckUpdatesInBackground,
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.update_auto_download),
                            summary = stringResource(Res.string.update_auto_download_desc),
                            checked = settings.autoDownloadUpdates,
                            onCheckedChange = viewModel::updateAutoDownloadUpdates,
                        )
                        if (settings.autoDownloadUpdates) {
                            DividerLine()
                            SettingSwitchRow(
                                title = stringResource(Res.string.update_auto_download_wifi_only),
                                summary = stringResource(Res.string.update_auto_download_wifi_only_desc),
                                checked = settings.autoDownloadWifiOnly,
                                onCheckedChange = viewModel::updateAutoDownloadWifiOnly,
                            )
                        }
                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(Res.string.update_auto_check_startup),
                            summary = stringResource(Res.string.update_auto_check_startup_desc),
                            checked = settings.autoCheckUpdateOnStartup,
                            onCheckedChange = viewModel::updateAutoCheckUpdateOnStartup,
                        )
                    }
                }
            }

            item {
                Section(stringResource(Res.string.about_section_links)) {
                    ElevatedPanel {
                        SettingLinkRow(stringResource(Res.string.update_view_on_github)) {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/yunfie-twitter/Palleria/releases"),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
