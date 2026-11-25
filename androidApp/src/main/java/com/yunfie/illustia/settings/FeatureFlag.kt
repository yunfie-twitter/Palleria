package com.yunfie.illustia.settings

import android.content.Context
import com.yunfie.illustia.settings.store.LEGACY_PREFS_NAME
import com.yunfie.illustia.settings.store.decodeFeatureFlags
import java.util.concurrent.ConcurrentHashMap

private val featureFlagCache = ConcurrentHashMap<String, Boolean>()

fun updateFeatureFlagCache(flags: Map<String, Boolean>) {
    featureFlagCache.putAll(flags)
}

@Suppress("ReturnCount")
fun readFeatureFlagSync(
    context: Context,
    flag: FeatureFlag,
): Boolean {
    featureFlagCache[flag.key]?.let { return it }
    val prefs =
        context.applicationContext.getSharedPreferences(
            LEGACY_PREFS_NAME,
            Context.MODE_PRIVATE,
        )
    val raw = prefs.getString("featureFlags", null)
    if (raw == null) {
        featureFlagCache[flag.key] = flag.defaultEnabled
        return flag.defaultEnabled
    }
    val flags = decodeFeatureFlags(raw)
    featureFlagCache.putAll(flags)
    val resolved = flags[flag.key] ?: flag.defaultEnabled
    featureFlagCache[flag.key] = resolved
    return resolved
}
