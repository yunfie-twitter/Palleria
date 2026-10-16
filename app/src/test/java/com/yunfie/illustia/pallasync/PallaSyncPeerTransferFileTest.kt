package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.CharacterCodingException

class PallaSyncPeerTransferFileTest {
    @Test
    fun `transfer reader enforces byte bound before decoding`() {
        val exact = ByteArray(PallaSyncPeerTransferFile.MAX_BYTES) { 'a'.code.toByte() }
        PallaSyncPeerTransferFile.read(ByteArrayInputStream(exact)).length shouldBe PallaSyncPeerTransferFile.MAX_BYTES
        shouldThrow<IllegalStateException> {
            PallaSyncPeerTransferFile.read(ByteArrayInputStream(exact + 'b'.code.toByte()))
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }

    @Test
    fun `transfer reader rejects malformed utf8`() {
        shouldThrow<CharacterCodingException> {
            PallaSyncPeerTransferFile.read(ByteArrayInputStream(byteArrayOf(0xc3.toByte(), 0x28)))
        }
    }

    @Test
    fun `transfer writer enforces the same byte bound`() {
        shouldThrow<IllegalStateException> {
            PallaSyncPeerTransferFile.encode("a".repeat(PallaSyncPeerTransferFile.MAX_BYTES + 1))
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }

    @Test
    fun `transfer writer rejects malformed utf16 input`() {
        shouldThrow<CharacterCodingException> {
            PallaSyncPeerTransferFile.encode("\uD800")
        }
    }

    @Test
    fun `utf8 size counts encoded bytes rather than utf16 code units`() {
        PallaSyncPeerTransferFile.utf8Size("aé😀") shouldBe 7
    }
}
