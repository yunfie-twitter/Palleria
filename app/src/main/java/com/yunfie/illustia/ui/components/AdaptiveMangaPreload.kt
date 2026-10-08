@file:Suppress("MatchingDeclarationName")

package com.yunfie.illustia.ui.components

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

enum class MangaReadingDirection {
    Forward,
    Reverse,
}

private const val MAX_PRELOAD_PAGES = 3

/**
 * Calculates reading direction based on page navigation delta.
 */
fun calculateMangaReadingDirection(
    currentPage: Int,
    previousPage: Int,
    currentDirection: MangaReadingDirection = MangaReadingDirection.Forward,
): MangaReadingDirection =
    when {
        currentPage < previousPage -> MangaReadingDirection.Reverse
        currentPage > previousPage -> MangaReadingDirection.Forward
        else -> currentDirection
    }

/**
 * Determines which pages to preload depending on whether the reader is navigating forward or backward.
 */
fun resolveAdaptivePreloadUrls(
    currentPage: Int,
    imageUrls: List<String>,
    direction: MangaReadingDirection,
    maxPages: Int = MAX_PRELOAD_PAGES,
): List<String> {
    if (imageUrls.isEmpty()) return emptyList()
    val urls = mutableListOf<String>()
    if (direction == MangaReadingDirection.Reverse) {
        if (currentPage - 1 in imageUrls.indices) urls.add(imageUrls[currentPage - 1])
        if (currentPage - 2 in imageUrls.indices) urls.add(imageUrls[currentPage - 2])
        if (currentPage + 1 in imageUrls.indices) urls.add(imageUrls[currentPage + 1])
    } else {
        if (currentPage + 1 in imageUrls.indices) urls.add(imageUrls[currentPage + 1])
        if (currentPage + 2 in imageUrls.indices) urls.add(imageUrls[currentPage + 2])
        if (currentPage - 1 in imageUrls.indices) urls.add(imageUrls[currentPage - 1])
    }
    return urls.take(maxPages)
}

/**
 * Automatically adjusts image prefetching direction (forward vs reverse) based on user's reading behavior.
 * When the user reads backwards (e.g. Japanese manga right-to-left, or checking punchline first),
 * reverses the prefetch queue so pages are loaded instantly with zero wait time.
 */
@Composable
fun AdaptiveMangaPreloader(
    pagerState: PagerState,
    imageUrls: List<String>,
    enabled: Boolean = true,
    adaptiveDirectionEnabled: Boolean = true,
) {
    if (!enabled || imageUrls.size <= 1) return

    var detectedDirection by remember(imageUrls) { mutableStateOf(MangaReadingDirection.Forward) }
    var previousPage by remember(imageUrls) { mutableIntStateOf(pagerState.currentPage) }

    LaunchedEffect(pagerState.currentPage, adaptiveDirectionEnabled) {
        val current = pagerState.currentPage
        if (adaptiveDirectionEnabled && current != previousPage) {
            detectedDirection = calculateMangaReadingDirection(current, previousPage, detectedDirection)
            previousPage = current
        }
    }

    val preloadUrls =
        remember(pagerState.currentPage, detectedDirection, imageUrls) {
            resolveAdaptivePreloadUrls(
                currentPage = pagerState.currentPage,
                imageUrls = imageUrls,
                direction = detectedDirection,
                maxPages = MAX_PRELOAD_PAGES,
            )
        }

    PrefetchPixivImages(
        urls = preloadUrls,
        enabled = enabled,
        limit = MAX_PRELOAD_PAGES,
    )
}
