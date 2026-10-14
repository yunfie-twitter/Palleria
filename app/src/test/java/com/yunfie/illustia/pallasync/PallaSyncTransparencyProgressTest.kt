package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncTransparencyProgressTest {
    private val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val log = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

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
}
