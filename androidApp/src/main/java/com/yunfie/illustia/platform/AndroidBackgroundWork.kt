package com.yunfie.illustia.platform

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.yunfie.illustia.updater.AppUpdateDownloadWorker
import kotlinx.coroutines.flow.Flow

class AndroidBackgroundWork(
    private val context: Context,
) : BackgroundWork {
    override fun appUpdateWorkInfos(): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(AppUpdateDownloadWorker.WORK_NAME)

    override fun cancelAppUpdateWork() {
        WorkManager.getInstance(context).cancelUniqueWork(AppUpdateDownloadWorker.WORK_NAME)
    }
}
