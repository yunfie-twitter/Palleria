package com.yunfie.illustia.updater

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yunfie.illustia.R
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AppUpdateDownloadWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val releaseJson =
                inputData.getString(EXTRA_RELEASE_INFO)
                    ?: return@withContext Result.failure()
            val release =
                runCatching { Json.decodeFromString<AppReleaseInfo>(releaseJson) }.getOrNull()
                    ?: return@withContext Result.failure()

            val updater = AppUpdaterRepository(context)

            setForeground(createForegroundInfo(release))

            val downloadResult =
                updater.downloadApk(release) { progress, downloaded, total ->
                    AppUpdateNotificationHelper.showDownloadProgress(
                        context = context,
                        release = release,
                        progress = (progress * 100).toInt(),
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        cancelPendingIntent = WorkManager.getInstance(context).createCancelPendingIntent(id),
                    )
                }

            val file = downloadResult.getOrNull()
            if (file != null) {
                AppUpdateNotificationHelper.cancelDownloadProgress(context)
                installDownloadedUpdate(updater, release, file)
                Result.success()
            } else {
                AppUpdateNotificationHelper.showDownloadFailed(context, release)
                Result.failure()
            }
        }

    private suspend fun installDownloadedUpdate(
        updater: AppUpdaterRepository,
        release: AppReleaseInfo,
        file: File,
    ) {
        val store = SettingsStore(context)
        val settings = store.read()
        val method = UpdateInstallMethod.fromValue(settings.updateInstallMethod)
        val shouldInstallViaShizuku =
            method == UpdateInstallMethod.SHIZUKU &&
                updater.isShizukuAvailable() &&
                updater.isShizukuPermissionGranted()

        if (shouldInstallViaShizuku) {
            val installResult = updater.installApk(file, UpdateInstallMethod.SHIZUKU)
            if (installResult.isSuccess) {
                AppUpdateNotificationHelper.showUpdateInstalled(context, release)
                return
            }
        }
        AppUpdateNotificationHelper.showUpdateDownloaded(context, release, file)
    }

    private fun createForegroundInfo(release: AppReleaseInfo): ForegroundInfo {
        AppUpdateNotificationHelper.createNotificationChannel(context)
        val notification =
            NotificationCompat
                .Builder(context, AppUpdateNotificationHelper.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.update_download_progress_title, release.versionName))
                .setContentText("Starting download...")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    companion object {
        const val EXTRA_RELEASE_INFO = "extra_release_info"
        private const val NOTIFICATION_ID_PROGRESS = 8104
        const val WORK_NAME = "AppUpdateDownload"

        fun start(
            context: Context,
            release: AppReleaseInfo,
        ) {
            val workRequest =
                OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
                    .setInputData(workDataOf(EXTRA_RELEASE_INFO to Json.encodeToString(release)))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }
    }
}
