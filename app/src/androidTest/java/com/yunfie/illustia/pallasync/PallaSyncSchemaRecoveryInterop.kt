package com.yunfie.illustia.pallasync

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals

/** Models an upgrade that now understands an immutable, previously unsupported accepted record. */
internal object PallaSyncSchemaRecoveryInterop {
    suspend fun exercise(
        coordinator: PalleriaSyncCoordinator,
        store: PallaSyncLocalStore,
        keys: PallaSyncKeystore,
        chain: String,
    ) {
        val event = checkNotNull(store.protocolDao().firstAcceptedEvent(chain))
        val before = checkNotNull(store.dao.getChainState(chain))
        val activeKeys = keys.getActiveChainKeys()
        val authority = keys.authorityHash(chain)
        val anchor = keys.transparencyPin(chain)
        val outbox = store.dao.pendingOutboxCount(chain)
        store.dao.updateInboxDisposition(chain, event.recordId, event.signedJson, "unsupported_collection", "C_UNSUPPORTED_SCHEMA")
        val staged = checkNotNull(store.dao.getInboxRecord(chain, event.recordId))
        assertEquals(event.signedJson, staged.rawRecordJson)
        assertEquals(1L, coordinator.retryUnsupportedInbox())
        val applied = checkNotNull(store.dao.getInboxRecord(chain, event.recordId))
        assertEquals("applied", applied.status)
        assertEquals(staged.rawRecordJson, applied.rawRecordJson)
        assertEquals(event.objectHash, PallaSyncCore.objectHash(applied.rawRecordJson))
        assertEquals(before, store.dao.getChainState(chain))
        assertEquals(activeKeys, keys.getActiveChainKeys())
        assertEquals(authority, keys.authorityHash(chain))
        assertEquals(anchor, keys.transparencyPin(chain))
        assertEquals(outbox, store.dao.pendingOutboxCount(chain))
        assertEquals(0L, coordinator.retryUnsupportedInbox())
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("pallasync_schema_recovery", "revalidated_original_once") },
        )
    }
}
