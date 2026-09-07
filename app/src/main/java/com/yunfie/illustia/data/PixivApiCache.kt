package com.yunfie.illustia.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache for Pixiv API responses to reduce API traffic and mitigate HTTP 429 (Rate Limiting).
 */
class PixivApiCache(
    private val defaultTtlMillis: Long = 10 * 60 * 1000L, // 10 minutes
    private val maxEntries: Int = 300,
) {
    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMillis: Long,
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now - timestamp > ttlMillis
    }

    private val cache = ConcurrentHashMap<String, CacheEntry<*>>()

    fun <T : Any> get(key: String, now: Long = System.currentTimeMillis()): T? {
        val entry = cache[key] ?: return null
        if (entry.isExpired(now)) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return entry.data as? T
    }

    fun <T : Any> getStale(key: String): T? {
        val entry = cache[key] ?: return null
        @Suppress("UNCHECKED_CAST")
        return entry.data as? T
    }

    fun <T : Any> put(
        key: String,
        data: T,
        ttlMillis: Long = defaultTtlMillis,
        now: Long = System.currentTimeMillis(),
    ) {
        if (cache.size >= maxEntries) {
            evictOldest(now)
        }
        cache[key] = CacheEntry(data, now, ttlMillis)
    }

    fun remove(key: String) {
        cache.remove(key)
    }

    fun removeByPrefix(prefix: String) {
        val keysToRemove = cache.keys.filter { it.startsWith(prefix) }
        for (key in keysToRemove) {
            cache.remove(key)
        }
    }

    fun clear() {
        cache.clear()
    }

    val size: Int
        get() = cache.size

    private fun evictOldest(now: Long) {
        // Remove expired entries first
        val expired = cache.entries.filter { it.value.isExpired(now) }
        for ((k, _) in expired) {
            cache.remove(k)
        }
        // If still over capacity, evict oldest entries
        if (cache.size >= maxEntries) {
            val sorted = cache.entries.sortedBy { it.value.timestamp }
            val countToRemove = (maxEntries * 0.2).toInt().coerceAtLeast(1)
            for (i in 0 until countToRemove.coerceAtMost(sorted.size)) {
                cache.remove(sorted[i].key)
            }
        }
    }
}
