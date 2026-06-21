package com.yunfie.illustia.settings.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface SettingsDao {
    @Query("SELECT * FROM search_history ORDER BY position ASC")
    List<SearchHistoryEntity> getSearchHistory();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSearchHistory(List<SearchHistoryEntity> items);

    @Query("DELETE FROM search_history")
    void clearSearchHistory();

    @Query("SELECT * FROM favorite_tags ORDER BY position ASC")
    List<FavoriteTagEntity> getFavoriteTags();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertFavoriteTags(List<FavoriteTagEntity> items);

    @Query("DELETE FROM favorite_tags")
    void clearFavoriteTags();

    @Query("SELECT * FROM view_history ORDER BY position ASC")
    List<ViewHistoryEntity> getViewHistory();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertViewHistory(List<ViewHistoryEntity> items);

    @Query("DELETE FROM view_history")
    void clearViewHistory();

    @Query("SELECT * FROM accounts ORDER BY position ASC")
    List<AccountEntity> getAccounts();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAccounts(List<AccountEntity> items);

    @Query("DELETE FROM accounts")
    void clearAccounts();
}
