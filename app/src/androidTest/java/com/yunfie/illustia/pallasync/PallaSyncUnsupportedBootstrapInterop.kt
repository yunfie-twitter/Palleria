package com.yunfie.illustia.pallasync

import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/** Real Python-authored unsupported ciphertext must not be treated as a completed bootstrap. */
internal object PallaSyncUnsupportedBootstrapInterop {
    suspend fun verify(
        args: Bundle,
        joined: Boolean,
        store: PallaSyncLocalStore,
        keys: PallaSyncKeystore,
    ) {
        assertFalse("Unsupported content must not claim complete activation", joined)
        val chain = checkNotNull(args.getString("unsupported_chain"))
        val id = checkNotNull(args.getString("unsupported_record_id"))
        val hash = checkNotNull(args.getString("unsupported_record_hash"))
        val sequence = checkNotNull(args.getString("unsupported_sequence")).toLong()
        val state = checkNotNull(store.dao.getChainState(chain))
        val record = checkNotNull(store.dao.getInboxRecord(chain, id))
        assertEquals("unsupported_collection", record.status)
        assertEquals("C_UNSUPPORTED_SCHEMA", record.quarantineReason)
        assertEquals(sequence, record.relaySeq)
        assertEquals(hash, PallaSyncCore.objectHash(record.rawRecordJson))
        assertEquals(listOf(record), store.dao.unsupportedInboxPage(chain, sequence, -1, "", 50))
        val log = checkNotNull(store.protocolDao().logEntry(chain, checkNotNull(state.logId), sequence))
        assertEquals("unsupported_collection", log.validationState)
        assertTrue(log.receiptJson.isNotBlank())
        assertNull(store.protocolDao().quarantineEvidence(chain, hash))
        assertNotNull(store.protocolDao().entity(chain, SEARCH_HISTORY_SCHEMA_V3, "Python after unsupported"))
        assertEquals(sequence - 1, state.verifiedSeq)
        assertEquals(sequence - 1, state.materializedSeq)
        assertTrue(state.downloadedSeq > sequence)
        assertFalse(state.initialPullCompleted)
        assertEquals("activation_preparing", state.writeState)
        assertNull(keys.getActiveChainKeys())
        assertEquals(chain, checkNotNull(keys.getPendingChainKeys()).chainId)
        assertEquals(PallaSyncCore.objectHash(checkNotNull(store.protocolDao().currentAuthority(chain))), keys.authorityHash(chain))
        assertTrue(PalleriaSyncCoordinator.syncLogs.value.any { it.contains("C_UNSUPPORTED_SCHEMA") })
        InstrumentationRegistry.getInstrumentation().sendStatus(
            0,
            Bundle().apply { putString("pallasync_unsupported_bootstrap", "retained_partial_prefix") },
        )
    }
}
