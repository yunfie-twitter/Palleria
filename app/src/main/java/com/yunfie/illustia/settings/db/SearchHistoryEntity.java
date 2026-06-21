package com.yunfie.illustia.settings.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "search_history")
public class SearchHistoryEntity {
    @PrimaryKey
    @NonNull
    public String query;
    public int position;

    public SearchHistoryEntity(@NonNull String query, int position) {
        this.query = query;
        this.position = position;
    }
}
