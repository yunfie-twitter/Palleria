package com.yunfie.illustia.ui.components

import com.yunfie.illustia.data.PixivApiException
import com.yunfie.illustia.data.isPixivRateLimited
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AutoLoadMoreThrottleTest :
    StringSpec({
        "enforces minimum cooldown between rapid requests for the same url" {
            val lastTime = 10000L
            // Only 100ms passed -> should delay by 150ms (250 - 100)
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 10100L,
                lastRequestTime = lastTime,
                isSameUrl = true,
                lastRequestFailed = false,
            ) shouldBe 150L

            // 250ms passed -> should not delay
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 10250L,
                lastRequestTime = lastTime,
                isSameUrl = true,
                lastRequestFailed = false,
            ) shouldBe 0L
        }

        "allows immediate load when new nextUrl arrives" {
            val lastTime = 10000L
            // New URL should not be blocked by previous page cooldown
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 10050L,
                lastRequestTime = lastTime,
                isSameUrl = false,
                lastRequestFailed = false,
            ) shouldBe 0L
        }

        "enforces 3-second failure backoff when the same nextUrl fails" {
            val lastTime = 10000L
            // Immediately after failure (0ms passed) -> delay 3000ms
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 10000L,
                lastRequestTime = lastTime,
                isSameUrl = true,
                lastRequestFailed = true,
            ) shouldBe 3000L

            // 1000ms passed -> still delay 2000ms
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 11000L,
                lastRequestTime = lastTime,
                isSameUrl = true,
                lastRequestFailed = true,
            ) shouldBe 2000L

            // 3000ms passed -> delay 0ms
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 13000L,
                lastRequestTime = lastTime,
                isSameUrl = true,
                lastRequestFailed = true,
            ) shouldBe 0L
        }

        "identifies 429 rate limit errors correctly" {
            val error429 = PixivApiException(statusCode = 429, apiMessage = "Rate limit reached")
            error429.isPixivRateLimited() shouldBe true

            val nested429 = RuntimeException("Wrapper", error429)
            nested429.isPixivRateLimited() shouldBe true

            val genericError = PixivApiException(statusCode = 500, apiMessage = "Internal Server Error")
            genericError.isPixivRateLimited() shouldBe false

            val textRateLimit = RuntimeException("Too Many Requests error")
            textRateLimit.isPixivRateLimited() shouldBe true
        }
    })
