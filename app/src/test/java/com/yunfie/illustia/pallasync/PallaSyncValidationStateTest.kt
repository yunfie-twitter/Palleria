package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncValidationStateTest {
    @Test
    fun `missing dependency keeps page cursor uncommitted`() {
        recordPageMustRemainUncommitted("C_MISSING_DEPENDENCY") shouldBe true
        recordPageMustRemainUncommitted("C_SCHEMA_VIOLATION") shouldBe false
        recordPageMustRemainUncommitted(null) shouldBe false
    }
}
