package com.yunfie.illustia.settings.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "view_history")
public class ViewHistoryEntity {
    @PrimaryKey
    public long id;
    @NonNull
    public String title;
    @NonNull
    public String artistName;
    @NonNull
    public String imageUrl;
    public int pageCount;
    @NonNull
    public String type;
    public int position;

    public ViewHistoryEntity(
            long id,
            @NonNull String title,
            @NonNull String artistName,
            @NonNull String imageUrl,
            int pageCount,
            @NonNull String type,
            int position
    ) {
        this.id = id;
        this.title = title;
        this.artistName = artistName;
        this.imageUrl = imageUrl;
        this.pageCount = pageCount;
        this.type = type;
        this.position = position;
    }
}
