package com.yunfie.illustia.platform

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yunfie.illustia.updater.AppReleaseInfo
import com.yunfie.illustia.updater.AppUpdateDownloadWorker
import com.yunfie.illustia.updater.AppUpdateScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AndroidBackgroundWork(
    private val context: Context,
) : BackgroundWork {
    override fun appUpdateProgress(): Flow<AppUpdateEvent?> =
        combine(
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkFlow(AppUpdateDownloadWorker.WORK_NAME)
                .map(::runningProgress),
            AppUpdateDownloadWorker.progressFlow.map { event -> event?.toAppUpdateEvent() },
        ) { running, event ->
            event ?: running
        }

    private fun runningProgress(workInfos: List<WorkInfo>): AppUpdateEvent? {
        val workInfo = workInfos.firstOrNull()
        val progress =
            if (workInfo?.state == WorkInfo.State.RUNNING) {
                workInfo.progress.getFloat(AppUpdateDownloadWorker.KEY_PROGRESS, -1f)
            } else {
                -1f
            }
        return if (workInfo != null && progress >= 0f) {
            AppUpdateEvent.Progress(
                progress = progress,
                downloadedBytes = workInfo.progress.getLong(AppUpdateDownloadWorker.KEY_DOWNLOADED_BYTES, 0L),
                totalBytes = workInfo.progress.getLong(AppUpdateDownloadWorker.KEY_TOTAL_BYTES, 0L),
            )
        } else {
            null
        }
    }

    override fun startAppUpdateDownload(release: AppReleaseInfo) {
        AppUpdateDownloadWorker.start(context.applicationContext, release)
    }

    override fun cancelAppUpdateWork() {
        WorkManager.getInstance(context).cancelUniqueWork(AppUpdateDownloadWorker.WORK_NAME)
    }

    override fun schedulePeriodicUpdateCheck() {
        AppUpdateScheduler.schedulePeriodicCheck(context.applicationContext)
    }

    override fun cancelPeriodicUpdateCheck() {
        AppUpdateScheduler.cancelPeriodicCheck(context.applicationContext)
    }
}

private fun AppUpdateDownloadWorker.DownloadProgressEvent.toAppUpdateEvent(): AppUpdateEvent =
    when (this) {
        is AppUpdateDownloadWorker.DownloadProgressEvent.Progress -> {
            AppUpdateEvent.Progress(progress, downloadedBytes, totalBytes)
        }

        is AppUpdateDownloadWorker.DownloadProgressEvent.Completed -> {
            AppUpdateEvent.Completed(apkPath, release)
        }

        is AppUpdateDownloadWorker.DownloadProgressEvent.Failed -> {
            AppUpdateEvent.Failed(error, release)
        }
    }
