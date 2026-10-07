package com.yunfie.illustia.data

import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.LoadState
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import io.kotest.matchers.shouldBe
import org.junit.Test

class OfflineStaleCacheTest {
    private fun createDummyIllust(id: Long) =
        Illust(
            id = id,
            title = "Illust $id",
            type = "illust",
            caption = "",
            artistId = 1L,
            artistName = "Artist",
            artistAvatarUrl = null,
            squareImageUrl = "https://example.com/$id/sq.jpg",
            mediumImageUrl = "https://example.com/$id/med.jpg",
            imageUrl = "https://example.com/$id/orig.jpg",
            originalImageUrl = null,
            tags = emptyList(),
            pageCount = 1,
            isBookmarked = false,
        )

    @Test
    fun `isOfflineCached defaults to false on default IllustiaUiState`() {
        val state = IllustiaUiState()
        state.isOfflineCached shouldBe false
    }

    @Test
    fun `FeatureFlag OfflineStaleCache defaults to enabled`() {
        val settings = AppSettings()
        settings.isFeatureEnabled(FeatureFlag.OfflineStaleCache) shouldBe true
    }

    @Test
    fun `stale cached items are preserved when offline stale cache flag is enabled`() {
        val existingItems = listOf(createDummyIllust(1L), createDummyIllust(2L))
        val settings = AppSettings()

        val isStaleCacheEnabled = settings.isFeatureEnabled(FeatureFlag.OfflineStaleCache)
        val hasExistingItems = existingItems.isNotEmpty()

        val resultingState =
            if (isStaleCacheEnabled && hasExistingItems) {
                IllustiaUiState(
                    homeItems = existingItems,
                    loadState = LoadState.Idle,
                    isOfflineCached = true,
                )
            } else {
                IllustiaUiState(
                    homeItems = emptyList(),
                    loadState = LoadState.Error("Network error"),
                    isOfflineCached = false,
                )
            }

        resultingState.isOfflineCached shouldBe true
        resultingState.homeItems.size shouldBe 2
        resultingState.loadState shouldBe LoadState.Idle
    }

    @Test
    fun `error state is shown when existing items are empty even if flag is enabled`() {
        val existingItems = emptyList<Illust>()
        val settings = AppSettings()

        val isStaleCacheEnabled = settings.isFeatureEnabled(FeatureFlag.OfflineStaleCache)
        val hasExistingItems = existingItems.isNotEmpty()

        val resultingState =
            if (isStaleCacheEnabled && hasExistingItems) {
                IllustiaUiState(
                    homeItems = existingItems,
                    loadState = LoadState.Idle,
                    isOfflineCached = true,
                )
            } else {
                IllustiaUiState(
                    homeItems = emptyList(),
                    loadState = LoadState.Error("Network error"),
                    isOfflineCached = false,
                )
            }

        resultingState.isOfflineCached shouldBe false
        resultingState.homeItems shouldBe emptyList()
        resultingState.loadState shouldBe LoadState.Error("Network error")
    }

    @Test
    fun `error state is shown when flag is disabled even if existing items exist`() {
        val existingItems = listOf(createDummyIllust(1L))
        val settings = AppSettings(featureFlags = mapOf(FeatureFlag.OfflineStaleCache.key to false))

        val isStaleCacheEnabled = settings.isFeatureEnabled(FeatureFlag.OfflineStaleCache)
        val hasExistingItems = existingItems.isNotEmpty()

        val resultingState =
            if (isStaleCacheEnabled && hasExistingItems) {
                IllustiaUiState(
                    homeItems = existingItems,
                    loadState = LoadState.Idle,
                    isOfflineCached = true,
                )
            } else {
                IllustiaUiState(
                    homeItems = emptyList(),
                    loadState = LoadState.Error("Network error"),
                    isOfflineCached = false,
                )
            }

        resultingState.isOfflineCached shouldBe false
        resultingState.loadState shouldBe LoadState.Error("Network error")
    }
}
