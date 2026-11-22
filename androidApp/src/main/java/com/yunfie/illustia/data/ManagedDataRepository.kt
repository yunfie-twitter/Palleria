package com.yunfie.illustia.data

import android.content.ContentResolver
import android.net.Uri
import com.yunfie.illustia.models.Illust
import com.yunfie.illustia.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject

/** Reads and writes the user-managed backup format without depending on UI state. */
class ManagedDataRepository(
    private val contentResolver: ContentResolver,
) {
    fun export(
        uri: Uri,
        settings: AppSettings,
    ) {
        val backup =
            JSONObject().apply {
                put("format", BACKUP_FORMAT)
                put("version", BACKUP_VERSION)
                put("exportedAt", System.currentTimeMillis())
                put(
                    "viewHistory",
                    JSONArray().apply {
                        settings.viewHistory.forEach { illust -> put(illust.toBackupJson()) }
                    },
                )
                put("searchHistory", JSONArray(settings.searchHistory))
                put("favoriteTags", JSONArray(settings.favoriteTags))
                put("mutedIllusts", JSONArray(settings.mutedIllusts))
                put("mutedUsers", JSONArray(settings.mutedUsers))
                put("mutedTags", JSONArray(settings.mutedTags))
            }
        contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
            writer.write(backup.toString(2))
        } ?: error("Unable to open export destination")
    }

    fun import(
        uri: Uri,
        current: AppSettings,
    ): AppSettings {
        val text =
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to open import source")
        val backup = JSONObject(text)
        require(backup.optString("format") == BACKUP_FORMAT)
        require(backup.optInt("version", 0) == BACKUP_VERSION)

        return current.copy(
            viewHistory =
                backup
                    .requireArray("viewHistory")
                    .jsonObjects()
                    .mapNotNull { item -> item.toHistoryIllust() }
                    .distinctBy(Illust::id)
                    .take(MAX_VIEW_HISTORY),
            searchHistory =
                backup
                    .requireArray("searchHistory")
                    .strings()
                    .distinct()
                    .take(MAX_SEARCH_HISTORY),
            favoriteTags =
                backup
                    .requireArray("favoriteTags")
                    .strings()
                    .distinct()
                    .take(MAX_FAVORITE_TAGS),
            mutedIllusts = backup.requireArray("mutedIllusts").longs().distinct(),
            mutedUsers = backup.requireArray("mutedUsers").longs().distinct(),
            mutedTags = backup.requireArray("mutedTags").strings().distinct(),
        )
    }

    private fun Illust.toBackupJson() =
        JSONObject().apply {
            put("id", id)
            put("title", title)
            put("type", type)
            put("artistId", artistId)
            put("artistName", artistName)
            put("imageUrl", imageUrl)
            put("pageCount", pageCount)
            put("isBookmarked", isBookmarked)
            put("xRestrict", xRestrict)
            put("illustAiType", illustAiType)
            put("tags", JSONArray(tags))
        }

    private fun JSONObject.toHistoryIllust(): Illust? {
        val illustId = optLong("id", 0L).takeIf { it > 0L } ?: return null
        val backupImageUrl = optString("imageUrl")
        val tags =
            optJSONArray("tags")?.let { array ->
                List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
            } ?: emptyList()
        return Illust(
            id = illustId,
            title = optString("title"),
            type = optString("type").ifBlank { "illust" },
            caption = "",
            artistId = optLong("artistId", 0L),
            artistName = optString("artistName"),
            artistAvatarUrl = null,
            squareImageUrl = "",
            mediumImageUrl = backupImageUrl,
            imageUrl = backupImageUrl,
            originalImageUrl = null,
            tags = tags,
            pageCount = optInt("pageCount", 1).coerceAtLeast(1),
            isBookmarked = optBoolean("isBookmarked", false),
            xRestrict = optInt("xRestrict", 0),
            illustAiType = optInt("illustAiType", 0),
        )
    }

    private fun JSONObject.requireArray(name: String): JSONArray {
        require(has(name) && !isNull(name)) { "Missing field: $name" }
        return getJSONArray(name)
    }

    private fun JSONArray.strings(): List<String> =
        buildList {
            repeat(length()) { index -> getString(index).takeIf(String::isNotBlank)?.let(::add) }
        }

    private fun JSONArray.longs(): List<Long> =
        buildList {
            repeat(length()) { index -> getLong(index).takeIf { it > 0L }?.let(::add) }
        }

    private fun JSONArray.jsonObjects(): List<JSONObject> =
        buildList {
            repeat(length()) { index -> add(getJSONObject(index)) }
        }

    private companion object {
        const val BACKUP_FORMAT = "illustia-data-backup"
        const val BACKUP_VERSION = 1
        const val MAX_VIEW_HISTORY = 48
        const val MAX_SEARCH_HISTORY = 6
        const val MAX_FAVORITE_TAGS = 24
    }
}
