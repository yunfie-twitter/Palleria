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
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

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

    @Synchronized
    fun <T : Any> put(
        key: String,
        data: T,
        ttlMillis: Long = defaultTtlMillis,
        now: Long = System.currentTimeMillis(),
    ) {
        // Capacity checking and insertion must be atomic across concurrent requests.
        // Refreshing an existing key does not consume another cache slot.
        if (!cache.containsKey(key) && cache.size >= maxEntries) {
            evictOldest(now)
        }
        cache[key] = CacheEntry(data, now, ttlMillis)
    }

    @Synchronized
    fun remove(key: String) {
        cache.remove(key)
    }

    @Synchronized
    fun removeByPrefix(prefix: String) {
        val iterator = cache.keys.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) {
                iterator.remove()
            }
        }
    }

    @Synchronized
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
        // If still over capacity, evict the oldest entries
        if (cache.size >= maxEntries) {
            val countToRemove = (maxEntries * 0.2).toInt().coerceAtLeast(1)
            val oldestKeys =
                cache.entries
                    .sortedBy { it.value.timestamp }
                    .take(countToRemove)
                    .map { it.key }
            for (key in oldestKeys) {
                cache.remove(key)
            }
        }
    }
}
