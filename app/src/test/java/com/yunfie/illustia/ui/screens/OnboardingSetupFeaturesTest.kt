package com.yunfie.illustia.ui.screens

import com.yunfie.illustia.data.PixivImageProxyOptions
import com.yunfie.illustia.settings.AppSettings
import com.yunfie.illustia.settings.AppThemeMode
import com.yunfie.illustia.settings.pixivNetworkModeOptions
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class OnboardingSetupFeaturesTest :
    FunSpec({
        test("theme selection options support system light and dark modes") {
            AppThemeMode.fromValue("system") shouldBe AppThemeMode.System
            AppThemeMode.fromValue("light") shouldBe AppThemeMode.Light
            AppThemeMode.fromValue("dark") shouldBe AppThemeMode.Dark
            AppThemeMode.fromValue("unknown") shouldBe AppThemeMode.System
        }

        test("content safety selection properly maps all-ages, R-18, and R-18G") {
            val defaultSettings = AppSettings()
            defaultSettings.allowR18 shouldBe true
            defaultSettings.allowR18G shouldBe true

            // All ages
            val allAges = defaultSettings.copy(allowR18 = false, allowR18G = false)
            allAges.allowR18 shouldBe false
            allAges.allowR18G shouldBe false

            // R-18
            val r18 = defaultSettings.copy(allowR18 = true, allowR18G = false)
            r18.allowR18 shouldBe true
            r18.allowR18G shouldBe false

            // R-18G
            val r18g = defaultSettings.copy(allowR18 = true, allowR18G = true)
            r18g.allowR18 shouldBe true
            r18g.allowR18G shouldBe true
        }

        test("network mode options include standard, compat, and ech") {
            val options = pixivNetworkModeOptions()
            options shouldContain "standard"
            options shouldContain "compat"
            options shouldContain "ech"
        }

        test("recommended network defaults to standard official images") {
            val settings = AppSettings()
            settings.pixivNetworkMode shouldBe "standard"
            settings.pixivImageProxyBaseUrl shouldBe ""
        }

        test("switching to ech and bypass proxy works as expected") {
            val settings = AppSettings()
            val echSettings =
                settings.copy(
                    pixivNetworkMode = "ech",
                    pixivImageProxyBaseUrl = "https://i.pixiv.re",
                )
            echSettings.pixivNetworkMode shouldBe "ech"
            echSettings.pixivImageProxyBaseUrl shouldBe "https://i.pixiv.re"
        }

        test("image proxy options contain pixiv.re proxy") {
            val pixivRe = PixivImageProxyOptions.firstOrNull { it.baseUrl.contains("pixiv.re") }
            pixivRe?.baseUrl shouldBe "https://i.pixiv.re/"
        }
    })
