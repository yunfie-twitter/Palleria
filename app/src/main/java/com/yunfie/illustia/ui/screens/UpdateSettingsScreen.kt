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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.SettingSwitchRow
import com.yunfie.illustia.updater.UpdateInstallMethod
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
    state: IllustiaUiState,
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
                listOf(context.getString(R.string.update_method_standard))
            } else {
                listOf(
                    context.getString(R.string.update_method_standard),
                    context.getString(R.string.update_method_shizuku),
                )
            }
        }
    val selectedInstallIndex =
        if (!isDesktop && state.settings.updateInstallMethod == UpdateInstallMethod.SHIZUKU.value) 1 else 0

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.update_settings_title),
                largeTitle = stringResource(R.string.update_settings_title),
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
                Section(stringResource(R.string.update_section_install)) {
                    ElevatedPanel {
                        if (!isDesktop) {
                            OverlayDropdownPreference(
                                title = stringResource(R.string.update_install_method),
                                summary =
                                    when (selectedInstallIndex) {
                                        1 -> stringResource(R.string.update_method_shizuku_desc)
                                        else -> stringResource(R.string.update_method_standard_desc)
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
                                        !isShizukuAvailable -> stringResource(R.string.update_shizuku_not_running)
                                        !isShizukuGranted -> stringResource(R.string.update_shizuku_permission_required)
                                        else -> stringResource(R.string.update_shizuku_available)
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
                                        Text(stringResource(R.string.update_shizuku_open))
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
                                        Text(stringResource(R.string.update_shizuku_grant), color = MiuixTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }

                        DividerLine()
                        SettingSwitchRow(
                            title = stringResource(R.string.update_auto_check_startup),
                            summary = stringResource(R.string.update_auto_check_startup_desc),
                            checked = state.settings.autoCheckUpdateOnStartup,
                            onCheckedChange = viewModel::updateAutoCheckUpdateOnStartup,
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.about_section_links)) {
                    ElevatedPanel {
                        SettingLinkRow(stringResource(R.string.update_view_on_github)) {
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
