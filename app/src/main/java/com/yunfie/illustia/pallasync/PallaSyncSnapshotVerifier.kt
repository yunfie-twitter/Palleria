package com.yunfie.illustia.pallasync

/** Bounded §16.6 part validator. This does not install state or authorize pruning. */
internal class PallaSyncSnapshotVerifier private constructor(
    private var handle: Long,
) : AutoCloseable {
    fun acceptDecryptedAuthenticatedPart(partJson: String) {
        val active = checkNotNullHandle()
        check(PallaSyncCore.verifySnapshotPart(active, partJson)) { "C_SNAPSHOT_INVALID" }
    }

    fun finish(expectedCommitment: String) {
        val active = checkNotNullHandle()
        handle = 0
        check(PallaSyncCore.finishSnapshotVerification(active, expectedCommitment)) { "C_SNAPSHOT_INVALID" }
    }

    override fun close() {
        val active = handle
        handle = 0
        if (active != 0L) PallaSyncCore.abortSnapshotVerification(active)
    }

    private fun checkNotNullHandle(): Long = handle.also { check(it != 0L) { "C_SNAPSHOT_INVALID" } }

    companion object {
        private const val MAX_SNAPSHOT_PARTS = 65_536

        fun begin(
            recordKey: ByteArray,
            headerJson: String,
            compactionId: String,
            partCount: Int,
        ): PallaSyncSnapshotVerifier {
            require(partCount in 1..MAX_SNAPSHOT_PARTS) { "C_SNAPSHOT_INVALID" }
            val handle = PallaSyncCore.beginSnapshotVerification(recordKey, headerJson, compactionId, partCount)
            check(handle != 0L) { "C_SNAPSHOT_INVALID" }
            return PallaSyncSnapshotVerifier(handle)
        }
    }
}
