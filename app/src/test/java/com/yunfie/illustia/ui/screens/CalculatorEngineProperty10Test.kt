package com.yunfie.illustia.ui.screens

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * **Validates: Requirements 3.2**
 *
 * Property 10: 有効式の評価は Double を返す
 *
 * For any valid (syntactically and mathematically correct) expression string `expr`,
 * `CalculatorEngine.evaluate(expr)` must return a non-null value (Double).
 */
@Tags("Feature: privacy-mode, Property 10: 有効式の評価は Double を返す")
class CalculatorEngineProperty10Test : FreeSpec({

    // Generates a non-zero integer operand as a string (to avoid division by zero divisors)
    val nonZeroIntArb: Arb<Int> = Arb.int(-999, 999).map { if (it == 0) 1 else it }

    // Generates any integer operand (including zero, valid as a non-divisor)
    val intArb: Arb<Int> = Arb.int(-999, 999)

    // Generates a valid simple expression: "a op b"
    // Division always uses a non-zero divisor to stay in the "valid" domain
    val validExprArb: Arb<String> = Arb.choice(
        // Addition: any int + any int
        Arb.bind(intArb, intArb) { a, b -> "$a+$b" },
        // Subtraction: any int - any int
        Arb.bind(intArb, intArb) { a, b -> "$a-$b" },
        // Multiplication: any int * any int
        Arb.bind(intArb, intArb) { a, b -> "$a*$b" },
        // Division: any int / non-zero int (avoid division by zero)
        Arb.bind(intArb, nonZeroIntArb) { a, b -> "$a/$b" },
        // Addition with × symbol
        Arb.bind(intArb, intArb) { a, b -> "$a×$b" },
        // Division with ÷ symbol
        Arb.bind(intArb, nonZeroIntArb) { a, b -> "$a÷$b" },
    )

    "Property 10: 有効式の評価は Double を返す" {
        checkAll(
            PropTestConfig(iterations = 100),
            validExprArb,
        ) { expr ->
            val result = CalculatorEngine.evaluate(expr)
            result shouldNotBe null
        }
    }
})
