package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncClientStatusTest {
    @Test
    fun `partial state follows downloaded verified materialized and separate transparency positions`() {
        status(downloaded = 10, verified = 9, materialized = 9).partial shouldBe true
        status(downloaded = 10, verified = 10, materialized = 9).partial shouldBe true
        status(downloaded = 10, verified = 10, materialized = 10).partial shouldBe false
        status(downloaded = 10, verified = 10, materialized = 10, transparencyTarget = 11).partial shouldBe true
        status(downloaded = 10, verified = 10, materialized = 10, transparencyConflict = true).partial shouldBe true
        status(downloaded = 10, verified = 10, materialized = 10, initialPullCompleted = false).partial shouldBe true
    }

    private fun status(
        downloaded: Long,
        verified: Long,
        materialized: Long,
        transparencyTarget: Long = 4,
        transparencyConflict: Boolean = false,
        initialPullCompleted: Boolean = true,
    ): PallaSyncClientStatus =
        PallaSyncClientStatus(
            downloadedSeq = downloaded,
            verifiedSeq = verified,
            materializedSeq = materialized,
            verifiedTransparencyIndex = 4,
            observedTransparencyTargetIndex = transparencyTarget,
            transparencyObservationConflict = transparencyConflict,
            pendingOutbox = 0,
            authorGaps = 0,
            unresolvedQuarantine = 0,
            writeState = "active",
            initialPullCompleted = initialPullCompleted,
            searchCount = 0,
            searchCeiling = 1_000_000,
            viewedCount = 0,
            viewedCeiling = 1_000_000,
            seenCount = 0,
            seenCeiling = 10_000_000,
        )
}
