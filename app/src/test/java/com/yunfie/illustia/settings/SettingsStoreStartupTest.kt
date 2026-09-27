package com.yunfie.illustia.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.models.SearchAgeRestriction
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class SettingsStoreStartupTest {
    @Test
    fun `setup completion and choices survive the startup settings read`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = SettingsStore(context)
            val original = store.read()
            try {
                val incomplete = original.copy(onboardingSetupCompleted = false)
                store.write(incomplete, original)
                store.readStartup().onboardingSetupCompleted shouldBe false
                val completed =
                    incomplete.copy(
                        onboardingSetupCompleted = true,
                        appLanguage = "en",
                        pixivNetworkMode = "compat",
                        pixivImageProxyBaseUrl = "https://i.pixiv.re/",
                    )
                store.write(completed, incomplete)
                store.readStartup().apply {
                    onboardingSetupCompleted shouldBe true
                    appLanguage shouldBe "en"
                    pixivNetworkMode shouldBe "compat"
                    pixivImageProxyBaseUrl shouldBe "https://i.pixiv.re/"
                }
            } finally {
                store.write(original, store.read())
            }
        }
    }

    @Test
    fun `startup read skips Room collections and uses the privacy mirror`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = SettingsStore(context)
            val original = store.read()
            val marker = "startup-room-marker"

            try {
                val updated =
                    original.copy(
                        privacyModeEnabled = true,
                        searchAgeRestriction = SearchAgeRestriction.R18G,
                        searchHistory = listOf(marker),
                        favoriteTags = listOf(marker),
                    )
                store.write(updated, original)

                SettingsStore.isPrivacyModeEnabledSync(context).shouldBeTrue()
                store.readStartup().apply {
                    searchAgeRestriction shouldBe SearchAgeRestriction.R18G
                    privacyModeEnabled.shouldBeTrue()
                    searchHistory.shouldBeEmpty()
                    favoriteTags.shouldBeEmpty()
                }
                store.read().apply {
                    searchAgeRestriction shouldBe SearchAgeRestriction.R18G
                    searchHistory.shouldContain(marker)
                    favoriteTags.shouldContain(marker)
                }
            } finally {
                store.write(original, store.read())
            }
        }
    }
}
