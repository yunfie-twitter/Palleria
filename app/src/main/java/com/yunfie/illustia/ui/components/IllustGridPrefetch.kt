package com.yunfie.illustia.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import coil3.compose.LocalPlatformContext
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.platform.PlatformCapabilities

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
    limit: Int = 12,
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

    val urls by remember(items, enabled, highQualityImages, preferLowDataImages, limit, effectiveVelocityLanding, isFastScrolling) {
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
    val context = LocalPlatformContext.current
    val isLowSpec = remember(context) { PlatformCapabilities.isLowSpecDevice(context) }
    val prefetchActive = enabled && (!isLowSpec || (!gridState.isScrollInProgress || (effectiveVelocityLanding && isFastScrolling)))
    PrefetchPixivImages(urls, enabled = prefetchActive, limit = limit)
}

@Composable
fun PrefetchNovelGridImages(
    items: List<com.yunfie.illustia.models.NovelPreview>,
    gridState: LazyGridState,
    enabled: Boolean,
    limit: Int = 12,
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
    val context = LocalPlatformContext.current
    val isLowSpec = remember(context) { PlatformCapabilities.isLowSpecDevice(context) }
    val prefetchActive = enabled && (!isLowSpec || (!gridState.isScrollInProgress || (effectiveVelocityLanding && isFastScrolling)))
    PrefetchPixivImages(urls, enabled = prefetchActive, limit = limit)
}
