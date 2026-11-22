package com.yunfie.illustia.updater

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.SettingsStore

class UpdateCheckWorker(
    private val context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result {
        val appContext = context.applicationContext
        val store = SettingsStore(appContext)
        val settings = store.read()

        if (settings.checkUpdatesInBackground && (settings.notifyNewVersion || settings.autoDownloadUpdates)) {
            val updater = AppUpdaterRepository(appContext)
            val releaseResult = updater.fetchLatestRelease(includePrerelease = settings.includePrereleaseUpdates)
            val release = releaseResult.getOrNull() ?: return Result.retry()

            if (updater.isNewerVersion(release.versionName)) {
                processUpdate(appContext, settings, release)
            }
        }
        return Result.success()
    }

    private fun processUpdate(
        context: Context,
        settings: AppSettings,
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

        AppUpdateDownloadWorker.start(context, release)
    }

    private fun isConnectedToWifi(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        return caps?.let {
            val isWifiOrEthernet =
                it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val isUnmetered = it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            isWifiOrEthernet && isUnmetered
        } ?: false
    }
}
