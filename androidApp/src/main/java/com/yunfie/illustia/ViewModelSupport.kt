package com.yunfie.illustia

import com.yunfie.illustia.models.Illust
import org.json.JSONArray
import org.json.JSONObject

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
