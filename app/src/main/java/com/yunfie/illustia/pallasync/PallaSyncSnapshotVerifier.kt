package com.yunfie.illustia.pallasync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Bounded §16.6 part validator. This does not install state or authorize pruning. */
internal class PallaSyncSnapshotVerifier private constructor(
    private var handle: Long,
) : AutoCloseable {
    fun acceptDecryptedAuthenticatedPart(partJson: String) {
        val active = checkNotNullHandle()
        check(PallaSyncSnapshotCore.acceptVerifiedPart(active, partJson)) { "C_SNAPSHOT_INVALID" }
    }

    /**
     * Verify the SyncRecord signature and decrypt its reserved snapshot frame before streaming
     * it into the commitment verifier. [authorPublicKeyBase64Url] must come from the already
     * authenticated registry history for this chain; this class does not establish trust.
     */
    fun acceptSignedRecord(
        recordJson: String,
        authorPublicKeyBase64Url: String,
        epochSession: PallaSyncEpochSession,
    ) {
        val active = checkNotNullHandle()
        val part = epochSession.decryptSnapshotPart(recordJson, authorPublicKeyBase64Url)
        check(PallaSyncSnapshotCore.acceptVerifiedPart(active, part)) { "C_SNAPSHOT_INVALID" }
    }

    fun finish(expectedCommitment: String) {
        val active = checkNotNullHandle()
        handle = 0
        check(PallaSyncSnapshotCore.finishVerification(active, expectedCommitment)) { "C_SNAPSHOT_INVALID" }
    }

    fun finishCommitment(): String {
        val active = checkNotNullHandle()
        handle = 0
        return PallaSyncSnapshotCore.finishCommitment(active)
    }

    override fun close() {
        val active = handle
        handle = 0
        if (active != 0L) PallaSyncSnapshotCore.abortVerification(active)
    }

    private fun checkNotNullHandle(): Long = handle.also { check(it != 0L) { "C_SNAPSHOT_INVALID" } }

    companion object {
        private const val MAX_SNAPSHOT_PARTS = 65_536

        /** Strictly validate the §10.11 batch partition and exact null-sequence storage receipts. */
        fun validateUploadedParts(
            response: ByteArray,
            submittedRecords: JsonArray,
            pin: JsonObject,
        ): JsonObject {
            val recordsJson = checkNotNull(PallaSyncCore.canonicalJson(submittedRecords.toString())) { "C_SCHEMA_VIOLATION" }
            val pinJson = checkNotNull(PallaSyncCore.canonicalJson(pin.toString())) { "C_BOOTSTRAP_TRUST_REQUIRED" }
            return Json
                .parseToJsonElement(
                    PallaSyncSnapshotCore.validateSnapshotPartUploadResponse(response, recordsJson, pinJson),
                ).jsonObject
        }

        fun begin(
            recordKey: ByteArray,
            headerJson: String,
            compactionId: String,
            partCount: Int,
        ): PallaSyncSnapshotVerifier {
            require(partCount in 1..MAX_SNAPSHOT_PARTS) { "C_SNAPSHOT_INVALID" }
            val handle = PallaSyncSnapshotCore.beginVerification(recordKey, headerJson, compactionId, partCount)
            check(handle != 0L) { "C_SNAPSHOT_INVALID" }
            return PallaSyncSnapshotVerifier(handle)
        }

        /** Producer-only entry point; the record key stays inside the epoch session in Rust. */
        fun beginFromEpochSession(
            epochSession: PallaSyncEpochSession,
            headerJson: String,
            compactionId: String,
            partCount: Int,
        ): PallaSyncSnapshotVerifier {
            require(partCount in 1..MAX_SNAPSHOT_PARTS) { "C_SNAPSHOT_INVALID" }
            val canonicalHeader = checkNotNull(PallaSyncCore.canonicalJson(headerJson)) { "C_SNAPSHOT_INVALID" }
            val handle =
                PallaSyncSnapshotCore.beginFromEpochSession(
                    epochSession.handle,
                    canonicalHeader,
                    compactionId,
                    partCount,
                )
            check(handle != 0L) { "C_SNAPSHOT_INVALID" }
            return PallaSyncSnapshotVerifier(handle)
        }
    }
}
