package com.yunfie.illustia.updater

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AppUpdateDownloadService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var isDownloading = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_DOWNLOAD) {
            cancelDownload()
            return START_NOT_STICKY
        }

        val releaseJson = intent?.getStringExtra(EXTRA_RELEASE_INFO) ?: return START_NOT_STICKY
        val release = runCatching { Json.decodeFromString<AppReleaseInfo>(releaseJson) }.getOrNull() ?: return START_NOT_STICKY

        if (!isDownloading) {
            isDownloading = true
            startDownload(release)
        }

        return START_NOT_STICKY
    }

    private fun startDownload(release: AppReleaseInfo) {
        val updater = AppUpdaterRepository(this)

        // Start foreground immediately
        AppUpdateNotificationHelper.createNotificationChannel(this)
        val notification = NotificationCompat.Builder(this, AppUpdateNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.update_download_progress_title, release.versionName))
            .setContentText("Starting download...")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID_PROGRESS, notification, 0)
        }

        serviceScope.launch {
            val downloadResult = updater.downloadApk(release) { progress, downloaded, total ->
                AppUpdateNotificationHelper.showDownloadProgress(
                    context = this@AppUpdateDownloadService,
                    release = release,
                    progress = (progress * 100).toInt(),
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    cancelPendingIntent = null // We can add a cancel intent if needed
                )
            }

            val file = downloadResult.getOrNull()
            if (file != null) {
                AppUpdateNotificationHelper.cancelDownloadProgress(this@AppUpdateDownloadService)
                installDownloadedUpdate(updater, release, file)
            } else {
                AppUpdateNotificationHelper.showDownloadFailed(this@AppUpdateDownloadService, release)
            }

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            isDownloading = false
        }
    }

    private suspend fun installDownloadedUpdate(
        updater: AppUpdaterRepository,
        release: AppReleaseInfo,
        file: File
    ) {
        val store = SettingsStore(this)
        val settings = store.read()
        val method = UpdateInstallMethod.fromValue(settings.updateInstallMethod)
        val shouldInstallViaShizuku =
            method == UpdateInstallMethod.SHIZUKU &&
                updater.isShizukuAvailable() &&
                updater.isShizukuPermissionGranted()

        if (shouldInstallViaShizuku) {
            val installResult = updater.installApk(file, UpdateInstallMethod.SHIZUKU)
            if (installResult.isSuccess) {
                AppUpdateNotificationHelper.showUpdateInstalled(this, release)
                return
            }
        }
        AppUpdateNotificationHelper.showUpdateDownloaded(this, release, file)
    }

    private fun cancelDownload() {
        AppUpdaterRepository(this).cancelDownload()
        AppUpdateNotificationHelper.cancelDownloadProgress(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        isDownloading = false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_CANCEL_DOWNLOAD = "com.yunfie.illustia.updater.action.CANCEL_DOWNLOAD"
        const val EXTRA_RELEASE_INFO = "extra_release_info"
        private const val NOTIFICATION_ID_PROGRESS = 8104

        fun start(context: Context, release: AppReleaseInfo) {
            val intent = Intent(context, AppUpdateDownloadService::class.java).apply {
                putExtra(EXTRA_RELEASE_INFO, Json.encodeToString(release))
            }
            context.startService(intent)
        }
    }
}
