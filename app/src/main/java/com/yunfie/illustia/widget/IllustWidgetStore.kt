package com.yunfie.illustia.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

data class IllustWidgetSelection(
    val illustId: Long,
    val pageIndex: Int,
    val pageCount: Int,
    val title: String,
    val artistName: String,
    val imageUrl: String,
    val imagePath: String,
)

class IllustWidgetStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(appWidgetId: Int, selection: IllustWidgetSelection) {
        prefs.edit()
            .putLong(key(appWidgetId, KEY_ILLUST_ID), selection.illustId)
            .putInt(key(appWidgetId, KEY_PAGE_INDEX), selection.pageIndex)
            .putInt(key(appWidgetId, KEY_PAGE_COUNT), selection.pageCount)
            .putString(key(appWidgetId, KEY_TITLE), selection.title)
            .putString(key(appWidgetId, KEY_ARTIST), selection.artistName)
            .putString(key(appWidgetId, KEY_IMAGE_URL), selection.imageUrl)
            .putString(key(appWidgetId, KEY_IMAGE_PATH), selection.imagePath)
            .apply()
    }

    fun load(appWidgetId: Int): IllustWidgetSelection? {
        if (!prefs.contains(key(appWidgetId, KEY_ILLUST_ID))) return null
        return IllustWidgetSelection(
            illustId = prefs.getLong(key(appWidgetId, KEY_ILLUST_ID), -1L),
            pageIndex = prefs.getInt(key(appWidgetId, KEY_PAGE_INDEX), 0),
            pageCount = prefs.getInt(key(appWidgetId, KEY_PAGE_COUNT), 1),
            title = prefs.getString(key(appWidgetId, KEY_TITLE), "").orEmpty(),
            artistName = prefs.getString(key(appWidgetId, KEY_ARTIST), "").orEmpty(),
            imageUrl = prefs.getString(key(appWidgetId, KEY_IMAGE_URL), "").orEmpty(),
            imagePath = prefs.getString(key(appWidgetId, KEY_IMAGE_PATH), "").orEmpty(),
        ).takeIf { it.illustId > 0 && it.imagePath.isNotBlank() }
    }

    fun remove(appWidgetId: Int) {
        prefs.edit()
            .remove(key(appWidgetId, KEY_ILLUST_ID))
            .remove(key(appWidgetId, KEY_PAGE_INDEX))
            .remove(key(appWidgetId, KEY_PAGE_COUNT))
            .remove(key(appWidgetId, KEY_TITLE))
            .remove(key(appWidgetId, KEY_ARTIST))
            .remove(key(appWidgetId, KEY_IMAGE_URL))
            .remove(key(appWidgetId, KEY_IMAGE_PATH))
            .apply()
    }

    private fun key(appWidgetId: Int, name: String) = "${name}_$appWidgetId"

    companion object {
        private const val PREFS_NAME = "illust_widget_store"
        private const val KEY_ILLUST_ID = "illustId"
        private const val KEY_PAGE_INDEX = "pageIndex"
        private const val KEY_PAGE_COUNT = "pageCount"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_IMAGE_URL = "imageUrl"
        private const val KEY_IMAGE_PATH = "imagePath"
    }
}
