package com.yunfie.illustia.pallasync

import java.util.Base64

/** Device keys are scoped to one native operation and every mutable JNI input is overwritten (§5.4). */
internal class PallaSyncDeviceSession private constructor(
    val handle: Long,
) : AutoCloseable {
    fun signCompaction(
        compactionJson: String,
        deviceId: String,
        signingPublicKey: String,
    ): String = PallaSyncCore.signCompactionRecord(handle, compactionJson, deviceId, signingPublicKey)

    override fun close() = PallaSyncCore.endDeviceSession(handle)

    companion object {
        private const val KEY_BYTES = 32

        fun beginToken(signingSeed: String): PallaSyncDeviceSession {
            val signing = decodeKey(signingSeed)
            return try {
                PallaSyncDeviceSession(PallaSyncCore.beginDeviceSession(signing, byteArrayOf()))
            } finally {
                signing.fill(0)
            }
        }

        fun begin(keys: PallaSyncKeySnapshot): PallaSyncDeviceSession {
            val signing = decodeKey(keys.signingKeyBase64Url)
            var record: ByteArray? = null
            return try {
                record = decodeKey(keys.encryptionKeyBase64Url)
                val handle = PallaSyncCore.beginDeviceSession(signing, record)
                check(handle > 0) { "C_BOOTSTRAP_TRUST_REQUIRED" }
                PallaSyncDeviceSession(handle)
            } finally {
                signing.fill(0)
                record?.fill(0)
            }
        }

        private fun decodeKey(value: String): ByteArray {
            val bytes = Base64.getUrlDecoder().decode(value)
            if (bytes.size != KEY_BYTES || Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) != value) {
                bytes.fill(0)
                error("C_SCHEMA_VIOLATION")
            }
            return bytes
        }
    }
}
