package com.yunfie.illustia.settings.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                SearchHistoryEntity.class,
                FavoriteTagEntity.class,
                ViewHistoryEntity.class,
                AccountEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class IllustiaDatabase extends RoomDatabase {
    public abstract SettingsDao settingsDao();

    private static volatile IllustiaDatabase INSTANCE;

    public static IllustiaDatabase getInstance(Context context) {
        IllustiaDatabase current = INSTANCE;
        if (current != null) {
            return current;
        }
        synchronized (IllustiaDatabase.class) {
            current = INSTANCE;
            if (current == null) {
                current = Room.databaseBuilder(
                                context.getApplicationContext(),
                                IllustiaDatabase.class,
                                "illustia.db"
                        )
                        .build();
                INSTANCE = current;
            }
            return current;
        }
    }
}
