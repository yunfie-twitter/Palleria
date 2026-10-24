package com.yunfie.illustia.pallasync

import com.yunfie.illustia.pallasync.data.WinnerColumns
import java.nio.ByteBuffer
import java.util.UUID

/** §15.1. Ordinary, clear, retention and history comparisons all use the full K. */
object PallaSyncConflictResolver {
    const val MAX_COUNTER = 9_007_199_254_740_991L
    private const val MAX_OP_INDEX = 1023
    private const val UUID_BYTES = 16
    private const val ENCODED_KEY_BYTES = Long.SIZE_BYTES + 1 + UUID_BYTES * 2 + Int.SIZE_BYTES

    fun key(
        lamport: Long,
        operation: String,
        deviceId: String,
        recordId: String,
        opIndex: Int,
    ): WinnerColumns {
        require(lamport in 0..MAX_COUNTER && opIndex in 0..MAX_OP_INDEX) { "C_SCHEMA_VIOLATION" }
        val rank =
            when (operation) {
                "upsert" -> 0
                "delete", "clear" -> 1
                else -> error("C_SCHEMA_VIOLATION")
            }
        return WinnerColumns(lamport, rank, uuidBytes(deviceId), uuidBytes(recordId), opIndex)
    }

    fun compare(
        a: WinnerColumns,
        b: WinnerColumns,
    ): Int = compareBytes(encoded(a), encoded(b))

    /** Lossless lexicographically sortable 45-byte last_access_seq (§15.6); no floats. */
    fun encoded(key: WinnerColumns): ByteArray =
        ByteBuffer
            .allocate(ENCODED_KEY_BYTES)
            .putLong(key.lamport)
            .put(key.opRank.toByte())
            .put(key.deviceId)
            .put(key.recordId)
            .putInt(key.opIndex)
            .array()

    fun compareIdentifiers(
        a: String,
        b: String,
    ): Int = compareBytes(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private fun uuidBytes(value: String): ByteArray {
        val id = UUID.fromString(value)
        require(id.toString() == value && id.version() == UUID_VERSION && id.variant() == RFC_VARIANT) { "C_SCHEMA_VIOLATION" }
        return ByteBuffer
            .allocate(UUID_BYTES)
            .putLong(id.mostSignificantBits)
            .putLong(id.leastSignificantBits)
            .array()
    }

    private fun compareBytes(
        a: ByteArray,
        b: ByteArray,
    ): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val comparison = (a[i].toInt() and 255).compareTo(b[i].toInt() and 255)
            if (comparison != 0) return comparison
        }
        return a.size.compareTo(b.size)
    }

    private const val UUID_VERSION = 4
    private const val RFC_VARIANT = 2
}
