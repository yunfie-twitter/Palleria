package com.yunfie.illustia.pallasync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PallaSyncGenesisFailureTest {
    @Test
    fun protocolRejectionKeepsRelayCodeAndStatus() {
        assertEquals(
            "Genesis request rejected: C_BOOTSTRAP_TRUST_REQUIRED (HTTP 403)",
            PallaSyncHttpResult.ProtocolError("C_BOOTSTRAP_TRUST_REQUIRED", 403).genesisFailureSummary(),
        )
    }

    @Test
    fun retryableFailureKeepsWaitAndDoesNotClaimMissingDependency() {
        val summary = PallaSyncHttpResult.Retryable("relay busy", 1_500, 503).genesisFailureSummary()

        assertEquals("Genesis request unresolved: relay busy (HTTP 503); retry after 1500ms", summary)
        assertFalse(summary.contains("C_MISSING_DEPENDENCY"))
    }

    @Test
    fun goneDoesNotAuthorizeActivationOrLocalDeletion() {
        assertEquals(
            "Genesis endpoint returned HTTP 410; activation was not committed",
            PallaSyncHttpResult.Gone.genesisFailureSummary(),
        )
    }
}
