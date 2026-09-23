package com.yunfie.illustia.settings

import com.yunfie.illustia.settings.store.decodeFeatureFlags
import com.yunfie.illustia.settings.store.encodeFeatureFlags
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FeatureFlagTest {
    @Test
    fun `isFeatureEnabled returns default when not explicitly overridden`() {
        val settings = AppSettings()
        FeatureFlag.entries.forEach { flag ->
            settings.isFeatureEnabled(flag) shouldBe flag.defaultEnabled
        }
    }

    @Test
    fun `isFeatureEnabled returns overridden value when present in map`() {
        val settings =
            AppSettings(
                featureFlags =
                    mapOf(
                        FeatureFlag.ShortsFeed.key to true,
                        FeatureFlag.HideHomeNovelButton.key to false,
                        FeatureFlag.CustomAppIcon.key to true,
                    ),
            )
        settings.isFeatureEnabled(FeatureFlag.ShortsFeed) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.HideHomeNovelButton) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.CustomAppIcon) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.ArtworkDynamicTheme) shouldBe false
    }

    @Test
    fun `encode and decode feature flags roundtrip correctly`() {
        val flags =
            mapOf(
                FeatureFlag.ShortsFeed.key to true,
                FeatureFlag.ArtworkDynamicTheme.key to true,
                FeatureFlag.NavigationCustomization.key to false,
            )
        val json = encodeFeatureFlags(flags)
        val decoded = decodeFeatureFlags(json)
        decoded shouldBe flags
    }

    @Test
    fun `decodeFeatureFlags handles blank or invalid json gracefully`() {
        decodeFeatureFlags(null) shouldBe emptyMap()
        decodeFeatureFlags("") shouldBe emptyMap()
        decodeFeatureFlags("invalid json") shouldBe emptyMap()
    }
}
