package com.yunfie.illustia.ui.screens.pallasync

import com.yunfie.illustia.R
import com.yunfie.illustia.pallasync.PallaSyncPeerJournal
import org.junit.Assert.assertEquals
import org.junit.Test

class PallaSyncDevicesScreenTest {
    @Test
    fun invitationInstructionsFollowTheProtocolStage() {
        assertEquals(R.string.pallasync_peer_invitation_progress, peerInvitationProgressResource(PallaSyncPeerJournal.Stage.OFFERED))
        assertEquals(R.string.pallasync_peer_invitation_compare_guidance, peerInvitationProgressResource(PallaSyncPeerJournal.Stage.BOUND))
        assertEquals(
            R.string.pallasync_peer_invitation_transfer_guidance,
            peerInvitationProgressResource(PallaSyncPeerJournal.Stage.TRANSFERRED),
        )
        assertEquals(R.string.pallasync_peer_invitation_wait_guidance, peerInvitationProgressResource(PallaSyncPeerJournal.Stage.SUBMITTED))
        assertEquals(
            R.string.pallasync_peer_invitation_acceptance_guidance,
            peerInvitationProgressResource(PallaSyncPeerJournal.Stage.ACCEPTED),
        )
    }
}
