package com.yunfie.illustia.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MainNavigationContentPadding
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class AppDataDeleteTarget {
    Cache,
    ViewHistory,
    SearchHistory,
    FavoriteTags,
    MuteData,
}

@Composable
fun AppDataScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var deleteTarget by remember { mutableStateOf<AppDataDeleteTarget?>(null) }
    val mutedTotal = state.settings.mutedIllusts.size + state.settings.mutedUsers.size + state.settings.mutedTags.size

    LaunchedEffect(state.message) {
        cacheBytes = withContext(Dispatchers.IO) { context.cacheBytes() }
    }

    deleteTarget?.let { target ->
        MiuixConfirmDialog(
            show = true,
            title = target.confirmTitle(context),
            summary = target.confirmSummary(context),
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                when (target) {
                    AppDataDeleteTarget.Cache -> viewModel.clearAppCache()
                    AppDataDeleteTarget.ViewHistory -> viewModel.clearViewHistory()
                    AppDataDeleteTarget.SearchHistory -> viewModel.clearSearchHistory()
                    AppDataDeleteTarget.FavoriteTags -> viewModel.clearFavoriteTags()
                    AppDataDeleteTarget.MuteData -> viewModel.clearMuteData()
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_data_title),
                largeTitle = stringResource(R.string.app_data_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MiuixTheme.colorScheme.surface),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = scaffoldPadding.calculateTopPadding() + 16.dp,
                bottom = MainNavigationContentPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {

        item {
            Section(stringResource(R.string.app_data_section_overview)) {
                ElevatedPanel {
                    SettingRow(stringResource(R.string.data_view_history), stringResource(R.string.data_items_count, state.settings.viewHistory.size)) {
                        Text(stringResource(R.string.data_view_history_badge), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold)
                    }
                    DividerLine()
                    SettingRow(stringResource(R.string.data_search_history), stringResource(R.string.data_items_count, state.settings.searchHistory.size)) {
                        Text(stringResource(R.string.data_search_history_badge), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold)
                    }
                    DividerLine()
                    SettingRow(stringResource(R.string.data_watchlist_tags), stringResource(R.string.data_items_count, state.settings.favoriteTags.size)) {
                        Text(stringResource(R.string.data_watchlist_tags_badge), color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    DividerLine()
                    SettingRow(stringResource(R.string.data_mute_data), stringResource(R.string.data_items_count, mutedTotal)) {
                        Text(stringResource(R.string.data_mute_data_badge), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontWeight = FontWeight.Bold)
                    }
                    DividerLine()
                    SettingRow(stringResource(R.string.data_cache), cacheBytes.readableBytes()) {
                        Text(stringResource(R.string.data_cache_badge), color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Section(stringResource(R.string.data_section_cleanup)) {
                ElevatedPanel(contentPadding = PaddingValues(0.dp)) {
                    SettingLinkRow(stringResource(R.string.data_delete_cache)) { deleteTarget = AppDataDeleteTarget.Cache }
                    DividerLine()
                    SettingLinkRow(stringResource(R.string.data_delete_view_history)) { deleteTarget = AppDataDeleteTarget.ViewHistory }
                    DividerLine()
                    SettingLinkRow(stringResource(R.string.data_delete_search_history)) { deleteTarget = AppDataDeleteTarget.SearchHistory }
                    DividerLine()
                    SettingLinkRow(stringResource(R.string.data_delete_watchlist_tags)) { deleteTarget = AppDataDeleteTarget.FavoriteTags }
                    DividerLine()
                    SettingLinkRow(stringResource(R.string.data_delete_mute_data)) { deleteTarget = AppDataDeleteTarget.MuteData }
                }
            }
        }

    }
    }
}

private fun AppDataDeleteTarget.confirmTitle(context: Context): String {
    return when (this) {
        AppDataDeleteTarget.Cache -> context.getString(R.string.data_delete_cache)
        AppDataDeleteTarget.ViewHistory -> context.getString(R.string.data_delete_view_history)
        AppDataDeleteTarget.SearchHistory -> context.getString(R.string.data_delete_search_history)
        AppDataDeleteTarget.FavoriteTags -> context.getString(R.string.data_delete_watchlist_tags)
        AppDataDeleteTarget.MuteData -> context.getString(R.string.data_delete_mute_data)
    }
}

private fun AppDataDeleteTarget.confirmSummary(context: Context): String {
    return when (this) {
        AppDataDeleteTarget.Cache -> context.getString(R.string.data_delete_cache_desc)
        AppDataDeleteTarget.ViewHistory -> context.getString(R.string.data_delete_view_history_desc)
        AppDataDeleteTarget.SearchHistory -> context.getString(R.string.data_delete_search_history_desc)
        AppDataDeleteTarget.FavoriteTags -> context.getString(R.string.data_delete_watchlist_tags_desc)
        AppDataDeleteTarget.MuteData -> context.getString(R.string.data_delete_mute_data_desc)
    }
}

private fun Context.cacheBytes(): Long {
    return sequenceOf(cacheDir, externalCacheDir)
        .filterNotNull()
        .sumOf { it.directorySize() }
}

private fun File.directorySize(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

private fun Long.readableBytes(): String {
    if (this <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var value = toDouble()
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return if (index == 0) {
        "${value.toLong()} ${units[index]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[index])
    }
}
