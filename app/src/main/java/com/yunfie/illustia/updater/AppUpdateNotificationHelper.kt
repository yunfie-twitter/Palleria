package com.yunfie.illustia.updater

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R
import java.io.File

object AppUpdateNotificationHelper {
    const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID_NEW_VERSION = 8101
    private const val NOTIFICATION_ID_DOWNLOADED = 8102
    private const val NOTIFICATION_ID_INSTALLED = 8103

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
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
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
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DOWNLOADED, notification)
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
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_INSTALLED, notification)
        }
    }
}
