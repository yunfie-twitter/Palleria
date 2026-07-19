package com.yunfie.illustia.wallpaper

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit

object WallpaperPlaylistScheduler {
    private const val ACTION = "com.yunfie.illustia.wallpaper.ROTATE"

    fun setEnabled(context: Context, enabled: Boolean) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val operation = PendingIntent.getBroadcast(
            context,
            7401,
            Intent(context, WallpaperPlaylistReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(operation)
        if (enabled && LiveWallpaperSupport.isSupported()) {
            context.sendBroadcast(Intent(context, WallpaperPlaylistReceiver::class.java).setAction(ACTION))
            alarm.setInexactRepeating(
                AlarmManager.RTC,
                System.currentTimeMillis() + TimeUnit.HOURS.toMillis(6),
                TimeUnit.HOURS.toMillis(6),
                operation,
            )
        }
    }
}

class WallpaperPlaylistReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = SettingsStore(context.applicationContext)
                if (!store.read().wallpaperPlaylistEnabled) return@launch
                val candidates = store.savedIllustDir()
                    .walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp") }
                    .toList()
                val image = candidates.randomOrNull() ?: return@launch
                BitmapFactory.decodeFile(image.absolutePath)?.let { bitmap ->
                    try {
                        WallpaperManager.getInstance(context).setBitmap(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
