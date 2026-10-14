package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.ProtocolMigration
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncMigrationTest {
    @Test
    fun `supported old layouts retain exact outbox and state through version eight and reopen`() =
        runBlocking<Unit> {
            val context = ApplicationProvider.getApplicationContext<Context>()
            for (version in 1..6) {
                val name = "pallasync-migration-$version"
                val factory = FrameworkSQLiteOpenHelperFactory()
                val old =
                    factory.create(
                        SupportSQLiteOpenHelper.Configuration
                            .builder(context)
                            .name(name)
                            .callback(
                                object : SupportSQLiteOpenHelper.Callback(version) {
                                    override fun onCreate(db: SupportSQLiteDatabase) {
                                        db.execSQL(
                                            "CREATE TABLE pallasync_chain_state(chainId TEXT NOT NULL PRIMARY KEY," +
                                                "lamport INTEGER NOT NULL,key_epoch INTEGER NOT NULL,chain_vector_json TEXT NOT NULL" +
                                                (
                                                    if (version >=
                                                        3
                                                    ) {
                                                        ",latest_control_seq INTEGER NOT NULL,prev_control_hash TEXT NOT NULL"
                                                    } else {
                                                        ""
                                                    }
                                                ) +
                                                (
                                                    if (version >=
                                                        4
                                                    ) {
                                                        ",last_relay_seq INTEGER NOT NULL DEFAULT 0,initial_pull_completed INTEGER NOT NULL DEFAULT 1"
                                                    } else {
                                                        ""
                                                    }
                                                ) +
                                                ")",
                                        )
                                        db.execSQL(
                                            "CREATE TABLE pallasync_outbox(id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                                                "chain_id TEXT NOT NULL,device_seq INTEGER NOT NULL,status TEXT NOT NULL," +
                                                "event_json TEXT NOT NULL,created_at INTEGER NOT NULL)",
                                        )
                                        db.execSQL(
                                            "CREATE TABLE pallasync_devices(deviceId TEXT NOT NULL PRIMARY KEY," +
                                                "chainId TEXT NOT NULL,deviceName TEXT NOT NULL,publicKey TEXT NOT NULL," +
                                                "joinedAtMs INTEGER NOT NULL)",
                                        )
                                        if (version >= 4) {
                                            PallaSyncDatabase.MIGRATION_3_4.let {
                                                db.execSQL(
                                                    "CREATE TABLE pallasync_inbox(chain_id TEXT NOT NULL,record_id TEXT NOT NULL," +
                                                        "relay_seq INTEGER NOT NULL,status TEXT NOT NULL,raw_record_json TEXT NOT NULL," +
                                                        "quarantine_reason TEXT,received_at_ms INTEGER NOT NULL," +
                                                        "PRIMARY KEY(chain_id,record_id))",
                                                )
                                                db.execSQL(
                                                    "CREATE INDEX index_pallasync_inbox_chain_id_relay_seq ON pallasync_inbox(chain_id,relay_seq)",
                                                )
                                                db.execSQL(
                                                    "CREATE INDEX index_pallasync_inbox_chain_id_status ON pallasync_inbox(chain_id,status)",
                                                )
                                            }
                                        }
                                        if (version >= 5) PallaSyncDatabase.MIGRATION_4_5.migrate(db)
                                        if (version >= 6) PallaSyncDatabase.MIGRATION_5_6.migrate(db)
                                        insertLegacyOutbox(db)
                                        val columns =
                                            "chainId,lamport,key_epoch,chain_vector_json" +
                                                (if (version >= 3) ",latest_control_seq,prev_control_hash" else "")
                                        val values = "'chain',19,0,'{\"device\":11}'" + (if (version >= 3) ",0,''" else "")
                                        db.execSQL("INSERT INTO pallasync_chain_state($columns) VALUES($values)")
                                    }

                                    override fun onUpgrade(
                                        db: SupportSQLiteDatabase,
                                        oldVersion: Int,
                                        newVersion: Int,
                                    ) = error("Unexpected fixture upgrade")
                                },
                            ).build(),
                    )
                old.writableDatabase
                old.close()

                fun open() =
                    Room
                        .databaseBuilder(context, PallaSyncDatabase::class.java, name)
                        .addMigrations(
                            PallaSyncDatabase.MIGRATION_1_3,
                            PallaSyncDatabase.MIGRATION_2_3,
                            PallaSyncDatabase.MIGRATION_3_4,
                            PallaSyncDatabase.MIGRATION_4_5,
                            PallaSyncDatabase.MIGRATION_5_6,
                            ProtocolMigration,
                            PallaSyncDatabase.MIGRATION_7_8,
                        ).build()
                repeat(2) { attempt ->
                    val db = open()
                    try {
                        val row =
                            db.openHelper.readableDatabase.query("SELECT id,event_json,status FROM pallasync_outbox").use { cursor ->
                                cursor.moveToFirst() shouldBe true
                                Triple(cursor.getLong(0), cursor.getString(1), cursor.getString(2))
                            }
                        row shouldBe Triple(17L, "exact legacy signed bytes", "legacy_pending_verification")
                        val state = db.pallaSyncDao().getChainState("chain")
                        state?.lamport shouldBe 19
                        state?.authorSeq shouldBe 0
                        state?.writeState shouldBe "pending_verification"
                        state?.chainVectorJson shouldBe "{\"device\":11}"
                        if (attempt == 0) {
                            db.openHelper.writableDatabase.execSQL(
                                "INSERT INTO ps_snapshots(chain_id,compaction_id,part_index,part_hash," +
                                    "signed_json,covered_seq,coverage_json,staging_state) " +
                                    "VALUES('chain','compaction',0,'hash','{}',0,'{}','downloaded')",
                            )
                        }
                        val missingReceipt =
                            db.openHelper.readableDatabase
                                .query("SELECT upload_receipt_json FROM ps_snapshots WHERE chain_id='chain'")
                                .use { cursor ->
                                    cursor.moveToFirst() shouldBe true
                                    cursor.getString(0)
                                }
                        missingReceipt shouldBe "null"
                    } finally {
                        db.close()
                    }
                }
                context.deleteDatabase(name)
            }
        }

    private fun insertLegacyOutbox(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO pallasync_outbox(id,chain_id,device_seq,status,event_json,created_at) " +
                "VALUES(17,'chain',11,'queued','exact legacy signed bytes',99)",
        )
    }
}
