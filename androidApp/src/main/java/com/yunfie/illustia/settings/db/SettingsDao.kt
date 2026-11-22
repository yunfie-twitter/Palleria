package com.yunfie.illustia.settings.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
@Suppress("TooManyFunctions")
interface SettingsDao {
    @Query("SELECT * FROM search_history ORDER BY position ASC")
    fun getSearchHistory(): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSearchHistory(items: List<SearchHistoryEntity>)

    @Query("DELETE FROM search_history")
    fun clearSearchHistory()

    @Query("SELECT * FROM favorite_tags ORDER BY position ASC")
    fun getFavoriteTags(): List<FavoriteTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFavoriteTags(items: List<FavoriteTagEntity>)

    @Query("DELETE FROM favorite_tags")
    fun clearFavoriteTags()

    @Query("SELECT * FROM view_history ORDER BY position ASC")
    fun getViewHistory(): List<ViewHistoryEntity>

    @Query("SELECT * FROM view_history ORDER BY position ASC LIMIT :limit")
    fun getViewHistory(limit: Int): List<ViewHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertViewHistory(items: List<ViewHistoryEntity>)

    @Query("DELETE FROM view_history")
    fun clearViewHistory()

    @Query("SELECT * FROM accounts ORDER BY position ASC")
    fun getAccounts(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAccounts(items: List<AccountEntity>)

    @Query("DELETE FROM accounts")
    fun clearAccounts()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSavedIllust(item: SavedIllustEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSavedIllustPages(items: List<SavedIllustPageEntity>)

    @Query("SELECT * FROM saved_illusts ORDER BY savedAt DESC")
    fun getSavedIllusts(): List<SavedIllustEntity>

    @Transaction
    @Query("SELECT * FROM saved_illusts WHERE illustId = :illustId LIMIT 1")
    fun getSavedIllust(illustId: Long): SavedIllustWithPages?

    @Query("DELETE FROM saved_illust_pages WHERE illustId = :illustId")
    fun deleteSavedIllustPages(illustId: Long)

    @Query("DELETE FROM saved_illusts WHERE illustId = :illustId")
    fun deleteSavedIllust(illustId: Long)

    @Query("DELETE FROM saved_illust_pages")
    fun clearSavedIllustPages()

    @Query("DELETE FROM saved_illusts")
    fun clearSavedIllusts()
}
