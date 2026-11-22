package com.yunfie.illustia.updater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AppUpdateScheduler {
    const val ACTION_CHECK_UPDATES = "com.yunfie.illustia.updater.CHECK_UPDATES"
    private const val UNIQUE_WORK_NAME = "app_update_periodic_check"
    private const val REQUEST_CODE = 8100
    private const val REPEAT_INTERVAL_HOURS = 12L

    fun schedulePeriodicCheck(context: Context) {
        cancelLegacyAlarm(context)

        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        val workRequest =
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(REPEAT_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }

    fun cancelPeriodicCheck(context: Context) {
        cancelLegacyAlarm(context)
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun cancelLegacyAlarm(context: Context) {
        runCatching {
            val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
            val intent =
                Intent(ACTION_CHECK_UPDATES).setPackage(context.packageName)
            val operation =
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                )
            if (operation != null) {
                alarmManager.cancel(operation)
                operation.cancel()
            }
        }
    }
}
