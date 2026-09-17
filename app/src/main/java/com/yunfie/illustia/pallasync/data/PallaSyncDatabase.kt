package com.yunfie.illustia.pallasync.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        OutboxEntity::class,
        ChainStateEntity::class,
        PallaSyncDeviceEntity::class,
        PallaSyncInboxEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class PallaSyncDatabase : RoomDatabase() {
    abstract fun pallaSyncDao(): PallaSyncDao

    companion object {
        @Volatile
        private var INSTANCE: PallaSyncDatabase? = null

        fun getDatabase(context: Context): PallaSyncDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            PallaSyncDatabase::class.java,
                            "pallasync_database",
                        ).addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                        .fallbackToDestructiveMigrationFrom(1, 2)
                        .build()
                INSTANCE = instance
                instance
            }

        /**
         * v4 introduces a relay-assigned cursor and a durable inbox. Both are
         * additive, so existing chain/outbox/device data remains untouched.
         */
        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        ALTER TABLE pallasync_chain_state
                        ADD COLUMN last_relay_seq INTEGER NOT NULL DEFAULT 0
                        """.trimIndent(),
                    )
                    // v1-v3 active chains were already operating, so only newly joined
                    // chains start in the pending-first-pull state.
                    db.execSQL(
                        """
                        ALTER TABLE pallasync_chain_state
                        ADD COLUMN initial_pull_completed INTEGER NOT NULL DEFAULT 1
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS pallasync_inbox (
                            chain_id TEXT NOT NULL,
                            record_id TEXT NOT NULL,
                            relay_seq INTEGER NOT NULL,
                            status TEXT NOT NULL,
                            raw_record_json TEXT NOT NULL,
                            quarantine_reason TEXT,
                            received_at_ms INTEGER NOT NULL,
                            PRIMARY KEY(chain_id, record_id)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_pallasync_inbox_chain_id_relay_seq
                        ON pallasync_inbox(chain_id, relay_seq)
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_pallasync_inbox_chain_id_status
                        ON pallasync_inbox(chain_id, status)
                        """.trimIndent(),
                    )
                }
            }

        /**
         * v5 introduces indices on pallasync_outbox for faster status and chain_id querying.
         */
        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_pallasync_outbox_status
                        ON pallasync_outbox(status)
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_pallasync_outbox_chain_id_status
                        ON pallasync_outbox(chain_id, status)
                        """.trimIndent(),
                    )
                }
            }
    }
}
