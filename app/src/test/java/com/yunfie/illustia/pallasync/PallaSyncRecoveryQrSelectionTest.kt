package com.yunfie.illustia.pallasync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** §18.6: only the current bounded QR scan may supply the peer descriptor. */
class PallaSyncRecoveryQrSelectionTest {
    @Test
    fun cancellationAndInvalidScansClearAnyPreviouslySelectedDescriptor() {
        assertNull(PallaSyncRecoveryQrSelection.fromScan(null))
        assertNull(PallaSyncRecoveryQrSelection.fromScan(""))
        assertNull(PallaSyncRecoveryQrSelection.fromScan("\uD800"))
    }

    @Test
    fun acceptsOnlyNonemptyUtf8WithinTransferLimit() {
        val descriptor = "descriptor-v3"
        assertEquals(descriptor, PallaSyncRecoveryQrSelection.fromScan(descriptor))
        assertNull(PallaSyncRecoveryQrSelection.fromScan("x".repeat(PallaSyncRecoveryLocalFiles.MAX_TRANSFER_BYTES + 1)))
    }
}
