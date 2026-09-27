package com.yunfie.illustia.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PixivApiCacheTest :
    StringSpec({
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
            // Oldest entry (key1 with timestamp 100L) must have been evicted
            cache.get<String>("key1", now = 600L) shouldBe null
            // Newer entries must remain
            cache.get<String>("key5", now = 600L) shouldBe "value5"
        }

        "clear should remove all cached items" {
            val cache = PixivApiCache()
            cache.put("a", "1")
            cache.put("b", "2")
            cache.clear()
            cache.size shouldBe 0
            cache.get<String>("a") shouldBe null
        }

        "refreshing a full cache does not evict unrelated responses" {
            val cache = PixivApiCache(maxEntries = 2)
            cache.put("first", "first response", now = 1000L)
            cache.put("second", "old response", now = 2000L)

            cache.put("second", "refreshed response", now = 3000L)

            cache.size shouldBe 2
            cache.get<String>("first", now = 3000L) shouldBe "first response"
            cache.get<String>("second", now = 3000L) shouldBe "refreshed response"
        }

        "concurrent responses cannot exceed the configured capacity" {
            val workers = 8
            val rounds = 100
            val capacity = 4
            val cache = PixivApiCache(maxEntries = capacity)
            val executor = Executors.newFixedThreadPool(workers)
            val barrier = CyclicBarrier(workers + 1)
            try {
                val tasks =
                    (0 until workers).map { worker ->
                        executor.submit {
                            repeat(rounds) { round ->
                                barrier.await(10, TimeUnit.SECONDS)
                                cache.put("$round:$worker", worker)
                                barrier.await(10, TimeUnit.SECONDS)
                            }
                        }
                    }
                repeat(rounds) {
                    cache.clear()
                    barrier.await(10, TimeUnit.SECONDS)
                    barrier.await(10, TimeUnit.SECONDS)
                    (cache.size <= capacity) shouldBe true
                }
                tasks.forEach { it.get(10, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
            }
        }

        "cache capacity must be positive" {
            shouldThrow<IllegalArgumentException> { PixivApiCache(maxEntries = 0) }
            shouldThrow<IllegalArgumentException> { PixivApiCache(maxEntries = -1) }
        }
    })
