package com.yunfie.illustia.ui.app

import androidx.compose.ui.graphics.vector.ImageVector
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.nav_bookmarks
import com.yunfie.illustia.resources.nav_bookmarks_full
import com.yunfie.illustia.resources.nav_home
import com.yunfie.illustia.resources.nav_more
import com.yunfie.illustia.resources.nav_novel
import com.yunfie.illustia.resources.nav_ranking
import com.yunfie.illustia.resources.nav_search
import com.yunfie.illustia.resources.nav_shorts_feed
import org.jetbrains.compose.resources.StringResource
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.TopDownloads
import top.yukonga.miuix.kmp.icon.extended.VerticalSplit
import top.yukonga.miuix.kmp.icon.extended.Search as MiuixSearch

enum class AppTab(
    val id: String,
    val labelResId: StringResource,
    val titleResId: StringResource,
    val icon: ImageVector,
) {
    Home("home", Res.string.nav_home, Res.string.nav_home, MiuixIcons.VerticalSplit),
    Novel("novel", Res.string.nav_novel, Res.string.nav_novel, MiuixIcons.Photos),
    Ranking("ranking", Res.string.nav_ranking, Res.string.nav_ranking, MiuixIcons.TopDownloads),
    Bookmarks("bookmarks", Res.string.nav_bookmarks, Res.string.nav_bookmarks_full, MiuixIcons.FavoritesFill),
    Search("search", Res.string.nav_search, Res.string.nav_search, MiuixIcons.MiuixSearch),
    ShortsFeed("shorts", Res.string.nav_shorts_feed, Res.string.nav_shorts_feed, MiuixIcons.Photos),
    More("more", Res.string.nav_more, Res.string.nav_more, MiuixIcons.More),
}
