package com.yunfie.illustia.settings

import com.yunfie.illustia.settings.store.decodeFeatureFlags
import com.yunfie.illustia.settings.store.encodeFeatureFlags
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
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
                        FeatureFlag.BookmarkHapticBurst.key to false,
                    ),
            )
        settings.isFeatureEnabled(FeatureFlag.ShortsFeed) shouldBe true
        settings.isFeatureEnabled(FeatureFlag.HideHomeNovelButton) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.BookmarkHapticBurst) shouldBe false
        settings.isFeatureEnabled(FeatureFlag.ArtworkDynamicTheme) shouldBe false
    }

    @Test
    fun `all feature flags have unique keys and valid resource ids`() {
        val allKeys = FeatureFlag.entries.map { it.key }
        allKeys.distinct() shouldHaveSize FeatureFlag.entries.size

        FeatureFlag.entries.forEach { flag ->
            (flag.titleRes != 0) shouldBe true
            (flag.descRes != 0) shouldBe true
        }
    }

    @Test
    fun `fromKey resolves each flag correctly and returns null for unknown key`() {
        FeatureFlag.entries.forEach { flag ->
            FeatureFlag.fromKey(flag.key) shouldBe flag
        }
        FeatureFlag.fromKey("unknown_flag").shouldBeNull()
        FeatureFlag.fromKey("").shouldBeNull()
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
        decodeFeatureFlags("{ \"flag_test\": true, \"flag_other\": false }") shouldBe
            mapOf("flag_test" to true, "flag_other" to false)
    }
}
