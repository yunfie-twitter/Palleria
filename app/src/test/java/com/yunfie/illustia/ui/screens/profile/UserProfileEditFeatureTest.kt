package com.yunfie.illustia.ui.screens.profile

import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.PixivStamp
import com.yunfie.illustia.models.pixiv.UserProfileEdit
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.FeatureFlag
import com.yunfie.illustia.settings.isFeatureEnabled
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserProfileEditFeatureTest {
    @Test
    fun `own profile detection matches active account, bookmark userId, or any account`() {
        val user1 = 1001L
        val user2 = 1002L
        val otherUser = 9999L

        val settings =
            AppSettings(
                activeAccountIndex = 0,
                accounts =
                    listOf(
                        StoredAccount(
                            userId = user1,
                            name = "Account 1",
                            account = "p1",
                            profileImageUrl = null,
                            refreshToken = "",
                        ),
                        StoredAccount(
                            userId = user2,
                            name = "Account 2",
                            account = "p2",
                            profileImageUrl = null,
                            refreshToken = "",
                        ),
                    ),
                bookmarkUserId = user1,
            )

        fun checkIsOwnProfile(userId: Long): Boolean =
            (settings.accounts.getOrNull(settings.activeAccountIndex)?.userId ?: settings.bookmarkUserId) == userId ||
                settings.accounts.any { it.userId == userId }

        checkIsOwnProfile(user1) shouldBe true
        checkIsOwnProfile(user2) shouldBe true
        checkIsOwnProfile(otherUser) shouldBe false
    }

    @Test
    fun `user profile edit feature flag controls edit availability`() {
        val disabledSettings = AppSettings(featureFlags = mapOf(FeatureFlag.UserProfileEdit.key to false))
        disabledSettings.isFeatureEnabled(FeatureFlag.UserProfileEdit) shouldBe false

        val enabledSettings = AppSettings(featureFlags = mapOf(FeatureFlag.UserProfileEdit.key to true))
        enabledSettings.isFeatureEnabled(FeatureFlag.UserProfileEdit) shouldBe true
    }

    @Test
    fun `comment stamps feature flag controls stamp availability`() {
        val defaultSettings = AppSettings()
        defaultSettings.isFeatureEnabled(FeatureFlag.CommentStamps) shouldBe FeatureFlag.CommentStamps.defaultEnabled

        val enabledSettings = AppSettings(featureFlags = mapOf(FeatureFlag.CommentStamps.key to true))
        enabledSettings.isFeatureEnabled(FeatureFlag.CommentStamps) shouldBe true

        val disabledSettings = AppSettings(featureFlags = mapOf(FeatureFlag.CommentStamps.key to false))
        disabledSettings.isFeatureEnabled(FeatureFlag.CommentStamps) shouldBe false
    }

    @Test
    fun `user profile edit payload retains modified values`() {
        val editPayload =
            UserProfileEdit(
                gender = "male",
                address = 1,
                job = 2,
                userName = "New Name",
                birthday = "2000-01-01",
                webpage = "https://example.com",
                twitter = "example_user",
                comment = "Hello world",
            )

        editPayload.userName shouldBe "New Name"
        editPayload.comment shouldBe "Hello world"
        editPayload.gender shouldBe "male"
        editPayload.webpage shouldBe "https://example.com"
        editPayload.twitter shouldBe "example_user"
    }

    @Test
    fun `pixiv stamp data model retains identity and url`() {
        val stamp = PixivStamp(id = 123L, url = "https://pixiv.net/stamp/123.png")
        stamp.id shouldBe 123L
        stamp.url shouldBe "https://pixiv.net/stamp/123.png"
    }
}
