package com.yunfie.illustia

import android.util.Base64
import com.yunfie.illustia.models.Illust
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

internal fun randomUrlSafeString(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

internal fun String.sha256Base64Url(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

internal fun JSONObject.requireArray(name: String): JSONArray {
    require(has(name))
    return getJSONArray(name)
}

internal fun JSONArray.strings(): List<String> =
    buildList {
        for (index in 0 until length()) {
            optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add)
        }
    }

internal fun JSONArray.longs(): List<Long> =
    buildList {
        for (index in 0 until length()) {
            optLong(index, 0L).takeIf { it > 0L }?.let(::add)
        }
    }

internal fun JSONArray.jsonObjects(): List<JSONObject> =
    buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(::add)
        }
    }

internal fun interleaveIllusts(
    first: List<Illust>,
    second: List<Illust>,
): List<Illust> =
    buildList(first.size + second.size) {
        val seenIds = HashSet<Long>(first.size + second.size)
        repeat(maxOf(first.size, second.size)) { index ->
            first.getOrNull(index)?.takeIf { seenIds.add(it.id) }?.let(::add)
            second.getOrNull(index)?.takeIf { seenIds.add(it.id) }?.let(::add)
        }
    }
