package com.yunfie.illustia.settings.db;

import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "accounts")
public class AccountEntity {
    @PrimaryKey
    public long userId;
    public String name;
    public String account;
    @Nullable
    public String profileImageUrl;
    public int position;

    public AccountEntity(long userId, String name, String account, @Nullable String profileImageUrl, int position) {
        this.userId = userId;
        this.name = name;
        this.account = account;
        this.profileImageUrl = profileImageUrl;
        this.position = position;
    }
}
