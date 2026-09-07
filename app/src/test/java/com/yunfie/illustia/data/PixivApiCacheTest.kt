package com.yunfie.illustia.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PixivApiCacheTest : StringSpec({
    "get should return cached value when within ttl" {
        val cache = PixivApiCache(defaultTtlMillis = 1000L)
        cache.put("key1", "value1", ttlMillis = 1000L, now = 1000L)

        cache.get<String>("key1", now = 1500L) shouldBe "value1"
    }

    "get should return null when expired" {
        val cache = PixivApiCache(defaultTtlMillis = 1000L)
        cache.put("key1", "value1", ttlMillis = 1000L, now = 1000L)

        cache.get<String>("key1", now = 2001L) shouldBe null
    }

    "getStale should return value even after expiration" {
        val cache = PixivApiCache(defaultTtlMillis = 1000L)
        cache.put("key1", "value1", ttlMillis = 1000L, now = 1000L)

        cache.get<String>("key1", now = 2500L) shouldBe null
        cache.getStale<String>("key1") shouldBe "value1"
    }

    "remove and removeByPrefix should delete keys properly" {
        val cache = PixivApiCache()
        cache.put("search:cat", "results_cat")
        cache.put("search:dog", "results_dog")
        cache.put("ranking:day", "ranking_day")

        cache.removeByPrefix("search:")
        cache.get<String>("search:cat") shouldBe null
        cache.get<String>("search:dog") shouldBe null
        cache.get<String>("ranking:day") shouldBe "ranking_day"

        cache.remove("ranking:day")
        cache.get<String>("ranking:day") shouldBe null
    }

    "evictOldest should prune entries when maxEntries is reached" {
        val cache = PixivApiCache(maxEntries = 5)
        for (i in 1..5) {
            cache.put("key$i", "value$i", ttlMillis = 10000L, now = i * 100L)
        }
        cache.size shouldBe 5

        cache.put("key6", "value6", ttlMillis = 10000L, now = 600L)
        (cache.size <= 5) shouldBe true
        cache.get<String>("key6", now = 600L) shouldBe "value6"
    }
})
