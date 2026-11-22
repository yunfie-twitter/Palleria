package com.yunfie.illustia.settings.db;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.List;

public class SavedIllustWithPages {
    @Embedded
    public final SavedIllustEntity illust;

    @Relation(parentColumn = "illustId", entityColumn = "illustId")
    public final List<SavedIllustPageEntity> pages;

    public SavedIllustWithPages(SavedIllustEntity illust, List<SavedIllustPageEntity> pages) {
        this.illust = illust;
        this.pages = pages;
    }
}
