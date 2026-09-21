package com.yunfie.illustia.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val store = SettingsStore(appContext)
                val settings = store.read()

                if (!settings.notifyNewVersion && !settings.autoDownloadUpdates) return@launch

                val updater = AppUpdaterRepository(appContext)
                val release =
                    updater
                        .fetchLatestRelease(includePrerelease = settings.includePrereleaseUpdates)
                        .getOrNull() ?: return@launch

                if (!updater.isNewerVersion(release.versionName)) return@launch

                processUpdate(appContext, settings, updater, release)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun processUpdate(
        context: Context,
        settings: AppSettings,
        updater: AppUpdaterRepository,
        release: AppReleaseInfo,
    ) {
        if (settings.autoDownloadUpdates) {
            val downloadResult = updater.downloadApk(release) { _, _, _ -> }
            val file = downloadResult.getOrNull() ?: return

            val method = UpdateInstallMethod.fromValue(settings.updateInstallMethod)
            if (method == UpdateInstallMethod.SHIZUKU &&
                updater.isShizukuAvailable() &&
                updater.isShizukuPermissionGranted()
            ) {
                val installResult = updater.installApk(file, UpdateInstallMethod.SHIZUKU)
                if (installResult.isSuccess) {
                    if (settings.notifyNewVersion) {
                        AppUpdateNotificationHelper.showUpdateInstalled(context, release)
                    }
                } else if (settings.notifyNewVersion) {
                    AppUpdateNotificationHelper.showUpdateDownloaded(context, release, file)
                }
            } else if (settings.notifyNewVersion) {
                AppUpdateNotificationHelper.showUpdateDownloaded(context, release, file)
            }
        } else if (settings.notifyNewVersion) {
            AppUpdateNotificationHelper.showNewVersionAvailable(context, release)
        }
    }
}
