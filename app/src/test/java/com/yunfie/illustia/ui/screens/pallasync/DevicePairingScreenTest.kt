package com.yunfie.illustia.ui.screens.pallasync

import com.yunfie.illustia.R
import org.junit.Assert.assertEquals
import org.junit.Test

class DevicePairingScreenTest {
    @Test
    fun enrollmentGuideFollowsThePersistedStage() {
        assertEquals(R.string.pallasync_peer_subject_step_scan, peerEnrollmentGuidanceResource(false, false, false))
        assertEquals(R.string.pallasync_peer_subject_step_compare, peerEnrollmentGuidanceResource(true, false, false))
        assertEquals(R.string.pallasync_peer_subject_step_transfer, peerEnrollmentGuidanceResource(true, true, false))
        assertEquals(R.string.pallasync_peer_subject_step_request, peerEnrollmentGuidanceResource(false, false, true))
    }
}
