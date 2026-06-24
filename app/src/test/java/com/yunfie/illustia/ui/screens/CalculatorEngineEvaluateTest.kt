package com.yunfie.illustia.ui.screens

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CalculatorEngineEvaluateTest : FreeSpec({
    "CalculatorEngine.evaluate should return expected result for valid expressions" {
        CalculatorEngine.evaluate("1+1") shouldBe 2.0
        CalculatorEngine.evaluate("10/2") shouldBe 5.0
        CalculatorEngine.evaluate("0.5*4") shouldBe 2.0
        CalculatorEngine.evaluate("2-3") shouldBe -1.0
        CalculatorEngine.evaluate("2×3") shouldBe 6.0
        CalculatorEngine.evaluate("8÷4") shouldBe 2.0
    }

    "CalculatorEngine.evaluate should return null for invalid or error expressions" {
        CalculatorEngine.evaluate("0/0") shouldBe null
        CalculatorEngine.evaluate("1++1") shouldBe null
        CalculatorEngine.evaluate("+2") shouldBe null
        CalculatorEngine.evaluate("2*") shouldBe null
        CalculatorEngine.evaluate("abc") shouldBe null
        CalculatorEngine.evaluate("") shouldBe null
    }
})
