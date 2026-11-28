package com.yunfie.illustia.data

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

private const val DEFAULT_TTL_MILLIS: Long = 10 * 60 * 1000L // 10 minutes
private const val DEFAULT_MAX_ENTRIES: Int = 300

/**
 * Thread-safe in-memory cache for Pixiv API responses to reduce API traffic and mitigate HTTP 429 (Rate Limiting).
 *
 * Shared by every target, so it cannot use `ConcurrentHashMap` (JVM-only) or `@Synchronized`
 * (JVM-only annotation). A plain map behind atomicfu's portable monitor is enough here: every
 * mutation is short and the only multi-step ones (capacity eviction) already had to be atomic.
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
        fun isExpired(now: Long = nowMillis()): Boolean = now - timestamp > ttlMillis
    }

    private val lock = SynchronizedObject()
    private val cache = mutableMapOf<String, CacheEntry<*>>()

    fun <T : Any> get(
        key: String,
        now: Long = nowMillis(),
    ): T? {
        val entry = synchronizedRead(key) ?: return null
        if (entry.isExpired(now)) return null
        @Suppress("UNCHECKED_CAST")
        return entry.data as? T
    }

    fun <T : Any> getStale(key: String): T? {
        val entry = synchronizedRead(key) ?: return null
        @Suppress("UNCHECKED_CAST")
        return entry.data as? T
    }

    fun <T : Any> put(
        key: String,
        data: T,
        ttlMillis: Long = defaultTtlMillis,
        now: Long = nowMillis(),
    ) {
        synchronized(lock) {
            // Capacity checking and insertion must be atomic across concurrent requests.
            // Refreshing an existing key does not consume another cache slot.
            if (!cache.containsKey(key) && cache.size >= maxEntries) {
                evictOldest(now)
            }
            cache[key] = CacheEntry(data, now, ttlMillis)
        }
    }

    fun remove(key: String) {
        synchronized(lock) { cache.remove(key) }
    }

    fun removeByPrefix(prefix: String) {
        synchronized(lock) {
            val iterator = cache.keys.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().startsWith(prefix)) {
                    iterator.remove()
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) { cache.clear() }
    }

    val size: Int
        get() = synchronized(lock) { cache.size }

    private fun synchronizedRead(key: String): CacheEntry<*>? = synchronized(lock) { cache[key] }

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

private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
