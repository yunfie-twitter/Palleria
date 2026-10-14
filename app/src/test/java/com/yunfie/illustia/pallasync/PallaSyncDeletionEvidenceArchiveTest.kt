package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.ByteArrayInputStream

class PallaSyncDeletionEvidenceArchiveTest {
    @Test
    fun `bounded reader accepts exact size and rejects the first excess byte`() {
        val boundary = ByteArray(262_144) { (it % 251).toByte() }
        PallaSyncDeletionEvidenceArchive.readBounded(ByteArrayInputStream(boundary), boundary.size) shouldBe boundary
        shouldThrow<IllegalStateException> {
            PallaSyncDeletionEvidenceArchive.readBounded(ByteArrayInputStream(boundary + 1.toByte()), boundary.size)
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }
}
