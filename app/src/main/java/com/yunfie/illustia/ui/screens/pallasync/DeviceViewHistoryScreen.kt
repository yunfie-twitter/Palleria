package com.yunfie.illustia.ui.screens.pallasync

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.pallasync.PallaSyncClientStatus
import com.yunfie.illustia.pallasync.PalleriaSyncManager
import com.yunfie.illustia.pallasync.data.HistoryCursor
import com.yunfie.illustia.ui.components.EmptyState
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.IllustCard
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.PrefetchIllustGridImages
import com.yunfie.illustia.ui.components.adaptiveIllustColumns
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Import
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Theme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.lazy.grid.items as gridItems

@Composable
fun DeviceViewHistoryScreen(
    deviceId: String,
    deviceName: String,
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onIllustClick: (Illust) -> Unit,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val context = LocalContext.current
    val syncManager = remember { PalleriaSyncManager(context = context) }

    var viewHistory by remember { mutableStateOf<List<Illust>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var cursor by remember(deviceId) { mutableStateOf<HistoryCursor?>(null) }
    var nextCursor by remember(deviceId) { mutableStateOf<HistoryCursor?>(null) }
    var partial by remember(deviceId) { mutableStateOf(true) }
    var clientStatus by remember { mutableStateOf<PallaSyncClientStatus?>(null) }
    var failure by remember(deviceId) { mutableStateOf<String?>(null) }
    val gridState = rememberLazyGridState()
    val feedHighQuality = state.settings.useHighQualityFeedImages
    val showAiBadge = state.settings.showAiBadge

    LaunchedEffect(deviceId, cursor) {
        isLoading = true
        try {
            val page = syncManager.getDeviceHistoryPage(deviceId, cursor)
            // Replace the display window; never accumulate the entire logical archive in RAM.
            viewHistory = page.items
            nextCursor = page.next
            partial = page.partial
            clientStatus = syncManager.getClientStatus()
            failure = null
            gridState.scrollToItem(0)
        } catch (error: IllegalStateException) {
            failure = error.message?.takeIf { it.startsWith("C_") } ?: "C_MISSING_DEPENDENCY"
            if (cursor != null) cursor = null // §16.5: a concurrent reorder restarts keyset paging.
        } finally {
            isLoading = false
        }
    }

    val bookmarkedIds =
        remember(state.bookmarkItems) {
            val items = state.bookmarkItems
            val set = HashSet<Long>(items.size)
            for (i in items.indices) {
                set.add(items[i].id)
            }
            set
        }

    val historyList =
        remember(viewHistory, bookmarkedIds) {
            val list = viewHistory ?: return@remember emptyList()
            list.map { illust ->
                if (illust.id in bookmarkedIds && !illust.isBookmarked) {
                    illust.copy(isBookmarked = true)
                } else {
                    illust
                }
            }
        }

    PrefetchIllustGridImages(
        items = historyList,
        gridState = gridState,
        enabled = state.settings.prefetchImages,
        highQualityImages = feedHighQuality,
        keyPrefix = "device_history_",
    )

    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = deviceName,
                largeTitle = deviceName,
                subtitle = stringResource(R.string.pallasync_device_view_history),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
            )
        },
    ) { scaffoldPadding ->
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(adaptiveIllustColumns(state.settings)),
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 14.dp,
                    bottom = 96.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                PallaSyncDeviceRevocationControl(syncManager, deviceId)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.pallasync_v3_history_window))
            }
            if (partial || clientStatus?.partial == true) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.pallasync_v3_history_partial))
                }
            }
            clientStatus?.let { status ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(
                            R.string.pallasync_v3_history_sync_positions,
                            status.downloadedSeq,
                            status.verifiedSeq,
                            status.materializedSeq,
                        ),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(
                            R.string.pallasync_capacity_details,
                            stringResource(R.string.pallasync_capacity_seen_scope),
                            status.seenCount,
                            status.seenProjectionCount,
                            status.seenColdCount,
                            status.seenCeiling,
                        ),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(
                            R.string.pallasync_capacity_details,
                            stringResource(R.string.pallasync_capacity_viewed_scope),
                            status.viewedCount,
                            status.viewedProjectionCount,
                            status.viewedColdCount,
                            status.viewedCeiling,
                        ),
                    )
                }
                if (status.safetyLimitReached) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.pallasync_safety_limit_reached))
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(
                            R.string.pallasync_v3_history_transparency,
                            status.verifiedTransparencyIndex,
                            status.observedTransparencyTargetIndex,
                        ),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(
                            R.string.pallasync_v3_history_pending,
                            status.pendingOutbox,
                            status.authorGaps,
                            status.unresolvedQuarantine,
                            status.writeState,
                        ),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(
                            R.string.pallasync_v3_history_capacity,
                            status.searchCount,
                            status.searchCeiling,
                            status.viewedCount,
                            status.viewedCeiling,
                            status.seenCount,
                            status.seenCeiling,
                        ),
                    )
                }
            }
            failure?.let { message -> item(span = { GridItemSpan(maxLineSpan) }) { Text(message) } }
            if (isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CircularProgressIndicator(modifier = Modifier.padding(32.dp).fillMaxWidth())
                }
            } else if (historyList.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.search_empty_illust))
                }
            } else {
                gridItems(historyList, key = { it.id }, contentType = { "illust_card" }) { illust ->
                    IllustCard(
                        illust = illust,
                        isSelected = false,
                        onBookmark = { viewModel.toggleBookmark(illust) },
                        onClick = { onIllustClick(illust) },
                        onLongClick = { },
                        highQualityImages = feedHighQuality,
                        showAiBadge = showAiBadge,
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(
                    text = stringResource(R.string.pallasync_v3_history_first),
                    enabled = !isLoading && cursor != null,
                    onClick = { cursor = null },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                TextButton(
                    text = stringResource(R.string.pallasync_v3_history_older),
                    enabled = !isLoading && nextCursor != null,
                    onClick = { cursor = nextCursor },
                )
            }
        }
    }
}
