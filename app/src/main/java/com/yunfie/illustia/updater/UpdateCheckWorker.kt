package com.yunfie.illustia.updater

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore
import java.io.File

class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val appContext = context.applicationContext
        val store = SettingsStore(appContext)
        val settings = store.read()

        if (settings.notifyNewVersion || settings.autoDownloadUpdates) {
            val updater = AppUpdaterRepository(appContext)
            val releaseResult = updater.fetchLatestRelease(includePrerelease = settings.includePrereleaseUpdates)
            val release = releaseResult.getOrNull() ?: return Result.retry()

            if (updater.isNewerVersion(release.versionName)) {
                processUpdate(appContext, settings, updater, release)
            }
        }
        return Result.success()
    }

    private suspend fun processUpdate(
        context: Context,
        settings: AppSettings,
        updater: AppUpdaterRepository,
        release: AppReleaseInfo,
    ) {
        val isWifi = isConnectedToWifi(context)
        val canAutoDownload = settings.autoDownloadUpdates && (!settings.autoDownloadWifiOnly || isWifi)

        if (!canAutoDownload) {
            if (settings.notifyNewVersion) {
                AppUpdateNotificationHelper.showNewVersionAvailable(context, release)
            }
            return
        }

        val downloadResult = updater.downloadApk(release) { _, _, _ -> }
        val file = downloadResult.getOrNull()
        if (file != null) {
            installDownloadedUpdate(context, settings, updater, release, file)
        } else if (settings.notifyNewVersion) {
            AppUpdateNotificationHelper.showNewVersionAvailable(context, release)
        }
    }

    private suspend fun installDownloadedUpdate(
        context: Context,
        settings: AppSettings,
        updater: AppUpdaterRepository,
        release: AppReleaseInfo,
        file: File,
    ) {
        val method = UpdateInstallMethod.fromValue(settings.updateInstallMethod)
        val shouldInstallViaShizuku =
            method == UpdateInstallMethod.SHIZUKU &&
                updater.isShizukuAvailable() &&
                updater.isShizukuPermissionGranted()

        if (shouldInstallViaShizuku) {
            val installResult = updater.installApk(file, UpdateInstallMethod.SHIZUKU)
            if (installResult.isSuccess) {
                if (settings.notifyNewVersion) {
                    AppUpdateNotificationHelper.showUpdateInstalled(context, release)
                }
                return
            }
        }
        if (settings.notifyNewVersion) {
            AppUpdateNotificationHelper.showUpdateDownloaded(context, release, file)
        }
    }

    private fun isConnectedToWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return caps?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } ?: false
    }
}
