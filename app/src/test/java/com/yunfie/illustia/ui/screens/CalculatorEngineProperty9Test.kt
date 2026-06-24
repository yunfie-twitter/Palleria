package com.yunfie.illustia.ui.screens

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

@Tags("Feature: privacy-mode, Property 9: 無効式の評価は null を返す")
class CalculatorEngineProperty9Test : FreeSpec({
    val invalidSymbolArb = Arb.choice(
        Arb.char('a', 'z'),
        Arb.char('A', 'Z'),
        Arb.choice('!', '@', '#', '$', '%', '^', '&', '*', '(', ')', '=', '?', '~'),
    )

    val invalidExprArb = Arb.string(1..12, invalidSymbolArb)

    "Property 9: 無効式の評価は null を返す" {
        checkAll(PropTestConfig(iterations = 100), invalidExprArb) { expr ->
            CalculatorEngine.evaluate(expr) shouldBe null
        }
    }
})
