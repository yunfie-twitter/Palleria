package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.ByteArrayInputStream

class PallaSyncDeletionEvidenceArchiveTest {
    @Test
    fun `strict object parser accepts legal whitespace and member order`() {
        PallaSyncDeletionEvidenceArchive.parseStrictObject("{ \"b\": 2, \"a\": 1 }").keys shouldBe setOf("b", "a")
    }

    @Test
    fun `strict object parser rejects duplicate member names before tree parsing`() {
        shouldThrow<IllegalStateException> {
            PallaSyncDeletionEvidenceArchive.parseStrictObject("{\"evidence_bundle\":{},\"evidence_bundle\":{}}")
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }

    @Test
    fun `strict object parser rejects non-object and malformed json`() {
        listOf("[]", "{\"receipt\":").forEach { input ->
            shouldThrow<IllegalStateException> {
                PallaSyncDeletionEvidenceArchive.parseStrictObject(input)
            }.message shouldBe "C_SCHEMA_VIOLATION"
        }
    }

    @Test
    fun `bounded read accepts exact limit and rejects one extra byte`() {
        PallaSyncDeletionEvidenceArchive.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3).toList() shouldBe
            listOf<Byte>(1, 2, 3)
        shouldThrow<IllegalStateException> {
            PallaSyncDeletionEvidenceArchive.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), 3)
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }
}
