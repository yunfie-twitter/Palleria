package com.yunfie.illustia.ui.components

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ParagraphNavigationTest : FunSpec({
    test("paragraph navigation skips headers, artwork, and vertical columns") {
        val paragraphs = listOf(1, 5, 8)
        paragraphTarget(paragraphs, 1, 1) shouldBe 5
        paragraphTarget(paragraphs, 3, 1) shouldBe 5
        paragraphTarget(paragraphs, 3, -1) shouldBe 1
        paragraphTarget(paragraphs, 5, -1) shouldBe 1
    }
    test("paragraph navigation remains at document boundaries") {
        paragraphTarget(listOf(1, 5), 5, 1) shouldBe null
        paragraphTarget(listOf(1, 5), 0, -1) shouldBe null
        paragraphTarget(emptyList(), 0, 1) shouldBe null
    }
})
