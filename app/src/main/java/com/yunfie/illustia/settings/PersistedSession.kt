package com.yunfie.illustia.settings

import android.content.SharedPreferences
import com.yunfie.illustia.models.MAX_SESSION_LIFETIME_SECONDS
import com.yunfie.illustia.models.PixivSession
import com.yunfie.illustia.models.SESSION_EXPIRY_SKEW_MILLIS
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal const val KEY_CACHED_SESSION = "oauthSessionV1"

internal data class AuthSettings(
    val refreshToken: String,
    val networkMode: String,
    val session: PixivSession?,
)

internal fun encodePersistedSession(session: PixivSession): String =
    JSONObject()
        .apply {
            put("access", session.accessToken)
            put("refresh", session.refreshToken)
            put("user", session.userId)
            put("expires", session.expiresAtMillis)
        }.toString()

internal fun readPersistedSession(
    preferences: SharedPreferences,
    refreshToken: String,
    now: Long = System.currentTimeMillis(),
): PixivSession? =
    runCatching {
        if (refreshToken.isBlank()) return null
        val json = JSONObject(preferences.getString(KEY_CACHED_SESSION, "").orEmpty())
        val expiry = json.getLong("expires")
        if (json.getString("refresh") != refreshToken || expiry <= now + SESSION_EXPIRY_SKEW_MILLIS ||
            expiry > now + TimeUnit.SECONDS.toMillis(MAX_SESSION_LIFETIME_SECONDS)
        ) {
            return null
        }
        val access = json.getString("access").takeIf { it.isNotBlank() } ?: return null
        PixivSession(access, refreshToken, json.optLong("user").takeIf { it > 0 }, expiry)
    }.getOrNull()
