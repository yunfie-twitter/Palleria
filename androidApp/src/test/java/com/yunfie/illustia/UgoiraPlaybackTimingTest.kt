package com.yunfie.illustia

import com.yunfie.illustia.models.pixiv.normalizedUgoiraDelayMillis
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class UgoiraPlaybackTimingTest :
    StringSpec({
        "preserves valid Pixiv frame delays" {
            normalizedUgoiraDelayMillis(80) shouldBe 80L
            normalizedUgoiraDelayMillis(20) shouldBe 20L
        }

        "uses a visible fallback for invalid ultra-short delays" {
            normalizedUgoiraDelayMillis(19) shouldBe 100L
            normalizedUgoiraDelayMillis(0) shouldBe 100L
        }
    })
