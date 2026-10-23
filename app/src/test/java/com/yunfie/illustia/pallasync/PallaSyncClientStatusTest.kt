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

    @Test
    fun `capacity status keeps cold history visible when the eager projection reaches its ceiling`() {
        status(downloaded = 10, verified = 10, materialized = 10, searchCount = 999_999).safetyLimitReached shouldBe false
        val exactCeiling =
            status(downloaded = 10, verified = 10, materialized = 10).copy(
                searchCount = 1_000_000,
                searchProjectionCount = 1_000_000,
            )
        exactCeiling.searchColdCount shouldBe 0
        exactCeiling.safetyLimitReached shouldBe true

        val status =
            status(downloaded = 10, verified = 10, materialized = 10).copy(
                searchCount = 1_000_010,
                searchCeiling = 1_000_000,
                searchProjectionCount = 1_000_000,
            )

        status.searchColdCount shouldBe 10
        status.safetyLimitReached shouldBe true
    }

    @Test
    fun `logical settings counts distinguish full totals from their eager projections`() {
        val status =
            status(downloaded = 10, verified = 10, materialized = 10).copy(
                favoriteCount = 250,
                favoriteProjectionCount = 200,
                muteCount = 203,
                muteProjectionCount = 200,
            )

        status.favoriteColdCount shouldBe 50
        status.muteColdCount shouldBe 3
    }

    private fun status(
        downloaded: Long,
        verified: Long,
        materialized: Long,
        transparencyTarget: Long = 4,
        transparencyConflict: Boolean = false,
        initialPullCompleted: Boolean = true,
        searchCount: Long = 0,
        searchProjectionCount: Long = searchCount,
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
            searchCount = searchCount,
            searchCeiling = 1_000_000,
            viewedCount = 0,
            viewedCeiling = 1_000_000,
            seenCount = 0,
            seenCeiling = 10_000_000,
            searchProjectionCount = searchProjectionCount,
        )
}
