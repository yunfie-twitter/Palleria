package com.yunfie.illustia

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class ImageCacheCapacityTest :
    FreeSpec({
        "zero means unlimited disk cache" {
            imageCacheMaximumSizeBytes(0) shouldBe null
        }

        "preset capacities convert from MiB to bytes" {
            imageCacheMaximumSizeBytes(300) shouldBe 300L * 1024 * 1024
        }
    })
