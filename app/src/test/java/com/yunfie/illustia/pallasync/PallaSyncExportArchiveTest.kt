package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

class PallaSyncExportArchiveTest {
    private val header =
        """{"chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","generation":0,"head_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","head_seq":0,"include_recovery":false,"kind":"header","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","protocol":"pallasync/3","relay_key_id":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","relay_public_key":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","transparency_head_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","transparency_head_index":0,"transparency_id":"dddddddd-dddd-4ddd-8ddd-dddddddddddd"}"""

    @Test
    fun nativeCanonicalizerRejectsNonJcsAndDuplicateKeys() {
        PallaSyncCore.canonicalJson("{\"b\":2,\"a\":1}") shouldBe "{\"a\":1,\"b\":2}"
        PallaSyncCore.canonicalJson("{\"a\":1,\"a\":2}") shouldBe null
        PallaSyncCore.canonicalJson("{ \"a\":1}") shouldBe "{\"a\":1}"
    }

    @Test
    fun mergesBoundedChunksAndWritesExactArchiveFooterHash() {
        val output = ByteArrayOutputStream()
        val archive = PallaSyncExportArchive.create(output)
        val first =
            listOf(
                header,
                """{"kind":"control","object":{},"object_type":"parameters"}""",
                """{"has_more":true,"kind":"continuation","next_cursor":"next"}""",
            ).joinToString("\n", postfix = "\n")
        val page = archive.acceptChunk(first.toByteArray(), null)
        page.cursor shouldBe "next"
        page.hasMore shouldBe true

        val second =
            listOf(
                header,
                """{"kind":"omitted","object_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","reason":"recovery_authority","relay_seq":1}""",
                """{"has_more":false,"kind":"continuation","next_cursor":null}""",
            ).joinToString("\n", postfix = "\n")
        archive.acceptChunk(second.toByteArray(), page.cursor).hasMore shouldBe false
        archive.finish()

        val lines = output.toString(Charsets.UTF_8).trimEnd('\n').split('\n')
        lines.size shouldBe 4
        lines[0] shouldBe header
        val preceding = (lines.dropLast(1).joinToString("\n") + "\n").toByteArray()
        val footer = lines.last()
        val expectedHash = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(preceding))
        footer shouldBe """{"archive_sha256":"$expectedHash","complete":false,"kind":"footer","line_count":3}"""
    }

    @Test
    fun refusesSkippedCursorNoncanonicalLinesAndIncompleteFinish() {
        val output = ByteArrayOutputStream()
        val archive = PallaSyncExportArchive.create(output)
        val chunk = "$header\n{\"has_more\":true,\"kind\":\"continuation\",\"next_cursor\":\"c1\"}\n"
        archive.acceptChunk(chunk.toByteArray(), null).cursor shouldBe "c1"
        shouldThrow<IllegalStateException> { archive.acceptChunk(chunk.toByteArray(), "wrong") }
        shouldThrow<IllegalStateException> { archive.acceptChunk(chunk.toByteArray(), "c1") }
        archive.close()

        val noncanonical = PallaSyncExportArchive.create(ByteArrayOutputStream())
        shouldThrow<IllegalStateException> {
            val invalid =
                listOf(
                    "{\"protocol\":\"pallasync/3\",\"kind\":\"header\"}",
                    "{\"has_more\":false,\"kind\":\"continuation\",\"next_cursor\":null}",
                ).joinToString("\n", postfix = "\n")
            noncanonical.acceptChunk(invalid.toByteArray(), null)
        }
        noncanonical.close()

        val incomplete = PallaSyncExportArchive.create(ByteArrayOutputStream())
        incomplete.acceptChunk("$header\n{\"has_more\":true,\"kind\":\"continuation\",\"next_cursor\":\"next\"}\n".toByteArray(), null)
        shouldThrow<IllegalStateException> { incomplete.finish() }
        incomplete.close()
    }
}
