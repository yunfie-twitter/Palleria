package com.yunfie.illustia.ui.components

import com.yunfie.illustia.data.PixivApiException
import com.yunfie.illustia.data.isPixivRateLimited
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AutoLoadMoreThrottleTest :
    StringSpec({
        "enforces minimum cooldown between successful requests" {
            val lastTime = 10000L
            // Only 400ms passed -> should delay by 800ms (1200 - 400)
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 10400L,
                lastRequestTime = lastTime,
                isSameUrl = false,
                lastRequestFailed = false,
            ) shouldBe 800L

            // 1200ms passed -> should not delay
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 11200L,
                lastRequestTime = lastTime,
                isSameUrl = false,
                lastRequestFailed = false,
            ) shouldBe 0L

            // 2000ms passed -> should not delay
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 12000L,
                lastRequestTime = lastTime,
                isSameUrl = false,
                lastRequestFailed = false,
            ) shouldBe 0L
        }

        "enforces 5-second failure backoff when the same nextUrl fails" {
            val lastTime = 10000L
            // Immediately after failure (0ms passed) -> delay 5000ms
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 10000L,
                lastRequestTime = lastTime,
                isSameUrl = true,
                lastRequestFailed = true,
            ) shouldBe 5000L

            // 2000ms passed -> still delay 3000ms
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 12000L,
                lastRequestTime = lastTime,
                isSameUrl = true,
                lastRequestFailed = true,
            ) shouldBe 3000L

            // 5000ms passed -> delay 0ms
            AutoLoadMoreThrottle.calculateDelayMillis(
                now = 15000L,
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
