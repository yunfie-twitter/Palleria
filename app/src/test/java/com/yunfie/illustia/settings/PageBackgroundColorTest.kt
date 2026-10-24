package com.yunfie.illustia.settings

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class PageBackgroundColorTest :
    FreeSpec({
        val profileFallbackColor = Color(0xFF121212)
        val dynamicBackgroundColor = Color(0xFF202020)

        "uses the shared profile fallback when dynamic color is disabled" {
            resolvePageBackgroundColor(
                useDynamicColor = false,
                dynamicColorAvailable = true,
                dynamicBackgroundColor = dynamicBackgroundColor,
                fallbackBackgroundColor = profileFallbackColor,
            ) shouldBe profileFallbackColor
        }

        "uses the shared profile fallback when dynamic color is unavailable" {
            resolvePageBackgroundColor(
                useDynamicColor = true,
                dynamicColorAvailable = false,
                dynamicBackgroundColor = dynamicBackgroundColor,
                fallbackBackgroundColor = profileFallbackColor,
            ) shouldBe profileFallbackColor
        }

        "uses the dynamic background only when enabled and available" {
            resolvePageBackgroundColor(
                useDynamicColor = true,
                dynamicColorAvailable = true,
                dynamicBackgroundColor = dynamicBackgroundColor,
                fallbackBackgroundColor = profileFallbackColor,
            ) shouldBe dynamicBackgroundColor
        }
    })
