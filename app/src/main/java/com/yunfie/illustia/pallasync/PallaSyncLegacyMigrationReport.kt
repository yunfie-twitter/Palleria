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
    ) {
        val temp = File.createTempFile("pallasync-migration-", ".json", cacheDir)
        try {
            writeReport(temp, migration, fetchPage)
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
    ) {
        file.outputStream().buffered().use { bytes ->
            val writer = BufferedWriter(OutputStreamWriter(bytes, Charsets.UTF_8))
            writer.write("{\"report_type\":\"pallasync-legacy-migration-report\",\"report_version\":1")
            writer.write(",\"migration_id\":${Json.encodeToString(migration.migrationId)}")
            writer.write(",\"source_chain_id\":${Json.encodeToString(migration.sourceChain)}")
            writer.write(",\"source_protocol\":${Json.encodeToString(migration.sourceProtocol)}")
            writer.write(",\"source_export_hash\":${Json.encodeToString(migration.exportHash)}")
            writer.write(",\"target_chain_id\":${Json.encodeToString(migration.targetChain)}")
            writer.write(",\"source_high_water\":${migration.sourceHighWater}")
            writer.write(",\"staged_through\":${migration.stagedThrough}")
            writer.write(",\"source_complete\":${migration.sourceComplete}")
            writer.write(",\"candidate_count\":${migration.candidates}")
            writer.write(",\"decision_count\":${migration.decisions}")
            writer.write(",\"pending_count\":${migration.pending}")
            writer.write(",\"constructed_count\":${migration.constructed}")
            writer.write(",\"quarantined_count\":${migration.quarantined}")
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
