package com.yunfie.illustia.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Records which cache keys were last answered by the stale offline fallback.
 *
 * [IllustiaRepository.withApiCache] returns stale data instead of throwing when the network
 * fails, so a caller cannot tell a real refresh from an offline replay by the return value
 * alone. Treating a replay as fresh writes the current time over old data, which caches the
 * offline state as valid and hides the failure until the TTL expires.
 */
internal class StaleFallbackTracker {
    private val keys = ConcurrentHashMap.newKeySet<String>()

    fun markStale(key: String) {
        keys.add(key)
    }

    fun clear(key: String) {
        keys.remove(key)
    }

    fun isStale(key: String): Boolean = key in keys
}
