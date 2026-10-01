package com.yunfie.illustia.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R
import java.util.concurrent.atomic.AtomicBoolean

/** Keeps queued and running artwork saves foreground while the activity is minimized. */
class ArtworkDownloadService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.download_queue_title), NotificationManager.IMPORTANCE_LOW),
            )
        }
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.download_queue_title))
                .setContentText(getString(R.string.download_queue_downloading))
                .setContentIntent(openApp)
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:artwork-download")
                .apply { acquire(MAX_WAKE_MILLIS) }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        synchronized(Companion) {
            if (activeSaves == 0) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(
        startId: Int,
        fgsType: Int,
    ) {
        // Android 15's dataSync quota must not cause an ANR.
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "artwork_downloads"
        private const val NOTIFICATION_ID = 4205
        private const val MAX_WAKE_MILLIS = 6 * 60 * 60 * 1000L
        private var activeSaves = 0

        @Synchronized
        fun acquire(context: Context): AutoCloseable {
            val app = context.applicationContext
            if (activeSaves == 0) {
                ContextCompat.startForegroundService(app, Intent(app, ArtworkDownloadService::class.java))
            }
            activeSaves++
            val released = AtomicBoolean(false)
            return AutoCloseable {
                synchronized(this) {
                    if (released.compareAndSet(false, true)) {
                        activeSaves--
                        if (activeSaves == 0) app.stopService(Intent(app, ArtworkDownloadService::class.java))
                    }
                }
            }
        }
    }
}
