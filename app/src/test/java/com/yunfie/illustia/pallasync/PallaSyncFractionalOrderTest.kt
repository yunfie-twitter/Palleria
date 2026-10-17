package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PallaSyncFractionalOrderTest :
    StringSpec({
        "C7 smallest exact fractional allocations" {
            PallaSyncFractionalOrder.between(null, null) shouldBe "1"
            PallaSyncFractionalOrder.between("1", null) shouldBe "2"
            PallaSyncFractionalOrder.between("1", "2") shouldBe "11"
            PallaSyncFractionalOrder.between("1", "11") shouldBe "101"
        }
        "initial and moved runs keep distinct sorted canonical keys" {
            val tags = (1..10_000).map { "tag-$it" }
            val keys = PallaSyncFractionalOrder.place(tags, tags.toSet(), emptyMap())
            tags.zipWithNext().all { (a, b) -> keys.getValue(a) < keys.getValue(b) } shouldBe true
            keys.values.all { it.length <= 256 && !it.endsWith('0') } shouldBe true
            val old = mapOf("a" to "1", "b" to "11", "c" to "2")
            val moved = PallaSyncFractionalOrder.place(listOf("a", "c", "b"), setOf("c", "b"), old)
            moved["a"] shouldBe old["a"]
            (moved.getValue("a") < moved.getValue("c") && moved.getValue("c") < moved.getValue("b")) shouldBe true
        }
        "exhaustion preserves the retained bounds" {
            val smallest = "0".repeat(255) + "1"
            shouldThrow<IllegalStateException> { PallaSyncFractionalOrder.between(null, smallest) }.message shouldBe "C_ORDER_KEY_EXHAUSTED"
        }
        "append follows cold state beyond the visible cache" {
            val placed = PallaSyncFractionalOrder.place(listOf("visible", "new"), setOf("new"), mapOf("visible" to "1"), "8")
            placed.getValue("visible") shouldBe "1"
            (placed.getValue("new") > "8") shouldBe true
        }
    })
