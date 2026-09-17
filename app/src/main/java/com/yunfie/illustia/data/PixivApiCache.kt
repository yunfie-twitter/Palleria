package com.yunfie.illustia.data

import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_TTL_MILLIS: Long = 10 * 60 * 1000L // 10 minutes
private const val DEFAULT_MAX_ENTRIES: Int = 300

/**
 * Thread-safe in-memory cache for Pixiv API responses to reduce API traffic and mitigate HTTP 429 (Rate Limiting).
 */
class PixivApiCache(
    private val defaultTtlMillis: Long = DEFAULT_TTL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    data class CacheEntry<T>(
        val data: T,
        val timestamp: Long,
        val ttlMillis: Long,
    ) {
        fun isExpired(now: Long = System.currentTimeMillis()): Boolean = now - timestamp > ttlMillis
    }

    private val cache = ConcurrentHashMap<String, CacheEntry<*>>()

    fun <T : Any> get(
        key: String,
        now: Long = System.currentTimeMillis(),
    ): T? {
        val entry = cache[key]
        if (entry == null || entry.isExpired(now)) {
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
        val iterator = cache.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) {
                iterator.remove()
            }
        }
    }

    fun clear() {
        cache.clear()
    }

    val size: Int
        get() = cache.size

    private fun evictOldest(now: Long) {
        // Remove expired entries first in place
        val iterator = cache.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value.isExpired(now)) {
                iterator.remove()
            }
        }
        // If still over capacity, evict the oldest entries using a bounded min-heap
        if (cache.size >= maxEntries) {
            val countToRemove = (maxEntries * 0.2).toInt().coerceAtLeast(1)
            // min-heap: entries with the smallest (oldest) timestamp float to the top
            val oldest =
                java.util.PriorityQueue<Map.Entry<String, CacheEntry<*>>>(
                    countToRemove + 1,
                    compareBy { it.value.timestamp },
                )
            for (entry in cache.entries) {
                oldest.offer(entry)
                if (oldest.size > countToRemove) {
                    oldest.poll() // removes the newest among collected entries, keeping oldest
                }
            }
            // At this point `oldest` holds the countToRemove oldest entries
            while (!oldest.isEmpty()) {
                oldest.poll()?.key?.let { cache.remove(it) }
            }
        }
    }
}
