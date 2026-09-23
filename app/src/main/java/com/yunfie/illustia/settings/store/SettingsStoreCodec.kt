package com.yunfie.illustia.settings.store

import android.util.Base64
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.models.StoredAccount
import com.yunfie.illustia.settings.db.AccountEntity
import com.yunfie.illustia.settings.db.FavoriteTagEntity
import com.yunfie.illustia.settings.db.SearchHistoryEntity
import com.yunfie.illustia.settings.db.ViewHistoryEntity
import org.json.JSONArray
import org.json.JSONObject

internal data class RoomSettingsData(
    val searchHistory: List<SearchHistoryEntity> = emptyList(),
    val favoriteTags: List<FavoriteTagEntity> = emptyList(),
    val viewHistory: List<ViewHistoryEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
)

internal inline fun <reified T : Enum<T>> enumValueOrDefault(
    value: String?,
    default: T,
): T =
    runCatching {
        value?.let { enumValueOf<T>(it) }
    }.getOrNull() ?: default

internal fun encodeStringList(values: List<String>): String =
    JSONArray()
        .apply {
            values.forEach { put(it) }
        }.toString()

internal fun decodeStringList(value: String?): List<String> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(value)
        List(array.length()) { index -> array.optString(index) }
            .filter { it.isNotBlank() }
    }.getOrElse {
        decodeLegacyStringList(value)
    }
}

internal fun decodeLegacyStringList(value: String?): List<String> =
    value
        .orEmpty()
        .split(HISTORY_SEPARATOR)
        .filter { it.isNotBlank() }

internal fun encodeLongList(values: List<Long>): String =
    JSONArray()
        .apply {
            values.forEach { put(it) }
        }.toString()

internal fun decodeLongList(value: String?): List<Long> {
    if (value.isNullOrBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(value)
        List(array.length()) { index -> array.optLong(index, 0L) }
            .filter { it > 0L }
    }.getOrElse {
        value.split(",").mapNotNull { it.toLongOrNull() }
    }
}

internal fun decodeHistoryIllusts(value: String?): List<Illust> {
    if (value.isNullOrBlank()) return emptyList()
    return if (value.trimStart().startsWith("[")) {
        runCatching {
            val array = JSONArray(value)
            List(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@List null
                historyIllustFromJson(item)
            }.filterNotNull()
        }.getOrDefault(emptyList())
    } else {
        value
            .split(HISTORY_SEPARATOR)
            .filter { it.isNotBlank() }
            .mapNotNull(::decodeLegacyHistoryIllust)
    }
}

internal fun encodeAccountTokens(accounts: List<StoredAccount>): String =
    JSONObject()
        .apply {
            accounts.forEach { account ->
                put(account.userId.toString(), account.refreshToken)
            }
        }.toString()

internal fun decodeAccountTokens(value: String): Map<Long, String> {
    if (value.isBlank()) return emptyMap()
    return runCatching {
        val json = JSONObject(value)
        json
            .keys()
            .asSequence()
            .mapNotNull { key ->
                val userId = key.toLongOrNull() ?: return@mapNotNull null
                val token = json.optString(key)
                if (token.isBlank()) null else userId to token
            }.toMap()
    }.getOrDefault(emptyMap())
}

internal fun decodeAccounts(value: String): List<StoredAccount> {
    if (value.isBlank()) return emptyList()
    return if (value.trimStart().startsWith("[")) {
        runCatching {
            val array = JSONArray(value)
            List(array.length()) { index ->
                val item = array.optJSONObject(index) ?: return@List null
                StoredAccount(
                    name = item.optString("name"),
                    account = item.optString("account"),
                    profileImageUrl = item.optString("profileImageUrl").takeIf { it.isNotBlank() && it != "null" },
                    refreshToken = item.optString("refreshToken"),
                    userId = item.optLong("userId", 0L).takeIf { it > 0L } ?: return@List null,
                )
            }.filterNotNull()
        }.getOrDefault(emptyList())
    } else {
        value.split(HISTORY_SEPARATOR).mapNotNull { entry ->
            val parts = entry.split(FIELD_SEPARATOR)
            if (parts.size < 5) return@mapNotNull null
            StoredAccount(
                name = parts[0].decodeBase64Field(),
                account = parts[1].decodeBase64Field(),
                profileImageUrl = parts[2].decodeBase64Field().takeIf { it.isNotBlank() },
                refreshToken = parts[3].decodeBase64Field(),
                userId = parts[4].toLongOrNull() ?: return@mapNotNull null,
            )
        }
    }
}

internal fun illustFromEntity(entity: ViewHistoryEntity): Illust =
    Illust(
        id = entity.id,
        title = entity.title,
        type = entity.type.ifBlank { "illust" },
        caption = "",
        artistId = 0L,
        artistName = entity.artistName,
        artistAvatarUrl = null,
        squareImageUrl = "",
        mediumImageUrl = entity.imageUrl,
        imageUrl = entity.imageUrl,
        originalImageUrl = null,
        mediumImagePages = emptyList(),
        imagePages = emptyList(),
        originalImagePages = emptyList(),
        tags = emptyList(),
        pageCount = entity.pageCount,
        isBookmarked = entity.isBookmarked,
    )

internal fun historyIllustFromJson(item: JSONObject): Illust? {
    val id = item.optLong("id", 0L).takeIf { it > 0L } ?: return null
    val imageUrl = item.optString("imageUrl")
    return Illust(
        id = id,
        title = item.optString("title"),
        type = item.optString("type").ifBlank { "illust" },
        caption = "",
        artistId = 0L,
        artistName = item.optString("artistName"),
        artistAvatarUrl = null,
        squareImageUrl = "",
        mediumImageUrl = imageUrl,
        imageUrl = imageUrl,
        originalImageUrl = null,
        mediumImagePages = emptyList(),
        imagePages = emptyList(),
        originalImagePages = emptyList(),
        tags = emptyList(),
        pageCount = item.optInt("pageCount", 1),
        isBookmarked = item.optBoolean("isBookmarked", false),
    )
}

internal fun decodeLegacyHistoryIllust(value: String): Illust? {
    val parts = value.split(FIELD_SEPARATOR)
    if (parts.size < 6) return null
    val imageUrl = parts[3].decodeBase64Field()
    return Illust(
        id = parts[0].toLongOrNull() ?: return null,
        title = parts[1].decodeBase64Field(),
        type = parts[5].decodeBase64Field().ifBlank { "illust" },
        caption = "",
        artistId = 0L,
        artistName = parts[2].decodeBase64Field(),
        artistAvatarUrl = null,
        squareImageUrl = "",
        mediumImageUrl = imageUrl,
        imageUrl = imageUrl,
        originalImageUrl = null,
        mediumImagePages = emptyList(),
        imagePages = emptyList(),
        originalImagePages = emptyList(),
        tags = emptyList(),
        pageCount = parts[4].toIntOrNull() ?: 1,
        isBookmarked = false,
    )
}

internal fun String.decodeBase64Field(): String =
    runCatching {
        String(Base64.decode(this, Base64.URL_SAFE or Base64.NO_WRAP))
    }.getOrDefault("")

internal fun encodeNovelProgress(progressMap: Map<Long, com.yunfie.illustia.models.NovelReadingProgress>): String {
    val array = JSONArray()
    progressMap.values.forEach { progress ->
        val obj =
            JSONObject().apply {
                put("novelId", progress.novelId)
                put("lastReadPage", progress.lastReadPage)
                put("totalPages", progress.totalPages)
                put("updatedAt", progress.updatedAt)
                put("status", progress.status.id)
            }
        array.put(obj)
    }
    return array.toString()
}

internal fun decodeNovelProgress(value: String?): Map<Long, com.yunfie.illustia.models.NovelReadingProgress> {
    if (value.isNullOrBlank()) return emptyMap()
    return runCatching {
        val array = JSONArray(value)
        (0 until array.length())
            .mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optLong("novelId", 0L)
                if (id <= 0L) return@mapNotNull null
                val status =
                    com.yunfie.illustia.models.NovelReadingStatus
                        .fromId(obj.optString("status"))
                id to
                    com.yunfie.illustia.models.NovelReadingProgress(
                        novelId = id,
                        lastReadPage = obj.optInt("lastReadPage", 0).coerceAtLeast(0),
                        totalPages = obj.optInt("totalPages", 1).coerceAtLeast(1),
                        updatedAt = obj.optLong("updatedAt", 0L),
                        status = status,
                    )
            }.toMap()
    }.getOrDefault(emptyMap())
}

internal fun encodeFeatureFlags(flags: Map<String, Boolean>): String {
    val obj = JSONObject()
    flags.forEach { (key, value) ->
        obj.put(key, value)
    }
    return obj.toString()
}

internal fun decodeFeatureFlags(value: String?): Map<String, Boolean> {
    if (value.isNullOrBlank()) return emptyMap()
    return runCatching {
        val obj = JSONObject(value)
        val result = mutableMapOf<String, Boolean>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = obj.optBoolean(key, false)
        }
        result
    }.getOrDefault(emptyMap())
}
