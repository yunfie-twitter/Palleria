package com.yunfie.illustia.ui.app

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation3.runtime.NavKey
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.nativebridge.NativeIntentEvent
import com.yunfie.illustia.nativebridge.NativeIntentRouter
import com.yunfie.illustia.settings.AppSettings

/**
 * コマンドパレットで選ばれた [PaletteAction] を実行する。
 *
 * 以前は [IllustiaAppRoot] の `CommandPalette(onAction = { ... })` に約 230 行の巨大な `when` が
 * インラインで書かれており、Toast・ナビゲーション・設定更新が 1 箇所に集中していた。
 * 画面ツリーからロジックを切り離し、ここで 1 つの責務としてまとめる。
 *
 * @param onNavigate ルートを積む。[IllustiaAppRoot.navigate] を想定。
 * @param onDismissPalette アクション実行後にパレットを閉じる。
 * @param onSelectTab 指定タブへ切り替える（メインタブの pager スクロール込み）。
 */
@Composable
internal fun rememberCommandPaletteActionHandler(
    viewModel: IllustiaViewModel,
    backStack: MutableList<NavKey>,
    settings: AppSettings,
    onNavigate: (AppRoute) -> Unit,
    onDismissPalette: () -> Unit,
    onSelectTab: (AppTab) -> Unit,
): (PaletteAction, String) -> Unit {
    val context = LocalContext.current

    val currentDeps =
        rememberUpdatedState(
            CommandPaletteDependencies(
                viewModel = viewModel,
                backStack = backStack,
                settings = settings,
                onNavigate = onNavigate,
                onDismissPalette = onDismissPalette,
                onSelectTab = onSelectTab,
            ),
        )

    return remember(context) {
        { action, query ->
            val deps = currentDeps.value
            deps.onDismissPalette()
            handlePaletteAction(action, query, context, deps)
        }
    }
}

private data class CommandPaletteDependencies(
    val viewModel: IllustiaViewModel,
    val backStack: MutableList<NavKey>,
    val settings: AppSettings,
    val onNavigate: (AppRoute) -> Unit,
    val onDismissPalette: () -> Unit,
    val onSelectTab: (AppTab) -> Unit,
)

private fun handlePaletteAction(
    action: PaletteAction,
    query: String,
    context: Context,
    deps: CommandPaletteDependencies,
) {
    when (val destination = action.navigationDestination()) {
        null -> {
            // タブ切替・設定トグル・クリップボードなど、単純な遷移ではないアクション。
            handlePaletteNonNavigationAction(action, query, context, deps)
        }

        is PaletteDestination.Tab -> {
            deps.backStack.clear()
            deps.backStack.add(AppRoute.Main)
            deps.onSelectTab(destination.tab)
        }

        is PaletteDestination.Route -> {
            deps.onNavigate(destination.route)
        }

        is PaletteDestination.Search -> {
            deps.viewModel.submitSearch(query)
            deps.onNavigate(AppRoute.SearchResults(query))
        }
    }
}

private fun handlePaletteNonNavigationAction(
    action: PaletteAction,
    query: String,
    context: Context,
    deps: CommandPaletteDependencies,
) {
    when (action) {
        PaletteAction.SwitchAccount -> {
            deps.viewModel.openAccountSwitcher()
        }

        PaletteAction.ClearCache -> {
            deps.viewModel.clearAppCache()
            toast(context, R.string.command_palette_cache_cleared)
        }

        PaletteAction.RefreshFeed -> {
            deps.viewModel.refreshHome(forceRefresh = true)
            deps.viewModel.refreshBookmarks(forceRefresh = true)
            deps.viewModel.refreshTimeline(forceRefresh = true)
            toast(context, R.string.command_palette_feeds_refreshed)
        }

        PaletteAction.OpenFromClipboard -> {
            openFromClipboard(context, query, deps.viewModel, deps.onNavigate)
        }

        PaletteAction.ToggleAi -> {
            handleToggleAi(context, deps)
        }

        PaletteAction.ToggleR18 -> {
            handleToggleR18(context, deps)
        }

        PaletteAction.ToggleGridColumns -> {
            handleToggleGridColumns(context, deps)
        }

        PaletteAction.ToggleImageQuality -> {
            handleToggleImageQuality(context, deps)
        }

        PaletteAction.ToggleUgoiraAutoPlay -> {
            handleToggleUgoiraAutoPlay(context, deps)
        }

        PaletteAction.Light -> {
            deps.viewModel.updateThemeMode("light")
        }

        PaletteAction.Dark -> {
            deps.viewModel.updateThemeMode("dark")
        }

        PaletteAction.System -> {
            deps.viewModel.updateThemeMode("system")
        }

        // 残りは navigationDestination() が処理するためここには来ない。
        else -> {
        }
    }
}

private fun handleToggleAi(
    context: Context,
    deps: CommandPaletteDependencies,
) {
    val next = !deps.settings.hideAiWorks
    deps.viewModel.updateHideAiWorks(next)
    toast(context, if (next) R.string.command_palette_ai_hidden else R.string.command_palette_ai_shown)
}

private fun handleToggleR18(
    context: Context,
    deps: CommandPaletteDependencies,
) {
    val next = !deps.settings.allowR18
    deps.viewModel.updateAllowR18(next)
    toast(context, if (next) R.string.command_palette_r18_enabled else R.string.command_palette_r18_disabled)
}

private fun handleToggleGridColumns(
    context: Context,
    deps: CommandPaletteDependencies,
) {
    val nextCols =
        when (deps.settings.verticalColumnCount) {
            1 -> 2
            2 -> 3
            3 -> 4
            else -> 2
        }
    deps.viewModel.updateVerticalColumnCount(nextCols)
    Toast
        .makeText(
            context,
            context.getString(R.string.command_palette_grid_columns_changed, nextCols),
            Toast.LENGTH_SHORT,
        ).show()
}

private fun handleToggleImageQuality(
    context: Context,
    deps: CommandPaletteDependencies,
) {
    val next = !deps.settings.highQualityImages
    deps.viewModel.updateHighQuality(next)
    toast(
        context,
        if (next) R.string.command_palette_high_quality_enabled else R.string.command_palette_high_quality_disabled,
    )
}

private fun handleToggleUgoiraAutoPlay(
    context: Context,
    deps: CommandPaletteDependencies,
) {
    val next = !deps.settings.ugoiraAutoPlay
    deps.viewModel.updateUgoiraAutoPlay(next)
    toast(
        context,
        if (next) R.string.command_palette_ugoira_autoplay_enabled else R.string.command_palette_ugoira_autoplay_disabled,
    )
}

/** 開く先を持つパレットアクションの遷移先。 */
private sealed interface PaletteDestination {
    data class Tab(
        val tab: AppTab,
    ) : PaletteDestination

    data class Route(
        val route: AppRoute,
    ) : PaletteDestination

    data object Search : PaletteDestination
}

/** タブ切替かルート遷移を行うアクションだけを [PaletteDestination] に変換する。 */
private fun PaletteAction.navigationDestination(): PaletteDestination? = paletteDestinations[this]

private val paletteDestinations: Map<PaletteAction, PaletteDestination> =
    mapOf(
        PaletteAction.Home to PaletteDestination.Tab(AppTab.Home),
        PaletteAction.Ranking to PaletteDestination.Tab(AppTab.Ranking),
        PaletteAction.Bookmarks to PaletteDestination.Tab(AppTab.Bookmarks),
        PaletteAction.ShortsFeed to PaletteDestination.Tab(AppTab.ShortsFeed),
        PaletteAction.Novel to PaletteDestination.Route(AppRoute.NovelList),
        PaletteAction.Search to PaletteDestination.Search,
        PaletteAction.ViewHistory to PaletteDestination.Route(AppRoute.ViewHistory),
        PaletteAction.FavoriteTags to PaletteDestination.Route(AppRoute.FavoriteTags),
        PaletteAction.DownloadQueue to PaletteDestination.Route(AppRoute.DownloadQueue),
        PaletteAction.OfflineLibrary to PaletteDestination.Route(AppRoute.OfflineLibrary),
        PaletteAction.Notifications to PaletteDestination.Route(AppRoute.Notifications),
        PaletteAction.MuteSettings to PaletteDestination.Route(AppRoute.MuteSettings),
        PaletteAction.AppData to PaletteDestination.Route(AppRoute.AppData),
        PaletteAction.Settings to PaletteDestination.Route(AppRoute.Settings),
        PaletteAction.Flags to PaletteDestination.Route(AppRoute.FeatureFlags),
    )

private fun openFromClipboard(
    context: Context,
    query: String,
    viewModel: IllustiaViewModel,
    onNavigate: (AppRoute) -> Unit,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clipText =
        clipboard
            ?.primaryClip
            ?.getItemAt(0)
            ?.text
            ?.toString()
            ?.trim()
    val target = if (query.isNotBlank()) query.trim() else clipText
    if (target.isNullOrBlank()) {
        toast(context, R.string.command_palette_no_clipboard_content)
        return
    }

    val numericId = target.toLongOrNull()
    if (numericId != null) {
        viewModel.openIllust(numericId)
        onNavigate(AppRoute.Detail(numericId))
        return
    }

    when (val event = NativeIntentRouter.parseText(target)) {
        is NativeIntentEvent.Artwork -> {
            viewModel.openIllust(event.id)
            onNavigate(AppRoute.Detail(event.id))
        }

        is NativeIntentEvent.User -> {
            viewModel.openUserPage(event.id)
            onNavigate(AppRoute.UserProfile(event.id))
        }

        is NativeIntentEvent.Tag -> {
            viewModel.submitSearch(event.tag)
            onNavigate(AppRoute.SearchResults(event.tag))
        }

        else -> {
            toast(context, R.string.command_palette_invalid_link)
        }
    }
}

private fun toast(
    context: Context,
    resId: Int,
) {
    Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
}
