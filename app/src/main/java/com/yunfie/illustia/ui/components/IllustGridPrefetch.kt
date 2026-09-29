package com.yunfie.illustia.ui.components

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import com.yunfie.illustia.models.Illust

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

@Composable
fun PrefetchIllustGridImages(
    items: List<Illust>,
    gridState: LazyGridState,
    enabled: Boolean,
    highQualityImages: Boolean,
    keyPrefix: String = "",
    limit: Int = 12,
) {
    val preferLowDataImages = LocalPreferLowDataImages.current
    val indexByKey =
        remember(items, keyPrefix) {
            items.withIndex().associate { (index, illust) ->
                val key: Any = if (keyPrefix.isEmpty()) illust.id else "$keyPrefix${illust.id}"
                key to index
            }
        }
    val lastVisibleIndex by remember(gridState, indexByKey) {
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

    val urls by remember(items, lastVisibleIndex, enabled, highQualityImages, preferLowDataImages, limit) {
        derivedStateOf(structuralEqualityPolicy()) {
            if (!enabled || items.isEmpty() || lastVisibleIndex < 0) {
                emptyList()
            } else {
                val start = (lastVisibleIndex + 1).coerceAtMost(items.size)
                val end = (start + limit.coerceAtLeast(0)).coerceAtMost(items.size)
                val result = ArrayList<String>(end - start)
                for (index in start until end) {
                    val illust = items[index]
                    result.add(if (highQualityImages && !preferLowDataImages) illust.previewUrl else illust.thumbnailUrl)
                }
                result
            }
        }
    }
    PrefetchPixivImages(urls, enabled = enabled, limit = limit)
}

@Composable
fun PrefetchNovelGridImages(
    items: List<com.yunfie.illustia.models.NovelPreview>,
    gridState: LazyGridState,
    enabled: Boolean,
    limit: Int = 12,
) {
    val indexByKey =
        remember(items) {
            items.withIndex().associate { (index, novel) ->
                novel.id to index
            }
        }
    val lastVisibleIndex by remember(gridState, indexByKey) {
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

    val urls by remember(items, lastVisibleIndex, enabled, limit) {
        derivedStateOf(structuralEqualityPolicy()) {
            if (!enabled || items.isEmpty() || lastVisibleIndex < 0) {
                emptyList()
            } else {
                val start = (lastVisibleIndex + 1).coerceAtMost(items.size)
                val end = (start + limit.coerceAtLeast(0)).coerceAtMost(items.size)
                val result = ArrayList<String>(end - start)
                for (index in start until end) {
                    val novel = items[index]
                    if (novel.coverUrl.isNotBlank()) {
                        result.add(novel.coverUrl)
                    }
                }
                result
            }
        }
    }
    PrefetchPixivImages(urls, enabled = enabled, limit = limit)
}
