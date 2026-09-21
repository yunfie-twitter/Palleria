package com.yunfie.illustia.updater

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.concurrent.TimeUnit

object AppUpdateScheduler {
    const val ACTION_CHECK_UPDATES = "com.yunfie.illustia.updater.CHECK_UPDATES"
    private const val REQUEST_CODE = 8100
    private val INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(12)

    fun schedulePeriodicCheck(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = createPendingIntent(context)
        alarmManager.cancel(operation)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + INTERVAL_MILLIS,
            INTERVAL_MILLIS,
            operation,
        )
    }

    fun cancelPeriodicCheck(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val operation = createPendingIntent(context)
        alarmManager.cancel(operation)
    }

    private fun createPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AppUpdateReceiver::class.java).setAction(ACTION_CHECK_UPDATES)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
