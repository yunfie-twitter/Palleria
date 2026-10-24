package com.yunfie.illustia.pallasync

import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.Closeable
import java.io.File

/** Causes real SQLITE_FULL while completing an inbox transaction, only in the disposable fixture DB. */
internal class PallaSyncSqliteCapacityFault(
    private val sql: SupportSQLiteDatabase,
) : Closeable {
    private val originalLimit: Long

    init {
        check(File(sql.path).name == PallaSyncInteropDatabase.NAME)
        check(!sql.isWriteAheadLoggingEnabled)
        originalLimit = scalar("PRAGMA max_page_count")
        sql.execSQL("CREATE TABLE storage_fault_padding (payload BLOB NOT NULL)")
        val allocation = (scalar("PRAGMA page_count") + TRIGGER_HEADROOM_PAGES) * scalar("PRAGMA page_size")
        check(allocation in 1..MAX_FIXTURE_ALLOCATION)
        sql.execSQL(
            "CREATE TRIGGER storage_fault_on_apply AFTER UPDATE OF status ON pallasync_inbox " +
                "WHEN NEW.status='applied' AND OLD.status='operationally_blocked' " +
                "AND OLD.quarantine_reason='C_LOCAL_STORAGE_FULL' BEGIN " +
                "INSERT INTO storage_fault_padding(payload) VALUES (zeroblob($allocation)); END",
        )
        val currentPages = scalar("PRAGMA page_count")
        check(scalar("PRAGMA max_page_count=$currentPages") == currentPages)
    }

    override fun close() {
        check(scalar("PRAGMA max_page_count=$originalLimit") == originalLimit)
        sql.execSQL("DROP TRIGGER storage_fault_on_apply")
        sql.execSQL("DROP TABLE storage_fault_padding")
    }

    private fun scalar(query: String): Long =
        sql.query(query).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private companion object {
        const val TRIGGER_HEADROOM_PAGES = 16L
        const val MAX_FIXTURE_ALLOCATION = 64L * 1024 * 1024
    }
}
