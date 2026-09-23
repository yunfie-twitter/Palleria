package com.yunfie.illustia.settings.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                SearchHistoryEntity.class,
                FavoriteTagEntity.class,
                ViewHistoryEntity.class,
                AccountEntity.class,
                SavedIllustEntity.class,
                SavedIllustPageEntity.class
        },
        version = 6,
        exportSchema = false
)
public abstract class IllustiaDatabase extends RoomDatabase {
    public abstract SettingsDao settingsDao();

    private static volatile IllustiaDatabase INSTANCE;
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE saved_illusts ADD COLUMN xRestrict INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_saved_illust_pages_illustId ON saved_illust_pages(illustId)");
        }
    };
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE view_history ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0");
        }
    };
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE view_history ADD COLUMN xRestrict INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE view_history ADD COLUMN tagsJson TEXT NOT NULL DEFAULT '[]'");
            database.execSQL("ALTER TABLE view_history ADD COLUMN illustAiType INTEGER NOT NULL DEFAULT 0");
        }
    };

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
                        .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                        .fallbackToDestructiveMigration()
                        .build();
                INSTANCE = current;
            }
            return current;
        }
    }
}
