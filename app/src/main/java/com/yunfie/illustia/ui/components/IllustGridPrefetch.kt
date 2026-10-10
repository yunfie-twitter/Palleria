package com.yunfie.illustia.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import coil3.compose.LocalPlatformContext
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.platform.PlatformCapabilities
import kotlinx.coroutines.flow.collect

val LocalVelocityLandingPrefetchEnabled = compositionLocalOf { true }

/** Keys keep banners, loading rows and sorted lists out of artwork index calculations. */
internal fun upcomingArtworkIndices(
    indexByKey: Map<Any, Int>,
    visibleKeys: Iterable<Any>,
    itemCount: Int,
    limit: Int,
): IntRange {
    var lastVisible = -1
    for (key in visibleKeys) {
        val index = indexByKey[key] ?: continue
        if (index > lastVisible) {
            lastVisible = index
        }
    }
    if (lastVisible < 0) return IntRange.EMPTY
    val start = (lastVisible + 1).coerceAtMost(itemCount)
    return start until (start + limit.coerceAtLeast(0)).coerceAtMost(itemCount)
}

internal const val FAST_SCROLL_LANDING_OFFSET = 8
internal const val FAST_SCROLL_INDEX_DELTA_THRESHOLD = 2
internal const val INITIAL_PREFETCH_DELAY_MS = 500L

internal fun nextFastScrollLandingState(
    previousFastScroll: Boolean,
    wasScrolling: Boolean,
    previousIndex: Int,
    currentIndex: Int,
    isScrolling: Boolean,
): Boolean {
    if (!isScrolling) return previousFastScroll
    val delta = currentIndex - previousIndex
    return if (delta == 0 && wasScrolling) previousFastScroll else delta >= FAST_SCROLL_INDEX_DELTA_THRESHOLD
}

/** Keys keep banners, loading rows and sorted lists out of artwork index calculations. */
internal fun calculatePrefetchRange(
    lastVisibleIndex: Int,
    itemCount: Int,
    limit: Int,
    isFastScrollingDown: Boolean = false,
    landingOffset: Int = FAST_SCROLL_LANDING_OFFSET,
): IntRange {
    if (lastVisibleIndex < 0 || itemCount <= 0 || limit <= 0) return IntRange.EMPTY
    val baseStart =
        if (isFastScrollingDown) {
            (lastVisibleIndex + landingOffset).coerceAtMost(itemCount)
        } else {
            (lastVisibleIndex + 1).coerceAtMost(itemCount)
        }
    val end = (baseStart + limit).coerceAtMost(itemCount)
    return baseStart until end
}

@Composable
fun PrefetchIllustGridImages(
    items: List<Illust>,
    gridState: LazyGridState,
    enabled: Boolean,
    highQualityImages: Boolean,
    keyPrefix: String = "",
    limit: Int = 6,
    velocityLandingEnabled: Boolean = true,
) {
    val preferLowDataImages = LocalPreferLowDataImages.current
    val indexByKey =
        remember(items, keyPrefix) {
            items.withIndex().associate { (index, illust) ->
                val key: Any = if (keyPrefix.isEmpty()) illust.id else "$keyPrefix${illust.id}"
                key to index
            }
        }

    var isFastScrolling by remember(gridState) { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(gridState) {
        var previousIndex = gridState.firstVisibleItemIndex
        var wasScrolling = gridState.isScrollInProgress
        var previousFastScroll = false
        snapshotFlow {
            gridState.firstVisibleItemIndex to gridState.isScrollInProgress
        }.collect { (currentIndex, isScrolling) ->
            val nextFastScroll =
                nextFastScrollLandingState(
                    previousFastScroll = previousFastScroll,
                    wasScrolling = wasScrolling,
                    previousIndex = previousIndex,
                    currentIndex = currentIndex,
                    isScrolling = isScrolling,
                )
            if (nextFastScroll != previousFastScroll) {
                previousFastScroll = nextFastScroll
                isFastScrolling = nextFastScroll
            }
            previousIndex = currentIndex
            wasScrolling = isScrolling
        }
    }

    var initialDelayPassed by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(INITIAL_PREFETCH_DELAY_MS)
        initialDelayPassed = true
    }

    // Pause prefetch during scrolling across all devices to prevent disk cache contention and decoder jank.
    // Settle 200ms after scrolling stops before resuming prefetch.
    var isScrollSettled by remember { androidx.compose.runtime.mutableStateOf(!gridState.isScrollInProgress) }
    androidx.compose.runtime.LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            isScrollSettled = false
        } else {
            kotlinx.coroutines.delay(200L)
            isScrollSettled = true
        }
    }

    val lastVisibleIndexState =
        remember(gridState, indexByKey) {
            derivedStateOf {
                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                var last = -1
                for (i in visibleItems.indices) {
                    val index = indexByKey[visibleItems[i].key] ?: continue
                    if (index > last) {
                        last = index
                    }
                }
                last
            }
        }

    val landingPrefetchSetting = LocalVelocityLandingPrefetchEnabled.current
    val effectiveVelocityLanding = velocityLandingEnabled && landingPrefetchSetting

    val urlsState =
        remember(items, enabled, highQualityImages, preferLowDataImages, limit, effectiveVelocityLanding) {
            derivedStateOf(structuralEqualityPolicy()) {
                val lastVisibleIndex = lastVisibleIndexState.value
                if (!enabled || items.isEmpty() || lastVisibleIndex < 0) {
                    emptyList()
                } else {
                    val range =
                        calculatePrefetchRange(
                            lastVisibleIndex = lastVisibleIndex,
                            itemCount = items.size,
                            limit = limit,
                            isFastScrollingDown = effectiveVelocityLanding && isFastScrolling,
                        )
                    val result = ArrayList<String>(range.last - range.first + 1)
                    for (index in range) {
                        val illust = items[index]
                        result.add(if (highQualityImages && !preferLowDataImages) illust.previewUrl else illust.thumbnailUrl)
                    }
                    result
                }
            }
        }
    val prefetchActive = enabled && initialDelayPassed && isScrollSettled
    val urls = if (prefetchActive) urlsState.value else emptyList()
    PrefetchPixivImages(urls, enabled = prefetchActive, limit = limit)
}

@Composable
fun PrefetchNovelGridImages(
    items: List<com.yunfie.illustia.models.NovelPreview>,
    gridState: LazyGridState,
    enabled: Boolean,
    limit: Int = 6,
    velocityLandingEnabled: Boolean = true,
) {
    val indexByKey =
        remember(items) {
            items.withIndex().associate { (index, novel) ->
                novel.id to index
            }
        }

    var lastFirstVisibleIndex by remember(gridState) { androidx.compose.runtime.mutableIntStateOf(gridState.firstVisibleItemIndex) }
    var isFastScrolling by remember(gridState) { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(gridState.firstVisibleItemIndex, gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            val delta = gridState.firstVisibleItemIndex - lastFirstVisibleIndex
            isFastScrolling = delta >= FAST_SCROLL_INDEX_DELTA_THRESHOLD
            lastFirstVisibleIndex = gridState.firstVisibleItemIndex
        } else {
            isFastScrolling = false
            lastFirstVisibleIndex = gridState.firstVisibleItemIndex
        }
    }

    var initialDelayPassed by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(INITIAL_PREFETCH_DELAY_MS)
        initialDelayPassed = true
    }

    // Pause prefetch during scrolling across all devices to prevent disk cache contention and decoder jank.
    // Settle 200ms after scrolling stops before resuming prefetch.
    var isScrollSettled by remember { androidx.compose.runtime.mutableStateOf(!gridState.isScrollInProgress) }
    androidx.compose.runtime.LaunchedEffect(gridState.isScrollInProgress) {
        if (gridState.isScrollInProgress) {
            isScrollSettled = false
        } else {
            kotlinx.coroutines.delay(200L)
            isScrollSettled = true
        }
    }

    val lastVisibleIndexState =
        remember(gridState, indexByKey) {
            derivedStateOf {
                val visibleItems = gridState.layoutInfo.visibleItemsInfo
                var last = -1
                for (i in visibleItems.indices) {
                    val index = indexByKey[visibleItems[i].key] ?: continue
                    if (index > last) {
                        last = index
                    }
                }
                last
            }
        }

    val landingPrefetchSetting = LocalVelocityLandingPrefetchEnabled.current
    val effectiveVelocityLanding = velocityLandingEnabled && landingPrefetchSetting

    val urls by remember(items, enabled, limit, effectiveVelocityLanding, isFastScrolling) {
        derivedStateOf(structuralEqualityPolicy()) {
            val lastVisibleIndex = lastVisibleIndexState.value
            if (!enabled || items.isEmpty() || lastVisibleIndex < 0) {
                emptyList()
            } else {
                val range =
                    calculatePrefetchRange(
                        lastVisibleIndex = lastVisibleIndex,
                        itemCount = items.size,
                        limit = limit,
                        isFastScrollingDown = effectiveVelocityLanding && isFastScrolling,
                    )
                val result = ArrayList<String>(range.last - range.first + 1)
                for (index in range) {
                    val novel = items[index]
                    if (novel.coverUrl.isNotBlank()) {
                        result.add(novel.coverUrl)
                    }
                }
                result
            }
        }
    }
    val prefetchActive = enabled && initialDelayPassed && isScrollSettled
    PrefetchPixivImages(urls, enabled = prefetchActive, limit = limit)
}
