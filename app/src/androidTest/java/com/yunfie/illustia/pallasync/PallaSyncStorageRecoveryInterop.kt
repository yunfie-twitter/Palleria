package com.yunfie.illustia.pallasync

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals

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
}
