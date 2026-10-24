package com.yunfie.illustia.pallasync

import android.content.SharedPreferences

/** §11.5 probe preferences and last successful relay comparison, stored in encrypted policy prefs. */
internal class PallaSyncProbeSchedule(
    private val preferences: SharedPreferences,
) {
    fun intervalMs(chainId: String): Long = preferences.getLong(intervalKey(chainId), DEFAULT_INTERVAL_MS)

    fun setIntervalMs(
        chainId: String,
        intervalMs: Long,
    ) {
        require(chainId.isNotBlank() && intervalMs >= DEFAULT_INTERVAL_MS) { "C_SCHEMA_VIOLATION" }
        check(preferences.edit().putLong(intervalKey(chainId), intervalMs).commit()) { "C_LOCAL_STORAGE_FULL" }
    }

    fun lastSuccessAtMs(chainId: String): Long? =
        if (preferences.contains(lastSuccessKey(chainId))) {
            preferences.getLong(lastSuccessKey(chainId), 0).takeIf { it > 0 }
        } else {
            null
        }

    fun recordSuccess(
        chainId: String,
        atMs: Long,
    ) {
        require(chainId.isNotBlank() && atMs > 0)
        check(preferences.edit().putLong(lastSuccessKey(chainId), atMs).commit()) { "C_LOCAL_STORAGE_FULL" }
    }

    private fun intervalKey(chainId: String) = "consistency-probe-interval:$chainId"

    private fun lastSuccessKey(chainId: String) = "consistency-probe-last-success:$chainId"

    companion object {
        const val DEFAULT_INTERVAL_MS = 15 * 60_000L
    }
}
