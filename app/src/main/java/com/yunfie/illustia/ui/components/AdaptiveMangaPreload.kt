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
import kotlinx.coroutines.delay

enum class MangaReadingDirection {
    Forward,
    Reverse,
}

private const val MAX_PRELOAD_PAGES = 1
private const val PRELOAD_SETTLE_DELAY_MS = 250L

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
    if (imageUrls.isEmpty() || maxPages <= 0) return emptyList()
    val urls = ArrayList<String>(maxPages)
    fun addIfPresent(page: Int) {
        if (urls.size < maxPages && page in imageUrls.indices) urls.add(imageUrls[page])
    }
    if (direction == MangaReadingDirection.Reverse) {
        // The pager composes the adjacent page itself; prefetch only the page beyond it.
        addIfPresent(currentPage - 2)
        addIfPresent(currentPage - 1)
        addIfPresent(currentPage + 1)
    } else {
        addIfPresent(currentPage + 2)
        addIfPresent(currentPage + 1)
        addIfPresent(currentPage - 1)
    }
    return urls
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

    // Let the visible page start decoding before adding another thumbnail decode.
    var preloadReady by remember(imageUrls) { mutableStateOf(false) }
    LaunchedEffect(pagerState.settledPage, imageUrls) {
        preloadReady = false
        delay(PRELOAD_SETTLE_DELAY_MS)
        preloadReady = true
    }

    // Key on settledPage, not currentPage: currentPage changes on every drag frame and would
    // re-run the preload resolution (and PrefetchPixivImages) mid-swipe for no benefit.
    val settledPage = pagerState.settledPage
    val preloadUrls =
        remember(settledPage, detectedDirection, imageUrls) {
            resolveAdaptivePreloadUrls(
                currentPage = settledPage,
                imageUrls = imageUrls,
                direction = detectedDirection,
                maxPages = MAX_PRELOAD_PAGES,
            )
        }

    PrefetchPixivImages(
        urls = preloadUrls,
        enabled = enabled && preloadReady,
        limit = MAX_PRELOAD_PAGES,
    )
}
