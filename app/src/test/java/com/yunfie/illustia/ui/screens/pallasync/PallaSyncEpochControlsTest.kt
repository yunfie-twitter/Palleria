package com.yunfie.illustia.ui.screens.pallasync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PallaSyncEpochControlsTest {
    @Test
    fun historyVerificationCanRecoverWhenEpochStatusIsUnavailable() {
        assertTrue(canFetchEpochHistory(synchronizationEnabled = true))
        assertFalse(canFetchEpochHistory(synchronizationEnabled = false))
    }
}
