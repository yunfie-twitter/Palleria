package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
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
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
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
    fun acceptsGenerationOneArchiveHeaderWhenItMatchesTheExpectedSource() {
        val expected =
            PallaSyncExportArchive.ExpectedSource(
                chainId = "ODtLW4w27pD6LS9IpFBfmvByjEoBFddv2Fx1QJIMJoc",
                generation = 1,
                logId = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                relayPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                relayKeyId = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            )
        val output = ByteArrayOutputStream()
        val generationOneHeader = header.replace("\"generation\":0", "\"generation\":1")
        PallaSyncExportArchive.create(output, expected).use { archive ->
            archive.acceptChunk(
                "$generationOneHeader\n{\"has_more\":false,\"kind\":\"continuation\",\"next_cursor\":null}\n".toByteArray(),
                null,
            )
            archive.finish()
        }

        output.toString(Charsets.UTF_8).contains("\"generation\":1") shouldBe true
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

        val (archiveWithUnreferencedSnapshot, snapshotExpected) = acceptedEvidenceArchive(addUnreferencedSnapshotPart = true)
        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archiveWithUnreferencedSnapshot),
                snapshotExpected,
                PallaSyncArchiveRecordVerifier { _, _, _ -> },
            )
        }.message shouldBe "C_ACCEPTANCE_UNPROVEN"

        val (archiveMissingReceipt, expectedMissingReceipt) = acceptedEvidenceArchive(dropLastReceipt = true)
        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(ByteArrayInputStream(archiveMissingReceipt), expectedMissingReceipt)
        }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
    }

    @Test
    fun completeRecoveryArchiveRequiresPinnedHistoryAndRecordVerification() {
        val (archive, expected) = acceptedEvidenceArchive(includeRecovery = true)
        val verified =
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
            )

        verified.includeRecovery shouldBe true
        verified.complete shouldBe true
    }

    @Test
    fun archiveHeaderGenerationMustMatchTheFinalSignedParameters() {
        val (archive, expected) = acceptedEvidenceArchive(includeRecovery = true)
        val lines = archive.toString(Charsets.UTF_8).trimEnd('\n').split('\n')
        val header = lines.first().replace("\"generation\":0", "\"generation\":1")
        val preceding = (listOf(header) + lines.drop(1).dropLast(1)).joinToString("\n", postfix = "\n").toByteArray()
        val hash = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(preceding))
        val footer = """{"archive_sha256":"$hash","complete":true,"kind":"footer","line_count":${lines.size - 1}}"""
        val tampered = preceding + footer.toByteArray(Charsets.UTF_8) + '\n'.code.toByte()

        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(tampered),
                expected.copy(generation = 1),
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
            )
        }.message shouldBe "C_LOG_HASH_MISMATCH"
    }

    @Test
    fun verifiedHistoryIsRetainedOnlyAfterCompleteArchiveAndEndOfInput() {
        val (archive, expected) = acceptedEvidenceArchive(includeRecovery = true)
        val retained = mutableListOf<Long>()
        val recordVerifier =
            PallaSyncArchiveRecordVerifier { authority, record, sequence ->
                PallaSyncCore.validateRecordAuthority(authority, record, sequence) shouldBe true
            }
        val snapshotVerifier = PallaSyncArchiveSnapshotVerifier { _, _, _ -> }
        val sink =
            object : PallaSyncArchiveLogEntrySink {
                override suspend fun retain(
                    authorityJson: String,
                    entryJson: String,
                    receiptJson: String,
                    relaySequence: Long,
                ) {
                    authorityJson shouldNotBe ""
                    entryJson shouldNotBe ""
                    receiptJson shouldNotBe ""
                    retained += relaySequence
                }
            }

        PallaSyncExportArchiveVerifier
            .verify(ByteArrayInputStream(archive), expected, recordVerifier, snapshotVerifier, sink)
        retained shouldBe listOf(1L, 2L, 3L, 4L, 5L, 6L)

        retained.clear()
        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive + byteArrayOf(0x20)),
                expected,
                recordVerifier,
                snapshotVerifier,
                sink,
            )
        }.message shouldBe "C_SCHEMA_VIOLATION"
        retained shouldBe emptyList()

        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                recordVerifier,
                snapshotVerifier,
                sink,
                beforeRetain = { error("C_ROLLBACK_DETECTED") },
            )
        }.message shouldBe "C_ROLLBACK_DETECTED"
        retained shouldBe emptyList()

        val (incompleteArchive, incompleteExpected) = acceptedEvidenceArchive(includeRecovery = false)
        PallaSyncExportArchiveVerifier.verify(
            ByteArrayInputStream(incompleteArchive),
            incompleteExpected,
            recordVerifier,
            snapshotVerifier,
            sink,
        )
        retained shouldBe emptyList()
    }

    @Test
    fun completeRecoveryArchiveRejectsAnyOmittedRecoveryObject() {
        val (archive, expected) = acceptedEvidenceArchive(includeRecovery = true, includeOmitted = true)
        val falselyComplete =
            archive
                .toString(Charsets.UTF_8)
                .replace("\"complete\":false,\"kind\":\"footer\"", "\"complete\":true,\"kind\":\"footer\"")
                .toByteArray()

        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(falselyComplete),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
            )
        }.message shouldBe "C_SCHEMA_VIOLATION"
    }

    @Test
    fun completeRecoveryArchiveRejectsUnverifiedSupplementaryEvidence() {
        val (archive, expected) = acceptedEvidenceArchive(includeRecovery = true, includeUnverifiedControl = true)

        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
            )
        }.message shouldBe "C_MISSING_DEPENDENCY"
    }

    @Test
    fun nullSequenceReceiptMustMatchExactlyOneSnapshotPartHash() {
        val record = Json.parseToJsonElement("""{"record_id":"22222222-2222-4222-8222-222222222222"}""").jsonObject
        val objectHash = checkNotNull(PallaSyncCore.objectHash(record.toString()))
        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addSnapshotPart(record)
            index.linkNullSequenceSnapshotReceipt(objectHash)
            index.requireAllMatched()

            shouldThrow<IllegalStateException> {
                index.linkNullSequenceSnapshotReceipt(objectHash)
            }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
        }

        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addSnapshotPart(record)
            shouldThrow<IllegalStateException> {
                index.linkNullSequenceSnapshotReceipt("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
        }
    }

    @Test
    fun removedRecordIdentityEvidenceRejectsAConflictingRetainedRecord() {
        val recordId = "22222222-2222-4222-8222-222222222222"
        val retained = Json.parseToJsonElement("""{"record_id":"$recordId","value":"retained"}""").jsonObject
        val retainedHash = checkNotNull(PallaSyncCore.objectHash(retained.toString()))
        val removedHash = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addRemovedId(recordId, retainedHash)
            index.addLogEntry(
                buildJsonObject {
                    put("kind", "record")
                    put("object", retained)
                    put("relay_seq", 1)
                },
            )
        }

        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addLogEntry(
                buildJsonObject {
                    put("kind", "record")
                    put("object", retained)
                    put("relay_seq", 1)
                },
            )
            shouldThrow<IllegalStateException> { index.addRemovedId(recordId, removedHash) }.message shouldBe
                "C_RECORD_ID_CONFLICT"
        }

        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addRemovedId(recordId, retainedHash)
            shouldThrow<IllegalStateException> { index.addRemovedId(recordId, retainedHash) }.message shouldBe
                "C_SCHEMA_VIOLATION"
        }
    }

    @Test
    fun archiveLogReceiptsAreScopedToTheirLogIncarnation() {
        val firstLog = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"
        val secondLog = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            val first =
                buildJsonObject {
                    put("log_id", firstLog)
                    put("relay_seq", 1)
                    put("receipt_id", "first")
                }
            val second =
                buildJsonObject {
                    put("log_id", secondLog)
                    put("relay_seq", 1)
                    put("receipt_id", "second")
                }

            index.addLogReceipt(first)
            index.addLogReceipt(second)

            Json
                .parseToJsonElement(index.logReceipt(firstLog, 1))
                .jsonObject
                .getValue("receipt_id")
                .jsonPrimitive.content shouldBe "first"
            Json
                .parseToJsonElement(index.logReceipt(secondLog, 1))
                .jsonObject
                .getValue("receipt_id")
                .jsonPrimitive.content shouldBe "second"
            shouldThrow<IllegalStateException> { index.logReceipt("ffffffff-ffff-4fff-8fff-ffffffffffff", 1) }.message shouldBe
                "C_ACCEPTANCE_UNPROVEN"
            shouldThrow<IllegalStateException> { index.addLogReceipt(first) }.message shouldBe "C_TRANSPARENCY_INVALID"
        }
    }

    @Test
    fun sourceReceiptMustMatchTheSignedHistoricalUploadReceipt() {
        val (archive, expected) =
            acceptedEvidenceArchive(
                includeRecovery = true,
                sourceReceiptSequence = 6L,
                sourceReceiptFromOtherIncarnation = true,
                duplicateSourceIncarnation = true,
            )
        val verifiedRecordSequences = mutableListOf<Long>()
        val retainedSourceReceipts = mutableListOf<Triple<String, Long, String>>()
        var verifiedAuthority: kotlinx.serialization.json.JsonObject? = null
        val verified =
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                    verifiedAuthority = Json.parseToJsonElement(authority).jsonObject
                    verifiedRecordSequences += relaySequence
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
                sourceLogAuthorityProvider = { _, sourceLogId ->
                    val authority = checkNotNull(verifiedAuthority)
                    val sourcePin =
                        buildJsonObject {
                            authority.getValue("pin").jsonObject.forEach { (key, value) ->
                                put(
                                    key,
                                    when (key) {
                                        "log_id" -> JsonPrimitive(sourceLogId)
                                        "public_key" -> JsonPrimitive(alternateRelayPublicKey())
                                        else -> value
                                    },
                                )
                            }
                        }
                    buildJsonObject {
                        authority.forEach { (key, value) -> put(key, if (key == "pin") sourcePin else value) }
                    }.toString()
                },
                sourceEntrySink = { logId, _, receipt, sequence ->
                    val receiptLogId =
                        Json
                            .parseToJsonElement(receipt)
                            .jsonObject
                            .getValue("log_id")
                            .jsonPrimitive.content
                    retainedSourceReceipts += Triple(logId, sequence, receiptLogId)
                },
            )
        verified.complete shouldBe true
        verifiedRecordSequences shouldBe listOf(6L, 6L)
        retainedSourceReceipts shouldBe
            listOf(
                Triple("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", 6L, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                Triple("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee", 6L, "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"),
            )
        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(removeSourceReceiptLines(archive)),
                expected,
                PallaSyncArchiveRecordVerifier { _, _, _ -> },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
            )
        }.message shouldBe "C_ACCEPTANCE_UNPROVEN"

        val (partialArchive, partialExpected) = acceptedEvidenceArchive(sourceReceiptSequence = 6L)
        val lines =
            partialArchive
                .toString(Charsets.UTF_8)
                .trimEnd('\n')
                .split('\n')
                .map { Json.parseToJsonElement(it) }
        val logEntry =
            lines
                .first { value ->
                    val line = value.jsonObject
                    line["kind"]?.jsonPrimitive?.content == "log" &&
                        line["entry"]
                            ?.jsonObject
                            ?.get("kind")
                            ?.jsonPrimitive
                            ?.content == "record"
                }.jsonObject
                .getValue("entry")
                .jsonObject
        val sequence = logEntry.getValue("relay_seq").jsonPrimitive.long
        val uploadReceipt =
            lines
                .first { value ->
                    val line = value.jsonObject
                    line["kind"]?.jsonPrimitive?.content == "upload_receipt" &&
                        line["receipt"]
                            ?.jsonObject
                            ?.get("relay_seq")
                            ?.jsonPrimitive
                            ?.long == sequence
                }.jsonObject
                .getValue("receipt")
                .jsonObject

        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addLogReceipt(uploadReceipt)
            index.addSourceReceipt(logEntry)
            runBlocking { index.verifySourceReceipts(checkNotNull(partialExpected.relayPinJson), null, null, null) } shouldBe false
        }

        val alteredEntry =
            buildJsonObject {
                logEntry.forEach { (key, value) -> put(key, value) }
                put("received_at_ms", logEntry.getValue("received_at_ms").jsonPrimitive.long + 1L)
            }
        val sourceRecord = logEntry.getValue("object").jsonObject
        val conflictingRecord =
            buildJsonObject {
                sourceRecord.forEach { (key, value) -> put(key, value) }
                put("generation", sourceRecord.getValue("generation").jsonPrimitive.long + 1L)
            }
        val conflictingSourceEntry =
            buildJsonObject {
                logEntry.forEach { (key, value) -> put(key, value) }
                put("object", conflictingRecord)
            }
        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addSourceReceipt(conflictingSourceEntry)
            shouldThrow<IllegalStateException> { index.addLogEntry(logEntry) }.message shouldBe "C_RECORD_ID_CONFLICT"
        }
        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addLogReceipt(uploadReceipt)
            index.addSourceReceipt(alteredEntry)
            shouldThrow<IllegalStateException> {
                runBlocking { index.verifySourceReceipts(checkNotNull(partialExpected.relayPinJson), null, null, null) }
            }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
        }

        val signature = uploadReceipt.getValue("relay_signature").jsonPrimitive.content
        val alteredReceipt =
            buildJsonObject {
                uploadReceipt.forEach { (key, value) -> put(key, value) }
                put("relay_signature", (if (signature.first() == 'A') "B" else "A") + signature.drop(1))
            }
        PallaSyncExportArchiveVerifier.ExportReceiptLinkIndex().use { index ->
            index.addLogReceipt(alteredReceipt)
            index.addSourceReceipt(logEntry)
            shouldThrow<IllegalStateException> {
                runBlocking { index.verifySourceReceipts(checkNotNull(partialExpected.relayPinJson), null, null, null) }
            }.message shouldBe "C_SIGNATURE_INVALID"
        }

        PallaSyncCore.validateExportAttestation(
            buildJsonObject {
                put("kind", "source_receipt")
                put("receipt", logEntry)
            }.toString(),
            checkNotNull(partialExpected.relayPinJson),
        ) shouldBe true
    }

    @Test
    fun detachedSourceControlEntryRequiresAndAdvancesExactPriorAuthority() {
        val (archive, expected) =
            acceptedEvidenceArchive(
                includeRecovery = true,
                sourceReceiptSequence = 1L,
                sourceReceiptFromOtherIncarnation = true,
            )
        val sourceLogId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        val sourcePin =
            buildJsonObject {
                put(
                    "origin",
                    Json.parseToJsonElement(checkNotNull(expected.relayPinJson)).jsonObject.getValue("origin"),
                )
                put("chain_id", expected.chainId)
                put("public_key", alternateRelayPublicKey())
                put("log_id", sourceLogId)
            }
        val priorAuthority =
            PallaSyncCore.beginExportAuthorityHistory(
                checkNotNull(expected.identityJson),
                checkNotNull(PallaSyncCore.canonicalJson(sourcePin.toString())),
            )
        val retainedSequences = mutableListOf<Long>()

        val verified =
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
                sourceLogAuthorityProvider = { _, candidateLogId ->
                    priorAuthority.takeIf { candidateLogId == sourceLogId }
                },
                sourceEntrySink = { candidateLogId, _, _, sequence ->
                    candidateLogId shouldBe sourceLogId
                    retainedSequences += sequence
                },
            )

        verified.complete shouldBe true
        retainedSequences shouldBe listOf(1L)

        val archiveLines =
            archive
                .toString(Charsets.UTF_8)
                .trimEnd('\n')
                .split('\n')
                .map(Json::parseToJsonElement)
        val sourceEntry =
            archiveLines
                .single { it.jsonObject["kind"]?.jsonPrimitive?.content == "source_receipt" }
                .jsonObject
                .getValue("receipt")
                .jsonObject
        val historicalReceipt =
            archiveLines
                .map { it.jsonObject }
                .single {
                    it["kind"]?.jsonPrimitive?.content == "upload_receipt" &&
                        it["receipt"]
                            ?.jsonObject
                            ?.get("log_id")
                            ?.jsonPrimitive
                            ?.content == sourceLogId
                }.getValue("receipt")
                .jsonObject
        val lateAuthority =
            PallaSyncCore.advanceAuthorityHistory(priorAuthority, sourceEntry.toString(), historicalReceipt.toString())
        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
                sourceLogAuthorityProvider = { _, candidateLogId ->
                    lateAuthority.takeIf { candidateLogId == sourceLogId }
                },
            )
        }.message shouldBe "C_ACCEPTANCE_UNPROVEN"
    }

    @Test
    fun completeArchiveBoundaryRequiresLocallyVerifiedAuthorityCheckpoint() {
        val (sourceArchive, expected) = acceptedEvidenceArchive(includeRecovery = true)
        val lines = sourceArchive.toString(Charsets.UTF_8).trimEnd('\n').split('\n')
        val archiveLines = lines.dropLast(1).toMutableList()
        val transparencyIndex =
            archiveLines.indexOfFirst { line ->
                Json
                    .parseToJsonElement(line)
                    .jsonObject["kind"]
                    ?.jsonPrimitive
                    ?.content == "transparency_entry"
            }
        check(transparencyIndex >= 0)
        archiveLines.add(transparencyIndex, """{"compaction":{},"kind":"boundary"}""")
        val preceding = archiveLines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(preceding)
        val footer =
            buildJsonObject {
                put("archive_sha256", Base64.getUrlEncoder().withoutPadding().encodeToString(digest))
                put("complete", true)
                put("kind", "footer")
                put("line_count", archiveLines.size)
            }
        val archive =
            preceding +
                checkNotNull(PallaSyncCore.canonicalJson(footer.toString())).toByteArray(Charsets.UTF_8) +
                '\n'.code.toByte()

        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, relaySequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, relaySequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
            )
        }.message shouldBe "C_MISSING_DEPENDENCY"
    }

    @Test
    fun completeArchiveRetainsExactRemovedIdEvidenceOnlyAfterFooterVerification() {
        val (archive, expected) = acceptedEvidenceArchive(includeRecovery = true, includeRemovedId = true)
        val retained = mutableListOf<List<String>>()
        val verified =
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(archive),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, sequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, sequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
                removedIdSink = { logId, archiveHash, recordId, recordHash, evidenceJson ->
                    retained += listOf(logId, archiveHash, recordId, recordHash, evidenceJson)
                },
            )

        verified.complete shouldBe true
        val footer =
            Json
                .parseToJsonElement(archive.toString(Charsets.UTF_8).trimEnd().substringAfterLast('\n'))
                .jsonObject
        verified.archiveHash shouldBe footer.getValue("archive_sha256").jsonPrimitive.content
        retained.map { it[0] to it[2] } shouldBe
            listOf("a187b5e7-efe5-4a10-8294-963db6bdc33c" to "99999999-9999-4999-8999-999999999999")
        retained.single()[3] shouldBe "${"A".repeat(43)}"
        Json
            .parseToJsonElement(retained.single()[4])
            .jsonObject
            .getValue("kind")
            .jsonPrimitive.content shouldBe "removed_id"

        val corruptFooterArchive = archive.copyOf()
        val archiveLines = corruptFooterArchive.toString(Charsets.UTF_8).trimEnd('\n').split('\n')
        val footerJson = Json.parseToJsonElement(archiveLines.last()).jsonObject
        val corruptFooter =
            buildJsonObject {
                footerJson.forEach { (key, value) ->
                    put(key, if (key == "archive_sha256") JsonPrimitive("B" + value.jsonPrimitive.content.drop(1)) else value)
                }
            }
        val corruptFooterBytes =
            (
                archiveLines.dropLast(1).joinToString("\n", postfix = "\n") +
                    checkNotNull(PallaSyncCore.canonicalJson(corruptFooter.toString())) + "\n"
            ).toByteArray(Charsets.UTF_8)
        var calledBeforeFooterValidation = false
        shouldThrow<IllegalStateException> {
            PallaSyncExportArchiveVerifier.verify(
                ByteArrayInputStream(corruptFooterBytes),
                expected,
                PallaSyncArchiveRecordVerifier { authority, record, sequence ->
                    PallaSyncCore.validateRecordAuthority(authority, record, sequence) shouldBe true
                },
                PallaSyncArchiveSnapshotVerifier { _, _, _ -> },
                removedIdSink = { _, _, _, _, _ -> calledBeforeFooterValidation = true },
            )
        }.message shouldBe "C_SCHEMA_VIOLATION"
        calledBeforeFooterValidation shouldBe false
    }

    private fun acceptedEvidenceArchive(
        dropLastReceipt: Boolean = false,
        addUnreferencedSnapshotPart: Boolean = false,
        includeRecovery: Boolean = false,
        includeOmitted: Boolean = false,
        includeUnverifiedControl: Boolean = false,
        includeRemovedId: Boolean = false,
        sourceReceiptSequence: Long? = null,
        sourceReceiptFromOtherIncarnation: Boolean = false,
        duplicateSourceIncarnation: Boolean = false,
    ): Pair<ByteArray, PallaSyncExportArchive.ExpectedSource> {
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
                includeRecovery = includeRecovery,
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
                    put("include_recovery", includeRecovery)
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
            val sourceEvidence =
                sourceReceiptSequence?.let { sequence ->
                    val entry =
                        logLines
                            .map { line ->
                                Json
                                    .parseToJsonElement(line)
                                    .jsonObject
                                    .getValue("entry")
                                    .jsonObject
                            }.single { it.getValue("relay_seq").jsonPrimitive.long == sequence }
                    val sourceEntry =
                        if (sourceReceiptFromOtherIncarnation) {
                            buildJsonObject {
                                entry.forEach { (key, value) -> put(key, value) }
                                put("received_at_ms", entry.getValue("received_at_ms").jsonPrimitive.long + 1L)
                            }
                        } else {
                            entry
                        }
                    val sourceReceipts =
                        if (sourceReceiptFromOtherIncarnation) {
                            val currentReceipt =
                                receipts
                                    .map { it.jsonObject }
                                    .single { it.getValue("relay_seq").jsonPrimitive.long == sequence }
                            listOf(
                                signHistoricalReceipt(
                                    currentReceipt,
                                    sourceEntry,
                                    logId = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                    previousHeadHash =
                                        if (sequence == 1L) {
                                            testDataZero(
                                                fixture
                                                    .getValue("identity")
                                                    .jsonObject
                                                    .getValue("chain_id")
                                                    .jsonPrimitive.content,
                                                "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                                            )
                                        } else {
                                            null
                                        },
                                ),
                            ) +
                                if (duplicateSourceIncarnation) {
                                    listOf(
                                        signHistoricalReceipt(
                                            currentReceipt,
                                            sourceEntry,
                                            logId = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee",
                                            receiptId = "ffffffff-ffff-4fff-8fff-ffffffffffff",
                                            transparencyId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                                        ),
                                    )
                                } else {
                                    emptyList()
                                }
                        } else {
                            emptyList()
                        }
                    sourceEntry to sourceReceipts
                }
            val historicalReceiptLines =
                sourceEvidence?.second?.map { receipt ->
                    checkNotNull(PallaSyncCore.canonicalJson("""{"kind":"upload_receipt","receipt":$receipt}"""))
                } ?: emptyList()
            val receiptLines =
                receipts.map { receipt ->
                    checkNotNull(PallaSyncCore.canonicalJson("""{"kind":"upload_receipt","receipt":$receipt}"""))
                } + historicalReceiptLines
            val sourceReceiptLines =
                sourceEvidence?.let { (entry, sourceReceipts) ->
                    List(sourceReceipts.size) {
                        checkNotNull(
                            PallaSyncCore.canonicalJson(
                                buildJsonObject {
                                    put("kind", "source_receipt")
                                    put("receipt", entry)
                                }.toString(),
                            ),
                        )
                    }
                } ?: emptyList()
            val snapshotLines =
                if (addUnreferencedSnapshotPart) {
                    listOf(
                        checkNotNull(
                            PallaSyncCore.canonicalJson(
                                """{"kind":"snapshot_part","record":{"record_id":"22222222-2222-4222-8222-222222222222"}}""",
                            ),
                        ),
                    )
                } else {
                    emptyList()
                }
            val firstChunkLines =
                listOf(header) + logLines + sourceReceiptLines + snapshotLines + receiptLines +
                    if (includeUnverifiedControl) {
                        listOf("""{"kind":"control","object":{},"object_type":"parameters"}""")
                    } else {
                        emptyList()
                    } +
                    if (includeOmitted) {
                        listOf(
                            """{"kind":"omitted","object_hash":"${"A".repeat(43)}","reason":"recovery_authority","relay_seq":1}""",
                        )
                    } else {
                        emptyList()
                    } +
                    if (includeRemovedId) {
                        listOf(
                            checkNotNull(
                                PallaSyncCore.canonicalJson(
                                    buildJsonObject {
                                        put("kind", "removed_id")
                                        put("record_id", "99999999-9999-4999-8999-999999999999")
                                        put("record_hash", "A".repeat(43))
                                    }.toString(),
                                ),
                            ),
                        )
                    } else {
                        emptyList()
                    } +
                    """{"has_more":true,"kind":"continuation","next_cursor":"transparency"}"""
            archive.acceptChunk(firstChunkLines.joinToString("\n", postfix = "\n").toByteArray(), null)
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
        val archiveBytes =
            if (sourceReceiptFromOtherIncarnation) {
                reSignArchivedSourceReceiptsWithAlternateKey(
                    output.toByteArray(),
                    checkNotNull(sourceReceiptSequence),
                )
            } else {
                output.toByteArray()
            }
        return archiveBytes to expected
    }

    private fun reSignArchivedSourceReceiptsWithAlternateKey(
        archive: ByteArray,
        sequence: Long,
    ): ByteArray {
        val lines = archive.toString(Charsets.UTF_8).trimEnd('\n').split('\n')
        val sourceEntry =
            lines
                .map(Json::parseToJsonElement)
                .map { it.jsonObject }
                .first { it["kind"]?.jsonPrimitive?.content == "source_receipt" }
                .getValue("receipt")
                .jsonObject
        val header = Json.parseToJsonElement(lines.first()).jsonObject
        val updatedLines =
            lines.dropLast(1).map { line ->
                val value = Json.parseToJsonElement(line).jsonObject
                val receipt = value["receipt"]?.jsonObject
                if (
                    value["kind"]?.jsonPrimitive?.content == "upload_receipt" &&
                    receipt?.get("relay_seq")?.jsonPrimitive?.long == sequence &&
                    receipt["log_id"]?.jsonPrimitive?.content != header.getValue("log_id").jsonPrimitive.content
                ) {
                    val alternate =
                        signHistoricalReceipt(
                            receipt,
                            sourceEntry,
                            logId = receipt.getValue("log_id").jsonPrimitive.content,
                            receiptId = receipt.getValue("receipt_id").jsonPrimitive.content,
                            transparencyId = receipt.getValue("transparency_id").jsonPrimitive.content,
                            useAlternateRelayKey = true,
                        )
                    checkNotNull(
                        PallaSyncCore.canonicalJson(
                            buildJsonObject {
                                value.forEach { (key, field) -> put(key, if (key == "receipt") alternate else field) }
                            }.toString(),
                        ),
                    )
                } else {
                    line
                }
            }
        val signedPrefix = (updatedLines.joinToString("\n", postfix = "\n")).toByteArray(Charsets.UTF_8)
        val oldFooter = Json.parseToJsonElement(lines.last()).jsonObject
        val footer =
            buildJsonObject {
                put(
                    "archive_sha256",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(signedPrefix),
                    ),
                )
                put("complete", oldFooter.getValue("complete").jsonPrimitive.booleanOrNull ?: false)
                put("kind", "footer")
                put("line_count", updatedLines.size)
            }
        return (signedPrefix.toString(Charsets.UTF_8) + checkNotNull(PallaSyncCore.canonicalJson(footer.toString())) + "\n")
            .toByteArray(Charsets.UTF_8)
    }

    private fun removeSourceReceiptLines(archive: ByteArray): ByteArray {
        val lines = archive.toString(Charsets.UTF_8).trimEnd('\n').split('\n')
        val updatedLines =
            lines.dropLast(1).filterNot { line ->
                Json
                    .parseToJsonElement(line)
                    .jsonObject["kind"]
                    ?.jsonPrimitive
                    ?.content == "source_receipt"
            }
        val signedPrefix = updatedLines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
        val oldFooter = Json.parseToJsonElement(lines.last()).jsonObject
        val footer =
            buildJsonObject {
                put(
                    "archive_sha256",
                    Base64.getUrlEncoder().withoutPadding().encodeToString(
                        MessageDigest.getInstance("SHA-256").digest(signedPrefix),
                    ),
                )
                put("complete", oldFooter.getValue("complete").jsonPrimitive.booleanOrNull ?: false)
                put("kind", "footer")
                put("line_count", updatedLines.size)
            }
        return (signedPrefix.toString(Charsets.UTF_8) + checkNotNull(PallaSyncCore.canonicalJson(footer.toString())) + "\n")
            .toByteArray(Charsets.UTF_8)
    }

    private fun signHistoricalReceipt(
        template: kotlinx.serialization.json.JsonObject,
        entry: kotlinx.serialization.json.JsonObject,
        logId: String = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
        previousHeadHash: String? = null,
        receiptId: String = "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
        transparencyId: String = "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
        useAlternateRelayKey: Boolean = false,
    ): kotlinx.serialization.json.JsonObject {
        val sequence = entry.getValue("relay_seq").jsonPrimitive.long
        val previousHash = Base64.getUrlDecoder().decode(template.getValue("previous_head_hash").jsonPrimitive.content)
        val entryHash = checkNotNull(PallaSyncCore.objectHash(entry.toString()))
        val entryDigest = Base64.getUrlDecoder().decode(entryHash)
        val linkInput =
            ByteBuffer
                .allocate(previousHash.size + Long.SIZE_BYTES + entryDigest.size)
                .put(previousHash)
                .putLong(sequence)
                .put(entryDigest)
                .array()
        val resultingHash = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(linkInput))
        val unsigned =
            buildJsonObject {
                template.filterKeys { it != "relay_signature" && it != "relay_key_id" }.forEach { (key, value) -> put(key, value) }
                previousHeadHash?.let { put("previous_head_hash", it) }
                put(
                    "relay_key_id",
                    if (useAlternateRelayKey) alternateRelayKeyId() else "oYESsLe0Il_zBSfg9896HjdC9jKwO2X0VCcDw6VlTck",
                )
                put("log_id", logId)
                put("receipt_id", receiptId)
                put("transparency_id", transparencyId)
                put("transparency_index", 1)
                put("entry_hash", entryHash)
                put("received_at_ms", entry.getValue("received_at_ms").jsonPrimitive.long)
                put("resulting_head_hash", resultingHash)
            }
        val canonical = checkNotNull(PallaSyncCore.canonicalJson(unsigned.toString())).toByteArray(Charsets.UTF_8)
        val privateKeyEncoding =
            byteArrayOf(
                0x30,
                0x2e,
                0x02,
                0x01,
                0x00,
                0x30,
                0x05,
                0x06,
                0x03,
                0x2b,
                0x65,
                0x70,
                0x04,
                0x22,
                0x04,
                0x20,
            ) +
                if (useAlternateRelayKey) {
                    decodeHex(ALTERNATE_RELAY_SEED_HEX)
                } else {
                    ByteArray(32) { (0xa0 + it).toByte() }
                }
        val privateKey = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(privateKeyEncoding))
        val signature =
            Signature.getInstance("Ed25519").run {
                initSign(privateKey)
                update("PALLASYNC-UPLOAD-RECEIPT-v3\u0000".toByteArray(Charsets.US_ASCII))
                update(canonical)
                Base64.getUrlEncoder().withoutPadding().encodeToString(sign())
            }
        return buildJsonObject {
            unsigned.forEach { (key, value) -> put(key, value) }
            put("relay_signature", signature)
        }
    }

    private fun testDataZero(
        chainId: String,
        logId: String,
    ): String {
        val chainBytes = Base64.getUrlDecoder().decode(chainId)
        val uuid = java.util.UUID.fromString(logId)
        val logBytes =
            ByteBuffer
                .allocate(16)
                .putLong(uuid.mostSignificantBits)
                .putLong(uuid.leastSignificantBits)
                .array()
        val prefix = "PALLASYNC-LOG-v3\u0000".toByteArray(Charsets.US_ASCII)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(prefix + chainBytes + logBytes),
        )
    }

    private fun alternateRelayPublicKey(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(decodeHex(ALTERNATE_RELAY_PUBLIC_HEX))

    private fun alternateRelayKeyId(): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(decodeHex(ALTERNATE_RELAY_PUBLIC_HEX)),
            )

    private fun decodeHex(value: String): ByteArray =
        value
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

    private companion object {
        const val ALTERNATE_RELAY_SEED_HEX = "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60"
        const val ALTERNATE_RELAY_PUBLIC_HEX = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"
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
