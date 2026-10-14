package com.yunfie.illustia.pallasync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yunfie.illustia.pallasync.data.PallaSyncDatabase
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
class PallaSyncSnapshotStagingTest {
    @Test
    fun `staging resumes only a compaction-listed exact ciphertext record`() =
        runBlocking<Unit> {
            val db =
                Room
                    .inMemoryDatabaseBuilder(
                        ApplicationProvider.getApplicationContext<Context>(),
                        PallaSyncDatabase::class.java,
                    ).build()
            try {
                val recordId = "11111111-1111-4111-8111-111111111111"
                val chain = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc"
                val compactionId = "33333333-3333-4333-8333-333333333333"
                val record =
                    """{"version":"3.0","chain_id":"$chain","generation":0,"record_id":"$recordId","device_id":"22222222-2222-4222-8222-222222222222","epoch":0,"collection_tag":null,"payload_nonce":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","encrypted_payload":"opaque","signature":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}"""
                val recordHash = checkNotNull(PallaSyncCore.objectHash(record))
                val compaction =
                    """{"compaction_id":"$compactionId","chain_id":"$chain","generation":0,"covered_through_seq":7,"snapshot_parts":[{"record_id":"$recordId","record_hash":"$recordHash"}]}"""
                val staging =
                    PallaSyncSnapshotStaging(
                        db.snapshotDao(),
                        chain,
                        compactionId,
                        generation = 0,
                        epoch = 0,
                        coveredThroughSeq = 7,
                        validatedCompactionJson = compaction,
                    )
                staging.retainDownloaded(0, record).signedJson shouldBe PallaSyncCore.canonicalJson(record)
                staging.resumeDownloaded().size shouldBe 1
                val conflict = runCatching { staging.retainDownloaded(0, record.replace("opaque", "changed")) }
                conflict.exceptionOrNull()?.message shouldBe "C_SNAPSHOT_INVALID"
            } finally {
                db.close()
            }
        }
}
