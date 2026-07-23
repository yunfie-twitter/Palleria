package com.yunfie.illustia.nativebridge

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class NativeIntentRouterTest : FunSpec({
    test("parses trusted Pixiv artwork and user routes") {
        NativeIntentRouter.parseText("https://www.pixiv.net/artworks/123") shouldBe
            NativeIntentEvent.Artwork(123)
        NativeIntentRouter.parseText("http://pixiv.net/users/456") shouldBe
            NativeIntentEvent.User(456)
        NativeIntentRouter.parseText("pixiv://illusts/789") shouldBe
            NativeIntentEvent.Artwork(789)
        NativeIntentRouter.parseText("pixez://users/987") shouldBe
            NativeIntentEvent.User(987)
    }

    test("rejects Pixiv-looking paths from untrusted origins") {
        NativeIntentRouter.parseText("https://attacker.example/artworks/123") shouldBe null
        NativeIntentRouter.parseText("https://pixiv.net.attacker.example/users/456") shouldBe null
        NativeIntentRouter.parseText("evil://illusts/789") shouldBe null
    }

    test("process text normalization removes the leading hash") {
        NativeIntentRouter.normalizeProcessText("  #初音ミク  ") shouldBe "初音ミク"
    }

    test("process text collapses whitespace and removes control characters") {
        NativeIntentRouter.normalizeProcessText("  blue\n archive\u0000\t tag  ") shouldBe
            "blue archive tag"
    }

    test("blank process text is ignored") {
        NativeIntentRouter.normalizeProcessText(" \n\t\u0000 ") shouldBe null
    }

    test("process text is capped without splitting a surrogate pair") {
        val text = "絵".repeat(NativeIntentRouter.MAX_PROCESS_TEXT_CODE_POINTS - 1) + "😀末尾"
        val normalized = NativeIntentRouter.normalizeProcessText(text)!!

        normalized.codePointCount(0, normalized.length) shouldBe
            NativeIntentRouter.MAX_PROCESS_TEXT_CODE_POINTS
        normalized.endsWith("😀") shouldBe true
    }
})
