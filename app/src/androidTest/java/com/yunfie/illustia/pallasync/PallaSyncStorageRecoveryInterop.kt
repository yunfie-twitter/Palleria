package com.yunfie.illustia.pallasync

import android.database.sqlite.SQLiteFullException
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.yunfie.illustia.pallasync.data.PallaSyncInboxEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File

/** Revalidate a real relay-accepted signed/encrypted object from a staged disk-full disposition. */
internal object PallaSyncStorageRecoveryInterop {
    suspend fun exercise(
        coordinator: PalleriaSyncCoordinator,
        store: PallaSyncLocalStore,
        keys: PallaSyncKeystore,
        chain: String,
    ) {
        val event = checkNotNull(store.protocolDao().firstAcceptedEvent(chain))
        val savedKeys = checkNotNull(keys.getActiveChainKeys())
        val before = checkNotNull(store.dao.getChainState(chain))
        val outbox = store.dao.pendingOutboxCount(chain)
        store.snapshotDatabase().openHelper.writableDatabase.execSQL(
            "UPDATE pallasync_inbox SET status='operationally_blocked',quarantine_reason='C_LOCAL_STORAGE_FULL' " +
                "WHERE chain_id=? AND record_id=?",
            arrayOf(chain, event.recordId),
        )
        val staged = checkNotNull(store.dao.getInboxRecord(chain, event.recordId))
        assertEquals(event.signedJson, staged.rawRecordJson)
        assertEquals(1L, coordinator.getClientStatus()?.storageBlockedRecords)
        assertEquals(emptyList<Any>(), store.dao.pendingInboxEvidenceRecords(chain, 1))
        val sql = store.snapshotDatabase().openHelper.writableDatabase
        if (File(sql.path).name == PallaSyncInteropDatabase.NAME) {
            assertSqliteFullRollback(coordinator, store, keys, staged)
        }
        assertEquals(1, coordinator.retryStorageBlockedInbox(limit = 1))
        val applied = checkNotNull(store.dao.getInboxRecord(chain, event.recordId))
        assertEquals("applied", applied.status)
        assertEquals(staged.rawRecordJson, applied.rawRecordJson)
        assertEquals(event.objectHash, PallaSyncCore.objectHash(applied.rawRecordJson))
        assertEquals(savedKeys, keys.getActiveChainKeys())
        assertEquals(outbox, store.dao.pendingOutboxCount(chain))
        val after = checkNotNull(store.dao.getChainState(chain))
        assertEquals(before.downloadedSeq, after.downloadedSeq)
        assertEquals(before.verifiedSeq, after.verifiedSeq)
        assertEquals(before.materializedSeq, after.materializedSeq)
        assertEquals(before.transparencyIndex, after.transparencyIndex)
        assertEquals(before.transparencyHash, after.transparencyHash)
        assertEquals(0L, coordinator.getClientStatus()?.storageBlockedRecords)
        assertEquals(0, coordinator.retryStorageBlockedInbox(limit = 1))
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("pallasync_local_storage_recovery", "revalidated_original_once") },
        )
    }

    private suspend fun assertSqliteFullRollback(
        coordinator: PalleriaSyncCoordinator,
        store: PallaSyncLocalStore,
        keys: PallaSyncKeystore,
        staged: PallaSyncInboxEntity,
    ) {
        val chain = staged.chainId
        val state = store.dao.getChainState(chain)
        val activeKeys = keys.getActiveChainKeys()
        val authority = keys.authorityHash(chain)
        val anchor = keys.transparencyPin(chain)
        val outbox = store.dao.pendingOutboxCount(chain)
        PallaSyncSqliteCapacityFault(store.snapshotDatabase().openHelper.writableDatabase).use {
            val failure = runCatching { coordinator.retryStorageBlockedInbox(limit = 1) }.exceptionOrNull()
            assertTrue(
                "Expected SQLiteFullException, got ${failure?.javaClass?.simpleName}",
                generateSequence(failure) { cause -> cause.cause }.any { cause -> cause is SQLiteFullException },
            )
            assertEquals(staged, store.dao.getInboxRecord(chain, staged.recordId))
            assertEquals(state, store.dao.getChainState(chain))
            assertEquals(activeKeys, keys.getActiveChainKeys())
            assertEquals(authority, keys.authorityHash(chain))
            assertEquals(anchor, keys.transparencyPin(chain))
            assertEquals(outbox, store.dao.pendingOutboxCount(chain))
            assertEquals(1L, coordinator.getClientStatus()?.storageBlockedRecords)
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("pallasync_sqlite_full_recovery", "transaction_rolled_back") },
        )
    }
}
