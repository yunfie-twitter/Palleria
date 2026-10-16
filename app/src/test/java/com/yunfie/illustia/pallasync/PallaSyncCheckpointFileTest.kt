package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.ByteArrayInputStream

class PallaSyncCheckpointFileTest {
    @Test
    fun `checkpoint medium preserves canonical bytes at the exact ceiling`() {
        val prefix = "{\"x\":\""
        val value = prefix + "a".repeat(PallaSyncCheckpointFile.MAX_BYTES - prefix.length - 2) + "\"}"
        PallaSyncCheckpointFile.encode(value).size shouldBe PallaSyncCheckpointFile.MAX_BYTES
        shouldThrow<IllegalStateException> {
            PallaSyncCheckpointFile.encode(prefix + "a".repeat(PallaSyncCheckpointFile.MAX_BYTES - prefix.length - 1) + "\"}")
        }.message shouldBe "C_LOCAL_LIMIT"
    }

    @Test
    fun `checkpoint medium rejects malformed utf8 and noncanonical json`() {
        shouldThrow<java.nio.charset.CharacterCodingException> {
            PallaSyncCheckpointFile.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
        }
        shouldThrow<IllegalStateException> { PallaSyncCheckpointFile.encode("{ \"x\":1}") }
    }
}
