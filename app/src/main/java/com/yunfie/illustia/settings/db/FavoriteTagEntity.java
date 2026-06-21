package com.yunfie.illustia.settings.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "favorite_tags")
public class FavoriteTagEntity {
    @PrimaryKey
    @NonNull
    public String tag;
    public int position;

    public FavoriteTagEntity(@NonNull String tag, int position) {
        this.tag = tag;
        this.position = position;
    }
}
