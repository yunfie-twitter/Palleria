package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class PallaSyncInboxStorageRecoveryTest {
    @Test
    fun `capacity failure retains exact ciphertext and all dispositions`() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val database = Room.inMemoryDatabaseBuilder(context, PallaSyncDatabase::class.java).build()
            try {
                val dao = database.pallaSyncDao()
                val records =
                    listOf(
                        record("later", 8),
                        record("earlier", 4),
                        record("unsupported", 1).copy(status = "unsupported_collection", quarantineReason = "C_UNSUPPORTED_SCHEMA"),
                        record("policy", 2).copy(quarantineReason = "C_WRITE_PAUSED_BY_POLICY"),
                        record("signature", 3).copy(status = "quarantined", quarantineReason = "C_SIGNATURE_INVALID"),
                        record("other-chain", 0).copy(chainId = "other"),
                    )
                dao.insertInboxRecords(records)
                dao.storageBlockedInboxCount("chain") shouldBe 2L
                dao.storageBlockedInboxRecords("chain", 1) shouldBe listOf(records[1])
                val selected = dao.storageBlockedInboxRecords("chain", 50)
                selected shouldBe listOf(records[1], records[0])
                listOf(null, 0L, 1L).forEach { available ->
                    shouldThrow<IllegalStateException> {
                        PallaSyncInboxStorageCapacity.requireAvailable(selected.first(), available)
                    }.message shouldBe "C_LOCAL_STORAGE_FULL"
                }
                PallaSyncInboxStorageCapacity.requireAvailable(selected.first(), Long.MAX_VALUE)
                records.forEach { dao.getInboxRecord(it.chainId, it.recordId) shouldBe it }
            } finally {
                database.close()
            }
        }

    @Test
    fun `capacity retry cannot authorize other operational or integrity failures`() {
        val original = record("storage", 1)
        PallaSyncInboxRetry.LOCAL_STORAGE.accepts(original) shouldBe true
        PallaSyncInboxRetry.EVIDENCE.accepts(original) shouldBe false
        listOf("C_WRITE_PAUSED_BY_POLICY", "C_COUNTER_EXHAUSTED", "C_RELAY_POLICY_CONSENT_REQUIRED").forEach { reason ->
            PallaSyncInboxRetry.LOCAL_STORAGE.accepts(original.copy(quarantineReason = reason)) shouldBe false
        }
        listOf("quarantined", "chain_suspended", "pending_dependency", "applied").forEach { status ->
            PallaSyncInboxRetry.LOCAL_STORAGE.accepts(original.copy(status = status)) shouldBe false
        }
    }

    private fun record(
        id: String,
        sequence: Long,
    ) = PallaSyncInboxEntity(
        chainId = "chain",
        recordId = id,
        relaySeq = sequence,
        status = "operationally_blocked",
        rawRecordJson = "{\"ciphertext\":\"immutable-$id\"}",
        quarantineReason = "C_LOCAL_STORAGE_FULL",
    )
}
