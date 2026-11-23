package com.yunfie.illustia.platform

import android.content.Context
import com.yunfie.illustia.wallpaper.WallpaperPlaylistScheduler
import com.yunfie.illustia.widget.IllustWidgetProvider
import com.yunfie.illustia.widget.RankingWidgetProvider

class AndroidAppServices(
    context: Context,
) : AppServices {
    private val appContext = context.applicationContext

    override fun setWallpaperPlaylistEnabled(enabled: Boolean) = WallpaperPlaylistScheduler.setEnabled(appContext, enabled)

    override fun refreshPrivacySensitiveWidgets() {
        IllustWidgetProvider.refreshAll(appContext)
        RankingWidgetProvider.refreshAll(appContext)
    }

    override suspend fun refreshRankingWidget() {
        RankingWidgetProvider.refreshAllSuspend(appContext)
    }

    override fun acquireArtworkDownloadService(): AutoCloseable = ArtworkDownloadService.acquire(appContext)
}
