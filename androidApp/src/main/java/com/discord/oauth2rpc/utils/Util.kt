package com.discord.oauth2rpc.utils

import org.json.JSONArray
import org.json.JSONObject

object Util {
    fun getEpochMillis(): Long = System.currentTimeMillis()

    fun List<String>.toJsonArray(): JSONArray {
        val array = JSONArray()
        for (item in this) {
            array.put(item)
        }
        return array
    }

    fun JSONObject.optStringOrNull(name: String): String? {
        val value = optString(name, "")
        return value.ifBlank { null }
    }
}
