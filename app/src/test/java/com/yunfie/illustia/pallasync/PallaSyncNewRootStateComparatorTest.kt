package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.ChainStateEntity
import com.yunfie.illustia.pallasync.data.EntityRegisterEntity
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.ScopeRegisterEntity
import com.yunfie.illustia.pallasync.data.WinnerColumns
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncNewRootStateComparatorTest {
    @Test
    fun `new root verification compares logical entities tombstones and registers without winner identity`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val source = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
                val target = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                val sourceLog = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
                val targetLog = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
                val sourceEpoch = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                val targetEpoch = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
                db.pallaSyncDao().updateChainState(
                    ChainStateEntity(
                        source,
                        5,
                        0,
                        "{}",
                        logId = sourceLog,
                        epochHash = sourceEpoch,
                        verifiedHash = sourceEpoch,
                        writeState = "active",
                    ),
                )
                db.pallaSyncDao().updateChainState(
                    ChainStateEntity(
                        target,
                        5,
                        0,
                        "{}",
                        logId = targetLog,
                        epochHash = targetEpoch,
                        verifiedHash = targetEpoch,
                        writeState = "active",
                    ),
                )
                val sourceWinner = WinnerColumns(3, 0, byteArrayOf(1), byteArrayOf(2), 0)
                val targetWinner = WinnerColumns(3, 0, byteArrayOf(8), byteArrayOf(9), 0)
                val sourceRows =
                    listOf(
                        EntityRegisterEntity(
                            source,
                            "history.v1",
                            "alpha",
                            sourceWinner,
                            "upsert",
                            "history",
                            """{"query":"alpha"}""",
                            """{"order_key":"A"}""",
                            "A",
                        ),
                        EntityRegisterEntity(source, "history.v1", "gone", sourceWinner, "delete", "history", "{}", "{}", null),
                    )
                val targetRows =
                    sourceRows.map { row ->
                        row.copy(chainId = target, winner = targetWinner)
                    }
                sourceRows.forEach { db.protocolDao().putEntity(it) }
                targetRows.forEach { db.protocolDao().putEntity(it) }
                listOf("clear", "retention").forEach { kind ->
                    val body = if (kind == "clear") "{}" else """{"mode":"retain","scope":"history"}"""
                    db.protocolDao().putRegister(ScopeRegisterEntity(source, "history.v1", "history", kind, sourceWinner, body))
                    db.protocolDao().putRegister(ScopeRegisterEntity(target, "history.v1", "history", kind, targetWinner, body))
                }
                val checkpoint =
                    NewRootMigrationCheckpoint(
                        NewRootMigrationSourceAnchor(source, 0, sourceLog, sourceEpoch, 0, sourceEpoch, 0),
                        NewRootMigrationCursor(),
                        0,
                        1,
                        1,
                        1,
                        true,
                    )
                val receipts = NewRootMigrationReceiptStatus(staged = 1, receiptVerified = 1, complete = true)
                val store = PallaSyncLocalStore(ApplicationProvider.getApplicationContext(), db)

                val matchingState =
                    PallaSyncSnapshotSemanticComparator.compareNewRootMaterializedState(store, source, target, checkpoint, receipts)
                matchingState shouldBe true

                db.protocolDao().putEntity(targetRows.first().copy(bodyJson = """{"query":"different"}"""))
                val mismatchingState =
                    PallaSyncSnapshotSemanticComparator.compareNewRootMaterializedState(store, source, target, checkpoint, receipts)
                mismatchingState shouldBe false
            } finally {
                db.close()
            }
        }
}
