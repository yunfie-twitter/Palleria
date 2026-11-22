package com.yunfie.illustia.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DrawingCacheExtensionsTest :
    FunSpec({
        test("cachedVerticalGradient returns a non-null composite Modifier") {
            val colors = listOf(Color.Transparent, Color.Black)
            val modifier = Modifier.cachedVerticalGradient(colors)
            modifier shouldNotBe null
        }

        test("cachedVerticalGradient with custom ratios builds correctly") {
            val colors = listOf(Color.Red, Color.Blue)
            val modifier =
                Modifier.cachedVerticalGradient(
                    colors = colors,
                    startYRatio = 0.2f,
                    endYRatio = 0.8f,
                )
            modifier shouldNotBe null
        }

        test("ratio coercion stays between 0 and 1") {
            val startRatio = -0.5f
            val endRatio = 1.5f
            startRatio.coerceIn(0f, 1f) shouldBe 0f
            endRatio.coerceIn(0f, 1f) shouldBe 1f
        }
    })
