package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncTransparencyProgressTest {
    private val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val log = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

    @Test
    fun `an unfinished signed target is resumed until its exact index is verified`() {
        PallaSyncTransparencyProgress.shouldResumeSavedTarget(savedTargetIndex = 19, verifiedIndex = 12) shouldBe true
        PallaSyncTransparencyProgress.shouldResumeSavedTarget(savedTargetIndex = 19, verifiedIndex = 19) shouldBe false
        PallaSyncTransparencyProgress.shouldResumeSavedTarget(savedTargetIndex = 12, verifiedIndex = 19) shouldBe false
    }

    @Test
    fun `same transparency id exposes separate signed target without lowering verified anchor`() {
        PallaSyncTransparencyProgress.fromSavedHead(id, log, 12, """{"transparency_id":"$id","log_id":"$log","entry_index":19}""") shouldBe
            PallaSyncTransparencyProgress(12, 19, false)
        PallaSyncTransparencyProgress.fromSavedHead(id, log, 19, """{"transparency_id":"$id","log_id":"$log","entry_index":4}""") shouldBe
            PallaSyncTransparencyProgress(19, 19, false)
    }

    @Test
    fun `different chain or malformed saved head is visible conflict and never target progress`() {
        PallaSyncTransparencyProgress
            .fromSavedHead(
                id,
                log,
                12,
                """{"transparency_id":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","log_id":"$log","entry_index":99}""",
            ) shouldBe
            PallaSyncTransparencyProgress(12, 12, true)
        PallaSyncTransparencyProgress
            .fromSavedHead(
                id,
                log,
                12,
                """{"transparency_id":"$id","log_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd","entry_index":99}""",
            ) shouldBe
            PallaSyncTransparencyProgress(12, 12, true)
        PallaSyncTransparencyProgress.fromSavedHead(id, log, 12, "not-json") shouldBe
            PallaSyncTransparencyProgress(12, 12, true)
    }

    @Test
    fun `large safe-integer target remains exact and separate from the verified anchor`() {
        val verified = 9_007_199_254_740_990L
        val target = 9_007_199_254_740_991L

        PallaSyncTransparencyProgress
            .fromSavedHead(
                id,
                log,
                verified,
                """{"transparency_id":"$id","log_id":"$log","entry_index":$target}""",
            ) shouldBe PallaSyncTransparencyProgress(verified, target, false)
    }
}
