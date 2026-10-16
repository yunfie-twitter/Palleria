package com.yunfie.illustia.pallasync

import android.content.ContentResolver
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.security.DigestInputStream
import java.security.MessageDigest

/** Streams a complete paged conversion-decision report without retaining all decisions in memory. */
internal object PallaSyncLegacyMigrationReport {
    suspend fun export(
        cacheDir: File,
        contentResolver: ContentResolver,
        destination: Uri,
        migration: PallaSyncLocalIntents.LegacyMigrationProgress,
        fetchPage: suspend (afterIndex: Long) -> PallaSyncLocalIntents.LegacyDecisionPage,
        fetchCandidatePage: suspend (afterIndex: Long) -> PallaSyncLocalIntents.LegacyCandidatePage,
        fetchSourcePage: suspend (afterSequence: Long) -> PallaSyncLocalIntents.LegacySourceEvidencePage,
    ) {
        val temp = File.createTempFile("pallasync-migration-", ".json", cacheDir)
        try {
            writeReport(temp, migration, fetchPage, fetchCandidatePage, fetchSourcePage)
            val expected = digest(temp)
            contentResolver.openOutputStream(destination, "wt").use { output ->
                checkNotNull(output) { "C_LOCAL_STORAGE_FULL" }
                temp.inputStream().buffered().use { input -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
                output.flush()
            }
            val readback =
                contentResolver.openInputStream(destination).use { input ->
                    checkNotNull(input) { "C_LOCAL_STORAGE_FULL" }
                    DigestInputStream(input, MessageDigest.getInstance("SHA-256")).use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (stream.read(buffer) != -1) Unit
                        stream.messageDigest.digest()
                    }
                }
            check(MessageDigest.isEqual(expected, readback)) { "C_SCHEMA_VIOLATION" }
        } finally {
            temp.delete()
        }
    }

    internal suspend fun writeReport(
        file: File,
        migration: PallaSyncLocalIntents.LegacyMigrationProgress,
        fetchPage: suspend (afterIndex: Long) -> PallaSyncLocalIntents.LegacyDecisionPage,
        fetchCandidatePage: suspend (afterIndex: Long) -> PallaSyncLocalIntents.LegacyCandidatePage,
        fetchSourcePage: suspend (afterSequence: Long) -> PallaSyncLocalIntents.LegacySourceEvidencePage,
    ) {
        file.outputStream().buffered().use { bytes ->
            val writer = BufferedWriter(OutputStreamWriter(bytes, Charsets.UTF_8))
            writer.write("{\"report_type\":\"pallasync-legacy-migration-report\",\"report_version\":3")
            writer.write(",\"migration_id\":${Json.encodeToString(migration.migrationId)}")
            writer.write(",\"source_chain_id\":${Json.encodeToString(migration.sourceChain)}")
            writer.write(",\"source_protocol\":${Json.encodeToString(migration.sourceProtocol)}")
            val retentionNote =
                when (migration.sourceProtocol) {
                    "2.0" -> {
                        "v2.0 /1 search-history retention (MAX_SEARCH_HISTORY is unspecified) and view-history uploads " +
                            "(last 50 items) may have omitted older entries"
                    }

                    "2.1" -> {
                        "v2.1 search, seen, and viewed-history retention may have removed older items"
                    }

                    else -> {
                        error("C_SCHEMA_VIOLATION")
                    }
                }
            writer.write(",\"legacy_history_completeness\":{\"status\":\"unknown\",\"reason\":${Json.encodeToString(retentionNote)}}")
            writer.write(",\"source_export_hash\":${Json.encodeToString(migration.exportHash)}")
            writer.write(",\"target_chain_id\":${Json.encodeToString(migration.targetChain)}")
            writer.write(",\"source_high_water\":${migration.sourceHighWater}")
            writer.write(",\"staged_through\":${migration.stagedThrough}")
            writer.write(",\"source_complete\":${migration.sourceComplete}")
            writer.write(",\"candidate_count\":${migration.candidates}")
            writer.write(",\"decision_count\":${migration.decisions}")
            writer.write(",\"pending_count\":${migration.pending}")
            writer.write(",\"constructed_count\":${migration.constructed}")
            writer.write(",\"accepted_count\":${migration.accepted}")
            writer.write(",\"excluded_count\":${migration.excluded}")
            writer.write(",\"quarantined_count\":${migration.quarantined}")
            writer.write(",\"migration_state\":${Json.encodeToString(migration.state)}")
            writer.write(",\"schema_counts\":${Json.encodeToString(migration.schemaCounts)}")
            writer.write(",\"decisions\":[")
            var afterIndex = 0L
            var written = 0L
            var first = true
            while (true) {
                val page = fetchPage(afterIndex)
                for (decision in page.items) {
                    if (!first) writer.write(",")
                    writer.write(Json.encodeToString(decision))
                    first = false
                    written += 1
                }
                val next = page.nextIndex ?: break
                check(next > afterIndex) { "C_SCHEMA_VIOLATION" }
                afterIndex = next
            }
            check(written == migration.decisions) { "C_SCHEMA_VIOLATION" }
            writer.write("],\"candidates\":[")
            var afterCandidateIndex = 0L
            var candidateWritten = 0L
            first = true
            while (true) {
                val pageCursor = afterCandidateIndex
                val page = fetchCandidatePage(afterCandidateIndex)
                for (candidate in page.items) {
                    check(candidate.migrationEntryIndex > pageCursor) { "C_SCHEMA_VIOLATION" }
                    if (!first) writer.write(",")
                    writer.write(Json.encodeToString(candidate))
                    first = false
                    candidateWritten += 1
                    afterCandidateIndex = candidate.migrationEntryIndex
                }
                val next = page.nextIndex ?: break
                check(next > pageCursor) { "C_SCHEMA_VIOLATION" }
                afterCandidateIndex = next
            }
            check(candidateWritten == migration.candidates) { "C_SCHEMA_VIOLATION" }
            writer.write("],\"source_records\":[")
            var afterSequence = 0L
            var sourceWritten = 0L
            first = true
            while (true) {
                val page = fetchSourcePage(afterSequence)
                for (evidence in page.items) {
                    check(evidence.sourceSequence == sourceWritten + 1) { "C_SCHEMA_VIOLATION" }
                    if (!first) writer.write(",")
                    writer.write(Json.encodeToString(evidence))
                    first = false
                    sourceWritten += 1
                }
                val next = page.nextSequence ?: break
                check(next > afterSequence) { "C_SCHEMA_VIOLATION" }
                afterSequence = next
            }
            check(sourceWritten == migration.sourceHighWater) { "C_SCHEMA_VIOLATION" }
            writer.write("]}")
            writer.flush()
        }
    }

    private fun digest(file: File): ByteArray =
        file.inputStream().buffered().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest()
        }
}
