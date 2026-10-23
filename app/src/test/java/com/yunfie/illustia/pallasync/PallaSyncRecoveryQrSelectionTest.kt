package com.yunfie.illustia.pallasync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §18.6: only the current bounded QR scan may supply the peer descriptor. */
class PallaSyncRecoveryQrSelectionTest {
    @Test
    fun cancellationAndInvalidScansClearAnyPreviouslySelectedDescriptor() {
        val selection = PallaSyncRecoveryQrSelection()
        assertFalse(selection.completeScan("descriptor-v3"))
        assertEquals("descriptor-v3", selection.descriptor)

        selection.beginScan()
        assertNull(selection.descriptor)
        assertFalse(selection.completeScan(null))
        assertNull(selection.descriptor)

        assertFalse(selection.completeScan("descriptor-v3"))
        selection.beginScan()
        assertTrue(selection.completeScan(""))
        assertNull(selection.descriptor)
        assertTrue(selection.completeScan("\uD800"))
        assertNull(selection.descriptor)
    }

    @Test
    fun acceptsOnlyNonemptyUtf8WithinTransferLimit() {
        val descriptor = "descriptor-v3"
        assertEquals(descriptor, PallaSyncRecoveryQrSelection.fromScan(descriptor))
        assertNull(PallaSyncRecoveryQrSelection.fromScan("x".repeat(PallaSyncRecoveryLocalFiles.MAX_TRANSFER_BYTES + 1)))
    }

    @Test
    fun choosingPeerFileInvalidatesTheQrTrustDecision() {
        val selection = PallaSyncRecoveryQrSelection()
        assertFalse(selection.completeScan("descriptor-v3"))
        selection.selectFile()
        assertNull(selection.descriptor)
    }
}
