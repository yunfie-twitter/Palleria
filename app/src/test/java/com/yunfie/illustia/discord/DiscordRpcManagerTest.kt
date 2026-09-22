package com.yunfie.illustia.discord

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiscordRpcManagerTest {
    @Before
    fun setUp() {
        DiscordRpcManager.clearAssetCache()
    }

    @Test
    fun testDefaultAppIdResolvesKnownAssetKeys() {
        runBlocking {
            val logoId = DiscordRpcManager.resolveAssetId(DiscordRpcManager.DEFAULT_APP_ID, "palleria_logo")
            logoId shouldBe DiscordRpcManager.DEFAULT_ASSET_PALLERIA_LOGO

            val appIconId = DiscordRpcManager.resolveAssetId(DiscordRpcManager.DEFAULT_APP_ID, "app_icon")
            appIconId shouldBe DiscordRpcManager.DEFAULT_ASSET_APP_ICON

            val palleriaId = DiscordRpcManager.resolveAssetId(DiscordRpcManager.DEFAULT_APP_ID, "palleria")
            palleriaId shouldBe DiscordRpcManager.DEFAULT_ASSET_PALLERIA
        }
    }

    @Test
    fun testDirectNumericSnowflakeIdOrProxyUrlIsReturnedAsIs() {
        runBlocking {
            val directId = DiscordRpcManager.resolveAssetId("custom_app", "1544722572242067458")
            directId shouldBe "1544722572242067458"

            val mediaProxy = DiscordRpcManager.resolveAssetId("custom_app", "mp:external/abc123/img.png")
            mediaProxy shouldBe "mp:external/abc123/img.png"

            val httpUrl = DiscordRpcManager.resolveAssetId("custom_app", "https://example.com/logo.png")
            httpUrl shouldBe "https://example.com/logo.png"

            val empty = DiscordRpcManager.resolveAssetId("custom_app", "")
            empty shouldBe ""
        }
    }
}
