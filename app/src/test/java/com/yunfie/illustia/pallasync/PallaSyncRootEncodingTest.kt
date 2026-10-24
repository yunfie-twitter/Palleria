package com.yunfie.illustia.pallasync

import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncRootEncodingTest {
    @Test
    fun `root input preserves whitespace and valid unicode without replacement`() {
        val input = "  e\u0301 日本語 😀\t\n "
        val bytes = strictRootUtf8(input)
        try {
            bytes.toString(Charsets.UTF_8) shouldBe input
        } finally {
            bytes.fill(0)
        }
    }

    @Test
    fun `unpaired surrogates cannot become a different passphrase`() {
        for (input in listOf("\uD800", "\uDC00", "a\uD800b", "\uD800\uD800")) {
            val error = runCatching { strictRootUtf8(input) }.exceptionOrNull()
            (error is IllegalStateException) shouldBe true
            error?.message shouldBe "C_RECOVERY_PARAMETER_INVALID"
        }
    }
}
