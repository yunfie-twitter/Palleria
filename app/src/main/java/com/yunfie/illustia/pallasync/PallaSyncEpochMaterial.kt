package com.yunfie.illustia.pallasync

import androidx.annotation.Keep
import java.util.Base64

/** Mutable normal epoch keys. No root/recovery material or private JSON. */
@Keep
class PallaSyncEpochMaterial(
    private val material: Array<ByteArray>,
) : AutoCloseable {
    private var closed = false

    init {
        check(material.size == MATERIAL_COUNT && material.all { it.size == KEY_BYTES }) { "C_SCHEMA_VIOLATION" }
    }

    fun snapshot(
        previous: PallaSyncKeySnapshot,
        epoch: Long,
    ): PallaSyncKeySnapshot {
        check(!closed) { "C_BOOTSTRAP_TRUST_REQUIRED" }
        return previous.copy(
            epoch = epoch,
            encryptionKeyBase64Url = encode(RECORD_KEY),
            deviceMetaKeyBase64Url = encode(META_KEY),
            collectionTagKeyBase64Url = encode(TAG_KEY),
            epochSecretBase64Url = encode(EPOCH_SECRET),
        )
    }

    override fun close() {
        material.forEach { it.fill(0) }
        closed = true
    }

    private fun encode(index: Int) = Base64.getUrlEncoder().withoutPadding().encodeToString(material[index])

    private companion object {
        const val RECORD_KEY = 0
        const val META_KEY = 1
        const val TAG_KEY = 2
        const val EPOCH_SECRET = 3
        const val KEY_BYTES = 32
        const val MATERIAL_COUNT = 4
    }
}
