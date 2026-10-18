package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

@RunWith(PallaSyncJniRobolectricRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
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
    fun rejectsArchiveHeaderFromAnotherChainCutOrRelayKey() {
        val expected =
            PallaSyncExportArchive.ExpectedSource(
                chainId = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
                generation = 0,
                logId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                relayPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                relayKeyId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            )
        val archive = PallaSyncExportArchive.create(ByteArrayOutputStream(), expected)
        val mismatched = expected.copy(logId = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee")
        val otherSource = PallaSyncExportArchive.create(ByteArrayOutputStream(), mismatched)
        shouldThrow<IllegalStateException> {
            otherSource.acceptChunk(
                "$header\n{\"has_more\":false,\"kind\":\"continuation\",\"next_cursor\":null}\n".toByteArray(),
                null,
            )
        }
        otherSource.close()
        archive.close()

        val recoveryMismatch = PallaSyncExportArchive.create(ByteArrayOutputStream(), expected.copy(includeRecovery = true))
        shouldThrow<IllegalStateException> {
            recoveryMismatch.acceptChunk(
                "$header\n{\"has_more\":false,\"kind\":\"continuation\",\"next_cursor\":null}\n".toByteArray(),
                null,
            )
        }
        recoveryMismatch.close()
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

    @Test
    fun verifiesArchiveFooterStreamingAndRejectsTampering() {
        val output = ByteArrayOutputStream()
        PallaSyncExportArchive.create(output).use { archive ->
            val chunk =
                listOf(
                    header,
                    """{"kind":"control","object":{},"object_type":"parameters"}""",
                    """{"has_more":false,"kind":"continuation","next_cursor":null}""",
                ).joinToString("\n", postfix = "\n")
            archive.acceptChunk(chunk.toByteArray(), null)
            archive.finish()
        }
        val bytes = output.toByteArray()
        PallaSyncExportArchiveVerifier.verify(ByteArrayInputStream(bytes)).complete shouldBe false

        val tampered = bytes.toString(Charsets.UTF_8).replace("parameters", "device").toByteArray()
        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(ByteArrayInputStream(tampered))
        }

        val recoveryOutput = ByteArrayOutputStream()
        val recoveryHeader = header.replace("\"include_recovery\":false", "\"include_recovery\":true")
        PallaSyncExportArchive.create(recoveryOutput).use { archive ->
            archive.acceptChunk(
                "$recoveryHeader\n{\"has_more\":false,\"kind\":\"continuation\",\"next_cursor\":null}\n".toByteArray(),
                null,
            )
            archive.finish()
        }
        val recoveryArchive = PallaSyncExportArchiveVerifier.verify(ByteArrayInputStream(recoveryOutput.toByteArray()))
        recoveryArchive.includeRecovery shouldBe true
        recoveryArchive.complete shouldBe false
    }

    @Test
    fun rejectsMalformedExportLineWrappersBeforePersistingThem() {
        val malformed =
            listOf(
                """{"kind":"control","object":{},"object_type":"unknown"}""",
                """{"extra":true,"kind":"omitted","object_hash":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA","reason":"recovery_authority","relay_seq":1}""",
                """{"kind":"removed_id","record_hash":"bad","record_id":"not-a-uuid"}""",
                """{"kind":"snapshot_part","record":[]}""",
                """{"kind":"transparency_head","head":null}""",
            )
        malformed.forEach { line ->
            val archive = PallaSyncExportArchive.create(ByteArrayOutputStream())
            shouldThrow<IllegalStateException> {
                archive.acceptChunk(
                    "$header\n$line\n{\"has_more\":false,\"kind\":\"continuation\",\"next_cursor\":null}\n".toByteArray(),
                    null,
                )
            }
            archive.close()
        }
    }

    @Test
    fun exportWithPinnedRelayRejectsUnsignedAttestationBeforeWritingLine() {
        val publicKey = "T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ"
        val relayKeyId = "oYESsLe0Il_zBSfg9896HjdC9jKwO2X0VCcDw6VlTck"
        val actualHeader =
            header
                .replace(
                    "\"relay_key_id\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"",
                    "\"relay_key_id\":\"$relayKeyId\"",
                ).replace(
                    "\"relay_public_key\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"",
                    "\"relay_public_key\":\"$publicKey\"",
                )
        val pin =
            """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"$publicKey","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}"""
        val expected =
            PallaSyncExportArchive.ExpectedSource(
                chainId = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
                generation = 0,
                logId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                relayPublicKey = publicKey,
                relayKeyId = relayKeyId,
                relayPinJson = pin,
            )
        val archive = PallaSyncExportArchive.create(ByteArrayOutputStream(), expected)
        val receipt =
            PallaSyncExportArchiveTest::class.java
                .getResourceAsStream("/pallasync/attestations/receipt.json")!!
                .bufferedReader()
                .use { it.readText() }
                .trim()
                .replace(
                    Regex("\"relay_signature\":\"[^\"]+\""),
                    "\"relay_signature\":\"${"A".repeat(86)}\"",
                )
        shouldThrow<IllegalStateException> {
            archive.acceptChunk(
                "$actualHeader\n{\"kind\":\"upload_receipt\",\"receipt\":$receipt}\n{\"has_more\":false,\"kind\":\"continuation\",\"next_cursor\":null}\n"
                    .toByteArray(),
                null,
            )
        }.message shouldBe "C_SIGNATURE_INVALID"
        archive.close()
    }

    @Test
    fun readbackWithTrustedRelayPinRequiresTheSignedTransparencyHeadCut() {
        val publicKey = "T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ"
        val relayKeyId = "oYESsLe0Il_zBSfg9896HjdC9jKwO2X0VCcDw6VlTck"
        val pin =
            """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"$publicKey","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}"""
        val expected =
            PallaSyncExportArchive.ExpectedSource(
                chainId = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
                generation = 0,
                logId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                relayPublicKey = publicKey,
                relayKeyId = relayKeyId,
                relayPinJson = pin,
            )
        val t0 = PallaSyncCore.initialExportTransparencyHash("dddddddd-dddd-4ddd-8ddd-dddddddddddd", pin)
        val pinnedHeader =
            header
                .replace("\"relay_key_id\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"", "\"relay_key_id\":\"$relayKeyId\"")
                .replace("\"relay_public_key\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"", "\"relay_public_key\":\"$publicKey\"")
                .replace("\"transparency_head_hash\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"", "\"transparency_head_hash\":\"$t0\"")
        val body = (pinnedHeader + "\n").toByteArray()
        val hash = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(body))
        val archive =
            body +
                """{"archive_sha256":"$hash","complete":false,"kind":"footer","line_count":1}""".toByteArray() +
                '\n'.code.toByte()

        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(ByteArrayInputStream(archive), expected)
        }.message shouldBe "C_MISSING_DEPENDENCY"
    }

    @Test
    fun pinnedArchiveReadbackMatchesEveryReceiptToItsAcceptedTransparencyEntry() {
        val (archive, expected) = acceptedEvidenceArchive()
        val verifiedRecordSequences = mutableListOf<Long>()
        val verified =
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                    verifiedRecordSequences += relaySequence
                },
            )
        verified.complete shouldBe false
        verifiedRecordSequences shouldBe listOf(6L)

        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(ByteArrayInputStream(archive), expected)
        }.message shouldBe "C_MISSING_DEPENDENCY"

        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { _, _, _ -> error("C_AEAD_FAILURE") },
            )
        }.message shouldBe "C_AEAD_FAILURE"

        val (archiveMissingReceipt, expectedMissingReceipt) = acceptedEvidenceArchive(dropLastReceipt = true)
        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(ByteArrayInputStream(archiveMissingReceipt), expectedMissingReceipt)
        }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
    }

    private fun acceptedEvidenceArchive(dropLastReceipt: Boolean = false): Pair<ByteArray, PallaSyncExportArchive.ExpectedSource> {
        val fixture =
            Json
                .parseToJsonElement(
                    PallaSyncExportArchiveTest::class.java
                        .getResourceAsStream("/pallasync/python-genesis-acceptance.json")!!
                        .bufferedReader()
                        .use { it.readText() },
                ).jsonObject
        val created = fixture.getValue("created").jsonObject
        val upload = fixture.getValue("record_upload").jsonObject
        val chain =
            fixture
                .getValue("identity")
                .jsonObject
                .getValue("chain_id")
                .jsonPrimitive
                .content
        val generation = 0L
        val logId = created.getValue("log_id").jsonPrimitive.content
        val relayKey = fixture.getValue("relay_public_key").jsonPrimitive.content
        val relayKeyId = "oYESsLe0Il_zBSfg9896HjdC9jKwO2X0VCcDw6VlTck"
        val transparencyHead = fixture.getValue("transparency_head").jsonObject
        val entries =
            fixture.getValue("transparency_pages").jsonArray.flatMap { page ->
                page.jsonObject.getValue("items").jsonArray
            }
        val receipts =
            (created.getValue("upload_receipts").jsonArray + upload.getValue("upload_receipts").jsonArray)
                .let { if (dropLastReceipt) it.dropLast(1) else it }
        val pin =
            buildJsonObject {
                put("origin", fixture.getValue("origin").jsonPrimitive.content)
                put("chain_id", chain)
                put("public_key", relayKey)
                put("log_id", logId)
            }
        val expected =
            PallaSyncExportArchive.ExpectedSource(
                chainId = chain,
                generation = generation,
                logId = logId,
                relayPublicKey = relayKey,
                relayKeyId = relayKeyId,
                includeRecovery = false,
                relayPinJson = checkNotNull(PallaSyncCore.canonicalJson(pin.toString())),
                identityJson = fixture.getValue("identity").jsonObject.toString(),
            )
        val header =
            PallaSyncCore.canonicalJson(
                buildJsonObject {
                    put("kind", "header")
                    put("protocol", "pallasync/3")
                    put("chain_id", chain)
                    put("generation", generation)
                    put("log_id", logId)
                    put("head_seq", upload.getValue("head_seq").jsonPrimitive.long)
                    put("head_hash", upload.getValue("head_hash").jsonPrimitive.content)
                    put("include_recovery", false)
                    put("relay_public_key", relayKey)
                    put("relay_key_id", relayKeyId)
                    put("transparency_id", transparencyHead.getValue("transparency_id").jsonPrimitive.content)
                    put("transparency_head_index", transparencyHead.getValue("entry_index").jsonPrimitive.long)
                    put("transparency_head_hash", transparencyHead.getValue("transparency_hash").jsonPrimitive.content)
                }.toString(),
            )!!
        val output = ByteArrayOutputStream()
        PallaSyncExportArchive.create(output, expected).use { archive ->
            val logLines =
                fixture.getValue("log_pages").jsonArray.flatMap { page ->
                    page.jsonObject.getValue("items").jsonArray.map { entry ->
                        checkNotNull(PallaSyncCore.canonicalJson("""{"kind":"log","entry":$entry}"""))
                    }
                }
            val receiptLines =
                receipts.map { receipt ->
                    checkNotNull(PallaSyncCore.canonicalJson("""{"kind":"upload_receipt","receipt":$receipt}"""))
                }
            archive.acceptChunk(
                (listOf(header) + logLines + receiptLines + """{"has_more":true,"kind":"continuation","next_cursor":"transparency"}""")
                    .joinToString("\n", postfix = "\n")
                    .toByteArray(),
                null,
            )
            val evidenceLines =
                buildList {
                    addAll(
                        entries.map { entry ->
                            checkNotNull(
                                PallaSyncCore.canonicalJson("""{"kind":"transparency_entry","entry":$entry}"""),
                            )
                        },
                    )
                    add(
                        checkNotNull(
                            PallaSyncCore.canonicalJson("""{"kind":"transparency_head","head":$transparencyHead}"""),
                        ),
                    )
                }
            archive.acceptChunk(
                (listOf(header) + evidenceLines + """{"has_more":false,"kind":"continuation","next_cursor":null}""")
                    .joinToString("\n", postfix = "\n")
                    .toByteArray(),
                "transparency",
            )
            archive.finish()
        }
        return output.toByteArray() to expected
    }

    @Test
    fun refusesArchiveWhenProtectedPinAndTrustedRelayKeyDisagree() {
        val publicKey = "T9CZzNR9eJPf6ewkQU7LDZtUICMqrTDZHEZb4zy-ZcQ"
        val wrongPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        val pin =
            """{"origin":"https://relay.example.com","chain_id":"ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc","public_key":"$wrongPublicKey","log_id":"cccccccc-cccc-4ccc-8ccc-cccccccccccc"}"""
        val expected =
            PallaSyncExportArchive.ExpectedSource(
                chainId = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
                generation = 0,
                logId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                relayPublicKey = publicKey,
                relayKeyId = "oYESsLe0Il_zBSfg9896HjdC9jKwO2X0VCcDw6VlTck",
                relayPinJson = pin,
            )
        val actualHeader =
            header
                .replace(
                    "\"relay_key_id\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"",
                    "\"relay_key_id\":\"oYESsLe0Il_zBSfg9896HjdC9jKwO2X0VCcDw6VlTck\"",
                ).replace(
                    "\"relay_public_key\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"",
                    "\"relay_public_key\":\"$publicKey\"",
                )
        val archive = PallaSyncExportArchive.create(ByteArrayOutputStream(), expected)
        shouldThrow<IllegalStateException> {
            archive.acceptChunk(
                "$actualHeader\n{\"has_more\":false,\"kind\":\"continuation\",\"next_cursor\":null}\n".toByteArray(),
                null,
            )
        }.message shouldBe "C_RELAY_KEY_MISMATCH"
        archive.close()
    }
}
