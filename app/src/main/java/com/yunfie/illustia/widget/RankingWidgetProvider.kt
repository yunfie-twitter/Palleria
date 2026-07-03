package com.yunfie.illustia.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import com.yunfie.illustia.IllustiaApplication
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.data.PixivApiClient
import com.yunfie.illustia.data.proxyPixivImageUrl
import com.yunfie.illustia.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.Request

class RankingWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        enqueueRefresh(context, AppWidgetManager.getInstance(context))
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        enqueueUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_RANKING_WIDGET) {
            enqueueRefresh(context, AppWidgetManager.getInstance(context))
        }
    }

    private fun enqueueRefresh(context: Context, manager: AppWidgetManager) {
        val ids = manager.getAppWidgetIds(ComponentName(context, RankingWidgetProvider::class.java))
        if (ids.isEmpty()) return
        enqueueUpdate(context, manager, ids)
    }

    private fun enqueueUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RankingWidgetUpdater(context, manager).update(appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_RANKING_WIDGET = "com.yunfie.illustia.widget.ACTION_REFRESH_RANKING_WIDGET"
    }
}

private class RankingWidgetUpdater(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
) {
    private val applicationContext = context.applicationContext
    private val repository = IllustiaRepository(
        SettingsStore(applicationContext),
        PixivApiClient((applicationContext as IllustiaApplication).sharedHttpClient),
    )

    suspend fun update(appWidgetIds: IntArray) {
        val settings = repository.readSettings()
        if (settings.refreshToken.isBlank()) {
            renderLoggedOut(appWidgetIds)
            return
        }

        renderLoading(appWidgetIds)

        runCatching {
            repository.login(settings.refreshToken)
            repository.loadRanking("day")
        }.onSuccess { page ->
            val entries = coroutineScope {
                page.items.take(3).mapIndexed { index, illust ->
                    async {
                        WidgetEntry(
                            rank = index + 1,
                            illust = illust,
                            bitmap = loadThumbnail(illust),
                        )
                    }
                }.map { it.await() }
            }
            renderLoggedIn(appWidgetIds, entries)
        }.onFailure {
            renderError(appWidgetIds)
        }
    }

    private fun renderLoggedOut(appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.ranking_widget)
            bindLoggedOut(views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun renderLoading(appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.ranking_widget)
            views.setViewVisibility(R.id.widget_status, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_ranking_loading))
            views.setViewVisibility(R.id.widget_logged_out_container, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_logged_in_container, android.view.View.VISIBLE)
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))
            clearRankedItems(views)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun renderError(appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.ranking_widget)
            views.setViewVisibility(R.id.widget_status, android.view.View.VISIBLE)
            views.setTextViewText(R.id.widget_status, context.getString(R.string.widget_ranking_error))
            views.setViewVisibility(R.id.widget_logged_out_container, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_logged_in_container, android.view.View.GONE)
            views.setTextViewText(R.id.widget_login_title, context.getString(R.string.widget_ranking_error))
            views.setTextViewText(R.id.widget_login_subtitle, context.getString(R.string.widget_ranking_login_subtitle))
            views.setTextViewText(R.id.widget_login_button, context.getString(R.string.widget_ranking_refresh))
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))
            views.setOnClickPendingIntent(
                R.id.widget_login_button,
                refreshPendingIntent(context),
            )
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun renderLoggedIn(appWidgetIds: IntArray, entries: List<WidgetEntry>) {
        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.ranking_widget)
            bindLoggedIn(views, entries)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun bindLoggedOut(views: RemoteViews) {
        views.setViewVisibility(R.id.widget_status, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_logged_out_container, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_logged_in_container, android.view.View.GONE)
        views.setTextViewText(R.id.widget_login_title, context.getString(R.string.widget_ranking_login_title))
        views.setTextViewText(R.id.widget_login_subtitle, context.getString(R.string.widget_ranking_login_subtitle))
        views.setTextViewText(R.id.widget_login_button, context.getString(R.string.widget_ranking_login_button))
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))
        val intent = launchAppIntent(context)
        views.setOnClickPendingIntent(R.id.widget_login_button, intent)
    }

    private fun bindLoggedIn(views: RemoteViews, entries: List<WidgetEntry>) {
        views.setViewVisibility(R.id.widget_status, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_logged_out_container, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_logged_in_container, android.view.View.VISIBLE)
        clearRankedItems(views)

        val slots = listOf(
            RankingWidgetSlot(
                rootId = R.id.widget_item_1,
                rankId = R.id.widget_item_1_rank,
                thumbId = R.id.widget_item_1_thumb,
                titleId = R.id.widget_item_1_title,
                artistId = R.id.widget_item_1_artist,
            ),
            RankingWidgetSlot(
                rootId = R.id.widget_item_2,
                rankId = R.id.widget_item_2_rank,
                thumbId = R.id.widget_item_2_thumb,
                titleId = R.id.widget_item_2_title,
                artistId = R.id.widget_item_2_artist,
            ),
            RankingWidgetSlot(
                rootId = R.id.widget_item_3,
                rankId = R.id.widget_item_3_rank,
                thumbId = R.id.widget_item_3_thumb,
                titleId = R.id.widget_item_3_title,
                artistId = R.id.widget_item_3_artist,
            ),
        )

        slots.forEachIndexed { index, slot ->
            val entry = entries.getOrNull(index)
            if (entry == null) {
                views.setViewVisibility(slot.rootId, android.view.View.GONE)
                return@forEachIndexed
            }

            views.setViewVisibility(slot.rootId, android.view.View.VISIBLE)
            views.setTextViewText(slot.rankId, entry.rank.toString())
            views.setTextViewText(slot.titleId, entry.illust.title.ifBlank { context.getString(R.string.widget_ranking_title) })
            views.setTextViewText(slot.artistId, entry.illust.artistName.ifBlank { "Pixiv" })
            if (entry.bitmap != null) {
                views.setImageViewBitmap(slot.thumbId, entry.bitmap)
            } else {
                views.setImageViewResource(slot.thumbId, R.mipmap.ic_launcher)
            }
            views.setOnClickPendingIntent(slot.rootId, artworkPendingIntent(context, entry.illust.id))
        }

        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent(context))
    }

    private fun clearRankedItems(views: RemoteViews) {
        listOf(
            R.id.widget_item_1,
            R.id.widget_item_2,
            R.id.widget_item_3,
        ).forEach { views.setViewVisibility(it, android.view.View.VISIBLE) }
    }

    private suspend fun loadThumbnail(illust: Illust): Bitmap? {
        val proxyBaseUrl = SettingsStore(context.applicationContext).read().pixivImageProxyBaseUrl
        val imageUrl = proxyPixivImageUrl(illust.thumbnailUrl, proxyBaseUrl)
        val request = Request.Builder()
            .url(imageUrl)
            .headers(
                Headers.Builder()
                    .add("Referer", "https://www.pixiv.net/")
                    .add("User-Agent", "PixivAndroidApp/6.184.0 (Android 14; Illustia)")
                    .build(),
            )
            .build()
        return runCatching {
            val response = (context.applicationContext as IllustiaApplication).sharedHttpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) return@runCatching null
                val bytes = it.body.bytes()
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
    }
}

private data class WidgetEntry(
    val rank: Int,
    val illust: Illust,
    val bitmap: Bitmap?,
)

private data class RankingWidgetSlot(
    val rootId: Int,
    val rankId: Int,
    val thumbId: Int,
    val titleId: Int,
    val artistId: Int,
)

private fun launchAppIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    return PendingIntent.getActivity(
        context,
        1001,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun refreshPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, RankingWidgetProvider::class.java).apply {
        action = RankingWidgetProvider.ACTION_REFRESH_RANKING_WIDGET
    }
    return PendingIntent.getBroadcast(
        context,
        1002,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

private fun artworkPendingIntent(context: Context, illustId: Long): PendingIntent {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pixiv.net/artworks/$illustId")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val requestCode = (illustId xor (illustId ushr 32)).toInt()
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

