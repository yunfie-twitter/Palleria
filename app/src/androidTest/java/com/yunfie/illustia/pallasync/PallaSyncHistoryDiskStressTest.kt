package com.yunfie.illustia.pallasync

import android.os.Debug
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.PALLASYNC_SEEN_SAFETY_LIMIT
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in on-device disk stress for §16.5/Appendix B IT-10; writes only a disposable Room DB. */
@RunWith(AndroidJUnit4::class)
class PallaSyncHistoryDiskStressTest {
    @Test(timeout = 1_800_000)
    fun tenMillionSeenRowsRemainDiskBackedAndKeysetPaged() = runBlocking<Unit> {
        assumeTrue(
            "Set pallasyncLargeHistoryStress=true for the destructive-sized disposable DB run",
            InstrumentationRegistry.getArguments().getString("pallasyncLargeHistoryStress") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseName = "pallasync-history-stress-${UUID.randomUUID()}.db"
        val chain = "history-stress-chain"
        val collection = "palleria.view_history/3"
        val scope = "seen"
        val device = ByteArray(16) { 1 }
        val record = ByteArray(16) { 2 }
        val seenCount =
            InstrumentationRegistry
                .getArguments()
                .getString("pallasyncStressRows")
                ?.toIntOrNull()
                ?: PALLASYNC_SEEN_SAFETY_LIMIT.toInt()
        require(seenCount in PAGE_SIZE * 2..PALLASYNC_SEEN_SAFETY_LIMIT.toInt())
        val database =
            Room.databaseBuilder(context, PallaSyncDatabase::class.java, databaseName)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
        try {
            database.pallaSyncDao().updateChainState(
                ChainStateEntity(chain, 0, 0, "{}", indexRevision = 1, writeState = "active"),
            )
            val sql = database.openHelper.writableDatabase
            val digitCount = (seenCount - 1).toString().length
            val aliases = (0 until digitCount).map { "d$it" }
            var decimalPower = 1
            val number =
                aliases.joinToString("+") { alias ->
                    val term = "$alias.d*$decimalPower"
                    decimalPower *= 10
                    term
                }
            val digitValues = (0..9).joinToString(",") { "($it)" }
            val digitCrossJoin = aliases.joinToString(" CROSS JOIN ") { "digits $it" }
            val historyInsert =
                sql.compileStatement(
                    "INSERT INTO ps_history(chain_id,collection,scope,last_access_seq,entity_id,lamport,op_rank,device_id,record_id,op_index) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?)",
                )

            val buildStart = System.nanoTime()
            sql.execSQL(
                "WITH digits(d) AS (VALUES $digitValues) " +
                    "INSERT INTO ps_entities(chain_id,collection,entity_id,lamport,op_rank,device_id,record_id,op_index,operation,scope,body_json,context_json,order_key) " +
                    "SELECT ?,?,('seen:'||($number)),($number)+1,1,?, ?,0,'upsert','seen','{}','{}',NULL " +
                    "FROM $digitCrossJoin WHERE ($number)<$seenCount",
                arrayOf(chain, collection, device, record),
            )
            var next = 0
            while (next < seenCount) {
                val end = minOf(next + INSERT_BATCH, seenCount)
                sql.beginTransaction()
                try {
                    while (next < end) {
                        val id = "seen:$next"
                        val key = ByteArray(Long.SIZE_BYTES)
                        val sequence = next.toLong()
                        for (byteIndex in key.indices) {
                            key[key.lastIndex - byteIndex] = (sequence ushr (byteIndex * 8)).toByte()
                        }

                        historyInsert.bindString(1, chain)
                        historyInsert.bindString(2, collection)
                        historyInsert.bindString(3, scope)
                        historyInsert.bindBlob(4, key)
                        historyInsert.bindString(5, id)
                        historyInsert.bindLong(6, next.toLong() + 1)
                        historyInsert.bindLong(7, 1)
                        historyInsert.bindBlob(8, device)
                        historyInsert.bindBlob(9, record)
                        historyInsert.bindLong(10, 0)
                        historyInsert.executeInsert()
                        historyInsert.clearBindings()
                        next++
                    }
                    sql.setTransactionSuccessful()
                } finally {
                    sql.endTransaction()
                }
                if (next % LOG_INTERVAL == 0 || next == seenCount) {
                    Log.i(TAG, "inserted=$next/$seenCount dbBytes=${context.getDatabasePath(databaseName).length()}")
                }
            }
            val buildMillis = (System.nanoTime() - buildStart) / 1_000_000
            val databaseBytes = context.getDatabasePath(databaseName).length()

            val dao = database.protocolDao()
            val projectionStart = System.nanoTime()
            val projection = dao.historyProjection(chain, collection, scope)
            val projectionMillis = (System.nanoTime() - projectionStart) / 1_000_000
            assertEquals(seenCount.toLong(), projection.liveCount)
            assertEquals(PALLASYNC_SEEN_SAFETY_LIMIT, projection.projectionLimit)
            if (seenCount == PALLASYNC_SEEN_SAFETY_LIMIT.toInt()) assertEquals("seen:0", projection.evictionEntity)
            val beforePssKb = Debug.getPss()
            val pagingStart = System.nanoTime()
            val first = dao.historySlice(chain, collection, scope, pageSize = PAGE_SIZE)
            val second = dao.historySlice(chain, collection, scope, first.next, PAGE_SIZE)
            val pagingMillis = (System.nanoTime() - pagingStart) / 1_000_000
            val afterPssKb = Debug.getPss()
            assertEquals(PAGE_SIZE, first.rows.size)
            assertNotNull(first.next)
            assertEquals(PAGE_SIZE, second.rows.size)
            assertNotNull(second.next)
            assertEquals("seen:${seenCount - 1}", first.rows.first().entityId)
            assertEquals("seen:${seenCount - PAGE_SIZE - 1}", second.rows.first().entityId)
            assertEquals(first.revision, second.revision)
            assert(afterPssKb - beforePssKb < MAX_PAGE_PSS_GROWTH_KB) {
                "page materialization grew process PSS by ${afterPssKb - beforePssKb} KiB"
            }

            Log.i(
                TAG,
                "rows=$seenCount dbBytes=$databaseBytes buildMs=$buildMillis firstPage=${first.rows.size} " +
                    "projectionMs=$projectionMillis pagingMs=$pagingMillis secondPage=${second.rows.size} " +
                    "pssBeforeKb=$beforePssKb pssAfterKb=$afterPssKb",
            )
        } finally {
            database.close()
            check(context.deleteDatabase(databaseName)) { "stress database cleanup failed" }
        }
    }

    private companion object {
        const val TAG = "PallaSyncHistoryStress"
        const val INSERT_BATCH = 10_000
        const val LOG_INTERVAL = 500_000
        const val PAGE_SIZE = 200
        const val MAX_PAGE_PSS_GROWTH_KB = 128 * 1024
    }
}
