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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.data.AppStorageUsage
import com.yunfie.illustia.data.readAppStorageUsage
import com.yunfie.illustia.pallasync.PallaSyncClientStatus
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.MiuixConfirmDialog
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import com.yunfie.illustia.ui.components.SettingLinkRow
import com.yunfie.illustia.ui.components.SettingRow
import com.yunfie.illustia.ui.components.adaptiveMainNavigationContentPadding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Copy
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.icon.extended.Theme
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
    val syncManager = remember(context) { PalleriaSyncManager(context = context) }
    val pageTitle = stringResource(R.string.app_data_title)
    var storageUsage by remember { mutableStateOf<AppStorageUsage?>(null) }
    var syncStatus by remember { mutableStateOf<PallaSyncClientStatus?>(null) }
    var deleteTarget by remember { mutableStateOf<AppDataDeleteTarget?>(null) }
    val mutedTotal = state.settings.mutedIllusts.size + state.settings.mutedUsers.size + state.settings.mutedTags.size

    LaunchedEffect(state.message, state.settings.pallaSyncEnabled) {
        storageUsage = withContext(Dispatchers.IO) { context.readAppStorageUsage() }
        syncStatus =
            try {
                syncManager.getClientStatus()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
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
        modifier = Modifier.semantics { paneTitle = pageTitle },
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = pageTitle,
                largeTitle = pageTitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 16.dp,
                    bottom = adaptiveMainNavigationContentPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                val loadingSize = stringResource(R.string.data_storage_calculating)
                Section(stringResource(R.string.data_storage_section)) {
                    ElevatedPanel {
                        SettingRow(
                            title = stringResource(R.string.data_storage_total),
                            summary = storageUsage?.totalBytes?.readableBytes() ?: loadingSize,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            Text(
                                text = stringResource(R.string.data_storage_total_badge),
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        DividerLine()
                        SettingRow(
                            title = stringResource(R.string.data_storage_app),
                            summary = storageUsage?.appBytes?.readableBytes() ?: loadingSize,
                        ) {
                            Text(
                                text = stringResource(R.string.data_storage_app_badge),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        DividerLine()
                        SettingRow(
                            title = stringResource(R.string.data_storage_user_data),
                            summary = storageUsage?.userDataBytes?.readableBytes() ?: loadingSize,
                        ) {
                            Text(
                                text = stringResource(R.string.data_storage_user_data_badge),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        DividerLine()
                        SettingRow(
                            title = stringResource(R.string.data_cache),
                            summary = storageUsage?.cacheBytes?.readableBytes() ?: loadingSize,
                        ) {
                            Text(
                                text = stringResource(R.string.data_cache_badge),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            storageUsage?.takeIf(AppStorageUsage::hasCodeBreakdown)?.let { usage ->
                item {
                    Section(stringResource(R.string.data_storage_code_breakdown)) {
                        ElevatedPanel {
                            StorageDetailRow(stringResource(R.string.data_storage_apk), usage.apkBytes)
                            DividerLine()
                            StorageDetailRow(stringResource(R.string.data_storage_optimized_code), usage.optimizedCodeBytes)
                            DividerLine()
                            StorageDetailRow(stringResource(R.string.data_storage_dex_metadata), usage.dexMetadataBytes)
                            DividerLine()
                            StorageDetailRow(stringResource(R.string.data_storage_native_libraries), usage.nativeLibraryBytes)
                            DividerLine()
                            StorageDetailRow(stringResource(R.string.data_storage_reference_profile), usage.referenceProfileBytes)
                            DividerLine()
                            StorageDetailRow(stringResource(R.string.data_storage_current_profile), usage.currentProfileBytes)
                        }
                    }
                }
            }

            item {
                Section(stringResource(R.string.app_data_section_overview)) {
                    ElevatedPanel {
                        val status = syncStatus
                        if (status?.partial == true) {
                            Text(stringResource(R.string.pallasync_count_partial))
                            DividerLine()
                        }
                        val viewCountSummary =
                            when {
                                status != null -> {
                                    stringResource(
                                        R.string.pallasync_capacity_details,
                                        stringResource(R.string.pallasync_capacity_viewed_scope),
                                        status.viewedCount,
                                        status.viewedProjectionCount,
                                        status.viewedColdCount,
                                        status.viewedCeiling,
                                    )
                                }

                                state.settings.pallaSyncEnabled -> {
                                    stringResource(R.string.pallasync_count_unavailable)
                                }

                                else -> {
                                    stringResource(R.string.data_items_count, state.settings.viewHistory.size)
                                }
                            }
                        val searchCountSummary =
                            when {
                                status != null -> {
                                    stringResource(
                                        R.string.pallasync_capacity_details,
                                        stringResource(R.string.pallasync_capacity_search_scope),
                                        status.searchCount,
                                        status.searchProjectionCount,
                                        status.searchColdCount,
                                        status.searchCeiling,
                                    )
                                }

                                state.settings.pallaSyncEnabled -> {
                                    stringResource(R.string.pallasync_count_unavailable)
                                }

                                else -> {
                                    stringResource(R.string.data_items_count, state.settings.searchHistory.size)
                                }
                            }
                        val favoriteCountSummary =
                            when {
                                status != null -> {
                                    stringResource(
                                        R.string.pallasync_logical_projection_details,
                                        stringResource(R.string.data_watchlist_tags),
                                        status.favoriteCount,
                                        status.favoriteProjectionCount,
                                        status.favoriteColdCount,
                                    )
                                }

                                state.settings.pallaSyncEnabled -> {
                                    stringResource(R.string.pallasync_count_unavailable)
                                }

                                else -> {
                                    stringResource(R.string.data_items_count, state.settings.favoriteTags.size)
                                }
                            }
                        val muteCountSummary =
                            when {
                                status != null -> {
                                    stringResource(
                                        R.string.pallasync_logical_projection_details,
                                        stringResource(R.string.data_mute_data),
                                        status.muteCount,
                                        status.muteProjectionCount,
                                        status.muteColdCount,
                                    )
                                }

                                state.settings.pallaSyncEnabled -> {
                                    stringResource(R.string.pallasync_count_unavailable)
                                }

                                else -> {
                                    stringResource(R.string.data_items_count, mutedTotal)
                                }
                            }
                        SettingRow(
                            stringResource(R.string.data_view_history),
                            viewCountSummary,
                        ) {
                            Text(
                                stringResource(R.string.data_view_history_badge),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        DividerLine()
                        SettingRow(
                            stringResource(R.string.data_search_history),
                            searchCountSummary,
                        ) {
                            Text(
                                stringResource(R.string.data_search_history_badge),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        DividerLine()
                        SettingRow(
                            stringResource(R.string.data_watchlist_tags),
                            favoriteCountSummary,
                        ) {
                            Text(
                                stringResource(R.string.data_watchlist_tags_badge),
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        DividerLine()
                        SettingRow(stringResource(R.string.data_mute_data), muteCountSummary) {
                            Text(
                                stringResource(R.string.data_mute_data_badge),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontWeight = FontWeight.Bold,
                            )
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
                        SettingLinkRow(stringResource(R.string.data_delete_search_history)) {
                            deleteTarget =
                                AppDataDeleteTarget.SearchHistory
                        }
                        DividerLine()
                        SettingLinkRow(stringResource(R.string.data_delete_watchlist_tags)) {
                            deleteTarget =
                                AppDataDeleteTarget.FavoriteTags
                        }
                        DividerLine()
                        SettingLinkRow(stringResource(R.string.data_delete_mute_data)) { deleteTarget = AppDataDeleteTarget.MuteData }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageDetailRow(
    title: String,
    bytes: Long?,
) {
    SettingRow(
        title = title,
        summary = (bytes ?: 0L).readableBytes(),
    ) {
        Text(
            text = stringResource(R.string.data_storage_android_15_badge),
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun AppDataDeleteTarget.confirmTitle(context: Context): String =
    when (this) {
        AppDataDeleteTarget.Cache -> context.getString(R.string.data_delete_cache)
        AppDataDeleteTarget.ViewHistory -> context.getString(R.string.data_delete_view_history)
        AppDataDeleteTarget.SearchHistory -> context.getString(R.string.data_delete_search_history)
        AppDataDeleteTarget.FavoriteTags -> context.getString(R.string.data_delete_watchlist_tags)
        AppDataDeleteTarget.MuteData -> context.getString(R.string.data_delete_mute_data)
    }

private fun AppDataDeleteTarget.confirmSummary(context: Context): String =
    when (this) {
        AppDataDeleteTarget.Cache -> context.getString(R.string.data_delete_cache_desc)
        AppDataDeleteTarget.ViewHistory -> context.getString(R.string.data_delete_view_history_desc)
        AppDataDeleteTarget.SearchHistory -> context.getString(R.string.data_delete_search_history_desc)
        AppDataDeleteTarget.FavoriteTags -> context.getString(R.string.data_delete_watchlist_tags_desc)
        AppDataDeleteTarget.MuteData -> context.getString(R.string.data_delete_mute_data_desc)
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
