package com.yunfie.illustia.settings

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsDefaultsTest {
    @Test
    fun `standardized features have defaultEnabled set to true in FeatureFlag`() {
        FeatureFlag.CommandPalette.defaultEnabled shouldBe true
        FeatureFlag.CustomAppIcon.defaultEnabled shouldBe true
        FeatureFlag.EmbedMetadata.defaultEnabled shouldBe true
        FeatureFlag.QuickPeek.defaultEnabled shouldBe true
        FeatureFlag.GridPinchToZoomColumns.defaultEnabled shouldBe true
        FeatureFlag.QuickPeekSharedElementTransition.defaultEnabled shouldBe true
        FeatureFlag.UgoiraAutoPlay.defaultEnabled shouldBe true
    }

    @Test
    fun `standardized features default to true in default AppSettings`() {
        val settings = AppSettings()
        settings.gridPinchToZoom shouldBe true
        settings.quickPeekEnabled shouldBe true
        settings.quickPeekSharedElementTransition shouldBe true
        settings.dynamicAmbientViewerEnabled shouldBe true
        settings.imageViewerSwipeToDismissEnabled shouldBe true

        settings.isFeatureEnabled(FeatureFlag.CommandPalette) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.CustomAppIcon) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.EmbedMetadata) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.QuickPeek) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.GridPinchToZoomColumns) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.QuickPeekSharedElementTransition) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.UgoiraAutoPlay) shouldBe true
    }

    @Test
    fun `feature flag override takes precedence over defaultEnabled`() {
        val settings =
            AppSettings(
                featureFlags =
                    mapOf(
                        FeatureFlag.CommandPalette.key to false,
                        FeatureFlag.CustomAppIcon.key to false,
                        FeatureFlag.EmbedMetadata.key to false,
                        FeatureFlag.QuickPeek.key to false,
                        FeatureFlag.GridPinchToZoomColumns.key to false,
                        FeatureFlag.QuickPeekSharedElementTransition.key to false,
                        FeatureFlag.UgoiraAutoPlay.key to false,
                        FeatureFlag.ShortsFeed.key to true,
                    ),
            )

        settings.isFeatureEnabled(FeatureFlag.CommandPalette) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.CustomAppIcon) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.EmbedMetadata) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.QuickPeek) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.GridPinchToZoomColumns) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.QuickPeekSharedElementTransition) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.UgoiraAutoPlay) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.ShortsFeed) shouldBe true
    }

    @Test
    fun `FeatureFlag fromKey finds matching enum or returns null`() {
        FeatureFlag.fromKey("flag_command_palette") shouldBe FeatureFlag.CommandPalette
        FeatureFlag.fromKey("flag_custom_app_icon") shouldBe FeatureFlag.CustomAppIcon
        FeatureFlag.fromKey("flag_embed_metadata") shouldBe FeatureFlag.EmbedMetadata
        FeatureFlag.fromKey("flag_quick_peek") shouldBe FeatureFlag.QuickPeek
        FeatureFlag.fromKey("flag_grid_pinch_to_zoom_columns") shouldBe FeatureFlag.GridPinchToZoomColumns
        FeatureFlag.fromKey("flag_quick_peek_shared_element_transition") shouldBe FeatureFlag.QuickPeekSharedElementTransition
        FeatureFlag.fromKey("flag_ugoira_auto_play") shouldBe FeatureFlag.UgoiraAutoPlay
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
