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
        RegistryHistoryEntity::class,
        EpochKeyEntity::class,
        SignedEventEntity::class,
        AuthorSequenceEntity::class,
        SnapshotAuthorHeadEntity::class,
        SnapshotAuthorGapEntity::class,
        EntityRegisterEntity::class,
        ScopeRegisterEntity::class,
        HistoryIndexEntity::class,
        QuarantineEvidenceEntity::class,
        SnapshotPartEntity::class,
        SnapshotPublicationEntity::class,
        SnapshotPublicationPartEntity::class,
        SnapshotInstallEntity::class,
        SnapshotInstallPartEntity::class,
        SnapshotInstallEntryEntity::class,
        ProtectedEvidenceEntity::class,
        AuthorityHistoryEntity::class,
        MigrationJournalEntity::class,
        LegacyMigrationCandidateEntity::class,
        LegacyMigrationChoiceEntity::class,
        ProjectionStateEntity::class,
        LogEntryEntity::class,
    ],
    version = 12,
    exportSchema = false,
)
abstract class PallaSyncDatabase : RoomDatabase() {
    abstract fun pallaSyncDao(): PallaSyncDao

    abstract fun protocolDao(): ProtocolDao

    abstract fun compactionCatalogueDao(): CompactionCatalogueDao

    abstract fun snapshotDao(): SnapshotDao

    abstract fun snapshotPublicationDao(): SnapshotPublicationDao

    abstract fun snapshotInstallDao(): SnapshotInstallDao

    abstract fun snapshotStateDao(): SnapshotStateDao

    abstract fun deletionEvidenceDao(): DeletionEvidenceDao

    companion object {
        private const val MIGRATION_TARGET_VERSION = 3

        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // v7 staging rows predate receipt persistence; `null` marks them for
                    // readback instead of inventing evidence or trusting the old row.
                    db.execSQL("ALTER TABLE ps_snapshots ADD COLUMN upload_receipt_json TEXT NOT NULL DEFAULT 'null'")
                }
            }

        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE ps_snapshot_publications (chain_id TEXT NOT NULL,compaction_id TEXT NOT NULL,generation INTEGER NOT NULL,epoch INTEGER NOT NULL,covered_through_seq INTEGER NOT NULL,log_id TEXT NOT NULL,header_json TEXT NOT NULL,part_count INTEGER NOT NULL,state_sha256 TEXT,compaction_json TEXT,status TEXT NOT NULL,PRIMARY KEY(chain_id,compaction_id))",
                    )
                    db.execSQL(
                        "CREATE TABLE ps_snapshot_publication_parts (chain_id TEXT NOT NULL,compaction_id TEXT NOT NULL,part_index INTEGER NOT NULL,record_id TEXT NOT NULL,record_hash TEXT NOT NULL,signed_json TEXT NOT NULL,upload_receipt_json TEXT,status TEXT NOT NULL,PRIMARY KEY(chain_id,compaction_id,part_index))",
                    )
                }
            }

        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE ps_snapshot_installs (chain_id TEXT NOT NULL,compaction_id TEXT NOT NULL,compaction_json TEXT NOT NULL,part_count INTEGER NOT NULL,state_sha256 TEXT NOT NULL,covered_through_seq INTEGER NOT NULL,covered_log_hash TEXT NOT NULL,generation INTEGER NOT NULL,status TEXT NOT NULL,PRIMARY KEY(chain_id,compaction_id))",
                    )
                    db.execSQL(
                        "CREATE TABLE ps_snapshot_install_parts (chain_id TEXT NOT NULL,compaction_id TEXT NOT NULL,part_index INTEGER NOT NULL,record_id TEXT NOT NULL,record_hash TEXT NOT NULL,entry_count INTEGER NOT NULL,PRIMARY KEY(chain_id,compaction_id,part_index))",
                    )
                    db.execSQL(
                        "CREATE TABLE ps_snapshot_install_entries (chain_id TEXT NOT NULL,compaction_id TEXT NOT NULL,part_index INTEGER NOT NULL,entry_index INTEGER NOT NULL,entry_json TEXT NOT NULL,PRIMARY KEY(chain_id,compaction_id,part_index,entry_index))",
                    )
                }
            }

        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE ps_snapshot_author_heads(chain_id TEXT NOT NULL,generation INTEGER NOT NULL,device_id TEXT NOT NULL,device_seq INTEGER NOT NULL,record_hash TEXT NOT NULL,compaction_id TEXT NOT NULL,PRIMARY KEY(chain_id,generation,device_id))",
                    )
                    db.execSQL(
                        "CREATE TABLE ps_snapshot_author_gaps(chain_id TEXT NOT NULL,generation INTEGER NOT NULL,device_id TEXT NOT NULL,first_seq INTEGER NOT NULL,last_seq INTEGER NOT NULL,compaction_id TEXT NOT NULL,PRIMARY KEY(chain_id,generation,device_id,first_seq,last_seq))",
                    )
                }
            }

        val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE ps_legacy_migration_candidates (migration_id TEXT NOT NULL,entry_index INTEGER NOT NULL,group_key TEXT NOT NULL,source_sequence INTEGER NOT NULL,candidate_index INTEGER NOT NULL,source_record_id TEXT NOT NULL,source_object_hash TEXT NOT NULL,schema TEXT NOT NULL,entity_id TEXT NOT NULL,operation TEXT NOT NULL,requires_choice INTEGER NOT NULL,PRIMARY KEY(migration_id,entry_index))",
                    )
                    db.execSQL(
                        "CREATE INDEX index_ps_legacy_migration_candidates_migration_id_group_key ON ps_legacy_migration_candidates(migration_id,group_key)",
                    )
                    db.execSQL(
                        "CREATE INDEX index_ps_legacy_migration_candidates_migration_id_source_sequence_candidate_index ON ps_legacy_migration_candidates(migration_id,source_sequence,candidate_index)",
                    )
                    db.execSQL(
                        "CREATE TABLE ps_legacy_migration_choices (migration_id TEXT NOT NULL,group_key TEXT NOT NULL,selected_entry_index INTEGER NOT NULL,user_confirmed INTEGER NOT NULL,PRIMARY KEY(migration_id,group_key))",
                    )
                }
            }

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
                        ).addMigrations(
                            MIGRATION_1_3,
                            MIGRATION_2_3,
                            MIGRATION_3_4,
                            MIGRATION_4_5,
                            MIGRATION_5_6,
                            ProtocolMigration,
                            MIGRATION_7_8,
                            MIGRATION_8_9,
                            MIGRATION_9_10,
                            MIGRATION_10_11,
                            MIGRATION_11_12,
                        ).build()
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

        /** Retain old tables/data. Unknown legacy layouts fail closed without a destructive fallback. */
        val MIGRATION_1_3 = legacyMigration(1)
        val MIGRATION_2_3 = legacyMigration(2)

        private fun legacyMigration(version: Int) =
            object : Migration(version, MIGRATION_TARGET_VERSION) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    val columns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(pallasync_chain_state)").use { cursor ->
                        val name = cursor.getColumnIndexOrThrow("name")
                        while (cursor.moveToNext()) columns += cursor.getString(name)
                    }
                    check(columns.containsAll(setOf("chainId", "lamport", "key_epoch", "chain_vector_json"))) { "C_SCHEMA_VIOLATION" }
                    if ("latest_control_seq" !in
                        columns
                    ) {
                        db.execSQL("ALTER TABLE pallasync_chain_state ADD COLUMN latest_control_seq INTEGER NOT NULL DEFAULT 0")
                    }
                    if ("prev_control_hash" !in
                        columns
                    ) {
                        db.execSQL("ALTER TABLE pallasync_chain_state ADD COLUMN prev_control_hash TEXT NOT NULL DEFAULT ''")
                    }
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

        /**
         * v6 adds an index on chainId for faster device lookup.
         */
        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE INDEX IF NOT EXISTS index_pallasync_devices_chainId
                        ON pallasync_devices(chainId)
                        """.trimIndent(),
                    )
                }
            }
    }
}
