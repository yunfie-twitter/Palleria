package com.yunfie.illustia.updater

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class AppUpdateDownloadWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    @Suppress("LongMethod")
    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val releaseJson =
                inputData.getString(EXTRA_RELEASE_INFO)
                    ?: return@withContext Result.failure()
            val release =
                runCatching { Json.decodeFromString<AppReleaseInfo>(releaseJson) }.getOrNull()
                    ?: return@withContext Result.failure()

            val updater = AppUpdaterRepository(context)

            // Guard against infinite update loop: If the app is already on this version or newer
            // (e.g. WorkManager resumed worker after Shizuku killed process during installation), exit immediately.
            val currentVersion = updater.getCurrentVersionName()
            if (!updater.isNewerVersion(release.versionName, currentVersion)) {
                updater.cleanUpdateApks()
                AppUpdateNotificationHelper.cancelDownloadProgress(context)
                _progressFlow.value = null
                return@withContext Result.success()
            }

            setForeground(createForegroundInfo(release))

            try {
                _progressFlow.value = DownloadProgressEvent.Progress(0f, 0L, release.apkSize)
                setProgress(
                    workDataOf(
                        KEY_PROGRESS to 0f,
                        KEY_DOWNLOADED_BYTES to 0L,
                        KEY_TOTAL_BYTES to release.apkSize,
                    ),
                )
                val downloadResult =
                    updater.downloadApk(release) { progress, downloaded, total ->
                        _progressFlow.value = DownloadProgressEvent.Progress(progress, downloaded, total)
                        setProgressAsync(
                            workDataOf(
                                KEY_PROGRESS to progress,
                                KEY_DOWNLOADED_BYTES to downloaded,
                                KEY_TOTAL_BYTES to total,
                            ),
                        )
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
                    _progressFlow.value = DownloadProgressEvent.Completed(file, release)
                    AppUpdateNotificationHelper.cancelDownloadProgress(context)
                    installDownloadedUpdate(updater, release, file)
                    Result.success()
                } else {
                    val error = downloadResult.exceptionOrNull()
                    val errorMsg = error?.message ?: "Download failed"
                    _progressFlow.value = DownloadProgressEvent.Failed(errorMsg, release)
                    AppUpdateNotificationHelper.showDownloadFailed(context, release)
                    if (isRetryableDownloadFailure(error) && runAttemptCount < MAX_DOWNLOAD_RETRY_ATTEMPTS) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                updater.cancelDownload()
                _progressFlow.value = null
                throw cancellation
            } finally {
                if (isStopped) {
                    updater.cancelDownload()
                    _progressFlow.value = null
                }
            }
        }

    private fun isRetryableDownloadFailure(error: Throwable?): Boolean =
        when (error) {
            is SecurityException, is IllegalArgumentException -> {
                false
            }

            is java.io.IOException -> {
                val statusCode =
                    Regex("HTTP (\\d{3})")
                        .find(error.message.orEmpty())
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull()
                statusCode == null ||
                    statusCode == HTTP_REQUEST_TIMEOUT ||
                    statusCode == HTTP_TOO_MANY_REQUESTS ||
                    statusCode >= HTTP_SERVER_ERROR
            }

            else -> {
                true
            }
        }

    private suspend fun installDownloadedUpdate(
        updater: AppUpdaterRepository,
        release: AppReleaseInfo,
        file: File,
    ) {
        val currentVersion = updater.getCurrentVersionName()
        if (!updater.isNewerVersion(release.versionName, currentVersion)) {
            updater.cleanUpdateApks()
            return
        }

        val store = SettingsStore(context)
        val settings = store.read()
        val method = UpdateInstallMethod.fromValue(settings.updateInstallMethod)
        val shouldInstallViaShizuku =
            method == UpdateInstallMethod.SHIZUKU &&
                updater.isShizukuAvailable() &&
                updater.isShizukuPermissionGranted()

        if (shouldInstallViaShizuku) {
            // Cancel unique work so that if the process is killed by Shizuku installation,
            // WorkManager will not re-trigger this worker indefinitely.
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            AppUpdateNotificationHelper.showInstalling(context, release)
            val installResult = updater.installApk(file, UpdateInstallMethod.SHIZUKU)
            if (installResult.isSuccess) {
                AppUpdateNotificationHelper.showUpdateInstalled(context, release)
                updater.cleanUpdateApks()
                return
            }
        }
        AppUpdateNotificationHelper.showUpdateDownloaded(context, release, file)
    }

    private fun createForegroundInfo(release: AppReleaseInfo): ForegroundInfo {
        val notification =
            AppUpdateNotificationHelper.buildProgressNotification(
                context = context,
                release = release,
                progress = 0,
                downloadedBytes = 0L,
                totalBytes = release.apkSize,
                cancelPendingIntent = WorkManager.getInstance(context).createCancelPendingIntent(id),
            )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                AppUpdateNotificationHelper.NOTIFICATION_ID_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(AppUpdateNotificationHelper.NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    companion object {
        const val EXTRA_RELEASE_INFO = "extra_release_info"
        const val KEY_PROGRESS = "progress"
        const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
        private const val NOTIFICATION_ID_PROGRESS = 8104
        private const val MAX_DOWNLOAD_RETRY_ATTEMPTS = 3
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
        const val WORK_NAME = "AppUpdateDownload"

        private val _progressFlow = MutableStateFlow<DownloadProgressEvent?>(null)
        val progressFlow: StateFlow<DownloadProgressEvent?> = _progressFlow.asStateFlow()

        fun start(
            context: Context,
            release: AppReleaseInfo,
        ) {
            _progressFlow.value = DownloadProgressEvent.Progress(0f, 0L, release.apkSize)
            // WorkManager refuses input Data over 10240 bytes. The GitHub release body in
            // releaseNotes can easily exceed that, and the worker never reads it or the other
            // display-only fields, so ship only what the download and notification need.
            val workRelease =
                release.copy(
                    releaseNotes = "",
                    releaseTitle = "",
                    publishedAt = "",
                    htmlUrl = "",
                    versionCode = null,
                    isPrerelease = false,
                )
            val workRequest =
                OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
                    .setInputData(workDataOf(EXTRA_RELEASE_INFO to Json.encodeToString(workRelease)))
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            _progressFlow.value = null
        }
    }

    sealed interface DownloadProgressEvent {
        data class Progress(
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long,
        ) : DownloadProgressEvent

        data class Completed(
            val file: File,
            val release: AppReleaseInfo,
        ) : DownloadProgressEvent

        data class Failed(
            val error: String,
            val release: AppReleaseInfo,
        ) : DownloadProgressEvent
    }
}
