package com.yunfie.illustia.platform

import android.content.Context
import android.content.Intent
import com.yunfie.illustia.account.PalleriaAccount
import com.yunfie.illustia.data.AppStorageUsage
import com.yunfie.illustia.data.BookmarkToggleScheduler
import com.yunfie.illustia.data.FollowDeltaSyncManager
import com.yunfie.illustia.data.readAppStorageUsage
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.Restrict
import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.wallpaper.PalleriaLiveWallpaperService
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

    override fun notifyLiveWallpaperSettingsChanged() {
        appContext.sendBroadcast(
            Intent(PalleriaLiveWallpaperService.ACTION_SETTINGS_CHANGED)
                .setPackage(appContext.packageName),
        )
    }

    override fun reconcileAccounts(accounts: List<StoredAccount>) {
        PalleriaAccount.reconcile(appContext, accounts)
    }

    override fun enqueueBookmarkToggle(
        illustId: Long,
        bookmarked: Boolean,
        restrict: Restrict,
        followRestrict: Restrict,
        bookmarkUserId: Long,
        artistId: Long,
        tags: List<String>,
        bookmarkTags: List<String>,
    ): Boolean =
        runCatching {
            BookmarkToggleScheduler.enqueue(
                context = appContext,
                illustId = illustId,
                bookmarked = bookmarked,
                restrict = restrict,
                followRestrict = followRestrict,
                bookmarkUserId = bookmarkUserId,
                artistId = artistId,
                tags = tags,
                bookmarkTags = bookmarkTags,
            )
        }.isSuccess

    override suspend fun recordFollowDeltaSnapshot(
        token: String,
        freshItems: List<Illust>,
        proxyBaseUrl: String,
    ) {
        FollowDeltaSyncManager.recordSnapshotAndPrewarmDelta(
            context = appContext,
            token = token,
            freshItems = freshItems,
            proxyBaseUrl = proxyBaseUrl,
        )
    }

    override suspend fun readStorageUsage(): AppStorageUsage =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { appContext.readAppStorageUsage() }

    override fun acquireArtworkDownloadService(): AutoCloseable = ArtworkDownloadService.acquire(appContext)
}
