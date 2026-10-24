package com.yunfie.illustia.ui.components

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PixivImageCacheKeyTest :
    FreeSpec({
        "prefetch and displayed thumbnail share their decoded image cache entry" {
            pixivImageMemoryCacheKey("https://i.pximg.net/image.jpg", thumbnail = true, decodeDimensionPx = 512) shouldBe
                "https://i.pximg.net/image.jpg#thumbnail-512"
        }

        "thumbnail and full-size decodes do not collide" {
            pixivImageMemoryCacheKey("image.jpg", thumbnail = true, decodeDimensionPx = 512) shouldNotBe
                pixivImageMemoryCacheKey("image.jpg", thumbnail = false, decodeDimensionPx = 1536)
        }

        "different decode dimensions have separate entries" {
            pixivImageMemoryCacheKey("image.jpg", thumbnail = false, decodeDimensionPx = 1080) shouldNotBe
                pixivImageMemoryCacheKey("image.jpg", thumbnail = false, decodeDimensionPx = 2048)
        }
    })
