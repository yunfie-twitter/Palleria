package com.yunfie.illustia.ui.app

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.yunfie.illustia.R
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Search as MiuixSearch
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit

internal enum class AppTab(
    @param:androidx.annotation.StringRes val labelResId: Int,
    @param:androidx.annotation.StringRes val titleResId: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, R.string.nav_home, MiuixIcons.VerticalSplit),
    Novel(R.string.nav_novel, R.string.nav_novel, MiuixIcons.Photos),
    Ranking(R.string.nav_ranking, R.string.nav_ranking, MiuixIcons.TopDownloads),
    Bookmarks(R.string.nav_bookmarks, R.string.nav_bookmarks_full, MiuixIcons.FavoritesFill),
    Search(R.string.nav_search, R.string.nav_search, MiuixIcons.MiuixSearch),
    ShortsFeed(R.string.nav_shorts_feed, R.string.nav_shorts_feed, MiuixIcons.Photos),
    More(R.string.nav_more, R.string.nav_more, MiuixIcons.More),
}

internal val SwipeTabs = listOf(AppTab.Home, AppTab.Search, AppTab.Bookmarks, AppTab.Ranking, AppTab.More)
internal val VisibleTabs = SwipeTabs

internal fun mainTabs(shortsFeedEnabled: Boolean): List<AppTab> = buildList {
    add(AppTab.Home)
    add(if (shortsFeedEnabled) AppTab.ShortsFeed else AppTab.Search)
    add(AppTab.Bookmarks)
    add(AppTab.Ranking)
    add(AppTab.More)
}

internal fun visibleTabs(shortsFeedEnabled: Boolean): List<AppTab> = mainTabs(shortsFeedEnabled)

internal fun startupTabFor(value: String): AppTab {
    return when (value) {
        "ranking" -> AppTab.Ranking
        "bookmarks" -> AppTab.Bookmarks
        "search" -> AppTab.Search
        "more" -> AppTab.More
        else -> AppTab.Home
    }
}

internal sealed interface AppRoute : NavKey {
    data object Main : AppRoute
    data object Search : AppRoute
    data object Onboarding : AppRoute
    data object Detail : AppRoute
    data object ImageViewer : AppRoute
    data object NovelList : AppRoute
    data object NovelReader : AppRoute
    data object Settings : AppRoute
    data object GeneralSettings : AppRoute
    data object ImageSettings : AppRoute
    data object BookmarkSettings : AppRoute
    data object AccountSettings : AppRoute
    data object AccountLoginMethod : AppRoute
    data object DataSettings : AppRoute
    data object ViewHistory : AppRoute
    data object MuteSettings : AppRoute
    data object AppData : AppRoute
    data object DownloadQueue : AppRoute
    data object OfflineLibrary : AppRoute
    data object SavedIllustViewer : AppRoute
    data object About : AppRoute
    data object FavoriteTags : AppRoute
    data object WatchlistSeries : AppRoute
    data object UserProfile : AppRoute
    data object AppLockSetup : AppRoute
    data object AppLockPinEntry : AppRoute
    data object PrivacyModeSettings : AppRoute
    data object IllustSeries : AppRoute
    data object Comments : AppRoute
}
