package com.yunfie.illustia

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException

class GlitchTipTelemetryPrivacyTest :
    FreeSpec({
        "telemetry event scrub removes user input and request details" {
            val searchTerm = "private search phrase"
            val requestUrl = "https://pixiv.example/search?q=$searchTerm"
            val event =
                SentryEvent().apply {
                    message = Message().apply { message = requestUrl }
                    request =
                        Request().apply {
                            url = requestUrl
                            queryString = searchTerm
                        }
                    extras = mapOf("query" to searchTerm, "url" to requestUrl, "illustId" to 42L)
                    exceptions = listOf(SentryException().apply { value = requestUrl })
                    breadcrumbs = listOf(Breadcrumb().apply { message = requestUrl })
                }

            scrubTelemetryEvent(event)

            event.request shouldBe null
            event.message shouldBe null
            event.exceptions?.single()?.value shouldBe "An operation failed"
            event.breadcrumbs.shouldBeEmpty()
            event.extras shouldBe mapOf("illustId" to 42L)
        }

        "wrapped exceptions do not retain user-controlled messages or causes" {
            val original = IllegalArgumentException("private search phrase")

            sanitizedTelemetryException(original).apply {
                message shouldBe "An operation failed"
                cause shouldBe null
            }
        }
    })
