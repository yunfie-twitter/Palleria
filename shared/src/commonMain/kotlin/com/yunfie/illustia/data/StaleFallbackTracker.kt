package com.yunfie.illustia.data

/**
 * Records which cache keys were last answered by the stale offline fallback.
 *
 * [IllustiaRepository.withApiCache] returns stale data instead of throwing when the network
 * fails, so a caller cannot tell a real refresh from an offline replay by the return value
 * alone. Treating a replay as fresh writes the current time over old data, which caches the
 * offline state as valid and hides the failure until the TTL expires.
 */
class StaleFallbackTracker {
    private val lock = Any()
    private val keys = mutableSetOf<String>()

    fun markStale(key: String) {
        synchronized(lock) { keys.add(key) }
    }

    fun clear(key: String) {
        synchronized(lock) { keys.remove(key) }
    }

    fun isStale(key: String): Boolean = synchronized(lock) { key in keys }
}
