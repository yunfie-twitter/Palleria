package com.yunfie.illustia.ui.screens

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.session.MediaSession
import android.os.Build
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R

internal object NovelTtsNotificationHelper {
    const val CHANNEL_ID = "novel_tts_channel"
    const val NOTIFICATION_ID = 2001

    private const val REQUEST_CODE_PREV = 101
    private const val REQUEST_CODE_TOGGLE = 102
    private const val REQUEST_CODE_NEXT = 103
    private const val REQUEST_CODE_STOP = 104
    private const val REQUEST_CODE_CONTENT = 105

    private const val ACTION_INDEX_PREV = 0
    private const val ACTION_INDEX_PLAY_PAUSE = 1
    private const val ACTION_INDEX_NEXT = 2

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.tts_channel_name)
            val descriptionText = context.getString(R.string.tts_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        service: Service,
        novelTitle: String,
        authorName: String,
        currentParagraphIndex: Int,
        totalParagraphs: Int,
        isPlaying: Boolean,
        mediaSession: MediaSession?,
    ): Notification {
        val title = novelTitle.ifBlank { service.getString(R.string.flag_novel_tts_audiobook_title) }
        val progressText = if (totalParagraphs > 0) "${currentParagraphIndex + 1}/$totalParagraphs" else ""
        val contentText =
            when {
                authorName.isNotBlank() && progressText.isNotBlank() -> "$authorName · $progressText"
                progressText.isNotBlank() -> progressText
                else -> authorName
            }

        val mediaStyle =
            Notification.MediaStyle().apply {
                mediaSession?.let { setMediaSession(it.sessionToken) }
                setShowActionsInCompactView(ACTION_INDEX_PREV, ACTION_INDEX_PLAY_PAUSE, ACTION_INDEX_NEXT)
            }

        return Notification
            .Builder(service, CHANNEL_ID)
            .setStyle(mediaStyle)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(service.getString(R.string.app_name))
            .setContentIntent(createContentPendingIntent(service))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .addAction(createPrevAction(service))
            .addAction(createPlayPauseAction(service, isPlaying))
            .addAction(createNextAction(service))
            .addAction(createStopAction(service))
            .build()
    }

    private fun createContentPendingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_CONTENT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createPrevAction(context: Context): Notification.Action =
        Notification.Action
            .Builder(
                Icon.createWithResource(context, android.R.drawable.ic_media_previous),
                context.getString(R.string.tts_prev_paragraph),
                createServicePendingIntent(context, NovelTtsService.ACTION_PREV, REQUEST_CODE_PREV),
            ).build()

    private fun createPlayPauseAction(
        context: Context,
        isPlaying: Boolean,
    ): Notification.Action {
        val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val title = context.getString(if (isPlaying) R.string.tts_pause else R.string.tts_play)
        return Notification.Action
            .Builder(
                Icon.createWithResource(context, iconRes),
                title,
                createServicePendingIntent(context, NovelTtsService.ACTION_TOGGLE_PLAY, REQUEST_CODE_TOGGLE),
            ).build()
    }

    private fun createNextAction(context: Context): Notification.Action =
        Notification.Action
            .Builder(
                Icon.createWithResource(context, android.R.drawable.ic_media_next),
                context.getString(R.string.tts_next_paragraph),
                createServicePendingIntent(context, NovelTtsService.ACTION_NEXT, REQUEST_CODE_NEXT),
            ).build()

    private fun createStopAction(context: Context): Notification.Action =
        Notification.Action
            .Builder(
                Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                context.getString(R.string.tts_stop),
                createServicePendingIntent(context, NovelTtsService.ACTION_STOP, REQUEST_CODE_STOP),
            ).build()

    private fun createServicePendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(context, NovelTtsService::class.java).apply {
                this.action = action
            }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
