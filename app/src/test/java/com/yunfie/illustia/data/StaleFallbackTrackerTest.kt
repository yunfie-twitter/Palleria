package com.yunfie.illustia.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Guards against the offline state being cached as a successful refresh.
 *
 * [IllustiaRepository.withApiCache] returns stale data instead of throwing when the network
 * drops, so callers that treat every return value as fresh would stamp the current time onto
 * old data and keep serving it until the TTL expired. The tracker is what lets them tell the
 * difference.
 */
class StaleFallbackTrackerTest :
    StringSpec({
        "a key is not stale before any fallback" {
            val tracker = StaleFallbackTracker()

            tracker.isStale("trending_tags") shouldBe false
        }

        "a stale fallback marks only its own key" {
            val tracker = StaleFallbackTracker()

            tracker.markStale("trending_tags")

            tracker.isStale("trending_tags") shouldBe true
            tracker.isStale("search_illust:cat") shouldBe false
        }

        "a successful fetch clears the stale marking" {
            val tracker = StaleFallbackTracker()

            tracker.markStale("trending_tags")
            tracker.clear("trending_tags")

            tracker.isStale("trending_tags") shouldBe false
        }

        "clearing unrelated keys leaves the stale marking intact" {
            val tracker = StaleFallbackTracker()

            tracker.markStale("trending_tags")
            tracker.clear("search_illust:cat")

            tracker.isStale("trending_tags") shouldBe true
        }
    })
