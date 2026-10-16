package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

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
}
