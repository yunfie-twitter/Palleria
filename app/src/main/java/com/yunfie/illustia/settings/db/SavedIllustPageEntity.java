package com.yunfie.illustia.settings.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "saved_illust_pages",
    indices = {@Index(value = {"illustId"})}
)
public class SavedIllustPageEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long illustId;
    public int pageIndex;
    public String localPath;
    public String sourceUrl;
}
