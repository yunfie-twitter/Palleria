package com.yunfie.illustia.settings.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "saved_illusts", indices = {@Index("savedAt")})
public class SavedIllustEntity {
    @PrimaryKey
    public long illustId;
    public String title;
    public String artistName;
    public long artistId;
    public String thumbUrl;
    public String localCoverPath;
    public String localPagePathsJson;
    public int pageCount;
    public long savedAt;
    public String saveGroup;
    public int xRestrict;
}
