package com.yunfie.illustia.updater

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R
import java.io.File
import java.util.Locale

@Suppress("TooManyFunctions")
object AppUpdateNotificationHelper {
    const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID_NEW_VERSION = 8101
    private const val NOTIFICATION_ID_DOWNLOADED = 8102
    private const val NOTIFICATION_ID_INSTALLED = 8103
    const val NOTIFICATION_ID_PROGRESS = 8104
    private const val NOTIFICATION_ID_FAILED = 8105

    private const val DEFAULT_ICON_SIZE_PX = 144
    private const val BYTES_PER_MB = 1024f * 1024f
    private const val BYTES_PER_KB = 1024.0
    private const val SPEED_SMOOTHING_PREV = 0.7
    private const val SPEED_SMOOTHING_INSTANT = 0.3
    private const val SECONDS_PER_MINUTE = 60
    private const val MS_PER_SECOND = 1000.0

    // Progress speed & ETA tracker state
    @Volatile
    private var lastBytes: Long = 0L

    @Volatile
    private var lastTimeMs: Long = 0L

    @Volatile
    private var smoothedSpeedBytesPerSec: Double = 0.0

    @Volatile
    private var cachedLargeIcon: Bitmap? = null

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_app_updates)
            val descriptionText = context.getString(R.string.notification_channel_app_updates_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun getLargeAppIcon(context: Context): Bitmap? {
        cachedLargeIcon?.let { return it }
        return runCatching {
            val drawable: Drawable = context.packageManager.getApplicationIcon(context.packageName)
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap.also { cachedLargeIcon = it }
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_ICON_SIZE_PX
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_ICON_SIZE_PX
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap.also { cachedLargeIcon = it }
            }
        }.getOrNull()
    }

    private fun baseNotificationBuilder(
        context: Context,
        title: String,
        text: String,
    ): NotificationCompat.Builder =
        NotificationCompat
            .Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .apply { getLargeAppIcon(context)?.let { setLargeIcon(it) } }
            .setContentTitle(title)
            .setContentText(text)

    fun showNewVersionAvailable(
        context: Context,
        release: AppReleaseInfo,
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_NEW_VERSION,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val title = context.getString(R.string.update_notification_new_version_title, release.versionName)
        val text = context.getString(R.string.update_notification_new_version_desc)

        val notification =
            baseNotificationBuilder(context, title, text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_NEW_VERSION, notification)
        }
    }

    fun showUpdateDownloaded(
        context: Context,
        release: AppReleaseInfo,
        apkFile: File,
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val installIntent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_DOWNLOADED,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val title = context.getString(R.string.update_notification_downloaded_title, release.versionName)
        val text = context.getString(R.string.update_notification_downloaded_desc)

        val notification =
            baseNotificationBuilder(context, title, text)
                .setSubText(context.getString(R.string.update_ready_to_install))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .addAction(
                    android.R.drawable.ic_menu_upload,
                    context.getString(R.string.update_install_button),
                    pendingIntent,
                ).build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DOWNLOADED, notification)
        }
    }

    fun showInstalling(
        context: Context,
        release: AppReleaseInfo,
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val contentPendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_PROGRESS,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            baseNotificationBuilder(context, context.getString(R.string.update_installing), release.versionName)
                .setProgress(0, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentPendingIntent)
                .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    fun showUpdateInstalled(
        context: Context,
        release: AppReleaseInfo,
    ) {
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID_INSTALLED,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val title = context.getString(R.string.update_notification_installed_title, release.versionName)
        val text = context.getString(R.string.update_notification_installed_desc)

        val notification =
            baseNotificationBuilder(context, title, text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_INSTALLED, notification)
        }
    }

    fun resetDownloadProgressTracking() {
        lastBytes = 0L
        lastTimeMs = 0L
        smoothedSpeedBytesPerSec = 0.0
    }

    private fun updateSpeedEstimate(downloadedBytes: Long) {
        val currentTimeMs = SystemClock.elapsedRealtime()
        val prevBytes = lastBytes
        val prevTimeMs = lastTimeMs

        if (prevTimeMs > 0 && currentTimeMs > prevTimeMs && downloadedBytes >= prevBytes) {
            val elapsedSec = (currentTimeMs - prevTimeMs) / MS_PER_SECOND
            val bytesDelta = downloadedBytes - prevBytes
            val instantSpeed = bytesDelta / elapsedSec
            smoothedSpeedBytesPerSec =
                if (smoothedSpeedBytesPerSec <= 0.0) {
                    instantSpeed
                } else {
                    smoothedSpeedBytesPerSec * SPEED_SMOOTHING_PREV + instantSpeed * SPEED_SMOOTHING_INSTANT
                }
        }
        lastBytes = downloadedBytes
        lastTimeMs = currentTimeMs
    }

    private fun computeProgressSubText(
        context: Context,
        downloadedBytes: Long,
        totalBytes: Long,
    ): String {
        val remainingSec =
            if (smoothedSpeedBytesPerSec > BYTES_PER_KB && totalBytes > downloadedBytes) {
                ((totalBytes - downloadedBytes) / smoothedSpeedBytesPerSec).toLong()
            } else {
                null
            }

        return if (remainingSec != null && remainingSec > 0) {
            if (remainingSec >= SECONDS_PER_MINUTE) {
                val mins = remainingSec / SECONDS_PER_MINUTE
                val secs = remainingSec % SECONDS_PER_MINUTE
                String.format(Locale.US, "%d分 %02d秒", mins, secs)
            } else {
                String.format(Locale.US, "%d秒", remainingSec)
            }
        } else {
            context.getString(R.string.update_in_app_updater)
        }
    }

    private fun formatProgressContentText(
        downloadedBytes: Long,
        totalBytes: Long,
        progress: Int,
    ): String {
        val downloadedMb = String.format(Locale.US, "%.1f", downloadedBytes / BYTES_PER_MB)
        val totalMb = String.format(Locale.US, "%.1f", totalBytes / BYTES_PER_MB)
        val speedText =
            when {
                smoothedSpeedBytesPerSec >= BYTES_PER_MB -> {
                    String.format(Locale.US, "%.1f MB/s", smoothedSpeedBytesPerSec / (BYTES_PER_KB * BYTES_PER_KB))
                }

                smoothedSpeedBytesPerSec >= BYTES_PER_KB -> {
                    String.format(Locale.US, "%.0f KB/s", smoothedSpeedBytesPerSec / BYTES_PER_KB)
                }

                smoothedSpeedBytesPerSec > 0.0 -> {
                    String.format(Locale.US, "%.0f B/s", smoothedSpeedBytesPerSec)
                }

                else -> {
                    null
                }
            }
        return if (speedText != null) {
            "$downloadedMb / $totalMb MB ($progress%) · $speedText"
        } else {
            "$downloadedMb / $totalMb MB ($progress%)"
        }
    }

    private fun createContentPendingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_PROGRESS,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun buildProgressNotification(
        context: Context,
        release: AppReleaseInfo,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        cancelPendingIntent: PendingIntent? = null,
    ): Notification {
        createNotificationChannel(context)
        updateSpeedEstimate(downloadedBytes)

        val contentText = formatProgressContentText(downloadedBytes, totalBytes, progress)
        val subText = computeProgressSubText(context, downloadedBytes, totalBytes)

        val builder =
            baseNotificationBuilder(
                context = context,
                title = context.getString(R.string.update_download_progress_title, release.versionName),
                text = contentText,
            ).setSubText(subText)
                .setProgress(100, progress, progress <= 0 && downloadedBytes <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(createContentPendingIntent(context))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (cancelPendingIntent != null) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.update_cancel_download),
                cancelPendingIntent,
            )
        }

        return builder.build()
    }

    fun showDownloadProgress(
        context: Context,
        release: AppReleaseInfo,
        progress: Int,
        downloadedBytes: Long,
        totalBytes: Long,
        cancelPendingIntent: PendingIntent? = null,
    ) {
        if (!hasNotificationPermission(context)) return

        val notification =
            buildProgressNotification(
                context = context,
                release = release,
                progress = progress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                cancelPendingIntent = cancelPendingIntent,
            )

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PROGRESS, notification)
        }
    }

    fun cancelDownloadProgress(context: Context) {
        resetDownloadProgressTracking()
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS)
        }
    }

    fun showDownloadFailed(
        context: Context,
        release: AppReleaseInfo,
        retryPendingIntent: PendingIntent? = null,
    ) {
        cancelDownloadProgress(context)
        if (!hasNotificationPermission(context)) return
        createNotificationChannel(context)

        val title = context.getString(R.string.update_download_failed)
        val text = release.versionName

        val builder =
            baseNotificationBuilder(context, title, text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

        if (retryPendingIntent != null) {
            builder.addAction(
                android.R.drawable.ic_menu_rotate,
                context.getString(R.string.update_retry_download),
                retryPendingIntent,
            )
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_FAILED, builder.build())
        }
    }
}
