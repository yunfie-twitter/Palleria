package com.yunfie.illustia.ui.screens

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class CalculatorEngineProperty9Test {
    private val invalidChars = ('a'..'z') + ('A'..'Z') + listOf('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '=', '?', '~')

    @Test
    fun `Property 9 無効式の評価は null を返す`() {
        repeat(100) {
            val length = Random.nextInt(1, 13)
            val expr =
                buildString(length) {
                    repeat(length) {
                        append(invalidChars.random())
                    }
                }
            CalculatorEngine.evaluate(expr) shouldBe null
        }
    }
}
