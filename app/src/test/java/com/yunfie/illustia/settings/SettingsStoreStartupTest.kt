package com.yunfie.illustia.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.models.SearchAgeRestriction
import com.yunfie.illustia.models.StoredAccount
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
                verifyAuthOnlyRotation()
            } finally {
                store.write(original, store.read())
            }
        }
    }

    private fun verifyAuthOnlyRotation() {
        runBlocking {
            val store = SettingsStore(ApplicationProvider.getApplicationContext<Context>())
            val original = store.read()
            try {
                val base =
                    store.write(
                        original.copy(
                            refreshToken = "before",
                            searchHistory = listOf("keep"),
                            mutedUsers = listOf(42),
                            accounts =
                                listOf(
                                    StoredAccount("one", "one", null, "before", 9),
                                    StoredAccount("two", "two", null, "other", 10),
                                ),
                        ),
                        original,
                    )
                store.persistAuth(
                    com.yunfie.illustia.models
                        .PixivSession("access", "rotated", 9, System.currentTimeMillis() + 3_600_000),
                )
                store.readAuth().refreshToken shouldBe "rotated"
                store.readFeedSettings().mutedUsers shouldBe listOf(42L)
                store.readFeedSettings().searchHistory.shouldBeEmpty()
                store.write(base.copy(themeMode = "dark"), base)
                store.read().apply {
                    refreshToken shouldBe "rotated"
                    searchHistory shouldBe listOf("keep")
                    mutedUsers shouldBe listOf(42L)
                    accounts.map { it.refreshToken } shouldBe listOf("rotated", "other")
                    bookmarkUserId shouldBe 9L
                }
            } finally {
                store.write(original, store.read())
            }
        }
    }
}
