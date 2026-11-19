package com.yunfie.illustia.nativebridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.yunfie.illustia.AppShortcutDestination
import com.yunfie.illustia.MainActivity
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust

/**
 * Publishes runtime (dynamic) shortcuts that mirror the four static destinations and
 * surface the most recently viewed artworks.
 *
 * Shortcuts are pushed through [ShortcutManagerCompat] so they are also eligible for
 * Assistant surfaces. Pushing rather than replacing keeps us clear of the per-activity
 * shortcut cap shared with the static XML entries.
 */
object DynamicShortcutPublisher {
    private const val MAX_RECENT_ARTWORKS = 2

    fun publish(
        context: Context,
        recentIllusts: List<Illust>,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        publishShortcuts(context, buildSpecs(context, recentIllusts))
    }

    @RequiresApi(Build.VERSION_CODES.N_MR1)
    private fun publishShortcuts(
        context: Context,
        specs: List<ShortcutInfoCompat>,
    ) {
        specs.forEach { spec ->
            runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, spec) }
        }
    }

    internal fun buildSpecs(
        context: Context,
        recentIllusts: List<Illust>,
    ): List<ShortcutInfoCompat> = destinationSpecs(context) + recentIllusts.take(MAX_RECENT_ARTWORKS).map { artworkSpec(context, it) }

    private fun destinationSpecs(context: Context): List<ShortcutInfoCompat> =
        listOf(
            destinationSpec(context, AppShortcutDestination.Search, R.string.shortcut_search, R.string.shortcut_search_long),
            destinationSpec(context, AppShortcutDestination.Ranking, R.string.shortcut_ranking, R.string.shortcut_ranking_long),
            destinationSpec(context, AppShortcutDestination.Bookmarks, R.string.shortcut_bookmarks, R.string.shortcut_bookmarks_long),
            destinationSpec(
                context,
                AppShortcutDestination.ViewHistory,
                R.string.shortcut_history,
                R.string.shortcut_history_long,
            ),
        )

    private fun destinationSpec(
        context: Context,
        destination: AppShortcutDestination,
        shortLabelRes: Int,
        longLabelRes: Int,
    ): ShortcutInfoCompat =
        ShortcutInfoCompat
            .Builder(context, destination.shortcutId)
            .setShortLabel(context.getString(shortLabelRes))
            .setLongLabel(context.getString(longLabelRes))
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(mainActivityIntent(context, Intent(destination.action)))
            .build()

    private fun artworkSpec(
        context: Context,
        illust: Illust,
    ): ShortcutInfoCompat {
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("pixiv://illusts/${illust.id}")).setClass(context, MainActivity::class.java)
        return ShortcutInfoCompat
            .Builder(context, "art-${illust.id}")
            .setShortLabel(illust.title.take(MAX_SHORT_LABEL_LENGTH).ifBlank { illust.id.toString() })
            .setLongLabel(illust.title.ifBlank { illust.id.toString() })
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(mainActivityIntent(context, intent))
            .build()
    }

    private fun mainActivityIntent(
        context: Context,
        intent: Intent,
    ): Intent =
        intent
            .setClass(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

    private const val MAX_SHORT_LABEL_LENGTH = 10
}

private val AppShortcutDestination.shortcutId: String
    get() =
        when (this) {
            AppShortcutDestination.Search -> "dyn-search"
            AppShortcutDestination.Ranking -> "dyn-ranking"
            AppShortcutDestination.Bookmarks -> "dyn-bookmarks"
            AppShortcutDestination.ViewHistory -> "dyn-history"
        }
