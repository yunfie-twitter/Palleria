package com.yunfie.illustia

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException

class CancellationFailuresTest :
    StringSpec({
        "recognizes a coroutine cancellation without relying on its class name" {
            CancellationException("dl3 was cancelled").isCancellationFailure() shouldBe true
        }

        "recognizes a wrapped coroutine cancellation" {
            IllegalStateException(
                "request failed",
                CancellationException("dl3 was cancelled"),
            ).isCancellationFailure() shouldBe true
        }

        "does not classify a regular failure as cancellation" {
            IllegalStateException("network failed").isCancellationFailure() shouldBe false
        }
    })
