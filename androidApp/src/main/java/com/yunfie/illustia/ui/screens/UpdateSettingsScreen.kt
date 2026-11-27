package com.yunfie.illustia.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.platform.AppStrings
import com.yunfie.illustia.platform.StringKey
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.updater.UpdateInstallMethod
import rikka.shizuku.Shizuku
import com.yunfie.illustia.ui.screens.UpdateSettingsScreen as SharedUpdateSettingsScreen

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

@Composable
fun UpdateSettingsScreen(
    settings: AppSettings,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val updater = viewModel.appUpdaterRepository
    var shizukuState by remember { mutableStateOf(ShizukuInstallState()) }

    fun refreshShizuku() {
        shizukuState =
            ShizukuInstallState(
                available = runCatching { updater.isShizukuAvailable() }.getOrDefault(false),
                permissionGranted = runCatching { updater.isShizukuPermissionGranted() }.getOrDefault(false),
            )
    }

    LifecycleResumeEffect(Unit) {
        refreshShizuku()
        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        refreshShizuku()
    }

    val actions =
        remember(viewModel, context) {
            object : UpdateSettingsActions {
                override fun updateInstallMethod(value: String) =
                    viewModel.updateUpdateInstallMethod(
                        if (value == "shizuku") UpdateInstallMethod.SHIZUKU else UpdateInstallMethod.STANDARD_APK,
                    )

                override fun updateIncludePrereleaseUpdates(enabled: Boolean) = viewModel.updateIncludePrereleaseUpdates(enabled)

                override fun updateNotifyNewVersion(enabled: Boolean) = viewModel.updateNotifyNewVersion(enabled)

                override fun updateCheckUpdatesInBackground(enabled: Boolean) = viewModel.updateCheckUpdatesInBackground(enabled)

                override fun updateAutoDownloadUpdates(enabled: Boolean) = viewModel.updateAutoDownloadUpdates(enabled)

                override fun updateAutoDownloadWifiOnly(enabled: Boolean) = viewModel.updateAutoDownloadWifiOnly(enabled)

                override fun updateAutoCheckUpdateOnStartup(enabled: Boolean) = viewModel.updateAutoCheckUpdateOnStartup(enabled)

                override fun requestShizukuPermission() = runCatching { updater.requestShizukuPermission() }.let {}

                override fun openShizukuApp() {
                    runCatching {
                        val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                        if (intent != null) {
                            context.startActivity(intent)
                        } else {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU_PACKAGE")),
                            )
                        }
                    }
                }

                override fun installMethodOptions(): List<String> =
                    listOf(
                        AppStrings.get(StringKey.UpdateMethodStandard),
                        AppStrings.get(StringKey.UpdateMethodShizuku),
                    )
            }
        }

    SharedUpdateSettingsScreen(
        settings = settings,
        shizukuState = shizukuState,
        actions = actions,
        onBack = onBack,
    )
}
