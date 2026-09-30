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
    fun `startup read skips Room collections and uses the privacy mirror`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = SettingsStore(context)
            val original = store.read()
            val marker = "startup-room-marker"

            try {
                val incomplete = original.copy(onboardingSetupCompleted = false)
                store.write(incomplete, original)
                store.readStartup().onboardingSetupCompleted shouldBe false
                val updated =
                    original.copy(
                        onboardingSetupCompleted = true,
                        appLanguage = "en",
                        pixivNetworkMode = "compat",
                        pixivImageProxyBaseUrl = "https://i.pixiv.re/",
                        privacyModeEnabled = true,
                        searchAgeRestriction = SearchAgeRestriction.R18G,
                        searchHistory = listOf(marker),
                        favoriteTags = listOf(marker),
                    )
                store.write(updated, incomplete)

                SettingsStore.isPrivacyModeEnabledSync(context).shouldBeTrue()
                store.readStartup().apply {
                    onboardingSetupCompleted shouldBe true
                    appLanguage shouldBe "en"
                    pixivNetworkMode shouldBe "compat"
                    pixivImageProxyBaseUrl shouldBe "https://i.pixiv.re/"
                    searchAgeRestriction shouldBe SearchAgeRestriction.R18G
                    privacyModeEnabled.shouldBeTrue()
                    searchHistory.shouldBeEmpty()
                    favoriteTags.shouldBeEmpty()
                }
                // Verify credentials are preserved when write is called with startup placeholder token
                val testToken = "valid_auth_token_12345"
                val withRealToken = updated.copy(refreshToken = testToken)
                store.write(withRealToken, updated)
                store.read().refreshToken shouldBe testToken

                val startupPlaceholderSettings =
                    store.readStartup().copy(
                        lastSeenAppVersionCode = 999,
                    )
                store.write(startupPlaceholderSettings, store.read())

                val persisted = store.read()
                persisted.refreshToken shouldBe testToken
                persisted.lastSeenAppVersionCode shouldBe 999
            } finally {
                store.write(original, store.read())
            }
        }
    }
}
