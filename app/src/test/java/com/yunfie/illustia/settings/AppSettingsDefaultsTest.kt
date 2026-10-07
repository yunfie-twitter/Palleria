package com.yunfie.illustia.settings

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsDefaultsTest {
    @Test
    fun `new UX feature flags have defaultEnabled set to true in FeatureFlag`() {
        FeatureFlag.OfflineStaleCache.defaultEnabled shouldBe true
        FeatureFlag.BookmarkHapticBurst.defaultEnabled shouldBe true
        FeatureFlag.ImageBlurPreview.defaultEnabled shouldBe true
    }

    @Test
    fun `standardized features default to true in default AppSettings`() {
        val settings = AppSettings()
        settings.gridPinchToZoom shouldBe true
        settings.quickPeekEnabled shouldBe true
        settings.quickPeekSharedElementTransition shouldBe true
        settings.dynamicAmbientViewerEnabled shouldBe true
        settings.imageViewerSwipeToDismissEnabled shouldBe true
        settings.embedMetadata shouldBe true
        settings.ugoiraAutoPlay shouldBe true
        settings.commandPaletteEnabled shouldBe true

        settings.isFeatureEnabled(FeatureFlag.OfflineStaleCache) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.BookmarkHapticBurst) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.ImageBlurPreview) shouldBe true
    }

    @Test
    fun `feature flag override takes precedence over defaultEnabled`() {
        val settings =
            AppSettings(
                featureFlags =
                    mapOf(
                        FeatureFlag.OfflineStaleCache.key to false,
                        FeatureFlag.BookmarkHapticBurst.key to false,
                        FeatureFlag.ImageBlurPreview.key to false,
                        FeatureFlag.ShortsFeed.key to true,
                    ),
            )

        settings.isFeatureEnabled(FeatureFlag.OfflineStaleCache) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.BookmarkHapticBurst) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.ImageBlurPreview) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.ShortsFeed) shouldBe true
    }

    @Test
    fun `FeatureFlag fromKey finds matching enum or returns null`() {
        FeatureFlag.fromKey("flag_offline_stale_cache") shouldBe FeatureFlag.OfflineStaleCache
        FeatureFlag.fromKey("flag_bookmark_haptic_burst") shouldBe FeatureFlag.BookmarkHapticBurst
        FeatureFlag.fromKey("flag_image_blur_preview") shouldBe FeatureFlag.ImageBlurPreview
        FeatureFlag.fromKey("non_existent_key").shouldBeNull()
    }

    @Test
    fun `useHighQualityFeedImages evaluates correctly based on highQualityImages and feedPreviewQuality`() {
        AppSettings(highQualityImages = true, feedPreviewQuality = "high").useHighQualityFeedImages shouldBe true
        AppSettings(highQualityImages = true, feedPreviewQuality = "medium").useHighQualityFeedImages shouldBe true
        AppSettings(highQualityImages = true, feedPreviewQuality = "low").useHighQualityFeedImages shouldBe false
        AppSettings(highQualityImages = false, feedPreviewQuality = "high").useHighQualityFeedImages shouldBe false
    }

    @Test
    fun `column counts have valid initial values`() {
        val settings = AppSettings()
        settings.verticalColumnCount shouldBe 2
        settings.horizontalColumnCount shouldBe 4
        settings.relatedIllustColumnCount shouldBe 3
    }
}
